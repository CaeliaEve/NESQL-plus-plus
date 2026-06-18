package com.github.dcysteine.nesql.exporter.main;

/**
 * Stable stage-family metadata consumed by timing/checksum reports and the NeoNEI importer.
 *
 * <p>The enum names remain the execution-level pipeline, while these families are the public
 * incremental-export contract: pipeline, data, ui-census, images, animated-images, render-contracts,
 * browser-layout, multiblocks, eec-models, recipe-layout-contracts, atlas-pack, and raw-export.</p>
 */
final class ExportStageMetadata {
    static final String[] SUPPORTED_INCREMENTAL_FAMILIES = new String[] {
            "data",
            "ui-census",
            "images",
            "animated-images",
            "render-contracts",
            "browser-layout",
            "multiblocks",
            "eec-models",
            "recipe-layout-contracts",
            "atlas-pack",
            "raw-export"
    };

    private ExportStageMetadata() {}

    static String family(ExportStage stage) {
        if (stage == null) {
            return "unknown";
        }
        switch (stage) {
            case INITIALIZE_REPOSITORY:
            case INITIALIZE_DATABASE:
            case INITIALIZE_PLUGINS:
            case COLLECT_PLUGIN_DATA:
            case COMMIT_DATABASE:
            case ROLLBACK_DATABASE:
            case COMPLETE:
                return "pipeline";
            case WRITE_MOD_BASED_ITEMS:
            case WRITE_MOD_BASED_RECIPES:
            case WRITE_CANONICAL_SNAPSHOT:
                return "data";
            case WRITE_UI_FAMILY_CENSUS:
                return "ui-census";
            case RENDER_IMAGES:
                return "images";
            case WRITE_RENDER_ASSET_MANIFEST:
            case WRITE_RENDER_INDEX:
                return "render-contracts";
            case WRITE_ANIMATION_MANIFEST:
                return "animated-images";
            case WRITE_ATLAS_PACKS:
            case WRITE_ANIMATED_ATLAS_PACKS:
            case WRITE_ATLAS_REGISTRY:
            case WRITE_BROWSER_ATLAS_INDEX:
                return "atlas-pack";
            case WRITE_BROWSER_LAYOUT_INDEX:
                return "browser-layout";
            case WRITE_MULTIBLOCK_BLUEPRINTS:
            case WRITE_BLOCK_FACE_METADATA:
                return "multiblocks";
            case WRITE_RAW_EXPORT_SIDECAR:
                return "raw-export";
            default:
                return "unknown";
        }
    }

    static String outputKind(ExportStage stage) {
        String family = family(stage);
        if ("pipeline".equals(family) || "unknown".equals(family)) {
            return family;
        }
        return "raw-export/" + family;
    }

    static boolean skippableByChecksum(ExportStage stage) {
        String family = family(stage);
        return !"pipeline".equals(family) && !"unknown".equals(family);
    }
}
