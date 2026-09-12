package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/** Writes one immutable, declared source dataset. The caller owns record ordering. */
public final class Dataset implements AutoCloseable {
    public static final String FORMAT = "elysium.source";
    public static final int REVISION = 10;
    public static final int RECORD_LIMIT = 1024 * 1024;
    public static final List<String> COLLECTIONS = java.util.Collections.unmodifiableList(java.util.Arrays.asList(
            "aspects", "assets", "blocks", "builds", "categories", "circuits", "fluids", "groups", "items", "materials", "models", "mutations", "recipes", "research", "shapes", "species", "strings", "structures", "tracks", "views"));
    private static final long SHARD_LIMIT = 16L * 1024 * 1024;
    private final Path staging;
    private final JsonObject manifest = new JsonObject();
    private final List<JsonObject> files = new ArrayList<>();
    private final Set<String> paths = new HashSet<>();
    private final Set<String> collections = new HashSet<>();
    private final List<Records> writers = new ArrayList<>();
    private final boolean complete;
    private boolean sealed;
    private boolean closed;
    private String id;

    public Dataset(Path staging, String producerVersion, JsonObject environment, boolean complete) throws IOException {
        if (environment == null) throw new IllegalArgumentException("Environment facts are required");
        this.staging = staging.toAbsolutePath().normalize();
        this.complete = complete;
        byte[] environmentBytes = CanonicalJson.bytes(environment);
        if (environmentBytes.length > 16 * 1024 * 1024) throw new IllegalArgumentException("Environment exceeds 16 MiB");
        plain(this.staging.getParent());
        Files.createDirectory(this.staging);
        manifest.addProperty("format", FORMAT);
        manifest.addProperty("revision", REVISION);
        String fingerprint = CanonicalJson.digest(environmentBytes);
        try {
            Files.write(this.staging.resolve("environment.json"), environmentBytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException | RuntimeException error) {
            try { removeStaging(); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            throw error;
        }
        declare("environment.json", "environment", "json", environmentBytes.length, environmentBytes.length, 1, fingerprint);
        manifest.addProperty("environment", fingerprint);
        JsonObject producer = new JsonObject();
        producer.addProperty("name", "nesql");
        producer.addProperty("version", producerVersion);
        manifest.add("producer", producer);
    }

    public Records records(String kind) throws IOException {
        writable();
        if (kind == null || !kind.matches("[a-z][a-z0-9-]{0,47}") || kind.equals("asset") || kind.equals("environment")) {
            throw new IllegalArgumentException("Invalid collection: " + kind);
        }
        if (!collections.add(kind)) throw new IllegalArgumentException("Collection already opened: " + kind);
        Records records = new Records(kind);
        writers.add(records);
        return records;
    }

    public String asset(Path source, String extension) throws IOException {
        writable();
        if (!java.util.Arrays.asList("png", "webp").contains(extension)) {
            throw new IllegalArgumentException("Unsupported asset encoding: " + extension);
        }
        long length = Files.size(source);
        if (length <= 0 || length > 64L * 1024 * 1024) throw new IOException("Asset exceeds size limit: " + source);
        Path temporary = staging.resolve("asset.tmp");
        MessageDigest digest = CanonicalJson.sha256();
        try (InputStream input = Files.newInputStream(source);
             OutputStream output = new DigestOutputStream(Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW), digest)) {
            byte[] buffer = new byte[65536];
            int count;
            long copied = 0;
            while ((count = input.read(buffer)) != -1) {
                Jobs.checkpoint();
                copied += count;
                if (copied > length) throw new IOException("Asset grew during capture: " + source);
                output.write(buffer, 0, count);
            }
        }
        if (Files.size(temporary) != length) throw new IOException("Asset changed during capture: " + source);
        String hash = CanonicalJson.hex(digest.digest());
        String relative = "assets/" + hash + "." + extension;
        if (paths.contains(relative)) Files.delete(temporary);
        else {
            Files.createDirectories(staging.resolve("assets"));
            Files.move(temporary, staging.resolve(relative), StandardCopyOption.ATOMIC_MOVE);
            declare(relative, "asset", extension, length, length, 0, hash);
        }
        return relative;
    }

    public String asset(byte[] bytes, String extension) throws IOException {
        writable();
        if (!java.util.Arrays.asList("png", "webp").contains(extension)) throw new IllegalArgumentException("Unsupported image encoding");
        if (bytes.length == 0 || bytes.length > 64 * 1024 * 1024) throw new IOException("Asset exceeds size limit");
        String hash = CanonicalJson.digest(bytes);
        String relative = "assets/" + hash + "." + extension;
        if (!paths.contains(relative)) {
            Files.createDirectories(staging.resolve("assets"));
            Files.write(staging.resolve(relative), bytes, StandardOpenOption.CREATE_NEW);
            declare(relative, "asset", extension, bytes.length, bytes.length, 0, hash);
        }
        return relative;
    }

    /** Finalizes all counts and digests before the dataset can become visible. */
    public String seal() throws IOException {
        writable();
        for (Records writer : writers) writer.close();
        if (complete && !collections.containsAll(COLLECTIONS)) {
            throw new IOException("A complete source must declare every core collection");
        }
        JsonObject scope = new JsonObject();
        scope.addProperty("mode", complete ? "complete" : "selection");
        JsonArray names = new JsonArray();
        collections.stream().sorted().forEach(name -> names.add(new com.google.gson.JsonPrimitive(name)));
        scope.add("collections", names);
        manifest.add("scope", scope);
        files.sort(Comparator.comparing(file -> file.get("path").getAsString()));
        JsonArray descriptors = new JsonArray();
        files.forEach(descriptors::add);
        manifest.add("files", descriptors);
        id = CanonicalJson.digest(manifest);
        manifest.addProperty("id", id);
        Files.write(staging.resolve("manifest.json"), CanonicalJson.bytes(manifest), StandardOpenOption.CREATE_NEW);
        sealed = true;
        return id;
    }

    /** Verify reuse and perform cleanup before the job's short atomic commit section. */
    public java.util.concurrent.Callable<Jobs.Result> prepare(Path datasets) throws IOException {
        if (!sealed || closed) throw new IllegalStateException("Dataset is not sealed");
        datasets = datasets.toAbsolutePath().normalize();
        plain(datasets.getParent());
        Files.createDirectories(datasets);
        plain(datasets);
        Path target = datasets.resolve(id);
        if (Files.exists(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            plain(target);
            // Repeated identical exports share a content id; verify every declared byte before reuse.
            byte[] expected = CanonicalJson.bytes(manifest);
            Path existing = target.resolve("manifest.json");
            plain(existing);
            if (Files.size(existing) != expected.length || !java.util.Arrays.equals(expected, Files.readAllBytes(existing))) {
                throw new IOException("Existing dataset has a different manifest: " + id);
            }
            for (JsonObject file : files) verifyFile(target, file);
            removeStaging();
            closed = true;
            return () -> new Jobs.Result(id, target);
        }
        return () -> {
            if (closed) throw new IllegalStateException("Dataset has already been closed");
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            closed = true;
            return new Jobs.Result(id, target);
        };
    }

    private static void verifyFile(Path root, JsonObject descriptor) throws IOException {
        Path path = root.resolve(descriptor.get("path").getAsString());
        plain(path);
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) != descriptor.get("bytes").getAsLong()) throw new IOException("Invalid dataset file: " + path);
        MessageDigest digest = CanonicalJson.sha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) { Jobs.checkpoint(); digest.update(buffer, 0, count); }
        }
        if (!CanonicalJson.hex(digest.digest()).equals(descriptor.get("sha256").getAsString())) {
            throw new IOException("Dataset file digest mismatch: " + path);
        }
    }

    private void declare(String path, String kind, String encoding, long bytes, long decoded, long rows, String hash) {
        if (!paths.add(path)) throw new IllegalStateException("Duplicate dataset path: " + path);
        JsonObject file = new JsonObject();
        file.addProperty("path", path);
        file.addProperty("kind", kind);
        file.addProperty("encoding", encoding);
        file.addProperty("bytes", bytes);
        file.addProperty("decodedBytes", decoded);
        file.addProperty("rows", rows);
        file.addProperty("sha256", hash);
        files.add(file);
    }

    private void writable() {
        if (sealed || closed) throw new IllegalStateException("Dataset is closed for writing");
        Jobs.checkpoint();
    }

    public final class Records implements AutoCloseable {
        private final String kind;
        private int part;
        private long rows;
        private long decoded;
        private String lastId;
        private byte[] lastRow;
        private String relative;
        private MessageDigest digest;
        private GZIPOutputStream output;
        private boolean finished;

        private Records(String kind) throws IOException { this.kind = kind; open(); }

        public void write(JsonObject record) throws IOException {
            writable();
            if (finished) throw new IllegalStateException("Collection is closed: " + kind);
            if (!record.has("id") || !record.get("id").isJsonPrimitive()) throw new IOException("Missing record id: " + kind);
            String key = record.get("id").getAsString();
            if (!key.matches("[a-zA-Z0-9_:.#/@-]{1,256}")) throw new IOException("Invalid record id: " + kind);
            byte[] row = CanonicalJson.bytes(record);
            if (row.length > RECORD_LIMIT) throw new IOException("Record exceeds size limit: " + kind + "/" + key);
            if (lastId != null) {
                int order = CanonicalJson.KEY_ORDER.compare(lastId, key);
                if (order > 0) throw new IOException("Records must be ordered by id: " + kind + "/" + key);
                if (order == 0) {
                    if (!java.util.Arrays.equals(lastRow, row)) throw new IOException("Conflicting record id: " + kind + "/" + key);
                    return;
                }
            }
            if (rows >= 4096 || decoded + row.length + 1 > SHARD_LIMIT) { finishPart(); open(); }
            output.write(row);
            output.write('\n');
            rows++;
            decoded += row.length + 1;
            lastId = key;
            lastRow = row;
        }

        private void open() throws IOException {
            relative = String.format(java.util.Locale.ROOT, "records/%s/part-%06d.jsonl.gz", kind, part++);
            Path path = staging.resolve(relative);
            Files.createDirectories(path.getParent());
            digest = CanonicalJson.sha256();
            output = new GZIPOutputStream(new BufferedOutputStream(new DigestOutputStream(
                    Files.newOutputStream(path, StandardOpenOption.CREATE_NEW), digest)), 65536);
            rows = 0;
            decoded = 0;
        }

        private void finishPart() throws IOException {
            output.close();
            declare(relative, kind, "jsonl.gzip", Files.size(staging.resolve(relative)), decoded, rows, CanonicalJson.hex(digest.digest()));
        }

        @Override
        public void close() throws IOException {
            if (!finished) { finished = true; finishPart(); }
        }
    }

    private void removeStaging() throws IOException {
        // Only this constructor-created staging directory is eligible for cleanup; never follow links.
        plain(staging);
        Files.walkFileTree(staging, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
            @Override public java.nio.file.FileVisitResult postVisitDirectory(Path path, IOException error) throws IOException {
                if (error != null) throw error;
                Files.delete(path);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    /** Check the existing ancestor before creating a directory tree. */
    public static void directory(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path ancestor = absolute;
        while (!Files.exists(ancestor, java.nio.file.LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
        plain(ancestor);
        Files.createDirectories(absolute);
        plain(absolute);
    }

    /** toRealPath resolves junctions too, unlike isSymbolicLink on Windows. */
    public static void plain(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(absolute) || !absolute.equals(absolute.toRealPath())) {
            throw new IOException("Dataset path must not traverse links or junctions: " + path);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        IOException failure = null;
        for (Records writer : writers) {
            try { writer.close(); }
            catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        try { removeStaging(); }
        catch (IOException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        closed = true;
        if (failure != null) throw failure;
    }
}
