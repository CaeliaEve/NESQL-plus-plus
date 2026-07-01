package com.github.dcysteine.nesql.exporter.main;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Declares the supported /nesql command modes and their flag aliases. */
final class ExportCommandModeSpec {
    static final ExportCommandModeSpec GUIDED = new ExportCommandModeSpec(
            ExportCommandMode.GUIDED,
            "guided",
            Collections.<String>emptyList());
    static final ExportCommandModeSpec SEMANTIC_CHECK = new ExportCommandModeSpec(
            ExportCommandMode.SEMANTIC_CHECK,
            "semantic-check",
            Arrays.asList("--semantic-check", "--semantic-only"));
    static final ExportCommandModeSpec FULL_EXPORT = new ExportCommandModeSpec(
            ExportCommandMode.FULL_EXPORT,
            "full-export",
            Arrays.asList("--full-export", "--full"));
    static final ExportCommandModeSpec NATIVE_UI_EXPORT = new ExportCommandModeSpec(
            ExportCommandMode.NATIVE_UI_EXPORT,
            "native-ui-export",
            Arrays.asList("--native-ui-export", "--native-ui"));

    static final List<ExportCommandModeSpec> FLAGGED_MODES = Collections.unmodifiableList(Arrays.asList(
            SEMANTIC_CHECK,
            FULL_EXPORT,
            NATIVE_UI_EXPORT));

    final ExportCommandMode mode;
    final String id;
    final List<String> flags;

    private ExportCommandModeSpec(ExportCommandMode mode, String id, List<String> flags) {
        this.mode = mode;
        this.id = id;
        this.flags = Collections.unmodifiableList(flags);
    }

    static ExportCommandModeSpec fromFlag(String value) {
        for (ExportCommandModeSpec spec : FLAGGED_MODES) {
            for (String flag : spec.flags) {
                if (flag.equalsIgnoreCase(value)) {
                    return spec;
                }
            }
        }
        return null;
    }
}
