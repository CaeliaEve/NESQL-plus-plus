package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;

import java.util.List;

/** Collects browser layout, atlas, multiblock, and entity index validation evidence. */
final class ExportValidationBrowserValidationProbe implements ExportValidationProbe {
    public String id() {
        return "validation.browser-layout";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList(
                "validation.browser.layout",
                "validation.browser.atlas",
                "validation.entity.index");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReport report) {
        report.staticAtlasPngFiles = ExportValidationJsonSupport.countFiles(
                RawExportFileCatalog.rawExportFile(
                        context.rawDir,
                        RawExportFileCatalog.BROWSER_ATLAS_ASSETS_DIRECTORY),
                ".png");
        report.animatedAtlasPngFiles = report.staticAtlasPngFiles;
        report.staticAtlasManifestAssets = ExportValidationJsonSupport.safeInt(report.rawTextures);
        report.animatedAtlasManifestAssets = ExportValidationJsonSupport.safeInt(report.rawAnimations);
        report.totalAtlasManifestAssets =
                report.staticAtlasManifestAssets + report.animatedAtlasManifestAssets;
        report.browserLayoutPresent =
                RawExportFileCatalog.rawExportFile(context.rawDir, RawExportFileCatalog.NEI_GROUPS_FILE).exists()
                        && RawExportFileCatalog.rawExportFile(context.rawDir, RawExportFileCatalog.NEI_ORDER_FILE)
                                .exists();
        report.browserLayoutEntries = ExportValidationJsonSupport.safeInt(report.rawBrowserItems);
        report.browserLayoutItemCount = ExportValidationJsonSupport.safeInt(report.rawBrowserItems);
        report.browserLayoutGroupCount = ExportValidationJsonSupport.safeInt(report.rawBrowserGroups);
        report.browserLayoutDefaultEntryCount = ExportValidationJsonSupport.safeInt(
                ExportValidationJsonSupport.countGzipJsonl(
                        RawExportFileCatalog.rawExportFile(context.rawDir, RawExportFileCatalog.NEI_ORDER_FILE)));
        ExportValidationBrowserAtlasProbe.inspect(context.rawDir, report);
        report.multiblockBlueprints = ExportValidationJsonSupport.safeInt(
                ExportValidationJsonSupport.countGzipJsonl(RawExportFileCatalog.rawExportFile(
                        context.rawDir,
                        RawExportFileCatalog.MULTIBLOCKS_INDEX_FILE)));
        report.entityPreviewEntries = ExportValidationJsonSupport.safeInt(
                ExportValidationJsonSupport.countGzipJsonl(RawExportFileCatalog.rawExportFile(
                        context.rawDir,
                        RawExportFileCatalog.ENTITIES_INDEX_FILE)));
        report.entityModelEntries = report.entityPreviewEntries;
    }
}
