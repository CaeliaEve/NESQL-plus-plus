package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.elysium.kernel.ExportControlFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportDebugFile;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.ExportStage;

final class RawExportManifestBuilder {
    private RawExportManifestBuilder() {}

    static RawExportManifest build(String schemaVersion, String generatedAt, ExportContext exportContext, RawExportReport report) {
        boolean includeUiFamilyCensus =
                exportContext.selection.includesStage(ExportStage.WRITE_UI_FAMILY_CENSUS, exportContext.profile);
        boolean includeUiTemplateCatalog =
                exportContext.selection.includesStage(ExportStage.WRITE_UI_TEMPLATE_CATALOG, exportContext.profile);
        RawExportManifest manifest = new RawExportManifest();
        manifest.schemaVersion = schemaVersion;
        manifest.generatedAt = generatedAt;
        manifest.repositoryName = exportContext.paths.repositoryName;
        manifest.profile = exportContext.profile.profileId;
        manifest.selection = exportContext.selection.describe();
        manifest.semanticRuleRuntime = report.semanticRuleRuntime;
        manifest.status = RawExportFileCatalog.RAW_EXPORT_STATUS;
        manifest.notes.addAll(RawExportFileCatalog.manifestNotes());
        manifest.capabilities.addAll(
                RawExportFileCatalog.manifestCapabilities(includeUiFamilyCensus, includeUiTemplateCatalog));
        RawExportFileCatalog.putManifestFiles(
                manifest.files,
                includeUiFamilyCensus,
                includeUiTemplateCatalog);
        for (ExportControlFile file : ExportControlFile.values()) {
            manifest.files.put(file.manifestKey(), file.rawExportPath());
        }
        for (ExportDebugFile file : ExportDebugFile.values()) {
            manifest.files.put(file.manifestKey(), file.rawExportDebugPath());
        }
        manifest.counts = report.counts;
        return manifest;
    }

}
