package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportSchemaCatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stable ABI/catalog surface for export validation reports, health policy, and validation errors.
 *
 * <p>Validation writers and policy evaluators must consume this catalog instead of copying schema,
 * status, warning, path-hygiene, or error-record literals at call sites.</p>
 */
final class ExportValidationAbiCatalog {
    private static final List<StringDescriptor> SCHEMA_DESCRIPTORS = validateStringDescriptors(
            "export validation schema",
            Arrays.asList(
                    descriptor("exportValidation", ExportSchemaCatalog.EXPORT_VALIDATION),
                    descriptor("exportError", ExportSchemaCatalog.EXPORT_ERROR)),
            "exportValidation",
            "exportError");

    private static final List<StringDescriptor> STATUS_DESCRIPTORS = validateStringDescriptors(
            "export validation status",
            Arrays.asList(
                    descriptor("ok", "ok"),
                    descriptor("warning", "warning"),
                    descriptor("failed", "failed"),
                    descriptor("blocked", "blocked")),
            "ok",
            "warning",
            "failed",
            "blocked");

    private static final List<StringDescriptor> HEALTH_STATUS_DESCRIPTORS = validateStringDescriptors(
            "export validation health status",
            Arrays.asList(descriptor("healthy", "healthy")),
            "healthy");

    private static final List<StringDescriptor> COMPILE_READINESS_DESCRIPTORS = validateStringDescriptors(
            "export validation compile readiness",
            Arrays.asList(
                    descriptor("ready", ExportValidationReadiness.READY),
                    descriptor("readyWithWarnings", ExportValidationReadiness.READY_WITH_WARNINGS)),
            "ready",
            "readyWithWarnings");

    private static final List<StringDescriptor> ERROR_STAGE_DESCRIPTORS = validateStringDescriptors(
            "export validation error stage",
            Arrays.asList(
                    descriptor("validation", "VALIDATION"),
                    descriptor("unknown", "<unknown>")),
            "validation",
            "unknown");

    private static final CodeMessageDescriptor PATH_HYGIENE_ERROR_DESCRIPTOR = validateCodeMessageDescriptor(
            "pathHygiene",
            "export-path-hygiene",
            "Runtime export payload contains a machine-specific path");

    private static final List<CodeMessageDescriptor> ACTION_DESCRIPTORS = validateCodeMessageDescriptors(
            "export validation action",
            Arrays.asList(
                    codeMessage(
                            "semanticUnclassifiedFamilies",
                            "semantic-unclassified-families",
                            "Review or intentionally classify top unclassified tagged families."),
                    codeMessage(
                            "semanticMissingFacets",
                            "semantic-missing-facets",
                            "Add family facet extraction so NeoNEI can filter/search variants without NBT guessing."),
                    codeMessage(
                            "semanticMissingSortKeys",
                            "semantic-missing-sort-keys",
                            "Add stable family sort keys so expanded variant order remains NEI-like.")),
            "semanticUnclassifiedFamilies",
            "semanticMissingFacets",
            "semanticMissingSortKeys");

    private static final List<StringDescriptor> WARNING_DESCRIPTORS = validateStringDescriptors(
            "export validation warning",
            Arrays.asList(
                    descriptor("renderAssetManifestMissing", "Missing raw-export/assets/textures/index.jsonl.gz."),
                    descriptor("browserLayoutMissing", "Missing raw-export NEI browser group/order streams."),
                    descriptor("itemShardsMissing", "No item json.gz shards found under items/."),
                    descriptor("recipeShardsMissing", "No recipe json.gz shards found under recipes/."),
                    descriptor("renderImagesMissing", "No rendered image files found under image/."),
                    descriptor("staticAtlasFilesMissing", "Static atlas manifest has assets but no atlas PNG files were found."),
                    descriptor("animatedAtlasFilesMissing", "Animated atlas manifest has assets but no animated atlas PNG files were found."),
                    descriptor("atlasManifestAssetsMissing", "Render assets exist but no atlas manifest assets were found."),
                    descriptor("semanticDiagnosticsMissing", "Missing raw-export semantic diagnostics report."),
                    descriptor("semanticRulePackMismatch", "Bundled GTNH semantic rule pack does not fully match the active Java semantic plugins."),
                    descriptor("angelicaBackendMissing", "Native render export did not confirm Angelica as the active backend."),
                    descriptor("neiHandlerMetadataMissing", "NEI handler metadata/layout facts are missing; recipe pages will use generic categories.")),
            "renderAssetManifestMissing",
            "browserLayoutMissing",
            "itemShardsMissing",
            "recipeShardsMissing",
            "renderImagesMissing",
            "staticAtlasFilesMissing",
            "animatedAtlasFilesMissing",
            "atlasManifestAssetsMissing",
            "semanticDiagnosticsMissing",
            "semanticRulePackMismatch",
            "angelicaBackendMissing",
            "neiHandlerMetadataMissing");

