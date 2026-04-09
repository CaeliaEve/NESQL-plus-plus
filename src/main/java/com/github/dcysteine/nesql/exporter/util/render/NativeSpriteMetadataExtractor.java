package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class NativeSpriteMetadataExtractor {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private NativeSpriteMetadataExtractor() {}

    static NativeSpriteMetadata extract(RenderJob job) {
        try {
            IIcon icon = resolveIcon(job);
            if (!(icon instanceof TextureAtlasSprite)) {
                return null;
            }

            TextureAtlasSprite sprite = (TextureAtlasSprite) icon;
            NativeSpriteMetadata metadata = new NativeSpriteMetadata();
            metadata.schemaVersion = "nesqlpp/native-sprite-metadata/v1-draft";
            metadata.iconName = sprite.getIconName();
            metadata.iconClass = icon.getClass().getName();
            metadata.atlasTexture = resolveAtlasTexture(job);
            metadata.originX = sprite.getOriginX();
            metadata.originY = sprite.getOriginY();
            metadata.width = sprite.getIconWidth();
            metadata.height = sprite.getIconHeight();
            metadata.minU = sprite.getMinU();
            metadata.maxU = sprite.getMaxU();
            metadata.minV = sprite.getMinV();
            metadata.maxV = sprite.getMaxV();
            metadata.animated = sprite.hasAnimationMetadata();
            metadata.frameCount = sprite.getFrameCount();

            AnimationMetadataSection animation = readAnimationMetadata(sprite);
            if (animation != null) {
                metadata.defaultFrameTime = animation.getFrameTime();
                int count = animation.getFrameCount();
                metadata.timeline = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    Map<String, Object> frame = new LinkedHashMap<>();
                    frame.put("timelineIndex", i);
                    frame.put("frameIndex", animation.getFrameIndex(i));
                    frame.put("durationMs", animation.getFrameTimeSingle(i) * 50);
                    metadata.timeline.add(frame);
                }
            } else if (Boolean.TRUE.equals(metadata.animated) && metadata.frameCount != null && metadata.frameCount > 0) {
                metadata.defaultFrameTime = 1;
                metadata.timeline = new ArrayList<>();
                for (int i = 0; i < metadata.frameCount; i++) {
                    Map<String, Object> frame = new LinkedHashMap<>();
                    frame.put("timelineIndex", i);
                    frame.put("frameIndex", i);
                    frame.put("durationMs", 50);
                    metadata.timeline.add(frame);
                }
            }

            return metadata;
        } catch (Exception e) {
            Logger.MOD.debug("Failed to extract native sprite metadata for {}", job.getImageFilePath(), e);
            return null;
        }
    }

    static void writeIfAvailable(RenderJob job, File imageDirectory) {
        NativeSpriteMetadata metadata = job.getNativeSpriteMetadata();
        if (metadata == null) {
            return;
        }
        metadata.atlasExportFile =
                NativeAtlasTextureExporter.ensureAtlasExported(
                        imageDirectory, resolveAtlasResource(job));
        writeNativeSpriteAtlas(job, imageDirectory, metadata);

        File outputFile = new File(imageDirectory, job.getSpriteMetadataFilePath());
        File parent = outputFile.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(outputFile, false), StandardCharsets.UTF_8)) {
            GSON.toJson(metadata, writer);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write native sprite metadata for {}", outputFile.getAbsolutePath(), e);
        }
    }

    private static IIcon resolveIcon(RenderJob job) {
        switch (job.getType()) {
            case ITEM:
                ItemStack stack = job.getItem();
                return stack == null ? null : stack.getIconIndex();
            case FLUID:
                FluidStack fluidStack = job.getFluid();
                return fluidStack == null || fluidStack.getFluid() == null
                        ? null
                        : fluidStack.getFluid().getIcon(fluidStack);
            default:
                return null;
        }
    }

    private static String resolveAtlasTexture(RenderJob job) {
        return resolveAtlasResource(job).toString();
    }

    private static ResourceLocation resolveAtlasResource(RenderJob job) {
        if (job.getType() == RenderJob.JobType.FLUID) {
            return TextureMap.locationBlocksTexture;
        }
        ItemStack stack = job.getItem();
        if (stack != null && stack.getItem() != null && getItemSpriteNumber(stack) == 1) {
            return TextureMap.locationItemsTexture;
        }
        return TextureMap.locationBlocksTexture;
    }

    private static int getItemSpriteNumber(ItemStack stack) {
        try {
            Method method = stack.getItem().getClass().getMethod("getItemSpriteNumber");
            Object value = method.invoke(stack.getItem());
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static AnimationMetadataSection readAnimationMetadata(TextureAtlasSprite sprite) {
        try {
            Field field = TextureAtlasSprite.class.getDeclaredField("animationMetadata");
            field.setAccessible(true);
            Object value = field.get(sprite);
            return value instanceof AnimationMetadataSection ? (AnimationMetadataSection) value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void writeNativeSpriteAtlas(
            RenderJob job, File imageDirectory, NativeSpriteMetadata metadata) {
        try {
            IIcon icon = resolveIcon(job);
            if (!(icon instanceof TextureAtlasSprite)) {
                return;
            }
            TextureAtlasSprite sprite = (TextureAtlasSprite) icon;
            int frameCount = Math.max(sprite.getFrameCount(), 1);
            if (frameCount <= 0) {
                return;
            }

            BufferedImage atlasImage =
                    new BufferedImage(
                            metadata.width,
                            metadata.height * frameCount,
                            BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = atlasImage.createGraphics();
            try {
                for (int i = 0; i < frameCount; i++) {
                    BufferedImage frameImage = buildFrameImage(sprite, i, metadata.width, metadata.height);
                    if (frameImage != null) {
                        graphics.drawImage(frameImage, 0, i * metadata.height, null);
                    }
                }
            } finally {
                graphics.dispose();
            }

            File outputFile = new File(imageDirectory, job.getNativeSpriteAtlasFilePath());
            File parent = outputFile.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            }
            ImageIO.write(atlasImage, "PNG", outputFile);
            metadata.nativeSpriteAtlasFile = job.getNativeSpriteAtlasFilePath().replace('\\', '/');
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write native sprite atlas for {}", job.getImageFilePath(), e);
        }
    }

    private static BufferedImage buildFrameImage(
            TextureAtlasSprite sprite, int frameIndex, int width, int height) {
        try {
            int[][] levels = sprite.getFrameTextureData(frameIndex);
            if (levels == null || levels.length == 0 || levels[0] == null) {
                return null;
            }
            int[] pixels = levels[0];
            if (pixels.length < width * height) {
                return null;
            }
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            return image;
        } catch (Exception ignored) {
            return null;
        }
    }

    static final class NativeSpriteMetadata {
        String schemaVersion;
        String iconName;
        String iconClass;
        String atlasTexture;
        String atlasExportFile;
        Integer originX;
        Integer originY;
        Integer width;
        Integer height;
        Float minU;
        Float maxU;
        Float minV;
        Float maxV;
        Boolean animated;
        Integer frameCount;
        Integer defaultFrameTime;
        String nativeSpriteAtlasFile;
        List<Map<String, Object>> timeline;
    }
}
