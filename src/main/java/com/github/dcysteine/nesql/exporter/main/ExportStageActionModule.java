package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportInitcallLevel;
import com.github.dcysteine.nesql.elysium.kernel.ExportModule;
import com.github.dcysteine.nesql.elysium.kernel.ExportStageActionRegistrar;

final class ExportStageActionModule implements ExportModule {
    private final ExportStageActionProvider provider;
    private final ExportInitcallLevel level;

    ExportStageActionModule(ExportStageActionProvider provider, ExportInitcallLevel level) {
        this.provider = provider;
        this.level = level;
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
    public ExportStageActionRegistrar<?, ?> stageActionRegistrar() {
        return provider;
    }
}
