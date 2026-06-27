package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class RawExportFactStreamRegistry {
    private RawExportFactStreamRegistry() {}

    static List<RawExportFactStreamProvider> defaultProviders() {
        return validateAndFreeze(providerList(
                new RawRepositoryFactStreamProvider(),
                new RawNeiFactStreamProvider(),
                new RawRenderAssetFactStreamProvider(),
                new RawEntityAndRenderBackendFactStreamProvider()));
    }

    private static List<RawExportFactStreamProvider> providerList(RawExportFactStreamProvider... providers) {
        List<RawExportFactStreamProvider> list = new ArrayList<RawExportFactStreamProvider>();
        Collections.addAll(list, providers);
        return list;
    }

    private static List<RawExportFactStreamProvider> validateAndFreeze(List<RawExportFactStreamProvider> providers) {
        Set<String> ids = new LinkedHashSet<String>();
        for (RawExportFactStreamProvider provider : providers) {
            if (provider == null) {
                throw new IllegalArgumentException("Raw export fact stream provider must be non-null");
            }
            String id = provider.id();
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Raw export fact stream provider id must be non-empty");
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate raw export fact stream provider id: " + id);
            }
        }
        return Collections.unmodifiableList(new ArrayList<RawExportFactStreamProvider>(providers));
    }
}
