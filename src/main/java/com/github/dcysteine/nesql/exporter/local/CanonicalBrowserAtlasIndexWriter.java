package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.EnumChatFormatting;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final String BROWSER_LAYOUT_INDEX = "browser-layout-index.json";
    private static final String FALLBACK_ATLAS_DIRECTORY = "browser-fallback-atlases";
    private static final String ITEM_ASSET_PREFIX = "nesqlpp:item/";
    private static final int FALLBACK_ATLAS_CHUNK_SIZE = 3500;

    private final File exportDirectory;
    private final Map<String, List<File>> rawItemSiblingImageCache = new LinkedHashMap<String, List<File>>();

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

        Set<String> indexedItemIds = new HashSet<String>();
        Set<String> forceRawFallbackItemIds = new HashSet<String>();
        for (RenderIndexEntry renderEntry : renderEntries) {
            if (renderEntry.assetId == null || !renderEntry.assetId.startsWith(ITEM_ASSET_PREFIX)) {
                continue;
            }
            String itemId = renderEntry.assetId.substring(ITEM_ASSET_PREFIX.length());
            StaticAtlasPlacement staticAtlas = staticPlacements.get(renderEntry.assetId);
            AnimatedAtlasPlacement animatedAtlas = animatedPlacements.get(renderEntry.assetId);
            if (shouldForceRawFallback(itemId, staticAtlas, animatedAtlas)) {
                forceRawFallbackItemIds.add(itemId);
                continue;
            }
            BrowserAtlasItem item = new BrowserAtlasItem();
            item.itemId = itemId;
            item.assetId = renderEntry.assetId;
            item.variantKey = renderEntry.variantKey;
            item.mode = renderEntry.mode;
            item.renderMode = renderEntry.renderMode;
            item.resolutionMode = renderEntry.resolutionMode;
            item.rendererFamily = renderEntry.rendererFamily;
            item.playbackHint = renderEntry.playbackHint;
            item.staticAtlas = staticAtlas;
            item.animatedAtlas = animatedAtlas;
            item.hasStaticAtlas = item.staticAtlas != null;
            item.hasAnimatedAtlas = item.animatedAtlas != null;
            if (item.hasAnimatedAtlas) {
                index.animatedItemCount += 1;
            }
            if (!item.hasStaticAtlas && !item.hasAnimatedAtlas) {
                index.missingAtlasCount += 1;
            }
            index.items.add(item);
            indexedItemIds.add(item.itemId);
        }

        int fallbackCount = appendFallbackRawImageAtlasEntries(
                canonicalDir,
                new File(canonicalDir, BROWSER_LAYOUT_INDEX),
                indexedItemIds,
                forceRawFallbackItemIds,
                index);

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
                        + index.missingAtlasCount
                        + ", rawFallback="
                        + fallbackCount
                        + ", correctedRawFallback="
                        + forceRawFallbackItemIds.size());
    }

    private boolean shouldForceRawFallback(
            String itemId,
            StaticAtlasPlacement staticAtlas,
            AnimatedAtlasPlacement animatedAtlas) {
        if (itemId == null || itemId.isEmpty() || animatedAtlas == null || staticAtlas != null) {
            return false;
        }
        String expectedStem = expectedItemImageStem(itemId);
        if (expectedStem == null || expectedStem.isEmpty()) {
            return false;
        }
        String sourcePath = firstAnimatedSourcePath(animatedAtlas);
        if (sourcePath == null || sourcePath.isEmpty()) {
            return false;
        }
        String sourceStem = stripImageExtension(stripSpriteAtlasSuffix(normalizePath(sourcePath)));
        if (sourceStem.equals(expectedStem)) {
            return false;
        }
        File exactRaw = resolveExportFile(expectedStem + ".png");
        return exactRaw.exists();
    }

    private int appendFallbackRawImageAtlasEntries(
            File canonicalDir,
            File layoutIndexFile,
            Set<String> indexedItemIds,
            Set<String> forceRawFallbackItemIds,
            BrowserAtlasIndex index) throws IOException {
        JsonObject layoutIndex = readJsonObject(layoutIndexFile);
        if (layoutIndex == null && (forceRawFallbackItemIds == null || forceRawFallbackItemIds.isEmpty())) {
            return 0;
        }

        List<CanonicalRenderAsset> fallbackAssets = new ArrayList<CanonicalRenderAsset>();
        Set<String> fallbackItemIds = new HashSet<String>();
        if (forceRawFallbackItemIds != null) {
            fallbackItemIds.addAll(forceRawFallbackItemIds);
        }
        if (layoutIndex != null) {
            JsonArray items = asArray(layoutIndex.get("items"));
            for (JsonElement itemElement : items) {
                JsonObject itemObject = asObject(itemElement);
                if (itemObject == null) {
                    continue;
                }
                String itemId = asString(itemObject.get("itemId"));
                if (itemId == null || itemId.isEmpty() || indexedItemIds.contains(itemId)) {
                    continue;
                }
                fallbackItemIds.add(itemId);
            }
        }
        for (String itemId : fallbackItemIds) {
            if (itemId == null || itemId.isEmpty() || indexedItemIds.contains(itemId)) {
                continue;
            }
            File imageFile = resolveRawItemImageFile(itemId);
            if (imageFile == null || !imageFile.exists()) {
                continue;
            }
            fallbackAssets.add(createRawFallbackAsset(itemId, imageFile));
        }

        if (fallbackAssets.isEmpty()) {
            return 0;
        }

        fallbackAssets.sort(Comparator.comparing(asset -> asset.assetId));
        File atlasDir = new File(canonicalDir, FALLBACK_ATLAS_DIRECTORY);
        if (!atlasDir.exists()) {
            atlasDir.mkdirs();
        }

        int emitted = 0;
        int chunkIndex = 0;
        for (int start = 0; start < fallbackAssets.size(); start += FALLBACK_ATLAS_CHUNK_SIZE) {
            int end = Math.min(fallbackAssets.size(), start + FALLBACK_ATLAS_CHUNK_SIZE);
            List<AtlasPackingSupport.AtlasSourceImage> sources = new ArrayList<AtlasPackingSupport.AtlasSourceImage>();
            for (CanonicalRenderAsset asset : fallbackAssets.subList(start, end)) {
                File imageFile = resolveExportFile(asset.staticFile);
                if (imageFile == null || !imageFile.exists()) {
                    continue;
                }
                BufferedImage image = ImageIO.read(imageFile);
                if (image == null) {
                    continue;
                }
                sources.add(new AtlasPackingSupport.AtlasSourceImage(asset, imageFile, image, null));
            }
            if (sources.isEmpty()) {
                continue;
            }

            AtlasPackingSupport.PackLayout layout = AtlasPackingSupport.computeLayout(sources);
            File atlasFile = new File(atlasDir, String.format("raw-fallback-%03d.png", chunkIndex));
            AtlasPackingSupport.writeAtlasImage(atlasFile, layout);
            String atlasFilePath = relativizeFromExportDirectory(atlasFile);

            for (AtlasPackingSupport.AtlasPlacement placement : layout.placements) {
                CanonicalRenderAsset asset = placement.source.asset;
                String itemId = asset.assetId.substring(ITEM_ASSET_PREFIX.length());
                StaticAtlasPlacement staticPlacement = new StaticAtlasPlacement();
                staticPlacement.atlasGroup = "browser-raw-fallback";
                staticPlacement.atlasFile = atlasFilePath;
                staticPlacement.atlasWidth = layout.width;
                staticPlacement.atlasHeight = layout.height;
                staticPlacement.x = placement.x;
                staticPlacement.y = placement.y;
                staticPlacement.width = placement.source.image.getWidth();
                staticPlacement.height = placement.source.image.getHeight();
                staticPlacement.sourcePath = asset.staticFile;

                BrowserAtlasItem browserItem = new BrowserAtlasItem();
                browserItem.itemId = itemId;
                browserItem.assetId = asset.assetId;
                browserItem.variantKey = asset.variantKey;
                browserItem.mode = asset.mode;
                browserItem.renderMode = asset.renderMode;
                browserItem.resolutionMode = "browser_layout_raw_image";
                browserItem.rendererFamily = asset.rendererFamily;
                browserItem.playbackHint = asset.playbackHint;
                browserItem.staticAtlas = staticPlacement;
                browserItem.animatedAtlas = null;
                browserItem.hasStaticAtlas = true;
                browserItem.hasAnimatedAtlas = false;
                index.items.add(browserItem);
                indexedItemIds.add(itemId);
                emitted += 1;
            }
            chunkIndex += 1;
        }
        return emitted;
    }

    private CanonicalRenderAsset createRawFallbackAsset(String itemId, File imageFile) {
        CanonicalRenderAsset asset = new CanonicalRenderAsset();
        asset.assetId = ITEM_ASSET_PREFIX + itemId;
        asset.variantKey = itemId;
        asset.sourceType = "item";
        asset.family = "item";
        asset.mode = "static_snapshot";
        asset.renderMode = "raw_image_fallback";
        asset.rendererFamily = "browser_raw_image";
        asset.playbackHint = "static";
        asset.atlasGroup = "browser-raw-fallback";
        asset.atlasCandidate = Boolean.TRUE;
        asset.staticFile = relativizeFromExportDirectory(imageFile);
        return asset;
    }

    private File resolveRawItemImageFile(String itemId) {
        List<String> candidates = rawItemImageCandidates(itemId);
        for (String candidate : candidates) {
            File file = resolveExportFile(candidate);
            if (file.exists()) {
                return file;
            }
        }
        File sibling = resolveRawItemSiblingImageFile(itemId);
        if (sibling != null && sibling.exists()) {
            return sibling;
        }
        return null;
    }

    private File resolveRawItemSiblingImageFile(String itemId) {
        String[] parts = itemId.split("~");
        if (parts.length < 4 || !"i".equals(parts[0])) {
            return null;
        }
        String modId = parts[1];
        String internalName = parts[2];
        String meta = parts[3];
        File familyDirectory = resolveExportFile("image/item/" + modId);
        if (!familyDirectory.exists() || !familyDirectory.isDirectory()) {
            return null;
        }

        String prefix = internalName + "~" + meta + "~";
        String cacheKey = familyDirectory.getAbsolutePath() + "|" + prefix;
        List<File> candidates = rawItemSiblingImageCache.get(cacheKey);
        if (candidates == null) {
            candidates = new ArrayList<File>();
            File[] children = familyDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!child.isFile()) {
                        continue;
                    }
                    String name = child.getName();
                    if (name.startsWith(prefix) && name.endsWith(".png")) {
                        candidates.add(child);
                    }
                }
            }
            Collections.sort(candidates, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
            rawItemSiblingImageCache.put(cacheKey, candidates);
        }

        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private List<String> rawItemImageCandidates(String itemId) {
        List<String> candidates = new ArrayList<String>();
        String[] parts = itemId.split("~");
        if (parts.length < 4 || !"i".equals(parts[0])) {
            return candidates;
        }
        String modId = parts[1];
        String internalName = parts[2];
        String meta = parts[3];
        StringBuilder exact = new StringBuilder();
        exact.append("image/item/").append(modId).append('/').append(internalName).append('~').append(meta);
        for (int i = 4; i < parts.length; i++) {
            exact.append('~').append(parts[i]);
        }
        exact.append(".png");
        candidates.add(exact.toString());
        candidates.add("image/item/" + modId + "/" + internalName + "~" + meta + ".png");
        candidates.add("image/item/" + modId + "/" + internalName + "~0.png");
        return candidates;
    }

    private File resolveExportFile(String relativePath) {
        File direct = new File(exportDirectory, relativePath.replace('/', File.separatorChar));
        if (direct.exists()) {
            return direct;
        }
        if (!relativePath.startsWith("image/")) {
            File underImage = new File(exportDirectory, ("image/" + relativePath).replace('/', File.separatorChar));
            if (underImage.exists()) {
                return underImage;
            }
        }
        return direct;
    }

    private String relativizeFromExportDirectory(File file) {
        String root = exportDirectory.getAbsolutePath();
        String path = file.getAbsolutePath();
        if (path.startsWith(root)) {
            path = path.substring(root.length());
        }
        while (path.startsWith(File.separator)) {
            path = path.substring(1);
        }
        return path.replace(File.separatorChar, '/');
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

    private String firstAnimatedSourcePath(AnimatedAtlasPlacement animatedAtlas) {
        if (animatedAtlas == null || animatedAtlas.frames == null || animatedAtlas.frames.isEmpty()) {
            return null;
        }
        Object value = animatedAtlas.frames.get(0).get("sourcePath");
        return value instanceof String ? (String) value : null;
    }

    private String expectedItemImageStem(String itemId) {
        String[] parts = itemId.split("~");
        if (parts.length < 4 || !"i".equals(parts[0])) {
            return null;
        }
        StringBuilder exact = new StringBuilder();
        exact.append("image/item/").append(parts[1]).append('/').append(parts[2]).append('~').append(parts[3]);
        for (int i = 4; i < parts.length; i++) {
            exact.append('~').append(parts[i]);
        }
        return normalizePath(exact.toString());
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/').replaceAll("^/+", "");
    }

    private String stripSpriteAtlasSuffix(String path) {
        String normalized = normalizePath(path);
        if (normalized.endsWith(".sprite-atlas.png")) {
            return normalized.substring(0, normalized.length() - ".sprite-atlas.png".length()) + ".png";
        }
        return normalized;
    }

    private String stripImageExtension(String path) {
        String normalized = normalizePath(path);
        if (normalized.endsWith(".png") || normalized.endsWith(".gif")) {
            return normalized.substring(0, normalized.length() - 4);
        }
        return normalized;
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
