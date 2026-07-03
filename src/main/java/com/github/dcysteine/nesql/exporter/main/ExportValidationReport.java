package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Stable schema model for export validation and health reports. */
final class ExportValidationReport {
    String schemaVersion;
    String healthStatus = "unknown";
    String compileReadinessStatus = "unknown";
    String repository;
    String profile;
    String selection;
    int validationProbeCount;
    List<ExportValidationProbeDescriptor> validationProbes = new ArrayList<ExportValidationProbeDescriptor>();
    int itemsJsonGzFiles;
    int recipeJsonGzFiles;
    int imagePngFiles;
    int imageGifFiles;
    int renderJsonFiles;
    int spriteJsonFiles;
    int staticAtlasPngFiles;
    int animatedAtlasPngFiles;
    int staticAtlasManifestAssets;
    int animatedAtlasManifestAssets;
    int totalAtlasManifestAssets;
    Double atlasManifestCoverageRatio;
    int renderAssetManifestAssets;
    boolean renderAssetManifestPresent;
    boolean browserLayoutPresent;
    int renderAssetMissingPrimaryArtifacts;
    List<String> renderAssetMissingPrimaryArtifactSamples = new ArrayList<String>();
    int renderAssetMissingTimelineFrames;
    List<String> renderAssetMissingTimelineFrameSamples = new ArrayList<String>();
    int browserLayoutEntries;
    int browserLayoutItemCount;
    int browserLayoutGroupCount;
    int browserLayoutDefaultEntryCount;
    boolean browserAtlasPresent;
    int browserAtlasItems;
    int browserAtlasDrawableItems;
    int browserAtlasAnimatedItems;
    int browserAtlasMissingAtlasCount;
    int browserAtlasLayoutItemCount;
    int browserAtlasLayoutCoveredItems;
    int browserAtlasLayoutMissingItems;
    Double browserAtlasLayoutCoverageRatio;
    List<String> browserAtlasLayoutMissingSamples = new ArrayList<String>();
    int multiblockBlueprints;
    int entityPreviewEntries;
    int entityModelEntries;
    int singularityLikeRenderAssets;
    int animatedSingularityLikeRenderAssets;
    int suspiciousStaticSingularityAssets;
    List<String> suspiciousStaticSingularitySamples = new ArrayList<String>();
    String exportPathHygieneStatus = "unknown";
    int exportPathHygieneAuditedFiles;
    int exportPathHygieneViolations;
    List<PathHygieneSample> exportPathHygieneSamples = new ArrayList<PathHygieneSample>();
    long rawItems;
    long rawRecipes;
    long rawTextures;
    long rawAnimations;
    long rawBrowserItems;
    long rawBrowserGroups;
    long rawNeiOrderEntries;
    long rawNeiRuntimePanelItems;
    long rawNeiExportOnlyItems;
    long rawNeiDefaultEntries;
    long rawNeiFallbackGroups;
    long rawNativeNeiGroups;
    long rawNeiSyntheticGroups;
    long rawGuidFilterRules;
    long rawHiddenItemRules;
    long rawHiddenItems;
    long rawNeiRepresentativeMismatches;
    long rawNeiHandlers;
    long rawNeiHandlerLayouts;
    long nativeUiLayouts;
    long nativeUiSlots;
    long nativeUiRects;
    long nativeUiPrimitives;
    long nativeUiMissingSurfaces;
    long nativeUiSlotBoundsViolations;
    long nativeUiRectBoundsViolations;
    long nativeUiPrimitiveBoundsViolations;
    long nativeUiBackgroundBoundsViolations;
    long nativeUiCoordinateContractViolations;
    long nativeUiInteractionContractViolations;
    long rawRecipeTypes;
    long renderBackendFacts;
    long renderBackendAngelica;
    long renderTextureSprites;
    long renderTextureSpritesMissingTiming;
    long renderItemRenderers;
    long renderShaderItems;
    long renderShaderItemsRequiringCapture;
    long renderShaderItemsMissingCapture;
    long renderUnknownSpecialRenderers;
    long renderFramebufferCaptures;
    long renderFramebufferCapturesWithoutFrames;
    boolean semanticDiagnosticsPresent;
    long semanticTotalItems;
    long semanticTaggedItems;
    long semanticClassifiedTaggedItems;
    long semanticUnclassifiedTaggedItems;
    long semanticFamilyCount;
    long semanticItems;
    long semanticVariants;
    long semanticPayloads;
    long semanticIdentityMapRows;
    Double semanticClassificationCoverageRatio;
    int semanticMissingFacetFamilyCount;
    int semanticMissingSortKeyFamilyCount;
    JsonArray semanticTopUnclassifiedFamilyActions = new JsonArray();
    JsonArray semanticMissingFacetFamilies = new JsonArray();
    JsonArray semanticMissingSortKeyFamilies = new JsonArray();
    SemanticRulePack.Validation semanticRulePack;
    ItemTotals itemTotals;
    BrowserGroupTotals browserGroupTotals;
    TextureTotals textureTotals;
    AnimationTotals animationTotals;
    NativeRenderTotals nativeRenderTotals;
    NativeUiTotals nativeUiTotals;
    RecipeTotals recipeTotals;
    RuntimeManifestMetadata runtimeManifestMetadata;
    List<String> blockedIssues = new ArrayList<String>();
    List<JsonObject> actionableIssues = new ArrayList<JsonObject>();
    PreviousSnapshot previous;
    DeltaSnapshot delta;
    List<String> warnings = new ArrayList<String>();

