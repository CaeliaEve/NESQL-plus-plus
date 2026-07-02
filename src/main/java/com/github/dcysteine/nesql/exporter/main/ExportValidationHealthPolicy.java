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
            report.warnings.add("Missing raw-export/assets/textures/index.jsonl.gz.");
        }
        if (!report.browserLayoutPresent) {
            report.warnings.add("Missing raw-export NEI browser group/order streams.");
        }
        if (report.itemsJsonGzFiles == 0) {
            report.warnings.add("No item json.gz shards found under items/.");
        }
        if (report.recipeJsonGzFiles == 0) {
            report.warnings.add("No recipe json.gz shards found under recipes/.");
        }
        if (report.imagePngFiles == 0 && report.imageGifFiles == 0) {
            report.warnings.add("No rendered image files found under image/.");
        }
        if (report.staticAtlasPngFiles == 0 && report.staticAtlasManifestAssets > 0) {
            report.warnings.add("Static atlas manifest has assets but no atlas PNG files were found.");
        }
        if (report.animatedAtlasPngFiles == 0 && report.animatedAtlasManifestAssets > 0) {
            report.warnings.add("Animated atlas manifest has assets but no animated atlas PNG files were found.");
        }
        if (report.renderAssetMissingPrimaryArtifacts > 0) {
            report.warnings.add("Render assets with missing primary/static artifacts: "
                    + report.renderAssetMissingPrimaryArtifacts);
        }
        if (report.renderAssetMissingTimelineFrames > 0) {
            report.warnings.add("Render assets with missing timeline frame files: "
                    + report.renderAssetMissingTimelineFrames);
        }
        if (report.suspiciousStaticSingularityAssets > 0) {
            report.warnings.add("Singularity-like render assets exported without animation: "
                    + report.suspiciousStaticSingularityAssets);
        }
        if (report.renderAssetManifestAssets > 0 && report.totalAtlasManifestAssets == 0) {
            report.warnings.add("Render assets exist but no atlas manifest assets were found.");
        }
        if (report.browserAtlasPresent && report.browserLayoutPresent && report.browserAtlasLayoutMissingItems > 0) {
            report.warnings.add("Browser layout items missing atlas coverage: "
                    + report.browserAtlasLayoutMissingItems);
        }
        if (report.exportPathHygieneViolations > 0) {
            report.warnings.add("Runtime export payloads contain machine-specific paths: "
                    + report.exportPathHygieneViolations);
        }
        if (!report.semanticDiagnosticsPresent && report.rawItems > 0L) {
            report.warnings.add("Missing raw-export semantic diagnostics report.");
        }
        if (report.semanticTaggedItems > 0L && report.semanticClassificationCoverageRatio != null
                && report.semanticClassificationCoverageRatio < 0.80D) {
            report.warnings.add("Semantic classification coverage below 80%: "
                    + report.semanticClassificationCoverageRatio);
        }
        if (report.semanticMissingFacetFamilyCount > 0) {
            report.warnings.add("Semantic families missing facet extraction: "
                    + report.semanticMissingFacetFamilyCount);
        }
        if (report.semanticMissingSortKeyFamilyCount > 0) {
            report.warnings.add("Semantic families missing stable sort keys: "
                    + report.semanticMissingSortKeyFamilyCount);
        }
        if (report.semanticRulePack == null || !"ok".equals(report.semanticRulePack.status)) {
            report.warnings.add("Bundled GTNH semantic rule pack does not fully match the active Java semantic plugins.");
        }
        if (report.renderBackendFacts > 0L && report.renderBackendAngelica == 0L) {
            report.warnings.add("Native render export did not confirm Angelica as the active backend.");
        }
        if (report.renderTextureSpritesMissingTiming > 0L) {
            report.warnings.add("Native texture sprites missing animation timing: "
                    + report.renderTextureSpritesMissingTiming);
        }
        if (report.renderShaderItemsMissingCapture > 0L) {
            report.warnings.add("Shader/custom renderer items missing native capture assets: "
                    + report.renderShaderItemsMissingCapture);
        }
        if (report.renderUnknownSpecialRenderers > 0L) {
            report.warnings.add("Unknown special item renderers need explicit Angelica/native classification: "
                    + report.renderUnknownSpecialRenderers);
        }
        if (report.renderFramebufferCapturesWithoutFrames > 0L) {
            report.warnings.add("Native framebuffer captures without frame data: "
                    + report.renderFramebufferCapturesWithoutFrames);
        }
        if (report.rawRecipes > 0L && (report.rawNeiHandlers == 0L || report.rawNeiHandlerLayouts == 0L)) {
            report.warnings.add("NEI handler metadata/layout facts are missing; recipe pages will use generic categories.");
        }
        if (report.nativeUiMissingSurfaces > 0L) {
            report.warnings.add("Native UI surfaces missing captured backgrounds: " + report.nativeUiMissingSurfaces);
        }
        if (report.nativeUiSlotBoundsViolations > 0L || report.nativeUiBackgroundBoundsViolations > 0L) {
            report.warnings.add("Native UI geometry bounds violations: slots="
                    + report.nativeUiSlotBoundsViolations
                    + ", backgrounds="
                    + report.nativeUiBackgroundBoundsViolations);
        }
        if (report.nativeUiCoordinateContractViolations > 0L) {
            report.warnings.add("Native UI coordinate contract violations: "
                    + report.nativeUiCoordinateContractViolations);
        }
    }

    private static void determineHealthStatus(ExportValidationReportWriter.ValidationReport report) {
        addBlockedIf(report, report.itemsJsonGzFiles == 0 && report.rawItems == 0L, "No item facts were exported.");
        addBlockedIf(report, report.recipeJsonGzFiles == 0 && report.rawRecipes == 0L, "No recipe facts were exported.");
        addBlockedIf(report, report.exportPathHygieneViolations > 0, "Runtime payload contains machine-specific local paths.");
        addBlockedIf(report,
                report.rawItems > 0L
                        && report.semanticTotalItems > 0L
                        && report.semanticIdentityMapRows > 0L
                        && report.semanticIdentityMapRows != report.rawItems,
                "Semantic identity-map row count does not match raw item count.");
        addBlockedIf(report,
                report.rawItems > 0L && report.semanticDiagnosticsPresent && report.semanticTotalItems != report.rawItems,
                "Semantic diagnostic item count does not match raw item count.");
        addBlockedIf(report,
                report.nativeUiLayouts > 0L
                        && (report.nativeUiSlots == 0L
                                || report.nativeUiMissingSurfaces > 0L
                                || report.nativeUiSlotBoundsViolations > 0L
                                || report.nativeUiBackgroundBoundsViolations > 0L
                                || report.nativeUiCoordinateContractViolations > 0L),
                "Native UI ABI validation is blocked by missing surfaces, bounds errors, or coordinate contract drift.");
        addActionableIssue(report,
                "semantic-unclassified-families",
                report.semanticTopUnclassifiedFamilyActions,
                "Review or intentionally classify top unclassified tagged families.");
        addActionableIssue(report,
                "semantic-missing-facets",
                report.semanticMissingFacetFamilies,
                "Add family facet extraction so NeoNEI can filter/search variants without NBT guessing.");
        addActionableIssue(report,
                "semantic-missing-sort-keys",
                report.semanticMissingSortKeyFamilies,
                "Add stable family sort keys so expanded variant order remains NEI-like.");
        if (!report.blockedIssues.isEmpty()) {
            report.healthStatus = "blocked";
            report.compileReadinessStatus = "blocked";
        } else if (!report.warnings.isEmpty() || !report.actionableIssues.isEmpty()) {
            report.healthStatus = "warning";
            report.compileReadinessStatus = "ready-with-warnings";
        } else {
            report.healthStatus = "healthy";
            report.compileReadinessStatus = "ready";
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
