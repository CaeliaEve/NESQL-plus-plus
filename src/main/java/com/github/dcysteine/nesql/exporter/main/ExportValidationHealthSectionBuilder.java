package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.google.gson.JsonObject;

import java.io.File;

/** Builds aggregate health sections after validation policy evaluation. */
final class ExportValidationHealthSectionBuilder {
    private ExportValidationHealthSectionBuilder() {}

    static void populate(File rawDir, ExportValidationReport report) {
        report.itemTotals = new ExportValidationReport.ItemTotals();
        report.itemTotals.rawItems = report.rawItems;
        report.itemTotals.browserItems = report.rawBrowserItems;
        report.itemTotals.hiddenItems = report.rawHiddenItems;
        report.itemTotals.recipeOnlyItems = Math.max(0L, report.rawItems - report.rawBrowserItems);
        report.itemTotals.neiRuntimePanelItems = report.rawNeiRuntimePanelItems;
        report.itemTotals.neiExportOnlyItems = report.rawNeiExportOnlyItems;

        report.browserGroupTotals = new ExportValidationReport.BrowserGroupTotals();
        report.browserGroupTotals.groups = report.rawBrowserGroups;
        report.browserGroupTotals.nativeNeiGroups = report.rawNativeNeiGroups;
        report.browserGroupTotals.guidFilterRules = report.rawGuidFilterRules;
        report.browserGroupTotals.hiddenItemRules = report.rawHiddenItemRules;
        report.browserGroupTotals.semanticGroups = Math.max(0L,
                report.rawBrowserGroups - report.rawNativeNeiGroups - report.rawNeiFallbackGroups - report.rawNeiSyntheticGroups);
        report.browserGroupTotals.fallbackGroups = report.rawNeiFallbackGroups;
        report.browserGroupTotals.syntheticGroups = report.rawNeiSyntheticGroups;
        report.browserGroupTotals.defaultEntries = report.rawNeiDefaultEntries;
        report.browserGroupTotals.orderEntries = report.rawNeiOrderEntries;
        report.browserGroupTotals.representativeMismatches = report.rawNeiRepresentativeMismatches;

        report.textureTotals = new ExportValidationReport.TextureTotals();
        report.textureTotals.textures = report.rawTextures;
        report.textureTotals.staticAtlasFiles = report.staticAtlasPngFiles;
        report.textureTotals.atlasManifestAssets = report.staticAtlasManifestAssets;
        report.textureTotals.missingTextures = report.renderAssetMissingPrimaryArtifacts;
        report.textureTotals.missingAtlasEntries = report.browserAtlasLayoutMissingItems;
        report.textureTotals.wrongRepresentativeTextureRisks = report.rawNeiRepresentativeMismatches;
        report.textureTotals.browserAtlasItems = report.browserAtlasItems;
        report.textureTotals.browserAtlasDrawableItems = report.browserAtlasDrawableItems;

        report.animationTotals = new ExportValidationReport.AnimationTotals();
        report.animationTotals.animations = report.rawAnimations;
        report.animationTotals.animatedAtlasFiles = report.animatedAtlasPngFiles;
        report.animationTotals.animatedAtlasManifestAssets = report.animatedAtlasManifestAssets;
        report.animationTotals.missingAnimatedAtlasEntries = Math.max(0,
                report.animatedAtlasManifestAssets - report.browserAtlasAnimatedItems);
        report.animationTotals.missingTimingData = ExportValidationJsonSupport.readLongMember(
                ExportValidationJsonSupport.readCountsObject(RawExportFileCatalog.rawExportFile(
                        rawDir,
                        RawExportFileCatalog.EXPORT_REPORT_FILE)),
                ExportValidationEvidenceCatalog.RawCount.RENDER_TEXTURE_SPRITES_MISSING_TIMING);
        report.animationTotals.staticWhenAnimationExpected = report.suspiciousStaticSingularityAssets;
        report.animationTotals.singularityLikeAssets = report.singularityLikeRenderAssets;
        report.animationTotals.animatedSingularityLikeAssets = report.animatedSingularityLikeRenderAssets;

        report.nativeRenderTotals = new ExportValidationReport.NativeRenderTotals();
        report.nativeRenderTotals.backendFacts = report.renderBackendFacts;
        report.nativeRenderTotals.backendAngelica = report.renderBackendAngelica;
        report.nativeRenderTotals.textureSprites = report.renderTextureSprites;
        report.nativeRenderTotals.textureSpritesMissingTiming = report.renderTextureSpritesMissingTiming;
        report.nativeRenderTotals.itemRenderers = report.renderItemRenderers;
        report.nativeRenderTotals.shaderItems = report.renderShaderItems;
        report.nativeRenderTotals.shaderItemsRequiringCapture = report.renderShaderItemsRequiringCapture;
        report.nativeRenderTotals.shaderItemsMissingCapture = report.renderShaderItemsMissingCapture;
        report.nativeRenderTotals.unknownSpecialRenderers = report.renderUnknownSpecialRenderers;
        report.nativeRenderTotals.framebufferCaptures = report.renderFramebufferCaptures;
        report.nativeRenderTotals.framebufferCapturesWithoutFrames = report.renderFramebufferCapturesWithoutFrames;
        report.nativeRenderTotals.captureCompletenessRatio = ExportValidationJsonSupport.ratio(
                report.renderShaderItemsRequiringCapture - report.renderShaderItemsMissingCapture,
                report.renderShaderItemsRequiringCapture);
        report.nativeRenderTotals.status = report.renderBackendAngelica == 1L
                && report.renderTextureSpritesMissingTiming == 0L
                && report.renderUnknownSpecialRenderers == 0L
                && report.renderShaderItemsMissingCapture == 0L
                && report.renderFramebufferCapturesWithoutFrames == 0L
                ? ExportValidationAbiCatalog.STATUS_OK
                : ExportValidationAbiCatalog.STATUS_WARNING;

        report.recipeTotals = new ExportValidationReport.RecipeTotals();
        report.recipeTotals.recipes = report.rawRecipes;
        report.recipeTotals.recipeTypes = report.rawRecipeTypes;
        report.recipeTotals.neiHandlers = report.rawNeiHandlers;
        report.recipeTotals.neiHandlerLayouts = report.rawNeiHandlerLayouts;
        inspectRecipeHandlerAnomalies(rawDir, report.recipeTotals);

        report.nativeUiTotals = new ExportValidationReport.NativeUiTotals();
        report.nativeUiTotals.layouts = report.nativeUiLayouts;
        report.nativeUiTotals.slots = report.nativeUiSlots;
        report.nativeUiTotals.rects = report.nativeUiRects;
        report.nativeUiTotals.primitives = report.nativeUiPrimitives;
        report.nativeUiTotals.missingSurfaces = report.nativeUiMissingSurfaces;
        report.nativeUiTotals.slotBoundsViolations = report.nativeUiSlotBoundsViolations;
        report.nativeUiTotals.rectBoundsViolations = report.nativeUiRectBoundsViolations;
        report.nativeUiTotals.primitiveBoundsViolations = report.nativeUiPrimitiveBoundsViolations;
        report.nativeUiTotals.backgroundBoundsViolations = report.nativeUiBackgroundBoundsViolations;
        report.nativeUiTotals.coordinateContractViolations = report.nativeUiCoordinateContractViolations;
        report.nativeUiTotals.interactionContractViolations = report.nativeUiInteractionContractViolations;
        report.nativeUiTotals.status = report.nativeUiLayouts > 0L
                && report.nativeUiSlots > 0L
                && report.nativeUiMissingSurfaces == 0L
                && report.nativeUiSlotBoundsViolations == 0L
                && report.nativeUiRectBoundsViolations == 0L
                && report.nativeUiPrimitiveBoundsViolations == 0L
                && report.nativeUiBackgroundBoundsViolations == 0L
                && report.nativeUiCoordinateContractViolations == 0L
                && report.nativeUiInteractionContractViolations == 0L
                ? ExportValidationAbiCatalog.STATUS_OK
                : ExportValidationAbiCatalog.STATUS_BLOCKED;

        report.runtimeManifestMetadata = new ExportValidationReport.RuntimeManifestMetadata();
        report.runtimeManifestMetadata.gtnhProfile = report.profile;
        report.runtimeManifestMetadata.exportRepository = report.repository;
        report.runtimeManifestMetadata.exportSelection = report.selection;
        report.runtimeManifestMetadata.exporterSchemaVersion = report.schemaVersion;
        report.runtimeManifestMetadata.exportTimestamp = ExportValidationJsonSupport.readStringMember(
                ExportValidationJsonSupport.readJsonObject(RawExportFileCatalog.rawExportFile(
                        rawDir,
                        RawExportFileCatalog.MANIFEST_FILE)),
                ExportValidationEvidenceCatalog.MEMBER_GENERATED_AT);
        report.runtimeManifestMetadata.healthStatus = report.healthStatus;
        report.runtimeManifestMetadata.compileReadinessStatus = report.compileReadinessStatus;
        report.runtimeManifestMetadata.assetHash = readExportAssetHash(rawDir);
    }

