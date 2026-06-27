package com.github.dcysteine.nesql.exporter.local;

import java.io.IOException;

interface RawExportFactStreamProvider {
    String id();

    void write(RawExportFactStreamContext context, RawFactCounts counts) throws IOException;
}
