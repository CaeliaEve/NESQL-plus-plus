package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.DriverProbeResult;
import com.github.dcysteine.nesql.elysium.kernel.ExportDevice;
import com.github.dcysteine.nesql.elysium.kernel.ExportDriver;
import com.github.dcysteine.nesql.elysium.kernel.ExportInitcallLevel;
import com.github.dcysteine.nesql.elysium.kernel.ExportKernelContext;
import com.github.dcysteine.nesql.elysium.kernel.ExportModule;
import com.github.dcysteine.nesql.elysium.kernel.ExportStageActionRegistrar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ExportStageActionModule implements ExportModule {
    static final String STAGE_ACTION_BUS_ID = "export-stage-action";
    static final String STAGE_ACTION_DEVICE_ID = "stage-action-dispatch";

    private final ExportStageActionProvider provider;
    private final ExportInitcallLevel level;
    private final List<ExportDriver> drivers;

    ExportStageActionModule(ExportStageActionProvider provider, ExportInitcallLevel level) {
        this.provider = provider;
        this.level = level;
        this.drivers = Collections.<ExportDriver>singletonList(new StageActionProviderDriver(provider));
    }

    @Override
    public String id() {
        return provider.id();
    }

    @Override
    public ExportInitcallLevel level() {
        return level;
    }

    @Override
    public List<String> capabilities() {
        return provider.capabilities();
    }

    @Override
    public List<String> stageIds() {
        List<String> stageIds = new ArrayList<String>();
        for (ExportStage stage : provider.stages()) {
            stageIds.add(stage.name());
        }
        return Collections.unmodifiableList(stageIds);
    }

    @Override
    public List<ExportDriver> drivers() {
        return drivers;
    }

    @Override
    public ExportStageActionRegistrar<?, ?> stageActionRegistrar() {
        return provider;
    }

    private static final class StageActionProviderDriver implements ExportDriver {
        private final ExportStageActionProvider provider;

        private StageActionProviderDriver(ExportStageActionProvider provider) {
            this.provider = provider;
        }

        @Override
        public String id() {
            return provider.id() + ".driver";
        }

        @Override
        public String busId() {
            return STAGE_ACTION_BUS_ID;
        }

        @Override
        public List<String> capabilities() {
            return provider.capabilities();
        }

        @Override
        public DriverProbeResult probe(ExportDevice device, ExportKernelContext context) {
            if (!STAGE_ACTION_BUS_ID.equals(device.busId())
                    || !STAGE_ACTION_DEVICE_ID.equals(device.id())) {
                return DriverProbeResult.unsupported("device is not the stage action dispatcher");
            }
            if (provider.stages().isEmpty()) {
                return DriverProbeResult.unsupported("provider declares no export stages");
            }
            return DriverProbeResult.supported("provider declares export stage actions");
        }
    }
}
