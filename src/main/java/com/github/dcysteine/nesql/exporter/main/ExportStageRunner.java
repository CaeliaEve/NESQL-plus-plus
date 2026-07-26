package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportKernel;
import com.github.dcysteine.nesql.elysium.kernel.ExportKernelContext;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportTracepoint;
import com.github.dcysteine.nesql.exporter.local.RawExportGenerationFinalizer;
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
        Exception primaryFailure = null;
        boolean preparationStopped = false;
        boolean kernelExited = false;
        boolean kernelTraceWritten = false;
        boolean generationPublished = false;

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
            kernel.exit(kernelContext);
            kernelExited = true;
            ExportDebugPlaneWriter.writeKernelTrace(exportContext, moduleCatalog, kernelContext);
            kernelTraceWritten = true;
            ExportDebugPlaneWriter.writeStageTimingReport(
                    exportContext,
                    timings,
                    System.currentTimeMillis() - exportStartedAt);
            ExportValidationReportWriter.write(exportContext);
            ExportControlPlaneWriter.write(exportContext, moduleCatalog);
            ExportIntegrityManifestWriter.write(exportContext);
            ExportWriterSupport.syncRawExportFinalReports(exportContext.rawExportDirectory());
            RawExportGenerationFinalizer.finalizeGeneration(exportContext.rawExportDirectory());
            exportContext.publishRawExportGeneration();
            generationPublished = true;
        } catch (RepositoryPreparationStoppedException ignored) {
            preparationStopped = true;
            return;
        } catch (Exception e) {
            primaryFailure = e;
            if (stageState.currentStage != null) {
                kernelContext.trace(ExportTracepoint.STAGE_RUN, stageState.currentStage.name(), "failed", 0L);
            }
            try {
                exportContext.ensureRawExportGeneration();
            } catch (Exception generationFailure) {
                e.addSuppressed(generationFailure);
            }
            File reportFile =
                    ExportDiagnosticsSupport.writeFailureReport(
                            exportContext, stageState.currentStage, e);
            if (exportContext.hasActiveRawExportGeneration()) {
                try {
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
                } catch (Exception checkpointFailure) {
                    e.addSuppressed(checkpointFailure);
                }
            }
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
            Exception finalizationFailure = null;
            if (!kernelExited) {
                try {
                    kernel.exit(kernelContext);
                    kernelExited = true;
                } catch (Exception exitFailure) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(exitFailure);
                    } else {
                        finalizationFailure = exitFailure;
                    }
                }
            }
            if (!preparationStopped
                    && !generationPublished
                    && !kernelTraceWritten
                    && exportContext.hasActiveRawExportGeneration()) {
                try {
                    ExportDebugPlaneWriter.writeKernelTrace(exportContext, moduleCatalog, kernelContext);
                    kernelTraceWritten = true;
                } catch (Exception traceFailure) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(traceFailure);
                    } else if (finalizationFailure != null) {
                        finalizationFailure.addSuppressed(traceFailure);
                    } else {
                        finalizationFailure = traceFailure;
                    }
                }
            }
            if (!generationPublished) {
                try {
                    exportContext.abortRawExportGeneration();
                } catch (Exception abortFailure) {
                    if (primaryFailure != null) {
                        primaryFailure.addSuppressed(abortFailure);
                    } else if (finalizationFailure != null) {
                        finalizationFailure.addSuppressed(abortFailure);
                    } else {
                        finalizationFailure = abortFailure;
                    }
                }
            }
            ExportWriterSupport.deleteCanonicalStagingDirectory(exportContext.paths.repositoryDirectory);
            if (finalizationFailure != null) {
                throw finalizationFailure;
            }
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
