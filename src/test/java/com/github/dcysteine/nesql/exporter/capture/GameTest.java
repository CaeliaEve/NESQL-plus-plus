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
                if ("tconstruct".equals(System.getProperty("nesql.nativeFamily")) || "scrapbox".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer("cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer");
                if ("inscriber".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$NbtListAccess");
                if ("soul".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$EnderRegistry");
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
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(Loader.class,Loader.instance(),new java.util.HashMap<String,cpw.mods.fml.common.ModContainer>(),"namedMods");
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

    /** The isolated dev jar lacks pack access transforms. Expose only the list field read by native AE2. */
    public static final class NbtListAccess implements net.minecraft.launchwrapper.IClassTransformer {
        @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
            if (bytes == null || !name.equals("net.minecraft.nbt.NBTTagList")) return bytes;
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
            for (Object value : node.fields) {
                org.objectweb.asm.tree.FieldNode field = (org.objectweb.asm.tree.FieldNode) value;
                if (field.name.equals("tagList")) field.access = (field.access & ~6) | 1;
            }
            org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
            node.accept(writer); return writer.toByteArray();
        }
    }

    /** Only replace mod startup/registry initialization. Native item, recipe and XP methods stay intact. */
    public static final class EnderRegistry implements net.minecraft.launchwrapper.IClassTransformer {
        @Override public byte[] transform(String name,String transformedName,byte[] bytes) {
            boolean holder=name.equals("crazypants.enderio.EnderIO"), config=name.equals("crazypants.enderio.machine.spawner.PoweredSpawnerConfig");
            if(bytes==null||!holder&&!config)return bytes;
            org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node,0);
            if(holder){
                node.superName="java/lang/Object";node.interfaces.clear();node.methods.clear();
                java.util.Set<String> retained=new java.util.HashSet<>(Arrays.asList("DOMAIN","itemSoulVessel","itemBrokenSpawner","blockPoweredSpawner","fluidXpJuice","itemMaterial","itemFrankenSkull","itemEnderface"));
                node.fields.removeIf(value->!retained.contains(((org.objectweb.asm.tree.FieldNode)value).name));
                for(Object value:node.fields){org.objectweb.asm.tree.FieldNode f=(org.objectweb.asm.tree.FieldNode)value;f.access=9;if(f.name.equals("DOMAIN"))f.value="enderio";}
            }else{
                node.methods.removeIf(value->((org.objectweb.asm.tree.MethodNode)value).name.equals("<clinit>"));
                for(Object value:node.fields){org.objectweb.asm.tree.FieldNode f=(org.objectweb.asm.tree.FieldNode)value;if(f.name.equals("instance"))f.access=9;}
            }
            org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(0);node.accept(writer);return writer.toByteArray();
        }
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
