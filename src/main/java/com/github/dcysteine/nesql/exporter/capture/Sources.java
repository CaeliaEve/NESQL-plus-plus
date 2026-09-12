package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.InjectedModContainer;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.MCPDummyContainer;
import cpw.mods.fml.common.MinecraftDummyContainer;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.CoreModManager;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Loaded artifact provenance. Metadata is read on the client; validation and hashing run on the worker. */
final class Sources {
    private static final String CLIENT_JAR = "minecraft.client.jar";
    private static final String[] CLIENT_CLASSES = {"net/minecraft/client/Minecraft.class", "bao.class"};
    private final Archive client;
    private final Jobs.Fault clientError;
    private final List<Mod> mods = new ArrayList<>();

    static Sources capture() {
        return new Sources(new ArrayList<>(Loader.instance().getActiveModList()), cores(wrappers()));
    }

    Sources(List<ModContainer> containers, Map<String, Core> cores) {
        Archive found = null; Jobs.Fault error = null;
        try { found = client(); } catch (Jobs.Fault failure) { error = failure; }
        client = found; clientError = error;
        if (containers.size() > 4096) throw fault("Too many mod containers");
        TreeMap<String, Mod> sorted = new TreeMap<>();
        for (ModContainer container : containers) {
            Jobs.checkpoint();
            Archive source = null; Jobs.Fault problem = null;
            try { source = resolve(container, cores, client); }
            catch (Jobs.Fault failure) { problem = failure; }
            Core core = cores.get(unwrap(container).getClass().getName());
            Mod mod = new Mod(container, source, problem, core == null ? null : core.plugin);
            if (sorted.put(mod.id, mod) != null) throw fault("Duplicate mod id: " + mod.id);
        }
        mods.addAll(sorted.values());
    }

    JsonObject inspect() {
        Map<String, JsonObject> checked = new HashMap<>();
        JsonObject game = describe(client, clientError, checked);
        boolean valid = game.get("valid").getAsBoolean();
        JsonArray rows = new JsonArray();
        for (Mod mod : mods) {
            Jobs.checkpoint();
            JsonObject row = object("id", mod.id, "name", mod.name, "container", mod.container, "plugin", mod.plugin);
            JsonObject status = describe(mod.source, mod.error, checked);
            status.entrySet().forEach(entry -> row.add(entry.getKey(), entry.getValue()));
            if (!status.get("valid").getAsBoolean()) valid = false;
            rows.add(row);
        }
        return object("client", game, "sources", object("valid", valid, "count", mods.size(), "rows", rows));
    }

    JsonArray fingerprints() throws IOException {
        JsonObject report = inspect();
        if (!report.getAsJsonObject("sources").get("valid").getAsBoolean()) {
            List<String> invalid = new ArrayList<>();
            for (com.google.gson.JsonElement value : report.getAsJsonObject("sources").getAsJsonArray("rows")) {
                JsonObject row = value.getAsJsonObject();
                if (!row.get("valid").getAsBoolean()) invalid.add(row.get("id").getAsString());
            }
            throw new Jobs.Fault("mod_sources_invalid", "Unusable mod sources: " + String.join(", ", invalid)
                    + "; inspect_game.sources.rows contains every failure");
        }
        Map<Path, String> hashes = new HashMap<>();
        JsonArray result = new JsonArray();
        for (Mod mod : mods) {
            Jobs.checkpoint();
            String digest = hashes.get(mod.source.path);
            if (digest == null) {
                try { digest = hash(mod.source.path); }
                catch (IOException failure) { throw new IOException("Cannot fingerprint mod " + mod.id + " at " + mod.source.path, failure); }
                hashes.put(mod.source.path, digest);
            }
            result.add(object("id", mod.id, "name", mod.name, "version", mod.version, "sha256", digest));
        }
        return result;
    }

