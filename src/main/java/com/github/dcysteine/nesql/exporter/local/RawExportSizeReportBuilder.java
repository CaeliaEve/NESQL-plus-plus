package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Builds the raw-export size/prohibited-output report from the file ABI catalog. */
final class RawExportSizeReportBuilder {
    private RawExportSizeReportBuilder() {}

    static JsonObject build(String schemaVersion, String generatedAt, File rawDir) {
        return buildReport(schemaVersion + RawExportFileCatalog.SIZE_REPORT_SCHEMA_SUFFIX, generatedAt, rawDir);
    }

    static void refreshFinal(File rawDir) throws IOException {
        File sizeReportFile = RawExportReportArtifactCatalog.sizeReportFile(rawDir);
        JsonObject existing;
        try (FileInputStream input = new FileInputStream(sizeReportFile);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            if (element == null || !element.isJsonObject()) {
                throw new IOException("Raw-export size report is not a JSON object: " + sizeReportFile.getAbsolutePath());
            }
            existing = element.getAsJsonObject();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse raw-export size report: " + sizeReportFile.getAbsolutePath(), e);
        }
        String schemaVersion = requiredString(existing, "schemaVersion", sizeReportFile);
        String generatedAt = requiredString(existing, "generatedAt", sizeReportFile);
        RawExportSidecarFileOps.writeJson(
                new com.google.gson.GsonBuilder().setPrettyPrinting().create(),
                sizeReportFile,
                buildReport(schemaVersion, generatedAt, rawDir));
    }

    private static JsonObject buildReport(String schemaVersion, String generatedAt, File rawDir) {
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", schemaVersion);
        report.addProperty("generatedAt", generatedAt);
        report.addProperty("strategy", RawExportFileCatalog.SIZE_REPORT_STRATEGY);
        report.addProperty("totalBytes", directorySizeExcludingSizeReport(rawDir));

        JsonArray prohibited = new JsonArray();
        for (String relativePath : RawExportFileCatalog.prohibitedRootOutputs()) {
            addProhibitedFile(prohibited, rawDir, relativePath);
        }
        report.add("prohibitedOutputs", prohibited);
        report.addProperty("status", prohibited.size() == 0 ? "pass" : "fail");
        return report;
    }

    private static String requiredString(JsonObject object, String key, File file) throws IOException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            throw new IOException("Raw-export size report is missing " + key + ": " + file.getAbsolutePath());
        }
        try {
            return element.getAsString();
        } catch (Exception e) {
            throw new IOException("Raw-export size report has invalid " + key + ": " + file.getAbsolutePath(), e);
        }
    }

    static long directorySizeExcludingSizeReport(File rawDir) {
        return directorySizeExcluding(
                rawDir,
                RawExportReportArtifactCatalog.sizeReportFile(rawDir));
    }

    private static long directorySizeExcluding(File file, File excluded) {
        if (file == null || !file.exists() || file.equals(excluded)) {
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
            total += directorySizeExcluding(child, excluded);
        }
        return total;
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
