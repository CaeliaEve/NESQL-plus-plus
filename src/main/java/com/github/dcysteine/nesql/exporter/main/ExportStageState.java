package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;

import java.util.List;

final class ExportStageState {
    ExportRuntime runtime;
    ExportSession session;
    boolean renderingImages;
    boolean writePreambleAnnounced;
    ExportStage currentStage;
    List<CanonicalRenderAsset> renderAssets;
}
