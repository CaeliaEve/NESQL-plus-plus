package com.github.dcysteine.nesql.elysium.kernel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Stable ControlFS-style files exposed under {@code raw-export/control}. */
public enum ExportControlFile {
    INDEX("controlIndex", "index", "index.json", ExportSchemaCatalog.CONTROL_INDEX),
    ABI("controlAbi", "abi", "abi.json", ExportSchemaCatalog.CONTROL_ABI),
    CAPABILITIES("controlCapabilities", "capabilities", "capabilities.json", ExportSchemaCatalog.CONTROL_CAPABILITIES),
    MODULES("controlModules", "modules", "modules.json", ExportSchemaCatalog.CONTROL_MODULES),
    DRIVERS("controlDrivers", "drivers", "drivers.json", ExportSchemaCatalog.CONTROL_DRIVERS),
    VALIDATION_PROBES(
            "controlValidationProbes",
            "validationProbes",
            "validation-probes.json",
            ExportSchemaCatalog.CONTROL_VALIDATION_PROBES),
    HEALTH("controlHealth", "health", "health.json", ExportSchemaCatalog.CONTROL_HEALTH),
    VERSION("controlVersion", "version", "version.json", ExportSchemaCatalog.CONTROL_VERSION);

    private final String manifestKey;
    private final String indexKey;
    private final String fileName;
    private final String schemaVersion;

    private static final List<ExportControlFile> VALIDATED_FILES =
            validateAndFreeze(Arrays.asList(values()));

    public static final String STABILITY_STABLE = "stable";
    public static final String VALIDATION_PROBE_POLICY = "ordered-fail-closed-validation-probe-catalog";

    ExportControlFile(String manifestKey, String indexKey, String fileName, String schemaVersion) {
        this.manifestKey = manifestKey;
        this.indexKey = indexKey;
        this.fileName = fileName;
        this.schemaVersion = schemaVersion;
    }

    public String manifestKey() {
        return manifestKey;
    }

    public String indexKey() {
        return indexKey;
    }

    public String fileName() {
        return fileName;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public String rawExportPath() {
        return "control/" + fileName;
    }

    public static Map<String, String> indexedFiles() {
        Map<String, String> files = new LinkedHashMap<String, String>();
        for (ExportControlFile file : VALIDATED_FILES) {
            if (file != INDEX) {
                files.put(file.indexKey, file.rawExportPath());
            }
        }
        return files;
    }

    private static List<ExportControlFile> validateAndFreeze(List<ExportControlFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalStateException("ControlFS file catalog must not be empty");
        }
        Set<String> manifestKeys = new LinkedHashSet<String>();
        Set<String> indexKeys = new LinkedHashSet<String>();
        Set<String> fileNames = new LinkedHashSet<String>();
        Set<String> schemaVersions = new LinkedHashSet<String>();
        for (ExportControlFile file : files) {
            if (file == null) {
                throw new IllegalStateException("ControlFS file descriptor must not be null");
            }
            requireNonEmpty("ControlFS manifest key", file.manifestKey, file.name());
            requireNonEmpty("ControlFS index key", file.indexKey, file.name());
            requireNonEmpty("ControlFS file name", file.fileName, file.name());
            requireNonEmpty("ControlFS schema version", file.schemaVersion, file.name());
            requireJsonFileName(file);
            if (!manifestKeys.add(file.manifestKey)) {
                throw new IllegalStateException("Duplicate ControlFS manifest key: " + file.manifestKey);
            }
            if (!indexKeys.add(file.indexKey)) {
                throw new IllegalStateException("Duplicate ControlFS index key: " + file.indexKey);
            }
            if (!fileNames.add(file.fileName)) {
                throw new IllegalStateException("Duplicate ControlFS file name: " + file.fileName);
            }
            if (!schemaVersions.add(file.schemaVersion)) {
                throw new IllegalStateException(
                        "Duplicate ControlFS schema version: " + file.schemaVersion);
            }
        }
        return Collections.unmodifiableList(new ArrayList<ExportControlFile>(files));
    }

    private static void requireJsonFileName(ExportControlFile file) {
        if (file.fileName.indexOf('/') >= 0
                || file.fileName.indexOf('\\') >= 0
                || file.fileName.contains("..")
                || !file.fileName.endsWith(".json")) {
            throw new IllegalStateException(
                    "ControlFS file name must be a single JSON file: " + file.name());
        }
    }

    private static void requireNonEmpty(String label, String value, String descriptorName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty: " + descriptorName);
        }
    }
}
