package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/**
 * Fixed Botania recipe tables whose NEI cache is the complete native projection.
 * Knowledge-gated and world-state domains remain separate adapters.
 */
final class BotaniaStaticRecipes implements RegistryRecipes {
    private static final String PURE = "vazkii.botania.client.integration.nei.recipe.RecipeHandlerPureDaisy";
    private static final String ELVEN = "vazkii.botania.client.integration.nei.recipe.RecipeHandlerElvenTrade";
    private static final String PETAL = "vazkii.botania.client.integration.nei.recipe.RecipeHandlerPetalApothecary";
    private final TemplateRecipeHandler handler;
    private final String outputId;
    private final int size;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return PURE.equals(name) || ELVEN.equals(name) || PETAL.equals(name); }

    BotaniaStaticRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown Botania static handler: " + handler.getClass().getName());
        this.handler = handler;
        outputId = PURE.equals(handler.getClass().getName()) ? "botania.pureDaisy"
                : PETAL.equals(handler.getClass().getName()) ? "botania.petalApothecary" : "botania.elvenTrade";
        handler.loadCraftingRecipes(outputId);
        size = handler.numRecipes();
        if (size <= 0 || size > 65536) throw fault("Botania static registry has an invalid size: " + size);
    }

    public int size() { return size; }

    public boolean capture(int index, RecipeRow row) {
        List<PositionedStack> inputs = handler.getIngredientStacks(index);
        if (inputs == null || inputs.isEmpty() || inputs.size() > 9) throw fault("Botania static recipe has invalid inputs");
        int slot = 0;
        for (PositionedStack input : inputs) {
            if (input == null || input.item == null || input.items == null || input.items.length == 0) throw fault("Botania static input is empty");
            boolean wildcard = input.items.length != 1;
            for (ItemStack candidate : input.items) if (candidate == null || candidate.getItemDamage() == Short.MAX_VALUE) wildcard = true;
            boolean catalyst = (PURE.equals(handler.getClass().getName()) && slot == 0)
                    || (PETAL.equals(handler.getClass().getName()) && input.relx == 73 && input.rely == 55);
            row.itemInput(input, slot++, Math.max(1, input.item.stackSize), catalyst, false,
                    object("kind", wildcard ? "wildcard" : "exact", "meta", wildcard, "nbt", true));
        }
        PositionedStack result = handler.getResultStack(index);
        if (result == null || result.item == null || result.items == null || result.items.length == 0) throw fault("Botania static recipe has no result");
        row.itemOutput(result, 0, result.item, 10000);
        return true;
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
