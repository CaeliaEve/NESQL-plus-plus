package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.ShapedRecipeHandler;
import codechicken.nei.recipe.ShapelessRecipeHandler;

/** A subclass does not inherit verified semantics merely by inheriting a NEI UI. */
public final class HandlersTest {
    public static void run() {
        require(Recipes.adapter(new FurnaceRecipeHandler()) == Recipes.Adapter.FURNACE, "Furnace loader disappeared");
        require(Recipes.adapter(new ShapedRecipeHandler()) == Recipes.Adapter.SHAPED, "Shaped loader disappeared");
        require(Recipes.adapter(new ShapelessRecipeHandler()) == Recipes.Adapter.SHAPELESS, "Shapeless loader disappeared");
        require(Recipes.adapter(new FurnaceRecipeHandler() {}) == null, "Unverified furnace subclass claimed support");
        require(Recipes.adapter(new ShapedRecipeHandler() {}) == null, "Unverified shaped subclass claimed support");
        require(Recipes.adapter(new ShapelessRecipeHandler() {}) == null, "Unverified shapeless subclass claimed support");
        require(BrewingRecipes.supportsClass("codechicken.nei.recipe.BrewingRecipeHandler"), "Brewing handler was not routed to its dedicated adapter");
        require(FireworkRecipes.supportsClass("codechicken.nei.recipe.FireworkRecipeHandler"), "Firework handler was not routed to its dedicated adapter");
        require(!FireworkRecipes.supportsClass("codechicken.nei.recipe.FuelRecipeHandler"), "Fuel handler claimed firework semantics");
        require(ProjectRedRecipes.supportsClass("mrtjp.projectred.core.libmc.recipe.PRShapedRecipeHandler"), "ProjectRed shaped handler was not routed");
        require(ProjectRedRecipes.supportsClass("mrtjp.projectred.core.libmc.recipe.PRShapelessRecipeHandler"), "ProjectRed shapeless handler was not routed");
        require(Ic2AdvancedRecipes.supportsClass("ic2.neiIntegration.core.recipehandler.AdvRecipeHandler"), "IC2 advanced shaped handler was not routed");
        require(Ic2AdvancedRecipes.supportsClass("ic2.neiIntegration.core.recipehandler.AdvShapelessRecipeHandler"), "IC2 advanced shapeless handler was not routed");
        require(Ic2LatheRecipes.supportsClass("ic2.neiIntegration.core.recipehandler.LatheRecipeHandler"), "IC2 lathe handler was not routed");
        require(ProjectBlueRecipes.supportsClass("gcewing.projectblue.nei.NEIRecipeHandler"), "ProjectBlue handler was not routed");
        require(FuelRecipes.supportsClass("codechicken.nei.recipe.FuelRecipeHandler"), "Fuel handler did not get an explicit dedicated module");
        require(BotaniaStaticRecipes.supportsClass("vazkii.botania.client.integration.nei.recipe.RecipeHandlerPureDaisy"), "Botania pure daisy handler was not routed");
        require(BotaniaStaticRecipes.supportsClass("vazkii.botania.client.integration.nei.recipe.RecipeHandlerElvenTrade"), "Botania elven trade handler was not routed");
        require(BotaniaStaticRecipes.supportsClass("vazkii.botania.client.integration.nei.recipe.RecipeHandlerPetalApothecary"), "Botania petal handler was not routed");
        require(BotaniaBreweryRecipes.supportsClass("vazkii.botania.client.integration.nei.recipe.RecipeHandlerBrewery"), "Botania brewery handler was not routed");
        require(ChiselRecipes.supportsClass("team.chisel.compat.nei.RecipeHandlerChisel"), "Chisel handler was not routed");
        require("dynamic_native_preview".equals(FireworkRecipes.exclusionReason()), "Firework must remain excluded until its dynamic cycle is modelled");
        require("stateful_dynamic_domain".equals(Ic2LatheRecipes.exclusionReason()), "IC2 lathe must remain excluded until state transitions are modelled");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
