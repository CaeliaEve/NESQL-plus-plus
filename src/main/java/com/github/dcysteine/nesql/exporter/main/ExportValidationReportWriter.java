package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;
import com.github.dcysteine.nesql.elysium.kernel.ExportSchemaCatalog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.EnumChatFormatting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** Writes a lightweight post-export integrity summary without changing exported data contracts. */
final class ExportValidationReportWriter {
    private ExportValidationReportWriter() {}

    static void write(ExportContext exportContext) {
        try {
            File repositoryDirectory = exportContext.paths.repositoryDirectory;
            File rawDir = new File(repositoryDirectory, "raw-export");
            File validationDir = new File(rawDir, "validation");

            ValidationReport report = new ValidationReport();
            report.schemaVersion = ExportSchemaCatalog.EXPORT_VALIDATION;
            report.profile = exportContext.profile.profileId;
            report.selection = exportContext.selection.describe();
            report.repository = exportContext.paths.repositoryName;
            report.itemsJsonGzFiles = countFiles(new File(repositoryDirectory, "items"), ".json.gz");
            report.recipeJsonGzFiles = countFiles(new File(repositoryDirectory, "recipes"), ".json.gz");
            report.imagePngFiles = countFiles(exportContext.paths.imageDirectory, ".png");
            report.imageGifFiles = countFiles(exportContext.paths.imageDirectory, ".gif");
            report.renderJsonFiles = countFiles(exportContext.paths.imageDirectory, ".render.json");
            report.spriteJsonFiles = countFiles(exportContext.paths.imageDirectory, ".sprite.json");
            inspectRawExportCounts(repositoryDirectory, report);
            report.staticAtlasPngFiles = countFiles(new File(rawDir, "assets/textures/atlas-assets"), ".png");
            report.animatedAtlasPngFiles = report.staticAtlasPngFiles;
            report.staticAtlasManifestAssets = safeInt(report.rawTextures);
            report.animatedAtlasManifestAssets = safeInt(report.rawAnimations);
            report.totalAtlasManifestAssets = report.staticAtlasManifestAssets + report.animatedAtlasManifestAssets;
            report.browserLayoutPresent = new File(rawDir, "facts/nei/groups.jsonl.gz").exists()
                    && new File(rawDir, "facts/nei/order.jsonl.gz").exists();
            report.browserLayoutEntries = safeInt(report.rawBrowserItems);
            report.browserLayoutItemCount = safeInt(report.rawBrowserItems);
            report.browserLayoutGroupCount = safeInt(report.rawBrowserGroups);
            report.browserLayoutDefaultEntryCount = safeInt(countGzipJsonl(new File(rawDir, "facts/nei/order.jsonl.gz")));
            inspectBrowserAtlasCoverage(rawDir, report);
            report.multiblockBlueprints = safeInt(countGzipJsonl(new File(rawDir, "models/multiblocks/index.jsonl.gz")));
            report.entityPreviewEntries = safeInt(countGzipJsonl(new File(rawDir, "models/entities/index.jsonl.gz")));
            report.entityModelEntries = report.entityPreviewEntries;
        inspectRenderAssets(repositoryDirectory, rawDir, report);
        inspectSemanticDiagnostics(repositoryDirectory, report);
        inspectSemanticRulePack(report);
        inspectExportPathHygiene(repositoryDirectory, report);
            report.atlasManifestCoverageRatio = ratio(report.totalAtlasManifestAssets, report.renderAssetManifestAssets);
            ExportValidationReportStore.applyPreviousDelta(validationDir, report);
            ExportValidationHealthPolicy.evaluate(report);
            populateHealthSections(repositoryDirectory, report);

            ExportValidationReportStore.ReportFiles reportFiles =
                    ExportValidationReportStore.write(validationDir, report);

            Logger.chatMessage(
                    EnumChatFormatting.GREEN
                            + "[NESQL] Export validation report written: "
                            + reportFiles.reportFile.getAbsolutePath());
            if (!report.warnings.isEmpty()) {
                Logger.chatMessage(
                        EnumChatFormatting.YELLOW
                                + "[NESQL] Validation warnings: "
                                + report.warnings.size()
                                + " (see report)");
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write NESQL++ validation report", e);
        }
    }

    private static void inspectRawExportCounts(File repositoryDirectory, ValidationReport report) {
        File reportFile = new File(repositoryDirectory, "raw-export" + File.separator + "export_report.json");
        JsonObject root = readJsonObject(reportFile);
        if (root == null || !root.has("counts") || !root.get("counts").isJsonObject()) {
            return;
        }
        JsonObject counts = root.getAsJsonObject("counts");
        report.rawItems = readLongMember(counts, "rawItems");
        report.rawRecipes = readLongMember(counts, "rawRecipes");
        report.rawTextures = readLongMember(counts, "rawTextures");
        report.rawAnimations = readLongMember(counts, "rawAnimations");
        report.rawBrowserItems = readLongMember(counts, "neiBrowserItems");
        report.rawBrowserGroups = readLongMember(counts, "rawGroups");
        report.rawNeiOrderEntries = readLongMember(counts, "rawNeiOrderEntries");
        report.rawNeiRuntimePanelItems = readLongMember(counts, "neiRuntimePanelItems");
        report.rawNeiExportOnlyItems = readLongMember(counts, "neiExportOnlyItems");
        report.rawNeiDefaultEntries = readLongMember(counts, "neiDefaultEntries");
        report.rawNeiFallbackGroups = readLongMember(counts, "neiFallbackGroups");
        report.rawNativeNeiGroups = readLongMember(counts, "neiNativeGroups");
        report.rawNeiSyntheticGroups = readLongMember(counts, "neiSyntheticGroups");
        report.rawGuidFilterRules = readLongMember(counts, "neiGuidFilterRules");
        report.rawHiddenItemRules = readLongMember(counts, "neiHiddenItemRules");
        report.rawHiddenItems = readLongMember(counts, "neiHiddenItems");
        report.rawNeiRepresentativeMismatches = readLongMember(counts, "neiRepresentativeMismatches");
        report.rawNeiHandlers = readLongMember(counts, "neiHandlers");
        report.rawNeiHandlerLayouts = readLongMember(counts, "neiHandlerLayouts");
        report.nativeUiLayouts = readLongMember(counts, "nativeUiLayouts");
        report.nativeUiSlots = readLongMember(counts, "nativeUiSlots");
        report.nativeUiMissingSurfaces = readLongMember(counts, "nativeUiMissingSurfaces");
        report.nativeUiSlotBoundsViolations = readLongMember(counts, "nativeUiSlotBoundsViolations");
        report.nativeUiBackgroundBoundsViolations = readLongMember(counts, "nativeUiBackgroundBoundsViolations");
        report.nativeUiCoordinateContractViolations = readLongMember(counts, "nativeUiCoordinateContractViolations");
        report.rawRecipeTypes = readLongMember(counts, "recipeTypes");
        report.renderBackendFacts = readLongMember(counts, "renderBackendFacts");
        report.renderBackendAngelica = readLongMember(counts, "renderBackendAngelica");
        report.renderTextureSprites = readLongMember(counts, "renderTextureSprites");
        report.renderTextureSpritesMissingTiming = readLongMember(counts, "renderTextureSpritesMissingTiming");
        report.renderItemRenderers = readLongMember(counts, "renderItemRenderers");
        report.renderShaderItems = readLongMember(counts, "renderShaderItems");
        report.renderShaderItemsRequiringCapture = readLongMember(counts, "renderShaderItemsRequiringCapture");
        report.renderShaderItemsMissingCapture = readLongMember(counts, "renderShaderItemsMissingCapture");
        report.renderUnknownSpecialRenderers = readLongMember(counts, "renderUnknownSpecialRenderers");
        report.renderFramebufferCaptures = readLongMember(counts, "renderFramebufferCaptures");
        report.renderFramebufferCapturesWithoutFrames = readLongMember(counts, "renderFramebufferCapturesWithoutFrames");
        report.semanticTotalItems = readLongMember(counts, "semanticTotalItems");
        report.semanticTaggedItems = readLongMember(counts, "semanticTaggedItems");
        report.semanticClassifiedTaggedItems = readLongMember(counts, "semanticClassifiedTaggedItems");
        report.semanticUnclassifiedTaggedItems = readLongMember(counts, "semanticUnclassifiedTaggedItems");
        report.semanticFamilyCount = readLongMember(counts, "semanticFamilyCount");
        report.semanticItems = readLongMember(counts, "semanticItems");
        report.semanticVariants = readLongMember(counts, "semanticVariants");
        report.semanticPayloads = readLongMember(counts, "semanticPayloads");
        report.semanticIdentityMapRows = readLongMember(counts, "semanticIdentityMapRows");
        report.semanticClassificationCoverageRatio = ratio(report.semanticClassifiedTaggedItems, report.semanticTaggedItems);
    }

    private static void populateHealthSections(File repositoryDirectory, ValidationReport report) {
        report.itemTotals = new ItemTotals();
        report.itemTotals.rawItems = report.rawItems;
        report.itemTotals.browserItems = report.rawBrowserItems;
        report.itemTotals.hiddenItems = report.rawHiddenItems;
        report.itemTotals.recipeOnlyItems = Math.max(0L, report.rawItems - report.rawBrowserItems);
        report.itemTotals.neiRuntimePanelItems = report.rawNeiRuntimePanelItems;
        report.itemTotals.neiExportOnlyItems = report.rawNeiExportOnlyItems;

        report.browserGroupTotals = new BrowserGroupTotals();
        report.browserGroupTotals.groups = report.rawBrowserGroups;
        report.browserGroupTotals.nativeNeiGroups = report.rawNativeNeiGroups;
        report.browserGroupTotals.guidFilterRules = report.rawGuidFilterRules;
        report.browserGroupTotals.hiddenItemRules = report.rawHiddenItemRules;
        report.browserGroupTotals.semanticGroups = Math.max(0L,
                report.rawBrowserGroups - report.rawNativeNeiGroups - report.rawNeiFallbackGroups - report.rawNeiSyntheticGroups);
        report.browserGroupTotals.fallbackGroups = report.rawNeiFallbackGroups;
        report.browserGroupTotals.syntheticGroups = report.rawNeiSyntheticGroups;
        report.browserGroupTotals.defaultEntries = report.rawNeiDefaultEntries;
        report.browserGroupTotals.orderEntries = report.rawNeiOrderEntries;
        report.browserGroupTotals.representativeMismatches = report.rawNeiRepresentativeMismatches;

        report.textureTotals = new TextureTotals();
        report.textureTotals.textures = report.rawTextures;
        report.textureTotals.staticAtlasFiles = report.staticAtlasPngFiles;
        report.textureTotals.atlasManifestAssets = report.staticAtlasManifestAssets;
        report.textureTotals.missingTextures = report.renderAssetMissingPrimaryArtifacts;
        report.textureTotals.missingAtlasEntries = report.browserAtlasLayoutMissingItems;
        report.textureTotals.wrongRepresentativeTextureRisks = report.rawNeiRepresentativeMismatches;
        report.textureTotals.browserAtlasItems = report.browserAtlasItems;
        report.textureTotals.browserAtlasDrawableItems = report.browserAtlasDrawableItems;

        report.animationTotals = new AnimationTotals();
        report.animationTotals.animations = report.rawAnimations;
        report.animationTotals.animatedAtlasFiles = report.animatedAtlasPngFiles;
        report.animationTotals.animatedAtlasManifestAssets = report.animatedAtlasManifestAssets;
        report.animationTotals.missingAnimatedAtlasEntries = Math.max(0,
                report.animatedAtlasManifestAssets - report.browserAtlasAnimatedItems);
        report.animationTotals.missingTimingData = readLongMember(
                readCountsObject(new File(repositoryDirectory, "raw-export" + File.separator + "export_report.json")),
                "renderTextureSpritesMissingTiming");
        report.animationTotals.staticWhenAnimationExpected = report.suspiciousStaticSingularityAssets;
        report.animationTotals.singularityLikeAssets = report.singularityLikeRenderAssets;
        report.animationTotals.animatedSingularityLikeAssets = report.animatedSingularityLikeRenderAssets;

        report.nativeRenderTotals = new NativeRenderTotals();
        report.nativeRenderTotals.backendFacts = report.renderBackendFacts;
        report.nativeRenderTotals.backendAngelica = report.renderBackendAngelica;
        report.nativeRenderTotals.textureSprites = report.renderTextureSprites;
        report.nativeRenderTotals.textureSpritesMissingTiming = report.renderTextureSpritesMissingTiming;
        report.nativeRenderTotals.itemRenderers = report.renderItemRenderers;
        report.nativeRenderTotals.shaderItems = report.renderShaderItems;
        report.nativeRenderTotals.shaderItemsRequiringCapture = report.renderShaderItemsRequiringCapture;
        report.nativeRenderTotals.shaderItemsMissingCapture = report.renderShaderItemsMissingCapture;
        report.nativeRenderTotals.unknownSpecialRenderers = report.renderUnknownSpecialRenderers;
        report.nativeRenderTotals.framebufferCaptures = report.renderFramebufferCaptures;
        report.nativeRenderTotals.framebufferCapturesWithoutFrames = report.renderFramebufferCapturesWithoutFrames;
        report.nativeRenderTotals.captureCompletenessRatio = ratio(
                report.renderShaderItemsRequiringCapture - report.renderShaderItemsMissingCapture,
                report.renderShaderItemsRequiringCapture);
        report.nativeRenderTotals.status = report.renderBackendAngelica == 1L
                && report.renderTextureSpritesMissingTiming == 0L
                && report.renderUnknownSpecialRenderers == 0L
                && report.renderShaderItemsMissingCapture == 0L
                && report.renderFramebufferCapturesWithoutFrames == 0L
                ? "ok"
                : "warning";

        report.recipeTotals = new RecipeTotals();
        report.recipeTotals.recipes = report.rawRecipes;
        report.recipeTotals.recipeTypes = report.rawRecipeTypes;
        report.recipeTotals.neiHandlers = report.rawNeiHandlers;
        report.recipeTotals.neiHandlerLayouts = report.rawNeiHandlerLayouts;
        inspectRecipeHandlerAnomalies(repositoryDirectory, report.recipeTotals);

        report.nativeUiTotals = new NativeUiTotals();
        report.nativeUiTotals.layouts = report.nativeUiLayouts;
        report.nativeUiTotals.slots = report.nativeUiSlots;
        report.nativeUiTotals.missingSurfaces = report.nativeUiMissingSurfaces;
        report.nativeUiTotals.slotBoundsViolations = report.nativeUiSlotBoundsViolations;
        report.nativeUiTotals.backgroundBoundsViolations = report.nativeUiBackgroundBoundsViolations;
        report.nativeUiTotals.coordinateContractViolations = report.nativeUiCoordinateContractViolations;
        report.nativeUiTotals.status = report.nativeUiLayouts > 0L
                && report.nativeUiSlots > 0L
                && report.nativeUiMissingSurfaces == 0L
                && report.nativeUiSlotBoundsViolations == 0L
                && report.nativeUiBackgroundBoundsViolations == 0L
                && report.nativeUiCoordinateContractViolations == 0L
                ? "ok"
                : "blocked";

        report.runtimeManifestMetadata = new RuntimeManifestMetadata();
        report.runtimeManifestMetadata.gtnhProfile = report.profile;
        report.runtimeManifestMetadata.exportRepository = report.repository;
        report.runtimeManifestMetadata.exportSelection = report.selection;
        report.runtimeManifestMetadata.exporterSchemaVersion = report.schemaVersion;
        report.runtimeManifestMetadata.exportTimestamp = readStringMember(
                readJsonObject(new File(repositoryDirectory, "raw-export" + File.separator + "manifest.json")),
                "generatedAt");
        report.runtimeManifestMetadata.healthStatus = report.healthStatus;
        report.runtimeManifestMetadata.compileReadinessStatus = report.compileReadinessStatus;
        report.runtimeManifestMetadata.assetHash = readExportAssetHash(repositoryDirectory);
    }

    private static JsonObject readCountsObject(File reportFile) {
        JsonObject root = readJsonObject(reportFile);
        if (root == null || !root.has("counts") || !root.get("counts").isJsonObject()) {
            return null;
        }
        return root.getAsJsonObject("counts");
    }

    private static void inspectRecipeHandlerAnomalies(File repositoryDirectory, RecipeTotals totals) {
        JsonObject root = readJsonObject(new File(repositoryDirectory,
                "raw-export" + File.separator + "validation" + File.separator + "nei_handler_anomalies.json"));
        if (root == null || !root.has("summary") || !root.get("summary").isJsonObject()) {
            return;
        }
        JsonObject summary = root.getAsJsonObject("summary");
        totals.handlersWithLoadedRecipes = readLongMember(summary, "handlersWithLoadedRecipes");
        totals.handlersWithExportedRecipes = readLongMember(summary, "handlersWithExportedRecipes");
        totals.suspiciousZeroRecipeHandlers = readLongMember(summary, "suspiciousZeroExports");
        totals.nativeCoveredZeroRecipeHandlers = readLongMember(summary, "nativeCoveredZeroExports");
        totals.nonRecipeInfoZeroRecipeHandlers = readLongMember(summary, "nonRecipeInfoZeroExports");
        totals.expectedEmptyHandlers = readLongMember(summary, "expectedEmptyHandlers");
        totals.legalZeroRecipeHandlers = readLongMember(summary, "legalZeroRecipeHandlers");
        totals.partialExports = readLongMember(summary, "partialExports");
        totals.duplicateCategoryRisks = readLongMember(summary, "duplicateCategoryRisks");
        totals.zeroRecipeStatus = readStringMember(summary, "status");
    }

    private static String readExportAssetHash(File repositoryDirectory) {
        JsonObject root = readJsonObject(new File(repositoryDirectory,
                "raw-export" + File.separator + "validation" + File.separator + "stage_checksums.json"));
        if (root == null) {
            return null;
        }
        String direct = readStringMember(root, "assetHash");
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        String checksum = readStringMember(root, "checksum");
        if (checksum != null && !checksum.isEmpty()) {
            return checksum;
        }
        return readStringMember(root, "sha256");
    }

    private static void inspectSemanticDiagnostics(File repositoryDirectory, ValidationReport report) {
        File semanticReportFile = new File(repositoryDirectory,
                "raw-export" + File.separator + "validation" + File.separator + "semantic"
                        + File.separator + "identity-normalization-report.json");
        JsonObject root = readJsonObject(semanticReportFile);
        if (root == null) {
            return;
        }
        report.semanticDiagnosticsPresent = true;
        report.semanticTopUnclassifiedFamilyActions = copyArray(root, "topUnclassifiedFamilyActions", 20);
        report.semanticMissingFacetFamilies = copyArray(root, "missingFacetFamilies", 20);
        report.semanticMissingSortKeyFamilies = copyArray(root, "missingSortKeyFamilies", 20);
        report.semanticMissingFacetFamilyCount = arraySize(root, "missingFacetFamilies");
        report.semanticMissingSortKeyFamilyCount = arraySize(root, "missingSortKeyFamilies");
        if (report.semanticTotalItems == 0L) {
            report.semanticTotalItems = readLongMember(root, "beforePublicItems");
        }
        if (report.semanticTaggedItems == 0L) {
            report.semanticTaggedItems = readLongMember(root, "taggedItems");
        }
        if (report.semanticClassifiedTaggedItems == 0L) {
            report.semanticClassifiedTaggedItems = readLongMember(root, "classifiedTaggedItems");
        }
        if (report.semanticUnclassifiedTaggedItems == 0L) {
            report.semanticUnclassifiedTaggedItems = readLongMember(root, "unclassifiedTaggedItems");
        }
        report.semanticClassificationCoverageRatio = ratio(report.semanticClassifiedTaggedItems, report.semanticTaggedItems);
    }

    private static void inspectSemanticRulePack(ValidationReport report) {
        report.semanticRulePack = SemanticRulePack.loadBundled().validateAgainstRegistry();
    }

    private static final PathHygieneRule[] PATH_HYGIENE_RULES = new PathHygieneRule[] {
            new PathHygieneRule("windows-backslash-absolute", Pattern.compile("(^|[\\s\\\"'`\\(\\[\\{:=,])[A-Za-z]:\\\\[A-Za-z0-9._ -]")),
            new PathHygieneRule("windows-slash-absolute", Pattern.compile("(^|[\\s\\\"'`\\(\\[\\{:=,])[A-Za-z]:/[A-Za-z0-9._ -]")),
            new PathHygieneRule("minecraft-version-path", Pattern.compile("\\.minecraft[\\\\/]versions", Pattern.CASE_INSENSITIVE)),
            new PathHygieneRule("local-gtnh-path", Pattern.compile("[A-Za-z]:[\\\\/]GTNH", Pattern.CASE_INSENSITIVE)),
            new PathHygieneRule("local-codex-path", Pattern.compile("[A-Za-z]:[\\\\/]codex", Pattern.CASE_INSENSITIVE)),
            new PathHygieneRule("linux-home-absolute", Pattern.compile("(^|[\\s\\\"'`\\(\\[\\{:=,])/(?:home|Users|mnt|opt|srv)/"))
    };

    private static void inspectExportPathHygiene(File repositoryDirectory, ValidationReport report) {
        List<File> files = new ArrayList<File>();
        collectRuntimeJsonFiles(new File(repositoryDirectory, "manifest.json"), files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "raw-export"), files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "facts"), files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "assets"), files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "special"), files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "models"), files);
        collectRuntimeJsonFiles(new File(repositoryDirectory, "raw-export" + File.separator + "manifest.json"), files);
        report.exportPathHygieneAuditedFiles = files.size();
        for (File file : files) {
            inspectExportPathHygieneFile(repositoryDirectory, file, report);
        }
        report.exportPathHygieneStatus = report.exportPathHygieneViolations == 0 ? "ok" : "failed";
        if (report.exportPathHygieneViolations > 0) {
            writePathHygieneErrors(repositoryDirectory, report);
        }
    }

    private static void collectRuntimeJsonFiles(File file, List<File> files) {
        if (file == null || !file.exists() || isDiagnosticPath(file)) {
            return;
        }
        if (file.isFile()) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".json") || name.endsWith(".jsonl")) {
                files.add(file);
            }
            return;
        }
        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            collectRuntimeJsonFiles(child, files);
        }
    }

    private static boolean isDiagnosticPath(File file) {
        String normalized = file.getPath().replace(File.separatorChar, '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/validation/")
                || normalized.contains("/diagnostic")
                || normalized.contains("/logs/")
                || normalized.endsWith("report.json")
                || normalized.endsWith("report.jsonl")
                || normalized.endsWith("log.json")
                || normalized.endsWith("log.jsonl");
    }

    private static void inspectExportPathHygieneFile(
            File repositoryDirectory,
            File file,
            ValidationReport report) {
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader input = new InputStreamReader(fis, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(input)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                for (PathHygieneRule rule : PATH_HYGIENE_RULES) {
                    if (rule.pattern.matcher(line).find()) {
                        report.exportPathHygieneViolations++;
                        addPathHygieneSample(
                                report,
                                relativize(repositoryDirectory, file),
                                lineNumber,
                                rule.name,
                                line);
                    }
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect export path hygiene for {}", file.getAbsolutePath(), e);
        }
    }

    private static void addPathHygieneSample(
            ValidationReport report,
            String file,
            int line,
            String rule,
            String text) {
        if (report.exportPathHygieneSamples.size() >= 100) {
            return;
        }
        PathHygieneSample sample = new PathHygieneSample();
        sample.file = file;
        sample.line = line;
        sample.rule = rule;
        sample.text = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (sample.text.length() > 240) {
            sample.text = sample.text.substring(0, 240);
        }
        report.exportPathHygieneSamples.add(sample);
    }

    private static void writePathHygieneErrors(File repositoryDirectory, ValidationReport report) {
        File validationDirectory = new File(repositoryDirectory, "raw-export" + File.separator + "validation");
        if (!validationDirectory.exists() && !validationDirectory.mkdirs()) {
            Logger.MOD.warn("Failed to create NESQL validation directory: {}", validationDirectory.getAbsolutePath());
            return;
        }
        File errorsFile = new File(validationDirectory, "errors.jsonl");
        try (FileOutputStream fos = new FileOutputStream(errorsFile, true);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();
            for (PathHygieneSample sample : report.exportPathHygieneSamples) {
                JsonObject entry = new JsonObject();
                entry.addProperty("schemaVersion", "nesqlpp/export-error/v1");
                entry.addProperty("generatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new Date()));
                entry.addProperty("stage", "VALIDATION");
                entry.addProperty("code", "export-path-hygiene");
                entry.addProperty("message", "Runtime export payload contains a machine-specific path");
                entry.addProperty("file", sample.file);
                entry.addProperty("line", sample.line);
                entry.addProperty("rule", sample.rule);
                writer.write(gson.toJson(entry));
                writer.write('\n');
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to write export path hygiene diagnostics", e);
        }
    }

    private static String relativize(File root, File file) {
        try {
            return root.toPath().toAbsolutePath().normalize()
                    .relativize(file.toPath().toAbsolutePath().normalize())
                    .toString()
                    .replace(File.separatorChar, '/');
        } catch (Exception ignored) {
            return file.getName();
        }
    }
    private static void inspectBrowserAtlasCoverage(File rawDir, ValidationReport report) {
        File browserAtlasFile = new File(rawDir, "assets/textures/browser_atlas_index.json");
        File browserLayoutFile = null;
        report.browserAtlasPresent = browserAtlasFile.exists();
        if (!browserAtlasFile.exists()) {
            return;
        }

        try (FileInputStream atlasFis = new FileInputStream(browserAtlasFile);
             InputStreamReader atlasReader = new InputStreamReader(atlasFis, StandardCharsets.UTF_8)) {
            JsonObject atlasObject = new JsonParser().parse(atlasReader).getAsJsonObject();
            report.browserAtlasItems = readIntMember(atlasObject, "itemCount");
            report.browserAtlasAnimatedItems = readIntMember(atlasObject, "animatedItemCount");
            report.browserAtlasMissingAtlasCount = readIntMember(atlasObject, "missingAtlasCount");

            Set<String> drawableAtlasItemIds = new HashSet<String>();
            if (atlasObject.has("items") && atlasObject.get("items").isJsonArray()) {
                if (report.browserAtlasItems == 0) {
                    report.browserAtlasItems = atlasObject.get("items").getAsJsonArray().size();
                }
                for (JsonElement element : atlasObject.get("items").getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject item = element.getAsJsonObject();
                    String itemId = readStringMember(item, "itemId");
                    if (itemId != null && hasDrawableAtlas(item)) {
                        drawableAtlasItemIds.add(itemId);
                    }
                }
            }
            report.browserAtlasDrawableItems = drawableAtlasItemIds.size();

            report.browserAtlasLayoutItemCount = report.browserAtlasItems;
            report.browserAtlasLayoutCoveredItems = report.browserAtlasDrawableItems;
            report.browserAtlasLayoutMissingItems = Math.max(0, report.browserAtlasItems - report.browserAtlasDrawableItems);
            report.browserAtlasLayoutCoverageRatio = ratio(report.browserAtlasLayoutCoveredItems, report.browserAtlasLayoutItemCount);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect browser atlas coverage", e);
        }
    }

    private static void inspectBrowserLayoutCoverage(
            File browserLayoutFile,
            Set<String> drawableAtlasItemIds,
            ValidationReport report) {
        try (FileInputStream layoutFis = new FileInputStream(browserLayoutFile);
             InputStreamReader layoutReader = new InputStreamReader(layoutFis, StandardCharsets.UTF_8)) {
            JsonObject layoutObject = new JsonParser().parse(layoutReader).getAsJsonObject();
            Set<String> layoutItemIds = new HashSet<String>();
            collectLayoutItemIds(layoutObject, "items", false, layoutItemIds);
            collectLayoutItemIds(layoutObject, "defaultEntries", true, layoutItemIds);

            report.browserAtlasLayoutItemCount = layoutItemIds.size();
            for (String itemId : layoutItemIds) {
                if (hasDrawableAtlasForItem(drawableAtlasItemIds, itemId)) {
                    report.browserAtlasLayoutCoveredItems++;
                } else {
                    report.browserAtlasLayoutMissingItems++;
                    addSample(report.browserAtlasLayoutMissingSamples, itemId);
                }
            }
            report.browserAtlasLayoutCoverageRatio =
                    ratio(report.browserAtlasLayoutCoveredItems, report.browserAtlasLayoutItemCount);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect browser layout atlas coverage", e);
        }
    }

    private static void collectLayoutItemIds(
            JsonObject layoutObject,
            String memberName,
            boolean preferRepresentative,
            Set<String> layoutItemIds) {
        if (!layoutObject.has(memberName) || !layoutObject.get(memberName).isJsonArray()) {
            return;
        }
        for (JsonElement element : layoutObject.get(memberName).getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String itemId = preferRepresentative
                    ? firstNonEmpty(readStringMember(item, "representativeItemId"), readStringMember(item, "itemId"), null)
                    : readStringMember(item, "itemId");
            if (itemId != null && !itemId.isEmpty()) {
                layoutItemIds.add(itemId);
            }
        }
    }

    private static boolean hasDrawableAtlas(JsonObject item) {
        return hasAtlasFile(item, "staticAtlas") || hasAtlasFile(item, "animatedAtlas");
    }

    private static boolean hasAtlasFile(JsonObject item, String memberName) {
        if (!item.has(memberName) || !item.get(memberName).isJsonObject()) {
            return false;
        }
        String atlasFile = readStringMember(item.get(memberName).getAsJsonObject(), "atlasFile");
        return atlasFile != null && !atlasFile.isEmpty();
    }

    private static boolean hasDrawableAtlasForItem(Set<String> drawableAtlasItemIds, String itemId) {
        if (drawableAtlasItemIds.contains(itemId)) {
            return true;
        }
        for (String alias : getItemIdAliases(itemId)) {
            if (drawableAtlasItemIds.contains(alias)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> getItemIdAliases(String itemId) {
        List<String> aliases = new ArrayList<String>();
        if (itemId == null) {
            return aliases;
        }
        String normalized = itemId.trim();
        if (normalized.isEmpty()) {
            return aliases;
        }
        String[] parts = normalized.split("~");
        if (parts.length >= 4 && "i".equals(parts[0])) {
            String compact = parts[0] + "~" + parts[1] + "~" + parts[2] + "~" + parts[3];
            String metaZero = parts[0] + "~" + parts[1] + "~" + parts[2] + "~0";
            if (!compact.equals(normalized)) {
                aliases.add(compact);
            }
            if (!metaZero.equals(normalized) && !aliases.contains(metaZero)) {
                aliases.add(metaZero);
            }
        }
        return aliases;
    }

    private static void inspectRenderAssets(File repositoryDirectory, File rawDir, ValidationReport report) {
        File manifestFile = new File(rawDir, "assets/textures/index.jsonl.gz");
        report.renderAssetManifestPresent = manifestFile.exists();
        if (!manifestFile.exists()) {
            return;
        }

        try (BufferedReader reader = openMaybeGzipUtf8(manifestFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                JsonElement parsed = new JsonParser().parse(line);
                if (parsed != null && parsed.isJsonObject()) {
                    report.renderAssetManifestAssets++;
                    inspectRenderAssetRow(repositoryDirectory, parsed.getAsJsonObject(), report);
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect raw-export render asset stream for validation", e);
        }
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private static long countGzipJsonl(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return 0L;
        }
        long count = 0L;
        try (BufferedReader reader = openMaybeGzipUtf8(file)) {
            while (reader.readLine() != null) {
                count++;
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to count JSONL stream {}", file.getAbsolutePath(), e);
        }
        return count;
    }

    private static BufferedReader openMaybeGzipUtf8(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        if (file.getName().endsWith(".gz")) {
            return new BufferedReader(new InputStreamReader(new GZIPInputStream(fis), StandardCharsets.UTF_8));
        }
        return new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
    }

    private static void inspectRenderAssetRow(
            File repositoryDirectory,
            JsonObject asset,
            ValidationReport report) {
        if (asset == null) {
            return;
        }
        String primaryPath = firstNonEmpty(
                readStringMember(asset, "primaryArtifact"),
                readStringMember(asset, "staticFile"),
                readStringMember(asset, "nativeSpriteAtlasFile"));
        if (primaryPath == null || !exportFileExists(repositoryDirectory, primaryPath)) {
            report.renderAssetMissingPrimaryArtifacts++;
            addSample(report.renderAssetMissingPrimaryArtifactSamples,
                    firstNonEmpty(readStringMember(asset, "assetId"), readStringMember(asset, "sourcePath"), primaryPath));
        }
        inspectSingularityAnimation(asset, report);
        JsonElement timeline = asset.get("timeline");
        if (timeline == null || !timeline.isJsonArray()) {
            return;
        }
        for (JsonElement frameElement : timeline.getAsJsonArray()) {
            if (frameElement == null || !frameElement.isJsonObject()) {
                continue;
            }
            String path = readStringMember(frameElement.getAsJsonObject(), "path");
            if (path != null && !exportFileExists(repositoryDirectory, path)) {
                report.renderAssetMissingTimelineFrames++;
                addSample(report.renderAssetMissingTimelineFrameSamples,
                        firstNonEmpty(readStringMember(asset, "assetId"), readStringMember(asset, "sourcePath"), path));
            }
        }
    }

    private static void inspectSingularityAnimation(JsonObject asset, ValidationReport report) {
        String haystack = joinLower(
                readStringMember(asset, "assetId"),
                readStringMember(asset, "variantKey"),
                readStringMember(asset, "family"),
                readStringMember(asset, "sourceType"),
                readStringMember(asset, "sourcePath"),
                readStringMember(asset, "primaryArtifact"),
                readStringMember(asset, "staticFile"),
                readStringMember(asset, "rendererFamily"),
                readStringMember(asset, "captureMethod"),
                readStringMember(asset, "captureSource"),
                readStringMember(asset, "animationMode"),
                readStringMember(asset, "renderMode"));
        if (!isSingularityLike(haystack)) {
            return;
        }
        report.singularityLikeRenderAssets++;
        boolean animated = contains(haystack, ".gif")
                || contains(haystack, "animated")
                || contains(haystack, "timeline")
                || arraySize(asset, "timeline") > 1
                || arraySize(asset, "frames") > 1
                || readIntMember(asset, "frameCount") > 1
                || readIntMember(asset, "capturedFrameCount") > 1;
        if (animated) {
            report.animatedSingularityLikeRenderAssets++;
            return;
        }
        report.suspiciousStaticSingularityAssets++;
        addSample(report.suspiciousStaticSingularitySamples,
                firstNonEmpty(readStringMember(asset, "assetId"), readStringMember(asset, "sourcePath"), readStringMember(asset, "primaryArtifact")));
    }

    private static void inspectRenderAsset(
            File repositoryDirectory,
            CanonicalRenderAsset asset,
            ValidationReport report) {
        if (asset == null) {
            return;
        }
        String primaryPath = firstNonEmpty(asset.primaryArtifact, asset.staticFile, asset.nativeSpriteAtlasFile);
        if (primaryPath == null || !exportFileExists(repositoryDirectory, primaryPath)) {
            report.renderAssetMissingPrimaryArtifacts++;
            addSample(report.renderAssetMissingPrimaryArtifactSamples, firstNonEmpty(asset.assetId, asset.sourcePath, primaryPath));
        }

        inspectSingularityAnimation(asset, report);

        if (asset.timeline == null || asset.timeline.isEmpty()) {
            return;
        }
        for (Map<String, Object> frame : asset.timeline) {
            if (frame == null) {
                continue;
            }
            Object path = frame.get("path");
            if (path instanceof String && !exportFileExists(repositoryDirectory, (String) path)) {
                report.renderAssetMissingTimelineFrames++;
                addSample(report.renderAssetMissingTimelineFrameSamples, firstNonEmpty(asset.assetId, asset.sourcePath, (String) path));
            }
        }
    }

    private static void inspectSingularityAnimation(CanonicalRenderAsset asset, ValidationReport report) {
        String haystack = joinLower(
                asset.assetId,
                asset.variantKey,
                asset.family,
                asset.sourceType,
                asset.sourcePath,
                asset.primaryArtifact,
                asset.staticFile,
                asset.rendererFamily,
                asset.captureMethod,
                asset.captureSource,
                asset.animationMode,
                asset.renderMode);
        if (!isSingularityLike(haystack)) {
            return;
        }

        report.singularityLikeRenderAssets++;
        boolean animated =
                contains(haystack, ".gif")
                        || contains(haystack, "animated")
                        || contains(haystack, "timeline")
                        || (asset.timeline != null && asset.timeline.size() > 1)
                        || (asset.frames != null && asset.frames.size() > 1)
                        || (asset.frameCount != null && asset.frameCount > 1)
                        || (asset.capturedFrameCount != null && asset.capturedFrameCount > 1);
        if (animated) {
            report.animatedSingularityLikeRenderAssets++;
            return;
        }

        report.suspiciousStaticSingularityAssets++;
        if (report.suspiciousStaticSingularitySamples.size() < 100) {
            report.suspiciousStaticSingularitySamples.add(firstNonEmpty(asset.assetId, asset.sourcePath, asset.primaryArtifact));
        }
    }

    private static void addSample(List<String> samples, String value) {
        if (value != null && !value.isEmpty() && samples.size() < 100 && !samples.contains(value)) {
            samples.add(value);
        }
    }

    private static String joinLower(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                builder.append(' ').append(value);
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private static boolean isSingularityLike(String value) {
        return contains(value, "singularity")
                || contains(value, "singularitie")
                || contains(value, "eternalsingularity")
                || contains(value, "universalsingularity")
                || contains(value, "universal_singularity")
                || contains(value, "avaritia")
                || contains(value, "cosmicneutronium")
                || contains(value, "transcendentmetal")
                || contains(value, "universium");
    }

    private static boolean contains(String value, String needle) {
        return value != null && needle != null && value.contains(needle);
    }

    private static String firstNonEmpty(String first, String second, String third) {
        if (first != null && !first.isEmpty()) {
            return first;
        }
        if (second != null && !second.isEmpty()) {
            return second;
        }
        if (third != null && !third.isEmpty()) {
            return third;
        }
        return null;
    }

    private static boolean exportFileExists(File repositoryDirectory, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        File direct = new File(repositoryDirectory, relativePath.replace('/', File.separatorChar));
        if (direct.exists() && direct.length() > 0L) {
            return true;
        }
        if (!relativePath.startsWith("image/")) {
            File underImage = new File(repositoryDirectory, ("image/" + relativePath).replace('/', File.separatorChar));
            return underImage.exists() && underImage.length() > 0L;
        }
        return false;
    }

    private static JsonObject readJsonObject(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read JSON object from {}", file.getAbsolutePath(), e);
            return null;
        }
    }

    private static long readLongMember(JsonObject object, String memberName) {
        if (object != null
                && object.has(memberName)
                && object.get(memberName).isJsonPrimitive()
                && object.get(memberName).getAsJsonPrimitive().isNumber()) {
            return object.get(memberName).getAsLong();
        }
        return 0L;
    }

    private static JsonArray copyArray(JsonObject object, String memberName, int limit) {
        JsonArray out = new JsonArray();
        if (object == null || !object.has(memberName) || !object.get(memberName).isJsonArray()) {
            return out;
        }
        JsonArray source = object.get(memberName).getAsJsonArray();
        for (int i = 0; i < source.size() && i < limit; i++) {
            out.add(source.get(i));
        }
        return out;
    }

    private static int arraySize(JsonObject object, String memberName) {
        if (object != null && object.has(memberName) && object.get(memberName).isJsonArray()) {
            return object.get(memberName).getAsJsonArray().size();
        }
        return 0;
    }

    private static int readManifestCount(File file, String memberName) {
        if (!file.exists()) {
            return 0;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonObject object = new JsonParser().parse(reader).getAsJsonObject();
            if (object.has(memberName) && object.get(memberName).isJsonPrimitive()) {
                return object.get(memberName).getAsInt();
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read manifest count from {}", file.getAbsolutePath(), e);
        }
        return 0;
    }

    private static int readArrayCount(File file, String... memberNames) {
        // Presence checks for key optional contracts are tracked separately so
        // the report can distinguish "empty" from "not produced".
        if (!file.exists()) {
            return 0;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonObject object = new JsonParser().parse(reader).getAsJsonObject();
            int total = 0;
            for (String memberName : memberNames) {
                if (!object.has(memberName)) {
                    continue;
                }
                JsonElement element = object.get(memberName);
                if (element.isJsonArray()) {
                    total += element.getAsJsonArray().size();
                } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                    total += element.getAsInt();
                }
            }
            return total;
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read array count from {}", file.getAbsolutePath(), e);
        }
        return 0;
    }

    private static int readIntMember(JsonObject object, String memberName) {
        if (object != null
                && object.has(memberName)
                && object.get(memberName).isJsonPrimitive()
                && object.get(memberName).getAsJsonPrimitive().isNumber()) {
            return object.get(memberName).getAsInt();
        }
        return 0;
    }

    private static String readStringMember(JsonObject object, String memberName) {
        if (object != null
                && object.has(memberName)
                && object.get(memberName).isJsonPrimitive()) {
            String value = object.get(memberName).getAsString();
            return value == null ? null : value.trim();
        }
        return null;
    }

    private static Double ratio(long numerator, long denominator) {
        if (denominator <= 0L) {
            return null;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }

    private static Double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return Math.round((numerator / (double) denominator) * 10000.0) / 10000.0;
    }

    private static int countFiles(File root, String suffix) {
        if (root == null || !root.exists()) {
            return 0;
        }
        Counter counter = new Counter();
        countFiles(root, suffix, counter);
        return counter.value;
    }

    private static void countFiles(File file, String suffix, Counter counter) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile()) {
            if (file.getName().endsWith(suffix)) {
                counter.value++;
            }
            return;
        }

        File[] children = file.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            countFiles(child, suffix, counter);
        }
    }

    private static final class Counter {
        int value;
    }

    static final class ValidationReport {
        String schemaVersion;
        String healthStatus = "unknown";
        String compileReadinessStatus = "unknown";
        String repository;
        String profile;
        String selection;
        int itemsJsonGzFiles;
        int recipeJsonGzFiles;
        int imagePngFiles;
        int imageGifFiles;
        int renderJsonFiles;
        int spriteJsonFiles;
        int staticAtlasPngFiles;
        int animatedAtlasPngFiles;
        int staticAtlasManifestAssets;
        int animatedAtlasManifestAssets;
        int totalAtlasManifestAssets;
        Double atlasManifestCoverageRatio;
        int renderAssetManifestAssets;
        boolean renderAssetManifestPresent;
        boolean browserLayoutPresent;
        int renderAssetMissingPrimaryArtifacts;
        List<String> renderAssetMissingPrimaryArtifactSamples = new ArrayList<String>();
        int renderAssetMissingTimelineFrames;
        List<String> renderAssetMissingTimelineFrameSamples = new ArrayList<String>();
        int browserLayoutEntries;
        int browserLayoutItemCount;
        int browserLayoutGroupCount;
        int browserLayoutDefaultEntryCount;
        boolean browserAtlasPresent;
        int browserAtlasItems;
        int browserAtlasDrawableItems;
        int browserAtlasAnimatedItems;
        int browserAtlasMissingAtlasCount;
        int browserAtlasLayoutItemCount;
        int browserAtlasLayoutCoveredItems;
        int browserAtlasLayoutMissingItems;
        Double browserAtlasLayoutCoverageRatio;
        List<String> browserAtlasLayoutMissingSamples = new ArrayList<String>();
        int multiblockBlueprints;
        int entityPreviewEntries;
        int entityModelEntries;
        int singularityLikeRenderAssets;
        int animatedSingularityLikeRenderAssets;
        int suspiciousStaticSingularityAssets;
        List<String> suspiciousStaticSingularitySamples = new ArrayList<String>();
        String exportPathHygieneStatus = "unknown";
        int exportPathHygieneAuditedFiles;
        int exportPathHygieneViolations;
        List<PathHygieneSample> exportPathHygieneSamples = new ArrayList<PathHygieneSample>();
        long rawItems;
        long rawRecipes;
        long rawTextures;
        long rawAnimations;
        long rawBrowserItems;
        long rawBrowserGroups;
        long rawNeiOrderEntries;
        long rawNeiRuntimePanelItems;
        long rawNeiExportOnlyItems;
        long rawNeiDefaultEntries;
        long rawNeiFallbackGroups;
        long rawNativeNeiGroups;
        long rawNeiSyntheticGroups;
        long rawGuidFilterRules;
        long rawHiddenItemRules;
        long rawHiddenItems;
        long rawNeiRepresentativeMismatches;
        long rawNeiHandlers;
        long rawNeiHandlerLayouts;
        long nativeUiLayouts;
        long nativeUiSlots;
        long nativeUiMissingSurfaces;
        long nativeUiSlotBoundsViolations;
        long nativeUiBackgroundBoundsViolations;
        long nativeUiCoordinateContractViolations;
        long rawRecipeTypes;
        long renderBackendFacts;
        long renderBackendAngelica;
        long renderTextureSprites;
        long renderTextureSpritesMissingTiming;
        long renderItemRenderers;
        long renderShaderItems;
        long renderShaderItemsRequiringCapture;
        long renderShaderItemsMissingCapture;
        long renderUnknownSpecialRenderers;
        long renderFramebufferCaptures;
        long renderFramebufferCapturesWithoutFrames;
        boolean semanticDiagnosticsPresent;
        long semanticTotalItems;
        long semanticTaggedItems;
        long semanticClassifiedTaggedItems;
        long semanticUnclassifiedTaggedItems;
        long semanticFamilyCount;
        long semanticItems;
        long semanticVariants;
        long semanticPayloads;
        long semanticIdentityMapRows;
        Double semanticClassificationCoverageRatio;
        int semanticMissingFacetFamilyCount;
        int semanticMissingSortKeyFamilyCount;
        JsonArray semanticTopUnclassifiedFamilyActions = new JsonArray();
        JsonArray semanticMissingFacetFamilies = new JsonArray();
        JsonArray semanticMissingSortKeyFamilies = new JsonArray();
        SemanticRulePack.Validation semanticRulePack;
        ItemTotals itemTotals;
        BrowserGroupTotals browserGroupTotals;
        TextureTotals textureTotals;
        AnimationTotals animationTotals;
        NativeRenderTotals nativeRenderTotals;
        NativeUiTotals nativeUiTotals;
        RecipeTotals recipeTotals;
        RuntimeManifestMetadata runtimeManifestMetadata;
        List<String> blockedIssues = new ArrayList<String>();
        List<JsonObject> actionableIssues = new ArrayList<JsonObject>();
        PreviousSnapshot previous;
        DeltaSnapshot delta;
        List<String> warnings = new ArrayList<String>();
    }

    private static final class ItemTotals {
        long rawItems;
        long browserItems;
        long hiddenItems;
        long recipeOnlyItems;
        long neiRuntimePanelItems;
        long neiExportOnlyItems;
    }

    private static final class BrowserGroupTotals {
        long groups;
        long nativeNeiGroups;
        long guidFilterRules;
        long hiddenItemRules;
        long semanticGroups;
        long fallbackGroups;
        long syntheticGroups;
        long defaultEntries;
        long orderEntries;
        long representativeMismatches;
    }

    private static final class TextureTotals {
        long textures;
        int staticAtlasFiles;
        int atlasManifestAssets;
        int missingTextures;
        int missingAtlasEntries;
        long wrongRepresentativeTextureRisks;
        int browserAtlasItems;
        int browserAtlasDrawableItems;
    }

    private static final class AnimationTotals {
        long animations;
        int animatedAtlasFiles;
        int animatedAtlasManifestAssets;
        int missingAnimatedAtlasEntries;
        long missingTimingData;
        int staticWhenAnimationExpected;
        int singularityLikeAssets;
        int animatedSingularityLikeAssets;
    }

    private static final class NativeRenderTotals {
        long backendFacts;
        long backendAngelica;
        long textureSprites;
        long textureSpritesMissingTiming;
        long itemRenderers;
        long shaderItems;
        long shaderItemsRequiringCapture;
        long shaderItemsMissingCapture;
        long unknownSpecialRenderers;
        long framebufferCaptures;
        long framebufferCapturesWithoutFrames;
        Double captureCompletenessRatio;
        String status;
    }

    private static final class RecipeTotals {
        long recipes;
        long recipeTypes;
        long neiHandlers;
        long neiHandlerLayouts;
        long handlersWithLoadedRecipes;
        long handlersWithExportedRecipes;
        long suspiciousZeroRecipeHandlers;
        long nativeCoveredZeroRecipeHandlers;
        long nonRecipeInfoZeroRecipeHandlers;
        long expectedEmptyHandlers;
        long legalZeroRecipeHandlers;
        long partialExports;
        long duplicateCategoryRisks;
        String zeroRecipeStatus;
    }

    private static final class NativeUiTotals {
        long layouts;
        long slots;
        long missingSurfaces;
        long slotBoundsViolations;
        long backgroundBoundsViolations;
        long coordinateContractViolations;
        String status;
    }

    private static final class RuntimeManifestMetadata {
        String gtnhProfile;
        String exportRepository;
        String exportSelection;
        String exporterSchemaVersion;
        String exportTimestamp;
        String healthStatus;
        String compileReadinessStatus;
        String assetHash;
    }

    private static final class PathHygieneRule {
        final String name;
        final Pattern pattern;

        PathHygieneRule(String name, Pattern pattern) {
            this.name = name;
            this.pattern = pattern;
        }
    }

    private static final class PathHygieneSample {
        String file;
        int line;
        String rule;
        String text;
    }

    private static final class RenderAssetManifest {
        List<CanonicalRenderAsset> assets;
    }

    static class PreviousSnapshot {
        int itemsJsonGzFiles;
        int recipeJsonGzFiles;
        int imagePngFiles;
        int imageGifFiles;
        int staticAtlasManifestAssets;
        int animatedAtlasManifestAssets;
    }

    static final class DeltaSnapshot extends PreviousSnapshot {}
}
