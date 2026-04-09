package com.github.dcysteine.nesql.exporter.plugin.thaumcraft;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Arcane Workbench recipes from Thaumcraft.
 */
public class ArcaneWorkbenchProcessor extends PluginHelper {
    private final RecipeType arcaneWorkbench;

    public ArcaneWorkbenchProcessor(
            PluginExporter exporter, ThaumcraftRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.arcaneWorkbench = recipeTypeHandler.getArcaneWorkbench();
    }

    public void process() {
        try {
            // Get arcane recipes from ThaumcraftCraftingManager - handle API differences
            List<ShapedArcaneRecipe> shapedRecipes = null;
            List<ShapelessArcaneRecipe> shapelessRecipes = null;

            try {
                @SuppressWarnings("unchecked")
                List<ShapedArcaneRecipe> recipes = thaumcraft.api.ThaumcraftCraftingManager.getArcaneRecipes();
                shapedRecipes = recipes;
            } catch (NoSuchMethodError e) {
                logger.info("getArcaneRecipes() method not found in this Thaumcraft version");
            }

            try {
                @SuppressWarnings("unchecked")
                List<ShapelessArcaneRecipe> recipes = thaumcraft.api.ThaumcraftCraftingManager.getArcaneShapelessRecipes();
                shapelessRecipes = recipes;
            } catch (NoSuchMethodError e) {
                logger.info("getArcaneShapelessRecipes() method not found in this Thaumcraft version");
            }

            int total = (shapedRecipes != null ? shapedRecipes.size() : 0) +
                       (shapelessRecipes != null ? shapelessRecipes.size() : 0);

            if (total == 0) {
                logger.info("No Arcane Workbench recipes found!");
                return;
            }

            logger.info("Processing {} Arcane Workbench recipes...", total);

            int count = 0;

            // Process shaped recipes
            if (shapedRecipes != null) {
                for (ShapedArcaneRecipe recipe : shapedRecipes) {
                    count++;
                    Recipe builtRecipe = processShapedRecipe(recipe);

                    if (builtRecipe != null && Logger.intermittentLog(count)) {
                        logger.info("Processed Arcane Workbench recipe {} of {}", count, total);
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
            }

            // Process shapeless recipes
            if (shapelessRecipes != null) {
                for (ShapelessArcaneRecipe recipe : shapelessRecipes) {
                    count++;
                    Recipe builtRecipe = processShapelessRecipe(recipe);

                    if (builtRecipe != null && Logger.intermittentLog(count)) {
                        logger.info("Processed Arcane Workbench recipe {} of {}", count, total);
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
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Arcane Workbench recipes!");
        } catch (Exception e) {
            logger.error("Error processing Arcane Workbench recipes", e);
        }
    }

    private Recipe processShapedRecipe(ShapedArcaneRecipe recipe) {
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
                logger.warn("Skipping Arcane recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, arcaneWorkbench);

            // Process inputs - handle API differences
            try {
                Object[] inputs = recipe.getInput();
                if (inputs != null) {
                    for (Object itemInput : inputs) {
                        if (itemInput == null) {
                            builder.skipItemInput();
                            continue;
                        }
                        handleItemInput(builder, itemInput);
                    }
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getInput() method not found, skipping inputs");
            }

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Add aspects as metadata - handle API differences
            try {
                AspectList aspects = recipe.getAspects();
                if (aspects != null && aspects.size() > 0) {
                    Map<String, Object> metadata = new HashMap<>();
                    Map<String, Integer> aspectMap = new HashMap<>();
                    for (Aspect aspect : aspects.getAspects()) {
                        aspectMap.put(aspect.getTag(), aspects.getAmount(aspect));
                    }
                    metadata.put("aspects", aspectMap);

                    try {
                        SpecialRecipeMetadataRegistry.registerMetadata(
                                builtRecipe.getId(),
                                new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("ArcaneWorkbench", metadata)
                        );
                    } catch (Exception e) {
                        // Ignore metadata registration errors
                    }
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getAspects() method not found, skipping aspect metadata");
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Arcane recipe", e);
            return null;
        }
    }

    private Recipe processShapelessRecipe(ShapelessArcaneRecipe recipe) {
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
                logger.warn("Skipping Arcane recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, arcaneWorkbench);

            // Process inputs - handle API differences
            try {
                List<?> inputs = recipe.getInput();
                if (inputs != null) {
                    for (Object itemInput : inputs) {
                        if (itemInput == null) {
                            continue;
                        }
                        handleItemInput(builder, itemInput);
                    }
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getInput() method not found, skipping inputs");
            }

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Add aspects as metadata - handle API differences
            try {
                AspectList aspects = recipe.getAspects();
                if (aspects != null && aspects.size() > 0) {
                    Map<String, Object> metadata = new HashMap<>();
                    Map<String, Integer> aspectMap = new HashMap<>();
                    for (Aspect aspect : aspects.getAspects()) {
                        aspectMap.put(aspect.getTag(), aspects.getAmount(aspect));
                    }
                    metadata.put("aspects", aspectMap);

                    try {
                        SpecialRecipeMetadataRegistry.registerMetadata(
                                builtRecipe.getId(),
                                new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("ArcaneWorkbench", metadata)
                        );
                    } catch (Exception e) {
                        // Ignore metadata registration errors
                    }
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getAspects() method not found, skipping aspect metadata");
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Arcane recipe", e);
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
        } else if (itemInput instanceof List) {
            // Handle ore dictionary lists
            @SuppressWarnings("unchecked")
            List<net.minecraft.item.ItemStack> itemList = (List<net.minecraft.item.ItemStack>) itemInput;
            if (!itemList.isEmpty() && itemList.get(0).getItem() != null) {
                builder.addItemGroupInput(itemList.toArray(new net.minecraft.item.ItemStack[0]));
            } else {
                builder.skipItemInput();
            }
        } else {
            builder.skipItemInput();
        }
    }
}
