package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.elysium.kernel.ExportDebugFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Catalog-owned final report aliases published at the raw-export root. */
final class RawExportFinalReportCatalog {
    private static final List<FinalReportCopy> FINAL_REPORT_COPIES = validateAndFreeze(Arrays.asList(
            new FinalReportCopy(
                    "export stage timings",
                    ExportDebugFile.STAGE_TIMING.validationAliasPath(),
                    "export_stage_timings.json"),
            new FinalReportCopy(
                    "stage checkpoint",
                    ExportDebugFile.STAGE_CHECKPOINT.validationAliasPath(),
                    "stage_checkpoint.json"),
            new FinalReportCopy(
                    "stage checksums",
                    RawExportFileCatalog.validationPath(RawExportFileCatalog.STAGE_CHECKSUMS_FILE_NAME),
                    RawExportFileCatalog.STAGE_CHECKSUMS_FILE_NAME),
            new FinalReportCopy(
                    "export manifest",
                    RawExportFileCatalog.validationPath(RawExportFileCatalog.EXPORT_MANIFEST_FILE_NAME),
                    RawExportFileCatalog.EXPORT_MANIFEST_FILE_NAME),
            new FinalReportCopy(
                    "export health report",
                    RawExportFileCatalog.validationPath(RawExportFileCatalog.EXPORT_HEALTH_REPORT_FILE_NAME),
                    "validation_report.json")));

    private RawExportFinalReportCatalog() {}

    static List<FinalReportCopy> finalReportCopies() {
        return FINAL_REPORT_COPIES;
    }

    static void syncFinalReports(File repositoryDirectory) throws IOException {
        File rawDir = RawExportFileCatalog.rawExportDirectory(repositoryDirectory);
        RawExportSidecarFileOps.ensureDirectory(rawDir);
        RawExportSidecarFileOps.ensureDirectory(RawExportFileCatalog.validationDirectory(rawDir));

        for (FinalReportCopy report : FINAL_REPORT_COPIES) {
            RawExportSidecarFileOps.copyRequired(
                    report.sourceFile(rawDir),
                    report.targetFile(rawDir),
                    report.label());
        }
    }

    private static List<FinalReportCopy> validateAndFreeze(List<FinalReportCopy> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Raw-export final report catalog must not be empty");
        }
        Set<String> labels = new LinkedHashSet<String>();
        Set<String> sources = new LinkedHashSet<String>();
        Set<String> targets = new LinkedHashSet<String>();
        List<FinalReportCopy> validated = new ArrayList<FinalReportCopy>();
        for (FinalReportCopy descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Raw-export final report descriptor must not be null");
            }
            requireRelativeJsonPath("source", descriptor.sourceRelativePath);
            requireRelativeJsonPath("target", descriptor.targetRelativePath);
            if (!labels.add(descriptor.label)) {
                throw new IllegalStateException("Duplicate raw-export final report label: " + descriptor.label);
            }
            if (!sources.add(descriptor.sourceRelativePath)) {
                throw new IllegalStateException(
                        "Duplicate raw-export final report source: " + descriptor.sourceRelativePath);
            }
            if (!targets.add(descriptor.targetRelativePath)) {
                throw new IllegalStateException(
                        "Duplicate raw-export final report target: " + descriptor.targetRelativePath);
            }
            validated.add(descriptor);
        }
        return Collections.unmodifiableList(validated);
    }

    private static void requireRelativeJsonPath(String label, String value) {
        if (value == null
                || value.trim().isEmpty()
                || value.startsWith("/")
                || value.startsWith("\\")
                || value.indexOf('\\') >= 0
                || value.contains("..")
                || !value.endsWith(".json")) {
            throw new IllegalStateException(
                    "Raw-export final report " + label + " path must be runtime-relative JSON: " + value);
        }
    }

    static final class FinalReportCopy {
        private final String label;
        private final String sourceRelativePath;
        private final String targetRelativePath;

        private FinalReportCopy(String label, String sourceRelativePath, String targetRelativePath) {
            if (label == null || label.trim().isEmpty()) {
                throw new IllegalArgumentException("Raw-export final report label must be non-empty");
            }
            this.label = label;
            this.sourceRelativePath = sourceRelativePath;
            this.targetRelativePath = targetRelativePath;
        }

        String label() {
            return label;
        }

        File sourceFile(File rawDir) {
            return RawExportFileCatalog.rawExportFile(rawDir, sourceRelativePath);
        }

        File targetFile(File rawDir) {
            return RawExportFileCatalog.rawExportFile(rawDir, targetRelativePath);
        }
    }
}
