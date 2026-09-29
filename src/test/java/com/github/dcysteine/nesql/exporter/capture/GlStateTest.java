package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.*;

/** Real, invisible OpenGL context: attribute restoration must target the caller's framebuffer. */
public final class GlStateTest {
    public static void main(String[] args) throws Exception {
        // Xvfb/Mesa may expose GLX window configs without LWJGL 2 Pbuffer configs.
        // Its window lives only on the CI virtual display; local runs stay invisible.
        boolean window = System.getenv("NESQL_GL_WINDOW") != null;
        Pbuffer context = null;
        if (window) {
            Display.setDisplayMode(new DisplayMode(32, 32));
            Display.create(new PixelFormat().withDepthBits(24));
        } else {
            context = new Pbuffer(32, 32, new PixelFormat().withDepthBits(24), null, null);
        }
        try {
            if (context != null) context.makeCurrent();
            System.out.println("OpenGL: " + GL11.glGetString(GL11.GL_VERSION) + "; " + GL11.glGetString(GL11.GL_RENDERER));
            OpenGlHelper.defaultTexUnit = GL13.GL_TEXTURE0;
            OpenGlHelper.lightmapTexUnit = GL13.GL_TEXTURE1;
            OpenGlHelper.framebufferSupported = true;
            int target = GL30.glGenFramebuffers();
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 32, 32, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            clean("test setup");
            for (int i = 0; i < 3; i++) {
                int draw = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);
                int read = GL11.glGetInteger(GL11.GL_READ_BUFFER);
                try (GlState state = new GlState()) {
                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
                    GL11.glClearColor(1, 0, 0, 1);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                    clean("capture " + i);
                }
                clean("restore " + i);
                require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == 0, "draw framebuffer restored");
                require(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == 0, "read framebuffer restored");
                require(GL11.glGetInteger(GL11.GL_DRAW_BUFFER) == draw, "draw buffer restored");
                require(GL11.glGetInteger(GL11.GL_READ_BUFFER) == read, "read buffer restored");
            }
            // A client can bind distinct read/draw targets. Restoration must preserve both,
            // including when the native renderer throws before pixel readback.
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target);
            GL11.glViewport(2, 3, 19, 23);
            GL11.glMatrixMode(GL11.GL_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            RuntimeException nativeFailure = new RuntimeException("native renderer failed");
            try (GlState state = new GlState("throwing renderer")) {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target);
                GL11.glViewport(0, 0, 32, 32);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                throw nativeFailure;
            } catch (RuntimeException error) {
                require(error == nativeFailure && error.getSuppressed().length == 0, "original draw failure preserved");
            }
            clean("restore after exception");
            require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == 0, "separate draw binding");
            require(GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) == target, "separate read binding");
            require(GL11.glGetInteger(GL11.GL_MATRIX_MODE) == GL11.GL_TEXTURE, "matrix mode restored");
            require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) == GL13.GL_TEXTURE1, "active texture restored");
            java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(16);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            require(viewport.get(0) == 2 && viewport.get(1) == 3 && viewport.get(2) == 19 && viewport.get(3) == 23, "viewport restored");
            java.nio.ByteBuffer pixel = org.lwjgl.BufferUtils.createByteBuffer(4);
            GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
            require((pixel.get(0) & 255) == 255 && pixel.get(1) == 0 && pixel.get(2) == 0 && (pixel.get(3) & 255) == 255, "captured red pixel retained");
            clean("read restored target");
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(target);
            GL11.glDeleteTextures(texture);
            GL11.glEnable(-1);
            boolean rejected = false;
            try (GlState state = new GlState()) {
                throw new AssertionError("pre-existing GL error was ignored");
            } catch (Jobs.Fault error) {
                rejected = error.getMessage().contains("entry") && error.getMessage().contains("1280");
            }
            require(rejected, "entry failure is attributed before capture");
            clean("after rejected entry");
            rejected = false;
            try (GlState state = new GlState("broken renderer")) {
                GL11.glEnable(-1);
            } catch (Jobs.Fault error) {
                rejected = error.getMessage().contains("restore") && error.getMessage().contains("broken renderer");
            }
            require(rejected, "unchecked renderer errors cannot leak into the next capture");
            clean("after failed capture");
            IconsTest.run();
            System.out.println("OpenGL restoration checks passed");
        } finally { if (context != null) context.destroy(); else Display.destroy(); }
    }

    private static void clean(String phase) {
        int error = GL11.glGetError();
        require(error == GL11.GL_NO_ERROR, phase + ": OpenGL error " + error);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
