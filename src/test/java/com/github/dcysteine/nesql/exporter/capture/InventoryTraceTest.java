package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.core.InventoryTrace;
import com.github.dcysteine.nesql.exporter.core.RendererTransformer;
import org.objectweb.asm.*;

/** A single no-GL contract for the scoped inventory entry hook. */
public final class InventoryTraceTest {
    public static void run() throws Exception {
        String name = "micdoodle8.mods.galacticraft.core.client.render.item.ItemRendererKey";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode(); init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN); init.visitMaxs(1, 1); init.visitEnd();
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "renderItem",
                "(Lnet/minecraftforge/client/IItemRenderer$ItemRenderType;Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V", null, null);
        method.visitCode(); method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 4); method.visitEnd();
        MethodVisitor time = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "time", "()J", null, null);
        time.visitCode(); time.visitMethodInsn(Opcodes.INVOKESTATIC, "org/lwjgl/Sys", "getTime", "()J", false);
        time.visitInsn(Opcodes.LRETURN); time.visitMaxs(2, 0); time.visitEnd(); writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        RendererTransformer transformer = new RendererTransformer();
        byte[] patched = transformer.transform(name, name, bytes);
        require(!java.util.Arrays.equals(bytes, patched), "Native inventory entry was not instrumented");
        require(java.util.Arrays.equals(patched, transformer.transform(name, name, patched)), "Entry hook is not idempotent");
        require(transformer.transform("unreviewed.Renderer", "unreviewed.Renderer", bytes) == bytes, "Unreviewed renderer changed");
        Class<?> fixture = new ClassLoader(InventoryTraceTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(name, patched, 0, patched.length); }
        }.define();
        Object instance = fixture.newInstance(); int[] observed = {0, 0};
        try (InventoryTrace.Scope scope = InventoryTrace.observe(new InventoryTrace.Observer() {
            @Override public void enter(Object renderer, Object type, Object stack) { require(renderer == instance, "Patched entry lost this"); observed[0]++; }
            @Override public void exit(Object renderer) { require(renderer == instance, "Patched return lost this"); observed[1]++; }
        }); com.github.dcysteine.nesql.exporter.core.RendererClock.Scope clock = com.github.dcysteine.nesql.exporter.core.RendererClock.at(3, 1000)) {
            fixture.getMethod("renderItem", net.minecraftforge.client.IItemRenderer.ItemRenderType.class,
                    net.minecraft.item.ItemStack.class, Object[].class).invoke(instance, null, null, new Object[0]);
            require((long) fixture.getMethod("time").invoke(null) == 150, "Private/native inventory clock was not scoped");
        }
        require(observed[0] == 1 && observed[1] == 1, "Patched renderer did not expose one completed call");
        int[] calls = {0}; Object marker = new Object();
        InventoryTrace.enter(marker, null, null);
        try (InventoryTrace.Scope outer = InventoryTrace.observe((renderer, type, stack) -> {
            require(renderer == marker, "Entry identity changed"); calls[0]++;
        })) {
            InventoryTrace.enter(marker, null, null);
            try (InventoryTrace.Scope inner = InventoryTrace.observe((renderer, type, stack) -> { throw new IllegalStateException("native failure"); })) {
                try { InventoryTrace.enter(marker, null, null); throw new AssertionError("Failure was swallowed"); }
                catch (IllegalStateException expected) { }
            }
            InventoryTrace.enter(marker, null, null);
            Thread thread = new Thread(() -> InventoryTrace.enter(marker, null, null)); thread.start(); thread.join();
        }
        InventoryTrace.enter(marker, null, null);
        require(calls[0] == 2, "Entry observer leaked across nested scope, failure, thread or close");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
