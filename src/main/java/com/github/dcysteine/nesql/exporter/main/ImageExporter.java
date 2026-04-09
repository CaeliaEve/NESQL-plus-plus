package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import net.minecraft.util.EnumChatFormatting;

/** Exports images only, without JSON data outputs. */
public final class ImageExporter {
    private final ExportContext exportContext;

    public ImageExporter() {
        this(ConfigOptions.REPOSITORY_NAME.get());
    }

    public ImageExporter(String repositoryName) {
        this.exportContext = ExportContext.forProfile(ExportProfile.IMAGES_ONLY, repositoryName);
    }

    /**
     * Wrapper for {@link #export()} which will report exceptions to chat.
     */
    public void exportReportException() {
        try {
            export();
        } catch (Exception e) {
            Logger.MOD.error("Image export failed", e);
            Logger.chatMessage(EnumChatFormatting.RED + "Image export failed: " + e.getMessage());
        }
    }

    public void export() throws Exception {
        ExportOrchestrator.execute(exportContext);
    }
}
