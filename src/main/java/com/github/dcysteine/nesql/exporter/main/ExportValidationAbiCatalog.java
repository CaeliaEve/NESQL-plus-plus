package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportSchemaCatalog;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Stable ABI/catalog surface for export validation reports, health policy, and validation errors.
 *
 * <p>Validation writers and policy evaluators must consume this catalog instead of copying schema,
 * status, warning, path-hygiene, or error-record literals at call sites.</p>
 */
final class ExportValidationAbiCatalog {
    static final String EXPORT_VALIDATION_SCHEMA = ExportSchemaCatalog.EXPORT_VALIDATION;
    static final String EXPORT_ERROR_SCHEMA = ExportSchemaCatalog.EXPORT_ERROR;

    static final String GENERATED_AT_TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    static final String STATUS_OK = "ok";
    static final String STATUS_WARNING = "warning";
    static final String STATUS_FAILED = "failed";
    static final String STATUS_BLOCKED = "blocked";
    static final String HEALTH_STATUS_HEALTHY = "healthy";
    static final String COMPILE_READINESS_READY = "ready";
    static final String COMPILE_READINESS_READY_WITH_WARNINGS = "ready-with-warnings";

    static final double SEMANTIC_CLASSIFICATION_MIN_COVERAGE_RATIO = 0.80D;

    static final int PATH_HYGIENE_SAMPLE_LIMIT = 100;
    static final String EXPORT_ERROR_STAGE_VALIDATION = "VALIDATION";
    static final String EXPORT_ERROR_STAGE_UNKNOWN = "<unknown>";
    static final String PATH_HYGIENE_ERROR_CODE = "export-path-hygiene";
    static final String PATH_HYGIENE_ERROR_MESSAGE =
            "Runtime export payload contains a machine-specific path";

    static final String ACTION_SEMANTIC_UNCLASSIFIED_FAMILIES_CODE = "semantic-unclassified-families";
    static final String ACTION_SEMANTIC_UNCLASSIFIED_FAMILIES_MESSAGE =
            "Review or intentionally classify top unclassified tagged families.";
    static final String ACTION_SEMANTIC_MISSING_FACETS_CODE = "semantic-missing-facets";
    static final String ACTION_SEMANTIC_MISSING_FACETS_MESSAGE =
            "Add family facet extraction so NeoNEI can filter/search variants without NBT guessing.";
    static final String ACTION_SEMANTIC_MISSING_SORT_KEYS_CODE = "semantic-missing-sort-keys";
    static final String ACTION_SEMANTIC_MISSING_SORT_KEYS_MESSAGE =
            "Add stable family sort keys so expanded variant order remains NEI-like.";

    static final String WARNING_RENDER_ASSET_MANIFEST_MISSING =
            "Missing raw-export/assets/textures/index.jsonl.gz.";
    static final String WARNING_BROWSER_LAYOUT_MISSING =
            "Missing raw-export NEI browser group/order streams.";
    static final String WARNING_ITEM_SHARDS_MISSING = "No item json.gz shards found under items/.";
    static final String WARNING_RECIPE_SHARDS_MISSING =
            "No recipe json.gz shards found under recipes/.";
    static final String WARNING_RENDER_IMAGES_MISSING =
            "No rendered image files found under image/.";
    static final String WARNING_STATIC_ATLAS_FILES_MISSING =
            "Static atlas manifest has assets but no atlas PNG files were found.";
    static final String WARNING_ANIMATED_ATLAS_FILES_MISSING =
            "Animated atlas manifest has assets but no animated atlas PNG files were found.";
    static final String WARNING_ATLAS_MANIFEST_ASSETS_MISSING =
            "Render assets exist but no atlas manifest assets were found.";
    static final String WARNING_SEMANTIC_DIAGNOSTICS_MISSING =
            "Missing raw-export semantic diagnostics report.";
    static final String WARNING_SEMANTIC_RULE_PACK_MISMATCH =
            "Bundled GTNH semantic rule pack does not fully match the active Java semantic plugins.";
    static final String WARNING_ANGELICA_BACKEND_MISSING =
            "Native render export did not confirm Angelica as the active backend.";
    static final String WARNING_NEI_HANDLER_METADATA_MISSING =
            "NEI handler metadata/layout facts are missing; recipe pages will use generic categories.";

    static final String BLOCKED_NO_ITEM_FACTS = "No item facts were exported.";
    static final String BLOCKED_NO_RECIPE_FACTS = "No recipe facts were exported.";
    static final String BLOCKED_MACHINE_PATHS =
            "Runtime payload contains machine-specific local paths.";
    static final String BLOCKED_SEMANTIC_IDENTITY_MAP_MISMATCH =
            "Semantic identity-map row count does not match raw item count.";
    static final String BLOCKED_SEMANTIC_DIAGNOSTIC_ITEM_MISMATCH =
            "Semantic diagnostic item count does not match raw item count.";
    static final String BLOCKED_NATIVE_UI_ABI =
            "Native UI ABI validation is blocked by missing surfaces, bounds errors, "
                    + "coordinate drift, or interaction contract drift.";

    private static final List<PathHygieneRule> PATH_HYGIENE_RULES = Collections.unmodifiableList(Arrays.asList(
            new PathHygieneRule(
                    "windows-backslash-absolute",
                    Pattern.compile("(^|[\\s\\\"'`\\(\\[\\{:=,])[A-Za-z]:\\\\[A-Za-z0-9._ -]")),
            new PathHygieneRule(
                    "windows-slash-absolute",
                    Pattern.compile("(^|[\\s\\\"'`\\(\\[\\{:=,])[A-Za-z]:/[A-Za-z0-9._ -]")),
            new PathHygieneRule(
                    "minecraft-version-path",
                    Pattern.compile("\\.minecraft[\\\\/]versions", Pattern.CASE_INSENSITIVE)),
            new PathHygieneRule(
                    "local-gtnh-path",
                    Pattern.compile("[A-Za-z]:[\\\\/]GTNH", Pattern.CASE_INSENSITIVE)),
            new PathHygieneRule(
                    "local-codex-path",
                    Pattern.compile("[A-Za-z]:[\\\\/]codex", Pattern.CASE_INSENSITIVE)),
            new PathHygieneRule(
                    "linux-home-absolute",
                    Pattern.compile("(^|[\\s\\\"'`\\(\\[\\{:=,])/(?:home|Users|mnt|opt|srv)/"))));

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

    static final class PathHygieneRule {
        final String name;
        final Pattern pattern;

        private PathHygieneRule(String name, Pattern pattern) {
            this.name = name;
            this.pattern = pattern;
        }
    }
}
