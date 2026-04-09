package com.github.dcysteine.nesql.exporter.plugin.bloodmagic;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.sql.Plugin;

/** Plugin which handles Blood Magic (AWWayofTime) recipes. */
public class BloodMagicPluginExporter extends PluginExporter {
    private final BloodMagicRecipeTypeHandler recipeTypeHandler;

    public BloodMagicPluginExporter(Plugin plugin, ExporterState exporterState) {
        super(plugin, exporterState);
        recipeTypeHandler = new BloodMagicRecipeTypeHandler(this);
    }

    @Override
    public void initialize() {
        recipeTypeHandler.initialize();
    }

    @Override
    public void process() {
        // Direct API access to Blood Magic recipe managers is unreliable
        // (static fields are often null at export time due to lazy initialization)
        // We now rely on the NEI plugin to export these recipes as a fallback
        Logger.MOD.info("Blood Magic plugin: Direct API access skipped, relying on NEI fallback");
        Logger.chatMessage("Blood Magic recipes will be exported via NEI handlers");

        // The NEI plugin (running last) will capture all Blood Magic recipes from NEI handlers
        // This approach is more reliable than direct API access

        // If you want to try direct access anyway, uncomment below:
        // try {
        //     new AltarProcessor(this, recipeTypeHandler).process();
        //     new AlchemyArrayProcessor(this, recipeTypeHandler).process();
        //     new SacrificialProcessor(this, recipeTypeHandler).process();
        //     new TartarForgeProcessor(this, recipeTypeHandler).process();
        // } catch (Exception e) {
        //     Logger.MOD.warn("Direct Blood Magic API access failed (expected), NEI will handle it");
        // }
    }
}
