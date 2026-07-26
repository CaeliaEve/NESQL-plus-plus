package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.List;

public final class RawExportValidationCountContractTest {
    private RawExportValidationCountContractTest() {}

    public static void main(String[] args) {
        tenMissingCaptureSamplesDoNotBecomeCountMismatches();
        realCountMismatchIsCountedWithoutUnrelatedIssues();
        supportPipelineKeepsDiagnosticsButReadiesCoreCounts();
    }

    private static void tenMissingCaptureSamplesDoNotBecomeCountMismatches() {
        List<String> issues = missingCaptureIssues(10);
        assertEquals(
                "real export missing capture samples are not count mismatches",
                0,
                RawExportValidationAbiCatalog.countMismatchIssueCount(issues));
        RawValidationGate core = coreGate(
                RawExportValidationAbiCatalog.buildGates(new RawExportCounts(), issues));
        assertEquals(
                "core counts remain ready",
                RawExportValidationAbiCatalog.STATUS_READY,
                core.status);
        assertEquals(
                "core counts ready summary",
                "Raw fact counts match database/canonical source counts.",
                core.summary);
    }

    private static void realCountMismatchIsCountedWithoutUnrelatedIssues() {
        List<String> issues = missingCaptureIssues(10);
        RawExportValidationAbiCatalog.addCountMismatch(
                issues,
                RawExportValidationAbiCatalog.COUNT_ITEMS,
                129984L,
                129983L);
        assertEquals(
                "only the typed count issue is counted",
                1,
                RawExportValidationAbiCatalog.countMismatchIssueCount(issues));
        RawValidationGate core = coreGate(
                RawExportValidationAbiCatalog.buildGates(new RawExportCounts(), issues));
        assertEquals(
                "real count mismatch blocks core counts",
                RawExportValidationAbiCatalog.STATUS_BLOCKED,
                core.status);
        assertEquals(
                "summary reports the real mismatch count",
                "Raw fact counts have 1 mismatch(es).",
                core.summary);
    }

    private static void supportPipelineKeepsDiagnosticsButReadiesCoreCounts() {
        RawExportReport report = new RawExportReport();
        report.counts = matchingCounts();
        report.validation = new RawExportValidation();
        report.counts.renderShaderItemsRequiringCapture = 10L;
        report.counts.renderShaderItemsMissingCapture = 10L;
        report.counts.renderShaderItemsMissingCaptureSamples = sampleItemIds(10);

        RawExportValidationSupport.apply(report);

        assertEquals(
                "all ten render diagnostics remain exported",
                10,
                report.validation.failedStages.size());
        assertEquals(
                "no count mismatch is synthesized",
                0,
                RawExportValidationAbiCatalog.countMismatchIssueCount(
                        report.validation.failedStages));
        assertEquals(
                "support pipeline core gate remains ready",
                RawExportValidationAbiCatalog.STATUS_READY,
                coreGate(report.validation.gates).status);
    }

    private static RawExportCounts matchingCounts() {
        RawExportCounts counts = new RawExportCounts();
        counts.items = 129984L;
        counts.rawItems = 129984L;
        counts.fluids = 1542L;
        counts.rawFluids = 1542L;
        counts.recipes = 247310L;
        counts.rawRecipes = 247310L;
        counts.renderAssets = 130416L;
        counts.rawTextures = 130416L;
        return counts;
    }

    private static List<String> missingCaptureIssues(int count) {
        List<String> issues = new ArrayList<String>();
        for (String itemId : sampleItemIds(count)) {
            issues.add(RawExportValidationAbiCatalog.ISSUE_MISSING_RENDER_CAPTURE + ":" + itemId);
        }
        return issues;
    }

    private static List<String> sampleItemIds(int count) {
        List<String> samples = new ArrayList<String>();
        for (int index = 0; index < count; index++) {
            samples.add("i~fixture~missing-capture~" + index);
        }
        return samples;
    }

    private static RawValidationGate coreGate(List<RawValidationGate> gates) {
        for (RawValidationGate gate : gates) {
            if (RawExportValidationAbiCatalog.GATE_CORE_COUNTS.equals(gate.name)) {
                return gate;
            }
        }
        throw new AssertionError("Missing core-counts gate");
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(
                    label + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
