package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.sql.Metadata;
import jakarta.persistence.EntityTransaction;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;

interface ExportExecutionStrategy {

    void announceStartup(ExportContext exportContext, File repositoryDirectory);

    void announceCompletion(ExportContext exportContext, File repositoryDirectory);

    boolean requiresFreshRepository();

    boolean shouldLogEntityManagerClose();

    ExportRuntime createRuntime(ExportContext exportContext);

    boolean initializeRendering(ExportContext exportContext, File imageDirectory) throws Exception;

    ExportSession startSession(ExportContext exportContext, ExportRuntime exportRuntime);

    void runCollectionStage(ExportContext exportContext, ExportRuntime exportRuntime);

    void finishTransaction(ExportContext exportContext, EntityTransaction transaction);

    static ExportExecutionStrategy forProfile(ExportProfile profile) {
        switch (profile) {
            case FULL_V104:
                return new FullV104ExecutionStrategy();
            case DATA_ONLY_V14:
                return new DataOnlyExecutionStrategy();
            case IMAGES_ONLY:
                return new ImagesOnlyExecutionStrategy();
            default:
                throw new IllegalArgumentException("Unsupported profile: " + profile);
        }
    }

    final class FullV104ExecutionStrategy implements ExportExecutionStrategy {
        @Override
        public void announceStartup(ExportContext exportContext, File repositoryDirectory) {
            ExportLifecycleSupport.announceProfile(exportContext, repositoryDirectory, "Starting NESQL++ v1.04 export...");
            Logger.chatMessage(repositoryDirectory.getAbsolutePath());
        }

        @Override
        public void announceCompletion(ExportContext exportContext, File repositoryDirectory) {
            Logger.chatMessage(EnumChatFormatting.GREEN + "NESQL++ v1.04 export complete!");
        }

        @Override
        public boolean requiresFreshRepository() {
            return true;
        }

        @Override
        public boolean shouldLogEntityManagerClose() {
            return false;
        }

        @Override
        public ExportRuntime createRuntime(ExportContext exportContext) {
            return ExportDatabaseSupport.createLegacyRuntime(exportContext.paths.databaseFile);
        }

        @Override
        public boolean initializeRendering(ExportContext exportContext, File imageDirectory) {
            if (!ConfigOptions.RENDER_ICONS.get()) {
                return false;
            }
            return RenderLifecycleSupport.initializeRendererOrSkip(imageDirectory);
        }

        @Override
        public ExportSession startSession(ExportContext exportContext, ExportRuntime exportRuntime) {
            Logger.MOD.info("Initializing plugins...");
            return ExportLifecycleSupport.startSession(
                    exportRuntime,
                    "Initializing plugins.",
                    "Exporting data...");
        }

        @Override
        public void runCollectionStage(ExportContext exportContext, ExportRuntime exportRuntime) {
            exportRuntime.entityManager.persist(new Metadata(exportRuntime.activePlugins.keySet()));
            exportRuntime.runPluginPipeline();
        }

        @Override
        public void finishTransaction(ExportContext exportContext, EntityTransaction transaction) {
            Logger.chatMessage(EnumChatFormatting.AQUA + "Finalizing database commit...");
            ExportLifecycleSupport.finishTransaction(transaction, true);
            Logger.chatMessage(EnumChatFormatting.GREEN + "Database commit complete!");
        }
    }

    final class DataOnlyExecutionStrategy implements ExportExecutionStrategy {
        @Override
        public void announceStartup(ExportContext exportContext, File repositoryDirectory) {
            Logger.MOD.info("============================================================");
            Logger.MOD.info("=== NESQL Data Export STARTED ===");
            Logger.MOD.info("============================================================");
            ExportLifecycleSupport.announceProfile(exportContext, repositoryDirectory, "Starting NESQL data export...");
            Logger.MOD.info("Database: {}", exportContext.paths.databaseFile.getAbsolutePath());
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Note: Images will NOT be rendered");
            Logger.MOD.info("Checking repository directory...");
        }

