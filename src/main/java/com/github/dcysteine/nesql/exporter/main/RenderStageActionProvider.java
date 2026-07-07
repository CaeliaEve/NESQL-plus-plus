package com.github.dcysteine.nesql.exporter.main;

import java.util.EnumMap;
import java.util.List;

final class RenderStageActionProvider implements ExportStageActionProvider {
    private static final List<ExportStage> STAGES = ExportStageActionProvider.stageList(
            ExportStage.RENDER_IMAGES,
            ExportStage.WRITE_RENDER_ASSET_MANIFEST,
            ExportStage.WRITE_ANIMATION_MANIFEST,
            ExportStage.WRITE_ATLAS_PACKS,
            ExportStage.WRITE_ANIMATED_ATLAS_PACKS,
            ExportStage.WRITE_ATLAS_REGISTRY,
            ExportStage.WRITE_RENDER_INDEX,
            ExportStage.WRITE_BROWSER_ATLAS_INDEX);
    private static final List<String> CAPABILITIES = ExportStageActionProvider.capabilityList(
            "export.render.capture",
            "export.render.asset-manifest",
            "export.render.animation-manifest",
            "export.render.atlas-packs",
            "export.render.browser-atlas-index");

    @Override
    public String id() {
        return "nesqlpp.export.render-assets";
    }

    @Override
    public List<ExportStage> stages() {
        return STAGES;
    }

    @Override
    public List<String> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public void register(EnumMap<ExportStage, ExportStageAction> actions, ExportStageActionContext context) {
        actions.put(ExportStage.RENDER_IMAGES, () -> {
            if (context.stageState.renderingImages) {
                com.github.dcysteine.nesql.exporter.nativeui.NativeNeiFrameExportRegistry.awaitPendingFrames();
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
