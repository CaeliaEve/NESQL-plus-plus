package com.github.dcysteine.nesql.exporter.capture;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.InjectedModContainer;
import cpw.mods.fml.common.MCPDummyContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import com.github.dcysteine.nesql.exporter.task.Jobs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exercises real archive lookup, including launchers without a class CodeSource. */
public final class EnvironmentTest {
    private EnvironmentTest() {}
    public static void run(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path placeholder = directory.resolve("minecraft.jar");
        Files.write(placeholder, new byte[] {1, 2, 3});
        ModMetadata metadata = new ModMetadata(); metadata.modId = "mcp"; metadata.version = "9.05";
        InjectedModContainer mcp = new InjectedModContainer(new MCPDummyContainer(metadata), placeholder.toFile());
        Path loaded = Paths.get(Minecraft.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String property = System.getProperty("minecraft.client.jar");
        try {
            System.clearProperty("minecraft.client.jar");
            Path actual = Sources.resolve(mcp, java.util.Collections.emptyMap(), Sources.client()).path;
            if (!Files.isSameFile(actual, loaded) || Files.isSameFile(actual, placeholder)) throw new AssertionError("MCP used its fictitious source path");
            Sources.Archive ordinaryLoader = Sources.archive(null, loaded.toUri().toURL(), null);
            if (!ordinaryLoader.check().equals("net/minecraft/client/Minecraft.class")) throw new AssertionError("Named game classes were rejected");

            Path launched = clientJar(directory.resolve("GT New Horizons 客户端.jar"), "bao.class");
            Sources.Archive launcher = Sources.archive(launched.toString(), null, null);
            if (!Files.isSameFile(launcher.path, launched) || !launcher.via.equals("launcher") || !launcher.check().equals("bao.class")) {
                throw new AssertionError("A launcher client jar with no CodeSource was rejected");
            }
            System.setProperty("minecraft.client.jar", launched.toString());
            if (!Files.isSameFile(Sources.resolve(mcp, java.util.Collections.emptyMap(), Sources.client()).path, launched)) throw new AssertionError("Runtime launcher property was ignored");
            URL resource = new URL("jar:" + launched.toUri().toASCIIString() + "!/bao.class");
            Sources.Archive jarLocation = Sources.archive(null, resource, null);
            if (!Files.isSameFile(jarLocation.path, launched)) throw new AssertionError("Jar CodeSource URL was not decoded");
            Sources.Archive resourceLocation = Sources.archive(null, new URL("https://invalid.example/client"), resource);
            if (!Files.isSameFile(resourceLocation.path, launched) || !resourceLocation.via.equals("resource")) throw new AssertionError("Local class resource was not used");
            if (!launcher.describe().get("valid").getAsBoolean()) throw new AssertionError("Client preflight did not validate the archive");

            for (String invalid : new String[] {"", "minecraft.jar", "https://invalid.example/client.jar"}) {
                rejected("mod_source_invalid", () -> Sources.archive(invalid, loaded.toUri().toURL(), resource));
            }
            rejected("mod_source_missing", () -> Sources.archive(null, null, null));
            rejected("mod_source_missing", () -> Sources.archive(null, new URL("https://invalid.example/client"), null));
            rejected("mod_source_invalid", () -> Sources.archive(placeholder.toString(), loaded.toUri().toURL(), null).check());
            rejected("mod_source_invalid", () -> Sources.archive(directory.resolve("missing-client.jar").toString(), loaded.toUri().toURL(), null).check());
            rejected("mod_source_invalid", () -> Sources.archive(directory.toString(), null, null).check());
            Path other = clientJar(directory.resolve("other.jar"), "another/Main.class");
            rejected("mod_source_invalid", () -> Sources.archive(other.toString(), null, null).check());
            Path invalidClass = directory.resolve("invalid-class.jar");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(invalidClass))) {
                zip.putNextEntry(new ZipEntry("bao.class")); zip.write(new byte[] {1, 2, 3, 4}); zip.closeEntry();
            }
            rejected("mod_source_invalid", () -> Sources.archive(invalidClass.toString(), null, null).check());

            // Optional local acceptance evidence uses the user's actual client jar,
            // with precisely the missing-CodeSource metadata reported by the game.
            String runtime = System.getenv("NESQL_TEST_CLIENT");
            if (runtime != null) {
                Sources.Archive external = Sources.archive(runtime, null, null);
                System.out.println("Verified launcher client archive: " + external.path + " [" + external.check() + "]");
            }
        } finally {
            if (property == null) System.clearProperty("minecraft.client.jar");
            else System.setProperty("minecraft.client.jar", property);
        }
        coreSources(directory, mcp);
        ModMetadata ordinary = new ModMetadata(); ordinary.modId = "fixture";
        InjectedModContainer mod = new InjectedModContainer(new DummyModContainer(ordinary), placeholder.toFile());
        if (!Sources.resolve(mod, java.util.Collections.emptyMap(), null).path.equals(placeholder.toAbsolutePath().normalize())) throw new AssertionError("Ordinary mod source changed");
        Path missing = directory.resolve("missing.jar");
        mod = new InjectedModContainer(new DummyModContainer(ordinary), missing.toFile());
        try { Sources.hash(Sources.resolve(mod, java.util.Collections.emptyMap(), null).path); throw new AssertionError("Missing ordinary mod source was hidden"); }
        catch (IOException expected) { /* missing files remain an export failure */ }
    }

    private static void coreSources(Path directory, ModContainer mcp) throws Exception {
        Path coreFile = directory.resolve("core.jar"); Files.write(coreFile, new byte[] {7, 8, 9});
        Path misleading = directory.resolve("minecraft.jar");
        ModContainer core = new InjectedModContainer(new TestContainer(), misleading.toFile());
        Object wrapper = wrapper(TestContainer.class.getName(), coreFile.toFile());
        Map<String, Sources.Core> registry = Sources.cores(Collections.singletonList(wrapper));
        Sources.Archive selected = Sources.resolve(core, registry, Sources.client());
        if (!selected.path.equals(coreFile.toAbsolutePath().normalize()) || !selected.via.equals("coremod")) {
            throw new AssertionError("Injected core container ignored Forge's registered artifact");
        }
        Sources first = new Sources(Arrays.asList(mcp, core), registry);
        JsonArray fingerprints = first.fingerprints();
        String coreHash = digest(fingerprints, "<fixture ASM>"), clientHash = digest(fingerprints, "mcp");
        if (coreHash.equals(clientHash) || !coreHash.equals(Sources.hash(coreFile))) throw new AssertionError("Core fingerprint was replaced by the client fingerprint");
        Files.write(coreFile, new byte[] {9, 8, 7, 6});
        JsonArray changed = new Sources(Arrays.asList(mcp, core), registry).fingerprints();
        if (coreHash.equals(digest(changed, "<fixture ASM>")) || !clientHash.equals(digest(changed, "mcp"))) {
            throw new AssertionError("Changing the core jar did not change only its own fingerprint");
        }
        Sources.cores(Arrays.asList(wrapper, wrapper));
        rejected("mod_source_conflict", () -> Sources.cores(Arrays.asList(wrapper, wrapper(TestContainer.class.getName(), misleading.toFile()))));
        Sources missing = new Sources(Arrays.asList(mcp, core),
                Sources.cores(Collections.singletonList(wrapper(TestContainer.class.getName(), directory.resolve("absent-core.jar").toFile()))));
        rejected("mod_sources_invalid", missing::fingerprints);
        JsonObject coreReport = missing.inspect().getAsJsonObject("sources");
        if (coreReport.get("valid").getAsBoolean()) throw new AssertionError("Missing registered core source was hidden");

        List<ModContainer> bad = Arrays.asList(ordinary("missing-a", directory.resolve("absent-a.jar")),
                ordinary("missing-b", directory.resolve("absent-b.jar")));
        Sources batch = new Sources(bad, Collections.emptyMap());
        JsonArray rows = batch.inspect().getAsJsonObject("sources").getAsJsonArray("rows");
        if (rows.size() != 2 || rows.get(0).getAsJsonObject().get("valid").getAsBoolean()
                || rows.get(1).getAsJsonObject().get("valid").getAsBoolean()) throw new AssertionError("Preflight did not report every missing source");
        rejected("mod_sources_invalid", batch::fingerprints);

        String runtimeMods = System.getenv("NESQL_TEST_MODS");
        if (runtimeMods != null) {
            for (String[] example : new String[][] {
                    {"CoFHCore-[1.7.10]3.1.4-329.jar", "cofh/asm/LoadingPlugin$CoFHDummyContainer.class"},
                    {"CodeChickenCore-1.4.10.jar", "codechicken/core/asm/CodeChickenCoreModContainer.class"}}) {
                Path jar = Paths.get(runtimeMods).resolve(example[0]);
                URL resource = new URL("jar:" + jar.toUri().toASCIIString() + "!/" + example[1]);
                Sources.Archive owned = Sources.owner(example[1], null, resource);
                if (!Files.isSameFile(owned.path, jar) || !owned.check().equals(example[1])) throw new AssertionError("Real core container archive was not resolved");
                Map<String, Sources.Core> actual = Sources.cores(Collections.singletonList(wrapper(example[1].replace('/', '.').replace(".class", ""), jar.toFile())));
                Sources.Core binding = actual.values().iterator().next();
                if (!Files.isSameFile(binding.source.path, jar)) throw new AssertionError("Real core registration lost its jar");
                System.out.println("Verified core archive: " + jar + " [" + owned.check() + "] " + Sources.hash(jar));
            }
        }
    }

    private static ModContainer ordinary(String id, Path file) {
        ModMetadata metadata = new ModMetadata(); metadata.modId = id; metadata.name = id; metadata.version = "1";
        return new DummyModContainer(metadata) { @Override public File getSource() { return file.toFile(); } };
    }

    private static String digest(JsonArray rows, String id) {
        for (com.google.gson.JsonElement value : rows) if (value.getAsJsonObject().get("id").getAsString().equals(id)) {
            return value.getAsJsonObject().get("sha256").getAsString();
        }
        throw new AssertionError("Missing fingerprint: " + id);
    }

    private static Object wrapper(String container, File file) throws Exception {
        Class<?> type = Class.forName("cpw.mods.fml.relauncher.CoreModManager$FMLPluginWrapper");
        Constructor<?> constructor = type.getDeclaredConstructor(String.class, IFMLLoadingPlugin.class, File.class, int.class, String[].class);
        constructor.setAccessible(true);
        return constructor.newInstance("fixture", new TestPlugin(container), file, 0, new String[0]);
    }

    public static final class TestContainer extends DummyModContainer {
        public TestContainer() { super(metadata()); }
        private static ModMetadata metadata() {
            ModMetadata result = new ModMetadata(); result.modId = "<fixture ASM>"; result.name = "Fixture core"; result.version = "1"; return result;
        }
    }
    private static final class TestPlugin implements IFMLLoadingPlugin {
        private final String container;
        TestPlugin(String container) { this.container = container; }
        @Override public String getModContainerClass() { return container; }
        @Override public String[] getASMTransformerClass() { throw new AssertionError("Do not load transformers"); }
        @Override public String getSetupClass() { throw new AssertionError("Do not run plugin setup"); }
        @Override public void injectData(Map<String, Object> data) { throw new AssertionError("Do not reinject core plugins"); }
        @Override public String getAccessTransformerClass() { throw new AssertionError("Do not load access transformers"); }
    }

    private static Path clientJar(Path file, String entry) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file));
             InputStream source = Minecraft.class.getResourceAsStream("/net/minecraft/client/Minecraft.class")) {
            if (source == null) throw new IOException("Missing test class bytes");
            zip.putNextEntry(new ZipEntry(entry));
            byte[] buffer = new byte[8192]; int count;
            while ((count = source.read(buffer)) != -1) zip.write(buffer, 0, count);
            zip.closeEntry();
        }
        return file;
    }

    private static void rejected(String code, Checked action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid client source was accepted: " + code); }
        catch (Jobs.Fault failure) { if (!code.equals(failure.code)) throw new AssertionError("Wrong client source failure: " + failure.code); }
    }
    private interface Checked { void run() throws Exception; }
}
