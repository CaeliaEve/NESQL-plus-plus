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
import java.util.List;

/** Writes a unified render index that points consumers to render, animation, and atlas outputs. */
public class CanonicalRenderIndexWriter {
    private static final String OUTPUT_DIRECTORY = "canonical";
    private static final String OUTPUT_FILE = "render-index.json";

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final List<CanonicalRenderAsset> precollectedAssets;

    public CanonicalRenderIndexWriter(EntityManager entityManager, File exportDirectory) {
        this(entityManager, exportDirectory, null);
    }

    public CanonicalRenderIndexWriter(
            EntityManager entityManager,
            File exportDirectory,
            List<CanonicalRenderAsset> precollectedAssets) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
        this.precollectedAssets = precollectedAssets;
    }

    public void export() throws IOException {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Exporting NESQL++ unified render index...");

        List<CanonicalRenderAsset> assets =
                precollectedAssets != null
                        ? new ArrayList<>(precollectedAssets)
                        : new CanonicalRenderAssetCollector(entityManager, exportDirectory).collectAll();
        assets.sort(Comparator.comparing(asset -> asset.assetId));

        RenderIndex index = new RenderIndex();
        for (CanonicalRenderAsset asset : assets) {
            RenderIndexEntry entry = new RenderIndexEntry();
            entry.assetId = asset.assetId;
            entry.variantKey = asset.variantKey;
            entry.mode = asset.mode;
            entry.renderMode = asset.renderMode;
            entry.atlasGroup = asset.atlasGroup;
            entry.staticFile = asset.staticFile;
            entry.spriteMetadataFile = asset.spriteMetadataFile;
            entry.renderContractFile = asset.contractFile;
            entry.atlasTexture = asset.atlasTexture;
            entry.rendererFamily = asset.rendererFamily;
            entry.captureSource = asset.captureSource;
            entry.playbackHint = asset.playbackHint;
            entry.primaryArtifact = asset.primaryArtifact;
            entry.atlasRegistryPath = "canonical/atlas-registry.json";
            entry.renderAssetManifestPath = "canonical/render-assets.json";
            entry.renderAssetGroupPath =
                    "canonical/render-assets-by-group/"
                            + safeName(asset.atlasGroup != null ? asset.atlasGroup : "ungrouped")
                            + ".json";

            if ("native_sprite_animation".equals(asset.mode)) {
                entry.animationManifestPath = "canonical/animation-manifest.json";
                entry.animationGroupPath =
                        "canonical/animation-manifests-by-group/"
                                + safeName(asset.atlasGroup != null ? asset.atlasGroup : "ungrouped")
                                + ".json";
                entry.resolutionMode = asset.renderMode != null ? asset.renderMode : "native_sprite_animation";
            } else if ("rendered_frames".equals(asset.mode)) {
                entry.framePattern = asset.framePattern;
                entry.frameCount = asset.frameCount;
                entry.animationManifestPath = "canonical/animation-manifest.json";
                entry.animationGroupPath =
                        "canonical/animation-manifests-by-group/"
                                + safeName(asset.atlasGroup != null ? asset.atlasGroup : "ungrouped")
                                + ".json";
                entry.animatedAtlasManifestPath = "canonical/animated-atlas-manifest.json";
                entry.animatedAtlasGroupPath =
                        "canonical/animated-atlas-manifests-by-group/"
                                + safeName(asset.atlasGroup != null ? asset.atlasGroup : "ungrouped")
                                + ".json";
                entry.resolutionMode = asset.renderMode != null ? asset.renderMode : "animated_frame_sequence";
            } else if ("native_sprite_snapshot".equals(asset.mode)) {
                entry.atlasManifestPath = "canonical/atlas-manifest.json";
                entry.atlasGroupPath =
                        "canonical/atlas-manifests-by-group/"
                                + safeName(asset.atlasGroup != null ? asset.atlasGroup : "ungrouped")
                                + ".json";
                entry.resolutionMode = asset.renderMode != null ? asset.renderMode : "native_sprite_snapshot";
            } else {
                entry.atlasManifestPath = "canonical/atlas-manifest.json";
                entry.atlasGroupPath =
                        "canonical/atlas-manifests-by-group/"
                                + safeName(asset.atlasGroup != null ? asset.atlasGroup : "ungrouped")
                                + ".json";
                entry.resolutionMode = asset.renderMode != null ? asset.renderMode : "static_snapshot";
            }

            index.assets.add(entry);
        }

        File canonicalDir = new File(exportDirectory, OUTPUT_DIRECTORY);
        if (!canonicalDir.exists()) {
            canonicalDir.mkdirs();
        }

        File outputFile = new File(canonicalDir, OUTPUT_FILE);
        Gson gson = new GsonBuilder().serializeNulls().create();
        try (FileOutputStream fos = new FileOutputStream(outputFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(index, writer);
        }

        Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ unified render index written:");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "  " + outputFile.getAbsolutePath());
    }

    private String safeName(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private static final class RenderIndex {
        String schemaVersion = "nesqlpp/render-index/v2-draft";
        List<RenderIndexEntry> assets = new ArrayList<>();
    }

    private static final class RenderIndexEntry {
        String assetId;
        String variantKey;
        String mode;
        String renderMode;
        String atlasGroup;
        String staticFile;
        String spriteMetadataFile;
        String renderContractFile;
        String atlasTexture;
        String rendererFamily;
        String captureSource;
        String playbackHint;
        String framePattern;
        Integer frameCount;
        String primaryArtifact;
        String atlasRegistryPath;
        String resolutionMode;
        String renderAssetManifestPath;
        String renderAssetGroupPath;
        String animationManifestPath;
        String animationGroupPath;
        String atlasManifestPath;
        String atlasGroupPath;
        String animatedAtlasManifestPath;
        String animatedAtlasGroupPath;
    }
}
