package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.sql.base.recipe.Recipe;

import java.util.List;
import java.util.Map;

final class ModBasedRecipeDataset {
    final List<Recipe> recipes;
    final Map<Recipe, com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe> gtRecipeMap;

    ModBasedRecipeDataset(
            List<Recipe> recipes,
            Map<Recipe, com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe> gtRecipeMap) {
        this.recipes = recipes;
        this.gtRecipeMap = gtRecipeMap;
    }
}
