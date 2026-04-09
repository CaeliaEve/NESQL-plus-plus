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
        // Direct API access to Thaumcraft recipe managers is unreliable
        // (static fields are often null at export time due to lazy initialization)
        // We now rely on the NEI plugin to export these recipes as a fallback
        Logger.MOD.info("Thaumcraft plugin: Direct API access skipped, relying on NEI fallback");
        Logger.chatMessage("Thaumcraft recipes will be exported via NEI handlers");

        // The NEI plugin (running last) will capture all Thaumcraft recipes from NEI handlers
        // This approach is more reliable than direct API access

        // If you want to try direct access anyway, uncomment below:
        // try {
        //     new InfusionCraftingProcessor(this, recipeTypeHandler).process();
        //     new ArcaneWorkbenchProcessor(this, recipeTypeHandler).process();
        //     new CrucibleProcessor(this, recipeTypeHandler).process();
        //     new AspectCombinationProcessor(this, recipeTypeHandler).process();
        // } catch (Exception e) {
        //     Logger.MOD.warn("Direct Thaumcraft API access failed (expected), NEI will handle it");
        // }
    }
}
