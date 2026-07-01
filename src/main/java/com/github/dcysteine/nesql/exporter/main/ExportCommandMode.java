package com.github.dcysteine.nesql.exporter.main;

/** Runtime mode selected by the /nesql command parser. */
enum ExportCommandMode {
    GUIDED,
    SEMANTIC_CHECK,
    FULL_EXPORT,
    NATIVE_UI_EXPORT
}
