package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;

import java.io.File;

/** Immutable filesystem and export context shared by validation probes. */
final class ExportValidationProbeContext {
    final ExportContext exportContext;
    final File repositoryDirectory;
    final File rawDir;
    final File validationDir;
    final File previousValidationDir;

    private ExportValidationProbeContext(
            ExportContext exportContext,
            File repositoryDirectory,
            File rawDir,
            File validationDir,
            File previousValidationDir) {
        this.exportContext = exportContext;
        this.repositoryDirectory = repositoryDirectory;
        this.rawDir = rawDir;
        this.validationDir = validationDir;
        this.previousValidationDir = previousValidationDir;
    }

    static ExportValidationProbeContext from(ExportContext exportContext) throws java.io.IOException {
        File repositoryDirectory = exportContext.paths.repositoryDirectory;
        File rawDir = exportContext.rawExportDirectory();
        File previousRawDir = exportContext.authoritativeRawExportDirectory();
        return new ExportValidationProbeContext(
                exportContext,
                repositoryDirectory,
                rawDir,
                RawExportFileCatalog.validationDirectory(rawDir),
                RawExportFileCatalog.validationDirectory(previousRawDir));
    }
}
