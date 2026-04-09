package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.exporter.main.Logger;
import net.minecraft.util.IIcon;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for capturing and writing animated GIF images.
 * Uses a simple GIF encoder implementation.
 */
public class GifRenderer {

    /**
     * Check if an icon supports animation by checking if it implements IPatchedTextureAtlasSprite.
     */
    public static boolean isAnimated(IIcon icon) {
        if (!ConfigOptions.EXPORT_GIF.get()) {
            return false;
        }
        try {
            Class<?> patchedSpriteClass = Class.forName("com.mitchej123.hodgepodge.textures.IPatchedTextureAtlasSprite");
            return patchedSpriteClass.isInstance(icon);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Capture multiple frames of an animation.
     * This should be called over multiple render ticks.
     */
    public static class AnimationCapture {
        private final File outputFile;
        private final List<BufferedImage> frames = new ArrayList<>();
        private int currentFrame = 0;
        private final int totalFrames;
        private final int frameDelayMs;
        private final boolean loop;

        // 动态检测：前N帧用于检测是否有动画
        private static final int DETECTION_FRAME_COUNT = 10;
        private boolean detectedAnimation = false;
        private boolean detectionComplete = false;

        // 超时检测：防止某个物品捕获时间过长
        private final long startTimeMs = System.currentTimeMillis();
        private static final long MAX_CAPTURE_TIME_MS = 30000; // 最多30秒

        public AnimationCapture(File outputFile) {
            this.outputFile = outputFile;
            this.totalFrames = ConfigOptions.GIF_FRAMES.get();
            this.frameDelayMs = ConfigOptions.GIF_FRAME_DELAY_MS.get();
            this.loop = ConfigOptions.GIF_LOOP_COUNT.get() == 0; // 0 means infinite loop
        }

        /**
         * 检查是否超时
         */
        public boolean isTimedOut() {
            long elapsedMs = System.currentTimeMillis() - startTimeMs;
            if (elapsedMs > MAX_CAPTURE_TIME_MS) {
                Logger.MOD.warn("Capture timeout for {} after {}ms (captured {} frames)",
                    outputFile.getName(), elapsedMs, currentFrame);
                return true;
            }
            return false;
        }

        /**
         * 检查是否应该提前完成（静态物品检测）
         * 如果前DETECTION_FRAME_COUNT帧都完全相同，认为是静态物品，停止捕获
         */
        public boolean shouldStopEarly(boolean allowStaticDetection) {
            if (!allowStaticDetection) {
                return false;
            }
            if (detectionComplete) {
                return false; // 已检测到动画或已完成检测，继续正常流程
            }

            if (currentFrame < DETECTION_FRAME_COUNT) {
                return false; // 还在检测期，继续捕获
            }

            // 检测期结束，检查是否有动画
            if (!detectedAnimation && areAllFramesIdentical()) {
                Logger.MOD.info("Static item detected for {} (first {} frames identical), stopping at {} frames",
                    outputFile.getName(), DETECTION_FRAME_COUNT, currentFrame);
                return true; // 静态物品，提前停止
            }

            // 检测到动画或有变化，继续完整捕获
            detectionComplete = true;
            if (detectedAnimation) {
                Logger.MOD.info("Animation detected for {}, will capture full {} frames",
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

        /**
         * 检查所有捕获的帧是否100%完全相同（像素级精确比较）
         * 只有所有帧的每个像素都完全相同才返回true
         */
        private boolean areAllFramesIdentical() {
            if (frames.isEmpty() || frames.size() == 1) {
                return true;
            }

            BufferedImage firstFrame = frames.get(0);
            if (firstFrame == null) {
                return false;
            }

            int width = firstFrame.getWidth();
            int height = firstFrame.getHeight();

            // 逐个像素比较所有帧
            for (int i = 1; i < frames.size(); i++) {
                BufferedImage frame = frames.get(i);
                if (frame == null) {
                    return false;
                }

                // 检查尺寸
                if (frame.getWidth() != width || frame.getHeight() != height) {
                    return false;
                }

                // 100%像素级比较，不允许任何差异
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        if (firstFrame.getRGB(x, y) != frame.getRGB(x, y)) {
                            return false; // 发现任何不同像素，立即返回false
                        }
                    }
                }
            }

            return true; // 所有帧100%相同
        }

        public void addFrame(BufferedImage frame) {
            if (!isComplete()) {
                // Validate frame has valid image data
                if (frame == null || frame.getWidth() <= 0 || frame.getHeight() <= 0) {
                    Logger.MOD.error("Invalid frame dimensions for {}: {}x{}",
                        outputFile.getName(),
                        frame != null ? frame.getWidth() : "null",
                        frame != null ? frame.getHeight() : "null");
                    return;
                }

                // 检测动画：在检测期内，比较当前帧与前一帧是否有变化
                if (!detectionComplete && currentFrame > 0 && !detectedAnimation) {
                    BufferedImage previousFrame = frames.get(currentFrame - 1);
                    if (!areFramesIdentical(previousFrame, frame)) {
                        detectedAnimation = true;
                        Logger.MOD.debug("Animation detected at frame {} for {}",
                            currentFrame + 1, outputFile.getName());
                    }
                }

                // 添加帧
                frames.add(frame);
                currentFrame++;

                // 每隔10帧输出一次进度信息
                if (currentFrame % 10 == 0) {
                    Logger.MOD.info("Added frame {} of {} for {}",
                        currentFrame, totalFrames, outputFile.getName());
                }
            }
        }

        /**
         * 快速比较两帧是否100%相同
         */
        private boolean areFramesIdentical(BufferedImage frame1, BufferedImage frame2) {
            if (frame1 == null || frame2 == null) {
                return false;
            }

            if (frame1.getWidth() != frame2.getWidth() ||
                frame1.getHeight() != frame2.getHeight()) {
                return false;
            }

            int width = frame1.getWidth();
            int height = frame1.getHeight();

            // 100%像素级比较
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (frame1.getRGB(x, y) != frame2.getRGB(x, y)) {
                        return false; // 发现任何不同像素
                    }
                }
            }

            return true;
        }

        public void writeGif() throws IOException {
            if (frames.isEmpty()) {
                Logger.MOD.warn("No frames to write for {}", outputFile.getName());
                return;
            }

            // Validate first frame
            BufferedImage firstFrame = frames.get(0);
            if (firstFrame == null) {
                Logger.MOD.error("First frame is null for {}", outputFile.getName());
                return;
            }

            // Check image dimensions
            if (firstFrame.getWidth() <= 0 || firstFrame.getHeight() <= 0) {
                Logger.MOD.error("First frame has invalid dimensions for {}: {}x{}",
                    outputFile.getName(), firstFrame.getWidth(), firstFrame.getHeight());
                return;
            }

            // Validate parent directory exists
            if (!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdirs();
            }

            // 检查所有帧是否100%相同
            if (areAllFramesIdentical()) {
                // 所有帧完全相同，只保存第一帧为PNG
                File pngFile = new File(outputFile.getParentFile(),
                                       outputFile.getName().replace(".gif", ".png"));

                try {
                    boolean success = javax.imageio.ImageIO.write(firstFrame, "PNG", pngFile);
                    if (!success) {
                        Logger.MOD.error("Failed to write PNG for {} - no appropriate writer found",
                            outputFile.getName());
                        return;
                    }
                    Logger.MOD.info("All {} frames are 100% identical. Wrote single PNG: {}",
                                   frames.size(), pngFile.getName());
                } catch (Exception e) {
                    Logger.MOD.error("Exception writing PNG for {}: {}",
                        outputFile.getName(), e.getMessage(), e);
                    throw e;
                }
            } else {
                // 帧有差异，保存所有帧为独立的PNG文件
                File pngFile = new File(outputFile.getParentFile(),
                                       outputFile.getName().replace(".gif", ".png"));

                try {
                    // 写入第一帧
                    boolean success = javax.imageio.ImageIO.write(firstFrame, "PNG", pngFile);
                    if (!success) {
                        Logger.MOD.error("Failed to write PNG for {} - no appropriate writer found",
                            outputFile.getName());
                        return;
                    }

                    Logger.MOD.info("Frames have variations. Writing {} frames as individual PNG files: {}",
                                   frames.size(), pngFile.getName());

                    // 写入其他帧（带编号）
                    for (int i = 1; i < frames.size(); i++) {
                        BufferedImage frame = frames.get(i);
                        if (frame != null && frame.getWidth() > 0 && frame.getHeight() > 0) {
                            File frameFile = new File(outputFile.getParentFile(),
                                    outputFile.getName().replace(".gif", "_frame" + i + ".png"));
                            javax.imageio.ImageIO.write(frame, "PNG", frameFile);
                        } else {
                            Logger.MOD.warn("Skipping invalid frame {} for {}", i, outputFile.getName());
                        }
                    }

                    Logger.MOD.info("Successfully wrote {} frames for {}", frames.size(), outputFile.getName());
                } catch (Exception e) {
                    Logger.MOD.error("Exception writing PNG files for {}: {}",
                        outputFile.getName(), e.getMessage(), e);
                    throw e;
                }
            }
        }
    }

    // Private constructor - this is a utility class
    private GifRenderer() {}
}
