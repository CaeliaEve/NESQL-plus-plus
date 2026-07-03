package com.github.dcysteine.nesql.exporter.local;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

final class RawExportEmptyJsonlWriter {
    private RawExportEmptyJsonlWriter() {}

    static void writeIfMissing(File out) throws IOException {
        if (out == null) {
            throw new IOException("Raw-export empty JSONL output must not be null");
        }
        if (out.exists()) {
            if (!out.isFile()) {
                throw new IOException(
                        "Raw-export empty JSONL output path exists but is not a file: " + out.getAbsolutePath());
            }
            return;
        }
        write(out);
    }

    static void write(File out) throws IOException {
        try (Writer ignored = RawExportSidecarFileOps.createUtf8JsonlWriter(out)) {
            // Empty JSONL remains valid when a source is unavailable for this run.
        }
    }
}
