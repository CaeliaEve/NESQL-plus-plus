package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalFluid;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Behavioral and resource-bound regression coverage for repository fact writers. */
public final class RawExportRepositoryFactWriterTest {
    private RawExportRepositoryFactWriterTest() {}

    public static void main(String[] args) throws Exception {
        File rawDir = Files.createTempDirectory("nesql-repository-writers-").toFile();
        try {
            assertTypedDirectJsonl(rawDir);
            assertBoundedRecipeShardWriters(rawDir);
            assertPublicationRollbackPreservesOldAuthority(rawDir);
            assertCompressionFailureDoesNotTouchOldAuthority(rawDir);
        } finally {
            deleteRecursively(rawDir);
        }
    }

    private static void assertTypedDirectJsonl(File rawDir) throws Exception {
        File direct = new File(rawDir, "typed-direct.jsonl.gz");
        File legacyTree = new File(rawDir, "typed-tree.jsonl.gz");
        Gson gson = new GsonBuilder().serializeNulls().create();
        CanonicalItem item = new CanonicalItem();
        item.itemId = "i~fixture~\u4e2d\u95f4~0";
        item.modId = "fixture";
        item.internalName = "quoted\"item";
        item.localizedName = "Line\nBreak";
        item.damage = 0;
        item.maxStackSize = 64;
        item.nbtDescriptor = null;
        Map<String, Integer> tools = new LinkedHashMap<String, Integer>();
        tools.put("wrench", 2);
        tools.put("hammer", 1);
        item.toolClasses = tools;
        CanonicalFluid fluid = new CanonicalFluid();
        fluid.fluidId = "f~fixture~steam";
        fluid.localizedName = "Steam \u6c34\u84b8\u6c14";
        fluid.temperature = 373;
        fluid.renderAssetRef = null;

        try (RawExportJsonlWriter writer = new RawExportJsonlWriter(direct, gson)) {
            writer.write(item, CanonicalItem.class);
            writer.write(fluid, CanonicalFluid.class);
        }
        try (RawExportJsonlWriter writer = new RawExportJsonlWriter(legacyTree, gson)) {
            writer.write(gson.toJsonTree(item, CanonicalItem.class));
            writer.write(gson.toJsonTree(fluid, CanonicalFluid.class));
        }

        assertEquals(
                "typed direct serialization matches legacy JsonElement bytes",
                readGzipLines(legacyTree),
                readGzipLines(direct));
    }

    private static void assertBoundedRecipeShardWriters(File rawDir) throws Exception {
        Gson gson = new GsonBuilder().serializeNulls().create();
        RawExportRecipeShardWriterPool pool = new RawExportRecipeShardWriterPool(rawDir, gson, 2);
        File spoolDir = pool.spoolDirectoryForTesting();
        RawExportRecipeShardWriterPool.Shard alpha =
                pool.createShard("alpha", "facts/recipes/by-handler/alpha.jsonl.gz");
        RawExportRecipeShardWriterPool.Shard beta =
                pool.createShard("beta", "facts/recipes/by-handler/beta.jsonl.gz");
        RawExportRecipeShardWriterPool.Shard gamma =
                pool.createShard("gamma", "facts/recipes/by-handler/gamma.jsonl.gz");
        try {
            pool.write(alpha, recipe("alpha-1"));
            pool.write(beta, recipe("beta-1"));
            pool.write(gamma, recipe("gamma-1"));
            pool.write(alpha, recipe("alpha-2"));
            pool.write(beta, recipe("beta-2"));
            pool.write(gamma, recipe("gamma-2"));

            assertEquals("hard writer limit", 2, pool.peakOpenWritersForTesting());
            assertTrue("current writer count remains bounded", pool.openWriterCountForTesting() <= 2);
            pool.finish(Arrays.asList(alpha, beta, gamma), new RawExportRecipeShardWriterPool.IndexPublisher() {
                @Override
                public void publish() throws java.io.IOException {
                    RawExportSidecarFileOps.writeJsonAtomically(
                            gson,
                            new File(rawDir, "facts/recipes/index.json"),
                            recipe("index"));
                }
            });
            assertEquals("writers closed before final compression", 0, pool.openWriterCountForTesting());
            assertTrue("spool directory removed after finalize", !spoolDir.exists());

            assertShard(rawDir, alpha, "alpha-1", "alpha-2");
            assertShard(rawDir, beta, "beta-1", "beta-2");
            assertShard(rawDir, gamma, "gamma-1", "gamma-2");
            assertIOException("write after finish", new IoAction() {
                @Override
                public void run() throws Exception {
                    pool.write(alpha, recipe("late"));
                }
            });
            assertIOException("repeat finish", new IoAction() {
                @Override
                public void run() throws Exception {
                    pool.finish(Arrays.asList(alpha), new RawExportRecipeShardWriterPool.IndexPublisher() {
                        @Override
                        public void publish() {}
                    });
                }
            });
        } finally {
            pool.close();
        }
    }

