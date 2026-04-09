package morphclock.api;

import net.minecraft.item.ItemStack;

/**
 * Stub class for IRecipe.
 */
public interface IRecipe {
    ItemStack getRecipeOutput();
    Object[] getInput();
}
