package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Atlas packer for animated assets, preferring native sprite atlases when available and
 * falling back to rendered frame-sequence assets otherwise.
 */
public class CanonicalAnimatedAtlasPackWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String ATLAS_DIRECTORY = "animated-atlases";
    private static final String OUTPUT_FILE = "animated-atlas-manifest.json";
    private static final String GROUP_DIRECTORY = "animated-atlas-manifests-by-group";
    /**
     * Browsers refuse to decode PNGs above roughly 16k px per side, and WebGL texture limits are
     * commonly 8192 px. Animated atlas pages must stay within this height; groups that would
     * exceed it are split into multiple pages at asset boundaries (all frames of one asset stay
     * on the same page because runtime timelines reference a single atlas file per asset).
     */
    private static final int WEBGL_SAFE_MAX_ATLAS_HEIGHT = 8192;

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final List<CanonicalRenderAsset> precollectedAssets;
    private final Map<String, List<BufferedImage>> gifFrameCache = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public CanonicalAnimatedAtlasPackWriter(EntityManager entityManager, File exportDirectory) {
        this(entityManager, exportDirectory, null);
    }

    public CanonicalAnimatedAtlasPackWriter(
            EntityManager entityManager,
            File exportDirectory,
            List<CanonicalRenderAsset> precollectedAssets) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
        this.precollectedAssets = precollectedAssets;
    }

    public void export() throws IOException {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Packing NESQL++ animated atlases...");

        List<CanonicalRenderAsset> assets =
                precollectedAssets != null
                        ? new ArrayList<>(precollectedAssets)
                        : loadRenderAssets();
        Map<String, List<CanonicalRenderAsset>> byGroup = groupAnimatedAssets(assets);

        File canonicalDir = new File(exportDirectory, OUTPUT_DIRECTORY);
        File atlasDir = new File(canonicalDir, ATLAS_DIRECTORY);
        if (!atlasDir.exists()) {
            atlasDir.mkdirs();
        }

        AnimatedAtlasManifest manifest = new AnimatedAtlasManifest();
        manifest.groups.addAll(packGroups(atlasDir, byGroup));
        manifest.groupCount = manifest.groups.size();
        manifest.assetCount = countAssets(manifest.groups);

        Gson gson = new GsonBuilder().serializeNulls().create();
        File manifestFile = new File(canonicalDir, OUTPUT_FILE);
        try (FileOutputStream fos = new FileOutputStream(manifestFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(manifest, writer);
        }

        writeGroupShards(canonicalDir, manifest.groups, gson);

        Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ animated atlas manifest written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + manifestFile.getAbsolutePath());
    }

    private List<AnimatedAtlasGroupManifest> packGroups(
            File atlasDir,
            Map<String, List<CanonicalRenderAsset>> byGroup) throws IOException {
        List<Map.Entry<String, List<CanonicalRenderAsset>>> entries = new ArrayList<>(byGroup.entrySet());
        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        int workers = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() * 2, entries.size()));
        if (workers == 1) {
            List<AnimatedAtlasGroupManifest> groups = new ArrayList<>();
            for (Map.Entry<String, List<CanonicalRenderAsset>> entry : entries) {
                groups.addAll(packGroup(atlasDir, entry.getKey(), entry.getValue()));
            }
            return groups;
        }

        Logger.chatMessage(
                EnumChatFormatting.AQUA
                        + "Packing animated atlas groups with "
                        + workers
                        + " IO workers...");
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<List<AnimatedAtlasGroupManifest>>> futures = new ArrayList<>();
            for (Map.Entry<String, List<CanonicalRenderAsset>> entry : entries) {
                final String atlasGroup = entry.getKey();
                final List<CanonicalRenderAsset> assets = new ArrayList<>(entry.getValue());
                futures.add(executor.submit(new Callable<List<AnimatedAtlasGroupManifest>>() {
                    @Override
                    public List<AnimatedAtlasGroupManifest> call() throws Exception {
                        return packGroup(atlasDir, atlasGroup, assets);
                    }
                }));
            }

            List<AnimatedAtlasGroupManifest> groups = new ArrayList<>();
            for (Future<List<AnimatedAtlasGroupManifest>> future : futures) {
                try {
                    groups.addAll(future.get());
                } catch (Exception e) {
                    throw new IOException("Failed to pack animated atlas group", e);
                }
            }
            groups.sort(Comparator.comparing(group -> group.atlasGroup));
            return groups;
        } finally {
            executor.shutdownNow();
        }
    }

    private List<CanonicalRenderAsset> loadRenderAssets() throws IOException {
        File manifestFile = new File(exportDirectory, OUTPUT_DIRECTORY + File.separator + "render-assets.json");
        if (manifestFile.exists()) {
            Gson gson = new GsonBuilder().serializeNulls().create();
            try (FileInputStream fis = new FileInputStream(manifestFile);
                 InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                CanonicalRenderAssetManifest manifest = gson.fromJson(reader, CanonicalRenderAssetManifest.class);
                if (manifest != null && manifest.assets != null && !manifest.assets.isEmpty()) {
                    for (CanonicalRenderAsset asset : manifest.assets) {
                        repairNativeAnimatedAsset(asset);
                    }
                    return manifest.assets;
                }
            }
        }
        return new CanonicalRenderAssetCollector(entityManager, exportDirectory).collectAll();
    }

    private Map<String, List<CanonicalRenderAsset>> groupAnimatedAssets(List<CanonicalRenderAsset> assets) {
        Map<String, List<CanonicalRenderAsset>> byGroup = new LinkedHashMap<>();
        for (CanonicalRenderAsset asset : assets) {
            if (!"rendered_frames".equals(asset.mode)
                    && !"native_sprite_animation".equals(asset.mode)) {
                continue;
            }
            String atlasGroup = asset.atlasGroup != null ? asset.atlasGroup : "ungrouped";
            byGroup.computeIfAbsent(atlasGroup, ignored -> new ArrayList<>()).add(asset);
        }
        return byGroup;
    }

    private List<AnimatedAtlasGroupManifest> packGroup(File atlasDir, String atlasGroup, List<CanonicalRenderAsset> assets) throws IOException {
        if (assets.isEmpty()) {
            return new ArrayList<>();
        }

        assets.sort(Comparator.comparing(asset -> asset.assetId));
        String safeName = atlasGroup.replaceAll("[^a-zA-Z0-9_-]", "_");
        List<AnimatedAtlasGroupManifest> reusable = readFreshGroupManifests(atlasDir, atlasGroup, assets, safeName);
        if (!reusable.isEmpty()) {
            return reusable;
        }
        deleteStaleAnimatedAtlasFiles(atlasDir, safeName);

        List<List<AtlasPackingSupport.AtlasSourceImage>> perAssetSources = new ArrayList<>();
        for (CanonicalRenderAsset asset : assets) {
            List<AtlasPackingSupport.AtlasSourceImage> assetSources = new ArrayList<>();
            if ("native_sprite_animation".equals(asset.mode)) {
                addNativeSpriteSources(assetSources, asset);
            } else {
                addRenderedFrameSources(assetSources, asset);
            }
            if (!assetSources.isEmpty()) {
                perAssetSources.add(assetSources);
            }
        }

        if (perAssetSources.isEmpty()) {
            return new ArrayList<>();
        }

        List<List<List<AtlasPackingSupport.AtlasSourceImage>>> pages = new ArrayList<>();
        addHeightSafePage(pages, perAssetSources);

        List<AnimatedAtlasGroupManifest> manifests = new ArrayList<>();
        int pageIndex = 0;
        for (List<List<AtlasPackingSupport.AtlasSourceImage>> page : pages) {
            manifests.add(writeAtlasPage(
                    atlasDir,
                    atlasPageName(safeName, pageIndex),
                    atlasPageName(atlasGroup, pageIndex),
                    flattenSources(page)));
            pageIndex++;
        }
        return manifests;
    }

    /**
     * Splits per-asset source lists into pages whose packed height stays browser-decodable.
     * Splitting happens at asset boundaries only: every frame of an asset must land on the
     * same atlas page because runtime timelines reference exactly one atlas file per asset.
     */
    private void addHeightSafePage(
            List<List<List<AtlasPackingSupport.AtlasSourceImage>>> pages,
            List<List<AtlasPackingSupport.AtlasSourceImage>> perAssetSources) {
        if (perAssetSources.isEmpty()) {
            return;
        }
        AtlasPackingSupport.PackLayout layout = AtlasPackingSupport.computeLayout(flattenSources(perAssetSources));
        if (layout.height <= WEBGL_SAFE_MAX_ATLAS_HEIGHT || perAssetSources.size() == 1) {
            if (layout.height > WEBGL_SAFE_MAX_ATLAS_HEIGHT) {
                Logger.MOD.warn(
                        "Animated atlas page for asset {} exceeds safe height {}x{}; single asset cannot be split",
                        perAssetSources.get(0).get(0).asset.assetId,
                        layout.width,
                        layout.height);
            }
            pages.add(perAssetSources);
            return;
        }
        int middle = perAssetSources.size() / 2;
        addHeightSafePage(pages, new ArrayList<>(perAssetSources.subList(0, middle)));
        addHeightSafePage(pages, new ArrayList<>(perAssetSources.subList(middle, perAssetSources.size())));
    }

    private List<AtlasPackingSupport.AtlasSourceImage> flattenSources(
            List<List<AtlasPackingSupport.AtlasSourceImage>> perAssetSources) {
        List<AtlasPackingSupport.AtlasSourceImage> flat = new ArrayList<>();
        for (List<AtlasPackingSupport.AtlasSourceImage> assetSources : perAssetSources) {
            flat.addAll(assetSources);
        }
        return flat;
    }

    private String atlasPageName(String baseName, int pageIndex) {
        return pageIndex == 0 ? baseName : String.format("%s-%03d", baseName, pageIndex);
    }

    private void deleteStaleAnimatedAtlasFiles(File atlasDir, String safeName) {
        File[] atlasFiles = atlasDir.listFiles((dir, name) ->
                name.equals(safeName + ".png") || name.matches(java.util.regex.Pattern.quote(safeName) + "-\\d{3}\\.png"));
        if (atlasFiles != null) {
            for (File atlasFile : atlasFiles) {
                if (!atlasFile.delete()) {
                    Logger.MOD.debug("Failed to delete stale animated atlas page {}", atlasFile.getAbsolutePath());
                }
            }
        }
        File groupDir = new File(atlasDir.getParentFile(), GROUP_DIRECTORY);
        File[] shardFiles = groupDir.listFiles((dir, name) ->
                name.equals(safeName + ".json") || name.matches(java.util.regex.Pattern.quote(safeName) + "-\\d{3}\\.json"));
        if (shardFiles != null) {
            for (File shardFile : shardFiles) {
                if (!shardFile.delete()) {
                    Logger.MOD.debug("Failed to delete stale animated atlas shard {}", shardFile.getAbsolutePath());
                }
            }
        }
    }

    private AnimatedAtlasGroupManifest writeAtlasPage(
            File atlasDir,
            String pageSafeName,
            String pageAtlasGroup,
            List<AtlasPackingSupport.AtlasSourceImage> sources) throws IOException {
        File atlasFile = new File(atlasDir, pageSafeName + ".png");
        AtlasPackingSupport.PackLayout layout = AtlasPackingSupport.computeLayout(sources);
        AtlasPackingSupport.writeAtlasImage(atlasFile, layout);

        AnimatedAtlasGroupManifest groupManifest = new AnimatedAtlasGroupManifest();
        groupManifest.atlasGroup = pageAtlasGroup;
        groupManifest.atlasFile = relativizeFromExportDirectory(atlasFile);
        groupManifest.width = layout.width;
        groupManifest.height = layout.height;
        groupManifest.assets = new ArrayList<>();

        Map<String, AnimatedAtlasAssetPlacement> assetsById = new LinkedHashMap<>();
        for (AtlasPackingSupport.AtlasPlacement placement : layout.placements) {
            String assetId = placement.source.asset.assetId;
            AnimatedAtlasAssetPlacement assetPlacement = assetsById.get(assetId);
            if (assetPlacement == null) {
                assetPlacement = new AnimatedAtlasAssetPlacement();
                assetPlacement.assetId = assetId;
                assetPlacement.variantKey = placement.source.asset.variantKey;
                assetPlacement.frameDurationMs = placement.source.asset.frameDurationMs;
                assetPlacement.loopMode = placement.source.asset.loopMode;
                assetPlacement.timeline = copyTimeline(placement.source.asset.timeline);
                assetPlacement.frames = new ArrayList<>();
                assetsById.put(assetId, assetPlacement);
                groupManifest.assets.add(assetPlacement);
            }

            AnimatedAtlasFramePlacement framePlacement = new AnimatedAtlasFramePlacement();
            framePlacement.index = placement.source.frameIndex != null ? placement.source.frameIndex : 0;
            framePlacement.x = placement.x;
            framePlacement.y = placement.y;
            framePlacement.width = placement.source.image.getWidth();
            framePlacement.height = placement.source.image.getHeight();
            framePlacement.sourcePath = placement.source.file.getAbsolutePath()
                    .substring(exportDirectory.getAbsolutePath().length())
                    .replace('\\', '/')
                    .replaceFirst("^/+", "");
            assetPlacement.frames.add(framePlacement);
        }

        for (AnimatedAtlasAssetPlacement assetPlacement : groupManifest.assets) {
            assetPlacement.frameCount = assetPlacement.frames.size();
            assetPlacement.frames.sort(Comparator.comparingInt(frame -> frame.index));
        }

        return groupManifest;
    }

    private List<AnimatedAtlasGroupManifest> readFreshGroupManifests(
            File atlasDir,
            String atlasGroup,
            List<CanonicalRenderAsset> assets,
            String safeName) {
        File canonicalDir = atlasDir.getParentFile();
        File groupDir = new File(canonicalDir, GROUP_DIRECTORY);
        File[] shardFiles = groupDir.listFiles((dir, name) ->
                name.equals(safeName + ".json")
                        || name.matches(java.util.regex.Pattern.quote(safeName) + "-\\d{3}\\.json"));
        if (shardFiles == null || shardFiles.length == 0) {
            return new ArrayList<>();
        }
        java.util.Arrays.sort(shardFiles, Comparator.comparing(File::getName));

        long newestSourceModified = newestSourceModified(assets);
        if (newestSourceModified <= 0L) {
            return new ArrayList<>();
        }

        List<AnimatedAtlasGroupManifest> manifests = new ArrayList<>();
        for (File shardFile : shardFiles) {
            try (FileInputStream fis = new FileInputStream(shardFile);
                 InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
                AnimatedAtlasGroupManifest manifest = GSON.fromJson(reader, AnimatedAtlasGroupManifest.class);
                if (manifest == null || manifest.assets == null || manifest.assets.isEmpty()) {
                    return new ArrayList<>();
                }
                if (manifest.height > WEBGL_SAFE_MAX_ATLAS_HEIGHT) {
                    // Pages written before height-safe sharding existed must be regenerated.
                    return new ArrayList<>();
                }
                File atlasFile = resolveExportFile(manifest.atlasFile);
                if (!atlasFile.exists()) {
                    return new ArrayList<>();
                }
                long oldestOutputModified = Math.min(atlasFile.lastModified(), shardFile.lastModified());
                if (oldestOutputModified < newestSourceModified) {
                    return new ArrayList<>();
                }
                manifests.add(manifest);
            } catch (Exception e) {
                Logger.MOD.warn("Failed to reuse animated atlas group {}", atlasGroup, e);
                return new ArrayList<>();
            }
        }
        if (!manifests.isEmpty()) {
            Logger.MOD.info(
                    "Reusing {} fresh animated atlas page(s) for group {}", manifests.size(), atlasGroup);
        }
        return manifests;
    }

    private long newestSourceModified(List<CanonicalRenderAsset> assets) {
        long newest = 0L;
        for (CanonicalRenderAsset asset : assets) {
            List<File> sourceFiles = sourceFilesForFreshness(asset);
            if (sourceFiles.isEmpty()) {
                return -1L;
            }
            for (File sourceFile : sourceFiles) {
                if (sourceFile == null || !sourceFile.exists()) {
                    return -1L;
                }
                newest = Math.max(newest, sourceFile.lastModified());
            }
        }
        return newest;
    }

    private List<File> sourceFilesForFreshness(CanonicalRenderAsset asset) {
        List<File> files = new ArrayList<>();
        if ("native_sprite_animation".equals(asset.mode)) {
            if (asset.nativeSpriteAtlasFile != null && !asset.nativeSpriteAtlasFile.isEmpty()) {
                files.add(resolveExportFile(asset.nativeSpriteAtlasFile));
            }
            if (asset.spriteMetadataFile != null && !asset.spriteMetadataFile.isEmpty()) {
                files.add(resolveExportFile(asset.spriteMetadataFile));
            }
            return files;
        }

        if (asset.timeline == null) {
            return files;
        }
        for (Map<String, Object> frame : asset.timeline) {
            Object path = frame.get("path");
            if (path instanceof String) {
                files.add(resolveExportFile((String) path));
            }
        }
        return files;
    }

    private List<AnimatedAtlasTimelineEntry> copyTimeline(List<Map<String, Object>> sourceTimeline) {
        if (sourceTimeline == null || sourceTimeline.isEmpty()) {
            return null;
        }

        List<AnimatedAtlasTimelineEntry> copied = new ArrayList<AnimatedAtlasTimelineEntry>(sourceTimeline.size());
        for (Map<String, Object> frame : sourceTimeline) {
            if (frame == null) {
                continue;
            }

            AnimatedAtlasTimelineEntry entry = new AnimatedAtlasTimelineEntry();
            entry.timelineIndex = asInt(frame.get("timelineIndex"));
            entry.frameIndex = frame.get("frameIndex") instanceof Number
                    ? ((Number) frame.get("frameIndex")).intValue()
                    : asInt(frame.get("index"));
            entry.index = asInt(frame.get("index"));
            entry.durationMs = asInt(frame.get("durationMs"));
            copied.add(entry);
        }

        return copied.isEmpty() ? null : copied;
    }

    private void addRenderedFrameSources(List<AtlasPackingSupport.AtlasSourceImage> sources, CanonicalRenderAsset asset)
            throws IOException {
        if (asset.timeline == null) {
            return;
        }
        for (Map<String, Object> frame : asset.timeline) {
            Object path = frame.get("path");
            Object index = frame.get("index");
            if (!(path instanceof String) || !(index instanceof Number)) {
                continue;
            }
            File sourceFile = new File(exportDirectory, ((String) path).replace('/', File.separatorChar));
            if (!sourceFile.exists()) {
                continue;
            }
            BufferedImage image = readRenderedFrameImage(sourceFile, ((Number) index).intValue());
            if (image == null) {
                continue;
            }
            sources.add(new AtlasPackingSupport.AtlasSourceImage(asset, sourceFile, image, ((Number) index).intValue()));
        }
    }

    private BufferedImage readRenderedFrameImage(File sourceFile, int frameIndex) throws IOException {
        if (sourceFile.getName().toLowerCase().endsWith(".gif")) {
            List<BufferedImage> frames = loadGifFrames(sourceFile);
            if (frameIndex < 0 || frameIndex >= frames.size()) {
                return null;
            }
            return frames.get(frameIndex);
        }
        return ImageIO.read(sourceFile);
    }

    private List<BufferedImage> loadGifFrames(File sourceFile) throws IOException {
        String cacheKey = sourceFile.getAbsolutePath();
        List<BufferedImage> cached = gifFrameCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<BufferedImage> frames = new ArrayList<>();
        try (ImageInputStream stream = ImageIO.createImageInputStream(sourceFile)) {
            if (stream == null) {
                gifFrameCache.put(cacheKey, frames);
                return frames;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                gifFrameCache.put(cacheKey, frames);
                return frames;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false, false);
                int frameCount = reader.getNumImages(true);
                for (int index = 0; index < frameCount; index++) {
                    BufferedImage frame = reader.read(index);
                    if (frame != null) {
                        frames.add(frame);
                    }
                }
            } finally {
                reader.dispose();
            }
        }

        gifFrameCache.put(cacheKey, frames);
        return frames;
    }

    private void addNativeSpriteSources(List<AtlasPackingSupport.AtlasSourceImage> sources, CanonicalRenderAsset asset)
            throws IOException {
        if (asset.nativeSpriteAtlasFile == null || asset.timeline == null || asset.baseSize == null) {
            return;
        }
        File atlasFile = resolveExportFile(asset.nativeSpriteAtlasFile);
        if (!atlasFile.exists()) {
            return;
        }
        BufferedImage atlasImage = ImageIO.read(atlasFile);
        if (atlasImage == null) {
            return;
        }
        int frameWidth = asInt(asset.baseSize.get("width"));
        int frameHeight = asInt(asset.baseSize.get("height"));
        if (frameWidth <= 0 || frameHeight <= 0) {
            return;
        }
        for (Map<String, Object> frame : asset.timeline) {
            Integer frameIndex = frame.get("frameIndex") instanceof Number
                    ? ((Number) frame.get("frameIndex")).intValue()
                    : (frame.get("index") instanceof Number ? ((Number) frame.get("index")).intValue() : null);
            if (frameIndex == null) {
                continue;
            }
            int y = frameIndex * frameHeight;
            if (y + frameHeight > atlasImage.getHeight()) {
                continue;
            }
            BufferedImage subImage = atlasImage.getSubimage(0, y, frameWidth, frameHeight);
            sources.add(new AtlasPackingSupport.AtlasSourceImage(asset, atlasFile, subImage, frameIndex));
        }
    }

    private void repairNativeAnimatedAsset(CanonicalRenderAsset asset) {
        if (!"native_sprite_animation".equals(asset.mode)) {
            return;
        }

        Integer previousFrameCount = asset.frameCount;
        hydrateNativeAnimatedMetadata(asset);

        if ((asset.nativeSpriteAtlasFile == null || asset.nativeSpriteAtlasFile.isEmpty())) {
            String basePath = asset.primaryArtifact != null ? asset.primaryArtifact : asset.staticFile;
            if (basePath != null && !basePath.isEmpty()) {
                asset.nativeSpriteAtlasFile = inferSpriteAtlasPath(basePath);
            }
        }

        if ((asset.timeline == null || asset.timeline.isEmpty() || shouldRegenerateTimeline(asset, previousFrameCount))
                && asset.frameCount != null && asset.frameCount > 0) {
            int durationMs = asset.frameDurationMs != null ? asset.frameDurationMs : 50;
            asset.timeline = new ArrayList<>();
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

    private boolean shouldRegenerateTimeline(CanonicalRenderAsset asset, Integer previousFrameCount) {
        if (asset.frameCount == null || asset.frameCount <= 0) {
            return false;
        }
        if (asset.timeline == null) {
            return true;
        }
        if (asset.timeline.size() != asset.frameCount) {
            return true;
        }
        return previousFrameCount != null && previousFrameCount < asset.frameCount;
    }

    private void hydrateNativeAnimatedMetadata(CanonicalRenderAsset asset) {
        if (asset.spriteMetadataFile == null || asset.spriteMetadataFile.isEmpty()) {
            return;
        }

        File metadataFile = new File(exportDirectory, asset.spriteMetadataFile.replace('/', File.separatorChar));
        if (!metadataFile.exists()) {
            return;
        }

        try (FileInputStream fis = new FileInputStream(metadataFile);
             InputStreamReader reader = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = GSON.fromJson(reader, Map.class);
            if (metadata == null) {
                return;
            }

            Integer frameCount = asIntOrNull(metadata.get("frameCount"));
            Integer defaultFrameTime = asIntOrNull(metadata.get("defaultFrameTime"));
            Integer width = asIntOrNull(metadata.get("width"));
            Integer height = asIntOrNull(metadata.get("height"));

            if (frameCount != null && frameCount > 0) {
                asset.frameCount = frameCount;
            }
            if (defaultFrameTime != null && defaultFrameTime > 0) {
                asset.frameDurationMs = defaultFrameTime * 50;
                asset.frameDurationSource = "native_sprite_metadata";
            }
            if (width != null && height != null) {
                if (asset.baseSize == null) {
                    asset.baseSize = new LinkedHashMap<>();
                }
                asset.baseSize.put("width", width);
                asset.baseSize.put("height", height);
            }

            Object timeline = metadata.get("timeline");
            if (timeline instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nativeTimeline = (List<Map<String, Object>>) timeline;
                if (!nativeTimeline.isEmpty()) {
                    asset.timeline = new ArrayList<>(nativeTimeline);
                }
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to hydrate animated atlas metadata for {}", asset.assetId, e);
        }
    }

    private String inferSpriteAtlasPath(String basePath) {
        if (basePath.endsWith(".png")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite-atlas.png";
        }
        if (basePath.endsWith(".gif")) {
            return basePath.substring(0, basePath.length() - 4) + ".sprite-atlas.png";
        }
        return basePath + ".sprite-atlas.png";
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

    private int countAssets(List<AnimatedAtlasGroupManifest> groups) {
        int total = 0;
        for (AnimatedAtlasGroupManifest group : groups) {
            total += group.assets.size();
        }
        return total;
    }

    private void writeGroupShards(File canonicalDir, List<AnimatedAtlasGroupManifest> groups, Gson gson) throws IOException {
        File groupDir = new File(canonicalDir, GROUP_DIRECTORY);
        if (!groupDir.exists()) {
            groupDir.mkdirs();
        }

        for (AnimatedAtlasGroupManifest group : groups) {
            String safeName = group.atlasGroup.replaceAll("[^a-zA-Z0-9_-]", "_");
            File shardFile = new File(groupDir, safeName + ".json");
            try (FileOutputStream fos = new FileOutputStream(shardFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                gson.toJson(group, writer);
            }
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

    private int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private Integer asIntOrNull(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private static final class AnimatedAtlasManifest {
        String schemaVersion = "nesqlpp/animated-atlas-pack/v1-draft";
        int groupCount;
        int assetCount;
        List<AnimatedAtlasGroupManifest> groups = new ArrayList<>();
    }

    private static final class AnimatedAtlasGroupManifest {
        String atlasGroup;
        String atlasFile;
        int width;
        int height;
        List<AnimatedAtlasAssetPlacement> assets = new ArrayList<>();
    }

    private static final class AnimatedAtlasAssetPlacement {
        String assetId;
        String variantKey;
        Integer frameDurationMs;
        String loopMode;
        int frameCount;
        List<AnimatedAtlasTimelineEntry> timeline;
        List<AnimatedAtlasFramePlacement> frames = new ArrayList<>();
    }

    private static final class AnimatedAtlasFramePlacement {
        int index;
        String sourcePath;
        int x;
        int y;
        int width;
        int height;
    }

    private static final class AnimatedAtlasTimelineEntry {
        int timelineIndex;
        int frameIndex;
        int index;
        int durationMs;
    }
}
