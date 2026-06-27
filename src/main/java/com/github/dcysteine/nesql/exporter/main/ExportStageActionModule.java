package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportInitcallLevel;
import com.github.dcysteine.nesql.elysium.kernel.ExportModule;
import com.github.dcysteine.nesql.elysium.kernel.ExportStageActionRegistrar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public ExportStageActionRegistrar<?, ?> stageActionRegistrar() {
        return provider;
    }
}
