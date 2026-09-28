package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.task.Checks;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

final class ResourcesTest {
    static void run(Path root) throws Exception {
        byte[] bytes = "original UTF-8 石头\n".getBytes(StandardCharsets.UTF_8);
        boolean[] closed = { false };
        JsonObject result = Resources.read("demo:lang/en_US.lang", new ByteArrayInputStream(bytes) {
            @Override public void close() { closed[0] = true; }
        });
        require(result.get("sha256").getAsString().equals(CanonicalJson.digest(bytes)), "Changed native bytes");
        require(result.get("bytes").getAsJsonPrimitive().isString() && result.get("bytes").getAsString().equals(Integer.toString(bytes.length)), "Byte count is not exact");
        require(result.get("path").getAsString().equals("assets/demo/lang/en_US.lang") && closed[0], "Resource stream was not closed or path was guessed");
        for (String name : new String[] { "../x:textures/a.png", "..:textures/a.png", ".:lang/a.lang", "Demo:textures/a.png", "demo:textures/../a.png", "demo:textures//a.png", "demo:secret.txt", "demo:lang/a\\b.lang" }) {
            try { Resources.path(name); throw new AssertionError("Accepted unsafe resource " + name); }
            catch (IllegalArgumentException expected) { }
        }
        closed[0] = false;
        try {
            Resources.read("demo:textures/a.png", new InputStream() {
                @Override public int read() throws IOException { throw new IOException("native read failed"); }
                @Override public void close() { closed[0] = true; }
            });
            throw new AssertionError("Swallowed resource read error");
        } catch (IOException expected) { require(closed[0], "Failed stream leaked"); }
        closed[0] = false;
        try {
            Resources.read("demo:textures/a.png", new InputStream() {
                @Override public int read() { return 0; }
                @Override public int read(byte[] buffer, int offset, int count) { return count; }
                @Override public void close() { closed[0] = true; }
            });
            throw new AssertionError("Accepted unbounded resource");
        } catch (Jobs.Fault expected) { require(expected.code.equals("resource_limit") && closed[0], "Budget failure leaked stream"); }
        Files.createDirectories(root);
        java.awt.image.BufferedImage pixels = new java.awt.image.BufferedImage(2, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        pixels.setRGB(0, 0, 0x800a141e); pixels.setRGB(1, 0, 0x0028323c);
        java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(pixels, "png", png);
        String location = "demo:lang/en_US.lang";
        String texture = "demo:textures/a.png";
        JsonObject base = archive(root.resolve("base.zip"), location, "name=Base\n", png.toByteArray());
        JsonObject pack = archive(root.resolve("pack.zip"), location, "name=Override 石头\n", png.toByteArray());
        SimpleReloadableResourceManager manager = new SimpleReloadableResourceManager(new IMetadataSerializer());
        JsonObject resolved, image;
        byte[] nativePng;
        try (FileResourcePack first = new FileResourcePack(root.resolve("base.zip").toFile());
             FileResourcePack last = new FileResourcePack(root.resolve("pack.zip").toFile())) {
            // Register native packs directly: the full reload wrapper invokes
            // Forge loading-screen services that require a running game.
            manager.reloadResourcePack(first); manager.reloadResourcePack(last);
            resolved = Resources.read(location, manager.getResource(new ResourceLocation(location)).getInputStream());
            image = Resources.read(texture, manager.getResource(new ResourceLocation(texture)).getInputStream());
            try (InputStream input = manager.getResource(new ResourceLocation(texture)).getInputStream()) {
                java.io.ByteArrayOutputStream encoded = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(javax.imageio.ImageIO.read(input), "png", encoded);
                nativePng = encoded.toByteArray();
            }
            require(resolved.get("sha256").getAsString().equals(CanonicalJson.digest("name=Override 石头\n".getBytes(StandardCharsets.UTF_8))), "Native resource override was lost");
            SimpleReloadableResourceManager reverse = new SimpleReloadableResourceManager(new IMetadataSerializer());
            reverse.reloadResourcePack(last); reverse.reloadResourcePack(first);
            JsonObject reordered = Resources.read(location, reverse.getResource(new ResourceLocation(location)).getInputStream());
            require(!resolved.get("sha256").equals(reordered.get("sha256")), "Native pack ordering was ignored");
        }
        JsonObject env = object("game", "Minecraft 1.7.10", "loader", "Forge", "locale", "en_US",
                "mods", array(object("id", "fixture", "name", "Fixture", "version", "1", "sha256", base.get("sha256"))), "resources", array("pack.zip"),
                "inputs", array(object("path", "resourcepacks/pack.zip", "sha256", pack.get("sha256"))),
                "knowledge", object(), "probes", array(Probe.defaults().json()), "settings", object("profile", "data", "handlers", "", "iconPixels", "64"));
        resolved.addProperty("status", "passed");
        image.addProperty("status", "passed");
        formal(root, env, resolved, image, nativePng);
        Files.write(root.resolve("input.json"), CanonicalJson.bytes(object("format", "elysium.resources", "revision", 1, "archives", array(base, pack))));
    }
    private static void formal(Path root, JsonObject env, JsonObject language, JsonObject image, byte[] pixels) throws Exception {
        String session = CanonicalJson.digest("native-library-fixture".getBytes(StandardCharsets.UTF_8));
        JsonObject full = new com.google.gson.JsonParser().parse(env.toString()).getAsJsonObject();
        full.getAsJsonObject("settings").addProperty("profile", "full");
        try (Jobs jobs = new Jobs(root.resolve("jobs"), context -> {
            boolean check = context.request().check != null;
            JsonObject environment = check ? env : full;
            context.provenance(environment, session);
            JsonObject copy = context.provenance(); copy.addProperty("session", "modified");
            context.provenance(environment, session);
            require(context.provenance().get("session").getAsString().equals(session), "Caller mutated capture provenance");
            try { context.provenance(environment, CanonicalJson.digest(new byte[]{7})); throw new AssertionError("Capture accepted a changed session"); }
            catch (Jobs.Fault expected) { require(expected.code.equals("environment_changed"), "Wrong provenance failure"); }
            if (check) {
                Checks.Report report = new Checks.Report(root.resolve("checks"), context, environment, array(language, image));
                context.checked(report.finish("complete", null));
                return;
            }
            try (Dataset dataset = new Dataset(root.resolve("staging"), "native-fixture", environment, false)) {
                String path = dataset.asset(pixels, "png");
                JsonObject asset = object("path", path, "width", 2, "height", 1, "frames", array(), "interpolate", false,
                        "source", object("kind", "resource", "location", "demo:textures/a.png"));
                asset.addProperty("id", Identity.content("asset", asset));
                for (String kind : Dataset.COLLECTIONS) {
                    try (Dataset.Records rows = dataset.records(kind)) { if (kind.equals("assets")) rows.write(asset); }
                }
                dataset.seal(); context.publish(dataset.prepare(root.resolve("datasets")));
            }
        })) {
            Jobs.Job source = await(jobs, jobs.start(new Jobs.Request("source", "source", "full", java.util.Collections.emptyList(),
                    java.util.Collections.singletonList(Probe.defaults()), "test-copy")).id, "succeeded");
            Files.copy(root.resolve("jobs").resolve(source.id + ".json"), root.resolve("capture.json"));
            Jobs.Job checked = await(jobs, jobs.start(Checks.request(object("key", "resources", "world", "test-copy", "domain", "resources",
                    "resources", array("demo:lang/en_US.lang", "demo:textures/a.png")))).id, "checked");
            Files.copy(java.nio.file.Paths.get(checked.report.path), root.resolve("check.json"));
            JsonObject report = new com.google.gson.JsonParser().parse(new String(Files.readAllBytes(root.resolve("check.json")), StandardCharsets.UTF_8)).getAsJsonObject();
            require(report.get("provenance").equals(checked.provenance), "Check report lost the journal's provenance");
            require(source.provenance.get("runtime").equals(checked.provenance.get("runtime")) && !source.provenance.get("environment").equals(checked.provenance.get("environment")), "Full and data scopes were conflated");
            Files.write(root.resolve("evidence.json"), CanonicalJson.bytes(object("environment", CanonicalJson.digest(env), "sha256", checked.report.sha256,
                    "source", source.result.path, "captureSha256", CanonicalJson.digest(Files.readAllBytes(root.resolve("capture.json"))))));
        }
        try (Jobs restored = new Jobs(root.resolve("jobs"), context -> { throw new AssertionError("Replayed completed capture"); })) {
            require(restored.current().provenance.get("session").getAsString().equals(session), "Restart lost provenance");
        }
    }
    private static Jobs.Job await(Jobs jobs, String id, String state) throws Exception {
        long end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            Jobs.Job job = jobs.read(id);
            if (job.state.equals(state)) return job;
            require(!job.state.equals("failed"), "Fixture capture failed: " + job.error);
            Thread.sleep(5);
        }
        throw new AssertionError("Fixture did not complete");
    }
    private static JsonObject archive(Path file, String location, String text, byte[] texture) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(Resources.path(location))); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/demo/textures/a.png")); zip.write(texture); zip.closeEntry();
        }
        byte[] bytes = Files.readAllBytes(file);
        return object("key", file.getFileName().toString(), "path", file.getFileName().toString(), "bytes", bytes.length, "sha256", CanonicalJson.digest(bytes));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
