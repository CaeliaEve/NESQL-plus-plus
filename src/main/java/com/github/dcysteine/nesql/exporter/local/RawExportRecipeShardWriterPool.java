package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bounds recipe shard file descriptors while preserving one standard gzip member per final shard.
 *
 * <p>Rows first land in appendable, uncompressed spool files. A small access-ordered writer cache
 * keeps hot handlers open and evicts the least recently used writer at the configured hard limit.
 * After streaming succeeds, each spool is compressed exactly once into its final artifact.</p>
 */
final class RawExportRecipeShardWriterPool implements Closeable {
    static final int DEFAULT_MAX_OPEN_WRITERS = 16;

    private static final int WRITER_BUFFER_BYTES = 64 * 1024;
    private final File spoolDir;
    private final File recipeDir;
    private final File stagingDir;
    private final Gson gson;
    private final int maxOpenWriters;
    private final LinkedHashMap<Shard, Writer> openWriters =
            new LinkedHashMap<Shard, Writer>(16, 0.75f, true);

    private int nextSpoolOrdinal;
    private int peakOpenWriters;
    private boolean finished;
    private boolean closed;

    RawExportRecipeShardWriterPool(File rawDir, Gson gson) throws IOException {
        this(rawDir, gson, DEFAULT_MAX_OPEN_WRITERS);
    }

    RawExportRecipeShardWriterPool(File rawDir, Gson gson, int maxOpenWriters) throws IOException {
        if (maxOpenWriters < 1) {
            throw new IllegalArgumentException("maxOpenWriters must be positive");
        }
        this.gson = gson;
        this.maxOpenWriters = maxOpenWriters;
        this.recipeDir = new File(rawDir, "facts/recipes");
        RawExportSidecarFileOps.ensureDirectory(recipeDir);
        this.spoolDir = Files.createTempDirectory(recipeDir.toPath(), ".shard-spool-").toFile();
        this.stagingDir = Files.createTempDirectory(recipeDir.toPath(), ".by-handler-staging-").toFile();
    }

    Shard createShard(String handlerId, String path) throws IOException {
        ensureWritable();
        String spoolName = String.format(java.util.Locale.ROOT, "%08d.jsonl", nextSpoolOrdinal++);
        return new Shard(handlerId, path, new File(spoolDir, spoolName));
    }

    void write(Shard shard, JsonElement element) throws IOException {
        ensureWritable();
        Writer writer = writerFor(shard);
        gson.toJson(element, writer);
        writer.write('\n');
        shard.recipeCount++;
    }

    void finish(Collection<Shard> shards, IndexPublisher indexPublisher) throws IOException {
        ensureWritable();
        closeOpenWriters();
        for (Shard shard : shards) {
            compressToStaging(shard);
        }
        publishAtomically(indexPublisher);
    }

    int peakOpenWritersForTesting() {
        return peakOpenWriters;
    }

    int openWriterCountForTesting() {
        return openWriters.size();
    }

    File spoolDirectoryForTesting() {
        return spoolDir;
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (finished) {
            deleteRecursivelyBestEffort(spoolDir);
            deleteRecursivelyBestEffort(stagingDir);
            return;
        }
        IOException failure = null;
        try {
            closeOpenWriters();
        } catch (IOException e) {
            failure = e;
        }
        try {
            if (spoolDir.exists()) {
                deleteRecursively(spoolDir);
            }
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            if (stagingDir.exists()) {
                deleteRecursively(stagingDir);
            }
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private Writer writerFor(Shard shard) throws IOException {
        Writer writer = openWriters.get(shard);
        if (writer != null) {
            return writer;
        }
        if (openWriters.size() == maxOpenWriters) {
            Iterator<Map.Entry<Shard, Writer>> entries = openWriters.entrySet().iterator();
            Map.Entry<Shard, Writer> eldest = entries.next();
            eldest.getValue().close();
            entries.remove();
        }
        writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(shard.spoolFile, true), StandardCharsets.UTF_8),
                WRITER_BUFFER_BYTES);
        openWriters.put(shard, writer);
        peakOpenWriters = Math.max(peakOpenWriters, openWriters.size());
        return writer;
    }