    private static void assertPublicationRollbackPreservesOldAuthority(File rawDir) throws Exception {
        File recipeDir = new File(rawDir, "facts/recipes");
        File finalDir = new File(recipeDir, "by-handler");
        deleteRecursively(finalDir);
        finalDir.mkdirs();
        File oldShard = new File(finalDir, "old.jsonl.gz");
        File index = new File(recipeDir, "index.json");
        Files.write(oldShard.toPath(), "old-shard".getBytes(StandardCharsets.UTF_8));
        Files.write(index.toPath(), "old-index".getBytes(StandardCharsets.UTF_8));

        Gson gson = new GsonBuilder().serializeNulls().create();
        RawExportRecipeShardWriterPool pool = new RawExportRecipeShardWriterPool(rawDir, gson, 2);
        File spoolDir = pool.spoolDirectoryForTesting();
        RawExportRecipeShardWriterPool.Shard replacement =
                pool.createShard("replacement", "facts/recipes/by-handler/replacement.jsonl.gz");
        pool.write(replacement, recipe("replacement"));
        try {
            assertIOException("index publication rollback", new IoAction() {
                @Override
                public void run() throws Exception {
                    pool.finish(Arrays.asList(replacement), new RawExportRecipeShardWriterPool.IndexPublisher() {
                        @Override
                        public void publish() throws java.io.IOException {
                            throw new java.io.IOException("injected index failure");
                        }
                    });
                }
            });
        } finally {
            pool.close();
        }
        assertEquals("old shard restored", "old-shard", readUtf8(oldShard));
        assertEquals("old index restored", "old-index", readUtf8(index));
        assertTrue("replacement shard absent after rollback", !new File(finalDir, "replacement.jsonl.gz").exists());
        assertTrue("spool removed after rollback", !spoolDir.exists());
    }

    private static void assertCompressionFailureDoesNotTouchOldAuthority(File rawDir) throws Exception {
        File recipeDir = new File(rawDir, "facts/recipes");
        File finalDir = new File(recipeDir, "by-handler");
        deleteRecursively(finalDir);
        finalDir.mkdirs();
        File oldShard = new File(finalDir, "old.jsonl.gz");
        File index = new File(recipeDir, "index.json");
        Files.write(oldShard.toPath(), "old-compression-shard".getBytes(StandardCharsets.UTF_8));
        Files.write(index.toPath(), "old-compression-index".getBytes(StandardCharsets.UTF_8));

        Gson gson = new GsonBuilder().serializeNulls().create();
        RawExportRecipeShardWriterPool pool = new RawExportRecipeShardWriterPool(rawDir, gson, 1);
        RawExportRecipeShardWriterPool.Shard missingSpool =
                pool.createShard("missing", "facts/recipes/by-handler/missing.jsonl.gz");
        try {
            assertIOException("compression failure", new IoAction() {
                @Override
                public void run() throws Exception {
                    pool.finish(Arrays.asList(missingSpool), new RawExportRecipeShardWriterPool.IndexPublisher() {
                        @Override
                        public void publish() {}
                    });
                }
            });
        } finally {
            pool.close();
        }
        assertEquals("old shard untouched before publication", "old-compression-shard", readUtf8(oldShard));
        assertEquals("old index untouched before publication", "old-compression-index", readUtf8(index));
    }

    private static JsonObject recipe(String id) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("recipeId", id);
        return recipe;
    }

    private static void assertShard(
            File rawDir, RawExportRecipeShardWriterPool.Shard shard, String firstId, String secondId)
            throws Exception {
        assertEquals("shard recipe count", 2L, shard.recipeCount);
        File file = new File(rawDir, shard.path);
        try (FileInputStream in = new FileInputStream(file)) {
            assertEquals("gzip magic byte 1", 0x1f, in.read());
            assertEquals("gzip magic byte 2", 0x8b, in.read());
        }
        List<String> lines = readGzipLines(file);
        assertEquals("shard JSONL line count", 2, lines.size());
        assertEquals("first shard row order", firstId, recipeId(lines.get(0)));
        assertEquals("second shard row order", secondId, recipeId(lines.get(1)));
    }

    private static String recipeId(String json) {
        return new JsonParser().parse(json).getAsJsonObject().get("recipeId").getAsString();
    }

    private static List<String> readGzipLines(File file) throws Exception {
        java.util.ArrayList<String> lines = new java.util.ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new FileInputStream(file)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String readUtf8(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static void assertIOException(String label, IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + ": expected IOException");
        } catch (java.io.IOException expected) {
            // Expected fail-closed path.
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

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private interface IoAction {
        void run() throws Exception;
    }
}
