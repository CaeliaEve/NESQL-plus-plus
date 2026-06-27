package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;

final class RawExportReport {
    String schemaVersion;
    String generatedAt;
    String profile;
    String selection;
    SemanticRulePack.RuntimeMetadata semanticRuleRuntime;
    RawExportCounts counts;
    RawExportValidation validation = new RawExportValidation();
    RawExportSidecarWriter.NeiBrowserContract neiBrowserContract;
}
