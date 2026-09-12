package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/** Bounded external sort: collection order never depends on game callbacks or hash-map iteration. */
public final class Rows implements AutoCloseable {
    private static final int BUFFER_LIMIT = 4 * 1024 * 1024;
    private static final int FAN_IN = 32;
    private final Path directory;
    private final int bufferLimit;
    private final Map<String, Buffer> buffers = new TreeMap<>();
    private int sequence;
    private boolean finished;

    public Rows(Path directory) throws IOException {
        this(directory, BUFFER_LIMIT);
    }

    Rows(Path directory, int bufferLimit) throws IOException {
        if (bufferLimit < 64 || bufferLimit > BUFFER_LIMIT) throw new IllegalArgumentException("Invalid sort memory budget");
        this.directory = directory.toAbsolutePath().normalize();
        this.bufferLimit = bufferLimit;
        Dataset.plain(this.directory.getParent());
        Files.createDirectory(this.directory);
        for (String kind : Dataset.COLLECTIONS) buffers.put(kind, new Buffer());
    }

    public void add(String kind, JsonObject row) throws IOException {
        Jobs.checkpoint();
        if (finished) throw new IllegalStateException("Record sorting is complete");
        Buffer buffer = buffers.get(kind);
        if (buffer == null) throw new IllegalArgumentException("Unknown collection: " + kind);
        String id = row.get("id").getAsString();
        byte[] bytes = CanonicalJson.bytes(row);
        if (bytes.length > Dataset.RECORD_LIMIT) throw new IOException("Record exceeds 1 MiB: " + kind + "/" + id);
        byte[] previous = buffer.rows.putIfAbsent(id, bytes);
        if (previous != null) {
            if (!Arrays.equals(previous, bytes)) throw new IOException("Conflicting " + kind + " identity: " + id);
            return;
        }
        buffer.bytes += bytes.length;
        if (buffer.bytes >= bufferLimit) spill(buffer);
    }

    public void write(Dataset dataset) throws IOException {
        if (finished) throw new IllegalStateException("Records already written");
        finished = true;
        for (Map.Entry<String, Buffer> entry : buffers.entrySet()) {
            Jobs.checkpoint();
            Buffer buffer = entry.getValue();
            if (!buffer.rows.isEmpty()) spill(buffer);
            while (buffer.runs.size() > FAN_IN) {
                List<Path> compacted = new ArrayList<>();
                for (int start = 0; start < buffer.runs.size(); start += FAN_IN) {
                    List<Path> batch = buffer.runs.subList(start, Math.min(start + FAN_IN, buffer.runs.size()));
                    Path target = run();
                    try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW))) {
                        merge(batch, row -> { output.write(row); output.write('\n'); });
                    }
                    compacted.add(target);
                    for (Path path : batch) Files.delete(path);
                }
                buffer.runs = compacted;
            }
            try (Dataset.Records records = dataset.records(entry.getKey())) {
                merge(buffer.runs, row -> records.write(parse(row)));
            }
            for (Path path : buffer.runs) Files.delete(path);
            buffer.runs.clear();
        }
    }

    private Path run() { return directory.resolve(String.format(java.util.Locale.ROOT, "run-%08d.jsonl", sequence++)); }

    private void spill(Buffer buffer) throws IOException {
        Path path = run();
        try (OutputStream output = new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW))) {
            for (byte[] row : buffer.rows.values()) { output.write(row); output.write('\n'); }
        }
        buffer.runs.add(path);
        buffer.rows.clear(); buffer.bytes = 0;
    }

    private static void merge(List<Path> runs, Sink output) throws IOException {
        List<Cursor> cursors = new ArrayList<>();
        PriorityQueue<Cursor> queue = new PriorityQueue<>((left, right) -> CanonicalJson.KEY_ORDER.compare(left.id, right.id));
        Throwable failure = null;
        try {
            for (Path path : runs) {
                Cursor cursor = new Cursor(path); cursors.add(cursor);
                if (cursor.next()) queue.add(cursor);
            }
            String previousId = null;
            byte[] previousRow = null;
            while (!queue.isEmpty()) {
                Jobs.checkpoint();
                Cursor cursor = queue.remove();
                if (cursor.id.equals(previousId)) {
                    if (!Arrays.equals(cursor.row, previousRow)) throw new IOException("Conflicting record identity: " + cursor.id);
                } else {
                    output.accept(cursor.row);
                    previousId = cursor.id; previousRow = cursor.row;
                }
                if (cursor.next()) queue.add(cursor);
            }
        } catch (IOException | RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            IOException closing = null;
            for (Cursor cursor : cursors) {
                try { cursor.input.close(); }
                catch (IOException error) { if (closing == null) closing = error; else closing.addSuppressed(error); }
            }
            if (closing != null) {
                if (failure != null) failure.addSuppressed(closing); else throw closing;
            }
        }
    }

    @Override public void close() throws IOException {
        finished = true;
        Dataset.plain(directory);
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (!file.getFileName().toString().matches("run-[0-9]{8}\\.jsonl")) {
                    throw new IOException("Unexpected file in sort workspace: " + file);
                }
                Files.delete(file);
            }
        }
        Files.delete(directory);
        buffers.clear();
    }

    private static JsonObject parse(byte[] row) { return new JsonParser().parse(new String(row, StandardCharsets.UTF_8)).getAsJsonObject(); }
    private interface Sink { void accept(byte[] row) throws IOException; }
    private static final class Buffer {
        final TreeMap<String, byte[]> rows = new TreeMap<>(CanonicalJson.KEY_ORDER);
        List<Path> runs = new ArrayList<>();
        int bytes;
    }
    private static final class Cursor {
        final InputStream input;
        String id;
        byte[] row;
        Cursor(Path path) throws IOException { input = new BufferedInputStream(Files.newInputStream(path)); }
        boolean next() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int value;
            while ((value = input.read()) != -1 && value != '\n') {
                if (bytes.size() >= Dataset.RECORD_LIMIT) throw new IOException("Sort run record exceeds 1 MiB");
                bytes.write(value);
            }
            if (bytes.size() == 0 && value == -1) return false;
            if (value != '\n') throw new IOException("Truncated sort run record");
            row = bytes.toByteArray(); id = parse(row).get("id").getAsString();
            return true;
        }
    }
}
