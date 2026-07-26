package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.elysium.kernel.ExportKernelContext;

final class ExportStageActionContext {
    final ExportContext exportContext;
    final ExportKernelContext kernelContext;
    final ExportExecutionStrategy strategy;
    final ExportStageState stageState;

    ExportStageActionContext(
            ExportContext exportContext,
            ExportKernelContext kernelContext,
            ExportExecutionStrategy strategy,
            ExportStageState stageState) {
        this.exportContext = exportContext;
        this.kernelContext = kernelContext;
        this.strategy = strategy;
        this.stageState = stageState;
    }

    void announceWritePreamble() {
        if (stageState.writePreambleAnnounced || exportContext.profile == ExportProfile.IMAGES_ONLY) {
            return;
        }

        Logger.chatMessage(net.minecraft.util.EnumChatFormatting.AQUA + "Data exported!");
        Logger.chatMessage(net.minecraft.util.EnumChatFormatting.AQUA + "Committing database...");
        Logger.chatMessage(
                net.minecraft.util.EnumChatFormatting.YELLOW
                        + (exportContext.profile.commitDatabase
                                ? "This may take several minutes for large datasets..."
                                : "This may take several minutes..."));
        stageState.writePreambleAnnounced = true;
    }

    void prepareRepositoryOrStop(boolean failIfExists) {
        if (!prepareRepository(failIfExists)) {
            throw new ExportStageRunner.RepositoryPreparationStoppedException();
        }
    }

    private boolean prepareRepository(boolean failIfExists) {
        if (!failIfExists && !exportContext.paths.repositoryDirectory.exists()) {
            Logger.MOD.info("Repository does not exist, creating...");
        }
        if (!ExportLifecycleSupport.ensureRepositoryDirectory(
                exportContext.paths.repositoryDirectory,
                exportContext.paths.repositoryName,
                failIfExists)) {
            if (!failIfExists) {
                Logger.MOD.error("Failed to create repository directory!");
            }
            return false;
        }
        if (!failIfExists) {
            Logger.MOD.info("Repository directory exists");
        }
        try {
            exportContext.beginRawExportGeneration();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to begin raw-export generation", e);
        }
        ExportWriterSupport.deleteCanonicalStagingDirectory(exportContext.paths.repositoryDirectory);
        return true;
    }
}
