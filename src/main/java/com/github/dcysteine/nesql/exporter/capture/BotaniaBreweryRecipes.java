package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Botania brewery: a fixed brew table with an explicit vial/container slot. */
final class BotaniaBreweryRecipes implements RegistryRecipes {
    private static final String HANDLER = "vazkii.botania.client.integration.nei.recipe.RecipeHandlerBrewery";
    private final TemplateRecipeHandler handler;
    private final int size;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return HANDLER.equals(name); }

    BotaniaBreweryRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown Botania brewery handler");
        this.handler = handler;
        handler.loadCraftingRecipes("botania.brewery");
        size = handler.numRecipes();
        if (size <= 0 || size > 65536) throw fault("Botania brewery registry has an invalid size: " + size);
    }

    public int size() { return size; }

    public boolean capture(int index, RecipeRow row) {
        List<PositionedStack> inputs = handler.getIngredientStacks(index);
        if (inputs == null || inputs.size() < 2 || inputs.size() > 9) throw fault("Botania brewery recipe has invalid inputs");
        int slot = 0;
        for (PositionedStack input : inputs) {
            if (input == null || input.item == null || input.items == null || input.items.length == 0) throw fault("Botania brewery input is empty");
            boolean wildcard = input.items.length != 1;
            for (ItemStack candidate : input.items) if (candidate == null || candidate.getItemDamage() == Short.MAX_VALUE) wildcard = true;
            boolean vial = input.relx == 39 && input.rely == 42;
            row.itemInput(input, slot++, Math.max(1, input.item.stackSize), vial, false,
                    object("kind", wildcard ? "wildcard" : "exact", "meta", wildcard, "nbt", true));
        }
        PositionedStack output = handler.getResultStack(index);
        if (output == null || output.item == null || output.items == null || output.items.length == 0) throw fault("Botania brewery recipe has no output");
        row.itemOutput(output, 0, output.item, 10000);
        return true;
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
