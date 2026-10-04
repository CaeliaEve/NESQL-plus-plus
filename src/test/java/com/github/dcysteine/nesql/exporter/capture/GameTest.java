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
                if ("tconstruct".equals(System.getProperty("nesql.nativeFamily")) || "core-fixes-casting".equals(System.getProperty("nesql.nativeFamily")) || "scrapbox".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer("cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer");
                if ("core-fixes-casting".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$ExtraUtilitiesConfig");
                if ("inscriber".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$NbtListAccess");
                if ("soul".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$EnderRegistry");
                if ("space".equals(System.getProperty("nesql.nativeFamily")) || "circuits".equals(System.getProperty("nesql.nativeFamily")) || System.getProperty("nesql.nativeFamily", "").startsWith("fluid-icons"))
                    loader.registerTransformer(GameTest.class.getName() + "$OptionalApis");
                if ("core-fixes-blast".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$Ic2Cells");
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

    /** Merge the two actual pinned Hodgepodge methods, as the pack's mixin does.
     * No body is reimplemented here; the test loads the derived native mixin jar.
     */
    public static final class Ic2Cells implements net.minecraft.launchwrapper.IClassTransformer {
        public byte[] transform(String name, String transformed, byte[] bytes) {
            if (!"ic2.core.item.resources.ItemCell".equals(name)) return bytes;
            try (java.io.InputStream in = getClass().getClassLoader().getResourceAsStream(
                    "com/mitchej123/hodgepodge/mixins/late/ic2/MixinIC2ItemCell.class")) {
                org.objectweb.asm.tree.ClassNode mixin = new org.objectweb.asm.tree.ClassNode();
                new org.objectweb.asm.ClassReader(in).accept(mixin, 0);
                org.objectweb.asm.tree.ClassNode target = new org.objectweb.asm.tree.ClassNode();
                new org.objectweb.asm.ClassReader(bytes).accept(target, 0);
                for (Object raw : mixin.methods) {
                    org.objectweb.asm.tree.MethodNode method = (org.objectweb.asm.tree.MethodNode) raw;
                    if (!method.name.equals("hasContainerItem") && !method.name.equals("getContainerItem")) continue;
                    for (org.objectweb.asm.tree.AbstractInsnNode insn : method.instructions.toArray())
                        if (insn instanceof org.objectweb.asm.tree.MethodInsnNode) {
                            org.objectweb.asm.tree.MethodInsnNode call = (org.objectweb.asm.tree.MethodInsnNode) insn;
                            if (call.owner.equals(mixin.name)) call.owner = target.name;
                        }
                    target.methods.add(method);
                }
                org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0); target.accept(writer); return writer.toByteArray();
            } catch (java.io.IOException error) { throw new IllegalStateException(error); }
        }
    }

    /** Config-only fixture: callbacks are unchanged; a test must not initialize a whole mod. */
    public static final class ExtraUtilitiesConfig implements net.minecraft.launchwrapper.IClassTransformer {
        public byte[] transform(String name,String transformed,byte[] bytes) {
            if (!"com.rwtema.extrautils.ExtraUtils".equals(name)) return bytes;
            org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader(bytes).accept(node,0);
            node.fields.removeIf(raw -> !((org.objectweb.asm.tree.FieldNode)raw).name.startsWith("tcon_"));
            node.methods.clear();node.interfaces.clear();node.superName="java/lang/Object";
            org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(0);node.accept(writer);return writer.toByteArray();
        }
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
