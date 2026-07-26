package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.canonical.ResourceAuthorityContract;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Materializes and hashes actual native sprite pixels instead of trusting declared frame counts. */
public final class NativeSpriteFrameMaterializer {
    private NativeSpriteFrameMaterializer() {}

    public static Result inspect(
            TextureAtlasSprite sprite,
            int width,
            int height,
            int requestedFrameCount) {
        List<Frame> frames = new ArrayList<Frame>();
        Set<String> contentHashes = new LinkedHashSet<String>();
        int frameCount = Math.max(1, requestedFrameCount);
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            BufferedImage image = buildFrameImage(sprite, frameIndex, width, height);
            if (image == null) {
                continue;
            }
            String contentHash = contentHash(image);
            int materializationIndex = frames.size();
            frames.add(new Frame(materializationIndex, frameIndex, contentHash, image));
            contentHashes.add(contentHash);
        }

        int materializedFrameCount = frames.size();
        int distinctFrameCount = contentHashes.size();
        String status = ResourceAuthorityContract.animationStatus(
                materializedFrameCount,
                distinctFrameCount);
        String reason;
        if (ResourceAuthorityContract.ANIMATION_MATERIALIZED.equals(status)) {
            reason = "distinct-native-frames-materialized";
        } else if (ResourceAuthorityContract.ANIMATION_STATIC.equals(status)) {
            reason = frameCount > 1
                    ? "declared-animation-has-single-distinct-frame"
                    : "single-native-frame-materialized";
        } else {
            reason = "no-native-frame-data";
        }
        return new Result(frames, distinctFrameCount, status, reason);
    }

    public static Result inspectVerticalAtlas(
            File atlasFile,
            int frameWidth,
            int frameHeight) {
        if (atlasFile == null || !atlasFile.isFile()) {
            return Result.unavailable("legacy-native-sprite-atlas-missing");
        }
        if (frameWidth <= 0 || frameHeight <= 0) {
            return Result.unavailable("legacy-native-sprite-atlas-frame-size-missing");
        }
        try {
            BufferedImage atlas = ImageIO.read(atlasFile);
            if (atlas == null) {
                return Result.unavailable("legacy-native-sprite-atlas-unreadable");
            }
            if (atlas.getWidth() != frameWidth
                    || atlas.getHeight() < frameHeight
                    || atlas.getHeight() % frameHeight != 0) {
                return Result.unavailable("legacy-native-sprite-atlas-dimensions-invalid");
            }
            int physicalFrameCount = atlas.getHeight() / frameHeight;
            List<Frame> frames = new ArrayList<Frame>(physicalFrameCount);
            Set<String> contentHashes = new LinkedHashSet<String>();
            for (int frameIndex = 0; frameIndex < physicalFrameCount; frameIndex++) {
                BufferedImage image = copyRegion(
                        atlas,
                        0,
                        frameIndex * frameHeight,
                        frameWidth,
                        frameHeight);
                String contentHash = contentHash(image);
                frames.add(new Frame(frameIndex, frameIndex, contentHash, image));
                contentHashes.add(contentHash);
            }
            String status = ResourceAuthorityContract.animationStatus(
                    frames.size(),
                    contentHashes.size());
            String reason = ResourceAuthorityContract.ANIMATION_MATERIALIZED.equals(status)
                    ? "legacy-native-sprite-atlas-distinct-frames-materialized"
                    : "legacy-native-sprite-atlas-single-distinct-frame";
            return new Result(frames, contentHashes.size(), status, reason);
        } catch (Exception ignored) {
            return Result.unavailable("legacy-native-sprite-atlas-read-failed");
        }
    }

    private static BufferedImage buildFrameImage(
            TextureAtlasSprite sprite,
            int frameIndex,
            int width,
            int height) {
        if (sprite == null || width <= 0 || height <= 0) {
            return null;
        }
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
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static BufferedImage copyRegion(
            BufferedImage source,
            int x,
            int y,
            int width,
            int height) {
        BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = source.getRGB(x, y, width, height, null, 0, width);
        copy.setRGB(0, 0, width, height, pixels, 0, width);
        return copy;
    }

    private static String contentHash(BufferedImage image) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateInt(digest, image.getWidth());
            updateInt(digest, image.getHeight());
            int[] pixels = image.getRGB(
                    0,
                    0,
                    image.getWidth(),
                    image.getHeight(),
                    null,
                    0,
                    image.getWidth());
            for (int pixel : pixels) {
                updateInt(digest, pixel);
            }
            StringBuilder hex = new StringBuilder(digest.getDigestLength() * 2);
            for (byte value : digest.digest()) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            }
            return "sha256:" + hex;
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    public static final class Result {
        private final List<Frame> frames;
        private final int distinctFrameCount;
        private final String status;
        private final String reason;

        private Result(
                List<Frame> frames,
                int distinctFrameCount,
                String status,
                String reason) {
            this.frames = Collections.unmodifiableList(new ArrayList<Frame>(frames));
            this.distinctFrameCount = distinctFrameCount;
            this.status = status;
            this.reason = reason;
        }

        private static Result unavailable(String reason) {
            return new Result(
                    Collections.<Frame>emptyList(),
                    0,
                    ResourceAuthorityContract.ANIMATION_UNAVAILABLE,
                    reason);
        }

        public int materializedFrameCount() {
            return frames.size();
        }

        public int distinctFrameCount() {
            return distinctFrameCount;
        }

        public String status() {
            return status;
        }

        public String reason() {
            return reason;
        }

        public List<Map<String, Object>> descriptors(int width, int height) {
            List<Map<String, Object>> descriptors =
                    new ArrayList<Map<String, Object>>(frames.size());
            for (Frame frame : frames) {
                descriptors.add(frame.descriptor(width, height));
            }
            return descriptors;
        }

        List<Frame> frames() {
            return frames;
        }
    }

    static final class Frame {
        private final int materializationIndex;
        private final int frameIndex;
        private final String contentHash;
        private final BufferedImage image;

        private Frame(
                int materializationIndex,
                int frameIndex,
                String contentHash,
                BufferedImage image) {
            this.materializationIndex = materializationIndex;
            this.frameIndex = frameIndex;
            this.contentHash = contentHash;
            this.image = image;
        }

        BufferedImage image() {
            return image;
        }

        private Map<String, Object> descriptor(int width, int height) {
            Map<String, Object> descriptor = new LinkedHashMap<String, Object>();
            descriptor.put("materializationIndex", materializationIndex);
            descriptor.put("frameIndex", frameIndex);
            descriptor.put("contentHash", contentHash);
            Map<String, Object> rect = new LinkedHashMap<String, Object>();
            rect.put("x", 0);
            rect.put("y", materializationIndex * height);
            rect.put("width", width);
            rect.put("height", height);
            descriptor.put("rect", rect);
            return descriptor;
        }
    }
}
