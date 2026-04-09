package com.github.dcysteine.nesql.exporter.plugin.botania;

import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.sql.Plugin;

/** Plugin which handles Botania recipes. */
public class BotaniaPluginExporter extends PluginExporter {
    private final BotaniaRecipeTypeHandler recipeTypeHandler;

    public BotaniaPluginExporter(Plugin plugin, ExporterState exporterState) {
        super(plugin, exporterState);
        recipeTypeHandler = new BotaniaRecipeTypeHandler(this);
    }

    @Override
    public void initialize() {
        recipeTypeHandler.initialize();
    }

    @Override
    public void process() {
        new ManaPoolProcessor(this, recipeTypeHandler).process();
        new PureDaisyProcessor(this, recipeTypeHandler).process();
        new PetalApothecaryProcessor(this, recipeTypeHandler).process();
        new RuneAltarProcessor(this, recipeTypeHandler).process();
        new ElvenTradeProcessor(this, recipeTypeHandler).process();
        new BrewRecipeProcessor(this, recipeTypeHandler).process();
        new TerraPlateProcessor(this, recipeTypeHandler).process();
    }
}
