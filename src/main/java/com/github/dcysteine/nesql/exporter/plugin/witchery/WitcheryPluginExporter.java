package com.github.dcysteine.nesql.exporter.plugin.witchery;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.sql.Plugin;

/** Plugin which handles Witchery recipes. */
public class WitcheryPluginExporter extends PluginExporter {
    private final WitcheryRecipeTypeHandler recipeTypeHandler;

    public WitcheryPluginExporter(Plugin plugin, ExporterState exporterState) {
        super(plugin, exporterState);
        recipeTypeHandler = new WitcheryRecipeTypeHandler(this);
    }

    @Override
    public void initialize() {
        recipeTypeHandler.initialize();
    }

    @Override
    public void process() {
        // Witchery's recipe-manager API is not a stable export contract in this
        // pack. The declared ingest surface for Witchery recipes is the NEI
        // handler phase, which mirrors the in-game UI recipe source.
        Logger.MOD.info("Witchery plugin: direct API access disabled; NEI handler ingest is authoritative");
        Logger.chatMessage("Witchery recipes will be exported through NEI handler ingest");
    }
}
