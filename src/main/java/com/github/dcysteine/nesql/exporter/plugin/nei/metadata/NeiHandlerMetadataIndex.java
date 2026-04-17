package com.github.dcysteine.nesql.exporter.plugin.nei.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Top-level JSON resource model for bundled NEI handler metadata. */
public class NeiHandlerMetadataIndex {
    private int schemaVersion;
    private String source;
    private String generatedAt;
    private List<NeiHandlerMetadataEntry> entries = new ArrayList<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getSource() {
        return source;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public List<NeiHandlerMetadataEntry> getEntries() {
        if (entries == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(entries);
    }
}
