package com.github.dcysteine.nesql.exporter.plugin.avaritia;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import fox.spiteful.avaritia.crafting.ExtremeCraftingManager;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes Extreme Crafting recipes from Avaritia.
 */
public class ExtremeCraftingProcessor extends PluginHelper {
    private final RecipeType extremeCrafting;

    public ExtremeCraftingProcessor(
            PluginExporter exporter, AvaritiaRecipeTypeHandler recipeTypeHandler) {
        super(exporter);
        this.extremeCrafting = recipeTypeHandler.getExtremeCrafting();
    }

    public void process() {
        try {
            // First, check if ExtremeCraftingManager exists
            logger.info("Attempting to access ExtremeCraftingManager...");

            Object managerInstance = null;
            try {
                managerInstance = ExtremeCraftingManager.getInstance();
                logger.info("Got ExtremeCraftingManager instance: {}", managerInstance != null ? managerInstance.getClass() : "null");
            } catch (Exception e) {
                logger.error("Failed to get ExtremeCraftingManager instance: {}", e.getMessage());
                return;
            }

            if (managerInstance == null) {
                logger.error("ExtremeCraftingManager.getInstance() returned null!");
                return;
            }

            // Use reflection to access the recipes field
            Object recipes = null;
            try {
                java.lang.reflect.Field recipesField = ExtremeCraftingManager.class.getDeclaredField("recipes");
                recipesField.setAccessible(true);
                recipes = recipesField.get(managerInstance);
                logger.info("Accessed recipes field via reflection: {}", recipes != null ? recipes.getClass() : "null");
            } catch (Exception e) {
                logger.error("Failed to get Extreme Crafting recipes via reflection: {}", e.getMessage(), e);
                return;
            }

            if (recipes == null) {
                logger.error("No Extreme Crafting recipes found (recipes map is null)!");

                // Try to list all fields
                logger.info("Listing all fields in ExtremeCraftingManager:");
                java.lang.reflect.Field[] fields = managerInstance.getClass().getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(managerInstance);
                        logger.info("  Field: {} = {}", field.getName(), value != null ? value.getClass().getName() : "null");
                    } catch (Exception e) {
                        logger.info("  Field: {} (failed to access)", field.getName());
                    }
                }
                return;
            }

            // Handle both Map and List
            Iterable<?> recipeIterable;
            int total;

            if (recipes instanceof Map) {
                Map<?, ?> recipeMap = (Map<?, ?>) recipes;
                recipeIterable = recipeMap.values();
                total = recipeMap.size();
            } else if (recipes instanceof Iterable) {
                // Handle List, ArrayList, etc.
                Iterable<?> recipeList = (Iterable<?>) recipes;
                recipeIterable = recipeList;
                // Try to get size
                total = 0;
                for (@SuppressWarnings("unused") Object recipe : recipeList) {
                    total++;
                }
            } else {
                logger.warn("Extreme Crafting recipes is neither Map nor Iterable: {}", recipes.getClass());
                return;
            }

            if (total == 0) {
                logger.info("No Extreme Crafting recipes found!");
                return;
            }

            logger.info("Processing {} Extreme Crafting recipes...", total);

            int count = 0;
            boolean diagnosed = false;
            for (Object recipe : recipeIterable) {
                // Diagnose first recipe structure
                if (!diagnosed) {
                    diagnoseRecipe(recipe);
                    diagnosed = true;
                }

                count++;
                processRecipe(recipe);

                if (Logger.intermittentLog(count)) {
                    logger.info("Processed Extreme Crafting recipe {} of {}", count, total);
                }
            }

