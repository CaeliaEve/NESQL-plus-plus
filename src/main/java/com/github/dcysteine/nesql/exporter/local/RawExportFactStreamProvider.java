package com.github.dcysteine.nesql.exporter.local;

import java.io.IOException;

interface RawExportFactStreamProvider {
    void write(RawExportFactStreamContext context, RawFactCounts counts) throws IOException;
}
