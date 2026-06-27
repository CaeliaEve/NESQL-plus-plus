package com.github.dcysteine.nesql.elysium.kernel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExportModuleRegistry {
    private ExportModuleRegistry() {}

    public static List<ExportModule> defaultModules() {
        List<ExportModule> modules = new ArrayList<ExportModule>();
        modules.add(new StaticExportModule("nesqlpp.export.core", ExportInitcallLevel.CORE));
        modules.add(new StaticExportModule("nesqlpp.export.stage-pipeline", ExportInitcallLevel.SUBSYS));
        modules.add(new StaticExportModule("nesqlpp.export.raw-facts", ExportInitcallLevel.FACTS));
        modules.add(new StaticExportModule("nesqlpp.export.render-assets", ExportInitcallLevel.RENDER));
        modules.add(new StaticExportModule("nesqlpp.export.native-ui", ExportInitcallLevel.NATIVE_UI));
        modules.add(new StaticExportModule("nesqlpp.export.validation", ExportInitcallLevel.VALIDATE));
        return Collections.unmodifiableList(modules);
    }

    private static final class StaticExportModule implements ExportModule {
        private final String id;
        private final ExportInitcallLevel level;

        private StaticExportModule(String id, ExportInitcallLevel level) {
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
