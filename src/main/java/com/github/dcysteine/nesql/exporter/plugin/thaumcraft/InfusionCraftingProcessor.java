package com.github.dcysteine.nesql.exporter.plugin.thaumcraft;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import java.util.List;
import java.util.Map;

/**
 * Processes Infusion Crafting recipes from Thaumcraft.
 */
public class InfusionCraftingProcessor extends PluginHelper {
    private final RecipeType infusionCrafting;

    public InfusionCraftingProcessor(
            PluginExporter exporter, ThaumcraftRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.infusionCrafting = recipeTypeHandler.getInfusionCrafting();
    }

    public void process() {
        try {
            Logger.chatMessage("=== Thaumcraft Infusion Crafting Diagnostics ===");

            // Diagnose ThaumcraftCraftingManager structure
            Object manager = null;
            try {
                Class<?> managerClass = Class.forName("thaumcraft.api.ThaumcraftCraftingManager");
                Logger.chatMessage("Found ThaumcraftCraftingManager class");

                // Try to get instance
                try {
                    java.lang.reflect.Field instanceField = managerClass.getDeclaredField("instance");
                    instanceField.setAccessible(true);
                    manager = instanceField.get(null);
                    Logger.chatMessage("Got instance: " + (manager != null ? manager.getClass() : "null"));
                } catch (Exception e) {
                    Logger.chatMessage("No instance field: " + e.getMessage());
                }

                if (manager == null) {
                    // Try static fields directly - but DON'T return, continue to recipe processing
                    Logger.chatMessage("No instance field, will access static fields directly for recipes...");
                    diagnoseClass(managerClass);
                } else {
                    diagnoseClass(manager.getClass());
                }

            } catch (ClassNotFoundException e) {
                logger.error("ThaumcraftCraftingManager class not found!", e);
                return;
            }

            // Get infusion recipes from ThaumcraftCraftingManager - use reflection for private fields
            Map<String, InfusionRecipe[]> recipeMap = null;
            Logger.chatMessage("Attempting to access infusionRecipes field...");

            try {
                // Try direct access first (in case it's public)
                @SuppressWarnings("unchecked")
                Map<String, InfusionRecipe[]> recipes =
                        (Map<String, InfusionRecipe[]>) thaumcraft.api.ThaumcraftCraftingManager.infusionRecipes;
                recipeMap = recipes;
                Logger.chatMessage("Direct access successful! Recipe types: " + (recipeMap != null ? recipeMap.size() : "null"));
            } catch (NoSuchFieldError e) {
                Logger.chatMessage("Direct access failed, trying reflection...");
                try {
                    java.lang.reflect.Field field = Class.forName("thaumcraft.api.ThaumcraftCraftingManager")
                            .getDeclaredField("infusionRecipes");
                    field.setAccessible(true);
                    recipeMap = (Map<String, InfusionRecipe[]>) field.get(null);
                    Logger.chatMessage("Reflection access successful! Recipe types: " + recipeMap.size());
                } catch (Exception ex) {
                    Logger.chatMessage("Reflection failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    logger.error("Failed to access infusionRecipes via reflection", ex);
                    return;
                }
            }

            if (recipeMap == null || recipeMap.isEmpty()) {
                Logger.chatMessage("No Infusion Crafting recipes found! Map is " + (recipeMap == null ? "null" : "empty (size=0)"));
                logger.info("No Infusion Crafting recipes found!");
                return;
            }

            int total = 0;
            for (InfusionRecipe[] recipes : recipeMap.values()) {
                total += recipes.length;
            }

            logger.info("Processing {} Infusion Crafting recipes...", total);

            int count = 0;
            for (Map.Entry<String, InfusionRecipe[]> entry : recipeMap.entrySet()) {
                for (InfusionRecipe recipe : entry.getValue()) {
                    count++;
                    Recipe builtRecipe = processRecipe(recipe);

                    if (builtRecipe != null && Logger.intermittentLog(count)) {
                        logger.info("Processed Infusion Crafting recipe {} of {}", count, total);
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
            logger.info("Finished processing Infusion Crafting recipes!");
        } catch (Exception e) {
            logger.error("Error processing Infusion Crafting recipes", e);
        }
    }

    private void diagnoseClass(Class<?> clazz) {
        Logger.chatMessage("--- Diagnosing class: " + clazz.getName() + " ---");

        // List all static fields
        Logger.chatMessage("--- Static fields ---");
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            String typeName = field.getType().getSimpleName();
            Logger.chatMessage("  Static field: " + typeName + " " + field.getName());
        }

        // List all public static methods
        Logger.chatMessage("--- Public static methods ---");
        for (java.lang.reflect.Method method : clazz.getMethods()) {
            String methodName = method.getName();
            if (java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                Logger.chatMessage("  Static method: " + method.getReturnType().getSimpleName() + " " + methodName);
            }
        }
    }

    private Recipe processRecipe(InfusionRecipe recipe) {
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
                logger.warn("Skipping Infusion recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, infusionCrafting);

            // Add the central item (recipeInput) - handle API differences
            try {
                Object recipeInput = recipe.getRecipeInput();
                if (recipeInput instanceof net.minecraft.item.ItemStack) {
                    net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) recipeInput;
                    if (itemStack.getItem() != null) {
                        builder.addItemInput(itemStack);
                    }
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getRecipeInput() method not found, skipping recipe input");
            }

            // Add the components (secondary inputs) - handle API differences
            try {
                Object[] components = recipe.getComponents();
                if (components != null) {
                    for (Object component : components) {
                        if (component == null) {
                            builder.skipItemInput();
                            continue;
                        }
                        handleItemInput(builder, component);
                    }
                }
            } catch (NoSuchMethodError e) {
                logger.debug("getComponents() method not found, skipping components");
            }

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Add aspects as metadata - handle API differences
            try {
                AspectList aspects = recipe.getAspects();
                if (aspects != null && aspects.size() > 0) {
                    Map<String, Object> metadata = new java.util.HashMap<>();
                    Map<String, Integer> aspectMap = new java.util.HashMap<>();
                    for (Aspect aspect : aspects.getAspects()) {
                        aspectMap.put(aspect.getTag(), aspects.getAmount(aspect));
                    }
                    metadata.put("aspects", aspectMap);

                    try {
                        SpecialRecipeMetadataRegistry.registerMetadata(
                                builtRecipe.getId(),
                                new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("InfusionCrafting", metadata)
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
            logger.error("Error processing individual Infusion recipe", e);
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
        } else if (itemInput instanceof net.minecraft.item.ItemStack[]) {
            // Handle item stack arrays
            net.minecraft.item.ItemStack[] itemStacks = (net.minecraft.item.ItemStack[]) itemInput;
            if (itemStacks.length > 0 && itemStacks[0].getItem() != null) {
                builder.addItemGroupInput(itemStacks);
            } else {
                builder.skipItemInput();
            }
        } else {
            builder.skipItemInput();
        }
    }
}
