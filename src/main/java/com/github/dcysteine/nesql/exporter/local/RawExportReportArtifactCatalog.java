package com.github.dcysteine.nesql.exporter.local;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Descriptor-owned JSON artifact list emitted by the raw-export report stage. */
final class RawExportReportArtifactCatalog {
    private static final List<JsonArtifactDescriptor> JSON_ARTIFACTS = validateAndFreeze(Arrays.asList(
            new JsonArtifactDescriptor(
                    "manifest",
                    RawExportFileCatalog.MANIFEST_FILE,
                    false,
                    JsonPayload.MANIFEST),
            new JsonArtifactDescriptor(
                    "exportReport",
                    RawExportFileCatalog.EXPORT_REPORT_FILE,
                    false,
                    JsonPayload.EXPORT_REPORT),
            new JsonArtifactDescriptor(
                    "validationExportReport",
                    RawExportFileCatalog.VALIDATION_EXPORT_REPORT_FILE,
                    false,
                    JsonPayload.EXPORT_REPORT),
            new JsonArtifactDescriptor(
                    "neiBrowserContract",
                    RawExportFileCatalog.NEI_BROWSER_CONTRACT_FILE,
                    true,
                    JsonPayload.NEI_BROWSER_CONTRACT)));

    private RawExportReportArtifactCatalog() {}

    static List<JsonArtifact> jsonArtifacts(RawExportManifest manifest, RawExportReport report) {
        List<JsonArtifact> artifacts = new ArrayList<JsonArtifact>();
        for (JsonArtifactDescriptor descriptor : JSON_ARTIFACTS) {
            Object value = descriptor.value(manifest, report);
            if (value == null) {
                if (!descriptor.optional) {
                    throw new IllegalStateException(
                            "Required raw-export report artifact payload is null: " + descriptor.key);
                }
                continue;
            }
            artifacts.add(new JsonArtifact(descriptor.key, descriptor.relativePath, value));
        }
        return Collections.unmodifiableList(artifacts);
    }

    static File sizeReportFile(File rawDir) {
        return RawExportFileCatalog.rawExportFile(rawDir, RawExportFileCatalog.SIZE_REPORT_FILE);
    }

    static File validationErrorsFile(File rawDir) {
        return RawExportFileCatalog.rawExportFile(rawDir, RawExportFileCatalog.VALIDATION_ERRORS_FILE);
    }

    private static List<JsonArtifactDescriptor> validateAndFreeze(List<JsonArtifactDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Raw-export report artifact catalog must not be empty");
        }
        Set<String> keys = new LinkedHashSet<String>();
        Set<String> paths = new LinkedHashSet<String>();
        List<JsonArtifactDescriptor> validated = new ArrayList<JsonArtifactDescriptor>();
        for (JsonArtifactDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Raw-export report artifact descriptor must not be null");
            }
            requireNonEmpty("Raw-export report artifact key", descriptor.key);
            requireJsonPath(descriptor.relativePath, descriptor.key);
            if (descriptor.payload == null) {
                throw new IllegalStateException(
                        "Raw-export report artifact payload selector must not be null: " + descriptor.key);
            }
            if (!keys.add(descriptor.key)) {
                throw new IllegalStateException("Duplicate raw-export report artifact key: " + descriptor.key);
            }
            if (!paths.add(descriptor.relativePath)) {
                throw new IllegalStateException(
                        "Duplicate raw-export report artifact path: " + descriptor.relativePath);
            }
            validated.add(descriptor);
        }
        return Collections.unmodifiableList(validated);
    }

    private static void requireJsonPath(String relativePath, String key) {
        if (relativePath == null
                || relativePath.trim().isEmpty()
                || relativePath.startsWith("/")
                || relativePath.startsWith("\\")
                || relativePath.indexOf('\\') >= 0
                || relativePath.contains("..")
                || !relativePath.endsWith(".json")) {
            throw new IllegalStateException(
                    "Raw-export report artifact path must be runtime-relative JSON: " + key);
        }
    }

    private static void requireNonEmpty(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty");
        }
    }

    private enum JsonPayload {
        MANIFEST {
            @Override
            Object value(RawExportManifest manifest, RawExportReport report) {
                return manifest;
            }
        },
        EXPORT_REPORT {
            @Override
            Object value(RawExportManifest manifest, RawExportReport report) {
                return report;
            }
        },
        NEI_BROWSER_CONTRACT {
            @Override
            Object value(RawExportManifest manifest, RawExportReport report) {
                return report == null ? null : report.neiBrowserContract;
            }
        };

        abstract Object value(RawExportManifest manifest, RawExportReport report);
    }

    private static final class JsonArtifactDescriptor {
        private final String key;
        private final String relativePath;
        private final boolean optional;
        private final JsonPayload payload;

        private JsonArtifactDescriptor(String key, String relativePath, boolean optional, JsonPayload payload) {
            this.key = key;
            this.relativePath = relativePath;
            this.optional = optional;
            this.payload = payload;
        }

        private Object value(RawExportManifest manifest, RawExportReport report) {
            return payload.value(manifest, report);
        }
    }

    static final class JsonArtifact {
        private final String key;
        private final String relativePath;
        private final Object value;

        private JsonArtifact(String key, String relativePath, Object value) {
            this.key = key;
            this.relativePath = relativePath;
            this.value = value;
        }

        String key() {
            return key;
        }

        Object value() {
            return value;
        }

        File file(File rawDir) {
            return RawExportFileCatalog.rawExportFile(rawDir, relativePath);
        }
    }
}
