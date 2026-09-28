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
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
