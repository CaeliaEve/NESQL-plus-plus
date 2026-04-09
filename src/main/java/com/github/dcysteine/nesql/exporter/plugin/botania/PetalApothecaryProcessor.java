package com.github.dcysteine.nesql.exporter.plugin.botania;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import vazkii.botania.api.recipe.RecipePetals;
import vazkii.botania.api.BotaniaAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Petal Apothecary recipes from Botania.
 */
public class PetalApothecaryProcessor extends PluginHelper {
    private final RecipeType petalApothecary;

    public PetalApothecaryProcessor(
            PluginExporter exporter, BotaniaRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.petalApothecary = recipeTypeHandler.getPetalApothecary();
    }

    public void process() {
        try {
            List<RecipePetals> recipes = null;

            // Try to access petalRecipes field - may not exist in all Botania versions
            try {
                recipes = BotaniaAPI.petalRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Petal Apothecary recipes field not found in this Botania version, skipping");
                return;
            }

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Petal Apothecary recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Petal Apothecary recipes...", total);

            int count = 0;
            for (RecipePetals recipe : recipes) {
                count++;
                Recipe builtRecipe = processRecipe(recipe);

                if (builtRecipe != null && Logger.intermittentLog(count)) {
                    logger.info("Processed Petal Apothecary recipe {} of {}", count, total);
                    if (recipe.getOutput() != null) {
                        logger.info("Most recent recipe: {}",
                                recipe.getOutput().getDisplayName());
                    }
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Petal Apothecary recipes!");
        } catch (Exception e) {
            logger.error("Error processing Petal Apothecary recipes", e);
        }
    }

    private Recipe processRecipe(RecipePetals recipe) {
        try {
            // Check output - handle API differences
            Object recipeOutput = null;
            try {
                recipeOutput = recipe.getOutput();
            } catch (NoSuchMethodError e) {
                logger.debug("getOutput() method not found, skipping recipe");
                return null;
            }

            if (recipeOutput == null) {
                logger.warn("Skipping Petal Apothecary recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, petalApothecary);

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
                logger.debug("Petal Apothecary recipe doesn't have getInputs() method, skipping");
                return null;
            }

            // Build recipe
            Recipe builtRecipe = builder.build();
            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Petal Apothecary recipe", e);
            return null;
        }
    }
}
