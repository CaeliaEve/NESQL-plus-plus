package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.BrewingRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Native brewing semantics: a precursor potion plus one catalyst produces one potion. */
final class BrewingRecipes implements RegistryRecipes {
    private final TemplateRecipeHandler handler;
    private final int size;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return BrewingRecipeHandler.class.getName().equals(name); }

    BrewingRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown brewing handler: " + handler.getClass().getName());
        this.handler = handler;
        handler.loadCraftingRecipes("brewing");
        size = handler.numRecipes();
        if (size <= 0 || size > 4096) throw fault("Brewing registry has an invalid recipe count: " + size);
    }

    public int size() { return size; }

    public boolean capture(int index, RecipeRow row) {
        List<PositionedStack> ingredients = handler.getIngredientStacks(index);
        if (ingredients == null || ingredients.size() != 2) throw fault("Brewing recipe must have exactly two inputs");
        PositionedStack catalyst = ingredients.get(0), precursor = ingredients.get(1);
        if (catalyst == null || catalyst.item == null || catalyst.items == null || catalyst.items.length == 0)
            throw fault("Brewing catalyst is empty");
        if (precursor == null || precursor.item == null || precursor.items == null || precursor.items.length == 0)
            throw fault("Brewing precursor is empty");
        row.itemInput(catalyst, 0, 1, false, true, object("kind", "wildcard", "meta", false, "nbt", true));
        row.itemInput(precursor, 1, 1, false, true, object("kind", "exact", "nbt", true));
        PositionedStack result = handler.getResultStack(index);
        if (result == null || result.item == null || result.items == null || result.items.length == 0)
            throw fault("Brewing result is empty");
        row.itemOutput(result, 0, result.item, 10000);
        return true;
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
