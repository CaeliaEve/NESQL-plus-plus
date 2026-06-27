package com.github.dcysteine.nesql.exporter.main;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class ExportStageActionRegistry {

    private ExportStageActionRegistry() {}

    static Map<ExportStage, ExportStageAction> build(
            ExportContext exportContext,
            ExportExecutionStrategy strategy,
            ExportStageState stageState) {
        EnumMap<ExportStage, ExportStageAction> actions = new EnumMap<>(ExportStage.class);
        ExportStageActionContext context = new ExportStageActionContext(exportContext, strategy, stageState);
        for (ExportStageActionProvider provider : providers()) {
            provider.register(actions, context);
        }
        return actions;
    }

    private static List<ExportStageActionProvider> providers() {
        return Arrays.<ExportStageActionProvider>asList(
                new LifecycleStageActionProvider(),
                new RawFactStageActionProvider(),
                new RenderStageActionProvider(),
                new NativeUiStageActionProvider());
    }
}
