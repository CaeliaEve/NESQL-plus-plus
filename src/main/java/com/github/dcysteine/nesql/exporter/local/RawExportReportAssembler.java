package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;

final class RawExportReportAssembler {
    private RawExportReportAssembler() {}

    static void apply(
            RawExportReport report,
            RawFactCounts factCounts,
            SemanticRulePack.RuntimeMetadata semanticRuleRuntime,
            SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary semanticAudit) {
        report.semanticRuleRuntime = semanticRuleRuntime;
        report.counts.rawItems = factCounts.items;
        report.counts.rawFluids = factCounts.fluids;
        report.counts.rawRecipes = factCounts.recipes;
        report.counts.rawGroups = factCounts.groups;
        report.counts.rawNeiOrderEntries = factCounts.neiOrderEntries;
        report.counts.neiRuntimePanelItems = factCounts.neiRuntimePanelItems;
        report.counts.neiExportOnlyItems = factCounts.neiExportOnlyItems;
        report.counts.neiBrowserItems = factCounts.neiBrowserItems;
        report.counts.neiDefaultEntries = factCounts.neiDefaultEntries;
        report.counts.neiFallbackGroups = factCounts.neiFallbackGroups;
        report.counts.neiNativeGroups = factCounts.neiNativeGroups;
        report.counts.neiSyntheticGroups = factCounts.neiSyntheticGroups;
        report.counts.neiGuidFilterRules = factCounts.neiGuidFilterRules;
        report.counts.neiHiddenItemRules = factCounts.neiHiddenItemRules;
        report.counts.neiHiddenItems = factCounts.neiHiddenItems;
        report.counts.neiRepresentativeMismatches = factCounts.neiRepresentativeMismatches;
        report.counts.neiHandlers = factCounts.neiHandlers;
        report.counts.neiHandlerLayouts = factCounts.neiHandlerLayouts;
        report.counts.uiFamilyCensusHandlers = factCounts.uiFamilyCensusHandlers;
        report.counts.uiFamilyCensusFamilies = factCounts.uiFamilyCensusFamilies;
        report.counts.uiTemplateCatalogHandlers = factCounts.uiTemplateCatalogHandlers;
        report.counts.uiTemplateCatalogTemplates = factCounts.uiTemplateCatalogTemplates;
        report.counts.uiTemplateCatalogFamilies = factCounts.uiTemplateCatalogFamilies;
        report.counts.rawTextures = factCounts.textures;
        report.counts.rawAnimations = factCounts.animations;
        report.counts.rawEntities = factCounts.entities;
        report.counts.rawBrowserAtlasAssets = factCounts.browserAtlasAssets;
        report.counts.renderBackendFacts = factCounts.renderBackendFacts;
        report.counts.renderBackendAngelica = factCounts.renderBackendAngelica;
        report.counts.renderTextureSprites = factCounts.renderTextureSprites;
        report.counts.renderTextureSpritesMissingTiming = factCounts.renderTextureSpritesMissingTiming;
        report.counts.renderItemRenderers = factCounts.renderItemRenderers;
        report.counts.renderShaderItems = factCounts.renderShaderItems;
        report.counts.renderShaderItemsRequiringCapture = factCounts.renderShaderItemsRequiringCapture;
        report.counts.renderShaderItemsMissingCapture = factCounts.renderShaderItemsMissingCapture;
        report.counts.renderShaderItemsMissingCaptureSamples = factCounts.renderShaderItemsMissingCaptureSamples;
        report.counts.renderUnknownSpecialRenderers = factCounts.renderUnknownSpecialRenderers;
        report.counts.renderFramebufferCaptures = factCounts.renderFramebufferCaptures;
        report.counts.renderFramebufferCapturesWithoutFrames = factCounts.renderFramebufferCapturesWithoutFrames;
        report.counts.renderFramebufferCapturesWithoutFramesSamples = factCounts.renderFramebufferCapturesWithoutFramesSamples;
        report.counts.semanticTotalItems = semanticAudit.totalItems;
        report.counts.semanticTaggedItems = semanticAudit.taggedItems;
        report.counts.semanticClassifiedTaggedItems = semanticAudit.classifiedTaggedItems;
        report.counts.semanticUnclassifiedTaggedItems = semanticAudit.unclassifiedTaggedItems;
        report.counts.semanticEstimatedPublicItems = semanticAudit.estimatedPublicItemsAfterNormalization;
        report.counts.semanticFamilyCount = semanticAudit.familyCount;
        report.counts.semanticItems = semanticAudit.semanticItems;
        report.counts.semanticVariants = semanticAudit.variants;
        report.counts.semanticPayloads = semanticAudit.payloads;
        report.counts.semanticIdentityMapRows = semanticAudit.identityMapRows;
        report.neiBrowserContract = factCounts.neiBrowserContract;
    }
}
