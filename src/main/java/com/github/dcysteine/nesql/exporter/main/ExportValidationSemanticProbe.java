package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;
import com.google.gson.JsonObject;

import java.io.File;

/** Owns semantic diagnostics and bundled rule-pack evidence collection. */
final class ExportValidationSemanticProbe {
    private ExportValidationSemanticProbe() {}

    static void inspectDiagnostics(File repositoryDirectory, ExportValidationReportWriter.ValidationReport report) {
        File semanticReportFile = RawExportFileCatalog.rawExportFile(
                RawExportFileCatalog.rawExportDirectory(repositoryDirectory),
                RawExportFileCatalog.SEMANTIC_IDENTITY_NORMALIZATION_REPORT_FILE);
        JsonObject root = ExportValidationJsonSupport.readJsonObject(semanticReportFile);
        if (root == null) {
            return;
        }
        report.semanticDiagnosticsPresent = true;
        report.semanticTopUnclassifiedFamilyActions = ExportValidationJsonSupport.copyArray(root, "topUnclassifiedFamilyActions", 20);
        report.semanticMissingFacetFamilies = ExportValidationJsonSupport.copyArray(root, "missingFacetFamilies", 20);
        report.semanticMissingSortKeyFamilies = ExportValidationJsonSupport.copyArray(root, "missingSortKeyFamilies", 20);
        report.semanticMissingFacetFamilyCount = ExportValidationJsonSupport.arraySize(root, "missingFacetFamilies");
        report.semanticMissingSortKeyFamilyCount = ExportValidationJsonSupport.arraySize(root, "missingSortKeyFamilies");
        if (report.semanticTotalItems == 0L) {
            report.semanticTotalItems = ExportValidationJsonSupport.readLongMember(root, "beforePublicItems");
        }
        if (report.semanticTaggedItems == 0L) {
            report.semanticTaggedItems = ExportValidationJsonSupport.readLongMember(root, "taggedItems");
        }
        if (report.semanticClassifiedTaggedItems == 0L) {
            report.semanticClassifiedTaggedItems = ExportValidationJsonSupport.readLongMember(root, "classifiedTaggedItems");
        }
        if (report.semanticUnclassifiedTaggedItems == 0L) {
            report.semanticUnclassifiedTaggedItems = ExportValidationJsonSupport.readLongMember(root, "unclassifiedTaggedItems");
        }
        report.semanticClassificationCoverageRatio = ExportValidationJsonSupport.ratio(
                report.semanticClassifiedTaggedItems,
                report.semanticTaggedItems);
    }

    static void inspectRulePack(ExportValidationReportWriter.ValidationReport report) {
        report.semanticRulePack = SemanticRulePack.loadBundled().validateAgainstRegistry();
    }
}
