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

/** Writes the NESQL++ render asset manifest from exported render artifacts. */
public class CanonicalRenderAssetManifestWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String OUTPUT_FILE = "render-assets.json";
    private static final String GROUP_DIRECTORY = "render-assets-by-group";

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final List<CanonicalRenderAsset> precollectedAssets;

    public CanonicalRenderAssetManifestWriter(EntityManager entityManager, File exportDirectory) {
        this(entityManager, exportDirectory, null);
    }

    public CanonicalRenderAssetManifestWriter(
            EntityManager entityManager,
            File exportDirectory,
            List<CanonicalRenderAsset> precollectedAssets) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
        this.precollectedAssets = precollectedAssets;
    }

    public void export() throws IOException {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ render asset manifest...");

        List<CanonicalRenderAsset> assets =
                precollectedAssets != null
                        ? new ArrayList<>(precollectedAssets)
                        : new CanonicalRenderAssetCollector(entityManager, exportDirectory).collectAll();
        assets.sort(Comparator.comparing(asset -> asset.assetId));

        CanonicalRenderAssetManifest manifest = new CanonicalRenderAssetManifest();
        manifest.assets.addAll(assets);
        manifest.groups.addAll(buildGroupInfos(assets));

        File canonicalDir = new File(exportDirectory, OUTPUT_DIRECTORY);
        if (!canonicalDir.exists()) {
            canonicalDir.mkdirs();
        }

        Gson gson = new GsonBuilder().serializeNulls().create();
        File outputFile = new File(canonicalDir, OUTPUT_FILE);
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(manifest, writer);
        }

        writeGroupShards(canonicalDir, assets, gson);

        Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ render asset manifest written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + outputFile.getAbsolutePath());
    }

    private List<CanonicalRenderAssetManifest.RenderAssetGroupInfo> buildGroupInfos(List<CanonicalRenderAsset> assets) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (CanonicalRenderAsset asset : assets) {
            String atlasGroup = asset.atlasGroup != null ? asset.atlasGroup : "ungrouped";
            counts.put(atlasGroup, counts.getOrDefault(atlasGroup, 0) + 1);
        }

        List<CanonicalRenderAssetManifest.RenderAssetGroupInfo> groups = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            groups.add(new CanonicalRenderAssetManifest.RenderAssetGroupInfo(entry.getKey(), entry.getValue()));
        }
        groups.sort(Comparator.comparing(group -> group.atlasGroup));
        return groups;
    }

    private void writeGroupShards(File canonicalDir, List<CanonicalRenderAsset> assets, Gson gson) throws IOException {
        File groupDir = new File(canonicalDir, GROUP_DIRECTORY);
        if (!groupDir.exists()) {
            groupDir.mkdirs();
        }

        Map<String, List<CanonicalRenderAsset>> byGroup = new LinkedHashMap<>();
        for (CanonicalRenderAsset asset : assets) {
            String atlasGroup = asset.atlasGroup != null ? asset.atlasGroup : "ungrouped";
            byGroup.computeIfAbsent(atlasGroup, ignored -> new ArrayList<>()).add(asset);
        }

        for (Map.Entry<String, List<CanonicalRenderAsset>> entry : byGroup.entrySet()) {
            String safeName = entry.getKey().replaceAll("[^a-zA-Z0-9_-]", "_");
            File shardFile = new File(groupDir, safeName + ".json");
            try (FileOutputStream fos = new FileOutputStream(shardFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                gson.toJson(entry.getValue(), writer);
            }
        }
    }
}