    private static final List<StringDescriptor> BLOCKED_DESCRIPTORS = validateStringDescriptors(
            "export validation blocked state",
            Arrays.asList(
                    descriptor("noItemFacts", "No item facts were exported."),
                    descriptor("noRecipeFacts", "No recipe facts were exported."),
                    descriptor("machinePaths", "Runtime payload contains machine-specific local paths."),
                    descriptor("semanticIdentityMapMismatch", "Semantic identity-map row count does not match raw item count."),
                    descriptor("semanticDiagnosticItemMismatch", "Semantic diagnostic item count does not match raw item count."),
                    descriptor(
                            "nativeUiAbi",
                            "Native UI ABI validation is blocked by missing surfaces, bounds errors, "
                                    + "coordinate drift, or interaction contract drift.")),
            "noItemFacts",
            "noRecipeFacts",
            "machinePaths",
            "semanticIdentityMapMismatch",
            "semanticDiagnosticItemMismatch",
            "nativeUiAbi");

    private static final List<PathHygieneRule> PATH_HYGIENE_RULES = validatePathHygieneRules(Arrays.asList(
            pathRule(
                    "windows-backslash-absolute",
                    Pattern.compile("(^|[\\s\"'`\\(\\[\\{:=,])[A-Za-z]:\\\\[A-Za-z0-9._ -]")),
            pathRule(
                    "windows-slash-absolute",
                    Pattern.compile("(^|[\\s\"'`\\(\\[\\{:=,])[A-Za-z]:/[A-Za-z0-9._ -]")),
            pathRule(
                    "minecraft-version-path",
                    Pattern.compile("\\.minecraft[\\\\/]versions", Pattern.CASE_INSENSITIVE)),
            pathRule(
                    "local-gtnh-path",
                    Pattern.compile("[A-Za-z]:[\\\\/]GTNH", Pattern.CASE_INSENSITIVE)),
            pathRule(
                    "local-codex-path",
                    Pattern.compile("[A-Za-z]:[\\\\/]codex", Pattern.CASE_INSENSITIVE)),
            pathRule(
                    "linux-home-absolute",
                    Pattern.compile("(^|[\\s\"'`\\(\\[\\{:=,])/(?:home|Users|mnt|opt|srv)/"))),
            "windows-backslash-absolute",
            "windows-slash-absolute",
            "minecraft-version-path",
            "local-gtnh-path",
            "local-codex-path",
            "linux-home-absolute");

    static final String EXPORT_VALIDATION_SCHEMA = descriptorValue(SCHEMA_DESCRIPTORS, "exportValidation");
    static final String EXPORT_ERROR_SCHEMA = descriptorValue(SCHEMA_DESCRIPTORS, "exportError");

    static final String GENERATED_AT_TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    static final String STATUS_OK = descriptorValue(STATUS_DESCRIPTORS, "ok");
    static final String STATUS_WARNING = descriptorValue(STATUS_DESCRIPTORS, "warning");
    static final String STATUS_FAILED = descriptorValue(STATUS_DESCRIPTORS, "failed");
    static final String STATUS_BLOCKED = descriptorValue(STATUS_DESCRIPTORS, "blocked");
    static final String HEALTH_STATUS_HEALTHY = descriptorValue(HEALTH_STATUS_DESCRIPTORS, "healthy");
    static final String COMPILE_READINESS_READY = descriptorValue(COMPILE_READINESS_DESCRIPTORS, "ready");
    static final String COMPILE_READINESS_READY_WITH_WARNINGS =
            descriptorValue(COMPILE_READINESS_DESCRIPTORS, "readyWithWarnings");

    static final double SEMANTIC_CLASSIFICATION_MIN_COVERAGE_RATIO = 0.80D;

