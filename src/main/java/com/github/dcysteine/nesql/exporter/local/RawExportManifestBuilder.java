package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.elysium.kernel.ExportControlFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportDebugFile;
import com.github.dcysteine.nesql.exporter.nativeui.NativeUiExportAbi;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.ExportStage;

final class RawExportManifestBuilder {
    private RawExportManifestBuilder() {}

    static RawExportManifest build(String schemaVersion, String generatedAt, ExportContext exportContext, RawExportReport report) {
        RawExportManifest manifest = new RawExportManifest();
        manifest.schemaVersion = schemaVersion;
        manifest.generatedAt = generatedAt;
        manifest.repositoryName = exportContext.paths.repositoryName;
        manifest.profile = exportContext.profile.profileId;
        manifest.selection = exportContext.selection.describe();
        manifest.semanticRuleRuntime = report.semanticRuleRuntime;
        manifest.status = "raw-export-authoritative";
        manifest.notes.add("Raw-export is the authoritative compiler input.");
        manifest.notes.add("Large fact streams are gzip-compressed JSONL and recipes are stored only as handler shards.");
        manifest.capabilities.add("facts");
        manifest.capabilities.add("assets");
        manifest.capabilities.add("models");
        manifest.capabilities.add("validation");
        manifest.capabilities.add("special");
        manifest.capabilities.add("semanticIdentity");
        manifest.capabilities.add("nativeNeiRules");
        manifest.capabilities.add("nativeNeiHandlers");
        if (exportContext.selection.includesStage(ExportStage.WRITE_UI_FAMILY_CENSUS, exportContext.profile)) {
            manifest.capabilities.add("uiFamilyCensus");
        }
        if (exportContext.selection.includesStage(ExportStage.WRITE_UI_TEMPLATE_CATALOG, exportContext.profile)) {
            manifest.capabilities.add("uiTemplateCatalog");
        }
        manifest.capabilities.add("angelicaNativeRenderFacts");
        manifest.files.put("items", "facts/items.jsonl.gz");
        manifest.files.put("semanticItems", "facts/items/semantic-items.jsonl.gz");
        manifest.files.put("itemVariants", "facts/items/variants.jsonl.gz");
        manifest.files.put("itemPayloads", "facts/items/payloads.jsonl.gz");
        manifest.files.put("itemIdentityMap", "facts/items/identity-map.jsonl.gz");
        manifest.files.put("fluids", "facts/fluids.jsonl.gz");
        manifest.files.put("recipeIndex", "facts/recipes/index.json");
        manifest.files.put("groups", "facts/nei/groups.jsonl.gz");
        manifest.files.put("neiOrder", "facts/nei/order.jsonl.gz");
        manifest.files.put("neiGuidFilters", "facts/nei/guidfilters.jsonl.gz");
        manifest.files.put("neiHiddenItems", "facts/nei/hiddenitems.jsonl.gz");
        manifest.files.put("textures", "assets/textures/index.jsonl.gz");
        manifest.files.put("uiBackgrounds", NativeUiExportAbi.UI_BACKGROUNDS_DIRECTORY);
        manifest.files.put("animations", "assets/animations/index.jsonl.gz");
        manifest.files.put("nativeSprites", "assets/animations/native-sprites.jsonl.gz");
        manifest.files.put("renderedGifs", "assets/animations/rendered-gifs.jsonl.gz");
        manifest.files.put("renderBackend", "facts/render/backend.json");
        manifest.files.put("renderTextureSprites", "facts/render/texture-sprites.jsonl.gz");
        manifest.files.put("renderItemRenderers", "facts/render/item-renderers.jsonl.gz");
        manifest.files.put("renderShaderItems", "facts/render/shader-items.jsonl.gz");
        manifest.files.put("renderFramebufferCaptures", "facts/render/framebuffer-captures.jsonl.gz");
        manifest.files.put("browserAtlasIndex", "assets/textures/browser_atlas_index.json");
        manifest.files.put("browserAtlasAssets", "assets/textures/atlas-assets");
        manifest.files.put("neiHandlers", "facts/nei/handlers.jsonl.gz");
        manifest.files.put("neiHandlerLayouts", NativeUiExportAbi.NEI_HANDLER_LAYOUTS_FILE);
        if (exportContext.selection.includesStage(ExportStage.WRITE_UI_FAMILY_CENSUS, exportContext.profile)) {
            manifest.files.put("uiFamilyCensus", NativeUiExportAbi.UI_FAMILY_CENSUS_FILE);
        }
        if (exportContext.selection.includesStage(ExportStage.WRITE_UI_TEMPLATE_CATALOG, exportContext.profile)) {
            manifest.files.put("uiTemplateCatalog", NativeUiExportAbi.UI_TEMPLATE_CATALOG_FILE);
        }
        manifest.files.put("multiblocks", "models/multiblocks/index.jsonl.gz");
        manifest.files.put("entities", "models/entities/index.jsonl.gz");
        manifest.files.put("specialIndex", "special/index.json");
        manifest.files.put("exportReport", "validation/export_report.json");
        manifest.files.put("exportHealthReport", "validation/export-health-report.json");
        manifest.files.put("errors", "validation/errors.jsonl");
        manifest.files.put("neiHandlerAnomalies", "validation/nei_handler_anomalies.json");
        manifest.files.put("pluginTimings", "validation/export-plugin-timings.json");
        manifest.files.put("stageTimings", "validation/export_stage_timings.json");
        manifest.files.put("stageCheckpoint", "validation/stage_checkpoint.json");
        manifest.files.put("stageChecksums", "validation/stage_checksums.json");
        manifest.files.put("sizeReport", "validation/size_report.json");
        manifest.files.put("neiBrowserContract", "validation/nei_browser_contract.json");
        manifest.files.put("semanticFamilyAudit", "validation/semantic/parametric-family-audit.json");
        manifest.files.put("semanticNbtKeyDistribution", "validation/semantic/nbt-key-distribution.json");
        manifest.files.put("semanticIdentityNormalizationReport", "validation/semantic/identity-normalization-report.json");
        manifest.files.put("semanticRulePack", "facts/semantic/rule-pack.json");
        for (ExportControlFile file : ExportControlFile.values()) {
            manifest.files.put(file.manifestKey(), file.rawExportPath());
        }
        for (ExportDebugFile file : ExportDebugFile.values()) {
            manifest.files.put(file.manifestKey(), file.rawExportDebugPath());
        }
        manifest.counts = report.counts;
        return manifest;
    }

}
