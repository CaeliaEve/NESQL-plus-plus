package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.ExportContext;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
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

        RawFactCounts factCounts = writeRawFactStreams(rawDir);

        RawExportReport report = buildReport();
        report.counts.rawItems = factCounts.items;
        report.counts.rawFluids = factCounts.fluids;
        report.counts.rawRecipes = factCounts.recipes;
        report.counts.rawGroups = factCounts.groups;
        report.counts.rawNeiOrderEntries = factCounts.neiOrderEntries;
        report.counts.rawTextures = factCounts.textures;
        report.counts.rawAnimations = factCounts.animations;
        report.counts.rawEntities = factCounts.entities;
        applyRawValidation(report);
        RawExportManifest manifest = buildManifest(report);

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        writeJson(gson, new File(rawDir, "manifest.json"), manifest);
        writeJson(gson, new File(rawDir, "export_report.json"), report);
        writeJson(gson, new File(rawDir, "validation/export_report.json"), report);
        createEmptyJsonlIfMissing(new File(rawDir, "validation/errors.jsonl"));

        Logger.chatMessage(EnumChatFormatting.GREEN + "Raw-export sidecar written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + rawDir.getAbsolutePath());
    }

    public static void syncFinalReports(File repositoryDirectory) throws IOException {
        File rawDir = new File(repositoryDirectory, OUTPUT_DIRECTORY);
        ensureDirectory(rawDir);
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-stage-timings.json"),
                new File(rawDir, "export_stage_timings.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-stage-timings.json"),
                new File(rawDir, "validation/export_stage_timings.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-stage-checkpoint.json"),
                new File(rawDir, "stage_checkpoint.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-stage-checkpoint.json"),
                new File(rawDir, "validation/stage_checkpoint.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/stage-checksums.json"),
                new File(rawDir, "stage_checksums.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/stage-checksums.json"),
                new File(rawDir, "validation/stage_checksums.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-validation-report.json"),
                new File(rawDir, "validation_report.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/export-validation-report.json"),
                new File(rawDir, "validation/export-health-report.json"));
    }

    private RawExportManifest buildManifest(RawExportReport report) {
        RawExportManifest manifest = new RawExportManifest();
        manifest.schemaVersion = SCHEMA_VERSION;
        manifest.generatedAt = utcNow();
        manifest.repositoryName = exportContext.paths.repositoryName;
        manifest.profile = exportContext.profile.profileId;
        manifest.selection = exportContext.selection.describe();
        manifest.status = "sidecar-alpha";
        manifest.notes.add("Canonical export remains authoritative in this phase.");
        manifest.notes.add("JSONL files are present as stable compiler targets and will be populated incrementally.");
        manifest.capabilities.add("facts");
        manifest.capabilities.add("assets");
        manifest.capabilities.add("models");
        manifest.capabilities.add("validation");
        manifest.capabilities.add("special");
        manifest.files.put("items", "facts/items.jsonl");
        manifest.files.put("fluids", "facts/fluids.jsonl");
        manifest.files.put("recipes", "facts/recipes/all.jsonl");
        manifest.files.put("recipeIndex", "facts/recipes/index.json");
        manifest.files.put("groups", "facts/nei/groups.jsonl");
        manifest.files.put("neiOrder", "facts/nei/order.jsonl");
        manifest.files.put("textures", "assets/textures/index.jsonl");
        manifest.files.put("animations", "assets/animations/index.jsonl");
        manifest.files.put("nativeSprites", "assets/animations/native-sprites.jsonl");
        manifest.files.put("renderedGifs", "assets/animations/rendered-gifs.jsonl");
        manifest.files.put("browserAtlasIndex", "assets/textures/browser_atlas_index.json");
        manifest.files.put("neiHandlers", "facts/nei/handlers.jsonl");
        manifest.files.put("multiblocks", "models/multiblocks/index.jsonl");
        manifest.files.put("entities", "models/entities/index.jsonl");
        manifest.files.put("specialIndex", "special/index.json");
        manifest.files.put("exportReport", "validation/export_report.json");
        manifest.files.put("errors", "validation/errors.jsonl");
        manifest.files.put("stageTimings", "validation/export_stage_timings.json");
        manifest.files.put("stageCheckpoint", "validation/stage_checkpoint.json");
        manifest.files.put("stageChecksums", "validation/stage_checksums.json");
        manifest.files.put("canonicalRepository", "../canonical/repository.json");
        manifest.compatibilityFiles.add(fileRef("items", "items.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("fluids", "fluids.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("recipes", "recipes.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("groups", "groups.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("nei-order", "nei_order.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("textures", "textures.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("animations", "animations.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("native-sprites", "native_sprites.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("rendered-gifs", "rendered_gifs.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("browser-atlas-index", "browser_atlas_index.json", "compat-json"));
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
        counts.canonicalFiles = countFiles(new File(repositoryDirectory, "canonical"), null);
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
        RepositoryStreamResult repository = streamRepositoryFacts(new File(repositoryDirectory, "canonical/repository.json"), rawDir);
        counts.items = repository.items;
        counts.fluids = repository.fluids;
        counts.recipes = repository.recipes;

        JsonObject browserLayout = readObject(new File(repositoryDirectory, "canonical/browser-layout-index.json"));
        if (browserLayout != null) {
            JsonArray groups = browserLayout.getAsJsonArray("groups");
            JsonArray order = browserLayout.getAsJsonArray("defaultEntries");
            counts.groups = writeArrayAsJsonl(groups, new File(rawDir, "facts/nei/groups.jsonl"));
            counts.neiOrderEntries =
                    writeArrayAsJsonl(order, new File(rawDir, "facts/nei/order.jsonl"));
            writeArrayAsJsonl(groups, new File(rawDir, "groups.jsonl"));
            writeArrayAsJsonl(order, new File(rawDir, "nei_order.jsonl"));
        } else {
            createEmptyJsonl(new File(rawDir, "groups.jsonl"));
            createEmptyJsonl(new File(rawDir, "nei_order.jsonl"));
            createEmptyJsonl(new File(rawDir, "facts/nei/groups.jsonl"));
            createEmptyJsonl(new File(rawDir, "facts/nei/order.jsonl"));
        }

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
                counts.textures = writeArrayAsJsonl(assets, new File(rawDir, "assets/textures/index.jsonl"));
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
                counts.animations = writeArrayAsJsonl(animated, new File(rawDir, "assets/animations/index.jsonl"));
                writeArrayAsJsonl(nativeSprites, new File(rawDir, "assets/animations/native-sprites.jsonl"));
                writeArrayAsJsonl(renderedGifs, new File(rawDir, "assets/animations/rendered-gifs.jsonl"));
                writeArrayAsJsonl(assets, new File(rawDir, "textures.jsonl"));
                writeArrayAsJsonl(animated, new File(rawDir, "animations.jsonl"));
                writeArrayAsJsonl(nativeSprites, new File(rawDir, "native_sprites.jsonl"));
                writeArrayAsJsonl(renderedGifs, new File(rawDir, "rendered_gifs.jsonl"));
            } else {
                createEmptyJsonl(new File(rawDir, "textures.jsonl"));
                createEmptyJsonl(new File(rawDir, "animations.jsonl"));
                createEmptyJsonl(new File(rawDir, "native_sprites.jsonl"));
                createEmptyJsonl(new File(rawDir, "rendered_gifs.jsonl"));
                createEmptyJsonl(new File(rawDir, "assets/textures/index.jsonl"));
                createEmptyJsonl(new File(rawDir, "assets/animations/index.jsonl"));
                createEmptyJsonl(new File(rawDir, "assets/animations/native-sprites.jsonl"));
                createEmptyJsonl(new File(rawDir, "assets/animations/rendered-gifs.jsonl"));
            }
        } else {
            counts.textures = writeArrayAsJsonl(textureRows, new File(rawDir, "assets/textures/index.jsonl"));
            counts.animations = writeArrayAsJsonl(animationRows, new File(rawDir, "assets/animations/index.jsonl"));
            writeArrayAsJsonl(nativeSpriteRows, new File(rawDir, "assets/animations/native-sprites.jsonl"));
            writeArrayAsJsonl(renderedGifRows, new File(rawDir, "assets/animations/rendered-gifs.jsonl"));
            writeArrayAsJsonl(textureRows, new File(rawDir, "textures.jsonl"));
            writeArrayAsJsonl(animationRows, new File(rawDir, "animations.jsonl"));
            writeArrayAsJsonl(nativeSpriteRows, new File(rawDir, "native_sprites.jsonl"));
            writeArrayAsJsonl(renderedGifRows, new File(rawDir, "rendered_gifs.jsonl"));
        }
        copyIfPresent(
                new File(repositoryDirectory, "canonical/browser-atlas-index.json"),
                new File(rawDir, "browser_atlas_index.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/browser-atlas-index.json"),
                new File(rawDir, "assets/textures/browser_atlas_index.json"));

        createEmptyJsonl(new File(rawDir, "nei_handlers.jsonl"));
        createEmptyJsonl(new File(rawDir, "multiblocks.jsonl"));
        createEmptyJsonl(new File(rawDir, "facts/nei/handlers.jsonl"));
        createEmptyJsonl(new File(rawDir, "models/multiblocks/index.jsonl"));
        counts.entities = writeEntityModelIndex(rawDir);
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
        writeArrayAsJsonl(rows, new File(rawDir, "entities.jsonl"));
        return writeArrayAsJsonl(rows, new File(rawDir, "models/entities/index.jsonl"));
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

    private static void writeRecipeIndex(File rawDir, JsonArray recipes) throws IOException {
        Map<String, JsonArray> byHandler = new LinkedHashMap<String, JsonArray>();
        if (recipes != null) {
            for (JsonElement element : recipes) {
                String handlerId = inferRecipeHandlerId(element);
                JsonArray bucket = byHandler.get(handlerId);
                if (bucket == null) {
                    bucket = new JsonArray();
                    byHandler.put(handlerId, bucket);
                }
                bucket.add(element);
            }
        }

        File shardDir = new File(rawDir, "facts/recipes/by-handler");
        ensureDirectory(shardDir);
        JsonArray shards = new JsonArray();
        Set<String> usedFileNames = new LinkedHashSet<String>();
        for (Map.Entry<String, JsonArray> entry : byHandler.entrySet()) {
            String handlerId = entry.getKey();
            String fileName = uniqueShardFileName(handlerId, usedFileNames);
            String path = "facts/recipes/by-handler/" + fileName;
            writeArrayAsJsonl(entry.getValue(), new File(rawDir, path));

            JsonObject shard = new JsonObject();
            shard.addProperty("handlerId", handlerId);
            shard.addProperty("path", path);
            shard.addProperty("recipeCount", entry.getValue().size());
            shards.add(shard);
        }

        JsonObject allShard = new JsonObject();
        allShard.addProperty("handlerId", "all");
        allShard.addProperty("path", "facts/recipes/all.jsonl");
        allShard.addProperty("recipeCount", recipes == null ? 0 : recipes.size());

        JsonObject index = new JsonObject();
        index.addProperty("schemaVersion", SCHEMA_VERSION + "/recipe-index");
        index.addProperty("strategy", "by-handler");
        index.addProperty("recipeCount", recipes == null ? 0 : recipes.size());
        index.addProperty("shardCount", shards.size());
        index.add("shards", shards);
        index.add("compatibilityShard", allShard);
        writeJson(new GsonBuilder().setPrettyPrinting().serializeNulls().create(), new File(rawDir, "facts/recipes/index.json"), index);
    }


    private static void writeSpecialIndexes(File rawDir, JsonArray recipes) throws IOException {
        String[][] domains = specialDomainSpecs();

        Map<String, JsonArray> buckets = new LinkedHashMap<String, JsonArray>();
        for (String[] domain : domains) {
            buckets.put(domain[0], new JsonArray());
        }

        if (recipes != null) {
            for (JsonElement element : recipes) {
                String descriptor = recipeDescriptor(element);
                for (String[] domain : domains) {
                    if (matchesAny(descriptor, domain[1])) {
                        buckets.get(domain[0]).add(element);
                    }
                }
            }
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        JsonArray domainIndex = new JsonArray();
        for (String[] domain : domains) {
            String domainId = domain[0];
            JsonArray bucket = buckets.get(domainId);
            File domainDir = new File(rawDir, "special/" + domainId);
            ensureDirectory(domainDir);
            writeArrayAsJsonl(bucket, new File(domainDir, "recipes.jsonl"));
            JsonArray payloads = buildSpecialDomainPayloads(domainId, bucket);
            writeArrayAsJsonl(payloads, new File(domainDir, "payloads.jsonl"));

            JsonObject summary = buildSpecialDomainSummary(domainId, bucket);
            JsonObject index = new JsonObject();
            index.addProperty("schemaVersion", SCHEMA_VERSION + "/special-domain");
            index.addProperty("domain", domainId);
            index.addProperty("recipeCount", bucket.size());
            index.addProperty("payloadCount", payloads.size());
            index.addProperty("recipes", "special/" + domainId + "/recipes.jsonl");
            index.addProperty("payloads", "special/" + domainId + "/payloads.jsonl");
            index.addProperty("summary", "special/" + domainId + "/summary.json");
            index.add("stats", summary);
            writeJson(gson, new File(domainDir, "index.json"), index);
            writeJson(gson, new File(domainDir, "summary.json"), summary);

            JsonObject entry = new JsonObject();
            entry.addProperty("domain", domainId);
            entry.addProperty("recipeCount", bucket.size());
            entry.addProperty("payloadCount", payloads.size());
            entry.addProperty("index", "special/" + domainId + "/index.json");
            entry.addProperty("recipes", "special/" + domainId + "/recipes.jsonl");
            entry.addProperty("payloads", "special/" + domainId + "/payloads.jsonl");
            entry.addProperty("summary", "special/" + domainId + "/summary.json");
            entry.add("stats", summary);
            domainIndex.add(entry);
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION + "/special-index");
        root.add("domains", domainIndex);
        writeJson(gson, new File(rawDir, "special/index.json"), root);
    }


    private static JsonArray buildSpecialDomainPayloads(String domainId, JsonArray recipes) {
        JsonArray payloads = new JsonArray();
        if (recipes == null) {
            return payloads;
        }

        int ordinal = 0;
        for (JsonElement element : recipes) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject recipe = element.getAsJsonObject();
            JsonObject payload = new JsonObject();
            payload.addProperty("domain", domainId);
            payload.addProperty("ordinal", ordinal++);
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

            copyElement(payload, "machine", recipe, "machine");
            copyElement(payload, "layout", recipe, "layout");
            copyElement(payload, "itemInputs", recipe, "itemInputs");
            copyElement(payload, "itemOutputs", recipe, "itemOutputs");
            copyElement(payload, "fluidInputs", recipe, "fluidInputs");
            copyElement(payload, "fluidOutputs", recipe, "fluidOutputs");
            copyElement(payload, "probabilities", recipe, "probabilities");
            copyElement(payload, "renderHints", recipe, "renderHints");
            copyElement(payload, "extensions", recipe, "extensions");

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
                payload.add("facts", facts);
            }

            JsonElement metadata = elementAt(recipe, "metadata");
            if (metadata != null && metadata.isJsonObject()) {
                payload.add("metadata", cloneJson(metadata));
            }
            payloads.add(payload);
        }
        return payloads;
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
            copyElement(facts, "drops", recipe, "metadata.drops");
            copyElement(facts, "fluidDrops", recipe, "metadata.fluidDrops");
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
        String candidate = base + ".jsonl";
        int suffix = 2;
        while (usedFileNames.contains(candidate)) {
            candidate = base + "-" + suffix + ".jsonl";
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
        try (FileOutputStream fos = new FileOutputStream(out);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
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

    private RepositoryStreamResult streamRepositoryFacts(File repositoryFile, File rawDir) throws IOException {
        createEmptyJsonl(new File(rawDir, "items.jsonl"));
        createEmptyJsonl(new File(rawDir, "fluids.jsonl"));
        createEmptyJsonl(new File(rawDir, "recipes.jsonl"));
        createEmptyJsonl(new File(rawDir, "facts/items.jsonl"));
        createEmptyJsonl(new File(rawDir, "facts/fluids.jsonl"));
        createEmptyJsonl(new File(rawDir, "facts/recipes/all.jsonl"));

        RepositoryStreamResult result = new RepositoryStreamResult();
        if (repositoryFile == null || !repositoryFile.exists()) {
            writeRecipeIndex(rawDir, new JsonArray());
            writeSpecialIndexes(rawDir, new JsonArray());
            return result;
        }

        Gson gson = new GsonBuilder().serializeNulls().create();
        Map<String, RecipeShardState> shards = new LinkedHashMap<String, RecipeShardState>();
        Set<String> usedShardFileNames = new LinkedHashSet<String>();
        Map<String, SpecialDomainStreamState> domains = createSpecialDomainStreamStates(rawDir);
        JsonlWriter itemFacts = null;
        JsonlWriter itemCompat = null;
        JsonlWriter fluidFacts = null;
        JsonlWriter fluidCompat = null;
        JsonlWriter recipeFacts = null;
        JsonlWriter recipeCompat = null;
        try {
            itemFacts = new JsonlWriter(new File(rawDir, "facts/items.jsonl"), gson);
            itemCompat = new JsonlWriter(new File(rawDir, "items.jsonl"), gson);
            fluidFacts = new JsonlWriter(new File(rawDir, "facts/fluids.jsonl"), gson);
            fluidCompat = new JsonlWriter(new File(rawDir, "fluids.jsonl"), gson);
            recipeFacts = new JsonlWriter(new File(rawDir, "facts/recipes/all.jsonl"), gson);
            recipeCompat = new JsonlWriter(new File(rawDir, "recipes.jsonl"), gson);

            try (FileInputStream fis = new FileInputStream(repositoryFile);
                 java.io.InputStreamReader input = new java.io.InputStreamReader(fis, StandardCharsets.UTF_8);
                 com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(input)) {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if ("items".equals(name)) {
                        result.items = streamPlainArray(reader, itemFacts, itemCompat, gson);
                    } else if ("fluids".equals(name)) {
                        result.fluids = streamPlainArray(reader, fluidFacts, fluidCompat, gson);
                    } else if ("recipes".equals(name)) {
                        result.recipes = streamRecipes(reader, recipeFacts, recipeCompat, shards, usedShardFileNames, domains, rawDir, gson);
                    } else {
                        reader.skipValue();
                    }
                }
                reader.endObject();
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to stream raw-export repository source: " + repositoryFile.getAbsolutePath(), e);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        } finally {
            closeQuietly(itemFacts);
            closeQuietly(itemCompat);
            closeQuietly(fluidFacts);
            closeQuietly(fluidCompat);
            closeQuietly(recipeFacts);
            closeQuietly(recipeCompat);
            for (RecipeShardState shard : shards.values()) {
                closeQuietly(shard.writer);
            }
            for (SpecialDomainStreamState domain : domains.values()) {
                closeQuietly(domain.recipeWriter);
                closeQuietly(domain.payloadWriter);
            }
        }

        writeRecipeIndex(rawDir, shards, result.recipes);
        writeSpecialIndexes(rawDir, domains);
        return result;
    }

    private static long streamPlainArray(
            com.google.gson.stream.JsonReader reader,
            JsonlWriter primary,
            JsonlWriter compatibility,
            Gson gson) throws IOException {
        long count = 0L;
        reader.beginArray();
        while (reader.hasNext()) {
            JsonElement element = gson.fromJson(reader, JsonElement.class);
            primary.write(element);
            compatibility.write(element);
            count++;
        }
        reader.endArray();
        return count;
    }

    private static long streamRecipes(
            com.google.gson.stream.JsonReader reader,
            JsonlWriter allRecipes,
            JsonlWriter compatibilityRecipes,
            Map<String, RecipeShardState> shards,
            Set<String> usedShardFileNames,
            Map<String, SpecialDomainStreamState> domains,
            File rawDir,
            Gson gson) throws IOException {
        long count = 0L;
        reader.beginArray();
        while (reader.hasNext()) {
            JsonElement element = gson.fromJson(reader, JsonElement.class);
            allRecipes.write(element);
            compatibilityRecipes.write(element);
            count++;

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
                        domain.recipeWriter.write(element);
                        JsonObject payload = buildSpecialDomainPayload(domain.domainId, recipe, domain.payloadOrdinal++);
                        domain.payloadWriter.write(payload);
                        domain.accept(recipe);
                    }
                }
            }
        }
        reader.endArray();
        return count;
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

        JsonObject allShard = new JsonObject();
        allShard.addProperty("handlerId", "all");
        allShard.addProperty("path", "facts/recipes/all.jsonl");
        allShard.addProperty("recipeCount", recipeCount);

        JsonObject index = new JsonObject();
        index.addProperty("schemaVersion", SCHEMA_VERSION + "/recipe-index");
        index.addProperty("strategy", "by-handler");
        index.addProperty("recipeCount", recipeCount);
        index.addProperty("shardCount", shards.size());
        index.add("shards", shards);
        index.add("compatibilityShard", allShard);
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
            index.addProperty("recipes", "special/" + domain.domainId + "/recipes.jsonl");
            index.addProperty("payloads", "special/" + domain.domainId + "/payloads.jsonl");
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
            entry.addProperty("recipes", "special/" + domain.domainId + "/recipes.jsonl");
            entry.addProperty("payloads", "special/" + domain.domainId + "/payloads.jsonl");
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
                    new JsonlWriter(new File(domainDir, "recipes.jsonl"), gson),
                    new JsonlWriter(new File(domainDir, "payloads.jsonl"), gson)));
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

        copyElement(payload, "machine", recipe, "machine");
        copyElement(payload, "layout", recipe, "layout");
        copyElement(payload, "itemInputs", recipe, "itemInputs");
        copyElement(payload, "itemOutputs", recipe, "itemOutputs");
        copyElement(payload, "fluidInputs", recipe, "fluidInputs");
        copyElement(payload, "fluidOutputs", recipe, "fluidOutputs");
        copyElement(payload, "probabilities", recipe, "probabilities");
        copyElement(payload, "renderHints", recipe, "renderHints");
        copyElement(payload, "extensions", recipe, "extensions");

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
        JsonElement metadata = elementAt(recipe, "metadata");
        if (metadata != null && metadata.isJsonObject()) {
            copyElement(payload, "metadata", recipe, "metadata");
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
            this.writer = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8);
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
        final JsonlWriter recipeWriter;
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

        SpecialDomainStreamState(String domainId, String needles, JsonlWriter recipeWriter, JsonlWriter payloadWriter) {
            this.domainId = domainId;
            this.needles = needles;
            this.recipeWriter = recipeWriter;
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
        try (FileOutputStream ignored = new FileOutputStream(out)) {
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
        long rawTextures;
        long rawAnimations;
        long rawEntities;
    }

    private static final class RawFactCounts {
        long items;
        long fluids;
        long recipes;
        long groups;
        long neiOrderEntries;
        long textures;
        long animations;
        long entities;
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


