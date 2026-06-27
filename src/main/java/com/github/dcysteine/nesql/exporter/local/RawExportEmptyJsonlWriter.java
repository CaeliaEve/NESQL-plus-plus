package com.github.dcysteine.nesql.exporter.local;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

final class RawExportEmptyJsonlWriter {
    private RawExportEmptyJsonlWriter() {}

    static void write(File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
        }
        try (Writer ignored = createUtf8Writer(out)) {
            // Empty JSONL remains valid when a source is unavailable for this run.
        }
    }

    private static OutputStreamWriter createUtf8Writer(File out) throws IOException {
        FileOutputStream fos = new FileOutputStream(out);
        if (out.getName().endsWith(".gz")) {
            return new OutputStreamWriter(new GZIPOutputStream(fos), StandardCharsets.UTF_8);
        }
        return new OutputStreamWriter(fos, StandardCharsets.UTF_8);
    }
}
