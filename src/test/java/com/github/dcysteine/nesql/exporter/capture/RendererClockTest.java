package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.RendererClock;
import com.github.dcysteine.nesql.exporter.core.RendererTransformer;
import org.objectweb.asm.*;

/** No world: exercise executable clock redirection, nested scopes and failure cleanup. */
public final class RendererClockTest {
    public static void run() throws Exception {
        String rocket = "micdoodle8.mods.galacticraft.core.client.render.item.ItemRendererTier1Rocket";
        byte[] input = fixture(rocket);
        RendererTransformer transformer = new RendererTransformer();
        byte[] output = transformer.transform(rocket, rocket, input);
        Class<?> fixture = new ClassLoader(RendererClockTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(rocket, output, 0, output.length); }
        }.define();
        require(RendererClock.instrumented(rocket), "Clock hook was not installed");
        try (RendererClock.Scope scope = RendererClock.at(150, 1000)) {
            require((long) fixture.getMethod("time").invoke(null) == 7500, "Sys clock lost native units");
            require(RendererClock.millis() == 7500, "Millis clock lost native speed");
            try (RendererClock.Scope inner = RendererClock.at(3, 1000)) {
                require(RendererClock.sysTime() == 150, "Nested clock did not advance");
                throw new IllegalStateException("native failure");
            } catch (IllegalStateException expected) { }
            require(RendererClock.sysTime() == 7500, "Exception leaked nested phase");
            long[] other = {-1}; Thread thread = new Thread(() -> other[0] = RendererClock.active() ? 1 : 0);
            thread.start(); thread.join(); require(other[0] == 0, "Clock escaped the capture thread");
        }
        require(!RendererClock.active(), "Clock leaked after capture");
        require(transformer.transform("other.Renderer", "other.Renderer", input) == input, "Unreviewed renderer was modified");
        require(java.util.Arrays.equals(output, transformer.transform(rocket, rocket, output)), "Transformer is not idempotent");
        require(RendererClock.cycleTicks(10800, 1000) == 216, "Rocket period/speed changed");
        require(RendererClock.cycleTicks(23040, 1000) == 2304, "Earth 64ms steps must close at exact 50ms boundaries");
        require(RendererClock.cycleTicks(10800, 1000000) == 27, "Sys timer resolution was assumed to be milliseconds");
        require(RendererClock.profile("micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererBeamReceiver") == null,
                "Entity-only animation was added to an inventory icon");
        try (RendererClock.Scope scope = RendererClock.at(17, 1000)) {
            require(RendererClock.entityTick(999) == 17, "Inventory pulse did not use its native tick");
        }
        require(RendererClock.entityTick(999) == 999, "Normal player tick was changed");
        try (RendererClock.Scope scope = RendererClock.at(2147483648L, 1000)) {
            require(RendererClock.entityTick(999) == Integer.MIN_VALUE, "Long shader clocks lost native signed player-tick wrapping");
        }
        require(RendererClock.profile("fox.spiteful.avaritia.render.CosmicRenderShenanigans$1") == null,
                "A non-finite shader clock became a short looping profile");
        require(RendererClock.cycleTicks(RendererClock.profile("witchinggadgets.client.render.ItemRenderPrimordialGauntlet").units, 1000) == 32,
                "Gauntlet inventory color/rune cycle must be 32 ticks");
        nativeBytes(transformer);
        InventoryTraceTest.run();
        RendererRandomTest.run();
        VisualGraphTest.run();
        NativeSamplersTest.run();
        InventoryDrawTraceTest.run();
        ForestryTraceTest.run();
        ModelDisplayListsTest.run();
        NativeAuthoritiesTest.run();
    }
    private static void nativeBytes(RendererTransformer transformer) throws Exception {
        String root = System.getProperty("nesql.animationMods");
        if (root == null) return;
        String[][] files = {
            {"Galacticraft-3.3.13-GTNH.jar", "micdoodle8.mods.galacticraft.core.client.render.item.ItemRendererTier1Rocket"},
            {"Galacticraft-3.3.13-GTNH.jar", "micdoodle8.mods.galacticraft.planets.mars.client.render.item.ItemRendererTier2Rocket"},
            {"Galacticraft-3.3.13-GTNH.jar", "micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererTier3Rocket"},
            {"Galacticraft-3.3.13-GTNH.jar", "micdoodle8.mods.galacticraft.planets.asteroids.client.render.item.ItemRendererAstroMiner"},
            {"GalaxySpace-1.1.121-GTNH.jar", "galaxyspace.core.render.item.ItemRendererRocket"},
            {"AmunRa-GC-0.8.2.jar", "de.katzenpapst.amunra.client.renderer.item.ItemRendererShuttle"},
            {"Draconic-Evolution-1.4.27-GTNH.jar", "com.brandon3055.draconicevolution.client.render.block.RenderEarthItem"},
            {"WitchingGadgets-1.7.25-GTNH.jar", "witchinggadgets.client.render.ItemRenderPrimordialGauntlet"},
            {"appliedenergistics2-rv3-beta-695-GTNH.jar", "appeng.client.render.blocks.RenderBlockSkyCompass"},
            {"Avaritia-1.77.jar", "fox.spiteful.avaritia.render.CosmicRenderShenanigans$1"},
            {"eternalsingularity-1.2.3.jar", "singulariteam.eternalsingularity.render.CosmicRenderStuffs$1"}
        };
        for (String[] file : files) try (java.util.zip.ZipFile jar = new java.util.zip.ZipFile(new java.io.File(root, file[0]))) {
            byte[] bytes;
            try (java.io.InputStream stream = jar.getInputStream(jar.getEntry(file[1].replace('.', '/') + ".class"))) {
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(); byte[] block = new byte[8192]; int n;
                while ((n = stream.read(block)) != -1) buffer.write(block, 0, n); bytes = buffer.toByteArray();
            }
            java.util.List<String> before = instructions(bytes, false);
            byte[] rewritten = transformer.transform(file[1], file[1], bytes);
            require(!java.util.Arrays.equals(bytes, rewritten), "Installed renderer lost its expected clock: " + file[1]);
            require(before.equals(instructions(rewritten, true)), "Native model/geometry/branch changed: " + file[1]);
            require(java.util.Arrays.equals(rewritten, transformer.transform(file[1], file[1], rewritten)), "Repeated hook changed native bytecode: " + file[1]);
        }
        System.out.println("Native animation: reviewed installed renderer/callback bytecode bodies preserved except scoped clock reads");
    }
    private static java.util.List<String> instructions(byte[] bytes, boolean normalize) {
        java.util.List<String> result = new java.util.ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                result.add(name + descriptor);
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (normalize && owner.endsWith("/RendererClock")) {
                            if (name.equals("entityTick")) return; // original GETFIELD is preserved
                            owner = name.equals("sysTime") ? "org/lwjgl/Sys" : "java/lang/System";
                            name = name.equals("sysTime") ? "getTime" : "currentTimeMillis";
                        }
                        result.add(op + owner + name + desc + itf);
                    }
                    @Override public void visitInsn(int op) { result.add("i"+op); }
                    @Override public void visitIntInsn(int op, int operand) { result.add("n"+op+":"+operand); }
                    @Override public void visitVarInsn(int op, int variable) { result.add("v"+op+":"+variable); }
                    @Override public void visitTypeInsn(int op, String type) { result.add("t"+op+":"+type); }
                    @Override public void visitFieldInsn(int op, String owner, String name, String desc) { result.add("f"+op+owner+name+desc); }
                    @Override public void visitLdcInsn(Object constant) { result.add("c"+constant); }
                    @Override public void visitJumpInsn(int op, Label target) { result.add("j"+op); }
                    @Override public void visitIincInsn(int variable, int increment) { result.add("inc"+variable+":"+increment); }
                    @Override public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) { result.add("s"+min+":"+max); }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }
    static byte[] fixture(String name) {
        ClassWriter writer = new ClassWriter(0); writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "time", "()J", null, null);
        method.visitCode(); method.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/Sys", "getTime", "()J", false);
        method.visitInsn(Opcodes.LRETURN); method.visitMaxs(2, 0); method.visitEnd(); writer.visitEnd(); return writer.toByteArray();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
