package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import net.minecraft.util.IIcon;

import javax.imageio.IIOImage;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Helper class for capturing and writing animated GIF images.
 */
public final class GifRenderer {
    private static final String GIF_FORMAT = "gif";
    private static final int DETECTION_FRAME_COUNT = 10;
    private static final long MAX_CAPTURE_TIME_MS = 30000L;
    public static final int DEFAULT_CAPTURE_FRAME_DELAY_MS = 50;

    /**
     * Check if an icon supports animation by checking if it implements IPatchedTextureAtlasSprite.
     */
    public static boolean isAnimated(IIcon icon) {
        if (!ConfigOptions.EXPORT_GIF.get()) {
            return false;
        }
        try {
            Class<?> patchedSpriteClass =
                    Class.forName("com.mitchej123.hodgepodge.textures.IPatchedTextureAtlasSprite");
            return patchedSpriteClass.isInstance(icon);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Capture multiple frames of an animation.
     * This should be called over multiple render ticks.
     */
    public static final class AnimationCapture {
        private final File outputFile;
        private final List<BufferedImage> frames = new ArrayList<BufferedImage>();
        private int currentFrame = 0;
        private final int totalFrames;
        private final int frameDelayMs;
        private final boolean loop;
        private final long startTimeMs = System.currentTimeMillis();
        private boolean detectedAnimation = false;
        private boolean detectionComplete = false;

        public AnimationCapture(File outputFile) {
            this(outputFile, ConfigOptions.GIF_FRAMES.get(), DEFAULT_CAPTURE_FRAME_DELAY_MS);
        }

        public AnimationCapture(File outputFile, int totalFrames, int frameDelayMs) {
            this.outputFile = outputFile;
            this.totalFrames = Math.max(1, totalFrames);
            this.frameDelayMs = Math.max(16, frameDelayMs);
            this.loop = ConfigOptions.GIF_LOOP_COUNT.get() == 0;
        }

        public boolean isTimedOut() {
            long elapsedMs = System.currentTimeMillis() - startTimeMs;
            if (elapsedMs > MAX_CAPTURE_TIME_MS) {
                Logger.MOD.warn(
                        "Capture timeout for {} after {}ms (captured {} frames)",
                        outputFile.getName(), elapsedMs, currentFrame);
                return true;
            }
            return false;
        }

        public boolean shouldStopEarly(boolean allowStaticDetection) {
            if (!allowStaticDetection || detectionComplete) {
                return false;
            }
            if (currentFrame < DETECTION_FRAME_COUNT) {
                return false;
            }
            if (!detectedAnimation && areAllFramesIdentical()) {
                Logger.MOD.info(
                        "Static item detected for {} (first {} frames identical), stopping at {} frames",
                        outputFile.getName(), DETECTION_FRAME_COUNT, currentFrame);
                return true;
            }

            detectionComplete = true;
            if (detectedAnimation) {
                Logger.MOD.info(
                        "Animation detected for {}, will capture full {} frames",
                        outputFile.getName(), totalFrames);
            }
            return false;
        }

        public boolean isComplete() {
            return currentFrame >= totalFrames;
        }

        public int getTotalFrames() {
            return totalFrames;
        }

        public int getCurrentFrameCount() {
            return currentFrame;
        }

        public void addFrame(BufferedImage frame) {
            if (isComplete()) {
                return;
            }
            if (frame == null || frame.getWidth() <= 0 || frame.getHeight() <= 0) {
                Logger.MOD.error(
                        "Invalid frame dimensions for {}: {}x{}",
                        outputFile.getName(),
                        frame != null ? frame.getWidth() : "null",
                        frame != null ? frame.getHeight() : "null");
                return;
            }

            if (!detectionComplete && currentFrame > 0 && !detectedAnimation) {
                BufferedImage previousFrame = frames.get(currentFrame - 1);
                if (!areFramesIdentical(previousFrame, frame)) {
                    detectedAnimation = true;
                    Logger.MOD.debug(
                            "Animation detected at frame {} for {}",
                            currentFrame + 1, outputFile.getName());
                }
            }

            frames.add(frame);
            currentFrame++;
            if (currentFrame == totalFrames || currentFrame % 32 == 0) {
                Logger.MOD.debug(
                        "Added frame {} of {} for {}",
                        currentFrame, totalFrames, outputFile.getName());
            }
        }

        private boolean areFramesIdentical(BufferedImage frame1, BufferedImage frame2) {
            if (frame1 == null || frame2 == null) {
                return false;
            }
            if (frame1.getWidth() != frame2.getWidth()
                    || frame1.getHeight() != frame2.getHeight()) {
                return false;
            }

            int width = frame1.getWidth();
            int height = frame1.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (frame1.getRGB(x, y) != frame2.getRGB(x, y)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean areAllFramesIdentical() {
            if (frames.size() <= 1) {
                return true;
            }
            BufferedImage firstFrame = frames.get(0);
            for (int i = 1; i < frames.size(); i++) {
                if (!areFramesIdentical(firstFrame, frames.get(i))) {
                    return false;
                }
            }
            return true;
        }

        public void writeGif() throws IOException {
            if (frames.isEmpty()) {
                Logger.MOD.warn("No frames to write for {}", outputFile.getName());
                return;
            }

            BufferedImage firstFrame = frames.get(0);
            if (firstFrame == null || firstFrame.getWidth() <= 0 || firstFrame.getHeight() <= 0) {
                Logger.MOD.error("First frame is invalid for {}", outputFile.getName());
                return;
            }

            File parentFile = outputFile.getParentFile();
            if (parentFile != null && !parentFile.exists()) {
                parentFile.mkdirs();
            }

            List<BufferedImage> framesToWrite = new ArrayList<BufferedImage>();
            framesToWrite.add(firstFrame);
            if (!areAllFramesIdentical()) {
                for (int i = 1; i < frames.size(); i++) {
                    BufferedImage frame = frames.get(i);
                    if (frame != null && frame.getWidth() > 0 && frame.getHeight() > 0) {
                        framesToWrite.add(frame);
                    } else {
                        Logger.MOD.warn("Skipping invalid frame {} for {}", i, outputFile.getName());
                    }
                }
            }

            if (framesToWrite.size() == 1) {
                writeSingleFrameGif(outputFile, firstFrame);
                Logger.MOD.debug("Wrote single-frame GIF: {}", outputFile.getName());
                return;
            }

            writeAnimatedGif(outputFile, framesToWrite, frameDelayMs, loop);
            Logger.MOD.debug(
                    "Successfully wrote animated GIF with {} frames for {}",
                    framesToWrite.size(), outputFile.getName());
        }
    }

    private static void writeSingleFrameGif(File outputFile, BufferedImage frame) throws IOException {
        boolean success = ImageIO.write(frame, "GIF", outputFile);
        if (!success) {
            throw new IOException("No GIF writer available for " + outputFile.getAbsolutePath());
        }
    }

    private static void writeAnimatedGif(
            File outputFile,
            List<BufferedImage> frames,
            int frameDelayMs,
            boolean loopContinuously) throws IOException {
        ImageWriter writer = getGifWriter();
        ImageWriteParam params = writer.getDefaultWriteParam();
        ImageOutputStream outputStream = null;
        try {
            outputStream = ImageIO.createImageOutputStream(outputFile);
            writer.setOutput(outputStream);
            writer.prepareWriteSequence(null);

            int delayTimeCs = Math.max(1, frameDelayMs / 10);
            for (int i = 0; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);
                ImageTypeSpecifier typeSpecifier = new ImageTypeSpecifier(frame);
                IIOMetadata metadata = writer.getDefaultImageMetadata(typeSpecifier, params);
                configureGifMetadata(metadata, delayTimeCs, loopContinuously && i == 0);
                writer.writeToSequence(new IIOImage(frame, null, metadata), params);
            }

            writer.endWriteSequence();
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            writer.dispose();
        }
    }

    private static ImageWriter getGifWriter() throws IIOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersBySuffix(GIF_FORMAT);
        if (!writers.hasNext()) {
            throw new IIOException("No GIF ImageWriter available");
        }
        return writers.next();
    }

    private static void configureGifMetadata(
            IIOMetadata metadata,
            int delayTimeCs,
            boolean writeLoopExtension) throws IIOException {
        String nativeFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(nativeFormat);

        IIOMetadataNode graphicControlExtension =
                getOrCreateNode(root, "GraphicControlExtension");
        graphicControlExtension.setAttribute("disposalMethod", "none");
        graphicControlExtension.setAttribute("userInputFlag", "FALSE");
        graphicControlExtension.setAttribute("transparentColorFlag", "FALSE");
        graphicControlExtension.setAttribute("delayTime", Integer.toString(delayTimeCs));
        graphicControlExtension.setAttribute("transparentColorIndex", "0");

        if (writeLoopExtension) {
            IIOMetadataNode appExtensions =
                    getOrCreateNode(root, "ApplicationExtensions");
            IIOMetadataNode appExtension = new IIOMetadataNode("ApplicationExtension");
            appExtension.setAttribute("applicationID", "NETSCAPE");
            appExtension.setAttribute("authenticationCode", "2.0");
            appExtension.setUserObject(new byte[] { 0x1, 0x0, 0x0 });
            appExtensions.appendChild(appExtension);
        }

        metadata.setFromTree(nativeFormat, root);
    }

    private static IIOMetadataNode getOrCreateNode(IIOMetadataNode rootNode, String nodeName) {
        for (int i = 0; i < rootNode.getLength(); i++) {
            if (nodeName.equals(rootNode.item(i).getNodeName())) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return node;
    }

    private GifRenderer() {}
}
