package com.github.dcysteine.nesql.exporter.plugin.botania;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import vazkii.botania.api.BotaniaAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Brew recipes from Botania.
 */
public class BrewRecipeProcessor extends PluginHelper {
    private final RecipeType brewRecipe;

    public BrewRecipeProcessor(
            PluginExporter exporter, BotaniaRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.brewRecipe = recipeTypeHandler.getBrewRecipe();
    }

    public void process() {
        try {
            List<?> recipes = readBrewRecipesReflectively();
            if (recipes == null || recipes.isEmpty()) {
                logger.info("No Brew recipes found!");
                return;
            }

            int total = recipes.size();
            logger.info("Processing {} Brew recipes...", total);

            int count = 0;
            for (Object recipe : recipes) {
                count++;
                processRecipe(recipe);

                if (Logger.intermittentLog(count)) {
                    logger.info("Processed Brew recipe {} of {}", count, total);
                    logger.info("Most recent recipe: {}", readBrewKey(recipe));
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Brew recipes!");
        } catch (Exception e) {
            logger.error("Error processing Brew recipes", e);
        }
    }

    private List<?> readBrewRecipesReflectively() {
        for (String fieldName : new String[] {"brewRecipes", "brewRecipeList"}) {
            try {
                java.lang.reflect.Field field = BotaniaAPI.class.getField(fieldName);
                Object value = field.get(null);
                if (value instanceof List<?>) {
                    return (List<?>) value;
                }
                if (value instanceof Map<?, ?>) {
                    return new ArrayList<>(((Map<?, ?>) value).values());
                }
            } catch (Exception ignored) {
            }
        }
        logger.warn("Brew recipes field not found in this Botania version");
        return null;
    }

    private void processRecipe(Object recipe) {
        try {
            RecipeBuilder builder = new RecipeBuilder(exporter, brewRecipe);

            for (Object input : readInputs(recipe)) {
                if (input instanceof net.minecraft.item.ItemStack) {
                    net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) input;
                    if (itemStack.getItem() != null) {
                        builder.addItemInput(itemStack);
                    }
                }
            }

            Recipe builtRecipe = builder.build();

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("brewKey", readBrewKey(recipe));
            int manaCost = readManaCost(recipe);
            if (manaCost > 0) {
                metadata.put("manaCost", manaCost);
            }
            try {
                SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("Brew", metadata)
                );
            } catch (Exception e) {
                // Ignore metadata registration errors.
            }

        } catch (Exception e) {
            logger.error("Error processing individual Brew recipe", e);
        }
    }

    private List<?> readInputs(Object recipe) {
        for (String methodName : new String[] {"getInputs", "getIngredients"}) {
            try {
                java.lang.reflect.Method method = recipe.getClass().getMethod(methodName);
                Object value = method.invoke(recipe);
                if (value instanceof List<?>) {
                    return (List<?>) value;
                }
                if (value instanceof Object[]) {
                    List<Object> result = new ArrayList<>();
                    for (Object entry : (Object[]) value) {
                        result.add(entry);
                    }
                    return result;
                }
            } catch (Exception ignored) {
            }
        }
        return java.util.Collections.emptyList();
    }

    private String readBrewKey(Object recipe) {
        try {
            java.lang.reflect.Method getBrew = recipe.getClass().getMethod("getBrew");
            Object brew = getBrew.invoke(recipe);
            if (brew != null) {
                java.lang.reflect.Method getKey = brew.getClass().getMethod("getKey");
                Object key = getKey.invoke(brew);
                if (key != null) {
                    return String.valueOf(key);
                }
            }
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method getKey = recipe.getClass().getMethod("getKey");
            Object key = getKey.invoke(recipe);
            if (key != null) {
                return String.valueOf(key);
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private int readManaCost(Object recipe) {
        try {
            java.lang.reflect.Method getManaUsage = recipe.getClass().getMethod("getManaUsage");
            Object value = getManaUsage.invoke(recipe);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method getBrew = recipe.getClass().getMethod("getBrew");
            Object brew = getBrew.invoke(recipe);
            if (brew != null) {
                java.lang.reflect.Method getManaCost = brew.getClass().getMethod("getManaCost");
                Object value = getManaCost.invoke(brew);
                if (value instanceof Number) {
                    return ((Number) value).intValue();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
