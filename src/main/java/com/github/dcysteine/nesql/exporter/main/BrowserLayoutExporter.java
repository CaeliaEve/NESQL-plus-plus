package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.CanonicalBrowserLayoutIndexWriter;
import net.minecraft.util.EnumChatFormatting;

/** Rebuilds only the NeoNEI item browser order/grouping contract from an existing NESQL++ repository. */
public final class BrowserLayoutExporter {
    private final ExportPaths exportPaths;

    public BrowserLayoutExporter() {
        this(com.github.dcysteine.nesql.exporter.main.config.ConfigOptions.REPOSITORY_NAME.get());
    }

    public BrowserLayoutExporter(String repositoryName) {
        this.exportPaths = ExportPaths.forRepository(repositoryName);
    }

    public void exportReportException() {
        try {
            export();
        } catch (Exception e) {
            Logger.MOD.error("Browser layout rebuild failed", e);
            Logger.chatMessage(EnumChatFormatting.RED + "Browser layout rebuild failed: " + e.getMessage());
        }
    }

    public void export() throws Exception {
        if (!exportPaths.repositoryDirectory.exists()) {
            throw new IllegalStateException("Repository does not exist: " + exportPaths.repositoryDirectory.getAbsolutePath());
        }
        java.io.File databasePropertiesFile = new java.io.File(exportPaths.databaseFile.getAbsolutePath() + ".properties");
        if (!databasePropertiesFile.exists()) {
            throw new IllegalStateException(
                    "Existing NESQL database is required for browser layout-only export: "
                            + exportPaths.databaseFile.getAbsolutePath());
        }

        Logger.chatMessage(EnumChatFormatting.AQUA + "Rebuilding browser order/grouping only...");
        Logger.chatMessage(EnumChatFormatting.GRAY + "Skipping recipe collection, image rendering, atlas and snapshots.");

        ExportRuntime runtime = ExportDatabaseSupport.createFileRuntime(exportPaths.databaseFile);
        try {
            new CanonicalBrowserLayoutIndexWriter(runtime.entityManager, exportPaths.repositoryDirectory).export();
        } finally {
            runtime.close();
        }

        Logger.chatMessage(EnumChatFormatting.GREEN + "Browser layout-only export complete!");
        Logger.chatMessage(
                EnumChatFormatting.YELLOW
                        + "Output: "
                        + new java.io.File(exportPaths.repositoryDirectory, "canonical/browser-layout-index.json")
                                .getAbsolutePath());
    }
}
