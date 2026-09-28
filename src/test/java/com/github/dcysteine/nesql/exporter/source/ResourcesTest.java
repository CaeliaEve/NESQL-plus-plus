package com.github.dcysteine.nesql.exporter.source;

import com.github.dcysteine.nesql.exporter.task.Jobs;
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
        String location = "demo:lang/en_US.lang";
        JsonObject base = archive(root.resolve("base.zip"), location, "name=Base\n");
        JsonObject pack = archive(root.resolve("pack.zip"), location, "name=Override 石头\n");
        SimpleReloadableResourceManager manager = new SimpleReloadableResourceManager(new IMetadataSerializer());
        JsonObject resolved;
        try (FileResourcePack first = new FileResourcePack(root.resolve("base.zip").toFile());
             FileResourcePack last = new FileResourcePack(root.resolve("pack.zip").toFile())) {
            // Register native packs directly: the full reload wrapper invokes
            // Forge loading-screen services that require a running game.
            manager.reloadResourcePack(first); manager.reloadResourcePack(last);
            resolved = Resources.read(location, manager.getResource(new ResourceLocation(location)).getInputStream());
            require(resolved.get("sha256").getAsString().equals(CanonicalJson.digest("name=Override 石头\n".getBytes(StandardCharsets.UTF_8))), "Native resource override was lost");
            SimpleReloadableResourceManager reverse = new SimpleReloadableResourceManager(new IMetadataSerializer());
            reverse.reloadResourcePack(last); reverse.reloadResourcePack(first);
            JsonObject reordered = Resources.read(location, reverse.getResource(new ResourceLocation(location)).getInputStream());
            require(!resolved.get("sha256").equals(reordered.get("sha256")), "Native pack ordering was ignored");
        }
        JsonObject env = object("mods", array(object("sha256", base.get("sha256"))), "resources", array("pack.zip"),
                "inputs", array(object("path", "resourcepacks/pack.zip", "sha256", pack.get("sha256"))));
        resolved.addProperty("status", "passed");
        JsonObject report = object("format", "nesql.check", "job", "native-fixture", "status", "complete", "environment", env,
                "request", object("world", "test-copy", "check", object("domain", "resources", "resources", array(location))), "rows", array(resolved));
        Files.write(root.resolve("check.json"), CanonicalJson.bytes(report));
        Files.write(root.resolve("input.json"), CanonicalJson.bytes(object("format", "elysium.resources", "revision", 1, "archives", array(base, pack))));
        Files.write(root.resolve("evidence.json"), CanonicalJson.bytes(object("environment", CanonicalJson.digest(env), "sha256", CanonicalJson.digest(report))));
    }
    private static JsonObject archive(Path file, String location, String text) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(Resources.path(location))); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        byte[] bytes = Files.readAllBytes(file);
        return object("key", file.getFileName().toString(), "path", file.getFileName().toString(), "bytes", bytes.length, "sha256", CanonicalJson.digest(bytes));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
