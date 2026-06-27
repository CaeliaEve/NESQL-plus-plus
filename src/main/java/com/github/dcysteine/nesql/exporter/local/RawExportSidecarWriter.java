package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.ExportStage;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.semantic.SemanticRulePack;
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
import net.minecraftforge.common.ForgeVersion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
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

        RawNeiFactCounts nei = new RawExportNeiFactWriter(repositoryDirectory, rawDir, SCHEMA_VERSION).write();
        counts.groups = nei.groups;
        counts.neiOrderEntries = nei.neiOrderEntries;
        counts.neiBrowserContract = nei.neiBrowserContract;
        counts.neiRuntimePanelItems = nei.neiRuntimePanelItems;
        counts.neiExportOnlyItems = nei.neiExportOnlyItems;
        counts.neiBrowserItems = nei.neiBrowserItems;
        counts.neiDefaultEntries = nei.neiDefaultEntries;
        counts.neiFallbackGroups = nei.neiFallbackGroups;
        counts.neiNativeGroups = nei.neiNativeGroups;
        counts.neiSyntheticGroups = nei.neiSyntheticGroups;
        counts.neiGuidFilterRules = nei.neiGuidFilterRules;
        counts.neiHiddenItemRules = nei.neiHiddenItemRules;
        counts.neiHiddenItems = nei.neiHiddenItems;
        counts.neiRepresentativeMismatches = nei.neiRepresentativeMismatches;
        counts.neiHandlers = nei.neiHandlers;
        counts.neiHandlerLayouts = nei.neiHandlerLayouts;
        counts.uiFamilyCensusHandlers = nei.uiFamilyCensusHandlers;
        counts.uiFamilyCensusFamilies = nei.uiFamilyCensusFamilies;
        counts.uiTemplateCatalogHandlers = nei.uiTemplateCatalogHandlers;
        counts.uiTemplateCatalogTemplates = nei.uiTemplateCatalogTemplates;
        counts.uiTemplateCatalogFamilies = nei.uiTemplateCatalogFamilies;

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

}
