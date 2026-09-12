package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;

/** Offscreen captures restore both fixed-function state and the bindings not covered by pushAttrib. */
final class GlState implements AutoCloseable {
    private final int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    private final int texture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
    private final int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
    private final int packBuffer = GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
    private final int[] bindings = new int[2];
    private final int[] units = { OpenGlHelper.defaultTexUnit, OpenGlHelper.lightmapTexUnit };

    GlState() {
        for (int index = 0; index < units.length; index++) {
            OpenGlHelper.setActiveTexture(units[index]);
            bindings[index] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        OpenGlHelper.setActiveTexture(texture);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT | GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix();
    }

    @Override public void close() {
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
        GL11.glPopClientAttrib(); GL11.glPopAttrib();
        OpenGlHelper.func_153171_g(GL30.GL_DRAW_FRAMEBUFFER, draw);
        OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, read);
        GL20.glUseProgram(program);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
        for (int index = 0; index < units.length; index++) {
            OpenGlHelper.setActiveTexture(units[index]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bindings[index]);
        }
        OpenGlHelper.setActiveTexture(texture);
        GL11.glMatrixMode(matrix);
    }
}
