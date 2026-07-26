package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;
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
    public PluginExportResult processResult() {
        // Export item list immediately (no dependency on handlers)
        Logger.MOD.info("Starting NEI item list export...");
        PluginExportResult itemResult = exportItems();
        Logger.MOD.info("Finished NEI item list export!");
        if (itemResult.status == PluginExportResult.Status.PARTIAL
                || itemResult.status == PluginExportResult.Status.FAILED) {
            Logger.MOD.error("NEI item collection is {}; recipe collection will not run", itemResult.status.id);
            return itemResult;
        }

        // Export NEI recipes immediately
        Logger.MOD.info("Starting NEI recipe export...");

        Logger.chatMessage("=== Starting NEI Recipe Export ===");
        Logger.chatMessage("NOTE: If NEI handlers haven't loaded recipes yet, some may be empty.");
        Logger.chatMessage("Try opening NEI GUI once before exporting if recipes are missing.");

        PluginExportResult recipeResult = exportRecipes();

        Logger.MOD.info("NEI recipe export completed with status {}", recipeResult.status.id);
        Logger.chatMessage("=== NEI Recipe Export Complete ===");
        return PluginExportResult.builder().merge(itemResult).merge(recipeResult).build();
    }

    protected PluginExportResult exportItems() {
        return new NeiItemListProcessor(this).process();
    }

    protected PluginExportResult exportRecipes() {
        return new NeiRecipeExportProcessor(this).process();
    }
}
