package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;

import java.util.ArrayList;
import java.util.List;

final class CanonicalRenderAssetManifest {
    String schemaVersion = "nesqlpp/render-assets/v1-draft";
    List<CanonicalRenderAsset> assets = new ArrayList<>();
    List<RenderAssetGroupInfo> groups = new ArrayList<>();

    static final class RenderAssetGroupInfo {
        String atlasGroup;
        int assetCount;

        RenderAssetGroupInfo(String atlasGroup, int assetCount) {
            this.atlasGroup = atlasGroup;
            this.assetCount = assetCount;
        }
    }
}
