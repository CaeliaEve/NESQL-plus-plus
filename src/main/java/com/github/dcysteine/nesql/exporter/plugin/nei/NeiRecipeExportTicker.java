package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Delays NEI recipe export to ensure all recipe handlers are registered.
 *
 * NEI recipe handlers are registered late in the game initialization process,
 * so we need to wait for a few ticks after the world loads before exporting.
 */
public class NeiRecipeExportTicker {

    private final PluginExporter exporter;
    private final int delayTicks;
    private int tickCounter = 0;
    private boolean hasExecuted = false;

    /**
     * Creates a new ticker with the specified delay.
     *
     * @param exporter The plugin exporter instance
     * @param delayTicks Number of ticks to wait (100 ticks = 5 seconds)
     */
    public NeiRecipeExportTicker(PluginExporter exporter, int delayTicks) {
        this.exporter = exporter;
        this.delayTicks = delayTicks;
    }

    /**
     * Starts the ticker by registering it to the Forge event bus.
     */
    public void start() {
        Logger.MOD.info("NEI Recipe Export Ticker started - waiting {} ticks before exporting...", delayTicks);
        MinecraftForge.EVENT_BUS.register(this);
    }

    /**
     * Called every server tick. Counts down and triggers recipe export when delay is over.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // Only process at end of tick
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Prevent multiple executions
        if (hasExecuted) {
            return;
        }

        tickCounter++;

        // Log progress every 20 ticks (1 second)
        if (tickCounter % 20 == 0) {
            Logger.MOD.info("NEI Recipe Export Ticker: {} / {} ticks ({}%)",
                    tickCounter, delayTicks, (tickCounter * 100 / delayTicks));
        }

        // Check if delay is over
        if (tickCounter >= delayTicks) {
            Logger.MOD.info("Delay complete! Executing NEI recipe export now...");
            executeExport();
        }
    }

    /**
     * Executes the actual NEI recipe export.
     */
    private void executeExport() {
        hasExecuted = true;

        try {
            Logger.MOD.info("Delay complete! Starting NEI recipe export now...");

            // Create and execute the processor
            NeiRecipeExportProcessor processor = new NeiRecipeExportProcessor(exporter);
            processor.process();

            Logger.MOD.info("NEI recipe export completed successfully!");

        } catch (Exception e) {
            Logger.MOD.error("Error during delayed NEI recipe export", e);
        } finally {
            // Always unregister ourselves
            MinecraftForge.EVENT_BUS.unregister(this);
            Logger.MOD.info("NEI Recipe Export Ticker unregistered");
        }
    }

    /**
     * Gets the current tick counter.
     */
    public int getTickCounter() {
        return tickCounter;
    }

    /**
     * Checks if the export has been executed.
     */
    public boolean hasExecuted() {
        return hasExecuted;
    }

    /**
     * Waits for the ticker to complete execution.
     * This blocks the calling thread until the export is done.
     *
     * @param timeoutMillis Maximum time to wait in milliseconds
     * @return true if export completed, false if timeout
     */
    public boolean waitForCompletion(long timeoutMillis) {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMillis;

        Logger.MOD.info("Waiting for NEI recipe export to complete (max {} ms)...", timeoutMillis);

        while (!hasExecuted) {
            try {
                // Check timeout
                if (System.currentTimeMillis() >= endTime) {
                    Logger.MOD.warn("Timeout waiting for NEI recipe export completion!");
                    return false;
                }

                // Wait a bit before checking again
                Thread.sleep(100);

                // Log progress every 5 seconds
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > 0 && elapsed % 5000 < 100) {
                    Logger.MOD.info("Still waiting for NEI export... {} ticks elapsed", tickCounter);
                }

            } catch (InterruptedException e) {
                Logger.MOD.warn("Wait interrupted while waiting for NEI export", e);
                Thread.currentThread().interrupt();
                return false;
            }
        }

        Logger.MOD.info("NEI recipe export completed! Ticks waited: {}", tickCounter);
        return true;
    }
}
