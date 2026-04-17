package com.github.dcysteine.nesql.exporter.plugin.nei;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.IUsageHandler;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiHandlerMetadataEntry;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiHandlerMetadataRepository;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.ItemFactory;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeBuilder;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.RecipeTypeFactory;
import com.github.dcysteine.nesql.exporter.util.ItemUtil;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.github.dcysteine.nesql.sql.base.recipe.RecipeType;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
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
    private final NeiHandlerMetadataRepository handlerMetadataRepository;

    // Cache for created recipe types
    private final Map<String, RecipeType> recipeTypeCache = new HashMap<>();

    public NeiRecipeExportProcessor(PluginExporter exporter) {
        super(exporter);
        this.recipeTypeFactory = new RecipeTypeFactory(exporter);
        this.itemFactory = new ItemFactory(exporter);
        this.handlerMetadataRepository = NeiHandlerMetadataRepository.getInstance();
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
            if (NeiExportDebugFilter.getMode() == NeiExportDebugFilter.Mode.BOTANIA_FAMILY) {
                craftingCount += exportKnownBotaniaCoreRecipes();
            }

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

    private int exportKnownBotaniaCoreRecipes() {
        try {
            Object manaResourceObject = Class.forName("vazkii.botania.common.item.ModItems")
                    .getField("manaResource")
                    .get(null);
            if (!(manaResourceObject instanceof net.minecraft.item.Item)) {
                logger.warn("Botania core recipe fallback skipped: ModItems.manaResource unavailable");
                return 0;
            }

            net.minecraft.item.Item manaResource = (net.minecraft.item.Item) manaResourceObject;
            RecipeType manaPool = recipeTypeFactory.newBuilder()
                    .setId("botania", "mana_pool_core")
                    .setCategory("Botania")
                    .setType("Mana Pool")
                    .setIcon(getFallbackIcon())
                    .setShapeless(true)
                    .setItemInputDimension(1, 1)
                    .setItemOutputDimension(1, 1)
                    .build();
            RecipeType terraPlate = recipeTypeFactory.newBuilder()
                    .setId("botania", "terra_plate_core")
                    .setCategory("Botania")
                    .setType("Terra Plate")
                    .setIcon(getFallbackIcon())
                    .setShapeless(true)
                    .setItemInputDimension(3, 1)
                    .setItemOutputDimension(1, 1)
                    .build();

            int exported = 0;
            exported += buildManaPoolRecipe(manaPool, new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_ingot), new net.minecraft.item.ItemStack(manaResource, 1, 0), 3000);
            exported += buildManaPoolRecipe(manaPool, new net.minecraft.item.ItemStack(net.minecraft.init.Items.ender_pearl), new net.minecraft.item.ItemStack(manaResource, 1, 1), 6000);
            exported += buildManaPoolRecipe(manaPool, new net.minecraft.item.ItemStack(net.minecraft.init.Items.diamond), new net.minecraft.item.ItemStack(manaResource, 1, 2), 10000);

            RecipeBuilder terraBuilder = new RecipeBuilder(exporter, terraPlate);
            terraBuilder.addItemInput(new net.minecraft.item.ItemStack(manaResource, 1, 0));
            terraBuilder.addItemInput(new net.minecraft.item.ItemStack(manaResource, 1, 1));
            terraBuilder.addItemInput(new net.minecraft.item.ItemStack(manaResource, 1, 2));
            terraBuilder.addItemOutput(new net.minecraft.item.ItemStack(manaResource, 1, 4));
            com.github.dcysteine.nesql.sql.base.recipe.Recipe terraRecipe = terraBuilder.build();
            Map<String, Object> terraMetadata = new HashMap<>();
            terraMetadata.put("manaCost", 500000);
            terraMetadata.put("ticks", 100);
            terraMetadata.put("synthetic", true);
            com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                    terraRecipe.getId(),
                    new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("TerraPlate", terraMetadata)
            );
            exported++;

            logger.info("Exported {} Botania core fallback recipes", exported);
            return exported;
        } catch (Exception e) {
            logger.error("Failed to export Botania core fallback recipes", e);
            return 0;
        }
    }

    private int buildManaPoolRecipe(RecipeType recipeType, ItemStack input, ItemStack output, int manaCost) {
        RecipeBuilder builder = new RecipeBuilder(exporter, recipeType);
        builder.addItemInput(input);
        builder.addItemOutput(output);
        com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe = builder.build();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("manaCost", manaCost);
        metadata.put("synthetic", true);
        com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                builtRecipe.getId(),
                new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata("ManaPool", metadata)
        );
        return 1;
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
                Object neiRecipe = getRecipeFromHandler(handler, i);
                RecipeType effectiveRecipeType =
                        resolveEffectiveRecipeType(handler.getHandlerId(), handlerName, handler, recipeType, neiRecipe);
                RecipeBuilder builder = new RecipeBuilder(exporter, effectiveRecipeType);

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
                    addIngredientInputs(builder, ingredients, effectiveRecipeType);
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

    private RecipeType getOrCreateRecipeType(
            String handlerId, String recipeTypeName, String category, Object handler) {
        try {
            NeiHandlerMetadataEntry metadata = findHandlerMetadata(handlerId, handler);

            String modName = metadata != null && !isBlank(metadata.getModName())
                    ? metadata.getModName()
                    : extractModName(handlerId);
            String typeName = overrideRecipeTypeName(handlerId, recipeTypeName, handler);
            String typeId = sanitizeRecipeTypeId(
                    metadata != null && !isBlank(metadata.getHandler())
                            ? metadata.getHandler()
                            : typeName);

            // Create a unique recipe type ID
            String recipeTypeId = modName + " - " + typeId;

            // Check cache first
            if (recipeTypeCache.containsKey(recipeTypeId)) {
                return recipeTypeCache.get(recipeTypeId);
            }

            Item icon = resolveHandlerIcon(metadata);
            if (icon == null) {
                icon = getFallbackIcon();
            }

            // Create new recipe type using RecipeTypeFactory
            RecipeType recipeType = recipeTypeFactory.newBuilder()
                    .setId(normalizeIdPart(modName), typeId)
                    .setCategory(modName)
                    .setType(typeName)
                    .setIcon(icon)
                    .setIconInfo(metadata != null ? nullToEmpty(metadata.getItemName()) : "")
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

    private String overrideRecipeTypeName(String handlerId, String recipeTypeName, Object handler) {
        String handlerClass = handler == null ? "" : handler.getClass().getName();
        String combined = (handlerId + " " + handlerClass + " " + recipeTypeName).toLowerCase();

        if (combined.contains("infusionrecipehandler")) {
            return "奥术注魔";
        }
        if (combined.contains("cruciblerecipehandler")) {
            return "坩埚";
        }
        if (combined.contains("neialtarrecipehandler")) {
            return "Blood Altar";
        }
        if (combined.contains("neialchemyrecipehandler")) {
            return "Alchemy Array";
        }
        if (combined.contains("neibindingritualhandler")) {
            return "Binding Ritual";
        }
        if (combined.contains("arcaneshapedrecipehandler")) {
            return "有序奥术合成";
        }
        if (combined.contains("arcaneshapelessrecipehandler")) {
            return "无序奥术合成";
        }
        if (combined.contains("recipehandlerrunicaltar")) {
            return "符文祭坛";
        }
        if (combined.contains("recipehandlermanapool")) {
            return "Mana Pool";
        }
        if (combined.contains("recipehandlerpuredaisy")) {
            return "Pure Daisy";
        }
        if (combined.contains("recipehandlerelventrade")) {
            return "Elven Trade";
        }
        if (combined.contains("recipehandlerpetalapothecary")) {
            return "Petal Apothecary";
        }

        return recipeTypeName;
    }

    private RecipeType resolveEffectiveRecipeType(
            String handlerId,
            String handlerName,
            Object handler,
            RecipeType baseRecipeType,
            Object recipe) {
        RecipeTypeCorrection correction = inferRecipeTypeCorrection(handlerId, handlerName, handler, recipe);
        if (correction == null) {
            return baseRecipeType;
        }

        return getOrCreateRecipeTypeVariant(handlerId, handler, baseRecipeType, correction);
    }

    private RecipeTypeCorrection inferRecipeTypeCorrection(
            String handlerId,
            String handlerName,
            Object handler,
            Object recipe) {
        if (recipe == null) {
            return null;
        }

        String recipeClass = recipe.getClass().getName().toLowerCase();
        String handlerClass = handler == null ? "" : handler.getClass().getName().toLowerCase();
        String combined = (handlerId + " " + handlerName + " " + handlerClass + " " + recipeClass).toLowerCase();

        if (combined.contains("thaum") || combined.contains("tcnei") || combined.contains("timeconqueror")) {
            if (recipeClass.contains("infusion")) {
                return new RecipeTypeCorrection("\u5965\u672f\u6ce8\u9b54", false);
            }
            if (recipeClass.contains("crucible")) {
                return new RecipeTypeCorrection("\u5769\u57da", false);
            }
            if (recipeClass.contains("arcane")) {
                boolean shapeless = recipeClass.contains("shapeless");
                return new RecipeTypeCorrection(
                        shapeless
                                ? "\u65e0\u5e8f\u5965\u672f\u5408\u6210"
                                : "\u6709\u5e8f\u5965\u672f\u5408\u6210",
                        shapeless);
            }
        }

        return null;
    }

    private RecipeType getOrCreateRecipeTypeVariant(
            String handlerId,
            Object handler,
            RecipeType baseRecipeType,
            RecipeTypeCorrection correction) {
        NeiHandlerMetadataEntry metadata = findHandlerMetadata(handlerId, handler);
        String modName = metadata != null && !isBlank(metadata.getModName())
                ? metadata.getModName()
                : baseRecipeType.getCategory();
        String typeId = sanitizeRecipeTypeId(correction.typeName);
        String recipeTypeId = modName + " - " + typeId + " - " + correction.shapeless;

        RecipeType cached = recipeTypeCache.get(recipeTypeId);
        if (cached != null) {
            return cached;
        }

        RecipeType correctedType = recipeTypeFactory.newBuilder()
                .setId(normalizeIdPart(modName), typeId)
                .setCategory(modName)
                .setType(correction.typeName)
                .setIcon(baseRecipeType.getIcon())
                .setIconInfo(baseRecipeType.getIconInfo())
                .setShapeless(correction.shapeless)
                .setItemInputDimension(baseRecipeType.getItemInputDimension())
                .setFluidInputDimension(baseRecipeType.getFluidInputDimension())
                .setItemOutputDimension(baseRecipeType.getItemOutputDimension())
                .setFluidOutputDimension(baseRecipeType.getFluidOutputDimension())
                .build();

        recipeTypeCache.put(recipeTypeId, correctedType);
        return correctedType;
    }

    private static final class RecipeTypeCorrection {
        private final String typeName;
        private final boolean shapeless;

        private RecipeTypeCorrection(String typeName, boolean shapeless) {
            this.typeName = typeName;
            this.shapeless = shapeless;
        }
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

    private NeiHandlerMetadataEntry findHandlerMetadata(String handlerId, Object handler) {
        NeiHandlerMetadataEntry metadata = handlerMetadataRepository.findByHandler(handlerId);
        if (metadata != null) {
            return metadata;
        }
        if (handler != null) {
            metadata = handlerMetadataRepository.findByHandler(handler.getClass().getName());
            if (metadata != null) {
                return metadata;
            }
        }
        return null;
    }

    private Item resolveHandlerIcon(NeiHandlerMetadataEntry metadata) {
        if (metadata == null || isBlank(metadata.getItemName())) {
            return null;
        }
        ItemStack stack = resolveItemStack(metadata.getItemName());
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        try {
            return itemFactory.get(stack);
        } catch (Exception e) {
            logger.debug("Failed to resolve NEI handler icon: {}", metadata.getItemName(), e);
            return null;
        }
    }

    private ItemStack resolveItemStack(String itemName) {
        if (isBlank(itemName)) {
            return null;
        }
        String trimmed = itemName.trim();
        String[] parts = trimmed.split(":");
        if (parts.length < 2) {
            return null;
        }

        String modId = parts[0];
        String name = parts[1];
        int damage = 0;
        if (parts.length >= 3) {
            try {
                damage = Integer.parseInt(parts[2]);
            } catch (NumberFormatException ignored) {
                damage = 0;
            }
        }

        net.minecraft.item.Item item = GameRegistry.findItem(modId, name);
        if (item != null) {
            return new ItemStack(item, 1, damage);
        }

        Block block = GameRegistry.findBlock(modId, name);
        if (block != null) {
            return new ItemStack(block, 1, damage);
        }
        return null;
    }

    private void registerHandlerMetadata(
            Object handler,
            String handlerId,
            com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe) {
        NeiHandlerMetadataEntry metadata = findHandlerMetadata(handlerId, handler);
        if (metadata == null || builtRecipe == null) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("handler", nullToEmpty(metadata.getHandler()));
        data.put("handlerId", nullToEmpty(handlerId));
        data.put("handlerClass", handler == null ? "" : handler.getClass().getName());
        data.put("modName", nullToEmpty(metadata.getModName()));
        data.put("modId", nullToEmpty(metadata.getModId()));
        data.put("handlerIcon", nullToEmpty(metadata.getItemName()));
        data.put("handlerHeight", metadata.getHandlerHeightInt());
        data.put("handlerWidth", metadata.getHandlerWidthInt());
        data.put("maxRecipesPerPage", metadata.getMaxRecipesPerPageInt());
        data.put("yShift", metadata.getYShiftInt());
        data.put("imageResource", nullToEmpty(metadata.getImageResource()));
        data.put("itemNotes", nullToEmpty(metadata.getItemNotes()));

        com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                builtRecipe.getId(),
                new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                        "NEI_Handler", data
                )
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeIdPart(String value) {
        if (isBlank(value)) {
            return "nei";
        }
        return value.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");
    }

    private static String sanitizeRecipeTypeId(String value) {
        String normalized = normalizeIdPart(value);
        return normalized.isEmpty() ? "unknown" : normalized;
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
        String lowerHandlerClass = handler.getClass().getName().toLowerCase();
        String lowerRecipeName = handler.getRecipeName() == null ? "" : handler.getRecipeName().toLowerCase();
        String combinedHandlerText = lowerId + " " + lowerHandlerClass + " " + lowerRecipeName;

        try {
            if (combinedHandlerText.contains("worldcraft") || combinedHandlerText.contains("neiworldcrafting")) {
                extractAe2WorldCraftingData(handler, recipeIndex, builtRecipe);
            }
            // Thaumcraft - extract aspects
            if (combinedHandlerText.contains("thaum")
                    || combinedHandlerText.contains("tcnei")
                    || combinedHandlerText.contains("timeconqueror")) {
                extractThaumcraftAspects(handler, recipeIndex, builtRecipe);
            }
            // Blood Magic - extract blood cost
            else if (combinedHandlerText.contains("blood") || combinedHandlerText.contains("awwayof")) {
                extractBloodMagicCost(handler, recipeIndex, builtRecipe);
            }
            else if (combinedHandlerText.contains("breeding") || combinedHandlerText.contains("produce")
                    || combinedHandlerText.contains("neiaddons.forestry")) {
                extractForestryMetadata(handler, recipeIndex, builtRecipe);
            }
            else if (combinedHandlerText.contains("botania")
                    || combinedHandlerText.contains("recipehandlerrunicaltar")
                    || combinedHandlerText.contains("recipehandlermanapool")
                    || combinedHandlerText.contains("recipehandlerpuredaisy")
                    || combinedHandlerText.contains("recipehandlerelventrade")
                    || combinedHandlerText.contains("recipehandlerpetalapothecary")) {
                extractBotaniaMetadata(handler, recipeIndex, builtRecipe);
            }
            // Witchery - extract special data
            else if (combinedHandlerText.contains("witch")) {
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
            Map<String, Object> metadata = new HashMap<>();

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
                            metadata.put("aspects", aspectMap);
                        }
                    }
                } catch (NoSuchMethodException e) {
                    logger.debug("No getAspects method on recipe object");
                }

                extractThaumcraftLayoutMetadata(recipe, handler, recipeIndex, metadata);
            }

            if (!metadata.isEmpty()) {
                com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                                "NEI_Thaumcraft", metadata
                        )
                );
                logger.debug("Extracted Thaumcraft metadata for recipe {}: {}", builtRecipe.getId(), metadata);
            }
        } catch (Exception e) {
            logger.debug("Failed to extract Thaumcraft aspects", e);
        }
    }

    private void extractThaumcraftLayoutMetadata(
            Object recipe,
            codechicken.nei.recipe.IRecipeHandler handler,
            int recipeIndex,
            Map<String, Object> metadata) {
        String recipeClass = recipe.getClass().getName().toLowerCase();
        if (recipeClass.contains("infusion")) {
            metadata.put("thaumcraftLayout", "infusion");
            metadata.put("correctedMachineType", "奥术注魔");
        } else if (recipeClass.contains("arcane")) {
            metadata.put("thaumcraftLayout", "arcane");
            metadata.put(
                    "correctedMachineType",
                    recipeClass.contains("shapeless") ? "无序奥术合成" : "有序奥术合成");
        }

        if (recipeClass.contains("infusion")) {
            metadata.put("correctedMachineType", "\u5965\u672f\u6ce8\u9b54");
        } else if (recipeClass.contains("arcane")) {
            metadata.put(
                    "correctedMachineType",
                    recipeClass.contains("shapeless")
                            ? "\u65e0\u5e8f\u5965\u672f\u5408\u6210"
                            : "\u6709\u5e8f\u5965\u672f\u5408\u6210");
        }

        String research = readThaumcraftResearch(recipe);
        if (research != null && !research.isEmpty()) {
            metadata.put("research", research);
        }

        Integer instability = readThaumcraftInstability(recipe);
        if (instability != null) {
            metadata.put("instability", instability);
        }

        List<PositionedStack> ingredients = null;
        try {
            java.lang.reflect.Method getIngredients = recipe.getClass().getMethod("getIngredients");
            Object value = getIngredients.invoke(recipe);
            if (value instanceof List<?>) {
                @SuppressWarnings("unchecked")
                List<PositionedStack> cast = (List<PositionedStack>) value;
                ingredients = cast;
            }
        } catch (Exception ignored) {
        }

        if (ingredients == null) {
            try {
                ingredients = handler.getIngredientStacks(recipeIndex);
            } catch (Exception ignored) {
            }
        }

        if (ingredients == null || ingredients.isEmpty()) {
            return;
        }

        List<Integer> componentSlotOrder = new ArrayList<>();
        Integer centerInputSlotIndex = null;
        String centralItemId = null;

        for (PositionedStack ingredient : ingredients) {
            if (!hasValidIngredient(ingredient)) continue;

            net.minecraft.item.ItemStack representative = ingredient.item;
            if (representative == null && ingredient.items != null && ingredient.items.length > 0) {
                representative = ingredient.items[0];
            }
            if (representative == null || representative.getItem() == null) continue;
            if (isThaumcraftAspectStack(representative)) continue;

            Integer x = readStackX(ingredient);
            Integer y = readStackY(ingredient);
            if (x == null || y == null) continue;

            int encodedSlot = encodePositionedSlotIndex(x, y);
            if (centerInputSlotIndex == null) {
                centerInputSlotIndex = encodedSlot;
                centralItemId = itemFactory.get(representative).getId();
            } else {
                componentSlotOrder.add(encodedSlot);
            }
        }

        if (centerInputSlotIndex != null) {
            metadata.put("centerInputSlotIndex", centerInputSlotIndex);
        }
        if (centralItemId != null) {
            metadata.put("centralItemId", centralItemId);
        }
        if (!componentSlotOrder.isEmpty()) {
            metadata.put("componentSlotOrder", componentSlotOrder);
        }
    }

    private void extractBotaniaMetadata(
            codechicken.nei.recipe.IRecipeHandler handler,
            int recipeIndex,
            com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe) {
        try {
            Object recipe = getRecipeFromHandler(handler, recipeIndex);
            if (recipe == null) {
                return;
            }

            Map<String, Object> metadata = new HashMap<>();
            String recipeClass = recipe.getClass().getName().toLowerCase();
            String handlerClass = handler.getClass().getName().toLowerCase();
            String combined = handlerClass + " " + recipeClass;

            if (combined.contains("runicaltar")) {
                metadata.put("specialRecipeType", "RuneAltar");
                metadata.put("correctedMachineType", "符文祭坛");
            } else if (combined.contains("manapool")) {
                metadata.put("specialRecipeType", "ManaPool");
                metadata.put("correctedMachineType", "Mana Pool");
            } else if (combined.contains("puredaisy")) {
                metadata.put("specialRecipeType", "PureDaisy");
                metadata.put("correctedMachineType", "Pure Daisy");
            } else if (combined.contains("elventrade")) {
                metadata.put("specialRecipeType", "ElvenTrade");
                metadata.put("correctedMachineType", "Elven Trade");
            } else if (combined.contains("petalapothecary")) {
                metadata.put("specialRecipeType", "PetalApothecary");
                metadata.put("correctedMachineType", "Petal Apothecary");
            }

            Integer manaCost = readPositiveInt(recipe, "getManaUsage");
            if (manaCost == null) {
                manaCost = readPositiveInt(recipe, "getManaToConsume");
            }
            if (manaCost == null) {
                manaCost = readPositiveInt(recipe, "getMana");
            }
            if (manaCost == null) {
                manaCost = readPositiveFieldInt(recipe, "manaUsage");
            }
            if (manaCost == null) {
                manaCost = readPositiveFieldInt(recipe, "mana");
            }
            if (manaCost != null) {
                metadata.put("manaCost", manaCost);
            }

            if (!metadata.isEmpty()) {
                com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                                String.valueOf(metadata.get("specialRecipeType")), metadata
                        )
                );
            }
        } catch (Exception e) {
            logger.debug("Failed to extract Botania metadata", e);
        }
    }

    private Integer readPositiveInt(Object target, String methodName) {
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(target);
            if (value instanceof Number) {
                int intValue = ((Number) value).intValue();
                return intValue > 0 ? intValue : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Integer readPositiveFieldInt(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            Object value = field.get(target);
            if (value instanceof Number) {
                int intValue = ((Number) value).intValue();
                return intValue > 0 ? intValue : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String readThaumcraftResearch(Object recipe) {
        try {
            java.lang.reflect.Field researchItemField = recipe.getClass().getDeclaredField("researchItem");
            researchItemField.setAccessible(true);
            Object researchItem = researchItemField.get(recipe);
            if (researchItem != null) {
                try {
                    java.lang.reflect.Field keyField = researchItem.getClass().getField("key");
                    Object key = keyField.get(researchItem);
                    if (key instanceof String) {
                        return (String) key;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Integer readThaumcraftInstability(Object recipe) {
        try {
            java.lang.reflect.Method getInstability = recipe.getClass().getDeclaredMethod("getInstability");
            getInstability.setAccessible(true);
            Object value = getInstability.invoke(recipe);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }

        try {
            java.lang.reflect.Field instabilityField = recipe.getClass().getDeclaredField("instability");
            instabilityField.setAccessible(true);
            Object value = instabilityField.get(recipe);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private boolean isThaumcraftAspectStack(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String itemClass = stack.getItem().getClass().getName().toLowerCase();
        if (itemClass.contains("aspect")) return true;
        String unlocalized = stack.getUnlocalizedName();
        return unlocalized != null && unlocalized.toLowerCase().contains("aspect");
    }

    private int encodePositionedSlotIndex(int x, int y) {
        return x * 100 + y;
    }

    private void extractAe2WorldCraftingData(
            codechicken.nei.recipe.IRecipeHandler handler,
            int recipeIndex,
            com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("specialRecipeType", "AE2WorldCrafting");

            java.lang.reflect.Field offsetsField = handler.getClass().getDeclaredField("offsets");
            java.lang.reflect.Field detailsField = handler.getClass().getDeclaredField("details");
            offsetsField.setAccessible(true);
            detailsField.setAccessible(true);

            Object offsets = offsetsField.get(handler);
            Object details = detailsField.get(handler);
            if (offsets instanceof List<?> && details instanceof Map<?, ?>) {
                List<?> offsetList = (List<?>) offsets;
                Map<?, ?> detailsMap = (Map<?, ?>) details;
                if (recipeIndex >= 0 && recipeIndex < offsetList.size()) {
                    Object key = offsetList.get(recipeIndex);
                    Object detail = detailsMap.get(key);
                    if (detail instanceof String) {
                        metadata.put("worldCraftingDescription", detail);
                    }
                }
            }

            if (metadata.size() > 1) {
                com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                                "AE2WorldCrafting", metadata
                        )
                );
            }
        } catch (Exception e) {
            logger.debug("Failed to extract AE2 world crafting metadata", e);
        }
    }

    private void extractForestryMetadata(
            codechicken.nei.recipe.IRecipeHandler handler,
            int recipeIndex,
            com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe) {
        try {
            Object recipe = getRecipeFromHandler(handler, recipeIndex);
            if (recipe == null) {
                return;
            }

            Map<String, Object> metadata = new HashMap<>();
            String recipeClass = recipe.getClass().getName().toLowerCase();
            if (recipeClass.contains("breeding")) {
                metadata.put("specialRecipeType", "ForestryBreeding");
            } else if (recipeClass.contains("produce")) {
                metadata.put("specialRecipeType", "ForestryProduce");
            }

            try {
                java.lang.reflect.Field chanceField = recipe.getClass().getDeclaredField("chance");
                chanceField.setAccessible(true);
                Object value = chanceField.get(recipe);
                if (value instanceof Number) {
                    metadata.put("chance", ((Number) value).doubleValue());
                }
            } catch (Exception ignored) {
            }

            try {
                java.lang.reflect.Field requirementsField = recipe.getClass().getDeclaredField("requirements");
                requirementsField.setAccessible(true);
                Object value = requirementsField.get(recipe);
                if (value instanceof Collection<?>) {
                    List<String> requirements = new ArrayList<>();
                    for (Object requirement : (Collection<?>) value) {
                        if (requirement != null) {
                            requirements.add(String.valueOf(requirement));
                        }
                    }
                    if (!requirements.isEmpty()) {
                        metadata.put("requirements", requirements);
                    }
                }
            } catch (Exception ignored) {
            }

            if (metadata.size() > 1) {
                com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                                "ForestryNEI", metadata
                        )
                );
            }
        } catch (Exception e) {
            logger.debug("Failed to extract Forestry metadata", e);
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
            if (recipe == null) {
                return;
            }

            Map<String, Object> metadata = new HashMap<>();
            String handlerClass = handler.getClass().getName().toLowerCase();

            Integer tier = readPositiveFieldInt(recipe, "tier");
            if (tier != null) {
                metadata.put("tier", tier);
            }

            if (handlerClass.contains("neialtarrecipehandler")) {
                metadata.put("specialRecipeType", "BloodAltar");
                metadata.put("correctedMachineType", "Blood Altar");

                Integer bloodCost = readPositiveFieldInt(recipe, "lp_amount");
                if (bloodCost == null) {
                    bloodCost = readPositiveFieldInt(recipe, "liquidRequired");
                }
                if (bloodCost != null) {
                    metadata.put("bloodCost", bloodCost);
                }

                Integer consumption = readPositiveFieldInt(recipe, "consumption");
                if (consumption != null) {
                    metadata.put("consumptionRate", consumption);
                }

                Integer drain = readPositiveFieldInt(recipe, "drain");
                if (drain != null) {
                    metadata.put("drainRate", drain);
                }
            } else if (handlerClass.contains("neialchemyrecipehandler")) {
                metadata.put("specialRecipeType", "AlchemyArray");
                metadata.put("correctedMachineType", "Alchemy Array");

                Integer lpCost = readPositiveFieldInt(recipe, "lp");
                if (lpCost != null) {
                    metadata.put("lpCost", lpCost);
                }
            } else if (handlerClass.contains("neibindingritualhandler")) {
                metadata.put("specialRecipeType", "BindingRitual");
                metadata.put("correctedMachineType", "Binding Ritual");
            }

            // Generic fallback for alternate implementations
            if (!metadata.containsKey("bloodCost")) {
                String[] possibleFields = {"bloodCost", "cost", "requiredBlood"};
                for (String fieldName : possibleFields) {
                    Integer cost = readPositiveFieldInt(recipe, fieldName);
                    if (cost != null) {
                        metadata.put("bloodCost", cost);
                        break;
                    }
                }
            }

            if (!metadata.containsKey("lpCost")) {
                String[] possibleFields = {"lpCost", "requiredLP", "lpRequired"};
                for (String fieldName : possibleFields) {
                    Integer cost = readPositiveFieldInt(recipe, fieldName);
                    if (cost != null) {
                        metadata.put("lpCost", cost);
                        break;
                    }
                }
            }

            if (!metadata.isEmpty()) {
                com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.registerMetadata(
                        builtRecipe.getId(),
                        new com.github.dcysteine.nesql.exporter.util.SpecialRecipeMetadataRegistry.SpecialRecipeMetadata(
                                "NEI_BloodMagic", metadata
                        )
                );
                logger.debug("Extracted Blood Magic metadata for recipe {}: {}", builtRecipe.getId(), metadata);
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
            java.lang.reflect.Field recipesField = findField(handler.getClass(), "arecipes");
            if (recipesField == null) {
                return null;
            }
            recipesField.setAccessible(true);
            Object recipes = recipesField.get(handler);
            if (recipes instanceof java.util.List<?>) {
                java.util.List<?> recipeList = (java.util.List<?>) recipes;
                if (recipeIndex >= 0 && recipeIndex < recipeList.size()) {
                    return recipeList.get(recipeIndex);
                }
            } else if (recipes instanceof Object[]) {
                Object[] recipeArray = (Object[]) recipes;
                if (recipeIndex >= 0 && recipeIndex < recipeArray.length) {
                    return recipeArray[recipeIndex];
                }
            }
        } catch (Exception e) {
            logger.debug("Could not access recipe from handler", e);
        }
        return null;
    }

    private static java.lang.reflect.Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
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
                                             ICraftingHandler handler) {
        try {
            // Create or get RecipeType for this handler
            RecipeType recipeType = getOrCreateRecipeType(handlerId, handlerName, "crafting", handler);

            int exported = 0;
            int numRecipes = handler.numRecipes();

            // Export each recipe from this handler
            for (int i = 0; i < numRecipes; i++) {
                // Declare variables outside try block for exception handling
                Object neiRecipe = null;
                PositionedStack result = null;
                List<PositionedStack> ingredients = null;

                try {
                    neiRecipe = getRecipeFromHandler(handler, i);
                    RecipeType effectiveRecipeType =
                            resolveEffectiveRecipeType(handlerId, handlerName, handler, recipeType, neiRecipe);
                    RecipeBuilder builder = new RecipeBuilder(exporter, effectiveRecipeType);

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
                        addIngredientInputs(builder, ingredients, effectiveRecipeType);
                    }

                    // Build the recipe
                    com.github.dcysteine.nesql.sql.base.recipe.Recipe builtRecipe = builder.build();

                    // Streaming export is the active data path. Preserve the same
                    // handler-specific metadata that the older batch path attached.
                    extractModSpecificMetadata(handler, i, builtRecipe, result);
                    registerHandlerMetadata(handler, handlerId, builtRecipe);
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
                                          IUsageHandler handler) {
        try {
            // Create or get RecipeType for this handler
            RecipeType recipeType = getOrCreateRecipeType(handlerId, handlerName, "usage", handler);

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
                    registerHandlerMetadata(handler, handlerId, builtRecipe);
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
