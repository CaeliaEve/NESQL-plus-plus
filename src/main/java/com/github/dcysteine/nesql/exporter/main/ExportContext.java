package com.github.dcysteine.nesql.exporter.main;

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

    public static ExportContext forProfile(ExportProfile profile, String repositoryName) {
        return new ExportContext(profile, ExportSelection.full(), repositoryName, java.util.Collections.emptySet());
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
}
