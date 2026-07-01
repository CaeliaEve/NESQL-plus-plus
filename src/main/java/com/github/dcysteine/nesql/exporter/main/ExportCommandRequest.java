package com.github.dcysteine.nesql.exporter.main;

/** Immutable parsed command request for /nesql. */
final class ExportCommandRequest {
    final String repositoryName;
    final ExportCommandMode mode;

    ExportCommandRequest(String repositoryName, ExportCommandMode mode) {
        this.repositoryName = repositoryName;
        this.mode = mode;
    }
}
