package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportStageActionRegistrar;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

interface ExportStageActionProvider extends ExportStageActionRegistrar<EnumMap<ExportStage, ExportStageAction>, ExportStageActionContext> {
    String id();

    List<ExportStage> stages();

    List<String> capabilities();

    @Override
    void register(EnumMap<ExportStage, ExportStageAction> actions, ExportStageActionContext context);

    static List<ExportStage> stageList(ExportStage... stages) {
        return Collections.unmodifiableList(Arrays.asList(stages));
    }

    static List<String> capabilityList(String... capabilities) {
        return Collections.unmodifiableList(Arrays.asList(capabilities));
    }
}
