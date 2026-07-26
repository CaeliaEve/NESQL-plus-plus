package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;

/** Collects handler-level failures into the typed NEI batch result. */
final class NeiRecipeBatchOutcome {
    private final PluginExportResult.Builder result = PluginExportResult.builder();
    private int failedHandlers;
    private PluginExportResult itemUniverseResult = PluginExportResult.success();

    void recordItemUniverseResult(PluginExportResult value) {
        if (value == null) {
            throw new IllegalArgumentException("NEI item universe result is required");
        }
        itemUniverseResult = value;
    }

    void recordFailure(String handlerName, String handlerClass, Throwable error) {
        failedHandlers++;
        result.error(
                "nei-handler-export-failed",
                handlerName + " (" + handlerClass + "): " + summarize(error),
                true);
    }

    int failedHandlers() {
        return failedHandlers;
    }

    PluginExportResult build(int totalHandlers, int processedHandlers, int recipesExported) {
        PluginExportResult handlerResult = result
                .status(failedHandlers == 0
                        ? PluginExportResult.Status.SUCCESS
                        : PluginExportResult.Status.PARTIAL)
                .count("handlersTotal", totalHandlers)
                .count("handlersProcessed", processedHandlers)
                .count("handlersFailed", failedHandlers)
                .count("recipesExported", recipesExported)
                .build();
        return PluginExportResult.builder()
                .merge(itemUniverseResult)
                .merge(handlerResult)
                .build();
    }

    private static String summarize(Throwable error) {
        StringBuilder summary = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) {
                summary.append(" <- ");
            }
            summary.append(current.getClass().getName())
                    .append(": ")
                    .append(current.getMessage() == null ? "<no message>" : current.getMessage());
            current = current.getCause();
            depth++;
        }
        return summary.toString();
    }
}
