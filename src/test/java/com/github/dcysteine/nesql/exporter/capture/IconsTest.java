package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
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
            sprite.setFramesTextureData(java.util.Arrays.asList(new int[][] {new int[] {0xff0000ff}}, new int[][] {new int[] {0xff00ff00}}));
            // Replacing one stitched sprite must preserve the exact live texel,
            // even when the renderer throws after the atlas upload.
            RuntimeException failure = new RuntimeException("native draw failure");
            try (GlState state = new GlState("animation patch"); NativeAnimation.Patch patch = new NativeAnimation.Patch(texture, sprite)) {
                patch.upload(new int[][] {{0,1},{1,2}}, 1);
                ByteBuffer actual = BufferUtils.createByteBuffer(4);
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, actual);
                if ((actual.get(1)&255)!=255 || actual.get(0)!=0) throw new AssertionError("Native layer frametime did not select green");
                throw failure;
            } catch (RuntimeException actual) { if (actual!=failure) throw actual; }
            ByteBuffer restored = BufferUtils.createByteBuffer(4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, restored);
            if ((restored.get(0)&255)!=255 || restored.get(1)!=0 || restored.get(2)!=0 || (restored.get(3)&255)!=255)
                throw new AssertionError("Native animation changed the live atlas after failure");
            RenderItem renderer = new RenderItem();
            try (Images images = new Images()) {
                clockPixels(images);
                singleMipAtlas();
                multiAtlas(client);
                compressedAtlas();
                VisualBatch batching = new VisualBatch();
                java.util.List<byte[]> snapshots = new java.util.ArrayList<>();
                for (int color = 0; color < 3; color++) {
                    final int channel = color;
                    batching.add(() -> {
                        byte[] rgba = images.icon("test:batch-" + channel, () -> {
                            GL11.glClearColor(channel == 0 ? 1 : 0, channel == 1 ? 1 : 0, channel == 2 ? 1 : 0, 1);
                            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                        });
                        java.awt.image.BufferedImage copy = new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        copy.setRGB(0,0,0xff000000 | (rgba[0]&255)<<16 | (rgba[1]&255)<<8 | (rgba[2]&255));
                        return new Images.Image(copy,new com.google.gson.JsonArray(),"capture","batch");
                    }, result -> snapshots.add(VisualThroughputTest.encode(result.pixels)));
                }
                while (!batching.done()) { batching.capture(); batching.write(); }
                for (int channel=0;channel<3;channel++) {
                    int color=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(snapshots.get(channel))).getRGB(0,0);
                    if(color!=(0xff000000 | 0xff << (16-channel*8)))throw new AssertionError("Batched FBO reused another frame's pixels");
                }
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

    /** A transformed native Sys call rotates geometry through the actual capture
     * FBO. Proves time affects pixels and the scope survives native exceptions. */
    private static void clockPixels(Images images) throws Exception {
        String name = "micdoodle8.mods.galacticraft.core.client.render.item.ItemRendererTier1Rocket";
        byte[] bytes = new com.github.dcysteine.nesql.exporter.core.RendererTransformer().transform(name, name, RendererClockTest.fixture(name));
        Class<?> type = new ClassLoader(IconsTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(name, bytes, 0, bytes.length); }
        }.define();
        java.lang.reflect.Method time = type.getMethod("time");
        java.util.List<byte[]> frames = new java.util.ArrayList<>();
        for (int tick : new int[] {0, 54, 108, 216}) {
            try (com.github.dcysteine.nesql.exporter.core.RendererClock.Scope scope = com.github.dcysteine.nesql.exporter.core.RendererClock.at(tick, 1000)) {
                frames.add(images.icon("test:clock-rotation", () -> {
                    long phase;
                    try { phase = (long) time.invoke(null); } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                    GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glDisable(GL11.GL_LIGHTING);
                    GL11.glDisable(GL11.GL_BLEND); GL11.glDisable(GL11.GL_CULL_FACE);
                    GL11.glColor4f(1, 0, 0, 1);
                    GL11.glTranslatef(8, 8, 0); GL11.glRotatef(phase / 30F % 360F, 0, 0, 1);
                    GL11.glBegin(GL11.GL_TRIANGLES);
                    GL11.glVertex3f(-2, -3, 0); GL11.glVertex3f(2, -3, 0); GL11.glVertex3f(0, 6, 0);
                    GL11.glEnd();
                }));
            }
        }
        if (java.util.Arrays.equals(frames.get(0), frames.get(1)) || java.util.Arrays.equals(frames.get(1), frames.get(2)))
            throw new AssertionError("Native clock rotation did not change captured pixels");
        if (!java.util.Arrays.equals(frames.get(0), frames.get(3))) throw new AssertionError("Native rotation cycle does not close");
        try (com.github.dcysteine.nesql.exporter.core.RendererClock.Scope scope = com.github.dcysteine.nesql.exporter.core.RendererClock.at(1, 1000)) {
            images.icon("test:clock-failure", () -> { throw new IllegalStateException("native clock draw failure"); });
            throw new AssertionError("Expected native draw failure");
        } catch (IllegalStateException expected) { }
        if (com.github.dcysteine.nesql.exporter.core.RendererClock.active()) throw new AssertionError("Native frame leaked its clock");
        System.out.println("Scoped native clock: real GL frames move, cycle closes, exception restores clock");
    }

    private static void multiAtlas(Minecraft client) {
        int item = GL11.glGenTextures(), block = GL11.glGenTextures();
        net.minecraft.client.renderer.texture.TextureManager previous = client.renderEngine;
        try (GlState state = new GlState("multi-atlas fixture")) {
            for (int texture : new int[] {item, block}) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                ByteBuffer data = BufferUtils.createByteBuffer(4);
                data.put(new byte[] {(byte) (texture == item ? 255 : 0), 0, (byte) (texture == block ? 255 : 0), (byte) 255}).flip();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
            }
            client.renderEngine = new net.minecraft.client.renderer.texture.TextureManager(null) {
                @Override public void bindTexture(net.minecraft.util.ResourceLocation atlas) {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.equals(net.minecraft.client.renderer.texture.TextureMap.locationItemsTexture) ? item : block);
                }
            };
            TextureAtlasSprite first = new TextureAtlasSprite("item:layer") {}, second = new TextureAtlasSprite("block:fluid") {};
            for (TextureAtlasSprite sprite : new TextureAtlasSprite[] {first, second}) {
                sprite.setIconWidth(1); sprite.setIconHeight(1); sprite.initSprite(1, 1, 0, 0, false);
                sprite.setFramesTextureData(java.util.Arrays.asList(new int[][] {new int[] {0xff00ff00}}, new int[][] {new int[] {0xffffffff}}));
            }
            RuntimeException error = new RuntimeException("fluid render failure");
            try (NativeAnimation.Patches patches = new NativeAnimation.Patches(
                    java.util.Arrays.asList(net.minecraft.client.renderer.texture.TextureMap.locationItemsTexture, net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture),
                    java.util.Arrays.asList(first, second))) {
                patches.upload(0, new int[][] {{0,1},{1,1}}, 0);
                patches.upload(1, new int[][] {{0,1},{1,1}}, 1);
                if (texel(item) != 0xff00ff00 || texel(block) != 0xffffffff) throw new AssertionError("Item and contained fluid uploaded to the wrong atlas");
                throw error;
            } catch (RuntimeException actual) { if (actual != error) throw actual; }
            if (texel(item) != 0xffff0000 || texel(block) != 0xff0000ff) throw new AssertionError("Multiple native atlases were not restored after failure");
        } finally { client.renderEngine = previous; GL11.glDeleteTextures(item); GL11.glDeleteTextures(block); }
        System.out.println("Native item/fluid layers: independent atlases and exception restoration passed");
    }
    private static int texel(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); ByteBuffer bytes = BufferUtils.createByteBuffer(4);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
        return (bytes.get(3)&255)<<24 | (bytes.get(0)&255)<<16 | (bytes.get(1)&255)<<8 | (bytes.get(2)&255);
    }

    /** A real atlas may expose only level zero while its sprite is larger than one texel. */
    private static void singleMipAtlas() {
        int texture = GL11.glGenTextures();
        try (GlState state = new GlState("single mip atlas fixture")) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            ByteBuffer bytes = BufferUtils.createByteBuffer(8 * 8 * 4);
            for (int i = 0; i < 64; i++) bytes.put((byte) 255).put((byte) 0).put((byte) 0).put((byte) 255);
            bytes.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 8, 8, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            TextureAtlasSprite sprite = new TextureAtlasSprite("fixture:single-mip") {};
            sprite.setIconWidth(8); sprite.setIconHeight(8); sprite.initSprite(8, 8, 0, 0, false);
            sprite.setFramesTextureData(java.util.Collections.singletonList(new int[][] {new int[64]}));
            try (NativeAnimation.Patch patch = new NativeAnimation.Patch(texture, sprite)) {
                patch.upload(new int[][] {{0, 1}}, 0);
            }
        } finally { GL11.glDeleteTextures(texture); }
        System.out.println("Native atlas max-level zero does not probe an unavailable mip");
    }

    private static void compressedAtlas() {
        // Decode/recompress is not an exact restoration: preserve the native S3TC
        // bytes, including neighboring blocks and every allocated mip level.
        if (!org.lwjgl.opengl.GLContext.getCapabilities().GL_EXT_texture_compression_s3tc)
            throw new AssertionError("Native atlas fixture requires S3TC support");
        for (int format : new int[] {0x83f0, 0x83f1, 0x83f2, 0x83f3}) {
            compressedAtlasCase(format, 8, 0, 0, 4);
            compressedAtlasCase(format, 4, 4, 4, 1);
            compressedAtlasCase(format, 2, 3, 3, 1);
        }
        System.out.println("Compressed native atlas: subregion and full mip chain restore original compressed bytes after draw failure");
    }

    private static void compressedAtlasCase(int format, int width, int x, int y, int levels) {
        int texture = GL11.glGenTextures();
        try (GlState state = new GlState("compressed atlas fixture")) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            java.util.List<byte[]> before = new java.util.ArrayList<>();
            for (int level = 0; level < levels; level++) {
                int size = 8 >> level;
                ByteBuffer bytes = BufferUtils.createByteBuffer(size * size * 4);
                for (int i = 0; i < size * size; i++)
                    bytes.put((byte)(17 + i)).put((byte)(level * 31)).put((byte)51).put((byte)255);
                bytes.flip();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, format,
                        size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
                before.add(compressedBytes(level));
            }
            TextureAtlasSprite sprite = new TextureAtlasSprite("fixture:compressed-atlas") {};
            sprite.setIconWidth(width); sprite.setIconHeight(width);
            sprite.initSprite(8, 8, x, y, false);
            int[][] frame = new int[levels][];
            for (int level = 0; level < levels; level++) {
                frame[level] = new int[(width >> level) * (width >> level)];
                java.util.Arrays.fill(frame[level], 0xff00ff00);
            }
            sprite.setFramesTextureData(java.util.Collections.singletonList(frame));
            int read = GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING);
            RuntimeException draw = new RuntimeException("compressed native draw failure");
            boolean aligned = x % 4 == 0 && y % 4 == 0 && (width % 4 == 0 || width == 8);
            try (NativeAnimation.Patch patch = new NativeAnimation.Patch(texture, sprite)) {
                if (!aligned) throw new AssertionError("Unsafe compressed upload reached animation capture");
                patch.upload(new int[][] {{0, 1}}, 0);
                if (java.util.Arrays.equals(before.get(0), compressedBytes(0)))
                    throw new AssertionError("Compressed native animation did not upload its frame");
                throw draw;
            } catch (RuntimeException expected) {
                if (expected != draw && (aligned
                        || !(expected instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault)
                        || !((com.github.dcysteine.nesql.exporter.task.Jobs.Fault)expected).code.equals("animation_unsupported")
                        || !expected.getMessage().contains("not block-aligned"))) throw expected;
            }
            for (int level = 0; level < levels; level++)
                if (!java.util.Arrays.equals(before.get(level), compressedBytes(level)))
                    throw new AssertionError("Native compressed atlas changed after failure at mip " + level);
            if (GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER_BINDING) != read)
                throw new AssertionError("Compressed atlas capture leaked its framebuffer");
        } finally { GL11.glDeleteTextures(texture); }
    }

    private static byte[] compressedBytes(int level) {
        int size = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, level, org.lwjgl.opengl.GL13.GL_TEXTURE_COMPRESSED_IMAGE_SIZE);
        ByteBuffer bytes = BufferUtils.createByteBuffer(size);
        org.lwjgl.opengl.GL13.glGetCompressedTexImage(GL11.GL_TEXTURE_2D, level, bytes);
        byte[] result = new byte[size]; bytes.get(result);
        return result;
    }
}
