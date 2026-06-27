package com.github.dcysteine.nesql.exporter.local;

import java.io.IOException;

final class RawNeiFactStreamProvider implements RawExportFactStreamProvider {
    @Override
    public String id() {
        return "raw.nei-facts";
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
    }
}
