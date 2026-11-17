package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Current native client configuration and loaded pools, never randomized NEI display specimens. */
final class InfernalDropsReferences extends ReaderSnapshot implements NativeReferences {
    static final String HANDLER = "com.kuba6000.mobsinfo.nei.MobHandlerInfernal";
    private static final String CORE = "atomicstryker.infernalmobs.common.InfernalMobsCore";
    private static final String[] TIERS = {"Elite", "Ultra", "Inferno"};
    private static final String[] POOLS = {"getDropIdListElite", "getDropIdListUltra", "getDropIdListInfernal"};
    private  Object core;
    private final boolean enabled;
    private  JsonObject source;
    private  JsonArray records;
    private final Map<String, ItemStack> specimens = new TreeMap<>();
    private  List<?>[] pools;
    private final int[] sizes;
    private final String domain, signature;

    static boolean supports(ICraftingHandler handler) { return handler != null && HANDLER.equals(handler.getClass().getName()); }
    InfernalDropsReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler)) throw fault("Unknown native infernal handler");
        this.source = copy(source); enabled = enabled(); core = enabled ? core() : null;
        if (enabled && core == null) throw new Jobs.Fault("handler_not_ready", "Native InfernalMobs core is not initialized");
        records = snapshot(core, enabled, this.source, specimens);
        domain = records.get(0).getAsJsonObject().get("domain").getAsString(); signature = CanonicalJson.digest(records);
        pools = enabled ? pools(core) : new List<?>[0]; sizes = new int[pools.length];
        for (int i = 0; i < pools.length; i++) sizes[i] = pools[i].size();
    }
    public int size() { checkOpen(); return records.size(); }
    public String domain() { checkOpen(); return domain; }
    public JsonObject counts() { checkOpen(); return object("tiers", enabled ? 3 : 0, "entries", records.size() - (enabled ? 4 : 1), "items", specimens.size()); }
    public void verify() { checkOpen();
        if (enabled() != enabled || enabled && core() != core) throw drift();
        if (!enabled) return;
        List<?>[] current = pools(core);
        for (int i = 0; i < current.length; i++) if (current[i] != pools[i] || current[i].size() != sizes[i]) throw drift();
    }
    public void finishVerification() { checkOpen();
        if (!signature.equals(CanonicalJson.digest(snapshot(enabled ? core() : null, enabled(), source, new HashMap<>())))) throw drift();
    }
    public void capture(int index, Facts facts) { checkOpen();
        Jobs.checkpoint(); JsonObject row = copy(records.get(index).getAsJsonObject()), data = row.getAsJsonObject("data");
        if (data.has("item")) {
            String id = data.get("item").getAsString(); ItemStack stack = specimens.get(id);
            if (stack == null || !id.equals(facts.item(stack.copy()))) throw drift();
        }
        facts.row("references", row);
    }
    private static boolean enabled() {
        Object value = field(type("com.kuba6000.mobsinfo.config.Config$Compatibility"), null, "enableMobHandlerInfernal");
        if (!(value instanceof Boolean)) throw fault("Invalid native infernal enabled flag"); return (Boolean) value;
    }
    private static Object core() { return invoke(type(CORE), null, "instance", new Class<?>[0]); }
    private static Object call(Object owner, String method) { return invoke(owner.getClass(), owner, method, new Class<?>[0]); }
    private static int integer(Object value) { if (!(value instanceof Integer)) throw fault("Invalid native infernal integer"); return (Integer) value; }
    private static List<?>[] pools(Object core) {
        List<?>[] result = new List<?>[3];
        for (int i = 0; i < result.length; i++) {
            Object raw = call(core, POOLS[i]);
            if (!(raw instanceof List) || ((List<?>) raw).size() > 262144) throw fault("Missing or oversized loaded infernal pool");
            result[i] = (List<?>) raw;
        }
        return result;
    }
    static JsonArray snapshot(Object core, boolean enabled, JsonObject source, Map<String, ItemStack> specimens) {
        JsonArray data = array(); JsonArray dimensions = array(); int entries = 0;
        if (enabled) {
            if (core == null || !CORE.equals(core.getClass().getName())) throw fault("Missing audited native InfernalMobs core");
            Object raw = field(core, "dimensionBlackList");
            if (!(raw instanceof List) || ((List<?>) raw).size() > 16384) throw fault("Invalid native infernal dimension list");
            for (Object dimension : (List<?>) raw) dimensions.add(value(integer(dimension)));
        }
        data.add(object("kind", "infernalDropsDefinition", "source", source, "enabled", enabled, "ready", true,
                "scope", "nativeClientConfiguration", "consuming", false, "tiers", enabled ? 3 : 0, "entries", 0,
                "dimensionBlacklist", dimensions, "selection", "finalModifierCountRepeatedUniformPool",
                "quantitySource", "loadedPoolStack", "enchanting", "nativeInfernalPerSelection"));
        if (enabled) {
            List<?>[] lists = pools(core);
            for (int i = 0; i < TIERS.length; i++) {
                Jobs.checkpoint(); String name = TIERS[i], tier = name.toLowerCase(Locale.ROOT);
                int rarity = integer(call(core, "get" + name + "Rarity"));
                int min = integer(call(core, "getMin" + name + "Modifiers")), max = integer(call(core, "getMax" + name + "Modifiers"));
                if (rarity <= 0 || min <= 0 || max <= 0 || min > 65536 || max > 65536) throw fault("Invalid native infernal rarity or requested modifier bounds");
                List<?> list = lists[i]; entries = Math.addExact(entries, list.size());
                if (entries > 262144) throw fault("Native infernal entry budget exceeded");
                data.add(object("kind", "infernalTier", "tier", tier, "rarity", rarity, "requestedMinModifiers", min,
                        "requestedMaxModifiers", max, "entries", list.size()));
                for (int n = 0; n < list.size(); n++) {
                    Jobs.checkpoint(); Object raw = list.get(n);
                    if (!(raw instanceof ItemStack)) throw fault("Missing loaded infernal stack"); ItemStack stack = ((ItemStack) raw).copy();
                    if (stack.getItem() == null || stack.stackSize <= 0) throw fault("Invalid loaded infernal quantity");
                    String registry = Item.itemRegistry.getNameForObject(stack.getItem());
                    if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) throw fault("Unregistered infernal item");
                    int enchantability = stack.getItem().getItemEnchantability(); if (enchantability < 0) throw fault("Invalid native infernal enchantability");
                    String id = Identity.item(registry, stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound()));
                    data.add(object("kind", "infernalPoolItem", "tier", tier, "entry", n, "item", id,
                            "amount", Integer.toString(stack.stackSize), "enchantability", enchantability,
                            "enchantmentKind", stack.getItem() instanceof ItemEnchantedBook ? "nativeRandomEnchantedBook" : "remainingModifierCount"));
                    stack.stackSize = 1; specimens.put(id, stack);
                }
            }
        }
        data.get(0).getAsJsonObject().addProperty("entries", entries);
        String domain = Identity.content("reference", object("kind", "mobsInfoInfernalPools", "records", data));
        JsonArray rows = array(); long bytes = 0;
        for (int i = 0; i < data.size(); i++) {
            JsonObject row = object("domain", domain, "order", i, "data", data.get(i)); row.addProperty("id", Identity.content("refrow", row));
            int size = CanonicalJson.bytes(row).length; bytes += size;
            if (size > 1048576 || bytes > 64L * 1024 * 1024) throw fault("Native infernal reference byte budget exceeded");
            rows.add(row);
        }
        return rows;
    }
    private static JsonObject copy(JsonObject row) { return new JsonParser().parse(row.toString()).getAsJsonObject(); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("reference_unsupported", message); }
    private static Jobs.Fault drift() { return new Jobs.Fault("environment_changed", "Native infernal configuration or loaded pools changed"); }

    @Override protected void release(){specimens.clear();core=null;source=null;records=null;pools=null;}
}
