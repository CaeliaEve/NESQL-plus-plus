package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;

/** Streams one JSON value per line without requiring an intermediate JSON tree. */
final class RawExportJsonlWriter implements Closeable {
    private final Gson gson;
    private final Writer writer;

    RawExportJsonlWriter(File out, Gson gson) throws IOException {
        this.gson = gson;
        this.writer = RawExportSidecarFileOps.createUtf8JsonlWriter(out);
    }

    void write(JsonElement element) throws IOException {
        gson.toJson(element, writer);
        writer.write('\n');
    }

    void write(Object value, Type type) throws IOException {
        gson.toJson(value, type, writer);
        writer.write('\n');
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
