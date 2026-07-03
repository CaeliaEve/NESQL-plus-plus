package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class RawExportRenderAssetCatalogWriter {
    private final File repositoryDirectory;
    private final File rawDir;
    private final List<CanonicalRenderAsset> renderAssets;

    RawExportRenderAssetCatalogWriter(
            File repositoryDirectory,
            File rawDir,
            List<CanonicalRenderAsset> renderAssets) {
        this.repositoryDirectory = repositoryDirectory;
        this.rawDir = rawDir;
        this.renderAssets = renderAssets;
    }

    RawRenderAssetCatalogCounts write() throws IOException {
        RawRenderAssetCatalogCounts counts = new RawRenderAssetCatalogCounts();
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
        counts.browserAtlasAssets = writeBrowserAtlasIndexAndAssets();
        return counts;
    }

    private long writeBrowserAtlasIndexAndAssets() throws IOException {
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
                rewriteBrowserAtlasPlacement( objectAt(item, "staticAtlas"), copiedAssets);
                rewriteBrowserAtlasPlacement( objectAt(item, "animatedAtlas"), copiedAssets);
            }
        }
        atlasIndex.addProperty("rawExportMaterializedAtlasAssets", copiedAssets.size());
        writeJson(new GsonBuilder().setPrettyPrinting().serializeNulls().create(),
                new File(rawDir, "assets/textures/browser_atlas_index.json"),
                atlasIndex);
        return copiedAssets.size();
    }

    private void rewriteBrowserAtlasPlacement(
            JsonObject placement,
            Set<String> copiedAssets) throws IOException {
        if (placement == null || !placement.has("atlasFile")) {
            return;
        }
        String atlasFile = stringValue(placement, "atlasFile");
        String rawAtlasPath = materializeBrowserAtlasAsset(atlasFile);
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

    private String materializeBrowserAtlasAsset(String atlasFile) throws IOException {
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


    private static JsonObject readObject(File file) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             java.io.InputStreamReader reader = new java.io.InputStreamReader(fis, StandardCharsets.UTF_8)) {
            JsonElement element = new com.google.gson.JsonParser().parse(reader);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long writeArrayAsJsonl(JsonArray array, File out) throws IOException {
        long count = 0L;
        Gson gson = new GsonBuilder().serializeNulls().create();
        try (OutputStreamWriter writer = RawExportSidecarFileOps.createUtf8JsonlWriter(out)) {
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
        RawExportEmptyJsonlWriter.write(out);
    }

    private static void writeJson(Gson gson, File out, Object value) throws IOException {
        RawExportSidecarFileOps.writeJson(gson, out, value);
    }

    private static void copyIfPresent(File source, File target) throws IOException {
        RawExportSidecarFileOps.copyOptional(source, target);
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
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? 0 : element.getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String stringValue(JsonObject object, String key) {
        try {
            JsonElement element = object == null ? null : object.get(key);
            return element == null || element.isJsonNull() ? null : element.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }
}
