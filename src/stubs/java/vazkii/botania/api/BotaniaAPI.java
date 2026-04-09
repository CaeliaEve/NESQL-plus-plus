package vazkii.botania.api;

import vazkii.botania.api.recipe.*;
import vazkii.botania.api.brew.BrewRecipe;

import java.util.List;
import java.util.Map;

/**
 * Stub class for BotaniaAPI to allow compilation.
 * At runtime, the actual Botania mod will provide this class.
 */
public class BotaniaAPI {
    // Recipe lists
    public static List<RecipeManaInfusion> manaInfusionRecipes;
    public static List<RecipePureDaisy> pureDaisyRecipes;
    public static List<RecipePetals> petalRecipes;
    public static List<RecipeRuneAltar> runeAltarRecipes;
    public static List<RecipeElvenTrade> elvenTradeRecipes;
    public static Map<String, BrewRecipe> brewRecipes;
    public static List<RecipeTerraPlate> terraPlateRecipes;
}
