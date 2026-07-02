package com.github.dcysteine.nesql.exporter.main;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Kernel-style validation probe boundary for one focused export validation concern. */
interface ExportValidationProbe {
    String id();

    List<String> capabilities();

    void inspect(
            ExportValidationProbeContext context,
            ExportValidationReportWriter.ValidationReport report) throws Exception;

    static List<String> capabilityList(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }
}
