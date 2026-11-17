package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.JsonObject;

/** Read-only native catalogues. They never enter the consuming recipe graph. */
interface NativeReferences extends AutoCloseable {
    int size();
    String domain();
    void verify();
    /** Full source-drift proof once before sealing; batch verify may use inexpensive guards. */
    default void finishVerification() { verify(); }
    JsonObject counts();
    void capture(int index, Facts facts);
    default java.util.List<Structures.Machine> structures(int start, int end) { return java.util.Collections.emptyList(); }
    @Override default void close() {}
}
