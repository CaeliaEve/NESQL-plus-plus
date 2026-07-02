package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportKernelContext;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportTraceEvent;
import com.github.dcysteine.nesql.elysium.kernel.ExportTracepoint;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Writes DebugFS-style export diagnostics. These files are not stable ABI. */
final class ExportDebugPlaneWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ExportDebugPlaneWriter() {}

    static StageTiming stageTiming(int index, int total, ExportStage stage, long elapsedMs) {
        return new StageTiming(index, total, stage, elapsedMs);
    }

    static void writeStageTimingReport(
            ExportContext exportContext,
            List<StageTiming> timings,
            long totalElapsedMs) {
        try {
            TimingReport report = new TimingReport();
            report.schemaVersion = "nesqlpp/export-debug-stage-timing/v1";
            report.profile = exportContext.profile.profileId;
            report.selection = exportContext.selection.describe();
            report.totalElapsedMs = totalElapsedMs;
            report.totalElapsed = formatDuration(totalElapsedMs);
            report.stages = timings;

            File validationFile = validationFile(exportContext, "export_stage_timings.json");
            File debugFile = debugFile(exportContext, "export/timing.json");
            writeJson(validationFile, report);
            writeJson(debugFile, report);
            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Stage timing report written: "
                            + validationFile.getAbsolutePath());
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ stage timing report", e);
        }
    }

    static void writeStageCheckpointReport(
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
            StageCheckpointReport report = new StageCheckpointReport();
            report.schemaVersion = "nesqlpp/export-debug-stage-checkpoint/v1";
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

            writeJson(validationFile(exportContext, "stage_checkpoint.json"), report);
            writeJson(debugFile(exportContext, "export/checkpoint.json"), report);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ stage checkpoint report", e);
        }
    }

    static void writeKernelTrace(
            ExportContext exportContext,
            ExportModuleCatalog catalog,
            ExportKernelContext context) {
        try {
            TraceReport report = new TraceReport();
            report.schemaVersion = "nesqlpp/export-debug-kernel-trace/v1";
            report.profile = exportContext.profile.profileId;
            report.selection = exportContext.selection.describe();
            report.tracepoints = ExportTracepoint.all();
            report.modules = catalog.descriptors();
            report.events = context.traceEvents();

            writeJson(validationFile(exportContext, "export_kernel_trace.json"), report);
            writeJson(debugFile(exportContext, "trace/latest.json"), report);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ export kernel trace", e);
        }
    }

    static String formatDuration(long elapsedMs) {
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

    private static File validationFile(ExportContext exportContext, String fileName) {
        return new File(
                new File(exportContext.paths.repositoryDirectory, "raw-export" + File.separator + "validation"),
                fileName);
    }

    private static File debugFile(ExportContext exportContext, String relativePath) {
        return new File(
                new File(exportContext.paths.repositoryDirectory, "raw-export" + File.separator + "debug"),
                relativePath.replace('/', File.separatorChar));
    }

    private static void writeJson(File file, Object value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new java.io.IOException("Failed to create directory: " + parent.getAbsolutePath());
        }
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
    }

    static final class StageTiming {
        int index;
        int total;
        String stage;
        String family;
        String outputKind;
        boolean skippableByChecksum;
        long elapsedMs;
        String elapsed;

        private StageTiming(int index, int total, ExportStage stage, long elapsedMs) {
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

    private static final class TraceReport {
        String schemaVersion;
        String profile;
        String selection;
        List<String> tracepoints;
        List<ExportModuleCatalog.ModuleDescriptor> modules;
        List<ExportTraceEvent> events;
    }
}
