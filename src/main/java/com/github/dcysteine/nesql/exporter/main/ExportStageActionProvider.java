package com.github.dcysteine.nesql.exporter.main;

import java.util.EnumMap;

interface ExportStageActionProvider {
    String id();

    void register(EnumMap<ExportStage, ExportStageAction> actions, ExportStageActionContext context);
}
