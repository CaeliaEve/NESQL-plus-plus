package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.github.dcysteine.nesql.sql.base.item.ItemStackWithProbability;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModBasedRecipeGroupingBuilder {

    private final ModBasedRecipeDtoAssembler dtoAssembler;

    ModBasedRecipeGroupingBuilder(ModBasedRecipeDtoAssembler dtoAssembler) {
        this.dtoAssembler = dtoAssembler;
    }

    Map<String, List<ModBasedRecipeExporter.RecipeDTO>> groupByOutputMod(
            ModBasedRecipeDataset dataset,
            String recipeType) {
        Logger.MOD.info("=== Grouping {} recipes by mod ===", dataset.recipes.size());

        Map<String, List<ModBasedRecipeExporter.RecipeDTO>> result = new HashMap<>();
        int processed = 0;
        int logInterval = Math.max(1, dataset.recipes.size() / 20);
        long startTime = System.currentTimeMillis();

        for (Recipe recipe : dataset.recipes) {
            processed++;

            if (processed % logInterval == 0 || processed == dataset.recipes.size()) {
                long elapsed = System.currentTimeMillis() - startTime;
                double percent = (processed * 100.0) / dataset.recipes.size();
                Logger.MOD.info("Grouping progress: {}/{} ({:.1f}%), elapsed: {}ms",
                        processed, dataset.recipes.size(), percent, elapsed);
            }

            try {
                if (recipe.getItemOutputs() == null || recipe.getItemOutputs().isEmpty()) {
                    continue;
                }

                Map.Entry<Integer, ItemStackWithProbability> firstOutput =
                        recipe.getItemOutputs().entrySet().iterator().next();
                if (firstOutput == null || firstOutput.getValue().getItem() == null) {
                    continue;
                }

                Item outputItem = firstOutput.getValue().getItem();
                String modId = outputItem.getModId();
                ModBasedRecipeExporter.RecipeDTO dto =
                        dtoAssembler.toDto(recipe, recipeType, dataset.gtRecipeMap);

                result.computeIfAbsent(modId, ignored -> new ArrayList<>()).add(dto);
            } catch (Exception e) {
                Logger.MOD.warn("Failed to group recipe during export: " + recipe.getId(), e);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        Logger.MOD.info("=== Grouping completed: {} mods, {} recipes, {}ms ===",
                result.size(), dataset.recipes.size(), elapsed);
        return result;
    }
}
