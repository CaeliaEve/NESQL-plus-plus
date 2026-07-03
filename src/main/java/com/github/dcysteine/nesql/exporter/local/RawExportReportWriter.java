package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;

final class RawExportReportWriter {
    private RawExportReportWriter() {}

    static void write(String schemaVersion, String generatedAt, File rawDir, RawExportManifest manifest, RawExportReport report)
            throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        for (RawExportReportArtifactCatalog.JsonArtifact artifact
                : RawExportReportArtifactCatalog.jsonArtifacts(manifest, report)) {
            RawExportSidecarFileOps.writeJson(gson, artifact.file(rawDir), artifact.value());
        }
        RawExportSidecarFileOps.writeJson(
                gson,
                RawExportReportArtifactCatalog.sizeReportFile(rawDir),
                RawExportSizeReportBuilder.build(schemaVersion, generatedAt, rawDir));
        RawExportEmptyJsonlWriter.writeIfMissing(
                RawExportReportArtifactCatalog.validationErrorsFile(rawDir));
    }
}
