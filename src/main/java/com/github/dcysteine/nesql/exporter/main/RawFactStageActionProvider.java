package com.github.dcysteine.nesql.exporter.main;

import java.util.EnumMap;

final class RawFactStageActionProvider implements ExportStageActionProvider {
    @Override
    public String id() {
        return "nesqlpp.export.raw-facts";
    }

    @Override
    public void register(EnumMap<ExportStage, ExportStageAction> actions, ExportStageActionContext context) {
        actions.put(ExportStage.WRITE_UI_FAMILY_CENSUS, () ->
                ExportWriterSupport.writeUiFamilyCensus(context.exportContext.paths.repositoryDirectory));
        actions.put(ExportStage.WRITE_UI_TEMPLATE_CATALOG, () ->
                ExportWriterSupport.writeUiTemplateCatalog(context.exportContext.paths.repositoryDirectory));
        actions.put(ExportStage.WRITE_MOD_BASED_ITEMS, () -> {
            context.announceWritePreamble();
            ExportWriterSupport.writeModBasedItems(
                    context.stageState.runtime.entityManager,
                    context.exportContext.paths.repositoryDirectory);
        });
        actions.put(ExportStage.WRITE_MOD_BASED_RECIPES, () -> {
            context.announceWritePreamble();
            ExportWriterSupport.writeModBasedRecipes(
                    context.stageState.runtime.entityManager,
                    context.exportContext.paths.repositoryDirectory,
                    context.exportContext.recipeExportModFilter);
        });
        actions.put(ExportStage.WRITE_MULTIBLOCK_BLUEPRINTS, () ->
                ExportWriterSupport.writeGregTechMultiblocks(context.exportContext.paths.repositoryName));
        actions.put(ExportStage.WRITE_BLOCK_FACE_METADATA, () ->
                ExportWriterSupport.writeBlockFaceMetadata(context.exportContext.paths.repositoryName));
        actions.put(ExportStage.WRITE_CANONICAL_SNAPSHOT, () ->
                context.stageState.renderAssets = ExportWriterSupport.writeCanonicalSnapshot(
                        context.stageState.runtime.entityManager,
                        context.exportContext.paths.repositoryDirectory,
                        context.exportContext.profile.profileId,
                        context.exportContext.profile.renderImages,
                        context.exportContext.profile == ExportProfile.DATA_ONLY_V104));
    }
}
