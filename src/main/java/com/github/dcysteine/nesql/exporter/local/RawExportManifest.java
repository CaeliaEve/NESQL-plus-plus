package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RawExportManifest {
    String schemaVersion;
    String generatedAt;
    String repositoryName;
    String profile;
    String selection;
    String status;
    SemanticRulePack.RuntimeMetadata semanticRuleRuntime;
    RawExportCounts counts;
    List<String> notes = new ArrayList<String>();
    List<String> capabilities = new ArrayList<String>();
    Map<String, String> files = new LinkedHashMap<String, String>();
}
