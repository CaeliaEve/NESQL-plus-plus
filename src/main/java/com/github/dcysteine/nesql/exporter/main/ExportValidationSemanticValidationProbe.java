package com.github.dcysteine.nesql.exporter.main;

import java.util.List;

/** Bridges semantic diagnostics and semantic rule-pack validation into the probe catalog. */
final class ExportValidationSemanticValidationProbe implements ExportValidationProbe {
    public String id() {
        return "validation.semantic";
    }

    public List<String> capabilities() {
        return ExportValidationProbe.capabilityList(
                "validation.semantic.diagnostics",
                "validation.semantic.rule-pack");
    }

    public void inspect(
            ExportValidationProbeContext context,
            ExportValidationReport report) {
        ExportValidationSemanticProbe.inspectDiagnostics(context.rawDir, report);
        ExportValidationSemanticProbe.inspectRulePack(report);
    }
}
