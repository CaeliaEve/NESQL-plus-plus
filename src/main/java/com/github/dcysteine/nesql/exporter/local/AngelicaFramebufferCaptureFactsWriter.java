package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Streams framebuffer capture facts produced by native Angelica render dispatch. */
final class AngelicaFramebufferCaptureFactsWriter {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final String schemaRoot;
    private final List<CanonicalRenderAsset> renderAssets;

    AngelicaFramebufferCaptureFactsWriter(String schemaRoot, List<CanonicalRenderAsset> renderAssets) {
        if (schemaRoot == null) {
            throw new IllegalArgumentException("Angelica framebuffer capture schema root must not be null");
        }
        if (renderAssets == null) {
            throw new IllegalArgumentException("Angelica framebuffer capture render assets must not be null");
        }
        this.schemaRoot = schemaRoot;
        this.renderAssets = Collections.unmodifiableList(new ArrayList<CanonicalRenderAsset>(renderAssets));
    }

    AngelicaFramebufferCaptureStreamCounts write(File out) throws IOException {
        AngelicaFramebufferCaptureStreamCounts counts = new AngelicaFramebufferCaptureStreamCounts();
        try (OutputStreamWriter writer = AngelicaRenderFactFileOps.createUtf8JsonlWriter(out)) {
            for (CanonicalRenderAsset asset : renderAssets) {
                if (!isFramebufferCaptureAsset(asset)) {
                    continue;
                }
                JsonObject row = new JsonObject();
                row.addProperty("schemaVersion", schemaRoot + "/framebuffer-capture");
                row.addProperty("assetId", asset.assetId);
                row.addProperty("variantKey", asset.variantKey);
                row.addProperty("family", asset.family);
                row.addProperty("rendererFamily", asset.rendererFamily);
                row.addProperty("renderMode", asset.renderMode);
                row.addProperty("animationMode", asset.animationMode);
                row.addProperty("captureMethod", asset.captureMethod);
                row.addProperty("captureSource", asset.captureSource);
                row.addProperty("primaryArtifact", asset.primaryArtifact);
                row.addProperty("staticFile", asset.staticFile);
                row.addProperty("framePattern", asset.framePattern);
                row.addProperty("frameCount", asset.frameCount);
                row.addProperty("capturedFrameCount", asset.capturedFrameCount);
                row.addProperty("configuredFrameCount", asset.configuredFrameCount);
                row.addProperty("frameDurationMs", asset.frameDurationMs);
                row.addProperty("frameDurationSource", asset.frameDurationSource);
                row.add("frames", GSON.toJsonTree(asset.frames));
                row.add("timeline", GSON.toJsonTree(asset.timeline));
                row.add("rendererContract", GSON.toJsonTree(asset.rendererContract));
                row.add("shaderContract", GSON.toJsonTree(asset.shaderContract));
                row.add("captureContract", GSON.toJsonTree(asset.captureContract));
                row.addProperty("source", "existing-render-dispatcher-capture");
                writer.write(GSON.toJson(row));
                writer.write('\n');
                counts.framebufferCaptures++;
                if (asset.assetId != null) {
                    counts.captureAssetIds.add(asset.assetId);
                }
                if (asset.variantKey != null) {
                    counts.captureVariantKeys.add(asset.variantKey);
                }
                if (asset.frames == null || asset.frames.isEmpty()) {
                    counts.framebufferCapturesWithoutFrames++;
                    if (counts.framebufferCapturesWithoutFramesSamples.size() < 20) {
                        counts.framebufferCapturesWithoutFramesSamples.add(asset.assetId == null ? String.valueOf(asset.variantKey) : asset.assetId);
                    }
                }
            }
        }
        return counts;
    }

    private static boolean isFramebufferCaptureAsset(CanonicalRenderAsset asset) {
        if (asset == null) {
            return false;
        }
        if (containsIgnoreCase(asset.captureMethod, "native_sprite_metadata")
                || containsIgnoreCase(asset.captureSource, "native_sprite_metadata")
                || containsIgnoreCase(asset.renderMode, "native_sprite")) {
            return false;
        }
        return containsIgnoreCase(asset.captureMethod, "framebuffer")
                || containsIgnoreCase(asset.captureSource, "framebuffer")
                || containsIgnoreCase(asset.captureSource, "inventory_renderer_capture")
                || containsIgnoreCase(asset.captureSource, "inventory_renderer_family_capture")
                || containsIgnoreCase(asset.renderMode, "framebuffer")
                || containsIgnoreCase(asset.renderMode, "captured_final_atlas")
                || containsIgnoreCase(asset.animationMode, "framebuffer")
                || containsIgnoreCase(asset.mode, "framebuffer")
                || containsIgnoreCase(asset.mode, "rendered_frames")
                || containsIgnoreCase(asset.primaryArtifact, ".gif")
                || asset.framePattern != null
                || (asset.capturedFrameCount != null && asset.capturedFrameCount > 1);
    }

    private static boolean containsIgnoreCase(String value, String token) {
        return value != null && token != null && value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

}
