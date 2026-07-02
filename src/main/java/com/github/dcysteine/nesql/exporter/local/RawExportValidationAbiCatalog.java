package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stable ABI/catalog surface for raw-export validation gates, statuses, summaries, and issue keys.
 *
 * <p>Raw export validation code must consume this catalog instead of copying gate/status/issue
 * literals at call sites. The support class owns orchestration; this catalog owns the exported
 * validation vocabulary and the fail-closed gate semantics that external consumers observe.</p>
 */
final class RawExportValidationAbiCatalog {
    private static final List<StringDescriptor> STATUS_DESCRIPTORS = validateStringDescriptors(
            "raw validation status",
            Arrays.asList(
                    descriptor("ok", "ok"),
                    descriptor("warning", "warning"),
                    descriptor("ready", "ready"),
                    descriptor("blocked", "blocked")),
            "ok",
            "warning",
            "ready",
            "blocked");

    private static final List<StringDescriptor> COUNT_DESCRIPTORS = validateStringDescriptors(
            "raw validation count label",
            Arrays.asList(
                    descriptor("items", "items"),
                    descriptor("fluids", "fluids"),
                    descriptor("recipes", "recipes"),
                    descriptor("renderAssetsTextures", "renderAssets/textures")),
            "items",
            "fluids",
            "recipes",
            "renderAssetsTextures");

    private static final List<StringDescriptor> ISSUE_DESCRIPTORS = validateStringDescriptors(
            "raw validation issue",
            Arrays.asList(
                    descriptor("missingRenderCapture", "missing-render-capture"),
                    descriptor("renderCaptureWithoutFrames", "render-capture-without-frames"),
                    descriptor("nativeUiMissingSurfaces", "native-ui-missing-surfaces"),
                    descriptor("nativeUiMissingSurface", "native-ui-missing-surface"),
                    descriptor("nativeUiSlotBounds", "native-ui-slot-bounds"),
                    descriptor("nativeUiRectBounds", "native-ui-rect-bounds"),
                    descriptor("nativeUiPrimitiveBounds", "native-ui-primitive-bounds"),
                    descriptor("nativeUiBackgroundBounds", "native-ui-background-bounds"),
                    descriptor("nativeUiCoordinateContract", "native-ui-coordinate-contract"),
                    descriptor("nativeUiInteractionContract", "native-ui-interaction-contract"),
                    descriptor("countMismatch", "count-mismatch")),
            "missingRenderCapture",
            "renderCaptureWithoutFrames",
            "nativeUiMissingSurfaces",
            "nativeUiMissingSurface",
            "nativeUiSlotBounds",
            "nativeUiRectBounds",
            "nativeUiPrimitiveBounds",
            "nativeUiBackgroundBounds",
            "nativeUiCoordinateContract",
            "nativeUiInteractionContract",
            "countMismatch");

