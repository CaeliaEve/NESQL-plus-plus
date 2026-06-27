package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import jakarta.persistence.EntityManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

final class RawExportFactStreamPipeline {
    private final RawExportFactStreamContext context;
    private final List<RawExportFactStreamProvider> providers;

    RawExportFactStreamPipeline(
            EntityManager entityManager,
            File repositoryDirectory,
            File rawDir,
            List<CanonicalRenderAsset> renderAssets,
            String schemaVersion) {
        this.context = new RawExportFactStreamContext(
                entityManager,
                repositoryDirectory,
                rawDir,
                renderAssets,
                schemaVersion);
        this.providers = RawExportFactStreamRegistry.defaultProviders();
    }

    RawFactCounts write() throws IOException {
        RawFactCounts counts = new RawFactCounts();
        for (RawExportFactStreamProvider provider : providers) {
            provider.write(context, counts);
        }
        return counts;
    }
}
