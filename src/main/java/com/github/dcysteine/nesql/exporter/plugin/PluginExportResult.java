package com.github.dcysteine.nesql.exporter.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, machine-readable result for one plugin lifecycle phase. */
public final class PluginExportResult {
    public enum Status {
        SUCCESS("success"),
        PARTIAL("partial"),
        SKIPPED("skipped"),
        FAILED("failed");

        public final String id;

        Status(String id) {
            this.id = id;
        }
    }

    public final Status status;
    public final Map<String, Long> counts;
    public final List<Error> errors;

    private PluginExportResult(Status status, Map<String, Long> counts, List<Error> errors) {
        this.status = status;
        this.counts = Collections.unmodifiableMap(new LinkedHashMap<String, Long>(counts));
        this.errors = Collections.unmodifiableList(new ArrayList<Error>(errors));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PluginExportResult success() {
        return builder().status(Status.SUCCESS).build();
    }

    public static PluginExportResult skipped(String code, String message) {
        return builder().status(Status.SKIPPED).error(code, message, false).build();
    }

    public static PluginExportResult failed(String code, String message) {
        return builder().status(Status.FAILED).error(code, message, true).build();
    }

    public static final class Builder {
        private Status status = Status.SUCCESS;
        private boolean statusExplicit;
        private boolean hasMerged;
        private final Map<String, Long> counts = new LinkedHashMap<String, Long>();
        private final List<Error> errors = new ArrayList<Error>();

        public Builder status(Status value) {
            if (value == null) {
                throw new IllegalArgumentException("Plugin export status is required");
            }
            status = value;
            statusExplicit = true;
            return this;
        }

        public Builder count(String name, long value) {
            if (name == null || name.trim().isEmpty() || value < 0L) {
                throw new IllegalArgumentException("Plugin export count name/value is invalid");
            }
            counts.put(name, value);
            return this;
        }

        public Builder error(String code, String message, boolean core) {
            errors.add(new Error(code, message, core));
            return this;
        }

        public Builder merge(PluginExportResult result) {
            if (result == null) {
                throw new IllegalArgumentException("Plugin export result to merge is required");
            }
            if (!statusExplicit && !hasMerged) {
                status = result.status;
            } else {
                status = mergeStatus(status, result.status);
            }
            hasMerged = true;
            for (Map.Entry<String, Long> entry : result.counts.entrySet()) {
                Long current = counts.get(entry.getKey());
                counts.put(entry.getKey(), (current == null ? 0L : current) + entry.getValue());
            }
            errors.addAll(result.errors);
            return this;
        }

        public PluginExportResult build() {
            if ((status == Status.PARTIAL || status == Status.FAILED) && errors.isEmpty()) {
                throw new IllegalStateException("Partial/failed plugin export results require errors");
            }
            return new PluginExportResult(status, counts, errors);
        }

        private static Status mergeStatus(Status left, Status right) {
            if (left == Status.FAILED || right == Status.FAILED) {
                return Status.FAILED;
            }
            if (left == Status.PARTIAL || right == Status.PARTIAL) {
                return Status.PARTIAL;
            }
            if (left == Status.SKIPPED && right == Status.SKIPPED) {
                return Status.SKIPPED;
            }
            return Status.SUCCESS;
        }
    }

    public static final class Error {
        public final String code;
        public final String message;
        public final boolean core;

        private Error(String code, String message, boolean core) {
            if (code == null || code.trim().isEmpty() || message == null || message.trim().isEmpty()) {
                throw new IllegalArgumentException("Plugin export error code/message is required");
            }
            this.code = code;
            this.message = message;
            this.core = core;
        }
    }
}
