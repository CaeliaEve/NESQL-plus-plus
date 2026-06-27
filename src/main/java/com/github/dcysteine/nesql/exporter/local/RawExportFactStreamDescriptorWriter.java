package com.github.dcysteine.nesql.exporter.local;

import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
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
        try (FileOutputStream fos = new FileOutputStream(output);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
        }
    }

    private static final class ProviderReport {
        String schemaVersion;
        int providerCount;
        List<RawExportFactStreamDescriptor> providers;
    }
}
