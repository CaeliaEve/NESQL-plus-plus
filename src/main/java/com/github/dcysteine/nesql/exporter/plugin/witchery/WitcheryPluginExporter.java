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
        // Direct API access to Witchery recipe managers is unreliable
        // We now rely on the NEI plugin to export these recipes as a fallback
        Logger.MOD.info("Witchery plugin: Direct API access skipped, relying on NEI fallback");
        Logger.chatMessage("Witchery recipes will be exported via NEI handlers");

        // The NEI plugin (running last) will capture all Witchery recipes from NEI handlers
        // This approach is more reliable than direct API access

        // If you want to try direct access anyway, uncomment below:
        // try {
        //     new SpinnerProcessor(this, recipeTypeHandler).process();
        //     new DistilleryProcessor(this, recipeTypeHandler).process();
        //     new AltarProcessor(this, recipeTypeHandler).process();
        //     new CauldronProcessor(this, recipeTypeHandler).process();
        // } catch (Exception e) {
        //     Logger.MOD.warn("Direct Witchery API access failed (expected), NEI will handle it");
        // }
    }
}
