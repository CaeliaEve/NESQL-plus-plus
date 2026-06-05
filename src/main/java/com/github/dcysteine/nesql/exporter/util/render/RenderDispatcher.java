package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import net.minecraft.util.EnumChatFormatting;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Singleton class which handles inter-thread communication and dispatching of render jobs.
 *
 * <p>Unfortunately, we cannot render in our own thread because it does not have an OpenGL context.
 * So we must do all of our rendering in the client thread.
 *
 * <p>This class's singleton instance will also be used as a lock. The contract for this is:
 * <ul>
 *     <li>The export thread will call {@link Object#wait()} when it wants to wait for render
 *         jobs to complete
 *     <li>The client thread will call {@link Object#notifyAll()} when all render jobs have
 *         completed
 * </ul>
 */
public enum RenderDispatcher {
    /** Singleton class, enforced by enum. */
    INSTANCE;

    public enum RendererState {
        /** Renderer is newly constructed or destroyed. */
        UNINITIALIZED,

        /** Exporter thread has requested renderer initialization. */
        INITIALIZING,

        /** Renderer has initialized itself. */
        INITIALIZED,

        /** Exporter thread has requested renderer destruction. */
        DESTROYING,

        /** We transition to this state if something went wrong during rendering. */
        ERROR,
    }

    /** Map of valid state transitions. Used for validation. */
    private static final ImmutableSetMultimap<RendererState, RendererState> STATE_TRANSITIONS =
            ImmutableSetMultimap.<RendererState, RendererState>builder()
                    .putAll(RendererState.UNINITIALIZED,
                            RendererState.INITIALIZING, RendererState.ERROR)
                    .putAll(RendererState.INITIALIZING,
                            RendererState.INITIALIZED, RendererState.ERROR)
                    .putAll(RendererState.INITIALIZED,
                            RendererState.DESTROYING, RendererState.ERROR)
                    .putAll(RendererState.DESTROYING,
                            RendererState.UNINITIALIZED, RendererState.ERROR)
                    .putAll(RendererState.ERROR,
                            RendererState.UNINITIALIZED, RendererState.INITIALIZING,
                            RendererState.ERROR)
                    .build();

    private static final ImmutableSet<RendererState> ACTIVE_STATES =
            ImmutableSet.of(RendererState.INITIALIZING, RendererState.INITIALIZED);

    /**
     * Used to keep track of current {@link Renderer} state, as well as ask it to initialize or
     * destroy itself.
     *
     * <p>The renderer can only be initialized or destroyed on the client thread, so we need this
     * dispatcher to handle sending state change requests from the exporter thread to the client
     * thread.
     *
     * <p>In order to avoid concurrency issues, it must be the case that each state transition can
     * only be performed by either the exporter thread or the client thread, and not both!
     */
    private RendererState rendererState = RendererState.UNINITIALIZED;

    // 单帧作业队列（普通物品）
    private final ConcurrentLinkedQueue<RenderJob> singleFrameJobQueue = new ConcurrentLinkedQueue<>();

    // 多帧作业队列（动画物品，优先处理）
    private final ConcurrentLinkedQueue<RenderJob> multiFrameJobQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<RenderJob> multiFrameDeferredQueue = new ConcurrentLinkedQueue<>();
    private final java.util.Set<String> queuedOutputPaths =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final java.util.concurrent.atomic.AtomicInteger duplicateJobSkipCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger existingOutputSkipCount =
            new java.util.concurrent.atomic.AtomicInteger();

    // Track active GIF animation captures by output file path
    private final ConcurrentHashMap<String, GifRenderer.AnimationCapture> activeCaptures = new ConcurrentHashMap<>();

    /**
     * Multi-frame framebuffer capture is intentionally serialized to one frame per client tick.
     * GTNH 2.8.4/Angelica can hard-exit when hundreds of animated GT metaitems advance and read
     * back OpenGL framebuffers concurrently in the same tick.
     */
    private volatile boolean multiFrameWorkDoneThisTick = false;

    private File imageDirectory;

    public void setImageDirectory(File imageDirectory) {
        this.imageDirectory = imageDirectory;
    }

    public RendererState getRendererState() {
        return rendererState;
    }

    public synchronized void setRendererState(RendererState newState) {
        if (!STATE_TRANSITIONS.get(rendererState).contains(newState)) {
            throw new IllegalStateException(
                    String.format(
                            "Attempted invalid state transition: %s to %s."
                                    + "\nDid you start up another export"
                                    + " while one was still in progress?",
                            rendererState, newState));
        }

        if (newState == RendererState.INITIALIZING) {
            queuedOutputPaths.clear();
            duplicateJobSkipCount.set(0);
            existingOutputSkipCount.set(0);
        }

        if ((newState == RendererState.UNINITIALIZED || newState == RendererState.INITIALIZING)
                && (!singleFrameJobQueue.isEmpty()
                || !multiFrameJobQueue.isEmpty()
                || !multiFrameDeferredQueue.isEmpty()
                || !activeCaptures.isEmpty())) {
            Logger.chatMessage(String.format(
                    EnumChatFormatting.RED + "Render dispatcher has %s single-frame jobs, %s multi-frame jobs, %s deferred multi-frame jobs and %s active captures!"
                            + "\nClearing and transitioning to state %s.",
                    singleFrameJobQueue.size(), multiFrameJobQueue.size(), multiFrameDeferredQueue.size(), activeCaptures.size(), newState.name()));
            singleFrameJobQueue.clear();
            multiFrameJobQueue.clear();
            multiFrameDeferredQueue.clear();
            queuedOutputPaths.clear();
            duplicateJobSkipCount.set(0);
            existingOutputSkipCount.set(0);
            activeCaptures.clear();
        } else if (newState == RendererState.ERROR) {
            singleFrameJobQueue.clear();
            multiFrameJobQueue.clear();
            multiFrameDeferredQueue.clear();
            queuedOutputPaths.clear();
            duplicateJobSkipCount.set(0);
            existingOutputSkipCount.set(0);
            activeCaptures.clear();
        }

        rendererState = newState;
    }

    public synchronized boolean isActive() {
        return ACTIVE_STATES.contains(rendererState);
    }

    public synchronized void waitUntilJobsComplete() throws InterruptedException {
        while ((!singleFrameJobQueue.isEmpty()
                || !multiFrameJobQueue.isEmpty()
                || !multiFrameDeferredQueue.isEmpty()
                || !activeCaptures.isEmpty()) && isActive()) {
            wait();
        }
    }

    /**
     * This method is kept unsynchronized since it runs on the render thread, and we want it to be
     * fast. This should be safe.
     */
    public synchronized void notifyJobsCompleted() {
        notifyAll();
    }

    public int getJobCount() {
        return singleFrameJobQueue.size() + multiFrameJobQueue.size() + multiFrameDeferredQueue.size();
    }

    /**
     * Get the total count of jobs including multi-frame captures.
     */
    public int getTotalJobCount() {
        return singleFrameJobQueue.size() + multiFrameJobQueue.size() + multiFrameDeferredQueue.size() + activeCaptures.size();
    }

    public int getActiveCaptureCount() {
        return activeCaptures.size();
    }

    public boolean hasMultiFrameWorkRemaining() {
        return !multiFrameJobQueue.isEmpty() || !multiFrameDeferredQueue.isEmpty() || !activeCaptures.isEmpty();
    }

    public int getDuplicateJobSkipCount() {
        return duplicateJobSkipCount.get();
    }

    public int getExistingOutputSkipCount() {
        return existingOutputSkipCount.get();
    }

    public void addJob(RenderJob job) {
        if (job == null) {
            return;
        }
        String outputPath = job.getOutputFilePath();
        if (shouldSkipExistingOutput(job, outputPath)) {
            int skipped = existingOutputSkipCount.incrementAndGet();
            if (skipped == 1 || skipped % 1000 == 0) {
                Logger.MOD.info("Skipped {} existing render outputs so far", skipped);
            }
            return;
        }
        if (outputPath != null && !queuedOutputPaths.add(outputPath)) {
            int skipped = duplicateJobSkipCount.incrementAndGet();
            if (skipped == 1 || skipped % 1000 == 0) {
                Logger.MOD.info("Skipped {} duplicate render jobs so far", skipped);
            }
            return;
        }
        if (job.needsMultipleFrames()) {
            multiFrameJobQueue.add(job);
        } else {
            singleFrameJobQueue.add(job);
        }
    }

    public void addAllJob(Collection<RenderJob> jobs) {
        for (RenderJob job : jobs) {
            addJob(job); // 使用addJob方法以正确分类
        }
    }

    public void clearJobs() {
        singleFrameJobQueue.clear();
        multiFrameJobQueue.clear();
        multiFrameDeferredQueue.clear();
        queuedOutputPaths.clear();
        duplicateJobSkipCount.set(0);
        activeCaptures.clear();
    }

    private boolean shouldSkipExistingOutput(RenderJob job, String outputPath) {
        if (imageDirectory == null || outputPath == null || outputPath.isEmpty()) {
            return false;
        }
        if (job.getType() == RenderJob.JobType.ENTITY) {
            return false;
        }

        File outputFile = new File(imageDirectory, outputPath);
        if ((!outputFile.exists() || outputFile.length() <= 0L)
                && RenderSignatureSupport.restoreFromCache(imageDirectory, job)) {
            return true;
        }
        if (!outputFile.exists() || outputFile.length() <= 0L) {
            return false;
        }
        if (!RenderSignatureSupport.matches(imageDirectory, job)) {
            return false;
        }

        File renderContractFile = new File(imageDirectory, job.getRenderContractFilePath());
        if (!renderContractFile.exists()) {
            return false;
        }

        if (job.shouldWriteNativeSpriteMetadata()) {
            File spriteMetadataFile = new File(imageDirectory, job.getSpriteMetadataFilePath());
            if (!spriteMetadataFile.exists()) {
                return false;
            }
        }

        if (job.shouldPreferNativeSpriteAnimation()) {
            File nativeSpriteAtlasFile = new File(imageDirectory, job.getNativeSpriteAtlasFilePath());
            if (!nativeSpriteAtlasFile.exists() || nativeSpriteAtlasFile.length() <= 0L) {
                return false;
            }
        }

        return true;
    }

    public void beginClientTick() {
        multiFrameWorkDoneThisTick = false;
    }

    /**
     * This method is kept unsynchronized since it runs on the render thread, and we want it to be
     * fast. This should be safe.
     */
    public boolean noJobsRemaining() {
        return singleFrameJobQueue.isEmpty()
                && multiFrameJobQueue.isEmpty()
                && multiFrameDeferredQueue.isEmpty()
                && activeCaptures.isEmpty();
    }

    /**
     * This method is kept unsynchronized since it runs on the render thread, and we want it to be
     * fast. This should be safe.
     */
    public Optional<RenderJob> getJob() {
        RenderJob job = null;

        // Keep one animated framebuffer capture active at a time, and advance it by at most one
        // frame per client tick. This avoids GTNH 2.8.4 hard exits caused by dense animated
        // metaitem queues hammering texture updates + framebuffer readback in one tick.
        if (!activeCaptures.isEmpty()) {
            if (multiFrameWorkDoneThisTick) {
                return Optional.empty();
            }
            job = multiFrameDeferredQueue.poll();
            if (job == null) {
                return Optional.empty();
            }
            multiFrameWorkDoneThisTick = true;
            return Optional.of(job);
        }

        if (!multiFrameWorkDoneThisTick) {
            job = multiFrameDeferredQueue.poll();
            if (job == null) {
                job = multiFrameJobQueue.poll();
            }
            if (job != null) {
                multiFrameWorkDoneThisTick = true;
            }
        }

        if (job == null) {
            job = singleFrameJobQueue.poll();
        }

        if (job != null && job.needsMultipleFrames()) {
            String outputPath = job.getOutputFilePath();
            File outputFile = new File(imageDirectory, outputPath);

            if (!activeCaptures.containsKey(outputPath)) {
                activeCaptures.put(
                        outputPath,
                        new GifRenderer.AnimationCapture(
                                outputFile,
                                job.getRequestedFrameCount(),
                                job.getRequestedFrameDelayMs()));
            }
        }

        return Optional.ofNullable(job);
    }
    /**
     * Called by Renderer after rendering a frame. Handles multi-frame capture logic.
     *
     * @param job The render job
     * @param image The rendered image
     */
    public void completeJob(RenderJob job, BufferedImage image) {
        if (job.needsMultipleFrames()) {
            String outputPath = job.getOutputFilePath();
            GifRenderer.AnimationCapture capture = activeCaptures.get(outputPath);

            // Bug fix: 如果capture为null，说明这个job的状态丢失了，直接跳过避免无限循环
            if (capture == null) {
                Logger.MOD.error("AnimationCapture not found for {}, skipping job (frame {})",
                    outputPath, job.getFrameIndex());
                // 不要重新排队，直接丢弃这个job
                return;
            }

            // Check for null image before adding
            if (image == null) {
                Logger.MOD.error("Rendered image is null for {}, skipping frame", outputPath);
                activeCaptures.remove(outputPath);
                return;
            }

            // Check for valid image dimensions
            if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                Logger.MOD.error("Rendered image has invalid dimensions for {}: {}x{}",
                    outputPath, image.getWidth(), image.getHeight());
                activeCaptures.remove(outputPath);
                return;
            }

            // Add this frame to the capture (no deduplication or loop detection)
            capture.addFrame(image);

            // Check for timeout
            if (capture.isTimedOut()) {
                Logger.MOD.warn("Capture timeout for {}, writing {} frames and skipping",
                    outputPath, capture.getCurrentFrameCount());
                try {
                    capture.writeGif();
                } catch (IOException e) {
                    Logger.MOD.error("Failed to write GIF on timeout: " + outputPath, e);
                }
                activeCaptures.remove(outputPath);
                return;
            }

            // Check if we should stop early (static item detected)
            if (capture.shouldStopEarly(!job.shouldForceFullCapture())) {
                // Static item detected, write only the first frame
                try {
                    capture.writeGif();
                    RenderSignatureSupport.write(imageDirectory, job);
                    activeCaptures.remove(outputPath);

                    Logger.MOD.debug("Static item export complete: {} (captured {} frames)",
                        outputPath, capture.getCurrentFrameCount());
                } catch (IOException e) {
                    Logger.MOD.error("Failed to write GIF: " + outputPath, e);
                    activeCaptures.remove(outputPath);
                }
                return;
            }

            // Check if capture is complete (reached target frame count)
            if (capture.isComplete()) {
                // Write the GIF file
                try {
                    capture.writeGif();
                    RenderSignatureSupport.write(imageDirectory, job);
                    activeCaptures.remove(outputPath);

                    Logger.MOD.debug("Animated item export complete: {} (captured {} frames)",
                        outputPath, capture.getCurrentFrameCount());
                } catch (IOException e) {
                    Logger.MOD.error("Failed to write GIF: " + outputPath, e);
                    activeCaptures.remove(outputPath);
                }
            } else {
                // Re-queue the same job for next frame
                job.incrementFrame();
                multiFrameDeferredQueue.add(job);

                Logger.MOD.debug("Captured frame {} of {} for {}",
                    job.getFrameIndex(), capture.getTotalFrames(), outputPath);
            }
        }
        // Single-frame jobs are handled directly by Renderer
    }

    public void abandonJob(RenderJob job) {
        if (job == null || !job.needsMultipleFrames()) {
            return;
        }

        String outputPath = job.getOutputFilePath();
        if (outputPath != null) {
            activeCaptures.remove(outputPath);
        }
    }
}
