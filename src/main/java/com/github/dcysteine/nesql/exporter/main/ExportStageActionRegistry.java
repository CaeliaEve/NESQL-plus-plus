package com.github.dcysteine.nesql.exporter.main;

import net.minecraft.util.EnumChatFormatting;

import java.util.EnumMap;
import java.util.Map;

final class ExportStageActionRegistry {

    private ExportStageActionRegistry() {}

    static Map<ExportStage, ExportStageAction> build(
            ExportContext exportContext,
            ExportExecutionStrategy strategy,
            ExportStageState stageState) {
        EnumMap<ExportStage, ExportStageAction> actions = new EnumMap<>(ExportStage.class);
        actions.put(ExportStage.INITIALIZE_REPOSITORY,
                () -> prepareRepositoryOrStop(exportContext, strategy.requiresFreshRepository(exportContext)));
        actions.put(ExportStage.INITIALIZE_DATABASE,
                () -> stageState.runtime = strategy.createRuntime(exportContext));
        actions.put(ExportStage.INITIALIZE_PLUGINS, () -> {
            stageState.renderingImages =
                    strategy.initializeRendering(exportContext, exportContext.paths.imageDirectory);
            stageState.session = strategy.startSession(exportContext, stageState.runtime);
        });
        actions.put(ExportStage.COLLECT_PLUGIN_DATA,
                () -> strategy.runCollectionStage(exportContext, stageState.runtime));
        actions.put(ExportStage.WRITE_MOD_BASED_ITEMS, () -> {
            announceWritePreamble(exportContext, stageState);
            ExportWriterSupport.writeModBasedItems(stageState.runtime.entityManager, exportContext.paths.repositoryDirectory);
        });
        actions.put(ExportStage.WRITE_MOD_BASED_RECIPES, () -> {
            announceWritePreamble(exportContext, stageState);
            ExportWriterSupport.writeModBasedRecipes(
                    stageState.runtime.entityManager,
                    exportContext.paths.repositoryDirectory,
                    exportContext.recipeExportModFilter);
        });
        actions.put(ExportStage.WRITE_MULTIBLOCK_BLUEPRINTS, () ->
                ExportWriterSupport.writeGregTechMultiblocks(exportContext.paths.repositoryName));
        actions.put(ExportStage.WRITE_BLOCK_FACE_METADATA, () ->
                ExportWriterSupport.writeBlockFaceMetadata(exportContext.paths.repositoryName));
        actions.put(ExportStage.WRITE_CANONICAL_SNAPSHOT, () ->
                stageState.renderAssets = ExportWriterSupport.writeCanonicalSnapshot(
                        stageState.runtime.entityManager,
                        exportContext.paths.repositoryDirectory,
                        exportContext.profile.profileId,
                        exportContext.profile.renderImages,
                        exportContext.profile == ExportProfile.DATA_ONLY_V104));
        actions.put(ExportStage.COMMIT_DATABASE,
                () -> strategy.finishTransaction(exportContext, stageState.session.transaction));
        actions.put(ExportStage.ROLLBACK_DATABASE,
                () -> strategy.finishTransaction(exportContext, stageState.session.transaction));
        actions.put(ExportStage.RENDER_IMAGES, () -> {
            if (stageState.renderingImages) {
                RenderLifecycleSupport.awaitRenderCompletion();
                if (stageState.renderAssets == null || stageState.renderAssets.isEmpty()) {
                    stageState.renderAssets =
                            ExportWriterSupport.collectRenderAssets(exportContext.paths.repositoryDirectory);
                } else {
                    Logger.chatMessage(
                            EnumChatFormatting.GREEN
                                    + "Reusing "
                                    + stageState.renderAssets.size()
                                    + " precollected render assets.");
                }
            }
        });
        actions.put(ExportStage.WRITE_RENDER_ASSET_MANIFEST, () -> {
            if (stageState.renderingImages) {
                ExportWriterSupport.writeRenderAssetManifest(
                        stageState.runtime.entityManager,
                        exportContext.paths.repositoryDirectory,
                        stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_ANIMATION_MANIFEST, () -> {
            if (stageState.renderingImages) {
                ExportWriterSupport.writeAnimationManifest(
                        stageState.runtime.entityManager,
                        exportContext.paths.repositoryDirectory,
                        stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_ATLAS_PACKS, () -> {
            if (stageState.renderingImages) {
                ExportWriterSupport.writeAtlasPacks(
                        stageState.runtime.entityManager,
                        exportContext.paths.repositoryDirectory,
                        stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_ANIMATED_ATLAS_PACKS, () -> {
            if (stageState.renderingImages) {
                ExportWriterSupport.writeAnimatedAtlasPacks(
                        stageState.runtime.entityManager,
                        exportContext.paths.repositoryDirectory,
                        stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_ATLAS_REGISTRY, () -> {
            if (stageState.renderingImages) {
                ExportWriterSupport.writeAtlasRegistry(
                        stageState.runtime.entityManager,
                        exportContext.paths.repositoryDirectory,
                        stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_RENDER_INDEX, () -> {
            if (stageState.renderingImages) {
                ExportWriterSupport.writeRenderIndex(
                        stageState.runtime.entityManager,
                        exportContext.paths.repositoryDirectory,
                        stageState.renderAssets);
            }
        });
        actions.put(ExportStage.WRITE_BROWSER_LAYOUT_INDEX, () -> {
            ExportWriterSupport.writeBrowserLayoutIndex(
                    stageState.runtime.entityManager,
                    exportContext.paths.repositoryDirectory);
        });
        actions.put(ExportStage.WRITE_BROWSER_ATLAS_INDEX, () -> {
            if (stageState.renderingImages) {
                ExportWriterSupport.writeBrowserAtlasIndex(exportContext.paths.repositoryDirectory);
            }
        });
        actions.put(ExportStage.COMPLETE, () -> {});
        return actions;
    }

    private static void announceWritePreamble(ExportContext exportContext, ExportStageState stageState) {
        if (stageState.writePreambleAnnounced || exportContext.profile == ExportProfile.IMAGES_ONLY) {
            return;
        }

        Logger.chatMessage(EnumChatFormatting.AQUA + "Data exported!");
        Logger.chatMessage(EnumChatFormatting.AQUA + "Committing database...");
        Logger.chatMessage(
                EnumChatFormatting.YELLOW
                        + (exportContext.profile.commitDatabase
                                ? "This may take several minutes for large datasets..."
                                : "This may take several minutes..."));
        stageState.writePreambleAnnounced = true;
    }

    private static void prepareRepositoryOrStop(ExportContext exportContext, boolean failIfExists) {
        if (!prepareRepository(exportContext, failIfExists)) {
            throw new ExportStageRunner.RepositoryPreparationStoppedException();
        }
    }

    private static boolean prepareRepository(ExportContext exportContext, boolean failIfExists) {
        if (!failIfExists && !exportContext.paths.repositoryDirectory.exists()) {
            Logger.MOD.info("Repository does not exist, creating...");
        }
        if (!ExportLifecycleSupport.ensureRepositoryDirectory(
                exportContext.paths.repositoryDirectory,
                exportContext.paths.repositoryName,
                failIfExists)) {
            if (!failIfExists) {
                Logger.MOD.error("Failed to create repository directory!");
            }
            return false;
        }
        if (!failIfExists) {
            Logger.MOD.info("Repository directory exists");
        }
        return true;
    }
}
