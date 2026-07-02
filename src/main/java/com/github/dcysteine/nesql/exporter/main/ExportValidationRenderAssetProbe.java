package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.util.Locale;

/** Owns render asset manifest, artifact residency, timeline, and singularity animation probes. */
final class ExportValidationRenderAssetProbe {
    private ExportValidationRenderAssetProbe() {}

    static void inspect(
            File repositoryDirectory,
            File rawDir,
            ExportValidationReportWriter.ValidationReport report) {
        File manifestFile = RawExportFileCatalog.rawExportFile(rawDir, RawExportFileCatalog.TEXTURE_INDEX_FILE);
        report.renderAssetManifestPresent = manifestFile.exists();
        if (!manifestFile.exists()) {
            return;
        }

        try (BufferedReader reader = ExportValidationJsonSupport.openMaybeGzipUtf8(manifestFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                JsonElement parsed = new JsonParser().parse(line);
                if (parsed != null && parsed.isJsonObject()) {
                    report.renderAssetManifestAssets++;
                    inspectRow(repositoryDirectory, parsed.getAsJsonObject(), report);
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect raw-export render asset stream for validation", e);
        }
    }

    private static void inspectRow(
            File repositoryDirectory,
            JsonObject asset,
            ExportValidationReportWriter.ValidationReport report) {
        if (asset == null) {
            return;
        }
        String primaryPath = ExportValidationJsonSupport.firstNonEmpty(
                ExportValidationJsonSupport.readStringMember(asset, "primaryArtifact"),
                ExportValidationJsonSupport.readStringMember(asset, "staticFile"),
                ExportValidationJsonSupport.readStringMember(asset, "nativeSpriteAtlasFile"));
        if (primaryPath == null || !exportFileExists(repositoryDirectory, primaryPath)) {
            report.renderAssetMissingPrimaryArtifacts++;
            ExportValidationJsonSupport.addSample(report.renderAssetMissingPrimaryArtifactSamples,
                    ExportValidationJsonSupport.firstNonEmpty(
                            ExportValidationJsonSupport.readStringMember(asset, "assetId"),
                            ExportValidationJsonSupport.readStringMember(asset, "sourcePath"),
                            primaryPath));
        }
        inspectSingularityAnimation(asset, report);
        JsonElement timeline = asset.get("timeline");
        if (timeline == null || !timeline.isJsonArray()) {
            return;
        }
        for (JsonElement frameElement : timeline.getAsJsonArray()) {
            if (frameElement == null || !frameElement.isJsonObject()) {
                continue;
            }
            String path = ExportValidationJsonSupport.readStringMember(frameElement.getAsJsonObject(), "path");
            if (path != null && !exportFileExists(repositoryDirectory, path)) {
                report.renderAssetMissingTimelineFrames++;
                ExportValidationJsonSupport.addSample(report.renderAssetMissingTimelineFrameSamples,
                        ExportValidationJsonSupport.firstNonEmpty(
                                ExportValidationJsonSupport.readStringMember(asset, "assetId"),
                                ExportValidationJsonSupport.readStringMember(asset, "sourcePath"),
                                path));
            }
        }
    }

    private static void inspectSingularityAnimation(
            JsonObject asset,
            ExportValidationReportWriter.ValidationReport report) {
        String haystack = joinLower(
                ExportValidationJsonSupport.readStringMember(asset, "assetId"),
                ExportValidationJsonSupport.readStringMember(asset, "variantKey"),
                ExportValidationJsonSupport.readStringMember(asset, "family"),
                ExportValidationJsonSupport.readStringMember(asset, "sourceType"),
                ExportValidationJsonSupport.readStringMember(asset, "sourcePath"),
                ExportValidationJsonSupport.readStringMember(asset, "primaryArtifact"),
                ExportValidationJsonSupport.readStringMember(asset, "staticFile"),
                ExportValidationJsonSupport.readStringMember(asset, "rendererFamily"),
                ExportValidationJsonSupport.readStringMember(asset, "captureMethod"),
                ExportValidationJsonSupport.readStringMember(asset, "captureSource"),
                ExportValidationJsonSupport.readStringMember(asset, "animationMode"),
                ExportValidationJsonSupport.readStringMember(asset, "renderMode"));
        if (!isSingularityLike(haystack)) {
            return;
        }
        report.singularityLikeRenderAssets++;
        boolean animated = contains(haystack, ".gif")
                || contains(haystack, "animated")
                || contains(haystack, "timeline")
                || ExportValidationJsonSupport.arraySize(asset, "timeline") > 1
                || ExportValidationJsonSupport.arraySize(asset, "frames") > 1
                || ExportValidationJsonSupport.readIntMember(asset, "frameCount") > 1
                || ExportValidationJsonSupport.readIntMember(asset, "capturedFrameCount") > 1;
        if (animated) {
            report.animatedSingularityLikeRenderAssets++;
            return;
        }
        report.suspiciousStaticSingularityAssets++;
        ExportValidationJsonSupport.addSample(report.suspiciousStaticSingularitySamples,
                ExportValidationJsonSupport.firstNonEmpty(
                        ExportValidationJsonSupport.readStringMember(asset, "assetId"),
                        ExportValidationJsonSupport.readStringMember(asset, "sourcePath"),
                        ExportValidationJsonSupport.readStringMember(asset, "primaryArtifact")));
    }

    private static String joinLower(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                builder.append(' ').append(value);
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isSingularityLike(String value) {
        return contains(value, "singularity")
                || contains(value, "singularitie")
                || contains(value, "eternalsingularity")
                || contains(value, "universalsingularity")
                || contains(value, "universal_singularity")
                || contains(value, "avaritia")
                || contains(value, "cosmicneutronium")
                || contains(value, "transcendentmetal")
                || contains(value, "universium");
    }

    private static boolean contains(String value, String needle) {
        return value != null && needle != null && value.contains(needle);
    }

    private static boolean exportFileExists(File repositoryDirectory, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        File direct = new File(repositoryDirectory, relativePath.replace('/', File.separatorChar));
        if (direct.exists() && direct.length() > 0L) {
            return true;
        }
        if (!relativePath.startsWith("image/")) {
            File underImage = new File(repositoryDirectory, ("image/" + relativePath).replace('/', File.separatorChar));
            return underImage.exists() && underImage.length() > 0L;
        }
        return false;
    }
}
