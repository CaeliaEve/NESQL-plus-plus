package com.github.dcysteine.nesql.exporter.main;

import java.util.EnumMap;

final class LifecycleStageActionProvider implements ExportStageActionProvider {
    @Override
    public String id() {
        return "nesqlpp.export.lifecycle";
    }

    @Override
    public void register(EnumMap<ExportStage, ExportStageAction> actions, ExportStageActionContext context) {
        actions.put(ExportStage.INITIALIZE_REPOSITORY,
                () -> context.prepareRepositoryOrStop(context.strategy.requiresFreshRepository(context.exportContext)));
        actions.put(ExportStage.INITIALIZE_DATABASE,
                () -> context.stageState.runtime = context.strategy.createRuntime(context.exportContext));
        actions.put(ExportStage.INITIALIZE_PLUGINS, () -> {
            context.stageState.renderingImages =
                    context.strategy.initializeRendering(
                            context.exportContext,
                            context.exportContext.paths.imageDirectory);
            context.stageState.session =
                    context.strategy.startSession(context.exportContext, context.stageState.runtime);
        });
        actions.put(ExportStage.COLLECT_PLUGIN_DATA,
                () -> context.strategy.runCollectionStage(context.exportContext, context.stageState.runtime));
        actions.put(ExportStage.COMMIT_DATABASE,
                () -> context.strategy.finishTransaction(context.exportContext, context.stageState.session.transaction));
        actions.put(ExportStage.ROLLBACK_DATABASE,
                () -> context.strategy.finishTransaction(context.exportContext, context.stageState.session.transaction));
        actions.put(ExportStage.COMPLETE, () -> {});
    }
}
