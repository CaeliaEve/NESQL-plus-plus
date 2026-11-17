package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import thaumcraft.client.gui.GuiResearchRecipe;
import java.util.*;
import java.util.function.Function;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** ARI 1.1.6 scanned-item observations, never material consumption. */
final class ItemAspectReferences extends ReaderSnapshot implements NativeReferences {
    static final String HANDLER = "com.gtnewhorizons.aspectrecipeindex.nei.ItemsContainingAspectHandler";
    private AspectRecipeIndexContext context;
    private  JsonObject source;
    private final String signature, domain;
    private  JsonArray records;
    private  List<String> nativeScans;
    private final Map<String, ItemStack> specimens = new HashMap<>();

    static boolean supports(ICraftingHandler value) { return value != null && HANDLER.equals(value.getClass().getName()); }
    ItemAspectReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler) || handler.getClass() != type(HANDLER)) throw fault("Unknown native item aspect handler");
        this.source = copy(source);
        context = AspectRecipeIndexContext.open(true);
        nativeScans = scanKeys();
        records = read(specimens); signature = CanonicalJson.digest(records);
        domain = records.get(0).getAsJsonObject().get("domain").getAsString();
    }
    private List<String> scanKeys() {
        return context.scans();
    }
    private JsonArray read(Map<String, ItemStack> owned) {
        JsonArray result;
        try {
            result = snapshot(scanKeys(), GuiResearchRecipe::getFromCache, Aspects::item,
                    context.showUndiscovered, source, owned);
        } catch (RuntimeException | Error failure) {
            try { context.verify(); } catch (RuntimeException | Error changed) { failure.addSuppressed(changed); }
            throw failure;
        }
        context.verify();
        return result;
    }
    public int size() { checkOpen(); return records.size(); }
    public String domain() { checkOpen(); return domain; }
    public JsonObject counts() { checkOpen(); return object("scans", records.size() - 1, "items", specimens.size()); }
    public void verify() { checkOpen();
        // Cheap per-batch guard; full quantities/NBT are re-read once before sealing.
        List<String> keys = scanKeys();
        if (keys != nativeScans || (keys == null ? 0 : keys.size()) != records.size() - 1) throw drift();
    }
    public void finishVerification() { checkOpen();
        if (!signature.equals(CanonicalJson.digest(read(new HashMap<>())))) throw drift();
    }
    public void capture(int index, Facts facts) { checkOpen();
        context.verify();
        JsonObject row = copy(records.get(index).getAsJsonObject());
        JsonObject data = row.getAsJsonObject("data");
        if (data.has("item") && !data.get("item").isJsonNull()) {
            String id = data.get("item").getAsString();
            ItemStack stack = specimens.get(id);
            try {
                if (stack == null || !facts.item(stack.copy()).equals(id)) throw drift();
            } catch (RuntimeException | Error failure) {
                try { context.verify(); } catch (RuntimeException | Error changed) { failure.addSuppressed(changed); }
                throw failure;
            }
        }
        context.verify();
        facts.row("references", row);
    }

    static JsonArray snapshot(List<String> scans, Function<Integer, ItemStack> cache,
            Function<ItemStack, JsonElement> aspects, boolean showUndiscovered, JsonObject source, Map<String, ItemStack> specimens) {
        // The native loaded-cache query explicitly treats an absent player's list as empty.
        scans = scans == null ? Collections.emptyList() : new ArrayList<>(scans);
        if (scans.size() > 262144) throw fault("Native scanned-object budget exceeded");
        JsonArray data = array();
        data.add(object("kind", "itemAspectsDefinition", "source", source, "scans", scans.size(),
                "visibility", showUndiscovered ? "allRegisteredAspects" : "nativePlayerKnowledge",
                "ready", true, "consuming", false));
        for (String scan : scans) {
            Jobs.checkpoint();
            if (scan == null || scan.isEmpty() || scan.length() > 256) throw fault("Invalid native scan key");
            Integer key = null;
            // Native code removes the first character, then parses an int. Preserve invalid entries.
            try { key = Integer.parseInt(scan.substring(1)); } catch (NumberFormatException ignored) { }
            ItemStack cached = key == null ? null : cache.apply(key);
            String status = key == null ? "invalidKey" : cached == null ? "cacheMiss" : "available";
            String item = null; JsonElement amounts = JsonNull.INSTANCE;
            if (cached != null) {
                ItemStack stack = cached.copy(); stack.stackSize = 1;
                String registry = Item.itemRegistry.getNameForObject(stack.getItem());
                if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) throw fault("Unregistered scanned item");
                item = Identity.item(registry, net.minecraft.init.Items.feather.getDamage(stack), TypedNbt.encode(stack.getTagCompound()));
                amounts = aspects.apply(stack.copy());
                if (amounts == null || amounts.isJsonNull()) throw fault("Native bonus aspect callback returned null");
                if (!amounts.isJsonArray() || amounts.getAsJsonArray().size() > 4096) throw fault("Invalid scanned aspect quantities");
                specimens.put(item, stack);
                if (amounts.getAsJsonArray().size() == 0) status = "noAspects";
            }
            data.add(object("kind", "itemAspectObservation", "scan", scan, "status", status, "item", item, "aspects", amounts));
        }
        String domain = Identity.content("reference", object("kind", "thaumcraftScannedItemAspects", "records", data));
        JsonArray rows = array();
        for (int i = 0; i < data.size(); i++) {
            JsonObject row = object("domain", domain, "order", i, "data", data.get(i));
            row.addProperty("id", Identity.content("refrow", row)); rows.add(row);
        }
        return rows;
    }
    private static JsonObject copy(JsonObject value) { return new JsonParser().parse(value.toString()).getAsJsonObject(); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("handler_unsupported", message); }
    private static Jobs.Fault drift() { return new Jobs.Fault("environment_changed", "Native scanned-item facts changed"); }

    @Override protected void release(){specimens.clear();context=null;source=null;records=null;nativeScans=null;}
}
