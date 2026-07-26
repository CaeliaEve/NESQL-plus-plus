package com.github.dcysteine.nesql.exporter.local;

import java.io.File;
import java.io.IOException;

/** Final generation metadata refresh performed after every other raw-export artifact is written. */
public final class RawExportGenerationFinalizer {
    private RawExportGenerationFinalizer() {}

    public static void finalizeGeneration(File rawDir) throws IOException {
        RawExportSizeReportBuilder.refreshFinal(rawDir);
    }
}
