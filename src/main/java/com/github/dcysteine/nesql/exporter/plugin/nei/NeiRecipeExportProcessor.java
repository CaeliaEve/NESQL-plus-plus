package com.github.dcysteine.nesql.exporter.plugin.nei;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.IUsageHandler;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.ItemFactory;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeTypeFactory;
import com.github.dcysteine.nesql.exporter.util.ItemUtil;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Exports all recipes from NEI recipe handlers.
 * This allows us to get recipes from any mod that registers with NEI,
 * including Avaritia, Thaumcraft, Botania, etc.
 */
public class NeiRecipeExportProcessor extends PluginHelper {

    private final RecipeTypeFactory recipeTypeFactory;
    private final ItemFactory itemFactory;

    // Cache for created recipe types
    private final Map<String, RecipeType> recipeTypeCache = new HashMap<>();

    public NeiRecipeExportProcessor(PluginExporter exporter) {
        super(exporter);
        this.recipeTypeFactory = new RecipeTypeFactory(exporter);
        this.itemFactory = new ItemFactory(exporter);
    }

    public void process() {
        try {
            Logger.chatMessage("=== NEI Recipe Export (V14 Optimized) ===");
            logger.info("Starting NEI recipe export in streaming mode...");

            // Use streaming export to avoid OutOfMemoryError
            Logger.chatMessage("Using streaming mode to prevent memory overflow");
            logger.info("Calling NEI streaming recipe exporter...");

            // ⚡ ONLY export crafting recipes - usage will be derived by backend
            Logger.chatMessage("⚡ 导出优化：跳过Usage配方，将由后端从Crafting配方逆推");
            Logger.chatMessage("⚡ 这样可以节省约6小时导出时间！");

            // Stream export crafting recipes
            int craftingCount = NeiRecipeBatchLoader.streamExportAllCraftingRecipes(this);

            // ❌ 不再导出usage配方 - 由后端逆推
            Logger.chatMessage("跳过Usage配方导出（节省6小时）");
            int usageCount = 0;

            Logger.chatMessage(String.format("✓ Crafting导出完成: %d 个配方", craftingCount));
            Logger.chatMessage("✅ 配方已存入数据库，Usage配方将由后端自动生成");
            logger.info("NEI export complete: {} crafting recipes (usage derived by backend)",
                    craftingCount);

            logger.info("Finished NEI recipe export!");
            Logger.chatMessage("=== NEI Recipe Export Complete ===");
        } catch (Exception e) {
            Logger.chatMessage("NEI export error: " + e.getMessage());
            logger.error("Error exporting NEI recipes", e);
        }
    }

    /**
     * Flush and clear the EntityManager to release memory.
     * This should be called periodically during long-running exports to prevent OutOfMemoryError.
     */
    public void flushEntityManager() {
        try {
            Logger.MOD.debug("Flushing EntityManager to free memory...");
            entityManager.flush();
            entityManager.clear();
            Logger.MOD.debug("EntityManager flushed and cleared successfully");
        } catch (Exception e) {
            Logger.MOD.error("Error flushing EntityManager", e);
            Logger.chatMessage("Warning: Error flushing database memory");
        }
    }

