package com.github.dcysteine.nesql.exporter.plugin.bloodmagic;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import WayofTime.alchemicalWizardry.AlchemyWizardryRecipes;
import WayofTime.alchemicalWizardry.CommonProxy;
import WayofTime.alchemicalWizardry.ModItems;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Blood Altar recipes from Blood Magic.
 */
public class AltarProcessor extends PluginHelper {
    private final RecipeType altar;

    public AltarProcessor(
            PluginExporter exporter, BloodMagicRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.altar = recipeTypeHandler.getAltar();
    }

    public void process() {
        try {
            Logger.chatMessage("=== Blood Magic Altar Diagnostics ===");

            // Diagnose AlchemyWizardryRecipes structure
            Class<?> recipesClass = AlchemyWizardryRecipes.class;
            Logger.chatMessage("Found AlchemyWizardryRecipes class: " + recipesClass.getName());

            diagnoseClass(recipesClass);

            // Try to access altarRecipes field - use reflection for private fields
            Map<ItemStack, ItemStack> recipeMap = null;
            Logger.chatMessage("Attempting to access altarRecipes field...");

            try {
                // Try direct access first (in case it's public)
                recipeMap = AlchemyWizardryRecipes.altarRecipes;
                Logger.chatMessage("Direct access successful! Recipe count: " + (recipeMap != null ? recipeMap.size() : "null"));
            } catch (NoSuchFieldError e) {
                Logger.chatMessage("Direct access failed, trying reflection...");
                try {
                    java.lang.reflect.Field field = AlchemyWizardryRecipes.class
                            .getDeclaredField("altarRecipes");
                    field.setAccessible(true);
                    recipeMap = (Map<ItemStack, ItemStack>) field.get(null);
                    Logger.chatMessage("Reflection access successful! Recipe count: " + recipeMap.size());
                } catch (Exception ex) {
                    Logger.chatMessage("Reflection failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    logger.error("Failed to access altarRecipes via reflection", ex);
                    return;
                }
            }

            if (recipeMap == null || recipeMap.isEmpty()) {
                Logger.chatMessage("No Blood Altar recipes found! Map is " + (recipeMap == null ? "null" : "empty (size=0)"));
                logger.info("No Blood Altar recipes found!");
                return;
            }

            int total = recipeMap.size();
            logger.info("Processing {} Blood Altar recipes...", total);

            int count = 0;
            for (Map.Entry<ItemStack, ItemStack> entry : recipeMap.entrySet()) {
                count++;
                processRecipe(entry.getKey(), entry.getValue(), false);

                if (Logger.intermittentLog(count)) {
                    logger.info("Processed Blood Altar recipe {} of {}", count, total);
                    try {
                        if (entry.getValue() != null) {
                            logger.info("Most recent recipe: {}", entry.getValue().getDisplayName());
                        }
                    } catch (Exception e) {
                        // Ignore display name errors
                    }
                }
            }

            // Also process weak activation recipes
            try {
                if (ModItems.weakActivationCrystal != null) {
                    Map<ItemStack, ItemStack> weakRecipes = AlchemyWizardryRecipes.weakActivationRecipes;
                    if (weakRecipes != null && !weakRecipes.isEmpty()) {
                        for (Map.Entry<ItemStack, ItemStack> entry : weakRecipes.entrySet()) {
                            count++;
                            processRecipe(entry.getKey(), entry.getValue(), true);

                            if (Logger.intermittentLog(count)) {
                                logger.info("Processed Weak Activation recipe {} of {}",
                                        count - recipeMap.size(), weakRecipes.size());
                            }
                        }
                    }
                }
            } catch (NoSuchFieldError e) {
                logger.debug("Weak activation recipes field not found, skipping weak activation recipes");
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Blood Altar recipes!");
        } catch (Exception e) {
            logger.error("Error processing Blood Altar recipes", e);
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

    private Recipe processRecipe(ItemStack input, ItemStack output, boolean isActivation) {
        try {
            if (output == null) {
                logger.warn("Skipping Blood Altar recipe with null output");
                return null;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, altar);

            // Add input
            if (input != null && input.getItem() != null) {
                builder.addItemInput(input);
            }

            // Add output
            builder.addItemOutput(output);

            // Build recipe
            Recipe builtRecipe = builder.build();

            // Get tier and blood cost from recipe lookup - handle API differences
            int tier = 0;
            int bloodCost = 0;
            try {
                tier = AlchemyWizardryRecipes.getTierOfRecipe(input);
            } catch (NoSuchMethodError e) {
                logger.debug("getTierOfRecipe() method not found, using default tier 0");
            }
            try {
                bloodCost = AlchemyWizardryRecipes.getBloodRequiredForRecipe(input, tier);
            } catch (NoSuchMethodError e) {
                logger.debug("getBloodRequiredForRecipe() method not found, using default blood cost 0");
            }

            // Register metadata
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tier", tier);
            metadata.put("bloodCost", bloodCost);
            metadata.put("isWeakActivation", isActivation);
            try {
                SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("BloodAltar", metadata)
                );
            } catch (Exception e) {
                // Ignore metadata registration errors
            }

            return builtRecipe;

        } catch (Exception e) {
            logger.error("Error processing individual Blood Altar recipe", e);
            return null;
        }
    }
}
