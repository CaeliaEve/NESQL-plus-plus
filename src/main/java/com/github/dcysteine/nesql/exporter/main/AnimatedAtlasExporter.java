package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.CanonicalAnimatedAtlasPackWriter;
import net.minecraft.util.EnumChatFormatting;

/** Rebuilds animated atlas outputs from existing NESQL++ data and rendered image assets. */
public final class AnimatedAtlasExporter {
    private final ExportPaths exportPaths;

    public AnimatedAtlasExporter() {
        this(com.github.dcysteine.nesql.exporter.main.config.ConfigOptions.REPOSITORY_NAME.get());
    }

    public AnimatedAtlasExporter(String repositoryName) {
        this.exportPaths = ExportPaths.forRepository(repositoryName);
    }

    public void exportReportException() {
        try {
            export();
        } catch (Exception e) {
            Logger.MOD.error("Animated atlas rebuild failed", e);
            Logger.chatMessage(EnumChatFormatting.RED + "Animated atlas rebuild failed: " + e.getMessage());
        }
    }

    public void export() throws Exception {
        if (!exportPaths.repositoryDirectory.exists()) {
            throw new IllegalStateException("Repository does not exist: " + exportPaths.repositoryDirectory.getAbsolutePath());
        }

        ExportRuntime runtime = ExportDatabaseSupport.createFileRuntime(exportPaths.databaseFile);
        try {
            new CanonicalAnimatedAtlasPackWriter(runtime.entityManager, exportPaths.repositoryDirectory).export();
        } finally {
            runtime.close();
        }

        Logger.chatMessage(EnumChatFormatting.GREEN + "Animated atlas rebuild complete!");
        Logger.chatMessage(
                EnumChatFormatting.YELLOW
                        + "Output: "
                        + new java.io.File(exportPaths.repositoryDirectory, "canonical/animated-atlas-manifest.json")
                                .getAbsolutePath());
    }
}
