package com.github.dcysteine.nesql.exporter.capture;

import cpw.mods.fml.common.Loader;
import net.minecraft.init.Bootstrap;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.util.Arrays;
import java.util.Collections;

/** One isolated native registry for capture regressions; no live game client or world is used. */
public final class GameTest {
    private GameTest() {}

    public static void run() throws Exception {
        if (!(GameTest.class.getClassLoader() instanceof LaunchClassLoader)) {
            java.net.URL[] urls = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                    .map(java.io.File::new).map(java.io.File::toURI).map(uri -> {
                        try { return uri.toURL(); } catch (java.net.MalformedURLException error) { throw new IllegalStateException(error); }
                    }).toArray(java.net.URL[]::new);
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            java.io.PrintStream out = System.out, err = System.err;
            try (LaunchClassLoader loader = new LaunchClassLoader(urls)) {
                Thread.currentThread().setContextClassLoader(loader);
                if ("tconstruct".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer("cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer");
                if ("space".equals(System.getProperty("nesql.nativeFamily")) || "circuits".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$OptionalApis");
                try { loader.loadClass(GameTest.class.getName()).getMethod("run").invoke(null); }
                catch (java.lang.reflect.InvocationTargetException error) {
                    if (error.getCause() instanceof Error) throw (Error) error.getCause();
                    throw (Exception) error.getCause();
                }
            } finally {
                Thread.currentThread().setContextClassLoader(previous);
                System.setOut(out); System.setErr(err);
            }
            return;
        }
        Loader.injectData("7", "99", "40", "1614", "1.7.10", "9.05", new java.io.File("."), Collections.emptyList());
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLRelaunchLog.class, null,
                cpw.mods.fml.relauncher.Side.CLIENT, "side");
        Bootstrap.func_151354_b();
        Facts.Batch batch = new Facts.Batch();
        com.google.gson.JsonObject nativeRecord = com.github.dcysteine.nesql.exporter.source.Json.object("id", "fixture", "value", 1);
        batch.records.add(new Facts.Record("recipes", nativeRecord));
        batch.freezeRecords(); nativeRecord.addProperty("value", 2);
        if (batch.records.get(0).value.get("value").getAsInt() != 1) throw new AssertionError("Advancing a native cursor changed an earlier batch");
        if (Boolean.getBoolean("nesql.nativeFilterTests")) { NativeFilterTest.run(); return; }
        if (Boolean.getBoolean("nesql.nativeInfusionTests")) { NativeInfusionTest.run(); return; }
        if (Boolean.getBoolean("nesql.nativeAeTests")) { NativeAeTest.run(); return; }
        if (Boolean.getBoolean("nesql.nativeMachineTests")) { NativeMachinesTest.run(); return; }
        CluesTest.run();
        StructuresTest.run();
        PreviewTest.run();
        ValuesTest.run();
        SlotsTest.run();
        CraftingTest.run();
    }

    /** Use FML's own @Optional processing in the isolated NASA test loader, without launching FML discovery. */
    public static final class OptionalApis implements net.minecraft.launchwrapper.IClassTransformer {
        @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
            if (bytes == null || !(name.startsWith("galaxyspace.") || name.startsWith("micdoodle8.") || name.startsWith("de.katzenpapst.amunra."))) return bytes;
            try {
                cpw.mods.fml.common.discovery.ASMDataTable table = new cpw.mods.fml.common.discovery.ASMDataTable();
                new cpw.mods.fml.common.discovery.asm.ASMModParser(new java.io.ByteArrayInputStream(bytes)).sendToTable(table,
                        new cpw.mods.fml.common.discovery.ModCandidate(new java.io.File("."), new java.io.File("."), cpw.mods.fml.common.discovery.ContainerType.JAR));
                cpw.mods.fml.common.asm.transformers.ModAPITransformer nativeTransformer = new cpw.mods.fml.common.asm.transformers.ModAPITransformer();
                nativeTransformer.initTable(table);
                return nativeTransformer.transform(name, transformedName, bytes);
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
    }
}
