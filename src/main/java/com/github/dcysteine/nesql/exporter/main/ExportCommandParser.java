package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;

/** Parses the /nesql command into a single explicit command request. */
final class ExportCommandParser {
    static final String USAGE = "/nesql [filename suffix] [--semantic-check|--full-export|--native-ui-export]";
    static final String MODE_CONFLICT_MESSAGE =
            "Choose only one of --semantic-check, --full-export, or --native-ui-export.";

    private ExportCommandParser() {}

    static ExportCommandParseResult parse(String[] args) {
        if (args.length > 2) {
            return ExportCommandParseResult.error("Too many parameters! Usage: " + USAGE);
        }

        ExportCommandMode mode = ExportCommandMode.GUIDED;
        String repositoryName = null;
        for (String arg : args) {
            ExportCommandModeSpec modeSpec = ExportCommandModeSpec.fromFlag(arg);
            if (modeSpec != null) {
                if (mode != ExportCommandMode.GUIDED) {
                    return ExportCommandParseResult.error(MODE_CONFLICT_MESSAGE);
                }
                mode = modeSpec.mode;
                continue;
            }
            if (repositoryName == null) {
                repositoryName = arg;
                continue;
            }
            return ExportCommandParseResult.error("Too many parameters! Usage: " + USAGE);
        }

        if (repositoryName == null || repositoryName.trim().length() == 0) {
            repositoryName = ConfigOptions.REPOSITORY_NAME.get();
        }
        return ExportCommandParseResult.ok(new ExportCommandRequest(repositoryName, mode));
    }

    static final class ExportCommandParseResult {
        final ExportCommandRequest request;
        final String errorMessage;

        private ExportCommandParseResult(ExportCommandRequest request, String errorMessage) {
            this.request = request;
            this.errorMessage = errorMessage;
        }

        static ExportCommandParseResult ok(ExportCommandRequest request) {
            return new ExportCommandParseResult(request, null);
        }

        static ExportCommandParseResult error(String errorMessage) {
            return new ExportCommandParseResult(null, errorMessage);
        }

        boolean ok() {
            return request != null;
        }
    }
}
