package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportInitcallLevel;
import com.github.dcysteine.nesql.elysium.kernel.ExportModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ExportStageModules {
    private ExportStageModules() {}

    static List<ExportModule> defaultModules() {
        List<ExportModule> modules = new ArrayList<ExportModule>();
        modules.add(new StaticModule("nesqlpp.export.core", ExportInitcallLevel.CORE));
        modules.add(new ExportStageActionModule(new LifecycleStageActionProvider(), ExportInitcallLevel.SUBSYS));
        modules.add(new ExportStageActionModule(new RawFactStageActionProvider(), ExportInitcallLevel.FACTS));
        modules.add(new ExportStageActionModule(new RenderStageActionProvider(), ExportInitcallLevel.RENDER));
        modules.add(new ExportStageActionModule(new NativeUiStageActionProvider(), ExportInitcallLevel.NATIVE_UI));
        modules.add(new StaticModule("nesqlpp.export.validation", ExportInitcallLevel.VALIDATE));
        return Collections.unmodifiableList(modules);
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
