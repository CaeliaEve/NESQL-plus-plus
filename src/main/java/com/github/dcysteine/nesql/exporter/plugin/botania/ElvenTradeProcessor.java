package com.github.dcysteine.nesql.exporter.plugin.botania;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import vazkii.botania.api.recipe.RecipeElvenTrade;
import vazkii.botania.api.BotaniaAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Elven Trade recipes from Botania.
 */
public class ElvenTradeProcessor extends PluginHelper {
    private final RecipeType elvenTrade;

    public ElvenTradeProcessor(
            PluginExporter exporter, BotaniaRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.elvenTrade = recipeTypeHandler.getElvenTrade();
    }

    public void process() {
        try {
            List<RecipeElvenTrade> recipes = null;

            // Try to access elvenTradeRecipes field - may not exist in all Botania versions
            try {
                recipes = BotaniaAPI.elvenTradeRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Elven Trade recipes field not found in this Botania version, skipping");
                return;
            }

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Elven Trade recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Elven Trade recipes...", total);

            int count = 0;
            for (RecipeElvenTrade recipe : recipes) {
                count++;
                Recipe builtRecipe = processRecipe(recipe);

                if (builtRecipe != null && Logger.intermittentLog(count)) {
                    logger.info("Processed Elven Trade recipe {} of {}", count, total);
                    try {
                        if (recipe.getOutput() != null && !recipe.getOutput().isEmpty()) {
                            logger.info("Most recent recipe has {} outputs", recipe.getOutput().size());
                        }
                    } catch (Exception e) {
                        // Ignore output display errors
                    }
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Elven Trade recipes!");
        } catch (Exception e) {
            logger.error("Error processing Elven Trade recipes", e);
        }
    }

    private Recipe processRecipe(RecipeElvenTrade recipe) {
        try {
            // Get outputs - handle API version differences
            List<?> outputs = null;
            try {
                outputs = recipe.getOutput();
            } catch (NoSuchMethodError e) {
                logger.debug("Elven Trade recipe doesn't have getOutput() method, skipping");
                return null;
            }

            if (outputs == null || outputs.isEmpty()) {
                logger.warn("Skipping Elven Trade recipe with no outputs");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, elvenTrade);

            // Add inputs - handle API version differences
            try {
                Object[] inputs = recipe.getInputs();
                if (inputs != null) {
                    for (Object input : inputs) {
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
                }
            } catch (NoSuchMethodError e) {
                // getInputs() method doesn't exist in this Botania version
                logger.debug("Elven Trade recipe doesn't have getInputs() method, skipping");
                return null;
            }

            // Add outputs (Elven Trade can have multiple outputs)
            for (Object output : outputs) {
                if (output instanceof net.minecraft.item.ItemStack) {
                    net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) output;
                    if (itemStack.getItem() != null) {
                        builder.addItemOutput(itemStack);
                    }
                }
            }

            // Build recipe
            Recipe builtRecipe = builder.build();
            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Elven Trade recipe", e);
            return null;
        }
    }
}