    static final int PATH_HYGIENE_SAMPLE_LIMIT = 100;
    static final String EXPORT_ERROR_STAGE_VALIDATION = descriptorValue(ERROR_STAGE_DESCRIPTORS, "validation");
    static final String EXPORT_ERROR_STAGE_UNKNOWN = descriptorValue(ERROR_STAGE_DESCRIPTORS, "unknown");
    static final String PATH_HYGIENE_ERROR_CODE = PATH_HYGIENE_ERROR_DESCRIPTOR.code;
    static final String PATH_HYGIENE_ERROR_MESSAGE = PATH_HYGIENE_ERROR_DESCRIPTOR.message;

    static final String ACTION_SEMANTIC_UNCLASSIFIED_FAMILIES_CODE =
            descriptorCode(ACTION_DESCRIPTORS, "semanticUnclassifiedFamilies");
    static final String ACTION_SEMANTIC_UNCLASSIFIED_FAMILIES_MESSAGE =
            descriptorMessage(ACTION_DESCRIPTORS, "semanticUnclassifiedFamilies");
    static final String ACTION_SEMANTIC_MISSING_FACETS_CODE =
            descriptorCode(ACTION_DESCRIPTORS, "semanticMissingFacets");
    static final String ACTION_SEMANTIC_MISSING_FACETS_MESSAGE =
            descriptorMessage(ACTION_DESCRIPTORS, "semanticMissingFacets");
    static final String ACTION_SEMANTIC_MISSING_SORT_KEYS_CODE =
            descriptorCode(ACTION_DESCRIPTORS, "semanticMissingSortKeys");
    static final String ACTION_SEMANTIC_MISSING_SORT_KEYS_MESSAGE =
            descriptorMessage(ACTION_DESCRIPTORS, "semanticMissingSortKeys");

    static final String WARNING_RENDER_ASSET_MANIFEST_MISSING =
            descriptorValue(WARNING_DESCRIPTORS, "renderAssetManifestMissing");
    static final String WARNING_BROWSER_LAYOUT_MISSING = descriptorValue(WARNING_DESCRIPTORS, "browserLayoutMissing");
    static final String WARNING_ITEM_SHARDS_MISSING = descriptorValue(WARNING_DESCRIPTORS, "itemShardsMissing");
    static final String WARNING_RECIPE_SHARDS_MISSING = descriptorValue(WARNING_DESCRIPTORS, "recipeShardsMissing");
    static final String WARNING_RENDER_IMAGES_MISSING = descriptorValue(WARNING_DESCRIPTORS, "renderImagesMissing");
    static final String WARNING_STATIC_ATLAS_FILES_MISSING =
            descriptorValue(WARNING_DESCRIPTORS, "staticAtlasFilesMissing");
    static final String WARNING_ANIMATED_ATLAS_FILES_MISSING =
            descriptorValue(WARNING_DESCRIPTORS, "animatedAtlasFilesMissing");
    static final String WARNING_ATLAS_MANIFEST_ASSETS_MISSING =
            descriptorValue(WARNING_DESCRIPTORS, "atlasManifestAssetsMissing");
    static final String WARNING_SEMANTIC_DIAGNOSTICS_MISSING =
            descriptorValue(WARNING_DESCRIPTORS, "semanticDiagnosticsMissing");
    static final String WARNING_SEMANTIC_RULE_PACK_MISMATCH =
            descriptorValue(WARNING_DESCRIPTORS, "semanticRulePackMismatch");
    static final String WARNING_ANGELICA_BACKEND_MISSING =
            descriptorValue(WARNING_DESCRIPTORS, "angelicaBackendMissing");
    static final String WARNING_NEI_HANDLER_METADATA_MISSING =
            descriptorValue(WARNING_DESCRIPTORS, "neiHandlerMetadataMissing");

    static final String BLOCKED_NO_ITEM_FACTS = descriptorValue(BLOCKED_DESCRIPTORS, "noItemFacts");
    static final String BLOCKED_NO_RECIPE_FACTS = descriptorValue(BLOCKED_DESCRIPTORS, "noRecipeFacts");
    static final String BLOCKED_MACHINE_PATHS = descriptorValue(BLOCKED_DESCRIPTORS, "machinePaths");
    static final String BLOCKED_SEMANTIC_IDENTITY_MAP_MISMATCH =
            descriptorValue(BLOCKED_DESCRIPTORS, "semanticIdentityMapMismatch");
    static final String BLOCKED_SEMANTIC_DIAGNOSTIC_ITEM_MISMATCH =
            descriptorValue(BLOCKED_DESCRIPTORS, "semanticDiagnosticItemMismatch");
    static final String BLOCKED_NATIVE_UI_ABI = descriptorValue(BLOCKED_DESCRIPTORS, "nativeUiAbi");

