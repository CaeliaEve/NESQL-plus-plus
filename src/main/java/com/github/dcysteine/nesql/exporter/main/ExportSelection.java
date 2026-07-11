package com.github.dcysteine.nesql.exporter.main;

/** Per-run stage selection for the interactive /nesql command. */
public final class ExportSelection {
    public final boolean writeItems;
    public final boolean writeRecipes;
    public final boolean writeCanonicalSnapshot;
    public final boolean writeUiFamilyCensus;
    public final boolean writeUiTemplateCatalog;
    public final boolean writeMultiblocks;
    public final boolean writeBlockFaces;
    public final boolean renderImages;
    public final boolean writeRenderManifests;
    public final boolean writeAtlasPacks;
    public final boolean writeAnimatedAtlasPacks;
    public final boolean writeBrowserIndexes;
    public final boolean commitDatabase;

    private ExportSelection(Builder builder) {
        this.writeItems = builder.writeItems;
        this.writeRecipes = builder.writeRecipes;
        this.writeCanonicalSnapshot = builder.writeCanonicalSnapshot;
        this.writeUiFamilyCensus = builder.writeUiFamilyCensus;
        this.writeUiTemplateCatalog = builder.writeUiTemplateCatalog;
        this.writeMultiblocks = builder.writeMultiblocks;
        this.writeBlockFaces = builder.writeBlockFaces;
        this.renderImages = builder.renderImages;
        this.writeRenderManifests = builder.writeRenderManifests;
        this.writeAtlasPacks = builder.writeAtlasPacks;
        this.writeAnimatedAtlasPacks = builder.writeAnimatedAtlasPacks;
        this.writeBrowserIndexes = builder.writeBrowserIndexes;
        this.commitDatabase = builder.commitDatabase;
    }

    public static ExportSelection full() {
        return new Builder().build();
    }

