package com.github.dcysteine.nesql.exporter.plugin.witchery;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * Processes Cauldron recipes from Witchery.
 */
public class CauldronProcessor extends PluginHelper {
    private final RecipeType cauldron;

    public CauldronProcessor(
            PluginExporter exporter, WitcheryRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.cauldron = recipeTypeHandler.getCauldron();
    }

    public void process() {
        try {
            // Access Witchery's recipe registry
            Object witcheryRegistry = Class.forName("witchery.common.RecipeRegistry")
                    .getField("instance").get(null);

            @SuppressWarnings("unchecked")
            Map<ItemStack, ItemStack> recipes = (Map<ItemStack, ItemStack>)
                    witcheryRegistry.getClass().getMethod("getCauldronRecipes").invoke(witcheryRegistry);

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Cauldron recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Cauldron recipes...", total);

            int count = 0;
            for (Map.Entry<ItemStack, ItemStack> entry : recipes.entrySet()) {
                count++;
                processRecipe(entry.getKey(), entry.getValue());

                if (Logger.intermittentLog(count)) {
                    logger.info("Processed Cauldron recipe {} of {}", count, total);
                    logger.info("Most recent recipe: {}",
                            entry.getValue() != null ? entry.getValue().getDisplayName() : "null");
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Cauldron recipes!");
        } catch (Exception e) {
            logger.error("Error processing Cauldron recipes", e);
        }
    }

    private void processRecipe(ItemStack input, ItemStack output) {
        try {
            if (output == null) {
                logger.warn("Skipping Cauldron recipe with null output");
                return;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, cauldron);

            // Add input
            if (input != null && input.getItem() != null) {
                builder.addItemInput(input);
            }

            builder.addItemOutput(output).build();

        } catch (Exception e) {
            logger.error("Error processing individual Cauldron recipe", e);
        }
    }
}
