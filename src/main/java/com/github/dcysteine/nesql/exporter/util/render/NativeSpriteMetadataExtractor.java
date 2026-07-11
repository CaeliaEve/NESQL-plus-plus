package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
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
import java.io.InputStreamReader;
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
            metadata.frameCount = sprite.getFrameCount();
            metadata.animated = sprite.hasAnimationMetadata()
                    || (metadata.frameCount != null && metadata.frameCount > 1);

            AnimationTimeline resolvedTimeline = resolveAnimationTimeline(sprite, job, metadata.frameCount);
            if (resolvedTimeline != null) {
                metadata.defaultFrameTime = resolvedTimeline.defaultFrameTimeTicks;
                metadata.timeline = resolvedTimeline.timeline;
                if (resolvedTimeline.frameCount != null && resolvedTimeline.frameCount > 0) {
                    metadata.frameCount = resolvedTimeline.frameCount;
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

    private static AnimationTimeline resolveAnimationTimeline(
            TextureAtlasSprite sprite,
            RenderJob job,
            Integer runtimeFrameCount) {
        ResourceAnimationMetadata resourceAnimation = readResourceAnimationMetadata(sprite, job, runtimeFrameCount);
        if (resourceAnimation != null) {
            return resourceAnimation.toTimeline(runtimeFrameCount);
        }

        AnimationMetadataSection animation = readAnimationMetadata(sprite);
        if (animation != null) {
            return timelineFromRuntimeMetadata(animation, runtimeFrameCount);
        }

        if (runtimeFrameCount != null && runtimeFrameCount > 1) {
            return createSequentialTimeline(runtimeFrameCount, 1);
        }

        return null;
    }

    private static AnimationTimeline timelineFromRuntimeMetadata(
            AnimationMetadataSection animation,
            Integer runtimeFrameCount) {
        int explicitCount = animation.getFrameCount();
        int count = explicitCount > 0
                ? explicitCount
                : (runtimeFrameCount != null ? runtimeFrameCount : 0);
        if (count <= 0) {
            return null;
        }

        AnimationTimeline timeline = new AnimationTimeline();
        timeline.defaultFrameTimeTicks = animation.getFrameTime();
        timeline.frameCount = runtimeFrameCount != null && runtimeFrameCount > 0 ? runtimeFrameCount : count;
        timeline.timeline = new ArrayList<Map<String, Object>>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> frame = new LinkedHashMap<String, Object>();
            frame.put("timelineIndex", i);
            frame.put("frameIndex", animation.getFrameIndex(i));
            frame.put("durationMs", animation.getFrameTimeSingle(i) * 50);
            timeline.timeline.add(frame);
        }
        return timeline;
    }

    private static AnimationTimeline createSequentialTimeline(int frameCount, int defaultFrameTimeTicks) {
        if (frameCount <= 0) {
            return null;
        }

        AnimationTimeline timeline = new AnimationTimeline();
        timeline.defaultFrameTimeTicks = defaultFrameTimeTicks;
        timeline.frameCount = frameCount;
        timeline.timeline = new ArrayList<Map<String, Object>>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            Map<String, Object> frame = new LinkedHashMap<String, Object>();
            frame.put("timelineIndex", i);
            frame.put("frameIndex", i);
            frame.put("durationMs", defaultFrameTimeTicks * 50);
            timeline.timeline.add(frame);
        }
        return timeline;
    }

    private static ResourceAnimationMetadata readResourceAnimationMetadata(
            TextureAtlasSprite sprite,
            RenderJob job,
            Integer runtimeFrameCount) {
        for (ResourceLocation candidate : buildAnimationMetadataCandidates(sprite, job)) {
            try {
                IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(candidate);
                if (resource == null) {
                    continue;
                }

                try (InputStreamReader reader =
                             new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonElement parsed = new JsonParser().parse(reader);
                    if (!parsed.isJsonObject()) {
                        continue;
                    }
                    ResourceAnimationMetadata metadata =
                            parseResourceAnimationMetadata(parsed.getAsJsonObject(), runtimeFrameCount);
                    if (metadata != null) {
                        return metadata;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static List<ResourceLocation> buildAnimationMetadataCandidates(
            TextureAtlasSprite sprite,
            RenderJob job) {
        List<ResourceLocation> candidates = new ArrayList<ResourceLocation>();
        String iconName = sprite.getIconName();
        if (iconName == null || iconName.isEmpty()) {
            return candidates;
        }

        String domain = "minecraft";
        String path = iconName;
        int separator = iconName.indexOf(':');
        if (separator >= 0) {
            domain = iconName.substring(0, separator);
            path = iconName.substring(separator + 1);
        }

        List<String> textureRoots = new ArrayList<String>();
        ResourceLocation atlasResource = resolveAtlasResource(job);
        if (TextureMap.locationItemsTexture.equals(atlasResource)) {
            textureRoots.add("items");
            textureRoots.add("blocks");
        } else {
            textureRoots.add("blocks");
            textureRoots.add("items");
        }

        for (String textureRoot : textureRoots) {
            candidates.add(
                    new ResourceLocation(
                            domain,
                            "textures/" + textureRoot + "/" + path + ".png.mcmeta"));
        }

        return candidates;
    }

    private static ResourceAnimationMetadata parseResourceAnimationMetadata(
            JsonObject root,
            Integer runtimeFrameCount) {
        JsonObject animation = root.getAsJsonObject("animation");
        if (animation == null) {
            return null;
        }

        ResourceAnimationMetadata metadata = new ResourceAnimationMetadata();
        metadata.defaultFrameTimeTicks =
                animation.has("frametime") && animation.get("frametime").isJsonPrimitive()
                        ? Math.max(1, animation.get("frametime").getAsInt())
                        : 1;

        JsonArray frames = animation.getAsJsonArray("frames");
        if (frames != null) {
            for (JsonElement element : frames) {
                Integer index = null;
                Integer timeTicks = metadata.defaultFrameTimeTicks;

                if (element.isJsonPrimitive()) {
                    index = element.getAsInt();
                } else if (element.isJsonObject()) {
                    JsonObject frameObject = element.getAsJsonObject();
                    if (frameObject.has("index") && frameObject.get("index").isJsonPrimitive()) {
                        index = frameObject.get("index").getAsInt();
                    }
                    if (frameObject.has("time") && frameObject.get("time").isJsonPrimitive()) {
                        timeTicks = Math.max(1, frameObject.get("time").getAsInt());
                    }
                }

                if (index != null && index >= 0) {
                    metadata.frames.add(new ResourceAnimationFrame(index, timeTicks));
                }
            }
        }

        if (metadata.frames.isEmpty()) {
            int frameCount = runtimeFrameCount != null ? runtimeFrameCount : 0;
            for (int i = 0; i < frameCount; i++) {
                metadata.frames.add(new ResourceAnimationFrame(i, metadata.defaultFrameTimeTicks));
            }
        }

        return metadata.frames.isEmpty() ? null : metadata;
    }

    private static final class AnimationTimeline {
        Integer defaultFrameTimeTicks;
        Integer frameCount;
        List<Map<String, Object>> timeline;
    }

    private static final class ResourceAnimationMetadata {
        Integer defaultFrameTimeTicks = 1;
        List<ResourceAnimationFrame> frames = new ArrayList<ResourceAnimationFrame>();

        AnimationTimeline toTimeline(Integer runtimeFrameCount) {
            if (frames.isEmpty()) {
                return null;
            }

            AnimationTimeline timeline = new AnimationTimeline();
            timeline.defaultFrameTimeTicks = defaultFrameTimeTicks;
            timeline.frameCount = runtimeFrameCount != null && runtimeFrameCount > 0
                    ? runtimeFrameCount
                    : inferFrameCount();
            timeline.timeline = new ArrayList<Map<String, Object>>(frames.size());
            for (int i = 0; i < frames.size(); i++) {
                ResourceAnimationFrame sourceFrame = frames.get(i);
                Map<String, Object> frame = new LinkedHashMap<String, Object>();
                frame.put("timelineIndex", i);
                frame.put("frameIndex", sourceFrame.frameIndex);
                frame.put("durationMs", sourceFrame.timeTicks * 50);
                timeline.timeline.add(frame);
            }
            return timeline;
        }

        private Integer inferFrameCount() {
            int maxFrameIndex = 0;
            for (ResourceAnimationFrame frame : frames) {
                if (frame.frameIndex > maxFrameIndex) {
                    maxFrameIndex = frame.frameIndex;
                }
            }
            return maxFrameIndex + 1;
        }
    }

    private static final class ResourceAnimationFrame {
        final int frameIndex;
        final int timeTicks;

        private ResourceAnimationFrame(int frameIndex, int timeTicks) {
            this.frameIndex = frameIndex;
            this.timeTicks = timeTicks;
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
