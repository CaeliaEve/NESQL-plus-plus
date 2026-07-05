package com.github.dcysteine.nesql.exporter.local;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/** Owns fail-closed file creation for Angelica render fact artifacts. */
final class AngelicaRenderFactFileOps {
    private AngelicaRenderFactFileOps() {
    }

    static OutputStreamWriter createUtf8JsonWriter(File out) throws IOException {
        return new OutputStreamWriter(openOutput(out), StandardCharsets.UTF_8);
    }

    static OutputStreamWriter createUtf8JsonlWriter(File out) throws IOException {
        return new OutputStreamWriter(openOutput(out), StandardCharsets.UTF_8);
    }

    static void ensureOutputFile(File out) throws IOException {
        if (out == null) {
            throw new IOException("Angelica render fact output file must not be null");
        }
        File parent = out.getParentFile();
        if (parent == null) {
            throw new IOException("Angelica render fact output parent directory must not be null: "
                    + out.getAbsolutePath());
        }
        ensureDirectory(parent);
        if (out.exists() && !out.isFile()) {
            throw new IOException("Angelica render fact output path exists but is not a file: "
                    + out.getAbsolutePath());
        }
    }

    static void ensureDirectory(File directory) throws IOException {
        if (directory == null) {
            throw new IOException("Angelica render fact output directory must not be null");
        }
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IOException(
                        "Angelica render fact output parent exists but is not a directory: "
                                + directory.getAbsolutePath());
            }
            return;
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Failed to create Angelica render fact output directory: "
                    + directory.getAbsolutePath());
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

    private static void closeAfterFailedWrap(FileOutputStream fos, Exception failure) {
        try {
            fos.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
