package com.github.dcysteine.nesql.exporter.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

interface RawExportFactStreamProvider {
    String id();

    List<String> capabilities();

    List<String> outputFamilies();

    void write(RawExportFactStreamContext context, RawFactCounts counts) throws IOException;

    static List<String> list(String... values) {
        return Collections.unmodifiableList(Arrays.asList(values));
    }
}
