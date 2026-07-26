package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Run-scoped immutable raw-export generation published by one atomic pointer replacement.
 *
 * <p>Generation contents are sealed under {@code raw-export/generations/<id>} before
 * {@code raw-export/current.json} changes. Readers therefore observe either the complete previous
 * generation or the complete next generation, including across process crashes.</p>
 */
public final class RawExportGeneration implements AutoCloseable {
    static final String GENERATIONS_DIRECTORY = "generations";
    static final String STAGING_PREFIX = ".staging-";
    static final String CURRENT_POINTER_FILE = "current.json";
    private static final String POINTER_SCHEMA = "nesqlpp/raw-export-generation-pointer/v1";
    private static final String LOCK_FILE = ".publish.lock";
    private static final Gson POINTER_GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File authorityRoot;
    private final File generationsDirectory;
    private final File currentPointerFile;
    private final File stagingDirectory;
    private final String generationId;
    private final PublicationValidator validator;
    private final FileOperations fileOperations;
    private final RandomAccessFile lockFile;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private File sealedDirectory;
    private boolean published;
    private boolean closed;

    public static RawExportGeneration begin(File repositoryDirectory) throws IOException {
        return new RawExportGeneration(
                repositoryDirectory,
                new RawExportGenerationValidator(),
                new NioFileOperations());
    }

    RawExportGeneration(
            File repositoryDirectory,
            PublicationValidator validator,
            FileOperations fileOperations) throws IOException {
        requireRepositoryDirectory(repositoryDirectory);
        if (validator == null || fileOperations == null) {
            throw new IOException("Raw-export generation validator and file operations are required");
        }
        this.validator = validator;
        this.fileOperations = fileOperations;
        this.authorityRoot = RawExportFileCatalog.rawExportRootDirectory(repositoryDirectory);
        RawExportSidecarFileOps.ensureDirectory(authorityRoot);
        this.generationsDirectory = new File(authorityRoot, GENERATIONS_DIRECTORY);
        RawExportSidecarFileOps.ensureDirectory(generationsDirectory);
        this.currentPointerFile = new File(authorityRoot, CURRENT_POINTER_FILE);

        RandomAccessFile acquiredLockFile = null;
        FileChannel acquiredLockChannel = null;
        FileLock acquiredLock = null;
        try {
            acquiredLockFile = new RandomAccessFile(new File(authorityRoot, LOCK_FILE), "rw");
            acquiredLockChannel = acquiredLockFile.getChannel();
            acquiredLock = acquiredLockChannel.tryLock();
            if (acquiredLock == null) {
                throw new IOException(
                        "Another raw-export generation is already active for "
                                + repositoryDirectory.getAbsolutePath());
            }
            this.lockFile = acquiredLockFile;
            this.lockChannel = acquiredLockChannel;
            this.lock = acquiredLock;
        } catch (IOException e) {
            closeQuietly(acquiredLock, acquiredLockChannel, acquiredLockFile);
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(acquiredLock, acquiredLockChannel, acquiredLockFile);
            throw new IOException("Failed to acquire raw-export generation lock", e);
        }

        boolean initialized = false;
        try {
            cleanupUnreferencedGenerations();
            this.generationId = generationId();
            this.stagingDirectory = new File(generationsDirectory, STAGING_PREFIX + generationId);
            RawExportSidecarFileOps.ensureDirectory(stagingDirectory);
            initialized = true;
        } finally {
            if (!initialized) {
                closeQuietly(lock, lockChannel, lockFile);
            }
        }
    }

    public File stagingDirectory() {
        return stagingDirectory;
    }

    public boolean isPublished() {
        return published;
    }

    public static File currentDirectoryOrMissing(File repositoryDirectory) throws IOException {
        File current = resolveCurrentDirectory(repositoryDirectory, false);
        if (current != null) {
            return current;
        }
        return new File(
                new File(RawExportFileCatalog.rawExportRootDirectory(repositoryDirectory), GENERATIONS_DIRECTORY),
                ".none");
    }

    public static File requireCurrentDirectory(File repositoryDirectory) throws IOException {
        File current = resolveCurrentDirectory(repositoryDirectory, true);
        if (current == null) {
            throw new IOException(
                    "Raw-export current generation pointer is missing for "
                            + repositoryDirectory.getAbsolutePath());
        }
        return current;
    }

