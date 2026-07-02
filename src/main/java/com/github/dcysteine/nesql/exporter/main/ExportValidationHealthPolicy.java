package com.github.dcysteine.nesql.exporter.main;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Owns validation warning, blocked-state, and compile-readiness policy. */
final class ExportValidationHealthPolicy {
    private ExportValidationHealthPolicy() {}

    static void evaluate(ExportValidationReportWriter.ValidationReport report) {
        collectWarnings(report);
        determineHealthStatus(report);
    }

    private static void collectWarnings(ExportValidationReportWriter.ValidationReport report) {
        if (!report.renderAssetManifestPresent) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_RENDER_ASSET_MANIFEST_MISSING);
        }
        if (!report.browserLayoutPresent) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_BROWSER_LAYOUT_MISSING);
        }
        if (report.itemsJsonGzFiles == 0) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_ITEM_SHARDS_MISSING);
        }
        if (report.recipeJsonGzFiles == 0) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_RECIPE_SHARDS_MISSING);
        }
        if (report.imagePngFiles == 0 && report.imageGifFiles == 0) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_RENDER_IMAGES_MISSING);
        }
        if (report.staticAtlasPngFiles == 0 && report.staticAtlasManifestAssets > 0) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_STATIC_ATLAS_FILES_MISSING);
        }
        if (report.animatedAtlasPngFiles == 0 && report.animatedAtlasManifestAssets > 0) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_ANIMATED_ATLAS_FILES_MISSING);
        }
        if (report.renderAssetMissingPrimaryArtifacts > 0) {
            report.warnings.add(ExportValidationAbiCatalog.renderAssetMissingPrimaryArtifactsWarning(
                    report.renderAssetMissingPrimaryArtifacts));
        }
        if (report.renderAssetMissingTimelineFrames > 0) {
            report.warnings.add(ExportValidationAbiCatalog.renderAssetMissingTimelineFramesWarning(
                    report.renderAssetMissingTimelineFrames));
        }
        if (report.suspiciousStaticSingularityAssets > 0) {
            report.warnings.add(ExportValidationAbiCatalog.suspiciousStaticSingularityAssetsWarning(
                    report.suspiciousStaticSingularityAssets));
        }
        if (report.renderAssetManifestAssets > 0 && report.totalAtlasManifestAssets == 0) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_ATLAS_MANIFEST_ASSETS_MISSING);
        }
        if (report.browserAtlasPresent && report.browserLayoutPresent && report.browserAtlasLayoutMissingItems > 0) {
            report.warnings.add(ExportValidationAbiCatalog.browserLayoutMissingAtlasCoverageWarning(
                    report.browserAtlasLayoutMissingItems));
        }
        if (report.exportPathHygieneViolations > 0) {
            report.warnings.add(ExportValidationAbiCatalog.exportPathHygieneWarning(
                    report.exportPathHygieneViolations));
        }
        if (!report.semanticDiagnosticsPresent && report.rawItems > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_SEMANTIC_DIAGNOSTICS_MISSING);
        }
        if (report.semanticTaggedItems > 0L
                && report.semanticClassificationCoverageRatio != null
                && report.semanticClassificationCoverageRatio
                        < ExportValidationAbiCatalog.SEMANTIC_CLASSIFICATION_MIN_COVERAGE_RATIO) {
            report.warnings.add(ExportValidationAbiCatalog.semanticClassificationCoverageWarning(
                    report.semanticClassificationCoverageRatio));
        }
        if (report.semanticMissingFacetFamilyCount > 0) {
            report.warnings.add(ExportValidationAbiCatalog.semanticMissingFacetFamilyWarning(
                    report.semanticMissingFacetFamilyCount));
        }
        if (report.semanticMissingSortKeyFamilyCount > 0) {
            report.warnings.add(ExportValidationAbiCatalog.semanticMissingSortKeyFamilyWarning(
                    report.semanticMissingSortKeyFamilyCount));
        }
        if (report.semanticRulePack == null
                || !ExportValidationAbiCatalog.STATUS_OK.equals(report.semanticRulePack.status)) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_SEMANTIC_RULE_PACK_MISMATCH);
        }
        if (report.renderBackendFacts > 0L && report.renderBackendAngelica == 0L) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_ANGELICA_BACKEND_MISSING);
        }
        if (report.renderTextureSpritesMissingTiming > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.renderTextureSpritesMissingTimingWarning(
                    report.renderTextureSpritesMissingTiming));
        }
        if (report.renderShaderItemsMissingCapture > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.renderShaderItemsMissingCaptureWarning(
                    report.renderShaderItemsMissingCapture));
        }
        if (report.renderUnknownSpecialRenderers > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.renderUnknownSpecialRenderersWarning(
                    report.renderUnknownSpecialRenderers));
        }
        if (report.renderFramebufferCapturesWithoutFrames > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.renderFramebufferCapturesWithoutFramesWarning(
                    report.renderFramebufferCapturesWithoutFrames));
        }
        if (report.rawRecipes > 0L && (report.rawNeiHandlers == 0L || report.rawNeiHandlerLayouts == 0L)) {
            report.warnings.add(ExportValidationAbiCatalog.WARNING_NEI_HANDLER_METADATA_MISSING);
        }
        if (report.nativeUiMissingSurfaces > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.nativeUiMissingSurfacesWarning(
                    report.nativeUiMissingSurfaces));
        }
        if (report.nativeUiSlotBoundsViolations > 0L
                || report.nativeUiRectBoundsViolations > 0L
                || report.nativeUiPrimitiveBoundsViolations > 0L
                || report.nativeUiBackgroundBoundsViolations > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.nativeUiGeometryBoundsWarning(
                    report.nativeUiSlotBoundsViolations,
                    report.nativeUiRectBoundsViolations,
                    report.nativeUiPrimitiveBoundsViolations,
                    report.nativeUiBackgroundBoundsViolations));
        }
        if (report.nativeUiCoordinateContractViolations > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.nativeUiCoordinateContractWarning(
                    report.nativeUiCoordinateContractViolations));
        }
        if (report.nativeUiInteractionContractViolations > 0L) {
            report.warnings.add(ExportValidationAbiCatalog.nativeUiInteractionContractWarning(
                    report.nativeUiInteractionContractViolations));
        }
    }

    private static void determineHealthStatus(ExportValidationReportWriter.ValidationReport report) {
        addBlockedIf(
                report,
                report.itemsJsonGzFiles == 0 && report.rawItems == 0L,
                ExportValidationAbiCatalog.BLOCKED_NO_ITEM_FACTS);
        addBlockedIf(
                report,
                report.recipeJsonGzFiles == 0 && report.rawRecipes == 0L,
                ExportValidationAbiCatalog.BLOCKED_NO_RECIPE_FACTS);
        addBlockedIf(
                report,
                report.exportPathHygieneViolations > 0,
                ExportValidationAbiCatalog.BLOCKED_MACHINE_PATHS);
        addBlockedIf(report,
                report.rawItems > 0L
                        && report.semanticTotalItems > 0L
                        && report.semanticIdentityMapRows > 0L
                        && report.semanticIdentityMapRows != report.rawItems,
                ExportValidationAbiCatalog.BLOCKED_SEMANTIC_IDENTITY_MAP_MISMATCH);
        addBlockedIf(report,
                report.rawItems > 0L && report.semanticDiagnosticsPresent && report.semanticTotalItems != report.rawItems,
                ExportValidationAbiCatalog.BLOCKED_SEMANTIC_DIAGNOSTIC_ITEM_MISMATCH);
        addBlockedIf(report,
                report.nativeUiLayouts > 0L
                        && (report.nativeUiSlots == 0L
                                || report.nativeUiMissingSurfaces > 0L
                                || report.nativeUiSlotBoundsViolations > 0L
                                || report.nativeUiRectBoundsViolations > 0L
                                || report.nativeUiPrimitiveBoundsViolations > 0L
                                || report.nativeUiBackgroundBoundsViolations > 0L
                                || report.nativeUiCoordinateContractViolations > 0L
                                || report.nativeUiInteractionContractViolations > 0L),
                ExportValidationAbiCatalog.BLOCKED_NATIVE_UI_ABI);
        addActionableIssue(report,
                ExportValidationAbiCatalog.ACTION_SEMANTIC_UNCLASSIFIED_FAMILIES_CODE,
                report.semanticTopUnclassifiedFamilyActions,
                ExportValidationAbiCatalog.ACTION_SEMANTIC_UNCLASSIFIED_FAMILIES_MESSAGE);
        addActionableIssue(report,
                ExportValidationAbiCatalog.ACTION_SEMANTIC_MISSING_FACETS_CODE,
                report.semanticMissingFacetFamilies,
                ExportValidationAbiCatalog.ACTION_SEMANTIC_MISSING_FACETS_MESSAGE);
        addActionableIssue(report,
                ExportValidationAbiCatalog.ACTION_SEMANTIC_MISSING_SORT_KEYS_CODE,
                report.semanticMissingSortKeyFamilies,
                ExportValidationAbiCatalog.ACTION_SEMANTIC_MISSING_SORT_KEYS_MESSAGE);
        if (!report.blockedIssues.isEmpty()) {
            report.healthStatus = ExportValidationAbiCatalog.STATUS_BLOCKED;
            report.compileReadinessStatus = ExportValidationAbiCatalog.STATUS_BLOCKED;
        } else if (!report.warnings.isEmpty() || !report.actionableIssues.isEmpty()) {
            report.healthStatus = ExportValidationAbiCatalog.STATUS_WARNING;
            report.compileReadinessStatus = ExportValidationAbiCatalog.COMPILE_READINESS_READY_WITH_WARNINGS;
        } else {
            report.healthStatus = ExportValidationAbiCatalog.HEALTH_STATUS_HEALTHY;
            report.compileReadinessStatus = ExportValidationAbiCatalog.COMPILE_READINESS_READY;
        }
    }

    private static void addBlockedIf(
            ExportValidationReportWriter.ValidationReport report,
            boolean condition,
            String message) {
        if (condition) {
            report.blockedIssues.add(message);
        }
    }

    private static void addActionableIssue(
            ExportValidationReportWriter.ValidationReport report,
            String code,
            JsonArray details,
            String message) {
        if (details == null || details.size() == 0) {
            return;
        }
        JsonObject issue = new JsonObject();
        issue.addProperty("code", code);
        issue.addProperty("message", message);
        issue.add("details", details);
        report.actionableIssues.add(issue);
    }
}
