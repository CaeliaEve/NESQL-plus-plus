package com.github.dcysteine.nesql.exporter.main;

import java.util.EnumMap;
import java.util.List;

final class NativeUiStageActionProvider implements ExportStageActionProvider {
    private static final List<ExportStage> STAGES = ExportStageActionProvider.stageList(
            ExportStage.WRITE_BROWSER_LAYOUT_INDEX,
            ExportStage.WRITE_RAW_EXPORT_SIDECAR);
    private static final List<String> CAPABILITIES = ExportStageActionProvider.capabilityList(
            "export.native-ui.browser-layout",
            "export.native-ui.capture",
            "export.raw-export.sidecar");

    @Override
    public String id() {
        return "nesqlpp.export.native-ui";
    }

    @Override
    public List<ExportStage> stages() {
        return STAGES;
    }

    @Override
    public List<String> capabilities() {
        return CAPABILITIES;
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
                        context.exportContext.rawExportDirectory(),
                        context.exportContext,
                        context.stageState.renderAssets));
    }
}
