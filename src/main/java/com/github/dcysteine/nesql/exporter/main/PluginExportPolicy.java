package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;
import com.github.dcysteine.nesql.sql.Plugin;

/** Central fail-closed publication policy for plugin lifecycle results. */
final class PluginExportPolicy {
    private PluginExportPolicy() {}

    static void requirePublishable(Plugin plugin, String phase, PluginExportResult result) {
        if (result == null) {
            throw new IllegalStateException("Plugin export result is missing: " + plugin.name() + "/" + phase);
        }
        if (result.status == PluginExportResult.Status.FAILED) {
            throw failure(plugin, phase, result, "failed");
        }
        if (result.status == PluginExportResult.Status.PARTIAL && !allowsPartial(plugin, phase)) {
            throw failure(plugin, phase, result, "partial result is not allowed");
        }
    }

    private static boolean allowsPartial(Plugin plugin, String phase) {
        // No core collection plugin currently has a reviewed partial-publication exception.
        return false;
    }

    private static IllegalStateException failure(
            Plugin plugin,
            String phase,
            PluginExportResult result,
            String reason) {
        String detail = result.errors.isEmpty()
                ? "no error detail"
                : result.errors.get(0).code + ": " + result.errors.get(0).message;
        return new IllegalStateException(
                "Plugin export " + reason + " for " + plugin.name() + "/" + phase + " (" + detail + ")");
    }
}
