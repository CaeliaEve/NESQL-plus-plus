package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalExportMapper;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalFluid;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.github.dcysteine.nesql.sql.base.fluid.Fluid;
import com.github.dcysteine.nesql.sql.base.item.Item;
import jakarta.persistence.EntityManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class CanonicalRenderAssetCollector {
    private static final Gson GSON = new Gson();

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final Map<String, CanonicalRenderAsset> assetTemplateCache = new HashMap<>();
    private final Map<String, List<File>> frameFilesCache = new HashMap<>();

    public CanonicalRenderAssetCollector(EntityManager entityManager, File exportDirectory) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
    }

    public List<CanonicalRenderAsset> collectAll() {
        List<CanonicalRenderAsset> assets = new ArrayList<>();
        if (entityManager != null) {
            assets.addAll(collectItemAssets());
            assets.addAll(collectFluidAssets());
        }
        if (!assets.isEmpty()) {
            return assets;
        }

        Logger.MOD.warn("No render assets available from database; falling back to exported file scan.");
        assets.addAll(collectItemAssetsFromFiles());
        assets.addAll(collectFluidAssetsFromFiles());
        return assets;
    }

    private List<CanonicalRenderAsset> collectItemAssets() {
        @SuppressWarnings("unchecked")
        List<Item> items = entityManager.createQuery("SELECT i FROM Item i", Item.class).getResultList();
        List<CanonicalRenderAsset> assets = new ArrayList<>();
        int processed = 0;
        for (Item item : items) {
            CanonicalItem canonical = CanonicalExportMapper.mapItem(item);
            CanonicalRenderAsset asset =
                    buildAssetFromImagePath(canonical.renderAssetRef, "item", item.getImageFilePath());
            if (asset != null) {
                assets.add(asset);
            }
            processed++;
            if (processed % 5000 == 0) {
                Logger.MOD.info("Collected {} item render assets so far...", processed);
            }
        }
        return assets;
    }

    private List<CanonicalRenderAsset> collectFluidAssets() {
        @SuppressWarnings("unchecked")
        List<Fluid> fluids = entityManager.createQuery("SELECT f FROM Fluid f", Fluid.class).getResultList();
        List<CanonicalRenderAsset> assets = new ArrayList<>();
        int processed = 0;
        for (Fluid fluid : fluids) {
            CanonicalFluid canonical = CanonicalExportMapper.mapFluid(fluid);
            CanonicalRenderAsset asset =
                    buildAssetFromImagePath(canonical.renderAssetRef, "fluid", fluid.getImageFilePath());
            if (asset != null) {
                assets.add(asset);
            }
            processed++;
            if (processed % 1000 == 0) {
                Logger.MOD.info("Collected {} fluid render assets so far...", processed);
            }
        }
        return assets;
    }

    private List<CanonicalRenderAsset> collectItemAssetsFromFiles() {
        File itemsRoot = new File(exportDirectory, "items");
        List<CanonicalRenderAsset> assets = new ArrayList<>();
        if (!itemsRoot.exists()) {
            return assets;
        }

        List<File> itemFiles = new ArrayList<>();
        collectJsonGzFiles(itemsRoot, itemFiles);
        itemFiles.sort(Comparator.comparing(File::getAbsolutePath));

        for (File itemFile : itemFiles) {
            assets.addAll(readItemAssetsFromFile(itemFile));
        }
        return assets;
    }

    private List<CanonicalRenderAsset> readItemAssetsFromFile(File itemFile) {
        List<CanonicalRenderAsset> assets = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(itemFile);
             GZIPInputStream gis = new GZIPInputStream(fis);
             InputStreamReader reader = new InputStreamReader(gis, java.nio.charset.StandardCharsets.UTF_8)) {
            ItemExportEntry[] entries = GSON.fromJson(reader, ItemExportEntry[].class);
            if (entries == null) {
                return assets;
            }
            for (ItemExportEntry entry : entries) {
                if (entry == null || entry.renderAssetRef == null || entry.imageFileName == null) {
                    continue;
                }
                CanonicalRenderAsset asset = buildAssetFromImagePath(entry.renderAssetRef, "item", entry.imageFileName);
                if (asset != null) {
                    assets.add(asset);
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read exported item assets from {}", itemFile.getAbsolutePath(), e);
        }
        return assets;
    }

    private List<CanonicalRenderAsset> collectFluidAssetsFromFiles() {
        File fluidsRoot = new File(exportDirectory, "image" + File.separator + "fluid");
        List<CanonicalRenderAsset> assets = new ArrayList<>();
        if (!fluidsRoot.exists()) {
            return assets;
        }

        List<File> primaryArtifacts = new ArrayList<>();
        collectPrimaryImageArtifacts(fluidsRoot, primaryArtifacts);
        primaryArtifacts.sort(Comparator.comparing(File::getAbsolutePath));

        for (File baseFile : primaryArtifacts) {
            String variantKey = buildVariantKey(fluidsRoot, baseFile);
            if (variantKey == null || variantKey.isEmpty()) {
                continue;
            }
            String normalizedVariant = variantKey.replace(File.separatorChar, '/');
            int separator = normalizedVariant.indexOf('/');
            if (separator <= 0 || separator == normalizedVariant.length() - 1) {
                continue;
            }
            String modId = normalizedVariant.substring(0, separator);
            String internalName = normalizedVariant.substring(separator + 1).replace('/', '~');
            String assetId = "nesqlpp:fluid/f~" + modId + "~" + internalName;
            CanonicalRenderAsset asset = buildAssetFromImagePath(assetId, "fluid", normalizedVariant);
            if (asset != null) {
                assets.add(asset);
            }
        }

        return assets;
    }

    private String safeNextString(JsonReader reader) throws IOException {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return reader.nextString();
    }

    private void collectJsonGzFiles(File directory, List<File> output) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectJsonGzFiles(child, output);
            } else if (child.isFile() && child.getName().endsWith(".json.gz")) {
                output.add(child);
            }
        }
    }

    private void collectPrimaryImageArtifacts(File directory, List<File> output) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectPrimaryImageArtifacts(child, output);
                continue;
            }
            if (!child.isFile()) {
                continue;
            }
            String name = child.getName();
            if (name.endsWith(".gif")) {
                output.add(child);
                continue;
            }
            if (!name.endsWith(".png")) {
                continue;
            }
            if (name.endsWith(".sprite-atlas.png") || name.endsWith(".sprite.json")) {
                continue;
            }
            if (name.matches(".*_frame\\d+\\.png")) {
                continue;
            }
            output.add(child);
        }
    }

    private CanonicalRenderAsset buildAssetFromImagePath(
            String assetId,
            String familyDirectory,
            String imageFilePath) {
        if (assetId == null || assetId.isEmpty() || imageFilePath == null || imageFilePath.isEmpty()) {
            return null;
        }

        String normalizedImagePath = normalizeImagePath(imageFilePath, familyDirectory);
        if (normalizedImagePath == null || normalizedImagePath.isEmpty()) {
            return null;
        }

        String cacheKey = familyDirectory + "|" + normalizedImagePath;
        CanonicalRenderAsset cached = assetTemplateCache.get(cacheKey);
        if (cached != null) {
            CanonicalRenderAsset copy = copyAsset(cached);
            copy.assetId = assetId;
            return copy;
        }

        File familyRoot = new File(exportDirectory, "image" + File.separator + familyDirectory);
        File baseFile = resolvePrimaryArtifact(familyRoot, normalizedImagePath);
        if (!baseFile.exists()) {
            return null;
        }
        String variantKey = buildVariantKey(familyRoot, baseFile);
        if (variantKey == null || variantKey.isEmpty()) {
            return null;
        }

        CanonicalRenderAsset asset = new CanonicalRenderAsset();
        asset.schemaVersion = "nesqlpp/render-asset/v1-draft";
        asset.assetId = assetId;
        asset.variantKey = familyDirectory + "/" + variantKey.replace(File.separatorChar, '/');
        asset.sourceType = familyDirectory;
        asset.family = familyDirectory;
        File metadataFile = metadataFile(baseFile);
        asset.contentHash = computeAssetFingerprint(baseFile, metadataFile, null);
        asset.mode = "static_snapshot";
        asset.animationMode = "none";
        asset.captureMethod = "single_frame_render";
        asset.sourceFormat = "png-sequence";
        asset.primaryArtifact = relativizeFromExportDirectory(baseFile);
        asset.loop = Boolean.TRUE;
        asset.loopMode = "loop";
        asset.frameDurationMs = null;
        asset.configuredFrameCount = 1;
        asset.capturedFrameCount = 1;
        asset.detectionFrameCount = 10;
        asset.captureTimeoutMs = 30000L;
        asset.atlasGroup = familyDirectory + "-static";
        asset.atlasCandidate = Boolean.TRUE;
        asset.atlasFile = null;
        asset.layers = null;
        asset.rect = null;
        asset.sourcePath = relativizeFromExportDirectory(baseFile);
        asset.atlasTexture = null;
        asset.atlasExportFile = null;
        asset.spriteMetadataFile = null;
        asset.nativeSpriteAtlasFile = null;
        asset.staticFile = relativizeFromExportDirectory(baseFile);
        applyNativeSpriteMetadata(asset, baseFile);
        boolean nativeAnimated = isNativeSpriteAnimated(asset);
        boolean nativeSnapshot = isNativeSpriteSnapshot(asset);

        List<File> frameFiles = Collections.emptyList();
        boolean shouldScanFrames =
                normalizedImagePath.endsWith(".gif")
                        && (asset.spriteMetadataFile == null || firstFrameFile(baseFile).exists());
        if (shouldScanFrames) {
            frameFiles = findFrameFiles(baseFile);
            asset.contentHash = computeAssetFingerprint(baseFile, metadataFile, frameFiles);
        }
        if (!nativeAnimated && !nativeSnapshot) {
            asset.frames = new ArrayList<>();
            asset.timeline = new ArrayList<>();
            asset.frames.add(frameDescriptor(baseFile, 0));
            asset.timeline.add(timelineEntry(baseFile, 0, null));
            for (int i = 0; i < frameFiles.size(); i++) {
                asset.frames.add(frameDescriptor(frameFiles.get(i), i + 1));
                asset.timeline.add(timelineEntry(frameFiles.get(i), i + 1, ConfigOptions.GIF_FRAME_DELAY_MS.get()));
            }
            asset.frameCount = asset.frames.size();
            asset.capturedFrameCount = asset.frames.size();
        }

        if (nativeAnimated) {
            asset.mode = "native_sprite_animation";
            asset.animationMode = "native_sprite";
            asset.captureMethod = "native_sprite_metadata";
            asset.atlasGroup = familyDirectory + "-native-animated";
            asset.atlasCandidate = Boolean.TRUE;
            asset.frames = null;
            asset.framePattern = null;
            asset.capturedFrameCount = 0;
            asset.configuredFrameCount = 0;
        } else if (nativeSnapshot) {
            asset.mode = "native_sprite_snapshot";
            asset.animationMode = "none";
            asset.captureMethod = "native_sprite_metadata";
            asset.atlasGroup = familyDirectory + "-native-static";
            asset.atlasCandidate = Boolean.TRUE;
            asset.frames = null;
            asset.framePattern = null;
            asset.capturedFrameCount = 0;
            asset.configuredFrameCount = 0;
        } else if (!frameFiles.isEmpty()) {
            asset.mode = "rendered_frames";
            asset.animationMode = "frame_sequence";
            asset.captureMethod = "multi_frame_render";
            asset.frameDurationMs = ConfigOptions.GIF_FRAME_DELAY_MS.get();
            asset.loop = ConfigOptions.GIF_LOOP_COUNT.get() == 0;
            asset.loopMode = asset.loop ? "loop" : "once";
            asset.framePattern = buildFramePattern(baseFile);
            asset.atlasGroup = familyDirectory + "-animated";
            asset.atlasCandidate = Boolean.FALSE;
            asset.configuredFrameCount = ConfigOptions.GIF_FRAMES.get();
        }

        if (asset.baseSize == null && "rendered_frames".equals(asset.mode)) {
            asset.baseSize = readBaseSize(baseFile);
        }
        assetTemplateCache.put(cacheKey, copyAsset(asset));
        return asset;
    }

    private String normalizeImagePath(String imageFilePath, String familyDirectory) {
        String normalized = imageFilePath.replace('/', File.separatorChar).replace('\\', File.separatorChar);
        String prefix = familyDirectory + File.separator;
        if (normalized.startsWith(prefix)) {
            normalized = normalized.substring(prefix.length());
        }
        while (normalized.startsWith(File.separator)) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private File resolvePrimaryArtifact(File familyRoot, String normalizedImagePath) {
        File exactPath = new File(familyRoot, normalizedImagePath);
        if (exactPath.exists()) {
            return exactPath;
        }

        File directPng = new File(familyRoot, normalizedImagePath + ".png");
        if (directPng.exists()) {
            return directPng;
        }

        File directGif = new File(familyRoot, normalizedImagePath + ".gif");
        if (directGif.exists()) {
            return directGif;
        }

        File spriteAtlas = new File(familyRoot, normalizedImagePath + ".sprite-atlas.png");
        if (spriteAtlas.exists()) {
            return spriteAtlas;
        }

        return exactPath;
    }

    private String buildVariantKey(File familyRoot, File baseFile) {
        String relative = baseFile.getAbsolutePath();
        String root = familyRoot.getAbsolutePath();
        if (!relative.startsWith(root)) {
            return null;
        }

        relative = relative.substring(root.length());
        while (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }

        if (relative.endsWith(".sprite-atlas.png")) {
            return relative.substring(0, relative.length() - ".sprite-atlas.png".length());
        }
        if (relative.endsWith(".png")) {
            return relative.substring(0, relative.length() - 4);
        }
        if (relative.endsWith(".gif")) {
            return relative.substring(0, relative.length() - 4);
        }
        return relative;
    }

    private void applyNativeSpriteMetadata(CanonicalRenderAsset asset, File baseFile) {
        File metadataFile = metadataFile(baseFile);
        if (!metadataFile.exists()) {
            return;
        }
        try (FileInputStream inputStream = new FileInputStream(metadataFile)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = GSON.fromJson(new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8), Map.class);
            if (metadata == null) {
                return;
            }
            asset.sourceFormat = "native_sprite_metadata";
            asset.captureMethod = "native_sprite_metadata";
            asset.atlasTexture = stringValue(metadata.get("atlasTexture"), null);
            asset.atlasExportFile = stringValue(metadata.get("atlasExportFile"), null);
            asset.spriteMetadataFile = relativizeFromExportDirectory(metadataFile);
            asset.nativeSpriteAtlasFile = stringValue(metadata.get("nativeSpriteAtlasFile"), null);
            if ((asset.nativeSpriteAtlasFile == null || asset.nativeSpriteAtlasFile.isEmpty())) {
                File inferredSpriteAtlasFile = inferNativeSpriteAtlasFile(baseFile);
                if (inferredSpriteAtlasFile != null && inferredSpriteAtlasFile.exists()) {
                    asset.nativeSpriteAtlasFile = relativizeFromExportDirectory(inferredSpriteAtlasFile);
                }
            }
            asset.primaryArtifact = relativizeFromExportDirectory(baseFile);
            asset.rect = new HashMap<>();
            putIfNumber(asset.rect, "originX", metadata.get("originX"));
            putIfNumber(asset.rect, "originY", metadata.get("originY"));
            putIfNumber(asset.rect, "width", metadata.get("width"));
            putIfNumber(asset.rect, "height", metadata.get("height"));
            putIfNumber(asset.rect, "minU", metadata.get("minU"));
            putIfNumber(asset.rect, "maxU", metadata.get("maxU"));
            putIfNumber(asset.rect, "minV", metadata.get("minV"));
            putIfNumber(asset.rect, "maxV", metadata.get("maxV"));
            Integer frameCount = integerValue(metadata.get("frameCount"));
            if (frameCount != null) {
                asset.frameCount = frameCount;
            }
            Integer width = integerValue(metadata.get("width"));
            Integer height = integerValue(metadata.get("height"));
            if (width != null && height != null) {
                asset.baseSize = new HashMap<>();
                asset.baseSize.put("width", width);
                asset.baseSize.put("height", height);
            }
            Integer defaultFrameTime = integerValue(metadata.get("defaultFrameTime"));
            if (defaultFrameTime != null) {
                asset.frameDurationMs = defaultFrameTime * 50;
            }
            Object timeline = metadata.get("timeline");
            if (timeline instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nativeTimeline = (List<Map<String, Object>>) timeline;
                asset.timeline = new ArrayList<>(nativeTimeline);
            }
            if (Boolean.TRUE.equals(metadata.get("animated"))) {
                asset.animationMode = "native_sprite";
                asset.loop = Boolean.TRUE;
                asset.loopMode = "loop";
                if ((asset.timeline == null || asset.timeline.isEmpty()) && asset.frameCount != null && asset.frameCount > 0) {
                    asset.timeline = new ArrayList<>();
                    int durationMs = asset.frameDurationMs != null ? asset.frameDurationMs : 50;
                    for (int i = 0; i < asset.frameCount; i++) {
                        Map<String, Object> frame = new LinkedHashMap<>();
                        frame.put("timelineIndex", i);
                        frame.put("frameIndex", i);
                        frame.put("index", i);
                        frame.put("durationMs", durationMs);
                        asset.timeline.add(frame);
                    }
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read native sprite metadata for {}", baseFile.getAbsolutePath(), e);
        }
    }

    private boolean isNativeSpriteAnimated(CanonicalRenderAsset asset) {
        return "native_sprite".equals(asset.animationMode) || "native_sprite_animation".equals(asset.mode);
    }

    private boolean isNativeSpriteSnapshot(CanonicalRenderAsset asset) {
        return asset.spriteMetadataFile != null
                && asset.nativeSpriteAtlasFile != null
                && !isNativeSpriteAnimated(asset);
    }

    private List<File> findFrameFiles(File baseFile) {
        String cacheKey = baseFile.getAbsolutePath();
        List<File> cached = frameFilesCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String fileName = baseFile.getName();
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        File parent = baseFile.getParentFile();

        List<File> result = new ArrayList<>();
        for (int index = 1; ; index++) {
            File candidate = new File(parent, stem + "_frame" + index + ".png");
            if (!candidate.exists()) {
                break;
            }
            result.add(candidate);
        }
        List<File> cachedResult = Collections.unmodifiableList(result);
        frameFilesCache.put(cacheKey, cachedResult);
        return cachedResult;
    }

    private File firstFrameFile(File baseFile) {
        String fileName = baseFile.getName();
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return new File(baseFile.getParentFile(), stem + "_frame1.png");
    }

    private Map<String, Object> frameDescriptor(File file, int index) {
        Map<String, Object> frame = new HashMap<>();
        frame.put("index", index);
        frame.put("path", relativizeFromExportDirectory(file));
        return frame;
    }

    private Map<String, Object> timelineEntry(File file, int index, Integer durationMs) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("index", index);
        entry.put("path", relativizeFromExportDirectory(file));
        if (durationMs != null) {
            entry.put("durationMs", durationMs);
        }
        return entry;
    }

    private String buildFramePattern(File baseFile) {
        String relative = relativizeFromExportDirectory(baseFile);
        if (relative.endsWith(".png")) {
            return relative.substring(0, relative.length() - 4) + "_frame{index}.png";
        }
        if (relative.endsWith(".gif")) {
            return relative.substring(0, relative.length() - 4) + "_frame{index}.png";
        }
        return relative + "_frame{index}.png";
    }

    private File metadataFile(File baseFile) {
        String path = baseFile.getAbsolutePath();
        if (path.endsWith(".png")) {
            return new File(path.substring(0, path.length() - 4) + ".sprite.json");
        }
        if (path.endsWith(".gif")) {
            return new File(path.substring(0, path.length() - 4) + ".sprite.json");
        }
        return new File(path + ".sprite.json");
    }

    private File inferNativeSpriteAtlasFile(File baseFile) {
        String path = baseFile.getAbsolutePath();
        if (path.endsWith(".png")) {
            return new File(path.substring(0, path.length() - 4) + ".sprite-atlas.png");
        }
        if (path.endsWith(".gif")) {
            return new File(path.substring(0, path.length() - 4) + ".sprite-atlas.png");
        }
        return new File(path + ".sprite-atlas.png");
    }

    private String computeAssetFingerprint(File baseFile, File metadataFile, List<File> frameFiles) {
        StringBuilder builder = new StringBuilder();
        appendFingerprint(builder, baseFile);
        appendFingerprint(builder, metadataFile);
        if (frameFiles != null) {
            for (File frameFile : frameFiles) {
                appendFingerprint(builder, frameFile);
            }
        }
        return builder.toString();
    }

    private void appendFingerprint(StringBuilder builder, File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('|');
        }
        builder.append(file.getName())
                .append(':')
                .append(file.length())
                .append(':')
                .append(file.lastModified());
    }

    private Map<String, Object> readBaseSize(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return null;
            }
            Map<String, Object> size = new HashMap<>();
            size.put("width", image.getWidth());
            size.put("height", image.getHeight());
            return size;
        } catch (IOException e) {
            Logger.MOD.warn("Failed to inspect render asset dimensions for {}", file.getAbsolutePath(), e);
            return null;
        }
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

    private String stringValue(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private CanonicalRenderAsset copyAsset(CanonicalRenderAsset source) {
        CanonicalRenderAsset copy = new CanonicalRenderAsset();
        copy.schemaVersion = source.schemaVersion;
        copy.assetId = source.assetId;
        copy.variantKey = source.variantKey;
        copy.sourceType = source.sourceType;
        copy.family = source.family;
        copy.contentHash = source.contentHash;
        copy.mode = source.mode;
        copy.animationMode = source.animationMode;
        copy.captureMethod = source.captureMethod;
        copy.sourceFormat = source.sourceFormat;
        copy.sourcePath = source.sourcePath;
        copy.atlasTexture = source.atlasTexture;
        copy.atlasExportFile = source.atlasExportFile;
        copy.spriteMetadataFile = source.spriteMetadataFile;
        copy.nativeSpriteAtlasFile = source.nativeSpriteAtlasFile;
        copy.primaryArtifact = source.primaryArtifact;
        copy.staticFile = source.staticFile;
        copy.framePattern = source.framePattern;
        copy.frameCount = source.frameCount;
        copy.configuredFrameCount = source.configuredFrameCount;
        copy.capturedFrameCount = source.capturedFrameCount;
        copy.loopMode = source.loopMode;
        copy.detectionFrameCount = source.detectionFrameCount;
        copy.captureTimeoutMs = source.captureTimeoutMs;
        copy.atlasGroup = source.atlasGroup;
        copy.atlasCandidate = source.atlasCandidate;
        copy.atlasFile = source.atlasFile;
        copy.frames = copyNestedMapList(source.frames);
        copy.timeline = copyNestedMapList(source.timeline);
        copy.frameDurationMs = source.frameDurationMs;
        copy.loop = source.loop;
        copy.rect = copyNestedMap(source.rect);
        copy.baseSize = copyNestedMap(source.baseSize);
        copy.layers = copyNestedMapList(source.layers);
        return copy;
    }

    private Map<String, Object> copyNestedMap(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        return new LinkedHashMap<>(source);
    }

    private List<Map<String, Object>> copyNestedMapList(List<Map<String, Object>> source) {
        if (source == null) {
            return null;
        }
        List<Map<String, Object>> copy = new ArrayList<>(source.size());
        for (Map<String, Object> entry : source) {
            copy.add(entry == null ? null : new LinkedHashMap<>(entry));
        }
        return copy;
    }

    private Integer integerValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private void putIfNumber(Map<String, Object> target, String key, Object value) {
        if (value instanceof Number) {
            target.put(key, value);
        }
    }

    private static final class ItemExportEntry {
        String renderAssetRef;
        String imageFileName;
    }

}
