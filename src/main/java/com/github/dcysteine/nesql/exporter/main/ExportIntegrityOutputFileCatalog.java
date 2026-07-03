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

/** Catalog of export integrity manifest/checksum output aliases. */
final class ExportIntegrityOutputFileCatalog {
    private static final List<OutputDescriptor> OUTPUTS = validateAndFreeze(Arrays.asList(
            new OutputDescriptor(
                    "manifest",
                    RawExportFileCatalog.EXPORT_MANIFEST_FILE_NAME,
                    OutputPayload.EXPORT_MANIFEST),
            new OutputDescriptor(
                    "manifestDash",
                    RawExportFileCatalog.EXPORT_MANIFEST_DASH_FILE_NAME,
                    OutputPayload.EXPORT_MANIFEST),
            new OutputDescriptor(
                    "checksums",
                    RawExportFileCatalog.STAGE_CHECKSUMS_FILE_NAME,
                    OutputPayload.STAGE_CHECKSUMS),
            new OutputDescriptor(
                    "checksumsDash",
                    RawExportFileCatalog.STAGE_CHECKSUMS_DASH_FILE_NAME,
                    OutputPayload.STAGE_CHECKSUMS)));

    private ExportIntegrityOutputFileCatalog() {}

    static File checksumFile(File validationDirectory) throws IOException {
        return descriptor("checksums").file(validationDirectory);
    }

    static File manifestFile(File validationDirectory) throws IOException {
        return descriptor("manifest").file(validationDirectory);
    }

    static List<OutputFile> outputFiles(
            File validationDirectory,
            ExportIntegrityManifestWriter.ExportManifest manifest,
            ExportIntegrityManifestWriter.ChecksumReport checksumReport) throws IOException {
        List<OutputFile> files = new ArrayList<OutputFile>();
        for (OutputDescriptor descriptor : OUTPUTS) {
            files.add(new OutputFile(
                    descriptor.key,
                    descriptor.file(validationDirectory),
                    descriptor.payload.value(manifest, checksumReport)));
        }
        return Collections.unmodifiableList(files);
    }

    static void ensureDirectory(File directory) throws IOException {
        requireDirectoryRoot(directory);
        if (!directory.exists() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Failed to create export integrity output directory: " + directory.getAbsolutePath());
        }
    }

    static void requireDirectoryRoot(File directory) throws IOException {
        if (directory == null) {
            throw new IOException("Export integrity output directory must not be null");
        }
        if (directory.exists() && !directory.isDirectory()) {
            throw new IOException(
                    "Export integrity output path exists but is not a directory: " + directory.getAbsolutePath());
        }
    }

    private static OutputDescriptor descriptor(String key) {
        for (OutputDescriptor descriptor : OUTPUTS) {
            if (descriptor.key.equals(key)) {
                return descriptor;
            }
        }
        throw new IllegalStateException("Missing export integrity output descriptor: " + key);
    }

    private static List<OutputDescriptor> validateAndFreeze(List<OutputDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Export integrity output file catalog must not be empty");
        }
        Set<String> keys = new LinkedHashSet<String>();
        Set<String> fileNames = new LinkedHashSet<String>();
        List<OutputDescriptor> validated = new ArrayList<OutputDescriptor>();
        for (OutputDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Export integrity output descriptor must not be null");
            }
            requireNonEmpty("Export integrity output key", descriptor.key);
            requireJsonFileName(descriptor.fileName, descriptor.key);
            if (descriptor.payload == null) {
                throw new IllegalStateException(
                        "Export integrity output payload selector must not be null: " + descriptor.key);
            }
            if (!keys.add(descriptor.key)) {
                throw new IllegalStateException("Duplicate export integrity output key: " + descriptor.key);
            }
            if (!fileNames.add(descriptor.fileName)) {
                throw new IllegalStateException(
                        "Duplicate export integrity output file name: " + descriptor.fileName);
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
                    "Export integrity output file name must be a single JSON file: " + key);
        }
    }

    private static void requireNonEmpty(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty");
        }
    }

    private enum OutputPayload {
        EXPORT_MANIFEST {
            @Override
            Object value(
                    ExportIntegrityManifestWriter.ExportManifest manifest,
                    ExportIntegrityManifestWriter.ChecksumReport checksumReport) {
                return manifest;
            }
        },
        STAGE_CHECKSUMS {
            @Override
            Object value(
                    ExportIntegrityManifestWriter.ExportManifest manifest,
                    ExportIntegrityManifestWriter.ChecksumReport checksumReport) {
                return checksumReport;
            }
        };

        abstract Object value(
                ExportIntegrityManifestWriter.ExportManifest manifest,
                ExportIntegrityManifestWriter.ChecksumReport checksumReport);
    }

    private static final class OutputDescriptor {
        private final String key;
        private final String fileName;
        private final OutputPayload payload;

        private OutputDescriptor(String key, String fileName, OutputPayload payload) {
            this.key = key;
            this.fileName = fileName;
            this.payload = payload;
        }

        private File file(File validationDirectory) throws IOException {
            requireDirectoryRoot(validationDirectory);
            return new File(validationDirectory, fileName);
        }
    }

    static final class OutputFile {
        final String key;
        final File file;
        final Object payload;

        private OutputFile(String key, File file, Object payload) {
            this.key = key;
            this.file = file;
            this.payload = payload;
        }
    }
}
