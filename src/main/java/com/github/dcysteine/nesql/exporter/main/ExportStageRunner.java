package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.GsonBuilder;

final class ExportStageRunner {

    private ExportStageRunner() {}

    static void run(ExportContext exportContext, ExportExecutionStrategy strategy) throws Exception {
        File repositoryDirectory = exportContext.paths.repositoryDirectory;
        ExportStageState stageState = new ExportStageState();

        strategy.announceStartup(exportContext, repositoryDirectory);
        try {
            Map<ExportStage, ExportStageAction> stageActions =
                    ExportStageActionRegistry.build(exportContext, strategy, stageState);
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
                timings.add(new StageTiming(index, totalStages, stage, stageElapsedMs));
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
            writeTimingReport(exportContext, timings, System.currentTimeMillis() - exportStartedAt);
            ExportValidationReportWriter.write(exportContext);
            ExportIntegrityManifestWriter.write(exportContext);
            ExportWriterSupport.syncRawExportV3FinalReports(exportContext.paths.repositoryDirectory);
        } catch (RepositoryPreparationStoppedException ignored) {
            return;
        } catch (Exception e) {
            File reportFile =
                    ExportDiagnosticsSupport.writeFailureReport(
                            exportContext, stageState.currentStage, e);
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
            if (stageState.session != null) {
                ExportLifecycleSupport.closeSession(stageState.session, strategy.shouldLogEntityManagerClose());
            } else if (stageState.runtime != null) {
                stageState.runtime.close();
            }
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
            File canonicalDir = new File(exportContext.paths.repositoryDirectory, "canonical");
            if (!canonicalDir.exists()) {
                canonicalDir.mkdirs();
            }
            File reportFile = new File(canonicalDir, "export-stage-timings.json");
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

    private static final class TimingReport {
        String schemaVersion;
        String profile;
        String selection;
        long totalElapsedMs;
        String totalElapsed;
        List<StageTiming> stages;
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
