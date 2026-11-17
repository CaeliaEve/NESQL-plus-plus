package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Configured reactor information, not the NEI display's apparent 1:1 transformation. */
final class DraconicReactorReferences extends ReaderSnapshot implements NativeReferences {
    static final String HANDLER = "com.brandon3055.draconicevolution.integration.nei.ReactorNEIHandler";
    private static final String CONFIG = "com.brandon3055.draconicevolution.common.handler.ConfigHandler";
    private static final String CONTAINER = "com.brandon3055.draconicevolution.common.container.ContainerReactor";
    private  JsonObject source;
    private  JsonArray records;
    private final Map<String, ItemStack> specimens = new TreeMap<>();
    private final String domain, signature;
    static boolean supports(ICraftingHandler handler) { return handler != null && HANDLER.equals(handler.getClass().getName()); }
    DraconicReactorReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler)) throw fault("Unknown native reactor information handler");
        this.source = copy(source);
        records = snapshot(this.source, stacks(), type(CONFIG), type(CONTAINER), specimens);
        domain = records.get(0).getAsJsonObject().get("domain").getAsString(); signature = CanonicalJson.digest(records);
    }
    public int size() { checkOpen(); return 1; }
    public String domain() { checkOpen(); return domain; }
    public JsonObject counts() { checkOpen(); return object("fuels", 3, "products", 4, "items", specimens.size()); }
    public void verify() { checkOpen();
        if (!signature.equals(CanonicalJson.digest(snapshot(source, stacks(), type(CONFIG), type(CONTAINER), new HashMap<>()))))
            throw new Jobs.Fault("environment_changed", "Native reactor registrations or configuration changed");
    }
    public void capture(int index, Facts facts) { checkOpen();
        if (index != 0) throw fault("Native reactor information index differs");
        for (Map.Entry<String, ItemStack> entry : specimens.entrySet()) {
            Jobs.checkpoint();
            if (!entry.getKey().equals(facts.item(entry.getValue().copy()))) throw fault("Native reactor item identity changed");
        }
        facts.row("references", copy(records.get(0).getAsJsonObject()));
    }
    private static ItemStack[] stacks() {
        Class<?> blocks = type("com.brandon3055.draconicevolution.common.ModBlocks"), items = type("com.brandon3055.draconicevolution.common.ModItems");
        return new ItemStack[]{block(blocks, "reactorCore"), block(blocks, "draconicBlock"), item(items, "draconicIngot", 0),
                item(items, "nugget", 1), item(items, "chaosFragment", 0), item(items, "chaosFragment", 1),
                item(items, "chaosFragment", 2), item(items, "chaosShard", 0)};
    }
    private static ItemStack block(Class<?> owner, String name) {
        Object raw = field(owner, null, name);
        if (!(raw instanceof Block)) throw new Jobs.Fault("handler_not_ready", "Native reactor block is unavailable: " + name);
        return new ItemStack((Block) raw, 1);
    }
    private static ItemStack item(Class<?> owner, String name, int meta) {
        Object raw = field(owner, null, name);
        if (!(raw instanceof Item)) throw new Jobs.Fault("handler_not_ready", "Native reactor item is unavailable: " + name);
        return new ItemStack((Item) raw, 1, meta);
    }
    static JsonArray snapshot(JsonObject source, ItemStack[] stacks, Class<?> config, Class<?> constants, Map<String, ItemStack> specimens) {
        if (stacks.length != 8 || !CONFIG.equals(config.getName()) || !CONTAINER.equals(constants.getName())) throw fault("Unverified native reactor definitions");
        String[] ids = new String[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            Jobs.checkpoint(); ItemStack stack = stacks[i];
            if (stack == null || stack.getItem() == null || stack.stackSize != 1) throw fault("Invalid reactor information specimen");
            String registry = Item.itemRegistry.getNameForObject(stack.getItem());
            if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem()) throw fault("Unregistered reactor information item");
            ids[i] = Identity.item(registry, stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound())); specimens.put(ids[i], stack.copy());
        }
        JsonArray fuels = array(), products = array();
        String[] fuelRoles = {"block", "ingot", "nugget"}, ores = {"blockDraconiumAwakened", "ingotDraconiumAwakened", "nuggetDraconiumAwakened"};
        String[] fuelFields = {"blockFuelAmount", "ingotFuelAmount", "nuggetFuelAmount"};
        String[] productRoles = {"tiny", "small", "large", "full"}, productFields = {"tinyChaosAmount", "smallChaosAmount", "largeChaosAmount", "fullChaosAmount"};
        for (int i = 0; i < 3; i++) fuels.add(object("role", fuelRoles[i], "ore", ores[i], "item", ids[1 + i], "units", Integer.toString(integer(constants, fuelFields[i]))));
        for (int i = 0; i < 4; i++) products.add(object("role", productRoles[i], "item", ids[4 + i], "units", Integer.toString(integer(constants, productFields[i]))));
        JsonObject settings = object("maximumFuelStorage", integer(constants, "maximumFuelStorage"),
                "configuredFuelStorage", integer(config, "reactorFuelStorage"), "configuredNuggetUnits", integer(config, "reactorFuelValue"),
                "configuredTinyChaosUnits", integer(config, "reactorChaosValue"), "outputMultiplier", decimal(config, "reactorOutputMultiplier"),
                "fuelUsageMultiplier", decimal(config, "reactorFuelUsageMultiplier"), "linearFuelUsage", bool(config, "linearReactorFuelUsage"),
                "largeExplosion", bool(config, "enableReactorBigBoom"), "automation", bool(config, "enableAutomation"));
        JsonObject data = object("kind", "draconicReactorDefinition", "source", source, "ready", true, "consuming", false,
                "scope", "nativeConfiguredReference", "core", ids[0], "fuels", fuels, "products", products, "config", settings,
                "fuelMatching", "orderedOreDictionary", "automationExtraction", "fullShardStrictlyGreater",
                "manualExtraction", "fuelRefundBeforeFragments", "manualStackUpgradeThreshold", 64, "manualAccess", "offlineOnly",
                "operation", "stateDependentEnergyAndConversion");
        String domain = Identity.content("reference", object("kind", "draconicReactorInformation", "records", array(data)));
        JsonObject row = object("domain", domain, "order", 0, "data", data); row.addProperty("id", Identity.content("refrow", row));
        if (CanonicalJson.bytes(row).length > 1048576) throw fault("Native reactor information exceeds leaf budget");
        return array(row);
    }
    private static int integer(Class<?> owner, String name) {
        Object raw = field(owner, null, name); if (!(raw instanceof Integer) || (Integer) raw < 0) throw fault("Invalid native reactor unit: " + name); return (Integer) raw;
    }
    private static boolean bool(Class<?> owner, String name) {
        Object raw = field(owner, null, name); if (!(raw instanceof Boolean)) throw fault("Invalid native reactor flag: " + name); return (Boolean) raw;
    }
    private static String decimal(Class<?> owner, String name) {
        Object raw = field(owner, null, name); if (!(raw instanceof Double) || !Double.isFinite((Double) raw) || (Double) raw < 0)
            throw fault("Invalid native reactor multiplier: " + name); return Double.toString((Double) raw);
    }
    private static JsonObject copy(JsonObject row) { return new JsonParser().parse(row.toString()).getAsJsonObject(); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("reference_unsupported", message); }

    @Override protected void release(){specimens.clear();source=null;records=null;}
}
