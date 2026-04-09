package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;

import java.util.ArrayList;
import java.util.List;

final class CanonicalAnimationManifest {
    String schemaVersion = "nesqlpp/animation-manifest/v1-draft";
    int assetCount;
    int groupCount;
    List<CanonicalRenderAsset> assets = new ArrayList<>();
    List<AnimationGroupInfo> groups = new ArrayList<>();

    static final class AnimationGroupInfo {
        String atlasGroup;
        int assetCount;

        AnimationGroupInfo(String atlasGroup, int assetCount) {
            this.atlasGroup = atlasGroup;
            this.assetCount = assetCount;
        }
    }
}
