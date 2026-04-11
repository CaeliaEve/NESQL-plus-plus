package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.exporter.plugin.nei.NeiExportDebugFilter;
import net.minecraft.util.EnumChatFormatting;

/** Exports data only, without image rendering. */
public final class DataExporter {
    private final ExportContext exportContext;
    private final NeiExportDebugFilter.Mode neiDebugMode;

    public DataExporter() {
        this(ConfigOptions.REPOSITORY_NAME.get());
    }

    public DataExporter(String repositoryName) {
        this.exportContext = ExportContext.forProfile(ExportProfile.DATA_ONLY_V14, repositoryName);
        this.neiDebugMode = NeiExportDebugFilter.Mode.NONE;
    }

    private DataExporter(String repositoryName, NeiExportDebugFilter.Mode neiDebugMode) {
        this.exportContext = ExportContext.forProfile(ExportProfile.DATA_ONLY_V14, repositoryName);
        this.neiDebugMode = neiDebugMode == null ? NeiExportDebugFilter.Mode.NONE : neiDebugMode;
    }

    public static DataExporter thaumcraftDebug() {
        return thaumcraftDebug(ConfigOptions.REPOSITORY_NAME.get());
    }

    public static DataExporter thaumcraftDebug(String repositoryName) {
        return new DataExporter(repositoryName, NeiExportDebugFilter.Mode.THAUMCRAFT_FAMILY);
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
        NeiExportDebugFilter.setMode(neiDebugMode);
        try {
            ExportOrchestrator.execute(exportContext);
        } finally {
            NeiExportDebugFilter.clear();
        }
    }
}
