package com.github.dcysteine.nesql.exporter.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stable ABI/catalog surface for validation evidence JSON member names and probe tokens.
 *
 * <p>Validation probes consume this catalog instead of copying raw-export report, semantic
 * diagnostics, browser atlas, render asset, checksum, or recipe-anomaly member literals.</p>
 */
final class ExportValidationEvidenceCatalog {
    static final int SEMANTIC_SAMPLE_LIMIT = 20;

    private static final List<FieldDescriptor> OBJECT_DESCRIPTORS = validateFieldDescriptors(
            "validation evidence object",
            Arrays.asList(
                    field("counts", "counts"),
                    field("summary", "summary"),
                    field("items", "items"),
                    field("defaultEntries", "defaultEntries"),
                    field("staticAtlas", "staticAtlas"),
                    field("animatedAtlas", "animatedAtlas"),
                    field("timeline", "timeline"),
                    field("frames", "frames")),
            "counts",
            "summary",
            "items",
            "defaultEntries",
            "staticAtlas",
            "animatedAtlas",
            "timeline",
            "frames");

    private static final List<FieldDescriptor> MEMBER_DESCRIPTORS = validateFieldDescriptors(
            "validation evidence member",
            Arrays.asList(
                    field("status", "status"),
                    field("generatedAt", "generatedAt"),
                    field("assetHash", "assetHash"),
                    field("checksum", "checksum"),
                    field("sha256", "sha256"),
                    field("itemId", "itemId"),
                    field("representativeItemId", "representativeItemId"),
                    field("atlasFile", "atlasFile"),
                    field("path", "path")),
            "status",
            "generatedAt",
            "assetHash",
            "checksum",
            "sha256",
            "itemId",
            "representativeItemId",
            "atlasFile",
            "path");

    private static final List<FieldDescriptor> ERROR_FIELD_DESCRIPTORS = validateFieldDescriptors(
            "validation error field",
            Arrays.asList(
                    field("schemaVersion", "schemaVersion"),
                    field("generatedAt", "generatedAt"),
                    field("stage", "stage"),
                    field("code", "code"),
                    field("message", "message"),
                    field("file", "file"),
                    field("line", "line"),
                    field("rule", "rule")),
            "schemaVersion",
            "generatedAt",
            "stage",
            "code",
            "message",
            "file",
            "line",
            "rule");

    private static final List<FieldDescriptor> PATH_TOKEN_DESCRIPTORS = validateFieldDescriptors(
            "validation evidence path token",
            Arrays.asList(
                    field("imageDirectoryPrefix", "image/"),
                    field("itemIdSeparator", "~"),
                    field("itemIdItemPrefix", "i"),
                    field("itemIdMetaZero", "0")),
            "imageDirectoryPrefix",
            "itemIdSeparator",
            "itemIdItemPrefix",
            "itemIdMetaZero");

    private static final List<FieldDescriptor> SINGULARITY_TOKEN_DESCRIPTORS = validateFieldDescriptors(
            "validation evidence singularity token",
            Arrays.asList(
                    field("singularity", "singularity"),
                    field("singularitie", "singularitie"),
                    field("eternal", "eternalsingularity"),
                    field("universal", "universalsingularity"),
                    field("universalUnderscore", "universal_singularity"),
                    field("avaritia", "avaritia"),
                    field("cosmicNeutronium", "cosmicneutronium"),
                    field("transcendentMetal", "transcendentmetal"),
                    field("universium", "universium")),
            "singularity",
            "singularitie",
            "eternal",
            "universal",
            "universalUnderscore",
            "avaritia",
            "cosmicNeutronium",
            "transcendentMetal",
            "universium");

    private static final List<FieldDescriptor> ANIMATION_TOKEN_DESCRIPTORS = validateFieldDescriptors(
            "validation evidence animation token",
            Arrays.asList(
                    field("gif", ".gif"),
                    field("animated", "animated"),
                    field("timeline", "timeline")),
            "gif",
            "animated",
            "timeline");

