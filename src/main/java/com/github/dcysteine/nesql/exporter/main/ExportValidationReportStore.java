package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.ExportValidationReport.DeltaSnapshot;
import com.github.dcysteine.nesql.exporter.main.ExportValidationReport.PreviousSnapshot;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Owns validation report persistence, previous-run snapshot, and delta metadata. */
final class ExportValidationReportStore {
    private static final Gson READ_GSON = new GsonBuilder().create();
    private static final Gson WRITE_GSON = new GsonBuilder().setPrettyPrinting().create();

    private ExportValidationReportStore() {}

    static void applyPreviousDelta(File validationDirectory, ExportValidationReport report) throws Exception {
        ExportValidationReport previousReport = readPreviousReport(
                ExportValidationReportFileCatalog.reportFile(validationDirectory));
        if (previousReport == null) {
            return;
        }
        report.previous = previousSnapshot(previousReport);
        report.delta = deltaSnapshot(report, previousReport);
    }

    static ReportFiles write(File validationDirectory, ExportValidationReport report) throws Exception {
        ExportValidationReportFileCatalog.ensureDirectory(validationDirectory);
        for (ExportValidationReportFileCatalog.ReportFile reportFile
                : ExportValidationReportFileCatalog.outputFiles(validationDirectory)) {
            writeJson(reportFile.file, report);
        }
        ReportFiles files = new ReportFiles(
                ExportValidationReportFileCatalog.reportFile(validationDirectory),
                ExportValidationReportFileCatalog.healthReportFile(validationDirectory));
        return files;
    }

    private static ExportValidationReport readPreviousReport(File reportFile) throws Exception {
        if (reportFile == null) {
            throw new IOException("Validation report file must not be null");
        }
        if (!reportFile.exists()) {
            return null;
        }
        if (!reportFile.isFile()) {
            throw new IOException(
                    "Validation report path exists but is not a file: " + reportFile.getAbsolutePath());
        }
        try (FileInputStream fis = new FileInputStream(reportFile);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            ExportValidationReport report = READ_GSON.fromJson(reader, ExportValidationReport.class);
            if (report == null) {
                throw new IOException(
                        "Validation report file is empty or null JSON: " + reportFile.getAbsolutePath());
            }
            return report;
        }
    }

    private static PreviousSnapshot previousSnapshot(ExportValidationReport previousReport) {
        PreviousSnapshot previous = new PreviousSnapshot();
        previous.itemsJsonGzFiles = previousReport.itemsJsonGzFiles;
        previous.recipeJsonGzFiles = previousReport.recipeJsonGzFiles;
        previous.imagePngFiles = previousReport.imagePngFiles;
        previous.imageGifFiles = previousReport.imageGifFiles;
        previous.staticAtlasManifestAssets = previousReport.staticAtlasManifestAssets;
        previous.animatedAtlasManifestAssets = previousReport.animatedAtlasManifestAssets;
        return previous;
    }

    private static DeltaSnapshot deltaSnapshot(ExportValidationReport report, ExportValidationReport previousReport) {
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

    private static void writeJson(File file, Object value) throws Exception {
        ExportValidationReportFileCatalog.ensureDirectory(file.getParentFile());
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
