package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Owns machine-path hygiene scanning and JSONL diagnostics emission. */
final class ExportValidationPathHygieneProbe {
    private ExportValidationPathHygieneProbe() {}

    static void inspect(File repositoryDirectory, ExportValidationReportWriter.ValidationReport report) {
        List<File> files = new ArrayList<File>();
        File rawDir = RawExportFileCatalog.rawExportDirectory(repositoryDirectory);
        collectRuntimeJsonFiles(new File(repositoryDirectory, RawExportFileCatalog.MANIFEST_FILE), files);
        collectRuntimeJsonFiles(rawDir, files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "facts"), files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "assets"), files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "special"), files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "models"), files);
        collectRuntimeJsonFiles(RawExportFileCatalog.rawExportFile(rawDir, RawExportFileCatalog.MANIFEST_FILE), files);
        report.exportPathHygieneAuditedFiles = files.size();
        for (File file : files) {
            inspectFile(repositoryDirectory, file, report);
        }
        report.exportPathHygieneStatus = report.exportPathHygieneViolations == 0
                ? ExportValidationAbiCatalog.STATUS_OK
                : ExportValidationAbiCatalog.STATUS_FAILED;
        if (report.exportPathHygieneViolations > 0) {
            writeErrors(repositoryDirectory, report);
        }
    }

    private static void collectRuntimeJsonFiles(File file, List<File> files) {
        if (file == null || !file.exists() || isDiagnosticPath(file)) {
            return;
        }
        if (file.isFile()) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".json") || name.endsWith(".jsonl")) {
                files.add(file);
            }
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            collectRuntimeJsonFiles(child, files);
        }
    }

    private static boolean isDiagnosticPath(File file) {
        String normalized = file.getPath().replace(File.separatorChar, '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/validation/")
                || normalized.contains("/diagnostic")
                || normalized.contains("/logs/")
                || normalized.endsWith("report.json")
                || normalized.endsWith("report.jsonl")
                || normalized.endsWith("log.json")
                || normalized.endsWith("log.jsonl");
    }

    private static void inspectFile(
            File repositoryDirectory,
            File file,
            ExportValidationReportWriter.ValidationReport report) {
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader input = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(input)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                for (ExportValidationAbiCatalog.PathHygieneRule rule :
                        ExportValidationAbiCatalog.pathHygieneRules()) {
                    if (rule.pattern.matcher(line).find()) {
                        report.exportPathHygieneViolations++;
                        addSample(
                                report,
                                relativize(repositoryDirectory, file),
                                lineNumber,
                                rule.name,
                                line);
                    }
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect export path hygiene for {}", file.getAbsolutePath(), e);
        }
    }

    private static void addSample(
            ExportValidationReportWriter.ValidationReport report,
            String file,
            int line,
            String rule,
            String text) {
        if (report.exportPathHygieneSamples.size() >= ExportValidationAbiCatalog.PATH_HYGIENE_SAMPLE_LIMIT) {
            return;
        }
        ExportValidationReportWriter.PathHygieneSample sample = new ExportValidationReportWriter.PathHygieneSample();
        sample.file = file;
        sample.line = line;
        sample.rule = rule;
        sample.text = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (sample.text.length() > 240) {
            sample.text = sample.text.substring(0, 240);
        }
        report.exportPathHygieneSamples.add(sample);
    }

    private static void writeErrors(File repositoryDirectory, ExportValidationReportWriter.ValidationReport report) {
        File rawDir = RawExportFileCatalog.rawExportDirectory(repositoryDirectory);
        File validationDirectory = RawExportFileCatalog.validationDirectory(rawDir);
        if (!validationDirectory.exists() && !validationDirectory.mkdirs()) {
            Logger.MOD.warn("Failed to create NESQL validation directory: {}", validationDirectory.getAbsolutePath());
            return;
        }
        File errorsFile = RawExportFileCatalog.rawExportFile(rawDir, RawExportFileCatalog.VALIDATION_ERRORS_FILE);
        try (FileOutputStream fos = new FileOutputStream(errorsFile, true);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            for (ExportValidationReportWriter.PathHygieneSample sample : report.exportPathHygieneSamples) {
                JsonObject entry = new JsonObject();
                entry.addProperty(ExportValidationEvidenceCatalog.ERROR_FIELD_SCHEMA_VERSION, ExportValidationAbiCatalog.EXPORT_ERROR_SCHEMA);
                entry.addProperty(
                        ExportValidationEvidenceCatalog.ERROR_FIELD_GENERATED_AT,
                        new SimpleDateFormat(ExportValidationAbiCatalog.GENERATED_AT_TIMESTAMP_PATTERN)
                                .format(new Date()));
                entry.addProperty(ExportValidationEvidenceCatalog.ERROR_FIELD_STAGE, ExportValidationAbiCatalog.EXPORT_ERROR_STAGE_VALIDATION);
                entry.addProperty(ExportValidationEvidenceCatalog.ERROR_FIELD_CODE, ExportValidationAbiCatalog.PATH_HYGIENE_ERROR_CODE);
                entry.addProperty(ExportValidationEvidenceCatalog.ERROR_FIELD_MESSAGE, ExportValidationAbiCatalog.PATH_HYGIENE_ERROR_MESSAGE);
                entry.addProperty(ExportValidationEvidenceCatalog.ERROR_FIELD_FILE, sample.file);
                entry.addProperty(ExportValidationEvidenceCatalog.ERROR_FIELD_LINE, sample.line);
                entry.addProperty(ExportValidationEvidenceCatalog.ERROR_FIELD_RULE, sample.rule);
                writer.write(gson.toJson(entry));
                writer.write('\n');
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write export path hygiene diagnostics", e);
        }
    }

    private static String relativize(File root, File file) {
        try {
            return root.toPath().toAbsolutePath().normalize()
                    .relativize(file.toPath().toAbsolutePath().normalize())
                    .toString()
                    .replace(File.separatorChar, '/');
        } catch (Exception ignored) {
            return file.getName();
        }
    }
}
