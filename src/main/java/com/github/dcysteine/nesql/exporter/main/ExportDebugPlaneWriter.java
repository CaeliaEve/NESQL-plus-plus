package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportDebugFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportKernelContext;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportTraceEvent;
import com.github.dcysteine.nesql.elysium.kernel.ExportTracepoint;
import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Writes DebugFS-style export diagnostics. These files are not stable ABI. */
final class ExportDebugPlaneWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<DebugReportDescriptor> DEBUG_REPORTS = validateAndFreeze(Arrays.asList(
            new DebugReportDescriptor(
                    ExportDebugFile.STAGE_TIMING,
                    EnumChatFormatting.GREEN + "[NESQL] Stage timing report written: "),
            new DebugReportDescriptor(
                    ExportDebugFile.STAGE_CHECKPOINT,
                    null),
            new DebugReportDescriptor(
                    ExportDebugFile.KERNEL_TRACE,
                    null)));

    private ExportDebugPlaneWriter() {}

    static StageTiming stageTiming(int index, int total, ExportStage stage, long elapsedMs) {
        return new StageTiming(index, total, stage, elapsedMs);
    }

    static void writeStageTimingReport(
            ExportContext exportContext,
            List<StageTiming> timings,
            long totalElapsedMs) throws Exception {
        writeDebugReport(
                exportContext,
                ExportDebugFile.STAGE_TIMING,
                () -> timingReport(exportContext, timings, totalElapsedMs));
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
            String errorSummary) throws Exception {
        writeDebugReport(
                exportContext,
                ExportDebugFile.STAGE_CHECKPOINT,
                () -> stageCheckpointReport(
                        exportContext,
                        timings,
                        completedStages,
                        totalStages,
                        currentStage,
                        nextStage,
                        status,
                        elapsedMs,
                        errorSummary));
    }

    static void writeKernelTrace(
            ExportContext exportContext,
            ExportModuleCatalog catalog,
            ExportKernelContext context) throws Exception {
        writeDebugReport(
                exportContext,
                ExportDebugFile.KERNEL_TRACE,
                () -> traceReport(exportContext, catalog, context));
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

    private static TimingReport timingReport(
            ExportContext exportContext,
            List<StageTiming> timings,
            long totalElapsedMs) {
        TimingReport report = new TimingReport();
        report.schemaVersion = ExportDebugFile.STAGE_TIMING.schemaVersion();
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.totalElapsedMs = totalElapsedMs;
        report.totalElapsed = formatDuration(totalElapsedMs);
        report.stages = timings;
        return report;
    }

    private static StageCheckpointReport stageCheckpointReport(
            ExportContext exportContext,
            List<StageTiming> timings,
            int completedStages,
            int totalStages,
            ExportStage currentStage,
            ExportStage nextStage,
            String status,
            long elapsedMs,
            String errorSummary) {
        StageCheckpointReport report = new StageCheckpointReport();
        report.schemaVersion = ExportDebugFile.STAGE_CHECKPOINT.schemaVersion();
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
        return report;
    }

    private static TraceReport traceReport(
            ExportContext exportContext,
            ExportModuleCatalog catalog,
            ExportKernelContext context) {
        TraceReport report = new TraceReport();
        report.schemaVersion = ExportDebugFile.KERNEL_TRACE.schemaVersion();
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.tracepoints = ExportTracepoint.all();
        report.modules = catalog.descriptors();
        report.events = context.traceEvents();
        return report;
    }

    private static void writeDebugReport(
            ExportContext exportContext,
            ExportDebugFile file,
            DebugReportFactory factory) throws Exception {
        DebugReportDescriptor descriptor = debugReportDescriptor(file);
        if (factory == null) {
            throw new IllegalArgumentException("DebugFS report factory must be non-null: " + file.name());
        }
        Object report = factory.build();
        File validationAliasFile = validationAliasFile(exportContext, descriptor.file());
        File debugFile = debugFile(exportContext, descriptor.file());
        writeJson(validationAliasFile, report);
        writeJson(debugFile, report);
        descriptor.reportSuccess(validationAliasFile);
    }

    private static DebugReportDescriptor debugReportDescriptor(ExportDebugFile file) {
        if (file == null) {
            throw new IllegalArgumentException("DebugFS report file must be non-null");
        }
        for (DebugReportDescriptor descriptor : DEBUG_REPORTS) {
            if (descriptor.file() == file) {
                return descriptor;
            }
        }
        throw new IllegalStateException("Missing DebugFS report descriptor: " + file.name());
    }

    private static File validationAliasFile(ExportContext exportContext, ExportDebugFile file) throws IOException {
        return new File(
                exportContext.rawExportDirectory(),
                file.validationAliasPath().replace('/', File.separatorChar));
    }

    private static File debugFile(ExportContext exportContext, ExportDebugFile file) throws IOException {
        return new File(
                new File(
                        exportContext.rawExportDirectory(),
                        RawExportFileCatalog.DEBUG_DIRECTORY),
                file.debugPath().replace('/', File.separatorChar));
    }

    private static void writeJson(File file, Object value) throws Exception {
        File parent = file.getParentFile();
        if (parent == null) {
            throw new java.io.IOException("DebugFS output parent must not be null: " + file.getAbsolutePath());
        }
        if (parent.exists()) {
            if (!parent.isDirectory()) {
                throw new java.io.IOException("DebugFS output path exists but is not a directory: " + parent.getAbsolutePath());
            }
        } else if (!parent.mkdirs()) {
            throw new java.io.IOException("Failed to create directory: " + parent.getAbsolutePath());
        }
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
    }

    private static List<DebugReportDescriptor> validateAndFreeze(List<DebugReportDescriptor> descriptors) {
        EnumSet<ExportDebugFile> seen = EnumSet.noneOf(ExportDebugFile.class);
        for (DebugReportDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("DebugFS report descriptor must not be null");
            }
            if (!seen.add(descriptor.file())) {
                throw new IllegalStateException("Duplicate DebugFS report descriptor: " + descriptor.file().name());
            }
        }
        for (ExportDebugFile file : ExportDebugFile.values()) {
            if (!seen.contains(file)) {
                throw new IllegalStateException("Missing DebugFS report descriptor: " + file.name());
            }
        }
        return Collections.unmodifiableList(new ArrayList<DebugReportDescriptor>(descriptors));
    }

    private interface DebugReportFactory {
        Object build();
    }

    private static final class DebugReportDescriptor {
        private final ExportDebugFile file;
        private final String successMessagePrefix;

        private DebugReportDescriptor(
                ExportDebugFile file,
                String successMessagePrefix) {
            if (file == null) {
                throw new IllegalArgumentException("DebugFS report file must be non-null");
            }
            this.file = file;
            this.successMessagePrefix = successMessagePrefix;
        }

        private ExportDebugFile file() {
            return file;
        }

        private void reportSuccess(File validationAliasFile) {
            if (successMessagePrefix != null) {
                Logger.chatMessage(successMessagePrefix + validationAliasFile.getAbsolutePath());
            }
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
