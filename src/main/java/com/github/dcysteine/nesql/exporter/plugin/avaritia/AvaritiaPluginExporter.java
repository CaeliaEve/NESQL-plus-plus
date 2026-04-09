package com.github.dcysteine.nesql.exporter.plugin.avaritia;

import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.sql.Plugin;

/** Plugin which handles Avaritia recipes. */
public class AvaritiaPluginExporter extends PluginExporter {
    private final AvaritiaRecipeTypeHandler recipeTypeHandler;

    public AvaritiaPluginExporter(Plugin plugin, ExporterState exporterState) {
        super(plugin, exporterState);
        recipeTypeHandler = new AvaritiaRecipeTypeHandler(this);
    }

    @Override
    public void initialize() {
        recipeTypeHandler.initialize();
    }

    @Override
    public void process() {
        new ExtremeCraftingProcessor(this, recipeTypeHandler).process();
    }
}
