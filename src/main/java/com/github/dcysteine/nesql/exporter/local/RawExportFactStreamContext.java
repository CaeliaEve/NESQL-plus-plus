package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import jakarta.persistence.EntityManager;

import java.io.File;
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
        this.entityManager = entityManager;
        this.repositoryDirectory = repositoryDirectory;
        this.rawDir = rawDir;
        this.renderAssets = renderAssets;
        this.schemaVersion = schemaVersion;
    }
}
