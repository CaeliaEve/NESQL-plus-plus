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
        report.rawItems = ExportValidationJsonSupport.readLongMember(counts, "rawItems");
        report.rawRecipes = ExportValidationJsonSupport.readLongMember(counts, "rawRecipes");
        report.rawTextures = ExportValidationJsonSupport.readLongMember(counts, "rawTextures");
        report.rawAnimations = ExportValidationJsonSupport.readLongMember(counts, "rawAnimations");
        report.rawBrowserItems = ExportValidationJsonSupport.readLongMember(counts, "neiBrowserItems");
        report.rawBrowserGroups = ExportValidationJsonSupport.readLongMember(counts, "rawGroups");
        report.rawNeiOrderEntries = ExportValidationJsonSupport.readLongMember(counts, "rawNeiOrderEntries");
        report.rawNeiRuntimePanelItems = ExportValidationJsonSupport.readLongMember(counts, "neiRuntimePanelItems");
        report.rawNeiExportOnlyItems = ExportValidationJsonSupport.readLongMember(counts, "neiExportOnlyItems");
        report.rawNeiDefaultEntries = ExportValidationJsonSupport.readLongMember(counts, "neiDefaultEntries");
        report.rawNeiFallbackGroups = ExportValidationJsonSupport.readLongMember(counts, "neiFallbackGroups");
        report.rawNativeNeiGroups = ExportValidationJsonSupport.readLongMember(counts, "neiNativeGroups");
        report.rawNeiSyntheticGroups = ExportValidationJsonSupport.readLongMember(counts, "neiSyntheticGroups");
        report.rawGuidFilterRules = ExportValidationJsonSupport.readLongMember(counts, "neiGuidFilterRules");
        report.rawHiddenItemRules = ExportValidationJsonSupport.readLongMember(counts, "neiHiddenItemRules");
        report.rawHiddenItems = ExportValidationJsonSupport.readLongMember(counts, "neiHiddenItems");
        report.rawNeiRepresentativeMismatches = ExportValidationJsonSupport.readLongMember(counts, "neiRepresentativeMismatches");
        report.rawNeiHandlers = ExportValidationJsonSupport.readLongMember(counts, "neiHandlers");
        report.rawNeiHandlerLayouts = ExportValidationJsonSupport.readLongMember(counts, "neiHandlerLayouts");
        report.nativeUiLayouts = ExportValidationJsonSupport.readLongMember(counts, "nativeUiLayouts");
        report.nativeUiSlots = ExportValidationJsonSupport.readLongMember(counts, "nativeUiSlots");
        report.nativeUiRects = ExportValidationJsonSupport.readLongMember(counts, "nativeUiRects");
        report.nativeUiPrimitives = ExportValidationJsonSupport.readLongMember(counts, "nativeUiPrimitives");
        report.nativeUiMissingSurfaces = ExportValidationJsonSupport.readLongMember(counts, "nativeUiMissingSurfaces");
        report.nativeUiSlotBoundsViolations = ExportValidationJsonSupport.readLongMember(counts, "nativeUiSlotBoundsViolations");
        report.nativeUiRectBoundsViolations = ExportValidationJsonSupport.readLongMember(counts, "nativeUiRectBoundsViolations");
        report.nativeUiPrimitiveBoundsViolations = ExportValidationJsonSupport.readLongMember(counts, "nativeUiPrimitiveBoundsViolations");
        report.nativeUiBackgroundBoundsViolations = ExportValidationJsonSupport.readLongMember(counts, "nativeUiBackgroundBoundsViolations");
        report.nativeUiCoordinateContractViolations = ExportValidationJsonSupport.readLongMember(counts, "nativeUiCoordinateContractViolations");
        report.nativeUiInteractionContractViolations = ExportValidationJsonSupport.readLongMember(counts, "nativeUiInteractionContractViolations");
        report.rawRecipeTypes = ExportValidationJsonSupport.readLongMember(counts, "recipeTypes");
        report.renderBackendFacts = ExportValidationJsonSupport.readLongMember(counts, "renderBackendFacts");
        report.renderBackendAngelica = ExportValidationJsonSupport.readLongMember(counts, "renderBackendAngelica");
        report.renderTextureSprites = ExportValidationJsonSupport.readLongMember(counts, "renderTextureSprites");
        report.renderTextureSpritesMissingTiming = ExportValidationJsonSupport.readLongMember(counts, "renderTextureSpritesMissingTiming");
        report.renderItemRenderers = ExportValidationJsonSupport.readLongMember(counts, "renderItemRenderers");
        report.renderShaderItems = ExportValidationJsonSupport.readLongMember(counts, "renderShaderItems");
        report.renderShaderItemsRequiringCapture = ExportValidationJsonSupport.readLongMember(counts, "renderShaderItemsRequiringCapture");
        report.renderShaderItemsMissingCapture = ExportValidationJsonSupport.readLongMember(counts, "renderShaderItemsMissingCapture");
        report.renderUnknownSpecialRenderers = ExportValidationJsonSupport.readLongMember(counts, "renderUnknownSpecialRenderers");
        report.renderFramebufferCaptures = ExportValidationJsonSupport.readLongMember(counts, "renderFramebufferCaptures");
        report.renderFramebufferCapturesWithoutFrames = ExportValidationJsonSupport.readLongMember(counts, "renderFramebufferCapturesWithoutFrames");
        report.semanticTotalItems = ExportValidationJsonSupport.readLongMember(counts, "semanticTotalItems");
        report.semanticTaggedItems = ExportValidationJsonSupport.readLongMember(counts, "semanticTaggedItems");
        report.semanticClassifiedTaggedItems = ExportValidationJsonSupport.readLongMember(counts, "semanticClassifiedTaggedItems");
        report.semanticUnclassifiedTaggedItems = ExportValidationJsonSupport.readLongMember(counts, "semanticUnclassifiedTaggedItems");
        report.semanticFamilyCount = ExportValidationJsonSupport.readLongMember(counts, "semanticFamilyCount");
        report.semanticItems = ExportValidationJsonSupport.readLongMember(counts, "semanticItems");
        report.semanticVariants = ExportValidationJsonSupport.readLongMember(counts, "semanticVariants");
        report.semanticPayloads = ExportValidationJsonSupport.readLongMember(counts, "semanticPayloads");
        report.semanticIdentityMapRows = ExportValidationJsonSupport.readLongMember(counts, "semanticIdentityMapRows");
        report.semanticClassificationCoverageRatio = ExportValidationJsonSupport.ratio(
                report.semanticClassifiedTaggedItems,
                report.semanticTaggedItems);
    }
}
