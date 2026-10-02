package com.github.dcysteine.nesql.exporter.capture;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;

/** Reuses only input bytes protected by a live filesystem lease. */
final class Fingerprints implements AutoCloseable {
    private final Path root;
    private Path witnesses;
    private final Map<Path, Entry> entries = new LinkedHashMap<>();
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor(action -> {
        Thread thread = new Thread(action, "NESQL input leases"); thread.setDaemon(true); return thread;
    });
    private ScheduledFuture<?> idle;
    private IOException failure;
    private boolean closed;
    private long hits, readBytes;
    private long generation;
    Fingerprints() { this(java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"))); }
    Fingerprints(Path root) { this.root = root; }

    synchronized void begin() throws IOException {
        if (closed) throw new IOException("Input hash cache is closed");
        if (failure != null) throw failure;
        if (idle != null) idle.cancel(false);
        generation++;
        hits = readBytes = 0;
    }
    synchronized void end() {
        if (closed) return;
        long expected = generation;
        idle = expiry.schedule(() -> {
            synchronized (Fingerprints.this) {
                if (generation != expected || closed) return;
                try { clear(); } catch (IOException error) { failure = error; }
            }
        }, 30, TimeUnit.SECONDS);
    }
    synchronized com.google.gson.JsonObject statistics() {
        return com.github.dcysteine.nesql.exporter.source.Json.object("hits", Long.toString(hits), "readBytes", Long.toString(readBytes), "leasedFiles", entries.size());
    }

    synchronized String hash(Path input) throws IOException {
        if (closed) throw new IOException("Input hash cache is closed");
        Jobs.checkpoint();
        Path path = input.toAbsolutePath().normalize();
        Sources.requirePlain(path);
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
        Entry entry = entries.get(path);
        if (entry != null) {
            if (entry.channel.isOpen() && Files.isSameFile(entry.witness, path) && entry.size == attributes.size()) { hits++; return entry.hash; }
            entries.remove(path); entry.close();
        }
        OpenOption[] protection = options();
        if (protection == null || attributes.size() < 256 * 1024 || entries.size() >= 512) {
            readBytes += attributes.size(); return Sources.hash(path);
        }
        FileChannel channel;
        Path witness = null;
        try {
            if (witnesses == null) { com.github.dcysteine.nesql.exporter.source.Dataset.directory(root); witnesses = Files.createTempDirectory(root, "input-leases-"); }
            witness = witnesses.resolve(java.util.UUID.randomUUID().toString());
            Files.createLink(witness, path);
            channel = FileChannel.open(path, protection);
        } catch (IOException | UnsupportedOperationException unavailable) {
            if (witness != null) Files.deleteIfExists(witness);
            readBytes += attributes.size(); return Sources.hash(path);
        }
        boolean retained = false;
        try {
            BasicFileAttributes pinned = Files.readAttributes(path, BasicFileAttributes.class);
            if (!Files.isSameFile(witness, path) || attributes.size() != pinned.size()) throw new IOException("Input changed while acquiring its lease: " + path);
            java.security.MessageDigest digest = CanonicalJson.sha256();
            ByteBuffer buffer = ByteBuffer.allocate(65536);
            while (channel.read(buffer) != -1) { Jobs.checkpoint(); buffer.flip(); readBytes += buffer.remaining(); digest.update(buffer); buffer.clear(); }
            String hash = CanonicalJson.hex(digest.digest());
            entries.put(path, new Entry(channel, witness, pinned.size(), hash)); retained = true;
            return hash;
        } finally { if (!retained) { channel.close(); Files.delete(witness); } }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static OpenOption[] options() {
        if (!System.getProperty("os.name").startsWith("Windows")) return null;
        try {
            Class type = Class.forName("com.sun.nio.file.ExtendedOpenOption");
            return new OpenOption[] {StandardOpenOption.READ, (OpenOption) Enum.valueOf(type, "NOSHARE_WRITE"), (OpenOption) Enum.valueOf(type, "NOSHARE_DELETE")};
        } catch (ReflectiveOperationException | IllegalArgumentException unavailable) { return null; }
    }
    private void clear() throws IOException {
        IOException error = null;
        for (Entry entry : entries.values()) try { entry.close(); }
        catch (IOException closing) { if (error == null) error = closing; else error.addSuppressed(closing); }
        entries.clear();
        if (error == null && witnesses != null) { Files.delete(witnesses); witnesses = null; }
        if (error != null) throw error;
    }
    @Override public synchronized void close() throws IOException { closed = true; expiry.shutdownNow(); clear(); }
    private static final class Entry {
        final FileChannel channel; final Path witness; final long size; final String hash;
        Entry(FileChannel channel, Path witness, long size, String hash) { this.channel = channel; this.witness = witness; this.size = size; this.hash = hash; }
        void close() throws IOException { channel.close(); Files.delete(witness); }
    }
}
