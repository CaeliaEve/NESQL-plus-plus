package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.elysium.kernel.ExportControlFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportDebugFile;
import com.github.dcysteine.nesql.exporter.nativeui.NativeUiExportAbi;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Raw-export ABI file/catalog surface.
 *
 * <p>All stable raw-export paths, manifest keys, report file names, capability labels, and integrity
 * artifact stage names live here so writers consume a single Linux-style ABI table instead of copying
 * strings through the exporter pipeline.</p>
 */
public final class RawExportFileCatalog {
    public static final String RAW_EXPORT_DIRECTORY = "raw-export";
    public static final String VALIDATION_DIRECTORY = "validation";
    public static final String CONTROL_DIRECTORY = "control";
    public static final String DEBUG_DIRECTORY = "debug";

    public static final String RAW_EXPORT_STATUS = "raw-export-authoritative";
    public static final String SIZE_REPORT_SCHEMA_SUFFIX = "/size-report";
    public static final String SIZE_REPORT_STRATEGY = "raw-export-only";

    public static final String MANIFEST_FILE = "manifest.json";
    public static final String EXPORT_REPORT_FILE = "export_report.json";
    public static final String ITEMS_FACTS_FILE = "facts/items.jsonl.gz";
    public static final String FLUIDS_FACTS_FILE = "facts/fluids.jsonl.gz";
    public static final String RECIPE_INDEX_FILE = "facts/recipes/index.json";
    public static final String NEI_GROUPS_FILE = "facts/nei/groups.jsonl.gz";
    public static final String NEI_ORDER_FILE = "facts/nei/order.jsonl.gz";
    public static final String NEI_GUID_FILTERS_FILE = "facts/nei/guidfilters.jsonl.gz";
    public static final String NEI_HIDDEN_ITEMS_FILE = "facts/nei/hiddenitems.jsonl.gz";
    public static final String TEXTURE_INDEX_FILE = "assets/textures/index.jsonl.gz";
    public static final String BROWSER_ATLAS_INDEX_FILE = "assets/textures/browser_atlas_index.json";
    public static final String BROWSER_ATLAS_ASSETS_DIRECTORY = "assets/textures/atlas-assets";
    public static final String ANIMATION_INDEX_FILE = "assets/animations/index.jsonl.gz";
    public static final String NATIVE_SPRITES_FILE = "assets/animations/native-sprites.jsonl.gz";
    public static final String RENDERED_GIFS_FILE = "assets/animations/rendered-gifs.jsonl.gz";
    public static final String RENDER_BACKEND_FILE = "facts/render/backend.json";
    public static final String RENDER_TEXTURE_SPRITES_FILE = "facts/render/texture-sprites.jsonl.gz";
    public static final String RENDER_ITEM_RENDERERS_FILE = "facts/render/item-renderers.jsonl.gz";
    public static final String RENDER_SHADER_ITEMS_FILE = "facts/render/shader-items.jsonl.gz";
    public static final String RENDER_FRAMEBUFFER_CAPTURES_FILE =
            "facts/render/framebuffer-captures.jsonl.gz";
    public static final String MULTIBLOCKS_INDEX_FILE = "models/multiblocks/index.jsonl.gz";
    public static final String ENTITIES_INDEX_FILE = "models/entities/index.jsonl.gz";
    public static final String SPECIAL_INDEX_FILE = "special/index.json";
    public static final String VALIDATION_EXPORT_REPORT_FILE = "validation/export_report.json";
    public static final String VALIDATION_ERRORS_FILE = "validation/errors.jsonl";
    public static final String NEI_BROWSER_CONTRACT_FILE = "validation/nei_browser_contract.json";
    public static final String NEI_HANDLER_ANOMALIES_FILE = "validation/nei_handler_anomalies.json";
    public static final String EXPORT_PLUGIN_TIMINGS_FILE = "validation/export-plugin-timings.json";
    public static final String SEMANTIC_FAMILY_AUDIT_FILE =
            "validation/semantic/parametric-family-audit.json";
    public static final String SEMANTIC_NBT_KEY_DISTRIBUTION_FILE =
            "validation/semantic/nbt-key-distribution.json";
    public static final String SEMANTIC_IDENTITY_NORMALIZATION_REPORT_FILE =
            "validation/semantic/identity-normalization-report.json";
    public static final String SEMANTIC_RULE_PACK_FILE = "facts/semantic/rule-pack.json";
    public static final String SIZE_REPORT_FILE = "validation/size_report.json";
    public static final String NATIVE_UI_VALIDATION_FILE = NativeUiExportAbi.NATIVE_UI_VALIDATION_FILE;

    public static final String EXPORT_VALIDATION_REPORT_FILE_NAME = "export_validation_report.json";
    public static final String EXPORT_HEALTH_REPORT_FILE_NAME = "export-health-report.json";
    public static final String EXPORT_MANIFEST_FILE_NAME = "export_manifest.json";
    public static final String EXPORT_MANIFEST_DASH_FILE_NAME = "export-manifest.json";
    public static final String STAGE_CHECKSUMS_FILE_NAME = "stage_checksums.json";
    public static final String STAGE_CHECKSUMS_DASH_FILE_NAME = "stage-checksums.json";

