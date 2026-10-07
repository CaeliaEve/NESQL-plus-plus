package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Durable immutable Source files. Incomplete captures are evidence, never datasets. */
public final class Fragments {
    private static final int LIMIT = 64 * 1024 * 1024;
    // Capture v1 contains both files and source.files. Keep the Source limit,
    // allowing two copies plus 1 MiB for request/provenance and envelope fields.
    private static final int MANIFEST_LIMIT = 2 * Dataset.MANIFEST_LIMIT + 1024 * 1024;
    private final Path root;
    private final Listener listener;
    private final JsonObject manifest;
    private final Map<String, JsonObject> files = new TreeMap<>();
    private boolean complete;
    private String manifestHash;
    private long manifestBytes;
    private long descriptorBytes = 2; // JSON array brackets; include commas as records arrive.

    public interface Listener { void saved(Receipt receipt) throws IOException; }
    public static final class Receipt {
        public String path, state, sha256;
        public long bytes;
        public int files;
    }

    public Fragments(Path root, JsonObject provenance, Jobs.Request request, Listener listener) throws IOException {
        if (request.check != null || provenance == null) throw new IllegalArgumentException("Fragments require export provenance");
        this.root = root.toAbsolutePath().normalize(); this.listener = listener;
        Dataset.directory(this.root.getParent());
        Files.createDirectory(this.root);
        Files.createDirectory(this.root.resolve("blobs"));
        Files.createDirectory(this.root.resolve("parts"));
        manifest = object("format", "elysium.capture", "revision", 1, "state", "writing", "provenance", copy(provenance),
                "request", new com.google.gson.Gson().toJsonTree(request));
        save();
    }

    void record(Path source, JsonObject descriptor) throws IOException {
        if (complete) throw new IOException("Capture is complete");
        Jobs.checkpoint();
        String path = descriptor.get("path").getAsString();
        if (files.containsKey(path)) throw new IOException("Duplicate capture file: " + path);
        byte[] partBytes = CanonicalJson.bytes(descriptor);
        long nextBytes = descriptorBytes + partBytes.length + (files.isEmpty() ? 0 : 1);
        if (nextBytes > Dataset.MANIFEST_LIMIT) throw new IOException("Capture descriptors exceed Source manifest byte limit");
        if (descriptor.get("kind").getAsString().equals("environment") &&
                !descriptor.get("sha256").equals(manifest.getAsJsonObject("provenance").get("environment"))) {
            throw new IOException("Capture environment differs from its provenance");
        }
        verify(source, descriptor);
        Path blob = blob(descriptor);
        if (Files.exists(blob, LinkOption.NOFOLLOW_LINKS)) verify(blob, descriptor);
        else {
            // Dataset files are closed and never rewritten. Sharing an immutable
            // inode avoids storing a second copy of every exported texture.
            try { Files.createLink(blob, source); }
            catch (UnsupportedOperationException | IOException unavailable) {
                if (Files.exists(blob, LinkOption.NOFOLLOW_LINKS)) throw unavailable;
                Path temporary = Files.createTempFile(root, "fragment-", ".tmp");
                try {
                    Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
                    verify(temporary, descriptor);
                    Files.move(temporary, blob, StandardCopyOption.ATOMIC_MOVE);
                } finally { Files.deleteIfExists(temporary); }
            }
            try (FileChannel channel = FileChannel.open(blob, StandardOpenOption.WRITE)) { channel.force(true); }
        }
        // One bounded receipt per fragment avoids rewriting a growing manifest
        // for every icon (quadratic work on a full item catalog).
        write(part(path), partBytes, false);
        files.put(path, copy(descriptor));
        descriptorBytes = nextBytes;
        if (files.size() % 64 == 0) notifySaved();
    }

