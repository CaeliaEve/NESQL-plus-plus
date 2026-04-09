package com.github.dcysteine.nesql.exporter.plugin.thaumcraft;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.ThaumcraftCraftingManager;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Processes Crucible recipes from Thaumcraft.
 */
public class CrucibleProcessor extends PluginHelper {
    private final RecipeType crucible;

    public CrucibleProcessor(
            PluginExporter exporter, ThaumcraftRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.crucible = recipeTypeHandler.getCrucible();
    }

    public void process() {
        try {
            @SuppressWarnings("unchecked")
            List<CrucibleRecipe> recipes = null;

            // Try to get crucible recipes - method may not exist in all Thaumcraft versions
            try {
                recipes = ThaumcraftCraftingManager.getCrucibleRecipes();
            } catch (NoSuchMethodError e) {
                logger.info("getCrucibleRecipes() method not found in this Thaumcraft version, skipping");
                return;
            }

            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Crucible recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Crucible recipes...", total);

            int count = 0;
            for (CrucibleRecipe recipe : recipes) {
                count++;
                Recipe builtRecipe = processRecipe(recipe);

                if (builtRecipe != null && Logger.intermittentLog(count)) {
                    logger.info("Processed Crucible recipe {} of {}", count, total);
                    try {
                        if (recipe.getRecipeOutput() != null) {
                            logger.info("Most recent recipe: {}",
                                    recipe.getRecipeOutput().getDisplayName());
                        }
                    } catch (Exception e) {
                        // Ignore output display errors
                    }
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Crucible recipes!");
        } catch (Exception e) {
            logger.error("Error processing Crucible recipes", e);
        }
    }

    private Recipe processRecipe(CrucibleRecipe recipe) {
        try {
            try {
                if (recipe.getRecipeOutput() == null) {
                    logger.warn("Skipping Crucible recipe with null output");
                    return null;
                }
            } catch (NoSuchMethodError e) {
                logger.warn("Skipping Crucible recipe - no getRecipeOutput() method");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, crucible);

            // Add input - handle API differences
            try {
                Object recipeInput = callNoArg(recipe, "getRecipeInput");
                if (recipeInput instanceof net.minecraft.item.ItemStack) {
                    net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) recipeInput;
                    if (itemStack.getItem() != null) {
                        builder.addItemInput(itemStack);
                    }
                }
            } catch (Exception e) {
                logger.debug("Crucible recipe doesn't have getRecipeInput() method");
            }

            // Add output - handle API differences
            try {
                builder.addItemOutput(recipe.getRecipeOutput());
            } catch (NoSuchMethodError e) {
                logger.debug("Crucible recipe doesn't have getRecipeOutput() method");
            }

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Register metadata (aspects) - handle API differences
            try {
                Object aspectList = callNoArg(recipe, "getAspects");
                if (aspectList != null) {
                    Integer aspectCount = asInteger(callNoArg(aspectList, "size"));
                    if (aspectCount == null || aspectCount <= 0) {
                        return builtRecipe;
                    }
                    Map<String, Object> metadata = new HashMap<>();
                    Map<String, Integer> aspects = new HashMap<>();
                    Object aspectsObj = callNoArg(aspectList, "getAspects");
                    if (aspectsObj instanceof Object[]) {
                        Object[] aspectArray = (Object[]) aspectsObj;
                        java.lang.reflect.Method getAmount = aspectList.getClass().getMethod("getAmount", aspectArray.getClass().getComponentType());
                        java.lang.reflect.Method getTag = aspectArray.getClass().getComponentType().getMethod("getTag");
                        for (Object aspect : aspectArray) {
                            Object tagObj = getTag.invoke(aspect);
                            Object amtObj = getAmount.invoke(aspectList, aspect);
                            if (tagObj instanceof String && amtObj instanceof Number) {
                                aspects.put((String) tagObj, ((Number) amtObj).intValue());
                            }
                        }
                    }
                    metadata.put("aspects", aspects);
                    SpecialRecipeMetadataRegistry.registerMetadata(
                            builtRecipe.getId(),
                            new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("Crucible", metadata)
                    );
                }
            } catch (Exception e) {
                logger.debug("Crucible recipe doesn't have getAspects() method");
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Crucible recipe", e);
            return null;
        }
    }

    private static Object callNoArg(Object target, String methodName) throws Exception {
        java.lang.reflect.Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Integer asInteger(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }
}
