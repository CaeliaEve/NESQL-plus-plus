package com.github.dcysteine.nesql.exporter.local;

import java.io.IOException;

final class RawRepositoryFactStreamProvider implements RawExportFactStreamProvider {
    @Override
    public String id() {
        return "raw.repository-facts";
    }

    @Override
    public void write(RawExportFactStreamContext context, RawFactCounts counts) throws IOException {
        RawRepositoryFactStreamResult repository =
                new RawExportRepositoryFactStreamer(context.entityManager, context.rawDir, context.schemaVersion).write();
        counts.items = repository.items;
        counts.fluids = repository.fluids;
        counts.recipes = repository.recipes;
    }
}