    private int exportRecipesFromHandler(IRecipeHandler handler, RecipeType recipeType) {
        int exported = 0;
        String handlerName = handler.getRecipeName();

        // Try to get recipe count using numRecipes() first
        int numRecipes = handler.numRecipes();
        logger.info("  [DEBUG] Handler: {}, numRecipes() returned: {}", handlerName, numRecipes);

        // If numRecipes() returns 0, try to access the internal recipe array directly
        if (numRecipes == 0) {
            logger.info("  [DEBUG] numRecipes() is 0, scanning ALL fields to find recipe arrays...");

            // AUTOMATICALLY SCAN ALL FIELDS
            try {
                java.lang.reflect.Field[] allFields = handler.getClass().getDeclaredFields();
                logger.info("  [DEBUG] Handler class has {} declared fields", allFields.length);

                Object[] recipes = null;
                String foundField = null;

                for (java.lang.reflect.Field field : allFields) {
                    field.setAccessible(true);
                    String fieldName = field.getName();
                    String fieldType = field.getType().getName();

                    try {
                        Object rawValue = field.get(handler);

                        if (rawValue == null) {
                            continue;
                        }

                        // Check if it's an Object[]
                        if (rawValue instanceof Object[]) {
                            Object[] arr = (Object[]) rawValue;
                            if (arr.length > 0) {
                                recipes = arr;
                                foundField = fieldName;
                                logger.info("  [DEBUG] FOUND! Field '{}' (Object[]) has {} elements",
                                    fieldName, arr.length);
                                break;
                            }
                        }
                        // Check if it's a List
                        else if (rawValue instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) rawValue;
                            if (list.size() > 0) {
                                recipes = list.toArray();
                                foundField = fieldName;
                                logger.info("  [DEBUG] FOUND! Field '{}' (List) has {} elements",
                                    fieldName, list.size());
                                break;
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("  [DEBUG] Error accessing field '{}': {}", fieldName, e.getMessage());
                    }
                }

                if (foundField != null && recipes != null && recipes.length > 0) {
                    numRecipes = recipes.length;
                    Logger.chatMessage(String.format("  ✅ AUTO-SCANNED: %s has %d recipes in field '%s'!",
                            handlerName, numRecipes, foundField));
                } else {
                    logger.info("  [DEBUG] No non-empty arrays/lists found in {} fields", allFields.length);
                }

            } catch (Exception e) {
                logger.error("  [DEBUG] Error during field scanning: {}", e.getMessage());
                e.printStackTrace();
            }
        }

        if (numRecipes == 0) {
            logger.debug("  [DEBUG] Skipping handler {} - no recipes found", handlerName);
            return 0;
        }

        logger.info("  [DEBUG] Starting to export {} recipes from {}", numRecipes, handlerName);

        for (int i = 0; i < numRecipes; i++) {
            try {
                RecipeBuilder builder = new RecipeBuilder(exporter, recipeType);

                // Get result stack
                PositionedStack result = handler.getResultStack(i);
                if (result != null && result.item != null) {
                    builder.addItemOutput(result.item);
                } else {
                    // No output? Skip this recipe
                    continue;
                }

                // Get ingredient stacks
                List<PositionedStack> ingredients = handler.getIngredientStacks(i);
                if (ingredients != null) {
                    addIngredientInputs(builder, ingredients, recipeType);
                }

                // Get other stacks (fuel, byproducts, etc.)
                List<PositionedStack> others = handler.getOtherStacks(i);
                if (others != null && !others.isEmpty()) {
                    // Store in additionalData for now
                    // Could be processed differently for specific recipe types
                }

                // Build the recipe
                com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe = builder.build();

                // Extract mod-specific metadata (aspects, blood cost, etc.)
                extractModSpecificMetadata(handler, i, builtRecipe, result);

                exported++;

                if (Logger.intermittentLog(exported)) {
                    logger.info("  Exported recipe {} of {}", exported, numRecipes);
                }

            } catch (Exception e) {
                logger.error("Error exporting recipe {} from handler {}",
                        i, handler.getHandlerId(), e);
            }
        }

        return exported;
    }

    private RecipeType getOrCreateRecipeType(String handlerId, String recipeTypeName, String category) {
        try {
            // Try to extract mod name from handler class name
            String modName = extractModName(handlerId);

            // Create a unique recipe type ID
            String recipeTypeId = modName + " - " + recipeTypeName;

            // Check cache first
            if (recipeTypeCache.containsKey(recipeTypeId)) {
                return recipeTypeCache.get(recipeTypeId);
            }

            // Try to get icon from first recipe result
            Item icon = getFallbackIcon();

            // Create new recipe type using RecipeTypeFactory
            RecipeType recipeType = recipeTypeFactory.newBuilder()
                    .setId(modName.toLowerCase().replace(" ", "_"), recipeTypeName.toLowerCase().replace(" ", "_"))
                    .setCategory(modName)
                    .setType(recipeTypeName)
                    .setIcon(icon)
                    .setShapeless(false) // Default to shaped
                    .setItemInputDimension(3, 3) // Default to 3x3
                    .setItemOutputDimension(1, 1)
                    .build();

            // Cache it
            recipeTypeCache.put(recipeTypeId, recipeType);
            return recipeType;

        } catch (Exception e) {
            logger.error("Error creating recipe type for handler: {}", handlerId, e);
            // Return a default recipe type from cache
            String defaultKey = "NEI - Unknown - " + category;
            if (!recipeTypeCache.containsKey(defaultKey)) {
                RecipeType defaultType = recipeTypeFactory.newBuilder()
                        .setId("nei", "unknown", category)
                        .setCategory("NEI")
                        .setType("Unknown")
                        .setIcon(getFallbackIcon())
                        .setShapeless(false)
                        .setItemInputDimension(1, 1)
                        .setItemOutputDimension(1, 1)
                        .build();
                recipeTypeCache.put(defaultKey, defaultType);
            }
            return recipeTypeCache.get(defaultKey);
        }
    }

    private String extractModName(String handlerClass) {
        // Extract mod name from package
        // e.g., "fox.spiteful.avaritia.compat.nei.ExtremeShapedRecipeHandler"
        // -> "Avaritia"

        try {
            String[] parts = handlerClass.split("\\.");
            if (parts.length >= 3) {
                // Usually the package structure is: domain.mod.subpackage...
                String packageName = parts[1]; // e.g., "spiteful" or "avaritia"

                // Try to find the actual mod name
                for (String part : parts) {
                    String lower = part.toLowerCase();
                    if (lower.contains("avaritia")) return "Avaritia";
                    if (lower.contains("thaumcraft")) return "Thaumcraft";
                    if (lower.contains("botania")) return "Botania";
                    if (lower.contains("gregtech")) return "GregTech";
                    if (lower.contains("bloodmagic")) return "Blood Magic";
                    if (lower.contains("awwayof")) return "Blood Magic";
                }

                // Fallback to capitalized package name
                return Character.toUpperCase(packageName.charAt(0)) + packageName.substring(1);
            }
        } catch (Exception e) {
            // Ignore
        }

        return "Unknown";
    }

    private Item getFallbackIcon() {
        try {
            ItemStack craftingTable = ItemUtil.getItemStack(Blocks.crafting_table).get();
            return itemFactory.get(craftingTable);
        } catch (Exception e) {
            logger.warn("Failed to get fallback icon", e);
            return null;
        }
    }

    private static Integer tryReadIntField(Object obj, String fieldName) {
        if (obj == null) return null;
        try {
            java.lang.reflect.Field field = obj.getClass().getField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Integer readStackX(PositionedStack stack) {
        Integer x = tryReadIntField(stack, "relx");
        if (x != null) return x;
        x = tryReadIntField(stack, "x");
        return x;
    }

    private static Integer readStackY(PositionedStack stack) {
        Integer y = tryReadIntField(stack, "rely");
        if (y != null) return y;
        y = tryReadIntField(stack, "y");
        return y;
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
        Set<Integer> uniqueSet = new HashSet<>(coords);
        if (uniqueSet.size() < 2) return 18;
        List<Integer> unique = new ArrayList<>(uniqueSet);
        Collections.sort(unique);
        int step = 0;
        for (int i = 1; i < unique.size(); i++) {
            int diff = unique.get(i) - unique.get(i - 1);
            if (diff <= 0) continue;
            step = step == 0 ? diff : gcd(step, diff);
        }
        return step <= 0 ? 18 : step;
    }

    private static int normalizeIndex(int value, int min, int step) {
        if (step <= 0) return 0;
        return Math.max(0, (int) Math.round((value - min) / (double) step));
    }

    private static int safeDimensionWidth(RecipeType recipeType) {
        if (recipeType == null || recipeType.getItemInputDimension() == null) return 0;
        return Math.max(0, recipeType.getItemInputDimension().getWidth());
    }

    private static int safeDimensionHeight(RecipeType recipeType) {
        if (recipeType == null || recipeType.getItemInputDimension() == null) return 0;
        return Math.max(0, recipeType.getItemInputDimension().getHeight());
    }

    private static boolean hasValidIngredient(PositionedStack ingredient) {
        return ingredient != null
                && ((ingredient.item != null && ingredient.item.getItem() != null)
                || (ingredient.items != null && ingredient.items.length > 0));
    }

    private Map<Integer, PositionedStack> buildSlotMapByPosition(
            List<PositionedStack> ingredients, RecipeType recipeType) {
        if (ingredients == null || ingredients.isEmpty()) return null;

        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();
        for (PositionedStack ingredient : ingredients) {
            if (!hasValidIngredient(ingredient)) continue;
            Integer x = readStackX(ingredient);
            Integer y = readStackY(ingredient);
            if (x == null || y == null) continue;
            xs.add(x);
            ys.add(y);
        }

        if (xs.isEmpty() || ys.isEmpty()) {
            return null;
        }

        int minX = Collections.min(xs);
        int minY = Collections.min(ys);
        int stepX = detectStep(xs);
        int stepY = detectStep(ys);

        int configuredWidth = safeDimensionWidth(recipeType);
        int configuredHeight = safeDimensionHeight(recipeType);

        int inferredWidth = 0;
        int inferredHeight = 0;
        for (int i = 0; i < xs.size(); i++) {
            inferredWidth = Math.max(inferredWidth, normalizeIndex(xs.get(i), minX, stepX) + 1);
            inferredHeight = Math.max(inferredHeight, normalizeIndex(ys.get(i), minY, stepY) + 1);
        }

        int width = Math.max(configuredWidth, inferredWidth);
        int height = Math.max(configuredHeight, inferredHeight);
        if (width <= 0 || height <= 0) return null;

        Map<Integer, PositionedStack> bySlot = new TreeMap<>();
        for (PositionedStack ingredient : ingredients) {
            if (!hasValidIngredient(ingredient)) continue;
            Integer x = readStackX(ingredient);
            Integer y = readStackY(ingredient);
            if (x == null || y == null) continue;

            int col = normalizeIndex(x, minX, stepX);
            int row = normalizeIndex(y, minY, stepY);
            if (col < 0 || row < 0) continue;
            if (col >= width || row >= height) continue;

            int slotIndex = row * width + col;
            bySlot.put(slotIndex, ingredient);
        }
        return bySlot.isEmpty() ? null : bySlot;
    }

    private void addIngredientToBuilder(RecipeBuilder builder, PositionedStack ingredient, Integer slotIndex) {
        if (ingredient == null) {
            if (slotIndex != null) builder.skipItemInputAt(slotIndex);
            else builder.skipItemInput();
            return;
        }
        if (ingredient.items != null && ingredient.items.length > 0) {
            if (slotIndex != null) builder.addItemGroupInputAt(slotIndex, ingredient.items);
            else builder.addItemGroupInput(ingredient.items);
            return;
        }
        if (ingredient.item == null || ingredient.item.getItem() == null) {
            if (slotIndex != null) builder.skipItemInputAt(slotIndex);
            else builder.skipItemInput();
            return;
        }
        if (slotIndex != null) builder.addItemInputAt(slotIndex, ingredient.item);
        else builder.addItemInput(ingredient.item);
    }

    private void addIngredientInputs(
            RecipeBuilder builder, List<PositionedStack> ingredients, RecipeType recipeType) {
        if (ingredients == null || ingredients.isEmpty()) return;
        Map<Integer, PositionedStack> bySlot = buildSlotMapByPosition(ingredients, recipeType);
        if (bySlot != null) {
            for (Map.Entry<Integer, PositionedStack> entry : bySlot.entrySet()) {
                addIngredientToBuilder(builder, entry.getValue(), entry.getKey());
            }
            return;
        }

        for (PositionedStack ingredient : ingredients) {
            addIngredientToBuilder(builder, ingredient, null);
        }
    }

    private int exportUsageRecipesFromHandler(IUsageHandler handler, RecipeType recipeType) {
        int exported = 0;
        int numRecipes = handler.numRecipes();

        for (int i = 0; i < numRecipes; i++) {
            try {
                RecipeBuilder builder = new RecipeBuilder(exporter, recipeType);

                // For usage handlers, the getIngredientStacks returns the item being used
                // and getResultStack returns what it produces
                // In usage context, we want to show what this item can be used to make

                // Get result stack (what is produced)
                PositionedStack result = handler.getResultStack(i);
                if (result != null && result.item != null) {
                    builder.addItemOutput(result.item);
                }

                // Get ingredient stacks (what is being used)
                List<PositionedStack> ingredients = handler.getIngredientStacks(i);
                if (ingredients != null && !ingredients.isEmpty()) {
                    addIngredientInputs(builder, ingredients, recipeType);
                }

                // Get other stacks (fuel, byproducts, etc.)
                List<PositionedStack> others = handler.getOtherStacks(i);
                if (others != null && !others.isEmpty()) {
                    // Store in additionalData for now
                }

                builder.build();
                exported++;

                if (Logger.intermittentLog(exported)) {
                    logger.info("  Exported usage recipe {} of {}", exported, numRecipes);
                }

            } catch (Exception e) {
                logger.error("Error exporting usage recipe {} from handler {}",
                        i, handler.getHandlerId(), e);
            }
        }

        return exported;
    }

    /**
     * Extract mod-specific metadata from recipes (aspects, blood cost, etc.)
     */
    private void extractModSpecificMetadata(
            codechicken.nei.recipe.IRecipeHandler handler,
            int recipeIndex,
            com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe,
            PositionedStack result) {
        String handlerId = handler.getHandlerId();
        String lowerId = handlerId.toLowerCase();

        try {
            // Thaumcraft - extract aspects
            if (lowerId.contains("thaum") || lowerId.contains("tc")) {
                extractThaumcraftAspects(handler, recipeIndex, builtRecipe);
            }
            // Blood Magic - extract blood cost
            else if (lowerId.contains("blood") || lowerId.contains("awwayof")) {
                extractBloodMagicCost(handler, recipeIndex, builtRecipe);
            }
            // Witchery - extract special data
            else if (lowerId.contains("witch")) {
                extractWitcheryData(handler, recipeIndex, builtRecipe);
            }
        } catch (Exception e) {
            logger.debug("Failed to extract metadata for " + handlerId, e);
        }
    }

    /**
     * Extract Thaumcraft aspect data from NEI recipe
     */
    private void extractThaumcraftAspects(
            codechicken.nei.recipe.IRecipeHandler handler,
            int recipeIndex,
            com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe) {
        try {
            // Try to access the internal recipe object
            Object recipe = getRecipeFromHandler(handler, recipeIndex);

            if (recipe != null) {
                // Try to call getAspects() method
                try {
                    java.lang.reflect.Method getAspects = recipe.getClass().getMethod("getAspects");
                    Object aspectList = getAspects.invoke(recipe);

                    if (aspectList != null) {
                        Map<String, Integer> aspectMap = new HashMap<>();

                        // Try to get aspect array
                        try {
                            java.lang.reflect.Method getAspectsArray = aspectList.getClass().getMethod("getAspects");
                            Object[] aspects = (Object[]) getAspectsArray.invoke(aspectList);

                            if (aspects != null && aspects.length > 0) {
                                java.lang.reflect.Method getAmount = aspectList.getClass().getMethod("getAmount", Object.class);
                                java.lang.reflect.Method getTag = aspects[0].getClass().getMethod("getTag");

                                for (Object aspect : aspects) {
                                    String tag = (String) getTag.invoke(aspect);
                                    int amount = (Integer) getAmount.invoke(aspectList, aspect);
                                    aspectMap.put(tag, amount);
                                }
                            }
                        } catch (Exception e) {
                            logger.debug("Could not extract aspect array", e);
                        }

                        // Register metadata if we found aspects
                        if (!aspectMap.isEmpty()) {
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("aspects", aspectMap);

                            com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                                    builtRecipe.getId(),
                                    new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                                            "NEI_Thaumcraft", metadata
                                    )
                            );
                            logger.debug("Extracted aspects for recipe {}: {}", builtRecipe.getId(), aspectMap);
                        }
                    }
                } catch (NoSuchMethodException e) {
                    logger.debug("No getAspects method on recipe object");
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract Thaumcraft aspects", e);
        }
    }

    /**
     * Extract Blood Magic cost data from NEI recipe
     */
    private void extractBloodMagicCost(
            codechicken.nei.recipe.IRecipeHandler handler,
            int recipeIndex,
            com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe) {
        try {
            Object recipe = getRecipeFromHandler(handler, recipeIndex);

            if (recipe != null) {
                Map<String, Object> metadata = new HashMap<>();

                // Try to get tier
                try {
                    java.lang.reflect.Field tierField = recipe.getClass().getDeclaredField("tier");
                    tierField.setAccessible(true);
                    int tier = tierField.getInt(recipe);
                    metadata.put("tier", tier);
                } catch (NoSuchFieldException e) {
                    // tier field doesn't exist, skip
                }

                // Try to get blood cost (try multiple possible field names)
                String[] possibleFields = {"bloodCost", "cost", "requiredBlood"};
                for (String fieldName : possibleFields) {
                    try {
                        java.lang.reflect.Field costField = recipe.getClass().getDeclaredField(fieldName);
                        costField.setAccessible(true);
                        int cost = costField.getInt(recipe);
                        metadata.put("bloodCost", cost);
                        break; // Found it, stop looking
                    } catch (NoSuchFieldException e) {
                        // Try next field name
                    }
                }

                // Register metadata if we found anything
                if (!metadata.isEmpty()) {
                    com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                            builtRecipe.getId(),
                            new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                                    "NEI_BloodMagic", metadata
                            )
                    );
                    logger.debug("Extracted Blood Magic metadata for recipe {}: {}", builtRecipe.getId(), metadata);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract Blood Magic metadata", e);
        }
    }

    /**
     * Extract Witchery special data from NEI recipe
     */
    private void extractWitcheryData(
            codechicken.nei.recipe.IRecipeHandler handler,
            int recipeIndex,
            com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe) {
        try {
            Object recipe = getRecipeFromHandler(handler, recipeIndex);

            if (recipe != null) {
                Map<String, Object> metadata = new HashMap<>();

                // Try to extract any Witchery-specific data
                // This is a placeholder - actual fields depend on Witchery's implementation
                try {
                    // Example: extract power, dimension cost, etc.
                    java.lang.reflect.Field[] fields = recipe.getClass().getDeclaredFields();
                    for (java.lang.reflect.Field field : fields) {
                        String fieldName = field.getName().toLowerCase();
                        if (fieldName.contains("power") || fieldName.contains("cost") ||
                            fieldName.contains("dimension") || fieldName.contains("witch")) {
                            field.setAccessible(true);
                            try {
                                Object value = field.get(recipe);
                                if (value instanceof Integer || value instanceof Float ||
                                    value instanceof Boolean || value instanceof String) {
                                    metadata.put(field.getName(), value);
                                }
                            } catch (Exception e) {
                                // Skip this field
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Error extracting Witchery fields", e);
                }

                // Register metadata if we found anything
                if (!metadata.isEmpty()) {
                    com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                            builtRecipe.getId(),
                            new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                                    "NEI_Witchery", metadata
                            )
                    );
                    logger.debug("Extracted Witchery metadata for recipe {}: {}", builtRecipe.getId(), metadata);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract Witchery metadata", e);
        }
    }

    /**
     * Get the internal recipe object from NEI handler
     */
    private Object getRecipeFromHandler(codechicken.nei.recipe.IRecipeHandler handler, int recipeIndex) {
        try {
            // Try to access handler's internal recipe array
            java.lang.reflect.Field recipesField = handler.getClass().getDeclaredField("arecipes");
            recipesField.setAccessible(true);
            Object[] recipes = (Object[]) recipesField.get(handler);
            if (recipes != null && recipeIndex < recipes.length) {
                return recipes[recipeIndex];
            }
        } catch (Exception e) {
            logger.debug("Could not access recipe from handler", e);
        }
        return null;
    }

    /**
     * Export recipes from a single crafting handler (for streaming mode).
     * This method is called by NeiRecipeBatchLoader for each handler individually.
     *
     * @param handlerId The handler's ID
     * @param handlerName The handler's name
     * @param handler The handler instance with loaded recipes
     * @return The number of recipes exported
     */
    public int exportSingleCraftingHandler(String handlerId, String handlerName,
                                             codechicken.nei.recipe.TemplateRecipeHandler handler) {
        try {
            // Create or get RecipeType for this handler
            RecipeType recipeType = getOrCreateRecipeType(handlerId, handlerName, "crafting");

            int exported = 0;
            int numRecipes = handler.numRecipes();

            // Export each recipe from this handler
            for (int i = 0; i < numRecipes; i++) {
                // Declare variables outside try block for exception handling
                PositionedStack result = null;
                List<PositionedStack> ingredients = null;

                try {
                    RecipeBuilder builder = new RecipeBuilder(exporter, recipeType);

                    // Get result stack
                    result = handler.getResultStack(i);
                    if (result != null && result.item != null) {
                        builder.addItemOutput(result.item);
                    } else {
                        // No output? Skip this recipe
                        continue;
                    }

                    // Get ingredient stacks
                    ingredients = handler.getIngredientStacks(i);
                    if (ingredients != null) {
                        addIngredientInputs(builder, ingredients, recipeType);
                    }

                    // Build the recipe
                    com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe = builder.build();
                    exported++;

                } catch (Exception e) {
                    // ⭐ 详细异常日志 - 用于诊断导出失败
                    logger.error("=== RECIPE EXPORT FAILED ===");
                    logger.error("Handler: {}, Recipe: {}/{}", handlerName, i, numRecipes);
                    logger.error("RecipeType: {}", recipeType.getId());

                    // 记录RecipeBuilder状态
                    try {
                        logger.error("Builder state: hasExporter={}, hasType={}",
                                exporter != null, recipeType != null);
                    } catch (Exception ex) {
                        logger.error("Builder state check failed: {}", ex.getMessage());
                    }

                    // 记录Result Stack详情
                    if (result != null) {
                        logger.error("Result: {}, toString()={}",
                                result.getClass().getName(), result.toString());
                        if (result.item != null) {
                            logger.error("Item: {}, displayName={}",
                                result.item.getClass().getName(),
                                result.item.getDisplayName());
                        } else {
                            logger.error("Result.item is NULL");
                        }
                    } else {
                        logger.error("Result stack is NULL");
                    }

                    // 记录Ingredients详情
                    if (ingredients != null) {
                        logger.error("Ingredients count: {}", ingredients.size());
                        for (int idx = 0; idx < Math.min(3, ingredients.size()); idx++) {
                            PositionedStack ing = ingredients.get(idx);
                            logger.error("Ing[{}]: item={}, items={}",
                                idx, ing.item, ing.items);
                        }
                    } else {
                        logger.error("Ingredients is NULL");
                    }

                    // 记录异常类型和消息
                    logger.error("Exception type: {}", e.getClass().getName());
                    logger.error("Exception message: {}", e.getMessage());
                    logger.error("Stack trace:", e);

                    // 如果是flush相关异常，特别标记
                    if (e.getMessage() != null) {
                        String msg = e.getMessage().toLowerCase();
                        if (msg.contains("detached") ||
                            msg.contains("flush") ||
                            msg.contains("cleared") ||
                            msg.contains("closed")) {
                            Logger.chatMessage("⚠️ FLUSH ERROR: Recipe object may be detached!");
                        }
                    }
                }
            }

            return exported;

        } catch (Exception e) {
            logger.error("Error exporting handler: " + handlerName, e);
            return 0;
        }
    }

    /**
     * Export recipes from a single usage handler (for streaming mode).
     *
     * @param handlerId The handler's ID
     * @param handlerName The handler's name
     * @param handler The handler instance with loaded recipes
     * @return The number of recipes exported
     */
    public int exportSingleUsageHandler(String handlerId, String handlerName,
                                          codechicken.nei.recipe.TemplateRecipeHandler handler) {
        try {
            // Create or get RecipeType for this handler
            RecipeType recipeType = getOrCreateRecipeType(handlerId, handlerName, "usage");

            int exported = 0;
            int numRecipes = handler.numRecipes();

            // Export each recipe from this handler
            for (int i = 0; i < numRecipes; i++) {
                try {
                    RecipeBuilder builder = new RecipeBuilder(exporter, recipeType);

                    // Get result stack
                    PositionedStack result = handler.getResultStack(i);
                    if (result != null && result.item != null) {
                        builder.addItemOutput(result.item);
                    } else {
                        // No output? Skip this recipe
                        continue;
                    }

                    // Get ingredient stacks
                    List<PositionedStack> ingredients = handler.getIngredientStacks(i);
                    if (ingredients != null) {
                        addIngredientInputs(builder, ingredients, recipeType);
                    }

                    // Build the recipe
                    com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe = builder.build();
                    exported++;

                } catch (Exception e) {
                    // ⭐ 详细异常日志
                    logger.error("=== USAGE RECIPE EXPORT FAILED ===");
                    logger.error("Handler: {}, Recipe: {}/{}", handlerName, i, numRecipes);
                    logger.error("Exception type: {}", e.getClass().getName());
                    logger.error("Exception message: {}", e.getMessage());
                    logger.error("Stack trace:", e);

                    // 标记flush错误
                    if (e.getMessage() != null && e.getMessage().toLowerCase().contains("detached")) {
                        Logger.chatMessage("⚠️ FLUSH ERROR: Usage recipe object detached");
                    }
                }
            }

            return exported;

        } catch (Exception e) {
            logger.error("Error exporting usage handler: " + handlerName, e);
            return 0;
        }
    }
}
