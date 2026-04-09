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
 * Processes Sacrificial recipes from Blood Magic.
 */
public class SacrificialProcessor extends PluginHelper {
    private final RecipeType sacrificial;

    public SacrificialProcessor(
            PluginExporter exporter, BloodMagicRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.sacrificial = recipeTypeHandler.getSacrificial();
    }

    public void process() {
        try {
            // Try to access sacrificialRecipes field - may not exist in all Blood Magic versions
            Map<ItemStack, ItemStack> recipeMap = null;
            try {
                recipeMap = AlchemyWizardryRecipes.sacrificialRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Sacrificial recipes field not found in this Blood Magic version, skipping");
                return;
            }

            if (recipeMap == null || recipeMap.isEmpty()) {
                logger.info("No Sacrificial recipes found!");
                return;
            }

            int total = recipeMap.size();
            logger.info("Processing {} Sacrificial recipes...", total);

            int count = 0;
            for (Map.Entry<ItemStack, ItemStack> entry : recipeMap.entrySet()) {
                count++;
                processRecipe(entry.getKey(), entry.getValue());

                if (Logger.intermittentLog(count)) {
                    logger.info("Processed Sacrificial recipe {} of {}", count, total);
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
            logger.info("Finished processing Sacrificial recipes!");
        } catch (Exception e) {
            logger.error("Error processing Sacrificial recipes", e);
        }
    }

    private Recipe processRecipe(ItemStack input, ItemStack output) {
        try {
            if (output == null) {
                logger.warn("Skipping Sacrificial recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, sacrificial);

            // Add input
            if (input != null && input.getItem() != null) {
                builder.addItemInput(input);
            }

            // Add output
            builder.addItemOutput(output);

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Get LP cost from recipe - handle API differences
            int lpCost = 0;
            try {
                lpCost = AlchemyWizardryRecipes.getSacrificialLPRequired(input);
            } catch (NoSuchMethodError e) {
                logger.debug("getSacrificialLPRequired() method not found, using default LP cost 0");
            }

            // Register metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("lpCost", lpCost);
            try {
                SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("Sacrificial", metadata)
                );
            } catch (Exception e) {
                // Ignore metadata registration errors
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Sacrificial recipe", e);
            return null;
        }
    }
}
