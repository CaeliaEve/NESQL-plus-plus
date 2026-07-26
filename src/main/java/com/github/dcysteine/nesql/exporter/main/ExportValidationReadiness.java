package com.github.dcysteine.nesql.exporter.main;

/** Stable compile-readiness values shared by report writers and publication validators. */
public final class ExportValidationReadiness {
    public static final String BLOCKED = "blocked";
    public static final String READY_WITH_WARNINGS = "ready-with-warnings";
    public static final String READY = "ready";

    private ExportValidationReadiness() {}

    public static boolean isPublishable(String value) {
        return READY.equals(value) || READY_WITH_WARNINGS.equals(value);
    }
}
