package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.JsonObject;

import java.io.File;

/** Owns raw-export report count extraction for validation reports. */
final class ExportValidationRawCountProbe {
    private ExportValidationRawCountProbe() {}

    static void inspect(File repositoryDirectory, ExportValidationReportWriter.ValidationReport report) {
        File reportFile = RawExportFileCatalog.rawExportFile(
                RawExportFileCatalog.rawExportDirectory(repositoryDirectory),
                RawExportFileCatalog.EXPORT_REPORT_FILE);
        JsonObject counts = ExportValidationJsonSupport.readCountsObject(reportFile);
        if (counts == null) {
            return;
        }
        report.rawItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RAW_ITEMS);
        report.rawRecipes = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RAW_RECIPES);
        report.rawTextures = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RAW_TEXTURES);
        report.rawAnimations = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RAW_ANIMATIONS);
        report.rawBrowserItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_BROWSER_ITEMS);
        report.rawBrowserGroups = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RAW_GROUPS);
        report.rawNeiOrderEntries = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RAW_NEI_ORDER_ENTRIES);
        report.rawNeiRuntimePanelItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_RUNTIME_PANEL_ITEMS);
        report.rawNeiExportOnlyItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_EXPORT_ONLY_ITEMS);
        report.rawNeiDefaultEntries = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_DEFAULT_ENTRIES);
        report.rawNeiFallbackGroups = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_FALLBACK_GROUPS);
        report.rawNativeNeiGroups = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_NATIVE_GROUPS);
        report.rawNeiSyntheticGroups = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_SYNTHETIC_GROUPS);
        report.rawGuidFilterRules = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_GUID_FILTER_RULES);
        report.rawHiddenItemRules = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_HIDDEN_ITEM_RULES);
        report.rawHiddenItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_HIDDEN_ITEMS);
        report.rawNeiRepresentativeMismatches = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_REPRESENTATIVE_MISMATCHES);
        report.rawNeiHandlers = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_HANDLERS);
        report.rawNeiHandlerLayouts = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NEI_HANDLER_LAYOUTS);
        report.nativeUiLayouts = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_LAYOUTS);
        report.nativeUiSlots = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_SLOTS);
        report.nativeUiRects = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_RECTS);
        report.nativeUiPrimitives = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_PRIMITIVES);
        report.nativeUiMissingSurfaces = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_MISSING_SURFACES);
        report.nativeUiSlotBoundsViolations = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_SLOT_BOUNDS_VIOLATIONS);
        report.nativeUiRectBoundsViolations = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_RECT_BOUNDS_VIOLATIONS);
        report.nativeUiPrimitiveBoundsViolations = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_PRIMITIVE_BOUNDS_VIOLATIONS);
        report.nativeUiBackgroundBoundsViolations = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_BACKGROUND_BOUNDS_VIOLATIONS);
        report.nativeUiCoordinateContractViolations = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_COORDINATE_CONTRACT_VIOLATIONS);
        report.nativeUiInteractionContractViolations = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.NATIVE_UI_INTERACTION_CONTRACT_VIOLATIONS);
        report.rawRecipeTypes = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RECIPE_TYPES);
        report.renderBackendFacts = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_BACKEND_FACTS);
        report.renderBackendAngelica = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_BACKEND_ANGELICA);
        report.renderTextureSprites = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_TEXTURE_SPRITES);
        report.renderTextureSpritesMissingTiming = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_TEXTURE_SPRITES_MISSING_TIMING);
        report.renderItemRenderers = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_ITEM_RENDERERS);
        report.renderShaderItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_SHADER_ITEMS);
        report.renderShaderItemsRequiringCapture = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_SHADER_ITEMS_REQUIRING_CAPTURE);
        report.renderShaderItemsMissingCapture = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_SHADER_ITEMS_MISSING_CAPTURE);
        report.renderUnknownSpecialRenderers = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_UNKNOWN_SPECIAL_RENDERERS);
        report.renderFramebufferCaptures = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_FRAMEBUFFER_CAPTURES);
        report.renderFramebufferCapturesWithoutFrames = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.RENDER_FRAMEBUFFER_CAPTURES_WITHOUT_FRAMES);
        report.semanticTotalItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.SEMANTIC_TOTAL_ITEMS);
        report.semanticTaggedItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.SEMANTIC_TAGGED_ITEMS);
        report.semanticClassifiedTaggedItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.SEMANTIC_CLASSIFIED_TAGGED_ITEMS);
        report.semanticUnclassifiedTaggedItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.SEMANTIC_UNCLASSIFIED_TAGGED_ITEMS);
        report.semanticFamilyCount = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.SEMANTIC_FAMILY_COUNT);
        report.semanticItems = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.SEMANTIC_ITEMS);
        report.semanticVariants = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.SEMANTIC_VARIANTS);
        report.semanticPayloads = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.SEMANTIC_PAYLOADS);
        report.semanticIdentityMapRows = ExportValidationJsonSupport.readLongMember(counts, ExportValidationEvidenceCatalog.RawCount.SEMANTIC_IDENTITY_MAP_ROWS);
        report.semanticClassificationCoverageRatio = ExportValidationJsonSupport.ratio(
                report.semanticClassifiedTaggedItems,
                report.semanticTaggedItems);
    }
}
