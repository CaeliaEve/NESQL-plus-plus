package com.github.dcysteine.nesql.exporter.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class RawExportFactStreamDescriptor {
    final String id;
    final List<String> capabilities;
    final List<String> outputFamilies;

    RawExportFactStreamDescriptor(
            String id,
            List<String> capabilities,
            List<String> outputFamilies) {
        this.id = id;
        this.capabilities = Collections.unmodifiableList(new ArrayList<String>(capabilities));
        this.outputFamilies = Collections.unmodifiableList(new ArrayList<String>(outputFamilies));
    }
}
