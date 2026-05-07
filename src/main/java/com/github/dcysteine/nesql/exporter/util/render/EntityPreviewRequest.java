package com.github.dcysteine.nesql.exporter.util.render;

import net.minecraft.entity.EntityLiving;
import org.lwjgl.util.Rectangle;

import java.lang.reflect.Method;

/**
 * Immutable render request for industrial slaughterhouse / EEC mob previews.
 *
 * <p>The actual preview is captured from Minecraft's live entity renderer on the client thread.
 * We keep the payload small and renderer-friendly so the existing render dispatcher can reuse it
 * across multiple frames while producing a transparent GIF turntable.</p>
 */
public final class EntityPreviewRequest {
    private static final int ANCHOR_X = 31;
    private static final int ANCHOR_Y = 50;
    private static final int BASE_SCALE = 20;

    private final String mobName;
    private final String localizedName;
    private final String modId;
    private final EntityLiving entity;
    private final String imageFilePath;
    private final int frameCount;
    private final int frameDelayMs;

    private transient Layout cachedLayout;

    public EntityPreviewRequest(
            String mobName,
            String localizedName,
            String modId,
            EntityLiving entity,
            String imageFilePath,
            int frameCount,
            int frameDelayMs) {
        this.mobName = mobName;
        this.localizedName = localizedName;
        this.modId = modId;
        this.entity = entity;
        this.imageFilePath = imageFilePath;
        this.frameCount = Math.max(1, frameCount);
        this.frameDelayMs = Math.max(16, frameDelayMs);
    }

    public String getMobName() {
        return mobName;
    }

    public String getLocalizedName() {
        return localizedName;
    }

    public String getModId() {
        return modId;
    }

    public EntityLiving getEntity() {
        return entity;
    }

    public String getImageFilePath() {
        return imageFilePath;
    }

    public String getOutputGifPath() {
        if (imageFilePath == null) {
            return null;
        }
        if (imageFilePath.endsWith(".png")) {
            return imageFilePath.substring(0, imageFilePath.length() - 4) + ".gif";
        }
        return imageFilePath + ".gif";
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getFrameDelayMs() {
        return frameDelayMs;
    }

    public Layout resolveLayout() {
        if (cachedLayout != null) {
            return cachedLayout;
        }
        cachedLayout = measureLayout();
        return cachedLayout;
    }

    private Layout measureLayout() {
        Rectangle measuredBounds = measureMobBounds(entity);
        if (measuredBounds != null && measuredBounds.getWidth() > 0 && measuredBounds.getHeight() > 0) {
            float yLocal = measuredBounds.getY() + measuredBounds.getHeight();
            float wantedY = 54.0f;

            float scaledByHeight = 40.0f / Math.max(1.0f, measuredBounds.getHeight());
            float scaledByWidth = 38.0f / Math.max(1.0f, measuredBounds.getWidth());
            float scaleMultiplier = Math.min(scaledByHeight, scaledByWidth);
            scaleMultiplier = Math.round(20.0f * scaleMultiplier) / 20.0f;

            float deltaY = ANCHOR_Y - yLocal;
            float adjustedDeltaY = deltaY - (deltaY * scaleMultiplier);
            float renderYOffset = (wantedY - yLocal) - adjustedDeltaY;

            return new Layout(
                    ANCHOR_X,
                    Math.round(ANCHOR_Y + renderYOffset),
                    Math.max(8, Math.round(BASE_SCALE * scaleMultiplier)));
        }

        float height = Math.max(entity.height, 0.5f);
        float width = Math.max(entity.width, 0.5f);
        float heightScale = 2.05f / height;
        float widthScale = 1.75f / width;
        int scale = Math.max(10, Math.round(18.0f * Math.min(heightScale, widthScale)));
        int y = 54 + Math.max(0, Math.round((1.30f - height) * 6.0f));
        return new Layout(ANCHOR_X, y, scale);
    }

    private Rectangle measureMobBounds(EntityLiving mob) {
        if (mob == null) {
            return null;
        }

        try {
            Class<?> mobUtilsClass = Class.forName("com.kuba6000.mobsinfo.api.utils.MobUtils");
            Method measureMethod = mobUtilsClass.getMethod(
                    "getMobSizeInGui",
                    EntityLiving.class,
                    int.class,
                    int.class,
                    int.class);
            Object result = measureMethod.invoke(null, mob, ANCHOR_X, ANCHOR_Y, BASE_SCALE);
            return result instanceof Rectangle ? (Rectangle) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final class Layout {
        public final int x;
        public final int y;
        public final int scale;

        private Layout(int x, int y, int scale) {
            this.x = x;
            this.y = y;
            this.scale = scale;
        }
    }
}
