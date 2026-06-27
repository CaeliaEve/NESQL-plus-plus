package com.github.dcysteine.nesql.exporter.local;

import java.io.IOException;
import java.util.List;

final class RawRenderAssetFactStreamProvider implements RawExportFactStreamProvider {
    @Override
    public String id() {
        return "raw.render-asset-facts";
    }

    @Override
    public List<String> capabilities() {
        return RawExportFactStreamProvider.list(
                "raw.render.textures",
                "raw.render.animations",
                "raw.render.browser-atlas-assets");
    }

    @Override
    public List<String> outputFamilies() {
        return RawExportFactStreamProvider.list(
                "facts/render-assets",
                "assets/browser-atlas");
    }

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
