package WayofTime.alchemicalWizardry;

import net.minecraft.item.ItemStack;
import java.util.Map;

/**
 * Stub class for AlchemyWizardryRecipes.
 */
public class AlchemyWizardryRecipes {
    public static Map<ItemStack, ItemStack> altarRecipes;
    public static Map<ItemStack, ItemStack> arrayRecipes;
    public static Map<ItemStack, ItemStack> sacrificialRecipes;
    public static Map<ItemStack, ItemStack> tartarForgeRecipes;
    public static Map<ItemStack, ItemStack> weakActivationRecipes;

    public static int getTierOfRecipe(ItemStack input) {
        return 0;
    }

    public static int getBloodRequiredForRecipe(ItemStack input, int tier) {
        return 0;
    }

    public static int getArrayBloodRequired(ItemStack input) {
        return 0;
    }

    public static int getSacrificialLPRequired(ItemStack input) {
        return 0;
    }

    public static int getTartarRequiredForRecipe(ItemStack input) {
        return 0;
    }
}
