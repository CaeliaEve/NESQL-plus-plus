package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Behavioral and crash-window regression coverage for pointer-published raw-export generations. */
public final class RawExportGenerationTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RawExportGenerationTest() {}

    public static void main(String[] args) throws Exception {
        RawExportUiCaptureKeyContractTest.runAll();
        File root = Files.createTempDirectory("nesql-raw-generation-").toFile();
        try {
            assertObsoleteLegacyManifestDoesNotBlockFreshGeneration(new File(root, "legacy-mismatch"));
            assertBlockedGateDiagnosticsIdentifyRootCause(new File(root, "blocked-gate-diagnostics"));
            assertAtomicPointerPublicationAndBoundedCleanup(new File(root, "success"));
            assertCrashInterruptedStagingIsPreserved(new File(root, "stale-staging"));
            assertValidationFailureLeavesOldPointerReadable(new File(root, "validation-failure"));
            assertPointerReplacementFailureLeavesOldGenerationReadable(new File(root, "pointer-failure"));
        } finally {
            deleteRecursively(root);
        }
    }

    private static void assertBlockedGateDiagnosticsIdentifyRootCause(File repository)
            throws Exception {
        File rawDir = RawExportFileCatalog.rawExportRootDirectory(repository);
        writePublishableGeneration(rawDir, "blocked-gate", "blocked", "ready");
        File reportFile = new File(rawDir, RawExportFileCatalog.EXPORT_REPORT_FILE);
        JsonObject report = new JsonParser()
                .parse(new String(Files.readAllBytes(reportFile.toPath()), StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonObject validation = report.getAsJsonObject("validation");
        JsonArray gates = new JsonArray();
        JsonObject gate = new JsonObject();
        gate.addProperty("name", "angelica-render-facts");
        gate.addProperty("status", "blocked");
        gate.addProperty("summary", "spritesMissingTiming=1382");
        gates.add(gate);
        validation.add("gates", gates);
        writeJson(reportFile, report);

        assertIOExceptionContaining(
                "blocked gate diagnostics identify the root cause",
                "angelica-render-facts=blocked (spritesMissingTiming=1382)",
                new IoAction() {
                    @Override
                    public void run() throws Exception {
                        new RawExportGenerationValidator().validate(rawDir);
                    }
                });
    }

    private static void assertObsoleteLegacyManifestDoesNotBlockFreshGeneration(File repository)
            throws Exception {
        File legacyAuthority = RawExportFileCatalog.rawExportRootDirectory(repository);
        writePublishableGeneration(legacyAuthority, "legacy-direct", "ready", "ready");
        File legacyManifest = new File(legacyAuthority, RawExportFileCatalog.MANIFEST_FILE);
        JsonObject manifest = new JsonParser()
                .parse(new String(Files.readAllBytes(legacyManifest.toPath()), StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (int index = 0; index < 11; index++) {
            String path = "obsolete/catalog-" + index + ".json";
            manifest.getAsJsonObject("files").addProperty("obsoleteCatalog" + index, path);
            writeJson(new File(legacyAuthority, path), new JsonObject());
        }
        writeJson(legacyManifest, manifest);
        byte[] originalManifest = Files.readAllBytes(legacyManifest.toPath());

        assertIOExceptionContaining(
                "legacy fixture has the production catalog mismatch",
                "manifest file catalog size mismatch",
                new IoAction() {
                    @Override
                    public void run() throws Exception {
                        new RawExportGenerationValidator().validate(legacyAuthority);
                    }
                });

        File pointer = new File(legacyAuthority, RawExportGeneration.CURRENT_POINTER_FILE);
        assertTrue("legacy direct authority starts without generation pointer", !pointer.exists());

        RawExportGeneration failed = RawExportGeneration.begin(repository);
        assertTrue("obsolete legacy manifest does not block fresh staging", failed.stagingDirectory().isDirectory());
        assertTrue("begin does not promote legacy direct authority", !pointer.exists());
        assertEquals("legacy direct files remain readable while staging", "legacy-direct", readMarker(legacyAuthority));
        assertBytesEqual("begin leaves legacy manifest unchanged", originalManifest, Files.readAllBytes(legacyManifest.toPath()));
        writePublishableGeneration(failed.stagingDirectory(), "invalid-fresh", "blocked", "ready");
        try {
            assertIOException("invalid fresh generation remains fail-closed", new IoAction() {
                @Override
                public void run() throws Exception {
                    failed.publish();
                }
            });
            assertTrue("failed publish does not create a generation pointer", !pointer.exists());
            assertEquals("failed publish leaves legacy authority readable", "legacy-direct", readMarker(legacyAuthority));
        } finally {
            failed.abort();
        }
        assertEquals("abort removes only failed staging", 0, countStagingGenerations(repository));
        assertBytesEqual("abort leaves legacy manifest unchanged", originalManifest, Files.readAllBytes(legacyManifest.toPath()));

        RawExportGeneration valid = RawExportGeneration.begin(repository);
        writePublishableGeneration(valid.stagingDirectory(), "fresh-current", "ready", "ready");
        assertTrue("valid staging is not visible before publish", !pointer.exists());
        valid.publish();

        assertTrue("valid generation publishes the first pointer", pointer.isFile());
        assertEquals("new valid generation becomes current", "fresh-current", readMarker(RawExportGeneration.requireCurrentDirectory(repository)));
        assertEquals("published generation does not overwrite legacy files", "legacy-direct", readMarker(legacyAuthority));
        assertBytesEqual("published generation leaves legacy manifest unchanged", originalManifest, Files.readAllBytes(legacyManifest.toPath()));
        assertEquals("only the valid sealed generation is retained", 1, countSealedGenerations(repository));
    }

    private static void assertAtomicPointerPublicationAndBoundedCleanup(File repository) throws Exception {
        publishValid(repository, "old-v1", "ready", "ready");
        File oldCurrent = RawExportGeneration.requireCurrentDirectory(repository);
        assertEquals("initial pointer generation", "old-v1", readMarker(oldCurrent));

        RawExportGeneration next = RawExportGeneration.begin(repository);
        writePublishableGeneration(next.stagingDirectory(), "new-v2", "ready", "ready-with-warnings");
        assertEquals(
                "pointer remains on old generation while next generation is staged",
                "old-v1",
                readMarker(RawExportGeneration.requireCurrentDirectory(repository)));
        next.publish();
        File newCurrent = RawExportGeneration.requireCurrentDirectory(repository);
        assertEquals("pointer switches to complete new generation", "new-v2", readMarker(newCurrent));
        assertTrue("new current has complete manifest", new File(newCurrent, RawExportFileCatalog.MANIFEST_FILE).isFile());
        assertTrue("old immutable generation remains readable after switch", oldCurrent.isDirectory());
        assertEquals("old immutable generation contents stay unchanged", "old-v1", readMarker(oldCurrent));
        assertEquals("current plus previous generation retained", 2, countSealedGenerations(repository));

        RawExportGeneration third = RawExportGeneration.begin(repository);
        assertEquals("next begin removes unreferenced previous generation", 1, countSealedGenerations(repository));
        writePublishableGeneration(third.stagingDirectory(), "new-v3", "ready", "ready");
        third.publish();
        assertEquals("third generation becomes current", "new-v3", readMarker(RawExportGeneration.requireCurrentDirectory(repository)));
        assertEquals("bounded history retains current and immediate previous", 2, countSealedGenerations(repository));
        assertEquals("no staging generation remains after publish", 0, countStagingGenerations(repository));
    }

    private static void assertValidationFailureLeavesOldPointerReadable(File repository) throws Exception {
        publishValid(repository, "stable-old", "ready", "ready");
        File oldCurrent = RawExportGeneration.requireCurrentDirectory(repository);
        RawExportGeneration generation = RawExportGeneration.begin(repository);
        writePublishableGeneration(generation.stagingDirectory(), "invalid-new", "blocked", "ready");
        try {
            assertIOException("blocked generation validation", new IoAction() {
                @Override
                public void run() throws Exception {
                    generation.publish();
                }
            });
            assertEquals(
                    "validation failure leaves pointer on old generation",
                    oldCurrent.getCanonicalFile(),
                    RawExportGeneration.requireCurrentDirectory(repository).getCanonicalFile());
            assertEquals("old generation remains readable", "stable-old", readMarker(oldCurrent));
        } finally {
            generation.abort();
        }
        assertEquals("failed staging generation is deleted", 0, countStagingGenerations(repository));
    }

    private static void assertCrashInterruptedStagingIsPreserved(File repository) throws Exception {
        publishValid(repository, "stable-current", "ready", "ready");
        File stale = new File(
                generationsDirectory(repository),
                RawExportGeneration.STAGING_PREFIX + "crash-interrupted-forensic-artifact");
        writeText(new File(stale, "debug/checkpoint.json"), "crash-evidence");

        RawExportGeneration next = RawExportGeneration.begin(repository);
        try {
            assertTrue("next generation preserves crash-interrupted staging", stale.isDirectory());
            assertEquals("stale plus active staging coexist", 2, countStagingGenerations(repository));
            assertEquals(
                    "stale forensic bytes remain unchanged",
                    "crash-evidence",
                    new String(
                            Files.readAllBytes(new File(stale, "debug/checkpoint.json").toPath()),
                            StandardCharsets.UTF_8));
        } finally {
            next.abort();
        }

        assertTrue("aborting the active generation does not delete stale staging", stale.isDirectory());
        assertEquals("only stale staging remains", 1, countStagingGenerations(repository));
    }

    private static void assertPointerReplacementFailureLeavesOldGenerationReadable(File repository)
            throws Exception {
        publishValid(repository, "pointer-old", "ready", "ready");
        File oldCurrent = RawExportGeneration.requireCurrentDirectory(repository);
        FailPointerReplaceFileOperations operations = new FailPointerReplaceFileOperations();
        RawExportGeneration generation = new RawExportGeneration(
                repository,
                new RawExportGeneration.PublicationValidator() {
                    @Override
                    public void validate(File stagingDirectory) {}
                },
                operations);
        writeText(new File(generation.stagingDirectory(), "generation.txt"), "pointer-new");
        try {
            assertEquals(
                    "old pointer readable before replacement",
                    "pointer-old",
                    readMarker(RawExportGeneration.requireCurrentDirectory(repository)));
            assertIOException("injected pointer replacement failure", new IoAction() {
                @Override
                public void run() throws Exception {
                    generation.publish();
                }
            });
            assertEquals("new generation was sealed before pointer attempt", 1, operations.directoryMoves);
            assertEquals("one pointer replacement was attempted", 1, operations.pointerReplacements);
            assertEquals(
                    "failed pointer replacement keeps exact old generation current",
                    oldCurrent.getCanonicalFile(),
                    RawExportGeneration.requireCurrentDirectory(repository).getCanonicalFile());
            assertEquals("old generation stays readable through crash window", "pointer-old", readMarker(oldCurrent));
        } finally {
            generation.abort();
        }
        assertEquals("unreferenced sealed generation is removed on abort", 1, countSealedGenerations(repository));
    }

    private static void publishValid(
            File repository,
            String marker,
            String rawReadiness,
            String compileReadiness) throws Exception {
        RawExportGeneration generation = RawExportGeneration.begin(repository);
        boolean published = false;
        try {
            writePublishableGeneration(
                    generation.stagingDirectory(), marker, rawReadiness, compileReadiness);
            generation.publish();
            published = true;
        } finally {
            if (!published) {
                generation.abort();
            }
        }
    }

    private static void writePublishableGeneration(
            File rawDir,
            String marker,
            String rawReadiness,
            String compileReadiness) throws Exception {
        writeText(new File(rawDir, "generation.txt"), marker);

        Map<String, String> manifestFiles = new LinkedHashMap<String, String>();
        RawExportManifestBuilder.putManifestFiles(manifestFiles, false, false);
        for (String path : manifestFiles.values()) {
            writeDefaultArtifact(new File(rawDir, path.replace('/', File.separatorChar)));
        }

        JsonObject manifest = new JsonObject();
        manifest.addProperty("schemaVersion", "nesqlpp/raw-export/alpha1");
        manifest.addProperty("generatedAt", "2026-07-12T00:00:00Z");
        JsonObject manifestFileObject = new JsonObject();
        for (Map.Entry<String, String> entry : manifestFiles.entrySet()) {
            manifestFileObject.addProperty(entry.getKey(), entry.getValue());
        }
        manifest.add("files", manifestFileObject);
        writeJson(new File(rawDir, RawExportFileCatalog.MANIFEST_FILE), manifest);

        JsonObject rawReport = new JsonObject();
        JsonObject rawValidation = new JsonObject();
        rawValidation.addProperty("readinessStatus", rawReadiness);
        rawReport.add("validation", rawValidation);
        writeJson(new File(rawDir, RawExportFileCatalog.EXPORT_REPORT_FILE), rawReport);
        writeJson(new File(rawDir, RawExportFileCatalog.VALIDATION_EXPORT_REPORT_FILE), rawReport);

        JsonObject health = new JsonObject();
        health.addProperty("compileReadinessStatus", compileReadiness);
        writeJson(
                new File(rawDir, RawExportFileCatalog.validationPath(
                        RawExportFileCatalog.EXPORT_VALIDATION_REPORT_FILE_NAME)),
                health);
        writeJson(
                new File(rawDir, RawExportFileCatalog.validationPath(
                        RawExportFileCatalog.EXPORT_HEALTH_REPORT_FILE_NAME)),
                health);

        File recipeShard = new File(rawDir, "facts/recipes/by-handler/fixture.jsonl.gz");
        writeGzipJsonl(recipeShard, "{\"recipeId\":\"fixture\"}");
        JsonObject recipeIndex = new JsonObject();
        recipeIndex.addProperty("schemaVersion", "nesqlpp/raw-export/alpha1/recipe-index");
        recipeIndex.addProperty("strategy", "by-handler");
        recipeIndex.addProperty("recipeCount", 1);
        recipeIndex.addProperty("shardCount", 1);
        JsonArray shards = new JsonArray();
        JsonObject shard = new JsonObject();
        shard.addProperty("handlerId", "fixture");
        shard.addProperty("path", "facts/recipes/by-handler/fixture.jsonl.gz");
        shard.addProperty("recipeCount", 1);
        shards.add(shard);
        recipeIndex.add("shards", shards);
        writeJson(new File(rawDir, RawExportFileCatalog.RECIPE_INDEX_FILE), recipeIndex);

        writeJson(new File(rawDir, "control/index.json"), new JsonObject());
        writeJson(new File(rawDir, "debug/trace/latest.json"), new JsonObject());
        writeJson(
                RawExportReportArtifactCatalog.sizeReportFile(rawDir),
                RawExportSizeReportBuilder.build(
                        "nesqlpp/raw-export/alpha1",
                        "2026-07-12T00:00:00Z",
                        rawDir));
        writeIntegrityCatalog(rawDir);
        RawExportGenerationFinalizer.finalizeGeneration(rawDir);
    }

    private static void writeDefaultArtifact(File file) throws Exception {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".jsonl.gz")) {
            writeGzipJsonl(file, "{}");
        } else if (name.endsWith(".json.gz")) {
            writeGzipJson(file, "{}");
        } else if (name.endsWith(".jsonl")) {
            writeText(file, "");
        } else if (name.endsWith(".json")) {
            writeJson(file, new JsonObject());
        } else {
            writeText(file, "fixture");
        }
    }

    private static void writeIntegrityCatalog(File rawDir) throws Exception {
        JsonArray artifacts = new JsonArray();
        addFileArtifact(artifacts, rawDir, RawExportFileCatalog.MANIFEST_FILE, "raw-manifest");
        addFileArtifact(artifacts, rawDir, RawExportFileCatalog.EXPORT_REPORT_FILE, "raw-report");
        for (String path : Arrays.asList("facts", "assets", "models", "special", "control", "debug")) {
            addDirectoryArtifact(artifacts, rawDir, path, "raw-" + path);
        }
        JsonObject checksums = new JsonObject();
        checksums.addProperty("schemaVersion", "nesqlpp/stage-checksums/v1");
        checksums.add("artifacts", artifacts);
        JsonObject exportManifest = new JsonObject();
        exportManifest.addProperty("schemaVersion", "nesqlpp/export-manifest/v1");
        JsonArray manifestArtifacts = new JsonArray();
        for (int index = 0; index < artifacts.size(); index++) {
            manifestArtifacts.add(artifacts.get(index));
        }
        exportManifest.add("artifacts", manifestArtifacts);
        writeJson(
                new File(rawDir, RawExportFileCatalog.validationPath(
                        RawExportFileCatalog.STAGE_CHECKSUMS_FILE_NAME)),
                checksums);
        writeJson(
                new File(rawDir, RawExportFileCatalog.validationPath(
                        RawExportFileCatalog.EXPORT_MANIFEST_FILE_NAME)),
                exportManifest);
    }

    private static void addFileArtifact(
            JsonArray artifacts,
            File rawDir,
            String relativePath,
            String stage) throws Exception {
        File file = new File(rawDir, relativePath.replace('/', File.separatorChar));
        JsonObject artifact = artifact(stage, relativePath);
        artifact.addProperty("exists", true);
        artifact.addProperty("bytes", file.length());
        artifact.addProperty("fileCount", 0);
        artifact.addProperty("sha256", sha256(file));
        artifacts.add(artifact);
    }

    private static void addDirectoryArtifact(
            JsonArray artifacts,
            File rawDir,
            String relativePath,
            String stage) throws Exception {
        File directory = new File(rawDir, relativePath.replace('/', File.separatorChar));
        DirectoryStats stats = new DirectoryStats();
        collectDirectoryStats(directory, directory, stats);
        JsonObject artifact = artifact(stage, relativePath);
        artifact.addProperty("exists", true);
        artifact.addProperty("bytes", stats.bytes);
        artifact.addProperty("fileCount", stats.fileCount);
        artifact.addProperty("sha256", stats.digest());
        artifacts.add(artifact);
    }

    private static JsonObject artifact(String stage, String relativePath) {
        JsonObject artifact = new JsonObject();
        artifact.addProperty("stage", stage);
        artifact.addProperty("family", stage);
        artifact.addProperty("path", "raw-export/" + relativePath);
        return artifact;
    }

    private static void writeJson(File file, JsonObject value) throws Exception {
        ensureParent(file);
        try (FileOutputStream output = new FileOutputStream(file, false);
             OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            GSON.toJson(value, writer);
        }
    }

    private static void writeGzipJsonl(File file, String... rows) throws Exception {
        ensureParent(file);
        try (FileOutputStream output = new FileOutputStream(file, false);
             GZIPOutputStream gzip = new GZIPOutputStream(output);
             OutputStreamWriter writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
            for (String row : rows) {
                writer.write(row);
                writer.write('\n');
            }
        }
    }

    private static void writeGzipJson(File file, String json) throws Exception {
        ensureParent(file);
        try (FileOutputStream output = new FileOutputStream(file, false);
             GZIPOutputStream gzip = new GZIPOutputStream(output);
             OutputStreamWriter writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {
            writer.write(json);
        }
    }

    private static void writeText(File file, String value) throws Exception {
        ensureParent(file);
        Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
    }

    private static void ensureParent(File file) throws IOException {
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Failed to create test directory: " + parent.getAbsolutePath());
        }
    }

    private static String readMarker(File directory) throws Exception {
        return new String(
                Files.readAllBytes(new File(directory, "generation.txt").toPath()),
                StandardCharsets.UTF_8);
    }

    private static int countSealedGenerations(File repository) {
        File generations = generationsDirectory(repository);
        File[] matches = generations.listFiles((dir, name) -> !name.startsWith(RawExportGeneration.STAGING_PREFIX));
        return matches == null ? 0 : matches.length;
    }

    private static int countStagingGenerations(File repository) {
        File[] matches = generationsDirectory(repository).listFiles(
                (dir, name) -> name.startsWith(RawExportGeneration.STAGING_PREFIX));
        return matches == null ? 0 : matches.length;
    }

    private static File generationsDirectory(File repository) {
        return new File(
                RawExportFileCatalog.rawExportRootDirectory(repository),
                RawExportGeneration.GENERATIONS_DIRECTORY);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[1024 * 1024];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return toHex(digest.digest());
    }

    private static void collectDirectoryStats(File root, File file, DirectoryStats stats)
            throws Exception {
        if (file.isFile()) {
            stats.fileCount++;
            stats.bytes += file.length();
            stats.digest.update(
                    (relative(root, file) + ":" + file.length() + "\n")
                            .getBytes(StandardCharsets.UTF_8));
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            throw new IOException("Failed to list test directory: " + file.getAbsolutePath());
        }
        Arrays.sort(children, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        for (File child : children) {
            collectDirectoryStats(root, child, stats);
        }
    }

    private static String relative(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private static void assertIOException(String label, IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + ": expected IOException");
        } catch (IOException expected) {
            // Expected fail-closed path.
        }
    }

    private static void assertIOExceptionContaining(
            String label,
            String expectedMessage,
            IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + ": expected IOException");
        } catch (IOException expected) {
            if (expected.getMessage() == null || !expected.getMessage().contains(expectedMessage)) {
                throw new AssertionError(
                        label + ": expected message containing " + expectedMessage
                                + ", actual=" + expected.getMessage(),
                        expected);
            }
        }
    }

    private static void assertTrue(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertBytesEqual(String label, byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(label);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Failed to list test path: " + file.getAbsolutePath());
            }
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        Files.delete(file.toPath());
    }

    private interface IoAction {
        void run() throws Exception;
    }

    private static final class DirectoryStats {
        long bytes;
        int fileCount;
        final MessageDigest digest;

        DirectoryStats() throws Exception {
            digest = MessageDigest.getInstance("SHA-256");
        }

        String digest() {
            return toHex(digest.digest());
        }
    }

    private static final class FailPointerReplaceFileOperations
            implements RawExportGeneration.FileOperations {
        int directoryMoves;
        int pointerReplacements;

        @Override
        public void atomicMoveDirectory(File source, File target) throws IOException {
            directoryMoves++;
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        }

        @Override
        public void atomicReplaceFile(File source, File target) throws IOException {
            pointerReplacements++;
            throw new IOException("injected current pointer replacement failure");
        }

        @Override
        public void deleteRecursively(File file) throws IOException {
            RawExportGenerationTest.deleteRecursively(file);
        }
    }
}
