package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.util.Map;

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
}
