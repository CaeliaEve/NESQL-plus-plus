package com.github.dcysteine.nesql.exporter.main;

import jakarta.persistence.EntityTransaction;

/**
 * Shared session bundle for a live export execution.
 */
public final class ExportSession implements AutoCloseable {
    public final ExportRuntime runtime;
    public final EntityTransaction transaction;

    public ExportSession(ExportRuntime runtime, EntityTransaction transaction) {
        this.runtime = runtime;
        this.transaction = transaction;
    }

    @Override
    public void close() {
        runtime.close();
    }
}
