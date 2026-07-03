package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.util.List;

final class RawExportFactStreamDescriptorWriter {
    private static final String SCHEMA_VERSION = "nesqlpp/raw-fact-stream-providers/v1";

    private RawExportFactStreamDescriptorWriter() {}

    static void write(File rawDir, List<RawExportFactStreamDescriptor> descriptors) throws IOException {
        File validationDir = new File(rawDir, "validation");
        RawExportSidecarFileOps.ensureDirectory(validationDir);

        ProviderReport report = new ProviderReport();
        report.schemaVersion = SCHEMA_VERSION;
        report.providerCount = descriptors.size();
        report.providers = descriptors;

        File output = new File(validationDir, "raw_fact_stream_providers.json");
        RawExportSidecarFileOps.writeJson(new GsonBuilder().setPrettyPrinting().create(), output, report);
    }

    private static final class ProviderReport {
        String schemaVersion;
        int providerCount;
        List<RawExportFactStreamDescriptor> providers;
    }
}
