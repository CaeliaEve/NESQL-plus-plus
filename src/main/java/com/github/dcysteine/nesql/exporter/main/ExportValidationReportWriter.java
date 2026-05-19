package com.github.dcysteine.nesql.exporter.main;

import com.google.gson.GsonBuilder;
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
            collectWarnings(report);

            File reportFile = new File(canonicalDir, "export-validation-report.json");
            try (FileOutputStream fos = new FileOutputStream(reportFile);
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
        List<String> warnings = new ArrayList<String>();
    }
}