    private void closeOpenWriters() throws IOException {
        IOException failure = null;
        for (Writer writer : openWriters.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        openWriters.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private void compressToStaging(Shard shard) throws IOException {
        File target = new File(stagingDir, new File(shard.path).getName());
        RawExportSidecarFileOps.gzipRequired(shard.spoolFile, target, shard.handlerId);
    }

    private void publishAtomically(IndexPublisher indexPublisher) throws IOException {
        if (indexPublisher == null) {
            throw new IOException("Recipe shard publication requires an index publisher");
        }
        File finalDir = new File(recipeDir, "by-handler");
        String transactionId = UUID.randomUUID().toString();
        File backupDir = new File(recipeDir, ".by-handler-backup-" + transactionId);
        File index = new File(recipeDir, "index.json");
        File indexBackup = new File(recipeDir, ".index-backup-" + transactionId + ".json");
        boolean oldDirectoryBackedUp = false;
        boolean newDirectoryPublished = false;
        boolean oldIndexBackedUp = false;
        try {
            if (index.exists()) {
                RawExportSidecarFileOps.atomicMove(index, indexBackup);
                oldIndexBackedUp = true;
            }
            if (finalDir.exists()) {
                RawExportSidecarFileOps.atomicMove(finalDir, backupDir);
                oldDirectoryBackedUp = true;
            }
            RawExportSidecarFileOps.atomicMove(stagingDir, finalDir);
            newDirectoryPublished = true;
            indexPublisher.publish();
            finished = true;
        } catch (Exception publicationFailure) {
            IOException failure = publicationFailure instanceof IOException
                    ? (IOException) publicationFailure
                    : new IOException("Recipe shard publication failed", publicationFailure);
            IOException rollbackFailure = rollbackPublication(
                    finalDir,
                    backupDir,
                    index,
                    indexBackup,
                    oldDirectoryBackedUp,
                    newDirectoryPublished,
                    oldIndexBackedUp);
            if (rollbackFailure != null) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }

        deleteRecursivelyBestEffort(backupDir);
        deleteRecursivelyBestEffort(indexBackup);
        deleteRecursivelyBestEffort(spoolDir);
    }

    private IOException rollbackPublication(
            File finalDir,
            File backupDir,
            File index,
            File indexBackup,
            boolean oldDirectoryBackedUp,
            boolean newDirectoryPublished,
            boolean oldIndexBackedUp) {
        IOException failure = null;
        failure = attemptDelete(index, failure);
        if (newDirectoryPublished) {
            failure = attemptDelete(finalDir, failure);
        }
        if (oldDirectoryBackedUp && backupDir.exists()) {
            failure = attemptMove(backupDir, finalDir, failure);
        }
        if (oldIndexBackedUp && indexBackup.exists()) {
            failure = attemptMove(indexBackup, index, failure);
        }
        return failure;
    }

    private static IOException attemptDelete(File file, IOException failure) {
        if (file == null || !file.exists()) {
            return failure;
        }
        try {
            deleteRecursively(file);
        } catch (IOException error) {
            if (failure == null) {
                return error;
            }
            failure.addSuppressed(error);
        }
        return failure;
    }

    private static IOException attemptMove(File source, File target, IOException failure) {
        try {
            RawExportSidecarFileOps.atomicMove(source, target);
        } catch (IOException error) {
            if (failure == null) {
                return error;
            }
            failure.addSuppressed(error);
        }
        return failure;
    }

    private static void deleteRecursivelyBestEffort(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            deleteRecursively(file);
        } catch (IOException ignored) {
            file.deleteOnExit();
        }
    }

    private void ensureWritable() throws IOException {
        if (closed) {
            throw new IOException("Recipe shard writer pool is closed");
        }
        if (finished) {
            throw new IOException("Recipe shard writer pool is already finalized");
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Failed to delete recipe shard spool path: " + file.getAbsolutePath());
        }
    }

    static final class Shard {
        final String handlerId;
        final String path;
        final File spoolFile;
        long recipeCount;

        private Shard(String handlerId, String path, File spoolFile) {
            this.handlerId = handlerId;
            this.path = path;
            this.spoolFile = spoolFile;
        }
    }

    interface IndexPublisher {
        void publish() throws IOException;
    }
}
