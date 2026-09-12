package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Probe;
import com.github.dcysteine.nesql.exporter.task.ClientThread;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraftforge.common.ForgeVersion;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.lib.research.PlayerKnowledge;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Captures metadata on the client; hashes artifacts, configs and scripts on the export worker. */
final class Environment {
    private Environment() {}

    static JsonObject capture(ClientThread.Session client, Path instance, Jobs.Request request) throws Exception {
        Snapshot snapshot = client.call(Environment::snapshot);
        JsonArray mods = snapshot.sources.fingerprints();
        TreeMap<String, Path> inputs = new TreeMap<>(CanonicalJson.KEY_ORDER);
        for (String directory : new String[] {"config", "scripts", "resources"}) {
            Path root = instance.resolve(directory);
            if (Files.exists(root)) collect(instance, root, inputs);
        }
        for (String name : snapshot.resources) {
            Path resource = instance.resolve("resourcepacks").resolve(name).normalize();
            if (!resource.startsWith(instance.resolve("resourcepacks"))) throw new IOException("Resource pack escapes its directory");
            collect(instance, resource, inputs);
        }
        JsonArray files = new JsonArray();
        for (Map.Entry<String, Path> file : inputs.entrySet()) files.add(object("path", file.getKey(), "sha256", Sources.hash(file.getValue())));
        JsonArray resources = new JsonArray();
        for (String name : snapshot.resources) resources.add(value(name));
        JsonArray probes = new JsonArray();
        for (Probe probe : request.probes) probes.add(probe.json());
        return object("game", "Minecraft 1.7.10", "loader", "Forge " + ForgeVersion.getVersion(), "locale", snapshot.locale,
                "mods", mods, "inputs", files, "resources", resources, "knowledge", snapshot.knowledge,
                "probes", probes,
                "settings", object("profile", request.profile, "handlers", String.join(",", request.handlers),
                        "iconPixels", "64", "tooltip", "advanced"));
    }

    private static Snapshot snapshot() {
        Minecraft game = Minecraft.getMinecraft();
        Snapshot snapshot = new Snapshot();
        snapshot.sources = Sources.capture();
        snapshot.locale = game.gameSettings.language;
        for (Object resource : game.getResourcePackRepository().getRepositoryEntries()) {
            snapshot.resources.add(((ResourcePackRepository.Entry) resource).getResourcePackName());
        }
        if (Loader.isModLoaded("Thaumcraft")) snapshot.knowledge.addProperty("thaumcraft", knowledge(game.thePlayer.getCommandSenderName()));
        return snapshot;
    }

    private static String knowledge(String player) {
        PlayerKnowledge knowledge = Thaumcraft.proxy.getPlayerKnowledge();
        JsonObject state = new JsonObject();
        state.addProperty("researchLoaded", knowledge.researchCompleted.containsKey(player));
        state.addProperty("aspectsLoaded", knowledge.aspectsDiscovered.containsKey(player));
        state.add("research", sorted(knowledge.researchCompleted.get(player)));
        state.add("objects", sorted(knowledge.objectsScanned.get(player)));
        state.add("entities", sorted(knowledge.entitiesScanned.get(player)));
        state.add("phenomena", sorted(knowledge.phenomenaScanned.get(player)));
        JsonObject aspects = new JsonObject();
        // Read the map directly: getAspectsDiscovered can initialize player state.
        AspectList discovered = knowledge.aspectsDiscovered.get(player);
        if (discovered != null) for (Aspect aspect : discovered.getAspects()) aspects.addProperty(aspect.getTag(), discovered.getAmount(aspect));
        state.add("aspects", aspects);
        return CanonicalJson.digest(state);
    }

    private static JsonArray sorted(List<String> values) {
        JsonArray result = new JsonArray();
        if (values != null) for (String value : new TreeSet<>(values)) result.add(value(value));
        return result;
    }

    private static void collect(Path instance, Path root, Map<String, Path> output) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                Jobs.checkpoint();
                Sources.requirePlain(directory);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Sources.requirePlain(file);
                if (!attrs.isRegularFile()) throw new IOException("Environment input is not a regular file: " + file);
                output.put(instance.relativize(file).toString().replace('\\', '/'), file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class Snapshot {
        String locale;
        Sources sources;
        final List<String> resources = new ArrayList<>();
        final JsonObject knowledge = new JsonObject();
    }
}
