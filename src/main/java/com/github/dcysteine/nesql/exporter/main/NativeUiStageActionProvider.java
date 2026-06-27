package com.github.dcysteine.nesql.exporter.main;

import java.util.EnumMap;

final class NativeUiStageActionProvider implements ExportStageActionProvider {
    @Override
    public String id() {
        return "nesqlpp.export.native-ui";
    }

    @Override
    public void register(EnumMap<ExportStage, ExportStageAction> actions, ExportStageActionContext context) {
        actions.put(ExportStage.WRITE_BROWSER_LAYOUT_INDEX, () ->
                ExportWriterSupport.writeBrowserLayoutIndex(
                        context.stageState.runtime.entityManager,
                        context.exportContext.paths.repositoryDirectory));
        actions.put(ExportStage.WRITE_RAW_EXPORT_SIDECAR, () ->
                ExportWriterSupport.writeRawExportSidecar(
                        context.stageState.runtime == null ? null : context.stageState.runtime.entityManager,
                        context.exportContext.paths.repositoryDirectory,
                        context.exportContext,
                        context.stageState.renderAssets));
    }
}
