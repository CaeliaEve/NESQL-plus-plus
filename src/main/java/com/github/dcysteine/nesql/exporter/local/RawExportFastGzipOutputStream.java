package com.github.dcysteine.nesql.exporter.local;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * Fast gzip writer for raw-export fact streams.
 *
 * <p>Raw-export artifacts are large, local, compiler-facing JSONL streams. Export latency matters
 * more than maximum compression ratio because the downstream ABI validates content, not gzip
 * density.</p>
 */
final class RawExportFastGzipOutputStream extends GZIPOutputStream {
    private static final int BUFFER_BYTES = 1 << 20;

    RawExportFastGzipOutputStream(OutputStream outputStream) throws IOException {
        super(outputStream, BUFFER_BYTES);
        def.setLevel(Deflater.BEST_SPEED);
    }
}
