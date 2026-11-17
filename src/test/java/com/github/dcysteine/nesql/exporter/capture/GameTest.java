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
                if ("observed-callbacks-bracelet".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeObservedCallbacksTest.class.getName()+"$BraceletRoles");
                if ("observed-callbacks-focus-container".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeObservedCallbacksTest.class.getName()+"$FocusDefinitions");
                if (Boolean.getBoolean("nesql.nativeOreOptimized"))
                    loader.registerTransformer(NativeOreStorageTest.Hodgepodge.class.getName());
                if ("harvest-storage".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeHarvestStorageTest.class.getName()+"$Definitions");
                if ("forestry-container-canisters".equals(System.getProperty("nesql.nativeFamily")) || "forestry-container-automagy".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeSqueezerFamiliesTest.class.getName() + "$Definitions");
                if ("banner".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeBannerTest.class.getName() + "$Definitions");
                if ("cal-imprint".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeCalImprintTest.class.getName() + "$Definitions");
                if ("ae-remaining".equals(System.getProperty("nesql.nativeFamily")) || "ae-world-ore".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeAeRemainingTest.class.getName() + "$IsolatedDefinitions");
                if ("inscriber".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeAeRemainingTest.class.getName() + "$IsolatedDefinitions");
                if ("betterquesting".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(BetterQuestingTest.class.getName() + "$UnusedGuiConstants");
                if ("nei-information".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(InformationReferencesTest.class.getName() + "$NoInfoGuiInitialization");
                if ("usage-queries".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeUsageTest.class.getName() + "$NoGuiInitialization");
                if ("fireworks-fixed".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(FireworkReferencesTest.class.getName() + "$NativeAccess");
                if ("tool-materials".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeToolMaterialsTest.class.getName() + "$PartBootstrap");
                if ("witchery-cooking".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$WitcheryRegistryDefaults");
                if ("tconstruct".equals(System.getProperty("nesql.nativeFamily")) || "core-fixes-casting".equals(System.getProperty("nesql.nativeFamily")) || "scrapbox".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer("cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer");
                if ("nei-diagram-generators".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer("cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer");
                if ("gt-crafting-declarations".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeGtCraftingTest.class.getName() + "$ElectricDefinitions");
                if ("core-fixes-casting".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$ExtraUtilitiesConfig");
                if ("inscriber".equals(System.getProperty("nesql.nativeFamily")) || "matter-manipulator".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$NbtListAccess");
                if ("soul".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(GameTest.class.getName() + "$EnderRegistry");
                if ("genetics-roots".equals(System.getProperty("nesql.nativeFamily")))
                    loader.registerTransformer(NativeGeneticsTest.class.getName() + "$Defaults");
                if ("infusion-tools".equals(System.getProperty("nesql.nativeFamily")) || "space".equals(System.getProperty("nesql.nativeFamily")) || "galaxy-assembly".equals(System.getProperty("nesql.nativeFamily")) || "circuits".equals(System.getProperty("nesql.nativeFamily")) || "rail-wildcard".equals(System.getProperty("nesql.nativeFamily")) || System.getProperty("nesql.nativeFamily", "").startsWith("fluid-icons"))
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
        if(Boolean.getBoolean("nesql.nativeOreOptimized")){
            java.io.File directory=new java.io.File("build/native-tests/ore-storage-profile");
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalStateException("Cannot create isolated config directory");
            java.lang.reflect.Field home=cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
            home.setAccessible(true);home.set(null,directory);
        }
        Bootstrap.func_151354_b();
        Facts.Batch batch = new Facts.Batch();
        com.google.gson.JsonObject nativeRecord = com.github.dcysteine.nesql.exporter.source.Json.object("id", "fixture", "value", 1);
        batch.records.add(new Facts.Record("recipes", nativeRecord));
        batch.freezeRecords(); nativeRecord.addProperty("value", 2);
        if (batch.records.get(0).value.get("value").getAsInt() != 1) throw new AssertionError("Advancing a native cursor changed an earlier batch");
        if (Boolean.getBoolean("nesql.nativeFilterTests")) { NativeFilterTest.run(); return; }
        if (Boolean.getBoolean("nesql.nativeInfusionTests")) { NativeInfusionTest.run(); return; }
        if (Boolean.getBoolean("nesql.nativeAeTests")) { NativeAeTest.run(); return; }
        if (Boolean.getBoolean("nesql.nativeScanTests")) { NativeScanTest.run(); return; }
        if (Boolean.getBoolean("nesql.nativeStructureTests")) { CompatStructureTest.run(); return; }
        if (Boolean.getBoolean("nesql.nativeMachineTests")) { NativeMachinesTest.run(); return; }
        CluesTest.run();
        StructuresTest.run();
        PreviewTest.run();
        CompatStructureTest.run();
        FuelReferencesTest.run();
        FireworkReferencesTest.run();
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

    /** The isolated dev jar lacks pack ATs. Native AE2 needs tagList;
     * the installed Matter Manipulator mm_at.cfg also exposes tagMap. */
    public static final class NbtListAccess implements net.minecraft.launchwrapper.IClassTransformer {
        @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
            if (bytes == null || !(name.equals("net.minecraft.nbt.NBTTagList") || name.equals("net.minecraft.nbt.NBTTagCompound"))) return bytes;
            org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(bytes).accept(node, 0);
            for (Object value : node.fields) {
                org.objectweb.asm.tree.FieldNode field = (org.objectweb.asm.tree.FieldNode) value;
                if (field.name.equals("tagList") || field.name.equals("tagMap")) field.access = (field.access & ~6) | 1;
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
    /** Skip unrelated full-mod recipe registration in this isolated JVM only.
     * Real registry types and all lookup/execution methods remain unchanged. */
    public static final class WitcheryRegistryDefaults implements net.minecraft.launchwrapper.IClassTransformer {
        @Override public byte[] transform(String name,String transformedName,byte[] bytes){
            if(!name.equals("com.emoniph.witchery.brewing.WitcheryBrewRegistry"))return bytes;
            org.objectweb.asm.ClassWriter output=new org.objectweb.asm.ClassWriter(0);
            new org.objectweb.asm.ClassReader(bytes).accept(new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM5,output){
                @Override public org.objectweb.asm.MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
                    return name.equals("<clinit>")?null:super.visitMethod(access,name,descriptor,signature,exceptions);
                }
                @Override public void visitEnd(){
                    String owner="com/emoniph/witchery/brewing/WitcheryBrewRegistry";
                    org.objectweb.asm.MethodVisitor method=super.visitMethod(org.objectweb.asm.Opcodes.ACC_STATIC,"<clinit>","()V",null,null);
                    method.visitCode();method.visitLdcInsn(org.objectweb.asm.Type.getObjectType(owner));
                    method.visitMethodInsn(org.objectweb.asm.Opcodes.INVOKESTATIC,"com/github/dcysteine/nesql/exporter/capture/WitcheryTestSource","emptyBrewRegistry","(Ljava/lang/Class;)Ljava/lang/Object;",false);
                    method.visitTypeInsn(org.objectweb.asm.Opcodes.CHECKCAST,owner);
                    method.visitFieldInsn(org.objectweb.asm.Opcodes.PUTSTATIC,owner,"INSTANCE","L"+owner+";");
                    method.visitInsn(org.objectweb.asm.Opcodes.RETURN);method.visitMaxs(1,0);method.visitEnd();super.visitEnd();
                }
            },0);return output.toByteArray();
        }
    }
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
