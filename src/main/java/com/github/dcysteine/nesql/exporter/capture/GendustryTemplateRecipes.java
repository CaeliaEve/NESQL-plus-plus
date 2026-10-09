package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;

/** Native, registry-backed adapter for Gendustry template crafting. */
final class GendustryTemplateRecipes implements RegistryRecipes {
    static final String HANDLER = "net.bdew.gendustry.nei.TemplateCraftingHandler";
    private final TemplateRecipeHandler handler;
    private final int size;

    static boolean supports(ICraftingHandler value) {
        return value != null && HANDLER.equals(value.getClass().getName());
    }

    GendustryTemplateRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unadapted Gendustry template handler");
        this.handler = handler;
        handler.arecipes.clear();
        invokeAddRecipe(handler);
        size = handler.arecipes.size();
        if (size <= 0 || size > 4096) throw fault("Gendustry template registry has no bounded recipes");
    }

    public int size() { return size; }

    public boolean capture(int index, RecipeRow row) {
        if (index < 0 || index >= size || handler.arecipes.size() != size)
            throw new Jobs.Fault("slot_changed", "Gendustry template cache changed during capture");
        java.util.List<PositionedStack> inputs = handler.getIngredientStacks(index);
        if (inputs == null || inputs.isEmpty() || inputs.size() > 32)
            throw fault("Gendustry template has invalid input count");
        int slot = 0;
        for (PositionedStack input : inputs) {
            if (input == null || input.items == null || input.items.length == 0)
                throw new Jobs.Fault("slot_missing", "Gendustry template omitted input slot " + slot);
            int amount = input.items[0] == null ? 0 : input.items[0].stackSize;
            if (amount <= 0) amount = 1;
            row.itemInput(input, slot++, amount, false, true,
                    (item, choice) -> com.github.dcysteine.nesql.exporter.source.Json.object(
                            "kind", "exact", "meta", false, "nbt", true));
        }
        PositionedStack output = handler.getResultStack(index);
        if (output == null || output.items == null || output.items.length == 0 || output.items[0] == null)
            throw new Jobs.Fault("output_missing", "Gendustry template has no native result");
        row.itemOutput(output, 0, output.items[0], 10000);
        row.property("gendustry:template", "Native template crafting", true);
        return true;
    }

    private static void invokeAddRecipe(TemplateRecipeHandler handler) {
        try {
            java.lang.reflect.Method method = handler.getClass().getMethod("addRecipe");
            method.invoke(handler);
        } catch (ReflectiveOperationException error) {
            Jobs.Fault failure = fault("Gendustry template population failed");
            failure.initCause(error);
            throw failure;
        }
    }

    private static Jobs.Fault fault(String message) {
        return new Jobs.Fault("recipe_unsupported", message);
    }
}