    private static void inspectRecipeHandlerAnomalies(
            File rawDir,
            ExportValidationReport.RecipeTotals totals) {
        JsonObject root = ExportValidationJsonSupport.readJsonObject(RawExportFileCatalog.rawExportFile(
                rawDir,
                RawExportFileCatalog.NEI_HANDLER_ANOMALIES_FILE));
        if (root == null || !root.has(ExportValidationEvidenceCatalog.OBJECT_SUMMARY) || !root.get(ExportValidationEvidenceCatalog.OBJECT_SUMMARY).isJsonObject()) {
            return;
        }
        JsonObject summary = root.getAsJsonObject(ExportValidationEvidenceCatalog.OBJECT_SUMMARY);
        totals.handlersWithLoadedRecipes = ExportValidationJsonSupport.readLongMember(summary, ExportValidationEvidenceCatalog.RecipeAnomaly.HANDLERS_WITH_LOADED_RECIPES);
        totals.handlersWithExportedRecipes = ExportValidationJsonSupport.readLongMember(summary, ExportValidationEvidenceCatalog.RecipeAnomaly.HANDLERS_WITH_EXPORTED_RECIPES);
        totals.suspiciousZeroRecipeHandlers = ExportValidationJsonSupport.readLongMember(summary, ExportValidationEvidenceCatalog.RecipeAnomaly.SUSPICIOUS_ZERO_EXPORTS);
        totals.nativeCoveredZeroRecipeHandlers = ExportValidationJsonSupport.readLongMember(summary, ExportValidationEvidenceCatalog.RecipeAnomaly.NATIVE_COVERED_ZERO_EXPORTS);
        totals.nonRecipeInfoZeroRecipeHandlers = ExportValidationJsonSupport.readLongMember(summary, ExportValidationEvidenceCatalog.RecipeAnomaly.NON_RECIPE_INFO_ZERO_EXPORTS);
        totals.expectedEmptyHandlers = ExportValidationJsonSupport.readLongMember(summary, ExportValidationEvidenceCatalog.RecipeAnomaly.EXPECTED_EMPTY_HANDLERS);
        totals.legalZeroRecipeHandlers = ExportValidationJsonSupport.readLongMember(summary, ExportValidationEvidenceCatalog.RecipeAnomaly.LEGAL_ZERO_RECIPE_HANDLERS);
        totals.partialExports = ExportValidationJsonSupport.readLongMember(summary, ExportValidationEvidenceCatalog.RecipeAnomaly.PARTIAL_EXPORTS);
        totals.duplicateCategoryRisks = ExportValidationJsonSupport.readLongMember(summary, ExportValidationEvidenceCatalog.RecipeAnomaly.DUPLICATE_CATEGORY_RISKS);
        totals.zeroRecipeStatus = ExportValidationJsonSupport.readStringMember(summary, ExportValidationEvidenceCatalog.MEMBER_STATUS);
    }

    private static String readExportAssetHash(File rawDir) {
        JsonObject root = ExportValidationJsonSupport.readJsonObject(RawExportFileCatalog.rawExportFile(
                rawDir,
                RawExportFileCatalog.validationPath(RawExportFileCatalog.STAGE_CHECKSUMS_FILE_NAME)));
        if (root == null) {
            return null;
        }
        String direct = ExportValidationJsonSupport.readStringMember(root, ExportValidationEvidenceCatalog.MEMBER_ASSET_HASH);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        String checksum = ExportValidationJsonSupport.readStringMember(root, ExportValidationEvidenceCatalog.MEMBER_CHECKSUM);
        if (checksum != null && !checksum.isEmpty()) {
            return checksum;
        }
        return ExportValidationJsonSupport.readStringMember(root, ExportValidationEvidenceCatalog.MEMBER_SHA256);
    }
}