    public static final String CAPABILITY_FACTS = "facts";
    public static final String CAPABILITY_ASSETS = "assets";
    public static final String CAPABILITY_MODELS = "models";
    public static final String CAPABILITY_VALIDATION = "validation";
    public static final String CAPABILITY_SPECIAL = "special";
    public static final String CAPABILITY_SEMANTIC_IDENTITY = "semanticIdentity";
    public static final String CAPABILITY_NATIVE_NEI_RULES = "nativeNeiRules";
    public static final String CAPABILITY_NATIVE_NEI_HANDLERS = "nativeNeiHandlers";
    public static final String CAPABILITY_UI_FAMILY_CENSUS = "uiFamilyCensus";
    public static final String CAPABILITY_UI_TEMPLATE_CATALOG = "uiTemplateCatalog";
    public static final String CAPABILITY_ANGELICA_NATIVE_RENDER_FACTS = "angelicaNativeRenderFacts";

    private static final List<String> MANIFEST_NOTES = Collections.unmodifiableList(Arrays.asList(
            "Raw-export is the authoritative compiler input.",
            "Large fact streams are gzip-compressed JSONL and recipes are stored only as handler shards."));

    private static final List<String> BASE_CAPABILITIES_BEFORE_OPTIONAL = Collections.unmodifiableList(Arrays.asList(
            CAPABILITY_FACTS,
            CAPABILITY_ASSETS,
            CAPABILITY_MODELS,
            CAPABILITY_VALIDATION,
            CAPABILITY_SPECIAL,
            CAPABILITY_SEMANTIC_IDENTITY,
            CAPABILITY_NATIVE_NEI_RULES,
            CAPABILITY_NATIVE_NEI_HANDLERS));

    private static final List<ManifestFile> BASE_MANIFEST_FILES_BEFORE_OPTIONAL =
            Collections.unmodifiableList(Arrays.asList(
                    new ManifestFile("items", ITEMS_FACTS_FILE),
                    new ManifestFile("semanticItems", "facts/items/semantic-items.jsonl.gz"),
                    new ManifestFile("itemVariants", "facts/items/variants.jsonl.gz"),
                    new ManifestFile("itemPayloads", "facts/items/payloads.jsonl.gz"),
                    new ManifestFile("itemIdentityMap", "facts/items/identity-map.jsonl.gz"),
                    new ManifestFile("fluids", FLUIDS_FACTS_FILE),
                    new ManifestFile("recipeIndex", RECIPE_INDEX_FILE),
                    new ManifestFile("groups", NEI_GROUPS_FILE),
                    new ManifestFile("neiOrder", NEI_ORDER_FILE),
                    new ManifestFile("neiGuidFilters", NEI_GUID_FILTERS_FILE),
                    new ManifestFile("neiHiddenItems", NEI_HIDDEN_ITEMS_FILE),
                    new ManifestFile("textures", TEXTURE_INDEX_FILE),
                    new ManifestFile("uiBackgrounds", NativeUiExportAbi.UI_BACKGROUNDS_DIRECTORY),
                    new ManifestFile("animations", ANIMATION_INDEX_FILE),
                    new ManifestFile("nativeSprites", NATIVE_SPRITES_FILE),
                    new ManifestFile("renderedGifs", RENDERED_GIFS_FILE),
                    new ManifestFile("renderBackend", RENDER_BACKEND_FILE),
                    new ManifestFile("renderTextureSprites", RENDER_TEXTURE_SPRITES_FILE),
                    new ManifestFile("renderItemRenderers", RENDER_ITEM_RENDERERS_FILE),
                    new ManifestFile("renderShaderItems", RENDER_SHADER_ITEMS_FILE),
                    new ManifestFile("renderFramebufferCaptures", RENDER_FRAMEBUFFER_CAPTURES_FILE),
                    new ManifestFile("browserAtlasIndex", BROWSER_ATLAS_INDEX_FILE),
                    new ManifestFile("browserAtlasAssets", BROWSER_ATLAS_ASSETS_DIRECTORY),
                    new ManifestFile("neiHandlers", "facts/nei/handlers.jsonl.gz"),
                    new ManifestFile("neiHandlerLayouts", NativeUiExportAbi.NEI_HANDLER_LAYOUTS_FILE)));

