package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalExportMapper;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalFluid;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.util.render.GifRenderer;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.github.dcysteine.nesql.sql.base.fluid.Fluid;
import com.github.dcysteine.nesql.sql.base.item.Item;
import jakarta.persistence.EntityManager;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
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
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.regex.Pattern;

public final class CanonicalRenderAssetCollector {
    private static final Gson GSON = new Gson();

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final Map<String, CanonicalRenderAsset> assetTemplateCache = new HashMap<>();
    private final Map<String, List<File>> frameFilesCache = new HashMap<>();
    private final Map<String, FamilyImageIndex> familyIndexCache = new HashMap<>();
    private final Map<String, GifAnimationInfo> gifAnimationCache = new HashMap<>();
    private final Map<String, Map<String, Object>> imageSizeCache = new HashMap<>();

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
        List<CanonicalRenderAsset> assets = new ArrayList<>();
        File itemsRoot = new File(exportDirectory, "items");
        if (itemsRoot.exists()) {
            List<File> itemFiles = new ArrayList<>();
            collectJsonGzFiles(itemsRoot, itemFiles);
            itemFiles.sort(Comparator.comparing(File::getAbsolutePath));

            int processedFiles = 0;
            for (File itemFile : itemFiles) {
                processedFiles++;
                assets.addAll(readItemAssetsFromFile(itemFile));
                if (processedFiles % 25 == 0) {
                    Logger.MOD.info(
                            "Collected render assets from {} exported item shards ({} assets so far)...",
                            processedFiles,
                            assets.size());
                }
            }
        }

        if (!assets.isEmpty()) {
            return assets;
        }

