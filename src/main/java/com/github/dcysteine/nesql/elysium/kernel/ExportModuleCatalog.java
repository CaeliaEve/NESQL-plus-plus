package com.github.dcysteine.nesql.elysium.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable export-module catalog.
 *
 * <p>The catalog owns Linux-style module ordering and identity validation. Callers provide modules in
 * declaration order; the catalog validates unique ids, sorts by initcall level and id, and exposes a
 * small manifest surface for diagnostics.</p>
 */
public final class ExportModuleCatalog {
    private final List<ExportModule> modules;
    private final List<ModuleDescriptor> descriptors;

    private ExportModuleCatalog(List<ExportModule> declaredModules) {
        List<ExportModule> sortedModules = new ArrayList<ExportModule>(declaredModules);
        validateModules(sortedModules);
        sortedModules.sort(Comparator.comparing(ExportModule::level).thenComparing(ExportModule::id));
        this.modules = Collections.unmodifiableList(sortedModules);
        this.descriptors = Collections.unmodifiableList(describe(sortedModules));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ExportModule> modules() {
        return modules;
    }

    public List<ModuleDescriptor> descriptors() {
        return descriptors;
    }

    private static void validateModules(List<ExportModule> modules) {
        Set<String> ids = new LinkedHashSet<String>();
        for (ExportModule module : modules) {
            if (module == null) {
                throw new IllegalArgumentException("Export module catalog contains null module");
            }
            String id = module.id();
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Export module id must be non-empty");
            }
            if (module.level() == null) {
                throw new IllegalArgumentException("Export module level must be non-null: " + id);
            }
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate export module id: " + id);
            }
        }
    }

    private static List<ModuleDescriptor> describe(List<ExportModule> modules) {
        List<ModuleDescriptor> descriptors = new ArrayList<ModuleDescriptor>();
        for (ExportModule module : modules) {
            descriptors.add(new ModuleDescriptor(
                    module.id(),
                    module.level().name(),
                    module.stageActionRegistrar() != null,
                    module.capabilities(),
                    module.stageIds()));
        }
        return descriptors;
    }

    public static final class Builder {
        private final List<ExportModule> modules = new ArrayList<ExportModule>();

        private Builder() {}

        public Builder add(ExportModule module) {
            modules.add(module);
            return this;
        }

        public ExportModuleCatalog build() {
            return new ExportModuleCatalog(modules);
        }
    }

    public static final class ModuleDescriptor {
        public final String id;
        public final String level;
        public final boolean stageActionRegistrar;
        public final List<String> capabilities;
        public final List<String> stages;

        private ModuleDescriptor(
                String id,
                String level,
                boolean stageActionRegistrar,
                List<String> capabilities,
                List<String> stages) {
            this.id = id;
            this.level = level;
            this.stageActionRegistrar = stageActionRegistrar;
            this.capabilities = Collections.unmodifiableList(new ArrayList<String>(capabilities));
            this.stages = Collections.unmodifiableList(new ArrayList<String>(stages));
        }
    }
}
