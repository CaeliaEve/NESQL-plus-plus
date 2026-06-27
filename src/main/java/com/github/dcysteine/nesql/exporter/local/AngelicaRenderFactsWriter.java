package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import jakarta.persistence.EntityManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Exports Angelica-native render facts for raw-export.
 *
 * <p>This writer records facts that NeoNEI can consume directly instead of guessing from static
 * images: active render backend, native atlas sprite animation timelines, and inventory
 * IItemRenderer classifications.</p>
 */
final class AngelicaRenderFactsWriter {
    private static final String SCHEMA_ROOT = "nesqlpp/raw-export/alpha1/render";

    private final EntityManager entityManager;
    private final File rawDir;
    private final List<CanonicalRenderAsset> renderAssets;

    AngelicaRenderFactsWriter(EntityManager entityManager, File rawDir, List<CanonicalRenderAsset> renderAssets) {
        this.entityManager = entityManager;
        this.rawDir = rawDir;
        this.renderAssets = renderAssets == null
                ? Collections.<CanonicalRenderAsset>emptyList()
                : renderAssets;
    }

    Counts write() throws IOException {
        ensureDirectory(new File(rawDir, "facts/render"));
        Counts counts = new Counts();
        counts.backend = new AngelicaRenderBackendFactsWriter(SCHEMA_ROOT)
                .write(new File(rawDir, "facts/render/backend.json"));
        counts.backendFacts = 1L;
        AngelicaTextureSpriteStreamCounts textureSpriteCounts = new AngelicaRenderTextureSpriteFactsWriter(SCHEMA_ROOT)
                .write(new File(rawDir, "facts/render/texture-sprites.jsonl.gz"));
        counts.textureSprites = textureSpriteCounts.textureSprites;
        counts.textureSpritesMissingTiming = textureSpriteCounts.missingTiming;
        AngelicaItemRendererStreamCounts itemRendererCounts = new AngelicaRenderItemRendererFactsWriter(entityManager, SCHEMA_ROOT)
                .write(new File(rawDir, "facts/render/item-renderers.jsonl.gz"),
                        new File(rawDir, "facts/render/shader-items.jsonl.gz"));
        counts.itemRenderers = itemRendererCounts.itemRenderers;
        counts.shaderItems = itemRendererCounts.shaderItems;
        counts.shaderItemsRequiringCapture = itemRendererCounts.shaderItemsRequiringCapture;
        counts.unknownSpecialRenderers = itemRendererCounts.unknownSpecialRenderers;
        AngelicaFramebufferCaptureStreamCounts captureCounts = new AngelicaFramebufferCaptureFactsWriter(SCHEMA_ROOT, renderAssets)
                .write(new File(rawDir, "facts/render/framebuffer-captures.jsonl.gz"));
        counts.framebufferCaptures = captureCounts.framebufferCaptures;
        counts.framebufferCapturesWithoutFrames = captureCounts.framebufferCapturesWithoutFrames;
        MissingCaptureResult missingCaptureResult = missingCaptureResult(itemRendererCounts.captureRequiredItemIds, captureCounts.captureAssetIds, captureCounts.captureVariantKeys);
        counts.shaderItemsMissingCapture = missingCaptureResult.count;
        counts.shaderItemsMissingCaptureSamples.addAll(missingCaptureResult.samples);
        counts.framebufferCapturesWithoutFramesSamples.addAll(captureCounts.framebufferCapturesWithoutFramesSamples);
        return counts;
    }


    private static MissingCaptureResult missingCaptureResult(Set<String> requiredItemIds, Set<String> captureAssetIds, Set<String> captureVariantKeys) {
        MissingCaptureResult result = new MissingCaptureResult();
        for (String itemId : requiredItemIds) {
            if (itemId == null) {
                continue;
            }
            String expectedAssetId = "nesqlpp:item/" + itemId;
            if (!captureAssetIds.contains(expectedAssetId) && !captureVariantKeys.contains(itemId)) {
                result.count++;
                if (result.samples.size() < 20) {
                    result.samples.add(itemId);
                }
            }
        }
        return result;
    }


    private static void ensureDirectory(File directory) throws IOException {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }


    static final class Counts {
        String backend;
        long backendFacts;
        long textureSprites;
        long textureSpritesMissingTiming;
        long itemRenderers;
        long shaderItems;
        long shaderItemsRequiringCapture;
        long shaderItemsMissingCapture;
        final List<String> shaderItemsMissingCaptureSamples = new ArrayList<String>();
        long unknownSpecialRenderers;
        long framebufferCaptures;
        long framebufferCapturesWithoutFrames;
        final List<String> framebufferCapturesWithoutFramesSamples = new ArrayList<String>();
    }


    private static final class MissingCaptureResult {
        long count;
        final List<String> samples = new ArrayList<String>();
    }

}

