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

/**
 * Atlas packer for animated assets, preferring native sprite atlases when available and
 * falling back to rendered frame-sequence assets otherwise.
 */
public class CanonicalAnimatedAtlasPackWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String ATLAS_DIRECTORY = "animated-atlases";
    private static final String OUTPUT_FILE = "animated-atlas-manifest.json";
    private static final String GROUP_DIRECTORY = "animated-atlas-manifests-by-group";

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final List<CanonicalRenderAsset> precollectedAssets;
    private final Map<String, List<BufferedImage>> gifFrameCache = new HashMap<>();
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
        for (Map.Entry<String, List<CanonicalRenderAsset>> entry : byGroup.entrySet()) {
            AnimatedAtlasGroupManifest groupManifest = packGroup(atlasDir, entry.getKey(), entry.getValue());
            if (groupManifest != null) {
                manifest.groups.add(groupManifest);
            }
        }
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

    private AnimatedAtlasGroupManifest packGroup(File atlasDir, String atlasGroup, List<CanonicalRenderAsset> assets) throws IOException {
        if (assets.isEmpty()) {
            return null;
        }

        assets.sort(Comparator.comparing(asset -> asset.assetId));
        List<AtlasPackingSupport.AtlasSourceImage> sources = new ArrayList<>();
        for (CanonicalRenderAsset asset : assets) {
            if ("native_sprite_animation".equals(asset.mode)) {
                addNativeSpriteSources(sources, asset);
            } else {
                addRenderedFrameSources(sources, asset);
            }
        }

        if (sources.isEmpty()) {
            return null;
        }

        AtlasPackingSupport.PackLayout layout = AtlasPackingSupport.computeLayout(sources);
        String safeName = atlasGroup.replaceAll("[^a-zA-Z0-9_-]", "_");
        File atlasFile = new File(atlasDir, safeName + ".png");
        AtlasPackingSupport.writeAtlasImage(atlasFile, layout);

        AnimatedAtlasGroupManifest groupManifest = new AnimatedAtlasGroupManifest();
        groupManifest.atlasGroup = atlasGroup;
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