    private static final List<RawGateDescriptor> RAW_GATE_DESCRIPTORS = validateGateDescriptors(Arrays.asList(
            gateDescriptor(
                    "coreCounts",
                    "core-counts",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return coreCountsGate(issues.isEmpty(), issues.size());
                        }
                    }),
            gateDescriptor(
                    "browserOrder",
                    "browser-order",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return browserOrderGate(counts);
                        }
                    }),
            gateDescriptor(
                    "neiBrowserContract",
                    "nei-browser-contract",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return neiBrowserContractGate(counts);
                        }
                    }),
            gateDescriptor(
                    "semanticIdentity",
                    "semantic-identity",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return semanticIdentityGate(counts);
                        }
                    }),
            gateDescriptor(
                    "uiFamilyCensus",
                    "ui-family-census",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return uiFamilyCensusGate(counts);
                        }
                    }),
            gateDescriptor(
                    "uiTemplateCatalog",
                    "ui-template-catalog",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return uiTemplateCatalogGate(counts);
                        }
                    }),
            gateDescriptor(
                    "nativeUiAbi",
                    "native-ui-abi",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return nativeUiAbiGate(counts);
                        }
                    }),
            gateDescriptor(
                    "nativeNeiRules",
                    "native-nei-rules",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return nativeNeiRulesGate(counts);
                        }
                    }),
            gateDescriptor(
                    "neiHandlerMetadata",
                    "nei-handler-metadata",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return neiHandlerMetadataGate(counts);
                        }
                    }),
            gateDescriptor(
                    "textures",
                    "textures",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return texturesGate(counts);
                        }
                    }),
            gateDescriptor(
                    "animations",
                    "animations",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return animationsGate(counts);
                        }
                    }),
            gateDescriptor(
                    "angelicaRenderFacts",
                    "angelica-render-facts",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return angelicaRenderFactsGate(counts);
                        }
                    }),
            gateDescriptor(
                    "angelicaSpecialCaptures",
                    "angelica-special-captures",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return angelicaSpecialCapturesGate(counts);
                        }
                    }),
            gateDescriptor(
                    "entityModels",
                    "entity-models",
                    new GateFactory() {
                        @Override
                        public RawValidationGate build(RawExportCounts counts, List<String> issues) {
                            return entityModelsGate(counts);
                        }
                    })),
            "coreCounts",
            "browserOrder",
            "neiBrowserContract",
            "semanticIdentity",
            "uiFamilyCensus",
            "uiTemplateCatalog",
            "nativeUiAbi",
            "nativeNeiRules",
            "neiHandlerMetadata",
            "textures",
            "animations",
            "angelicaRenderFacts",
            "angelicaSpecialCaptures",
            "entityModels");

    static final String STATUS_OK = descriptorValue(STATUS_DESCRIPTORS, "ok");
    static final String STATUS_WARNING = descriptorValue(STATUS_DESCRIPTORS, "warning");
    static final String STATUS_READY = descriptorValue(STATUS_DESCRIPTORS, "ready");
    static final String STATUS_BLOCKED = descriptorValue(STATUS_DESCRIPTORS, "blocked");

    static final String GATE_CORE_COUNTS = gateName("coreCounts");
    static final String GATE_BROWSER_ORDER = gateName("browserOrder");
    static final String GATE_NEI_BROWSER_CONTRACT = gateName("neiBrowserContract");
    static final String GATE_SEMANTIC_IDENTITY = gateName("semanticIdentity");
    static final String GATE_UI_FAMILY_CENSUS = gateName("uiFamilyCensus");
    static final String GATE_UI_TEMPLATE_CATALOG = gateName("uiTemplateCatalog");
    static final String GATE_NATIVE_UI_ABI = gateName("nativeUiAbi");
    static final String GATE_NATIVE_NEI_RULES = gateName("nativeNeiRules");
    static final String GATE_NEI_HANDLER_METADATA = gateName("neiHandlerMetadata");
    static final String GATE_TEXTURES = gateName("textures");
    static final String GATE_ANIMATIONS = gateName("animations");
    static final String GATE_ANGELICA_RENDER_FACTS = gateName("angelicaRenderFacts");
    static final String GATE_ANGELICA_SPECIAL_CAPTURES = gateName("angelicaSpecialCaptures");
    static final String GATE_ENTITY_MODELS = gateName("entityModels");

    static final String COUNT_ITEMS = descriptorValue(COUNT_DESCRIPTORS, "items");
    static final String COUNT_FLUIDS = descriptorValue(COUNT_DESCRIPTORS, "fluids");
    static final String COUNT_RECIPES = descriptorValue(COUNT_DESCRIPTORS, "recipes");
    static final String COUNT_RENDER_ASSETS_TEXTURES = descriptorValue(COUNT_DESCRIPTORS, "renderAssetsTextures");

    static final String ISSUE_MISSING_RENDER_CAPTURE = descriptorValue(ISSUE_DESCRIPTORS, "missingRenderCapture");
    static final String ISSUE_RENDER_CAPTURE_WITHOUT_FRAMES =
            descriptorValue(ISSUE_DESCRIPTORS, "renderCaptureWithoutFrames");
    static final String ISSUE_NATIVE_UI_MISSING_SURFACES = descriptorValue(ISSUE_DESCRIPTORS, "nativeUiMissingSurfaces");
    static final String ISSUE_NATIVE_UI_MISSING_SURFACE = descriptorValue(ISSUE_DESCRIPTORS, "nativeUiMissingSurface");
    static final String ISSUE_NATIVE_UI_SLOT_BOUNDS = descriptorValue(ISSUE_DESCRIPTORS, "nativeUiSlotBounds");
    static final String ISSUE_NATIVE_UI_RECT_BOUNDS = descriptorValue(ISSUE_DESCRIPTORS, "nativeUiRectBounds");
    static final String ISSUE_NATIVE_UI_PRIMITIVE_BOUNDS = descriptorValue(ISSUE_DESCRIPTORS, "nativeUiPrimitiveBounds");
    static final String ISSUE_NATIVE_UI_BACKGROUND_BOUNDS = descriptorValue(ISSUE_DESCRIPTORS, "nativeUiBackgroundBounds");
    static final String ISSUE_NATIVE_UI_COORDINATE_CONTRACT =
            descriptorValue(ISSUE_DESCRIPTORS, "nativeUiCoordinateContract");
    static final String ISSUE_NATIVE_UI_INTERACTION_CONTRACT =
            descriptorValue(ISSUE_DESCRIPTORS, "nativeUiInteractionContract");

    private static final String ISSUE_COUNT_MISMATCH = descriptorValue(ISSUE_DESCRIPTORS, "countMismatch");

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

    static List<RawValidationGate> buildGates(RawExportCounts counts, List<String> issues) {
        if (counts == null) {
            throw new IllegalStateException("Raw validation counts must not be null");
        }
        if (issues == null) {
            throw new IllegalStateException("Raw validation issue list must not be null");
        }
        List<RawValidationGate> gates = new ArrayList<RawValidationGate>();
        for (RawGateDescriptor descriptor : RAW_GATE_DESCRIPTORS) {
            gates.add(descriptor.factory.build(counts, issues));
        }
        return gates;
    }

    private static RawValidationGate coreCountsGate(boolean noIssues, int issueCount) {
        return gate(
                GATE_CORE_COUNTS,
                noIssues,
                noIssues
                        ? "Raw fact counts match database/canonical source counts."
                        : "Raw fact counts have " + issueCount + " mismatch(es).");
    }

    private static RawValidationGate browserOrderGate(RawExportCounts counts) {
        boolean ready = counts.rawGroups > 0 && counts.rawNeiOrderEntries > 0;
        return gate(
                GATE_BROWSER_ORDER,
                ready,
                ready
                        ? "NEI browser groups and ordering rows are present."
                        : "NEI browser groups or ordering rows are missing.");
    }

    private static RawValidationGate neiBrowserContractGate(RawExportCounts counts) {
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

    private static RawValidationGate semanticIdentityGate(RawExportCounts counts) {
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

    private static RawValidationGate uiFamilyCensusGate(RawExportCounts counts) {
        return gate(
                GATE_UI_FAMILY_CENSUS,
                counts.uiFamilyCensusHandlers > 0 && counts.uiFamilyCensusFamilies > 0,
                "NEI UI family census: handlers="
                        + counts.uiFamilyCensusHandlers
                        + ", families="
                        + counts.uiFamilyCensusFamilies
                        + ".");
    }

    private static RawValidationGate uiTemplateCatalogGate(RawExportCounts counts) {
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

    private static RawValidationGate nativeUiAbiGate(RawExportCounts counts) {
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

    private static RawValidationGate nativeNeiRulesGate(RawExportCounts counts) {
        return gate(
                GATE_NATIVE_NEI_RULES,
                counts.neiGuidFilterRules >= 0 && counts.neiHiddenItemRules >= 0,
                "Native NEI rule streams: guidFilters="
                        + counts.neiGuidFilterRules
                        + ", hiddenItems="
                        + counts.neiHiddenItemRules
                        + ".");
    }

    private static RawValidationGate neiHandlerMetadataGate(RawExportCounts counts) {
        boolean ready = counts.neiHandlers > 0 && counts.neiHandlerLayouts > 0;
        return gate(
                GATE_NEI_HANDLER_METADATA,
                ready,
                ready
                        ? "NEI handler metadata and layout streams are present."
                        : "NEI handler metadata or layout facts are missing.");
    }

    private static RawValidationGate texturesGate(RawExportCounts counts) {
        boolean ready = counts.rawItems == 0 || counts.rawTextures > 0;
        return gate(
                GATE_TEXTURES,
                ready,
                ready
                        ? "Texture index stream is present for exported item rows."
                        : "Texture index is empty while item rows are present.");
    }

    private static RawValidationGate animationsGate(RawExportCounts counts) {
        return gate(
                GATE_ANIMATIONS,
                counts.rawAnimations >= 0,
                "Animation metadata stream is present; zero rows is valid when no animated assets are detected.");
    }

    private static RawValidationGate angelicaRenderFactsGate(RawExportCounts counts) {
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

    private static RawValidationGate angelicaSpecialCapturesGate(RawExportCounts counts) {
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

    private static RawValidationGate entityModelsGate(RawExportCounts counts) {
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
            if (gate == null || !STATUS_READY.equals(gate.status)) {
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

    private static StringDescriptor descriptor(String key, String value) {
        return new StringDescriptor(key, value);
    }

    private static RawGateDescriptor gateDescriptor(String key, String name, GateFactory factory) {
        return new RawGateDescriptor(key, name, factory);
    }

    private static List<StringDescriptor> validateStringDescriptors(
            String label,
            List<StringDescriptor> descriptors,
            String... expectedKeys) {
        if (descriptors == null) {
            throw new IllegalStateException(label + " descriptors must not be null");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedKeys));
        Set<String> seenKeys = new LinkedHashSet<String>();
        Set<String> seenValues = new LinkedHashSet<String>();
        for (StringDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException(label + " descriptor must not be null");
            }
            requireExpectedKey(label, expected, descriptor.key);
            requireNonEmpty(label + " descriptor key", descriptor.key);
            requireNonEmpty(label + " descriptor value", descriptor.value);
            if (!seenKeys.add(descriptor.key)) {
                throw new IllegalStateException("Duplicate " + label + " descriptor: " + descriptor.key);
            }
            if (!seenValues.add(descriptor.value)) {
                throw new IllegalStateException("Duplicate " + label + " descriptor value: " + descriptor.value);
            }
        }
        requireCompleteCoverage(label, expected, seenKeys);
        return Collections.unmodifiableList(new ArrayList<StringDescriptor>(descriptors));
    }

    private static List<RawGateDescriptor> validateGateDescriptors(
            List<RawGateDescriptor> descriptors,
            String... expectedKeys) {
        if (descriptors == null) {
            throw new IllegalStateException("raw validation gate descriptors must not be null");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedKeys));
        Set<String> seenKeys = new LinkedHashSet<String>();
        Set<String> seenNames = new LinkedHashSet<String>();
        for (RawGateDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("raw validation gate descriptor must not be null");
            }
            requireExpectedKey("raw validation gate", expected, descriptor.key);
            requireNonEmpty("raw validation gate descriptor key", descriptor.key);
            requireNonEmpty("raw validation gate descriptor name", descriptor.name);
            if (descriptor.factory == null) {
                throw new IllegalStateException("raw validation gate factory must not be null: " + descriptor.key);
            }
            if (!seenKeys.add(descriptor.key)) {
                throw new IllegalStateException("Duplicate raw validation gate descriptor: " + descriptor.key);
            }
            if (!seenNames.add(descriptor.name)) {
                throw new IllegalStateException("Duplicate raw validation gate name: " + descriptor.name);
            }
        }
        requireCompleteCoverage("raw validation gate", expected, seenKeys);
        return Collections.unmodifiableList(new ArrayList<RawGateDescriptor>(descriptors));
    }

    private static void requireExpectedKey(String label, Set<String> expected, String key) {
        if (!expected.contains(key)) {
            throw new IllegalStateException("Unknown " + label + " descriptor: " + key);
        }
    }

    private static void requireCompleteCoverage(String label, Set<String> expected, Set<String> seenKeys) {
        for (String key : expected) {
            if (!seenKeys.contains(key)) {
                throw new IllegalStateException("Missing " + label + " descriptor: " + key);
            }
        }
    }

    private static void requireNonEmpty(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty");
        }
    }

    private static String descriptorValue(List<StringDescriptor> descriptors, String key) {
        for (StringDescriptor descriptor : descriptors) {
            if (descriptor.key.equals(key)) {
                return descriptor.value;
            }
        }
        throw new IllegalStateException("Missing descriptor projection: " + key);
    }

    private static String gateName(String key) {
        for (RawGateDescriptor descriptor : RAW_GATE_DESCRIPTORS) {
            if (descriptor.key.equals(key)) {
                return descriptor.name;
            }
        }
        throw new IllegalStateException("Missing raw validation gate projection: " + key);
    }

    private interface GateFactory {
        RawValidationGate build(RawExportCounts counts, List<String> issues);
    }

    private static final class StringDescriptor {
        final String key;
        final String value;

        StringDescriptor(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final class RawGateDescriptor {
        final String key;
        final String name;
        final GateFactory factory;

        RawGateDescriptor(String key, String name, GateFactory factory) {
            this.key = key;
            this.name = name;
            this.factory = factory;
        }
    }
}
