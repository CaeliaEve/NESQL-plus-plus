package com.github.dcysteine.nesql.exporter.main;

import java.util.List;

/** Applies validation warning, blocked-state, and compile-readiness policy as a catalog stage. */
final class ExportValidationHealthPolicyProbe implements ExportValidationProbe {
    public String id() {
        return "validation.health-policy";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList("validation.health.policy");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReport report) {
        ExportValidationHealthPolicy.evaluate(report);
    }
}
