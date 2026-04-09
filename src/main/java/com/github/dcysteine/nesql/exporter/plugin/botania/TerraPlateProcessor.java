package com.github.dcysteine.nesql.exporter.plugin.botania;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import vazkii.botania.api.recipe.RecipeTerraPlate;
import vazkii.botania.api.BotaniaAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Terra Plate recipes from Botania.
 */
public class TerraPlateProcessor extends PluginHelper {
    private final RecipeType terraPlate;

    public TerraPlateProcessor(
            PluginExporter exporter, BotaniaRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.terraPlate = recipeTypeHandler.getTerraPlate();
    }

    public void process() {
        try {
            List<RecipeTerraPlate> recipes = null;

            // Try to access terraPlateRecipes field - may not exist in all Botania versions
            try {
                recipes = BotaniaAPI.terraPlateRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Terra Plate recipes field not found in this Botania version, skipping");
                return;
            }

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Terra Plate recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Terra Plate recipes...", total);

            int count = 0;
            for (RecipeTerraPlate recipe : recipes) {
                count++;
                Recipe builtRecipe = processRecipe(recipe);

                if (builtRecipe != null && Logger.intermittentLog(count)) {
                    logger.info("Processed Terra Plate recipe {} of {}", count, total);
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
            logger.info("Finished processing Terra Plate recipes!");
        } catch (Exception e) {
            logger.error("Error processing Terra Plate recipes", e);
        }
    }

    private Recipe processRecipe(RecipeTerraPlate recipe) {
        try {
            try {
                if (recipe.getOutput() == null) {
                    logger.warn("Skipping Terra Plate recipe with null output");
                    return null;
                }
            } catch (NoSuchMethodError e) {
                logger.warn("Skipping Terra Plate recipe - no getOutput() method");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, terraPlate);

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
                logger.debug("Terra Plate recipe doesn't have getInputs() method");
            }

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Add mana cost and number of ticks as metadata - handle API differences
            try {
                Map<String, Object> metadata = new HashMap<>();
                try {
                    metadata.put("manaCost", recipe.getMana());
                } catch (NoSuchMethodError e) {
                    // Ignore
                }
                try {
                    metadata.put("ticks", recipe.getTicks());
                } catch (NoSuchMethodError e) {
                    // Ignore
                }
                if (!metadata.isEmpty()) {
                    SpecialRecipeMetadataRegistry.registerMetadata(
                            builtRecipe.getId(),
                            new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("TerraPlate", metadata)
                    );
                }
            } catch (Exception e) {
                // Ignore metadata errors
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Terra Plate recipe", e);
            return null;
        }
    }
}
