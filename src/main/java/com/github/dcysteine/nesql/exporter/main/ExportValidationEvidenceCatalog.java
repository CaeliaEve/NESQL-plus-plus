package com.github.dcysteine.nesql.exporter.main;

/**
 * Stable ABI/catalog surface for validation evidence JSON member names and probe tokens.
 *
 * <p>Validation probes consume this catalog instead of copying raw-export report, semantic
 * diagnostics, browser atlas, render asset, checksum, or recipe-anomaly member literals.</p>
 */
final class ExportValidationEvidenceCatalog {
    static final int SEMANTIC_SAMPLE_LIMIT = 20;

    static final String OBJECT_COUNTS = "counts";
    static final String OBJECT_SUMMARY = "summary";
    static final String OBJECT_ITEMS = "items";
    static final String OBJECT_DEFAULT_ENTRIES = "defaultEntries";
    static final String OBJECT_STATIC_ATLAS = "staticAtlas";
    static final String OBJECT_ANIMATED_ATLAS = "animatedAtlas";
    static final String OBJECT_TIMELINE = "timeline";
    static final String OBJECT_FRAMES = "frames";

    static final String MEMBER_STATUS = "status";
    static final String MEMBER_GENERATED_AT = "generatedAt";
    static final String MEMBER_ASSET_HASH = "assetHash";
    static final String MEMBER_CHECKSUM = "checksum";
    static final String MEMBER_SHA256 = "sha256";
    static final String MEMBER_ITEM_ID = "itemId";
    static final String MEMBER_REPRESENTATIVE_ITEM_ID = "representativeItemId";
    static final String MEMBER_ATLAS_FILE = "atlasFile";
    static final String MEMBER_PATH = "path";

    static final String ERROR_FIELD_SCHEMA_VERSION = "schemaVersion";
    static final String ERROR_FIELD_GENERATED_AT = "generatedAt";
    static final String ERROR_FIELD_STAGE = "stage";
    static final String ERROR_FIELD_CODE = "code";
    static final String ERROR_FIELD_MESSAGE = "message";
    static final String ERROR_FIELD_FILE = "file";
    static final String ERROR_FIELD_LINE = "line";
    static final String ERROR_FIELD_RULE = "rule";

    static final String IMAGE_DIRECTORY_PREFIX = "image/";
    static final String ITEM_ID_SEPARATOR = "~";
    static final String ITEM_ID_ITEM_PREFIX = "i";
    static final String ITEM_ID_META_ZERO = "0";

    static final String SINGULARITY_TOKEN_SINGULARITY = "singularity";
    static final String SINGULARITY_TOKEN_SINGULARITIE = "singularitie";
    static final String SINGULARITY_TOKEN_ETERNAL = "eternalsingularity";
    static final String SINGULARITY_TOKEN_UNIVERSAL = "universalsingularity";
    static final String SINGULARITY_TOKEN_UNIVERSAL_UNDERSCORE = "universal_singularity";
    static final String SINGULARITY_TOKEN_AVARITIA = "avaritia";
    static final String SINGULARITY_TOKEN_COSMIC_NEUTRONIUM = "cosmicneutronium";
    static final String SINGULARITY_TOKEN_TRANSCENDENT_METAL = "transcendentmetal";
    static final String SINGULARITY_TOKEN_UNIVERSIUM = "universium";
    static final String ANIMATION_TOKEN_GIF = ".gif";
    static final String ANIMATION_TOKEN_ANIMATED = "animated";
    static final String ANIMATION_TOKEN_TIMELINE = "timeline";

    private ExportValidationEvidenceCatalog() {}

