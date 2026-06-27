package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.List;

final class RawFactCounts {
        long items;
        long fluids;
        long recipes;
        long groups;
        long neiOrderEntries;
        long neiRuntimePanelItems;
        long neiExportOnlyItems;
        long neiBrowserItems;
        long neiDefaultEntries;
        long neiFallbackGroups;
        long neiNativeGroups;
        long neiSyntheticGroups;
        long neiGuidFilterRules;
        long neiHiddenItemRules;
        long neiHiddenItems;
        long neiRepresentativeMismatches;
        long neiHandlers;
        long neiHandlerLayouts;
        long uiFamilyCensusHandlers;
        long uiFamilyCensusFamilies;
        long uiTemplateCatalogHandlers;
        long uiTemplateCatalogTemplates;
        long uiTemplateCatalogFamilies;
        long textures;
        long animations;
        long entities;
        long browserAtlasAssets;
        long renderBackendFacts;
        long renderBackendAngelica;
        long renderTextureSprites;
        long renderTextureSpritesMissingTiming;
        long renderItemRenderers;
        long renderShaderItems;
        long renderShaderItemsRequiringCapture;
        long renderShaderItemsMissingCapture;
        List<String> renderShaderItemsMissingCaptureSamples = new ArrayList<String>();
        long renderUnknownSpecialRenderers;
        long renderFramebufferCaptures;
        long renderFramebufferCapturesWithoutFrames;
        List<String> renderFramebufferCapturesWithoutFramesSamples = new ArrayList<String>();
        NeiBrowserContract neiBrowserContract;
}
