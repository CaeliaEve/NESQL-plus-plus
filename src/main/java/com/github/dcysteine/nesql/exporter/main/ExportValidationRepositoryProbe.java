package com.github.dcysteine.nesql.exporter.main;

import java.io.File;
import java.util.List;

/** Collects repository-level validation metadata and legacy shard/file counts. */
final class ExportValidationRepositoryProbe implements ExportValidationProbe {
    public String id() {
        return "validation.repository-summary";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList("validation.repository.summary");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReport report) {
        report.schemaVersion = ExportValidationAbiCatalog.EXPORT_VALIDATION_SCHEMA;
        report.profile = context.exportContext.profile.profileId;
        report.selection = context.exportContext.selection.describe();
        report.repository = context.exportContext.paths.repositoryName;
        report.itemsJsonGzFiles = ExportValidationJsonSupport.countFiles(
                new File(context.repositoryDirectory, "items"), ".json.gz");
        report.recipeJsonGzFiles = ExportValidationJsonSupport.countFiles(
                new File(context.repositoryDirectory, "recipes"), ".json.gz");
        int[] imageFileCounts = ExportValidationJsonSupport.countFilesBySuffix(
                context.exportContext.paths.imageDirectory,
                ".png",
                ".gif",
                ".render.json",
                ".sprite.json");
        report.imagePngFiles = imageFileCounts[0];
        report.imageGifFiles = imageFileCounts[1];
        report.renderJsonFiles = imageFileCounts[2];
        report.spriteJsonFiles = imageFileCounts[3];
    }
}
