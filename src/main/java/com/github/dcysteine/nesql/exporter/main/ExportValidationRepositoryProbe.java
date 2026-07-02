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
            ExportValidationReportWriter.ValidationReport report) {
        report.schemaVersion = ExportValidationAbiCatalog.EXPORT_VALIDATION_SCHEMA;
        report.profile = context.exportContext.profile.profileId;
        report.selection = context.exportContext.selection.describe();
        report.repository = context.exportContext.paths.repositoryName;
        report.itemsJsonGzFiles = ExportValidationJsonSupport.countFiles(
                new File(context.repositoryDirectory, "items"), ".json.gz");
        report.recipeJsonGzFiles = ExportValidationJsonSupport.countFiles(
                new File(context.repositoryDirectory, "recipes"), ".json.gz");
        report.imagePngFiles =
                ExportValidationJsonSupport.countFiles(context.exportContext.paths.imageDirectory, ".png");
        report.imageGifFiles =
                ExportValidationJsonSupport.countFiles(context.exportContext.paths.imageDirectory, ".gif");
        report.renderJsonFiles =
                ExportValidationJsonSupport.countFiles(context.exportContext.paths.imageDirectory, ".render.json");
        report.spriteJsonFiles =
                ExportValidationJsonSupport.countFiles(context.exportContext.paths.imageDirectory, ".sprite.json");
    }
}
