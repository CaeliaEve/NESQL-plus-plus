package com.github.dcysteine.nesql.exporter.local;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

final class RawExportEmptyJsonlWriter {
    private RawExportEmptyJsonlWriter() {}

    static void write(File out) throws IOException {
        try (Writer ignored = RawExportSidecarFileOps.createUtf8JsonlWriter(out)) {
            // Empty JSONL remains valid when a source is unavailable for this run.
        }
    }
}
