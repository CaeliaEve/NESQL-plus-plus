package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import net.minecraft.util.EnumChatFormatting;

/** Exports data only, without image rendering. */
public final class DataExporter {
    private final ExportContext exportContext;

    public DataExporter() {
        this(ConfigOptions.REPOSITORY_NAME.get());
    }

    public DataExporter(String repositoryName) {
        this.exportContext = ExportContext.forProfile(ExportProfile.DATA_ONLY_V14, repositoryName);
    }

    /**
     * Wrapper for {@link #export()} which will report exceptions to chat.
     */
    public void exportReportException() {
        try {
            export();
        } catch (Exception e) {
            Logger.MOD.error("Data export failed", e);
            Logger.chatMessage(EnumChatFormatting.RED + "Data export failed: " + e.getMessage());
        }
    }

    public void export() throws Exception {
        ExportOrchestrator.execute(exportContext);
    }
}
