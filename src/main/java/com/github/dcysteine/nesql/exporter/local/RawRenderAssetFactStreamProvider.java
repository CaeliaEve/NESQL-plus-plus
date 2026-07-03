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
    }
}
