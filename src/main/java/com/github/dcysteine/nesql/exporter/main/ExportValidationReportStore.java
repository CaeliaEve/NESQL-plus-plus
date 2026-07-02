package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.ExportValidationReportWriter.DeltaSnapshot;
import com.github.dcysteine.nesql.exporter.main.ExportValidationReportWriter.PreviousSnapshot;
import com.github.dcysteine.nesql.exporter.main.ExportValidationReportWriter.ValidationReport;
import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Owns validation report persistence, aliases, previous-run snapshot, and delta metadata. */
final class ExportValidationReportStore {
    private static final Gson READ_GSON = new GsonBuilder().create();
    private static final Gson WRITE_GSON = new GsonBuilder().setPrettyPrinting().create();

    private ExportValidationReportStore() {}

    static void applyPreviousDelta(File validationDirectory, ValidationReport report) {
        ValidationReport previousReport = readPreviousReport(reportFile(validationDirectory));
        if (previousReport == null) {
            return;
        }
        report.previous = previousSnapshot(previousReport);
        report.delta = deltaSnapshot(report, previousReport);
    }

    static ReportFiles write(File validationDirectory, ValidationReport report) throws Exception {
        ensureDirectory(validationDirectory);
        ReportFiles files = new ReportFiles(reportFile(validationDirectory), healthReportFile(validationDirectory));
        writeJson(files.reportFile, report);
        writeJson(files.healthReportFile, report);
        return files;
    }

    private static ValidationReport readPreviousReport(File reportFile) {
        if (reportFile == null || !reportFile.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(reportFile);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            return READ_GSON.fromJson(reader, ValidationReport.class);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read previous NESQL++ validation report", e);
            return null;
        }
    }

    private static PreviousSnapshot previousSnapshot(ValidationReport previousReport) {
        PreviousSnapshot previous = new PreviousSnapshot();
        previous.itemsJsonGzFiles = previousReport.itemsJsonGzFiles;
        previous.recipeJsonGzFiles = previousReport.recipeJsonGzFiles;
        previous.imagePngFiles = previousReport.imagePngFiles;
        previous.imageGifFiles = previousReport.imageGifFiles;
        previous.staticAtlasManifestAssets = previousReport.staticAtlasManifestAssets;
        previous.animatedAtlasManifestAssets = previousReport.animatedAtlasManifestAssets;
        return previous;
    }

    private static DeltaSnapshot deltaSnapshot(ValidationReport report, ValidationReport previousReport) {
        DeltaSnapshot delta = new DeltaSnapshot();
        delta.itemsJsonGzFiles = report.itemsJsonGzFiles - previousReport.itemsJsonGzFiles;
        delta.recipeJsonGzFiles = report.recipeJsonGzFiles - previousReport.recipeJsonGzFiles;
        delta.imagePngFiles = report.imagePngFiles - previousReport.imagePngFiles;
        delta.imageGifFiles = report.imageGifFiles - previousReport.imageGifFiles;
        delta.staticAtlasManifestAssets =
                report.staticAtlasManifestAssets - previousReport.staticAtlasManifestAssets;
        delta.animatedAtlasManifestAssets =
                report.animatedAtlasManifestAssets - previousReport.animatedAtlasManifestAssets;
        return delta;
    }

    private static File reportFile(File validationDirectory) {
        return new File(validationDirectory, RawExportFileCatalog.EXPORT_VALIDATION_REPORT_FILE_NAME);
    }

    private static File healthReportFile(File validationDirectory) {
        return new File(validationDirectory, RawExportFileCatalog.EXPORT_HEALTH_REPORT_FILE_NAME);
    }

    private static void ensureDirectory(File directory) throws Exception {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new java.io.IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static void writeJson(File file, Object value) throws Exception {
        ensureDirectory(file.getParentFile());
        try (FileOutputStream fos = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            WRITE_GSON.toJson(value, writer);
        }
    }

    static final class ReportFiles {
        final File reportFile;
        final File healthReportFile;

        private ReportFiles(File reportFile, File healthReportFile) {
            this.reportFile = reportFile;
            this.healthReportFile = healthReportFile;
        }
    }
}
