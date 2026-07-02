package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.List;

final class RawExportValidationSupport {
    private RawExportValidationSupport() {}

    static void apply(RawExportReport report) {
        if (report == null || report.counts == null || report.validation == null) {
            return;
        }
        RawExportCounts counts = report.counts;
        List<String> issues = new ArrayList<String>();
        RawExportValidationAbiCatalog.addCountMismatch(
                issues, RawExportValidationAbiCatalog.COUNT_ITEMS, counts.items, counts.rawItems);
        RawExportValidationAbiCatalog.addCountMismatch(
                issues, RawExportValidationAbiCatalog.COUNT_FLUIDS, counts.fluids, counts.rawFluids);
        RawExportValidationAbiCatalog.addCountMismatch(
                issues, RawExportValidationAbiCatalog.COUNT_RECIPES, counts.recipes, counts.rawRecipes);
        if (counts.renderAssets > 0) {
            RawExportValidationAbiCatalog.addCountMismatch(
                    issues,
                    RawExportValidationAbiCatalog.COUNT_RENDER_ASSETS_TEXTURES,
                    counts.renderAssets,
                    counts.rawTextures);
        }
        report.validation.missingTextureCount = counts.rawItems > 0 && counts.rawTextures == 0 ? counts.rawItems : 0;
        report.validation.missingAnimationMetadataCount = counts.rawAnimations > 0 ? 0 : report.validation.missingAnimationMetadataCount;
        report.validation.missingGroupOrOrderCount = (counts.rawGroups == 0 || counts.rawNeiOrderEntries == 0) ? 1 : 0;
        RawExportValidationAbiCatalog.addRenderSampleIssues(
                issues,
                RawExportValidationAbiCatalog.ISSUE_MISSING_RENDER_CAPTURE,
                counts.renderShaderItemsMissingCaptureSamples);
        RawExportValidationAbiCatalog.addRenderSampleIssues(
                issues,
                RawExportValidationAbiCatalog.ISSUE_RENDER_CAPTURE_WITHOUT_FRAMES,
                counts.renderFramebufferCapturesWithoutFramesSamples);
        RawExportValidationAbiCatalog.addNativeUiIssues(issues, counts);
        report.validation.failedStages = issues;
        report.validation.status = RawExportValidationAbiCatalog.validationStatus(issues);
        report.validation.gates = RawExportValidationAbiCatalog.buildGates(counts, issues);
        report.validation.readinessStatus = rawValidationReady(report.validation)
                ? RawExportValidationAbiCatalog.STATUS_READY
                : RawExportValidationAbiCatalog.STATUS_BLOCKED;
    }

    private static boolean rawValidationReady(RawExportValidation validation) {
        return RawExportValidationAbiCatalog.allGatesReady(validation);
    }
}
