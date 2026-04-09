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
 * Processes Spinning Wheel recipes from Witchery.
 */
public class SpinnerProcessor extends PluginHelper {
    private final RecipeType spinner;

    public SpinnerProcessor(
            PluginExporter exporter, WitcheryRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.spinner = recipeTypeHandler.getSpinner();
    }

    public void process() {
        try {
            // Access Witchery's recipe registry
            Object witcheryRegistry = Class.forName("witchery.common.RecipeRegistry")
                    .getField("instance").get(null);

            @SuppressWarnings("unchecked")
            Map<ItemStack, ItemStack> recipes = (Map<ItemStack, ItemStack>)
                    witcheryRegistry.getClass().getMethod("getSpinnerRecipes").invoke(witcheryRegistry);

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Spinning Wheel recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Spinning Wheel recipes...", total);

            int count = 0;
            for (Map.Entry<ItemStack, ItemStack> entry : recipes.entrySet()) {
                count++;
                processRecipe(entry.getKey(), entry.getValue());

                if (Logger.intermittentLog(count)) {
                    logger.info("Processed Spinning Wheel recipe {} of {}", count, total);
                    logger.info("Most recent recipe: {}",
                            entry.getValue() != null ? entry.getValue().getDisplayName() : "null");
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Spinning Wheel recipes!");
        } catch (Exception e) {
            logger.error("Error processing Spinning Wheel recipes", e);
        }
    }

    private void processRecipe(ItemStack input, ItemStack output) {
        try {
            if (output == null) {
                logger.warn("Skipping Spinning Wheel recipe with null output");
                return;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, spinner);

            // Add input
            if (input != null && input.getItem() != null) {
                builder.addItemInput(input);
            }

            builder.addItemOutput(output).build();

        } catch (Exception e) {
            logger.error("Error processing individual Spinning Wheel recipe", e);
        }
    }
}
