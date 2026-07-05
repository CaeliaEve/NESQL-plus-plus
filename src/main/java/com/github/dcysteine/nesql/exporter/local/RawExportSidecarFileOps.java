package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

final class RawExportSidecarFileOps {
    private static final int COPY_BUFFER_BYTES = 1024 * 1024;

    private RawExportSidecarFileOps() {}

    static void ensureDirectory(File directory) throws IOException {
        if (directory == null) {
            throw new IOException("Raw-export directory must not be null");
        }
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException("Raw-export path exists but is not a directory: " + directory.getAbsolutePath());
            }
            return;
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Failed to create raw-export directory: " + directory.getAbsolutePath());
        }
    }

    static void ensureOutputFile(File out) throws IOException {
        if (out == null) {
            throw new IOException("Raw-export output file must not be null");
        }
        File parent = out.getParentFile();
        if (parent == null) {
            throw new IOException("Raw-export output parent directory must not be null: " + out.getAbsolutePath());
        }
        ensureDirectory(parent);
        if (out.exists() && !out.isFile()) {
            throw new IOException("Raw-export output path exists but is not a file: " + out.getAbsolutePath());
        }
    }

    static OutputStreamWriter createUtf8JsonWriter(File out) throws IOException {
        return new OutputStreamWriter(openOutput(out), StandardCharsets.UTF_8);
    }

    static OutputStreamWriter createUtf8JsonlWriter(File out) throws IOException {
        return new OutputStreamWriter(openOutput(out), StandardCharsets.UTF_8);
    }

    static void writeJson(Gson gson, File out, Object value) throws IOException {
        if (gson == null) {
            throw new IOException("Raw-export JSON writer Gson must not be null");
        }
        try (OutputStreamWriter writer = createUtf8JsonWriter(out)) {
            gson.toJson(value, writer);
        }
    }

    static void copyRequired(File source, File target, String label) throws IOException {
        if (source == null) {
            throw new IOException("Missing required raw-export file source for " + label);
        }
        if (!source.exists() || !source.isFile()) {
            throw new IOException(
                    "Missing required raw-export file for "
                            + label
                            + ": "
                            + source.getAbsolutePath());
        }
        copyExisting(source, target, label);
    }

    static boolean copyOptional(File source, File target) throws IOException {
        if (source == null || !source.exists() || !source.isFile()) {
            return false;
        }
        copyExisting(source, target, "optional raw-export asset");
        return true;
    }

    static void purgeLegacyRawExportOutputs(File rawDir) throws IOException {
        for (String relativePath : RawExportFileCatalog.prohibitedRootOutputs()) {
            deleteIfExists(RawExportFileCatalog.rawExportFile(rawDir, relativePath));
        }
    }

    private static OutputStream openOutput(File out) throws IOException {
        ensureOutputFile(out);
        FileOutputStream fos = new FileOutputStream(out, false);
        try {
            if (out.getName().endsWith(".gz")) {
                return new RawExportFastGzipOutputStream(fos);
            }
            return fos;
        } catch (IOException e) {
            closeAfterFailedWrap(fos, e);
            throw e;
        } catch (RuntimeException e) {
            closeAfterFailedWrap(fos, e);
            throw e;
        }
    }

    private static void copyExisting(File source, File target, String label) throws IOException {
        if (target == null) {
            throw new IOException("Missing raw-export copy target for " + label);
        }
        ensureOutputFile(target);
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target, false)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    private static void closeAfterFailedWrap(FileOutputStream fos, Exception failure) {
        try {
            fos.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteIfExists(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Failed to delete legacy raw-export output: " + file.getAbsolutePath());
        }
    }
}