    private ExportValidationAbiCatalog() {}

    static List<PathHygieneRule> pathHygieneRules() {
        return PATH_HYGIENE_RULES;
    }

    static String renderAssetMissingPrimaryArtifactsWarning(long count) {
        return "Render assets with missing primary/static artifacts: " + count;
    }

    static String renderAssetMissingTimelineFramesWarning(long count) {
        return "Render assets with missing timeline frame files: " + count;
    }

    static String suspiciousStaticSingularityAssetsWarning(long count) {
        return "Singularity-like render assets exported without animation: " + count;
    }

    static String browserLayoutMissingAtlasCoverageWarning(long count) {
        return "Browser layout items missing atlas coverage: " + count;
    }

    static String exportPathHygieneWarning(long count) {
        return "Runtime export payloads contain machine-specific paths: " + count;
    }

    static String semanticClassificationCoverageWarning(Double ratio) {
        return "Semantic classification coverage below 80%: " + ratio;
    }

    static String semanticMissingFacetFamilyWarning(long count) {
        return "Semantic families missing facet extraction: " + count;
    }

    static String semanticMissingSortKeyFamilyWarning(long count) {
        return "Semantic families missing stable sort keys: " + count;
    }

    static String renderTextureSpritesMissingTimingWarning(long count) {
        return "Native texture sprites missing animation timing: " + count;
    }

    static String renderShaderItemsMissingCaptureWarning(long count) {
        return "Shader/custom renderer items missing native capture assets: " + count;
    }

    static String renderUnknownSpecialRenderersWarning(long count) {
        return "Unknown special item renderers need explicit Angelica/native classification: " + count;
    }

    static String renderFramebufferCapturesWithoutFramesWarning(long count) {
        return "Native framebuffer captures without frame data: " + count;
    }

    static String nativeUiMissingSurfacesWarning(long count) {
        return "Native UI surfaces missing captured backgrounds: " + count;
    }

    static String nativeUiGeometryBoundsWarning(
            long slots,
            long rects,
            long primitives,
            long backgrounds) {
        return "Native UI geometry bounds violations: slots="
                + slots
                + ", rects="
                + rects
                + ", primitives="
                + primitives
                + ", backgrounds="
                + backgrounds;
    }

    static String nativeUiCoordinateContractWarning(long count) {
        return "Native UI coordinate contract violations: " + count;
    }

    static String nativeUiInteractionContractWarning(long count) {
        return "Native UI interaction contract violations: " + count;
    }

    private static StringDescriptor descriptor(String key, String value) {
        return new StringDescriptor(key, value);
    }

    private static CodeMessageDescriptor codeMessage(String key, String code, String message) {
        return new CodeMessageDescriptor(key, code, message);
    }

    private static CodeMessageDescriptor validateCodeMessageDescriptor(String key, String code, String message) {
        return validateCodeMessageDescriptors(
                "export validation code/message",
                Arrays.asList(codeMessage(key, code, message)),
                key)
                .get(0);
    }

    private static PathHygieneRule pathRule(String name, Pattern pattern) {
        return new PathHygieneRule(name, pattern);
    }

