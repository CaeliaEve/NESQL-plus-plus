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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        RawExportManifest manifest = buildManifest(report);

        Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        writeJson(gson, new File(rawDir, "manifest.json"), manifest);
        writeJson(gson, new File(rawDir, "export_report.json"), report);
        writeJson(gson, new File(rawDir, "validation/export_report.json"), report);

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
        manifest.capabilities.add("validation");
        manifest.files.put("items", "facts/items.jsonl");
        manifest.files.put("fluids", "facts/fluids.jsonl");
        manifest.files.put("recipes", "facts/recipes/all.jsonl");
        manifest.files.put("recipeIndex", "facts/recipes/index.json");
        manifest.files.put("groups", "facts/nei/groups.jsonl");
        manifest.files.put("neiOrder", "facts/nei/order.jsonl");
        manifest.files.put("textures", "assets/textures/index.jsonl");
        manifest.files.put("animations", "assets/animations/index.jsonl");
        manifest.files.put("browserAtlasIndex", "assets/textures/browser_atlas_index.json");
        manifest.files.put("neiHandlers", "facts/nei/handlers.jsonl");
        manifest.files.put("multiblocks", "models/multiblocks/index.jsonl");
        manifest.files.put("entities", "models/entities/index.jsonl");
        manifest.files.put("exportReport", "validation/export_report.json");
        manifest.files.put("stageTimings", "validation/export_stage_timings.json");
        manifest.files.put("stageChecksums", "validation/stage_checksums.json");
        manifest.files.put("canonicalRepository", "../canonical/repository.json");
        manifest.compatibilityFiles.add(fileRef("items", "items.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("fluids", "fluids.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("recipes", "recipes.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("groups", "groups.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("nei-order", "nei_order.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("textures", "textures.jsonl", "compat-jsonl"));
        manifest.compatibilityFiles.add(fileRef("animations", "animations.jsonl", "compat-jsonl"));
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
        report.validation.status = "not-yet-enforced";
        return report;
    }

    private RawFactCounts writeRawFactStreams(File rawDir) throws IOException {
        RawFactCounts counts = new RawFactCounts();
        JsonObject repository = readObject(new File(repositoryDirectory, "canonical/repository.json"));
        if (repository != null) {
            JsonArray items = repository.getAsJsonArray("items");
            JsonArray fluids = repository.getAsJsonArray("fluids");
            JsonArray recipes = repository.getAsJsonArray("recipes");
            counts.items = writeArrayAsJsonl(items, new File(rawDir, "facts/items.jsonl"));
            counts.fluids = writeArrayAsJsonl(fluids, new File(rawDir, "facts/fluids.jsonl"));
            counts.recipes = writeArrayAsJsonl(recipes, new File(rawDir, "facts/recipes/all.jsonl"));
            writeArrayAsJsonl(items, new File(rawDir, "items.jsonl"));
            writeArrayAsJsonl(fluids, new File(rawDir, "fluids.jsonl"));
            writeArrayAsJsonl(recipes, new File(rawDir, "recipes.jsonl"));
            writeRecipeIndex(rawDir, recipes);
        } else {
            createEmptyJsonl(new File(rawDir, "items.jsonl"));
            createEmptyJsonl(new File(rawDir, "fluids.jsonl"));
            createEmptyJsonl(new File(rawDir, "recipes.jsonl"));
            createEmptyJsonl(new File(rawDir, "facts/items.jsonl"));
            createEmptyJsonl(new File(rawDir, "facts/fluids.jsonl"));
            createEmptyJsonl(new File(rawDir, "facts/recipes/all.jsonl"));
            writeRecipeIndex(rawDir, new JsonArray());
        }

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
        for (CanonicalRenderAsset asset : renderAssets) {
            JsonObject row = toRenderAssetRow(asset);
            textureRows.add(row);
            if (isAnimated(asset)) {
                animationRows.add(row);
            }
        }
        if (textureRows.size() == 0) {
            JsonObject renderManifest = readObject(new File(repositoryDirectory, "canonical/render-assets.json"));
            if (renderManifest != null && renderManifest.has("assets") && renderManifest.get("assets").isJsonArray()) {
                JsonArray assets = renderManifest.getAsJsonArray("assets");
                counts.textures = writeArrayAsJsonl(assets, new File(rawDir, "assets/textures/index.jsonl"));
                JsonArray animated = new JsonArray();
                for (JsonElement element : assets) {
                    if (element.isJsonObject() && isAnimated(element.getAsJsonObject())) {
                        animated.add(element);
                    }
                }
                counts.animations = writeArrayAsJsonl(animated, new File(rawDir, "assets/animations/index.jsonl"));
                writeArrayAsJsonl(assets, new File(rawDir, "textures.jsonl"));
                writeArrayAsJsonl(animated, new File(rawDir, "animations.jsonl"));
            } else {
                createEmptyJsonl(new File(rawDir, "textures.jsonl"));
                createEmptyJsonl(new File(rawDir, "animations.jsonl"));
                createEmptyJsonl(new File(rawDir, "assets/textures/index.jsonl"));
                createEmptyJsonl(new File(rawDir, "assets/animations/index.jsonl"));
            }
        } else {
            counts.textures = writeArrayAsJsonl(textureRows, new File(rawDir, "assets/textures/index.jsonl"));
            counts.animations = writeArrayAsJsonl(animationRows, new File(rawDir, "assets/animations/index.jsonl"));
            writeArrayAsJsonl(textureRows, new File(rawDir, "textures.jsonl"));
            writeArrayAsJsonl(animationRows, new File(rawDir, "animations.jsonl"));
        }

        copyIfPresent(
                new File(repositoryDirectory, "canonical/browser-atlas-index.json"),
                new File(rawDir, "browser_atlas_index.json"));
        copyIfPresent(
                new File(repositoryDirectory, "canonical/browser-atlas-index.json"),
                new File(rawDir, "assets/textures/browser_atlas_index.json"));

        createEmptyJsonl(new File(rawDir, "nei_handlers.jsonl"));
        createEmptyJsonl(new File(rawDir, "multiblocks.jsonl"));
        createEmptyJsonl(new File(rawDir, "entities.jsonl"));
        createEmptyJsonl(new File(rawDir, "facts/nei/handlers.jsonl"));
        createEmptyJsonl(new File(rawDir, "models/multiblocks/index.jsonl"));
        createEmptyJsonl(new File(rawDir, "models/entities/index.jsonl"));
        return counts;
    }

    private static void writeRecipeIndex(File rawDir, JsonArray recipes) throws IOException {
        JsonObject index = new JsonObject();
        index.addProperty("schemaVersion", SCHEMA_VERSION + "/recipe-index");
        index.addProperty("strategy", "single-shard-compat");
        index.addProperty("recipeCount", recipes == null ? 0 : recipes.size());
        JsonArray shards = new JsonArray();
        JsonObject shard = new JsonObject();
        shard.addProperty("handlerId", "all");
        shard.addProperty("path", "facts/recipes/all.jsonl");
        shard.addProperty("recipeCount", recipes == null ? 0 : recipes.size());
        shards.add(shard);
        index.add("shards", shards);
        writeJson(new GsonBuilder().setPrettyPrinting().serializeNulls().create(), new File(rawDir, "facts/recipes/index.json"), index);
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
        add(row, "frameDurationMs", asset.frameDurationMs);
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

    private static void createEmptyJsonl(File out) throws IOException {
        File parent = out.getParentFile();
        if (parent != null) {
            ensureDirectory(parent);
        }
        try (FileOutputStream ignored = new FileOutputStream(out)) {
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
    }

    private static final class RawFactCounts {
        long items;
        long fluids;
        long recipes;
        long groups;
        long neiOrderEntries;
        long textures;
        long animations;
    }

    private static final class RawExportValidation {
        String status;
        long missingTextureCount;
        long missingAnimationMetadataCount;
        long missingGroupOrOrderCount;
        List<String> failedStages;
    }

    private static final class FileRef {
        String logicalName;
        String path;
        String kind;
    }
}