    /** Called only after native capture and resource cleanup have passed their guards. */
    public void complete(JsonObject source) throws IOException {
        if (CanonicalJson.bytes(source).length > Dataset.MANIFEST_LIMIT) throw new IOException("Source manifest exceeds 64 MiB");
        JsonObject expected = copy(source);
        String id = expected.remove("id").getAsString();
        if (!CanonicalJson.digest(expected).equals(id) || !source.get("format").getAsString().equals(Dataset.FORMAT)
                || source.get("revision").getAsInt() != Dataset.REVISION
                || !source.get("environment").equals(manifest.getAsJsonObject("provenance").get("environment"))
                || !source.getAsJsonArray("files").equals(descriptors())) throw new IOException("Capture does not contain exactly this Source");
        if (complete && !source.equals(manifest.getAsJsonObject("source"))) throw new IOException("Capture Source changed");
        for (JsonObject descriptor : files.values()) {
            verify(blob(descriptor), descriptor);
            Path receipt = part(descriptor.get("path").getAsString()); Dataset.plain(receipt);
            byte[] expectedPart = CanonicalJson.bytes(descriptor);
            if (Files.size(receipt) != expectedPart.length || !java.util.Arrays.equals(Files.readAllBytes(receipt), expectedPart)) throw new IOException("Capture fragment receipt changed");
        }
        Jobs.checkpoint();
        manifest.add("source", copy(source)); manifest.addProperty("state", "complete");
        complete = true; save();
    }

    private Path blob(JsonObject descriptor) throws IOException {
        Dataset.plain(root); Dataset.plain(root.resolve("blobs"));
        String hash = descriptor.get("sha256").getAsString();
        if (!hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid fragment digest");
        return root.resolve("blobs").resolve(hash);
    }

    private static void verify(Path path, JsonObject descriptor) throws IOException {
        Dataset.plain(path);
        long expected = descriptor.get("bytes").getAsLong();
        if (expected < 1 || expected > LIMIT || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid capture fragment");
        MessageDigest hash = CanonicalJson.sha256(); long size = 0;
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) {
                Jobs.checkpoint(); size += count;
                if (size > expected) throw new IOException("Capture fragment grew");
                hash.update(buffer, 0, count);
            }
        }
        if (size != expected || !CanonicalJson.hex(hash.digest()).equals(descriptor.get("sha256").getAsString())) throw new IOException("Capture fragment digest mismatch");
    }

    private JsonArray descriptors() {
        JsonArray array = new JsonArray(); for (JsonObject value : files.values()) array.add(value); return array;
    }

    private void save() throws IOException {
        Dataset.plain(root);
        manifest.add("files", descriptors()); manifest.remove("id");
        manifest.addProperty("id", CanonicalJson.digest(manifest));
        byte[] bytes = CanonicalJson.bytes(manifest);
        if (bytes.length > MANIFEST_LIMIT) throw new IOException("Capture manifest exceeds 129 MiB");
        write(root.resolve("manifest.json"), bytes, true);
        manifestHash = CanonicalJson.digest(bytes); manifestBytes = bytes.length;
        notifySaved();
    }

    private Path part(String path) throws IOException {
        Dataset.plain(root); Dataset.plain(root.resolve("parts"));
        return root.resolve("parts").resolve(CanonicalJson.digest(path.getBytes(java.nio.charset.StandardCharsets.UTF_8)) + ".json");
    }

    private void write(Path path, byte[] bytes, boolean replace) throws IOException {
        Dataset.plain(root);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Dataset.plain(path);
            if (!replace) throw new IOException("Capture fragment already exists");
        }
        Path temporary = Files.createTempFile(root, "manifest-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            if (replace) Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }

    private void notifySaved() throws IOException {
        Receipt receipt = new Receipt(); receipt.path = root.resolve("manifest.json").toString(); receipt.state = manifest.get("state").getAsString();
        receipt.sha256 = manifestHash; receipt.bytes = manifestBytes; receipt.files = files.size();
        listener.saved(receipt);
    }

    private static JsonObject copy(JsonObject value) { return new JsonParser().parse(value.toString()).getAsJsonObject(); }
}
