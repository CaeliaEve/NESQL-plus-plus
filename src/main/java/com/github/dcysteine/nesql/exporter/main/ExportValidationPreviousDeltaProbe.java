package com.github.dcysteine.nesql.exporter.main;

import java.util.List;

/** Applies previous-run validation snapshot and delta metadata as an ordered probe stage. */
final class ExportValidationPreviousDeltaProbe implements ExportValidationProbe {
    public String id() {
        return "validation.previous-delta";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList("validation.previous.delta");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReport report) throws Exception {
        ExportValidationReportStore.applyPreviousDelta(context.previousValidationDir, report);
    }
}
