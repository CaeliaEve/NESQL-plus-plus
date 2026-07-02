package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportResourceManager;

import java.util.EnumMap;
import java.util.List;

final class LifecycleStageActionProvider implements ExportStageActionProvider {
    private static final List<ExportStage> STAGES = ExportStageActionProvider.stageList(
            ExportStage.INITIALIZE_REPOSITORY,
            ExportStage.INITIALIZE_DATABASE,
            ExportStage.INITIALIZE_PLUGINS,
            ExportStage.COLLECT_PLUGIN_DATA,
            ExportStage.COMMIT_DATABASE,
            ExportStage.ROLLBACK_DATABASE,
            ExportStage.COMPLETE);
    private static final List<String> CAPABILITIES = ExportStageActionProvider.capabilityList(
            "export.lifecycle.repository",
            "export.lifecycle.database",
            "export.lifecycle.plugins",
            "export.lifecycle.transaction");

    @Override
    public String id() {
        return "nesqlpp.export.lifecycle";
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
        actions.put(ExportStage.INITIALIZE_REPOSITORY,
                () -> context.prepareRepositoryOrStop(context.strategy.requiresFreshRepository(context.exportContext)));
        actions.put(ExportStage.INITIALIZE_DATABASE, () -> {
            ExportRuntime runtime = context.strategy.createRuntime(context.exportContext);
            context.stageState.runtime = runtime;
            context.kernelContext.resources().add(
                    "export.runtime",
                    runtime,
                    new ExportResourceManager.ResourceReleaser<ExportRuntime>() {
                        @Override
                        public void release(ExportRuntime resource) {
                            if (context.stageState.session == null) {
                                resource.close();
                            }
                        }
                    });
        });
        actions.put(ExportStage.INITIALIZE_PLUGINS, () -> {
            context.stageState.renderingImages =
                    context.strategy.initializeRendering(
                            context.exportContext,
                            context.exportContext.paths.imageDirectory);
            ExportSession session =
                    context.strategy.startSession(context.exportContext, context.stageState.runtime);
            context.stageState.session = session;
            context.kernelContext.resources().add(
                    "export.session",
                    session,
                    new ExportResourceManager.ResourceReleaser<ExportSession>() {
                        @Override
                        public void release(ExportSession resource) {
                            ExportLifecycleSupport.closeSession(
                                    resource,
                                    context.strategy.shouldLogEntityManagerClose());
                        }
                    });
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
