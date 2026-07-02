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
    private final List<ExportDevice> devices;
    private final List<ExportDriver> drivers;
    private final List<ModuleDescriptor> descriptors;

    private ExportModuleCatalog(List<ExportModule> declaredModules) {
        List<ExportModule> sortedModules = new ArrayList<ExportModule>(declaredModules);
        validateModules(sortedModules);
        sortedModules.sort(Comparator.comparing(ExportModule::level).thenComparing(ExportModule::id));
        this.modules = Collections.unmodifiableList(sortedModules);
        this.devices = Collections.unmodifiableList(collectDevices(sortedModules));
        this.drivers = Collections.unmodifiableList(collectDrivers(sortedModules));
        validateDevices(this.devices);
        validateDrivers(this.drivers);
        this.descriptors = Collections.unmodifiableList(describe(sortedModules));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ExportModule> modules() {
        return modules;
    }

    public List<ExportDevice> devices() {
        return devices;
    }

    public List<ExportDriver> drivers() {
        return drivers;
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

    private static List<ExportDevice> collectDevices(List<ExportModule> modules) {
        List<ExportDevice> devices = new ArrayList<ExportDevice>();
        for (ExportModule module : modules) {
            devices.addAll(module.devices());
        }
        return devices;
    }

    private static List<ExportDriver> collectDrivers(List<ExportModule> modules) {
        List<ExportDriver> drivers = new ArrayList<ExportDriver>();
        for (ExportModule module : modules) {
            drivers.addAll(module.drivers());
        }
        return drivers;
    }

    private static void validateDevices(List<ExportDevice> devices) {
        Set<String> ids = new LinkedHashSet<String>();
        for (ExportDevice device : devices) {
            if (device == null) {
                throw new IllegalArgumentException("Export module catalog contains null device");
            }
            String identity = device.busId() + ":" + device.id();
            if (!ids.add(identity)) {
                throw new IllegalArgumentException("Duplicate export device id: " + identity);
            }
        }
    }

    private static void validateDrivers(List<ExportDriver> drivers) {
        Set<String> ids = new LinkedHashSet<String>();
        for (ExportDriver driver : drivers) {
            if (driver == null) {
                throw new IllegalArgumentException("Export module catalog contains null driver");
            }
            String id = driver.id();
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Export driver id must be non-empty");
            }
            if (driver.busId() == null || driver.busId().trim().isEmpty()) {
                throw new IllegalArgumentException("Export driver bus id must be non-empty: " + id);
            }
            String identity = driver.busId() + ":" + id;
            if (!ids.add(identity)) {
                throw new IllegalArgumentException("Duplicate export driver id: " + identity);
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
                    module.stageIds(),
                    describeDevices(module.devices()),
                    describeDrivers(module.drivers())));
        }
        return descriptors;
    }

    private static List<DeviceDescriptor> describeDevices(List<ExportDevice> devices) {
        List<DeviceDescriptor> descriptors = new ArrayList<DeviceDescriptor>();
        for (ExportDevice device : devices) {
            descriptors.add(new DeviceDescriptor(
                    device.busId(),
                    device.id(),
                    device.required(),
                    device.capabilities()));
        }
        return descriptors;
    }

    private static List<DriverDescriptor> describeDrivers(List<ExportDriver> drivers) {
        List<DriverDescriptor> descriptors = new ArrayList<DriverDescriptor>();
        for (ExportDriver driver : drivers) {
            descriptors.add(new DriverDescriptor(
                    driver.busId(),
                    driver.id(),
                    driver.capabilities()));
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
        public final List<DeviceDescriptor> devices;
        public final List<DriverDescriptor> drivers;

        private ModuleDescriptor(
                String id,
                String level,
                boolean stageActionRegistrar,
                List<String> capabilities,
                List<String> stages,
                List<DeviceDescriptor> devices,
                List<DriverDescriptor> drivers) {
            this.id = id;
            this.level = level;
            this.stageActionRegistrar = stageActionRegistrar;
            this.capabilities = Collections.unmodifiableList(new ArrayList<String>(capabilities));
            this.stages = Collections.unmodifiableList(new ArrayList<String>(stages));
            this.devices = Collections.unmodifiableList(new ArrayList<DeviceDescriptor>(devices));
            this.drivers = Collections.unmodifiableList(new ArrayList<DriverDescriptor>(drivers));
        }
    }

    public static final class DeviceDescriptor {
        public final String busId;
        public final String id;
        public final boolean required;
        public final List<String> capabilities;

        private DeviceDescriptor(String busId, String id, boolean required, List<String> capabilities) {
            this.busId = busId;
            this.id = id;
            this.required = required;
            this.capabilities = Collections.unmodifiableList(new ArrayList<String>(capabilities));
        }
    }

    public static final class DriverDescriptor {
        public final String busId;
        public final String id;
        public final List<String> capabilities;

        private DriverDescriptor(String busId, String id, List<String> capabilities) {
            this.busId = busId;
            this.id = id;
            this.capabilities = Collections.unmodifiableList(new ArrayList<String>(capabilities));
        }
    }
}