    static Archive resolve(ModContainer mod, Map<String, Core> cores, Archive client) {
        ModContainer container = unwrap(mod);
        if (container instanceof MCPDummyContainer || container instanceof MinecraftDummyContainer) {
            if (client == null) throw fault("Minecraft client archive is unavailable");
            return client;
        }
        if (mod instanceof InjectedModContainer) {
            Core core = cores.get(container.getClass().getName());
            if (core != null) {
                if (core.error != null) throw core.error;
                return core.source;
            }
            // Containers injected outside the core-plugin registry must identify
            // their own implementation archive, never the synthetic minecraft.jar.
            if (container.getClass() != DummyModContainer.class) return owner(container.getClass());
            if (mod.getSource() == null || mod.getSource().getPath().equals("minecraft.jar")) {
                throw fault("Injected container has no owning artifact: " + mod.getModId());
            }
        }
        if (mod.getSource() == null) throw fault("Mod has no declared source: " + mod.getModId());
        return new Archive(mod.getSource().toPath().toAbsolutePath().normalize(), "declared");
    }

    private static ModContainer unwrap(ModContainer mod) {
        return mod instanceof InjectedModContainer ? ((InjectedModContainer) mod).wrappedContainer : mod;
    }

    private static List<?> wrappers() {
        try {
            Field field = CoreModManager.class.getDeclaredField("loadPlugins"); field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof List<?>)) throw fault("Forge core-plugin registry is unavailable");
            return new ArrayList<>((List<?>) value);
        } catch (ReflectiveOperationException failure) { throw fault("Cannot read the pinned Forge core-plugin registry: " + failure); }
    }

    /** The pinned FML wrapper retains the actual file passed to loadCoreMod. */
    static Map<String, Core> cores(List<?> wrappers) {
        if (wrappers.size() > 4096) throw fault("Too many core plugins");
        Map<String, Core> result = new TreeMap<>();
        try {
            Class<?> type = Class.forName("cpw.mods.fml.relauncher.CoreModManager$FMLPluginWrapper");
            Field instance = type.getDeclaredField("coreModInstance"), location = type.getDeclaredField("location");
            instance.setAccessible(true); location.setAccessible(true);
            for (Object wrapper : wrappers) {
                Jobs.checkpoint();
                if (!type.isInstance(wrapper)) throw fault("Unknown Forge core-plugin wrapper");
                IFMLLoadingPlugin plugin = (IFMLLoadingPlugin) instance.get(wrapper);
                String container = plugin.getModContainerClass();
                if (container == null) continue;
                File file = (File) location.get(wrapper);
                Archive source = null; Jobs.Fault error = null;
                try { source = file == null ? owner(plugin.getClass()) : new Archive(file.toPath().toAbsolutePath().normalize(), "coremod"); }
                catch (Jobs.Fault failure) { error = failure; }
                Core core = new Core(plugin.getClass().getName(), source, error);
                Core previous = result.putIfAbsent(container, core);
                if (previous != null && (previous.source == null || source == null || !previous.source.path.equals(source.path))) {
                    throw new Jobs.Fault("mod_source_conflict", "Different core artifacts provide container " + container);
                }
            }
        } catch (ReflectiveOperationException failure) { throw fault("Cannot read pinned core-plugin metadata: " + failure); }
        return Collections.unmodifiableMap(result);
    }

    static Archive client() {
        String configured = System.getProperty(CLIENT_JAR);
        java.security.CodeSource source = Minecraft.class.getProtectionDomain().getCodeSource();
        URL location = source == null ? null : source.getLocation();
        URL resource = null;
        if (configured == null && local(location) == null) {
            for (String entry : CLIENT_CLASSES) {
                resource = Minecraft.class.getResource("/" + entry);
                if (local(resource) != null) break;
            }
        }
        return archive(configured, location, resource);
    }

    static Archive archive(String configured, URL location, URL resource) {
        if (configured != null) {
            try {
                Path path = Paths.get(configured);
                if (!path.isAbsolute() || path.toUri().getAuthority() != null) throw new InvalidPathException(configured, "Expected an absolute local path");
                return new Archive(path.normalize(), "launcher", CLIENT_CLASSES);
            } catch (InvalidPathException failure) {
                throw new Jobs.Fault("mod_source_invalid", CLIENT_JAR + " must name an absolute local client jar: " + failure.getReason());
            }
        }
        Path path = local(location);
        if (path != null) return new Archive(path, "class", CLIENT_CLASSES);
        path = local(resource);
        if (path != null) return new Archive(path, "resource", CLIENT_CLASSES);
        throw fault("Minecraft client jar is unavailable: no " + CLIENT_JAR + " property or local class archive");
    }

    private static Archive owner(Class<?> type) {
        String entry = type.getName().replace('.', '/') + ".class";
        java.security.CodeSource code = type.getProtectionDomain().getCodeSource();
        URL location = code == null ? null : code.getLocation();
        return owner(entry, location, local(location) == null ? type.getResource("/" + entry) : null);
    }

    static Archive owner(String entry, URL location, URL resource) {
        Path path = local(location);
        if (path != null) return new Archive(path, "class", entry);
        path = local(resource);
        if (path != null) return new Archive(path, "resource", entry);
        throw fault("No local archive owns " + entry);
    }

    private static Path local(URL location) {
        if (location == null) return null;
        try {
            if ("jar".equals(location.getProtocol())) {
                URLConnection connection = location.openConnection();
                if (!(connection instanceof JarURLConnection)) return null;
                location = ((JarURLConnection) connection).getJarFileURL();
            }
            if (!"file".equals(location.getProtocol()) || (location.getAuthority() != null && !location.getAuthority().isEmpty())) return null;
            Path path = Paths.get(location.toURI());
            return path.isAbsolute() ? path.normalize() : null;
        } catch (IOException | java.net.URISyntaxException | IllegalArgumentException failure) { return null; }
    }

    private static JsonObject describe(Archive archive, Jobs.Fault failure, Map<String, JsonObject> checked) {
        if (failure != null || archive == null) return object("valid", false, "error", error(failure == null ? fault("Missing artifact") : failure));
        String key = archive.path + "\0" + Arrays.toString(archive.entries);
        JsonObject status = checked.get(key);
        if (status == null) {
            try { status = object("valid", true, "entry", archive.check()); }
            catch (Jobs.Fault invalid) { status = object("valid", false, "error", error(invalid)); }
            checked.put(key, status);
        }
        JsonObject result = object("path", archive.path.toString(), "via", archive.via);
        status.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        return result;
    }

    private static JsonObject error(Jobs.Fault failure) { return object("code", failure.code, "message", failure.getMessage()); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("mod_source_missing", message); }

    static void requirePlain(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !path.toAbsolutePath().normalize().equals(path.toRealPath())) {
            throw new IOException("Export paths must not be links or junctions: " + path);
        }
    }

    static String hash(Path path) throws IOException {
        requirePlain(path);
        if (!Files.isRegularFile(path)) throw new IOException("Mod input must be a file: " + path);
        MessageDigest digest = CanonicalJson.sha256();
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) { Jobs.checkpoint(); digest.update(buffer, 0, count); }
        }
        return CanonicalJson.hex(digest.digest());
    }

    static final class Archive {
        final Path path; final String via; final String[] entries;
        Archive(Path path, String via, String... entries) { this.path = path; this.via = via; this.entries = entries.clone(); }
        String check() {
            try {
                requirePlain(path);
                if (!Files.isRegularFile(path)) throw new IOException("Source is not a regular file");
                if (entries.length == 0) return null;
                try (ZipFile jar = new ZipFile(path.toFile())) {
                    for (String name : entries) {
                        ZipEntry entry = jar.getEntry(name);
                        if (entry == null || entry.isDirectory() || entry.getSize() < 4 || entry.getSize() > 8 * 1024 * 1024) continue;
                        try (InputStream input = jar.getInputStream(entry)) {
                            if (input.read() == 0xca && input.read() == 0xfe && input.read() == 0xba && input.read() == 0xbe) return name;
                        }
                    }
                }
                throw new IOException("Archive does not contain its declared class: " + String.join(", ", entries));
            } catch (IOException failure) { throw new Jobs.Fault("mod_source_invalid", "Invalid source from " + via + " at " + path + ": " + failure.getMessage()); }
        }
        JsonObject describe() { return object("valid", true, "path", path.toString(), "via", via, "entry", check()); }
    }

    static final class Core {
        final String plugin; final Archive source; final Jobs.Fault error;
        Core(String plugin, Archive source, Jobs.Fault error) { this.plugin = plugin; this.source = source; this.error = error; }
    }

    private static final class Mod {
        final String id, name, version, container, plugin;
        final Archive source; final Jobs.Fault error;
        Mod(ModContainer mod, Archive source, Jobs.Fault error, String plugin) {
            id = mod.getModId(); name = mod.getName(); version = mod.getVersion(); container = unwrap(mod).getClass().getName();
            this.source = source; this.error = error; this.plugin = plugin;
        }
    }
}
