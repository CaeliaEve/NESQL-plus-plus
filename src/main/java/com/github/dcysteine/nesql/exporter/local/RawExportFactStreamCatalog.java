package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RawExportFactStreamCatalog {
    private final List<RawExportFactStreamProvider> providers;
    private final List<RawExportFactStreamDescriptor> descriptors;

    RawExportFactStreamCatalog(
            List<RawExportFactStreamProvider> providers,
            List<RawExportFactStreamDescriptor> descriptors) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Raw export fact stream provider catalog must not be empty");
        }
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalArgumentException("Raw export fact stream descriptor catalog must not be empty");
        }
        if (providers.size() != descriptors.size()) {
            throw new IllegalArgumentException(
                    "Raw export fact stream provider and descriptor catalog sizes must match");
        }
        this.providers = Collections.unmodifiableList(new ArrayList<RawExportFactStreamProvider>(providers));
        this.descriptors =
                Collections.unmodifiableList(new ArrayList<RawExportFactStreamDescriptor>(descriptors));
    }

    List<RawExportFactStreamProvider> providers() {
        return providers;
    }

    List<RawExportFactStreamDescriptor> descriptors() {
        return descriptors;
    }
}
