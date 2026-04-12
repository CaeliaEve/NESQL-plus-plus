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
        this.neiDebugMode = neiDebugMode == null ? NeiExportDebugFilter.Mode.NONE : neiDebugMode;
        this.exportContext = ExportContext.forProfile(
                ExportProfile.DATA_ONLY_V14,
                repositoryName,
                recipeModFilterFor(this.neiDebugMode));
    }

    public static DataExporter thaumcraftDebug() {
        return thaumcraftDebug(ConfigOptions.REPOSITORY_NAME.get());
    }

    public static DataExporter thaumcraftDebug(String repositoryName) {
        return new DataExporter(repositoryName, NeiExportDebugFilter.Mode.THAUMCRAFT_FAMILY);
    }

    public static DataExporter botaniaDebug() {
        return botaniaDebug(ConfigOptions.REPOSITORY_NAME.get());
    }

    public static DataExporter botaniaDebug(String repositoryName) {
        return new DataExporter(repositoryName, NeiExportDebugFilter.Mode.BOTANIA_FAMILY);
    }

    private static java.util.Set<String> recipeModFilterFor(NeiExportDebugFilter.Mode mode) {
        java.util.LinkedHashSet<String> mods = new java.util.LinkedHashSet<>();
        if (mode == NeiExportDebugFilter.Mode.BOTANIA_FAMILY) {
            mods.add("Botania");
            return mods;
        }
        if (mode == NeiExportDebugFilter.Mode.THAUMCRAFT_FAMILY) {
            mods.add("Thaumcraft");
            mods.add("ThaumicTinkerer");
            mods.add("ThaumicExploration");
            mods.add("ThaumicHorizons");
            mods.add("thaumicbases");
            mods.add("thaumicenergistics");
            mods.add("thaumicboots");
            mods.add("ForbiddenMagic");
            mods.add("Automagy");
            mods.add("gadomancy");
            mods.add("NodalMechanics");
            mods.add("WarpTheory");
            mods.add("alchgrate");
            mods.add("salisarcana");
            mods.add("TaintedMagic");
            mods.add("Timeconqueror");
            return mods;
        }
        return mods;
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
