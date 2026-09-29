package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;

/** Native icon quads through the production FBO, without starting a game or loading a world. */
public final class IconsTest {
    public static void run() throws Exception {
        if (!(IconsTest.class.getClassLoader() instanceof net.minecraft.launchwrapper.LaunchClassLoader)) {
            java.net.URL[] urls = java.util.Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                    .map(java.io.File::new).map(java.io.File::toURI).map(uri -> {
                        try { return uri.toURL(); } catch (java.net.MalformedURLException error) { throw new IllegalStateException(error); }
                    }).toArray(java.net.URL[]::new);
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            java.io.PrintStream out = System.out, err = System.err;
            try (net.minecraft.launchwrapper.LaunchClassLoader loader = new net.minecraft.launchwrapper.LaunchClassLoader(urls)) {
                // The caller owns the native context; do not load a second LWJGL instance.
                loader.addClassLoaderExclusion("org.lwjgl.");
                Thread.currentThread().setContextClassLoader(loader);
                try { loader.loadClass(IconsTest.class.getName()).getMethod("run").invoke(null); }
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
        cpw.mods.fml.common.Loader.injectData("7", "99", "40", "1614", "1.7.10", "9.05", new java.io.File("."), java.util.Collections.emptyList());
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLRelaunchLog.class, null,
                cpw.mods.fml.relauncher.Side.CLIENT, "side");
        net.minecraft.init.Bootstrap.func_151354_b();
        // Framebuffer reads only this setting. Avoid the client constructor (window, threads, game startup).
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        sun.misc.Unsafe allocator = (sun.misc.Unsafe) access.get(null);
        Field singleton = Minecraft.class.getDeclaredField("theMinecraft");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        Minecraft client = (Minecraft) allocator.allocateInstance(Minecraft.class);
        client.gameSettings = (GameSettings) allocator.allocateInstance(GameSettings.class);
        client.gameSettings.fboEnable = true;
        singleton.set(null, client);
        try {
            OpenGlHelper.initializeTextures();
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            ByteBuffer texel = BufferUtils.createByteBuffer(4);
            texel.put(new byte[] {(byte) 255, 0, 0, (byte) 255}).flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, texel);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            TextureAtlasSprite sprite = new TextureAtlasSprite("nesql:test") {};
            sprite.setIconWidth(1); sprite.setIconHeight(1); sprite.initSprite(1, 1, 0, 0, false);
            RenderItem renderer = new RenderItem();
            try (Images images = new Images()) {
                for (int i = 0; i < 3; i++) {
                    final boolean overlay = i == 1;
                    byte[] pixels = images.icon("test:native-item", () -> {
                        GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_BLEND);
                        GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
                        GL11.glEnable(GL11.GL_DEPTH_TEST);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                        // NEI adds 100; RenderItem.renderItemAndEffectIntoGUI adds another 50.
                        renderer.zLevel = 150;
                        renderer.renderIcon(0, 0, sprite, 16, 16);
                        if (overlay) {
                            // A visible durability bar must not be mistaken for a visible item body.
                            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_TEXTURE_2D);
                            GL11.glColor4f(0, 1, 0, 1);
                            GL11.glBegin(GL11.GL_QUADS);
                            GL11.glVertex3f(0, 13, 100); GL11.glVertex3f(0, 15, 100);
                            GL11.glVertex3f(16, 15, 100); GL11.glVertex3f(16, 13, 100);
                            GL11.glEnd();
                        }
                    });
                    int visible = 0;
                    for (int p = 3; p < pixels.length; p += 4) if ((pixels[p] & 255) != 0) visible++;
                    if (visible != 4096) throw new AssertionError("Native item body clipped: " + visible + "/4096 pixels");
                    int center = (32 * 64 + 32) * 4;
                    if ((pixels[center] & 255) != 255 || pixels[center + 1] != 0)
                        throw new AssertionError("Item body must be red even when its overlay is visible");
                    int bar = (8 * 64 + 32) * 4;
                    if (overlay && ((pixels[bar + 1] & 255) != 255 || pixels[bar] != 0))
                        throw new AssertionError("Overlay lost while restoring the item body");
                }
            } finally { GL11.glDeleteTextures(texture); }
            System.out.println("Native icon body at NEI depth survives repeated FBO capture");
        } finally { singleton.set(null, previous); }
    }
}
