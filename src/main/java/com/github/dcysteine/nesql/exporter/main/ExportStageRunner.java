package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportKernel;
import com.github.dcysteine.nesql.elysium.kernel.ExportKernelContext;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.google.gson.GsonBuilder;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
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
        boolean pipelineCompleted = false;

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
            List<StageTiming> timings = new ArrayList<StageTiming>();
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
                kernelContext.trace("export.stage.run", stage.name(), "ok", stageElapsedMs);
                timings.add(new StageTiming(index, totalStages, stage, stageElapsedMs));
                writeCheckpointReport(
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
                                + formatDuration(stageElapsedMs));
            }
            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Export pipeline runtime: "
                            + formatDuration(System.currentTimeMillis() - exportStartedAt));
            writeCheckpointReport(
                    exportContext,
                    timings,
                    totalStages,
                    totalStages,
                    ExportStage.COMPLETE,
                    null,
                    "complete",
                    System.currentTimeMillis() - exportStartedAt,
                    null);
            writeTimingReport(exportContext, timings, System.currentTimeMillis() - exportStartedAt);
            ExportValidationReportWriter.write(exportContext);
            ExportIntegrityManifestWriter.write(exportContext);
            ExportWriterSupport.syncRawExportFinalReports(exportContext.paths.repositoryDirectory);
            pipelineCompleted = true;
        } catch (RepositoryPreparationStoppedException ignored) {
            return;
        } catch (Exception e) {
            if (stageState.currentStage != null) {
                kernelContext.trace("export.stage.run", stageState.currentStage.name(), "failed", 0L);
            }
            File reportFile =
                    ExportDiagnosticsSupport.writeFailureReport(
                            exportContext, stageState.currentStage, e);
            writeCheckpointReport(
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
                kernel.writeTrace(kernelContext);
            }
            ExportWriterSupport.deleteCanonicalStagingDirectory(exportContext.paths.repositoryDirectory);
        }
        strategy.announceCompletion(exportContext, repositoryDirectory);
    }

    static final class RepositoryPreparationStoppedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static String formatDuration(long elapsedMs) {
        long totalSeconds = Math.max(0L, elapsedMs / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (minutes > 0L) {
            return String.format("%dm %02ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    private static void writeTimingReport(
            ExportContext exportContext,
            List<StageTiming> timings,
            long totalElapsedMs) {
        try {
            File validationDir = new File(exportContext.paths.repositoryDirectory, "raw-export" + File.separator + "validation");
            if (!validationDir.exists()) {
                validationDir.mkdirs();
            }
            File reportFile = new File(validationDir, "export_stage_timings.json");
            TimingReport report = new TimingReport();
            report.schemaVersion = "nesqlpp/export-stage-timings/v1";
            report.profile = exportContext.profile.profileId;
            report.selection = exportContext.selection.describe();
            report.totalElapsedMs = totalElapsedMs;
            report.totalElapsed = formatDuration(totalElapsedMs);
            report.stages = timings;
            try (FileOutputStream fos = new FileOutputStream(reportFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
            }
            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Stage timing report written: "
                            + reportFile.getAbsolutePath());
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ stage timing report", e);
        }
    }

    private static ExportStage nextStage(ExportContext exportContext, int completedIndex) {
        if (completedIndex < 0 || completedIndex >= exportContext.executionPlan.stages.size()) {
            return null;
        }
        return exportContext.executionPlan.stages.get(completedIndex);
    }

    private static void writeCheckpointReport(
            ExportContext exportContext,
            List<StageTiming> timings,
            int completedStages,
            int totalStages,
            ExportStage currentStage,
            ExportStage nextStage,
            String status,
            long elapsedMs,
            String errorSummary) {
        try {
            File validationDir = new File(exportContext.paths.repositoryDirectory, "raw-export" + File.separator + "validation");
            if (!validationDir.exists()) {
                validationDir.mkdirs();
            }
            File reportFile = new File(validationDir, "stage_checkpoint.json");
            StageCheckpointReport report = new StageCheckpointReport();
            report.schemaVersion = "nesqlpp/export-stage-checkpoint/v1";
            report.generatedAtEpochMs = System.currentTimeMillis();
            report.profile = exportContext.profile.profileId;
            report.selection = exportContext.selection.describe();
            report.status = status;
            report.completedStages = Math.max(0, completedStages);
            report.totalStages = Math.max(0, totalStages);
            report.currentStage = currentStage == null ? null : currentStage.name();
            report.nextStage = nextStage == null ? null : nextStage.name();
            report.elapsedMs = Math.max(0L, elapsedMs);
            report.elapsed = formatDuration(report.elapsedMs);
            report.errorSummary = errorSummary;
            report.completed = timings == null
                    ? new ArrayList<StageTiming>()
                    : new ArrayList<StageTiming>(timings);
            try (FileOutputStream fos = new FileOutputStream(reportFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ stage checkpoint report", e);
        }
    }

    private static final class TimingReport {
        String schemaVersion;
        String profile;
        String selection;
        long totalElapsedMs;
        String totalElapsed;
        List<StageTiming> stages;
    }

    private static final class StageCheckpointReport {
        String schemaVersion;
        long generatedAtEpochMs;
        String profile;
        String selection;
        String status;
        int completedStages;
        int totalStages;
        String currentStage;
        String nextStage;
        long elapsedMs;
        String elapsed;
        String errorSummary;
        List<StageTiming> completed;
    }

    private static final class StageTiming {
        int index;
        int total;
        String stage;
        String family;
        String outputKind;
        boolean skippableByChecksum;
        long elapsedMs;
        String elapsed;

        StageTiming(int index, int total, ExportStage stage, long elapsedMs) {
            this.index = index;
            this.total = total;
            this.stage = stage.name();
            this.family = ExportStageMetadata.family(stage);
            this.outputKind = ExportStageMetadata.outputKind(stage);
            this.skippableByChecksum = ExportStageMetadata.skippableByChecksum(stage);
            this.elapsedMs = elapsedMs;
            this.elapsed = formatDuration(elapsedMs);
        }
    }
}
