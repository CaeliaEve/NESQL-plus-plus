package com.github.dcysteine.nesql.exporter.main;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Descriptor-owned validation health rules, blocked gates, actionable issues, and status policy. */
final class ExportValidationHealthPolicyCatalog {
    private static final List<WarningRuleDescriptor> WARNING_RULES = validateWarningRules(Arrays.asList(
            warningRule(
                    "render-asset-manifest",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return !report.renderAssetManifestPresent;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_RENDER_ASSET_MANIFEST_MISSING)),
            warningRule(
                    "browser-layout",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return !report.browserLayoutPresent;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_BROWSER_LAYOUT_MISSING)),
            warningRule(
                    "item-shards",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.itemsJsonGzFiles == 0;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_ITEM_SHARDS_MISSING)),
            warningRule(
                    "recipe-shards",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.recipeJsonGzFiles == 0;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_RECIPE_SHARDS_MISSING)),
            warningRule(
                    "render-images",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.imagePngFiles == 0 && report.imageGifFiles == 0;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_RENDER_IMAGES_MISSING)),
            warningRule(
                    "static-atlas-files",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.staticAtlasPngFiles == 0
                                    && report.staticAtlasManifestAssets > 0;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_STATIC_ATLAS_FILES_MISSING)),
            warningRule(
                    "animated-atlas-files",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.animatedAtlasPngFiles == 0
                                    && report.animatedAtlasManifestAssets > 0;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_ANIMATED_ATLAS_FILES_MISSING)),
            warningRule(
                    "render-asset-primary-artifacts",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.renderAssetMissingPrimaryArtifacts > 0;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.renderAssetMissingPrimaryArtifactsWarning(
                                    report.renderAssetMissingPrimaryArtifacts);
                        }
                    }),
            warningRule(
                    "render-asset-timeline-frames",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.renderAssetMissingTimelineFrames > 0;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.renderAssetMissingTimelineFramesWarning(
                                    report.renderAssetMissingTimelineFrames);
                        }
                    }),
            warningRule(
                    "suspicious-static-singularity",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.suspiciousStaticSingularityAssets > 0;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.suspiciousStaticSingularityAssetsWarning(
                                    report.suspiciousStaticSingularityAssets);
                        }
                    }),
            warningRule(
                    "atlas-manifest-assets",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.renderAssetManifestAssets > 0
                                    && report.totalAtlasManifestAssets == 0;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_ATLAS_MANIFEST_ASSETS_MISSING)),
            warningRule(
                    "browser-layout-atlas-coverage",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.browserAtlasPresent
                                    && report.browserLayoutPresent
                                    && report.browserAtlasLayoutMissingItems > 0;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.browserLayoutMissingAtlasCoverageWarning(
                                    report.browserAtlasLayoutMissingItems);
                        }
                    }),
            warningRule(
                    "export-path-hygiene",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.exportPathHygieneViolations > 0;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.exportPathHygieneWarning(
                                    report.exportPathHygieneViolations);
                        }
                    }),
            warningRule(
                    "semantic-diagnostics",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return !report.semanticDiagnosticsPresent && report.rawItems > 0L;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_SEMANTIC_DIAGNOSTICS_MISSING)),
            warningRule(
                    "semantic-classification-coverage",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.semanticTaggedItems > 0L
                                    && report.semanticClassificationCoverageRatio != null
                                    && report.semanticClassificationCoverageRatio
                                            < ExportValidationAbiCatalog
                                                    .SEMANTIC_CLASSIFICATION_MIN_COVERAGE_RATIO;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.semanticClassificationCoverageWarning(
                                    report.semanticClassificationCoverageRatio);
                        }
                    }),
            warningRule(
                    "semantic-missing-facet-families",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.semanticMissingFacetFamilyCount > 0;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.semanticMissingFacetFamilyWarning(
                                    report.semanticMissingFacetFamilyCount);
                        }
                    }),
            warningRule(
                    "semantic-missing-sort-key-families",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.semanticMissingSortKeyFamilyCount > 0;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.semanticMissingSortKeyFamilyWarning(
                                    report.semanticMissingSortKeyFamilyCount);
                        }
                    }),
            warningRule(
                    "semantic-rule-pack",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.semanticRulePack == null
                                    || !ExportValidationAbiCatalog.STATUS_OK.equals(
                                            report.semanticRulePack.status);
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_SEMANTIC_RULE_PACK_MISMATCH)),
            warningRule(
                    "angelica-backend",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.renderBackendFacts > 0L && report.renderBackendAngelica == 0L;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_ANGELICA_BACKEND_MISSING)),
            warningRule(
                    "render-texture-sprites-timing",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.renderTextureSpritesMissingTiming > 0L;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.renderTextureSpritesMissingTimingWarning(
                                    report.renderTextureSpritesMissingTiming);
                        }
                    }),
            warningRule(
                    "render-shader-items-capture",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.renderShaderItemsMissingCapture > 0L;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.renderShaderItemsMissingCaptureWarning(
                                    report.renderShaderItemsMissingCapture);
                        }
                    }),
            warningRule(
                    "render-unknown-special-renderers",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.renderUnknownSpecialRenderers > 0L;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.renderUnknownSpecialRenderersWarning(
                                    report.renderUnknownSpecialRenderers);
                        }
                    }),
            warningRule(
                    "render-framebuffer-captures",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.renderFramebufferCapturesWithoutFrames > 0L;
                        }
                    },
                    new MessageBuilder() {
                        @Override
                        public String build(ExportValidationReport report) {
                            return ExportValidationAbiCatalog.renderFramebufferCapturesWithoutFramesWarning(
                                    report.renderFramebufferCapturesWithoutFrames);
                        }
                    }),
            warningRule(
                    "nei-handler-metadata",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.rawRecipes > 0L
                                    && (report.rawNeiHandlers == 0L
                                            || report.rawNeiHandlerLayouts == 0L);
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.WARNING_NEI_HANDLER_METADATA_MISSING)),
            warningEmitter(
                    "native-ui-evidence",
                    new WarningEmitter() {
                        @Override
                        public void emit(ExportValidationReport report) {
                            ExportValidationNativeUiEvidenceCatalog.collectWarnings(report);
                        }
                    })),
            "render-asset-manifest",
            "browser-layout",
            "item-shards",
            "recipe-shards",
            "render-images",
            "static-atlas-files",
            "animated-atlas-files",
            "render-asset-primary-artifacts",
            "render-asset-timeline-frames",
            "suspicious-static-singularity",
            "atlas-manifest-assets",
            "browser-layout-atlas-coverage",
            "export-path-hygiene",
            "semantic-diagnostics",
            "semantic-classification-coverage",
            "semantic-missing-facet-families",
            "semantic-missing-sort-key-families",
            "semantic-rule-pack",
            "angelica-backend",
            "render-texture-sprites-timing",
            "render-shader-items-capture",
            "render-unknown-special-renderers",
            "render-framebuffer-captures",
            "nei-handler-metadata",
            "native-ui-evidence");

    private static final List<BlockedRuleDescriptor> BLOCKED_RULES = validateBlockedRules(Arrays.asList(
            blockedRule(
                    "no-item-facts",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.itemsJsonGzFiles == 0 && report.rawItems == 0L;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.BLOCKED_NO_ITEM_FACTS)),
            blockedRule(
                    "no-recipe-facts",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.recipeJsonGzFiles == 0 && report.rawRecipes == 0L;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.BLOCKED_NO_RECIPE_FACTS)),
            blockedRule(
                    "machine-paths",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.exportPathHygieneViolations > 0;
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.BLOCKED_MACHINE_PATHS)),
            blockedRule(
                    "semantic-identity-map-mismatch",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.rawItems > 0L
                                    && report.semanticTotalItems > 0L
                                    && report.semanticIdentityMapRows > 0L
                                    && report.semanticIdentityMapRows != report.rawItems;
                        }
                    },
                    constantMessage(
                            ExportValidationAbiCatalog.BLOCKED_SEMANTIC_IDENTITY_MAP_MISMATCH)),
            blockedRule(
                    "semantic-diagnostic-item-mismatch",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return report.rawItems > 0L
                                    && report.semanticDiagnosticsPresent
                                    && report.semanticTotalItems != report.rawItems;
                        }
                    },
                    constantMessage(
                            ExportValidationAbiCatalog.BLOCKED_SEMANTIC_DIAGNOSTIC_ITEM_MISMATCH)),
            blockedRule(
                    "native-ui-abi",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return ExportValidationNativeUiEvidenceCatalog.isAbiBlocked(report);
                        }
                    },
                    constantMessage(ExportValidationAbiCatalog.BLOCKED_NATIVE_UI_ABI))),
            "no-item-facts",
            "no-recipe-facts",
            "machine-paths",
            "semantic-identity-map-mismatch",
            "semantic-diagnostic-item-mismatch",
            "native-ui-abi");

    private static final List<ActionableIssueDescriptor> ACTIONABLE_ISSUE_RULES =
            validateActionableIssueRules(Arrays.asList(
                    actionableIssue(
                            "semantic-unclassified-families",
                            ExportValidationAbiCatalog.ACTION_SEMANTIC_UNCLASSIFIED_FAMILIES_CODE,
                            ExportValidationAbiCatalog.ACTION_SEMANTIC_UNCLASSIFIED_FAMILIES_MESSAGE,
                            new JsonArraySelector() {
                                @Override
                                public JsonArray select(ExportValidationReport report) {
                                    return report.semanticTopUnclassifiedFamilyActions;
                                }
                            }),
                    actionableIssue(
                            "semantic-missing-facets",
                            ExportValidationAbiCatalog.ACTION_SEMANTIC_MISSING_FACETS_CODE,
                            ExportValidationAbiCatalog.ACTION_SEMANTIC_MISSING_FACETS_MESSAGE,
                            new JsonArraySelector() {
                                @Override
                                public JsonArray select(ExportValidationReport report) {
                                    return report.semanticMissingFacetFamilies;
                                }
                            }),
                    actionableIssue(
                            "semantic-missing-sort-keys",
                            ExportValidationAbiCatalog.ACTION_SEMANTIC_MISSING_SORT_KEYS_CODE,
                            ExportValidationAbiCatalog.ACTION_SEMANTIC_MISSING_SORT_KEYS_MESSAGE,
                            new JsonArraySelector() {
                                @Override
                                public JsonArray select(ExportValidationReport report) {
                                    return report.semanticMissingSortKeyFamilies;
                                }
                            })),
                    "semantic-unclassified-families",
                    "semantic-missing-facets",
                    "semantic-missing-sort-keys");

    private static final List<StatusRuleDescriptor> STATUS_RULES = validateStatusRules(Arrays.asList(
            statusRule(
                    "blocked",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return !report.blockedIssues.isEmpty();
                        }
                    },
                    ExportValidationAbiCatalog.STATUS_BLOCKED,
                    ExportValidationAbiCatalog.STATUS_BLOCKED),
            statusRule(
                    "warning",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return !report.warnings.isEmpty() || !report.actionableIssues.isEmpty();
                        }
                    },
                    ExportValidationAbiCatalog.STATUS_WARNING,
                    ExportValidationAbiCatalog.COMPILE_READINESS_READY_WITH_WARNINGS),
            statusRule(
                    "healthy",
                    new ReportPredicate() {
                        @Override
                        public boolean matches(ExportValidationReport report) {
                            return true;
                        }
                    },
                    ExportValidationAbiCatalog.HEALTH_STATUS_HEALTHY,
                    ExportValidationAbiCatalog.COMPILE_READINESS_READY)),
            "blocked",
            "warning",
            "healthy");

    private ExportValidationHealthPolicyCatalog() {}

    static void collectWarnings(ExportValidationReport report) {
        requireReport(report);
        for (WarningRuleDescriptor descriptor : WARNING_RULES) {
            descriptor.apply(report);
        }
    }

    static void applyBlockedRules(ExportValidationReport report) {
        requireReport(report);
        for (BlockedRuleDescriptor descriptor : BLOCKED_RULES) {
            descriptor.apply(report);
        }
    }

    static void collectActionableIssues(ExportValidationReport report) {
        requireReport(report);
        for (ActionableIssueDescriptor descriptor : ACTIONABLE_ISSUE_RULES) {
            descriptor.apply(report);
        }
    }

    static void resolveStatus(ExportValidationReport report) {
        requireReport(report);
        for (StatusRuleDescriptor descriptor : STATUS_RULES) {
            if (descriptor.predicate.matches(report)) {
                report.healthStatus = descriptor.healthStatus;
                report.compileReadinessStatus = descriptor.compileReadinessStatus;
                return;
            }
        }
        throw new IllegalStateException("Export validation health status catalog did not resolve");
    }

    private static WarningRuleDescriptor warningRule(
            String id,
            final ReportPredicate predicate,
            final MessageBuilder messageBuilder) {
        return warningEmitter(
                id,
                new WarningEmitter() {
                    @Override
                    public void emit(ExportValidationReport report) {
                        if (predicate.matches(report)) {
                            addWarning(report, messageBuilder.build(report));
                        }
                    }
                });
    }

    private static WarningRuleDescriptor warningEmitter(String id, WarningEmitter emitter) {
        return new WarningRuleDescriptor(id, emitter);
    }

    private static BlockedRuleDescriptor blockedRule(
            String id,
            ReportPredicate predicate,
            MessageBuilder messageBuilder) {
        return new BlockedRuleDescriptor(id, predicate, messageBuilder);
    }

    private static ActionableIssueDescriptor actionableIssue(
            String id,
            String code,
            String message,
            JsonArraySelector detailsSelector) {
        return new ActionableIssueDescriptor(id, code, message, detailsSelector);
    }

    private static StatusRuleDescriptor statusRule(
            String id,
            ReportPredicate predicate,
            String healthStatus,
            String compileReadinessStatus) {
        return new StatusRuleDescriptor(id, predicate, healthStatus, compileReadinessStatus);
    }

    private static MessageBuilder constantMessage(final String message) {
        return new MessageBuilder() {
            @Override
            public String build(ExportValidationReport report) {
                return message;
            }
        };
    }

    private static List<WarningRuleDescriptor> validateWarningRules(
            List<WarningRuleDescriptor> descriptors,
            String... expectedIds) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Export validation warning rule catalog must not be empty");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedIds));
        Set<String> seen = new LinkedHashSet<String>();
        for (WarningRuleDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Export validation warning rule must not be null");
            }
            validateDescriptorId("warning rule", expected, seen, descriptor.id);
            if (descriptor.emitter == null) {
                throw new IllegalStateException(
                        "Export validation warning emitter must not be null: " + descriptor.id);
            }
        }
        requireCompleteCoverage("warning rule", expected, seen);
        return Collections.unmodifiableList(new ArrayList<WarningRuleDescriptor>(descriptors));
    }

    private static List<BlockedRuleDescriptor> validateBlockedRules(
            List<BlockedRuleDescriptor> descriptors,
            String... expectedIds) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Export validation blocked rule catalog must not be empty");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedIds));
        Set<String> seen = new LinkedHashSet<String>();
        for (BlockedRuleDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Export validation blocked rule must not be null");
            }
            validateDescriptorId("blocked rule", expected, seen, descriptor.id);
            if (descriptor.predicate == null) {
                throw new IllegalStateException(
                        "Export validation blocked predicate must not be null: " + descriptor.id);
            }
            if (descriptor.messageBuilder == null) {
                throw new IllegalStateException(
                        "Export validation blocked message must not be null: " + descriptor.id);
            }
        }
        requireCompleteCoverage("blocked rule", expected, seen);
        return Collections.unmodifiableList(new ArrayList<BlockedRuleDescriptor>(descriptors));
    }

    private static List<ActionableIssueDescriptor> validateActionableIssueRules(
            List<ActionableIssueDescriptor> descriptors,
            String... expectedIds) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException(
                    "Export validation actionable issue catalog must not be empty");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedIds));
        Set<String> seen = new LinkedHashSet<String>();
        Set<String> seenCodes = new LinkedHashSet<String>();
        for (ActionableIssueDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException(
                        "Export validation actionable issue descriptor must not be null");
            }
            validateDescriptorId("actionable issue", expected, seen, descriptor.id);
            requireNonEmpty("Export validation actionable issue code", descriptor.code);
            requireNonEmpty("Export validation actionable issue message", descriptor.message);
            if (!seenCodes.add(descriptor.code)) {
                throw new IllegalStateException(
                        "Duplicate export validation actionable issue code: " + descriptor.code);
            }
            if (descriptor.detailsSelector == null) {
                throw new IllegalStateException(
                        "Export validation actionable issue details selector must not be null: "
                                + descriptor.id);
            }
        }
        requireCompleteCoverage("actionable issue", expected, seen);
        return Collections.unmodifiableList(new ArrayList<ActionableIssueDescriptor>(descriptors));
    }

    private static List<StatusRuleDescriptor> validateStatusRules(
            List<StatusRuleDescriptor> descriptors,
            String... expectedIds) {
        if (descriptors == null || descriptors.isEmpty()) {
            throw new IllegalStateException("Export validation status rule catalog must not be empty");
        }
        Set<String> expected = new LinkedHashSet<String>(Arrays.asList(expectedIds));
        Set<String> seen = new LinkedHashSet<String>();
        for (StatusRuleDescriptor descriptor : descriptors) {
            if (descriptor == null) {
                throw new IllegalStateException("Export validation status rule must not be null");
            }
            validateDescriptorId("status rule", expected, seen, descriptor.id);
            if (descriptor.predicate == null) {
                throw new IllegalStateException(
                        "Export validation status predicate must not be null: " + descriptor.id);
            }
            requireNonEmpty("Export validation health status", descriptor.healthStatus);
            requireNonEmpty(
                    "Export validation compile readiness status",
                    descriptor.compileReadinessStatus);
        }
        requireCompleteCoverage("status rule", expected, seen);
        return Collections.unmodifiableList(new ArrayList<StatusRuleDescriptor>(descriptors));
    }

    private static void validateDescriptorId(
            String label,
            Set<String> expected,
            Set<String> seen,
            String id) {
        requireNonEmpty("Export validation " + label + " id", id);
        if (!expected.contains(id)) {
            throw new IllegalStateException("Unknown export validation " + label + ": " + id);
        }
        if (!seen.add(id)) {
            throw new IllegalStateException("Duplicate export validation " + label + ": " + id);
        }
    }

    private static void requireCompleteCoverage(
            String label,
            Set<String> expected,
            Set<String> seen) {
        for (String id : expected) {
            if (!seen.contains(id)) {
                throw new IllegalStateException("Missing export validation " + label + ": " + id);
            }
        }
    }

    private static void addWarning(ExportValidationReport report, String message) {
        requireNonEmpty("Export validation warning message", message);
        report.warnings.add(message);
    }

    private static void addBlockedIssue(ExportValidationReport report, String message) {
        requireNonEmpty("Export validation blocked issue message", message);
        report.blockedIssues.add(message);
    }

    private static void addActionableIssue(
            ExportValidationReport report,
            String code,
            String message,
            JsonArray details) {
        if (details == null || details.size() == 0) {
            return;
        }
        JsonObject issue = new JsonObject();
        issue.addProperty("code", code);
        issue.addProperty("message", message);
        issue.add("details", details);
        report.actionableIssues.add(issue);
    }

    private static void requireReport(ExportValidationReport report) {
        if (report == null) {
            throw new IllegalStateException("Export validation health report must not be null");
        }
    }

    private static void requireNonEmpty(String label, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(label + " must be non-empty");
        }
    }

    private interface WarningEmitter {
        void emit(ExportValidationReport report);
    }

    private interface ReportPredicate {
        boolean matches(ExportValidationReport report);
    }

    private interface MessageBuilder {
        String build(ExportValidationReport report);
    }

    private interface JsonArraySelector {
        JsonArray select(ExportValidationReport report);
    }

    private static final class WarningRuleDescriptor {
        final String id;
        final WarningEmitter emitter;

        WarningRuleDescriptor(String id, WarningEmitter emitter) {
            this.id = id;
            this.emitter = emitter;
        }

        void apply(ExportValidationReport report) {
            emitter.emit(report);
        }
    }

    private static final class BlockedRuleDescriptor {
        final String id;
        final ReportPredicate predicate;
        final MessageBuilder messageBuilder;

        BlockedRuleDescriptor(String id, ReportPredicate predicate, MessageBuilder messageBuilder) {
            this.id = id;
            this.predicate = predicate;
            this.messageBuilder = messageBuilder;
        }

        void apply(ExportValidationReport report) {
            if (predicate.matches(report)) {
                addBlockedIssue(report, messageBuilder.build(report));
            }
        }
    }

    private static final class ActionableIssueDescriptor {
        final String id;
        final String code;
        final String message;
        final JsonArraySelector detailsSelector;

        ActionableIssueDescriptor(
                String id,
                String code,
                String message,
                JsonArraySelector detailsSelector) {
            this.id = id;
            this.code = code;
            this.message = message;
            this.detailsSelector = detailsSelector;
        }

        void apply(ExportValidationReport report) {
            addActionableIssue(report, code, message, detailsSelector.select(report));
        }
    }

    private static final class StatusRuleDescriptor {
        final String id;
        final ReportPredicate predicate;
        final String healthStatus;
        final String compileReadinessStatus;

        StatusRuleDescriptor(
                String id,
                ReportPredicate predicate,
                String healthStatus,
                String compileReadinessStatus) {
            this.id = id;
            this.predicate = predicate;
            this.healthStatus = healthStatus;
            this.compileReadinessStatus = compileReadinessStatus;
        }
    }
}
