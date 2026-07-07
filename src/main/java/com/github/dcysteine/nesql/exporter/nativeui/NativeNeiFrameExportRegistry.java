package com.github.dcysteine.nesql.exporter.nativeui;

import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.util.render.NativeNeiFrameRenderDispatcher;
import com.github.dcysteine.nesql.exporter.util.render.NativeNeiFrameRenderRequest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Run-scoped bridge between recipe exporters and the strict native NEI frame renderer.
 *
 * <p>The compiler ABI requires a top-level {@code nativeFrame} object on every recipe that opts into
 * native UI. This registry creates that ABI object and queues the matching in-game render request.
 * Missing captures are fatal; callers must drain the queue before discarding live NEI handler state.</p>
 */
public final class NativeNeiFrameExportRegistry {
    private static final Object LOCK = new Object();

    private static boolean enabled;
    private static File rawExportDirectory;
    private static long queuedFrames;

    private NativeNeiFrameExportRegistry() {}

    public static void begin(File repositoryDirectory, boolean enableNativeFrames) {
        synchronized (LOCK) {
            enabled = enableNativeFrames;
            rawExportDirectory = repositoryDirectory == null
                    ? null
                    : new File(repositoryDirectory, "raw-export");
            queuedFrames = 0L;
            NativeNeiFrameRenderDispatcher.INSTANCE.reset();
        }
    }

    public static void end() {
        synchronized (LOCK) {
            enabled = false;
            rawExportDirectory = null;
            queuedFrames = 0L;
        }
    }

    public static boolean isEnabled() {
        synchronized (LOCK) {
            return enabled && rawExportDirectory != null;
        }
    }

    public static long getQueuedFrames() {
        synchronized (LOCK) {
            return queuedFrames;
        }
    }

    public static Map<String, Object> captureRecipeFrame(
            String recipeId,
            IRecipeHandler handler,
            int recipeIndex) {
        if (recipeId == null || recipeId.trim().isEmpty() || handler == null || !isEnabled()) {
            return null;
        }

        FrameSurface surface = resolveSurface(handler, recipeIndex);
        String assetRef = assetRefForRecipe(recipeId);
        File outputFile;
        synchronized (LOCK) {
            outputFile = new File(rawExportDirectory, assetRef.replace('/', File.separatorChar));
            queuedFrames++;
        }

        NativeNeiFrameRenderDispatcher.INSTANCE.submit(
                new NativeNeiFrameRenderRequest(
                        recipeId,
                        assetRef,
                        outputFile,
                        handler,
                        recipeIndex,
                        surface.width,
                        surface.height));

        Map<String, Object> nativeFrame = new LinkedHashMap<String, Object>();
        nativeFrame.put("status", NativeUiExportAbi.NATIVE_FRAME_STATUS_CAPTURED);
        nativeFrame.put("assetRef", assetRef);
        nativeFrame.put("width", surface.width);
        nativeFrame.put("height", surface.height);
        nativeFrame.put("coordinateSpace", NativeUiExportAbi.COORDINATE_SPACE);
        nativeFrame.put("source", NativeUiExportAbi.NATIVE_FRAME_SOURCE_IN_GAME_NEI_RENDER);
        nativeFrame.put("handlerKey", safeHandlerId(handler));
        nativeFrame.put("handlerClass", handler.getClass().getName());
        nativeFrame.put("recipeIndex", recipeIndex);
        return nativeFrame;
    }

    public static void awaitPendingFrames() {
        try {
            NativeNeiFrameRenderDispatcher.INSTANCE.waitUntilJobsComplete();
        } catch (InterruptedException wakeUp) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for native NEI frame captures", wakeUp);
        }
    }

    private static FrameSurface resolveSurface(IRecipeHandler handler, int recipeIndex) {
        int width = 166;
        int height = 65;
        try {
            java.lang.reflect.Method method = handler.getClass().getMethod("getRecipeHeight", int.class);
            Object value = method.invoke(handler, recipeIndex);
            if (value instanceof Number && ((Number) value).intValue() > 0) {
                height = Math.max(height, ((Number) value).intValue());
            }
        } catch (Throwable heightFailure) {
            Logger.MOD.warn(
                    "Could not resolve NEI frame surface for handler {} recipe {}; using {}x{}",
                    handler.getClass().getName(),
                    recipeIndex,
                    width,
                    height,
                    heightFailure);
        }
        return new FrameSurface(width, height);
    }

    private static String assetRefForRecipe(String recipeId) {
        String digest = sha256Hex(recipeId);
        String shard = digest.substring(0, 2);
        return NativeUiExportAbi.NATIVE_NEI_FRAMES_DIRECTORY + "/" + shard + "/" + digest + ".png";
    }

    private static String safeHandlerId(IRecipeHandler handler) {
        try {
            String handlerId = handler.getHandlerId();
            if (handlerId != null && !handlerId.trim().isEmpty()) {
                return handlerId;
            }
        } catch (Throwable ignored) {
        }
        return handler.getClass().getName();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b & 0xff));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash native NEI frame recipe id", e);
        }
    }

    private static final class FrameSurface {
        final int width;
        final int height;

        FrameSurface(int width, int height) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }
    }
}
