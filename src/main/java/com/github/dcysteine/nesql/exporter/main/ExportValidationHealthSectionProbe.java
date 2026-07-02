package com.github.dcysteine.nesql.exporter.main;

import java.util.List;

/** Builds validation health summary sections after health policy evaluation. */
final class ExportValidationHealthSectionProbe implements ExportValidationProbe {
    public String id() {
        return "validation.health-sections";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList("validation.health.sections");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReportWriter.ValidationReport report) {
        ExportValidationHealthSectionBuilder.populate(context.repositoryDirectory, report);
    }
}
