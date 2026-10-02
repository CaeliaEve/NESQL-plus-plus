package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Et Futurum 2.6.2.25: native merge/blacklist semantics, without the NEI loader's quadratic cache. */
final class SmeltingRecipes implements RegistryRecipes {
    private static final String PREFIX = "ganymedes01.etfuturum.";
    private final FurnaceRecipeHandler handler;
    private final Object registry;
    private final List<Map.Entry<ItemStack, ItemStack>> recipes;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(PREFIX + "compat.nei.SmokerRecipeHandler") || name.equals(PREFIX + "compat.nei.BlastFurnaceRecipeHandler");
    }
    SmeltingRecipes(FurnaceRecipeHandler handler) {
        version("etfuturum", "2.6.2.25-GTNH");
        this.handler = handler;
        String kind = handler.getClass().getSimpleName().replace("RecipeHandler", "Recipes");
        registry = invoke(type(PREFIX + "recipes." + kind), null, "smelting", new Class<?>[0]);
        recipes = enumerate(registry, FurnaceRecipes.smelting().getSmeltingList());
    }
    public int size() { return recipes.size(); }
    public boolean capture(int index, RecipeRow row) { capture(handler, recipes.get(index), registry, row); return true; }

    @SuppressWarnings("unchecked")
    static List<Map.Entry<ItemStack, ItemStack>> enumerate(Object registry, Map<ItemStack, ItemStack> furnace) {
        Map<ItemStack, ItemStack> combined;
        try { combined = (Map<ItemStack, ItemStack>) type(PREFIX + "core.utils.ItemStackMap").newInstance(); }
        catch (ReflectiveOperationException error) { throw new Jobs.Fault("recipe_unsupported", "Et Futurum item map is unavailable: " + error); }
        for (Map.Entry<ItemStack, ItemStack> recipe : furnace.entrySet()) {
            Jobs.checkpoint();
            if ((Boolean) invoke(registry.getClass(), registry, "canAdd", new Class<?>[] {ItemStack.class, ItemStack.class}, recipe.getKey(), recipe.getValue())) {
                combined.put(recipe.getKey(), recipe.getValue());
            }
        }
        combined.putAll((Map<ItemStack, ItemStack>) field(registry, "smeltingList"));
        Object blacklist = field(registry, "smeltingBlacklist");
        List<Map.Entry<ItemStack, ItemStack>> result = new ArrayList<>();
        for (Map.Entry<ItemStack, ItemStack> recipe : combined.entrySet()) {
            Jobs.checkpoint();
            if (!(Boolean) invoke(blacklist.getClass(), blacklist, "contains", new Class<?>[] {ItemStack.class}, recipe.getKey())) {
                result.add(new AbstractMap.SimpleImmutableEntry<>(recipe.getKey().copy(), recipe.getValue().copy()));
            }
        }
        return result;
    }

    static void capture(FurnaceRecipeHandler handler, Map.Entry<ItemStack, ItemStack> recipe, Object registry, RecipeRow row) {
        ItemStack input = recipe.getKey().copy(), output = recipe.getValue().copy();
        if (input.getItem() == null || output.getItem() == null || output.stackSize <= 0) throw new Jobs.Fault("recipe_unsupported", "Et Futurum has an invalid smelting pair");
        input.stackSize = 1;
        FurnaceRecipeHandler.SmeltingPair cached = handler.new SmeltingPair(input.copy(), output.copy());
        List<PositionedStack> displayed = cached.getIngredients();
        if (displayed.size() != 1) throw new Jobs.Fault("slot_changed", "Et Futurum changed its smelting input layout");
        boolean wildcard = input.getItemDamage() == OreDictionary.WILDCARD_VALUE;
        row.itemInput(displayed.get(0), 0, 1, false, false, object("kind", "wildcard", "meta", wildcard, "nbt", true));
        row.itemOutput(cached.getResult(), 0, output, 10000);
        row.record.addProperty("duration", "100");
        row.property("minecraft:experience", "Experience", invoke(registry.getClass(), registry, "getSmeltingExperience", new Class<?>[] {ItemStack.class}, output.copy()));
        handler.arecipes.clear(); handler.arecipes.add(cached);
    }
}
