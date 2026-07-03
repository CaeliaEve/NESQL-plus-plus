package com.github.dcysteine.nesql.exporter.main;

import java.util.List;

/** Computes validation metrics derived from earlier ordered probe evidence. */
final class ExportValidationDerivedMetricsProbe implements ExportValidationProbe {
    public String id() {
        return "validation.derived-metrics";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList("validation.derived.metrics");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReport report) {
        report.atlasManifestCoverageRatio = ExportValidationJsonSupport.ratio(
                report.totalAtlasManifestAssets,
                report.renderAssetManifestAssets);
    }
}
