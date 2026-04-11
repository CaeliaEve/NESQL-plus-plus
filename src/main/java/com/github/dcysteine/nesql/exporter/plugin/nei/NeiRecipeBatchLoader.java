package com.github.dcysteine.nesql.exporter.plugin.nei;

import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IUsageHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

import com.github.dcysteine.nesql.exporter.main.Logger;

import net.minecraft.item.ItemStack;

import com.google.common.base.Stopwatch;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for streaming NEI recipe export.
 * This version uses a streaming approach: load one handler, export it, then discard it.
 * This prevents OutOfMemoryError when dealing with millions of recipes.
 */
public class NeiRecipeBatchLoader {

    /**
     * Streaming export of all crafting recipes.
     * Processes handlers one by one: load → export → discard.
     *
     * @param exporter The export processor to handle the actual export
     * @return The total number of recipes exported
     */
    public static int streamExportAllCraftingRecipes(NeiRecipeExportProcessor exporter) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger totalRecipes = new AtomicInteger(0);
        AtomicInteger processedHandlers = new AtomicInteger(0);

        Logger.MOD.info("=== Starting Stream Export of NEI Crafting Recipes ===");
        Logger.MOD.info("Total crafting handlers: {}", GuiCraftingRecipe.craftinghandlers.size());
        Logger.chatMessage("=== Starting NEI Crafting Recipe Export ===");
        Logger.chatMessage("Streaming mode: Processing handlers one by one to save memory");
        Logger.chatMessage(String.format("Total handlers to process: %d", GuiCraftingRecipe.craftinghandlers.size()));

        int handlerIndex = 0;
        for (ICraftingHandler baseHandler : GuiCraftingRecipe.craftinghandlers) {
            handlerIndex++;
            String handlerId = baseHandler.getHandlerId();
            String handlerName = baseHandler.getRecipeName();

            try {
                Logger.MOD.info("Processing handler {}/{}: {}",
                        handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), handlerName);

                if (!(baseHandler instanceof TemplateRecipeHandler)) {
                    if (isCustomDiagramHandler(baseHandler)) {
                        Logger.MOD.info("Skipping NEI custom diagram handler; NESQL++ exports structured diagram data separately: {}",
                                handlerName);
                        processedHandlers.incrementAndGet();
                        continue;
                    }
                    int exported = exportNonTemplateCraftingHandler(
                            exporter,
                            baseHandler,
                            handlerIndex,
                            GuiCraftingRecipe.craftinghandlers.size());
                    totalRecipes.addAndGet(exported);
                    processedHandlers.incrementAndGet();
                    continue;
                }

                // Create a NEW instance for this handler when possible.
                ICraftingHandler workingHandler = ((TemplateRecipeHandler) baseHandler).newInstance();

                if (workingHandler == null) {
                    Logger.MOD.warn("Could not create handler instance for: {}", handlerName);
                    continue;
                }

                // ⚡ V14 优化5: 检查handler是否需要物品扫描
                boolean needsItemScan = needsItemScanning(handlerName);

                // Load all recipes for this handler
                int recipeCount;
                if (needsItemScan) {
                    Logger.chatMessage(String.format("[%d/%d] Loading recipes for: %s...",
                            handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), handlerName));
                    recipeCount = loadRecipesForHandler((TemplateRecipeHandler) workingHandler);
                } else {
                    // Handler不需要物品扫描，配方已经在arecipes中
                    Logger.MOD.debug("Handler {} doesn't need item scan, using pre-loaded recipes", handlerName);
                    recipeCount = workingHandler.numRecipes();
                }

                if (recipeCount > 0) {
                    Logger.chatMessage(String.format("[%d/%d] Exporting %d recipes from: %s",
                            handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), recipeCount, handlerName));

                    // Export this handler immediately
                    int exported = exporter.exportSingleCraftingHandler(handlerId, handlerName, workingHandler);
                    totalRecipes.addAndGet(exported);

