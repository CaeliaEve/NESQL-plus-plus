package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

final class NativeAtlasTextureExporter {
    private static final Map<String, String> EXPORTED = new HashMap<>();

    private NativeAtlasTextureExporter() {}

    static synchronized String ensureAtlasExported(File imageDirectory, ResourceLocation atlasResource) {
        String key = atlasResource.toString();
        if (EXPORTED.containsKey(key)) {
            return EXPORTED.get(key);
        }

        try {
            ITextureObject textureObject =
                    Minecraft.getMinecraft().getTextureManager().getTexture(atlasResource);
            if (!(textureObject instanceof AbstractTexture)) {
                return null;
            }

            int glTextureId = ((AbstractTexture) textureObject).getGlTextureId();
            if (glTextureId <= 0) {
                return null;
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (width <= 0 || height <= 0) {
                return null;
            }

            java.nio.ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12Compat.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buffer);

            int[] pixels = new int[width * height];
            buffer.asIntBuffer().get(pixels);
            int[] flipped = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                int x = i % width;
                int y = height - (i / width + 1);
                flipped[i] = pixels[x + width * y];
            }

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, flipped, 0, width);

            File atlasDir = new File(imageDirectory, "native-atlas");
            if (!atlasDir.exists()) {
                atlasDir.mkdirs();
            }
            String safeName =
                    atlasResource.getResourceDomain()
                            + "_"
                            + atlasResource.getResourcePath().replace('/', '_').replace(':', '_');
            File output = new File(atlasDir, safeName + ".png");
            ImageIO.write(image, "PNG", output);

            String relative = "native-atlas/" + output.getName();
            EXPORTED.put(key, relative);
            return relative;
        } catch (Exception e) {
            Logger.MOD.warn("Failed to export native atlas {}", atlasResource, e);
            return null;
        }
    }

    private static final class GL12Compat {
        private static final int GL_BGRA = 0x80E1;
    }
}
