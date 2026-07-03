package com.github.dcysteine.nesql.exporter.registry;

import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.sql.Plugin;

import java.util.EnumMap;
import java.util.Map;

/** Runtime consumer of the descriptor-owned plugin registry catalog. */
public class PluginRegistry {
    private final Map<Plugin, PluginExporter> activePlugins = new EnumMap<>(Plugin.class);

    /** Constructs plugins whose dependencies are met. Returns a list of activated plugins. */
    public Map<Plugin, PluginExporter> initialize(ExporterState exporterState) {
        PluginRegistryCatalog.entries().stream()
                .filter(RegistryEntry::areDependenciesSatisfied)
                .filter(RegistryEntry::isEnabled)
                .forEach(
                        entry ->
                                activePlugins.put(
                                        entry.getPlugin(), entry.instantiate(exporterState)));

        if (!activePlugins.containsKey(Plugin.BASE)) {
            throw new IllegalStateException("base plugin must be enabled!");
        }
        return activePlugins;
    }

    public Map<Plugin, PluginExporter> getActivePlugins() {
        return activePlugins;
    }

    public void initializePlugins() {
        activePlugins.values().forEach(PluginExporter::initialize);
    }

    public void processPlugins() {
        activePlugins.values().forEach(PluginExporter::process);
    }

    public void postProcessPlugins() {
        activePlugins.values().forEach(PluginExporter::postProcess);
    }
}
