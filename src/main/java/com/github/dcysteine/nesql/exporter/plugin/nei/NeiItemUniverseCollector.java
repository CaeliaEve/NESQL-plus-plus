package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fail-closed normalization for the core NEI ItemList universe. */
final class NeiItemUniverseCollector {
    private NeiItemUniverseCollector() {}

    static <S, T> Result<T> collect(Iterable<S> entries, Normalizer<S, T> normalizer) {
        if (entries == null || normalizer == null) {
            throw new IllegalArgumentException("NEI item universe entries and normalizer are required");
        }

        Map<String, T> unique = new LinkedHashMap<String, T>();
        List<Failure> failures = new ArrayList<Failure>();
        int total = 0;
        for (S entry : entries) {
            int index = total++;
            try {
                Normalized<T> normalized = normalizer.normalize(entry);
                if (normalized == null || normalized.value == null || normalized.key == null
                        || normalized.key.trim().isEmpty()) {
                    throw new IllegalStateException("normalizer returned an incomplete item identity");
                }
                if (!unique.containsKey(normalized.key)) {
                    unique.put(normalized.key, normalized.value);
                }
            } catch (Throwable failure) {
                failures.add(new Failure(index, failure));
            }
        }
        return new Result<T>(total, unique, failures);
    }

    interface Normalizer<S, T> {
        Normalized<T> normalize(S entry);
    }

    static final class Normalized<T> {
        private final T value;
        private final String key;

        Normalized(T value, String key) {
            this.value = value;
            this.key = key;
        }
    }

    static final class Result<T> {
        private final int total;
        private final Map<String, T> keyedValues;
        private final List<T> values;
        private final List<Failure> failures;

        private Result(int total, Map<String, T> keyedValues, List<Failure> failures) {
            this.total = total;
            this.keyedValues = Collections.unmodifiableMap(new LinkedHashMap<String, T>(keyedValues));
            this.values = Collections.unmodifiableList(new ArrayList<T>(keyedValues.values()));
            this.failures = Collections.unmodifiableList(failures);
        }

        List<T> values() {
            return values;
        }

        Map<String, T> keyedValues() {
            return keyedValues;
        }

        int failureCount() {
            return failures.size();
        }

        PluginExportResult toPluginExportResult() {
            PluginExportResult.Builder result = PluginExportResult.builder()
                    .status(total == 0
                            ? PluginExportResult.Status.FAILED
                            : failures.isEmpty()
                            ? PluginExportResult.Status.SUCCESS
                            : values.isEmpty()
                                    ? PluginExportResult.Status.FAILED
                                    : PluginExportResult.Status.PARTIAL)
                    .count("itemUniverseEntriesTotal", total)
                    .count("itemUniverseEntriesNormalized", values.size())
                    .count("itemUniverseEntriesFailed", failures.size());
            if (total == 0) {
                result.error(
                        "nei-item-universe-empty",
                        "NEI ItemList contains no core entries",
                        true);
            }
            for (Failure failure : failures) {
                result.error(
                        "nei-item-universe-normalization-failed",
                        failure.message(),
                        true);
            }
            return result.build();
        }
    }

    private static final class Failure {
        private final int index;
        private final Throwable cause;

        private Failure(int index, Throwable cause) {
            this.index = index;
            this.cause = cause;
        }

        private String message() {
            String detail = cause.getMessage() == null ? "<no message>" : cause.getMessage();
            return "NEI ItemList entry " + index + " failed normalization: "
                    + cause.getClass().getName() + ": " + detail;
        }
    }
}
