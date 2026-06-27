package com.github.dcysteine.nesql.exporter.local;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

final class RawEntityAndRenderBackendFactStreamProvider implements RawExportFactStreamProvider {
    @Override
    public String id() {
        return "raw.entity-render-backend-facts";
    }

    @Override
    public void write(RawExportFactStreamContext context, RawFactCounts counts) throws IOException {
        RawExportEmptyJsonlWriter.write(new File(context.rawDir, "models/multiblocks/index.jsonl.gz"));
        counts.entities =
                new RawExportEntityModelWriter(
                        context.repositoryDirectory,
                        context.rawDir,
                        context.schemaVersion).write();
        AngelicaRenderFactsWriter.Counts renderCounts =
                new AngelicaRenderFactsWriter(context.entityManager, context.rawDir, context.renderAssets).write();
        counts.renderBackendFacts = renderCounts.backendFacts;
        counts.renderBackendAngelica = "angelica".equals(renderCounts.backend) ? 1L : 0L;
        counts.renderTextureSprites = renderCounts.textureSprites;
        counts.renderTextureSpritesMissingTiming = renderCounts.textureSpritesMissingTiming;
        counts.renderItemRenderers = renderCounts.itemRenderers;
        counts.renderShaderItems = renderCounts.shaderItems;
        counts.renderShaderItemsRequiringCapture = renderCounts.shaderItemsRequiringCapture;
        counts.renderShaderItemsMissingCapture = renderCounts.shaderItemsMissingCapture;
        counts.renderShaderItemsMissingCaptureSamples = new ArrayList<String>(renderCounts.shaderItemsMissingCaptureSamples);
        counts.renderUnknownSpecialRenderers = renderCounts.unknownSpecialRenderers;
        counts.renderFramebufferCaptures = renderCounts.framebufferCaptures;
        counts.renderFramebufferCapturesWithoutFrames = renderCounts.framebufferCapturesWithoutFrames;
        counts.renderFramebufferCapturesWithoutFramesSamples =
                new ArrayList<String>(renderCounts.framebufferCapturesWithoutFramesSamples);
    }
}
