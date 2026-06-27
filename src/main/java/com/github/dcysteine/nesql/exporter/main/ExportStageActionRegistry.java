package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportModule;
import com.github.dcysteine.nesql.elysium.kernel.ExportStageActionRegistrar;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class ExportStageActionRegistry {

    private ExportStageActionRegistry() {}

    static Map<ExportStage, ExportStageAction> build(
            ExportContext exportContext,
            ExportExecutionStrategy strategy,
            ExportStageState stageState,
            List<ExportModule> modules) {
        EnumMap<ExportStage, ExportStageAction> actions = new EnumMap<>(ExportStage.class);
        ExportStageActionContext context = new ExportStageActionContext(exportContext, strategy, stageState);
        for (ExportModule module : modules) {
            ExportStageActionRegistrar<?, ?> registrar = module.stageActionRegistrar();
            if (registrar != null) {
                register(registrar, actions, context);
            }
        }
        return actions;
    }

    @SuppressWarnings("unchecked")
    private static void register(
            ExportStageActionRegistrar<?, ?> registrar,
            EnumMap<ExportStage, ExportStageAction> actions,
            ExportStageActionContext context) {
        ((ExportStageActionRegistrar<EnumMap<ExportStage, ExportStageAction>, ExportStageActionContext>) registrar)
                .register(actions, context);
    }
}
