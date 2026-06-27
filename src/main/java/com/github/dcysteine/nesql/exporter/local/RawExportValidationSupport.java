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
        addCountMismatch(issues, "items", counts.items, counts.rawItems);
        addCountMismatch(issues, "fluids", counts.fluids, counts.rawFluids);
        addCountMismatch(issues, "recipes", counts.recipes, counts.rawRecipes);
        if (counts.renderAssets > 0) {
            addCountMismatch(issues, "renderAssets/textures", counts.renderAssets, counts.rawTextures);
        }
        report.validation.missingTextureCount = counts.rawItems > 0 && counts.rawTextures == 0 ? counts.rawItems : 0;
        report.validation.missingAnimationMetadataCount = counts.rawAnimations > 0 ? 0 : report.validation.missingAnimationMetadataCount;
        report.validation.missingGroupOrOrderCount = (counts.rawGroups == 0 || counts.rawNeiOrderEntries == 0) ? 1 : 0;
        addRenderSampleIssues(issues, "missing-render-capture", counts.renderShaderItemsMissingCaptureSamples);
        addRenderSampleIssues(issues, "render-capture-without-frames", counts.renderFramebufferCapturesWithoutFramesSamples);
        report.validation.failedStages = issues;
        report.validation.status = issues.isEmpty() ? "ok" : "warning";
        report.validation.gates = buildRawValidationGates(counts, issues);
        report.validation.readinessStatus = rawValidationReady(report.validation) ? "ready" : "blocked";
    }

    private static List<RawValidationGate> buildRawValidationGates(
            RawExportCounts counts,
            List<String> issues) {
        List<RawValidationGate> gates = new ArrayList<RawValidationGate>();
        gates.add(validationGate(
                "core-counts",
                issues.isEmpty(),
                issues.isEmpty()
                        ? "Raw fact counts match database/canonical source counts."
                        : "Raw fact counts have " + issues.size() + " mismatch(es)."));
        gates.add(validationGate(
                "browser-order",
                counts.rawGroups > 0 && counts.rawNeiOrderEntries > 0,
                counts.rawGroups > 0 && counts.rawNeiOrderEntries > 0
                        ? "NEI browser groups and ordering rows are present."
                        : "NEI browser groups or ordering rows are missing."));
        gates.add(validationGate(
                "nei-browser-contract",
                counts.neiBrowserItems > 0
                        && counts.rawGroups == counts.neiNativeGroups + counts.neiFallbackGroups + counts.neiSyntheticGroups
                        && counts.neiRepresentativeMismatches == 0,
                "NEI browser contract: panelItems="
                        + counts.neiRuntimePanelItems
                        + ", browserItems="
                        + counts.neiBrowserItems
                        + ", groups="
                        + counts.rawGroups
                        + ", nativeGroups="
                        + counts.neiNativeGroups
                        + ", fallbackGroups="
                        + counts.neiFallbackGroups
                        + ", syntheticGroups="
                        + counts.neiSyntheticGroups
                        + ", representativeMismatches="
                        + counts.neiRepresentativeMismatches
                        + "."));
        gates.add(validationGate(
                "semantic-identity",
                counts.rawItems == 0
                        || (counts.semanticTotalItems == counts.rawItems
                                && counts.semanticIdentityMapRows == counts.rawItems
                                && counts.semanticItems > 0
                                && counts.semanticFamilyCount > 0),
                "Semantic identity streams: rawItems="
                        + counts.rawItems
                        + ", totalItems="
                        + counts.semanticTotalItems
                        + ", identityMapRows="
                        + counts.semanticIdentityMapRows
                        + ", semanticItems="
                        + counts.semanticItems
                        + ", families="
                        + counts.semanticFamilyCount
                        + ", classifiedTagged="
                        + counts.semanticClassifiedTaggedItems
                        + ", unclassifiedTagged="
                        + counts.semanticUnclassifiedTaggedItems
                        + "."));
        gates.add(validationGate(
                "ui-family-census",
                counts.uiFamilyCensusHandlers > 0 && counts.uiFamilyCensusFamilies > 0,
                "NEI UI family census: handlers="
                        + counts.uiFamilyCensusHandlers
                        + ", families="
                        + counts.uiFamilyCensusFamilies
                        + "."));
        gates.add(validationGate(
                "ui-template-catalog",
                counts.uiTemplateCatalogHandlers > 0
                        && counts.uiTemplateCatalogTemplates > 0
                        && counts.uiTemplateCatalogFamilies > 0,
                "NEI UI template catalog: handlers="
                        + counts.uiTemplateCatalogHandlers
                        + ", templates="
                        + counts.uiTemplateCatalogTemplates
                        + ", families="
                        + counts.uiTemplateCatalogFamilies
                        + "."));
        gates.add(validationGate(
                "native-nei-rules",
                counts.neiGuidFilterRules >= 0 && counts.neiHiddenItemRules >= 0,
                "Native NEI rule streams: guidFilters="
                        + counts.neiGuidFilterRules
                        + ", hiddenItems="
                        + counts.neiHiddenItemRules
                        + "."));
        gates.add(validationGate(
                "nei-handler-metadata",
                counts.neiHandlers > 0 && counts.neiHandlerLayouts > 0,
                counts.neiHandlers > 0 && counts.neiHandlerLayouts > 0
                        ? "NEI handler metadata and layout streams are present."
                        : "NEI handler metadata or layout facts are missing."));
        gates.add(validationGate(
                "textures",
                counts.rawItems == 0 || counts.rawTextures > 0,
                counts.rawItems == 0 || counts.rawTextures > 0
                        ? "Texture index stream is present for exported item rows."
                        : "Texture index is empty while item rows are present."));
        gates.add(validationGate(
                "animations",
                counts.rawAnimations >= 0,
                "Animation metadata stream is present; zero rows is valid when no animated assets are detected."));
        gates.add(validationGate(
                "angelica-render-facts",
                counts.renderBackendFacts == 1
                        && counts.renderBackendAngelica == 1
                        && counts.renderTextureSprites >= 0
                        && counts.renderTextureSpritesMissingTiming == 0
                        && counts.renderItemRenderers == counts.rawItems
                        && counts.renderUnknownSpecialRenderers == 0,
                "Angelica render facts: backendFacts="
                        + counts.renderBackendFacts
                        + ", backendAngelica="
                        + counts.renderBackendAngelica
                        + ", textureSprites="
                        + counts.renderTextureSprites
                        + ", spritesMissingTiming="
                        + counts.renderTextureSpritesMissingTiming
                        + ", itemRenderers="
                        + counts.renderItemRenderers
                        + ", unknownSpecialRenderers="
                        + counts.renderUnknownSpecialRenderers
                        + ", shaderItems="
                        + counts.renderShaderItems
                        + ", shaderItemsRequiringCapture="
                        + counts.renderShaderItemsRequiringCapture
                        + ", shaderItemsMissingCapture="
                        + counts.renderShaderItemsMissingCapture
                        + ", framebufferCaptures="
                        + counts.renderFramebufferCaptures
                        + ", framebufferCapturesWithoutFrames="
                        + counts.renderFramebufferCapturesWithoutFrames
                        + ", rawItems="
                        + counts.rawItems
                        + "."));
        gates.add(validationGate(
                "angelica-special-captures",
                counts.renderShaderItemsRequiringCapture == 0
                        || (counts.renderShaderItemsMissingCapture == 0
                                && counts.renderFramebufferCapturesWithoutFrames == 0),
                counts.renderShaderItemsRequiringCapture == 0
                        ? "No shader/custom renderer items require framebuffer capture."
                        : "Shader/custom renderer items requiring capture="
                                + counts.renderShaderItemsRequiringCapture
                                + ", missing capture assets="
                                + counts.renderShaderItemsMissingCapture
                                + ", exported framebuffer capture assets="
                                + counts.renderFramebufferCaptures
                                + ", framebuffer captures without frames="
                                + counts.renderFramebufferCapturesWithoutFrames
                                + "."));
        gates.add(validationGate(
                "entity-models",
                counts.rawEntities >= 0,
                "Entity model stream is present; zero rows is valid when entity exports are not selected."));
        return gates;
    }

    private static RawValidationGate validationGate(String name, boolean ready, String summary) {
        RawValidationGate gate = new RawValidationGate();
        gate.name = name;
        gate.status = ready ? "ready" : "blocked";
        gate.summary = summary;
        return gate;
    }

    private static boolean rawValidationReady(RawExportValidation validation) {
        if (validation == null || validation.gates == null || validation.gates.isEmpty()) {
            return false;
        }
        for (RawValidationGate gate : validation.gates) {
            if (!"ready".equals(gate.status)) {
                return false;
            }
        }
        return true;
    }

    private static void addCountMismatch(List<String> issues, String label, long expected, long actual) {
        if (expected != actual) {
            issues.add("count-mismatch:" + label + ":expected=" + expected + ":actual=" + actual);
        }
    }

    private static void addRenderSampleIssues(List<String> issues, String label, List<String> samples) {
        if (issues == null || samples == null || samples.isEmpty()) {
            return;
        }
        for (String sample : samples) {
            if (sample != null && !sample.trim().isEmpty()) {
                issues.add(label + ":" + sample);
            }
        }
    }
}
