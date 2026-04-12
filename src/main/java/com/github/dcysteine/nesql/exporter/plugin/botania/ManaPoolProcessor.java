package com.github.dcysteine.nesql.exporter.plugin.botania;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import vazkii.botania.api.recipe.RecipeManaInfusion;
import vazkii.botania.api.BotaniaAPI;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Processes Mana Pool recipes from Botania.
 */
public class ManaPoolProcessor extends PluginHelper {
    private final RecipeType manaPool;

    public ManaPoolProcessor(
            PluginExporter exporter, BotaniaRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.manaPool = recipeTypeHandler.getManaPool();
    }

    public void process() {
        try {
            List<RecipeManaInfusion> recipes = null;

            // Try to access manaInfusionRecipes field - may not exist in all Botania versions
            try {
                recipes = BotaniaAPI.manaInfusionRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Mana Pool recipes field not found in this Botania version, skipping");
                return;
            }

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Mana Pool recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Mana Pool recipes...", total);

            int count = 0;
            for (RecipeManaInfusion recipe : recipes) {
                count++;
                Recipe builtRecipe = processRecipe(recipe);

                if (builtRecipe != null && Logger.intermittentLog(count)) {
                    logger.info("Processed Mana Pool recipe {} of {}", count, total);
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
            logger.info("Finished processing Mana Pool recipes!");
        } catch (Exception e) {
            logger.error("Error processing Mana Pool recipes", e);
        }
    }

    private Recipe processRecipe(RecipeManaInfusion recipe) {
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
                logger.warn("Skipping Mana Pool recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, manaPool);
            if (recipeOutput instanceof net.minecraft.item.ItemStack) {
                net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) recipeOutput;
                if (itemStack.getItem() != null) {
                    builder.addItemOutput(itemStack);
                }
            } else {
                logger.warn("Skipping Mana Pool recipe with unsupported output type: {}",
                        recipeOutput.getClass().getName());
                return null;
            }

            // Add input - handle API differences
            try {
                Object input = recipe.getInput();
                if (input != null) {
                    handleItemInput(builder, input);
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getInput() method not found, skipping input");
            }

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Register metadata (mana cost) - handle API differences
            int manaCost = 0;
            try {
                manaCost = recipe.getManaToConsume();
            } catch (NoSuchMethodError e) {
                logger.debug("getManaToConsume() method not found, using default mana cost 0");
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("manaCost", manaCost);
            try {
                SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("ManaPool", metadata)
                );
            } catch (Exception e) {
                // Ignore metadata registration errors
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Mana Pool recipe", e);
            return null;
        }
    }

    private void handleItemInput(RecipeBuilder builder, Object itemInput) {
        if (itemInput instanceof net.minecraft.item.ItemStack) {
            net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) itemInput;
            if (itemStack.getItem() == null) {
                builder.skipItemInput();
            } else {
                builder.addItemInput(itemStack);
            }
        } else if (itemInput instanceof String) {
            java.util.List<net.minecraft.item.ItemStack> itemStacks =
                    net.minecraftforge.oredict.OreDictionary.getOres((String) itemInput, false);
            if (itemStacks == null || itemStacks.isEmpty()) {
                builder.skipItemInput();
            } else {
                builder.addItemGroupInput(itemStacks);
            }
        } else {
            // For other types (like ore dictionary), skip for now
            builder.skipItemInput();
        }
    }
}
