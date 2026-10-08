package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real files and the normal Rows/Dataset writer; no Minecraft or image timing mocks. */
final class CheckpointsTest {
    static void run(Path root) throws Exception {
        semantic();
        Files.createDirectories(root);
        List<String> handlers = Arrays.asList("category_" + repeat('a'), "category_" + repeat('b'));
        JsonObject proof = object("revision", 1, "environment", repeat('1'), "runtime", repeat('2'),
                "selection", repeat('3'), "session", repeat('4'));
        JsonObject environment = object("fixture", "checkpoint");
        byte[] pixels = {1, 2, 3, 4};
        String image = "assets/" + CanonicalJson.digest(pixels) + ".png";
        JsonObject item = object("id", "item_" + repeat('5'), "order", 7);
        JsonObject recipe = object("id", "recipe_" + repeat('6'), "category", handlers.get(0));
        JsonObject[] saved = {null};
        Path first = root.resolve("first");
        try (Checkpoints checkpoint = new Checkpoints(first, proof, handlers, "fixture", value -> saved[0] = value)) {
            checkpoint.begin("base");
            Path original = root.resolve("image.png"); Files.write(original, pixels);
            checkpoint.asset(original, image);
            checkpoint.row("items", item);
            checkpoint.finish(object());
            checkpoint.begin(handlers.get(0));
            checkpoint.row("recipes", recipe);
            checkpoint.finish(object("excluded", 2));
            checkpoint.begin(handlers.get(1));
            checkpoint.row("recipes", object("id", "partial"));
            // A later native failure leaves an unfinished unit; it is not in the receipt.
            checkpoint.release(true);
        }
        String digest = saved[0].get("sha256").getAsString();
        String resumed;
        try (Dataset dataset = new Dataset(root.resolve("resumed"), "fixture", environment, false);
             Rows rows = new Rows(root.resolve("resumed-sort"));
             Checkpoints checkpoint = new Checkpoints(root.resolve("second"), proof, handlers, "fixture", value -> {})) {
            int[] restored = {0};
            int count = checkpoint.replay(first, digest, dataset, rows, (kind, row) -> restored[0]++);
            require(count == 2 && restored[0] == 2, "Replayed a partial unit or lost completed records");
            checkpoint.begin(handlers.get(1));
            JsonObject last = object("id", "recipe_last", "category", handlers.get(1));
            rows.add("recipes", last); checkpoint.row("recipes", last); checkpoint.finish(object());
            rows.write(dataset); resumed = dataset.seal();
        }
        try (Dataset dataset = new Dataset(root.resolve("direct"), "fixture", environment, false);
             Rows rows = new Rows(root.resolve("direct-sort"))) {
            dataset.asset(pixels, "png"); rows.add("items", item); rows.add("recipes", recipe);
            rows.add("recipes", object("id", "recipe_last", "category", handlers.get(1)));
            rows.write(dataset);
            require(resumed.equals(dataset.seal()), "Resume changed the ordinary Source byte identity");
        }
        JsonObject changed = new com.google.gson.JsonParser().parse(proof.toString()).getAsJsonObject();
        changed.addProperty("session", repeat('9'));
        reject(root.resolve("stale"), first, digest, changed, handlers, environment, "Accepted another game session");
        reject(root.resolve("wrong-hash"), first, repeat('0'), proof, handlers, environment, "Accepted an unpinned receipt");
        Path rejected = root.resolve("changed-native");
        try (Dataset dataset = new Dataset(root.resolve("changed-work"), "fixture", environment, false);
             Rows rows = new Rows(root.resolve("changed-sort"));
             Checkpoints checkpoint = new Checkpoints(rejected, proof, handlers, "fixture", value -> saved[0] = value)) {
            try {
                checkpoint.replay(first, digest, dataset, rows, (kind, row) -> {}, (index, evidence) -> {
                    if (index == 1) throw new java.io.IOException("native facts changed");
                });
                throw new AssertionError("Native validation failure was ignored");
            } catch (java.io.IOException expected) { require(expected.getMessage().equals("native facts changed"), "Wrong native validation failure"); }
            checkpoint.release(true);
        }
        require(saved[0].get("units").getAsInt() == 1 && !Files.exists(root.resolve("changed-work")),
                "A semantically changed unit was sealed or published");
        byte[] original = Files.readAllBytes(first.resolve("units/unit-0001.jsonl"));
        Files.write(first.resolve("units/unit-0001.jsonl"), new byte[]{'{', '}'});
        reject(root.resolve("corrupt"), first, digest, proof, handlers, environment, "Accepted corrupt unit data");
        Files.write(first.resolve("units/unit-0001.jsonl"), original);
        Files.write(first.resolve("blobs").resolve(CanonicalJson.digest(pixels) + ".png"), new byte[]{8});
        reject(root.resolve("corrupt-asset"), first, digest, proof, handlers, environment, "Accepted corrupt asset bytes");
        Path unsafe = root.resolve("unsafe");
        try (Checkpoints checkpoint = new Checkpoints(unsafe, proof, handlers, "fixture", value -> saved[0] = value)) {
            checkpoint.begin("base"); checkpoint.finish(object()); checkpoint.release(false);
        }
        reject(root.resolve("unclean"), unsafe, saved[0].get("sha256").getAsString(), proof, handlers, environment,
                "Accepted a checkpoint after failed native cleanup");
        System.out.println("Handler checkpoints: completed prefix, exact replay, stale/corrupt/unclean rejection passed");
    }

