package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Catalog of validation report files owned by the export validation store. */
final class ExportValidationReportFileCatalog {
    private static final List<ReportFileDescriptor> REPORT_FILES = validateAndFreeze(Arrays.asList(
            new ReportFileDescriptor(
                    "validationReport",
                    RawExportFileCatalog.EXPORT_VALIDATION_REPORT_FILE_NAME),
            new ReportFileDescriptor(
                    "healthReport",
                    RawExportFileCatalog.EXPORT_HEALTH_REPORT_FILE_NAME)));

    private ExportValidationReportFileCatalog() {}

    static File reportFile(File validationDirectory) throws IOException {
        return descriptor("validationReport").file(validationDirectory);
    }

    static File healthReportFile(File validationDirectory) throws IOException {
        return descriptor("healthReport").file(validationDirectory);
    }

    static List<ReportFile> outputFiles(File validationDirectory) throws IOException {
        List<ReportFile> files = new ArrayList<ReportFile>();
        for (ReportFileDescriptor descriptor : REPORT_FILES) {
            files.add(new ReportFile(descriptor.key, descriptor.file(validationDirectory)));
        }
        return Collections.unmodifiableList(files);
    }

    static void ensureDirectory(File directory) throws IOException {
        requireDirectoryRoot(directory);
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Failed to create validation report directory: " + directory.getAbsolutePath());
        }
    }

    static void requireDirectoryRoot(File directory) throws IOException {
        if (directory == null) {
            throw new IOException("Validation report directory must not be null");
        }
        if (directory.exists() && !directory.isDirectory()) {
            throw new IOException(
                    "Validation report path exists but is not a directory: " + directory.getAbsolutePath());
        }
    }

    private static ReportFileDescriptor descriptor(String key) {
        for (ReportFileDescriptor descriptor : REPORT_FILES) {
            if (descriptor.key.equals(key)) {
                return descriptor;
            }
        }
        throw new IllegalStateException("Missing validation report file descriptor: " + key);
    }

    private static List<ReportFileDescriptor> validateAndFreeze(List<ReportFileDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Validation report file catalog must not be empty");
        }
        Set<String> keys = new LinkedHashSet<String>();
        Set<String> fileNames = new LinkedHashSet<String>();
        List<ReportFileDescriptor> validated = new ArrayList<ReportFileDescriptor>();
        for (ReportFileDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Validation report file descriptor must not be null");
            }
            requireNonEmpty("Validation report file key", descriptor.key);
            requireJsonFileName(descriptor.fileName, descriptor.key);
            if (!keys.add(descriptor.key)) {
                throw new IllegalStateException("Duplicate validation report file key: " + descriptor.key);
            }
            if (!fileNames.add(descriptor.fileName)) {
                throw new IllegalStateException(
                        "Duplicate validation report file name: " + descriptor.fileName);
            }
            validated.add(descriptor);
        }
        return Collections.unmodifiableList(validated);
    }

    private static void requireJsonFileName(String value, String key) {
        if (value == null
                || value.trim().isEmpty()
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0
                || value.contains("..")
                || !value.endsWith(".json")) {
            throw new IllegalStateException(
                    "Validation report file name must be a single JSON file: " + key);
        }
    }

    private static void requireNonEmpty(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty");
        }
    }

    private static final class ReportFileDescriptor {
        private final String key;
        private final String fileName;

        private ReportFileDescriptor(String key, String fileName) {
            this.key = key;
            this.fileName = fileName;
        }

        private File file(File validationDirectory) throws IOException {
            requireDirectoryRoot(validationDirectory);
            return new File(validationDirectory, fileName);
        }
    }

    static final class ReportFile {
        final String key;
        final File file;

        private ReportFile(String key, File file) {
            this.key = key;
            this.file = file;
        }
    }
}
