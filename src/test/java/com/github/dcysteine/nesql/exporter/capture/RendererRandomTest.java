package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.RendererRandom;
import java.util.Random;
import com.github.dcysteine.nesql.exporter.core.InventoryTrace;
import com.github.dcysteine.nesql.exporter.core.RendererTransformer;
import org.objectweb.asm.*;

/** Uses the actual JDK Gaussian implementation as the oracle; no game or GL. */
public final class RendererRandomTest {
    public static void run() throws Exception {
        transformedCall();
        Random original = new Random(1234), oracle = new Random(1234), unchanged = new Random(1234);
        same(original.nextGaussian(), oracle.nextGaussian()); unchanged.nextGaussian();
        try (RendererRandom.Session session = new RendererRandom.Session()) {
            RendererRandom.Snapshot start = session.snapshot(original);
            require(start.haveGaussian, "The native cached Gaussian was discarded");
            try (RendererRandom.Scope scope = session.scope()) {
                for (int i = 0; i < 32; i++) same(RendererRandom.gaussian(original), oracle.nextGaussian());
                try (RendererRandom.Session inner = new RendererRandom.Session(); RendererRandom.Scope nested = inner.scope()) {
                    same(RendererRandom.gaussian(new Random(0)), new Random(0).nextGaussian());
                    throw new IllegalStateException("native failure");
                } catch (IllegalStateException expected) { }
                same(RendererRandom.gaussian(original), oracle.nextGaussian());
                long[] result = {0}; Thread thread = new Thread(() -> result[0] = Double.doubleToRawLongBits(RendererRandom.gaussian(new Random(7))));
                thread.start(); thread.join(); require(result[0] == Double.doubleToRawLongBits(new Random(7).nextGaussian()), "Random scope escaped its thread");
                int[] calls = {0}; Random unknown = new Random(0) { @Override public synchronized double nextGaussian() { calls[0]++; return 0; } };
                try { RendererRandom.gaussian(unknown); throw new AssertionError("Unknown random callback was executed"); }
                catch (IllegalArgumentException expected) { require(calls[0] == 0, "Unknown callback ran before admission"); }
            }
            require(session.snapshot(original) == start, "A renderer stream was reset per item or draw");
            same(original.nextGaussian(), unchanged.nextGaussian()); same(original.nextDouble(), unchanged.nextDouble());
            Random actualOther = new Random(0); require(session.snapshot(actualOther).stream != start.stream, "Independent native streams were merged");
            RendererRandom.Snapshot restored = RendererRandom.Snapshot.from(start.stream, start.seed, start.haveGaussian, start.gaussian);
            same(restored.copy().nextGaussian(), unchangedFrom(1234).nextGaussian());
        }
        RendererRandom.Session closed = new RendererRandom.Session(); closed.close(); closed.close();
        try { closed.scope(); throw new AssertionError("Closed random session was reopened"); } catch (IllegalStateException expected) { }
        Random source = new Random(42);long[] observed = {0};
        try (RendererRandom.Session session = new RendererRandom.Session(); RendererRandom.Scope random = session.scope();
             com.github.dcysteine.nesql.exporter.core.InventoryDrawTrace.Scope trace = com.github.dcysteine.nesql.exporter.core.InventoryDrawTrace.observe(new com.github.dcysteine.nesql.exporter.core.InventoryDrawTrace.Observer() {
                 @Override public void begin(Object tess,int mode) { }
                 @Override public void vertex(Object tess,double x,double y,double z) { }
                 @Override public void draw(Object tess) { }
                 @Override public void gaussian(Random actual,RendererRandom.Snapshot initial,long draw,double value) {
                     require(actual == source && initial.stream == 1 && draw == 1, "Pulse lost its native stream/draw identity");
                     same(value, new Random(42).nextGaussian());observed[0]++;
                 }
             })) { RendererRandom.gaussian(source); }
        require(observed[0] == 1, "Scoped Gaussian draw was not observed");
        sealedPosition();
        nativeBytes();
    }
    private static void sealedPosition() throws Exception {
        Random nativeRandom=new Random(77),unchanged=new Random(77);RendererRandom.State saved;
        try(RendererRandom.Session session=new RendererRandom.Session()){
            RendererRandom.Scope outer=session.scope(),inner=session.scope();
            try{outer.close();throw new AssertionError("Same-session scopes closed out of order");}catch(IllegalStateException expected){ }
            inner.close();outer.close();
            try(RendererRandom.Scope scope=session.scope()){for(int i=0;i<3;i++)RendererRandom.gaussian(nativeRandom);}
            saved=session.save();require(saved.positions().size()==1&&saved.positions().get(0).draws==3,"Sealed stream lost draw position");
        }
        try(RendererRandom.Session resumed=new RendererRandom.Session()){
            try{resumed.restore(saved,(source,initial)->{throw new IllegalStateException("authority drift");});throw new AssertionError("Unverified random authority was restored");}
            catch(IllegalStateException expected){ }
            resumed.restore(saved,(source,initial)->require(source==nativeRandom&&initial.stream==1,"Native stream binding changed"));
            Random oracle=saved.positions().get(0).current.copy();
            try(RendererRandom.Scope scope=resumed.scope()){for(int i=0;i<8;i++)same(RendererRandom.gaussian(nativeRandom),oracle.nextGaussian());}
            require(resumed.draws(nativeRandom)==11,"Recovered stream restarted its draw counter");
        }
        same(nativeRandom.nextGaussian(),unchanged.nextGaussian());
        int[] rejected={0};Thread other=new Thread(()->{
            try(RendererRandom.Session session=new RendererRandom.Session()){
                try{session.restore(saved,(source,initial)->{});}catch(IllegalStateException expected){rejected[0]++;}
            }
        });other.start();other.join();require(rejected[0]==1,"Random state crossed its native client thread");
    }
    private static void transformedCall() throws Exception {
        String name = InventoryTrace.ETERNAL; ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "renderItem",
                "(Lnet/minecraftforge/client/IItemRenderer$ItemRenderType;Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V", null, null);
        render.visitCode(); render.visitInsn(Opcodes.RETURN); render.visitMaxs(0, 4); render.visitEnd();
        MethodVisitor pulse = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "pulse", "(Ljava/util/Random;)D", null, null);
        pulse.visitCode(); pulse.visitVarInsn(Opcodes.ALOAD, 0);
        pulse.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Random", "nextGaussian", "()D", false);
        pulse.visitInsn(Opcodes.DRETURN); pulse.visitMaxs(2, 1); pulse.visitEnd(); writer.visitEnd();
        RendererTransformer transformer = new RendererTransformer(); byte[] bytes = transformer.transform(name, name, writer.toByteArray());
        require(java.util.Arrays.equals(bytes, transformer.transform(name, name, bytes)), "Random hook is not idempotent");
        Class<?> fixture = new ClassLoader(RendererRandomTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(name, bytes, 0, bytes.length); }
        }.define();
        java.lang.reflect.Method call = fixture.getMethod("pulse", Random.class);
        Random original = new Random(9);
        try (RendererRandom.Session session = new RendererRandom.Session(); RendererRandom.Scope scope = session.scope()) {
            same((double) call.invoke(null, original), new Random(9).nextGaussian());
        }
        same(original.nextGaussian(), new Random(9).nextGaussian());
        // Inactive hook must also preserve ordinary renderer state advancement.
        Random actual = new Random(5), expected = new Random(5);
        same((double) call.invoke(null, actual), expected.nextGaussian()); same(actual.nextDouble(), expected.nextDouble());
    }
    private static void nativeBytes() throws Exception {
        String root = System.getProperty("nesql.animationMods"); if (root == null) return;
        try (java.util.zip.ZipFile jar = new java.util.zip.ZipFile(new java.io.File(root, "eternalsingularity-1.2.3.jar"))) {
            byte[] bytes;
            try (java.io.InputStream input = jar.getInputStream(jar.getEntry(InventoryTrace.ETERNAL.replace('.', '/') + ".class"))) {
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream(); byte[] block = new byte[8192]; int count;
                while ((count = input.read(block)) != -1) output.write(block, 0, count); bytes = output.toByteArray();
            }
            RendererTransformer transformer = new RendererTransformer(); byte[] rewritten = transformer.transform(InventoryTrace.ETERNAL, InventoryTrace.ETERNAL, bytes);
            require(calls(bytes, false) == 1 && calls(rewritten, false) == 0 && calls(rewritten, true) == 1,
                    "Installed Eternal pulse changed its native RNG call site");
            require(java.util.Arrays.equals(rewritten, transformer.transform(InventoryTrace.ETERNAL, InventoryTrace.ETERNAL, rewritten)),
                    "Installed Eternal hook is not idempotent");
        }
    }
    private static int calls(byte[] bytes, boolean hooked) {
        int[] count = {0};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (hooked ? op == Opcodes.INVOKESTATIC && owner.equals("com/github/dcysteine/nesql/exporter/core/RendererRandom") && name.equals("gaussian") && desc.equals("(Ljava/util/Random;)D")
                                : op == Opcodes.INVOKEVIRTUAL && owner.equals("java/util/Random") && name.equals("nextGaussian") && desc.equals("()D")) count[0]++;
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES); return count[0];
    }
    private static Random unchangedFrom(long seed) { Random value = new Random(seed); value.nextGaussian(); return value; }
    private static void same(double a, double b) { require(Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b), "Native Gaussian bits changed"); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
