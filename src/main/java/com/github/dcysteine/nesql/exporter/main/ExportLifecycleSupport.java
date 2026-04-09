package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.sql.Plugin;
import net.minecraft.util.EnumChatFormatting;

import jakarta.persistence.EntityTransaction;
import java.io.File;
import java.util.Map;

/**
 * Shared execution scaffolding for NESQL export entrypoints.
 */
public final class ExportLifecycleSupport {

    private ExportLifecycleSupport() {}

    public static void announceProfile(ExportContext exportContext, File repositoryDirectory, String introMessage) {
        Logger.chatMessage(EnumChatFormatting.AQUA + introMessage);
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Repository: " + exportContext.paths.repositoryName);
        Logger.chatMessage(EnumChatFormatting.YELLOW + "NESQL++ profile: " + exportContext.profile.profileId);
        Logger.MOD.info("Repository: {}", repositoryDirectory.getAbsolutePath());
        Logger.MOD.info("NESQL++ profile: {}", exportContext.profile.profileId);
        Logger.MOD.info("NESQL++ stage plan: {}", exportContext.executionPlan.describeStages());
    }

    public static boolean ensureRepositoryDirectory(File repositoryDirectory, String repositoryName, boolean failIfExists) {
        if (repositoryDirectory.exists()) {
            if (failIfExists) {
                Logger.chatMessage(
                        EnumChatFormatting.RED
                                + String.format("Cannot create repository \"%s\"; it already exists!", repositoryName));
                return false;
            }
            return true;
        }

        if (!repositoryDirectory.mkdirs()) {
            Logger.chatMessage(
                    EnumChatFormatting.RED
                            + String.format("Failed to create repository \"%s\"!", repositoryName));
            return false;
        }
        return true;
    }

    public static void announceActivePlugins(Map<Plugin, PluginExporter> activePlugins) {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Active plugins:");
        Logger.MOD.info("Active plugins: {}", activePlugins.size());
        activePlugins.keySet().forEach(
                plugin -> {
                    Logger.MOD.info("  - Plugin: {}", plugin.getName());
                    Logger.chatMessage("  " + EnumChatFormatting.YELLOW + plugin.getName());
                });
    }

    public static EntityTransaction beginTransaction(ExportRuntime exportRuntime) {
        Logger.MOD.info("Starting transaction...");
        EntityTransaction transaction = exportRuntime.beginTransaction();
        Logger.MOD.info("Transaction started");
        return transaction;
    }

    public static ExportSession startSession(
            ExportRuntime exportRuntime,
            String initializingPluginsMessage,
            String exportMessage) {
        Logger.chatMessage(EnumChatFormatting.AQUA + initializingPluginsMessage);
        ExportLifecycleSupport.announceActivePlugins(exportRuntime.activePlugins);
        Logger.chatMessage(EnumChatFormatting.AQUA + exportMessage);
        EntityTransaction transaction = ExportLifecycleSupport.beginTransaction(exportRuntime);
        return new ExportSession(exportRuntime, transaction);
    }

    public static void finishTransaction(EntityTransaction transaction, boolean commit) {
        if (commit) {
            Logger.MOD.info("Committing transaction...");
            transaction.commit();
            Logger.MOD.info("Transaction committed");
        } else {
            Logger.MOD.info("Rolling back transaction...");
            transaction.rollback();
            Logger.MOD.info("Transaction rolled back");
        }
    }

    public static void closeSession(ExportSession session, boolean logEntityManager) {
        Logger.chatMessage(EnumChatFormatting.AQUA + "Closing database...");
        if (logEntityManager) {
            Logger.MOD.info("Closing EntityManager...");
        }
        session.close();
        if (logEntityManager) {
            Logger.MOD.info("EntityManager closed");
            Logger.MOD.info("Closing EntityManagerFactory...");
            Logger.MOD.info("EntityManagerFactory closed");
        }
    }
}
