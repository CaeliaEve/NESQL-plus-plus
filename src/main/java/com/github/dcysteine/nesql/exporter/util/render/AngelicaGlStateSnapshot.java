package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.IntBuffer;

/**
 * Captures the GL state that Angelica/GLSM and custom item renderers most often mutate.
 *
 * <p>{@code glPushAttrib(GL_ALL_ATTRIB_BITS)} is still used by {@link Renderer}; this snapshot is
 * an additional explicit guard for framebuffer, program, viewport, scissor, active texture, and
 * texture bindings that are easy to leak across offscreen render jobs under LWJGL3ify/Angelica.
 */
final class AngelicaGlStateSnapshot {
    private static final int[] TEXTURE_UNITS_TO_TRACK =
            new int[] { OpenGlHelper.defaultTexUnit, OpenGlHelper.lightmapTexUnit };

    private final int activeTexture;
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int framebuffer;
    private final int currentProgram;
    private final int matrixMode;
    private final int[] viewport;
    private final int[] scissorBox;
    private final boolean blendEnabled;
    private final boolean alphaTestEnabled;
    private final boolean depthTestEnabled;
    private final boolean cullFaceEnabled;
    private final boolean scissorTestEnabled;
    private final boolean texture2dEnabled;
    private final boolean rescaleNormalEnabled;
    private final int[] textureUnits;
    private final int[] boundTextures2d;

    private AngelicaGlStateSnapshot() {
        this.activeTexture = getInteger(GL13.GL_ACTIVE_TEXTURE, OpenGlHelper.defaultTexUnit);
        this.drawFramebuffer = getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING, 0);
        this.readFramebuffer = getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING, drawFramebuffer);
        this.framebuffer = getInteger(GL30.GL_FRAMEBUFFER_BINDING, drawFramebuffer);
        this.currentProgram = getInteger(GL20.GL_CURRENT_PROGRAM, 0);
        this.matrixMode = getInteger(GL11.GL_MATRIX_MODE, GL11.GL_MODELVIEW);
        this.viewport = getIntegerArray(GL11.GL_VIEWPORT, 4);
        this.scissorBox = getIntegerArray(GL11.GL_SCISSOR_BOX, 4);
        this.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        this.alphaTestEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        this.depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        this.cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        this.scissorTestEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        this.texture2dEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        this.rescaleNormalEnabled = GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL);
        this.textureUnits = TEXTURE_UNITS_TO_TRACK.clone();
        this.boundTextures2d = captureTextureBindings(textureUnits);
        drainErrors();
    }

    static AngelicaGlStateSnapshot capture() {
        return new AngelicaGlStateSnapshot();
    }

    void restore(RenderJob job) {
        try {
            restoreFramebufferBindings();
            restoreProgram();
            restoreTextureBindings();
            restoreBoolean(GL11.GL_BLEND, blendEnabled);
            restoreBoolean(GL11.GL_ALPHA_TEST, alphaTestEnabled);
            restoreBoolean(GL11.GL_DEPTH_TEST, depthTestEnabled);
            restoreBoolean(GL11.GL_CULL_FACE, cullFaceEnabled);
            restoreBoolean(GL11.GL_SCISSOR_TEST, scissorTestEnabled);
            restoreBoolean(GL11.GL_TEXTURE_2D, texture2dEnabled);
            restoreBoolean(GL12.GL_RESCALE_NORMAL, rescaleNormalEnabled);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            GL11.glMatrixMode(matrixMode);
            OpenGlHelper.setActiveTexture(activeTexture);
            int error = GL11.glGetError();
            if (error != GL11.GL_NO_ERROR) {
                Logger.MOD.warn(
                        "OpenGL error {} while restoring Angelica render state for {}",
                        error,
                        job == null ? "<null>" : job.getOutputFilePath());
                drainErrors();
            }
        } catch (Throwable restoreFailure) {
            Logger.MOD.warn(
                    "Could not fully restore Angelica render state for {}",
                    job == null ? "<null>" : job.getOutputFilePath(),
                    restoreFailure);
            drainErrors();
        }
    }

    private void restoreFramebufferBindings() {
        OpenGlHelper.func_153171_g(GL30.GL_FRAMEBUFFER, framebuffer);
        OpenGlHelper.func_153171_g(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
    }

    private void restoreProgram() {
        try {
            GL20.glUseProgram(currentProgram);
        } catch (Throwable ignored) {
        }
    }

    private void restoreTextureBindings() {
        for (int i = 0; i < textureUnits.length; i++) {
            OpenGlHelper.setActiveTexture(textureUnits[i]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, boundTextures2d[i]);
        }
        OpenGlHelper.setActiveTexture(activeTexture);
    }

    private static int[] captureTextureBindings(int[] textureUnits) {
        int previousActiveTexture = getInteger(GL13.GL_ACTIVE_TEXTURE, OpenGlHelper.defaultTexUnit);
        int[] bindings = new int[textureUnits.length];
        for (int i = 0; i < textureUnits.length; i++) {
            try {
                OpenGlHelper.setActiveTexture(textureUnits[i]);
                bindings[i] = getInteger(GL11.GL_TEXTURE_BINDING_2D, 0);
            } catch (Throwable ignored) {
                bindings[i] = 0;
            }
        }
        try {
            OpenGlHelper.setActiveTexture(previousActiveTexture);
        } catch (Throwable ignored) {
        }
        return bindings;
    }

    private static int getInteger(int pname, int defaultValue) {
        try {
            return GL11.glGetInteger(pname);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private static int[] getIntegerArray(int pname, int size) {
        int[] values = new int[size];
        IntBuffer buffer = BufferUtils.createIntBuffer(size);
        try {
            GL11.glGetInteger(pname, buffer);
            for (int i = 0; i < size; i++) {
                values[i] = buffer.get(i);
            }
        } catch (Throwable ignored) {
        }
        return values;
    }

    private static void restoreBoolean(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }

    private static void drainErrors() {
        while (GL11.glGetError() != GL11.GL_NO_ERROR) {
            // Drain the error queue so one custom renderer does not poison later captures.
        }
    }
}
