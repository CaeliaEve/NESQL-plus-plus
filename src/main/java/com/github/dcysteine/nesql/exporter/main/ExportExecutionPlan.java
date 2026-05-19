package com.github.dcysteine.nesql.exporter.main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transitional execution-plan surface for NESQL++ export profiles.
 *
 * <p>Current exporters still execute their own flows. This class captures the
 * intended stage order for each profile so the later orchestrator refactor can
 * replace duplicated command logic without rediscovering semantics.</p>
 */
public final class ExportExecutionPlan {
    public final ExportProfile profile;
    public final List<ExportStage> stages;

    private ExportExecutionPlan(ExportProfile profile, List<ExportStage> stages) {
        this.profile = profile;
        this.stages = Collections.unmodifiableList(stages);
    }

    public static ExportExecutionPlan fromProfile(ExportProfile profile) {
        List<ExportStage> stages = new ArrayList<>();
        stages.add(ExportStage.INITIALIZE_REPOSITORY);
        stages.add(ExportStage.INITIALIZE_DATABASE);
        stages.add(ExportStage.INITIALIZE_PLUGINS);
        stages.add(ExportStage.COLLECT_PLUGIN_DATA);

        if (profile.writeModBasedItems) {
            stages.add(ExportStage.WRITE_MOD_BASED_ITEMS);
        }
        if (profile.writeModBasedRecipes) {
            stages.add(ExportStage.WRITE_MOD_BASED_RECIPES);
        }
        if (profile.writeCanonicalSnapshot) {
            stages.add(ExportStage.WRITE_CANONICAL_SNAPSHOT);
        }
        if (profile.writeModBasedItems) {
            stages.add(ExportStage.WRITE_BROWSER_LAYOUT_INDEX);
        }

        if (profile == ExportProfile.FULL_V104) {
            stages.add(ExportStage.WRITE_MULTIBLOCK_BLUEPRINTS);
            stages.add(ExportStage.WRITE_BLOCK_FACE_METADATA);
        }

        if (profile.renderImages) {
            stages.add(ExportStage.RENDER_IMAGES);
            stages.add(ExportStage.WRITE_RENDER_ASSET_MANIFEST);
            stages.add(ExportStage.WRITE_ANIMATION_MANIFEST);
            stages.add(ExportStage.WRITE_ATLAS_PACKS);
            stages.add(ExportStage.WRITE_ANIMATED_ATLAS_PACKS);
            stages.add(ExportStage.WRITE_ATLAS_REGISTRY);
            stages.add(ExportStage.WRITE_RENDER_INDEX);
            stages.add(ExportStage.WRITE_BROWSER_ATLAS_INDEX);
        }

        if (profile.commitDatabase) {
            stages.add(ExportStage.COMMIT_DATABASE);
        } else {
            stages.add(ExportStage.ROLLBACK_DATABASE);
        }

        stages.add(ExportStage.COMPLETE);
        return new ExportExecutionPlan(profile, stages);
    }

    public String describeStages() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < stages.size(); i++) {
            if (i > 0) {
                builder.append(" -> ");
            }
            builder.append(stages.get(i).name());
        }
        return builder.toString();
    }
}
