package com.github.dcysteine.nesql.exporter.plugin.nei;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IUsageHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.util.IdUtil;

import net.minecraft.item.ItemStack;

import com.google.common.base.Stopwatch;

import java.util.HashSet;
import java.util.List;
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
     * Processes handlers one by one: load, export, then discard.
     *
     * @param exporter The export processor to handle the actual export
     * @return The total number of recipes exported
     */
    public static int streamExportAllCraftingRecipes(NeiRecipeExportProcessor exporter) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger totalRecipes = new AtomicInteger(0);
        AtomicInteger processedHandlers = new AtomicInteger(0);
        List<ItemStack> itemUniverse = NeiItemUniverse.getItems();

        Logger.MOD.info("=== Starting Stream Export of NEI Crafting Recipes ===");
        Logger.MOD.info("Total crafting handlers: {}", GuiCraftingRecipe.craftinghandlers.size());
        Logger.MOD.info("Effective item scan universe size: {}", itemUniverse.size());
        Logger.chatMessage("=== Starting NEI Crafting Recipe Export ===");
        Logger.chatMessage("Streaming mode: Processing handlers one by one to save memory");
        Logger.chatMessage(String.format("Total handlers to process: %d", GuiCraftingRecipe.craftinghandlers.size()));
        NeiExportTimingRegistry.clear();

        int handlerIndex = 0;
        for (ICraftingHandler baseHandler : GuiCraftingRecipe.craftinghandlers) {
            long handlerStartedAt = System.currentTimeMillis();
            long loadElapsedMs = 0L;
            long exportElapsedMs = 0L;
            int recipeCount = 0;
            int exportedForHandler = 0;
            boolean itemScan = false;
            handlerIndex++;
            String handlerId = baseHandler.getHandlerId();
            String handlerName = baseHandler.getRecipeName();

            try {
                if (!NeiExportDebugFilter.shouldProcess(baseHandler)) {
                    Logger.MOD.info("Skipping handler due to debug filter {}: {}",
                            NeiExportDebugFilter.getMode(), handlerName);
                    processedHandlers.incrementAndGet();
                    continue;
                }
                if (shouldSkipCraftingHandler(baseHandler)) {
                    Logger.MOD.info(
                            "Skipping NEI handler {} ({}) because another exporter owns its canonical facts.",
                            handlerName,
                            baseHandler.getClass().getName());
                    processedHandlers.incrementAndGet();
                    recordHandlerTiming(
                            handlerIndex,
                            baseHandler,
                            baseHandler instanceof TemplateRecipeHandler,
                            false,
                            0,
                            0,
                            0L,
                            0L,
                            handlerStartedAt);
                    continue;
                }
                Logger.MOD.info("Processing handler {}/{}: {}",
                        handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), handlerName);

                if (!(baseHandler instanceof TemplateRecipeHandler)) {
                    if (isCustomDiagramHandler(baseHandler)) {
                        Logger.MOD.info("Skipping NEI custom diagram handler; NESQL++ exports structured diagram data separately: {}",
                                handlerName);
                        processedHandlers.incrementAndGet();
                        continue;
                    }
                    long exportStartedAt = System.currentTimeMillis();
                    int exported = exportNonTemplateCraftingHandler(
                            exporter,
                            baseHandler,
                            itemUniverse,
                            handlerIndex,
                            GuiCraftingRecipe.craftinghandlers.size());
                    exportElapsedMs = System.currentTimeMillis() - exportStartedAt;
                    exportedForHandler = exported;
                    totalRecipes.addAndGet(exported);
                    processedHandlers.incrementAndGet();
                    recordHandlerTiming(
                            handlerIndex,
                            baseHandler,
                            false,
                            itemScan,
                            recipeCount,
                            exportedForHandler,
                            loadElapsedMs,
                            exportElapsedMs,
                            handlerStartedAt);
                    continue;
                }

                // Create a NEW instance for this handler when possible.
                ICraftingHandler workingHandler = ((TemplateRecipeHandler) baseHandler).newInstance();

                if (workingHandler == null) {
                    Logger.MOD.warn("Could not create handler instance for: {}", handlerName);
                    continue;
                }

                // v1.04 optimization: decide whether this handler needs item-context scanning.
                boolean needsItemScan = needsItemScanning(handlerName);
                itemScan = needsItemScan;

                // Load all recipes for this handler
                long loadStartedAt = System.currentTimeMillis();
                if (needsItemScan) {
                    Logger.chatMessage(String.format("[%d/%d] Loading recipes for: %s...",
                            handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), handlerName));
                    recipeCount = loadRecipesForHandler((TemplateRecipeHandler) workingHandler, itemUniverse);
                } else {
                    // Handler does not need item scanning; recipes are already available in arecipes.
                    Logger.MOD.debug("Handler {} doesn't need item scan, using pre-loaded recipes", handlerName);
                    recipeCount = workingHandler.numRecipes();
                }
                loadElapsedMs = System.currentTimeMillis() - loadStartedAt;

                if (recipeCount > 0) {
                    Logger.chatMessage(String.format("[%d/%d] Exporting %d recipes from: %s",
                            handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), recipeCount, handlerName));

                    // Export this handler immediately
                    long exportStartedAt = System.currentTimeMillis();
                    int exported = exporter.exportSingleCraftingHandler(handlerId, handlerName, workingHandler);
                    exportElapsedMs = System.currentTimeMillis() - exportStartedAt;
                    exportedForHandler = exported;
                    totalRecipes.addAndGet(exported);

                    Logger.MOD.info("Exported {} recipes from handler: {}", exported, handlerName);
                    Logger.chatMessage(String.format("[%d/%d] Completed: %s (%d recipes)",
                            handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), handlerName, exported));
                } else {
                    Logger.MOD.debug("No recipes found for handler: {}", handlerName);
                }

                // Clear and allow GC to reclaim memory
                if (workingHandler instanceof TemplateRecipeHandler) {
                    ((TemplateRecipeHandler) workingHandler).arecipes.clear();
                }
                processedHandlers.incrementAndGet();
                recordHandlerTiming(
                        handlerIndex,
                        baseHandler,
                        true,
                        itemScan,
                        recipeCount,
                        exportedForHandler,
                        loadElapsedMs,
                        exportElapsedMs,
                        handlerStartedAt);

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
                Logger.chatMessage(String.format("[%d/%d] Skipped: %s (error)",
                        handlerIndex, GuiCraftingRecipe.craftinghandlers.size(), handlerName));
                recordHandlerTiming(
                        handlerIndex,
                        baseHandler,
                        baseHandler instanceof TemplateRecipeHandler,
                        itemScan,
                        recipeCount,
                        exportedForHandler,
                        loadElapsedMs,
                        exportElapsedMs,
                        handlerStartedAt);
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

    private static void recordHandlerTiming(
            int handlerIndex,
            ICraftingHandler handler,
            boolean templateHandler,
            boolean itemScan,
            int loadedRecipes,
            int exportedRecipes,
            long loadElapsedMs,
            long exportElapsedMs,
            long handlerStartedAt) {
        try {
            NeiExportTimingRegistry.record(
                    handlerIndex,
                    GuiCraftingRecipe.craftinghandlers.size(),
                    handler.getHandlerId(),
                    handler.getRecipeName(),
                    handler.getClass().getName(),
                    templateHandler,
                    itemScan,
                    loadedRecipes,
                    exportedRecipes,
                    loadElapsedMs,
                    exportElapsedMs,
                    System.currentTimeMillis() - handlerStartedAt);
        } catch (Throwable ignored) {
        }
    }
    private static boolean isCustomDiagramHandler(ICraftingHandler handler) {
        return handler != null
                && handler.getClass().getName().startsWith("com.github.dcysteine.neicustomdiagram.");
    }

    private static int exportNonTemplateCraftingHandler(
            NeiRecipeExportProcessor exporter,
            ICraftingHandler baseHandler,
            List<ItemStack> itemUniverse,
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
                Logger.chatMessage(String.format("[%d/%d] Completed: %s (%d recipes)",
                        handlerIndex, totalHandlers, handlerName, exported));
                return exported;
            }

            int exported = 0;
            Set<String> exportedKeys = new HashSet<>();
            for (ItemStack item : itemUniverse) {
                try {
                    ICraftingHandler derived = baseHandler.getRecipeHandler("item", item);
                    if (derived == null || derived.numRecipes() <= 0) {
                        continue;
                    }

                    String dedupeKey = derived.getHandlerId() + "::" + IdUtil.itemId(item);
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
                Logger.chatMessage(String.format("[%d/%d] Completed: %s (%d recipes)",
                        handlerIndex, totalHandlers, handlerName, exported));
            } else {
                Logger.MOD.warn("No recipes resolved for non-template handler: {}", handlerName);
            }
            return exported;
        } catch (Exception e) {
            Logger.MOD.error("Error processing non-template crafting handler: " + handlerName, e);
            Logger.chatMessage(String.format("[%d/%d] Skipped: %s (error)",
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
        List<ItemStack> itemUniverse = NeiItemUniverse.getItems();

        Logger.MOD.info("=== Starting Stream Export of NEI Usage Recipes ===");
        Logger.MOD.info("Total usage handlers: {}", GuiUsageRecipe.usagehandlers.size());
        Logger.MOD.info("Effective usage scan universe size: {}", itemUniverse.size());
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
                    ItemStack seedItem = itemUniverse.isEmpty() ? null : itemUniverse.get(0);
                    workingHandler = seedItem == null ? null : baseHandler.getUsageHandler("item", seedItem);
                }

                if (workingHandler == null) {
                    Logger.MOD.warn("Could not create usage handler instance for: {}", handlerName);
                    continue;
                }

                // Load all usage recipes for this handler
                Logger.chatMessage(String.format("[%d/%d] Loading usage recipes for: %s...",
                        handlerIndex, GuiUsageRecipe.usagehandlers.size(), handlerName));

                int recipeCount = loadUsageRecipesForHandler((TemplateRecipeHandler) workingHandler, itemUniverse);

                if (recipeCount > 0) {
                    Logger.chatMessage(String.format("[%d/%d] Exporting %d usage recipes from: %s",
                            handlerIndex, GuiUsageRecipe.usagehandlers.size(), recipeCount, handlerName));

                    // Export this handler immediately
                    int exported = exporter.exportSingleUsageHandler(handlerId, handlerName, workingHandler);
                    totalRecipes.addAndGet(exported);

                    Logger.MOD.info("Exported {} usage recipes from handler: {}", exported, handlerName);
                    Logger.chatMessage(String.format("[%d/%d] Completed: %s (%d recipes)",
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
                Logger.chatMessage(String.format("[%d/%d] Skipped: %s (error)",
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
    private static int loadRecipesForHandler(TemplateRecipeHandler handler, List<ItemStack> itemUniverse) {
        AtomicInteger loadedCount = new AtomicInteger(0);

        try {
            // v1.04 optimization: use preloaded recipes when available.
            int initialCount = handler.numRecipes();
            if (initialCount > 0) {
                // Handler already populated recipes; avoid an expensive full item scan.
                Logger.MOD.debug("Handler {} already has {} recipes, skipping item scan",
                    handler.getRecipeName(), initialCount);
                return initialCount;
            }

            Integer overlayCount = tryLoadOverlayRecipes(handler);
            if (overlayCount != null && overlayCount > 0) {
                return overlayCount;
            }

            Integer allRecipesCount = tryLoadAllRecipes(handler);
            if (allRecipesCount != null && allRecipesCount > 0) {
                return allRecipesCount;
            }

            // Clear any existing recipes
            handler.arecipes.clear();

            // v1.04 fallback: scan item universe only when the handler requires it.
            // Most handlers are sparse; the caller decides whether this cost is acceptable.
            Logger.MOD.debug("Loading recipes for handler: {}", handler.getRecipeName());
            return loadRecipesFullScan(handler, itemUniverse, loadedCount);

        } catch (Exception e) {
            Logger.MOD.warn("Error loading recipes for handler: " + handler.getRecipeName(), e);
        }

        return loadedCount.get();
    }

    private static Integer tryLoadOverlayRecipes(TemplateRecipeHandler handler) {
        if (!shouldTryOverlayLoad(handler)) {
            return null;
        }

        try {
            handler.arecipes.clear();
            String overlayId = handler.getOverlayIdentifier();
            if (overlayId == null || overlayId.trim().isEmpty()) {
                return 0;
            }
            handler.loadCraftingRecipes(overlayId, (Object) null);
            int loaded = handler.numRecipes();
            Logger.MOD.info("Loaded {} recipes for handler {} via overlay {}",
                loaded, handler.getRecipeName(), overlayId);
            return loaded;
        } catch (Exception e) {
            Logger.MOD.warn("Failed overlay recipe load for handler: " + handler.getRecipeName(), e);
            return null;
        }
    }

    private static boolean shouldTryOverlayLoad(TemplateRecipeHandler handler) {
        if (handler == null) {
            return false;
        }

        String handlerId = handler.getHandlerId() == null ? "" : handler.getHandlerId().toLowerCase();
        String handlerName = handler.getRecipeName() == null ? "" : handler.getRecipeName().toLowerCase();
        String handlerClass = handler.getClass().getName().toLowerCase();
        String overlayId = "";
        try {
            overlayId = handler.getOverlayIdentifier() == null ? "" : handler.getOverlayIdentifier().toLowerCase();
        } catch (Exception ignored) {
        }

        String combined = handlerId + " " + handlerName + " " + handlerClass + " " + overlayId;
        if (combined.contains("mobsinfo.mobhandler")
            || combined.contains("mobsinfo.mobhandlerinfernal")
            || combined.contains("com.kuba6000.mobsinfo")) {
            return true;
        }

        // Many GTNH NEI handlers expose a complete category list through their overlay id.
        // Loading that category directly avoids scanning the full 50k+ item universe while
        // preserving the same handler-generated recipe objects. If a handler returns nothing
        // here, the caller falls back to the full scan.
        return overlayId != null && !overlayId.trim().isEmpty();
    }

    private static Integer tryLoadAllRecipes(TemplateRecipeHandler handler) {
        try {
            java.lang.reflect.Method method = handler.getClass().getMethod("loadAllRecipes");
            handler.arecipes.clear();
            method.setAccessible(true);
            method.invoke(handler);
            int loaded = handler.numRecipes();
            Logger.MOD.info("Loaded {} recipes for handler {} via loadAllRecipes()",
                    loaded, handler.getRecipeName());
            return loaded;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Exception e) {
            Logger.MOD.warn("Failed loadAllRecipes() recipe load for handler: " + handler.getRecipeName(), e);
            return null;
        }
    }

    /**
     * Full item scan loader for handlers that need item-context recipe discovery.
     */
    private static int loadRecipesFullScan(
            TemplateRecipeHandler handler, List<ItemStack> itemUniverse, AtomicInteger loadedCount) {
        for (ItemStack item : itemUniverse) {
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
     * Filtered item scan loader for specialized handlers.
     */
    private static int loadRecipesFiltered(TemplateRecipeHandler handler, AtomicInteger loadedCount,
                                          java.util.function.Predicate<ItemStack> filter) {
        int filtered = 0;
        int matched = 0;
        List<ItemStack> itemUniverse = NeiItemUniverse.getItems();

        for (ItemStack item : itemUniverse) {
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
    private static int loadUsageRecipesForHandler(TemplateRecipeHandler handler, List<ItemStack> itemUniverse) {
        AtomicInteger loadedCount = new AtomicInteger(0);

        try {
            // Clear any existing recipes
            handler.arecipes.clear();

            // Iterate through all items and load usage recipes
            for (ItemStack item : itemUniverse) {
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
     * v1.04 handler policy: only scan items when the handler needs item-context loading.
     * Handlers that already preload recipes can skip the expensive scan.
     */
    private static boolean needsItemScanning(String handlerName) {
        String lower = handlerName.toLowerCase();

        // GregTech assembly handlers preload recipes; scanning every item is redundant.
        if (lower.contains("gregtech") && (lower.contains("assembly") || lower.contains("line") || lower.contains("assemble"))) {
            // Keep the export fast by trusting the handler recipe list.
            return false;
        }

        // Built-in NEI handlers usually need item-context loading.
        if (lower.startsWith("nei.")) {
            return true;
        }

        // Default to scanning for correctness.
        return true;
    }

    private static boolean shouldSkipCraftingHandler(ICraftingHandler handler) {
        if (handler == null) {
            return false;
        }
        String handlerId = handler.getHandlerId() == null ? "" : handler.getHandlerId();
        String className = handler.getClass().getName();

        // GregTech machine recipes are exported through GregTechRecipeProcessor with machine
        // metadata. The NEI GTNEIDefaultHandler path loaded 195k duplicate display rows in the
        // latest GTNH export but produced zero NESQL recipe facts because those rows have no
        // canonical result stack for RecipeBuilder. Skipping it preserves exported facts and saves
        // the full item-universe scan.
        if ("gregtech.nei.GTNEIDefaultHandler".equals(handlerId)
                || "gregtech.nei.GTNEIDefaultHandler".equals(className)) {
            return true;
        }

        // CreativeCore's IRecipeInfo handler is an informational NEI surface, not a recipe source.
        return "com.creativemd.creativecore.api.nei.NEIRecipeInfoHandler".equals(className);
    }
}
