package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.SemanticItemIdentityDiagnosticsWriter;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.util.EnumChatFormatting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
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
