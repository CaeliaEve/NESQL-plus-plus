package com.github.dcysteine.nesql.exporter.plugin.botania;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import vazkii.botania.api.recipe.RecipePureDaisy;
import vazkii.botania.api.BotaniaAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Pure Daisy recipes from Botania.
 */
public class PureDaisyProcessor extends PluginHelper {
    private final RecipeType pureDaisy;

    public PureDaisyProcessor(
            PluginExporter exporter, BotaniaRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.pureDaisy = recipeTypeHandler.getPureDaisy();
    }

    public void process() {
        try {
            List<RecipePureDaisy> recipes = null;

            // Try to access pureDaisyRecipes field - may not exist in all Botania versions
            try {
                recipes = BotaniaAPI.pureDaisyRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Pure Daisy recipes field not found in this Botania version, skipping");
                return;
            }

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Pure Daisy recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Pure Daisy recipes...", total);

            int count = 0;
            for (RecipePureDaisy recipe : recipes) {
                count++;
                Recipe builtRecipe = processRecipe(recipe);

                if (builtRecipe != null && Logger.intermittentLog(count)) {
                    logger.info("Processed Pure Daisy recipe {} of {}", count, total);
                    if (recipe.getOutput() != null) {
                        logger.info("Most recent recipe: {}",
                                recipe.getOutput().getDisplayName());
                    }
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Pure Daisy recipes!");
        } catch (Exception e) {
            logger.error("Error processing Pure Daisy recipes", e);
        }
    }

    private Recipe processRecipe(RecipePureDaisy recipe) {
        try {
            RecipeBuilder builder = new RecipeBuilder(exporter, pureDaisy);

            // Add input - handle API differences
            try {
                Object input = recipe.getInput();
                if (input != null && input instanceof net.minecraft.item.ItemStack) {
                    net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) input;
                    if (itemStack.getItem() != null) {
                        builder.addItemInput(itemStack);
                    }
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getInput() method not found, skipping input");
            }

            // Try to get output object - Pure Daisy recipes transform blocks
            try {
                Object output = recipe.getOutput();
                if (output != null && output instanceof net.minecraft.item.ItemStack) {
                    net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) output;
                    if (itemStack.getItem() != null) {
                        builder.addItemOutput(itemStack);
                    }
                }
            } catch (NoSuchMethodError e) {
                // getOutput() method doesn't exist in this Botania version
                // Pure Daisy recipes are block-to-block transformations
                // We'll just store the input and metadata
                logger.debug("Pure Daisy recipe doesn't have getOutput() method, storing input-only recipe");
            }

            // Build recipe
            Recipe builtRecipe = builder.build();
            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Pure Daisy recipe", e);
            return null;
        }
    }
}