    private static void reject(Path root, Path previous, String digest, JsonObject proof, List<String> handlers,
                               JsonObject environment, String message) throws Exception {
        Files.createDirectories(root);
        try (Dataset dataset = new Dataset(root.resolve("work"), "fixture", environment, false);
             Rows rows = new Rows(root.resolve("sort"));
             Checkpoints checkpoint = new Checkpoints(root.resolve("checkpoints"), proof, handlers, "fixture", value -> {})) {
            try { checkpoint.replay(previous, digest, dataset, rows, (kind, row) -> {}); throw new AssertionError(message); }
            catch (java.io.IOException expected) { }
        }
        require(!Files.exists(root.resolve("work")), "A rejected checkpoint published a dataset");
    }
    private static String repeat(char value) { char[] chars = new char[64]; Arrays.fill(chars, value); return new String(chars); }
    private static void semantic() throws Exception {
        Class<?> type = Class.forName("com.github.dcysteine.nesql.exporter.source.Semantic");
        java.lang.reflect.Method add = type.getMethod("add", String.class, JsonObject.class), finish = type.getMethod("finish");
        Object first = type.newInstance(), same = type.newInstance(), changed = type.newInstance();
        JsonObject source = object("id", "item_one", "icon", "asset_old", "tags", array("oreCopper"), "stackLimit", 64);
        add.invoke(first, "items", source);
        add.invoke(first, "tracks", object("id", "visual-only"));
        source.addProperty("icon", "asset_new");
        add.invoke(same, "items", source);
        source.addProperty("stackLimit", 16);
        add.invoke(changed, "items", source);
        Object proof = finish.invoke(first);
        require(proof.equals(finish.invoke(same)), "Visual assets changed native semantic proof");
        require(!proof.equals(finish.invoke(changed)), "Same-ID native property drift escaped semantic proof");
        require(source.get("icon").getAsString().equals("asset_new"), "Semantic projection mutated original facts");
        Semantic research = new Semantic(), graphics = new Semantic(), knowledge = new Semantic();
        JsonObject study = object("id", "research_one", "parents", array("research_parent"), "completed", false, "icon", null, "texture", "old_art");
        research.add("research", study);
        study.addProperty("texture", "new_art"); graphics.add("research", study);
        study.addProperty("completed", true); knowledge.add("research", study);
        require(research.finish().equals(graphics.finish()), "Research display art changed its native semantic proof");
        require(!research.finish().equals(knowledge.finish()), "Research dependency or knowledge changes escaped recovery validation");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
