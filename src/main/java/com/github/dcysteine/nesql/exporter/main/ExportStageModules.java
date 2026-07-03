package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportDevice;
import com.github.dcysteine.nesql.elysium.kernel.ExportInitcallLevel;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ExportStageModules {
    private static final List<ModuleDescriptor> MODULE_DESCRIPTORS = validateAndFreeze(Arrays.asList(
            ModuleDescriptor.staticModule(
                    "nesqlpp.export.core",
                    ExportInitcallLevel.CORE,
                    Collections.singletonList(ExportDevice.required(
                            ExportStageActionModule.STAGE_ACTION_BUS_ID,
                            ExportStageActionModule.STAGE_ACTION_DEVICE_ID,
                            ExportStageActionProvider.capabilityList(
                                    ExportStageActionModule.STAGE_ACTION_DISPATCH_CAPABILITY)))),
            ModuleDescriptor.stageAction(
                    "nesqlpp.export.lifecycle",
                    ExportInitcallLevel.SUBSYS,
                    LifecycleStageActionProvider::new),
            ModuleDescriptor.stageAction(
                    "nesqlpp.export.raw-facts",
                    ExportInitcallLevel.FACTS,
                    RawFactStageActionProvider::new),
            ModuleDescriptor.stageAction(
                    "nesqlpp.export.render-assets",
                    ExportInitcallLevel.RENDER,
                    RenderStageActionProvider::new),
            ModuleDescriptor.stageAction(
                    "nesqlpp.export.native-ui",
                    ExportInitcallLevel.NATIVE_UI,
                    NativeUiStageActionProvider::new),
            ModuleDescriptor.staticModule(
                    "nesqlpp.export.validation",
                    ExportInitcallLevel.VALIDATE,
                    Collections.<ExportDevice>emptyList())));

    private ExportStageModules() {}

    static ExportModuleCatalog defaultCatalog() {
        ExportModuleCatalog.Builder builder = ExportModuleCatalog.builder();
        for (ModuleDescriptor descriptor : MODULE_DESCRIPTORS) {
            builder.add(descriptor.createModule());
        }
        return builder.build();
    }

    private static List<ModuleDescriptor> validateAndFreeze(List<ModuleDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Export stage module descriptor catalog must not be empty");
        }
        Set<String> ids = new LinkedHashSet<String>();
        List<ModuleDescriptor> validated = new ArrayList<ModuleDescriptor>();
        for (ModuleDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Export stage module descriptor must not be null");
            }
            descriptor.validate();
            if (!ids.add(descriptor.id)) {
                throw new IllegalStateException("Duplicate export stage module descriptor id: " + descriptor.id);
            }
            validated.add(descriptor);
        }
        return Collections.unmodifiableList(validated);
    }

    private interface StageActionProviderFactory {
        ExportStageActionProvider create();
    }

    private static final class ModuleDescriptor {
        private final String id;
        private final ExportInitcallLevel level;
        private final List<ExportDevice> devices;
        private final StageActionProviderFactory stageActionProviderFactory;

        private ModuleDescriptor(
                String id,
                ExportInitcallLevel level,
                List<ExportDevice> devices,
                StageActionProviderFactory stageActionProviderFactory) {
            this.id = id;
            this.level = level;
            this.devices = devices == null
                    ? Collections.<ExportDevice>emptyList()
                    : Collections.unmodifiableList(new ArrayList<ExportDevice>(devices));
            this.stageActionProviderFactory = stageActionProviderFactory;
        }

        private static ModuleDescriptor staticModule(
                String id,
                ExportInitcallLevel level,
                List<ExportDevice> devices) {
            return new ModuleDescriptor(id, level, devices, null);
        }

        private static ModuleDescriptor stageAction(
                String id,
                ExportInitcallLevel level,
                StageActionProviderFactory providerFactory) {
            return new ModuleDescriptor(id, level, Collections.<ExportDevice>emptyList(), providerFactory);
        }

        private void validate() {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalStateException("Export stage module descriptor id must be non-empty");
            }
            if (level == null) {
                throw new IllegalStateException("Export stage module descriptor level must be non-null: " + id);
            }
            if (stageActionProviderFactory == null && devices == null) {
                throw new IllegalStateException("Export static module descriptor devices must not be null: " + id);
            }
        }

        private ExportModule createModule() {
            if (stageActionProviderFactory == null) {
                return new StaticModule(id, level, devices);
            }
            ExportStageActionProvider provider = stageActionProviderFactory.create();
            if (!id.equals(provider.id())) {
                throw new IllegalStateException(
                        "Export stage provider id does not match module descriptor: "
                                + id + " != " + provider.id());
            }
            return new ExportStageActionModule(provider, level);
        }
    }

    private static final class StaticModule implements ExportModule {
        private final String id;
        private final ExportInitcallLevel level;
        private final List<ExportDevice> devices;

        private StaticModule(String id, ExportInitcallLevel level, List<ExportDevice> devices) {
            this.id = id;
            this.level = level;
            this.devices = devices;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public ExportInitcallLevel level() {
            return level;
        }

        @Override
        public List<ExportDevice> devices() {
            return devices;
        }
    }
}
