package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.main.Logger;

import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Strict high-priority queue for recipe-frame captures.
 *
 * <p>These requests hold live NEI handler recipe objects, so exporters wait for this queue to drain
 * before clearing handler state. This is intentionally separate from item icon rendering: native
 * recipe frames must not sit behind thousands of atlas jobs while the handler is discarded.</p>
 */
public enum NativeNeiFrameRenderDispatcher {
    INSTANCE;

    private final ConcurrentLinkedQueue<NativeNeiFrameRenderRequest> queue =
            new ConcurrentLinkedQueue<NativeNeiFrameRenderRequest>();

    private Throwable failure;
    private long completed;
    private int active;

    public synchronized void reset() {
        queue.clear();
        failure = null;
        completed = 0L;
        active = 0;
        notifyAll();
    }

    public void submit(NativeNeiFrameRenderRequest request) {
        if (request == null) {
            return;
        }
        queue.add(request);
    }

    public Optional<NativeNeiFrameRenderRequest> getJob() {
        NativeNeiFrameRenderRequest request = queue.poll();
        if (request != null) {
            synchronized (this) {
                active++;
            }
        }
        return Optional.ofNullable(request);
    }

    public boolean hasJobs() {
        return !queue.isEmpty();
    }

    public int getJobCount() {
        synchronized (this) {
            return queue.size() + active;
        }
    }

    public synchronized long getCompletedCount() {
        return completed;
    }

    public synchronized void completeJob(NativeNeiFrameRenderRequest request) {
        if (active > 0) {
            active--;
        }
        completed++;
        notifyAll();
    }

    public synchronized void failJob(NativeNeiFrameRenderRequest request, Throwable throwable) {
        if (active > 0) {
            active--;
        }
        if (failure == null) {
            failure = throwable == null ? new IllegalStateException("Unknown native NEI frame render failure") : throwable;
        }
        String recipeId = request == null ? "<unknown>" : request.getRecipeId();
        Logger.MOD.error("Native NEI frame render failed for recipe {}", recipeId, failure);
        notifyAll();
    }

    public synchronized void waitUntilJobsComplete() throws InterruptedException {
        while ((!queue.isEmpty() || active > 0) && failure == null) {
            wait(1000L);
        }
        if (failure != null) {
            throw new IllegalStateException("Native NEI frame export failed", failure);
        }
    }
}
