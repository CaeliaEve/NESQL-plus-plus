package thaumcraft.api;

import java.util.List;
import java.util.Map;

/**
 * Stub class for ThaumcraftCraftingManager.
 */
public class ThaumcraftCraftingManager {
    public static Map<String, thaumcraft.api.crafting.InfusionRecipe[]> infusionRecipes;
    public static java.util.Collection<thaumcraft.api.crafting.IRecipeArcanum> arcaneRecipes;
    public static List<thaumcraft.api.crafting.ShapedArcaneRecipe> arcaneRecipesShaped;
    public static List<thaumcraft.api.crafting.ShapelessArcaneRecipe> arcaneRecipesShapeless;

    public static List<thaumcraft.api.crafting.ShapedArcaneRecipe> getArcaneRecipes() {
        return arcaneRecipesShaped;
    }

    public static List<thaumcraft.api.crafting.ShapelessArcaneRecipe> getArcaneShapelessRecipes() {
        return arcaneRecipesShapeless;
    }

    public static List<thaumcraft.api.crafting.CrucibleRecipe> getCrucibleRecipes() {
        return null;
    }
}