    public void publish() throws IOException {
        requireOpen();
        validator.validate(stagingDirectory);

        sealedDirectory = new File(generationsDirectory, generationId);
        fileOperations.atomicMoveDirectory(stagingDirectory, sealedDirectory);

        File pointerTemp = new File(authorityRoot, ".current-" + generationId + ".tmp");
        try {
            writePointer(pointerTemp, generationId);
            fileOperations.atomicReplaceFile(pointerTemp, currentPointerFile);
            published = true;
        } finally {
            if (pointerTemp.exists() && !pointerTemp.delete()) {
                pointerTemp.deleteOnExit();
            }
            if (published) {
                releaseLockAfterCommit();
            }
        }
    }

    public void abort() throws IOException {
        if (closed || published) {
            return;
        }
        IOException failure = null;
        for (File candidate : new File[] {stagingDirectory, sealedDirectory}) {
            try {
                if (candidate != null && !isCurrentGeneration(candidate)) {
                    fileOperations.deleteRecursively(candidate);
                }
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        try {
            releaseLock();
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

    @Override
    public void close() throws IOException {
        if (published) {
            releaseLock();
        } else {
            abort();
        }
    }

    private void cleanupUnreferencedGenerations() throws IOException {
        File current = resolveCurrentDirectory(authorityRoot.getParentFile(), false);
        File[] generations = generationsDirectory.listFiles();
        if (generations == null) {
            throw new IOException(
                    "Failed to list raw-export generations directory: "
                            + generationsDirectory.getAbsolutePath());
        }
        for (File generation : generations) {
            if (!generation.isDirectory()) {
                throw new IOException(
                        "Raw-export generations entry is not a directory: "
                                + generation.getAbsolutePath());
            }
            // Crash-interrupted staging directories are forensic artifacts. Preserve them until an
            // explicit, separately authorized cleanup applies the workspace retention policy.
            if (generation.getName().startsWith(STAGING_PREFIX)) {
                continue;
            }
            if (current == null || !sameFile(generation, current)) {
                fileOperations.deleteRecursively(generation);
            }
        }
    }

    private boolean isCurrentGeneration(File candidate) throws IOException {
        File current = resolveCurrentDirectory(authorityRoot.getParentFile(), false);
        return current != null && sameFile(candidate, current);
    }

    private static File resolveCurrentDirectory(File repositoryDirectory, boolean requireDirectory)
            throws IOException {
        File authorityRoot = RawExportFileCatalog.rawExportRootDirectory(repositoryDirectory);
        File pointerFile = new File(authorityRoot, CURRENT_POINTER_FILE);
        if (!pointerFile.exists()) {
            return null;
        }
        if (!pointerFile.isFile() || Files.isSymbolicLink(pointerFile.toPath())) {
            throw new IOException(
                    "Raw-export current pointer is not a regular file: "
                            + pointerFile.getAbsolutePath());
        }
        JsonObject pointer = readJsonObject(pointerFile);
        String schemaVersion = stringMember(pointer, "schemaVersion", pointerFile);
        String generationId = stringMember(pointer, "generationId", pointerFile);
        String relativePath = stringMember(pointer, "relativePath", pointerFile);
        if (!POINTER_SCHEMA.equals(schemaVersion)) {
            throw new IOException("Unsupported raw-export current pointer schema: " + schemaVersion);
        }
        requireGenerationId(generationId);
        String expectedPath = GENERATIONS_DIRECTORY + "/" + generationId;
        if (!expectedPath.equals(relativePath)) {
            throw new IOException(
                    "Raw-export current pointer path does not match generation: " + relativePath);
        }
        File generation = new File(authorityRoot, relativePath.replace('/', File.separatorChar));
        File normalizedRoot = new File(authorityRoot, GENERATIONS_DIRECTORY).getCanonicalFile();
        File normalizedGeneration = generation.getCanonicalFile();
        if (!normalizedGeneration.getParentFile().equals(normalizedRoot)) {
            throw new IOException(
                    "Raw-export current pointer escapes generations root: "
                            + normalizedGeneration.getAbsolutePath());
        }
        if (requireDirectory && (!normalizedGeneration.exists() || !normalizedGeneration.isDirectory())) {
            throw new IOException(
                    "Raw-export current generation is missing: "
                            + normalizedGeneration.getAbsolutePath());
        }
        return normalizedGeneration;
    }

    private static void writePointer(File file, String generationId) throws IOException {
        JsonObject pointer = new JsonObject();
        pointer.addProperty("schemaVersion", POINTER_SCHEMA);
        pointer.addProperty("generationId", generationId);
        pointer.addProperty("relativePath", GENERATIONS_DIRECTORY + "/" + generationId);
        pointer.addProperty("publishedAtEpochMs", System.currentTimeMillis());
        try (FileOutputStream output = new FileOutputStream(file, false);
             OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            POINTER_GSON.toJson(pointer, writer);
            writer.flush();
            output.getFD().sync();
        }
    }

    private static JsonObject readJsonObject(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            if (element == null || !element.isJsonObject()) {
                throw new IOException("Raw-export current pointer is not a JSON object: " + file.getAbsolutePath());
            }
            return element.getAsJsonObject();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to parse raw-export current pointer: " + file.getAbsolutePath(), e);
        }
    }

    private static String stringMember(JsonObject object, String key, File file) throws IOException {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            throw new IOException("Raw-export current pointer is missing " + key + ": " + file.getAbsolutePath());
        }
        try {
            return element.getAsString();
        } catch (Exception e) {
            throw new IOException("Raw-export current pointer has invalid " + key + ": " + file.getAbsolutePath(), e);
        }
    }

    private static void requireRepositoryDirectory(File repositoryDirectory) throws IOException {
        if (repositoryDirectory == null) {
            throw new IOException("Raw-export generation repository directory must not be null");
        }
        if (!repositoryDirectory.exists() && !repositoryDirectory.mkdirs() && !repositoryDirectory.isDirectory()) {
            throw new IOException("Failed to create raw-export generation repository: " + repositoryDirectory.getAbsolutePath());
        }
        if (!repositoryDirectory.isDirectory()) {
            throw new IOException("Raw-export generation repository is not a directory: " + repositoryDirectory.getAbsolutePath());
        }
    }

    private void requireOpen() throws IOException {
        if (closed || published) {
            throw new IOException("Raw-export generation is not open for publication");
        }
        if (!stagingDirectory.exists() || !stagingDirectory.isDirectory()) {
            throw new IOException("Raw-export staging generation is missing: " + stagingDirectory.getAbsolutePath());
        }
    }

    private void releaseLockAfterCommit() {
        try {
            releaseLock();
        } catch (IOException ignored) {
            // The atomic pointer commit is already durable.
        }
    }

    private void releaseLock() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException e) {
            failure = e;
        }
        try {
            lockChannel.close();
        } catch (IOException e) {
            failure = merge(failure, e);
        }
        try {
            lockFile.close();
        } catch (IOException e) {
            failure = merge(failure, e);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static IOException merge(IOException primary, IOException next) {
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }

    private static String generationId() {
        return String.format(
                "%013d-%s",
                System.currentTimeMillis(),
                UUID.randomUUID().toString().replace("-", ""));
    }

    private static void requireGenerationId(String value) throws IOException {
        if (value == null || !value.matches("[a-z0-9][a-z0-9-]{8,80}")) {
            throw new IOException("Invalid raw-export generation id: " + value);
        }
    }

    private static boolean sameFile(File left, File right) throws IOException {
        return left.getCanonicalFile().equals(right.getCanonicalFile());
    }

    private static void closeQuietly(FileLock lock, FileChannel channel, RandomAccessFile file) {
        try {
            if (lock != null && lock.isValid()) {
                lock.release();
            }
        } catch (Exception ignored) {
            // Preserve the acquisition failure.
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (Exception ignored) {
            // Preserve the acquisition failure.
        }
        try {
            if (file != null) {
                file.close();
            }
        } catch (Exception ignored) {
            // Preserve the acquisition failure.
        }
    }

    interface PublicationValidator {
        void validate(File stagingDirectory) throws IOException;
    }

    interface FileOperations {
        void atomicMoveDirectory(File source, File target) throws IOException;

        void atomicReplaceFile(File source, File target) throws IOException;

        void deleteRecursively(File file) throws IOException;
    }

    private static final class NioFileOperations implements FileOperations {
        @Override
        public void atomicMoveDirectory(File source, File target) throws IOException {
            if (target.exists()) {
                throw new IOException("Raw-export generation target already exists: " + target.getAbsolutePath());
            }
            atomicMove(source, target, false);
        }

        @Override
        public void atomicReplaceFile(File source, File target) throws IOException {
            atomicMove(source, target, true);
        }

        private void atomicMove(File source, File target, boolean replace) throws IOException {
            try {
                if (replace) {
                    Files.move(
                            source.toPath(),
                            target.toPath(),
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException(
                        "Raw-export publication requires same-filesystem atomic moves: "
                                + source.getAbsolutePath()
                                + " -> "
                                + target.getAbsolutePath(),
                        e);
            }
        }

        @Override
        public void deleteRecursively(File file) throws IOException {
            if (file == null || !file.exists()) {
                return;
            }
            if (Files.isSymbolicLink(file.toPath())) {
                Files.delete(file.toPath());
                return;
            }
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children == null) {
                    throw new IOException("Failed to list raw-export generation path: " + file.getAbsolutePath());
                }
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
            Files.delete(file.toPath());
        }
    }
}
