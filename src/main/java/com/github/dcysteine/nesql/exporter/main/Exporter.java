package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.exporter.util.render.RenderDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;

/** Exports recipes and other data to a file. */
public final class Exporter {
    private final ExportContext exportContext;

    public Exporter() {
        this(ConfigOptions.REPOSITORY_NAME.get());
    }

    public Exporter(String repositoryName) {
        this(repositoryName, ExportSelection.full());
    }

    public Exporter(String repositoryName, ExportSelection selection) {
        this.exportContext = ExportContext.forProfile(ExportProfile.FULL_V104, selection, repositoryName);
    }

    public static Exporter nativeUiExport(String repositoryName) {
        return new Exporter(
                ExportContext.forProfile(
                        ExportProfile.DATA_ONLY_V104,
                        ExportSelection.nativeUiExport(),
                        repositoryName));
    }

    private Exporter(ExportContext exportContext) {
        this.exportContext = exportContext;
    }

    /**
     * Wrapper for {@link #export()} which will report exceptions to chat.
     *
     * <p>This is needed because exceptions thrown within threads only appear in logs.
     */
    public void exportReportException() {
        while (Minecraft.getMinecraft().thePlayer == null) {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException wakeUp) {}
        }

        try {
            export();
        } catch (Exception e) {
            Logger.chatMessage(
                    EnumChatFormatting.RED
                            + "Something went wrong during export! Please check your logs.");
            throw e;
        } finally {
            RenderDispatcher.INSTANCE.setRendererState(RenderDispatcher.RendererState.ERROR);
        }
    }

    private void export() {
        try {
            ExportOrchestrator.execute(exportContext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
