package com.github.dcysteine.nesql.exporter.main;

/** Applies descriptor-owned validation warning, blocked-state, and compile-readiness policy. */
final class ExportValidationHealthPolicy {
    private ExportValidationHealthPolicy() {}

    static void evaluate(ExportValidationReport report) {
        ExportValidationHealthPolicyCatalog.collectWarnings(report);
        ExportValidationHealthPolicyCatalog.applyBlockedRules(report);
        ExportValidationHealthPolicyCatalog.collectActionableIssues(report);
        ExportValidationHealthPolicyCatalog.resolveStatus(report);
    }
}