    static final class RawCount {
        static final String RAW_ITEMS = "rawItems";
        static final String RAW_RECIPES = "rawRecipes";
        static final String RAW_TEXTURES = "rawTextures";
        static final String RAW_ANIMATIONS = "rawAnimations";
        static final String NEI_BROWSER_ITEMS = "neiBrowserItems";
        static final String RAW_GROUPS = "rawGroups";
        static final String RAW_NEI_ORDER_ENTRIES = "rawNeiOrderEntries";
        static final String NEI_RUNTIME_PANEL_ITEMS = "neiRuntimePanelItems";
        static final String NEI_EXPORT_ONLY_ITEMS = "neiExportOnlyItems";
        static final String NEI_DEFAULT_ENTRIES = "neiDefaultEntries";
        static final String NEI_FALLBACK_GROUPS = "neiFallbackGroups";
        static final String NEI_NATIVE_GROUPS = "neiNativeGroups";
        static final String NEI_SYNTHETIC_GROUPS = "neiSyntheticGroups";
        static final String NEI_GUID_FILTER_RULES = "neiGuidFilterRules";
        static final String NEI_HIDDEN_ITEM_RULES = "neiHiddenItemRules";
        static final String NEI_HIDDEN_ITEMS = "neiHiddenItems";
        static final String NEI_REPRESENTATIVE_MISMATCHES = "neiRepresentativeMismatches";
        static final String NEI_HANDLERS = "neiHandlers";
        static final String NEI_HANDLER_LAYOUTS = "neiHandlerLayouts";
        static final String NATIVE_UI_LAYOUTS = "nativeUiLayouts";
        static final String NATIVE_UI_SLOTS = "nativeUiSlots";
        static final String NATIVE_UI_RECTS = "nativeUiRects";
        static final String NATIVE_UI_PRIMITIVES = "nativeUiPrimitives";
        static final String NATIVE_UI_MISSING_SURFACES = "nativeUiMissingSurfaces";
        static final String NATIVE_UI_SLOT_BOUNDS_VIOLATIONS = "nativeUiSlotBoundsViolations";
        static final String NATIVE_UI_RECT_BOUNDS_VIOLATIONS = "nativeUiRectBoundsViolations";
        static final String NATIVE_UI_PRIMITIVE_BOUNDS_VIOLATIONS = "nativeUiPrimitiveBoundsViolations";
        static final String NATIVE_UI_BACKGROUND_BOUNDS_VIOLATIONS = "nativeUiBackgroundBoundsViolations";
        static final String NATIVE_UI_COORDINATE_CONTRACT_VIOLATIONS = "nativeUiCoordinateContractViolations";
        static final String NATIVE_UI_INTERACTION_CONTRACT_VIOLATIONS = "nativeUiInteractionContractViolations";
        static final String RECIPE_TYPES = "recipeTypes";
        static final String RENDER_BACKEND_FACTS = "renderBackendFacts";
        static final String RENDER_BACKEND_ANGELICA = "renderBackendAngelica";
        static final String RENDER_TEXTURE_SPRITES = "renderTextureSprites";
        static final String RENDER_TEXTURE_SPRITES_MISSING_TIMING = "renderTextureSpritesMissingTiming";
        static final String RENDER_ITEM_RENDERERS = "renderItemRenderers";
        static final String RENDER_SHADER_ITEMS = "renderShaderItems";
        static final String RENDER_SHADER_ITEMS_REQUIRING_CAPTURE = "renderShaderItemsRequiringCapture";
        static final String RENDER_SHADER_ITEMS_MISSING_CAPTURE = "renderShaderItemsMissingCapture";
        static final String RENDER_UNKNOWN_SPECIAL_RENDERERS = "renderUnknownSpecialRenderers";
        static final String RENDER_FRAMEBUFFER_CAPTURES = "renderFramebufferCaptures";
        static final String RENDER_FRAMEBUFFER_CAPTURES_WITHOUT_FRAMES = "renderFramebufferCapturesWithoutFrames";
        static final String SEMANTIC_TOTAL_ITEMS = "semanticTotalItems";
        static final String SEMANTIC_TAGGED_ITEMS = "semanticTaggedItems";
        static final String SEMANTIC_CLASSIFIED_TAGGED_ITEMS = "semanticClassifiedTaggedItems";
        static final String SEMANTIC_UNCLASSIFIED_TAGGED_ITEMS = "semanticUnclassifiedTaggedItems";
        static final String SEMANTIC_FAMILY_COUNT = "semanticFamilyCount";
        static final String SEMANTIC_ITEMS = "semanticItems";
        static final String SEMANTIC_VARIANTS = "semanticVariants";
        static final String SEMANTIC_PAYLOADS = "semanticPayloads";
        static final String SEMANTIC_IDENTITY_MAP_ROWS = "semanticIdentityMapRows";

        private RawCount() {}
    }

    static final class SemanticDiagnostics {
        static final String TOP_UNCLASSIFIED_FAMILY_ACTIONS = "topUnclassifiedFamilyActions";
        static final String MISSING_FACET_FAMILIES = "missingFacetFamilies";
        static final String MISSING_SORT_KEY_FAMILIES = "missingSortKeyFamilies";
        static final String BEFORE_PUBLIC_ITEMS = "beforePublicItems";
        static final String TAGGED_ITEMS = "taggedItems";
        static final String CLASSIFIED_TAGGED_ITEMS = "classifiedTaggedItems";
        static final String UNCLASSIFIED_TAGGED_ITEMS = "unclassifiedTaggedItems";

        private SemanticDiagnostics() {}
    }

    static final class BrowserAtlas {
        static final String ITEM_COUNT = "itemCount";
        static final String ANIMATED_ITEM_COUNT = "animatedItemCount";
        static final String MISSING_ATLAS_COUNT = "missingAtlasCount";

        private BrowserAtlas() {}
    }

    static final class RenderAsset {
        static final String PRIMARY_ARTIFACT = "primaryArtifact";
        static final String STATIC_FILE = "staticFile";
        static final String NATIVE_SPRITE_ATLAS_FILE = "nativeSpriteAtlasFile";
        static final String ASSET_ID = "assetId";
        static final String SOURCE_PATH = "sourcePath";
        static final String VARIANT_KEY = "variantKey";
        static final String FAMILY = "family";
        static final String SOURCE_TYPE = "sourceType";
        static final String RENDERER_FAMILY = "rendererFamily";
        static final String CAPTURE_METHOD = "captureMethod";
        static final String CAPTURE_SOURCE = "captureSource";
        static final String ANIMATION_MODE = "animationMode";
        static final String RENDER_MODE = "renderMode";
        static final String FRAME_COUNT = "frameCount";
        static final String CAPTURED_FRAME_COUNT = "capturedFrameCount";

        private RenderAsset() {}
    }

    static final class RecipeAnomaly {
        static final String HANDLERS_WITH_LOADED_RECIPES = "handlersWithLoadedRecipes";
        static final String HANDLERS_WITH_EXPORTED_RECIPES = "handlersWithExportedRecipes";
        static final String SUSPICIOUS_ZERO_EXPORTS = "suspiciousZeroExports";
        static final String NATIVE_COVERED_ZERO_EXPORTS = "nativeCoveredZeroExports";
        static final String NON_RECIPE_INFO_ZERO_EXPORTS = "nonRecipeInfoZeroExports";
        static final String EXPECTED_EMPTY_HANDLERS = "expectedEmptyHandlers";
        static final String LEGAL_ZERO_RECIPE_HANDLERS = "legalZeroRecipeHandlers";
        static final String PARTIAL_EXPORTS = "partialExports";
        static final String DUPLICATE_CATEGORY_RISKS = "duplicateCategoryRisks";

        private RecipeAnomaly() {}
    }
}
