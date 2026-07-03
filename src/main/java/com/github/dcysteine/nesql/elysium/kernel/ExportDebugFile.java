package com.github.dcysteine.nesql.elysium.kernel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    private static final List<ExportDebugFile> VALIDATED_FILES =
            validateAndFreeze(Arrays.asList(values()));

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

    private static List<ExportDebugFile> validateAndFreeze(List<ExportDebugFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalStateException("DebugFS file catalog must not be empty");
        }
        Set<String> manifestKeys = new LinkedHashSet<String>();
        Set<String> debugPaths = new LinkedHashSet<String>();
        Set<String> validationAliasPaths = new LinkedHashSet<String>();
        Set<String> schemaVersions = new LinkedHashSet<String>();
        for (ExportDebugFile file : files) {
            if (file == null) {
                throw new IllegalStateException("DebugFS file descriptor must not be null");
            }
            requireNonEmpty("DebugFS manifest key", file.manifestKey, file.name());
            requireNonEmpty("DebugFS path", file.debugPath, file.name());
            requireNonEmpty("DebugFS validation alias path", file.validationAliasPath, file.name());
            requireNonEmpty("DebugFS schema version", file.schemaVersion, file.name());
            requireRelativeJsonPath("DebugFS path", file.debugPath, file.name());
            requireValidationAliasPath(file);
            if (!manifestKeys.add(file.manifestKey)) {
                throw new IllegalStateException("Duplicate DebugFS manifest key: " + file.manifestKey);
            }
            if (!debugPaths.add(file.debugPath)) {
                throw new IllegalStateException("Duplicate DebugFS path: " + file.debugPath);
            }
            if (!validationAliasPaths.add(file.validationAliasPath)) {
                throw new IllegalStateException(
                        "Duplicate DebugFS validation alias path: " + file.validationAliasPath);
            }
            if (!schemaVersions.add(file.schemaVersion)) {
                throw new IllegalStateException("Duplicate DebugFS schema version: " + file.schemaVersion);
            }
        }
        return Collections.unmodifiableList(new ArrayList<ExportDebugFile>(files));
    }

    private static void requireValidationAliasPath(ExportDebugFile file) {
        requireRelativeJsonPath("DebugFS validation alias path", file.validationAliasPath, file.name());
        if (!file.validationAliasPath.startsWith("validation/")) {
            throw new IllegalStateException(
                    "DebugFS validation alias path must live under validation/: " + file.name());
        }
    }

    private static void requireRelativeJsonPath(String label, String value, String descriptorName) {
        if (value.startsWith("/")
                || value.startsWith("\\")
                || value.indexOf('\\') >= 0
                || value.contains("..")
                || !value.endsWith(".json")) {
            throw new IllegalStateException(label + " must be a runtime-relative JSON path: " + descriptorName);
        }
    }

    private static void requireNonEmpty(String label, String value, String descriptorName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty: " + descriptorName);
        }
    }
}
