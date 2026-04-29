package com.github.dcysteine.nesql.exporter.main;

import com.google.common.collect.ImmutableMap;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.util.Map;

/**
 * Shared database URL/property helpers for NESQL export profiles.
 */
public final class ExportDatabaseSupport {

    private ExportDatabaseSupport() {}

    public static String fileDatabaseUrl(File databaseFile) {
        return "jdbc:hsqldb:file:" + databaseFile.getAbsolutePath();
    }

    public static String memoryDatabaseUrl(String name) {
        return "jdbc:hsqldb:mem:" + name;
    }

    public static ImmutableMap<String, String> defaultProperties(String databaseUrl) {
        return ImmutableMap.of("hibernate.connection.url", databaseUrl);
    }

    public static ImmutableMap<String, String> optimizedLegacyProperties(String databaseUrl) {
        return ImmutableMap.<String, String>builder()
                .put("hibernate.connection.url", databaseUrl)
                .put("hibernate.jdbc.batch_size", "500")
                .put("hibernate.order_inserts", "true")
                .put("hibernate.order_updates", "true")
                .put("hibernate.batch_versioned_data", "true")
                .put("hibernate.jdbc.fetch_size", "1000")
                .put("hibernate.default_batch_fetch_size", "256")
                .put("hibernate.connection.autocommit", "false")
                .put("hibernate.cache.use_second_level_cache", "false")
                .put("hibernate.cache.use_query_cache", "false")
                .put("hibernate.dialect", "org.hibernate.dialect.HSQLDialect")
                .build();
    }

    public static String resolveImageDatabaseUrl(File databaseFile) {
        File databasePropertiesFile = new File(databaseFile.getAbsolutePath() + ".properties");
        if (databasePropertiesFile.exists()) {
            Logger.chatMessage(EnumChatFormatting.YELLOW + "Using existing database for filtering.");
            return fileDatabaseUrl(databaseFile);
        }

        Logger.chatMessage(EnumChatFormatting.YELLOW + "No database found, scanning all items...");
        return memoryDatabaseUrl("nesql-temp");
    }

    public static ExportRuntime createFileRuntime(File databaseFile) {
        String databaseUrl = fileDatabaseUrl(databaseFile);
        return ExportRuntime.create(databaseUrl, optimizedLegacyProperties(databaseUrl));
    }

    public static ExportRuntime createLegacyRuntime(File databaseFile) {
        String databaseUrl = fileDatabaseUrl(databaseFile);
        return ExportRuntime.create(databaseUrl, optimizedLegacyProperties(databaseUrl));
    }

    public static ExportRuntime createImageRuntime(File databaseFile) {
        String databaseUrl = resolveImageDatabaseUrl(databaseFile);
        return ExportRuntime.create(databaseUrl, defaultProperties(databaseUrl));
    }
}
