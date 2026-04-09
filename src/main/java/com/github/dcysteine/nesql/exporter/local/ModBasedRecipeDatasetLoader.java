package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import jakarta.persistence.EntityManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModBasedRecipeDatasetLoader {

    private ModBasedRecipeDatasetLoader() {}

    static ModBasedRecipeDataset load(EntityManager entityManager, String recipeType) {
        Logger.MOD.info("Loading recipes from database...");
        Logger.MOD.info("=== Starting database query for {} recipes ===", recipeType);

        long startTime = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        List<Recipe> recipes = entityManager
                .createQuery("SELECT DISTINCT r FROM Recipe r " +
                             "LEFT JOIN FETCH r.recipeType rt " +
                             "LEFT JOIN FETCH r.itemOutputs io " +
                             "LEFT JOIN FETCH r.itemInputs ii " +
                             "LEFT JOIN FETCH r.fluidInputs fi " +
                             "LEFT JOIN FETCH r.fluidOutputs fo", Recipe.class)
                .getResultList();

        long endTime = System.currentTimeMillis();
        Logger.MOD.info("=== Database query completed: {} ms, {} recipes ===", endTime - startTime, recipes.size());

        Logger.MOD.info("Pre-loading GregTech metadata for all recipes...");
        long gtStartTime = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        List<com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe> gtRecipes = entityManager.createQuery(
                "SELECT gtr FROM com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe gtr",
                com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe.class)
                .getResultList();

        Map<Recipe, com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe> gtRecipeMap = new HashMap<>();
        for (com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe gtRecipe : gtRecipes) {
            if (gtRecipe.getRecipe() != null) {
                gtRecipeMap.put(gtRecipe.getRecipe(), gtRecipe);
            }
        }

        long gtEndTime = System.currentTimeMillis();
        Logger.MOD.info("GregTech metadata loaded: {} entries, {}ms", gtRecipeMap.size(), gtEndTime - gtStartTime);

        return new ModBasedRecipeDataset(recipes, gtRecipeMap);
    }
}
