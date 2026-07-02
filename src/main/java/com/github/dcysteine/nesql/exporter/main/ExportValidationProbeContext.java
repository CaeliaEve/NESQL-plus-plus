package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;

import java.io.File;

/** Immutable filesystem and export context shared by validation probes. */
final class ExportValidationProbeContext {
    final ExportContext exportContext;
    final File repositoryDirectory;
    final File rawDir;
    final File validationDir;

    private ExportValidationProbeContext(
            ExportContext exportContext,
            File repositoryDirectory,
            File rawDir,
            File validationDir) {
        this.exportContext = exportContext;
        this.repositoryDirectory = repositoryDirectory;
        this.rawDir = rawDir;
        this.validationDir = validationDir;
    }

    static ExportValidationProbeContext from(ExportContext exportContext) {
        File repositoryDirectory = exportContext.paths.repositoryDirectory;
        File rawDir = RawExportFileCatalog.rawExportDirectory(repositoryDirectory);
        return new ExportValidationProbeContext(
                exportContext,
                repositoryDirectory,
                rawDir,
                RawExportFileCatalog.validationDirectory(rawDir));
    }
}
