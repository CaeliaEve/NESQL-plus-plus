package com.github.dcysteine.nesql.exporter.local;

import java.io.IOException;
import java.util.List;

final class RawRepositoryFactStreamProvider implements RawExportFactStreamProvider {
    @Override
    public String id() {
        return "raw.repository-facts";
    }

    @Override
    public List<String> capabilities() {
        return RawExportFactStreamProvider.list(
                "raw.items",
                "raw.fluids",
                "raw.recipes");
    }

    @Override
    public List<String> outputFamilies() {
        return RawExportFactStreamProvider.list(
                "facts/items",
                "facts/fluids",
                "facts/recipes");
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
