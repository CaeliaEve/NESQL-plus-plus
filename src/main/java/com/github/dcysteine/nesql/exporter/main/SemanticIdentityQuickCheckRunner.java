package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.SemanticItemIdentityDiagnosticsWriter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.EnumChatFormatting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * Rewrites and validates the semantic item-identity JSONL streams from an
 * existing NESQL database.
 *
 * <p>This is intentionally much faster than a full export: it does not scan NEI
 * recipes, render images, export multiblocks, or rebuild atlases. It only opens
 * the already-exported repository database, rewrites facts/items semantic
 * streams, and validates strict one-object-per-line JSONL shape for NeoNEI.</p>
 */
final class SemanticIdentityQuickCheckRunner {
    private static final String[] STREAMS = new String[] {
            "semantic-items.jsonl.gz",
            "variants.jsonl.gz",
            "payloads.jsonl.gz",
            "identity-map.jsonl.gz"
    };

    private SemanticIdentityQuickCheckRunner() {}

    static void run(String repositoryName) throws Exception {
        ExportPaths paths = ExportPaths.forRepository(repositoryName);
        File rawExportDirectory = new File(paths.repositoryDirectory, "raw-export");
        if (!rawExportDirectory.exists() && !rawExportDirectory.mkdirs()) {
            throw new IllegalStateException("Failed to create raw-export directory: " + rawExportDirectory);
        }
        File rawItemsFile = new File(rawExportDirectory, "facts/items.jsonl.gz");
        if (!rawItemsFile.exists()) {
            throw new IllegalStateException(
                    "No raw item fact stream found for repository '"
                            + repositoryName
                            + "'. Run one normal export first.");
        }

        long startedAt = System.currentTimeMillis();
        SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary summary =
                SemanticItemIdentityDiagnosticsWriter.writeFromRawItems(rawExportDirectory);
        JsonlValidationSummary validation = validateSemanticStreams(rawExportDirectory);
        refreshRawExportReports(rawExportDirectory, summary);

        Logger.chatMessage(
                EnumChatFormatting.GREEN
                        + "[NESQL] Quick semantic JSONL check complete in "
                        + formatDuration(System.currentTimeMillis() - startedAt));
        Logger.chatMessage(
                EnumChatFormatting.GRAY
                        + "[NESQL] items="
                        + summary.totalItems
                        + ", tagged="
                        + summary.taggedItems
                        + ", semanticItems="
                        + summary.semanticItems
                        + ", variants="
                        + summary.variants
                        + ", payloads="
                        + summary.payloads
                        + ", identityMap="
                        + summary.identityMapRows);
        Logger.chatMessage(
                EnumChatFormatting.GREEN
                        + "[NESQL] JSONL validation: "
                        + validation.files
                        + " files, "
                        + validation.lines
                        + " compact object lines.");
        Logger.chatMessage(
                EnumChatFormatting.YELLOW
                        + "[NESQL] Output: "
                        + new File(rawExportDirectory, "facts/items").getAbsolutePath());
        ExportWriterSupport.deleteCanonicalStagingDirectory(paths.repositoryDirectory);
    }


    private static void refreshRawExportReports(
            File rawExportDirectory,
            SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary summary) throws Exception {
        refreshReportFile(new File(rawExportDirectory, "export_report.json"), summary);
        refreshReportFile(new File(rawExportDirectory, "validation/export_report.json"), summary);
        refreshManifestFile(new File(rawExportDirectory, "manifest.json"), summary);
    }

    private static void refreshReportFile(
            File reportFile,
            SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary summary) throws Exception {
        if (!reportFile.exists()) {
            return;
        }
        JsonObject root = readJsonObject(reportFile);
        if (root == null) {
            return;
        }
        JsonObject counts = object(root, "counts");
        applySemanticCounts(counts, summary);
        JsonObject validation = object(root, "validation");
        JsonArray gates = array(validation, "gates");
        upsertSemanticIdentityGate(gates, counts, summary);
        validation.addProperty("readinessStatus", allGatesReady(gates) ? "ready" : "blocked");
        validation.addProperty("status", allGatesReady(gates) ? "ok" : "warning");
        writeJson(reportFile, root);
    }

    private static void refreshManifestFile(
            File manifestFile,
            SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary summary) throws Exception {
        if (!manifestFile.exists()) {
            return;
        }
        JsonObject root = readJsonObject(manifestFile);
        if (root == null) {
            return;
        }
        JsonObject counts = object(root, "counts");
        applySemanticCounts(counts, summary);
        writeJson(manifestFile, root);
    }