    /**
     * Web-authored UI reference export lane.
     *
     * <p>NeoNEI now treats hand-authored web components as the final recipe UI. This selection keeps
     * layout/reference facts and item/fluid browser atlas artifacts, while intentionally avoiding
     * captured NEI frame/background PNG authority. Raw-export still writes item and recipe facts
     * directly from the export database.</p>
     */
    public static ExportSelection nativeUiExport() {
        return new Builder()
                .writeItems(false)
                .writeRecipes(false)
                .writeUiFamilyCensus(true)
                .writeUiTemplateCatalog(true)
                .writeMultiblocks(false)
                .writeBlockFaces(false)
                .renderImages(true)
                .writeRenderManifests(true)
                .writeAtlasPacks(true)
                .writeAnimatedAtlasPacks(true)
                .writeBrowserIndexes(true)
                .commitDatabase(false)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isFullExportCompatible() {
        return writeItems
                && writeRecipes
                && writeMultiblocks
                && writeBlockFaces
                && renderImages
                && writeRenderManifests
                && writeAtlasPacks
                && writeAnimatedAtlasPacks
                && writeBrowserIndexes
                && writeUiFamilyCensus
                && writeUiTemplateCatalog
                && commitDatabase;
    }

    public boolean isNativeUiExport() {
        return writeUiFamilyCensus
                && writeUiTemplateCatalog
                && !writeCanonicalSnapshot
                && !writeMultiblocks
                && !writeBlockFaces
                && renderImages
                && writeRenderManifests
                && writeAtlasPacks
                && writeAnimatedAtlasPacks
                && writeBrowserIndexes
                && !commitDatabase;
    }

    public boolean includesStage(ExportStage stage, ExportProfile profile) {
        switch (stage) {
            case WRITE_MOD_BASED_ITEMS:
                return writeItems;
            case WRITE_MOD_BASED_RECIPES:
                return writeRecipes;
            case WRITE_CANONICAL_SNAPSHOT:
                return writeCanonicalSnapshot;
            case WRITE_UI_FAMILY_CENSUS:
                return writeUiFamilyCensus;
            case WRITE_UI_TEMPLATE_CATALOG:
                return writeUiTemplateCatalog;
            case WRITE_MULTIBLOCK_BLUEPRINTS:
                return writeMultiblocks;
            case WRITE_BLOCK_FACE_METADATA:
                return writeBlockFaces;
            case RENDER_IMAGES:
                return renderImages;
            case WRITE_RENDER_ASSET_MANIFEST:
            case WRITE_ANIMATION_MANIFEST:
            case WRITE_RENDER_INDEX:
            case WRITE_ATLAS_REGISTRY:
                return renderImages && writeRenderManifests;
            case WRITE_ATLAS_PACKS:
                return renderImages && writeAtlasPacks;
            case WRITE_ANIMATED_ATLAS_PACKS:
                return renderImages && writeAnimatedAtlasPacks;
            case WRITE_BROWSER_LAYOUT_INDEX:
                return writeBrowserIndexes;
            case WRITE_BROWSER_ATLAS_INDEX:
                return renderImages && writeBrowserIndexes;
            case COMMIT_DATABASE:
                return commitDatabase && profile.commitDatabase;
            case ROLLBACK_DATABASE:
                return !commitDatabase || !profile.commitDatabase;
            default:
                return true;
        }
    }

    public String describe() {
        StringBuilder builder = new StringBuilder();
        append(builder, "items", writeItems);
        append(builder, "recipes", writeRecipes);
        append(builder, "snapshot", writeCanonicalSnapshot);
        append(builder, "ui-census", writeUiFamilyCensus);
        append(builder, "ui-template-catalog", writeUiTemplateCatalog);
        append(builder, "multiblocks", writeMultiblocks);
        append(builder, "blockfaces", writeBlockFaces);
        append(builder, "images", renderImages);
        append(builder, "manifests", writeRenderManifests);
        append(builder, "atlas", writeAtlasPacks);
        append(builder, "animated-atlas", writeAnimatedAtlasPacks);
        append(builder, "browser-indexes", writeBrowserIndexes);
        append(builder, "db-commit", commitDatabase);
        return builder.toString();
    }

    private static void append(StringBuilder builder, String label, boolean enabled) {
        if (builder.length() > 0) {
            builder.append(", ");
        }
        builder.append(enabled ? "+" : "-").append(label);
    }

    public static final class Builder {
        private boolean writeItems = true;
        private boolean writeRecipes = true;
        private boolean writeCanonicalSnapshot = false;
        private boolean writeUiFamilyCensus = true;
        private boolean writeUiTemplateCatalog = true;
        private boolean writeMultiblocks = true;
        private boolean writeBlockFaces = true;
        private boolean renderImages = true;
        private boolean writeRenderManifests = true;
        private boolean writeAtlasPacks = true;
        private boolean writeAnimatedAtlasPacks = true;
        private boolean writeBrowserIndexes = true;
        private boolean commitDatabase = true;

        public Builder writeItems(boolean value) {
            this.writeItems = value;
            return this;
        }

        public Builder writeRecipes(boolean value) {
            this.writeRecipes = value;
            return this;
        }

        public Builder writeCanonicalSnapshot(boolean value) {
            this.writeCanonicalSnapshot = value;
            return this;
        }

        public Builder writeUiFamilyCensus(boolean value) {
            this.writeUiFamilyCensus = value;
            return this;
        }

        public Builder writeUiTemplateCatalog(boolean value) {
            this.writeUiTemplateCatalog = value;
            return this;
        }

        public Builder writeMultiblocks(boolean value) {
            this.writeMultiblocks = value;
            return this;
        }

        public Builder writeBlockFaces(boolean value) {
            this.writeBlockFaces = value;
            return this;
        }

        public Builder renderImages(boolean value) {
            this.renderImages = value;
            return this;
        }

        public Builder writeRenderManifests(boolean value) {
            this.writeRenderManifests = value;
            return this;
        }

        public Builder writeAtlasPacks(boolean value) {
            this.writeAtlasPacks = value;
            return this;
        }

        public Builder writeAnimatedAtlasPacks(boolean value) {
            this.writeAnimatedAtlasPacks = value;
            return this;
        }

        public Builder writeBrowserIndexes(boolean value) {
            this.writeBrowserIndexes = value;
            return this;
        }

        public Builder commitDatabase(boolean value) {
            this.commitDatabase = value;
            return this;
        }

        public ExportSelection build() {
            return new ExportSelection(this);
        }
    }
}
