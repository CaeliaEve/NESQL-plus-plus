package com.github.dcysteine.nesql.exporter.main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Stable descriptor for a validation probe published through the validation catalog. */
final class ExportValidationProbeDescriptor {
    final String id;
    final List<String> capabilities;

    ExportValidationProbeDescriptor(ExportValidationProbe probe) {
        this.id = probe.id();
        this.capabilities = Collections.unmodifiableList(new ArrayList<String>(probe.capabilities()));
    }
}
