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
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.PRIMARY_ARTIFACT),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.STATIC_FILE),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.NATIVE_SPRITE_ATLAS_FILE));
        if (primaryPath == null || !exportFileExists(repositoryDirectory, primaryPath)) {
            report.renderAssetMissingPrimaryArtifacts++;
            ExportValidationJsonSupport.addSample(report.renderAssetMissingPrimaryArtifactSamples,
                    ExportValidationJsonSupport.firstNonEmpty(
                            ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.ASSET_ID),
                            ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.SOURCE_PATH),
                            primaryPath));
        }
        inspectSingularityAnimation(asset, report);
        JsonElement timeline = asset.get(ExportValidationEvidenceCatalog.OBJECT_TIMELINE);
        if (timeline == null || !timeline.isJsonArray()) {
            return;
        }
        for (JsonElement frameElement : timeline.getAsJsonArray()) {
            if (frameElement == null || !frameElement.isJsonObject()) {
                continue;
            }
            String path = ExportValidationJsonSupport.readStringMember(frameElement.getAsJsonObject(), ExportValidationEvidenceCatalog.MEMBER_PATH);
            if (path != null && !exportFileExists(repositoryDirectory, path)) {
                report.renderAssetMissingTimelineFrames++;
                ExportValidationJsonSupport.addSample(report.renderAssetMissingTimelineFrameSamples,
                        ExportValidationJsonSupport.firstNonEmpty(
                                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.ASSET_ID),
                                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.SOURCE_PATH),
                                path));
            }
        }
    }

    private static void inspectSingularityAnimation(
            JsonObject asset,
            ExportValidationReportWriter.ValidationReport report) {
        String haystack = joinLower(
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.ASSET_ID),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.VARIANT_KEY),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.FAMILY),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.SOURCE_TYPE),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.SOURCE_PATH),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.PRIMARY_ARTIFACT),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.STATIC_FILE),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.RENDERER_FAMILY),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.CAPTURE_METHOD),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.CAPTURE_SOURCE),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.ANIMATION_MODE),
                ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.RENDER_MODE));
        if (!isSingularityLike(haystack)) {
            return;
        }
        report.singularityLikeRenderAssets++;
        boolean animated = contains(haystack, ExportValidationEvidenceCatalog.ANIMATION_TOKEN_GIF)
                || contains(haystack, ExportValidationEvidenceCatalog.ANIMATION_TOKEN_ANIMATED)
                || contains(haystack, ExportValidationEvidenceCatalog.ANIMATION_TOKEN_TIMELINE)
                || ExportValidationJsonSupport.arraySize(asset, ExportValidationEvidenceCatalog.OBJECT_TIMELINE) > 1
                || ExportValidationJsonSupport.arraySize(asset, ExportValidationEvidenceCatalog.OBJECT_FRAMES) > 1
                || ExportValidationJsonSupport.readIntMember(asset, ExportValidationEvidenceCatalog.RenderAsset.FRAME_COUNT) > 1
                || ExportValidationJsonSupport.readIntMember(asset, ExportValidationEvidenceCatalog.RenderAsset.CAPTURED_FRAME_COUNT) > 1;
        if (animated) {
            report.animatedSingularityLikeRenderAssets++;
            return;
        }
        report.suspiciousStaticSingularityAssets++;
        ExportValidationJsonSupport.addSample(report.suspiciousStaticSingularitySamples,
                ExportValidationJsonSupport.firstNonEmpty(
                        ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.ASSET_ID),
                        ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.SOURCE_PATH),
                        ExportValidationJsonSupport.readStringMember(asset, ExportValidationEvidenceCatalog.RenderAsset.PRIMARY_ARTIFACT)));
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
        return contains(value, ExportValidationEvidenceCatalog.SINGULARITY_TOKEN_SINGULARITY)
                || contains(value, ExportValidationEvidenceCatalog.SINGULARITY_TOKEN_SINGULARITIE)
                || contains(value, ExportValidationEvidenceCatalog.SINGULARITY_TOKEN_ETERNAL)
                || contains(value, ExportValidationEvidenceCatalog.SINGULARITY_TOKEN_UNIVERSAL)
                || contains(value, ExportValidationEvidenceCatalog.SINGULARITY_TOKEN_UNIVERSAL_UNDERSCORE)
                || contains(value, ExportValidationEvidenceCatalog.SINGULARITY_TOKEN_AVARITIA)
                || contains(value, ExportValidationEvidenceCatalog.SINGULARITY_TOKEN_COSMIC_NEUTRONIUM)
                || contains(value, ExportValidationEvidenceCatalog.SINGULARITY_TOKEN_TRANSCENDENT_METAL)
                || contains(value, ExportValidationEvidenceCatalog.SINGULARITY_TOKEN_UNIVERSIUM);
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
        if (!relativePath.startsWith(ExportValidationEvidenceCatalog.IMAGE_DIRECTORY_PREFIX)) {
            File underImage = new File(repositoryDirectory, (ExportValidationEvidenceCatalog.IMAGE_DIRECTORY_PREFIX + relativePath).replace('/', File.separatorChar));
            return underImage.exists() && underImage.length() > 0L;
        }
        return false;
    }
}
