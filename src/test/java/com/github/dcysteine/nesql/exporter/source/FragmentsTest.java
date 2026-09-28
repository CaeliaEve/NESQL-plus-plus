package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

final class FragmentsTest {
    static void run(Path root) throws Exception {
        Files.createDirectories(root);
        String handler = "category_" + String.join("", Collections.nCopies(64, "a"));
        Jobs.Request request = Jobs.Request.parse(object("key", "fragments", "name", "fixture", "profile", "full",
                "scope", "recipes", "handlers", array(handler), "world", "test-copy"));
        JsonObject environment = object("game", "Minecraft 1.7.10", "loader", "Forge", "locale", "en_US", "mods", array(),
                "inputs", array(), "resources", array(), "knowledge", object(), "probes", array(Probe.defaults().json()),
                "settings", object("profile", "full", "handlers", handler, "scope", "recipes"));
        JsonObject provenance = Provenance.capture(environment, request, CanonicalJson.digest(new byte[]{1}));
        java.awt.image.BufferedImage pixels = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, 0xff102030);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(pixels, "png", bytes);
        final Fragments.Receipt[] receipt = {null};
        Fragments failed = new Fragments(root.resolve("partial"), provenance, request, value -> receipt[0] = value);
        byte[] initial = Files.readAllBytes(root.resolve("partial/manifest.json"));
        try (Dataset dataset = new Dataset(root.resolve("failed-work"), "fixture", environment, false, failed)) {
            dataset.asset(bytes.toByteArray(), "png");
            for (int index = 0; index < 130; index++) {
                pixels.setRGB(0, 0, 0xff000000 | index);
                java.io.ByteArrayOutputStream image = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(pixels, "png", image);
                dataset.asset(image.toByteArray(), "png");
            }
            require(receipt[0].state.equals("writing"), "An open capture was advertised as complete");
            require(receipt[0].files == 128, "Fragment progress was not sampled");
            require(java.util.Arrays.equals(initial, Files.readAllBytes(root.resolve("partial/manifest.json"))), "Open capture rewrote its growing manifest");
            try (java.util.stream.Stream<Path> parts = Files.list(root.resolve("partial/parts"))) {
                require(parts.count() == 132, "Durable receipts lost individual files");
            }
            try { dataset.archive(); throw new AssertionError("Archived an unsealed dataset"); }
            catch (IllegalStateException expected) { }
        }
        require(!Files.exists(root.resolve("failed-work")), "Failed Source left its working directory");
        JsonObject partial = read(root.resolve("partial/manifest.json"));
        require(partial.get("state").getAsString().equals("writing") && !partial.has("source"), "Partial data gained a Source identity");
        require(Files.exists(root.resolve("partial/blobs").resolve(CanonicalJson.digest(bytes.toByteArray()))), "Completed image disappeared after failure");
        Fragments archive = new Fragments(root.resolve("complete"), provenance, request, value -> receipt[0] = value);
        String source;
        try (Dataset dataset = new Dataset(root.resolve("ready-work"), "fixture", environment, false, archive)) {
            String path = dataset.asset(bytes.toByteArray(), "png");
            dataset.asset(bytes.toByteArray(), "png");
            JsonObject asset = object("path", path, "width", 1, "height", 1, "frames", array(), "interpolate", false,
                    "source", object("kind", "capture", "location", "fixture"));
            asset.addProperty("id", Identity.content("asset", asset));
            for (String kind : Dataset.COLLECTIONS) {
                try (Dataset.Records rows = dataset.records(kind)) { if (kind.equals("assets")) rows.write(asset); }
            }
            source = dataset.seal();
            require(receipt[0].state.equals("writing"), "Sealing files bypassed the final session guard");
            dataset.archive();
            // Model failure after all capture/cleanup guards, before Source publication.
        }
        require(!Files.exists(root.resolve("ready-work")), "Unpublished Source escaped cleanup");
        JsonObject complete = read(root.resolve("complete/manifest.json"));
        require(complete.get("state").getAsString().equals("complete") && complete.getAsJsonObject("source").get("id").getAsString().equals(source), "Complete capture cannot recover its Source");
        require(complete.getAsJsonArray("files").size() == Dataset.COLLECTIONS.size() + 2, "Missing or duplicate fragments");
        require(receipt[0].sha256.equals(CanonicalJson.digest(Files.readAllBytes(root.resolve("complete/manifest.json")))), "Receipt hash differs from durable manifest");
        Files.write(root.resolve("evidence.json"), CanonicalJson.bytes(object("source", source, "captureSha256", receipt[0].sha256)));
        try { new Fragments(root.resolve("complete"), provenance, request, value -> {}); throw new AssertionError("Overwrote capture history"); }
        catch (java.io.IOException expected) { }
        // A changed file must not be accepted just because its digest-named link exists.
        Path blob = root.resolve("complete/blobs").resolve(CanonicalJson.digest(bytes.toByteArray()));
        Files.write(blob, new byte[]{7});
        try { archive.complete(complete.getAsJsonObject("source")); throw new AssertionError("Reused damaged completed fragments"); }
        catch (java.io.IOException expected) { }
        Files.write(blob, bytes.toByteArray()); // Restore this test-owned fixture for the Rust consumer.
        journal(root.resolve("journal"), environment, request);
    }

    private static void journal(Path root, JsonObject environment, Jobs.Request request) throws Exception {
        Files.createDirectories(root);
        String id;
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> {
            context.provenance(environment, CanonicalJson.digest(new byte[]{2}));
            Fragments archive = new Fragments(root.resolve("captures").resolve(context.id()), context.provenance(), context.request(), context::fragments);
            try (Dataset dataset = new Dataset(root.resolve("work"), "fixture", environment, false, archive)) {
                for (String kind : Dataset.COLLECTIONS) try (Dataset.Records ignored = dataset.records(kind)) { }
                dataset.seal(); dataset.archive();
                throw new java.io.IOException("Simulated publication failure");
            }
        })) {
            id = jobs.start(request).id;
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!jobs.read(id).state.equals("failed") && System.nanoTime() < deadline) Thread.sleep(10);
            require(jobs.read(id).state.equals("failed"), "Publication failure lost job failure state");
        }
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> { throw new AssertionError("Restart recaptured native facts"); })) {
            Jobs.Job job = jobs.read(id);
            require(job.state.equals("failed") && job.result == null && job.fragments.state.equals("complete"), "Restart lost recovery evidence or published a failed result");
            require(job.fragments.sha256.equals(CanonicalJson.digest(Files.readAllBytes(java.nio.file.Paths.get(job.fragments.path)))), "Restored fragment receipt differs from its manifest");
        }
    }
    private static JsonObject read(Path path) throws Exception { return new JsonParser().parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8)).getAsJsonObject(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