    private static final List<ManifestFile> BASE_MANIFEST_FILES_AFTER_OPTIONAL =
            Collections.unmodifiableList(Arrays.asList(
                    new ManifestFile("multiblocks", MULTIBLOCKS_INDEX_FILE),
                    new ManifestFile("entities", ENTITIES_INDEX_FILE),
                    new ManifestFile("specialIndex", SPECIAL_INDEX_FILE),
                    new ManifestFile("exportReport", VALIDATION_EXPORT_REPORT_FILE),
                    new ManifestFile("exportHealthReport", validationPath(EXPORT_HEALTH_REPORT_FILE_NAME)),
                    new ManifestFile("errors", VALIDATION_ERRORS_FILE),
                    new ManifestFile("neiHandlerAnomalies", NEI_HANDLER_ANOMALIES_FILE),
                    new ManifestFile("nativeUiValidation", NativeUiExportAbi.NATIVE_UI_VALIDATION_FILE),
                    new ManifestFile("pluginTimings", EXPORT_PLUGIN_TIMINGS_FILE),
                    new ManifestFile("stageTimings", ExportDebugFile.STAGE_TIMING.validationAliasPath()),
                    new ManifestFile("stageCheckpoint", ExportDebugFile.STAGE_CHECKPOINT.validationAliasPath()),
                    new ManifestFile("stageChecksums", validationPath(STAGE_CHECKSUMS_FILE_NAME)),
                    new ManifestFile("sizeReport", SIZE_REPORT_FILE),
                    new ManifestFile("neiBrowserContract", NEI_BROWSER_CONTRACT_FILE),
                    new ManifestFile("semanticFamilyAudit", SEMANTIC_FAMILY_AUDIT_FILE),
                    new ManifestFile("semanticNbtKeyDistribution", SEMANTIC_NBT_KEY_DISTRIBUTION_FILE),
                    new ManifestFile("semanticIdentityNormalizationReport", SEMANTIC_IDENTITY_NORMALIZATION_REPORT_FILE),
                    new ManifestFile("semanticRulePack", SEMANTIC_RULE_PACK_FILE)));

    private static final List<String> PROHIBITED_ROOT_OUTPUTS = Collections.unmodifiableList(Arrays.asList(
            "recipes.jsonl",
            "items.jsonl",
            "fluids.jsonl",
            "entities.jsonl",
            "facts/items.jsonl",
            "facts/fluids.jsonl",
            "facts/recipes/all.jsonl",
            "special/gregtech/recipes.jsonl",
            "special/thaumcraft/recipes.jsonl",
            "special/botania/recipes.jsonl",
            "special/bloodmagic/recipes.jsonl",
            "special/forestry/recipes.jsonl",
            "special/eec/recipes.jsonl"));

    private RawExportFileCatalog() {}

    public static List<String> manifestNotes() {
        return MANIFEST_NOTES;
    }

    public static List<String> manifestCapabilities(boolean includeUiFamilyCensus, boolean includeUiTemplateCatalog) {
        List<String> capabilities = new ArrayList<String>(BASE_CAPABILITIES_BEFORE_OPTIONAL);
        if (includeUiFamilyCensus) {
            capabilities.add(CAPABILITY_UI_FAMILY_CENSUS);
        }
        if (includeUiTemplateCatalog) {
            capabilities.add(CAPABILITY_UI_TEMPLATE_CATALOG);
        }
        capabilities.add(CAPABILITY_ANGELICA_NATIVE_RENDER_FACTS);
        return Collections.unmodifiableList(capabilities);
    }

    public static void putManifestFiles(
            Map<String, String> files,
            boolean includeUiFamilyCensus,
            boolean includeUiTemplateCatalog) {
        putManifestFiles(files, BASE_MANIFEST_FILES_BEFORE_OPTIONAL);
        if (includeUiFamilyCensus) {
            files.put("uiFamilyCensus", NativeUiExportAbi.UI_FAMILY_CENSUS_FILE);
        }
        if (includeUiTemplateCatalog) {
            files.put("uiTemplateCatalog", NativeUiExportAbi.UI_TEMPLATE_CATALOG_FILE);
        }
        putManifestFiles(files, BASE_MANIFEST_FILES_AFTER_OPTIONAL);
    }

    public static List<String> prohibitedRootOutputs() {
        return PROHIBITED_ROOT_OUTPUTS;
    }

    public static File rawExportDirectory(File repositoryDirectory) {
        return new File(repositoryDirectory, RAW_EXPORT_DIRECTORY);
    }

    public static File validationDirectory(File rawDir) {
        return new File(rawDir, VALIDATION_DIRECTORY);
    }

    public static File rawExportFile(File rawDir, String relativePath) {
        return new File(rawDir, relativePath.replace('/', File.separatorChar));
    }

    public static String validationPath(String fileName) {
        return VALIDATION_DIRECTORY + "/" + fileName;
    }

    public static String controlArtifactStage(ExportControlFile file) {
        if (file == ExportControlFile.INDEX) {
            return "control";
        }
        return "control-" + file.indexKey();
    }

    public static String debugArtifactStage(ExportDebugFile file) {
        if (file == ExportDebugFile.KERNEL_TRACE) {
            return "debug-trace";
        }
        return "debug-" + file.debugPath().replace('/', '-').replace(".json", "");
    }

    private static void putManifestFiles(Map<String, String> files, List<ManifestFile> entries) {
        for (ManifestFile entry : entries) {
            files.put(entry.manifestKey, entry.relativePath);
        }
    }

    private static final class ManifestFile {
        private final String manifestKey;
        private final String relativePath;

        private ManifestFile(String manifestKey, String relativePath) {
            this.manifestKey = manifestKey;
            this.relativePath = relativePath;
        }
    }
}
