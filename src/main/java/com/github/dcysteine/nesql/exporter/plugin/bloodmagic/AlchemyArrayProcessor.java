package com.github.dcysteine.nesql.exporter.plugin.bloodmagic;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import WayofTime.alchemicalWizardry.AlchemyWizardryRecipes;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Processes Alchemy Array recipes from Blood Magic.
 */
public class AlchemyArrayProcessor extends PluginHelper {
    private final RecipeType alchemyArray;

    public AlchemyArrayProcessor(
            PluginExporter exporter, BloodMagicRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.alchemyArray = recipeTypeHandler.getAlchemyArray();
    }

    public void process() {
        try {
            // Try to access arrayRecipes field - may not exist in all Blood Magic versions
            Map<ItemStack, ItemStack> recipeMap = null;
            try {
                recipeMap = AlchemyWizardryRecipes.arrayRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Array recipes field not found in this Blood Magic version, skipping");
                return;
            }

            if (recipeMap == null || recipeMap.isEmpty()) {
                logger.info("No Alchemy Array recipes found!");
                return;
            }

            int total = recipeMap.size();
            logger.info("Processing {} Alchemy Array recipes...", total);

            int count = 0;
            for (Map.Entry<ItemStack, ItemStack> entry : recipeMap.entrySet()) {
                count++;
                processRecipe(entry.getKey(), entry.getValue());

                if (Logger.intermittentLog(count)) {
                    logger.info("Processed Alchemy Array recipe {} of {}", count, total);
                    try {
                        if (entry.getValue() != null) {
                            logger.info("Most recent recipe: {}", entry.getValue().getDisplayName());
                        }
                    } catch (Exception e) {
                        // Ignore display name errors
                    }
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Alchemy Array recipes!");
        } catch (Exception e) {
            logger.error("Error processing Alchemy Array recipes", e);
        }
    }

    private Recipe processRecipe(ItemStack input, ItemStack output) {
        try {
            if (output == null) {
                logger.warn("Skipping Alchemy Array recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, alchemyArray);

            // Add input
            if (input != null && input.getItem() != null) {
                builder.addItemInput(input);
            }

            // Add output
            builder.addItemOutput(output);

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Get blood cost from recipe - handle API differences
            int bloodCost = 0;
            try {
                bloodCost = AlchemyWizardryRecipes.getArrayBloodRequired(input);
            } catch (NoSuchMethodError e) {
                logger.debug("getArrayBloodRequired() method not found, using default blood cost 0");
            }

            // Register metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("bloodCost", bloodCost);
            try {
                SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("AlchemyArray", metadata)
                );
            } catch (Exception e) {
                // Ignore metadata registration errors
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Alchemy Array recipe", e);
            return null;
        }
    }
}
