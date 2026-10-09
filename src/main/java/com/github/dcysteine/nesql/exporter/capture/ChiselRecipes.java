package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Chisel groups are fixed one-input to many-output native carving tables. */
final class ChiselRecipes implements RegistryRecipes {
    private static final String HANDLER = "team.chisel.compat.nei.RecipeHandlerChisel";
    private final TemplateRecipeHandler handler;
    private final int size;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return HANDLER.equals(name); }

    ChiselRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown Chisel handler");
        this.handler = handler;
        handler.loadCraftingRecipes("chisel2.chisel");
        size = handler.numRecipes();
        if (size <= 0 || size > 65536) throw fault("Chisel registry has an invalid size: " + size);
    }
    public int size() { return size; }

    public boolean capture(int index, RecipeRow row) {
        List<PositionedStack> inputs = handler.getIngredientStacks(index);
        if (inputs == null || inputs.size() != 1 || inputs.get(0) == null || inputs.get(0).item == null) throw fault("Chisel recipe must have one input");
        PositionedStack input = inputs.get(0);
        if (input.items == null || input.items.length == 0) throw fault("Chisel input has no candidates");
        boolean wildcard = input.items.length != 1;
        for (ItemStack candidate : input.items) if (candidate == null || candidate.getItemDamage() == Short.MAX_VALUE) wildcard = true;
        row.itemInput(input, 0, Math.max(1, input.item.stackSize), false, false,
                object("kind", wildcard ? "wildcard" : "exact", "meta", wildcard, "nbt", true));
        List<PositionedStack> outputs = handler.getOtherStacks(index);
        if (outputs == null || outputs.isEmpty() || outputs.size() > 45) throw fault("Chisel recipe has invalid outputs");
        int slot = 0;
        for (PositionedStack output : outputs) {
            if (output == null || output.item == null || output.items == null || output.items.length == 0) throw fault("Chisel output is empty");
            row.itemOutput(output, slot++, output.item, 10000);
        }
        return true;
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