    static final String OBJECT_COUNTS = descriptorValue(OBJECT_DESCRIPTORS, "counts");
    static final String OBJECT_SUMMARY = descriptorValue(OBJECT_DESCRIPTORS, "summary");
    static final String OBJECT_ITEMS = descriptorValue(OBJECT_DESCRIPTORS, "items");
    static final String OBJECT_DEFAULT_ENTRIES = descriptorValue(OBJECT_DESCRIPTORS, "defaultEntries");
    static final String OBJECT_STATIC_ATLAS = descriptorValue(OBJECT_DESCRIPTORS, "staticAtlas");
    static final String OBJECT_ANIMATED_ATLAS = descriptorValue(OBJECT_DESCRIPTORS, "animatedAtlas");
    static final String OBJECT_TIMELINE = descriptorValue(OBJECT_DESCRIPTORS, "timeline");
    static final String OBJECT_FRAMES = descriptorValue(OBJECT_DESCRIPTORS, "frames");

    static final String MEMBER_STATUS = descriptorValue(MEMBER_DESCRIPTORS, "status");
    static final String MEMBER_GENERATED_AT = descriptorValue(MEMBER_DESCRIPTORS, "generatedAt");
    static final String MEMBER_ASSET_HASH = descriptorValue(MEMBER_DESCRIPTORS, "assetHash");
    static final String MEMBER_CHECKSUM = descriptorValue(MEMBER_DESCRIPTORS, "checksum");
    static final String MEMBER_SHA256 = descriptorValue(MEMBER_DESCRIPTORS, "sha256");
    static final String MEMBER_ITEM_ID = descriptorValue(MEMBER_DESCRIPTORS, "itemId");
    static final String MEMBER_REPRESENTATIVE_ITEM_ID =
            descriptorValue(MEMBER_DESCRIPTORS, "representativeItemId");
    static final String MEMBER_ATLAS_FILE = descriptorValue(MEMBER_DESCRIPTORS, "atlasFile");
    static final String MEMBER_PATH = descriptorValue(MEMBER_DESCRIPTORS, "path");

    static final String ERROR_FIELD_SCHEMA_VERSION = descriptorValue(ERROR_FIELD_DESCRIPTORS, "schemaVersion");
    static final String ERROR_FIELD_GENERATED_AT = descriptorValue(ERROR_FIELD_DESCRIPTORS, "generatedAt");
    static final String ERROR_FIELD_STAGE = descriptorValue(ERROR_FIELD_DESCRIPTORS, "stage");
    static final String ERROR_FIELD_CODE = descriptorValue(ERROR_FIELD_DESCRIPTORS, "code");
    static final String ERROR_FIELD_MESSAGE = descriptorValue(ERROR_FIELD_DESCRIPTORS, "message");
    static final String ERROR_FIELD_FILE = descriptorValue(ERROR_FIELD_DESCRIPTORS, "file");
    static final String ERROR_FIELD_LINE = descriptorValue(ERROR_FIELD_DESCRIPTORS, "line");
    static final String ERROR_FIELD_RULE = descriptorValue(ERROR_FIELD_DESCRIPTORS, "rule");

    static final String IMAGE_DIRECTORY_PREFIX = descriptorValue(PATH_TOKEN_DESCRIPTORS, "imageDirectoryPrefix");
    static final String ITEM_ID_SEPARATOR = descriptorValue(PATH_TOKEN_DESCRIPTORS, "itemIdSeparator");
    static final String ITEM_ID_ITEM_PREFIX = descriptorValue(PATH_TOKEN_DESCRIPTORS, "itemIdItemPrefix");
    static final String ITEM_ID_META_ZERO = descriptorValue(PATH_TOKEN_DESCRIPTORS, "itemIdMetaZero");