            exporterState.flushEntityManager();
            logger.info("Finished processing Extreme Crafting recipes!");
        } catch (Exception e) {
            logger.error("Error processing Extreme Crafting recipes", e);
        }
    }

    private void diagnoseRecipe(Object recipe) {
        logger.info("=== Diagnosing first Extreme Crafting recipe ===");
        logger.info("Recipe class: {}", recipe.getClass().getName());

        // Check parent class
        Class<?> superClass = recipe.getClass().getSuperclass();
        logger.info("Super class: {}", superClass != null ? superClass.getName() : "none");

        // List all declared fields
        logger.info("--- Declared fields ---");
        for (java.lang.reflect.Field field : recipe.getClass().getDeclaredFields()) {
            String typeName = field.getType().getSimpleName();
            logger.info("  Field: {} {}", typeName, field.getName());
        }

        // List all public methods
        logger.info("--- Public methods (getRecipeOutput, getOutput, etc.) ---");
        for (java.lang.reflect.Method method : recipe.getClass().getMethods()) {
            String methodName = method.getName();
            if (methodName.startsWith("get") && method.getParameterTypes().length == 0) {
                logger.info("  Method: {}()", method.getReturnType().getSimpleName(), methodName);
            }
        }
    }

    private void processRecipe(Object recipe) {
        try {
            ItemStack output = readRecipeOutput(recipe);
            if (output == null || output.getItem() == null) {
                logger.warn("Skipping Extreme Crafting recipe - could not get output from {}", recipe.getClass().getName());
                return;
            }

            RecipeBuilder builder = new RecipeBuilder(exporter, extremeCrafting);
            Object[] inputs = readRecipeInputs(recipe);

            if (inputs == null) {
                logger.warn("Extreme Crafting recipe has null input");
                return;
            }

            logger.debug("Processing Extreme Crafting recipe with {} inputs", inputs.length);

            int gridWidth = 9;
            int gridHeight = 9;
            Integer reflectedWidth = tryReadInt(recipe, "width");
            Integer reflectedHeight = tryReadInt(recipe, "height");
            if (reflectedWidth != null && reflectedWidth > 0) gridWidth = reflectedWidth;
            if (reflectedHeight != null && reflectedHeight > 0) gridHeight = reflectedHeight;

            Map<Integer, Object> explicitSlotMap = buildSlotMapFromCoordinates(inputs, gridWidth, gridHeight);
            if (explicitSlotMap != null && !explicitSlotMap.isEmpty()) {
                for (Map.Entry<Integer, Object> entry : explicitSlotMap.entrySet()) {
                    handleItemInputAt(builder, entry.getValue(), entry.getKey());
                }
            } else {
                for (int i = 0; i < inputs.length; i++) {
                    Object itemInput = inputs[i];
                    if (itemInput == null) {
                        builder.skipItemInput();
                        continue;
                    }
                    handleItemInput(builder, itemInput);
                }
            }

            builder.addItemOutput(output).build();

        } catch (Exception e) {
            logger.error("Error processing individual Extreme Crafting recipe", e);
        }
    }
    private ItemStack readRecipeOutput(Object recipe) {
        for (String fieldName : new String[] {"output", "recipeOutput", "out"}) {
            try {
                java.lang.reflect.Field outputField = recipe.getClass().getDeclaredField(fieldName);
                outputField.setAccessible(true);
                Object value = outputField.get(recipe);
                if (value instanceof ItemStack) {
                    return (ItemStack) value;
                }
            } catch (Exception ignored) {
            }
        }
        for (String methodName : new String[] {"getRecipeOutput", "getOutput"}) {
            try {
                java.lang.reflect.Method method = recipe.getClass().getMethod(methodName);
                if (method.getParameterTypes().length == 0) {
                    Object value = method.invoke(recipe);
                    if (value instanceof ItemStack) {
                        return (ItemStack) value;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Object[] readRecipeInputs(Object recipe) {
        for (String fieldName : new String[] {"input", "inputs"}) {
            Object[] converted = readInputMember(recipe, fieldName, false);
            if (converted != null) {
                return converted;
            }
        }
        for (String methodName : new String[] {"getInput", "getInputs"}) {
            Object[] converted = readInputMember(recipe, methodName, true);
            if (converted != null) {
                return converted;
            }
        }
        return null;
    }

    private Object[] readInputMember(Object recipe, String name, boolean method) {
        try {
            Object value;
            if (method) {
                java.lang.reflect.Method m = recipe.getClass().getMethod(name);
                if (m.getParameterTypes().length != 0) {
                    return null;
                }
                value = m.invoke(recipe);
            } else {
                java.lang.reflect.Field f = recipe.getClass().getDeclaredField(name);
                f.setAccessible(true);
                value = f.get(recipe);
            }
            if (value instanceof Object[]) {
                return (Object[]) value;
            }
            if (value instanceof java.util.List) {
                return ((java.util.List<?>) value).toArray();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void handleItemInput(RecipeBuilder builder, Object itemInput) {
        if (itemInput instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) itemInput;
            if (itemStack.getItem() == null) {
                builder.skipItemInput();
            } else {
                builder.addItemInput(itemStack);
            }
        } else if (itemInput instanceof ItemStack[]) {
            // Handle ore dictionary or other item stack arrays
            ItemStack[] itemStacks = (ItemStack[]) itemInput;
            if (itemStacks.length > 0 && itemStacks[0] != null && itemStacks[0].getItem() != null) {
                builder.addItemGroupInput(itemStacks);
            } else {
                builder.skipItemInput();
            }
        } else {
            // Try to handle as a single ItemStack via reflection
            try {
                if (itemInput != null && itemInput.getClass().getMethod("getItem") != null) {
                    builder.addItemInput((ItemStack) itemInput);
                } else {
                    builder.skipItemInput();
                }
            } catch (Exception e) {
                builder.skipItemInput();
            }
        }
    }

    private void handleItemInputAt(RecipeBuilder builder, Object itemInput, int slotIndex) {
        if (itemInput instanceof ItemStack) {
            ItemStack itemStack = (ItemStack) itemInput;
            if (itemStack.getItem() == null) {
                builder.skipItemInputAt(slotIndex);
            } else {
                builder.addItemInputAt(slotIndex, itemStack);
            }
        } else if (itemInput instanceof ItemStack[]) {
            ItemStack[] itemStacks = (ItemStack[]) itemInput;
            if (itemStacks.length > 0 && itemStacks[0] != null && itemStacks[0].getItem() != null) {
                builder.addItemGroupInputAt(slotIndex, itemStacks);
            } else {
                builder.skipItemInputAt(slotIndex);
            }
        } else {
            try {
                if (itemInput != null && itemInput.getClass().getMethod("getItem") != null) {
                    builder.addItemInputAt(slotIndex, (ItemStack) itemInput);
                } else {
                    builder.skipItemInputAt(slotIndex);
                }
            } catch (Exception e) {
                builder.skipItemInputAt(slotIndex);
            }
        }
    }

    private static Integer tryReadInt(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object v = f.get(obj);
            if (v instanceof Number) return ((Number) v).intValue();
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Field f = obj.getClass().getField(fieldName);
            f.setAccessible(true);
            Object v = f.get(obj);
            if (v instanceof Number) return ((Number) v).intValue();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static int gcd(int a, int b) {
        int x = Math.abs(a);
        int y = Math.abs(b);
        while (y != 0) {
            int t = x % y;
            x = y;
            y = t;
        }
        return Math.max(1, x);
    }

    private static int detectStep(List<Integer> coords) {
        if (coords.size() < 2) return 18;
        coords.sort(Integer::compareTo);
        int step = 0;
        for (int i = 1; i < coords.size(); i++) {
            int diff = coords.get(i) - coords.get(i - 1);
            if (diff <= 0) continue;
            step = step == 0 ? diff : gcd(step, diff);
        }
        return step <= 0 ? 18 : step;
    }

    private static Map<Integer, Object> buildSlotMapFromCoordinates(Object[] inputs, int width, int height) {
        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        for (Object input : inputs) {
            if (input == null) continue;
            Integer x = tryReadInt(input, "x");
            if (x == null) x = tryReadInt(input, "relx");
            Integer y = tryReadInt(input, "y");
            if (y == null) y = tryReadInt(input, "rely");
            if (x == null || y == null) continue;
            xs.add(x);
            ys.add(y);
        }

        if (xs.isEmpty() || ys.isEmpty()) return null;

        int minX = xs.stream().min(Integer::compareTo).orElse(0);
        int minY = ys.stream().min(Integer::compareTo).orElse(0);
        int stepX = detectStep(xs);
        int stepY = detectStep(ys);

        Map<Integer, Object> bySlot = new HashMap<>();
        for (Object input : inputs) {
            if (input == null) continue;
            Integer x = tryReadInt(input, "x");
            if (x == null) x = tryReadInt(input, "relx");
            Integer y = tryReadInt(input, "y");
            if (y == null) y = tryReadInt(input, "rely");
            if (x == null || y == null) continue;
            int col = Math.max(0, (int) Math.round((x - minX) / (double) stepX));
            int row = Math.max(0, (int) Math.round((y - minY) / (double) stepY));
            if (col >= width || row >= height) continue;
            bySlot.put(row * width + col, input);
        }
        return bySlot.isEmpty() ? null : bySlot;
    }
}

