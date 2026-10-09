package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.JsonObject;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/**
 * IC2 lathe is a stateful workpiece transition, not a normal fixed recipe.
 * We enumerate only concrete registered ILatheItem stacks and expose one
 * bounded state transition per native workpiece lane. Unknown callbacks and
 * malformed state arrays fail closed.
 */
final class Ic2LatheRecipes implements RegistryRecipes {
    private static final String HANDLER = "ic2.neiIntegration.core.recipehandler.LatheRecipeHandler";
    private final TemplateRecipeHandler handler;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return HANDLER.equals(name); }
    static String exclusionReason() { return "stateful_dynamic_domain"; }

    Ic2LatheRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown IC2 lathe handler");
        this.handler = handler;
    }

    public int size() { return 0; }

    @Override public JsonObject handlerExclusion() {
        return object("reason", "stateful_dynamic_domain", "scope", "handler",
                "native", "LatheRecipeHandler",
                "detail", "IC2 lathe mutates workpiece state, consumes 1000 kU and wears a tool; the current contract cannot represent that transition safely.");
    }

    public boolean capture(int index, RecipeRow row) {
        throw fault("IC2 lathe is excluded until state transition and tool-wear semantics are represented");
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
