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
        Logger.MOD.info("Blood Magic plugin: attempting direct API export before NEI fallback");
        Logger.chatMessage("Blood Magic direct export: altar / alchemy / sacrificial / tartaric");

        boolean hadFailure = false;
        hadFailure |= runProcessor("altar", () -> new AltarProcessor(this, recipeTypeHandler).process());
        hadFailure |= runProcessor("alchemy array", () -> new AlchemyArrayProcessor(this, recipeTypeHandler).process());
        hadFailure |= runProcessor("sacrificial", () -> new SacrificialProcessor(this, recipeTypeHandler).process());
        hadFailure |= runProcessor("tartaric forge", () -> new TartarForgeProcessor(this, recipeTypeHandler).process());

        if (hadFailure) {
            Logger.MOD.warn("Blood Magic direct API export completed with one or more failures; NEI fallback will still run");
            Logger.chatMessage("Blood Magic direct export had errors, but NEI fallback will handle any missing recipes");
        } else {
            Logger.MOD.info("Blood Magic direct API export completed");
        }
    }

    private boolean runProcessor(String name, Runnable processor) {
        try {
            processor.run();
            return false;
        } catch (Exception e) {
            Logger.MOD.warn("Blood Magic direct API export failed for {} processor, NEI fallback will still run", name, e);
            return true;
        }
    }
}
