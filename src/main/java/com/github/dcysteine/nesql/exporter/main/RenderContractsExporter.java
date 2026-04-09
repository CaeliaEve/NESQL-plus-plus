package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.local.CanonicalAnimatedAtlasPackWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalAnimationManifestWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalAtlasPackWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalAtlasRegistryWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalRenderAssetCollector;
import com.github.dcysteine.nesql.exporter.local.CanonicalRenderAssetManifestWriter;
import com.github.dcysteine.nesql.exporter.local.CanonicalRenderIndexWriter;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

/** Rebuilds image canonical/atlas/index outputs from existing NESQL++ data and rendered assets. */
public final class RenderContractsExporter {
    private final ExportPaths exportPaths;

    public RenderContractsExporter() {
        this(com.github.dcysteine.nesql.exporter.main.config.ConfigOptions.REPOSITORY_NAME.get());
    }

    public RenderContractsExporter(String repositoryName) {
        this.exportPaths = ExportPaths.forRepository(repositoryName);
    }

    public void exportReportException() {
        try {
            export();
        } catch (Exception e) {
            Logger.MOD.error("Render contract rebuild failed", e);
            Logger.chatMessage(EnumChatFormatting.RED + "Render contract rebuild failed: " + e.getMessage());
        }
    }

    public void export() throws Exception {
        if (!exportPaths.repositoryDirectory.exists()) {
            throw new IllegalStateException("Repository does not exist: " + exportPaths.repositoryDirectory.getAbsolutePath());
        }

        List<CanonicalRenderAsset> assets =
                new CanonicalRenderAssetCollector(null, exportPaths.repositoryDirectory).collectAll();
        Logger.chatMessage(EnumChatFormatting.AQUA + "Collected " + assets.size() + " render assets for rebuild.");

        new CanonicalRenderAssetManifestWriter(null, exportPaths.repositoryDirectory, assets).export();
        new CanonicalAnimationManifestWriter(null, exportPaths.repositoryDirectory, assets).export();
        new CanonicalAtlasPackWriter(null, exportPaths.repositoryDirectory, assets).export();
        new CanonicalAnimatedAtlasPackWriter(null, exportPaths.repositoryDirectory, assets).export();
        new CanonicalAtlasRegistryWriter(null, exportPaths.repositoryDirectory, assets).export();
        new CanonicalRenderIndexWriter(null, exportPaths.repositoryDirectory, assets).export();

        Logger.chatMessage(EnumChatFormatting.GREEN + "Render contract rebuild complete!");
        Logger.chatMessage(
                EnumChatFormatting.YELLOW
                        + "Output: "
                        + new java.io.File(exportPaths.repositoryDirectory, "canonical/render-assets.json")
                                .getAbsolutePath());
    }
}
