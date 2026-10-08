package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native GT++ decay chest conversion, restricted to fresh inputs with no NBT. */
final class DecayableRecipes implements RegistryRecipes {
    private static final String HANDLER = "gtPlusPlus.nei.DecayableRecipeHandler";
    private static final String RECIPE = "gtPlusPlus.core.handler.Recipes.DecayableRecipe";
    private static final String DUST = "gtPlusPlus.core.item.materials.DustDecayable";
    private final TemplateRecipeHandler handler;
    private final List<Entry> entries;
    private final String signature;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    DecayableRecipes(TemplateRecipeHandler handler) {
        version("gregtech_nh", "5.09.51.482");
        if (!supports(handler)) throw fault("Unadapted GT++ decay handler");
        this.handler = handler; entries = registry(); signature = signature(entries);
    }
    public int size() { return entries.size(); }
    public void verify() {
        if (!signature.equals(signature(registry()))) throw new Jobs.Fault("environment_changed", "GT++ decay registry, item threshold or shared result template changed");
    }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = entries.get(index);
        if (!entry.valid) return false;
        // The real tile mutates its shared result template to size one. A capture
        // must never call that path; both native display stacks below are owned.
        TemplateRecipeHandler.CachedRecipe cached = (TemplateRecipeHandler.CachedRecipe)TinkerRecipes.construct(
                type(HANDLER + "$DecayableRecipeNEI"),
                new Class<?>[]{handler.getClass(), ItemStack.class, ItemStack.class, int.class},
                handler, entry.input.copy(), entry.displayOutput.copy(), entry.displayedTicks);
        List<PositionedStack> inputs = cached.getIngredients(); PositionedStack output = cached.getResult();
        if (inputs.size() != 1 || inputs.get(0).relx != 93 || inputs.get(0).rely != 24
                || output == null || output.relx != 142 || output.rely != 42)
            throw new Jobs.Fault("slot_changed", "GT++ native decay layout changed");
        ItemStack anchor = new ItemStack(entry.input.getItem());
        // A root compound, even an empty one, follows another native state path.
        // Malformed TickableItem tags can throw and never produce an output.
        row.itemInput(inputs.get(0), 0, Collections.singletonList(new RecipeRow.Ingredient(anchor, 1, false,
                object("kind", "wildcard", "meta", true, "nbt", false))), false);
        row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().add("consume", object("kind", "stack"));
        ItemStack result = entry.result.copy(); result.stackSize = 1;
        row.itemOutput(output, 0, result, 10000);
        row.property("decay:maxTicks", "Native item decay threshold", entry.maxTicks);
        row.property("decay:displayedTicks", "Original NEI displayed time", entry.displayedTicks);
        row.property("decay:freshBatches", "Batches required for fresh untagged input", (entry.maxTicks + 21L) / 20L);
        row.property("decay:batchLimit", "Maximum native progress steps per batch", 20);
        row.property("decay:inventoryLimit", "Normal decay chest slot insertion limit", 1);
        row.property("decay:scope", "Supported input state", "Fresh dust with no root NBT only; arbitrary metadata is accepted. Saved progress, empty root NBT and malformed NBT variants are outside this conversion contract.");
        row.property("decay:scheduler", "Native batch scheduling", "Loaded server tile updates increment a signed Java int tickCount, then run a batch when tickCount % 20 == 0. Normal batch spacing is 20 updates; signed-int wrap produces a 16-update spacing. Initial phase, unloading and lag prevent a fixed duration.");
        row.property("decay:progress", "Fresh input state transitions", "The first batch creates TickableItem with Tick=0L, maxTick=(long)maxTicks and isActive=true. Each of at most 20 steps reads isTicking before tickItemTag. The item threshold, not the stored maxTick tag, governs deactivation; completion waits until both the last pre-step ticking flag and step result are false. Fresh input therefore needs ceil((maxTicks+2)/20) batches.");
        row.property("decay:replacement", "Native slot replacement", "Completion replaces the entire stored input stack with exactly one copied result, without crafting container returns. Ordinary insertion clamps to one; native NBT inventory loading preserves larger stored counts. Dust onUpdate does not advance decay in ordinary player inventory.");
        handler.arecipes.clear(); handler.arecipes.add(cached); return true;
    }
    public JsonObject exclusion(int index) {
        Entry entry = entries.get(index);
        return entry.valid ? null : object("reason", "native_recipe_invalid", "scope", "registered_entry", "nativeClass", RECIPE,
                "condition", "DecayableRecipe.isValid requires mTime > 0 and non-null mInput and mOutput",
                "displayedTicks", entry.displayedTicks, "inputPresent", entry.input != null, "outputPresent", entry.displayOutput != null);
    }
    private static List<Entry> registry() {
        Object source = field(type(RECIPE), null, "mRecipes");
        if (source == null || source.getClass() != ArrayList.class) throw fault("Unadapted GT++ decay registry implementation");
        List<Entry> entries = new ArrayList<>();
        for (Object recipe : (ArrayList<?>)source) {
            Jobs.checkpoint();
            if (entries.size() == 262144) throw fault("GT++ decay registry exceeds its budget");
            entries.add(new Entry(recipe));
        }
        return entries;
    }
    private static final class Entry {
        final int displayedTicks, maxTicks;
        final boolean valid;
        final ItemStack input, displayOutput, result;
        final String signature;
        Entry(Object recipe) {
            if (recipe == null || !recipe.getClass().getName().equals(RECIPE)) throw fault("Unadapted GT++ decay recipe override");
            displayedTicks = (Integer)field(recipe, "mTime");
            input = owned((ItemStack)field(recipe, "mInput")); displayOutput = owned((ItemStack)field(recipe, "mOutput"));
            valid = displayedTicks > 0 && input != null && displayOutput != null;
            if (valid) {
                Item dust = input.getItem();
                // Exact ownership guards getMaxTicks, createNBT, getDecayResult,
                // onUpdate and all inherited tick behavior before any callback.
                if (!dust.getClass().getName().equals(DUST)) throw fault("Unadapted GT++ decay item or callback override: " + dust.getClass().getName());
                maxTicks = (Integer)field(dust, "maxTicks");
                if (maxTicks <= 0) throw fault("GT++ decay item has a nonpositive native threshold");
                result = owned((ItemStack)field(dust, "turnsIntoItem"));
                if (result == null || result.getItem() != displayOutput.getItem()
                        || Items.feather.getDamage(result) != Items.feather.getDamage(displayOutput)
                        || !ItemStack.areItemStackTagsEqual(result, displayOutput))
                    throw fault("GT++ registered decay output differs from the native item result");
            } else { maxTicks = 0; result = null; }
            signature = CanonicalJson.digest(object("displayedTicks", displayedTicks, "input", stack(input),
                    "displayOutput", stack(displayOutput), "maxTicks", maxTicks, "nativeResult", stack(result)));
        }
    }
    private static ItemStack owned(ItemStack stack) {
        if (stack == null) return null;
        if (stack.getItem() == null || Item.itemRegistry.getNameForObject(stack.getItem()) == null) throw fault("Unregistered GT++ decay stack");
        return stack.copy();
    }
    private static JsonObject stack(ItemStack stack) {
        return stack == null ? null : object("item", Item.itemRegistry.getNameForObject(stack.getItem()),
                "metadata", Items.feather.getDamage(stack), "amount", stack.stackSize, "nbt", TypedNbt.encode(stack.getTagCompound()));
    }
    private static String signature(List<Entry> entries) {
        JsonArray result = array(); for (Entry entry : entries) result.add(value(entry.signature)); return CanonicalJson.digest(result);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
