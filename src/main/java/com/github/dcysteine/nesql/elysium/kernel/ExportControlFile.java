package com.github.dcysteine.nesql.elysium.kernel;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stable ControlFS-style files exposed under {@code raw-export/control}. */
public enum ExportControlFile {
    INDEX("controlIndex", "index", "index.json", ExportSchemaCatalog.CONTROL_INDEX),
    ABI("controlAbi", "abi", "abi.json", ExportSchemaCatalog.CONTROL_ABI),
    CAPABILITIES("controlCapabilities", "capabilities", "capabilities.json", ExportSchemaCatalog.CONTROL_CAPABILITIES),
    MODULES("controlModules", "modules", "modules.json", ExportSchemaCatalog.CONTROL_MODULES),
    DRIVERS("controlDrivers", "drivers", "drivers.json", ExportSchemaCatalog.CONTROL_DRIVERS),
    HEALTH("controlHealth", "health", "health.json", ExportSchemaCatalog.CONTROL_HEALTH),
    VERSION("controlVersion", "version", "version.json", ExportSchemaCatalog.CONTROL_VERSION);

    private final String manifestKey;
    private final String indexKey;
    private final String fileName;
    private final String schemaVersion;

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
        for (ExportControlFile file : values()) {
            if (file != INDEX) {
                files.put(file.indexKey, file.rawExportPath());
            }
        }
        return files;
    }
}
