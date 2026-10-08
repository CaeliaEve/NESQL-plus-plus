package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Gendustry 1.9.4 native item-to-fluid tables. Registry keys, never display stacks, define matching. */
final class GendustryFluidRecipes implements RegistryRecipes {
    private static final String ROOT = "net.bdew.gendustry.";
    private final Object registry;
    private final List<Entry> entries;
    private final FluidStack output;
    private final float mjPerItem, powerUseRate, activationEnergy;
    private final int tankSize;
    private final String signature;
    private TemplateRecipeHandler handler;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(ROOT + "nei.LiquifierHandler") || name.equals(ROOT + "nei.MutagenProducerHandler");
    }

    GendustryFluidRecipes(TemplateRecipeHandler handler) {
        this(context(handler));
        this.handler = handler;
    }
    private GendustryFluidRecipes(Context source) {
        this(source.registry, source.output, source.mjPerItem, source.powerUseRate, source.activationEnergy, source.tankSize);
    }
    GendustryFluidRecipes(Object registry, FluidStack output, float mjPerItem, float powerUseRate, float activationEnergy, int tankSize) {
        if (output == null || output.getFluid() == null || !Float.isFinite(mjPerItem) || mjPerItem <= 0
                || !Float.isFinite(powerUseRate) || powerUseRate < 0 || !Float.isFinite(activationEnergy) || activationEnergy < 0 || tankSize <= 0)
            throw fault("Invalid Gendustry processor configuration");
        this.registry = registry; this.output = output.copy();
        this.mjPerItem = mjPerItem; this.powerUseRate = powerUseRate; this.activationEnergy = activationEnergy; this.tankSize = tankSize;
        entries = snapshot(registry); signature = signature(entries);
    }
    public int size() { return entries.size(); }
    public void verify() {
        if (!signature.equals(signature(snapshot(registry)))) throw new Jobs.Fault("environment_changed", "Gendustry fluid source registry changed");
    }
    public boolean capture(int index, RecipeRow row) {
        if (!captureFacts(index, row)) return false;
        if (handler == null) throw fault("Gendustry native view handler is absent");
        Entry entry = entries.get(index);
        ItemStack input = anchor(entry);
        String kind = handler.getClass().getName().endsWith("LiquifierHandler") ? "Liquifier" : "MutagenProducer";
        TemplateRecipeHandler.CachedRecipe cached = (TemplateRecipeHandler.CachedRecipe) TinkerRecipes.construct(
                type(ROOT + "nei." + kind + "Handler$" + kind + "Recipe"),
                new Class<?>[]{handler.getClass(), ItemStack.class, int.class}, handler, input.copy(), entry.amount);
        List<PositionedStack> positions = cached.getIngredients();
        if (positions.size() != 1 || positions.get(0).relx != 39 || positions.get(0).rely != 28)
            throw new Jobs.Fault("slot_changed", "Gendustry native input position changed");
        row.slot("input", "item", 0, 39, 28);
        handler.arecipes.clear(); handler.arecipes.add(cached);
        return true;
    }
    boolean captureFacts(int index, RecipeRow row) {
        Entry entry = entries.get(index);
        // Both GUI and sided inventory require getValue(stack)>0. Keep all keys in
        // the snapshot: rejected exact keys must still block the wildcard fallback.
        if (entry.amount <= 0) return false;
        // The start consumes one, then completion waits for the ENTIRE batch.
        // A batch larger than this native tank can never finish, but is not silently excluded.
        if (entry.amount > tankSize) throw fault("Gendustry batch exceeds its output tank and cannot complete");
        ItemStack input = anchor(entry);
        JsonObject rule = object("kind", "wildcard", "meta", entry.meta == 32767, "nbt", true);
        if (entry.meta == 32767) {
            JsonArray excludes = array();
            for (Entry sibling : entries) if (sibling.item == entry.item && sibling.meta != 32767) {
                excludes.add(object("id", row.facts.item(new ItemStack(sibling.item, 1, sibling.meta)),
                        "rule", object("kind", "wildcard", "meta", false, "nbt", true)));
            }
            if (excludes.size() > 4096) throw fault("Gendustry wildcard overrides exceed the rule budget");
            if (excludes.size() > 0) rule = object("kind", "except", "base", rule, "exclude", excludes);
        }
        row.itemInput(null, 0, Collections.singletonList(new RecipeRow.Ingredient(input, 1, false, rule)), false);
        FluidStack result = output.copy(); result.amount = entry.amount;
        row.fluidOutput(null, 0, result);
        row.property("gendustry:mjPerItem", "Processor energy per item (MJ)", mjPerItem);
        row.property("gendustry:powerUseRate", "Fraction of stored energy available per tick", powerUseRate);
        row.property("gendustry:activationEnergy", "Stored energy must be greater than this activation threshold (MJ)", activationEnergy);
        row.property("gendustry:tankCapacity", "Output tank capacity (mB)", tankSize);
        row.property("gendustry:completion", "Completion condition", "Waits for space for the entire output batch; consumed input remains in progress; no fluid overflow is discarded");
        return true;
    }
    public JsonObject exclusion(int index) {
        Entry entry = entries.get(index);
        if (entry.amount > 0) return null;
        return object("reason", "native_input_slot_rejected", "scope", "registered_entry", "nativeClass", ROOT + "fluids.FluidSourceRegistry",
                "condition", "TileLiquifier/TileMutagenProducer.isItemValidForSlot requires getValue(stack) > 0", "nativeValue", entry.amount,
                "item", Item.itemRegistry.getNameForObject(entry.item), "metadata", entry.meta,
                "bypassBehavior", "Direct inventory mutation bypasses slot validation: tryStart consumes one and produces no working operation");
    }
    private ItemStack anchor(Entry entry) {
        if (entry.meta != 32767) return new ItemStack(entry.item, 1, entry.meta);
        Set<Integer> overridden = new HashSet<>();
        for (Entry other : entries) if (other.item == entry.item && other.meta != 32767) overridden.add(other.meta);
        // An example anchors a rule; its NBT/count is never used as matching semantics.
        for (int meta = 0; meta < 32767; meta++) if (!overridden.contains(meta)) return new ItemStack(entry.item, 1, meta);
        throw fault("Gendustry wildcard has no bounded concrete metadata example");
    }
    private static List<Entry> snapshot(Object registry) {
        if (registry == null) throw fault("Missing Gendustry registry");
        String name = registry.getClass().getName();
        if (!name.equals(ROOT + "fluids.FluidSourceRegistry") && !name.equals(ROOT + "fluids.ProteinSources$") && !name.equals(ROOT + "fluids.MutagenSources$"))
            throw fault("Unadapted Gendustry registry implementation: " + name);
        List<Entry> result = new ArrayList<>();
        Object values = call(registry, "values");
        Object iterator = call(values, "iterator");
        while ((Boolean) call(iterator, "hasNext")) {
            Jobs.checkpoint(); Object pair = call(iterator, "next");
            Object item = call(pair, "_1");
            if (!(item instanceof Item) || Item.itemRegistry.getNameForObject(item) == null) throw fault("Unregistered Gendustry source item");
            if (ItemCallbacks.method((Item)item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                throw fault("Unadapted Gendustry source metadata callback: " + item.getClass().getName());
            Object sub = call(call(pair, "_2"), "iterator");
            while ((Boolean) call(sub, "hasNext")) {
                Jobs.checkpoint(); Object value = call(sub, "next");
                Object meta = call(value, "_1"), amount = call(value, "_2");
                if (!(meta instanceof Integer) || !(amount instanceof Integer) || (Integer)meta < 0) throw fault("Invalid Gendustry registry entry");
                result.add(new Entry((Item)item, (Integer)meta, (Integer)amount));
                if (result.size() > 262144) throw fault("Gendustry registry exceeds its budget");
            }
        }
        result.sort(Comparator.comparing((Entry entry) -> Item.itemRegistry.getNameForObject(entry.item)).thenComparingInt(entry -> entry.meta));
        return result;
    }
    private static String signature(List<Entry> entries) {
        JsonArray values = array();
        for (Entry entry : entries) values.add(object("item", Item.itemRegistry.getNameForObject(entry.item), "meta", entry.meta, "amount", entry.amount));
        return CanonicalJson.digest(values);
    }
    private static Context context(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unadapted Gendustry fluid handler");
        version("gendustry", "1.9.4-GTNH"); version("bdlib", "1.11.0-GTNH");
        boolean protein = handler.getClass().getName().endsWith("LiquifierHandler");
        Object registry = module(ROOT + "fluids." + (protein ? "ProteinSources" : "MutagenSources"));
        Object machine = module(ROOT + "machines." + (protein ? "liquifier.MachineLiquifier" : "mproducer.MachineMutagenProducer"));
        Fluid fluid = (Fluid) call(module(ROOT + "config.Fluids"), protein ? "protein" : "mutagen");
        return new Context(registry, new FluidStack(fluid, 1), (Float)call(machine, "mjPerItem"),
                (Float)call(machine, "powerUseRate"), (Float)call(machine, "activationEnergy"), (Integer)call(machine, "tankSize"));
    }
    private static Object module(String name) { return field(type(name + "$"), null, "MODULE$"); }
    private static Object call(Object target, String name) { return invoke(target.getClass(), target, name, new Class<?>[0]); }
    private static final class Entry {
        final Item item; final int meta, amount;
        Entry(Item item, int meta, int amount) { this.item = item; this.meta = meta; this.amount = amount; }
    }
    private static final class Context {
        final Object registry; final FluidStack output; final float mjPerItem, powerUseRate, activationEnergy; final int tankSize;
        Context(Object registry, FluidStack output, float mjPerItem, float powerUseRate, float activationEnergy, int tankSize) {
            this.registry = registry; this.output = output; this.mjPerItem = mjPerItem; this.powerUseRate = powerUseRate; this.activationEnergy = activationEnergy; this.tankSize = tankSize;
        }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