    static final String SINGULARITY_TOKEN_SINGULARITY =
            descriptorValue(SINGULARITY_TOKEN_DESCRIPTORS, "singularity");
    static final String SINGULARITY_TOKEN_SINGULARITIE =
            descriptorValue(SINGULARITY_TOKEN_DESCRIPTORS, "singularitie");
    static final String SINGULARITY_TOKEN_ETERNAL =
            descriptorValue(SINGULARITY_TOKEN_DESCRIPTORS, "eternal");
    static final String SINGULARITY_TOKEN_UNIVERSAL =
            descriptorValue(SINGULARITY_TOKEN_DESCRIPTORS, "universal");
    static final String SINGULARITY_TOKEN_UNIVERSAL_UNDERSCORE =
            descriptorValue(SINGULARITY_TOKEN_DESCRIPTORS, "universalUnderscore");
    static final String SINGULARITY_TOKEN_AVARITIA =
            descriptorValue(SINGULARITY_TOKEN_DESCRIPTORS, "avaritia");
    static final String SINGULARITY_TOKEN_COSMIC_NEUTRONIUM =
            descriptorValue(SINGULARITY_TOKEN_DESCRIPTORS, "cosmicNeutronium");
    static final String SINGULARITY_TOKEN_TRANSCENDENT_METAL =
            descriptorValue(SINGULARITY_TOKEN_DESCRIPTORS, "transcendentMetal");
    static final String SINGULARITY_TOKEN_UNIVERSIUM =
            descriptorValue(SINGULARITY_TOKEN_DESCRIPTORS, "universium");
    static final String ANIMATION_TOKEN_GIF = descriptorValue(ANIMATION_TOKEN_DESCRIPTORS, "gif");
    static final String ANIMATION_TOKEN_ANIMATED = descriptorValue(ANIMATION_TOKEN_DESCRIPTORS, "animated");
    static final String ANIMATION_TOKEN_TIMELINE = descriptorValue(ANIMATION_TOKEN_DESCRIPTORS, "timeline");

    private ExportValidationEvidenceCatalog() {}

