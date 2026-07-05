package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.sql.Metadata;
import jakarta.persistence.EntityTransaction;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;

interface ExportExecutionStrategy {

    void announceStartup(ExportContext exportContext, File repositoryDirectory);

    void announceCompletion(ExportContext exportContext, File repositoryDirectory);

    boolean requiresFreshRepository(ExportContext exportContext);

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
            case DATA_ONLY_V104:
                return new DataOnlyExecutionStrategy();
            case NATIVE_UI_V104:
                return new NativeUiExecutionStrategy();
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
        public boolean requiresFreshRepository(ExportContext exportContext) {
            return exportContext.selection.isFullExportCompatible();
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
            if (!exportContext.selection.renderImages) {
                Logger.chatMessage(EnumChatFormatting.YELLOW + "Image rendering disabled by export selection.");
                return false;
            }
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
            try {
                exportRuntime.entityManager.persist(new Metadata(exportRuntime.activePlugins.keySet()));
                exportRuntime.runPluginPipeline();
            } finally {
                ExportPluginTimingReportWriter.write(exportContext, exportRuntime);
            }
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
            Logger.MOD.info("=== NESQL++ v1.04 Data Export STARTED ===");
            Logger.MOD.info("============================================================");
            ExportLifecycleSupport.announceProfile(exportContext, repositoryDirectory, "Starting NESQL++ v1.04 data export...");
            Logger.MOD.info("Database: {}", exportContext.paths.databaseFile.getAbsolutePath());
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Note: Images will NOT be rendered");
            Logger.MOD.info("Checking repository directory...");
        }

        @Override
        public void announceCompletion(ExportContext exportContext, File repositoryDirectory) {
            Logger.MOD.info("============================================================");
            Logger.MOD.info("=== NESQL++ v1.04 Data Export COMPLETE ===");
            Logger.MOD.info("============================================================");
            Logger.MOD.info("Export directory: {}", repositoryDirectory.getAbsolutePath());
            Logger.MOD.info("v1.04 data files location:");
            Logger.MOD.info("  - items/{modId}/items.json");
            Logger.MOD.info("  - recipes/crafting/{modId}/recipes.json.gz");
            Logger.chatMessage(EnumChatFormatting.GREEN + "v1.04 data export complete!");
            Logger.chatMessage(EnumChatFormatting.GREEN + "Database not saved (data files already written)");
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Export location: " + repositoryDirectory.getAbsolutePath());
        }

        @Override
        public boolean requiresFreshRepository(ExportContext exportContext) {
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
            try {
                Logger.MOD.debug("Data export collection: running plugin pipeline");
                exportRuntime.runPluginPipeline();
                Logger.MOD.debug("Data export collection complete; file writers will finalize through the stage runner");
            } finally {
                ExportPluginTimingReportWriter.write(exportContext, exportRuntime);
            }
        }

        @Override
        public void finishTransaction(ExportContext exportContext, EntityTransaction transaction) {
            Logger.MOD.info("v1.04 file export complete; skipping database commit");
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Skipping database commit (data files already exported)");
            ExportLifecycleSupport.finishTransaction(transaction, false);
        }
    }

    final class NativeUiExecutionStrategy implements ExportExecutionStrategy {
        @Override
        public void announceStartup(ExportContext exportContext, File repositoryDirectory) {
            Logger.MOD.info("============================================================");
            Logger.MOD.info("=== NESQL++ v1.04 Native UI Compiler Export STARTED ===");
            Logger.MOD.info("============================================================");
            ExportLifecycleSupport.announceProfile(
                    exportContext,
                    repositoryDirectory,
                    "Starting NESQL++ v1.04 native UI compiler export...");
            Logger.MOD.info("Database: {}", exportContext.paths.databaseFile.getAbsolutePath());
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Note: Browser atlas rendering is enabled for compiler ABI.");
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Note: Database commit is disabled for this export lane.");
        }

        @Override
        public void announceCompletion(ExportContext exportContext, File repositoryDirectory) {
            Logger.MOD.info("============================================================");
            Logger.MOD.info("=== NESQL++ v1.04 Native UI Compiler Export COMPLETE ===");
            Logger.MOD.info("============================================================");
            Logger.MOD.info("Export directory: {}", repositoryDirectory.getAbsolutePath());
            Logger.chatMessage(EnumChatFormatting.GREEN + "Native UI compiler export complete!");
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Export location: " + repositoryDirectory.getAbsolutePath());
        }

        @Override
        public boolean requiresFreshRepository(ExportContext exportContext) {
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
            if (!exportContext.selection.renderImages) {
                throw new IllegalStateException("Native UI compiler export requires browser atlas rendering.");
            }
            if (!ConfigOptions.RENDER_ICONS.get()) {
                throw new IllegalStateException("Native UI compiler export requires ConfigOptions.RENDER_ICONS.");
            }
            try {
                RenderLifecycleSupport.initializeRendererOrThrow(imageDirectory);
            } catch (Exception e) {
                throw new IllegalStateException("Native UI compiler export renderer initialization failed.", e);
            }
            return true;
        }

        @Override
        public ExportSession startSession(ExportContext exportContext, ExportRuntime exportRuntime) {
            Logger.MOD.info("Initializing plugins for native UI compiler export...");
            return ExportLifecycleSupport.startSession(
                    exportRuntime,
                    "Initializing plugins for native UI compiler export.",
                    "Exporting native UI compiler data and browser atlas assets...");
        }

        @Override
        public void runCollectionStage(ExportContext exportContext, ExportRuntime exportRuntime) {
            try {
                Logger.MOD.debug("Native UI compiler export: running plugin pipeline");
                exportRuntime.activePlugins.get(com.github.dcysteine.nesql.sql.Plugin.BASE)
                        .getDatabase()
                        .setLegacyBasePostProcessingEnabled(false);
                exportRuntime.runPluginPipeline();
                Logger.MOD.debug("Native UI compiler export collection complete");
            } finally {
                ExportPluginTimingReportWriter.write(exportContext, exportRuntime);
            }
        }

        @Override
        public void finishTransaction(ExportContext exportContext, EntityTransaction transaction) {
            Logger.MOD.info("Native UI compiler export complete; skipping database commit");
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Skipping database commit (native UI compiler export files already written)");
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
        public boolean requiresFreshRepository(ExportContext exportContext) {
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
            try {
                exportRuntime.runPluginPipeline();
            } finally {
                ExportPluginTimingReportWriter.write(exportContext, exportRuntime);
            }
        }

        @Override
        public void finishTransaction(ExportContext exportContext, EntityTransaction transaction) {
            Logger.chatMessage(EnumChatFormatting.AQUA + "Items processed for rendering!");
            ExportLifecycleSupport.finishTransaction(transaction, false);
        }
    }
}
