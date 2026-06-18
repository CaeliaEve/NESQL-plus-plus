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
        return fromProfile(profile, ExportSelection.full());
    }

    public static ExportExecutionPlan fromProfile(ExportProfile profile, ExportSelection selection) {
        List<ExportStage> stages = new ArrayList<>();
        stages.add(ExportStage.INITIALIZE_REPOSITORY);
        stages.add(ExportStage.INITIALIZE_DATABASE);
        stages.add(ExportStage.INITIALIZE_PLUGINS);
        stages.add(ExportStage.COLLECT_PLUGIN_DATA);
        if (selection.includesStage(ExportStage.WRITE_UI_FAMILY_CENSUS, profile)) {
            stages.add(ExportStage.WRITE_UI_FAMILY_CENSUS);
        }

        if (profile.writeModBasedItems && selection.includesStage(ExportStage.WRITE_MOD_BASED_ITEMS, profile)) {
            stages.add(ExportStage.WRITE_MOD_BASED_ITEMS);
        }
        if (profile.writeModBasedRecipes && selection.includesStage(ExportStage.WRITE_MOD_BASED_RECIPES, profile)) {
            stages.add(ExportStage.WRITE_MOD_BASED_RECIPES);
        }
        if (profile.writeCanonicalSnapshot && selection.includesStage(ExportStage.WRITE_CANONICAL_SNAPSHOT, profile)) {
            stages.add(ExportStage.WRITE_CANONICAL_SNAPSHOT);
        }
        if (profile.writeModBasedItems && selection.includesStage(ExportStage.WRITE_BROWSER_LAYOUT_INDEX, profile)) {
            stages.add(ExportStage.WRITE_BROWSER_LAYOUT_INDEX);
        }

        if (profile == ExportProfile.FULL_V104) {
            if (selection.includesStage(ExportStage.WRITE_MULTIBLOCK_BLUEPRINTS, profile)) {
                stages.add(ExportStage.WRITE_MULTIBLOCK_BLUEPRINTS);
            }
            if (selection.includesStage(ExportStage.WRITE_BLOCK_FACE_METADATA, profile)) {
                stages.add(ExportStage.WRITE_BLOCK_FACE_METADATA);
            }
        }

        if (profile.renderImages && selection.includesStage(ExportStage.RENDER_IMAGES, profile)) {
            addIfSelected(stages, ExportStage.RENDER_IMAGES, profile, selection);
            addIfSelected(stages, ExportStage.WRITE_RENDER_ASSET_MANIFEST, profile, selection);
            addIfSelected(stages, ExportStage.WRITE_ANIMATION_MANIFEST, profile, selection);
            addIfSelected(stages, ExportStage.WRITE_ATLAS_PACKS, profile, selection);
            addIfSelected(stages, ExportStage.WRITE_ANIMATED_ATLAS_PACKS, profile, selection);
            addIfSelected(stages, ExportStage.WRITE_ATLAS_REGISTRY, profile, selection);
            addIfSelected(stages, ExportStage.WRITE_RENDER_INDEX, profile, selection);
            addIfSelected(stages, ExportStage.WRITE_BROWSER_ATLAS_INDEX, profile, selection);
        }

        if (selection.includesStage(ExportStage.COMMIT_DATABASE, profile)) {
            stages.add(ExportStage.WRITE_RAW_EXPORT_SIDECAR);
            stages.add(ExportStage.COMMIT_DATABASE);
        } else {
            stages.add(ExportStage.WRITE_RAW_EXPORT_SIDECAR);
            stages.add(ExportStage.ROLLBACK_DATABASE);
        }

        stages.add(ExportStage.COMPLETE);
        return new ExportExecutionPlan(profile, stages);
    }

    private static void addIfSelected(
            List<ExportStage> stages,
            ExportStage stage,
            ExportProfile profile,
            ExportSelection selection) {
        if (selection.includesStage(stage, profile)) {
            stages.add(stage);
        }
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
