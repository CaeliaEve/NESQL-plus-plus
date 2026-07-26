package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.elysium.kernel.ExportControlFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportDebugFile;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.ExportStage;

import java.util.Map;

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
        putManifestFiles(manifest.files, includeUiFamilyCensus, includeUiTemplateCatalog);
        manifest.counts = report.counts;
        return manifest;
    }

    static void putManifestFiles(
            Map<String, String> files,
            boolean includeUiFamilyCensus,
            boolean includeUiTemplateCatalog) {
        if (files == null) {
            throw new IllegalArgumentException("Raw-export manifest file map must be non-null");
        }
        RawExportFileCatalog.putManifestFiles(
                files,
                includeUiFamilyCensus,
                includeUiTemplateCatalog);
        for (ExportControlFile file : ExportControlFile.values()) {
            putManifestFile(files, file.manifestKey(), file.rawExportPath());
        }
        for (ExportDebugFile file : ExportDebugFile.values()) {
            putManifestFile(files, file.manifestKey(), file.rawExportDebugPath());
        }
    }

    private static void putManifestFile(Map<String, String> files, String key, String path) {
        if (files.containsKey(key)) {
            throw new IllegalStateException("Duplicate raw-export manifest file key: " + key);
        }
        if (files.containsValue(path)) {
            throw new IllegalStateException("Duplicate raw-export manifest file path: " + path);
        }
        files.put(key, path);
    }

}
