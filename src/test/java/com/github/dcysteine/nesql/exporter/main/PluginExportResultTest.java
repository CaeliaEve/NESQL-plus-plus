package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;
import com.github.dcysteine.nesql.sql.Plugin;

public final class PluginExportResultTest {
    private PluginExportResultTest() {}

    public static void main(String[] args) {
        assertStatusFactoriesAndCounts();
        assertSkippedOptionalAdapterIsPublishable();
        assertPureSkippedMergeStaysSkipped();
        assertPartialCoreCollectionIsFailClosed();
        assertFailedCoreExceptionIsFailClosed();
    }

    private static void assertPureSkippedMergeStaysSkipped() {
        PluginExportResult aggregate = PluginExportResult.builder()
                .merge(PluginExportResult.skipped("optional-one", "optional adapter one absent"))
                .merge(PluginExportResult.skipped("optional-two", "optional adapter two absent"))
                .build();
        require(aggregate.status == PluginExportResult.Status.SKIPPED, "pure skipped aggregate status");
        require(aggregate.errors.size() == 2, "pure skipped aggregate errors");
    }

    private static void assertStatusFactoriesAndCounts() {
        PluginExportResult success = PluginExportResult.builder()
                .status(PluginExportResult.Status.SUCCESS)
                .count("exported", 3)
                .build();
        require(success.status == PluginExportResult.Status.SUCCESS, "success status");
        require(success.counts.get("exported") == 3L, "success count");

        PluginExportResult partial = PluginExportResult.builder()
                .status(PluginExportResult.Status.PARTIAL)
                .count("failed", 1)
                .error("core-row-failed", "one core row failed", true)
                .build();
        require(partial.status == PluginExportResult.Status.PARTIAL, "partial status");

        require(
                PluginExportResult.skipped("optional-missing", "optional adapter absent").status
                        == PluginExportResult.Status.SKIPPED,
                "skipped status");
        require(
                PluginExportResult.failed("core-exception", "core collection threw").status
                        == PluginExportResult.Status.FAILED,
                "failed status");
    }

    private static void assertSkippedOptionalAdapterIsPublishable() {
        PluginExportPolicy.requirePublishable(
                Plugin.NEI,
                ExportPluginLifecycleCatalog.PHASE_PROCESS,
                PluginExportResult.skipped("optional-adapter-unavailable", "optional adapter absent"));
    }

    private static void assertPartialCoreCollectionIsFailClosed() {
        PluginExportResult partial = PluginExportResult.builder()
                .status(PluginExportResult.Status.PARTIAL)
                .count("handlersFailed", 1)
                .error("nei-handler-export-failed", "core handler failed", true)
                .build();
        assertRejected(partial, "partial result is not allowed");
    }

    private static void assertFailedCoreExceptionIsFailClosed() {
        assertRejected(
                PluginExportResult.failed(
                        "plugin-phase-exception",
                        "java.lang.IllegalStateException: core collection failed"),
                "failed");
    }

    private static void assertRejected(PluginExportResult result, String expectedMessage) {
        try {
            PluginExportPolicy.requirePublishable(
                    Plugin.NEI,
                    ExportPluginLifecycleCatalog.PHASE_PROCESS,
                    result);
            throw new AssertionError("Expected plugin export policy rejection");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains(expectedMessage), "policy error message");
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Plugin export result regression failed: " + label);
        }
    }
}
