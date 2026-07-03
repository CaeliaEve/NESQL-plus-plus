package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportDevice;
import com.github.dcysteine.nesql.elysium.kernel.ExportInitcallLevel;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportModule;

import java.util.Collections;
import java.util.List;

final class ExportStageModules {
    private ExportStageModules() {}

    static ExportModuleCatalog defaultCatalog() {
        return ExportModuleCatalog.builder()
                .add(new StaticModule(
                        "nesqlpp.export.core",
                        ExportInitcallLevel.CORE,
                        Collections.singletonList(ExportDevice.required(
                                ExportStageActionModule.STAGE_ACTION_BUS_ID,
                                ExportStageActionModule.STAGE_ACTION_DEVICE_ID,
                                ExportStageActionProvider.capabilityList(
                                        ExportStageActionModule.STAGE_ACTION_DISPATCH_CAPABILITY)))))
                .add(new ExportStageActionModule(new LifecycleStageActionProvider(), ExportInitcallLevel.SUBSYS))
                .add(new ExportStageActionModule(new RawFactStageActionProvider(), ExportInitcallLevel.FACTS))
                .add(new ExportStageActionModule(new RenderStageActionProvider(), ExportInitcallLevel.RENDER))
                .add(new ExportStageActionModule(new NativeUiStageActionProvider(), ExportInitcallLevel.NATIVE_UI))
                .add(new StaticModule("nesqlpp.export.validation", ExportInitcallLevel.VALIDATE))
                .build();
    }

    private static final class StaticModule implements ExportModule {
        private final String id;
        private final ExportInitcallLevel level;
        private final List<ExportDevice> devices;

        private StaticModule(String id, ExportInitcallLevel level) {
            this(id, level, Collections.<ExportDevice>emptyList());
        }

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
