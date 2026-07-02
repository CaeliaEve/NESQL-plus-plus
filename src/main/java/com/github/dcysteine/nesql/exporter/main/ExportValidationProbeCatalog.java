package com.github.dcysteine.nesql.exporter.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Owns validation probe ordering, identity checks, and probe capability descriptors. */
final class ExportValidationProbeCatalog {
    private static final List<ExportValidationProbe> DEFAULT_PROBES = validateAndFreeze(Arrays.asList(
            new ExportValidationRepositoryProbe(),
            new ExportValidationRawCountsValidationProbe(),
            new ExportValidationBrowserValidationProbe(),
            new ExportValidationRenderAssetValidationProbe(),
            new ExportValidationSemanticValidationProbe(),
            new ExportValidationPathValidationProbe(),
            new ExportValidationDerivedMetricsProbe(),
            new ExportValidationPreviousDeltaProbe(),
            new ExportValidationHealthPolicyProbe(),
            new ExportValidationHealthSectionProbe()));

    private ExportValidationProbeCatalog() {}

    static List<ExportValidationProbe> defaultProbes() {
        return DEFAULT_PROBES;
    }

    static List<ExportValidationProbeDescriptor> descriptors() {
        List<ExportValidationProbeDescriptor> descriptors = new ArrayList<ExportValidationProbeDescriptor>();
        for (ExportValidationProbe probe : DEFAULT_PROBES) {
            descriptors.add(new ExportValidationProbeDescriptor(probe));
        }
        return Collections.unmodifiableList(descriptors);
    }

    private static List<ExportValidationProbe> validateAndFreeze(List<ExportValidationProbe> probes) {
        Set<String> ids = new LinkedHashSet<String>();
        for (ExportValidationProbe probe : probes) {
            if (probe == null) {
                throw new IllegalStateException("Export validation probe must not be null");
            }
            String id = probe.id();
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalStateException("Export validation probe id must be non-empty");
            }
            if (!ids.add(id)) {
                throw new IllegalStateException("Duplicate export validation probe id: " + id);
            }
            if (probe.capabilities() == null || probe.capabilities().isEmpty()) {
                throw new IllegalStateException(
                        "Export validation probe capabilities must be non-empty: " + id);
            }
        }
        return Collections.unmodifiableList(new ArrayList<ExportValidationProbe>(probes));
    }
}
