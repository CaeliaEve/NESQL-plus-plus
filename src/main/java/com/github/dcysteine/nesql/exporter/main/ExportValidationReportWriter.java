package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Writes a lightweight post-export integrity summary without changing exported data contracts. */
final class ExportValidationReportWriter {
    private ExportValidationReportWriter() {}

    static void write(ExportContext exportContext) {
        try {
            File repositoryDirectory = exportContext.paths.repositoryDirectory;
            File canonicalDir = new File(repositoryDirectory, "canonical");
            if (!canonicalDir.exists()) {
                canonicalDir.mkdirs();
            }

            ValidationReport report = new ValidationReport();
            report.schemaVersion = "nesqlpp/export-validation/v1";
            report.profile = exportContext.profile.profileId;
            report.selection = exportContext.selection.describe();
            report.repository = exportContext.paths.repositoryName;
            report.itemsJsonGzFiles = countFiles(new File(repositoryDirectory, "items"), ".json.gz");
            report.recipeJsonGzFiles = countFiles(new File(repositoryDirectory, "recipes"), ".json.gz");
            report.imagePngFiles = countFiles(exportContext.paths.imageDirectory, ".png");
            report.imageGifFiles = countFiles(exportContext.paths.imageDirectory, ".gif");
            report.renderJsonFiles = countFiles(exportContext.paths.imageDirectory, ".render.json");
            report.spriteJsonFiles = countFiles(exportContext.paths.imageDirectory, ".sprite.json");
            report.staticAtlasPngFiles = countFiles(new File(canonicalDir, "atlases"), ".png");
            report.animatedAtlasPngFiles = countFiles(new File(canonicalDir, "animated-atlases"), ".png");
            report.staticAtlasManifestAssets =
                    readManifestCount(new File(canonicalDir, "atlas-manifest.json"), "assetCount");
            report.animatedAtlasManifestAssets =
                    readManifestCount(new File(canonicalDir, "animated-atlas-manifest.json"), "assetCount");
            report.totalAtlasManifestAssets = report.staticAtlasManifestAssets + report.animatedAtlasManifestAssets;
            report.browserLayoutEntries =
                    readArrayCount(new File(canonicalDir, "browser-layout-index.json"), "entries", "items", "groups");
            report.multiblockBlueprints =
                    readArrayCount(new File(canonicalDir, "multiblock-blueprints.json"), "blueprints", "entries");
            report.entityPreviewEntries =
                    readArrayCount(new File(canonicalDir, "entity-previews.json"), "entries");
            report.entityModelEntries =
                    readArrayCount(new File(canonicalDir, "entity-models.json"), "entries");
            inspectRenderAssets(repositoryDirectory, canonicalDir, report);
            report.atlasManifestCoverageRatio = ratio(report.totalAtlasManifestAssets, report.renderAssetManifestAssets);
            File reportFile = new File(canonicalDir, "export-validation-report.json");
            File healthReportFile = new File(canonicalDir, "export-health-report.json");
            ValidationReport previousReport = readPreviousReport(reportFile);
            if (previousReport != null) {
                report.previous = new PreviousSnapshot();
                report.previous.itemsJsonGzFiles = previousReport.itemsJsonGzFiles;
                report.previous.recipeJsonGzFiles = previousReport.recipeJsonGzFiles;
                report.previous.imagePngFiles = previousReport.imagePngFiles;
                report.previous.imageGifFiles = previousReport.imageGifFiles;
                report.previous.staticAtlasManifestAssets = previousReport.staticAtlasManifestAssets;
                report.previous.animatedAtlasManifestAssets = previousReport.animatedAtlasManifestAssets;
                report.delta = new DeltaSnapshot();
                report.delta.itemsJsonGzFiles = report.itemsJsonGzFiles - previousReport.itemsJsonGzFiles;
                report.delta.recipeJsonGzFiles = report.recipeJsonGzFiles - previousReport.recipeJsonGzFiles;
                report.delta.imagePngFiles = report.imagePngFiles - previousReport.imagePngFiles;
                report.delta.imageGifFiles = report.imageGifFiles - previousReport.imageGifFiles;
                report.delta.staticAtlasManifestAssets =
                        report.staticAtlasManifestAssets - previousReport.staticAtlasManifestAssets;
                report.delta.animatedAtlasManifestAssets =
                        report.animatedAtlasManifestAssets - previousReport.animatedAtlasManifestAssets;
            }
            collectWarnings(report);

            try (FileOutputStream fos = new FileOutputStream(reportFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
            }
            try (FileOutputStream fos = new FileOutputStream(healthReportFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
            }

            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Export validation report written: "
                            + reportFile.getAbsolutePath());
            if (!report.warnings.isEmpty()) {
                Logger.chatMessage(
                        EnumChatFormatting.YELLOW
                                + "[NESQL] Validation warnings: "
                                + report.warnings.size()
                                + " (see report)");
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ validation report", e);
        }
    }

    private static ValidationReport readPreviousReport(File reportFile) {
        if (!reportFile.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(reportFile);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            return new GsonBuilder().create().fromJson(reader, ValidationReport.class);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read previous NESQL++ validation report", e);
            return null;
        }
    }

    private static void collectWarnings(ValidationReport report) {
        if (report.itemsJsonGzFiles == 0) {
            report.warnings.add("No item json.gz shards found under items/.");
        }
        if (report.recipeJsonGzFiles == 0) {
            report.warnings.add("No recipe json.gz shards found under recipes/.");
        }
        if (report.imagePngFiles == 0 && report.imageGifFiles == 0) {
            report.warnings.add("No rendered image files found under image/.");
        }
        if (report.staticAtlasPngFiles == 0 && report.staticAtlasManifestAssets > 0) {
            report.warnings.add("Static atlas manifest has assets but no atlas PNG files were found.");
        }
        if (report.animatedAtlasPngFiles == 0 && report.animatedAtlasManifestAssets > 0) {
            report.warnings.add("Animated atlas manifest has assets but no animated atlas PNG files were found.");
        }
        if (report.renderAssetMissingPrimaryArtifacts > 0) {
            report.warnings.add("Render assets with missing primary/static artifacts: "
                    + report.renderAssetMissingPrimaryArtifacts);
        }
        if (report.renderAssetMissingTimelineFrames > 0) {
            report.warnings.add("Render assets with missing timeline frame files: "
                    + report.renderAssetMissingTimelineFrames);
        }
        if (report.suspiciousStaticSingularityAssets > 0) {
            report.warnings.add("Singularity-like render assets exported without animation: "
                    + report.suspiciousStaticSingularityAssets);
        }
        if (report.renderAssetManifestAssets > 0 && report.totalAtlasManifestAssets == 0) {
            report.warnings.add("Render assets exist but no atlas manifest assets were found.");
        }
    }

    private static void inspectRenderAssets(File repositoryDirectory, File canonicalDir, ValidationReport report) {
        File manifestFile = new File(canonicalDir, "render-assets.json");
        if (!manifestFile.exists()) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(manifestFile);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            RenderAssetManifest manifest = new Gson().fromJson(reader, RenderAssetManifest.class);
            if (manifest == null || manifest.assets == null) {
                return;
            }
            report.renderAssetManifestAssets = manifest.assets.size();
            for (CanonicalRenderAsset asset : manifest.assets) {
                inspectRenderAsset(repositoryDirectory, asset, report);
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect render asset manifest for validation", e);
        }
    }

    private static void inspectRenderAsset(
            File repositoryDirectory,
            CanonicalRenderAsset asset,
            ValidationReport report) {
        if (asset == null) {
            return;
        }
        String primaryPath = firstNonEmpty(asset.primaryArtifact, asset.staticFile, asset.nativeSpriteAtlasFile);
        if (primaryPath == null || !exportFileExists(repositoryDirectory, primaryPath)) {
            report.renderAssetMissingPrimaryArtifacts++;
            addSample(report.renderAssetMissingPrimaryArtifactSamples, firstNonEmpty(asset.assetId, asset.sourcePath, primaryPath));
        }

        inspectSingularityAnimation(asset, report);

        if (asset.timeline == null || asset.timeline.isEmpty()) {
            return;
        }
        for (Map<String, Object> frame : asset.timeline) {
            if (frame == null) {
                continue;
            }
            Object path = frame.get("path");
            if (path instanceof String && !exportFileExists(repositoryDirectory, (String) path)) {
                report.renderAssetMissingTimelineFrames++;
                addSample(report.renderAssetMissingTimelineFrameSamples, firstNonEmpty(asset.assetId, asset.sourcePath, (String) path));
            }
        }
    }

    private static void inspectSingularityAnimation(CanonicalRenderAsset asset, ValidationReport report) {
        String haystack = joinLower(
                asset.assetId,
                asset.variantKey,
                asset.family,
                asset.sourceType,
                asset.sourcePath,
                asset.primaryArtifact,
                asset.staticFile,
                asset.rendererFamily,
                asset.captureMethod,
                asset.captureSource,
                asset.animationMode,
                asset.renderMode);
        if (!isSingularityLike(haystack)) {
            return;
        }

        report.singularityLikeRenderAssets++;
        boolean animated =
                contains(haystack, ".gif")
                        || contains(haystack, "animated")
                        || contains(haystack, "timeline")
                        || (asset.timeline != null && asset.timeline.size() > 1)
                        || (asset.frames != null && asset.frames.size() > 1)
                        || (asset.frameCount != null && asset.frameCount > 1)
                        || (asset.capturedFrameCount != null && asset.capturedFrameCount > 1);
        if (animated) {
            report.animatedSingularityLikeRenderAssets++;
            return;
        }

        report.suspiciousStaticSingularityAssets++;
        if (report.suspiciousStaticSingularitySamples.size() < 100) {
            report.suspiciousStaticSingularitySamples.add(firstNonEmpty(asset.assetId, asset.sourcePath, asset.primaryArtifact));
        }
    }

    private static void addSample(List<String> samples, String value) {
        if (value != null && !value.isEmpty() && samples.size() < 100 && !samples.contains(value)) {
            samples.add(value);
        }
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

    private static String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        if (third != null && !third.isEmpty()) {
            return third;
        }
        return null;
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

    private static int readManifestCount(File file, String memberName) {
        if (!file.exists()) {
            return 0;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonObject object = new JsonParser().parse(reader).getAsJsonObject();
            if (object.has(memberName) && object.get(memberName).isJsonPrimitive()) {
                return object.get(memberName).getAsInt();
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read manifest count from {}", file.getAbsolutePath(), e);
        }
        return 0;
    }

    private static int readArrayCount(File file, String... memberNames) {
        if (!file.exists()) {
            return 0;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonObject object = new JsonParser().parse(reader).getAsJsonObject();
            int total = 0;
            for (String memberName : memberNames) {
                if (!object.has(memberName)) {
                    continue;
                }
                JsonElement element = object.get(memberName);
                if (element.isJsonArray()) {
                    total += element.getAsJsonArray().size();
                } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    total += element.getAsInt();
                }
            }
            return total;
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read array count from {}", file.getAbsolutePath(), e);
        }
        return 0;
    }

    private static Double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }

    private static int countFiles(File root, String suffix) {
        if (root == null || !root.exists()) {
            return 0;
        }
        Counter counter = new Counter();
        countFiles(root, suffix, counter);
        return counter.value;
    }

    private static void countFiles(File file, String suffix, Counter counter) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            if (file.getName().endsWith(suffix)) {
                counter.value++;
            }
            return;
        }

        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            countFiles(child, suffix, counter);
        }
    }

    private static final class Counter {
        int value;
    }

    private static final class ValidationReport {
        String schemaVersion;
        String repository;
        String profile;
        String selection;
        int itemsJsonGzFiles;
        int recipeJsonGzFiles;
        int imagePngFiles;
        int imageGifFiles;
        int renderJsonFiles;
        int spriteJsonFiles;
        int staticAtlasPngFiles;
        int animatedAtlasPngFiles;
        int staticAtlasManifestAssets;
        int animatedAtlasManifestAssets;
        int totalAtlasManifestAssets;
        Double atlasManifestCoverageRatio;
        int renderAssetManifestAssets;
        int renderAssetMissingPrimaryArtifacts;
        List<String> renderAssetMissingPrimaryArtifactSamples = new ArrayList<String>();
        int renderAssetMissingTimelineFrames;
        List<String> renderAssetMissingTimelineFrameSamples = new ArrayList<String>();
        int browserLayoutEntries;
        int multiblockBlueprints;
        int entityPreviewEntries;
        int entityModelEntries;
        int singularityLikeRenderAssets;
        int animatedSingularityLikeRenderAssets;
        int suspiciousStaticSingularityAssets;
        List<String> suspiciousStaticSingularitySamples = new ArrayList<String>();
        PreviousSnapshot previous;
        DeltaSnapshot delta;
        List<String> warnings = new ArrayList<String>();
    }

    private static final class RenderAssetManifest {
        List<CanonicalRenderAsset> assets;
    }

    private static class PreviousSnapshot {
        int itemsJsonGzFiles;
        int recipeJsonGzFiles;
        int imagePngFiles;
        int imageGifFiles;
        int staticAtlasManifestAssets;
        int animatedAtlasManifestAssets;
    }

    private static final class DeltaSnapshot extends PreviousSnapshot {}
}
