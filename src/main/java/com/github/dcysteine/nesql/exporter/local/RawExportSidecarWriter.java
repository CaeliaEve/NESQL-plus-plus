package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.ExportStage;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiUiFamilyClassifier;
import com.github.dcysteine.nesql.exporter.plugin.nei.metadata.NeiUiTemplateLayoutSpecs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.persistence.EntityManager;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeVersion;

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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final String GT_NEI_BACKGROUND_ASSET_REF = "assets/ui-backgrounds/gregtech/nei_single_recipe.png";
    private static final String GT_NEI_BACKGROUND_RESOURCE = "gregtech:textures/gui/background/nei_single_recipe.png";
    private static final Map<String, String> MACHINE_CATALYST_RULES = loadBundledMachineCatalystRules();

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
        SemanticRulePack.RuntimeMetadata semanticRuleRuntime = buildSemanticRuleRuntimeMetadata();
        SemanticRulePack.writeBundledCopy(new File(rawDir, "facts/semantic/rule-pack.json"), semanticRuleRuntime);
        SemanticItemIdentityDiagnosticsWriter.SemanticAuditSummary semanticAudit =
                new SemanticItemIdentityDiagnosticsWriter(entityManager, rawDir).write();

        RawExportReport report = buildReport();
        report.semanticRuleRuntime = semanticRuleRuntime;
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
        report.counts.uiFamilyCensusHandlers = factCounts.uiFamilyCensusHandlers;
        report.counts.uiFamilyCensusFamilies = factCounts.uiFamilyCensusFamilies;
        report.counts.uiTemplateCatalogHandlers = factCounts.uiTemplateCatalogHandlers;
        report.counts.uiTemplateCatalogTemplates = factCounts.uiTemplateCatalogTemplates;
        report.counts.uiTemplateCatalogFamilies = factCounts.uiTemplateCatalogFamilies;
        report.counts.rawTextures = factCounts.textures;
        report.counts.rawAnimations = factCounts.animations;
        report.counts.rawEntities = factCounts.entities;
        report.counts.rawBrowserAtlasAssets = factCounts.browserAtlasAssets;
        report.counts.renderBackendFacts = factCounts.renderBackendFacts;
        report.counts.renderBackendAngelica = factCounts.renderBackendAngelica;
        report.counts.renderTextureSprites = factCounts.renderTextureSprites;
        report.counts.renderTextureSpritesMissingTiming = factCounts.renderTextureSpritesMissingTiming;
        report.counts.renderItemRenderers = factCounts.renderItemRenderers;
        report.counts.renderShaderItems = factCounts.renderShaderItems;
        report.counts.renderShaderItemsRequiringCapture = factCounts.renderShaderItemsRequiringCapture;
        report.counts.renderShaderItemsMissingCapture = factCounts.renderShaderItemsMissingCapture;
        report.counts.renderShaderItemsMissingCaptureSamples = factCounts.renderShaderItemsMissingCaptureSamples;
        report.counts.renderUnknownSpecialRenderers = factCounts.renderUnknownSpecialRenderers;
        report.counts.renderFramebufferCaptures = factCounts.renderFramebufferCaptures;
        report.counts.renderFramebufferCapturesWithoutFrames = factCounts.renderFramebufferCapturesWithoutFrames;
        report.counts.renderFramebufferCapturesWithoutFramesSamples = factCounts.renderFramebufferCapturesWithoutFramesSamples;
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
        RawExportValidationSupport.apply(report);
        String generatedAt = utcNow();
        RawExportManifest manifest = RawExportManifestBuilder.build(SCHEMA_VERSION, generatedAt, exportContext, report);

        RawExportReportWriter.write(SCHEMA_VERSION, generatedAt, rawDir, manifest, report);

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

    private SemanticRulePack.RuntimeMetadata buildSemanticRuleRuntimeMetadata() {
        SemanticRulePack.RuntimeMetadata metadata = new SemanticRulePack.RuntimeMetadata();
        metadata.repositoryName = exportContext.paths.repositoryName;
        metadata.exportProfile = exportContext.profile.profileId;
        metadata.exportSelection = exportContext.selection.describe();
        metadata.javaVersion = System.getProperty("java.version", "");
        metadata.minecraftVersion = safeMinecraftVersion();
        metadata.forgeVersion = safeForgeVersion();
        for (String modId : semanticFingerprintModIds()) {
            String version = safeModVersion(modId);
            if (version != null && !version.trim().isEmpty()) {
                metadata.modVersions.put(modId, version);
            }
        }
        metadata.gtnhFingerprint = semanticFingerprint(metadata.modVersions);
        return metadata;
    }

    private static List<String> semanticFingerprintModIds() {
        ArrayList<String> ids = new ArrayList<String>();
        Collections.addAll(ids,
                "gregtech",
                "NotEnoughItems",
                "angelica",
                "dreamcraft",
                "Thaumcraft",
                "appliedenergistics2",
                "Avaritia",
                "EnderIO",
                "BuildCraft|Core",
                "Forestry",
                "TConstruct",
                "ExtraUtilities",
                "OpenBlocks",
                "GalacticraftCore");
        return ids;
    }

    private static String safeMinecraftVersion() {
        try {
            ModContainer minecraft = Loader.instance().getMinecraftModContainer();
            return minecraft == null ? "" : minecraft.getVersion();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeForgeVersion() {
        try {
            return ForgeVersion.getVersion();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String safeModVersion(String modId) {
        try {
            ModContainer container = Loader.instance().getIndexedModList().get(modId);
            if (container == null) {
                return "";
            }
            String version = container.getVersion();
            return version == null ? "" : version;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String semanticFingerprint(Map<String, String> modVersions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (modVersions != null) {
                for (Map.Entry<String, String> entry : modVersions.entrySet()) {
                    String line = entry.getKey() + "=" + entry.getValue() + "\n";
                    digest.update(line.getBytes(StandardCharsets.UTF_8));
                }
            }
            byte[] bytes = digest.digest();
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < Math.min(12, bytes.length); index++) {
                builder.append(String.format("%02x", bytes[index] & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ignored) {
            return "";
        }
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

    private RawFactCounts writeRawFactStreams(File rawDir) throws IOException {
        RawFactCounts counts = new RawFactCounts();
        RawRepositoryFactStreamResult repository = streamRepositoryFacts(rawDir);
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
        UiFamilyCensusCounts uiFamilyCensusCounts = readUiFamilyCensusCounts(rawDir);
        counts.uiFamilyCensusHandlers = uiFamilyCensusCounts.handlers;
        counts.uiFamilyCensusFamilies = uiFamilyCensusCounts.families;
        UiTemplateCatalogCounts uiTemplateCatalogCounts = readUiTemplateCatalogCounts(rawDir);
        counts.uiTemplateCatalogHandlers = uiTemplateCatalogCounts.handlers;
        counts.uiTemplateCatalogTemplates = uiTemplateCatalogCounts.templates;
        counts.uiTemplateCatalogFamilies = uiTemplateCatalogCounts.families;
        createEmptyJsonl(new File(rawDir, "models/multiblocks/index.jsonl.gz"));
        counts.entities = writeEntityModelIndex(rawDir);
        AngelicaRenderFactsWriter.Counts renderCounts =
                new AngelicaRenderFactsWriter(entityManager, rawDir, renderAssets).write();
        counts.renderBackendFacts = renderCounts.backendFacts;
        counts.renderBackendAngelica = "angelica".equals(renderCounts.backend) ? 1L : 0L;
        counts.renderTextureSprites = renderCounts.textureSprites;
        counts.renderTextureSpritesMissingTiming = renderCounts.textureSpritesMissingTiming;
        counts.renderItemRenderers = renderCounts.itemRenderers;
        counts.renderShaderItems = renderCounts.shaderItems;
        counts.renderShaderItemsRequiringCapture = renderCounts.shaderItemsRequiringCapture;
        counts.renderShaderItemsMissingCapture = renderCounts.shaderItemsMissingCapture;
        counts.renderShaderItemsMissingCaptureSamples = new ArrayList<String>(renderCounts.shaderItemsMissingCaptureSamples);
        counts.renderUnknownSpecialRenderers = renderCounts.unknownSpecialRenderers;
        counts.renderFramebufferCaptures = renderCounts.framebufferCaptures;
        counts.renderFramebufferCapturesWithoutFrames = renderCounts.framebufferCapturesWithoutFrames;
        counts.renderFramebufferCapturesWithoutFramesSamples = new ArrayList<String>(renderCounts.framebufferCapturesWithoutFramesSamples);
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

    private static UiFamilyCensusCounts readUiFamilyCensusCounts(File rawDir) {
        UiFamilyCensusCounts counts = new UiFamilyCensusCounts();
        JsonObject report = readObject(new File(rawDir, "validation/ui-family-census.json"));
        JsonObject summary = report == null ? null : report.getAsJsonObject("summary");
        counts.handlers = readLong(summary, "handlerCount", 0L);
        counts.families = readLong(summary, "familyCount", 0L);
        return counts;
    }

    private static UiTemplateCatalogCounts readUiTemplateCatalogCounts(File rawDir) {
        UiTemplateCatalogCounts counts = new UiTemplateCatalogCounts();
        JsonObject report = readObject(new File(rawDir, "validation/ui-template-catalog.json"));
        JsonObject summary = report == null ? null : report.getAsJsonObject("summary");
        counts.handlers = readLong(summary, "handlerCount", 0L);
        counts.templates = readLong(summary, "templateCount", 0L);
        counts.families = readLong(summary, "familyCount", 0L);
        return counts;
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
        boolean requiresGtNeiBackgroundAsset = false;
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
            String imageResource = trimToEmpty(readString(source, "imageResource", null));
            String family = NeiUiFamilyClassifier.classifyHandlerFamily(handlerClass, itemName, modId);
            String layoutKind = NeiUiFamilyClassifier.inferLayoutKind(handlerClass, itemName, family);
            JsonObject nativeBackground = buildNativeBackground(source, family, layoutKind, width, height, yShift);
            requiresGtNeiBackgroundAsset = requiresGtNeiBackgroundAsset
                    || isGtModularUiBackground(nativeBackground);

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
            handler.addProperty("imageResource", imageResource);
            handler.add("nativeBackground", cloneJsonObject(nativeBackground));
            handler.add("source", new JsonParser().parse(source.toString()));
            handlerRows.add(handler);

            JsonObject layout = new JsonObject();
            layout.addProperty("schemaVersion", SCHEMA_VERSION + "/nei-handler-layout");
            layout.addProperty("handlerKey", handlerKey);
            layout.addProperty("handlerClass", handlerClass);
            layout.addProperty("canonicalMachineFamily", family);
            layout.addProperty("layoutKind", layoutKind);
            layout.addProperty("width", width);
            layout.addProperty("height", height);
            layout.addProperty("yShift", yShift);
            layout.addProperty("maxRecipesPerPage", maxPerPage);
            layout.addProperty("imageResource", imageResource);
            addImageRegion(layout, source);
            layout.add("nativeBackground", cloneJsonObject(nativeBackground));
            layout.add("slots", NeiUiTemplateLayoutSpecs.defaultLayoutSlotsJson(layoutKind));
            layout.add("textOverlays", new JsonArray());
            layout.add("dynamicPrimitives", new JsonArray());
            layout.add("progressBars", NeiUiTemplateLayoutSpecs.defaultProgressBarsJson(family, layoutKind));
            layout.add("fluidBars", new JsonArray());
            layout.add("energyBars", new JsonArray());
            layout.add("hotspots", new JsonArray());
            layout.add("viewports", new JsonArray());
            layoutRows.add(layout);
        }

        HandlerMetadataCounts counts = new HandlerMetadataCounts();
        if (requiresGtNeiBackgroundAsset) {
            materializeGtNeiBackgroundAsset(rawDir);
        }
        counts.handlers = writeArrayAsJsonl(handlerRows, new File(rawDir, "facts/nei/handlers.jsonl.gz"));
        counts.layouts = writeArrayAsJsonl(layoutRows, new File(rawDir, "facts/nei/handler-layouts.jsonl.gz"));
        return counts;
    }

    private static boolean isGtModularUiBackground(JsonObject background) {
        return background != null
                && "gt-modular-ui".equals(readString(background, "kind", ""))
                && ("captured".equals(readString(background, "status", ""))
                || "semantic".equals(readString(background, "status", "")));
    }

    private static JsonObject buildNativeBackground(
            JsonObject source,
            String family,
            String layoutKind,
            int width,
            int height,
            int yShift) {
        String imageResource = trimToEmpty(readString(source, "imageResource", null));
        int imageWidth = parseInt(readString(source, "imageWidth", null), 0);
        int imageHeight = parseInt(readString(source, "imageHeight", null), 0);
        JsonObject background = new JsonObject();
        background.addProperty("schemaVersion", SCHEMA_VERSION + "/native-ui-background");
        background.addProperty("width", width);
        background.addProperty("height", height);
        background.addProperty("yShift", yShift);
        background.addProperty("layoutKind", layoutKind);
        background.addProperty("canonicalMachineFamily", family);
        if (!imageResource.trim().isEmpty() && imageWidth > 0 && imageHeight > 0) {
            background.addProperty("status", "captured");
            background.addProperty("kind", "texture-region");
            background.addProperty("resource", imageResource);
            JsonObject region = new JsonObject();
            region.addProperty("x", parseInt(readString(source, "imageX", null), 0));
            region.addProperty("y", parseInt(readString(source, "imageY", null), 0));
            region.addProperty("width", imageWidth);
            region.addProperty("height", imageHeight);
            background.add("region", region);
            return background;
        }
        if ("gregtech-machine".equals(family)) {
            background.addProperty("status", "captured");
            background.addProperty("kind", "gt-modular-ui");
            background.addProperty("source", "GTNEIDefaultHandler.drawUI(ModularWindow.getBackground)");
            background.addProperty("drawable", "GTUITextures.BACKGROUND_NEI_SINGLE_RECIPE");
            background.addProperty("assetRef", GT_NEI_BACKGROUND_ASSET_REF);
            background.addProperty("resource", GT_NEI_BACKGROUND_RESOURCE);
            background.addProperty("scaling", "nine-slice");
            JsonObject texture = new JsonObject();
            texture.addProperty("width", 64);
            texture.addProperty("height", 64);
            texture.addProperty("borderU", 2);
            texture.addProperty("borderV", 2);
            background.add("texture", texture);
            JsonObject offset = new JsonObject();
            offset.addProperty("x", 3);
            offset.addProperty("y", 3);
            background.add("recipeBackgroundOffset", offset);
            JsonObject size = new JsonObject();
            size.addProperty("width", Math.max(0, width - 6));
            size.addProperty("height", Math.max(0, height - yShift - 6));
            background.add("recipeBackgroundSize", size);
            background.addProperty("captureRequired", false);
            return background;
        }
        background.addProperty("status", "missing");
        background.addProperty("kind", "unknown");
        background.addProperty("captureRequired", true);
        return background;
    }

    private static JsonObject cloneJsonObject(JsonObject source) {
        return new JsonParser().parse(source.toString()).getAsJsonObject();
    }

    private static void materializeGtNeiBackgroundAsset(File rawDir) throws IOException {
        File target = new File(rawDir, GT_NEI_BACKGROUND_ASSET_REF.replace('/', File.separatorChar));
        File parent = target.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        File temporary = new File(parent, target.getName() + ".tmp");
        deleteIfExists(temporary);
        ResourceLocation location = new ResourceLocation(GT_NEI_BACKGROUND_RESOURCE);
        try (InputStream input = net.minecraft.client.Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(location)
                .getInputStream();
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
        } catch (Exception e) {
            deleteIfExists(temporary);
            throw new IOException("Failed to materialize required GT NEI ModularUI background asset: " + location, e);
        }
        if (!temporary.isFile() || temporary.length() <= 0L) {
            deleteIfExists(temporary);
            throw new IOException("Materialized GT NEI ModularUI background asset is empty: " + location);
        }
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void addImageRegion(JsonObject layout, JsonObject source) {
        int imageWidth = parseInt(readString(source, "imageWidth", null), 0);
        int imageHeight = parseInt(readString(source, "imageHeight", null), 0);
        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }
        JsonObject region = new JsonObject();
        region.addProperty("x", parseInt(readString(source, "imageX", null), 0));
        region.addProperty("y", parseInt(readString(source, "imageY", null), 0));
        region.addProperty("width", imageWidth);
        region.addProperty("height", imageHeight);
        layout.add("imageRegion", region);
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

    private static Map<String, String> loadBundledMachineCatalystRules() {
        LinkedHashMap<String, String> rules = new LinkedHashMap<String, String>();
        InputStream stream = RawExportSidecarWriter.class
                .getClassLoader()
                .getResourceAsStream("gtnh-semantic-rules/machine-catalyst-rules.json");
        if (stream == null) {
            return rules;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement parsed = new JsonParser().parse(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                return rules;
            }
            JsonObject exactHandlers = parsed.getAsJsonObject().getAsJsonObject("exactHandlers");
            if (exactHandlers == null) {
                return rules;
            }
            for (Map.Entry<String, JsonElement> entry : exactHandlers.entrySet()) {
                String key = normalizeKey(entry.getKey());
                String value = entry.getValue() != null && entry.getValue().isJsonPrimitive()
                        ? trimToNull(entry.getValue().getAsString())
                        : null;
                if (key.length() > 0 && value != null) {
                    rules.put(key, value);
                }
            }
        } catch (Exception ignored) {
            return rules;
        }
        return rules;
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

    private static String preferredMachineItemName(String handlerClass, String itemName, String family) {
        for (String candidate : new String[] { handlerClass, itemName, family }) {
            String explicit = MACHINE_CATALYST_RULES.get(normalizeKey(candidate));
            if (explicit != null && explicit.length() > 0) {
                return explicit;
            }
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

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
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

    private static RawExportFileRef fileRef(String logicalName, String path, String kind) {
        RawExportFileRef ref = new RawExportFileRef();
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

    private RawRepositoryFactStreamResult streamRepositoryFacts(File rawDir) throws IOException {
        return new RawExportRepositoryFactStreamer(entityManager, rawDir, SCHEMA_VERSION).write();
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

    private static void purgeLegacyRawExportOutputs(File rawDir) throws IOException {
        deleteIfExists(new File(rawDir, "recipes.jsonl"));
        deleteIfExists(new File(rawDir, "items.jsonl"));
        deleteIfExists(new File(rawDir, "fluids.jsonl"));
        deleteIfExists(new File(rawDir, "entities.jsonl"));
        deleteIfExists(new File(rawDir, "facts/items.jsonl"));
        deleteIfExists(new File(rawDir, "facts/fluids.jsonl"));
        deleteIfExists(new File(rawDir, "facts/recipes/all.jsonl"));
        for (String domainId : RawExportRepositoryFactStreamer.specialDomainIds()) {
            deleteIfExists(new File(rawDir, "special/" + domainId + "/recipes.jsonl"));
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
        long uiFamilyCensusHandlers;
        long uiFamilyCensusFamilies;
        long uiTemplateCatalogHandlers;
        long uiTemplateCatalogTemplates;
        long uiTemplateCatalogFamilies;
        long textures;
        long animations;
        long entities;
        long browserAtlasAssets;
        long renderBackendFacts;
        long renderBackendAngelica;
        long renderTextureSprites;
        long renderTextureSpritesMissingTiming;
        long renderItemRenderers;
        long renderShaderItems;
        long renderShaderItemsRequiringCapture;
        long renderShaderItemsMissingCapture;
        List<String> renderShaderItemsMissingCaptureSamples = new ArrayList<String>();
        long renderUnknownSpecialRenderers;
        long renderFramebufferCaptures;
        long renderFramebufferCapturesWithoutFrames;
        List<String> renderFramebufferCapturesWithoutFramesSamples = new ArrayList<String>();
        NeiBrowserContract neiBrowserContract;
    }

    private static final class HandlerMetadataCounts {
        long handlers;
        long layouts;
    }

    private static final class UiFamilyCensusCounts {
        long handlers;
        long families;
    }

    private static final class UiTemplateCatalogCounts {
        long handlers;
        long templates;
        long families;
    }

    static final class NeiBrowserContract {
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






}
