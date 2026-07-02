package com.github.dcysteine.nesql.exporter.local;

import java.util.List;

/**
 * Stable ABI/catalog surface for raw-export validation gates, statuses, summaries, and issue keys.
 *
 * <p>Raw export validation code must consume this catalog instead of copying gate/status/issue
 * literals at call sites. The support class owns orchestration; this catalog owns the exported
 * validation vocabulary and the fail-closed gate semantics that external consumers observe.</p>
 */
final class RawExportValidationAbiCatalog {
    static final String STATUS_OK = "ok";
    static final String STATUS_WARNING = "warning";
    static final String STATUS_READY = "ready";
    static final String STATUS_BLOCKED = "blocked";

    static final String GATE_CORE_COUNTS = "core-counts";
    static final String GATE_BROWSER_ORDER = "browser-order";
    static final String GATE_NEI_BROWSER_CONTRACT = "nei-browser-contract";
    static final String GATE_SEMANTIC_IDENTITY = "semantic-identity";
    static final String GATE_UI_FAMILY_CENSUS = "ui-family-census";
    static final String GATE_UI_TEMPLATE_CATALOG = "ui-template-catalog";
    static final String GATE_NATIVE_UI_ABI = "native-ui-abi";
    static final String GATE_NATIVE_NEI_RULES = "native-nei-rules";
    static final String GATE_NEI_HANDLER_METADATA = "nei-handler-metadata";
    static final String GATE_TEXTURES = "textures";
    static final String GATE_ANIMATIONS = "animations";
    static final String GATE_ANGELICA_RENDER_FACTS = "angelica-render-facts";
    static final String GATE_ANGELICA_SPECIAL_CAPTURES = "angelica-special-captures";
    static final String GATE_ENTITY_MODELS = "entity-models";

    static final String COUNT_ITEMS = "items";
    static final String COUNT_FLUIDS = "fluids";
    static final String COUNT_RECIPES = "recipes";
    static final String COUNT_RENDER_ASSETS_TEXTURES = "renderAssets/textures";

    static final String ISSUE_MISSING_RENDER_CAPTURE = "missing-render-capture";
    static final String ISSUE_RENDER_CAPTURE_WITHOUT_FRAMES = "render-capture-without-frames";
    static final String ISSUE_NATIVE_UI_MISSING_SURFACES = "native-ui-missing-surfaces";
    static final String ISSUE_NATIVE_UI_MISSING_SURFACE = "native-ui-missing-surface";
    static final String ISSUE_NATIVE_UI_SLOT_BOUNDS = "native-ui-slot-bounds";
    static final String ISSUE_NATIVE_UI_RECT_BOUNDS = "native-ui-rect-bounds";
    static final String ISSUE_NATIVE_UI_PRIMITIVE_BOUNDS = "native-ui-primitive-bounds";
    static final String ISSUE_NATIVE_UI_BACKGROUND_BOUNDS = "native-ui-background-bounds";
    static final String ISSUE_NATIVE_UI_COORDINATE_CONTRACT = "native-ui-coordinate-contract";
    static final String ISSUE_NATIVE_UI_INTERACTION_CONTRACT = "native-ui-interaction-contract";

    private static final String ISSUE_COUNT_MISMATCH = "count-mismatch";

    private RawExportValidationAbiCatalog() {}

    static String validationStatus(List<String> issues) {
        return issues == null || issues.isEmpty() ? STATUS_OK : STATUS_WARNING;
    }

    static void addCountMismatch(List<String> issues, String label, long expected, long actual) {
        if (expected != actual) {
            issues.add(ISSUE_COUNT_MISMATCH + ":" + label + ":expected=" + expected + ":actual=" + actual);
        }
    }

    static void addRenderSampleIssues(List<String> issues, String label, List<String> samples) {
        if (issues == null || samples == null || samples.isEmpty()) {
            return;
        }
        for (String sample : samples) {
            if (sample != null && !sample.trim().isEmpty()) {
                issues.add(label + ":" + sample);
            }
        }
    }