    private static void applySemanticCounts(
            JsonObject counts,
            SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary summary) {
        counts.addProperty("semanticTotalItems", summary.totalItems);
        counts.addProperty("semanticTaggedItems", summary.taggedItems);
        counts.addProperty("semanticClassifiedTaggedItems", summary.classifiedTaggedItems);
        counts.addProperty("semanticUnclassifiedTaggedItems", summary.unclassifiedTaggedItems);
        counts.addProperty("semanticEstimatedPublicItems", summary.estimatedPublicItemsAfterNormalization);
        counts.addProperty("semanticFamilyCount", summary.familyCount);
        counts.addProperty("semanticItems", summary.semanticItems);
        counts.addProperty("semanticVariants", summary.variants);
        counts.addProperty("semanticPayloads", summary.payloads);
        counts.addProperty("semanticIdentityMapRows", summary.identityMapRows);
    }

    private static void upsertSemanticIdentityGate(
            JsonArray gates,
            JsonObject counts,
            SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary summary) {
        long rawItems = longValue(counts, "rawItems", summary.totalItems);
        boolean ready = rawItems == 0L
                || (summary.totalItems == rawItems
                && summary.identityMapRows == rawItems
                && summary.semanticItems > 0L
                && summary.familyCount > 0L);
        JsonObject gate = null;
        for (int i = 0; i < gates.size(); i++) {
            JsonElement element = gates.get(i);
            if (element != null && element.isJsonObject()) {
                JsonObject object = element.getAsJsonObject();
                if ("semantic-identity".equals(stringValue(object, "name"))) {
                    gate = object;
                    break;
                }
            }
        }
        if (gate == null) {
            gate = new JsonObject();
            gate.addProperty("name", "semantic-identity");
            gates.add(gate);
        }
        gate.addProperty("status", ready ? "ready" : "blocked");
        gate.addProperty(
                "summary",
                "Semantic identity streams: rawItems="
                        + rawItems
                        + ", totalItems="
                        + summary.totalItems
                        + ", identityMapRows="
                        + summary.identityMapRows
                        + ", semanticItems="
                        + summary.semanticItems
                        + ", families="
                        + summary.familyCount
                        + ", classifiedTagged="
                        + summary.classifiedTaggedItems
                        + ", unclassifiedTagged="
                        + summary.unclassifiedTaggedItems
                        + ".");
    }

    private static boolean allGatesReady(JsonArray gates) {
        if (gates == null || gates.size() == 0) {
            return false;
        }
        for (int i = 0; i < gates.size(); i++) {
            JsonElement element = gates.get(i);
            if (element == null || !element.isJsonObject()) {
                return false;
            }
            if (!"ready".equals(stringValue(element.getAsJsonObject(), "status"))) {
                return false;
            }
        }
        return true;
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement existing = root.get(key);
        if (existing != null && existing.isJsonObject()) {
            return existing.getAsJsonObject();
        }
        JsonObject created = new JsonObject();
        root.add(key, created);
        return created;
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement existing = root.get(key);
        if (existing != null && existing.isJsonArray()) {
            return existing.getAsJsonArray();
        }
        JsonArray created = new JsonArray();
        root.add(key, created);
        return created;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsLong();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String stringValue(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? "" : element.getAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static JsonObject readJsonObject(File file) throws Exception {
        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JsonElement parsed = new JsonParser().parse(reader);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        }
    }

    private static void writeJson(File file, JsonObject object) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create directory: " + parent.getAbsolutePath());
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            gson.toJson(object, writer);
        }
    }

    private static JsonlValidationSummary validateSemanticStreams(File rawExportDirectory) throws Exception {
        File itemDir = new File(rawExportDirectory, "facts/items");
        JsonlValidationSummary summary = new JsonlValidationSummary();
        for (String stream : STREAMS) {
            File file = new File(itemDir, stream);
            if (!file.exists()) {
                throw new IllegalStateException("Missing semantic stream: " + file.getAbsolutePath());
            }
            long lines = validateJsonlFile(file);
            summary.files++;
            summary.lines += lines;
        }
        return summary;
    }

    private static long validateJsonlFile(File file) throws Exception {
        long lines = 0L;
        JsonParser parser = new JsonParser();
        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     new GZIPInputStream(new FileInputStream(file)),
                                     StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0) {
                    continue;
                }
                if (line.indexOf('\n') >= 0 || !line.startsWith("{") || !line.endsWith("}")) {
                    throw new IllegalStateException(
                            "Invalid compact JSONL object in "
                                    + file.getName()
                                    + " at line "
                                    + (lines + 1));
                }
                JsonElement parsed = parser.parse(line);
                if (parsed == null || !parsed.isJsonObject()) {
                    throw new IllegalStateException(
                            "JSONL row is not an object in "
                                    + file.getName()
                                    + " at line "
                                    + (lines + 1));
                }
                lines++;
            }
        }
        if (lines == 0L) {
            throw new IllegalStateException("Semantic stream is empty: " + file.getAbsolutePath());
        }
        return lines;
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

    private static final class JsonlValidationSummary {
        long files;
        long lines;
    }
}
