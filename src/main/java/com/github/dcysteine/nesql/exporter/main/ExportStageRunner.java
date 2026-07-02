package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportKernel;
import com.github.dcysteine.nesql.elysium.kernel.ExportKernelContext;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportTracepoint;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ExportStageRunner {

    private ExportStageRunner() {}

    static void run(ExportContext exportContext, ExportExecutionStrategy strategy) throws Exception {
        File repositoryDirectory = exportContext.paths.repositoryDirectory;
        ExportStageState stageState = new ExportStageState();
        ExportModuleCatalog moduleCatalog = ExportStageModules.defaultCatalog();
        ExportKernel kernel = new ExportKernel(moduleCatalog);
        ExportKernelContext kernelContext = new ExportKernelContext(exportContext);

        strategy.announceStartup(exportContext, repositoryDirectory);
        try {
            kernel.init(kernelContext);
            ExportStageActionContext stageActionContext =
                    new ExportStageActionContext(exportContext, kernelContext, strategy, stageState);
            Map<ExportStage, ExportStageAction> stageActions =
                    kernel.buildStageActions(ExportStage.class, stageActionContext);
            int totalStages = exportContext.executionPlan.stages.size();
            int index = 0;
            long exportStartedAt = System.currentTimeMillis();
            List<ExportDebugPlaneWriter.StageTiming> timings =
                    new ArrayList<ExportDebugPlaneWriter.StageTiming>();
            for (ExportStage stage : exportContext.executionPlan.stages) {
                index++;
                stageState.currentStage = stage;
                ExportDiagnosticsSupport.announceStageStart(exportContext, stage, index, totalStages);
                ExportStageAction action = stageActions.get(stage);
                if (action == null) {
                    throw new IllegalStateException("Unhandled export stage: " + stage);
                }
                long stageStartedAt = System.currentTimeMillis();
                action.run();
                long stageElapsedMs = System.currentTimeMillis() - stageStartedAt;
                kernelContext.trace(ExportTracepoint.STAGE_RUN, stage.name(), "ok", stageElapsedMs);
                timings.add(ExportDebugPlaneWriter.stageTiming(index, totalStages, stage, stageElapsedMs));
                ExportDebugPlaneWriter.writeStageCheckpointReport(
                        exportContext,
                        timings,
                        index,
                        totalStages,
                        stage,
                        nextStage(exportContext, index),
                        "running",
                        System.currentTimeMillis() - exportStartedAt,
                        null);
                Logger.chatMessage(
                        EnumChatFormatting.GRAY
                                + "[NESQL] Stage complete: "
                                + stage.name()
                                + " in "
                                + ExportDebugPlaneWriter.formatDuration(stageElapsedMs));
            }
            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Export pipeline runtime: "
                            + ExportDebugPlaneWriter.formatDuration(System.currentTimeMillis() - exportStartedAt));
            ExportDebugPlaneWriter.writeStageCheckpointReport(
                    exportContext,
                    timings,
                    totalStages,
                    totalStages,
                    ExportStage.COMPLETE,
                    null,
                    "complete",
                    System.currentTimeMillis() - exportStartedAt,
                    null);
            ExportDebugPlaneWriter.writeStageTimingReport(
                    exportContext,
                    timings,
                    System.currentTimeMillis() - exportStartedAt);
            ExportValidationReportWriter.write(exportContext);
            ExportControlPlaneWriter.write(exportContext, moduleCatalog);
            ExportIntegrityManifestWriter.write(exportContext);
            ExportWriterSupport.syncRawExportFinalReports(exportContext.paths.repositoryDirectory);
        } catch (RepositoryPreparationStoppedException ignored) {
            return;
        } catch (Exception e) {
            if (stageState.currentStage != null) {
                kernelContext.trace(ExportTracepoint.STAGE_RUN, stageState.currentStage.name(), "failed", 0L);
            }
            File reportFile =
                    ExportDiagnosticsSupport.writeFailureReport(
                            exportContext, stageState.currentStage, e);
            ExportDebugPlaneWriter.writeStageCheckpointReport(
                    exportContext,
                    null,
                    0,
                    exportContext.executionPlan.stages.size(),
                    stageState.currentStage,
                    null,
                    "failed",
                    0L,
                    ExportDiagnosticsSupport.summarizeThrowable(e));
            Logger.chatMessage(
                    EnumChatFormatting.RED
                            + "[NESQL] Export failed at stage: "
                            + (stageState.currentStage == null
                                    ? "<unknown>"
                                    : stageState.currentStage.name()));
            Logger.chatMessage(
                    EnumChatFormatting.RED
                            + "[NESQL] Root cause: "
                            + ExportDiagnosticsSupport.summarizeThrowable(e));
            Logger.chatMessage(
                    EnumChatFormatting.YELLOW
                            + "[NESQL] Debug report: "
                            + reportFile.getAbsolutePath());
            throw e;
        } finally {
            try {
                kernel.exit(kernelContext);
            } finally {
                ExportDebugPlaneWriter.writeKernelTrace(exportContext, moduleCatalog, kernelContext);
            }
            ExportWriterSupport.deleteCanonicalStagingDirectory(exportContext.paths.repositoryDirectory);
        }
        strategy.announceCompletion(exportContext, repositoryDirectory);
    }

    static final class RepositoryPreparationStoppedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static ExportStage nextStage(ExportContext exportContext, int completedIndex) {
        if (completedIndex < 0 || completedIndex >= exportContext.executionPlan.stages.size()) {
            return null;
        }
        return exportContext.executionPlan.stages.get(completedIndex);
    }
}
