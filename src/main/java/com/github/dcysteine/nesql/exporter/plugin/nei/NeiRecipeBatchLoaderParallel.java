package com.github.dcysteine.nesql.exporter.plugin.nei;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.IUsageHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.common.base.Stopwatch;

import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parallel version of NeiRecipeBatchLoader.
 * Uses multiple threads to process handlers concurrently for faster export.
 *
 * ⚠️ THREAD SAFETY APPROACH:
 * - Multiple threads can LOAD recipes in parallel (CPU-intensive, thread-safe)
 * - Single thread exports to DATABASE (EntityManager is NOT thread-safe)
 * - Uses a work queue pattern: Thread pool loads → Single thread exports
 *
 * This gives us speedup from parallel recipe loading while maintaining database integrity.
 */
public class NeiRecipeBatchLoaderParallel {

    // Number of threads for parallel LOADING (export is still serial)
    private static final int LOADER_THREAD_POOL_SIZE = 4;

    // Queue size for loaded handlers waiting to be exported
    private static final int QUEUE_CAPACITY = 10;

    // Batch size for progress reporting
    private static final int PROGRESS_REPORT_BATCH = 20;

    /**
     * Parallel export of all crafting recipes.
     *
     * @param exporter The export processor (will be accessed by a single thread)
     * @return The total number of recipes exported
     */
    public static int parallelExportAllCraftingRecipes(NeiRecipeExportProcessor exporter) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger totalRecipes = new AtomicInteger(0);
        AtomicInteger processedHandlers = new AtomicInteger(0);

        Logger.MOD.info("=== Starting Parallel Export of NEI Crafting Recipes ===");
        Logger.MOD.info("Loader threads: {}", LOADER_THREAD_POOL_SIZE);
        Logger.chatMessage("=== Starting NEI Crafting Recipe Export (Parallel Mode) ===");
        Logger.chatMessage("Using " + LOADER_THREAD_POOL_SIZE + " threads for recipe loading");

        List<ICraftingHandler> handlers = new ArrayList<>(GuiCraftingRecipe.craftinghandlers);
        Logger.chatMessage("Total handlers to process: " + handlers.size());

        // Start loader threads
        ExecutorService loaderExecutor = Executors.newFixedThreadPool(LOADER_THREAD_POOL_SIZE);
        List<Future<LoaderResult>> loaderFutures = new ArrayList<>();

        // Submit loader tasks
        for (int i = 0; i < handlers.size(); i++) {
            final int handlerIndex = i;
            final ICraftingHandler handler = handlers.get(i);

            Future<LoaderResult> future = loaderExecutor.submit(() -> {
                return loadCraftingHandler(handler, handlerIndex, handlers.size());
            });

            loaderFutures.add(future);
        }

        // Process loaded handlers in main thread (export to database)
        // This ensures EntityManager is only accessed by one thread
        List<LoaderResult> loaderResults = new ArrayList<>();

        for (Future<LoaderResult> future : loaderFutures) {
            try {
                LoaderResult result = future.get();
                loaderResults.add(result);

                if (result.loadedHandler != null) {
                    // Export this handler immediately (synchronized database access)
                    int exported = exporter.exportSingleCraftingHandler(
                            result.handlerId,
                            result.handlerName,
                            (ICraftingHandler) result.loadedHandler
                    );

                    totalRecipes.addAndGet(exported);
                    processedHandlers.incrementAndGet();

                    Logger.MOD.info("Exported {} recipes from handler: {}", exported, result.handlerName);
                    Logger.chatMessage(String.format("[%d/%d] ✓ Completed: %s (%d recipes)",
                            processedHandlers.get(), handlers.size(), result.handlerName, exported));

                    // Progress reporting
                    if (processedHandlers.get() % PROGRESS_REPORT_BATCH == 0) {
                        Logger.chatMessage(String.format("Progress: %d/%d handlers processed, %d recipes exported so far",
                                processedHandlers.get(), handlers.size(), totalRecipes.get()));
                    }

                    // Flush every 50 handlers
                    if (processedHandlers.get() % 50 == 0) {
                        Logger.chatMessage("Flushing database to free memory (50 handlers processed)...");
                        exporter.flushEntityManager();
                        Logger.chatMessage("Memory flushed, continuing export...");
                    }
                } else {
                    processedHandlers.incrementAndGet();
                    Logger.MOD.warn("Failed to load handler: {} - {}", result.handlerName, result.error);
                }

            } catch (InterruptedException | ExecutionException e) {
                Logger.MOD.error("Error processing loader result", e);
            }
        }