    private static List<StringDescriptor> validateStringDescriptors(
            String label,
            List<StringDescriptor> descriptors,
            String... expectedKeys) {
        if (descriptors == null) {
            throw new IllegalStateException(label + " descriptors must not be null");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedKeys));
        Set<String> seenKeys = new LinkedHashSet<String>();
        Set<String> seenValues = new LinkedHashSet<String>();
        for (StringDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException(label + " descriptor must not be null");
            }
            requireExpectedKey(label, expected, descriptor.key);
            requireNonEmpty(label + " descriptor key", descriptor.key);
            requireNonEmpty(label + " descriptor value", descriptor.value);
            if (!seenKeys.add(descriptor.key)) {
                throw new IllegalStateException("Duplicate " + label + " descriptor: " + descriptor.key);
            }
            if (!seenValues.add(descriptor.value)) {
                throw new IllegalStateException("Duplicate " + label + " descriptor value: " + descriptor.value);
            }
        }
        requireCompleteCoverage(label, expected, seenKeys);
        return Collections.unmodifiableList(new ArrayList<StringDescriptor>(descriptors));
    }

    private static List<CodeMessageDescriptor> validateCodeMessageDescriptors(
            String label,
            List<CodeMessageDescriptor> descriptors,
            String... expectedKeys) {
        if (descriptors == null) {
            throw new IllegalStateException(label + " descriptors must not be null");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedKeys));
        Set<String> seenKeys = new LinkedHashSet<String>();
        Set<String> seenCodes = new LinkedHashSet<String>();
        for (CodeMessageDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException(label + " descriptor must not be null");
            }
            requireExpectedKey(label, expected, descriptor.key);
            requireNonEmpty(label + " descriptor key", descriptor.key);
            requireNonEmpty(label + " descriptor code", descriptor.code);
            requireNonEmpty(label + " descriptor message", descriptor.message);
            if (!seenKeys.add(descriptor.key)) {
                throw new IllegalStateException("Duplicate " + label + " descriptor: " + descriptor.key);
            }
            if (!seenCodes.add(descriptor.code)) {
                throw new IllegalStateException("Duplicate " + label + " descriptor code: " + descriptor.code);
            }
        }
        requireCompleteCoverage(label, expected, seenKeys);
        return Collections.unmodifiableList(new ArrayList<CodeMessageDescriptor>(descriptors));
    }

    private static List<PathHygieneRule> validatePathHygieneRules(
            List<PathHygieneRule> rules,
            String... expectedNames) {
        if (rules == null) {
            throw new IllegalStateException("path hygiene rule descriptors must not be null");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedNames));
        Set<String> seenNames = new LinkedHashSet<String>();
        Set<String> seenPatterns = new LinkedHashSet<String>();
        for (PathHygieneRule rule : rules) {
            if (rule == null) {
                throw new IllegalStateException("path hygiene rule descriptor must not be null");
            }
            requireExpectedKey("path hygiene rule", expected, rule.name);
            requireNonEmpty("path hygiene rule name", rule.name);
            if (rule.pattern == null) {
                throw new IllegalStateException("path hygiene rule pattern must not be null: " + rule.name);
            }
            if (!seenNames.add(rule.name)) {
                throw new IllegalStateException("Duplicate path hygiene rule descriptor: " + rule.name);
            }
            String patternKey = rule.pattern.pattern() + "/" + rule.pattern.flags();
            if (!seenPatterns.add(patternKey)) {
                throw new IllegalStateException("Duplicate path hygiene rule pattern: " + rule.name);
            }
        }
        requireCompleteCoverage("path hygiene rule", expected, seenNames);
        return Collections.unmodifiableList(new ArrayList<PathHygieneRule>(rules));
    }

    private static void requireExpectedKey(String label, Set<String> expected, String key) {
        if (!expected.contains(key)) {
            throw new IllegalStateException("Unknown " + label + " descriptor: " + key);
        }
    }

    private static void requireCompleteCoverage(String label, Set<String> expected, Set<String> seenKeys) {
        for (String key : expected) {
            if (!seenKeys.contains(key)) {
                throw new IllegalStateException("Missing " + label + " descriptor: " + key);
            }
        }
    }

    private static void requireNonEmpty(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty");
        }
    }

    private static String descriptorValue(List<StringDescriptor> descriptors, String key) {
        for (StringDescriptor descriptor : descriptors) {
            if (descriptor.key.equals(key)) {
                return descriptor.value;
            }
        }
        throw new IllegalStateException("Missing descriptor projection: " + key);
    }

    private static String descriptorCode(List<CodeMessageDescriptor> descriptors, String key) {
        for (CodeMessageDescriptor descriptor : descriptors) {
            if (descriptor.key.equals(key)) {
                return descriptor.code;
            }
        }
        throw new IllegalStateException("Missing descriptor code projection: " + key);
    }

    private static String descriptorMessage(List<CodeMessageDescriptor> descriptors, String key) {
        for (CodeMessageDescriptor descriptor : descriptors) {
            if (descriptor.key.equals(key)) {
                return descriptor.message;
            }
        }
        throw new IllegalStateException("Missing descriptor message projection: " + key);
    }

    static final class PathHygieneRule {
        final String name;
        final Pattern pattern;

        private PathHygieneRule(String name, Pattern pattern) {
            this.name = name;
            this.pattern = pattern;
        }
    }

    private static final class StringDescriptor {
        final String key;
        final String value;

        StringDescriptor(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private static final class CodeMessageDescriptor {
        final String key;
        final String code;
        final String message;

        CodeMessageDescriptor(String key, String code, String message) {
            this.key = key;
            this.code = code;
            this.message = message;
        }
    }
}
