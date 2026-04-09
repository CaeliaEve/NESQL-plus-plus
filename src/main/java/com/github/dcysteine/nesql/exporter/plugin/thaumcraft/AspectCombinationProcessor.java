package com.github.dcysteine.nesql.exporter.plugin.thaumcraft;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import thaumcraft.api.crafting.IRecipeArcanum;
import thaumcraft.api.ThaumcraftCraftingManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Processes Aspect Combination recipes from Thaumcraft.
 */
public class AspectCombinationProcessor extends PluginHelper {
    private final RecipeType aspectCombination;

    public AspectCombinationProcessor(
            PluginExporter exporter, ThaumcraftRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.aspectCombination = recipeTypeHandler.getAspectCombination();
    }

    public void process() {
        try {
            @SuppressWarnings("unchecked")
            java.util.Collection<IRecipeArcanum> recipes = null;

            // Try to access arcaneRecipes field - may not exist in all Thaumcraft versions
            try {
                recipes = (java.util.Collection<IRecipeArcanum>) ThaumcraftCraftingManager.arcaneRecipes;
            } catch (NoSuchFieldError e) {
                logger.info("Arcane recipes field not found in this Thaumcraft version, skipping");
                return;
            }

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Aspect Combination recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Aspect Combination recipes...", total);

            int count = 0;
            for (IRecipeArcanum recipe : recipes) {
                count++;
                Recipe builtRecipe = processRecipe(recipe);

                if (builtRecipe != null && Logger.intermittentLog(count)) {
                    logger.info("Processed Aspect Combination recipe {} of {}", count, total);
                    try {
                        Object output = recipe.getRecipeOutput();
                        if (output instanceof net.minecraft.item.ItemStack) {
                            net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) output;
                            logger.info("Most recent recipe: {}", itemStack.getDisplayName());
                        }
                    } catch (Exception e) {
                        // Ignore output display errors
                    }
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Aspect Combination recipes!");
        } catch (Exception e) {
            logger.error("Error processing Aspect Combination recipes", e);
        }
    }

    private Recipe processRecipe(IRecipeArcanum recipe) {
        try {
            // Check output - handle API differences
            Object recipeOutput = null;
            try {
                recipeOutput = recipe.getRecipeOutput();
            } catch (NoSuchMethodError e) {
                logger.debug("getRecipeOutput() method not found, skipping recipe");
                return null;
            }

            if (recipeOutput == null) {
                logger.warn("Skipping Aspect Combination recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, aspectCombination);

            // Add input aspects as metadata (these are not physical items) - handle API differences
            Map<String, Object> metadata = new HashMap<>();

            try {
                if (recipe.getAspects() != null && recipe.getAspects().size() > 0) {
                    Map<String, Integer> aspects = new HashMap<>();
                    for (thaumcraft.api.aspects.Aspect aspect : recipe.getAspects().getAspects()) {
                        aspects.put(aspect.getTag(), recipe.getAspects().getAmount(aspect));
                    }
                    metadata.put("aspects", aspects);
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getAspects() method not found, skipping aspect metadata");
            }

            // Add output
            if (recipeOutput instanceof net.minecraft.item.ItemStack) {
                builder.addItemOutput((net.minecraft.item.ItemStack) recipeOutput);
            }

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Register metadata
            try {
                SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("AspectCombination", metadata)
                );
            } catch (Exception e) {
                // Ignore metadata registration errors
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Aspect Combination recipe", e);
            return null;
        }
    }
}
