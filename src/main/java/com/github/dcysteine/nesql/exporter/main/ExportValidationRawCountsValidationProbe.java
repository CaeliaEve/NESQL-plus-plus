package com.github.dcysteine.nesql.exporter.main;

import java.util.List;

/** Bridges raw-export aggregate count extraction into the validation probe catalog. */
final class ExportValidationRawCountsValidationProbe implements ExportValidationProbe {
    public String id() {
        return "validation.raw-counts";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList("validation.raw.counts");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReportWriter.ValidationReport report) {
        ExportValidationRawCountProbe.inspect(context.repositoryDirectory, report);
    }
}
