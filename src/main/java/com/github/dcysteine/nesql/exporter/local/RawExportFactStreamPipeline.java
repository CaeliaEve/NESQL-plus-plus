package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import jakarta.persistence.EntityManager;

import java.io.File;
import java.io.IOException;
import java.util.List;

final class RawExportFactStreamPipeline {
    private final RawExportFactStreamContext context;
    private final List<RawExportFactStreamProvider> providers;
    private final List<RawExportFactStreamDescriptor> descriptors;

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
        RawExportFactStreamCatalog catalog = RawExportFactStreamRegistry.defaultCatalog();
        this.providers = catalog.providers();
        this.descriptors = catalog.descriptors();
    }

    List<RawExportFactStreamDescriptor> descriptors() {
        return descriptors;
    }

    RawFactCounts write() throws IOException {
        RawFactCounts counts = new RawFactCounts();
        RawExportFactStreamDescriptorWriter.write(context.rawDir, descriptors);
        for (RawExportFactStreamProvider provider : providers) {
            provider.write(context, counts);
        }
        return counts;
    }
}
