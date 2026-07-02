package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.nativeui.NativeUiExportValidator;

import java.io.IOException;
import java.util.List;

final class RawNeiFactStreamProvider implements RawExportFactStreamProvider {
    @Override
    public String id() {
        return "raw.nei-facts";
    }

    @Override
    public List<String> capabilities() {
        return RawExportFactStreamProvider.list(
                "raw.nei.browser-order",
                "raw.nei.handler-metadata",
                "raw.native-ui.handler-layouts");
    }

    @Override
    public List<String> outputFamilies() {
        return RawExportFactStreamProvider.list(
                "facts/nei",
                "facts/native-ui",
                "validation/nei-browser-contract");
    }

    @Override
    public void write(RawExportFactStreamContext context, RawFactCounts counts) throws IOException {
        RawNeiFactCounts nei =
                new RawExportNeiFactWriter(context.repositoryDirectory, context.rawDir, context.schemaVersion).write();
        counts.groups = nei.groups;
        counts.neiOrderEntries = nei.neiOrderEntries;
        counts.neiBrowserContract = nei.neiBrowserContract;
        counts.neiRuntimePanelItems = nei.neiRuntimePanelItems;
        counts.neiExportOnlyItems = nei.neiExportOnlyItems;
        counts.neiBrowserItems = nei.neiBrowserItems;
        counts.neiDefaultEntries = nei.neiDefaultEntries;
        counts.neiFallbackGroups = nei.neiFallbackGroups;
        counts.neiNativeGroups = nei.neiNativeGroups;
        counts.neiSyntheticGroups = nei.neiSyntheticGroups;
        counts.neiGuidFilterRules = nei.neiGuidFilterRules;
        counts.neiHiddenItemRules = nei.neiHiddenItemRules;
        counts.neiHiddenItems = nei.neiHiddenItems;
        counts.neiRepresentativeMismatches = nei.neiRepresentativeMismatches;
        counts.neiHandlers = nei.neiHandlers;
        counts.neiHandlerLayouts = nei.neiHandlerLayouts;
        counts.uiFamilyCensusHandlers = nei.uiFamilyCensusHandlers;
        counts.uiFamilyCensusFamilies = nei.uiFamilyCensusFamilies;
        counts.uiTemplateCatalogHandlers = nei.uiTemplateCatalogHandlers;
        counts.uiTemplateCatalogTemplates = nei.uiTemplateCatalogTemplates;
        counts.uiTemplateCatalogFamilies = nei.uiTemplateCatalogFamilies;
        NativeUiExportValidator.Result nativeUi = NativeUiExportValidator.validate(context.rawDir);
        counts.nativeUiLayouts = nativeUi.layoutCount;
        counts.nativeUiSlots = nativeUi.slotCount;
        counts.nativeUiMissingSurfaces = nativeUi.missingSurfaceCount;
        counts.nativeUiSlotBoundsViolations = nativeUi.slotBoundsViolationCount;
        counts.nativeUiBackgroundBoundsViolations = nativeUi.backgroundBoundsViolationCount;
        counts.nativeUiCoordinateContractViolations = nativeUi.coordinateContractViolationCount;
        counts.nativeUiMissingSurfaceSamples = nativeUi.missingSurfaceSamples;
        counts.nativeUiSlotBoundsViolationSamples = nativeUi.slotBoundsSamples;
        counts.nativeUiBackgroundBoundsViolationSamples = nativeUi.backgroundBoundsSamples;
        counts.nativeUiCoordinateContractViolationSamples = nativeUi.coordinateContractSamples;
    }
}
