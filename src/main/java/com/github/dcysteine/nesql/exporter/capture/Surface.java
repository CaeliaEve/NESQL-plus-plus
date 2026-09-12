package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;

import java.nio.ByteBuffer;

/** One reusable render target and readback buffer, owned exclusively by the client thread. */
final class Surface implements AutoCloseable {
    private Framebuffer target;
    private ByteBuffer pixels;

    byte[] capture(int width, int height, int logicalWidth, int logicalHeight, int depth, String location, Runnable draw) {
        return run(width, height, logicalWidth, logicalHeight, depth, location, draw, true);
    }

    void render(String location, Runnable draw) { run(16, 16, 1, 1, 256, location, draw, false); }

    private byte[] run(int width, int height, int logicalWidth, int logicalHeight, int depth, String location, Runnable draw, boolean read) {
        Jobs.checkpoint();
        if (width < 1 || height < 1 || width > 4096 || height > 4096) throw new Jobs.Fault("texture_limit", "Invalid capture dimensions");
        try (GlState state = new GlState()) {
            // Growing only when necessary avoids per-recipe framebuffer and direct-buffer allocation.
            if (target == null || target.framebufferWidth < width || target.framebufferHeight < height) {
                int capacityWidth = target == null ? width : Math.max(width, target.framebufferWidth);
                int capacityHeight = target == null ? height : Math.max(height, target.framebufferHeight);
                if (target != null) target.deleteFramebuffer();
                target = null;
                target = new Framebuffer(capacityWidth, capacityHeight, true);
                pixels = BufferUtils.createByteBuffer(capacityWidth * capacityHeight * 4);
            }
            target.bindFramebuffer(false);
            GL20.glUseProgram(0);
            GL11.glViewport(0, 0, width, height);
            GL11.glEnable(GL11.GL_SCISSOR_TEST); GL11.glScissor(0, 0, width, height);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glLoadIdentity();
            GL11.glOrtho(0, logicalWidth, logicalHeight, 0, -depth, depth);
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glLoadIdentity();
            GL11.glColor4f(1, 1, 1, 1);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            RenderHelper.disableStandardItemLighting();
            draw.run();
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) throw new Jobs.Fault("render_failed", "OpenGL error " + error + " capturing " + location);
            if (!read) return null;
            target.bindFramebuffer(false);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS, 0);
            int length = width * height * 4;
            pixels.clear(); pixels.limit(length);
            GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) throw new Jobs.Fault("render_failed", "OpenGL error " + error + " capturing " + location);
            byte[] copy = new byte[length];
            pixels.get(copy);
            return copy;
        }
    }

    @Override public void close() {
        if (target != null) { target.deleteFramebuffer(); target = null; }
        pixels = null;
    }
}