    static final class ItemTotals {
        long rawItems;
        long browserItems;
        long hiddenItems;
        long recipeOnlyItems;
        long neiRuntimePanelItems;
        long neiExportOnlyItems;
    }

    static final class BrowserGroupTotals {
        long groups;
        long nativeNeiGroups;
        long guidFilterRules;
        long hiddenItemRules;
        long semanticGroups;
        long fallbackGroups;
        long syntheticGroups;
        long defaultEntries;
        long orderEntries;
        long representativeMismatches;
    }

    static final class TextureTotals {
        long textures;
        int staticAtlasFiles;
        int atlasManifestAssets;
        int missingTextures;
        int missingAtlasEntries;
        long wrongRepresentativeTextureRisks;
        int browserAtlasItems;
        int browserAtlasDrawableItems;
    }

    static final class AnimationTotals {
        long animations;
        int animatedAtlasFiles;
        int animatedAtlasManifestAssets;
        int missingAnimatedAtlasEntries;
        long missingTimingData;
        int staticWhenAnimationExpected;
        int singularityLikeAssets;
        int animatedSingularityLikeAssets;
    }

    static final class NativeRenderTotals {
        long backendFacts;
        long backendAngelica;
        long textureSprites;
        long textureSpritesMissingTiming;
        long itemRenderers;
        long shaderItems;
        long shaderItemsRequiringCapture;
        long shaderItemsMissingCapture;
        long unknownSpecialRenderers;
        long framebufferCaptures;
        long framebufferCapturesWithoutFrames;
        Double captureCompletenessRatio;
        String status;
    }

    static final class RecipeTotals {
        long recipes;
        long recipeTypes;
        long neiHandlers;
        long neiHandlerLayouts;
        long handlersWithLoadedRecipes;
        long handlersWithExportedRecipes;
        long suspiciousZeroRecipeHandlers;
        long nativeCoveredZeroRecipeHandlers;
        long nonRecipeInfoZeroRecipeHandlers;
        long expectedEmptyHandlers;
        long legalZeroRecipeHandlers;
        long partialExports;
        long duplicateCategoryRisks;
        String zeroRecipeStatus;
    }

    static final class NativeUiTotals {
        long layouts;
        long slots;
        long rects;
        long primitives;
        long missingSurfaces;
        long slotBoundsViolations;
        long rectBoundsViolations;
        long primitiveBoundsViolations;
        long backgroundBoundsViolations;
        long coordinateContractViolations;
        long interactionContractViolations;
        String status;
    }

    static final class RuntimeManifestMetadata {
        String gtnhProfile;
        String exportRepository;
        String exportSelection;
        String exporterSchemaVersion;
        String exportTimestamp;
        String healthStatus;
        String compileReadinessStatus;
        String assetHash;
    }

    static final class PathHygieneSample {
        String file;
        int line;
        String rule;
        String text;
    }

    static class PreviousSnapshot {
        int itemsJsonGzFiles;
        int recipeJsonGzFiles;
        int imagePngFiles;
        int imageGifFiles;
        int staticAtlasManifestAssets;
        int animatedAtlasManifestAssets;
    }

    static final class DeltaSnapshot extends PreviousSnapshot {}
}