    static void addNativeUiIssues(List<String> issues, RawExportCounts counts) {
        if (counts.nativeUiMissingSurfaces > 0) {
            issues.add(countedIssue(ISSUE_NATIVE_UI_MISSING_SURFACES, counts.nativeUiMissingSurfaces));
            addRenderSampleIssues(issues, ISSUE_NATIVE_UI_MISSING_SURFACE, counts.nativeUiMissingSurfaceSamples);
        }
        addCountedNativeUiSampleIssues(
                issues,
                ISSUE_NATIVE_UI_SLOT_BOUNDS,
                counts.nativeUiSlotBoundsViolations,
                counts.nativeUiSlotBoundsViolationSamples);
        addCountedNativeUiSampleIssues(
                issues,
                ISSUE_NATIVE_UI_RECT_BOUNDS,
                counts.nativeUiRectBoundsViolations,
                counts.nativeUiRectBoundsViolationSamples);
        addCountedNativeUiSampleIssues(
                issues,
                ISSUE_NATIVE_UI_PRIMITIVE_BOUNDS,
                counts.nativeUiPrimitiveBoundsViolations,
                counts.nativeUiPrimitiveBoundsViolationSamples);
        addCountedNativeUiSampleIssues(
                issues,
                ISSUE_NATIVE_UI_BACKGROUND_BOUNDS,
                counts.nativeUiBackgroundBoundsViolations,
                counts.nativeUiBackgroundBoundsViolationSamples);
        addCountedNativeUiSampleIssues(
                issues,
                ISSUE_NATIVE_UI_COORDINATE_CONTRACT,
                counts.nativeUiCoordinateContractViolations,
                counts.nativeUiCoordinateContractViolationSamples);
        addCountedNativeUiSampleIssues(
                issues,
                ISSUE_NATIVE_UI_INTERACTION_CONTRACT,
                counts.nativeUiInteractionContractViolations,
                counts.nativeUiInteractionContractViolationSamples);
    }

    static RawValidationGate coreCountsGate(boolean noIssues, int issueCount) {
        return gate(
                GATE_CORE_COUNTS,
                noIssues,
                noIssues
                        ? "Raw fact counts match database/canonical source counts."
                        : "Raw fact counts have " + issueCount + " mismatch(es).");
    }

    static RawValidationGate browserOrderGate(RawExportCounts counts) {
        boolean ready = counts.rawGroups > 0 && counts.rawNeiOrderEntries > 0;
        return gate(
                GATE_BROWSER_ORDER,
                ready,
                ready
                        ? "NEI browser groups and ordering rows are present."
                        : "NEI browser groups or ordering rows are missing.");
    }

    static RawValidationGate neiBrowserContractGate(RawExportCounts counts) {
        return gate(
                GATE_NEI_BROWSER_CONTRACT,
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
                        + ".");
    }

    static RawValidationGate semanticIdentityGate(RawExportCounts counts) {
        return gate(
                GATE_SEMANTIC_IDENTITY,
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
                        + ".");
    }

    static RawValidationGate uiFamilyCensusGate(RawExportCounts counts) {
        return gate(
                GATE_UI_FAMILY_CENSUS,
                counts.uiFamilyCensusHandlers > 0 && counts.uiFamilyCensusFamilies > 0,
                "NEI UI family census: handlers="
                        + counts.uiFamilyCensusHandlers
                        + ", families="
                        + counts.uiFamilyCensusFamilies
                        + ".");
    }

    static RawValidationGate uiTemplateCatalogGate(RawExportCounts counts) {
        return gate(
                GATE_UI_TEMPLATE_CATALOG,
                counts.uiTemplateCatalogHandlers > 0
                        && counts.uiTemplateCatalogTemplates > 0
                        && counts.uiTemplateCatalogFamilies > 0,
                "NEI UI template catalog: handlers="
                        + counts.uiTemplateCatalogHandlers
                        + ", templates="
                        + counts.uiTemplateCatalogTemplates
                        + ", families="
                        + counts.uiTemplateCatalogFamilies
                        + ".");
    }

