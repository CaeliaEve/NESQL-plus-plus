package com.github.dcysteine.nesql.exporter.main;

import com.google.gson.GsonBuilder;
import com.github.dcysteine.nesql.exporter.plugin.nei.NeiExportTimingRegistry;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Writes fine-grained plugin phase timings for the expensive collection stage. */
final class ExportPluginTimingReportWriter {
    private ExportPluginTimingReportWriter() {}

    static void write(ExportContext exportContext, ExportRuntime exportRuntime) {
        if (exportRuntime == null || exportRuntime.pluginTimings.isEmpty()) {
            return;
        }

        try {
            File canonicalDir = new File(exportContext.paths.repositoryDirectory, "canonical");
            if (!canonicalDir.exists()) {
                canonicalDir.mkdirs();
            }

            PluginTimingReport report = new PluginTimingReport();
            report.schemaVersion = "nesqlpp/export-plugin-timings/v1";
            report.profile = exportContext.profile.profileId;
            report.selection = exportContext.selection.describe();
            report.timings = new ArrayList<ExportRuntime.PluginTiming>(exportRuntime.pluginTimings);
            report.neiHandlerTimings = NeiExportTimingRegistry.snapshot();
            report.slowestNeiHandlers = NeiExportTimingRegistry.slowestSnapshot(50);
            report.slowest = new ArrayList<ExportRuntime.PluginTiming>(exportRuntime.pluginTimings);
            report.slowest.sort(
                    new Comparator<ExportRuntime.PluginTiming>() {
                        @Override
                        public int compare(ExportRuntime.PluginTiming left, ExportRuntime.PluginTiming right) {
                            return Long.compare(right.elapsedMs, left.elapsedMs);
                        }
                    });
            if (report.slowest.size() > 20) {
                report.slowest = new ArrayList<ExportRuntime.PluginTiming>(report.slowest.subList(0, 20));
            }

            File reportFile = new File(canonicalDir, "export-plugin-timings.json");
            try (FileOutputStream fos = new FileOutputStream(reportFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
            }

            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Plugin timing report written: "
                            + reportFile.getAbsolutePath());
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ plugin timing report", e);
        }
    }

    private static final class PluginTimingReport {
        String schemaVersion;
        String profile;
        String selection;
        List<ExportRuntime.PluginTiming> timings;
        List<ExportRuntime.PluginTiming> slowest;
        List<NeiExportTimingRegistry.HandlerTiming> neiHandlerTimings;
        List<NeiExportTimingRegistry.HandlerTiming> slowestNeiHandlers;
    }
}