        // Shutdown loader executor
        loaderExecutor.shutdown();
        try {
            loaderExecutor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Logger.MOD.error("Interrupted while waiting for loaders to complete", e);
        }

        stopwatch.stop();

        Logger.MOD.info("=== Parallel Export Complete ===");
        Logger.MOD.info("Processed handlers: {}/{}", processedHandlers.get(), handlers.size());
        Logger.MOD.info("Total recipes exported: {}", totalRecipes.get());
        Logger.MOD.info("Time taken: {}", stopwatch);
        Logger.chatMessage("=== Crafting Recipe Export Complete ===");
        Logger.chatMessage(String.format("Exported %d recipes from %d handlers in %s",
                totalRecipes.get(), processedHandlers.get(), stopwatch));

        return totalRecipes.get();
    }

    /**
     * Load recipes from a single crafting handler (thread-safe).
     * This runs in parallel in multiple threads.
     */
    private static LoaderResult loadCraftingHandler(
            ICraftingHandler baseHandler,
            int handlerIndex,
            int totalHandlers) {

        LoaderResult result = new LoaderResult();
        result.handlerId = baseHandler.getHandlerId();
        result.handlerName = baseHandler.getRecipeName();

        try {
            Logger.MOD.info("[Loader Thread {}] Loading handler {}/{}: {}",
                    Thread.currentThread().getName(),
                    handlerIndex + 1, totalHandlers, result.handlerName);

            // Create a NEW instance for this handler when possible.
            ICraftingHandler workingHandler = null;
            if (baseHandler instanceof TemplateRecipeHandler) {
                workingHandler = ((TemplateRecipeHandler) baseHandler).newInstance();
            } else {
                workingHandler = baseHandler.getRecipeHandler("item", ItemList.items.get(0));
            }

            if (workingHandler == null) {
                result.error = "Could not create handler instance";
                return result;
            }

            // Load all recipes for this handler (CPU-intensive, thread-safe)
            int recipeCount = workingHandler instanceof TemplateRecipeHandler
                    ? loadRecipesForHandler((TemplateRecipeHandler) workingHandler)
                    : workingHandler.numRecipes();

            if (recipeCount > 0) {
                result.loadedHandler = workingHandler;
                result.recipeCount = recipeCount;
                Logger.MOD.info("[Loader Thread {}] Loaded {} recipes from: {}",
                        Thread.currentThread().getName(), recipeCount, result.handlerName);
            } else {
                result.loadedHandler = null;
                result.recipeCount = 0;
            }

        } catch (Exception e) {
            result.error = e.getMessage();
            Logger.MOD.error("Error loading handler: " + result.handlerName, e);
        }

        return result;
    }

    /**
     * Parallel export of all usage recipes.
     */
    public static int parallelExportAllUsageRecipes(NeiRecipeExportProcessor exporter) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        AtomicInteger totalRecipes = new AtomicInteger(0);
        AtomicInteger processedHandlers = new AtomicInteger(0);

        Logger.MOD.info("=== Starting Parallel Export of NEI Usage Recipes ===");
        Logger.MOD.info("Loader threads: {}", LOADER_THREAD_POOL_SIZE);
        Logger.chatMessage("=== Starting NEI Usage Recipe Export (Parallel Mode) ===");

        List<IUsageHandler> handlers = new ArrayList<>(GuiUsageRecipe.usagehandlers);
        Logger.chatMessage("Total usage handlers to process: " + handlers.size());

        // Start loader threads
        ExecutorService loaderExecutor = Executors.newFixedThreadPool(LOADER_THREAD_POOL_SIZE);
        List<Future<LoaderResult>> loaderFutures = new ArrayList<>();

        // Submit loader tasks
        for (int i = 0; i < handlers.size(); i++) {
            final int handlerIndex = i;
            final IUsageHandler handler = handlers.get(i);

            Future<LoaderResult> future = loaderExecutor.submit(() -> {
                return loadUsageHandler(handler, handlerIndex, handlers.size());
            });

            loaderFutures.add(future);
        }

        // Process loaded handlers in main thread
        for (Future<LoaderResult> future : loaderFutures) {
            try {
                LoaderResult result = future.get();

                if (result.loadedHandler != null) {
                    // Export this handler immediately
                    int exported = exporter.exportSingleUsageHandler(
                            result.handlerId,
                            result.handlerName,
                            (IUsageHandler) result.loadedHandler
                    );

                    totalRecipes.addAndGet(exported);
                    processedHandlers.incrementAndGet();

                    Logger.MOD.info("Exported {} usage recipes from handler: {}", exported, result.handlerName);
                    Logger.chatMessage(String.format("[%d/%d] ✓ Completed: %s (%d recipes)",
                            processedHandlers.get(), handlers.size(), result.handlerName, exported));

                    // Progress reporting
                    if (processedHandlers.get() % PROGRESS_REPORT_BATCH == 0) {
                        Logger.chatMessage(String.format("Progress: %d/%d usage handlers processed, %d recipes exported so far",
                                processedHandlers.get(), handlers.size(), totalRecipes.get()));

                        // Flush every 20 for usage
                        Logger.chatMessage("Flushing database to free memory...");
                        exporter.flushEntityManager();
                        Logger.chatMessage("Memory flushed, continuing export...");
                    }
                } else {
                    processedHandlers.incrementAndGet();
                    Logger.MOD.warn("Failed to load usage handler: {} - {}", result.handlerName, result.error);
                }

            } catch (InterruptedException | ExecutionException e) {
                Logger.MOD.error("Error processing usage loader result", e);
            }
        }

        // Shutdown loader executor
        loaderExecutor.shutdown();
        try {
            loaderExecutor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Logger.MOD.error("Interrupted while waiting for usage loaders to complete", e);
        }

        stopwatch.stop();

        Logger.MOD.info("=== Parallel Usage Export Complete ===");
        Logger.MOD.info("Processed handlers: {}/{}", processedHandlers.get(), handlers.size());
        Logger.MOD.info("Total usage recipes exported: {}", totalRecipes.get());
        Logger.MOD.info("Time taken: {}", stopwatch);
        Logger.chatMessage("=== Usage Recipe Export Complete ===");
        Logger.chatMessage(String.format("Exported %d usage recipes from %d handlers in %s",
                totalRecipes.get(), processedHandlers.get(), stopwatch));

        return totalRecipes.get();
    }

    /**
     * Load usage recipes from a single handler (thread-safe).
     */
    private static LoaderResult loadUsageHandler(
            IUsageHandler baseHandler,
            int handlerIndex,
            int totalHandlers) {

        LoaderResult result = new LoaderResult();
        result.handlerId = baseHandler.getHandlerId();
        result.handlerName = baseHandler.getRecipeName();

        try {
            Logger.MOD.info("[Loader Thread {}] Loading usage handler {}/{}: {}",
                    Thread.currentThread().getName(),
                    handlerIndex + 1, totalHandlers, result.handlerName);

            // Create a NEW instance for this handler when possible.
            IUsageHandler workingHandler = null;
            if (baseHandler instanceof TemplateRecipeHandler) {
                workingHandler = ((TemplateRecipeHandler) baseHandler).newInstance();
            } else {
                workingHandler = baseHandler.getUsageHandler("item", ItemList.items.get(0));
            }

            if (workingHandler == null) {
                result.error = "Could not create handler instance";
                return result;
            }

            // Load all usage recipes for this handler
            int recipeCount = workingHandler instanceof TemplateRecipeHandler
                    ? loadUsageRecipesForHandler((TemplateRecipeHandler) workingHandler)
                    : workingHandler.numRecipes();

            if (recipeCount > 0) {
                result.loadedHandler = workingHandler;
                result.recipeCount = recipeCount;
                Logger.MOD.info("[Loader Thread {}] Loaded {} usage recipes from: {}",
                        Thread.currentThread().getName(), recipeCount, result.handlerName);
            } else {
                result.loadedHandler = null;
                result.recipeCount = 0;
            }

        } catch (Exception e) {
            result.error = e.getMessage();
            Logger.MOD.error("Error loading usage handler: " + result.handlerName, e);
        }

        return result;
    }

    /**
     * Load all crafting recipes for a single handler.
     * This method is thread-safe (no shared state).
     */
    private static int loadRecipesForHandler(TemplateRecipeHandler handler) {
        AtomicInteger loadedCount = new AtomicInteger(0);

        try {
            handler.arecipes.clear();

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
        } catch (Exception e) {
            Logger.MOD.warn("Error loading recipes for handler: " + handler.getRecipeName(), e);
        }

        return loadedCount.get();
    }

    /**
     * Load all usage recipes for a single handler.
     * This method is thread-safe (no shared state).
     */
    private static int loadUsageRecipesForHandler(TemplateRecipeHandler handler) {
        AtomicInteger loadedCount = new AtomicInteger(0);

        try {
            handler.arecipes.clear();

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
     * Result class for handler loading.
     */
    private static class LoaderResult {
        String handlerId;
        String handlerName;
        IRecipeHandler loadedHandler;
        int recipeCount;
        String error;
    }
}
