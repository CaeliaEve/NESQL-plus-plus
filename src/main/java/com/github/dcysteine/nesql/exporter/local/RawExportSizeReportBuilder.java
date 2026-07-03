package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;

/** Builds the raw-export size/prohibited-output report from the file ABI catalog. */
final class RawExportSizeReportBuilder {
    private RawExportSizeReportBuilder() {}

    static JsonObject build(String schemaVersion, String generatedAt, File rawDir) {
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", schemaVersion + RawExportFileCatalog.SIZE_REPORT_SCHEMA_SUFFIX);
        report.addProperty("generatedAt", generatedAt);
        report.addProperty("strategy", RawExportFileCatalog.SIZE_REPORT_STRATEGY);
        report.addProperty("totalBytes", directorySize(rawDir));

        JsonArray prohibited = new JsonArray();
        for (String relativePath : RawExportFileCatalog.prohibitedRootOutputs()) {
            addProhibitedFile(prohibited, rawDir, relativePath);
        }
        report.add("prohibitedOutputs", prohibited);
        report.addProperty("status", prohibited.size() == 0 ? "pass" : "fail");
        return report;
    }

    private static void addProhibitedFile(JsonArray out, File rawDir, String relativePath) {
        File file = RawExportFileCatalog.rawExportFile(rawDir, relativePath);
        if (!file.exists()) {
            return;
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("path", relativePath);
        entry.addProperty("bytes", file.isFile() ? file.length() : directorySize(file));
        out.add(entry);
    }

    private static long directorySize(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            total += directorySize(child);
        }
        return total;
    }
}
