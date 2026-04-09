package com.github.dcysteine.nesql.exporter.canonical;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level NESQL++ canonical export model.
 */
public class CanonicalRepositoryModel {
    public String schemaVersion = "nesqlpp/v1-draft";
    public String sourceProfileId;
    public List<CanonicalItem> items = new ArrayList<>();
    public List<CanonicalFluid> fluids = new ArrayList<>();
    public List<CanonicalRecipe> recipes = new ArrayList<>();
    public List<CanonicalRenderAsset> renderAssets = new ArrayList<>();
}
