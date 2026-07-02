package com.github.dcysteine.nesql.elysium.kernel;

/** DebugFS-style diagnostic files exposed under {@code raw-export/debug}. */
public enum ExportDebugFile {
    STAGE_TIMING(
            "debugStageTimings",
            "export/timing.json",
            "validation/export_stage_timings.json",
            ExportSchemaCatalog.DEBUG_STAGE_TIMING),
    STAGE_CHECKPOINT(
            "debugStageCheckpoint",
            "export/checkpoint.json",
            "validation/stage_checkpoint.json",
            ExportSchemaCatalog.DEBUG_STAGE_CHECKPOINT),
    KERNEL_TRACE(
            "debugTraceLatest",
            "trace/latest.json",
            "validation/export_kernel_trace.json",
            ExportSchemaCatalog.DEBUG_KERNEL_TRACE);

    private final String manifestKey;
    private final String debugPath;
    private final String validationAliasPath;
    private final String schemaVersion;

    ExportDebugFile(String manifestKey, String debugPath, String validationAliasPath, String schemaVersion) {
        this.manifestKey = manifestKey;
        this.debugPath = debugPath;
        this.validationAliasPath = validationAliasPath;
        this.schemaVersion = schemaVersion;
    }

    public String manifestKey() {
        return manifestKey;
    }

    public String debugPath() {
        return debugPath;
    }

    public String validationAliasPath() {
        return validationAliasPath;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public String rawExportDebugPath() {
        return "debug/" + debugPath;
    }
}
