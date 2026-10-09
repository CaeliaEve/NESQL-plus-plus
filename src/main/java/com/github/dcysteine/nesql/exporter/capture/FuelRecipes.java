package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.FuelRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.JsonObject;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Fuel is an energy source, not a fixed item-to-item recipe. Keep an explicit proof instead of inventing outputs. */
final class FuelRecipes implements RegistryRecipes {
    private final TemplateRecipeHandler handler;
    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return FuelRecipeHandler.class.getName().equals(name); }
    FuelRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw new com.github.dcysteine.nesql.exporter.task.Jobs.Fault("recipe_unsupported", "Unknown fuel handler");
        this.handler = handler;
    }
    public int size() { return 0; }
    public boolean capture(int index, RecipeRow row) { return false; }
    public JsonObject handlerExclusion() {
        return object("reason", "non_recipe_energy_source", "scope", "handler", "native", "FuelRecipeHandler",
                "detail", "Fuel entries expose burn time and a dynamically cycled furnace preview; they do not define a fixed output recipe.");
    }
}
