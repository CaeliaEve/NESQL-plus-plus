package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Immutable ordered capture units. These are replay inputs, never publishable Sources. */
public final class Checkpoints implements AutoCloseable {
    private static final int MANIFEST_LIMIT = 16 * 1024 * 1024;
    private static final int EVENT_LIMIT = Dataset.RECORD_LIMIT + 256;
    private static final long UNIT_LIMIT = 128L * 1024 * 1024 * 1024;
    private final Path root;
    private final JsonObject identity;
    private final JsonArray units = new JsonArray();
    private final List<String> order = new ArrayList<>();
    private final Set<String> assets = new HashSet<>();
    private final Listener listener;
    private OutputStream output;
    private MessageDigest digest;
    private String current, latest;
    private long bytes, events;
    private boolean released;

    public interface Listener { void saved(JsonObject receipt) throws IOException; }
    public interface Validator { void check(int unit, JsonObject evidence) throws Exception; }

    public Checkpoints(Path root, JsonObject provenance, List<String> handlers, String producer, Listener listener) throws IOException {
        this.root = root.toAbsolutePath().normalize(); this.listener = listener;
        if (provenance == null || handlers.size() > 512 || new HashSet<>(handlers).size() != handlers.size()) {
            throw new IllegalArgumentException("Invalid checkpoint identity");
        }
        for (String field : new String[]{"environment", "runtime", "session", "selection"}) {
            if (!provenance.has(field) || !provenance.get(field).getAsString().matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("Missing checkpoint provenance: " + field);
            }
        }
        JsonArray selected = new JsonArray();
        for (String handler : handlers) {
            if (!handler.matches("category_[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid checkpoint handler");
            selected.add(value(handler));
        }
        order.add("base"); order.addAll(handlers);
        identity = object("format", "nesql.checkpoint", "revision", 1, "producer", producer,
                "provenance", copy(provenance), "handlers", selected);
        Dataset.directory(this.root.getParent()); Files.createDirectory(this.root);
        Files.createDirectory(this.root.resolve("units"));
        Files.createDirectory(this.root.resolve("blobs"));
        Files.createDirectory(this.root.resolve("manifests"));
    }

    public void begin(String name) throws IOException {
        if (released || output != null || units.size() >= order.size() || !order.get(units.size()).equals(name)) {
            throw new IOException("Checkpoint units must follow the exact capture order");
        }
        current = name; bytes = 0; events = 0; digest = CanonicalJson.sha256();
        Dataset.plain(root.resolve("units"));
        output = new BufferedOutputStream(new DigestOutputStream(Files.newOutputStream(unit(units.size()), StandardOpenOption.CREATE_NEW), digest));
    }

    public void row(String kind, JsonObject row) throws IOException {
        if (!Dataset.COLLECTIONS.contains(kind) || !row.has("id")) throw new IOException("Invalid checkpoint record");
        if (CanonicalJson.bytes(row).length > Dataset.RECORD_LIMIT) throw new IOException("Checkpoint record exceeds 1 MiB");
        event(object("kind", kind, "row", row));
    }

    public void asset(Path source, String path) throws IOException {
        if (!path.matches("assets/[a-f0-9]{64}\\.(png|webp)")) throw new IOException("Invalid checkpoint asset path");
        if (assets.contains(path)) return;
        String file = path.substring("assets/".length()), hash = file.substring(0, 64);
        long size = Files.size(source);
        if (size < 1 || size > 64L * 1024 * 1024) throw new IOException("Invalid checkpoint asset size");
        verify(source, size, hash);
        Path target = root.resolve("blobs").resolve(file); Dataset.plain(target.getParent());
        try { Files.createLink(target, source); }
        catch (UnsupportedOperationException | IOException unavailable) {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw unavailable;
            Files.copy(source, target); verify(target, size, hash);
        }
        force(target);
        event(object("kind", "blob", "path", path, "bytes", Long.toString(size)));
        assets.add(path);
    }

    private void event(JsonObject event) throws IOException {
        Jobs.checkpoint();
        if (released || output == null) throw new IOException("No open checkpoint unit");
        byte[] encoded = CanonicalJson.bytes(event);
        if (encoded.length > EVENT_LIMIT || bytes + encoded.length + 1 > UNIT_LIMIT) throw new IOException("Checkpoint unit exceeds byte budget");
        output.write(encoded); output.write('\n'); bytes += encoded.length + 1; events++;
    }

    /** Native handler cleanup and the environment/session guards must pass first. */
    public void finish(JsonObject evidence) throws IOException {
        Jobs.checkpoint();
        if (released || output == null) throw new IOException("No open checkpoint unit");
        output.close(); output = null; force(unit(units.size()));
        JsonObject descriptor = object("name", current, "bytes", Long.toString(bytes), "events", Long.toString(events),
                "sha256", CanonicalJson.hex(digest.digest()), "evidence", copy(evidence));
        units.add(descriptor);
        JsonObject manifest = copy(identity); manifest.add("units", units);
        byte[] encoded = CanonicalJson.bytes(manifest);
        if (encoded.length > MANIFEST_LIMIT) throw new IOException("Checkpoint manifest exceeds byte budget");
        String hash = CanonicalJson.digest(encoded);
        write(root.resolve("manifests").resolve(hash + ".json"), encoded);
        latest = hash;
        listener.saved(object("path", root.toString(), "sha256", hash, "units", units.size(),
                "handlers", Math.max(0, units.size() - 1), "totalHandlers", order.size() - 1));
    }

    /** A terminal attempt can be retried only after all native resources were safely released. */
    public void release(boolean nativeCleanupSucceeded) throws IOException {
        if (released) return;
        released = true;
        boolean interrupted = Thread.interrupted();
        try {
            if (output != null) { output.close(); output = null; }
            if (nativeCleanupSucceeded && latest != null) write(root.resolve("released.json"), CanonicalJson.bytes(object("sha256", latest)));
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    /** Replays a sealed prefix into a fresh attempt; partial files are never consulted. */
    int replay(Path previous, String expected, Dataset dataset, Rows rows, BiConsumer<String, JsonObject> restored) throws IOException {
        try { return replay(previous, expected, dataset, rows, restored, (unit, evidence) -> {}); }
        catch (IOException failure) { throw failure; }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IOException(failure); }
    }

    public int replay(Path previous, String expected, Dataset dataset, Rows rows, BiConsumer<String, JsonObject> restored, Validator validator) throws Exception {
        java.util.Objects.requireNonNull(validator, "Native checkpoint validation is required");
        if (units.size() != 0 || output != null || released || previous.toAbsolutePath().normalize().equals(root)) throw new IOException("Invalid replay target");
        if (expected == null || !expected.matches("[a-f0-9]{64}")) throw new IOException("Checkpoint digest required");
        Dataset.plain(previous);
        JsonObject terminal = parse(read(previous.resolve("released.json"), 1024));
        if (!expected.equals(terminal.get("sha256").getAsString())) throw new IOException("Checkpoint is not the released terminal prefix");
        byte[] manifestBytes = read(previous.resolve("manifests").resolve(expected + ".json"), MANIFEST_LIMIT);
        if (!CanonicalJson.digest(manifestBytes).equals(expected)) throw new IOException("Checkpoint manifest digest mismatch");
        JsonObject manifest = parse(manifestBytes);
        JsonElement sourceUnits = manifest.remove("units");
        if (!identity.equals(manifest) || sourceUnits == null || !sourceUnits.isJsonArray()) throw new IOException("Checkpoint environment, build, session or selection changed");
        JsonArray saved = sourceUnits.getAsJsonArray();
        if (saved.size() < 1 || saved.size() > order.size()) throw new IOException("Invalid checkpoint unit count");
        for (int index = 0; index < saved.size(); index++) {
            Jobs.checkpoint();
            JsonObject descriptor = saved.get(index).getAsJsonObject();
            if (!order.get(index).equals(descriptor.get("name").getAsString())) throw new IOException("Checkpoint handler order changed");
            long length = descriptor.get("bytes").getAsLong();
            if (length < 0 || length > UNIT_LIMIT) throw new IOException("Invalid checkpoint unit size");
            Path source = previous.resolve("units").resolve(String.format(java.util.Locale.ROOT, "unit-%04d.jsonl", index));
            Dataset.plain(source);
            if (Files.size(source) != length) throw new IOException("Checkpoint unit size mismatch");
            begin(order.get(index));
            MessageDigest hash = CanonicalJson.sha256(); long count = 0, read = 0;
            try (InputStream input = new BufferedInputStream(Files.newInputStream(source))) {
                while (true) {
                    byte[] line = line(input); if (line == null) break;
                    Jobs.checkpoint(); read += line.length + 1;
                    if (read > length) throw new IOException("Checkpoint unit grew");
                    hash.update(line); hash.update((byte) '\n'); count++;
                    JsonObject entry = parse(line); String kind = entry.get("kind").getAsString();
                    if (kind.equals("blob")) {
                        String path = entry.get("path").getAsString();
                        if (!path.matches("assets/[a-f0-9]{64}\\.(png|webp)")) throw new IOException("Invalid checkpoint blob path");
                        Path blob = previous.resolve("blobs").resolve(path.substring(7));
                        long size = entry.get("bytes").getAsLong();
                        if (size < 1 || size > 64L * 1024 * 1024) throw new IOException("Invalid checkpoint blob length");
                        verify(blob, size, path.substring(7, 71));
                        String actual = dataset.asset(blob, path.endsWith(".png") ? "png" : "webp");
                        if (!actual.equals(path)) throw new IOException("Checkpoint blob changed during replay");
                        asset(blob, path);
                    } else {
                        JsonObject record = entry.getAsJsonObject("row");
                        row(kind, record); rows.add(kind, record); restored.accept(kind, record);
                    }
                }
            }
            if (read != length || count != descriptor.get("events").getAsLong()
                    || !CanonicalJson.hex(hash.digest()).equals(descriptor.get("sha256").getAsString())) {
                throw new IOException("Checkpoint unit digest/count mismatch");
            }
            // Reject damaged disk evidence before doing a potentially long native
            // data recheck. All replay output is still unpublished staging here.
            validator.check(index, descriptor.getAsJsonObject("evidence"));
            finish(descriptor.getAsJsonObject("evidence"));
        }
        return saved.size();
    }

    public JsonObject evidence() {
        JsonObject result = new JsonObject();
        for (JsonElement entry : units) {
            JsonObject unit = entry.getAsJsonObject();
            JsonObject evidence = unit.getAsJsonObject("evidence");
            JsonObject excluded = evidence.has("exclusions") ? evidence.getAsJsonObject("exclusions") : evidence;
            if (!unit.get("name").getAsString().equals("base") && !excluded.entrySet().isEmpty()) {
                result.add(unit.get("name").getAsString(), copy(excluded));
            }
        }
        return result;
    }

    private Path unit(int index) { return root.resolve("units").resolve(String.format(java.util.Locale.ROOT, "unit-%04d.jsonl", index)); }
    private static JsonObject copy(JsonObject object) { return new JsonParser().parse(object.toString()).getAsJsonObject(); }
    private static JsonObject parse(byte[] bytes) throws IOException {
        try { return new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IOException("Invalid checkpoint JSON", invalid); }
    }
    private static byte[] read(Path path, int limit) throws IOException {
        Dataset.plain(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > limit) throw new IOException("Invalid checkpoint file");
        try (InputStream input = Files.newInputStream(path); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[65536]; int length;
            while ((length = input.read(buffer)) != -1) { Jobs.checkpoint(); if (output.size() + length > limit) throw new IOException("Checkpoint file grew"); output.write(buffer, 0, length); }
            return output.toByteArray();
        }
    }
    private static byte[] line(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); int value;
        while ((value = input.read()) != -1 && value != '\n') {
            if (output.size() >= EVENT_LIMIT) throw new IOException("Checkpoint row exceeds byte budget"); output.write(value);
        }
        if (value == -1) { if (output.size() != 0) throw new IOException("Truncated checkpoint row"); return null; }
        return output.toByteArray();
    }
    private static void verify(Path path, long expected, String hash) throws IOException {
        Dataset.plain(path);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != expected) throw new IOException("Checkpoint blob size mismatch");
        MessageDigest digest = CanonicalJson.sha256(); long size = 0;
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int length;
            while ((length = input.read(buffer)) != -1) { Jobs.checkpoint(); size += length; if (size > expected) throw new IOException("Checkpoint blob grew"); digest.update(buffer, 0, length); }
        }
        if (size != expected || !CanonicalJson.hex(digest.digest()).equals(hash)) throw new IOException("Checkpoint blob digest mismatch");
    }
    private static void write(Path path, byte[] bytes) throws IOException {
        Dataset.plain(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "checkpoint-", ".tmp");
        try { Files.write(temporary, bytes); force(temporary); Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE); }
        finally { Files.deleteIfExists(temporary); }
    }
    private static void force(Path path) throws IOException { try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) { channel.force(true); } }
    @Override public void close() throws IOException { if (output != null) { output.close(); output = null; } }
}
