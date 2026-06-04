package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalExportMapper;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalFluid;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRecipe;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.fluid.Fluid;
import com.github.dcysteine.nesql.sql.base.recipe.Recipe;
import com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

/**
 * Writes the first raw-export sidecar without replacing the current canonical
 * export structure.
 *
 * <p>This is intentionally conservative: the sidecar records counts, source
 * profile, selected stages, and references to the existing canonical outputs.
 * Later rebuild phases can replace each placeholder JSONL file with true raw
 * fact streams while NeoNEI continues to consume the current export layout.</p>
 */
public final class RawExportSidecarWriter {
    private static final String OUTPUT_DIRECTORY = "raw-export";
    private static final String SCHEMA_VERSION = "nesqlpp/raw-export/alpha1";
    private static final int ITEM_BATCH_SIZE = 4096;
    private static final int FLUID_BATCH_SIZE = 2048;
    private static final int RECIPE_BATCH_SIZE = 512;

    private final EntityManager entityManager;
    private final File repositoryDirectory;
    private final ExportContext exportContext;
    private final List<CanonicalRenderAsset> renderAssets;

    public RawExportSidecarWriter(
            EntityManager entityManager,
            File repositoryDirectory,
            ExportContext exportContext,
            List<CanonicalRenderAsset> renderAssets) {
        this.entityManager = entityManager;
        this.repositoryDirectory = repositoryDirectory;
        this.exportContext = exportContext;
        this.renderAssets = renderAssets == null
                ? java.util.Collections.<CanonicalRenderAsset>emptyList()
                : renderAssets;
    }

    public void export() throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        ensureDirectory(rawDir);
        purgeLegacyRawExportOutputs(rawDir);

        RawFactCounts factCounts = writeRawFactStreams(rawDir);
        SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary semanticAudit =
                new SemanticItemIdentityDiagnosticsWriter(entityManager, rawDir).write();

        RawExportReport report = buildReport();
        report.counts.rawItems = factCounts.items;
        report.counts.rawFluids = factCounts.fluids;
        report.counts.rawRecipes = factCounts.recipes;
        report.counts.rawGroups = factCounts.groups;
        report.counts.rawNeiOrderEntries = factCounts.neiOrderEntries;
        report.counts.neiRuntimePanelItems = factCounts.neiRuntimePanelItems;
        report.counts.neiExportOnlyItems = factCounts.neiExportOnlyItems;
        report.counts.neiBrowserItems = factCounts.neiBrowserItems;
        report.counts.neiDefaultEntries = factCounts.neiDefaultEntries;
        report.counts.neiFallbackGroups = factCounts.neiFallbackGroups;
        report.counts.neiNativeGroups = factCounts.neiNativeGroups;
        report.counts.neiSyntheticGroups = factCounts.neiSyntheticGroups;
        report.counts.neiGuidFilterRules = factCounts.neiGuidFilterRules;
        report.counts.neiHiddenItemRules = factCounts.neiHiddenItemRules;
        report.counts.neiHiddenItems = factCounts.neiHiddenItems;
        report.counts.neiRepresentativeMismatches = factCounts.neiRepresentativeMismatches;
        report.counts.neiHandlers = factCounts.neiHandlers;
        report.counts.neiHandlerLayouts = factCounts.neiHandlerLayouts;
        report.counts.rawTextures = factCounts.textures;
        report.counts.rawAnimations = factCounts.animations;
        report.counts.rawEntities = factCounts.entities;
        report.counts.rawBrowserAtlasAssets = factCounts.browserAtlasAssets;
        report.counts.renderBackendFacts = factCounts.renderBackendFacts;
        report.counts.renderTextureSprites = factCounts.renderTextureSprites;
        report.counts.renderItemRenderers = factCounts.renderItemRenderers;
        report.counts.semanticTotalItems = semanticAudit.totalItems;
        report.counts.semanticTaggedItems = semanticAudit.taggedItems;
        report.counts.semanticClassifiedTaggedItems = semanticAudit.classifiedTaggedItems;
        report.counts.semanticUnclassifiedTaggedItems = semanticAudit.unclassifiedTaggedItems;
        report.counts.semanticEstimatedPublicItems = semanticAudit.estimatedPublicItemsAfterNormalization;
        report.counts.semanticFamilyCount = semanticAudit.familyCount;
        report.counts.semanticItems = semanticAudit.semanticItems;
        report.counts.semanticVariants = semanticAudit.variants;
        report.counts.semanticPayloads = semanticAudit.payloads;
        report.counts.semanticIdentityMapRows = semanticAudit.identityMapRows;
        report.neiBrowserContract = factCounts.neiBrowserContract;
        applyRawValidation(report);
        RawExportManifest manifest = buildManifest(report);

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        writeJson(gson, new File(rawDir, "manifest.json"), manifest);
        writeJson(gson, new File(rawDir, "export_report.json"), report);
        writeJson(gson, new File(rawDir, "validation/export_report.json"), report);
        if (report.neiBrowserContract != null) {
            writeJson(gson, new File(rawDir, "validation/nei_browser_contract.json"), report.neiBrowserContract);
        }
        writeSizeReport(gson, rawDir);
        createEmptyJsonlIfMissing(new File(rawDir, "validation/errors.jsonl"));

        Logger.MOD.info(
                "Semantic item identity audit written: totalItems={}, taggedItems={}, classifiedTaggedItems={}, semanticItems={}, variants={}, payloads={}",
                semanticAudit.totalItems,
                semanticAudit.taggedItems,
                semanticAudit.classifiedTaggedItems,
                semanticAudit.semanticItems,
                semanticAudit.variants,
                semanticAudit.payloads);
        Logger.chatMessage(EnumChatFormatting.GREEN + "Raw-export sidecar written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + rawDir.getAbsolutePath());
    }

