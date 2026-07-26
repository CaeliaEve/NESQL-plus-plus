package com.github.dcysteine.nesql.exporter.local;

import java.io.IOException;

final class RawRenderAssetFactStreamProvider implements RawExportFactStreamProvider {
    @Override
    public void write(RawExportFactStreamContext context, RawFactCounts counts) throws IOException {
        RawRenderAssetCatalogCounts renderAssetCatalog =
                new RawExportRenderAssetCatalogWriter(
                        context.repositoryDirectory,
                        context.rawDir,
                        context.renderAssets).write();
        counts.textures = renderAssetCatalog.textures;
        counts.animations = renderAssetCatalog.animations;
        counts.browserAtlasAssets = renderAssetCatalog.browserAtlasAssets;
        counts.animationFrameMaterializations =
                renderAssetCatalog.animationFrameMaterializations;
        counts.materializedAnimations = renderAssetCatalog.materializedAnimations;
        counts.staticAnimationFrames = renderAssetCatalog.staticAnimationFrames;
        counts.unavailableAnimationFrames = renderAssetCatalog.unavailableAnimationFrames;
        RawFacadeResolutionCounts facadeCounts =
                new RawExportFacadeResolutionWriter(context.entityManager).write(
                        RawExportFileCatalog.rawExportFile(
                                context.rawDir,
                                RawExportFileCatalog.FACADE_RESOLUTIONS_FILE));
        counts.facadeResolutions = facadeCounts.total;
        counts.resolvedFacades = facadeCounts.resolved;
        counts.partialFacades = facadeCounts.partial;
        counts.unresolvedFacades = facadeCounts.unresolved;
    }
}
