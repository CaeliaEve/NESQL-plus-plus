package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.sql.Plugin;

/**
 * Plugin which exports NEI data (item list and recipes).
 *
 * This plugin is processed last because NEI handlers are the authoritative
 * recipe-view ingest surface for handler-owned recipes. Specialized direct
 * API plugins run earlier for canonical enrichment; the NEI stage records
 * what the in-game recipe UI can actually expose instead of acting as a
 * legacy compatibility fallback.
 */
public class NeiPluginExporter extends PluginExporter {

    public NeiPluginExporter(Plugin plugin, ExporterState exporterState) {
        super(plugin, exporterState);
    }

    @Override
    public void process() {
        // Export item list immediately (no dependency on handlers)
        Logger.MOD.info("Starting NEI item list export...");
        new NeiItemListProcessor(this).process();
        Logger.MOD.info("Finished NEI item list export!");

        // Export NEI recipes immediately
        Logger.MOD.info("Starting NEI recipe export...");

        try {
            Logger.chatMessage("=== Starting NEI Recipe Export ===");
            Logger.chatMessage("NOTE: If NEI handlers haven't loaded recipes yet, some may be empty.");
            Logger.chatMessage("Try opening NEI GUI once before exporting if recipes are missing.");

            // Direct execution
            new NeiRecipeExportProcessor(this).process();

            Logger.MOD.info("NEI recipe export completed successfully!");
            Logger.chatMessage("=== NEI Recipe Export Complete ===");

        } catch (Exception e) {
            Logger.MOD.error("Error during NEI recipe export", e);
            Logger.chatMessage("WARNING: Some NEI recipes may not have been exported");
        }
    }
}
