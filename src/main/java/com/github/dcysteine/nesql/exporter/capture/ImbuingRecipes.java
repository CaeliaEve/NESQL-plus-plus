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
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** RandomThings 2.6.6 healthy imbuing runs, with a proof that native HashMap matching is unambiguous. */
final class ImbuingRecipes implements RegistryRecipes {
    private static final String HANDLER = "lumien.randomthings.Handler.ModCompability.NEI.ImbuingStationRecipeHandler";
    private static final String RECIPE = "lumien.randomthings.Handler.ImbuingStation.ImbuingRecipe";
    private static final String REGISTRY = "lumien.randomthings.Handler.ImbuingStation.ImbuingRecipeHandler";
    private static final String SETTINGS = "lumien.randomthings.Configuration.Settings";
    private final TemplateRecipeHandler handler;
    private final List<?> registry;
    private final List<Entry> entries = new ArrayList<>();
    private final float threshold;
    private final int ticks;
    private boolean nativeRegistry;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    ImbuingRecipes(TemplateRecipeHandler handler) { this(handler, registry(), (Float) field(type(SETTINGS), null, "IMBUING_LENGTH")); nativeRegistry = true; }
    private static List<?> registry() {
        version("RandomThings", "2.6.6");
        return (List<?>) field(type(REGISTRY), null, "imbuingRecipes");
    }
    ImbuingRecipes(TemplateRecipeHandler handler, List<?> registry, float threshold) {
        if (!supports(handler) || registry == null || registry.size() > 4096) throw fault("Invalid imbuing registry");
        this.handler = handler; this.registry = registry; this.threshold = threshold; ticks = duration(threshold);
        Work work = new Work();
        for (Object raw : registry) { Jobs.checkpoint(); entries.add(new Entry(raw, work)); }
        for (int i = 0; i < entries.size(); i++) for (int j = 0; j < i; j++) {
            Jobs.checkpoint(); Entry a = entries.get(i), b = entries.get(j);
            work.step();
            if (a.ingredients.size() == b.ingredients.size() && a.base.overlaps(b.base, work) && allocationOverlap(a.ingredients, b.ingredients, 0, 0, work))
                throw fault("Imbuing recipes have overlapping first-match inventory predicates");
        }
    }
    public int size() { return entries.size(); }
    public void verify() {
        if (nativeRegistry && (field(type(REGISTRY), null, "imbuingRecipes") != registry
                || Float.floatToIntBits((Float) field(type(SETTINGS), null, "IMBUING_LENGTH")) != Float.floatToIntBits(threshold))
                || registry.size() != entries.size()) changed();
        Work work = new Work();
        for (int i = 0; i < entries.size(); i++) {
            Jobs.checkpoint(); Entry entry = entries.get(i);
            if (registry.get(i) != entry.source || !entry.signature.equals(new Entry(registry.get(i), work).signature)) changed();
        }
    }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = entries.get(index);
        if (!entry.signature.equals(new Entry(entry.source, new Work()).signature)) changed();
        List<PositionedStack> reagents = new ArrayList<>();
        int[][] positions = {{72, 3}, {27, 48}, {72, 93}};
        ArrayList<ItemStack> nativeInputs = new ArrayList<>();
        for (int slot = 0; slot < entry.ingredients.size(); slot++) {
            Group group = entry.ingredients.get(slot); List<RecipeRow.Ingredient> choices = group.choices();
            PositionedStack position = new PositionedStack(choices.stream().map(choice -> choice.item.copy()).toArray(ItemStack[]::new), positions[slot][0], positions[slot][1], false);
            row.itemInput(position, slot, choices, false); reagents.add(position); nativeInputs.add(choices.get(0).item.copy());
        }
        List<RecipeRow.Ingredient> base = entry.base.choices();
        PositionedStack baseDisplay = new PositionedStack(base.stream().map(choice -> choice.item.copy()).toArray(ItemStack[]::new), 72, 48, false);
        row.itemInput(baseDisplay, 3, base, false);
        PositionedStack result = new PositionedStack(entry.output.copy(), 117, 48, false);
        row.itemOutput(result, 0, entry.output.copy(), 10000);
        row.record.addProperty("duration", Integer.toString(ticks));
        row.property("randomthings:threshold", "Configured imbuing length", threshold);
        row.property("randomthings:allocation", "Native input slots", "Reagents occupy distinct slots among 0..2 in any order; the item to imbue occupies slot 3");
        row.property("randomthings:emptySlots", "Required empty reagent slots", 3 - entry.ingredients.size());
        row.property("randomthings:execution", "Native execution conditions", "Initially empty output slot; stable valid inputs until completion; one completed cycle from a reset progress counter");
        TemplateRecipeHandler.CachedRecipe cache = (TemplateRecipeHandler.CachedRecipe) TinkerRecipes.construct(type(HANDLER + "$CachedImbuingRecipe"),
                new Class<?>[]{handler.getClass(), ArrayList.class, ItemStack.class, ItemStack.class}, handler, nativeInputs, base.get(0).item.copy(), entry.output.copy());
        @SuppressWarnings("unchecked") List<PositionedStack> cacheInputs = (List<PositionedStack>) field(cache, "ingredients");
        cacheInputs.clear(); cacheInputs.addAll(reagents);
        try {
            java.lang.reflect.Field field = cache.getClass().getDeclaredField("toImbue"); field.setAccessible(true); field.set(cache, baseDisplay);
        } catch (ReflectiveOperationException error) { throw fault("Imbuing cache binding changed: " + error); }
        handler.arecipes.clear(); handler.arecipes.add(cache);
        return true;
    }
    static int[][] progressBars() { return new int[][]{{74,22,176,0,12,26,12,1},{46,50,189,0,26,12,12,0},{74,65,176,24,12,24,12,3},{91,48,189,13,24,16,200,0}}; }
    private static int duration(float threshold) {
        if (!Float.isFinite(threshold) || threshold > (float) Integer.MAX_VALUE) throw fault("Imbuing timer cannot complete within a native signed counter cycle");
        // Native compares an int counter after conversion to float. Math.ceil is not exact above 2^24.
        long low = 1, high = Integer.MAX_VALUE;
        while (low < high) {
            long middle = low + (high - low) / 2;
            if ((float) (int) middle >= threshold) high = middle; else low = middle + 1;
        }
        return (int) low;
    }
    private static boolean allocationOverlap(List<Group> a, List<Group> b, int at, int used, Work work) {
        work.step();
        if (at == a.size()) return true;
        for (int i = 0; i < b.size(); i++) if ((used & 1 << i) == 0 && a.get(at).overlaps(b.get(i), work) && allocationOverlap(a, b, at + 1, used | 1 << i, work)) return true;
        return false;
    }
    private static final class Work {
        int remaining = 1048576;
        void step() {
            if (--remaining < 0) throw fault("Imbuing matching proof exceeds its comparison budget");
            if ((remaining & 1023) == 0) Jobs.checkpoint();
        }
    }
    private static final class Entry {
        final Object source; final Group base; final List<Group> ingredients = new ArrayList<>();
        final ItemStack output; final String signature;
        Entry(Object source, Work work) {
            this.source = source;
            if (source == null || !source.getClass().getName().equals(RECIPE)) throw fault("Unadapted imbuing recipe callback");
            base = new Group((ItemStack) field(source, "toImbue"), work);
            List<?> raw = (List<?>) field(source, "ingredients");
            if (raw == null || raw.size() > 3) throw fault("Imbuing ingredient count exceeds the three physical slots");
            JsonArray inputs = new JsonArray();
            for (Object value : raw) {
                if (!(value instanceof ItemStack)) throw fault("Invalid imbuing ingredient");
                Group group = new Group((ItemStack) value, work);
                for (Group previous : ingredients) if (group.overlaps(previous, work))
                    throw fault("Imbuing native HashMap may reuse one stack for overlapping requirements");
                ingredients.add(group); inputs.add(value(group.signature));
            }
            ItemStack result = (ItemStack) field(source, "result");
            if (result == null || result.getItem() == null || result.stackSize <= 0) throw fault("Invalid imbuing output");
            output = result.copy(); output.stackSize = Math.min(output.stackSize, 64);
            signature = CanonicalJson.digest(object("base", base.signature, "ingredients", inputs, "output", stackValue(result)));
        }
    }
    private static final class Group {
        final List<Pattern> patterns = new ArrayList<>(); final String signature;
        Group(ItemStack source, Work work) {
            audit(source); patterns.add(new Pattern(source, false, false));
            JsonArray ores = new JsonArray(); int count = 0;
            for (int ore : OreDictionary.getOreIDs(source)) {
                JsonArray entries = new JsonArray();
                for (ItemStack item : OreDictionary.getOres(OreDictionary.getOreName(ore))) {
                    work.step();
                    if (++count > 65536) throw fault("Imbuing ore alternatives exceed their budget");
                    audit(item); patterns.add(new Pattern(item, item.getItemDamage() == 32767, true)); entries.add(stackValue(item));
                }
                ores.add(object("ore", OreDictionary.getOreName(ore), "entries", entries));
            }
            signature = CanonicalJson.digest(object("direct", stackValue(source), "ores", ores));
            // A shared-ore predicate often fully includes the direct exact-NBT alternative.
            for (int i = patterns.size() - 1; i >= 0; i--) {
                Pattern candidate = patterns.get(i); boolean redundant = false;
                for (int j = 0; j < patterns.size(); j++) {
                    work.step();
                    if (i != j && patterns.get(j).covers(candidate)
                            && (j < i || !candidate.covers(patterns.get(j)))) { redundant = true; break; }
                }
                if (redundant) patterns.remove(i);
            }
        }
        boolean overlaps(Group other, Work work) {
            for (Pattern a : patterns) for (Pattern b : other.patterns) {
                work.step(); if (a.overlaps(b)) return true;
            }
            return false;
        }
        List<RecipeRow.Ingredient> choices() {
            List<RecipeRow.Ingredient> result = new ArrayList<>();
            for (Pattern pattern : patterns) result.add(new RecipeRow.Ingredient(pattern.example(), 1, false, pattern.rule()));
            return result;
        }
    }
    private static final class Pattern {
        final ItemStack item; final boolean meta, nbt;
        Pattern(ItemStack source, boolean meta, boolean nbt) {
            item = source.copy(); item.stackSize = 1; this.meta = meta; this.nbt = nbt;
            if (nbt) item.setTagCompound(null);
        }
        boolean covers(Pattern other) { return item.getItem() == other.item.getItem() && (meta || !other.meta && item.getItemDamage() == other.item.getItemDamage())
                && (nbt || !other.nbt && ItemStack.areItemStackTagsEqual(item, other.item)); }
        boolean overlaps(Pattern other) { return item.getItem() == other.item.getItem() && (meta || other.meta || item.getItemDamage() == other.item.getItemDamage())
                && (nbt || other.nbt || ItemStack.areItemStackTagsEqual(item, other.item)); }
        JsonObject rule() { return meta || nbt ? object("kind", "wildcard", "meta", meta, "nbt", nbt) : object("kind", "exact"); }
        ItemStack example() {
            if (!meta) {
                if (item.getItemDamage() == 32767) throw fault("Literal imbuing wildcard metadata has no supported concrete item display");
                return item.copy();
            }
            ItemStack[] samples = new PositionedStack(item.copy(), 0, 0, true).items;
            if (samples == null || samples.length == 0 || samples.length > 65536) throw fault("Imbuing wildcard lacks a bounded native display");
            for (ItemStack sample : samples) if (sample != null && sample.getItem() == item.getItem() && sample.getItemDamage() != 32767) {
                ItemStack copy = sample.copy(); copy.stackSize = 1; copy.setTagCompound(null); return copy;
            }
            throw fault("Imbuing wildcard lacks a concrete native display");
        }
    }
    private static void audit(ItemStack item) {
        if (item == null || item.getItem() == null) throw fault("Invalid imbuing input");
        if (ItemCallbacks.method(item.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
            throw fault("Imbuing input overrides native metadata matching");
    }
    private static JsonObject stackValue(ItemStack item) { return object("item", Item.itemRegistry.getNameForObject(item.getItem()), "meta", Items.feather.getDamage(item), "amount", item.stackSize, "nbt", TypedNbt.encode(item.getTagCompound())); }
    private static void changed() { throw new Jobs.Fault("recipe_changed", "Imbuing native registry or timer changed during capture"); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
