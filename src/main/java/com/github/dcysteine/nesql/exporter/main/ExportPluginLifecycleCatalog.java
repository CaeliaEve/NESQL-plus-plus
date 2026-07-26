package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Descriptor-owned plugin lifecycle phase catalog for the export runtime. */
final class ExportPluginLifecycleCatalog {
    static final String PHASE_INITIALIZE = "initialize";
    static final String PHASE_PROCESS = "process";
    static final String PHASE_POST_PROCESS = "postProcess";

    private static final List<PhaseDescriptor> PHASES = validateAndFreeze(Arrays.asList(
            new PhaseDescriptor(
                    PHASE_INITIALIZE,
                    "Construct recipe types and register plugin-local export helpers.",
                    PluginExporter::initializeResult),
            new PhaseDescriptor(
                    PHASE_PROCESS,
                    "Persist plugin recipes, items, fluids, and plugin-owned export facts.",
                    PluginExporter::processResult),
            new PhaseDescriptor(
                    PHASE_POST_PROCESS,
                    "Run cross-plugin work that requires persisted items, fluids, and recipes.",
                    PluginExporter::postProcessResult)));

    private ExportPluginLifecycleCatalog() {}

    static List<PhaseDescriptor> phases() {
        return PHASES;
    }

    private static List<PhaseDescriptor> validateAndFreeze(List<PhaseDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Plugin lifecycle phase catalog must not be empty");
        }
        Set<String> ids = new LinkedHashSet<String>();
        List<PhaseDescriptor> validated = new ArrayList<PhaseDescriptor>();
        for (PhaseDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Plugin lifecycle phase descriptor must not be null");
            }
            requireNonEmpty("Plugin lifecycle phase id", descriptor.id);
            requireNonEmpty("Plugin lifecycle phase contract", descriptor.contract);
            if (descriptor.invoker == null) {
                throw new IllegalStateException(
                        "Plugin lifecycle phase invoker must not be null: " + descriptor.id);
            }
            if (!ids.add(descriptor.id)) {
                throw new IllegalStateException(
                        "Duplicate plugin lifecycle phase id: " + descriptor.id);
            }
            validated.add(descriptor);
        }
        return Collections.unmodifiableList(validated);
    }

    private static void requireNonEmpty(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty");
        }
    }

    interface PhaseInvoker {
        PluginExportResult invoke(PluginExporter exporter);
    }

    static final class PhaseDescriptor {
        private final String id;
        private final String contract;
        private final PhaseInvoker invoker;

        private PhaseDescriptor(String id, String contract, PhaseInvoker invoker) {
            this.id = id;
            this.contract = contract;
            this.invoker = invoker;
        }

        String id() {
            return id;
        }

        String contract() {
            return contract;
        }

        PluginExportResult invoke(PluginExporter exporter) {
            return invoker.invoke(exporter);
        }
    }
}
