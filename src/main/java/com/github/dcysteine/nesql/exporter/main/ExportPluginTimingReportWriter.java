package com.github.dcysteine.nesql.exporter.main;

import com.google.gson.GsonBuilder;
import com.github.dcysteine.nesql.exporter.plugin.nei.NeiExportTimingRegistry;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
            File rawValidationDir = new File(exportContext.paths.repositoryDirectory, "raw-export/validation");
            if (!rawValidationDir.exists()) {
                rawValidationDir.mkdirs();
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

            com.google.gson.Gson gson = new GsonBuilder().setPrettyPrinting().create();
            File reportFile = new File(canonicalDir, "export-plugin-timings.json");
            try (FileOutputStream fos = new FileOutputStream(reportFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                gson.toJson(report, writer);
            }
            File rawTimingFile = new File(rawValidationDir, "export-plugin-timings.json");
            try (FileOutputStream fos = new FileOutputStream(rawTimingFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                gson.toJson(report, writer);
            }

            NeiHandlerAnomalyReport anomalyReport = buildAnomalyReport(exportContext, report.neiHandlerTimings);
            File anomalyFile = new File(rawValidationDir, "nei_handler_anomalies.json");
            try (FileOutputStream fos = new FileOutputStream(anomalyFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                gson.toJson(anomalyReport, writer);
            }

            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Plugin timing report written: "
                            + reportFile.getAbsolutePath());
            if (anomalyReport.summary.suspiciousZeroExports > 0 || anomalyReport.summary.partialExports > 0) {
                Logger.chatMessage(
                        EnumChatFormatting.YELLOW
                                + "[NESQL] NEI handler anomaly report: suspiciousZero="
                                + anomalyReport.summary.suspiciousZeroExports
                                + ", partial="
                                + anomalyReport.summary.partialExports);
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ plugin timing report", e);
        }
    }

    private static NeiHandlerAnomalyReport buildAnomalyReport(
            ExportContext exportContext,
            List<NeiExportTimingRegistry.HandlerTiming> timings) {
        NeiHandlerAnomalyReport report = new NeiHandlerAnomalyReport();
        report.schemaVersion = "nesqlpp/raw-export/alpha1/nei-handler-anomalies";
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();
        report.summary = new NeiHandlerAnomalySummary();
        report.handlers = new ArrayList<NeiHandlerAnomaly>();

        if (timings == null) {
            return report;
        }

        for (NeiExportTimingRegistry.HandlerTiming timing : timings) {
            if (timing == null) {
                continue;
            }
            report.summary.totalHandlers++;
            if (timing.loadedRecipes > 0) {
                report.summary.handlersWithLoadedRecipes++;
            }
            if (timing.exportedRecipes > 0) {
                report.summary.handlersWithExportedRecipes++;
            }

            NeiHandlerAnomaly anomaly = classify(timing);
            increment(report.summary.categories, anomaly.category);
            increment(report.summary.severities, anomaly.severity);
            if ("suspicious-zero-export".equals(anomaly.category)) {
                report.summary.suspiciousZeroExports++;
            } else if ("partial-export".equals(anomaly.category)) {
                report.summary.partialExports++;
            } else if ("native-export-covered-zero".equals(anomaly.category)) {
                report.summary.nativeCoveredZeroExports++;
            } else if ("expected-empty".equals(anomaly.category)) {
                report.summary.expectedEmptyHandlers++;
            }
            if (!"ok".equals(anomaly.category) && report.handlers.size() < 300) {
                report.handlers.add(anomaly);
            }
        }

        report.summary.status =
                report.summary.suspiciousZeroExports > 0 || report.summary.partialExports > 0
                        ? "warning"
                        : "ok";
        return report;
    }

    private static NeiHandlerAnomaly classify(NeiExportTimingRegistry.HandlerTiming timing) {
        NeiHandlerAnomaly anomaly = new NeiHandlerAnomaly();
        anomaly.index = timing.index;
        anomaly.total = timing.total;
        anomaly.handlerId = timing.handlerId;
        anomaly.handlerName = timing.handlerName;
        anomaly.handlerClass = timing.handlerClass;
        anomaly.templateHandler = timing.templateHandler;
        anomaly.itemScan = timing.itemScan;
        anomaly.loadedRecipes = timing.loadedRecipes;
        anomaly.exportedRecipes = timing.exportedRecipes;
        anomaly.loadElapsedMs = timing.loadElapsedMs;
        anomaly.exportElapsedMs = timing.exportElapsedMs;
        anomaly.totalElapsedMs = timing.totalElapsedMs;

        if (timing.loadedRecipes <= 0 && timing.exportedRecipes <= 0) {
            anomaly.category = "expected-empty";
            anomaly.severity = "info";
            anomaly.reason = "Handler reported no recipes to load; this is normal for empty, dynamic, or selection-skipped NEI pages.";
            return anomaly;
        }
        if (timing.loadedRecipes > 0 && timing.exportedRecipes == 0) {
            if (isNativeExporterCovered(timing)) {
                anomaly.category = "native-export-covered-zero";
                anomaly.severity = "info";
                anomaly.reason = "NEI handler loaded recipes, but this domain is primarily exported by a native NESQL++ plugin; keep for parity checks.";
                return anomaly;
            }
            if (isInformationalHandler(timing)) {
                anomaly.category = "non-recipe-info-zero";
                anomaly.severity = "info";
                anomaly.reason = "Handler is known to describe guide/statistical pages that may not map to item-output recipes.";
                return anomaly;
            }
            anomaly.category = "suspicious-zero-export";
            anomaly.severity = "warning";
            anomaly.reason = "Handler loaded recipes but NESQL++ exported none; this likely needs a handler adapter or metadata extractor.";
            return anomaly;
        }
        if (timing.loadedRecipes > 0 && timing.exportedRecipes > 0) {
            double ratio = ((double) timing.exportedRecipes) / ((double) timing.loadedRecipes);
            if (ratio < 0.10d) {
                anomaly.category = "partial-export";
                anomaly.severity = "warning";
                anomaly.reason = "Handler exported less than 10% of loaded recipes; this may be valid deduping, but should be checked.";
                return anomaly;
            }
        }
        anomaly.category = "ok";
        anomaly.severity = "ok";
        anomaly.reason = "Loaded/exported counts are within the current validation threshold.";
        return anomaly;
    }

    private static boolean isNativeExporterCovered(NeiExportTimingRegistry.HandlerTiming timing) {
        String className = lower(timing.handlerClass);
        String name = lower(timing.handlerName);
        return className.contains("gregtech.nei.gtneidefaulthandler")
                || name.contains("gregtech");
    }

    private static boolean isInformationalHandler(NeiExportTimingRegistry.HandlerTiming timing) {
        String className = lower(timing.handlerClass);
        String name = lower(timing.handlerName);
        return className.contains("gtneioreplugin")
                || className.contains("questrecipehandler")
                || name.contains("矿脉")
                || name.contains("贫瘠矿石")
                || name.contains("quest");
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static void increment(Map<String, Integer> map, String key) {
        if (key == null) {
            key = "unknown";
        }
        Integer current = map.get(key);
        map.put(key, current == null ? 1 : current + 1);
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

    private static final class NeiHandlerAnomalyReport {
        String schemaVersion;
        String profile;
        String selection;
        NeiHandlerAnomalySummary summary;
        List<NeiHandlerAnomaly> handlers;
    }

    private static final class NeiHandlerAnomalySummary {
        String status;
        int totalHandlers;
        int handlersWithLoadedRecipes;
        int handlersWithExportedRecipes;
        int suspiciousZeroExports;
        int nativeCoveredZeroExports;
        int partialExports;
        int expectedEmptyHandlers;
        Map<String, Integer> categories = new LinkedHashMap<String, Integer>();
        Map<String, Integer> severities = new LinkedHashMap<String, Integer>();
    }

    private static final class NeiHandlerAnomaly {
        int index;
        int total;
        String handlerId;
        String handlerName;
        String handlerClass;
        boolean templateHandler;
        boolean itemScan;
        int loadedRecipes;
        int exportedRecipes;
        long loadElapsedMs;
        long exportElapsedMs;
        long totalElapsedMs;
        String category;
        String severity;
        String reason;
    }
}
