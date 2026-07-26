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
import java.util.Collections;
import java.util.List;

/**
 * Coordinates raw-export fact streams and reports.
 *
 * <p>Stable paths, report aliases, and validation ownership live in catalog/pipeline classes; this
 * writer only wires the export transaction together.</p>
 */
public final class RawExportSidecarWriter {
    private static final String SCHEMA_VERSION = ExportSchemaCatalog.RAW_EXPORT_ABI;

    private final EntityManager entityManager;
    private final File repositoryDirectory;
    private final File rawDir;
    private final ExportContext exportContext;
    private final List<CanonicalRenderAsset> renderAssets;

    public RawExportSidecarWriter(
            EntityManager entityManager,
            File repositoryDirectory,
            File rawDir,
            ExportContext exportContext,
            List<CanonicalRenderAsset> renderAssets) {
        this.entityManager = entityManager;
        this.repositoryDirectory = repositoryDirectory;
        if (rawDir == null) {
            throw new IllegalArgumentException("Raw-export sidecar generation directory must not be null");
        }
        this.rawDir = rawDir;
        this.exportContext = exportContext;
        if (renderAssets == null) {
            throw new IllegalArgumentException("Raw-export sidecar requires precollected render assets");
        }
        this.renderAssets = Collections.unmodifiableList(new ArrayList<CanonicalRenderAsset>(renderAssets));
    }

    public void export() throws IOException {
        RawExportSidecarFileOps.ensureDirectory(rawDir);

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

    public static void syncFinalReports(File rawDir) throws IOException {
        RawExportFinalReportCatalog.syncFinalReports(rawDir);
    }
}
