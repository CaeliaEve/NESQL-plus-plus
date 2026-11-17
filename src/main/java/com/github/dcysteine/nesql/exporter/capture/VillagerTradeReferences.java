package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.math.BigDecimal;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Mobs Info 0.5.6 published client trade catalogue, never its randomized PositionedStacks. */
final class VillagerTradeReferences extends ReaderSnapshot implements NativeReferences {
    static final String HANDLER = "com.kuba6000.mobsinfo.nei.VillagerTradesHandler";
    private static final int LIMIT = 262144;
    private  Class<?> owner;
    private  JsonObject source;
    private  List<?> pages;
    private final Map<String, ItemStack> specimens = new HashMap<>();
    private  JsonArray data;
    private final String domain, signature;

    static boolean supports(ICraftingHandler handler) {
        return handler != null && HANDLER.equals(handler.getClass().getName());
    }

    VillagerTradeReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler)) throw fault("Unknown villager trade handler");
        this.owner = handler.getClass();
        this.source = copy(source);
        this.pages = publishedPages();
        if (pages.isEmpty()) throw new Jobs.Fault("handler_not_ready", "Mobs Info client trade catalogue is empty; readiness is unproven");
        data = snapshot(pages, this.source, specimens);
        signature = CanonicalJson.digest(data);
        domain = Identity.content("reference", object("kind", "mobsInfoVillagerTrades", "records", data));
    }

    public int size() { checkOpen(); return data.size(); }
    public String domain() { checkOpen(); return domain; }
    public JsonObject counts() { checkOpen();
        return object("professions", pages.size(), "offers", data.size() - pages.size(), "items", specimens.size());
    }
    public void verify() { checkOpen();
        List<?> current = publishedPages();
        if (current.size() != pages.size()) throw drift();
        for (int i = 0; i < current.size(); i++) if (current.get(i) != pages.get(i)) throw drift();
    }
    public void finishVerification() { checkOpen();
        if (!signature.equals(CanonicalJson.digest(snapshot(publishedPages(), source, new HashMap<>())))) throw drift();
    }
    public void capture(int index, Facts facts) { checkOpen();
        Jobs.checkpoint();
        JsonObject entry = copy(data.get(index).getAsJsonObject());
        if (entry.get("kind").getAsString().equals("villagerProfession")) {
            facts.text(entry.get("name").getAsString());
        } else {
            for (JsonElement input : entry.getAsJsonArray("inputs")) captureItem(input.getAsJsonObject(), facts);
            captureItem(entry.getAsJsonObject("output"), facts);
        }
        JsonObject row = object("domain", domain, "order", index, "data", entry);
        row.addProperty("id", Identity.content("refrow", row));
        facts.row("references", row);
    }
    private void captureItem(JsonObject item, Facts facts) {
        String id = item.get("item").getAsString();
        ItemStack stack = specimens.get(id);
        if (stack == null || !id.equals(facts.item(stack.copy()))) throw drift();
    }
    private List<?> publishedPages() {
        Object raw = field(owner, null, "cachedRecipes");
        if (!(raw instanceof List)) throw fault("Mobs Info trade cache is not a list");
        synchronized (raw) { return new ArrayList<>((List<?>) raw); }
    }

    static JsonArray snapshot(List<?> pages, JsonObject source, Map<String, ItemStack> specimens) {
        JsonArray result = array();
        Set<Integer> professions = new HashSet<>();
        if (pages.size() > LIMIT) throw fault("Too many villager professions");
        for (Object page : pages) {
            Jobs.checkpoint();
            if (page == null) throw fault("Null villager profession");
            int profession = integer(field(page, "professionID"), "profession");
            if (!professions.add(profession)) throw fault("Duplicate villager profession");
            String name = text(field(page, "profession"), "profession name");
            String mod = text(field(page, "mod"), "profession mod");
            Object raw = field(page, "tradeList");
            if (!(raw instanceof List)) throw fault("Missing native trade list");
            List<?> offers = new ArrayList<>((List<?>) raw);
            if ((long) result.size() + 1 + offers.size() > LIMIT) throw fault("Villager catalogue exceeds entry budget");
            result.add(object("kind", "villagerProfession", "source", source, "profession", profession,
                    "name", name, "mod", mod, "offers", offers.size()));
            for (int i = 0; i < offers.size(); i++) {
                Jobs.checkpoint();
                Object trade = call(offers.get(i), "getRight");
                Object rate = call(trade, "getChance");
                if (!(rate instanceof Double) || !Double.isFinite((Double) rate) || (Double) rate < 0)
                    throw fault("Invalid native trade frequency");
                // The native collector sums equal offers. Values above one are not exchange failure probabilities.
                String frequency = BigDecimal.valueOf((Double) rate).stripTrailingZeros().toPlainString();
                JsonArray inputs = array();
                inputs.add(tradeItem(call(trade, "getFirstInput"), specimens));
                Object secondary = call(trade, "getSecondInput");
                if (secondary != null) inputs.add(tradeItem(secondary, specimens));
                result.add(object("kind", "villagerOffer", "source", source, "profession", profession, "offer", i,
                        "nativeFrequency", frequency, "inputs", inputs, "output", tradeItem(call(trade, "getOutput"), specimens)));
            }
        }
        return result;
    }
    private static JsonObject tradeItem(Object raw, Map<String, ItemStack> specimens) {
        if (raw == null) throw fault("Missing native trade item");
        Object value = field(raw, "stack");
        if (!(value instanceof ItemStack)) throw fault("Missing native trade stack");
        ItemStack stack = ((ItemStack) value).copy();
        if (stack.getItem() == null || stack.stackSize <= 0) throw fault("Invalid native trade stack");
        String registry = Item.itemRegistry.getNameForObject(stack.getItem());
        if (registry == null) throw fault("Unregistered native trade item");
        String id = Identity.item(registry, net.minecraft.init.Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound()));
        specimens.put(id, stack);
        Object rawSizes = field(raw, "possibleSizes");
        TreeSet<Integer> sizes = new TreeSet<>();
        if (rawSizes == null) sizes.add(stack.stackSize);
        else {
            if (!(rawSizes instanceof Set) || ((Set<?>) rawSizes).isEmpty() || ((Set<?>) rawSizes).size() > 65536)
                throw fault("Invalid native trade quantity alternatives");
            for (Object size : (Set<?>) rawSizes) {
                int amount = integer(size, "trade amount");
                // Mobs Info assigns these signed Java ints directly to its display stack.
                // Preserve anomalous observations; this reference domain never consumes items.
                sizes.add(amount);
            }
        }
        JsonArray amounts = array(); for (int size : sizes) amounts.add(value(size));
        Object enchantment = field(raw, "enchantability");
        if (enchantment != null && integer(enchantment, "enchantment power") < 0) throw fault("Negative enchantment power");
        return object("item", id, "sizes", amounts, "enchantmentPower", enchantment);
    }
    private static Object call(Object target, String name) {
        if (target == null) throw fault("Missing native trade object for " + name);
        return invoke(target.getClass(), target, name, new Class<?>[0]);
    }
    private static int integer(Object value, String name) {
        if (!(value instanceof Integer)) throw fault("Invalid " + name);
        return (Integer) value;
    }
    private static String text(Object value, String name) {
        if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > 65536) throw fault("Invalid " + name);
        return (String) value;
    }
    private static JsonObject copy(JsonObject value) { return new JsonParser().parse(value.toString()).getAsJsonObject(); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("reference_unsupported", message); }
    private static Jobs.Fault drift() { return new Jobs.Fault("environment_changed", "Mobs Info native trade catalogue changed"); }

    @Override protected void release(){specimens.clear();owner=null;source=null;pages=null;data=null;}
}
