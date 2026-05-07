package com.github.dcysteine.nesql.exporter.main;

/**
 * Transitional export profiles for the NESQL++ single-orchestrator migration.
 *
 * <p>Current commands still dispatch to separate exporter classes. This enum
 * makes the intended command-to-profile mapping explicit so later phases can
 * converge them onto one orchestrator without rediscovering the behavior split.</p>
 */
public enum ExportProfile {
    FULL_V104(
            "v1.04",
            true,
            true,
            true,
            true,
            true),
    DATA_ONLY_V104(
            "v1.04-data",
            false,
            false,
            true,
            true,
            true),
    IMAGES_ONLY(
            "images-only",
            true,
            false,
            false,
            false,
            false);

    public final String profileId;
    public final boolean renderImages;
    public final boolean commitDatabase;
    public final boolean writeModBasedItems;
    public final boolean writeModBasedRecipes;
    public final boolean writeCanonicalSnapshot;

    ExportProfile(
            String profileId,
            boolean renderImages,
            boolean commitDatabase,
            boolean writeModBasedItems,
            boolean writeModBasedRecipes,
            boolean writeCanonicalSnapshot) {
        this.profileId = profileId;
        this.renderImages = renderImages;
        this.commitDatabase = commitDatabase;
        this.writeModBasedItems = writeModBasedItems;
        this.writeModBasedRecipes = writeModBasedRecipes;
        this.writeCanonicalSnapshot = writeCanonicalSnapshot;
    }
}