    static final class RawCount {
        private static final List<FieldDescriptor> DESCRIPTORS = validateFieldDescriptors(
                "raw count evidence",
                Arrays.asList(
                        field("rawItems", "rawItems"),
                        field("rawRecipes", "rawRecipes"),
                        field("rawTextures", "rawTextures"),
                        field("rawAnimations", "rawAnimations"),
                        field("neiBrowserItems", "neiBrowserItems"),
                        field("rawGroups", "rawGroups"),
                        field("rawNeiOrderEntries", "rawNeiOrderEntries"),
                        field("neiRuntimePanelItems", "neiRuntimePanelItems"),
                        field("neiExportOnlyItems", "neiExportOnlyItems"),
                        field("neiDefaultEntries", "neiDefaultEntries"),
                        field("neiFallbackGroups", "neiFallbackGroups"),
                        field("neiNativeGroups", "neiNativeGroups"),
                        field("neiSyntheticGroups", "neiSyntheticGroups"),
                        field("neiGuidFilterRules", "neiGuidFilterRules"),
                        field("neiHiddenItemRules", "neiHiddenItemRules"),
                        field("neiHiddenItems", "neiHiddenItems"),
                        field("neiRepresentativeMismatches", "neiRepresentativeMismatches"),
                        field("neiHandlers", "neiHandlers"),
                        field("neiHandlerLayouts", "neiHandlerLayouts"),
                        field("nativeUiLayouts", "nativeUiLayouts"),
                        field("nativeUiSlots", "nativeUiSlots"),
                        field("nativeUiRects", "nativeUiRects"),
                        field("nativeUiPrimitives", "nativeUiPrimitives"),
                        field("nativeUiMissingSurfaces", "nativeUiMissingSurfaces"),
                        field("nativeUiSlotBoundsViolations", "nativeUiSlotBoundsViolations"),
                        field("nativeUiRectBoundsViolations", "nativeUiRectBoundsViolations"),
                        field("nativeUiPrimitiveBoundsViolations", "nativeUiPrimitiveBoundsViolations"),
                        field("nativeUiBackgroundBoundsViolations", "nativeUiBackgroundBoundsViolations"),
                        field("nativeUiCoordinateContractViolations", "nativeUiCoordinateContractViolations"),
                        field("nativeUiInteractionContractViolations", "nativeUiInteractionContractViolations"),
                        field("recipeTypes", "recipeTypes"),
                        field("renderBackendFacts", "renderBackendFacts"),
                        field("renderBackendAngelica", "renderBackendAngelica"),
                        field("renderTextureSprites", "renderTextureSprites"),
                        field("renderTextureSpritesMissingTiming", "renderTextureSpritesMissingTiming"),
                        field("renderItemRenderers", "renderItemRenderers"),
                        field("renderShaderItems", "renderShaderItems"),
                        field("renderShaderItemsRequiringCapture", "renderShaderItemsRequiringCapture"),
                        field("renderShaderItemsMissingCapture", "renderShaderItemsMissingCapture"),
                        field("renderUnknownSpecialRenderers", "renderUnknownSpecialRenderers"),
                        field("renderFramebufferCaptures", "renderFramebufferCaptures"),
                        field("renderFramebufferCapturesWithoutFrames", "renderFramebufferCapturesWithoutFrames"),
                        field("semanticTotalItems", "semanticTotalItems"),
                        field("semanticTaggedItems", "semanticTaggedItems"),
                        field("semanticClassifiedTaggedItems", "semanticClassifiedTaggedItems"),
                        field("semanticUnclassifiedTaggedItems", "semanticUnclassifiedTaggedItems"),
                        field("semanticFamilyCount", "semanticFamilyCount"),
                        field("semanticItems", "semanticItems"),
                        field("semanticVariants", "semanticVariants"),
                        field("semanticPayloads", "semanticPayloads"),
                        field("semanticIdentityMapRows", "semanticIdentityMapRows")),
                "rawItems",
                "rawRecipes",
                "rawTextures",
                "rawAnimations",
                "neiBrowserItems",
                "rawGroups",
                "rawNeiOrderEntries",
                "neiRuntimePanelItems",
                "neiExportOnlyItems",
                "neiDefaultEntries",
                "neiFallbackGroups",
                "neiNativeGroups",
                "neiSyntheticGroups",
                "neiGuidFilterRules",
                "neiHiddenItemRules",
                "neiHiddenItems",
                "neiRepresentativeMismatches",
                "neiHandlers",
                "neiHandlerLayouts",
                "nativeUiLayouts",
                "nativeUiSlots",
                "nativeUiRects",
                "nativeUiPrimitives",
                "nativeUiMissingSurfaces",
                "nativeUiSlotBoundsViolations",
                "nativeUiRectBoundsViolations",
                "nativeUiPrimitiveBoundsViolations",
                "nativeUiBackgroundBoundsViolations",
                "nativeUiCoordinateContractViolations",
                "nativeUiInteractionContractViolations",
                "recipeTypes",
                "renderBackendFacts",
                "renderBackendAngelica",
                "renderTextureSprites",
                "renderTextureSpritesMissingTiming",
                "renderItemRenderers",
                "renderShaderItems",
                "renderShaderItemsRequiringCapture",
                "renderShaderItemsMissingCapture",
                "renderUnknownSpecialRenderers",
                "renderFramebufferCaptures",
                "renderFramebufferCapturesWithoutFrames",
                "semanticTotalItems",
                "semanticTaggedItems",
                "semanticClassifiedTaggedItems",
                "semanticUnclassifiedTaggedItems",
                "semanticFamilyCount",
                "semanticItems",
                "semanticVariants",
                "semanticPayloads",
                "semanticIdentityMapRows");

