package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** AdvancedSolarPanel's ordered molecular transformer registry; energy is per operation. */
final class SolarRecipes implements RegistryRecipes {
    private static final String HANDLER = "advsolar.client.nei.MTRecipeHandler";
    private final TemplateRecipeHandler handler;
    private final List<Entry> entries = new ArrayList<>();

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    SolarRecipes(TemplateRecipeHandler handler) { this(handler, registry()); }
    private static List<?> registry() {
        version("AdvancedSolarPanel", "1.7.10-3.5.1");
        return (List<?>) field(type("advsolar.utils.MTRecipeManager"), null, "transformerRecipes");
    }
    SolarRecipes(TemplateRecipeHandler handler, List<?> recipes) {
        if (!supports(handler)) throw fault("Unknown molecular transformer handler");
        if (recipes == null || recipes.size() > 262144) throw fault("Invalid molecular transformer registry");
        this.handler = handler;
        Map<Item, Set<Integer>> seen = new IdentityHashMap<>();
        for (Object recipe : recipes) {
            Jobs.checkpoint();
            if (recipe == null || !recipe.getClass().getName().equals("advsolar.utils.MTRecipeRecord"))
                throw fault("Unknown molecular transformer recipe implementation");
            Entry entry = new Entry(recipe);
            // canSmelt returns false, rather than trying the next row, if the first
            // item/meta match lacks quantity or output space. Later rows cannot win.
            entry.shadowed = !seen.computeIfAbsent(entry.input.getItem(), ignored -> new HashSet<>()).add(entry.input.getItemDamage());
            entries.add(entry);
        }
    }
    public int size() { return entries.size(); }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = entries.get(index);
        if (entry.shadowed) return false;
        if (entry.input.stackSize < 0 || entry.output.stackSize <= 0 || entry.energy < 0)
            throw fault("Molecular transformer has a negative input/energy or nonpositive result");
        if (entry.input.stackSize > 64) throw fault("Molecular transformer input exceeds normal slot capacity");
        PositionedStack input = new PositionedStack(entry.input.copy(), 12, 8, false);
        PositionedStack output = new PositionedStack(entry.output.copy(), 12, 43, false);
        row.itemInput(input, 0, Math.max(1, entry.input.stackSize), entry.input.stackSize == 0, false,
                object("kind", "wildcard", "meta", false, "nbt", true));
        row.itemOutput(output, 0, entry.output.copy(), 10000);
        row.property("advancedsolar:energyPerOperation", "Energy per operation (EU)", entry.energy);
        row.property("advancedsolar:processing", "Processing", "Input is consumed when work starts; duration depends on supplied energy; output must have room");
        handler.arecipes.clear();
        handler.arecipes.add((TemplateRecipeHandler.CachedRecipe) TinkerRecipes.construct(type(HANDLER + "$CachedMTRecipe"),
                new Class<?>[] {handler.getClass(), ItemStack.class, ItemStack.class, int.class},
                handler, entry.input.copy(), entry.output.copy(), entry.energy));
        return true;
    }
    private static final class Entry {
        final ItemStack input, output; final int energy; boolean shadowed;
        Entry(Object recipe) {
            ItemStack source = (ItemStack) field(recipe, "inputStack"), result = (ItemStack) field(recipe, "outputStack");
            if (source == null || source.getItem() == null || result == null || result.getItem() == null || source.getItemDamage() < 0)
                throw fault("Invalid molecular transformer item reference");
            try {
                if (source.getItem().getClass().getMethod("getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                    throw fault("Molecular transformer input uses a custom metadata getter");
            } catch (NoSuchMethodException error) { throw fault("Missing native item metadata getter"); }
            input = source.copy(); output = result.copy(); energy = (Integer) field(recipe, "energyPerOperation");
        }
    }
    static int[][] progressBars() { return new int[][] {{15, 29, 177, 3, 10, 9, 20, 1}}; }
    static void scene(TemplateRecipeHandler handler, Runnable draw) {
        try {
            java.lang.reflect.Field clock = handler.getClass().getField("ticks"); int previous = clock.getInt(handler);
            try { clock.setInt(handler, 0); draw.run(); } finally { clock.setInt(handler, previous); }
        } catch (ReflectiveOperationException error) { throw fault("Native molecular transformer clock is unavailable: " + error); }
    }
    static void draw(TemplateRecipeHandler handler) {
        scene(handler, () -> { handler.drawBackground(0); handler.drawExtras(0); });
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
