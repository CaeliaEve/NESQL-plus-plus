package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.List;

final class NeiBrowserContract {
    String schemaVersion;
    String generatedAt;
    String status;
    String summary;
    boolean neiRuntimeSnapshot;
    String orderSource;
    String groupingSource;
    String guidFiltersSource;
    String hiddenItemsSource;
    long guidFilterRuleCount;
    long hiddenItemRuleCount;
    long hiddenItemCount;
    long neiRuntimePanelItemCount;
    long exportOnlyItemCount;
    long browserItemCount;
    long runtimeToBrowserDelta;
    long groupCount;
    long nativeGroupCount;
    long fallbackGroupCount;
    long syntheticGroupCount;
    long defaultEntryCount;
    long groupedMemberCount;
    long ungroupedBrowserItemCount;
    long missingRepresentativeCount;
    long representativeMismatchCount;
    List<BrowserContractMismatch> representativeMismatchSamples = new ArrayList<BrowserContractMismatch>();
}
