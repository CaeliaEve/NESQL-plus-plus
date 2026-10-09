package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.FireworkRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Firework is a time-varying native preview, not a fixed recipe. */
final class FireworkRecipes implements RegistryRecipes {
    private final TemplateRecipeHandler handler;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return FireworkRecipeHandler.class.getName().equals(name); }
    static String exclusionReason() { return "dynamic_native_preview"; }

    FireworkRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown firework handler: " + handler.getClass().getName());
        this.handler = handler;
    }

    public int size() { return 0; }

    @Override public JsonObject handlerExclusion() {
        return object("reason", "dynamic_native_preview", "scope", "handler",
                "native", "FireworkRecipeHandler", "cycleTicks", 40,
                "detail", "The native cache changes ingredients and recomputes output NBT across its 40 tick phase; no fixed single-row projection is emitted.");
    }

    public boolean capture(int index, RecipeRow row) {
        throw fault("Firework recipes are dynamically phased and are excluded until a cycle contract exists");
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
