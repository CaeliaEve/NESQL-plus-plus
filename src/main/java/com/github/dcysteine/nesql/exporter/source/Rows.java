package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
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
    private boolean finished, created;

    public Rows(Path directory) throws IOException {
        this(directory, BUFFER_LIMIT);
    }

    Rows(Path directory, int bufferLimit) throws IOException {
        if (bufferLimit < 64 || bufferLimit > BUFFER_LIMIT) throw new IllegalArgumentException("Invalid sort memory budget");
        this.directory = directory.toAbsolutePath().normalize();
        this.bufferLimit = bufferLimit;
        Dataset.plain(this.directory.getParent());
        if (Files.exists(this.directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new IOException("Sort workspace already exists: " + directory);
        for (String kind : Dataset.COLLECTIONS) buffers.put(kind, new Buffer());
    }

    public void add(String kind, JsonObject row) throws IOException {
        Jobs.checkpoint();
        if (finished) throw new IllegalStateException("Record sorting is complete");
        Buffer buffer = buffers.get(kind);
        if (buffer == null) throw new IllegalArgumentException("Unknown collection: " + kind);
        CanonicalRecord record = new CanonicalRecord(row);
        CanonicalRecord previous = buffer.rows.putIfAbsent(record.id, record);
        if (previous != null) {
            if (!Arrays.equals(previous.bytes, record.bytes)) throw new IOException("Conflicting " + kind + " identity: " + record.id);
            return;
        }
        buffer.bytes += record.bytes.length;
        if (buffer.bytes >= bufferLimit) spill(buffer);
    }

    public void write(Dataset dataset) throws IOException {
        if (finished) throw new IllegalStateException("Records already written");
        finished = true;
        for (Map.Entry<String, Buffer> entry : buffers.entrySet()) {
            Jobs.checkpoint();
            Buffer buffer = entry.getValue();
            sort(buffer);
            try (Dataset.Records records = dataset.records(entry.getKey())) {
                merge(buffer.runs, records::write);
            }
            for (Path path : buffer.runs) Files.delete(path);
            buffer.runs.clear();
        }
    }

    /** Exercise the same bounded merge and identity checks without publishing a source. */
    public Map<String, Long> check() throws IOException {
        try (Jobs.Timing ignored = Jobs.measure("rowsCheck")) { return checkRows(); }
    }
    private Map<String, Long> checkRows() throws IOException {
        if (finished) throw new IllegalStateException("Records already checked");
        finished = true;
        Map<String, Long> counts = new TreeMap<>();
        for (Map.Entry<String, Buffer> entry : buffers.entrySet()) {
            Jobs.checkpoint();
            Buffer buffer = entry.getValue();
            if (buffer.runs.isEmpty()) {
                if (!buffer.rows.isEmpty()) counts.put(entry.getKey(), (long) buffer.rows.size());
                buffer.rows.clear(); buffer.bytes = 0;
                continue;
            }
            sort(buffer);
            long[] count = {0};
            merge(buffer.runs, row -> count[0]++);
            if (count[0] != 0) counts.put(entry.getKey(), count[0]);
            for (Path path : buffer.runs) Files.delete(path);
            buffer.runs.clear();
        }
        return counts;
    }

    private void sort(Buffer buffer) throws IOException {
        if (!buffer.rows.isEmpty()) spill(buffer);
        while (buffer.runs.size() > FAN_IN) {
            List<Path> compacted = new ArrayList<>();
            for (int start = 0; start < buffer.runs.size(); start += FAN_IN) {
                List<Path> batch = buffer.runs.subList(start, Math.min(start + FAN_IN, buffer.runs.size()));
                Path target = run();
                try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)))) {
                    merge(batch, row -> row.spill(output));
                }
                compacted.add(target);
                for (Path path : batch) Files.delete(path);
            }
            buffer.runs = compacted;
        }
    }

    private Path run() { return directory.resolve(String.format(java.util.Locale.ROOT, "run-%08d.rows", sequence++)); }

    private void spill(Buffer buffer) throws IOException {
        if (!created) { Dataset.plain(directory.getParent()); Files.createDirectory(directory); created = true; }
        Path path = run();
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)))) {
            for (CanonicalRecord row : buffer.rows.values()) row.spill(output);
        }
        buffer.runs.add(path);
        buffer.rows.clear(); buffer.bytes = 0;
    }

    private static void merge(List<Path> runs, Sink output) throws IOException {
        List<Cursor> cursors = new ArrayList<>();
        PriorityQueue<Cursor> queue = new PriorityQueue<>((left, right) -> CanonicalJson.KEY_ORDER.compare(left.row.id, right.row.id));
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
                if (cursor.row.id.equals(previousId)) {
                    if (!Arrays.equals(cursor.row.bytes, previousRow)) throw new IOException("Conflicting record identity: " + cursor.row.id);
                } else {
                    output.accept(cursor.row);
                    previousId = cursor.row.id; previousRow = cursor.row.bytes;
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
        if (!created) { buffers.clear(); return; }
        Dataset.plain(directory);
        try (java.nio.file.DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                if (!file.getFileName().toString().matches("run-[0-9]{8}\\.rows")) {
                    throw new IOException("Unexpected file in sort workspace: " + file);
                }
                Files.delete(file);
            }
        }
        Files.delete(directory);
        buffers.clear();
    }

    private interface Sink { void accept(CanonicalRecord row) throws IOException; }
    private static final class Buffer {
        final TreeMap<String, CanonicalRecord> rows = new TreeMap<>(CanonicalJson.KEY_ORDER);
        List<Path> runs = new ArrayList<>();
        int bytes;
    }
    private static final class Cursor {
        final DataInputStream input;
        CanonicalRecord row;
        Cursor(Path path) throws IOException { input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path))); }
        boolean next() throws IOException {
            row = CanonicalRecord.read(input);
            return row != null;
        }
    }
}
