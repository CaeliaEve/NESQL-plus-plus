package com.github.dcysteine.nesql.exporter.main;

import java.util.List;

/** Bridges portable-path hygiene checks into the validation probe catalog. */
final class ExportValidationPathValidationProbe implements ExportValidationProbe {
    public String id() {
        return "validation.path-hygiene";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList("validation.path.hygiene");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReport report) {
        ExportValidationPathHygieneProbe.inspect(context.repositoryDirectory, report);
    }
}
