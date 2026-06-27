package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;
import jakarta.persistence.EntityManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

final class RawExportSidecarReportPipeline {
    private final EntityManager entityManager;
    private final File repositoryDirectory;
    private final File rawDir;
    private final ExportContext exportContext;
    private final List<CanonicalRenderAsset> renderAssets;
    private final String schemaVersion;

    RawExportSidecarReportPipeline(
            EntityManager entityManager,
            File repositoryDirectory,
            File rawDir,
            ExportContext exportContext,
            List<CanonicalRenderAsset> renderAssets,
            String schemaVersion) {
        this.entityManager = entityManager;
        this.repositoryDirectory = repositoryDirectory;
        this.rawDir = rawDir;
        this.exportContext = exportContext;
        this.renderAssets = renderAssets;
        this.schemaVersion = schemaVersion;
    }

    RawExportSidecarReportResult write(RawFactCounts factCounts) throws IOException {
        SemanticRulePack.RuntimeMetadata semanticRuleRuntime = new RawExportSemanticRuntimeBuilder(exportContext).build();
        SemanticRulePack.writeBundledCopy(new File(rawDir, "facts/semantic/rule-pack.json"), semanticRuleRuntime);
        SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary semanticAudit =
                new SemanticItemIdentityDiagnosticsWriter(entityManager, rawDir).write();

        RawExportReport report = new RawExportReportFactory(
                entityManager, repositoryDirectory, exportContext, renderAssets, schemaVersion).build();
        RawExportReportAssembler.apply(report, factCounts, semanticRuleRuntime, semanticAudit);
        RawExportValidationSupport.apply(report);

        String generatedAt = utcNow();
        RawExportManifest manifest = RawExportManifestBuilder.build(schemaVersion, generatedAt, exportContext, report);
        RawExportReportWriter.write(schemaVersion, generatedAt, rawDir, manifest, report);
        return new RawExportSidecarReportResult(semanticAudit);
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    static final class RawExportSidecarReportResult {
        final SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary semanticAudit;

        private RawExportSidecarReportResult(SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary semanticAudit) {
            this.semanticAudit = semanticAudit;
        }
    }
}