        static final String RAW_ITEMS = descriptorValue(DESCRIPTORS, "rawItems");
        static final String RAW_RECIPES = descriptorValue(DESCRIPTORS, "rawRecipes");
        static final String RAW_TEXTURES = descriptorValue(DESCRIPTORS, "rawTextures");
        static final String RAW_ANIMATIONS = descriptorValue(DESCRIPTORS, "rawAnimations");
        static final String NEI_BROWSER_ITEMS = descriptorValue(DESCRIPTORS, "neiBrowserItems");
        static final String RAW_GROUPS = descriptorValue(DESCRIPTORS, "rawGroups");
        static final String RAW_NEI_ORDER_ENTRIES = descriptorValue(DESCRIPTORS, "rawNeiOrderEntries");
        static final String NEI_RUNTIME_PANEL_ITEMS = descriptorValue(DESCRIPTORS, "neiRuntimePanelItems");
        static final String NEI_EXPORT_ONLY_ITEMS = descriptorValue(DESCRIPTORS, "neiExportOnlyItems");
        static final String NEI_DEFAULT_ENTRIES = descriptorValue(DESCRIPTORS, "neiDefaultEntries");
        static final String NEI_FALLBACK_GROUPS = descriptorValue(DESCRIPTORS, "neiFallbackGroups");
        static final String NEI_NATIVE_GROUPS = descriptorValue(DESCRIPTORS, "neiNativeGroups");
        static final String NEI_SYNTHETIC_GROUPS = descriptorValue(DESCRIPTORS, "neiSyntheticGroups");
        static final String NEI_GUID_FILTER_RULES = descriptorValue(DESCRIPTORS, "neiGuidFilterRules");
        static final String NEI_HIDDEN_ITEM_RULES = descriptorValue(DESCRIPTORS, "neiHiddenItemRules");
        static final String NEI_HIDDEN_ITEMS = descriptorValue(DESCRIPTORS, "neiHiddenItems");
        static final String NEI_REPRESENTATIVE_MISMATCHES =
                descriptorValue(DESCRIPTORS, "neiRepresentativeMismatches");
        static final String NEI_HANDLERS = descriptorValue(DESCRIPTORS, "neiHandlers");
        static final String NEI_HANDLER_LAYOUTS = descriptorValue(DESCRIPTORS, "neiHandlerLayouts");
        static final String NATIVE_UI_LAYOUTS = descriptorValue(DESCRIPTORS, "nativeUiLayouts");
        static final String NATIVE_UI_SLOTS = descriptorValue(DESCRIPTORS, "nativeUiSlots");
        static final String NATIVE_UI_RECTS = descriptorValue(DESCRIPTORS, "nativeUiRects");
        static final String NATIVE_UI_PRIMITIVES = descriptorValue(DESCRIPTORS, "nativeUiPrimitives");
        static final String NATIVE_UI_MISSING_SURFACES =
                descriptorValue(DESCRIPTORS, "nativeUiMissingSurfaces");
        static final String NATIVE_UI_SLOT_BOUNDS_VIOLATIONS =
                descriptorValue(DESCRIPTORS, "nativeUiSlotBoundsViolations");
        static final String NATIVE_UI_RECT_BOUNDS_VIOLATIONS =
                descriptorValue(DESCRIPTORS, "nativeUiRectBoundsViolations");
        static final String NATIVE_UI_PRIMITIVE_BOUNDS_VIOLATIONS =
                descriptorValue(DESCRIPTORS, "nativeUiPrimitiveBoundsViolations");
        static final String NATIVE_UI_BACKGROUND_BOUNDS_VIOLATIONS =
                descriptorValue(DESCRIPTORS, "nativeUiBackgroundBoundsViolations");
        static final String NATIVE_UI_COORDINATE_CONTRACT_VIOLATIONS =
                descriptorValue(DESCRIPTORS, "nativeUiCoordinateContractViolations");
        static final String NATIVE_UI_INTERACTION_CONTRACT_VIOLATIONS =
                descriptorValue(DESCRIPTORS, "nativeUiInteractionContractViolations");
        static final String RECIPE_TYPES = descriptorValue(DESCRIPTORS, "recipeTypes");
        static final String RENDER_BACKEND_FACTS = descriptorValue(DESCRIPTORS, "renderBackendFacts");
        static final String RENDER_BACKEND_ANGELICA = descriptorValue(DESCRIPTORS, "renderBackendAngelica");
        static final String RENDER_TEXTURE_SPRITES = descriptorValue(DESCRIPTORS, "renderTextureSprites");
        static final String RENDER_TEXTURE_SPRITES_MISSING_TIMING =
                descriptorValue(DESCRIPTORS, "renderTextureSpritesMissingTiming");
        static final String RENDER_ITEM_RENDERERS = descriptorValue(DESCRIPTORS, "renderItemRenderers");
        static final String RENDER_SHADER_ITEMS = descriptorValue(DESCRIPTORS, "renderShaderItems");
        static final String RENDER_SHADER_ITEMS_REQUIRING_CAPTURE =
                descriptorValue(DESCRIPTORS, "renderShaderItemsRequiringCapture");
        static final String RENDER_SHADER_ITEMS_MISSING_CAPTURE =
                descriptorValue(DESCRIPTORS, "renderShaderItemsMissingCapture");
        static final String RENDER_UNKNOWN_SPECIAL_RENDERERS =
                descriptorValue(DESCRIPTORS, "renderUnknownSpecialRenderers");
        static final String RENDER_FRAMEBUFFER_CAPTURES =
                descriptorValue(DESCRIPTORS, "renderFramebufferCaptures");
        static final String RENDER_FRAMEBUFFER_CAPTURES_WITHOUT_FRAMES =
                descriptorValue(DESCRIPTORS, "renderFramebufferCapturesWithoutFrames");
        static final String SEMANTIC_TOTAL_ITEMS = descriptorValue(DESCRIPTORS, "semanticTotalItems");
        static final String SEMANTIC_TAGGED_ITEMS = descriptorValue(DESCRIPTORS, "semanticTaggedItems");
        static final String SEMANTIC_CLASSIFIED_TAGGED_ITEMS =
                descriptorValue(DESCRIPTORS, "semanticClassifiedTaggedItems");
        static final String SEMANTIC_UNCLASSIFIED_TAGGED_ITEMS =
                descriptorValue(DESCRIPTORS, "semanticUnclassifiedTaggedItems");
        static final String SEMANTIC_FAMILY_COUNT = descriptorValue(DESCRIPTORS, "semanticFamilyCount");
        static final String SEMANTIC_ITEMS = descriptorValue(DESCRIPTORS, "semanticItems");
        static final String SEMANTIC_VARIANTS = descriptorValue(DESCRIPTORS, "semanticVariants");
        static final String SEMANTIC_PAYLOADS = descriptorValue(DESCRIPTORS, "semanticPayloads");
        static final String SEMANTIC_IDENTITY_MAP_ROWS =
                descriptorValue(DESCRIPTORS, "semanticIdentityMapRows");

