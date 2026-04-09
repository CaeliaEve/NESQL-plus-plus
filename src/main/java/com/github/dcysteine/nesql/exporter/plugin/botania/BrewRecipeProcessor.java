package com.github.dcysteine.nesql.exporter.plugin.botania;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import vazkii.botania.api.brew.BrewRecipe;
import vazkii.botania.api.BotaniaAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Brew recipes from Botania.
 */
public class BrewRecipeProcessor extends PluginHelper {
    private final RecipeType brewRecipe;

    public BrewRecipeProcessor(
            PluginExporter exporter, BotaniaRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.brewRecipe = recipeTypeHandler.getBrewRecipe();
    }

    public void process() {
        try {
            Map<String, BrewRecipe> recipeMap = null;

            // Try to access brewRecipes field - may not exist in all Botania versions
            try {
                recipeMap = BotaniaAPI.brewRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Brew recipes field not found in this Botania version, skipping Brew recipes");
                return;
            }

            if (recipeMap == null || recipeMap.isEmpty()) {
                logger.info("No Brew recipes found!");
                return;
            }

            int total = recipeMap.size();
            logger.info("Processing {} Brew recipes...", total);

            int count = 0;
            for (BrewRecipe recipe : recipeMap.values()) {
                count++;
                processRecipe(recipe);

                if (Logger.intermittentLog(count)) {
                    logger.info("Processed Brew recipe {} of {}", count, total);
                    try {
                        logger.info("Most recent recipe: {}", recipe.getKey());
                    } catch (Exception e) {
                        // Ignore key display errors
                    }
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Brew recipes!");
        } catch (Exception e) {
            logger.error("Error processing Brew recipes", e);
        }
    }

    private void processRecipe(BrewRecipe recipe) {
        try {
            RecipeBuilder builder = new RecipeBuilder(exporter, brewRecipe);

            // Add inputs - handle API differences
            try {
                for (Object input : recipe.getIngredients()) {
                    if (input == null) {
                        continue;
                    }
                    if (input instanceof net.minecraft.item.ItemStack) {
                        net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) input;
                        if (itemStack.getItem() != null) {
                            builder.addItemInput(itemStack);
                        }
                    }
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getIngredients() method not found, skipping inputs");
            }

            // Build recipe (Brew recipes don't have traditional item outputs)
            Recipe builtRecipe = builder.build();

            // Register brew key as metadata - handle API differences
            String brewKey = "unknown";
            try {
                brewKey = recipe.getKey();
            } catch (NoSuchMethodError e) {
                logger.debug("getKey() method not found, using default key 'unknown'");
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("brewKey", brewKey);
            try {
                SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("Brew", metadata)
                );
            } catch (Exception e) {
                // Ignore metadata registration errors
            }

        } catch (Exception e) {
            logger.error("Error processing individual Brew recipe", e);
        }
    }
}
