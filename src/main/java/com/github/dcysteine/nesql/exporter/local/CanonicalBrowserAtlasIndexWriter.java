package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes an itemId-centric atlas lookup for NeoNEI's browser fast path.
 *
 * <p>The existing canonical atlas manifests are grouped by atlas file and asset id. That is ideal
 * for export/debugging, but the browser needs the reverse lookup: {@code itemId -> static atlas
 * rect / animated atlas frames}. Keeping this derivation in NESQL++ lets NeoNEI skip per-page
 * texture discovery and eventually render arbitrary browser pages from a global resource pool.</p>
 */
public class CanonicalBrowserAtlasIndexWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String OUTPUT_FILE = "browser-atlas-index.json";
    private static final String STATIC_ATLAS_MANIFEST = "atlas-manifest.json";
    private static final String ANIMATED_ATLAS_MANIFEST = "animated-atlas-manifest.json";
    private static final String RENDER_INDEX = "render-index.json";
    private static final String ITEM_ASSET_PREFIX = "nesqlpp:item/";

    private final File exportDirectory;

    public CanonicalBrowserAtlasIndexWriter(File exportDirectory) {
        this.exportDirectory = exportDirectory;
    }

    public void export() throws IOException {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Writing NESQL++ browser atlas index...");

        File canonicalDir = new File(exportDirectory, OUTPUT_DIRECTORY);
        if (!canonicalDir.exists()) {
            canonicalDir.mkdirs();
        }

        Map<String, StaticAtlasPlacement> staticPlacements =
                loadStaticPlacements(new File(canonicalDir, STATIC_ATLAS_MANIFEST));
        Map<String, AnimatedAtlasPlacement> animatedPlacements =
                loadAnimatedPlacements(new File(canonicalDir, ANIMATED_ATLAS_MANIFEST));
        List<RenderIndexEntry> renderEntries =
                loadRenderEntries(new File(canonicalDir, RENDER_INDEX));

        BrowserAtlasIndex index = new BrowserAtlasIndex();
        index.generatedAt = System.currentTimeMillis();
        index.staticAtlasManifest = OUTPUT_DIRECTORY + "/" + STATIC_ATLAS_MANIFEST;
        index.animatedAtlasManifest = OUTPUT_DIRECTORY + "/" + ANIMATED_ATLAS_MANIFEST;
        index.renderIndex = OUTPUT_DIRECTORY + "/" + RENDER_INDEX;

        for (RenderIndexEntry renderEntry : renderEntries) {
            if (renderEntry.assetId == null || !renderEntry.assetId.startsWith(ITEM_ASSET_PREFIX)) {
                continue;
            }
            BrowserAtlasItem item = new BrowserAtlasItem();
            item.itemId = renderEntry.assetId.substring(ITEM_ASSET_PREFIX.length());
            item.assetId = renderEntry.assetId;
            item.variantKey = renderEntry.variantKey;
            item.mode = renderEntry.mode;
            item.renderMode = renderEntry.renderMode;
            item.resolutionMode = renderEntry.resolutionMode;
            item.rendererFamily = renderEntry.rendererFamily;
            item.playbackHint = renderEntry.playbackHint;
            item.staticAtlas = staticPlacements.get(renderEntry.assetId);
            item.animatedAtlas = animatedPlacements.get(renderEntry.assetId);
            item.hasStaticAtlas = item.staticAtlas != null;
            item.hasAnimatedAtlas = item.animatedAtlas != null;
            if (item.hasAnimatedAtlas) {
                index.animatedItemCount += 1;
            }
            if (!item.hasStaticAtlas && !item.hasAnimatedAtlas) {
                index.missingAtlasCount += 1;
            }
            index.items.add(item);
        }

        index.items.sort(Comparator.comparing(item -> item.itemId));
        index.itemCount = index.items.size();

        File outputFile = new File(canonicalDir, OUTPUT_FILE);
        Gson gson = new GsonBuilder().serializeNulls().create();
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(index, writer);
        }

        Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ browser atlas index written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + outputFile.getAbsolutePath());
        Logger.chatMessage(
                EnumChatFormatting.GRAY
                        + "  items="
                        + index.itemCount
                        + ", animated="
                        + index.animatedItemCount
                        + ", missingAtlas="
                        + index.missingAtlasCount);
    }

    private Map<String, StaticAtlasPlacement> loadStaticPlacements(File manifestFile) throws IOException {
        Map<String, StaticAtlasPlacement> placements = new LinkedHashMap<>();
        JsonObject manifest = readJsonObject(manifestFile);
        if (manifest == null) {
            return placements;
        }
        JsonArray groups = asArray(manifest.get("groups"));
        for (JsonElement groupElement : groups) {
            JsonObject group = asObject(groupElement);
            if (group == null) {
                continue;
            }
            String atlasGroup = asString(group.get("atlasGroup"));
            String atlasFile = asString(group.get("atlasFile"));
            int atlasWidth = asInt(group.get("width"));
            int atlasHeight = asInt(group.get("height"));
            JsonArray assets = asArray(group.get("assets"));
            for (JsonElement assetElement : assets) {
                JsonObject asset = asObject(assetElement);
                if (asset == null) {
                    continue;
                }
                String assetId = asString(asset.get("assetId"));
                if (assetId == null || assetId.isEmpty()) {
                    continue;
                }
                StaticAtlasPlacement placement = new StaticAtlasPlacement();
                placement.atlasGroup = atlasGroup;
                placement.atlasFile = atlasFile;
                placement.atlasWidth = atlasWidth;
                placement.atlasHeight = atlasHeight;
                placement.x = asInt(asset.get("x"));
                placement.y = asInt(asset.get("y"));
                placement.width = asInt(asset.get("width"));
                placement.height = asInt(asset.get("height"));
                placement.sourcePath = asString(asset.get("sourcePath"));
                placements.put(assetId, placement);
            }
        }
        return placements;
    }

    private Map<String, AnimatedAtlasPlacement> loadAnimatedPlacements(File manifestFile) throws IOException {
        Map<String, AnimatedAtlasPlacement> placements = new LinkedHashMap<>();
        JsonObject manifest = readJsonObject(manifestFile);
        if (manifest == null) {
            return placements;
        }
        JsonArray groups = asArray(manifest.get("groups"));
        for (JsonElement groupElement : groups) {
            JsonObject group = asObject(groupElement);
            if (group == null) {
                continue;
            }
            String atlasGroup = asString(group.get("atlasGroup"));
            String atlasFile = asString(group.get("atlasFile"));
            int atlasWidth = asInt(group.get("width"));
            int atlasHeight = asInt(group.get("height"));
            JsonArray assets = asArray(group.get("assets"));
            for (JsonElement assetElement : assets) {
                JsonObject asset = asObject(assetElement);
                if (asset == null) {
                    continue;
                }
                String assetId = asString(asset.get("assetId"));
                if (assetId == null || assetId.isEmpty()) {
                    continue;
                }
                AnimatedAtlasPlacement placement = new AnimatedAtlasPlacement();
                placement.atlasGroup = atlasGroup;
                placement.atlasFile = atlasFile;
                placement.atlasWidth = atlasWidth;
                placement.atlasHeight = atlasHeight;
                placement.variantKey = asString(asset.get("variantKey"));
                placement.frameDurationMs = asInteger(asset.get("frameDurationMs"));
                placement.loopMode = asString(asset.get("loopMode"));
                placement.frameCount = asInteger(asset.get("frameCount"));
                placement.timeline = copyJsonObjects(asset.get("timeline"));
                placement.frames = copyJsonObjects(asset.get("frames"));
                placements.put(assetId, placement);
            }
        }
        return placements;
    }

    private List<RenderIndexEntry> loadRenderEntries(File renderIndexFile) throws IOException {
        List<RenderIndexEntry> entries = new ArrayList<>();
        JsonObject renderIndex = readJsonObject(renderIndexFile);
        if (renderIndex == null) {
            return entries;
        }
        JsonArray assets = asArray(renderIndex.get("assets"));
        for (JsonElement assetElement : assets) {
            JsonObject asset = asObject(assetElement);
            if (asset == null) {
                continue;
            }
            RenderIndexEntry entry = new RenderIndexEntry();
            entry.assetId = asString(asset.get("assetId"));
            entry.variantKey = asString(asset.get("variantKey"));
            entry.mode = asString(asset.get("mode"));
            entry.renderMode = asString(asset.get("renderMode"));
            entry.resolutionMode = asString(asset.get("resolutionMode"));
            entry.rendererFamily = asString(asset.get("rendererFamily"));
            entry.playbackHint = asString(asset.get("playbackHint"));
            entries.add(entry);
        }
        return entries;
    }

    private JsonObject readJsonObject(File file) throws IOException {
        if (!file.exists()) {
            Logger.MOD.warn("Browser atlas index input does not exist: {}", file.getAbsolutePath());
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }

    private List<Map<String, Object>> copyJsonObjects(JsonElement element) {
        List<Map<String, Object>> copied = new ArrayList<>();
        JsonArray array = asArray(element);
        for (JsonElement entryElement : array) {
            JsonObject entry = asObject(entryElement);
            if (entry == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> field : entry.entrySet()) {
                JsonElement value = field.getValue();
                if (value == null || value.isJsonNull()) {
                    map.put(field.getKey(), null);
                } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    map.put(field.getKey(), value.getAsNumber());
                } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                    map.put(field.getKey(), value.getAsBoolean());
                } else {
                    map.put(field.getKey(), value.getAsString());
                }
            }
            copied.add(map);
        }
        return copied.isEmpty() ? null : copied;
    }

    private JsonArray asArray(JsonElement element) {
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private JsonObject asObject(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private String asString(JsonElement element) {
        return element != null && !element.isJsonNull() ? element.getAsString() : null;
    }

    private int asInt(JsonElement element) {
        Integer value = asInteger(element);
        return value != null ? value : 0;
    }

    private Integer asInteger(JsonElement element) {
        return element != null && !element.isJsonNull() ? element.getAsInt() : null;
    }

    private static final class BrowserAtlasIndex {
        String schemaVersion = "nesqlpp/browser-atlas-index/v1-draft";
        long generatedAt;
        String staticAtlasManifest;
        String animatedAtlasManifest;
        String renderIndex;
        int itemCount;
        int animatedItemCount;
        int missingAtlasCount;
        List<BrowserAtlasItem> items = new ArrayList<>();
    }

    private static final class BrowserAtlasItem {
        String itemId;
        String assetId;
        String variantKey;
        String mode;
        String renderMode;
        String resolutionMode;
        String rendererFamily;
        String playbackHint;
        boolean hasStaticAtlas;
        boolean hasAnimatedAtlas;
        StaticAtlasPlacement staticAtlas;
        AnimatedAtlasPlacement animatedAtlas;
    }

    private static final class StaticAtlasPlacement {
        String atlasGroup;
        String atlasFile;
        int atlasWidth;
        int atlasHeight;
        int x;
        int y;
        int width;
        int height;
        String sourcePath;
    }

    private static final class AnimatedAtlasPlacement {
        String atlasGroup;
        String atlasFile;
        int atlasWidth;
        int atlasHeight;
        String variantKey;
        Integer frameDurationMs;
        String loopMode;
        Integer frameCount;
        List<Map<String, Object>> frames;
        List<Map<String, Object>> timeline;
    }

    private static final class RenderIndexEntry {
        String assetId;
        String variantKey;
        String mode;
        String renderMode;
        String resolutionMode;
        String rendererFamily;
        String playbackHint;
    }
}
