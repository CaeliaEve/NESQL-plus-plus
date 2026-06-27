package com.github.dcysteine.nesql.exporter.main;

import java.util.EnumMap;

final class RenderStageActionProvider implements ExportStageActionProvider {
    @Override
    public String id() {
        return "nesqlpp.export.render-assets";
    }

    @Override
    public void register(EnumMap<ExportStage, ExportStageAction> actions, ExportStageActionContext context) {
        actions.put(ExportStage.RENDER_IMAGES, () -> {
            if (context.stageState.renderingImages) {
                RenderLifecycleSupport.awaitRenderCompletion();
                context.stageState.renderAssets =
                        ExportWriterSupport.collectRenderAssets(context.exportContext.paths.repositoryDirectory);
            }
        });
        actions.put(ExportStage.WRITE_RENDER_ASSET_MANIFEST, () -> {
            if (context.stageState.renderingImages) {
                ExportWriterSupport.writeRenderAssetManifest(
                        context.stageState.runtime.entityManager,
                        context.exportContext.paths.repositoryDirectory,
                        context.stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_ANIMATION_MANIFEST, () -> {
            if (context.stageState.renderingImages) {
                ExportWriterSupport.writeAnimationManifest(
                        context.stageState.runtime.entityManager,
                        context.exportContext.paths.repositoryDirectory,
                        context.stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_ATLAS_PACKS, () -> {
            if (context.stageState.renderingImages) {
                ExportWriterSupport.writeAtlasPacks(
                        context.stageState.runtime.entityManager,
                        context.exportContext.paths.repositoryDirectory,
                        context.stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_ANIMATED_ATLAS_PACKS, () -> {
            if (context.stageState.renderingImages) {
                ExportWriterSupport.writeAnimatedAtlasPacks(
                        context.stageState.runtime.entityManager,
                        context.exportContext.paths.repositoryDirectory,
                        context.stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_ATLAS_REGISTRY, () -> {
            if (context.stageState.renderingImages) {
                ExportWriterSupport.writeAtlasRegistry(
                        context.stageState.runtime.entityManager,
                        context.exportContext.paths.repositoryDirectory,
                        context.stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_RENDER_INDEX, () -> {
            if (context.stageState.renderingImages) {
                ExportWriterSupport.writeRenderIndex(
                        context.stageState.runtime.entityManager,
                        context.exportContext.paths.repositoryDirectory,
                        context.stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_BROWSER_ATLAS_INDEX, () -> {
            if (context.stageState.renderingImages) {
                ExportWriterSupport.writeBrowserAtlasIndex(context.exportContext.paths.repositoryDirectory);
            }
        });
    }
}