        return collectFamilyAssetsFromImageFiles("item");
    }

    private List<CanonicalRenderAsset> readItemAssetsFromFile(File itemFile) {
        List<CanonicalRenderAsset> assets = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(itemFile);
             GZIPInputStream gis = new GZIPInputStream(fis);
             InputStreamReader reader = new InputStreamReader(gis, java.nio.charset.StandardCharsets.UTF_8);
             JsonReader jsonReader = new JsonReader(reader)) {
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                ItemExportEntry entry = readItemExportEntry(jsonReader);
                if (entry == null || entry.renderAssetRef == null || entry.imageFileName == null) {
                    continue;
                }
                CanonicalRenderAsset asset = buildAssetFromImagePath(entry.renderAssetRef, "item", entry.imageFileName);
                if (asset != null) {
                    assets.add(asset);
                }
            }
            jsonReader.endArray();
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read exported item assets from {}", itemFile.getAbsolutePath(), e);
        }
        return assets;
    }

    private List<CanonicalRenderAsset> collectFluidAssetsFromFiles() {
        return collectFamilyAssetsFromImageFiles("fluid");
    }

    private List<CanonicalRenderAsset> collectFamilyAssetsFromImageFiles(String familyDirectory) {
        List<CanonicalRenderAsset> assets = new ArrayList<>();
        FamilyImageIndex index = getFamilyImageIndex(familyDirectory);
        if (!index.familyRoot.exists()) {
            return assets;
        }

        List<File> primaryArtifacts = new ArrayList<>(index.primaryArtifactsByStem.values());
        primaryArtifacts.sort(Comparator.comparing(File::getAbsolutePath));

        for (File baseFile : primaryArtifacts) {
            String variantKey = buildVariantKey(index.familyRoot, baseFile);
            if (variantKey == null || variantKey.isEmpty()) {
                continue;
            }
            String assetId = buildAssetId(familyDirectory, variantKey);
            if (assetId == null || assetId.isEmpty()) {
                continue;
            }
            CanonicalRenderAsset asset =
                    buildAssetFromImagePath(
                            assetId,
                            familyDirectory,
                            variantKey.replace(File.separatorChar, '/'));
            if (asset != null) {
                assets.add(asset);
            }
        }

        return assets;
    }

    private ItemExportEntry readItemExportEntry(JsonReader reader) throws IOException {
        ItemExportEntry entry = new ItemExportEntry();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if ("renderAssetRef".equals(name)) {
                entry.renderAssetRef = safeNextString(reader);
            } else if ("imageFileName".equals(name)) {
                entry.imageFileName = safeNextString(reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return entry;
    }

    private String buildAssetId(String familyDirectory, String variantKey) {
        String normalizedVariant = variantKey.replace(File.separatorChar, '/');
        int separator = normalizedVariant.indexOf('/');
        if (separator <= 0 || separator == normalizedVariant.length() - 1) {
            return null;
        }

        String modId = normalizedVariant.substring(0, separator);
        String internalName = normalizedVariant.substring(separator + 1).replace('/', '~');
        String idPrefix = "item".equals(familyDirectory) ? "i" : "f";
        return "nesqlpp:" + familyDirectory + "/" + idPrefix + "~" + modId + "~" + internalName;
    }

    private String safeNextString(JsonReader reader) throws IOException {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return reader.nextString();
    }

    private FamilyImageIndex getFamilyImageIndex(String familyDirectory) {
        FamilyImageIndex cached = familyIndexCache.get(familyDirectory);
        if (cached != null) {
            return cached;
        }

        File familyRoot = new File(exportDirectory, "image" + File.separator + familyDirectory);
        FamilyImageIndex index = new FamilyImageIndex(familyRoot);
        if (familyRoot.exists()) {
            indexFamilyArtifacts(index, familyRoot);
        }
        familyIndexCache.put(familyDirectory, index);
        return index;
    }

    private void indexFamilyArtifacts(FamilyImageIndex index, File directory) {
        File[] children = directory.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                indexFamilyArtifacts(index, child);
                continue;
            }
            if (!child.isFile()) {
                continue;
            }

            String relativePath = relativizeWithinDirectory(index.familyRoot, child);
            if (relativePath == null || relativePath.isEmpty()) {
                continue;
            }

            if (relativePath.endsWith(".sprite-atlas.png")) {
                index.spriteAtlasesByStem.put(
                        relativePath.substring(0, relativePath.length() - ".sprite-atlas.png".length()),
                        child);
                continue;
            }
            if (relativePath.endsWith(".sprite.json") || relativePath.endsWith(".render.json")) {
                continue;
            }
            if (relativePath.matches(".*_frame_?\\d+\\.png")) {
                continue;
            }
            if (!relativePath.endsWith(".png") && !relativePath.endsWith(".gif")) {
                continue;
            }

            index.exactArtifacts.put(relativePath, child);
            String stem = stripImageExtension(relativePath);
            File existingPrimary = index.primaryArtifactsByStem.get(stem);
            if (existingPrimary == null) {
                index.primaryArtifactsByStem.put(stem, child);
            } else {
                index.primaryArtifactsByStem.put(stem, preferPrimaryArtifact(existingPrimary, child));
            }

            String siblingStemKey = siblingVariantStemKey(relativePath);
            if (siblingStemKey != null) {
                index.siblingVariantCandidatesByStem
                        .computeIfAbsent(siblingStemKey, ignored -> new ArrayList<File>())
                        .add(child);
            }
        }
    }

    private File preferPrimaryArtifact(File current, File candidate) {
        boolean currentGif = isGifFile(current);
        boolean candidateGif = isGifFile(candidate);
        if (currentGif != candidateGif) {
            if (candidateGif && inspectGifAnimation(candidate).animated) {
                return candidate;
            }
            if (currentGif && inspectGifAnimation(current).animated) {
                return current;
            }
            return currentGif ? candidate : current;
        }
        return candidate.getName().compareToIgnoreCase(current.getName()) < 0 ? candidate : current;
    }

    private String siblingVariantStemKey(String relativePath) {
        String stem = stripImageExtension(relativePath);
        int slash = stem.lastIndexOf('/');
        String parent = slash >= 0 ? stem.substring(0, slash + 1) : "";
        String fileStem = slash >= 0 ? stem.substring(slash + 1) : stem;
        int variantSeparator = fileStem.indexOf('~');
        if (variantSeparator <= 0) {
            return null;
        }
        return parent + fileStem.substring(0, variantSeparator);
    }

    private String relativizeWithinDirectory(File root, File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        if (!filePath.startsWith(rootPath)) {
            return null;
        }
        String relative = filePath.substring(rootPath.length());
        while (relative.startsWith(File.separator)) {
            relative = relative.substring(1);
        }
        return normalizeFamilyKey(relative);
    }

    private String normalizeFamilyKey(String path) {
        return path.replace('\\', '/');
    }

    private boolean hasImageExtension(String path) {
        return path.endsWith(".png") || path.endsWith(".gif");
    }

    private String stripImageExtension(String path) {
        if (path.endsWith(".png") || path.endsWith(".gif")) {
            return path.substring(0, path.length() - 4);
        }
        return path;
    }

    private String imageExtension(String path) {
        if (path.endsWith(".png")) {
            return ".png";
        }
        if (path.endsWith(".gif")) {
            return ".gif";
        }
        return "";
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

    private void collectPrimaryImageArtifacts(File directory, Map<String, File> output) {
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
            if (name.endsWith(".sprite-atlas.png") || name.endsWith(".sprite.json")) {
                continue;
            }
            if (name.matches(".*_frame_?\\d+\\.png")) {
                continue;
            }
            if (!name.endsWith(".png") && !name.endsWith(".gif")) {
                continue;
            }

            String stem = primaryArtifactStem(child);
            if (stem == null) {
                continue;
            }

            File existing = output.get(stem);
            if (existing == null || shouldPreferPrimaryArtifact(child, existing)) {
                output.put(stem, child);
            }
        }
    }

    private boolean shouldPreferPrimaryArtifact(File candidate, File existing) {
        return isGifFile(candidate) && !isGifFile(existing);
    }

    private String primaryArtifactStem(File file) {
        String name = file.getName();
        if (name.endsWith(".gif")) {
            return new File(file.getParentFile(), name.substring(0, name.length() - 4)).getAbsolutePath();
        }
        if (name.endsWith(".png")) {
            return new File(file.getParentFile(), name.substring(0, name.length() - 4)).getAbsolutePath();
        }
        return null;
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

        FamilyImageIndex familyIndex = getFamilyImageIndex(familyDirectory);
        File baseFile = resolvePrimaryArtifact(familyIndex, normalizedImagePath);
        if (!baseFile.exists()) {
            return null;
        }
        String variantKey = buildVariantKey(familyIndex.familyRoot, baseFile);
        if (variantKey == null || variantKey.isEmpty()) {
            return null;
        }

        CanonicalRenderAsset asset = new CanonicalRenderAsset();
        asset.schemaVersion = "nesqlpp/render-asset/v2-draft";
        asset.contractVersion = "nesqlpp/render-contract/v2-draft";
        asset.assetId = assetId;
        asset.variantKey = familyDirectory + "/" + variantKey.replace(File.separatorChar, '/');
        asset.sourceType = familyDirectory;
        asset.family = familyDirectory;
        File metadataFile = metadataFile(baseFile);
        File renderContractFile = renderContractFile(baseFile);
        asset.contentHash = computeAssetFingerprint(baseFile, metadataFile, renderContractFile, null);
        asset.mode = "static_snapshot";
        asset.renderMode = "native_sprite";
        asset.animationMode = "none";
        asset.captureMethod = "single_frame_render";
        asset.captureSource = "framebuffer_singleframe";
        asset.playbackHint = "native_sprite";
        asset.sourceFormat = "png-sequence";
        asset.primaryArtifact = relativizeFromExportDirectory(baseFile);
        asset.loop = Boolean.TRUE;
        asset.loopMode = "loop";
        asset.frameDurationMs = null;
        asset.frameDurationSource = null;
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
        asset.contractFile = renderContractFile.exists() ? relativizeFromExportDirectory(renderContractFile) : null;
        asset.nativeSpriteAtlasFile = null;
        asset.staticFile = relativizeFromExportDirectory(baseFile);
        applyRenderContractMetadata(asset, renderContractFile);
        applyNativeSpriteMetadata(asset, baseFile);
        boolean nativeAnimated = isNativeSpriteAnimated(asset);
        GifAnimationInfo gifAnimation = inspectGifAnimation(baseFile);
        boolean nativeSnapshot = isNativeSpriteSnapshot(asset) && !gifAnimation.animated;

        List<File> frameFiles = Collections.emptyList();
        boolean shouldScanFrames =
                isGifFile(baseFile)
                        && gifAnimation.frameCount <= 1
                        && (asset.spriteMetadataFile == null || firstFrameFile(baseFile).exists() || legacyFirstFrameFile(baseFile).exists());
        if (shouldScanFrames) {
            frameFiles = findFrameFiles(baseFile);
            asset.contentHash = computeAssetFingerprint(baseFile, metadataFile, renderContractFile, frameFiles);
        }
        if (!nativeAnimated && !nativeSnapshot && !gifAnimation.animated) {
            asset.frames = new ArrayList<>();
            asset.timeline = new ArrayList<>();
            asset.frames.add(frameDescriptor(baseFile, 0));
            asset.timeline.add(timelineEntry(baseFile, 0, null));
            for (int i = 0; i < frameFiles.size(); i++) {
                asset.frames.add(frameDescriptor(frameFiles.get(i), i + 1));
            asset.timeline.add(timelineEntry(frameFiles.get(i), i + 1, GifRenderer.DEFAULT_CAPTURE_FRAME_DELAY_MS));
            }
            asset.frameCount = asset.frames.size();
            asset.capturedFrameCount = asset.frames.size();
        }

        if (nativeAnimated) {
            asset.mode = "native_sprite_animation";
            asset.animationMode = "native_sprite";
            asset.captureMethod = "native_sprite_metadata";
            asset.renderMode = "native_sprite";
            asset.captureSource = "native_sprite_metadata";
            asset.playbackHint = "native_sprite";
            asset.atlasGroup = familyDirectory + "-native-animated";
            asset.atlasCandidate = Boolean.TRUE;
            asset.frames = null;
            asset.framePattern = null;
            asset.capturedFrameCount = 0;
            asset.configuredFrameCount = 0;
        } else if (gifAnimation.animated) {
            asset.mode = "rendered_frames";
            asset.animationMode = "gif_sequence";
            asset.captureMethod = "multi_frame_render";
            asset.renderMode = "captured_final_atlas";
            asset.captureSource = "framebuffer_multiframe";
            asset.playbackHint = "atlas_timeline";
            asset.sourceFormat = "gif";
            asset.frameDurationMs = gifAnimation.defaultFrameDurationMs;
            asset.frameDurationSource = "gif_metadata";
            asset.loop = Boolean.TRUE;
            asset.loopMode = "loop";
            asset.framePattern = null;
            asset.atlasGroup = familyDirectory + "-animated";
            asset.atlasCandidate = Boolean.FALSE;
            asset.frames = gifAnimation.frames;
            asset.timeline = gifAnimation.timeline;
            asset.frameCount = gifAnimation.frameCount;
            asset.capturedFrameCount = gifAnimation.frameCount;
            asset.configuredFrameCount = gifAnimation.frameCount;
            if (asset.baseSize == null && gifAnimation.baseSize != null) {
                asset.baseSize = gifAnimation.baseSize;
            }
        } else if (nativeSnapshot) {
            asset.mode = "native_sprite_snapshot";
            asset.animationMode = "none";
            asset.captureMethod = "native_sprite_metadata";
            asset.renderMode = "native_sprite";
            asset.captureSource = "native_sprite_metadata";
            asset.playbackHint = "native_sprite";
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
            asset.renderMode = "captured_final_atlas";
            asset.captureSource = "framebuffer_multiframe";
            asset.playbackHint = "atlas_timeline";
            asset.frameDurationMs = GifRenderer.DEFAULT_CAPTURE_FRAME_DELAY_MS;
            asset.frameDurationSource = "minecraft_tick_capture";
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
        applyLegacyRenderContractDefaults(asset);
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

    private File resolvePrimaryArtifact(FamilyImageIndex familyIndex, String normalizedImagePath) {
        String normalizedKey = normalizeFamilyKey(normalizedImagePath);
        File exactPath = familyIndex.exactArtifacts.get(normalizedKey);
        File alternateGif = replaceExtension(familyIndex, normalizedKey, ".gif");
        if (exactPath != null) {
            String exactName = exactPath.getName().toLowerCase();
            if (exactName.endsWith(".png") && alternateGif != null && inspectGifAnimation(alternateGif).animated) {
                return alternateGif;
            }
            // Keep exact metadata/damage variants isolated.  Some GTNH items expose
            // one animated sibling next to many static damage variants (for example
            // AE2 ItemMultiMaterial~47 singularity).  Promoting every exact PNG to
            // the first animated sibling makes unrelated variants share that sprite
            // atlas in browser-atlas-index.json, causing whole NEI pages to render
            // as the same animated item.  Only the same-stem alternate GIF above is
            // allowed to override an exact PNG.
            return exactPath;
        }

        if (alternateGif != null) {
            return alternateGif;
        }

        File alternatePng = replaceExtension(familyIndex, normalizedKey, ".png");
        if (alternatePng != null) {
            return alternatePng;
        }

        if (!hasImageExtension(normalizedKey)) {
            File primary = familyIndex.primaryArtifactsByStem.get(normalizedKey);
            if (primary != null) {
                return primary;
            }
        }

        File siblingVariant = findSiblingVariantArtifact(familyIndex, normalizedKey);
        if (siblingVariant != null) {
            return siblingVariant;
        }

        File spriteAtlas = familyIndex.spriteAtlasesByStem.get(stripImageExtension(normalizedKey));
        if (spriteAtlas != null) {
            return spriteAtlas;
        }

        return new File(familyIndex.familyRoot, normalizedKey.replace('/', File.separatorChar));
    }

    private File replaceExtension(FamilyImageIndex familyIndex, String normalizedImagePath, String extension) {
        int dot = normalizedImagePath.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String swappedPath = normalizedImagePath.substring(0, dot) + extension;
        return familyIndex.exactArtifacts.get(swappedPath);
    }

    private File findAnimatedSiblingVariantArtifact(FamilyImageIndex familyIndex, String normalizedImagePath) {
        String requestedStem = stripImageExtension(normalizedImagePath);
        List<File> candidates = familyIndex.siblingVariantCandidatesByStem.get(requestedStem);
        if ((candidates == null || candidates.isEmpty())) {
            String siblingKey = siblingVariantStemKey(normalizedImagePath);
            if (siblingKey != null) {
                candidates = familyIndex.siblingVariantCandidatesByStem.get(siblingKey);
            }
        }
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        File best = null;
        for (File candidate : candidates) {
            String name = candidate.getName().toLowerCase();
            if (!name.endsWith(".gif") || !inspectGifAnimation(candidate).animated) {
                continue;
            }
            if (best == null || candidate.getName().compareToIgnoreCase(best.getName()) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    private File findSiblingVariantArtifact(FamilyImageIndex familyIndex, String normalizedImagePath) {
        String requestedStem = stripImageExtension(normalizedImagePath);
        List<File> candidates = familyIndex.siblingVariantCandidatesByStem.get(requestedStem);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        String extension = imageExtension(normalizedImagePath);
        File bestMatch = null;
        for (File sibling : candidates) {
            if (bestMatch == null) {
                bestMatch = sibling;
                continue;
            }
            bestMatch = preferVariantArtifact(bestMatch, sibling, extension);
        }

        return bestMatch;
    }

    private File preferVariantArtifact(File current, File candidate, String requestedExtension) {
        String currentName = current.getName().toLowerCase();
        String candidateName = candidate.getName().toLowerCase();
        boolean currentMatchesRequested = !requestedExtension.isEmpty() && currentName.endsWith(requestedExtension);
        boolean candidateMatchesRequested = !requestedExtension.isEmpty() && candidateName.endsWith(requestedExtension);

        if (candidateMatchesRequested && !currentMatchesRequested) {
            return candidate;
        }
        if (currentMatchesRequested && !candidateMatchesRequested) {
            return current;
        }

        boolean currentAnimatedGif = currentName.endsWith(".gif") && inspectGifAnimation(current).animated;
        boolean candidateAnimatedGif = candidateName.endsWith(".gif") && inspectGifAnimation(candidate).animated;
        if (candidateAnimatedGif && !currentAnimatedGif) {
            return candidate;
        }
        if (currentAnimatedGif && !candidateAnimatedGif) {
            return current;
        }

        return candidate.getName().compareToIgnoreCase(current.getName()) < 0 ? candidate : current;
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
            boolean primaryNativeSprite = shouldTreatNativeSpriteAsPrimary(asset);
            if (primaryNativeSprite) {
                asset.sourceFormat = "native_sprite_metadata";
                asset.captureMethod = "native_sprite_metadata";
            }
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
                asset.frameDurationSource = "native_sprite_metadata";
            }
            Object timeline = metadata.get("timeline");
            if (timeline instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nativeTimeline = (List<Map<String, Object>>) timeline;
                asset.timeline = new ArrayList<>(nativeTimeline);
            }
            if (Boolean.TRUE.equals(metadata.get("animated"))) {
                if (primaryNativeSprite) {
                    asset.animationMode = "native_sprite";
                } else if (asset.animationMode == null || asset.animationMode.isEmpty() || "none".equals(asset.animationMode)) {
                    asset.animationMode = "native_sprite_aux";
                }
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

    private boolean shouldTreatNativeSpriteAsPrimary(CanonicalRenderAsset asset) {
        return asset == null
                || asset.renderMode == null
                || asset.renderMode.isEmpty()
                || "native_sprite".equals(asset.renderMode);
    }

    private void applyRenderContractMetadata(CanonicalRenderAsset asset, File renderContractFile) {
        if (renderContractFile == null || !renderContractFile.exists()) {
            return;
        }

        try (FileInputStream inputStream = new FileInputStream(renderContractFile)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = GSON.fromJson(
                    new java.io.InputStreamReader(inputStream, java.nio.charset.StandardCharsets.UTF_8),
                    Map.class);
            if (metadata == null) {
                return;
            }

            asset.contractVersion = stringValue(metadata.get("schemaVersion"), asset.contractVersion);
            asset.contractFile = relativizeFromExportDirectory(renderContractFile);
            asset.renderMode = stringValue(metadata.get("renderMode"), asset.renderMode);
            asset.rendererFamily = stringValue(metadata.get("rendererFamily"), asset.rendererFamily);
            asset.captureSource = stringValue(metadata.get("captureSource"), asset.captureSource);
            asset.playbackHint = stringValue(metadata.get("playbackHint"), asset.playbackHint);
            asset.layers = castMapList(metadata.get("layers"), asset.layers);
            asset.rendererContract = castMap(metadata.get("rendererContract"), asset.rendererContract);
            asset.shaderContract = castMap(metadata.get("shaderContract"), asset.shaderContract);
            asset.captureContract = castMap(metadata.get("captureContract"), asset.captureContract);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to read render contract metadata for {}", renderContractFile.getAbsolutePath(), e);
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
            File candidate = new File(parent, stem + "_frame_" + index + ".png");
            File legacyCandidate = new File(parent, stem + "_frame" + index + ".png");
            if (candidate.exists()) {
                result.add(candidate);
                continue;
            }
            if (legacyCandidate.exists()) {
                result.add(legacyCandidate);
                continue;
            }
            if (!candidate.exists() && !legacyCandidate.exists()) {
                break;
            }
        }
        List<File> cachedResult = Collections.unmodifiableList(result);
        frameFilesCache.put(cacheKey, cachedResult);
        return cachedResult;
    }

    private File firstFrameFile(File baseFile) {
        String fileName = baseFile.getName();
        int dot = fileName.lastIndexOf('.');
        String stem = dot >= 0 ? fileName.substring(0, dot) : fileName;
        return new File(baseFile.getParentFile(), stem + "_frame_1.png");
    }

    private File legacyFirstFrameFile(File baseFile) {
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
            return relative.substring(0, relative.length() - 4) + "_frame_{index}.png";
        }
        if (relative.endsWith(".gif")) {
            return relative.substring(0, relative.length() - 4) + "_frame_{index}.png";
        }
        return relative + "_frame_{index}.png";
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

    private File renderContractFile(File baseFile) {
        String path = baseFile.getAbsolutePath();
        if (path.endsWith(".png")) {
            return new File(path.substring(0, path.length() - 4) + ".render.json");
        }
        if (path.endsWith(".gif")) {
            return new File(path.substring(0, path.length() - 4) + ".render.json");
        }
        return new File(path + ".render.json");
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

    private String computeAssetFingerprint(
            File baseFile,
            File metadataFile,
            File renderContractFile,
            List<File> frameFiles) {
        StringBuilder builder = new StringBuilder();
        appendFingerprint(builder, baseFile);
        appendFingerprint(builder, metadataFile);
        appendFingerprint(builder, renderContractFile);
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
        String cacheKey = file != null ? file.getAbsolutePath() : "<null>";
        if (imageSizeCache.containsKey(cacheKey)) {
            return imageSizeCache.get(cacheKey);
        }
        if (file == null || !file.exists()) {
            imageSizeCache.put(cacheKey, null);
            return null;
        }

        try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
            if (stream == null) {
                imageSizeCache.put(cacheKey, null);
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                imageSizeCache.put(cacheKey, null);
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                Map<String, Object> size = new HashMap<>();
                size.put("width", reader.getWidth(0));
                size.put("height", reader.getHeight(0));
                imageSizeCache.put(cacheKey, size);
                return size;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            Logger.MOD.warn("Failed to inspect render asset dimensions for {}", file.getAbsolutePath(), e);
            imageSizeCache.put(cacheKey, null);
            return null;
        } catch (RuntimeException e) {
            Logger.MOD.warn("Failed to inspect render asset dimensions for {}", file.getAbsolutePath(), e);
            imageSizeCache.put(cacheKey, null);
            return null;
        }
    }

    @SuppressWarnings("unused")
    private Map<String, Object> readBaseSizeByDecoding(File file) {
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
        copy.contractVersion = source.contractVersion;
        copy.assetId = source.assetId;
        copy.variantKey = source.variantKey;
        copy.sourceType = source.sourceType;
        copy.family = source.family;
        copy.contentHash = source.contentHash;
        copy.mode = source.mode;
        copy.renderMode = source.renderMode;
        copy.animationMode = source.animationMode;
        copy.captureMethod = source.captureMethod;
        copy.captureSource = source.captureSource;
        copy.rendererFamily = source.rendererFamily;
        copy.playbackHint = source.playbackHint;
        copy.sourceFormat = source.sourceFormat;
        copy.sourcePath = source.sourcePath;
        copy.atlasTexture = source.atlasTexture;
        copy.atlasExportFile = source.atlasExportFile;
        copy.spriteMetadataFile = source.spriteMetadataFile;
        copy.contractFile = source.contractFile;
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
        copy.frameDurationSource = source.frameDurationSource;
        copy.loop = source.loop;
        copy.rect = copyNestedMap(source.rect);
        copy.baseSize = copyNestedMap(source.baseSize);
        copy.layers = copyNestedMapList(source.layers);
        copy.rendererContract = copyNestedMap(source.rendererContract);
        copy.shaderContract = copyNestedMap(source.shaderContract);
        copy.captureContract = copyNestedMap(source.captureContract);
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

    private Map<String, Object> castMap(Object value, Map<String, Object> fallback) {
        if (!(value instanceof Map)) {
            return fallback;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) value;
        return new LinkedHashMap<>(cast);
    }

    private List<Map<String, Object>> castMapList(Object value, List<Map<String, Object>> fallback) {
        if (!(value instanceof List)) {
            return fallback;
        }
        List<?> list = (List<?>) value;
        List<Map<String, Object>> cast = new ArrayList<>();
        for (Object entry : list) {
            if (entry instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) entry;
                cast.add(new LinkedHashMap<>(map));
            }
        }
        return cast;
    }

    private Integer integerValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    private void putIfNumber(Map<String, Object> target, String key, Object value) {
        if (value instanceof Number) {
            target.put(key, value);
        }
    }

    private boolean isGifFile(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".gif");
    }

    private GifAnimationInfo inspectGifAnimation(File baseFile) {
        String cacheKey = baseFile != null ? baseFile.getAbsolutePath() : "<null>";
        GifAnimationInfo cached = gifAnimationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (!isGifFile(baseFile) || !baseFile.exists()) {
            GifAnimationInfo none = GifAnimationInfo.none();
            gifAnimationCache.put(cacheKey, none);
            return none;
        }

        try (ImageInputStream stream = ImageIO.createImageInputStream(baseFile)) {
            if (stream == null) {
                GifAnimationInfo none = GifAnimationInfo.none();
                gifAnimationCache.put(cacheKey, none);
                return none;
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                GifAnimationInfo none = GifAnimationInfo.none();
                gifAnimationCache.put(cacheKey, none);
                return none;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, false, false);
                int frameCount = reader.getNumImages(true);
                if (frameCount <= 1) {
                    GifAnimationInfo none = GifAnimationInfo.none();
                    gifAnimationCache.put(cacheKey, none);
                    return none;
                }

                BufferedImage firstFrame = reader.read(0);
                Map<String, Object> baseSize = null;
                if (firstFrame != null) {
                    baseSize = new LinkedHashMap<>();
                    baseSize.put("width", firstFrame.getWidth());
                    baseSize.put("height", firstFrame.getHeight());
                }

                List<Map<String, Object>> frames = new ArrayList<>(frameCount);
                List<Map<String, Object>> timeline = new ArrayList<>(frameCount);
                Integer defaultFrameDurationMs = null;
                String relativePath = relativizeFromExportDirectory(baseFile);
                for (int index = 0; index < frameCount; index++) {
                    Map<String, Object> frame = new LinkedHashMap<>();
                    frame.put("index", index);
                    frame.put("frameIndex", index);
                    frame.put("path", relativePath);
                    frames.add(frame);

                    Integer durationMs = readGifFrameDelayMs(reader.getImageMetadata(index));
                    if (durationMs == null) {
                        durationMs = GifRenderer.DEFAULT_CAPTURE_FRAME_DELAY_MS;
                    }
                    if (defaultFrameDurationMs == null) {
                        defaultFrameDurationMs = durationMs;
                    }

                    Map<String, Object> timelineEntry = new LinkedHashMap<>();
                    timelineEntry.put("timelineIndex", index);
                    timelineEntry.put("index", index);
                    timelineEntry.put("frameIndex", index);
                    timelineEntry.put("path", relativePath);
                    timelineEntry.put("durationMs", durationMs);
                    timeline.add(timelineEntry);
                }

                GifAnimationInfo info = new GifAnimationInfo();
                info.animated = true;
                info.frameCount = frameCount;
                info.frames = frames;
                info.timeline = timeline;
                info.defaultFrameDurationMs =
                        defaultFrameDurationMs != null ? defaultFrameDurationMs : GifRenderer.DEFAULT_CAPTURE_FRAME_DELAY_MS;
                info.baseSize = baseSize;
                gifAnimationCache.put(cacheKey, info);
                return info;
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            Logger.MOD.warn("Failed to inspect GIF animation metadata for {}", baseFile.getAbsolutePath(), e);
            GifAnimationInfo none = GifAnimationInfo.none();
            gifAnimationCache.put(cacheKey, none);
            return none;
        }
    }

    private void applyLegacyRenderContractDefaults(CanonicalRenderAsset asset) {
        if (asset.renderMode == null || asset.renderMode.isEmpty()) {
            if ("native_sprite_animation".equals(asset.mode) || "native_sprite_snapshot".equals(asset.mode)) {
                asset.renderMode = "native_sprite";
            } else if ("rendered_frames".equals(asset.mode)) {
                asset.renderMode = "captured_final_atlas";
            } else if (asset.layers != null && asset.layers.size() > 1) {
                asset.renderMode = "layered_item";
            } else {
                asset.renderMode = "native_sprite";
            }
        }

        if (asset.captureSource == null || asset.captureSource.isEmpty()) {
            if ("native_sprite".equals(asset.renderMode)) {
                asset.captureSource = "native_sprite_metadata";
            } else if ("rendered_frames".equals(asset.mode)) {
                asset.captureSource = "framebuffer_multiframe";
            } else {
                asset.captureSource = "framebuffer_singleframe";
            }
        }

        if (asset.playbackHint == null || asset.playbackHint.isEmpty()) {
            if ("layered_item".equals(asset.renderMode)) {
                asset.playbackHint = "layered_canvas";
            } else if ("renderer_family".equals(asset.renderMode)) {
                asset.playbackHint = "renderer_family_adapter";
            } else if ("captured_final_atlas".equals(asset.renderMode)) {
                asset.playbackHint = "atlas_timeline";
            } else {
                asset.playbackHint = "native_sprite";
            }
        }
    }

    private Integer readGifFrameDelayMs(IIOMetadata metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            String formatName = metadata.getNativeMetadataFormatName();
            if (formatName == null) {
                return null;
            }
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(formatName);
            for (int i = 0; i < root.getLength(); i++) {
                if (!"GraphicControlExtension".equals(root.item(i).getNodeName())) {
                    continue;
                }
                IIOMetadataNode node = (IIOMetadataNode) root.item(i);
                String delayTime = node.getAttribute("delayTime");
                if (delayTime == null || delayTime.isEmpty()) {
                    return null;
                }
                return Integer.parseInt(delayTime) * 10;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static final class ItemExportEntry {
        String renderAssetRef;
        String imageFileName;
    }

    private static final class FamilyImageIndex {
        final File familyRoot;
        final Map<String, File> exactArtifacts = new HashMap<>();
        final Map<String, File> primaryArtifactsByStem = new HashMap<>();
        final Map<String, List<File>> siblingVariantCandidatesByStem = new HashMap<>();
        final Map<String, File> spriteAtlasesByStem = new HashMap<>();

        private FamilyImageIndex(File familyRoot) {
            this.familyRoot = familyRoot;
        }
    }

    private static final class GifAnimationInfo {
        boolean animated;
        int frameCount;
        Integer defaultFrameDurationMs;
        List<Map<String, Object>> frames;
        List<Map<String, Object>> timeline;
        Map<String, Object> baseSize;

        static GifAnimationInfo none() {
            return new GifAnimationInfo();
        }
    }

}