        @Override
        public void announceCompletion(ExportContext exportContext, File repositoryDirectory) {
            Logger.MOD.info("============================================================");
            Logger.MOD.info("=== NESQL Data Export COMPLETE ===");
            Logger.MOD.info("============================================================");
            Logger.MOD.info("Export directory: {}", repositoryDirectory.getAbsolutePath());
            Logger.MOD.info("V14 files location:");
            Logger.MOD.info("  - items/{modId}/items.json");
            Logger.MOD.info("  - recipes/crafting/{modId}/recipes.json.gz");
            Logger.chatMessage(EnumChatFormatting.GREEN + "V14 export complete!");
            Logger.chatMessage(EnumChatFormatting.GREEN + "Database not saved (V14 files only)");
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Export location: " + repositoryDirectory.getAbsolutePath());
        }

        @Override
        public boolean requiresFreshRepository() {
            return false;
        }

        @Override
        public boolean shouldLogEntityManagerClose() {
            return true;
        }

        @Override
        public ExportRuntime createRuntime(ExportContext exportContext) {
            return ExportDatabaseSupport.createFileRuntime(exportContext.paths.databaseFile);
        }

        @Override
        public boolean initializeRendering(ExportContext exportContext, File imageDirectory) {
            return false;
        }

        @Override
        public ExportSession startSession(ExportContext exportContext, ExportRuntime exportRuntime) {
            Logger.MOD.info("Initializing plugins...");
            return ExportLifecycleSupport.startSession(
                    exportRuntime,
                    "Initializing plugins.",
                    "Exporting data...");
        }

        @Override
        public void runCollectionStage(ExportContext exportContext, ExportRuntime exportRuntime) {
            Logger.MOD.info("Step 1-3: exportRuntime.runPluginPipeline()...");
            exportRuntime.runPluginPipeline();
            Logger.MOD.info("Step 3 complete");
            Logger.MOD.info("=== postProcessPlugins() completed, starting V14 export ===");
        }

        @Override
        public void finishTransaction(ExportContext exportContext, EntityTransaction transaction) {
            Logger.MOD.info("============================================================");
            Logger.MOD.info("=== V14 export complete, skipping database commit ===");
            Logger.MOD.info("============================================================");
            Logger.MOD.info("All V14 files have been exported successfully");
            Logger.MOD.info("Skipping database commit to save time");
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Skipping database commit (V14 files already exported)");
            Logger.chatMessage(EnumChatFormatting.YELLOW + "This saves several minutes of disk I/O!");
            ExportLifecycleSupport.finishTransaction(transaction, false);
        }
    }

    final class ImagesOnlyExecutionStrategy implements ExportExecutionStrategy {
        @Override
        public void announceStartup(ExportContext exportContext, File repositoryDirectory) {
            ExportLifecycleSupport.announceProfile(exportContext, repositoryDirectory, "Starting NESQL image export...");
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Note: Only rendering images, no JSON export");
        }

        @Override
        public void announceCompletion(ExportContext exportContext, File repositoryDirectory) {
            Logger.chatMessage(EnumChatFormatting.GREEN + "Image export complete!");
        }

        @Override
        public boolean requiresFreshRepository() {
            return false;
        }

        @Override
        public boolean shouldLogEntityManagerClose() {
            return false;
        }

        @Override
        public ExportRuntime createRuntime(ExportContext exportContext) {
            return ExportDatabaseSupport.createImageRuntime(exportContext.paths.databaseFile);
        }

        @Override
        public boolean initializeRendering(ExportContext exportContext, File imageDirectory) throws Exception {
            RenderLifecycleSupport.initializeRendererOrThrow(imageDirectory);
            return true;
        }

        @Override
        public ExportSession startSession(ExportContext exportContext, ExportRuntime exportRuntime) {
            return ExportLifecycleSupport.startSession(
                    exportRuntime,
                    "Initializing plugins for image rendering.",
                    "Processing items for rendering...");
        }

        @Override
        public void runCollectionStage(ExportContext exportContext, ExportRuntime exportRuntime) {
            exportRuntime.runPluginPipeline();
        }

        @Override
        public void finishTransaction(ExportContext exportContext, EntityTransaction transaction) {
            Logger.chatMessage(EnumChatFormatting.AQUA + "Items processed for rendering!");
            ExportLifecycleSupport.finishTransaction(transaction, false);
        }
    }
}
