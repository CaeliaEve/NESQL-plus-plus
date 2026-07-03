package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import jakarta.persistence.EntityManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RawExportFactStreamContext {
    final EntityManager entityManager;
    final File repositoryDirectory;
    final File rawDir;
    final List<CanonicalRenderAsset> renderAssets;
    final String schemaVersion;

    RawExportFactStreamContext(
            EntityManager entityManager,
            File repositoryDirectory,
            File rawDir,
            List<CanonicalRenderAsset> renderAssets,
            String schemaVersion) {
        this.entityManager = requireNonNull("Raw export fact stream entity manager", entityManager);
        this.repositoryDirectory = requireNonNull("Raw export fact stream repository directory", repositoryDirectory);
        this.rawDir = requireNonNull("Raw export fact stream raw-export directory", rawDir);
        this.renderAssets = Collections.unmodifiableList(new ArrayList<CanonicalRenderAsset>(
                requireNonNull("Raw export fact stream render assets", renderAssets)));
        this.schemaVersion = requireNonNull("Raw export fact stream schema version", schemaVersion);
    }

    private static <T> T requireNonNull(String label, T value) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        return value;
    }
}
