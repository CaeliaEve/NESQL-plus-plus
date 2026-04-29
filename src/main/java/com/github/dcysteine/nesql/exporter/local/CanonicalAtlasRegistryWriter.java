package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes a unified atlas registry tying exported atlas textures to sprite metadata and assets.
 */
public class CanonicalAtlasRegistryWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String OUTPUT_FILE = "atlas-registry.json";

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final List<CanonicalRenderAsset> precollectedAssets;

    public CanonicalAtlasRegistryWriter(EntityManager entityManager, File exportDirectory) {
        this(entityManager, exportDirectory, null);
    }

    public CanonicalAtlasRegistryWriter(
            EntityManager entityManager,
            File exportDirectory,
            List<CanonicalRenderAsset> precollectedAssets) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
        this.precollectedAssets = precollectedAssets;
    }

    public void export() throws IOException {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ atlas registry...");

        List<CanonicalRenderAsset> assets =
                precollectedAssets != null
                        ? new ArrayList<>(precollectedAssets)
                        : new CanonicalRenderAssetCollector(entityManager, exportDirectory).collectAll();
        assets.sort(Comparator.comparing(asset -> asset.assetId));

        AtlasRegistry registry = new AtlasRegistry();
        Map<String, AtlasEntry> atlases = new LinkedHashMap<>();
        for (CanonicalRenderAsset asset : assets) {
            String atlasKey =
                    asset.atlasExportFile != null && !asset.atlasExportFile.isEmpty()
                            ? asset.atlasExportFile
                            : (asset.atlasTexture != null && !asset.atlasTexture.isEmpty()
                                    ? asset.atlasTexture
                                    : "unassigned");
            AtlasEntry entry = atlases.get(atlasKey);
            if (entry == null) {
                entry = new AtlasEntry();
                entry.atlasKey = atlasKey;
                entry.atlasTexture = asset.atlasTexture;
                entry.atlasExportFile = asset.atlasExportFile;
                entry.assets = new ArrayList<>();
                atlases.put(atlasKey, entry);
            }

            SpriteEntry sprite = new SpriteEntry();
            sprite.assetId = asset.assetId;
            sprite.variantKey = asset.variantKey;
            sprite.mode = asset.mode;
            sprite.spriteMetadataFile = asset.spriteMetadataFile;
            sprite.nativeSpriteAtlasFile = asset.nativeSpriteAtlasFile;
            sprite.primaryArtifact = asset.primaryArtifact;
            sprite.frameCount = asset.frameCount;
            sprite.rect = asset.rect;
            sprite.timeline = asset.timeline;
            entry.assets.add(sprite);
        }

        registry.atlases.addAll(atlases.values());
        registry.atlasCount = registry.atlases.size();
        registry.assetCount = assets.size();

        File canonicalDir = new File(exportDirectory, OUTPUT_DIRECTORY);
        if (!canonicalDir.exists()) {
            canonicalDir.mkdirs();
        }

        File outputFile = new File(canonicalDir, OUTPUT_FILE);
        Gson gson = new GsonBuilder().serializeNulls().create();
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(registry, writer);
        }

        Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ atlas registry written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + outputFile.getAbsolutePath());
    }

    private static final class AtlasRegistry {
        String schemaVersion = "nesqlpp/atlas-registry/v1-draft";
        int atlasCount;
        int assetCount;
        List<AtlasEntry> atlases = new ArrayList<>();
    }

    private static final class AtlasEntry {
        String atlasKey;
        String atlasTexture;
        String atlasExportFile;
        List<SpriteEntry> assets = new ArrayList<>();
    }

    private static final class SpriteEntry {
        String assetId;
        String variantKey;
        String mode;
        String spriteMetadataFile;
        String nativeSpriteAtlasFile;
        String primaryArtifact;
        Integer frameCount;
        Map<String, Object> rect;
        List<Map<String, Object>> timeline;
    }
}
