package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
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

        List<CanonicalRenderAsset> assets = ExportWriterSupport.collectRenderAssets(exportPaths.repositoryDirectory);
        Logger.chatMessage(EnumChatFormatting.AQUA + "Collected " + assets.size() + " render assets for rebuild.");

        ExportWriterSupport.writeRenderAssetManifest(null, exportPaths.repositoryDirectory, assets);
        ExportWriterSupport.writeAnimationManifest(null, exportPaths.repositoryDirectory, assets);
        ExportWriterSupport.writeAtlasPacks(null, exportPaths.repositoryDirectory, assets);
        ExportWriterSupport.writeAnimatedAtlasPacks(null, exportPaths.repositoryDirectory, assets);
        ExportWriterSupport.writeAtlasRegistry(null, exportPaths.repositoryDirectory, assets);
        ExportWriterSupport.writeRenderIndex(null, exportPaths.repositoryDirectory, assets);

        Logger.chatMessage(EnumChatFormatting.GREEN + "Render contract rebuild complete!");
        Logger.chatMessage(
                EnumChatFormatting.YELLOW
                        + "Output: "
                        + new java.io.File(exportPaths.repositoryDirectory, "canonical/render-assets.json")
                                .getAbsolutePath());
    }
}
