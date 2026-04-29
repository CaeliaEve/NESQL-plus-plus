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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Atlas packer for static assets, preferring native sprite atlases when available and falling
 * back to rendered static snapshots otherwise.
 */
public class CanonicalAtlasPackWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String ATLAS_DIRECTORY = "atlases";
    private static final String OUTPUT_FILE = "atlas-manifest.json";
    private static final String GROUP_DIRECTORY = "atlas-manifests-by-group";

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
        for (Map.Entry<String, List<CanonicalRenderAsset>> entry : byGroup.entrySet()) {
            AtlasGroupManifest groupManifest = packGroup(atlasDir, entry.getKey(), entry.getValue());
            if (groupManifest != null) {
                manifest.groups.add(groupManifest);
            }
        }
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

    private AtlasGroupManifest packGroup(File atlasDir, String atlasGroup, List<CanonicalRenderAsset> assets) throws IOException {
        if (assets.isEmpty()) {
            return null;
        }

        assets.sort(Comparator.comparing(asset -> asset.assetId));
        List<AtlasPackingSupport.AtlasSourceImage> sources = new ArrayList<>();
        for (CanonicalRenderAsset asset : assets) {
            File sourceFile = resolveSourceFile(asset);
            if (sourceFile == null || !sourceFile.exists()) {
                continue;
            }
            BufferedImage image = ImageIO.read(sourceFile);
            if (image == null) {
                continue;
            }
            sources.add(new AtlasPackingSupport.AtlasSourceImage(asset, sourceFile, image, null));
        }

        if (sources.isEmpty()) {
            return null;
        }

        AtlasPackingSupport.PackLayout layout = AtlasPackingSupport.computeLayout(sources);
        String safeName = atlasGroup.replaceAll("[^a-zA-Z0-9_-]", "_");
        File atlasFile = new File(atlasDir, safeName + ".png");
        AtlasPackingSupport.writeAtlasImage(atlasFile, layout);

        AtlasGroupManifest groupManifest = new AtlasGroupManifest();
        groupManifest.atlasGroup = atlasGroup;
        groupManifest.atlasFile = relativizeFromExportDirectory(atlasFile);
        groupManifest.width = layout.width;
        groupManifest.height = layout.height;
        groupManifest.assets = new ArrayList<>();

        for (AtlasPackingSupport.AtlasPlacement placement : layout.placements) {
            AtlasAssetPlacement assetPlacement = new AtlasAssetPlacement();
            assetPlacement.assetId = placement.source.asset.assetId;
            assetPlacement.variantKey = placement.source.asset.variantKey;
            assetPlacement.x = placement.x;
            assetPlacement.y = placement.y;
            assetPlacement.width = placement.source.image.getWidth();
            assetPlacement.height = placement.source.image.getHeight();
            assetPlacement.sourcePath = placement.source.asset.staticFile;
            groupManifest.assets.add(assetPlacement);
        }

        return groupManifest;
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
        if ("native_sprite_snapshot".equals(asset.mode)
                && asset.nativeSpriteAtlasFile != null
                && !asset.nativeSpriteAtlasFile.isEmpty()) {
            return new File(exportDirectory, asset.nativeSpriteAtlasFile.replace('/', File.separatorChar));
        }
        if (asset.staticFile == null || asset.staticFile.isEmpty()) {
            return null;
        }
        return new File(exportDirectory, asset.staticFile.replace('/', File.separatorChar));
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
        String atlasGroup;
        String atlasFile;
        int width;
        int height;
        List<AtlasAssetPlacement> assets = new ArrayList<>();
    }

    private static final class AtlasAssetPlacement {
        String assetId;
        String variantKey;
        String sourcePath;
        int x;
        int y;
        int width;
        int height;
    }
}
