package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportInitcallLevel;
import com.github.dcysteine.nesql.elysium.kernel.ExportModuleCatalog;
import com.github.dcysteine.nesql.elysium.kernel.ExportModule;

final class ExportStageModules {
    private ExportStageModules() {}

    static ExportModuleCatalog defaultCatalog() {
        return ExportModuleCatalog.builder()
                .add(new StaticModule("nesqlpp.export.core", ExportInitcallLevel.CORE))
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

        private StaticModule(String id, ExportInitcallLevel level) {
            this.id = id;
            this.level = level;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public ExportInitcallLevel level() {
            return level;
        }
    }
}