        private RawCount() {}
    }

    static final class SemanticDiagnostics {
        private static final List<FieldDescriptor> DESCRIPTORS = validateFieldDescriptors(
                "semantic diagnostics evidence",
                Arrays.asList(
                        field("topUnclassifiedFamilyActions", "topUnclassifiedFamilyActions"),
                        field("missingFacetFamilies", "missingFacetFamilies"),
                        field("missingSortKeyFamilies", "missingSortKeyFamilies"),
                        field("beforePublicItems", "beforePublicItems"),
                        field("taggedItems", "taggedItems"),
                        field("classifiedTaggedItems", "classifiedTaggedItems"),
                        field("unclassifiedTaggedItems", "unclassifiedTaggedItems")),
                "topUnclassifiedFamilyActions",
                "missingFacetFamilies",
                "missingSortKeyFamilies",
                "beforePublicItems",
                "taggedItems",
                "classifiedTaggedItems",
                "unclassifiedTaggedItems");

        static final String TOP_UNCLASSIFIED_FAMILY_ACTIONS =
                descriptorValue(DESCRIPTORS, "topUnclassifiedFamilyActions");
        static final String MISSING_FACET_FAMILIES = descriptorValue(DESCRIPTORS, "missingFacetFamilies");
        static final String MISSING_SORT_KEY_FAMILIES =
                descriptorValue(DESCRIPTORS, "missingSortKeyFamilies");
        static final String BEFORE_PUBLIC_ITEMS = descriptorValue(DESCRIPTORS, "beforePublicItems");
        static final String TAGGED_ITEMS = descriptorValue(DESCRIPTORS, "taggedItems");
        static final String CLASSIFIED_TAGGED_ITEMS = descriptorValue(DESCRIPTORS, "classifiedTaggedItems");
        static final String UNCLASSIFIED_TAGGED_ITEMS =
                descriptorValue(DESCRIPTORS, "unclassifiedTaggedItems");

