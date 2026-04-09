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
 * Processes Tartaric Forge recipes from Blood Magic.
 */
public class TartarForgeProcessor extends PluginHelper {
    private final RecipeType tartarForge;

    public TartarForgeProcessor(
            PluginExporter exporter, BloodMagicRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.tartarForge = recipeTypeHandler.getTartarForge();
    }

    public void process() {
        try {
            // Try to access tartarForgeRecipes field - may not exist in all Blood Magic versions
            Map<ItemStack, ItemStack> recipeMap = null;
            try {
                recipeMap = AlchemyWizardryRecipes.tartarForgeRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Tartaric Forge recipes field not found in this Blood Magic version, skipping");
                return;
            }

            if (recipeMap == null || recipeMap.isEmpty()) {
                logger.info("No Tartaric Forge recipes found!");
                return;
            }

            int total = recipeMap.size();
            logger.info("Processing {} Tartaric Forge recipes...", total);

            int count = 0;
            for (Map.Entry<ItemStack, ItemStack> entry : recipeMap.entrySet()) {
                count++;
                processRecipe(entry.getKey(), entry.getValue());

                if (Logger.intermittentLog(count)) {
                    logger.info("Processed Tartaric Forge recipe {} of {}", count, total);
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
            logger.info("Finished processing Tartaric Forge recipes!");
        } catch (Exception e) {
            logger.error("Error processing Tartaric Forge recipes", e);
        }
    }

    private Recipe processRecipe(ItemStack input, ItemStack output) {
        try {
            if (output == null) {
                logger.warn("Skipping Tartaric Forge recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, tartarForge);

            // Add input
            if (input != null && input.getItem() != null) {
                builder.addItemInput(input);
            }

            // Add output
            builder.addItemOutput(output);

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Get tartaric cost from recipe - handle API differences
            int tartaricCost = 0;
            try {
                tartaricCost = AlchemyWizardryRecipes.getTartarRequiredForRecipe(input);
            } catch (NoSuchMethodError e) {
                logger.debug("getTartarRequiredForRecipe() method not found, using default tartaric cost 0");
            }

            // Register metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tartaricCost", tartaricCost);
            try {
                SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("TartaricForge", metadata)
                );
            } catch (Exception e) {
                // Ignore metadata registration errors
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Tartaric Forge recipe", e);
            return null;
        }
    }
}
