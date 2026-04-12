package com.github.dcysteine.nesql.exporter.plugin.botania;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import vazkii.botania.api.recipe.RecipeRuneAltar;
import vazkii.botania.api.BotaniaAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Rune Altar recipes from Botania.
 */
public class RuneAltarProcessor extends PluginHelper {
    private final RecipeType runeAltar;

    public RuneAltarProcessor(
            PluginExporter exporter, BotaniaRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.runeAltar = recipeTypeHandler.getRuneAltar();
    }

    public void process() {
        try {
            List<RecipeRuneAltar> recipes = null;

            // Try to access runeAltarRecipes field - may not exist in all Botania versions
            try {
                recipes = BotaniaAPI.runeAltarRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Rune Altar recipes field not found in this Botania version, skipping");
                return;
            }

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Rune Altar recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Rune Altar recipes...", total);

            int count = 0;
            for (RecipeRuneAltar recipe : recipes) {
                count++;
                Recipe builtRecipe = processRecipe(recipe);

                if (builtRecipe != null && Logger.intermittentLog(count)) {
                    logger.info("Processed Rune Altar recipe {} of {}", count, total);
                    try {
                        if (recipe.getOutput() != null) {
                            logger.info("Most recent recipe: {}",
                                    recipe.getOutput().getDisplayName());
                        }
                    } catch (Exception e) {
                        // Ignore output display errors
                    }
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Rune Altar recipes!");
        } catch (Exception e) {
            logger.error("Error processing Rune Altar recipes", e);
        }
    }

    private Recipe processRecipe(RecipeRuneAltar recipe) {
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
                logger.warn("Skipping Rune Altar recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, runeAltar);
            if (recipeOutput instanceof net.minecraft.item.ItemStack) {
                net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) recipeOutput;
                if (itemStack.getItem() != null) {
                    builder.addItemOutput(itemStack);
                }
            } else {
                logger.warn("Skipping Rune Altar recipe with unsupported output type: {}",
                        recipeOutput.getClass().getName());
                return null;
            }

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
                logger.debug("Rune Altar recipe doesn't have getInputs() method, skipping");
                return null;
            }

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Add mana cost as metadata - handle API differences
            try {
                int manaCost = recipe.getManaUsage();
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("manaCost", manaCost);
                try {
                    SpecialRecipeMetadataRegistry.registerMetadata(
                            builtRecipe.getId(),
                            new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("RuneAltar", metadata)
                    );
                } catch (Exception e) {
                    // Ignore metadata registration errors
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getManaUsage() method not found, skipping mana cost metadata");
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Rune Altar recipe", e);
            return null;
        }
    }
}