    public static void syncFinalReports(File repositoryDirectory) throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        ensureDirectory(rawDir);
        File rawValidationDir = new File(rawDir, "validation");
        ensureDirectory(rawValidationDir);
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-stage-timings.json"),
                new File(rawDir, "export_stage_timings.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-stage-timings.json"),
                new File(rawValidationDir, "export_stage_timings.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-stage-checkpoint.json"),
                new File(rawDir, "stage_checkpoint.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-stage-checkpoint.json"),
                new File(rawValidationDir, "stage_checkpoint.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/stage-checksums.json"),
                new File(rawDir, "stage_checksums.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/stage-checksums.json"),
                new File(rawValidationDir, "stage_checksums.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-manifest.json"),
                new File(rawDir, "export_manifest.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-manifest.json"),
                new File(rawValidationDir, "export_manifest.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-validation-report.json"),
                new File(rawDir, "validation_report.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-validation-report.json"),
                new File(rawValidationDir, "export-health-report.json"));

        copyIfPresent(
                new File(rawValidationDir, "export_stage_timings.json"),
                new File(rawDir, "export_stage_timings.json"));
        copyIfPresent(
                new File(rawValidationDir, "stage_checkpoint.json"),
                new File(rawDir, "stage_checkpoint.json"));
        copyIfPresent(
                new File(rawValidationDir, "stage_checksums.json"),
                new File(rawDir, "stage_checksums.json"));
        copyIfPresent(
                new File(rawValidationDir, "export_manifest.json"),
                new File(rawDir, "export_manifest.json"));
        copyIfPresent(
                new File(rawValidationDir, "export-health-report.json"),
                new File(rawDir, "validation_report.json"));
    }

    private RawExportManifest buildManifest(RawExportReport report) {
        RawExportManifest manifest = new RawExportManifest();
        manifest.schemaVersion = SCHEMA_VERSION;
        manifest.generatedAt = utcNow();
        manifest.repositoryName = exportContext.paths.repositoryName;
        manifest.profile = exportContext.profile.profileId;
        manifest.selection = exportContext.selection.describe();
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
        manifest.files.put("animations", "assets/animations/index.jsonl.gz");
        manifest.files.put("nativeSprites", "assets/animations/native-sprites.jsonl.gz");
        manifest.files.put("renderedGifs", "assets/animations/rendered-gifs.jsonl.gz");
        manifest.files.put("renderBackend", "facts/render/backend.json");
        manifest.files.put("renderTextureSprites", "facts/render/texture-sprites.jsonl.gz");
        manifest.files.put("renderItemRenderers", "facts/render/item-renderers.jsonl.gz");
        manifest.files.put("browserAtlasIndex", "assets/textures/browser_atlas_index.json");
        manifest.files.put("browserAtlasAssets", "assets/textures/atlas-assets");
        manifest.files.put("neiHandlers", "facts/nei/handlers.jsonl.gz");
        manifest.files.put("neiHandlerLayouts", "facts/nei/handler-layouts.jsonl.gz");
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
        manifest.counts = report.counts;
        return manifest;
    }

    private RawExportReport buildReport() {
        RawExportReport report = new RawExportReport();
        report.schemaVersion = SCHEMA_VERSION + "/report";
        report.generatedAt = utcNow();
        report.profile = exportContext.profile.profileId;
        report.selection = exportContext.selection.describe();

        RawExportCounts counts = new RawExportCounts();
        counts.items = countQuery("SELECT COUNT(i) FROM Item i");
        counts.fluids = countQuery("SELECT COUNT(f) FROM Fluid f");
        counts.recipes = countQuery("SELECT COUNT(r) FROM Recipe r");
        counts.recipeTypes = countQuery("SELECT COUNT(rt) FROM RecipeType rt");
        counts.renderAssets = renderAssets.size();
        counts.itemModFiles = countFiles(new File(repositoryDirectory, "items"), "items.json.gz");
        counts.recipeModFiles = countFiles(new File(repositoryDirectory, "recipes"), "recipes.json.gz");
        counts.canonicalFiles = 0L;
        report.counts = counts;

        report.validation.missingTextureCount = 0;
        report.validation.missingAnimationMetadataCount = 0;
        report.validation.missingGroupOrOrderCount = 0;
        report.validation.failedStages = new ArrayList<String>();
        report.validation.gates = new ArrayList<RawValidationGate>();
        report.validation.status = "not-yet-enforced";
        report.validation.readinessStatus = "blocked";
        return report;
    }

    private static void applyRawValidation(RawExportReport report) {
        if (report == null || report.counts == null || report.validation == null) {
            return;
        }
        RawExportCounts counts = report.counts;
        List<String> issues = new ArrayList<String>();
        addCountMismatch(issues, "items", counts.items, counts.rawItems);
        addCountMismatch(issues, "fluids", counts.fluids, counts.rawFluids);
        addCountMismatch(issues, "recipes", counts.recipes, counts.rawRecipes);
        if (counts.renderAssets > 0) {
            addCountMismatch(issues, "renderAssets/textures", counts.renderAssets, counts.rawTextures);
        }
        report.validation.missingTextureCount = counts.rawItems > 0 && counts.rawTextures == 0 ? counts.rawItems : 0;
        report.validation.missingAnimationMetadataCount = counts.rawAnimations > 0 ? 0 : report.validation.missingAnimationMetadataCount;
        report.validation.missingGroupOrOrderCount = (counts.rawGroups == 0 || counts.rawNeiOrderEntries == 0) ? 1 : 0;
        report.validation.failedStages = issues;
        report.validation.status = issues.isEmpty() ? "ok" : "warning";
        report.validation.gates = buildRawValidationGates(counts, issues);
        report.validation.readinessStatus = rawValidationReady(report.validation) ? "ready" : "blocked";
    }

    private static List<RawValidationGate> buildRawValidationGates(RawExportCounts counts, List<String> issues) {
        List<RawValidationGate> gates = new ArrayList<RawValidationGate>();
        gates.add(validationGate(
                "core-counts",
                issues.isEmpty(),
                issues.isEmpty()
                        ? "Raw fact counts match database/canonical source counts."
                        : "Raw fact counts have " + issues.size() + " mismatch(es)."));
        gates.add(validationGate(
                "browser-order",
                counts.rawGroups > 0 && counts.rawNeiOrderEntries > 0,
                counts.rawGroups > 0 && counts.rawNeiOrderEntries > 0
                        ? "NEI browser groups and ordering rows are present."
                        : "NEI browser groups or ordering rows are missing."));
        gates.add(validationGate(
                "nei-browser-contract",
                counts.neiBrowserItems > 0
                        && counts.rawGroups == counts.neiNativeGroups + counts.neiFallbackGroups + counts.neiSyntheticGroups
                        && counts.neiRepresentativeMismatches == 0,
                "NEI browser contract: panelItems="
                        + counts.neiRuntimePanelItems
                        + ", browserItems="
                        + counts.neiBrowserItems
                        + ", groups="
                        + counts.rawGroups
                        + ", nativeGroups="
                        + counts.neiNativeGroups
                        + ", fallbackGroups="
                        + counts.neiFallbackGroups
                        + ", syntheticGroups="
                        + counts.neiSyntheticGroups
                        + ", representativeMismatches="
                        + counts.neiRepresentativeMismatches
                        + "."));
        gates.add(validationGate(
                "semantic-identity",
                counts.rawItems == 0
                        || (counts.semanticTotalItems == counts.rawItems
                                && counts.semanticIdentityMapRows == counts.rawItems
                                && counts.semanticItems > 0
                                && counts.semanticFamilyCount > 0),
                "Semantic identity streams: rawItems="
                        + counts.rawItems
                        + ", totalItems="
                        + counts.semanticTotalItems
                        + ", identityMapRows="
                        + counts.semanticIdentityMapRows
                        + ", semanticItems="
                        + counts.semanticItems
                        + ", families="
                        + counts.semanticFamilyCount
                        + ", classifiedTagged="
                        + counts.semanticClassifiedTaggedItems
                        + ", unclassifiedTagged="
                        + counts.semanticUnclassifiedTaggedItems
                        + "."));
        gates.add(validationGate(
                "native-nei-rules",
                counts.neiGuidFilterRules >= 0 && counts.neiHiddenItemRules >= 0,
                "Native NEI rule streams: guidFilters="
                        + counts.neiGuidFilterRules
                        + ", hiddenItems="
                        + counts.neiHiddenItemRules
                        + "."));
        gates.add(validationGate(
                "nei-handler-metadata",
                counts.neiHandlers > 0 && counts.neiHandlerLayouts > 0,
                counts.neiHandlers > 0 && counts.neiHandlerLayouts > 0
                        ? "NEI handler metadata and layout streams are present."
                        : "NEI handler metadata or layout facts are missing."));
        gates.add(validationGate(
                "textures",
                counts.rawItems == 0 || counts.rawTextures > 0,
                counts.rawItems == 0 || counts.rawTextures > 0
                        ? "Texture index stream is present for exported item rows."
                        : "Texture index is empty while item rows are present."));
        gates.add(validationGate(
                "animations",
                counts.rawAnimations >= 0,
                "Animation metadata stream is present; zero rows is valid when no animated assets are detected."));
        gates.add(validationGate(
                "angelica-render-facts",
                counts.renderBackendFacts == 1 && counts.renderTextureSprites >= 0 && counts.renderItemRenderers == counts.rawItems,
                "Angelica render facts: backendFacts="
                        + counts.renderBackendFacts
                        + ", textureSprites="
                        + counts.renderTextureSprites
                        + ", itemRenderers="
                        + counts.renderItemRenderers
                        + ", rawItems="
                        + counts.rawItems
                        + "."));
        gates.add(validationGate(
                "entity-models",
                counts.rawEntities >= 0,
                "Entity model stream is present; zero rows is valid when entity exports are not selected."));
        return gates;
    }

    private static RawValidationGate validationGate(String name, boolean ready, String summary) {
        RawValidationGate gate = new RawValidationGate();
        gate.name = name;
        gate.status = ready ? "ready" : "blocked";
        gate.summary = summary;
        return gate;
    }

    private static boolean rawValidationReady(RawExportValidation validation) {
        if (validation == null || validation.gates == null || validation.gates.isEmpty()) {
            return false;
        }
        for (RawValidationGate gate : validation.gates) {
            if (!"ready".equals(gate.status)) {
                return false;
            }
        }
        return true;
    }

    private static void addCountMismatch(List<String> issues, String label, long expected, long actual) {
        if (expected != actual) {
            issues.add("count-mismatch:" + label + ":expected=" + expected + ":actual=" + actual);
        }
    }

    private RawFactCounts writeRawFactStreams(File rawDir) throws IOException {
        RawFactCounts counts = new RawFactCounts();
        RepositoryStreamResult repository = streamRepositoryFacts(rawDir);
        counts.items = repository.items;
        counts.fluids = repository.fluids;
        counts.recipes = repository.recipes;

        JsonObject browserLayout = readObject(new File(repositoryDirectory, "canonical/browser-layout-index.json"));
        if (browserLayout != null) {
            JsonArray groups = browserLayout.getAsJsonArray("groups");
            JsonArray order = browserLayout.getAsJsonArray("defaultEntries");
            counts.groups = writeArrayAsJsonl(groups, new File(rawDir, "facts/nei/groups.jsonl.gz"));
            counts.neiOrderEntries =
                    writeArrayAsJsonl(order, new File(rawDir, "facts/nei/order.jsonl.gz"));
            counts.neiBrowserContract = buildNeiBrowserContract(browserLayout, groups, order);
            counts.neiRuntimePanelItems = readLong(browserLayout, "neiRuntimeItemCount", 0L);
            counts.neiExportOnlyItems = readLong(browserLayout, "exportOnlyItemCount", 0L);
            counts.neiBrowserItems = readLong(browserLayout, "itemCount", countArray(browserLayout, "items"));
            counts.neiDefaultEntries = readLong(browserLayout, "defaultEntryCount", order == null ? 0L : order.size());
            counts.neiHiddenItems = readLong(browserLayout, "hiddenItemCount", 0L);
            if (counts.neiBrowserContract != null) {
                counts.neiFallbackGroups = counts.neiBrowserContract.fallbackGroupCount;
                counts.neiNativeGroups = counts.neiBrowserContract.nativeGroupCount;
                counts.neiSyntheticGroups = counts.neiBrowserContract.syntheticGroupCount;
                counts.neiRepresentativeMismatches = counts.neiBrowserContract.representativeMismatchCount;
            }
        } else {
            createEmptyJsonl(new File(rawDir, "facts/nei/groups.jsonl.gz"));
            createEmptyJsonl(new File(rawDir, "facts/nei/order.jsonl.gz"));
        }
        counts.neiGuidFilterRules = writeGuidFilterRules(rawDir);
        counts.neiHiddenItemRules = writeHiddenItemRules(rawDir);

        JsonArray textureRows = new JsonArray();
        JsonArray animationRows = new JsonArray();
        JsonArray nativeSpriteRows = new JsonArray();
        JsonArray renderedGifRows = new JsonArray();
        for (CanonicalRenderAsset asset : renderAssets) {
            JsonObject row = toRenderAssetRow(asset);
            textureRows.add(row);
            if (isAnimated(asset)) {
                animationRows.add(row);
                if (isNativeSpriteAnimation(asset)) {
                    nativeSpriteRows.add(row);
                } else if (isRenderedGifAnimation(asset)) {
                    renderedGifRows.add(row);
                }
            }
        }
        if (textureRows.size() == 0) {
            JsonObject renderManifest = readObject(new File(repositoryDirectory, "canonical/render-assets.json"));
            if (renderManifest != null && renderManifest.has("assets") && renderManifest.get("assets").isJsonArray()) {
                JsonArray assets = renderManifest.getAsJsonArray("assets");
                counts.textures = writeArrayAsJsonl(assets, new File(rawDir, "assets/textures/index.jsonl.gz"));
                JsonArray animated = new JsonArray();
                JsonArray nativeSprites = new JsonArray();
                JsonArray renderedGifs = new JsonArray();
                for (JsonElement element : assets) {
                    if (element.isJsonObject() && isAnimated(element.getAsJsonObject())) {
                        JsonObject asset = element.getAsJsonObject();
                        animated.add(element);
                        if (isNativeSpriteAnimation(asset)) {
                            nativeSprites.add(element);
                        } else if (isRenderedGifAnimation(asset)) {
                            renderedGifs.add(element);
                        }
                    }
                }
                counts.animations = writeArrayAsJsonl(animated, new File(rawDir, "assets/animations/index.jsonl.gz"));
                writeArrayAsJsonl(nativeSprites, new File(rawDir, "assets/animations/native-sprites.jsonl.gz"));
                writeArrayAsJsonl(renderedGifs, new File(rawDir, "assets/animations/rendered-gifs.jsonl.gz"));
            } else {
                createEmptyJsonl(new File(rawDir, "assets/textures/index.jsonl.gz"));
                createEmptyJsonl(new File(rawDir, "assets/animations/index.jsonl.gz"));
                createEmptyJsonl(new File(rawDir, "assets/animations/native-sprites.jsonl.gz"));
                createEmptyJsonl(new File(rawDir, "assets/animations/rendered-gifs.jsonl.gz"));
            }
        } else {
            counts.textures = writeArrayAsJsonl(textureRows, new File(rawDir, "assets/textures/index.jsonl.gz"));
            counts.animations = writeArrayAsJsonl(animationRows, new File(rawDir, "assets/animations/index.jsonl.gz"));
            writeArrayAsJsonl(nativeSpriteRows, new File(rawDir, "assets/animations/native-sprites.jsonl.gz"));
            writeArrayAsJsonl(renderedGifRows, new File(rawDir, "assets/animations/rendered-gifs.jsonl.gz"));
        }
        counts.browserAtlasAssets = writeBrowserAtlasIndexAndAssets(rawDir);

        HandlerMetadataCounts handlerCounts = writeHandlerMetadata(rawDir);
        counts.neiHandlers = handlerCounts.handlers;
        counts.neiHandlerLayouts = handlerCounts.layouts;
        createEmptyJsonl(new File(rawDir, "models/multiblocks/index.jsonl.gz"));
        counts.entities = writeEntityModelIndex(rawDir);
        AngelicaRenderFactsWriter.Counts renderCounts =
                new AngelicaRenderFactsWriter(entityManager, rawDir).write();
        counts.renderBackendFacts = renderCounts.backendFacts;
        counts.renderTextureSprites = renderCounts.textureSprites;
        counts.renderItemRenderers = renderCounts.itemRenderers;
        return counts;
    }

    private static NeiBrowserContract buildNeiBrowserContract(
            JsonObject browserLayout,
            JsonArray groups,
            JsonArray defaultEntries) {
        NeiBrowserContract contract = new NeiBrowserContract();
        contract.schemaVersion = SCHEMA_VERSION + "/nei-browser-contract";
        contract.generatedAt = utcNow();
        contract.neiRuntimeSnapshot = readBoolean(browserLayout, "neiRuntimeSnapshot", false);
        contract.neiRuntimePanelItemCount = readLong(browserLayout, "neiRuntimeItemCount", 0L);
        contract.exportOnlyItemCount = readLong(browserLayout, "exportOnlyItemCount", 0L);
        contract.browserItemCount = readLong(browserLayout, "itemCount", countArray(browserLayout, "items"));
        contract.groupCount = readLong(browserLayout, "groupCount", groups == null ? 0L : groups.size());
        contract.defaultEntryCount = readLong(browserLayout, "defaultEntryCount", defaultEntries == null ? 0L : defaultEntries.size());
        JsonObject source = browserLayout == null ? null : browserLayout.getAsJsonObject("source");
        if (source != null) {
            contract.orderSource = readString(source, "order", null);
            contract.groupingSource = readString(source, "grouping", null);
            contract.guidFiltersSource = readString(source, "guidFilters", null);
            contract.hiddenItemsSource = readString(source, "hiddenItems", null);
        }
        contract.guidFilterRuleCount = readLong(browserLayout, "guidFilterRuleCount", 0L);
        contract.hiddenItemRuleCount = readLong(browserLayout, "hiddenItemRuleCount", 0L);
        contract.hiddenItemCount = readLong(browserLayout, "hiddenItemCount", 0L);
        if (groups != null) {
            for (JsonElement element : groups) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject group = element.getAsJsonObject();
                String groupKey = readString(group, "groupKey", "");
                if (groupKey.startsWith("fallback:")) {
                    contract.fallbackGroupCount++;
                } else if (groupKey.startsWith("nei:")) {
                    contract.nativeGroupCount++;
                } else if (!groupKey.isEmpty()) {
                    contract.syntheticGroupCount++;
                }
                JsonArray members = group.getAsJsonArray("memberItemIds");
                contract.groupedMemberCount += members == null ? 0 : members.size();
                String representative = readString(group, "representativeItemId", null);
                if (representative == null || representative.isEmpty()) {
                    contract.missingRepresentativeCount++;
                } else if (!arrayContainsString(members, representative)) {
                    contract.representativeMismatchCount++;
                    if (contract.representativeMismatchSamples.size() < 50) {
                        BrowserContractMismatch sample = new BrowserContractMismatch();
                        sample.groupKey = groupKey;
                        sample.groupLabel = readString(group, "groupLabel", null);
                        sample.representativeItemId = representative;
                        sample.firstMemberItemIds = firstStrings(members, 5);
                        contract.representativeMismatchSamples.add(sample);
                    }
                }
            }
        }
        contract.ungroupedBrowserItemCount = Math.max(0L, contract.browserItemCount - contract.groupedMemberCount);
        contract.runtimeToBrowserDelta = contract.browserItemCount - contract.neiRuntimePanelItemCount;
        contract.status = contract.browserItemCount > 0 && contract.representativeMismatchCount == 0 ? "ok" : "warning";
        contract.summary = "NEI panel items="
                + contract.neiRuntimePanelItemCount
                + ", NeoNEI browser items="
                + contract.browserItemCount
                + ", groups="
                + contract.groupCount
                + ", fallbackGroups="
                + contract.fallbackGroupCount
                + ", hiddenItems="
                + contract.hiddenItemCount
                + ", representativeMismatches="
                + contract.representativeMismatchCount
                + ".";
        return contract;
    }

    private static long countArray(JsonObject object, String key) {
        JsonArray array = object == null ? null : object.getAsJsonArray(key);
        return array == null ? 0L : array.size();
    }

    private static long readLong(JsonObject object, String key, long fallback) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readString(JsonObject object, String key, String fallback) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? fallback : element.getAsString();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean arrayContainsString(JsonArray array, String expected) {
        if (array == null || expected == null) {
            return false;
        }
        for (JsonElement element : array) {
            if (element != null && !element.isJsonNull() && expected.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> firstStrings(JsonArray array, int limit) {
        List<String> values = new ArrayList<String>();
        if (array == null || limit <= 0) {
            return values;
        }
        for (JsonElement element : array) {
            if (values.size() >= limit) {
                break;
            }
            if (element != null && !element.isJsonNull()) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private long writeEntityModelIndex(File rawDir) throws IOException {
        JsonObject previews = readObject(new File(repositoryDirectory, "canonical/entity-previews.json"));
        JsonObject models = readObject(new File(repositoryDirectory, "canonical/entity-models.json"));
        JsonArray previewEntries = previews == null ? null : previews.getAsJsonArray("entries");
        JsonArray modelEntries = models == null ? null : models.getAsJsonArray("entries");

        Map<String, JsonObject> byMobName = new LinkedHashMap<String, JsonObject>();
        if (previewEntries != null) {
            for (JsonElement element : previewEntries) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject preview = element.getAsJsonObject();
                String mobName = stringAt(preview, "mobName");
                if (mobName == null || mobName.trim().length() == 0) {
                    continue;
                }
                JsonObject row = entityRow(byMobName, mobName.trim());
                copyElement(row, "preview", preview, "");
                copyString(row, "entityId", preview, "mobName");
                copyString(row, "displayName", preview, "localizedName");
                copyString(row, "modId", preview, "modId");
                copyString(row, "previewImage", preview, "relativeGifPath");
                copyFirstNumber(row, "frameCount", preview, "frameCount");
                copyFirstNumber(row, "frameDurationMs", preview, "frameDurationMs");
                copyFirstNumber(row, "width", preview, "width");
                copyFirstNumber(row, "height", preview, "height");
                copyString(row, "previewRenderMode", preview, "renderMode");
            }
        }
        if (modelEntries != null) {
            for (JsonElement element : modelEntries) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject model = element.getAsJsonObject();
                String mobName = stringAt(model, "mobName");
                if (mobName == null || mobName.trim().length() == 0) {
                    continue;
                }
                JsonObject row = entityRow(byMobName, mobName.trim());
                copyElement(row, "model", model, "");
                copyString(row, "entityId", model, "mobName");
                copyString(row, "displayName", model, "localizedName");
                copyString(row, "modId", model, "modId");
                copyString(row, "modelPath", model, "relativeModelPath");
                copyFirstNumber(row, "componentCount", model, "componentCount");
                copyString(row, "modelRenderMode", model, "renderMode");
            }
        }

        JsonArray rows = new JsonArray();
        for (JsonObject row : byMobName.values()) {
            row.addProperty("schemaVersion", SCHEMA_VERSION + "/entity-model");
            rows.add(row);
        }
        return writeArrayAsJsonl(rows, new File(rawDir, "models/entities/index.jsonl.gz"));
    }

    private long writeBrowserAtlasIndexAndAssets(File rawDir) throws IOException {
        JsonObject atlasIndex = readObject(new File(repositoryDirectory, "canonical/browser-atlas-index.json"));
        if (atlasIndex == null) {
            return 0L;
        }

        LinkedHashSet<String> copiedAssets = new LinkedHashSet<String>();
        JsonArray items = atlasIndex.getAsJsonArray("items");
        if (items != null) {
            for (JsonElement element : items) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                rewriteBrowserAtlasPlacement(rawDir, objectAt(item, "staticAtlas"), copiedAssets);
                rewriteBrowserAtlasPlacement(rawDir, objectAt(item, "animatedAtlas"), copiedAssets);
            }
        }
        atlasIndex.addProperty("rawExportMaterializedAtlasAssets", copiedAssets.size());
        writeJson(new GsonBuilder().setPrettyPrinting().serializeNulls().create(),
                new File(rawDir, "assets/textures/browser_atlas_index.json"),
                atlasIndex);
        return copiedAssets.size();
    }

    private long writeGuidFilterRules(File rawDir) throws IOException {
        File source = resolveNativeNeiRulePath(
                "nesql.guidFiltersCfg",
                "NESQL_GUID_FILTERS_CFG",
                "guidfilters.cfg",
                "assets/nei/cfg/guidfilters.cfg");
        JsonArray rows = new JsonArray();
        if (source != null) {
            try (BufferedReader reader = openUtf8Reader(source)) {
                String rawLine;
                int lineNumber = 0;
                while ((rawLine = reader.readLine()) != null) {
                    lineNumber++;
                    String line = stripBom(rawLine).trim();
                    if (line.length() == 0 || line.startsWith("#")) {
                        continue;
                    }
                    JsonObject row = new JsonObject();
                    row.addProperty("schemaVersion", SCHEMA_VERSION + "/nei-guidfilter-rule");
                    row.addProperty("sourceKind", "gtnh-nei-config");
                    row.addProperty("sourceFile", source.getName());
                    row.addProperty("lineNumber", lineNumber);
                    row.addProperty("raw", line);
                    int comma = line.indexOf(',');
                    String itemExpression = comma >= 0 ? line.substring(0, comma).trim() : line;
                    String nbtPath = comma >= 0 ? line.substring(comma + 1).trim() : "";
                    row.addProperty("itemExpression", itemExpression);
                    row.addProperty("nbtPath", nbtPath.length() == 0 ? null : nbtPath);
                    row.addProperty("normalizedItemExpression", normalizeRuleToken(itemExpression));
                    row.addProperty("normalizedNbtPath", nbtPath.length() == 0 ? null : normalizeRuleToken(nbtPath));
                    rows.add(row);
                }
            }
        }
        return writeArrayAsJsonl(rows, new File(rawDir, "facts/nei/guidfilters.jsonl.gz"));
    }

    private long writeHiddenItemRules(File rawDir) throws IOException {
        File source = resolveNativeNeiRulePath(
                "nesql.hiddenItemsCfg",
                "NESQL_HIDDEN_ITEMS_CFG",
                "hiddenitems.cfg",
                null);
        JsonArray rows = new JsonArray();
        if (source != null) {
            try (BufferedReader reader = openUtf8Reader(source)) {
                String rawLine;
                int lineNumber = 0;
                while ((rawLine = reader.readLine()) != null) {
                    lineNumber++;
                    String line = stripBom(rawLine).trim();
                    if (line.length() == 0 || line.startsWith("#") || line.startsWith(";")) {
                        continue;
                    }
                    JsonObject row = new JsonObject();
                    row.addProperty("schemaVersion", SCHEMA_VERSION + "/nei-hidden-item-rule");
                    row.addProperty("sourceKind", "gtnh-nei-config");
                    row.addProperty("sourceFile", source.getName());
                    row.addProperty("lineNumber", lineNumber);
                    row.addProperty("raw", line);
                    row.addProperty("itemExpression", line);
                    row.addProperty("normalizedItemExpression", normalizeRuleToken(line));
                    rows.add(row);
                }
            }
        }
        return writeArrayAsJsonl(rows, new File(rawDir, "facts/nei/hiddenitems.jsonl.gz"));
    }

    private HandlerMetadataCounts writeHandlerMetadata(File rawDir) throws IOException {
        JsonArray sourceEntries = loadBundledHandlerMetadata();
        JsonArray handlerRows = new JsonArray();
        JsonArray layoutRows = new JsonArray();
        Set<String> seenHandlers = new LinkedHashSet<String>();
        int ordinal = 0;
        for (JsonElement element : sourceEntries) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject source = element.getAsJsonObject();
            String handlerClass = trimToNull(readString(source, "handler", null));
            if (handlerClass == null || seenHandlers.contains(handlerClass)) {
                continue;
            }
            seenHandlers.add(handlerClass);
            String itemName = trimToNull(readString(source, "itemName", null));
            String modId = trimToNull(readString(source, "modId", null));
            String modName = trimToNull(readString(source, "modName", null));
            String displayName = handlerDisplayName(handlerClass, itemName);
            String handlerKey = stableHandlerKey(handlerClass, itemName, ordinal++);
            int width = parseInt(readString(source, "handlerWidth", null), 166);
            int height = parseInt(readString(source, "handlerHeight", null), 65);
            int maxPerPage = parseInt(readString(source, "maxRecipesPerPage", null), 1);
            int yShift = parseInt(readString(source, "yShift", null), 0);
            String family = classifyHandlerFamily(handlerClass, itemName, modId);
            String layoutKind = inferLayoutKind(handlerClass, itemName, family);

            JsonObject handler = new JsonObject();
            handler.addProperty("schemaVersion", SCHEMA_VERSION + "/nei-handler");
            handler.addProperty("handlerKey", handlerKey);
            handler.addProperty("handlerClass", handlerClass);
            handler.addProperty("displayName", displayName);
            handler.addProperty("localizedName", displayName);
            handler.addProperty("canonicalMachineFamily", family);
            handler.addProperty("modId", modId);
            handler.addProperty("modName", modName);
            handler.addProperty("catalystItemName", itemName);
            handler.addProperty("preferredMachineItemName", preferredMachineItemName(handlerClass, itemName, family));
            handler.addProperty("gtMultiblockPreferred", isLikelyGtMultiblock(handlerClass, itemName, family));
            handler.addProperty("maxRecipesPerPage", maxPerPage);
            handler.addProperty("handlerWidth", width);
            handler.addProperty("handlerHeight", height);
            handler.addProperty("yShift", yShift);
            handler.add("source", new JsonParser().parse(source.toString()));
            handlerRows.add(handler);

            JsonObject layout = new JsonObject();
            layout.addProperty("schemaVersion", SCHEMA_VERSION + "/nei-handler-layout");
            layout.addProperty("handlerKey", handlerKey);
            layout.addProperty("handlerClass", handlerClass);
            layout.addProperty("layoutKind", layoutKind);
            layout.addProperty("width", width);
            layout.addProperty("height", height);
            layout.addProperty("yShift", yShift);
            layout.addProperty("maxRecipesPerPage", maxPerPage);
            layout.add("slots", defaultLayoutSlots(layoutKind));
            layout.add("textOverlays", new JsonArray());
            layoutRows.add(layout);
        }

        HandlerMetadataCounts counts = new HandlerMetadataCounts();
        counts.handlers = writeArrayAsJsonl(handlerRows, new File(rawDir, "facts/nei/handlers.jsonl.gz"));
        counts.layouts = writeArrayAsJsonl(layoutRows, new File(rawDir, "facts/nei/handler-layouts.jsonl.gz"));
        return counts;
    }

    private static JsonArray loadBundledHandlerMetadata() throws IOException {
        InputStream stream = RawExportSidecarWriter.class
                .getClassLoader()
                .getResourceAsStream("nesql/nei/handler-metadata.json");
        if (stream == null) {
            return new JsonArray();
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return new JsonArray();
            }
            JsonArray entries = parsed.getAsJsonObject().getAsJsonArray("entries");
            return entries == null ? new JsonArray() : entries;
        }
    }

    private static String stableHandlerKey(String handlerClass, String itemName, int ordinal) {
        String base = normalizeKey(firstNonBlank(handlerClass, itemName, "handler-" + ordinal));
        return base.length() == 0 ? "handler-" + ordinal : base;
    }

    private static String handlerDisplayName(String handlerClass, String itemName) {
        String simple = handlerSimpleName(handlerClass);
        if (simple.endsWith("RecipeHandler")) {
            simple = simple.substring(0, simple.length() - "RecipeHandler".length());
        } else if (simple.endsWith("Handler")) {
            simple = simple.substring(0, simple.length() - "Handler".length());
        }
        simple = simple.replaceAll("([a-z])([A-Z])", "$1 $2").trim();
        return simple.length() == 0 ? firstNonBlank(itemName, "NEI Handler") : simple;
    }

    private static String classifyHandlerFamily(String handlerClass, String itemName, String modId) {
        String descriptor = (firstNonBlank(handlerClass, "") + " " + firstNonBlank(itemName, "") + " " + firstNonBlank(modId, ""))
                .toLowerCase(java.util.Locale.ROOT);
        if (descriptor.contains("shaped") || descriptor.contains("shapeless") || descriptor.contains("crafting")) {
            return "crafting-table";
        }
        if (descriptor.contains("furnace") || descriptor.contains("smelting")) {
            return "furnace";
        }
        if (descriptor.contains("brewing")) {
            return "brewing";
        }
        if (descriptor.contains("gregtech") || descriptor.contains("gt.")) {
            return "gregtech-machine";
        }
        if (descriptor.contains("thaum") || descriptor.contains("arcane") || descriptor.contains("crucible") || descriptor.contains("infusion")) {
            return "thaumcraft";
        }
        if (descriptor.contains("botania") || descriptor.contains("mana")) {
            return "botania";
        }
        if (descriptor.contains("fluid") || descriptor.contains("liquid") || descriptor.contains("chemical")) {
            return "fluid-machine";
        }
        return "native-nei";
    }

    private static String inferLayoutKind(String handlerClass, String itemName, String family) {
        String descriptor = (firstNonBlank(handlerClass, "") + " " + firstNonBlank(itemName, "") + " " + firstNonBlank(family, ""))
                .toLowerCase(java.util.Locale.ROOT);
        if (descriptor.contains("crafting") || descriptor.contains("shaped") || descriptor.contains("shapeless")) {
            return "crafting-grid";
        }
        if (descriptor.contains("furnace") || descriptor.contains("smelting")) {
            return "furnace";
        }
        if (descriptor.contains("fluid") || descriptor.contains("liquid") || descriptor.contains("chemical")) {
            return "fluid-machine";
        }
        if (descriptor.contains("gregtech") || descriptor.contains("machine")) {
            return "machine";
        }
        return "native-nei";
    }

    private static JsonArray defaultLayoutSlots(String layoutKind) {
        JsonArray slots = new JsonArray();
        if ("crafting-grid".equals(layoutKind)) {
            addSlot(slots, "item-input", 0, 3, 3, 30, 12);
            addSlot(slots, "item-output", 9, 1, 1, 124, 30);
        } else if ("furnace".equals(layoutKind)) {
            addSlot(slots, "item-input", 0, 1, 1, 45, 24);
            addSlot(slots, "item-output", 1, 1, 1, 115, 24);
            addSlot(slots, "fuel", 2, 1, 1, 45, 46);
        } else if ("fluid-machine".equals(layoutKind)) {
            addSlot(slots, "item-input", 0, 3, 2, 18, 16);
            addSlot(slots, "fluid-input", 0, 3, 2, 72, 16);
            addSlot(slots, "item-output", 6, 3, 2, 126, 16);
        } else if ("machine".equals(layoutKind)) {
            addSlot(slots, "item-input", 0, 3, 3, 18, 12);
            addSlot(slots, "fluid-input", 0, 1, 3, 76, 12);
            addSlot(slots, "item-output", 9, 2, 2, 112, 21);
        } else {
            addSlot(slots, "item-input", 0, 3, 2, 24, 18);
            addSlot(slots, "item-output", 6, 2, 2, 116, 20);
        }
        return slots;
    }

    private static void addSlot(JsonArray slots, String role, int startIndex, int columns, int rows, int x, int y) {
        JsonObject slot = new JsonObject();
        slot.addProperty("role", role);
        slot.addProperty("startIndex", startIndex);
        slot.addProperty("columns", columns);
        slot.addProperty("rows", rows);
        slot.addProperty("x", x);
        slot.addProperty("y", y);
        slots.add(slot);
    }

    private static String preferredMachineItemName(String handlerClass, String itemName, String family) {
        if (isLikelyGtMultiblock(handlerClass, itemName, family)) {
            return itemName;
        }
        return itemName;
    }

    private static boolean isLikelyGtMultiblock(String handlerClass, String itemName, String family) {
        String descriptor = (firstNonBlank(handlerClass, "") + " " + firstNonBlank(itemName, "") + " " + firstNonBlank(family, ""))
                .toLowerCase(java.util.Locale.ROOT);
        return descriptor.contains("gregtech")
                && (descriptor.contains("multiblock")
                || descriptor.contains("large")
                || descriptor.contains("mega")
                || descriptor.contains("gt.blockmachines"));
    }

    private static String handlerSimpleName(String handlerClass) {
        String value = firstNonBlank(handlerClass, "");
        int index = value.lastIndexOf('.');
        return index >= 0 ? value.substring(index + 1) : value;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.trim().length() == 0 ? fallback : Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._:-]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    private static File resolveNativeNeiRulePath(
            String propertyName,
            String envName,
            String fileName,
            String bundledResourceRelativePath) {
        List<File> candidates = new ArrayList<File>();
        String property = System.getProperty(propertyName);
        if (property != null && !property.trim().isEmpty()) {
            candidates.add(new File(property.trim()));
        }
        String env = System.getenv(envName);
        if (env != null && !env.trim().isEmpty()) {
            candidates.add(new File(env.trim()));
        }
        candidates.add(new File("config/NEI/" + fileName));
        candidates.add(new File("config/notenoughitems/" + fileName));
        candidates.add(new File("config/" + fileName));
        if (bundledResourceRelativePath != null && !bundledResourceRelativePath.trim().isEmpty()) {
            candidates.add(new File(bundledResourceRelativePath.trim()));
        }
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private static BufferedReader openUtf8Reader(File file) throws IOException {
        return new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
    }

    private static String stripBom(String value) {
        return value == null ? "" : value.replaceFirst("^\\uFEFF", "");
    }

    private static String normalizeRuleToken(String value) {
        return (value == null ? "" : value).trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
    }

    private void rewriteBrowserAtlasPlacement(
            File rawDir,
            JsonObject placement,
            Set<String> copiedAssets) throws IOException {
        if (placement == null || !placement.has("atlasFile")) {
            return;
        }
        String atlasFile = stringAt(placement, "atlasFile");
        String rawAtlasPath = materializeBrowserAtlasAsset(rawDir, atlasFile);
        if (rawAtlasPath != null) {
            placement.addProperty("atlasFile", rawAtlasPath);
            copiedAssets.add(rawAtlasPath);
        }
    }

    private JsonObject objectAt(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return null;
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private String materializeBrowserAtlasAsset(File rawDir, String atlasFile) throws IOException {
        String normalized = normalizeRelativePath(atlasFile);
        if (normalized == null) {
            return null;
        }
        String rawRelative = "assets/textures/atlas-assets/" + stripCanonicalPrefix(normalized);
        File source = resolveRepositoryRelativeFile(normalized);
        if (source == null || !source.isFile()) {
            Logger.MOD.warn("Missing browser atlas asset for raw-export: " + normalized);
            return normalized;
        }
        copyIfPresent(source, new File(rawDir, rawRelative.replace('/', File.separatorChar)));
        return rawRelative;
    }

    private File resolveRepositoryRelativeFile(String relativePath) {
        File direct = new File(repositoryDirectory, relativePath.replace('/', File.separatorChar));
        if (direct.isFile()) {
            return direct;
        }
        String stripped = stripCanonicalPrefix(relativePath);
        File canonical = new File(new File(repositoryDirectory, "canonical"), stripped.replace('/', File.separatorChar));
        if (canonical.isFile()) {
            return canonical;
        }
        return direct;
    }

    private static String stripCanonicalPrefix(String relativePath) {
        return relativePath != null && relativePath.startsWith("canonical/")
                ? relativePath.substring("canonical/".length())
                : relativePath;
    }

    private static String normalizeRelativePath(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        String normalized = relativePath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() == 0
                || normalized.indexOf('\0') >= 0
                || normalized.contains("://")
                || normalized.startsWith("../")
                || normalized.contains("/../")
                || normalized.matches("^[A-Za-z]:/.*")) {
            return null;
        }
        return normalized;
    }

    private static JsonObject entityRow(Map<String, JsonObject> rows, String mobName) {
        JsonObject row = rows.get(mobName);
        if (row == null) {
            row = new JsonObject();
            row.addProperty("entityId", mobName);
            row.addProperty("mobName", mobName);
            rows.put(mobName, row);
        }
        return row;
    }

    private static JsonObject buildSpecialSlotStats(JsonObject recipe) {
        JsonObject stats = new JsonObject();
        addCount(stats, "itemInputCount", recipe, "itemInputs");
        addCount(stats, "itemOutputCount", recipe, "itemOutputs");
        addCount(stats, "fluidInputCount", recipe, "fluidInputs");
        addCount(stats, "fluidOutputCount", recipe, "fluidOutputs");
        addCount(stats, "layoutItemSlotCount", recipe, "layout.itemSlots");
        addCount(stats, "layoutFluidSlotCount", recipe, "layout.fluidSlots");
        return stats;
    }

    private static JsonObject buildSpecialPrimaryRefs(JsonObject recipe) {
        JsonObject refs = new JsonObject();
        addCollectedStrings(refs, "itemInputIds", recipe, "itemInputs", "variants", "itemId", 24);
        addCollectedStrings(refs, "itemOutputIds", recipe, "itemOutputs", null, "itemId", 24);
        addCollectedStrings(refs, "fluidInputIds", recipe, "fluidInputs", "variants", "fluidId", 24);
        addCollectedStrings(refs, "fluidOutputIds", recipe, "fluidOutputs", null, "fluidId", 24);
        return refs;
    }

    private static void addDomainFacts(JsonObject facts, String domainId, JsonObject recipe) {
        if ("gregtech".equals(domainId)) {
            copyFirstNumber(facts, "duration", recipe, "metadata.duration", "metadata.ticks");
            copyFirstNumber(facts, "voltage", recipe, "metadata.voltage");
            copyFirstNumber(facts, "amperage", recipe, "metadata.amperage");
            copyFirstNumber(facts, "totalEU", recipe, "metadata.totalEU");
            copyString(facts, "voltageTier", recipe, "metadata.voltageTier");
            copyElement(facts, "requiresCleanroom", recipe, "metadata.requiresCleanroom");
            copyElement(facts, "requiresLowGravity", recipe, "metadata.requiresLowGravity");
            copyElement(facts, "specialItems", recipe, "metadata.specialItems");
        } else if ("thaumcraft".equals(domainId)) {
            copyElement(facts, "aspects", recipe, "metadata.aspects");
            copyElement(facts, "aspects", recipe, "layout.bindings.aspects");
            copyElement(facts, "aspectItems", recipe, "metadata.aspectItems");
            copyElement(facts, "aspectItems", recipe, "layout.bindings.aspectItems");
            copyElement(facts, "research", recipe, "metadata.research");
            copyElement(facts, "research", recipe, "layout.bindings.research");
            copyElement(facts, "instability", recipe, "metadata.instability");
            copyElement(facts, "instability", recipe, "layout.bindings.instability");
            copyElement(facts, "centralItemId", recipe, "metadata.centralItemId");
            copyElement(facts, "centralItemId", recipe, "layout.bindings.centralItemId");
            copyElement(facts, "centerInputSlotIndex", recipe, "metadata.centerInputSlotIndex");
            copyElement(facts, "centerInputSlotIndex", recipe, "layout.bindings.centerInputSlotIndex");
            copyElement(facts, "componentSlotOrder", recipe, "metadata.componentSlotOrder");
            copyElement(facts, "componentSlotOrder", recipe, "layout.bindings.componentSlotOrder");
        } else if ("botania".equals(domainId)) {
            copyFirstNumber(facts, "manaCost", recipe, "metadata.manaCost", "layout.bindings.manaCost", "metadata.mana");
            copyFirstNumber(facts, "ticks", recipe, "metadata.ticks", "layout.bindings.ticks", "metadata.duration");
            copyElement(facts, "catalyst", recipe, "metadata.catalyst");
            copyElement(facts, "catalystItemId", recipe, "metadata.catalystItemId");
            copyString(facts, "recipeKind", recipe, "metadata.recipeKind");
            copyString(facts, "recipeKind", recipe, "metadata.specialRecipeType");
        } else if ("bloodmagic".equals(domainId)) {
            copyFirstNumber(facts, "bloodCost", recipe, "metadata.bloodCost", "layout.bindings.bloodCost", "metadata.lpCost", "layout.bindings.lpCost", "metadata.requiredLP", "metadata.lp");
            copyFirstNumber(facts, "lpCost", recipe, "metadata.lpCost", "metadata.requiredLP", "metadata.bloodCost", "layout.bindings.lpCost");
            copyFirstNumber(facts, "requiredLP", recipe, "metadata.requiredLP", "metadata.lpCost", "metadata.bloodCost", "layout.bindings.lpCost", "layout.bindings.bloodCost");
            copyFirstNumber(facts, "tier", recipe, "metadata.tier", "metadata.altarTier", "layout.bindings.tier");
            copyFirstNumber(facts, "altarTier", recipe, "metadata.altarTier", "metadata.tier", "layout.bindings.altarTier", "layout.bindings.tier");
            copyFirstNumber(facts, "consumptionRate", recipe, "metadata.consumptionRate", "layout.bindings.consumptionRate");
            copyFirstNumber(facts, "drainRate", recipe, "metadata.drainRate", "layout.bindings.drainRate");
            copyFirstNumber(facts, "tartaricCost", recipe, "metadata.tartaricCost", "layout.bindings.tartaricCost");
            copyElement(facts, "orb", recipe, "metadata.orb");
            copyElement(facts, "ritual", recipe, "metadata.ritual");
            copyElement(facts, "isWeakActivation", recipe, "metadata.isWeakActivation");
            copyElement(facts, "isWeakActivation", recipe, "layout.bindings.isWeakActivation");
        } else if ("forestry".equals(domainId)) {
            copyElement(facts, "beeSpecies", recipe, "metadata.beeSpecies");
            copyElement(facts, "species", recipe, "metadata.species");
            copyElement(facts, "species", recipe, "metadata.beeSpecies");
            copyElement(facts, "allele", recipe, "metadata.allele");
            copyElement(facts, "alleles", recipe, "metadata.alleles");
            copyElement(facts, "temperature", recipe, "metadata.temperature");
            copyElement(facts, "humidity", recipe, "metadata.humidity");
            copyElement(facts, "chance", recipe, "metadata.chance");
            copyElement(facts, "mutations", recipe, "metadata.mutations");
        } else if ("eec".equals(domainId)) {
            copyElement(facts, "entity", recipe, "metadata.entity");
            copyElement(facts, "entityId", recipe, "metadata.entityId");
            copyElement(facts, "entityId", recipe, "metadata.mobName");
            copyElement(facts, "entityId", recipe, "metadata.entityName");
            copyElement(facts, "entityName", recipe, "metadata.entityName");
            copyElement(facts, "mobName", recipe, "metadata.mobName");
            copyElement(facts, "entityHealth", recipe, "metadata.entityHealth");
            copyElement(facts, "entityHealth", recipe, "metadata.maxHealth");
            copyElement(facts, "maxHealth", recipe, "metadata.maxHealth");
            copyElement(facts, "drops", recipe, "metadata.drops");
            copyElement(facts, "fluidDrops", recipe, "metadata.fluidDrops");
            copyFirstNumber(facts, "normalOutputsCount", recipe, "metadata.normalOutputsCount");
            copyFirstNumber(facts, "rareOutputsCount", recipe, "metadata.rareOutputsCount");
            copyFirstNumber(facts, "additionalOutputsCount", recipe, "metadata.additionalOutputsCount");
            copyFirstNumber(facts, "infernalOutputsCount", recipe, "metadata.infernalOutputsCount");
            copyFirstNumber(facts, "outputCount", recipe, "metadata.outputCount");
            copyFirstNumber(facts, "eliteChance", recipe, "metadata.eliteChance");
            copyFirstNumber(facts, "ultraChance", recipe, "metadata.ultraChance");
            copyFirstNumber(facts, "infernoChance", recipe, "metadata.infernoChance");
            copyElement(facts, "modelRef", recipe, "metadata.modelRef");
            copyElement(facts, "previewImage", recipe, "metadata.previewImage");
        }
    }

    private static JsonObject buildSpecialDomainSummary(String domainId, JsonArray recipes) {
        JsonObject summary = new JsonObject();
        summary.addProperty("domain", domainId);
        summary.addProperty("recipeCount", recipes == null ? 0 : recipes.size());
        summary.add("families", topStrings(recipes, "family", 50));
        summary.add("recipeTypes", topStrings(recipes, "recipeType", 50));
        summary.add("sourcePlugins", topStrings(recipes, "sourcePlugin", 50));
        summary.add("machineIds", topStrings(recipes, "machine.machineId", 80));
        summary.add("machineNames", topStrings(recipes, "machine.displayName", 80));
        summary.add("sampleRecipeIds", sampleStrings(recipes, "recipeId", 50));
        return summary;
    }

    private static JsonArray topStrings(JsonArray rows, String dottedPath, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        if (rows != null) {
            for (JsonElement row : rows) {
                if (row == null || !row.isJsonObject()) {
                    continue;
                }
                String value = stringAt(row.getAsJsonObject(), dottedPath);
                if (value == null || value.trim().length() == 0) {
                    continue;
                }
                String normalized = value.trim();
                Integer current = counts.get(normalized);
                counts.put(normalized, current == null ? 1 : current + 1);
            }
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> left, Map.Entry<String, Integer> right) {
                int byCount = right.getValue().compareTo(left.getValue());
                return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
            }
        });
        JsonArray out = new JsonArray();
        int count = 0;
        for (Map.Entry<String, Integer> entry : entries) {
            if (count >= limit) {
                break;
            }
            JsonObject object = new JsonObject();
            object.addProperty("value", entry.getKey());
            object.addProperty("count", entry.getValue());
            out.add(object);
            count++;
        }
        return out;
    }

    private static JsonArray sampleStrings(JsonArray rows, String dottedPath, int limit) {
        JsonArray out = new JsonArray();
        Set<String> seen = new LinkedHashSet<String>();
        if (rows != null) {
            for (JsonElement row : rows) {
                if (out.size() >= limit || row == null || !row.isJsonObject()) {
                    continue;
                }
                String value = stringAt(row.getAsJsonObject(), dottedPath);
                if (value == null || value.trim().length() == 0 || seen.contains(value.trim())) {
                    continue;
                }
                seen.add(value.trim());
                out.add(new com.google.gson.JsonPrimitive(value.trim()));
            }
        }
        return out;
    }

    private static void copyString(JsonObject target, String to, JsonObject source, String dottedPath) {
        String value = stringAt(source, dottedPath);
        if (value != null && value.trim().length() > 0) {
            target.addProperty(to, value.trim());
        }
    }

    private static void copyElement(JsonObject target, String to, JsonObject source, String dottedPath) {
        JsonElement value = elementAt(source, dottedPath);
        if (value != null && !value.isJsonNull()) {
            target.add(to, cloneJson(value));
        }
    }

    private static void copyFirstNumber(JsonObject target, String to, JsonObject source, String... dottedPaths) {
        for (String dottedPath : dottedPaths) {
            JsonElement value = elementAt(source, dottedPath);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                continue;
            }
            try {
                target.add(to, cloneJson(value));
                return;
            } catch (Exception ignored) {
                // Try the next candidate.
            }
        }
    }

    private static JsonElement cloneJson(JsonElement value) {
        return value == null ? null : new JsonParser().parse(value.toString());
    }

    private static void addCount(JsonObject target, String to, JsonObject source, String dottedPath) {
        JsonElement value = elementAt(source, dottedPath);
        if (value != null && value.isJsonArray()) {
            target.addProperty(to, value.getAsJsonArray().size());
        }
    }

    private static void addCollectedStrings(
            JsonObject target,
            String to,
            JsonObject source,
            String arrayPath,
            String nestedArrayName,
            String valueKey,
            int limit) {
        JsonElement value = elementAt(source, arrayPath);
        if (value == null || !value.isJsonArray()) {
            return;
        }
        JsonArray out = new JsonArray();
        Set<String> seen = new LinkedHashSet<String>();
        for (JsonElement row : value.getAsJsonArray()) {
            if (out.size() >= limit || row == null || !row.isJsonObject()) {
                continue;
            }
            if (nestedArrayName == null) {
                addStringIfPresent(out, seen, row.getAsJsonObject().get(valueKey));
            } else {
                JsonElement nested = row.getAsJsonObject().get(nestedArrayName);
                if (nested == null || !nested.isJsonArray()) {
                    continue;
                }
                for (JsonElement nestedRow : nested.getAsJsonArray()) {
                    if (out.size() >= limit || nestedRow == null || !nestedRow.isJsonObject()) {
                        continue;
                    }
                    addStringIfPresent(out, seen, nestedRow.getAsJsonObject().get(valueKey));
                }
            }
        }
        if (out.size() > 0) {
            target.add(to, out);
        }
    }

    private static void addStringIfPresent(JsonArray target, Set<String> seen, JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return;
        }
        try {
            String text = value.getAsString();
            if (text != null && text.trim().length() > 0 && !seen.contains(text.trim())) {
                seen.add(text.trim());
                target.add(new com.google.gson.JsonPrimitive(text.trim()));
            }
        } catch (Exception ignored) {
            // Ignore non-string primitives.
        }
    }

    private static JsonElement elementAt(JsonObject object, String dottedPath) {
        if (dottedPath == null || dottedPath.length() == 0) {
            return object;
        }
        JsonElement current = object;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static String recipeDescriptor(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return "";
        }
        JsonObject recipe = element.getAsJsonObject();
        StringBuilder builder = new StringBuilder();
        appendDescriptor(builder, stringAt(recipe, "metadata.handlerId"));
        appendDescriptor(builder, stringAt(recipe, "metadata.handlerName"));
        appendDescriptor(builder, stringAt(recipe, "additionalData.handlerId"));
        appendDescriptor(builder, stringAt(recipe, "additionalData.handlerName"));
        appendDescriptor(builder, stringAt(recipe, "machine.machineId"));
        appendDescriptor(builder, stringAt(recipe, "machine.displayName"));
        appendDescriptor(builder, stringAt(recipe, "family"));
        appendDescriptor(builder, stringAt(recipe, "sourcePlugin"));
        appendDescriptor(builder, stringAt(recipe, "recipeType"));
        appendDescriptor(builder, stringAt(recipe, "displayName"));
        appendDescriptor(builder, stringAt(recipe, "category"));
        return builder.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static void appendDescriptor(StringBuilder builder, String value) {
        if (value != null && value.trim().length() > 0) {
            builder.append(' ').append(value.trim());
        }
    }

    private static boolean matchesAny(String descriptor, String pipeSeparatedNeedles) {
        if (descriptor == null || descriptor.length() == 0) {
            return false;
        }
        for (String needle : pipeSeparatedNeedles.split("\\|")) {
            String normalized = needle.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.length() > 0 && descriptor.contains(normalized)) {
                return true;
            }
        }
        return false;
    }
    private static String inferRecipeHandlerId(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return "unknown";
        }
        JsonObject recipe = element.getAsJsonObject();
        String value = firstNonBlank(
                stringAt(recipe, "metadata.handlerId"),
                stringAt(recipe, "metadata.handlerName"),
                stringAt(recipe, "additionalData.handlerId"),
                stringAt(recipe, "additionalData.handlerName"),
                stringAt(recipe, "machine.machineId"),
                stringAt(recipe, "machine.displayName"),
                stringAt(recipe, "family"),
                stringAt(recipe, "sourcePlugin"),
                stringAt(recipe, "recipeType"),
                stringAt(recipe, "displayName"));
        return value == null ? "unknown" : value;
    }

    private static String stringAt(JsonObject object, String dottedPath) {
        JsonElement current = object;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getAsJsonObject().get(part);
        }
        if (current == null || current.isJsonNull()) {
            return null;
        }
        try {
            return current.isJsonPrimitive() ? current.getAsString() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return null;
    }

    private static String uniqueShardFileName(String handlerId, Set<String> usedFileNames) {
        String base = safeShardFileName(handlerId);
        String candidate = base + ".jsonl.gz";
        int suffix = 2;
        while (usedFileNames.contains(candidate)) {
            candidate = base + "-" + suffix + ".jsonl.gz";
            suffix++;
        }
        usedFileNames.add(candidate);
        return candidate;
    }

    private static String safeShardFileName(String handlerId) {
        String normalized = handlerId == null ? "unknown" : handlerId.trim().toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return normalized.length() == 0 ? "unknown" : normalized;
    }

    private static JsonObject toRenderAssetRow(CanonicalRenderAsset asset) {
        JsonObject row = new JsonObject();
        add(row, "assetId", asset.assetId);
        add(row, "variantKey", asset.variantKey);
        add(row, "family", asset.family);
        add(row, "sourceType", asset.sourceType);
        add(row, "contentHash", asset.contentHash);
        add(row, "mode", asset.mode);
        add(row, "renderMode", asset.renderMode);
        add(row, "animationMode", asset.animationMode);
        add(row, "playbackHint", asset.playbackHint);
        add(row, "primaryArtifact", asset.primaryArtifact);
        add(row, "staticFile", asset.staticFile);
        add(row, "framePattern", asset.framePattern);
        add(row, "frameCount", asset.frameCount);
        add(row, "configuredFrameCount", asset.configuredFrameCount);
        add(row, "capturedFrameCount", asset.capturedFrameCount);
        add(row, "frameDurationMs", asset.frameDurationMs);
        add(row, "frameDurationSource", asset.frameDurationSource);
        add(row, "frames", asset.frames);
        add(row, "timeline", asset.timeline);
        add(row, "loopMode", asset.loopMode);
        add(row, "loop", asset.loop);
        add(row, "baseSize", asset.baseSize);
        add(row, "rect", asset.rect);
        add(row, "atlasGroup", asset.atlasGroup);
        add(row, "atlasFile", asset.atlasFile);
        add(row, "atlasTexture", asset.atlasTexture);
        add(row, "atlasExportFile", asset.atlasExportFile);
        add(row, "spriteMetadataFile", asset.spriteMetadataFile);
        add(row, "nativeSpriteAtlasFile", asset.nativeSpriteAtlasFile);
        return row;
    }

    private static boolean isAnimated(CanonicalRenderAsset asset) {
        return (asset.frameCount != null && asset.frameCount > 1)
                || (asset.capturedFrameCount != null && asset.capturedFrameCount > 1)
                || (asset.configuredFrameCount != null && asset.configuredFrameCount > 1)
                || containsIgnoreCase(asset.mode, "animated")
                || containsIgnoreCase(asset.animationMode, "animated")
                || asset.framePattern != null;
    }


    private static boolean isNativeSpriteAnimation(CanonicalRenderAsset asset) {
        return "native_sprite_animation".equals(asset.mode)
                || "native_sprite".equals(asset.animationMode)
                || "native_sprite_aux".equals(asset.animationMode)
                || asset.spriteMetadataFile != null;
    }

    private static boolean isNativeSpriteAnimation(JsonObject asset) {
        return "native_sprite_animation".equals(stringValue(asset, "mode"))
                || "native_sprite".equals(stringValue(asset, "animationMode"))
                || "native_sprite_aux".equals(stringValue(asset, "animationMode"))
                || stringValue(asset, "spriteMetadataFile") != null;
    }

    private static boolean isRenderedGifAnimation(CanonicalRenderAsset asset) {
        return containsIgnoreCase(asset.animationMode, "gif")
                || containsIgnoreCase(asset.mode, "gif")
                || containsIgnoreCase(asset.primaryArtifact, ".gif")
                || containsIgnoreCase(asset.staticFile, ".gif");
    }

    private static boolean isRenderedGifAnimation(JsonObject asset) {
        return containsIgnoreCase(stringValue(asset, "animationMode"), "gif")
                || containsIgnoreCase(stringValue(asset, "mode"), "gif")
                || containsIgnoreCase(stringValue(asset, "primaryArtifact"), ".gif")
                || containsIgnoreCase(stringValue(asset, "staticFile"), ".gif");
    }
    private static boolean isAnimated(JsonObject asset) {
        return intValue(asset, "frameCount") > 1
                || intValue(asset, "capturedFrameCount") > 1
                || intValue(asset, "configuredFrameCount") > 1
                || containsIgnoreCase(stringValue(asset, "mode"), "animated")
                || containsIgnoreCase(stringValue(asset, "animationMode"), "animated")
                || stringValue(asset, "framePattern") != null;
    }

    private long countQuery(String query) {
        if (entityManager == null) {
            return -1L;
        }
        try {
            Object value = entityManager.createQuery(query).getSingleResult();
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to calculate raw-export count for query: " + query, e);
        }
        return -1L;
    }

    private static FileRef fileRef(String logicalName, String path, String kind) {
        FileRef ref = new FileRef();
        ref.logicalName = logicalName;
        ref.path = path;
        ref.kind = kind;
        return ref;
    }

    private static long writeArrayAsJsonl(JsonArray array, File out) throws IOException {
        long count = 0L;
        Gson gson = new GsonBuilder().serializeNulls().create();
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (OutputStreamWriter writer = createUtf8Writer(out)) {
            if (array != null) {
                for (JsonElement element : array) {
                    gson.toJson(element, writer);
                    writer.write('\n');
                    count++;
                }
            }
        }
        return count;
    }

    private RepositoryStreamResult streamRepositoryFacts(File rawDir) throws IOException {
        RepositoryStreamResult result = streamDatabaseRepositoryFacts(rawDir);
        Logger.MOD.info(
                "Raw-export facts streamed directly from database: items={}, fluids={}, recipes={}",
                result.items,
                result.fluids,
                result.recipes);
        return result;
    }

    private RepositoryStreamResult streamDatabaseRepositoryFacts(File rawDir) throws IOException {
        createEmptyJsonl(new File(rawDir, "facts/items.jsonl.gz"));
        createEmptyJsonl(new File(rawDir, "facts/fluids.jsonl.gz"));

        RepositoryStreamResult result = new RepositoryStreamResult();
        Gson gson = new GsonBuilder().serializeNulls().create();
        Map<String, RecipeShardState> shards = new LinkedHashMap<String, RecipeShardState>();
        Set<String> usedShardFileNames = new LinkedHashSet<String>();
        Map<String, SpecialDomainStreamState> domains = createSpecialDomainStreamStates(rawDir);
        JsonlWriter itemFacts = null;
        JsonlWriter fluidFacts = null;
        try {
            itemFacts = new JsonlWriter(new File(rawDir, "facts/items.jsonl.gz"), gson);
            fluidFacts = new JsonlWriter(new File(rawDir, "facts/fluids.jsonl.gz"), gson);

            result.items = streamDatabaseItems(itemFacts, gson);
            result.fluids = streamDatabaseFluids(fluidFacts, gson);
            result.recipes =
                    streamDatabaseRecipes(
                            shards,
                            usedShardFileNames,
                            domains,
                            rawDir,
                            gson);
        } finally {
            closeQuietly(itemFacts);
            closeQuietly(fluidFacts);
            for (RecipeShardState shard : shards.values()) {
                closeQuietly(shard.writer);
            }
            for (SpecialDomainStreamState domain : domains.values()) {
                closeQuietly(domain.payloadWriter);
            }
        }

        writeRecipeIndex(rawDir, shards, result.recipes);
        writeSpecialIndexes(rawDir, domains);
        return result;
    }

    private long streamDatabaseItems(JsonlWriter primary, Gson gson) throws IOException {
        long written = 0L;
        int offset = 0;
        while (true) {
            TypedQuery<com.github.dcysteine.nesql.sql.base.item.Item> query = entityManager.createQuery(
                    "SELECT i FROM Item i ORDER BY i.id",
                    com.github.dcysteine.nesql.sql.base.item.Item.class);
            List<com.github.dcysteine.nesql.sql.base.item.Item> items = query
                    .setFirstResult(offset)
                    .setMaxResults(ITEM_BATCH_SIZE)
                    .getResultList();
            if (items.isEmpty()) {
                break;
            }
            for (com.github.dcysteine.nesql.sql.base.item.Item item : items) {
                CanonicalItem mapped = CanonicalExportMapper.mapItem(item);
                JsonElement element = gson.toJsonTree(mapped, CanonicalItem.class);
                primary.write(element);
                written++;
            }
            offset += items.size();
            entityManager.clear();
        }
        return written;
    }

    private long streamDatabaseFluids(JsonlWriter primary, Gson gson) throws IOException {
        long written = 0L;
        int offset = 0;
        while (true) {
            TypedQuery<Fluid> query = entityManager.createQuery(
                    "SELECT f FROM Fluid f ORDER BY f.id",
                    Fluid.class);
            List<Fluid> fluids = query
                    .setFirstResult(offset)
                    .setMaxResults(FLUID_BATCH_SIZE)
                    .getResultList();
            if (fluids.isEmpty()) {
                break;
            }
            for (Fluid fluid : fluids) {
                CanonicalFluid mapped = CanonicalExportMapper.mapFluid(fluid);
                JsonElement element = gson.toJsonTree(mapped, CanonicalFluid.class);
                primary.write(element);
                written++;
            }
            offset += fluids.size();
            entityManager.clear();
        }
        return written;
    }

    private long streamDatabaseRecipes(
            Map<String, RecipeShardState> shards,
            Set<String> usedShardFileNames,
            Map<String, SpecialDomainStreamState> domains,
            File rawDir,
            Gson gson) throws IOException {
        long written = 0L;
        int offset = 0;
        while (true) {
            TypedQuery<Recipe> query = entityManager.createQuery(
                    "SELECT r FROM Recipe r LEFT JOIN FETCH r.recipeType ORDER BY r.id",
                    Recipe.class);
            List<Recipe> recipes = query
                    .setFirstResult(offset)
                    .setMaxResults(RECIPE_BATCH_SIZE)
                    .getResultList();
            if (recipes.isEmpty()) {
                break;
            }
            Map<String, GregTechRecipe> gtByRecipeId = loadGregTechRecipeBatch(recipes);
            for (Recipe recipe : recipes) {
                CanonicalRecipe mapped = CanonicalExportMapper.mapRecipe(recipe, gtByRecipeId.get(recipe.getId()));
                JsonElement element = gson.toJsonTree(mapped, CanonicalRecipe.class);
                writeRecipeElement(element, shards, usedShardFileNames, domains, rawDir, gson);
                written++;
            }
            offset += recipes.size();
            gtByRecipeId.clear();
            entityManager.clear();
        }
        return written;
    }

    private Map<String, GregTechRecipe> loadGregTechRecipeBatch(List<Recipe> recipes) {
        List<String> recipeIds = new ArrayList<String>(recipes.size());
        for (Recipe recipe : recipes) {
            recipeIds.add(recipe.getId());
        }
        if (recipeIds.isEmpty()) {
            return new LinkedHashMap<String, GregTechRecipe>();
        }
        TypedQuery<GregTechRecipe> query = entityManager.createQuery(
                "SELECT DISTINCT gtr FROM com.github.dcysteine.nesql.sql.gregtech.GregTechRecipe gtr "
                        + "LEFT JOIN FETCH gtr.recipe "
                        + "WHERE gtr.recipe.id IN :recipeIds",
                GregTechRecipe.class);
        List<GregTechRecipe> gtRecipes = query
                .setParameter("recipeIds", recipeIds)
                .getResultList();
        Map<String, GregTechRecipe> gtByRecipeId = new LinkedHashMap<String, GregTechRecipe>();
        for (GregTechRecipe gtRecipe : gtRecipes) {
            if (gtRecipe.getRecipe() != null) {
                gtByRecipeId.put(gtRecipe.getRecipe().getId(), gtRecipe);
            }
        }
        return gtByRecipeId;
    }

    private static long streamRecipes(
            com.google.gson.stream.JsonReader reader,
            Map<String, RecipeShardState> shards,
            Set<String> usedShardFileNames,
            Map<String, SpecialDomainStreamState> domains,
            File rawDir,
            Gson gson) throws IOException {
        long count = 0L;
        reader.beginArray();
        while (reader.hasNext()) {
            JsonElement element = gson.fromJson(reader, JsonElement.class);
            writeRecipeElement(
                    element,
                    shards,
                    usedShardFileNames,
                    domains,
                    rawDir,
                    gson);
            count++;
        }
        reader.endArray();
        return count;
    }

    private static void writeRecipeElement(
            JsonElement element,
            Map<String, RecipeShardState> shards,
            Set<String> usedShardFileNames,
            Map<String, SpecialDomainStreamState> domains,
            File rawDir,
            Gson gson) throws IOException {
        String handlerId = inferRecipeHandlerId(element);
        RecipeShardState shard = shards.get(handlerId);
        if (shard == null) {
            String fileName = uniqueShardFileName(handlerId, usedShardFileNames);
            String path = "facts/recipes/by-handler/" + fileName;
            shard = new RecipeShardState(handlerId, path, new JsonlWriter(new File(rawDir, path), gson));
            shards.put(handlerId, shard);
        }
        shard.writer.write(element);
        shard.recipeCount++;

        if (element != null && element.isJsonObject()) {
            JsonObject recipe = element.getAsJsonObject();
            String descriptor = recipeDescriptor(element);
            for (SpecialDomainStreamState domain : domains.values()) {
                if (matchesAny(descriptor, domain.needles)) {
                    JsonObject payload = buildSpecialDomainPayload(domain.domainId, recipe, domain.payloadOrdinal++);
                    domain.payloadWriter.write(payload);
                    domain.accept(recipe);
                }
            }
        }
    }

    private static void writeRecipeIndex(File rawDir, Map<String, RecipeShardState> shardsByHandler, long recipeCount)
            throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        JsonArray shards = new JsonArray();
        for (RecipeShardState entry : shardsByHandler.values()) {
            JsonObject shard = new JsonObject();
            shard.addProperty("handlerId", entry.handlerId);
            shard.addProperty("path", entry.path);
            shard.addProperty("recipeCount", entry.recipeCount);
            shards.add(shard);
        }

        JsonObject index = new JsonObject();
        index.addProperty("schemaVersion", SCHEMA_VERSION + "/recipe-index");
        index.addProperty("strategy", "by-handler");
        index.addProperty("recipeCount", recipeCount);
        index.addProperty("shardCount", shards.size());
        index.add("shards", shards);
        writeJson(gson, new File(rawDir, "facts/recipes/index.json"), index);
    }

    private static void writeSpecialIndexes(File rawDir, Map<String, SpecialDomainStreamState> domains) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        JsonArray domainIndex = new JsonArray();
        for (SpecialDomainStreamState domain : domains.values()) {
            JsonObject summary = domain.summary();
            JsonObject index = new JsonObject();
            index.addProperty("schemaVersion", SCHEMA_VERSION + "/special-domain");
            index.addProperty("domain", domain.domainId);
            index.addProperty("recipeCount", domain.recipeCount);
            index.addProperty("payloadCount", domain.payloadCount);
            index.addProperty("payloads", "special/" + domain.domainId + "/payloads.jsonl.gz");
            index.addProperty("summary", "special/" + domain.domainId + "/summary.json");
            index.add("stats", summary);
            File domainDir = new File(rawDir, "special/" + domain.domainId);
            writeJson(gson, new File(domainDir, "index.json"), index);
            writeJson(gson, new File(domainDir, "summary.json"), summary);

            JsonObject entry = new JsonObject();
            entry.addProperty("domain", domain.domainId);
            entry.addProperty("recipeCount", domain.recipeCount);
            entry.addProperty("payloadCount", domain.payloadCount);
            entry.addProperty("index", "special/" + domain.domainId + "/index.json");
            entry.addProperty("payloads", "special/" + domain.domainId + "/payloads.jsonl.gz");
            entry.addProperty("summary", "special/" + domain.domainId + "/summary.json");
            entry.add("stats", summary);
            domainIndex.add(entry);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION + "/special-index");
        root.add("domains", domainIndex);
        writeJson(gson, new File(rawDir, "special/index.json"), root);
    }

    private static Map<String, SpecialDomainStreamState> createSpecialDomainStreamStates(File rawDir) throws IOException {
        Gson gson = new GsonBuilder().serializeNulls().create();
        Map<String, SpecialDomainStreamState> states = new LinkedHashMap<String, SpecialDomainStreamState>();
        for (String[] spec : specialDomainSpecs()) {
            File domainDir = new File(rawDir, "special/" + spec[0]);
            ensureDirectory(domainDir);
            states.put(spec[0], new SpecialDomainStreamState(
                    spec[0],
                    spec[1],
                    new JsonlWriter(new File(domainDir, "payloads.jsonl.gz"), gson)));
        }
        return states;
    }

    private static String[][] specialDomainSpecs() {
        return new String[][] {
                {"gregtech", "gregtech|gt_|gt |assembler|assembly line|chemical reactor|blast furnace|macerator|fluid solidifier|alloy smelter|research station"},
                {"thaumcraft", "thaumcraft|thaumic|arcane|infusion|crucible|aspect|research"},
                {"botania", "botania|mana pool|rune altar|runic altar|terra plate|pure daisy|elven trade|petal apothecary"},
                {"bloodmagic", "bloodmagic|blood magic|blood altar|alchemy array|binding ritual|blood orb|lp"},
                {"forestry", "forestry|bee|alveary|centrifuge|squeezer|carpenter"},
                {"eec", "extreme entity crusher|industrial slaughter|infernal drops|mobsinfo|kubatech|entity crusher"}
        };
    }

    private static JsonObject buildSpecialDomainPayload(String domainId, JsonObject recipe, int ordinal) {
        JsonObject payload = new JsonObject();
        payload.addProperty("domain", domainId);
        payload.addProperty("ordinal", ordinal);
        copyString(payload, "recipeId", recipe, "recipeId");
        copyString(payload, "family", recipe, "family");
        copyString(payload, "sourcePlugin", recipe, "sourcePlugin");
        copyString(payload, "sourceMod", recipe, "sourceMod");
        copyString(payload, "recipeType", recipe, "recipeType");
        copyString(payload, "displayName", recipe, "displayName");
        copyString(payload, "handlerId", recipe, "metadata.handlerId");
        copyString(payload, "handlerName", recipe, "metadata.handlerName");
        copyString(payload, "handlerClass", recipe, "metadata.handlerClass");
        copyString(payload, "machineId", recipe, "machine.machineId");
        copyString(payload, "machineName", recipe, "machine.displayName");
        copyString(payload, "layoutClass", recipe, "layout.layoutClass");

        JsonObject slotStats = buildSpecialSlotStats(recipe);
        if (slotStats.entrySet().size() > 0) {
            payload.add("slotStats", slotStats);
        }
        JsonObject primaryRefs = buildSpecialPrimaryRefs(recipe);
        if (primaryRefs.entrySet().size() > 0) {
            payload.add("primaryRefs", primaryRefs);
        }
        JsonObject facts = new JsonObject();
        addDomainFacts(facts, domainId, recipe);
        if (facts.entrySet().size() > 0) {
            payload.add("domainFacts", facts);
        }
        return payload;
    }

    private static void increment(Map<String, Integer> counts, String value) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        String normalized = value.trim();
        Integer current = counts.get(normalized);
        counts.put(normalized, current == null ? 1 : current + 1);
    }

    private static JsonArray topStrings(Map<String, Integer> counts, int limit) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> left, Map.Entry<String, Integer> right) {
                int byCount = right.getValue().compareTo(left.getValue());
                return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
            }
        });
        JsonArray out = new JsonArray();
        int count = 0;
        for (Map.Entry<String, Integer> entry : entries) {
            if (count >= limit) {
                break;
            }
            JsonObject object = new JsonObject();
            object.addProperty("value", entry.getKey());
            object.addProperty("count", entry.getValue());
            out.add(object);
            count++;
        }
        return out;
    }

    private static JsonArray sampleStrings(Set<String> samples) {
        JsonArray out = new JsonArray();
        for (String sample : samples) {
            out.add(new com.google.gson.JsonPrimitive(sample));
        }
        return out;
    }

    private static void closeQuietly(JsonlWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
            // best-effort cleanup after export failure
        }
    }

    private static final class JsonlWriter implements java.io.Closeable {
        private final Gson gson;
        private final Writer writer;

        JsonlWriter(File out, Gson gson) throws IOException {
            this.gson = gson;
            File parent = out.getParentFile();
            if (parent != null) {
                ensureDirectory(parent);
            }
            this.writer = createUtf8Writer(out);
        }

        void write(JsonElement element) throws IOException {
            gson.toJson(element, writer);
            writer.write('\n');
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }
    }

    private static final class RepositoryStreamResult {
        long items;
        long fluids;
        long recipes;
    }

    private static final class RecipeShardState {
        final String handlerId;
        final String path;
        final JsonlWriter writer;
        long recipeCount;

        RecipeShardState(String handlerId, String path, JsonlWriter writer) {
            this.handlerId = handlerId;
            this.path = path;
            this.writer = writer;
        }
    }

    private static final class SpecialDomainStreamState {
        final String domainId;
        final String needles;
        final JsonlWriter payloadWriter;
        final Map<String, Integer> families = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> recipeTypes = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> sourcePlugins = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> machineIds = new LinkedHashMap<String, Integer>();
        final Map<String, Integer> machineNames = new LinkedHashMap<String, Integer>();
        final Set<String> sampleRecipeIds = new LinkedHashSet<String>();
        long recipeCount;
        long payloadCount;
        int payloadOrdinal;

        SpecialDomainStreamState(String domainId, String needles, JsonlWriter payloadWriter) {
            this.domainId = domainId;
            this.needles = needles;
            this.payloadWriter = payloadWriter;
        }

        void accept(JsonObject recipe) {
            recipeCount++;
            payloadCount++;
            increment(families, stringAt(recipe, "family"));
            increment(recipeTypes, stringAt(recipe, "recipeType"));
            increment(sourcePlugins, stringAt(recipe, "sourcePlugin"));
            increment(machineIds, stringAt(recipe, "machine.machineId"));
            increment(machineNames, stringAt(recipe, "machine.displayName"));
            String recipeId = stringAt(recipe, "recipeId");
            if (recipeId != null && recipeId.trim().length() > 0 && sampleRecipeIds.size() < 50) {
                sampleRecipeIds.add(recipeId.trim());
            }
        }

        JsonObject summary() {
            JsonObject summary = new JsonObject();
            summary.addProperty("domain", domainId);
            summary.addProperty("recipeCount", recipeCount);
            summary.add("families", topStrings(families, 50));
            summary.add("recipeTypes", topStrings(recipeTypes, 50));
            summary.add("sourcePlugins", topStrings(sourcePlugins, 50));
            summary.add("machineIds", topStrings(machineIds, 80));
            summary.add("machineNames", topStrings(machineNames, 80));
            summary.add("sampleRecipeIds", sampleStrings(sampleRecipeIds));
            return summary;
        }
    }
    private static void createEmptyJsonl(File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (Writer ignored = createUtf8Writer(out)) {
            // Empty JSONL remains valid when a source is unavailable for this run.
        }
    }

    private static void createEmptyJsonlIfMissing(File out) throws IOException {
        if (out.exists()) {
            return;
        }
        createEmptyJsonl(out);
    }

    private static JsonObject readObject(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
             java.io.InputStreamReader reader = new java.io.InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read raw-export source file: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    private static void add(JsonObject object, String key, String value) {
        if (value != null) {
            object.addProperty(key, value);
        }
    }

    private static void add(JsonObject object, String key, Number value) {
        if (value != null) {
            object.addProperty(key, value);
        }
    }


    private static void add(JsonObject object, String key, Boolean value) {
        if (value != null) {
            object.addProperty(key, value);
        }
    }

    private static void add(JsonObject object, String key, Object value) {
        if (value != null) {
            object.add(key, new GsonBuilder().serializeNulls().create().toJsonTree(value));
        }
    }
    private static boolean containsIgnoreCase(String value, String token) {
        return value != null && token != null && value.toLowerCase(java.util.Locale.ROOT).contains(token.toLowerCase(java.util.Locale.ROOT));
    }

    private static int intValue(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? 0 : element.getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String stringValue(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? null : element.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long countFiles(File root, String requiredName) {
        if (root == null || !root.exists()) {
            return 0L;
        }
        if (root.isFile()) {
            return requiredName == null || requiredName.equals(root.getName()) ? 1L : 0L;
        }
        long count = 0L;
        File[] children = root.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            count += countFiles(child, requiredName);
        }
        return count;
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static void copyIfPresent(File source, File target) throws IOException {
        if (source == null || !source.exists() || !source.isFile()) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        byte[] buffer = new byte[1024 * 1024];
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) >= 0) {
                if (read > 0) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    private static void writeJson(Gson gson, File out, Object value) throws IOException {
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (FileOutputStream fos = new FileOutputStream(out);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(value, writer);
        }
    }

    private static void writeSizeReport(Gson gson, File rawDir) throws IOException {
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", SCHEMA_VERSION + "/size-report");
        report.addProperty("generatedAt", utcNow());
        report.addProperty("strategy", "raw-export-only");
        report.addProperty("totalBytes", directorySize(rawDir));

        JsonArray prohibited = new JsonArray();
        addProhibitedFile(prohibited, rawDir, "recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "items.jsonl");
        addProhibitedFile(prohibited, rawDir, "fluids.jsonl");
        addProhibitedFile(prohibited, rawDir, "entities.jsonl");
        addProhibitedFile(prohibited, rawDir, "facts/items.jsonl");
        addProhibitedFile(prohibited, rawDir, "facts/fluids.jsonl");
        addProhibitedFile(prohibited, rawDir, "facts/recipes/all.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/gregtech/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/thaumcraft/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/botania/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/bloodmagic/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/forestry/recipes.jsonl");
        addProhibitedFile(prohibited, rawDir, "special/eec/recipes.jsonl");
        report.add("prohibitedOutputs", prohibited);
        report.addProperty("status", prohibited.size() == 0 ? "pass" : "fail");
        writeJson(gson, new File(rawDir, "validation/size_report.json"), report);
    }

    private static void purgeLegacyRawExportOutputs(File rawDir) throws IOException {
        deleteIfExists(new File(rawDir, "recipes.jsonl"));
        deleteIfExists(new File(rawDir, "items.jsonl"));
        deleteIfExists(new File(rawDir, "fluids.jsonl"));
        deleteIfExists(new File(rawDir, "entities.jsonl"));
        deleteIfExists(new File(rawDir, "facts/items.jsonl"));
        deleteIfExists(new File(rawDir, "facts/fluids.jsonl"));
        deleteIfExists(new File(rawDir, "facts/recipes/all.jsonl"));
        for (String[] spec : specialDomainSpecs()) {
            deleteIfExists(new File(rawDir, "special/" + spec[0] + "/recipes.jsonl"));
        }
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteIfExists(child);
                }
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Failed to delete legacy raw-export output: " + file.getAbsolutePath());
        }
    }

    private static void addProhibitedFile(JsonArray out, File rawDir, String relativePath) {
        File file = new File(rawDir, relativePath.replace('/', File.separatorChar));
        if (!file.exists()) {
            return;
        }
        JsonObject entry = new JsonObject();
        entry.addProperty("path", relativePath);
        entry.addProperty("bytes", file.isFile() ? file.length() : directorySize(file));
        out.add(entry);
    }

    private static long directorySize(File file) {
        if (file == null || !file.exists()) {
            return 0L;
        }
        if (file.isFile()) {
            return file.length();
        }
        long total = 0L;
        File[] children = file.listFiles();
        if (children == null) {
            return 0L;
        }
        for (File child : children) {
            total += directorySize(child);
        }
        return total;
    }

    private static OutputStreamWriter createUtf8Writer(File out) throws IOException {
        FileOutputStream fos = new FileOutputStream(out);
        if (out.getName().endsWith(".gz")) {
            return new OutputStreamWriter(new GZIPOutputStream(fos), StandardCharsets.UTF_8);
        }
        return new OutputStreamWriter(fos, StandardCharsets.UTF_8);
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static final class RawExportManifest {
        String schemaVersion;
        String generatedAt;
        String repositoryName;
        String profile;
        String selection;
        String status;
        RawExportCounts counts;
        List<String> notes = new ArrayList<String>();
        List<String> capabilities = new ArrayList<String>();
        Map<String, String> files = new LinkedHashMap<String, String>();
        List<FileRef> compatibilityFiles = new ArrayList<FileRef>();
    }

    private static final class RawExportReport {
        String schemaVersion;
        String generatedAt;
        String profile;
        String selection;
        RawExportCounts counts;
        RawExportValidation validation = new RawExportValidation();
        NeiBrowserContract neiBrowserContract;
    }

    private static final class RawExportCounts {
        long items;
        long fluids;
        long recipes;
        long recipeTypes;
        long renderAssets;
        long itemModFiles;
        long recipeModFiles;
        long canonicalFiles;
        long rawItems;
        long rawFluids;
        long rawRecipes;
        long rawGroups;
        long rawNeiOrderEntries;
        long neiRuntimePanelItems;
        long neiExportOnlyItems;
        long neiBrowserItems;
        long neiDefaultEntries;
        long neiFallbackGroups;
        long neiNativeGroups;
        long neiSyntheticGroups;
        long neiGuidFilterRules;
        long neiHiddenItemRules;
        long neiHiddenItems;
        long neiRepresentativeMismatches;
        long neiHandlers;
        long neiHandlerLayouts;
        long rawTextures;
        long rawAnimations;
        long rawEntities;
        long rawBrowserAtlasAssets;
        long renderBackendFacts;
        long renderTextureSprites;
        long renderItemRenderers;
        long semanticTotalItems;
        long semanticTaggedItems;
        long semanticClassifiedTaggedItems;
        long semanticUnclassifiedTaggedItems;
        long semanticEstimatedPublicItems;
        long semanticFamilyCount;
        long semanticItems;
        long semanticVariants;
        long semanticPayloads;
        long semanticIdentityMapRows;
    }

    private static final class RawFactCounts {
        long items;
        long fluids;
        long recipes;
        long groups;
        long neiOrderEntries;
        long neiRuntimePanelItems;
        long neiExportOnlyItems;
        long neiBrowserItems;
        long neiDefaultEntries;
        long neiFallbackGroups;
        long neiNativeGroups;
        long neiSyntheticGroups;
        long neiGuidFilterRules;
        long neiHiddenItemRules;
        long neiHiddenItems;
        long neiRepresentativeMismatches;
        long neiHandlers;
        long neiHandlerLayouts;
        long textures;
        long animations;
        long entities;
        long browserAtlasAssets;
        long renderBackendFacts;
        long renderTextureSprites;
        long renderItemRenderers;
        NeiBrowserContract neiBrowserContract;
    }

    private static final class HandlerMetadataCounts {
        long handlers;
        long layouts;
    }

    private static final class NeiBrowserContract {
        String schemaVersion;
        String generatedAt;
        String status;
        String summary;
        boolean neiRuntimeSnapshot;
        String orderSource;
        String groupingSource;
        String guidFiltersSource;
        String hiddenItemsSource;
        long guidFilterRuleCount;
        long hiddenItemRuleCount;
        long hiddenItemCount;
        long neiRuntimePanelItemCount;
        long exportOnlyItemCount;
        long browserItemCount;
        long runtimeToBrowserDelta;
        long groupCount;
        long nativeGroupCount;
        long fallbackGroupCount;
        long syntheticGroupCount;
        long defaultEntryCount;
        long groupedMemberCount;
        long ungroupedBrowserItemCount;
        long missingRepresentativeCount;
        long representativeMismatchCount;
        List<BrowserContractMismatch> representativeMismatchSamples = new ArrayList<BrowserContractMismatch>();
    }

    private static final class BrowserContractMismatch {
        String groupKey;
        String groupLabel;
        String representativeItemId;
        List<String> firstMemberItemIds = new ArrayList<String>();
    }

    private static final class RawExportValidation {
        String status;
        String readinessStatus;
        long missingTextureCount;
        long missingAnimationMetadataCount;
        long missingGroupOrOrderCount;
        List<String> failedStages;
        List<RawValidationGate> gates;
    }

    private static final class RawValidationGate {
        String name;
        String status;
        String summary;
    }

    private static final class FileRef {
        String logicalName;
        String path;
        String kind;
    }
}




