package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Atlas packer for static assets, preferring native sprite atlases when available and falling
 * back to rendered static snapshots otherwise.
 */
public class CanonicalAtlasPackWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String ATLAS_DIRECTORY = "atlases";
    private static final String OUTPUT_FILE = "atlas-manifest.json";
    private static final String GROUP_DIRECTORY = "atlas-manifests-by-group";
    private static final int WEBGL_SAFE_ATLAS_CHUNK_SIZE = 3000;
    private static final int WEBGL_SAFE_MAX_ATLAS_HEIGHT = 8192;
    private static final int PACKER_VERSION = 2;

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final List<CanonicalRenderAsset> precollectedAssets;

    public CanonicalAtlasPackWriter(EntityManager entityManager, File exportDirectory) {
        this(entityManager, exportDirectory, null);
    }

    public CanonicalAtlasPackWriter(
            EntityManager entityManager,
            File exportDirectory,
            List<CanonicalRenderAsset> precollectedAssets) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
        this.precollectedAssets = precollectedAssets;
    }

    public void export() throws IOException {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Packing NESQL++ static atlases...");

        List<CanonicalRenderAsset> assets =
                precollectedAssets != null
                        ? new ArrayList<>(precollectedAssets)
                        : new CanonicalRenderAssetCollector(entityManager, exportDirectory).collectAll();
        Map<String, List<CanonicalRenderAsset>> byGroup = groupStaticAtlasAssets(assets);

        File canonicalDir = new File(exportDirectory, OUTPUT_DIRECTORY);
        File atlasDir = new File(canonicalDir, ATLAS_DIRECTORY);
        if (!atlasDir.exists()) {
            atlasDir.mkdirs();
        }

        AtlasManifest manifest = new AtlasManifest();
        manifest.groups.addAll(packGroups(atlasDir, byGroup));
        manifest.groupCount = manifest.groups.size();
        manifest.assetCount = countAssets(manifest.groups);

        File manifestFile = new File(canonicalDir, OUTPUT_FILE);
        Gson gson = new GsonBuilder().serializeNulls().create();
        try (FileOutputStream fos = new FileOutputStream(manifestFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(manifest, writer);
        }

        writeGroupShards(canonicalDir, manifest.groups, gson);

        Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ atlas manifest written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + manifestFile.getAbsolutePath());
    }

    private List<AtlasGroupManifest> packGroups(
            File atlasDir,
            Map<String, List<CanonicalRenderAsset>> byGroup) throws IOException {
        List<Map.Entry<String, List<CanonicalRenderAsset>>> entries = new ArrayList<>(byGroup.entrySet());
        if (entries.isEmpty()) {
            return new ArrayList<>();
        }

        int workers = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() * 2, entries.size()));
        if (workers == 1) {
            List<AtlasGroupManifest> groups = new ArrayList<>();
            for (Map.Entry<String, List<CanonicalRenderAsset>> entry : entries) {
                groups.addAll(packGroup(atlasDir, entry.getKey(), entry.getValue()));
            }
            return groups;
        }

        Logger.chatMessage(
                EnumChatFormatting.AQUA
                        + "Packing static atlas groups with "
                        + workers
                        + " IO workers...");
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<List<AtlasGroupManifest>>> futures = new ArrayList<>();
            for (Map.Entry<String, List<CanonicalRenderAsset>> entry : entries) {
                final String atlasGroup = entry.getKey();
                final List<CanonicalRenderAsset> assets = new ArrayList<>(entry.getValue());
                futures.add(executor.submit(new Callable<List<AtlasGroupManifest>>() {
                    @Override
                    public List<AtlasGroupManifest> call() throws Exception {
                        return packGroup(atlasDir, atlasGroup, assets);
                    }
                }));
            }

            List<AtlasGroupManifest> groups = new ArrayList<>();
            for (Future<List<AtlasGroupManifest>> future : futures) {
                try {
                    groups.addAll(future.get());
                } catch (Exception e) {
                    throw new IOException("Failed to pack static atlas group", e);
                }
            }
            groups.sort(Comparator.comparing(group -> group.atlasGroup));
            return groups;
        } finally {
            executor.shutdownNow();
        }
    }

    private Map<String, List<CanonicalRenderAsset>> groupStaticAtlasAssets(List<CanonicalRenderAsset> assets) {
        Map<String, List<CanonicalRenderAsset>> byGroup = new LinkedHashMap<>();
        for (CanonicalRenderAsset asset : assets) {
            if (!Boolean.TRUE.equals(asset.atlasCandidate)) {
                continue;
            }
            if (!"static_snapshot".equals(asset.mode)
                    && !"native_sprite_snapshot".equals(asset.mode)) {
                continue;
            }
            String atlasGroup = asset.atlasGroup != null ? asset.atlasGroup : "ungrouped";
            byGroup.computeIfAbsent(atlasGroup, ignored -> new ArrayList<>()).add(asset);
        }
        return byGroup;
    }

    private List<AtlasGroupManifest> packGroup(File atlasDir, String atlasGroup, List<CanonicalRenderAsset> assets) throws IOException {
        if (assets.isEmpty()) {
            return new ArrayList<>();
        }

        assets.sort(Comparator.comparing(asset -> asset.assetId));
        String safeName = atlasGroup.replaceAll("[^a-zA-Z0-9_-]", "_");

        List<AtlasGroupManifest> reusableGroups = readFreshGroupManifests(atlasDir, atlasGroup, assets, safeName);
        if (!reusableGroups.isEmpty()) {
            return reusableGroups;
        }

        List<AtlasPackingSupport.AtlasSourceImage> sources = loadSourceImages(assets);
        if (sources.isEmpty()) {
            return new ArrayList<>();
        }

        List<List<AtlasPackingSupport.AtlasSourceImage>> chunks = splitWebglSafeChunks(sources);
        List<AtlasGroupManifest> groupManifests = new ArrayList<>();
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            String chunkSafeName = chunks.size() == 1 ? safeName : String.format("%s-%03d", safeName, chunkIndex);
            String chunkAtlasGroup = chunks.size() == 1 ? atlasGroup : String.format("%s-%03d", atlasGroup, chunkIndex);
            File atlasFile = new File(atlasDir, chunkSafeName + ".png");
            AtlasPackingSupport.PackLayout layout = AtlasPackingSupport.computeLayout(chunks.get(chunkIndex));
            AtlasPackingSupport.writeAtlasImage(atlasFile, layout);

            AtlasGroupManifest groupManifest = new AtlasGroupManifest();
            groupManifest.packerVersion = PACKER_VERSION;
            groupManifest.atlasGroup = chunkAtlasGroup;
            groupManifest.atlasFile = relativizeFromExportDirectory(atlasFile);
            groupManifest.width = layout.width;
            groupManifest.height = layout.height;
            groupManifest.sourceSignature = sourceSignature(chunks.get(chunkIndex));
            groupManifest.atlasPageSha256 = sha256(atlasFile);
            groupManifest.assets = new ArrayList<>();

            for (AtlasPackingSupport.AtlasPlacement placement : layout.placements) {
                AtlasAssetPlacement assetPlacement = new AtlasAssetPlacement();
                assetPlacement.assetId = placement.source.asset.assetId;
                assetPlacement.variantKey = placement.source.asset.variantKey;
                assetPlacement.x = placement.x;
                assetPlacement.y = placement.y;
                assetPlacement.width = placement.source.image.getWidth();
                assetPlacement.height = placement.source.image.getHeight();
                assetPlacement.sourcePath = relativizeFromExportDirectory(placement.source.file);
                assetPlacement.sourceBytes = placement.source.file.length();
                assetPlacement.sourceSha256 = sha256(placement.source.file);
                groupManifest.assets.add(assetPlacement);
            }
            groupManifests.add(groupManifest);
        }

        return groupManifests;
    }

    private List<AtlasPackingSupport.AtlasSourceImage> loadSourceImages(List<CanonicalRenderAsset> assets) throws IOException {
        if (assets.size() < 512) {
            List<AtlasPackingSupport.AtlasSourceImage> sources = new ArrayList<>();
            for (CanonicalRenderAsset asset : assets) {
                AtlasPackingSupport.AtlasSourceImage source = loadSourceImage(asset);
                if (source != null) {
                    sources.add(source);
                }
            }
            return sources;
        }

        int workers = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() * 2, assets.size()));
        Logger.MOD.info("Loading {} static atlas source images with {} workers...", assets.size(), workers);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<AtlasPackingSupport.AtlasSourceImage>> futures = new ArrayList<>(assets.size());
            for (final CanonicalRenderAsset asset : assets) {
                futures.add(executor.submit(new Callable<AtlasPackingSupport.AtlasSourceImage>() {
                    @Override
                    public AtlasPackingSupport.AtlasSourceImage call() throws Exception {
                        return loadSourceImage(asset);
                    }
                }));
            }

            List<AtlasPackingSupport.AtlasSourceImage> sources = new ArrayList<>(assets.size());
            for (Future<AtlasPackingSupport.AtlasSourceImage> future : futures) {
                try {
                    AtlasPackingSupport.AtlasSourceImage source = future.get();
                    if (source != null) {
                        sources.add(source);
                    }
                } catch (Exception e) {
                    throw new IOException("Failed to load static atlas source image", e);
                }
            }
            return sources;
        } finally {
            executor.shutdownNow();
        }
    }

    private AtlasPackingSupport.AtlasSourceImage loadSourceImage(CanonicalRenderAsset asset) throws IOException {
        File sourceFile = resolveSourceFile(asset);
        if (sourceFile == null || !sourceFile.exists()) {
            return null;
        }
        BufferedImage image = ImageIO.read(sourceFile);
        if (image == null) {
            return null;
        }
        return new AtlasPackingSupport.AtlasSourceImage(asset, sourceFile, image, null);
    }

    private List<List<AtlasPackingSupport.AtlasSourceImage>> splitWebglSafeChunks(
            List<AtlasPackingSupport.AtlasSourceImage> sources) {
        List<List<AtlasPackingSupport.AtlasSourceImage>> chunks = new ArrayList<>();
        for (int start = 0; start < sources.size(); start += WEBGL_SAFE_ATLAS_CHUNK_SIZE) {
            int end = Math.min(sources.size(), start + WEBGL_SAFE_ATLAS_CHUNK_SIZE);
            addWebglSafeChunk(chunks, new ArrayList<>(sources.subList(start, end)));
        }
        return chunks;
    }

    private void addWebglSafeChunk(
            List<List<AtlasPackingSupport.AtlasSourceImage>> chunks,
            List<AtlasPackingSupport.AtlasSourceImage> sources) {
        if (sources.isEmpty()) {
            return;
        }
        AtlasPackingSupport.PackLayout layout = AtlasPackingSupport.computeLayout(sources);
        if (layout.height <= WEBGL_SAFE_MAX_ATLAS_HEIGHT || sources.size() == 1) {
            chunks.add(sources);
            return;
        }
        int middle = sources.size() / 2;
        addWebglSafeChunk(chunks, new ArrayList<>(sources.subList(0, middle)));
        addWebglSafeChunk(chunks, new ArrayList<>(sources.subList(middle, sources.size())));
    }

    private List<AtlasGroupManifest> readFreshGroupManifests(
            File atlasDir,
            String atlasGroup,
            List<CanonicalRenderAsset> assets,
            String safeName) {
        File canonicalDir = atlasDir.getParentFile();
        File groupDir = new File(canonicalDir, GROUP_DIRECTORY);
        if (!groupDir.exists()) {
            return new ArrayList<>();
        }

        File[] shardFiles = groupDir.listFiles((dir, name) ->
                name.equals(safeName + ".json")
                        || (name.startsWith(safeName + "-") && name.endsWith(".json")));
        if (shardFiles == null || shardFiles.length == 0) {
            return new ArrayList<>();
        }

        java.util.Arrays.sort(shardFiles, Comparator.comparing(File::getName));
        List<AtlasGroupManifest> groups = new ArrayList<>();
        Map<String, Boolean> expectedAssetIds = new HashMap<>();
        for (CanonicalRenderAsset asset : assets) {
            expectedAssetIds.put(asset.assetId, Boolean.FALSE);
        }

        for (File shardFile : shardFiles) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(shardFile);
                 java.io.InputStreamReader reader =
                         new java.io.InputStreamReader(fis, StandardCharsets.UTF_8)) {
                AtlasGroupManifest manifest = new Gson().fromJson(reader, AtlasGroupManifest.class);
                if (manifest == null || manifest.assets == null || manifest.assets.isEmpty()) {
                    return new ArrayList<>();
                }
                if (manifest.packerVersion != PACKER_VERSION) {
                    return new ArrayList<>();
                }

                File atlasFile = resolveExportFile(manifest.atlasFile);
                if (!atlasFile.exists()) {
                    return new ArrayList<>();
                }
                if (manifest.atlasPageSha256 == null
                        || !manifest.atlasPageSha256.equals(sha256(atlasFile))) {
                    return new ArrayList<>();
                }

                String actualSignature = sourceSignatureForPlacements(manifest.assets);
                if (manifest.sourceSignature == null || !manifest.sourceSignature.equals(actualSignature)) {
                    return new ArrayList<>();
                }
                for (AtlasAssetPlacement placement : manifest.assets) {
                    if (!expectedAssetIds.containsKey(placement.assetId)) {
                        return new ArrayList<>();
                    }
                    expectedAssetIds.put(placement.assetId, Boolean.TRUE);
                }
                groups.add(manifest);
            } catch (Exception e) {
                Logger.MOD.warn("Failed to reuse static atlas group shard {}", shardFile.getAbsolutePath(), e);
                return new ArrayList<>();
            }
        }

        for (Boolean seen : expectedAssetIds.values()) {
            if (!Boolean.TRUE.equals(seen)) {
                return new ArrayList<>();
            }
        }

        Logger.MOD.info("Reusing {} fresh static atlas shard(s) for group {}", groups.size(), atlasGroup);
        return groups;
    }

    private String sourceSignature(List<AtlasPackingSupport.AtlasSourceImage> sources) {
        List<String> parts = new ArrayList<>();
        for (AtlasPackingSupport.AtlasSourceImage source : sources) {
            parts.add(source.asset.assetId
                    + "|"
                    + relativizeFromExportDirectory(source.file)
                    + "|"
                    + source.file.length()
                    + "|"
                    + sha256(source.file));
        }
        parts.sort(String::compareTo);
        return sha256(String.join("\n", parts));
    }

    private String sourceSignatureForPlacements(List<AtlasAssetPlacement> placements) {
        List<String> parts = new ArrayList<>();
        for (AtlasAssetPlacement placement : placements) {
            File sourceFile = resolveExportFile(placement.sourcePath);
            if (!sourceFile.exists()) {
                return null;
            }
            String sourceSha256 = sha256(sourceFile);
            if (placement.sourceSha256 != null && !placement.sourceSha256.equals(sourceSha256)) {
                return null;
            }
            if (placement.sourceBytes > 0L && placement.sourceBytes != sourceFile.length()) {
                return null;
            }
            parts.add(placement.assetId
                    + "|"
                    + placement.sourcePath
                    + "|"
                    + sourceFile.length()
                    + "|"
                    + sourceSha256);
        }
        parts.sort(String::compareTo);
        return sha256(String.join("\n", parts));
    }

    private String sha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            try (FileInputStream inputStream = new FileInputStream(file)) {
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash atlas file " + file.getAbsolutePath(), e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash atlas signature", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }

    private int countAssets(List<AtlasGroupManifest> groups) {
        int total = 0;
        for (AtlasGroupManifest group : groups) {
            total += group.assets.size();
        }
        return total;
    }

    private void writeGroupShards(File canonicalDir, List<AtlasGroupManifest> groups, Gson gson) throws IOException {
        File groupDir = new File(canonicalDir, GROUP_DIRECTORY);
        if (!groupDir.exists()) {
            groupDir.mkdirs();
        }

        for (AtlasGroupManifest group : groups) {
            String safeName = group.atlasGroup.replaceAll("[^a-zA-Z0-9_-]", "_");
            File shardFile = new File(groupDir, safeName + ".json");
            try (FileOutputStream fos = new FileOutputStream(shardFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                gson.toJson(group, writer);
            }
        }
    }

    private File resolveSourceFile(CanonicalRenderAsset asset) {
        // The homepage browser atlas needs a directly drawable item snapshot. NESQL++ also stores
        // native atlas metadata for animation/timeline reconstruction, but those atlas textures can
        // contain transparent cells for some GTNH native sprites. Packing them as static browser
        // tiles made NeoNEI believe coverage was complete while fast page flips rendered blank
        // slots. Prefer the already-rendered static snapshot for all static atlas entries, and keep
        // native atlas files for the animated atlas writer / metadata paths.
        if (asset.staticFile == null || asset.staticFile.isEmpty()) {
            if ("native_sprite_snapshot".equals(asset.mode)
                    && asset.nativeSpriteAtlasFile != null
                    && !asset.nativeSpriteAtlasFile.isEmpty()) {
                return resolveExportFile(asset.nativeSpriteAtlasFile);
            }
            return null;
        }
        return resolveExportFile(asset.staticFile);
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

    private static final class AtlasManifest {
        String schemaVersion = "nesqlpp/atlas-pack/v1-draft";
        int groupCount;
        int assetCount;
        List<AtlasGroupManifest> groups = new ArrayList<>();
    }

    private static final class AtlasGroupManifest {
        int packerVersion;
        String atlasGroup;
        String atlasFile;
        String sourceSignature;
        String atlasPageSha256;
        int width;
        int height;
        List<AtlasAssetPlacement> assets = new ArrayList<>();
    }

    private static final class AtlasAssetPlacement {
        String assetId;
        String variantKey;
        String sourcePath;
        long sourceBytes;
        String sourceSha256;
        int x;
        int y;
        int width;
        int height;
    }
}
