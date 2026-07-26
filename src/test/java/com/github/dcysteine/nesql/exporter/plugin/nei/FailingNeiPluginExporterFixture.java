package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;
import com.github.dcysteine.nesql.sql.Plugin;

/** Test fixture that injects a row failure through the real NEI batch-result boundary. */
public final class FailingNeiPluginExporterFixture extends NeiPluginExporter {
    public FailingNeiPluginExporterFixture(ExporterState exporterState) {
        super(Plugin.NEI, exporterState);
    }

    @Override
    protected PluginExportResult exportItems() {
        return PluginExportResult.builder()
                .status(PluginExportResult.Status.SUCCESS)
                .count("itemsExported", 1)
                .build();
    }

    @Override
    protected PluginExportResult exportRecipes() {
        NeiRecipeBatchOutcome outcome = new NeiRecipeBatchOutcome();
        try {
            NeiRecipeExportProcessor.exportRowsFailClosed(
                    "mobsinfo.mobhandler",
                    1,
                    new NeiRecipeExportProcessor.RecipeRowExporter() {
                        @Override
                        public boolean export(int recipeIndex) {
                            throw new IllegalStateException("EEC row failed");
                        }
                    });
            throw new AssertionError("Expected EEC row failure");
        } catch (IllegalStateException handlerFailure) {
            outcome.recordFailure(
                    "MobsInfo - mobsinfo.mobhandler",
                    FailingNeiPluginExporterFixture.class.getName(),
                    handlerFailure);
        }
        return outcome.build(1, 0, 0);
    }
}