                    Logger.MOD.info("Exported {} recipes from handler: {}", exported, handlerName);
                    Logger.chatMessage(String.format("[%d/%d] ✓ Completed: %s (%d recipes)",
                            handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), handlerName, exported));
                } else {
                    Logger.MOD.debug("No recipes found for handler: {}", handlerName);
                }

                // Clear and allow GC to reclaim memory
                if (workingHandler instanceof TemplateRecipeHandler) {
                    ((TemplateRecipeHandler) workingHandler).arecipes.clear();
                }
                processedHandlers.incrementAndGet();

                // Progress every 10 handlers, and flush database to free memory
                if (processedHandlers.get() % 10 == 0) {
                    Logger.chatMessage(String.format("Progress: %d/%d handlers processed, %d recipes exported so far",
                            processedHandlers.get(), GuiCraftingRecipe.craftinghandlers.size(), totalRecipes.get()));
                }

                // Flush every 50 handlers to reduce object detachment risk
                if (processedHandlers.get() % 50 == 0) {
                    Logger.chatMessage("Flushing database to free memory (50 handlers processed)...");
                    exporter.flushEntityManager();
                    Logger.chatMessage("Memory flushed, continuing export...");
                }

            } catch (Exception e) {
                Logger.MOD.error("Error processing handler: " + handlerName, e);
                Logger.chatMessage(String.format("[%d/%d] ⚠ Skipped: %s (error)",
                        handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), handlerName));
            }
        }

        stopwatch.stop();

        Logger.MOD.info("=== Stream Export Complete ===");
        Logger.MOD.info("Processed handlers: {}/{}", processedHandlers.get(), GuiCraftingRecipe.craftinghandlers.size());
        Logger.MOD.info("Total recipes exported: {}", totalRecipes.get());
        Logger.MOD.info("Time taken: {}", stopwatch);
        Logger.chatMessage("=== Crafting Recipe Export Complete ===");
        Logger.chatMessage(String.format("Exported %d recipes from %d handlers in {}",
                totalRecipes.get(), processedHandlers.get(), stopwatch));

        return totalRecipes.get();
    }

    private static boolean isCustomDiagramHandler(ICraftingHandler handler) {
        return handler != null
                && handler.getClass().getName().startsWith("com.github.dcysteine.neicustomdiagram.");
    }

    private static int exportNonTemplateCraftingHandler(
            NeiRecipeExportProcessor exporter,
            ICraftingHandler baseHandler,
            int handlerIndex,
            int totalHandlers) {
        String handlerName = baseHandler.getRecipeName();
        Logger.MOD.info("Processing non-template crafting handler {}/{}: {}",
                handlerIndex, totalHandlers, handlerName);

        try {
            if (baseHandler.numRecipes() > 0) {
                int exported = exporter.exportSingleCraftingHandler(
                        baseHandler.getHandlerId(),
                        handlerName,
                        baseHandler);
                Logger.chatMessage(String.format("[%d/%d] ✓ Completed: %s (%d recipes)",
                        handlerIndex, totalHandlers, handlerName, exported));
                return exported;
            }

            int exported = 0;
            Set<String> exportedKeys = new HashSet<>();
            for (ItemStack item : ItemList.items) {
                try {
                    ICraftingHandler derived = baseHandler.getRecipeHandler("item", item);
                    if (derived == null || derived.numRecipes() <= 0) {
                        continue;
                    }

                    String dedupeKey = derived.getHandlerId()
                            + "::"
                            + item.getItem().getUnlocalizedName()
                            + "::"
                            + item.getItemDamage();
                    if (!exportedKeys.add(dedupeKey)) {
                        continue;
                    }

                    exported += exporter.exportSingleCraftingHandler(
                            derived.getHandlerId(),
                            derived.getRecipeName(),
                            derived);
                } catch (Exception ignored) {
                }
            }

            if (exported > 0) {
                Logger.chatMessage(String.format("[%d/%d] ✓ Completed: %s (%d recipes)",
                        handlerIndex, totalHandlers, handlerName, exported));
            } else {
                Logger.MOD.warn("No recipes resolved for non-template handler: {}", handlerName);
            }
            return exported;
        } catch (Exception e) {
            Logger.MOD.error("Error processing non-template crafting handler: " + handlerName, e);
            Logger.chatMessage(String.format("[%d/%d] ⚠ Skipped: %s (error)",
                    handlerIndex, totalHandlers, handlerName));
            return 0;
        }
    }

    /**
     * Streaming export of all usage recipes.
     *
     * @param exporter The export processor to handle the actual export
     * @return The total number of recipes exported
     */
    public static int streamExportAllUsageRecipes(NeiRecipeExportProcessor exporter) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger totalRecipes = new AtomicInteger(0);
        AtomicInteger processedHandlers = new AtomicInteger(0);

        Logger.MOD.info("=== Starting Stream Export of NEI Usage Recipes ===");
        Logger.MOD.info("Total usage handlers: {}", GuiUsageRecipe.usagehandlers.size());
        Logger.chatMessage("=== Starting NEI Usage Recipe Export ===");
        Logger.chatMessage(String.format("Total handlers to process: %d", GuiUsageRecipe.usagehandlers.size()));

        int handlerIndex = 0;
        for (IUsageHandler baseHandler : GuiUsageRecipe.usagehandlers) {
            handlerIndex++;
            String handlerId = baseHandler.getHandlerId();
            String handlerName = baseHandler.getRecipeName();

            try {
                Logger.MOD.info("Processing usage handler {}/{}: {}",
                        handlerIndex, GuiUsageRecipe.usagehandlers.size(), handlerName);

                // Create a NEW instance for this handler when possible.
                IUsageHandler workingHandler = null;
                if (baseHandler instanceof TemplateRecipeHandler) {
                    workingHandler = ((TemplateRecipeHandler) baseHandler).newInstance();
                } else {
                    // Non-template handlers may still return a non-template handler instance.
                    workingHandler = baseHandler.getUsageHandler("item", ItemList.items.get(0));
                }

                if (workingHandler == null) {
                    Logger.MOD.warn("Could not create usage handler instance for: {}", handlerName);
                    continue;
                }

                // Load all usage recipes for this handler
                Logger.chatMessage(String.format("[%d/%d] Loading usage recipes for: %s...",
                        handlerIndex, GuiUsageRecipe.usagehandlers.size(), handlerName));

                int recipeCount = loadUsageRecipesForHandler((TemplateRecipeHandler) workingHandler);

                if (recipeCount > 0) {
                    Logger.chatMessage(String.format("[%d/%d] Exporting %d usage recipes from: %s",
                            handlerIndex, GuiUsageRecipe.usagehandlers.size(), recipeCount, handlerName));

                    // Export this handler immediately
                    int exported = exporter.exportSingleUsageHandler(handlerId, handlerName, workingHandler);
                    totalRecipes.addAndGet(exported);

                    Logger.MOD.info("Exported {} usage recipes from handler: {}", exported, handlerName);
                    Logger.chatMessage(String.format("[%d/%d] ✓ Completed: %s (%d recipes)",
                            handlerIndex, GuiUsageRecipe.usagehandlers.size(), handlerName, exported));
                } else {
                    Logger.MOD.debug("No usage recipes found for handler: {}", handlerName);
                }

                // Clear and allow GC to reclaim memory
                if (workingHandler instanceof TemplateRecipeHandler) {
                    ((TemplateRecipeHandler) workingHandler).arecipes.clear();
                }
                processedHandlers.incrementAndGet();

                // Progress every 10 handlers
                if (processedHandlers.get() % 10 == 0) {
                    Logger.chatMessage(String.format("Progress: %d/%d usage handlers processed, %d recipes exported so far",
                            processedHandlers.get(), GuiUsageRecipe.usagehandlers.size(), totalRecipes.get()));
                }

                // Flush every 50 handlers to reduce object detachment risk
                if (processedHandlers.get() % 50 == 0) {
                    Logger.chatMessage("Flushing database to free memory (50 usage handlers processed)...");
                    exporter.flushEntityManager();
                    Logger.chatMessage("Memory flushed, continuing export...");
                }

            } catch (Exception e) {
                Logger.MOD.error("Error processing usage handler: " + handlerName, e);
                Logger.chatMessage(String.format("[%d/%d] ⚠ Skipped: %s (error)",
                        handlerIndex, GuiUsageRecipe.usagehandlers.size(), handlerName));
            }
        }

        stopwatch.stop();

        Logger.MOD.info("=== Stream Export Complete ===");
        Logger.MOD.info("Processed handlers: {}/{}", processedHandlers.get(), GuiUsageRecipe.usagehandlers.size());
        Logger.MOD.info("Total usage recipes exported: {}", totalRecipes.get());
        Logger.MOD.info("Time taken: {}", stopwatch);
        Logger.chatMessage("=== Usage Recipe Export Complete ===");
        Logger.chatMessage(String.format("Exported %d usage recipes from %d handlers in {}",
                totalRecipes.get(), processedHandlers.get(), stopwatch));

        return totalRecipes.get();
    }

    /**
     * Load all crafting recipes for a single handler.
     *
     * @param handler The handler to load recipes into
     * @return The number of recipes loaded
     */
    private static int loadRecipesForHandler(TemplateRecipeHandler handler) {
        AtomicInteger loadedCount = new AtomicInteger(0);

        try {
            // ⚡ V14 优化1: 检查handler是否已经有配方
            int initialCount = handler.numRecipes();
            if (initialCount > 0) {
                // Handler已经预加载了配方，直接返回
                Logger.MOD.debug("Handler {} already has {} recipes, skipping item scan",
                    handler.getRecipeName(), initialCount);
                return initialCount;
            }

            // Clear any existing recipes
            handler.arecipes.clear();

            // ⚡ V14 修复: 移除不准确的优化，使用完整扫描
            // 之前的采样检测和mod过滤会跳过大量配方
            Logger.MOD.debug("Loading recipes for handler: {}", handler.getRecipeName());
            return loadRecipesFullScan(handler, loadedCount);

        } catch (Exception e) {
            Logger.MOD.warn("Error loading recipes for handler: " + handler.getRecipeName(), e);
        }

        return loadedCount.get();
    }

    /**
     * ⚡ 完整物品扫描 - 用于处理所有物品的handler
     */
    private static int loadRecipesFullScan(TemplateRecipeHandler handler, AtomicInteger loadedCount) {
        for (ItemStack item : ItemList.items) {
            try {
                int beforeSize = handler.arecipes.size();
                handler.loadCraftingRecipes(item);
                int afterSize = handler.arecipes.size();

                if (afterSize > beforeSize) {
                    loadedCount.addAndGet(afterSize - beforeSize);
                }
            } catch (Exception e) {
                // Some items may fail, that's ok
            }
        }
        return loadedCount.get();
    }

    /**
     * ⚡ 过滤物品扫描 - 只扫描满足条件的物品
     */
    private static int loadRecipesFiltered(TemplateRecipeHandler handler, AtomicInteger loadedCount,
                                          java.util.function.Predicate<ItemStack> filter) {
        int filtered = 0;
        int matched = 0;

        for (ItemStack item : ItemList.items) {
            filtered++;
            try {
                if (filter.test(item)) {
                    matched++;
                    int beforeSize = handler.arecipes.size();
                    handler.loadCraftingRecipes(item);
                    int afterSize = handler.arecipes.size();

                    if (afterSize > beforeSize) {
                        loadedCount.addAndGet(afterSize - beforeSize);
                    }
                }
            } catch (Exception e) {
                // Some items may fail, that's ok
            }
        }

        Logger.MOD.debug("Filtered scan: checked {} items, matched {} items, loaded {} recipes",
            filtered, matched, loadedCount.get());
        return loadedCount.get();
    }

    /**
     * Load all usage recipes for a single handler.
     *
     * @param handler The handler to load recipes into
     * @return The number of recipes loaded
     */
    private static int loadUsageRecipesForHandler(TemplateRecipeHandler handler) {
        AtomicInteger loadedCount = new AtomicInteger(0);

        try {
            // Clear any existing recipes
            handler.arecipes.clear();

            // Iterate through all items and load usage recipes
            for (ItemStack item : ItemList.items) {
                try {
                    int beforeSize = handler.arecipes.size();
                    handler.loadUsageRecipes(item);
                    int afterSize = handler.arecipes.size();

                    if (afterSize > beforeSize) {
                        loadedCount.addAndGet(afterSize - beforeSize);
                    }
                } catch (Exception e) {
                    // Some items may fail, that's ok
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn("Error loading usage recipes for handler: " + handler.getRecipeName(), e);
        }

        return loadedCount.get();
    }

    /**
     * ⚡ V14 优化: 检查handler是否需要遍历所有物品来加载配方
     * 某些handler在初始化时已经加载了配方，不需要再扫描物品
     */
    private static boolean needsItemScanning(String handlerName) {
        String lower = handlerName.toLowerCase();

        // 这些handler通常已经预加载了配方，不需要物品扫描
        if (lower.contains("gregtech") && (lower.contains("assembly") || lower.contains("line") || lower.contains("assemble"))) {
            // GregTech装配线不需要物品扫描（配方已经预加载）
            return false;
        }

        // NEI内置handler通常需要物品扫描
        if (lower.startsWith("nei.")) {
            return true;
        }

        // 默认需要物品扫描
        return true;
    }
}
