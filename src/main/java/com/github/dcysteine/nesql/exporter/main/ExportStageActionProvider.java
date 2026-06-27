package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportStageActionRegistrar;

import java.util.EnumMap;

interface ExportStageActionProvider extends ExportStageActionRegistrar<EnumMap<ExportStage, ExportStageAction>, ExportStageActionContext> {
    String id();

    @Override
    void register(EnumMap<ExportStage, ExportStageAction> actions, ExportStageActionContext context);
}