    static RawValidationGate nativeUiAbiGate(RawExportCounts counts) {
        return gate(
                GATE_NATIVE_UI_ABI,
                counts.nativeUiLayouts > 0
                        && counts.nativeUiSlots > 0
                        && counts.nativeUiMissingSurfaces == 0
                        && counts.nativeUiSlotBoundsViolations == 0
                        && counts.nativeUiRectBoundsViolations == 0
                        && counts.nativeUiPrimitiveBoundsViolations == 0
                        && counts.nativeUiBackgroundBoundsViolations == 0
                        && counts.nativeUiCoordinateContractViolations == 0
                        && counts.nativeUiInteractionContractViolations == 0,
                "Native UI ABI: layouts="
                        + counts.nativeUiLayouts
                        + ", slots="
                        + counts.nativeUiSlots
                        + ", rects="
                        + counts.nativeUiRects
                        + ", primitives="
                        + counts.nativeUiPrimitives
                        + ", missingSurfaces="
                        + counts.nativeUiMissingSurfaces
                        + ", slotBoundsViolations="
                        + counts.nativeUiSlotBoundsViolations
                        + ", rectBoundsViolations="
                        + counts.nativeUiRectBoundsViolations
                        + ", primitiveBoundsViolations="
                        + counts.nativeUiPrimitiveBoundsViolations
                        + ", backgroundBoundsViolations="
                        + counts.nativeUiBackgroundBoundsViolations
                        + ", coordinateContractViolations="
                        + counts.nativeUiCoordinateContractViolations
                        + ", interactionContractViolations="
                        + counts.nativeUiInteractionContractViolations
                        + ".");
    }

    static RawValidationGate nativeNeiRulesGate(RawExportCounts counts) {
        return gate(
                GATE_NATIVE_NEI_RULES,
                counts.neiGuidFilterRules >= 0 && counts.neiHiddenItemRules >= 0,
                "Native NEI rule streams: guidFilters="
                        + counts.neiGuidFilterRules
                        + ", hiddenItems="
                        + counts.neiHiddenItemRules
                        + ".");
    }

    static RawValidationGate neiHandlerMetadataGate(RawExportCounts counts) {
        boolean ready = counts.neiHandlers > 0 && counts.neiHandlerLayouts > 0;
        return gate(
                GATE_NEI_HANDLER_METADATA,
                ready,
                ready
                        ? "NEI handler metadata and layout streams are present."
                        : "NEI handler metadata or layout facts are missing.");
    }

    static RawValidationGate texturesGate(RawExportCounts counts) {
        boolean ready = counts.rawItems == 0 || counts.rawTextures > 0;
        return gate(
                GATE_TEXTURES,
                ready,
                ready
                        ? "Texture index stream is present for exported item rows."
                        : "Texture index is empty while item rows are present.");
    }

    static RawValidationGate animationsGate(RawExportCounts counts) {
        return gate(
                GATE_ANIMATIONS,
                counts.rawAnimations >= 0,
                "Animation metadata stream is present; zero rows is valid when no animated assets are detected.");
    }

    static RawValidationGate angelicaRenderFactsGate(RawExportCounts counts) {
        return gate(
                GATE_ANGELICA_RENDER_FACTS,
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
                        + ".");
    }

    static RawValidationGate angelicaSpecialCapturesGate(RawExportCounts counts) {
        boolean ready = counts.renderShaderItemsRequiringCapture == 0
                || (counts.renderShaderItemsMissingCapture == 0
                        && counts.renderFramebufferCapturesWithoutFrames == 0);
        return gate(
                GATE_ANGELICA_SPECIAL_CAPTURES,
                ready,
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
                                + ".");
    }

    static RawValidationGate entityModelsGate(RawExportCounts counts) {
        return gate(
                GATE_ENTITY_MODELS,
                counts.rawEntities >= 0,
                "Entity model stream is present; zero rows is valid when entity exports are not selected.");
    }

    static boolean allGatesReady(RawExportValidation validation) {
        if (validation == null || validation.gates == null || validation.gates.isEmpty()) {
            return false;
        }
        for (RawValidationGate gate : validation.gates) {
            if (!STATUS_READY.equals(gate.status)) {
                return false;
            }
        }
        return true;
    }

    private static void addCountedNativeUiSampleIssues(
            List<String> issues,
            String label,
            long count,
            List<String> samples) {
        if (count > 0) {
            issues.add(countedIssue(label, count));
            addRenderSampleIssues(issues, label, samples);
        }
    }

    private static String countedIssue(String label, long count) {
        return label + ":" + count;
    }

    private static RawValidationGate gate(String name, boolean ready, String summary) {
        RawValidationGate gate = new RawValidationGate();
        gate.name = name;
        gate.status = ready ? STATUS_READY : STATUS_BLOCKED;
        gate.summary = summary;
        return gate;
    }
}
