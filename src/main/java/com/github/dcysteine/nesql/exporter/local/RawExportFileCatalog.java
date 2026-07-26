package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.elysium.kernel.ExportControlFile;
import com.github.dcysteine.nesql.elysium.kernel.ExportDebugFile;
import com.github.dcysteine.nesql.exporter.nativeui.NativeUiExportAbi;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public static final String SIZE_REPORT_STRATEGY = "final-generation-excluding-size-report";

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
    public static final String FACADE_RESOLUTIONS_FILE =
            "assets/textures/facade-resolutions.jsonl.gz";
    public static final String BROWSER_ATLAS_INDEX_FILE = "assets/textures/browser_atlas_index.json";
    public static final String BROWSER_ATLAS_ASSETS_DIRECTORY = "assets/textures/atlas-assets";
    public static final String ANIMATION_INDEX_FILE = "assets/animations/index.jsonl.gz";
    public static final String ANIMATION_FRAME_MATERIALIZATIONS_FILE =
            "assets/animations/frame-materializations.jsonl.gz";
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

    private static final List<String> EXPECTED_OPTIONAL_MANIFEST_KEYS =
            Collections.unmodifiableList(Arrays.asList("uiFamilyCensus", "uiTemplateCatalog"));
    private static final List<String> EXPECTED_OPTIONAL_CAPABILITIES = Collections.unmodifiableList(Arrays.asList(
            CAPABILITY_UI_FAMILY_CENSUS,
            CAPABILITY_UI_TEMPLATE_CATALOG));

    private static final List<String> MANIFEST_NOTES =
            validateStringCatalog(
                    "raw-export manifest note",
                    Arrays.asList(
                            "Raw-export is the authoritative compiler input.",
                            "Large fact streams are gzip-compressed JSONL "
                                    + "and recipes are stored only as handler shards."));

    private static final CapabilityCatalog CAPABILITY_CATALOG = CapabilityCatalog.validateAndFreeze(
            Arrays.asList(
                    new CapabilityDescriptor(CAPABILITY_FACTS),
                    new CapabilityDescriptor(CAPABILITY_ASSETS),
                    new CapabilityDescriptor(CAPABILITY_MODELS),
                    new CapabilityDescriptor(CAPABILITY_VALIDATION),
                    new CapabilityDescriptor(CAPABILITY_SPECIAL),
                    new CapabilityDescriptor(CAPABILITY_SEMANTIC_IDENTITY),
                    new CapabilityDescriptor(CAPABILITY_NATIVE_NEI_RULES),
                    new CapabilityDescriptor(CAPABILITY_NATIVE_NEI_HANDLERS)),
            Arrays.asList(
                    new CapabilityDescriptor(CAPABILITY_UI_FAMILY_CENSUS),
                    new CapabilityDescriptor(CAPABILITY_UI_TEMPLATE_CATALOG)),
            Arrays.asList(new CapabilityDescriptor(CAPABILITY_ANGELICA_NATIVE_RENDER_FACTS)));

    private static final List<CapabilityDescriptor> BASE_CAPABILITIES_BEFORE_OPTIONAL =
            CAPABILITY_CATALOG.beforeOptional();
    private static final List<CapabilityDescriptor> OPTIONAL_CAPABILITIES =
            CAPABILITY_CATALOG.optional();
    private static final List<CapabilityDescriptor> CAPABILITIES_AFTER_OPTIONAL =
            CAPABILITY_CATALOG.afterOptional();

    private static final ManifestFileCatalog MANIFEST_FILE_CATALOG = ManifestFileCatalog.validateAndFreeze(
            Arrays.asList(
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
                    new ManifestFile("facadeResolutions", FACADE_RESOLUTIONS_FILE),
                    new ManifestFile("animations", ANIMATION_INDEX_FILE),
                    new ManifestFile(
                            "animationFrameMaterializations",
                            ANIMATION_FRAME_MATERIALIZATIONS_FILE),
                    new ManifestFile("nativeSprites", NATIVE_SPRITES_FILE),
                    new ManifestFile("renderedGifs", RENDERED_GIFS_FILE),
                    new ManifestFile("renderBackend", RENDER_BACKEND_FILE),
                    new ManifestFile("renderTextureSprites", RENDER_TEXTURE_SPRITES_FILE),
                    new ManifestFile("renderItemRenderers", RENDER_ITEM_RENDERERS_FILE),
                    new ManifestFile("renderShaderItems", RENDER_SHADER_ITEMS_FILE),
                    new ManifestFile("renderFramebufferCaptures", RENDER_FRAMEBUFFER_CAPTURES_FILE),
                    new ManifestFile("browserAtlasIndex", BROWSER_ATLAS_INDEX_FILE),
                    new ManifestFile("neiHandlers", NativeUiExportAbi.NEI_HANDLERS_FILE),
                    new ManifestFile("neiHandlerLayouts", NativeUiExportAbi.NEI_HANDLER_LAYOUTS_FILE)),
            Arrays.asList(
                    new ManifestFile("uiFamilyCensus", NativeUiExportAbi.UI_FAMILY_CENSUS_FILE),
                    new ManifestFile("uiTemplateCatalog", NativeUiExportAbi.UI_TEMPLATE_CATALOG_FILE)),
            Arrays.asList(
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
                    new ManifestFile(
                            "semanticIdentityNormalizationReport",
                            SEMANTIC_IDENTITY_NORMALIZATION_REPORT_FILE),
                    new ManifestFile("semanticRulePack", SEMANTIC_RULE_PACK_FILE)));

    private static final List<ManifestFile> BASE_MANIFEST_FILES_BEFORE_OPTIONAL =
            MANIFEST_FILE_CATALOG.beforeOptional();
    private static final List<ManifestFile> OPTIONAL_MANIFEST_FILES =
            MANIFEST_FILE_CATALOG.optional();
    private static final List<ManifestFile> BASE_MANIFEST_FILES_AFTER_OPTIONAL =
            MANIFEST_FILE_CATALOG.afterOptional();

    private static final List<String> PROHIBITED_ROOT_OUTPUTS =
            validatePathCatalog(
                    "raw-export prohibited root output",
                    Arrays.asList(
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
        List<String> capabilities = new ArrayList<String>();
        addCapabilities(capabilities, BASE_CAPABILITIES_BEFORE_OPTIONAL);
        addOptionalCapability(capabilities, CAPABILITY_UI_FAMILY_CENSUS, includeUiFamilyCensus);
        addOptionalCapability(capabilities, CAPABILITY_UI_TEMPLATE_CATALOG, includeUiTemplateCatalog);
        addCapabilities(capabilities, CAPABILITIES_AFTER_OPTIONAL);
        return Collections.unmodifiableList(capabilities);
    }

    public static void putManifestFiles(
            Map<String, String> files,
            boolean includeUiFamilyCensus,
            boolean includeUiTemplateCatalog) {
        if (files == null) {
            throw new IllegalArgumentException("Raw-export manifest file map must be non-null");
        }
        putManifestFiles(files, BASE_MANIFEST_FILES_BEFORE_OPTIONAL);
        putOptionalManifestFile(files, "uiFamilyCensus", includeUiFamilyCensus);
        putOptionalManifestFile(files, "uiTemplateCatalog", includeUiTemplateCatalog);
        putManifestFiles(files, BASE_MANIFEST_FILES_AFTER_OPTIONAL);
    }

    public static List<String> prohibitedRootOutputs() {
        return PROHIBITED_ROOT_OUTPUTS;
    }

    public static File rawExportRootDirectory(File repositoryDirectory) {
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

    private static void putOptionalManifestFile(Map<String, String> files, String manifestKey, boolean enabled) {
        if (!enabled) {
            return;
        }
        ManifestFile descriptor = optionalManifestFile(manifestKey);
        files.put(descriptor.manifestKey, descriptor.relativePath);
    }

    private static ManifestFile optionalManifestFile(String manifestKey) {
        for (ManifestFile descriptor : OPTIONAL_MANIFEST_FILES) {
            if (descriptor.manifestKey.equals(manifestKey)) {
                return descriptor;
            }
        }
        throw new IllegalStateException("Missing raw-export optional manifest file descriptor: " + manifestKey);
    }

    private static void addCapabilities(List<String> capabilities, List<CapabilityDescriptor> descriptors) {
        for (CapabilityDescriptor descriptor : descriptors) {
            capabilities.add(descriptor.capability);
        }
    }

    private static void addOptionalCapability(List<String> capabilities, String capability, boolean enabled) {
        if (!enabled) {
            return;
        }
        capabilities.add(optionalCapability(capability).capability);
    }

    private static CapabilityDescriptor optionalCapability(String capability) {
        for (CapabilityDescriptor descriptor : OPTIONAL_CAPABILITIES) {
            if (descriptor.capability.equals(capability)) {
                return descriptor;
            }
        }
        throw new IllegalStateException("Missing raw-export optional capability descriptor: " + capability);
    }

    private static List<String> validateStringCatalog(String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(label + " catalog must not be empty");
        }
        Set<String> seen = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalStateException(label + " descriptor must not be null");
            }
            requireNonEmpty(label, value, value);
            if (!seen.add(value)) {
                throw new IllegalStateException("Duplicate " + label + " descriptor: " + value);
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    private static List<String> validatePathCatalog(String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalStateException(label + " catalog must not be empty");
        }
        Set<String> seen = new LinkedHashSet<String>();
        for (String value : values) {
            if (value == null) {
                throw new IllegalStateException(label + " descriptor must not be null");
            }
            requireNonEmpty(label, value, value);
            requireRuntimeRelativePath(label, value, value);
            if (!seen.add(value)) {
                throw new IllegalStateException("Duplicate " + label + " descriptor: " + value);
            }
        }
        return Collections.unmodifiableList(new ArrayList<String>(values));
    }

    private static void requireNonEmpty(String label, String value, String descriptorName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty: " + descriptorName);
        }
    }

    private static void requireRuntimeRelativePath(String label, String value, String descriptorName) {
        if (value.startsWith("/")
                || value.startsWith("\\")
                || value.indexOf('\\') >= 0
                || value.contains("..")
                || value.startsWith(RAW_EXPORT_DIRECTORY + "/")
                || value.startsWith("./")
                || value.endsWith("/")
                || value.indexOf("//") >= 0) {
            throw new IllegalStateException(label + " must be a runtime-relative raw-export path: " + descriptorName);
        }
    }

    private static void requireCompleteCoverage(String label, List<String> expected, Set<String> actual) {
        if (expected == null || expected.isEmpty()) {
            throw new IllegalStateException(label + " expected descriptor set must not be empty");
        }
        for (String expectedKey : expected) {
            if (!actual.contains(expectedKey)) {
                throw new IllegalStateException("Missing " + label + " descriptor: " + expectedKey);
            }
        }
        if (actual.size() != expected.size()) {
            for (String actualKey : actual) {
                if (!expected.contains(actualKey)) {
                    throw new IllegalStateException("Unknown " + label + " descriptor: " + actualKey);
                }
            }
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

    private static final class ManifestFileCatalog {
        private final List<ManifestFile> beforeOptional;
        private final List<ManifestFile> optional;
        private final List<ManifestFile> afterOptional;

        private ManifestFileCatalog(
                List<ManifestFile> beforeOptional,
                List<ManifestFile> optional,
                List<ManifestFile> afterOptional) {
            this.beforeOptional = beforeOptional;
            this.optional = optional;
            this.afterOptional = afterOptional;
        }

        private static ManifestFileCatalog validateAndFreeze(
                List<ManifestFile> beforeOptional,
                List<ManifestFile> optional,
                List<ManifestFile> afterOptional) {
            requireManifestFileGroup("before optional", beforeOptional);
            requireManifestFileGroup("optional", optional);
            requireManifestFileGroup("after optional", afterOptional);

            Set<String> manifestKeys = new LinkedHashSet<String>();
            Set<String> relativePaths = new LinkedHashSet<String>();
            Set<String> optionalKeys = new LinkedHashSet<String>();
            List<ManifestFile> validatedBefore = validateManifestFiles(
                    "before optional",
                    beforeOptional,
                    manifestKeys,
                    relativePaths,
                    null);
            List<ManifestFile> validatedOptional = validateManifestFiles(
                    "optional",
                    optional,
                    manifestKeys,
                    relativePaths,
                    optionalKeys);
            List<ManifestFile> validatedAfter = validateManifestFiles(
                    "after optional",
                    afterOptional,
                    manifestKeys,
                    relativePaths,
                    null);
            requireCompleteCoverage(
                    "raw-export optional manifest file",
                    EXPECTED_OPTIONAL_MANIFEST_KEYS,
                    optionalKeys);
            return new ManifestFileCatalog(validatedBefore, validatedOptional, validatedAfter);
        }

        private static void requireManifestFileGroup(String group, List<ManifestFile> descriptors) {
            if (descriptors == null || descriptors.isEmpty()) {
                throw new IllegalStateException(
                        "Raw-export manifest " + group + " file catalog must not be empty");
            }
        }

        private static List<ManifestFile> validateManifestFiles(
                String group,
                List<ManifestFile> descriptors,
                Set<String> manifestKeys,
                Set<String> relativePaths,
                Set<String> groupKeys) {
            List<ManifestFile> validated = new ArrayList<ManifestFile>();
            for (ManifestFile descriptor : descriptors) {
                if (descriptor == null) {
                    throw new IllegalStateException(
                            "Raw-export manifest file descriptor must not be null: " + group);
                }
                requireNonEmpty("Raw-export manifest key", descriptor.manifestKey, group);
                requireNonEmpty("Raw-export manifest path", descriptor.relativePath, descriptor.manifestKey);
                requireRuntimeRelativePath(
                        "Raw-export manifest path",
                        descriptor.relativePath,
                        descriptor.manifestKey);
                if (!manifestKeys.add(descriptor.manifestKey)) {
                    throw new IllegalStateException(
                            "Duplicate raw-export manifest key: " + descriptor.manifestKey);
                }
                if (!relativePaths.add(descriptor.relativePath)) {
                    throw new IllegalStateException(
                            "Duplicate raw-export manifest path: " + descriptor.relativePath);
                }
                if (groupKeys != null) {
                    groupKeys.add(descriptor.manifestKey);
                }
                validated.add(descriptor);
            }
            return Collections.unmodifiableList(validated);
        }

        private List<ManifestFile> beforeOptional() {
            return beforeOptional;
        }

        private List<ManifestFile> optional() {
            return optional;
        }

        private List<ManifestFile> afterOptional() {
            return afterOptional;
        }
    }

    private static final class CapabilityDescriptor {
        private final String capability;

        private CapabilityDescriptor(String capability) {
            this.capability = capability;
        }
    }

    private static final class CapabilityCatalog {
        private final List<CapabilityDescriptor> beforeOptional;
        private final List<CapabilityDescriptor> optional;
        private final List<CapabilityDescriptor> afterOptional;

        private CapabilityCatalog(
                List<CapabilityDescriptor> beforeOptional,
                List<CapabilityDescriptor> optional,
                List<CapabilityDescriptor> afterOptional) {
            this.beforeOptional = beforeOptional;
            this.optional = optional;
            this.afterOptional = afterOptional;
        }

        private static CapabilityCatalog validateAndFreeze(
                List<CapabilityDescriptor> beforeOptional,
                List<CapabilityDescriptor> optional,
                List<CapabilityDescriptor> afterOptional) {
            requireCapabilityGroup("before optional", beforeOptional);
            requireCapabilityGroup("optional", optional);
            requireCapabilityGroup("after optional", afterOptional);

            Set<String> capabilities = new LinkedHashSet<String>();
            Set<String> optionalCapabilities = new LinkedHashSet<String>();
            List<CapabilityDescriptor> validatedBefore =
                    validateCapabilities("before optional", beforeOptional, capabilities, null);
            List<CapabilityDescriptor> validatedOptional =
                    validateCapabilities("optional", optional, capabilities, optionalCapabilities);
            List<CapabilityDescriptor> validatedAfter =
                    validateCapabilities("after optional", afterOptional, capabilities, null);
            requireCompleteCoverage(
                    "raw-export optional capability",
                    EXPECTED_OPTIONAL_CAPABILITIES,
                    optionalCapabilities);
            return new CapabilityCatalog(validatedBefore, validatedOptional, validatedAfter);
        }

        private static void requireCapabilityGroup(String group, List<CapabilityDescriptor> descriptors) {
            if (descriptors == null || descriptors.isEmpty()) {
                throw new IllegalStateException(
                        "Raw-export capability " + group + " catalog must not be empty");
            }
        }

        private static List<CapabilityDescriptor> validateCapabilities(
                String group,
                List<CapabilityDescriptor> descriptors,
                Set<String> capabilities,
                Set<String> groupCapabilities) {
            List<CapabilityDescriptor> validated = new ArrayList<CapabilityDescriptor>();
            for (CapabilityDescriptor descriptor : descriptors) {
                if (descriptor == null) {
                    throw new IllegalStateException(
                            "Raw-export capability descriptor must not be null: " + group);
                }
                requireNonEmpty("Raw-export capability", descriptor.capability, group);
                if (!capabilities.add(descriptor.capability)) {
                    throw new IllegalStateException(
                            "Duplicate raw-export capability descriptor: " + descriptor.capability);
                }
                if (groupCapabilities != null) {
                    groupCapabilities.add(descriptor.capability);
                }
                validated.add(descriptor);
            }
            return Collections.unmodifiableList(validated);
        }

        private List<CapabilityDescriptor> beforeOptional() {
            return beforeOptional;
        }

        private List<CapabilityDescriptor> optional() {
            return optional;
        }

        private List<CapabilityDescriptor> afterOptional() {
            return afterOptional;
        }
    }
}
