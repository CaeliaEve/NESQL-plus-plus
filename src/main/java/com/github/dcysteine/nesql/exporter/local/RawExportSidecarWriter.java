package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.elysium.kernel.ExportSchemaCatalog;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Writes the first raw-export sidecar without replacing the current canonical
 * export structure.
 *
 * <p>This is intentionally conservative: the sidecar records counts, source
 * profile, selected stages, and references to the existing canonical outputs.
 * Later rebuild phases can replace each placeholder JSONL file with true raw
 * fact streams while NeoNEI continues to consume the current export layout.</p>
 */
public final class RawExportSidecarWriter {
    private static final String OUTPUT_DIRECTORY = "raw-export";
    private static final String SCHEMA_VERSION = ExportSchemaCatalog.RAW_EXPORT_ABI;
    private static final List<FinalReportCopy> FINAL_REPORT_COPIES =
            Collections.unmodifiableList(Arrays.asList(
                    new FinalReportCopy(
                            "export stage timings",
                            "export_stage_timings.json",
                            "export_stage_timings.json"),
                    new FinalReportCopy(
                            "stage checkpoint",
                            "stage_checkpoint.json",
                            "stage_checkpoint.json"),
                    new FinalReportCopy(
                            "stage checksums",
                            "stage_checksums.json",
                            "stage_checksums.json"),
                    new FinalReportCopy(
                            "export manifest",
                            "export_manifest.json",
                            "export_manifest.json"),
                    new FinalReportCopy(
                            "export health report",
                            "export-health-report.json",
                            "validation_report.json")));

    private final EntityManager entityManager;
    private final File repositoryDirectory;
    private final ExportContext exportContext;
    private final List<CanonicalRenderAsset> renderAssets;

    public RawExportSidecarWriter(
            EntityManager entityManager,
            File repositoryDirectory,
            ExportContext exportContext,
            List<CanonicalRenderAsset> renderAssets) {
        this.entityManager = entityManager;
        this.repositoryDirectory = repositoryDirectory;
        this.exportContext = exportContext;
        if (renderAssets == null) {
            throw new IllegalArgumentException("Raw-export sidecar requires precollected render assets");
        }
        this.renderAssets = Collections.unmodifiableList(new ArrayList<CanonicalRenderAsset>(renderAssets));
    }

    public void export() throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        RawExportSidecarFileOps.ensureDirectory(rawDir);
        RawExportSidecarFileOps.purgeLegacyRawExportOutputs(rawDir);

        RawFactCounts factCounts = new RawExportFactStreamPipeline(
                entityManager,
                repositoryDirectory,
                rawDir,
                renderAssets,
                SCHEMA_VERSION).write();
        RawExportSidecarReportPipeline.RawExportSidecarReportResult reportResult =
                new RawExportSidecarReportPipeline(
                        entityManager,
                        repositoryDirectory,
                        rawDir,
                        exportContext,
                        renderAssets,
                        SCHEMA_VERSION).write(factCounts);

        Logger.MOD.info(
                "Semantic item identity audit written: totalItems={}, taggedItems={}, classifiedTaggedItems={}, semanticItems={}, variants={}, payloads={}",
                reportResult.semanticAudit.totalItems,
                reportResult.semanticAudit.taggedItems,
                reportResult.semanticAudit.classifiedTaggedItems,
                reportResult.semanticAudit.semanticItems,
                reportResult.semanticAudit.variants,
                reportResult.semanticAudit.payloads);
        Logger.chatMessage(EnumChatFormatting.GREEN + "Raw-export sidecar written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + rawDir.getAbsolutePath());
    }

    public static void syncFinalReports(File repositoryDirectory) throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        RawExportSidecarFileOps.ensureDirectory(rawDir);
        File rawValidationDir = new File(rawDir, "validation");
        RawExportSidecarFileOps.ensureDirectory(rawValidationDir);

        for (FinalReportCopy report : FINAL_REPORT_COPIES) {
            RawExportSidecarFileOps.copyRequired(
                    new File(rawValidationDir, report.sourceFile),
                    new File(rawDir, report.targetFile),
                    report.label);
        }
    }

    private static final class FinalReportCopy {
        private final String label;
        private final String sourceFile;
        private final String targetFile;

        private FinalReportCopy(String label, String sourceFile, String targetFile) {
            this.label = label;
            this.sourceFile = sourceFile;
            this.targetFile = targetFile;
        }
    }
}