        private SemanticDiagnostics() {}
    }

    static final class BrowserAtlas {
        private static final List<FieldDescriptor> DESCRIPTORS = validateFieldDescriptors(
                "browser atlas evidence",
                Arrays.asList(
                        field("itemCount", "itemCount"),
                        field("animatedItemCount", "animatedItemCount"),
                        field("missingAtlasCount", "missingAtlasCount")),
                "itemCount",
                "animatedItemCount",
                "missingAtlasCount");

        static final String ITEM_COUNT = descriptorValue(DESCRIPTORS, "itemCount");
        static final String ANIMATED_ITEM_COUNT = descriptorValue(DESCRIPTORS, "animatedItemCount");
        static final String MISSING_ATLAS_COUNT = descriptorValue(DESCRIPTORS, "missingAtlasCount");

        private BrowserAtlas() {}
    }

    static final class RenderAsset {
        private static final List<FieldDescriptor> DESCRIPTORS = validateFieldDescriptors(
                "render asset evidence",
                Arrays.asList(
                        field("primaryArtifact", "primaryArtifact"),
                        field("staticFile", "staticFile"),
                        field("nativeSpriteAtlasFile", "nativeSpriteAtlasFile"),
                        field("assetId", "assetId"),
                        field("sourcePath", "sourcePath"),
                        field("variantKey", "variantKey"),
                        field("family", "family"),
                        field("sourceType", "sourceType"),
                        field("rendererFamily", "rendererFamily"),
                        field("captureMethod", "captureMethod"),
                        field("captureSource", "captureSource"),
                        field("animationMode", "animationMode"),
                        field("renderMode", "renderMode"),
                        field("frameCount", "frameCount"),
                        field("capturedFrameCount", "capturedFrameCount")),
                "primaryArtifact",
                "staticFile",
                "nativeSpriteAtlasFile",
                "assetId",
                "sourcePath",
                "variantKey",
                "family",
                "sourceType",
                "rendererFamily",
                "captureMethod",
                "captureSource",
                "animationMode",
                "renderMode",
                "frameCount",
                "capturedFrameCount");

        static final String PRIMARY_ARTIFACT = descriptorValue(DESCRIPTORS, "primaryArtifact");
        static final String STATIC_FILE = descriptorValue(DESCRIPTORS, "staticFile");
        static final String NATIVE_SPRITE_ATLAS_FILE = descriptorValue(DESCRIPTORS, "nativeSpriteAtlasFile");
        static final String ASSET_ID = descriptorValue(DESCRIPTORS, "assetId");
        static final String SOURCE_PATH = descriptorValue(DESCRIPTORS, "sourcePath");
        static final String VARIANT_KEY = descriptorValue(DESCRIPTORS, "variantKey");
        static final String FAMILY = descriptorValue(DESCRIPTORS, "family");
        static final String SOURCE_TYPE = descriptorValue(DESCRIPTORS, "sourceType");
        static final String RENDERER_FAMILY = descriptorValue(DESCRIPTORS, "rendererFamily");
        static final String CAPTURE_METHOD = descriptorValue(DESCRIPTORS, "captureMethod");
        static final String CAPTURE_SOURCE = descriptorValue(DESCRIPTORS, "captureSource");
        static final String ANIMATION_MODE = descriptorValue(DESCRIPTORS, "animationMode");
        static final String RENDER_MODE = descriptorValue(DESCRIPTORS, "renderMode");
        static final String FRAME_COUNT = descriptorValue(DESCRIPTORS, "frameCount");
        static final String CAPTURED_FRAME_COUNT = descriptorValue(DESCRIPTORS, "capturedFrameCount");

