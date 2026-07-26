package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportGeneration;

import java.io.File;
import java.io.IOException;

/**
 * Shared transitional export context for NESQL++ profiles.
 *
 * <p>This keeps profile, path, and stage-plan wiring together while the codebase
 * still uses multiple concrete exporter entrypoints.</p>
 */
public final class ExportContext {
    public final ExportProfile profile;
    public final ExportSelection selection;
    public final ExportPaths paths;
    public final ExportExecutionPlan executionPlan;
    public final java.util.Set<String> recipeExportModFilter;
    private RawExportGeneration rawExportGeneration;

    private ExportContext(
            ExportProfile profile,
            ExportSelection selection,
            String repositoryName,
            java.util.Set<String> recipeExportModFilter) {
        this.profile = profile;
        this.selection = selection == null ? ExportSelection.full() : selection;
        this.paths = ExportPaths.forRepository(repositoryName);
        this.executionPlan = ExportExecutionPlan.fromProfile(profile, this.selection);
        this.recipeExportModFilter = recipeExportModFilter == null
                ? java.util.Collections.emptySet()
                : java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(recipeExportModFilter));
    }

    private ExportContext(ExportProfile profile, String repositoryName, File repositoryDirectory) {
        this.profile = profile;
        this.selection = ExportSelection.full();
        this.paths = ExportPaths.forRepositoryDirectory(repositoryName, repositoryDirectory);
        this.executionPlan = ExportExecutionPlan.fromProfile(profile, this.selection);
        this.recipeExportModFilter = java.util.Collections.emptySet();
    }

    public static ExportContext forProfile(ExportProfile profile, String repositoryName) {
        return new ExportContext(profile, ExportSelection.full(), repositoryName, java.util.Collections.emptySet());
    }

    public static ExportContext forRepositoryDirectoryForTest(
            ExportProfile profile,
            String repositoryName,
            File repositoryDirectory) {
        return new ExportContext(profile, repositoryName, repositoryDirectory);
    }

    public static ExportContext forProfile(
            ExportProfile profile,
            ExportSelection selection,
            String repositoryName) {
        return new ExportContext(profile, selection, repositoryName, java.util.Collections.emptySet());
    }

    public static ExportContext forProfile(
            ExportProfile profile,
            String repositoryName,
            java.util.Set<String> recipeExportModFilter) {
        return new ExportContext(profile, ExportSelection.full(), repositoryName, recipeExportModFilter);
    }

    synchronized void beginRawExportGeneration() throws IOException {
        if (rawExportGeneration != null) {
            throw new IOException("Raw-export generation is already active");
        }
        rawExportGeneration = RawExportGeneration.begin(paths.repositoryDirectory);
    }

    synchronized void ensureRawExportGeneration() throws IOException {
        if (rawExportGeneration == null) {
            beginRawExportGeneration();
        }
    }

    public synchronized File rawExportDirectory() throws IOException {
        if (rawExportGeneration != null && !rawExportGeneration.isPublished()) {
            return rawExportGeneration.stagingDirectory();
        }
        return RawExportGeneration.requireCurrentDirectory(paths.repositoryDirectory);
    }

    synchronized boolean hasActiveRawExportGeneration() {
        return rawExportGeneration != null && !rawExportGeneration.isPublished();
    }

    public File authoritativeRawExportDirectory() throws IOException {
        return RawExportGeneration.currentDirectoryOrMissing(paths.repositoryDirectory);
    }

    synchronized void publishRawExportGeneration() throws IOException {
        if (rawExportGeneration == null) {
            throw new IOException("Raw-export generation was not started");
        }
        rawExportGeneration.publish();
    }

    synchronized void abortRawExportGeneration() throws IOException {
        if (rawExportGeneration == null) {
            return;
        }
        try {
            rawExportGeneration.abort();
        } finally {
            rawExportGeneration = null;
        }
    }
}
