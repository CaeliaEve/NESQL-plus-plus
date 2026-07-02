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
        report.semanticTopUnclassifiedFamilyActions = ExportValidationJsonSupport.copyArray(root, ExportValidationEvidenceCatalog.SemanticDiagnostics.TOP_UNCLASSIFIED_FAMILY_ACTIONS, 20);
        report.semanticMissingFacetFamilies = ExportValidationJsonSupport.copyArray(root, ExportValidationEvidenceCatalog.SemanticDiagnostics.MISSING_FACET_FAMILIES, 20);
        report.semanticMissingSortKeyFamilies = ExportValidationJsonSupport.copyArray(root, ExportValidationEvidenceCatalog.SemanticDiagnostics.MISSING_SORT_KEY_FAMILIES, 20);
        report.semanticMissingFacetFamilyCount = ExportValidationJsonSupport.arraySize(root, ExportValidationEvidenceCatalog.SemanticDiagnostics.MISSING_FACET_FAMILIES);
        report.semanticMissingSortKeyFamilyCount = ExportValidationJsonSupport.arraySize(root, ExportValidationEvidenceCatalog.SemanticDiagnostics.MISSING_SORT_KEY_FAMILIES);
        if (report.semanticTotalItems == 0L) {
            report.semanticTotalItems = ExportValidationJsonSupport.readLongMember(root, ExportValidationEvidenceCatalog.SemanticDiagnostics.BEFORE_PUBLIC_ITEMS);
        }
        if (report.semanticTaggedItems == 0L) {
            report.semanticTaggedItems = ExportValidationJsonSupport.readLongMember(root, ExportValidationEvidenceCatalog.SemanticDiagnostics.TAGGED_ITEMS);
        }
        if (report.semanticClassifiedTaggedItems == 0L) {
            report.semanticClassifiedTaggedItems = ExportValidationJsonSupport.readLongMember(root, ExportValidationEvidenceCatalog.SemanticDiagnostics.CLASSIFIED_TAGGED_ITEMS);
        }
        if (report.semanticUnclassifiedTaggedItems == 0L) {
            report.semanticUnclassifiedTaggedItems = ExportValidationJsonSupport.readLongMember(root, ExportValidationEvidenceCatalog.SemanticDiagnostics.UNCLASSIFIED_TAGGED_ITEMS);
        }
        report.semanticClassificationCoverageRatio = ExportValidationJsonSupport.ratio(
                report.semanticClassifiedTaggedItems,
                report.semanticTaggedItems);
    }

    static void inspectRulePack(ExportValidationReportWriter.ValidationReport report) {
        report.semanticRulePack = SemanticRulePack.loadBundled().validateAgainstRegistry();
    }
}
