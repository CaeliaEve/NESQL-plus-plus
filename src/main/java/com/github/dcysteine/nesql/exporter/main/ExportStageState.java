package com.github.dcysteine.nesql.exporter.main;

final class ExportStageState {
    ExportRuntime runtime;
    ExportSession session;
    boolean renderingImages;
    boolean writePreambleAnnounced;
    ExportStage currentStage;
}
