package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

final class RawExportReportWriter {
    private RawExportReportWriter() {}

    static void write(String schemaVersion, String generatedAt, File rawDir, RawExportManifest manifest, RawExportReport report)
            throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        writeJson(gson, new File(rawDir, "manifest.json"), manifest);
        writeJson(gson, new File(rawDir, "export_report.json"), report);
        writeJson(gson, new File(rawDir, "validation/export_report.json"), report);
        if (report.neiBrowserContract != null) {
            writeJson(gson, new File(rawDir, "validation/nei_browser_contract.json"), report.neiBrowserContract);
        }
        writeSizeReport(gson, schemaVersion, generatedAt, rawDir);
        createEmptyJsonlIfMissing(new File(rawDir, "validation/errors.jsonl"));
    }

    private static void writeJson(Gson gson, File out, Object value) throws IOException {
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (FileOutputStream fos = new FileOutputStream(out);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(value, writer);
        }
    }

    private static void writeSizeReport(Gson gson, String schemaVersion, String generatedAt, File rawDir)
            throws IOException {
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", schemaVersion + "/size-report");
        report.addProperty("generatedAt", generatedAt);
        report.addProperty("strategy", "raw-export-only");
        report.addProperty("totalBytes", directorySize(rawDir));

        JsonArray prohibited = new JsonArray();
        addProhibitedFile(prohibited, rawDir, "recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "items.jsonl");
        addProhibitedFile(prohibited, rawDir, "fluids.jsonl");
        addProhibitedFile(prohibited, rawDir, "entities.jsonl");
        addProhibitedFile(prohibited, rawDir, "facts/items.jsonl");
        addProhibitedFile(prohibited, rawDir, "facts/fluids.jsonl");
        addProhibitedFile(prohibited, rawDir, "facts/recipes/all.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/gregtech/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/thaumcraft/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/botania/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/bloodmagic/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/forestry/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/eec/recipes.jsonl");
        report.add("prohibitedOutputs", prohibited);
        report.addProperty("status", prohibited.size() == 0 ? "pass" : "fail");
        writeJson(gson, new File(rawDir, "validation/size_report.json"), report);
    }

    private static void addProhibitedFile(JsonArray out, File rawDir, String relativePath) {
        File file = new File(rawDir, relativePath.replace('/', File.separatorChar));
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

    private static void createEmptyJsonlIfMissing(File out) throws IOException {
        if (out.exists()) {
            return;
        }
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (Writer ignored = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            // Empty JSONL remains valid when no validation errors were emitted.
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }
}
