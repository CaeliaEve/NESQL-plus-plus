package com.github.dcysteine.nesql.exporter.plugin.thaumcraft;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.sql.Plugin;

/** Plugin which handles Thaumcraft recipes. */
public class ThaumcraftPluginExporter extends PluginExporter {
    private final ThaumcraftRecipeTypeHandler recipeTypeHandler;

    public ThaumcraftPluginExporter(Plugin plugin, ExporterState exporterState) {
        super(plugin, exporterState);
        recipeTypeHandler = new ThaumcraftRecipeTypeHandler(this);
    }

    @Override
    public void initialize() {
        recipeTypeHandler.initialize();
    }

    @Override
    public void process() {
        // Direct API access to Thaumcraft recipe managers is not a stable export
        // contract in GTNH 1.7.10: key static fields can be absent until the UI
        // path initializes them. The declared ingest surface for Thaumcraft
        // recipes is therefore the NEI handler phase.
        Logger.MOD.info("Thaumcraft plugin: direct API access disabled; NEI handler ingest is authoritative");
        Logger.chatMessage("Thaumcraft recipes will be exported through NEI handler ingest");
    }
}