        private RenderAsset() {}
    }

    static final class RecipeAnomaly {
        private static final List<FieldDescriptor> DESCRIPTORS = validateFieldDescriptors(
                "recipe anomaly evidence",
                Arrays.asList(
                        field("handlersWithLoadedRecipes", "handlersWithLoadedRecipes"),
                        field("handlersWithExportedRecipes", "handlersWithExportedRecipes"),
                        field("suspiciousZeroExports", "suspiciousZeroExports"),
                        field("nativeCoveredZeroExports", "nativeCoveredZeroExports"),
                        field("nonRecipeInfoZeroExports", "nonRecipeInfoZeroExports"),
                        field("expectedEmptyHandlers", "expectedEmptyHandlers"),
                        field("legalZeroRecipeHandlers", "legalZeroRecipeHandlers"),
                        field("partialExports", "partialExports"),
                        field("duplicateCategoryRisks", "duplicateCategoryRisks")),
                "handlersWithLoadedRecipes",
                "handlersWithExportedRecipes",
                "suspiciousZeroExports",
                "nativeCoveredZeroExports",
                "nonRecipeInfoZeroExports",
                "expectedEmptyHandlers",
                "legalZeroRecipeHandlers",
                "partialExports",
                "duplicateCategoryRisks");

        static final String HANDLERS_WITH_LOADED_RECIPES =
                descriptorValue(DESCRIPTORS, "handlersWithLoadedRecipes");
        static final String HANDLERS_WITH_EXPORTED_RECIPES =
                descriptorValue(DESCRIPTORS, "handlersWithExportedRecipes");
        static final String SUSPICIOUS_ZERO_EXPORTS = descriptorValue(DESCRIPTORS, "suspiciousZeroExports");
        static final String NATIVE_COVERED_ZERO_EXPORTS =
                descriptorValue(DESCRIPTORS, "nativeCoveredZeroExports");
        static final String NON_RECIPE_INFO_ZERO_EXPORTS =
                descriptorValue(DESCRIPTORS, "nonRecipeInfoZeroExports");
        static final String EXPECTED_EMPTY_HANDLERS = descriptorValue(DESCRIPTORS, "expectedEmptyHandlers");
        static final String LEGAL_ZERO_RECIPE_HANDLERS =
                descriptorValue(DESCRIPTORS, "legalZeroRecipeHandlers");
        static final String PARTIAL_EXPORTS = descriptorValue(DESCRIPTORS, "partialExports");
        static final String DUPLICATE_CATEGORY_RISKS = descriptorValue(DESCRIPTORS, "duplicateCategoryRisks");

        private RecipeAnomaly() {}
    }

    private static FieldDescriptor field(String key, String value) {
        return new FieldDescriptor(key, value);
    }

    private static List<FieldDescriptor> validateFieldDescriptors(
            String label,
            List<FieldDescriptor> descriptors,
            String... expectedKeys) {
        if (descriptors == null) {
            throw new IllegalStateException(label + " descriptors must not be null");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedKeys));
        Set<String> seenKeys = new LinkedHashSet<String>();
        Set<String> seenValues = new LinkedHashSet<String>();
        for (FieldDescriptor descriptor : descriptors) {
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
        return Collections.unmodifiableList(new ArrayList<FieldDescriptor>(descriptors));
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

    private static String descriptorValue(List<FieldDescriptor> descriptors, String key) {
        for (FieldDescriptor descriptor : descriptors) {
            if (descriptor.key.equals(key)) {
                return descriptor.value;
            }
        }
        throw new IllegalStateException("Missing descriptor projection: " + key);
    }

    private static final class FieldDescriptor {
        final String key;
        final String value;

        FieldDescriptor(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
