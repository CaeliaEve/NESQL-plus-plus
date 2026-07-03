package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.registry.PluginRegistry;
import com.github.dcysteine.nesql.sql.Plugin;
import com.google.common.collect.ImmutableMap;
import org.hibernate.jpa.HibernatePersistenceProvider;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared bootstrap/runtime holder for NESQL export entrypoints.
 *
 * <p>This is a transitional extraction for the NESQL++ single-orchestrator
 * refactor. It centralizes the database + plugin bootstrap path while existing
 * exporters still own their profile-specific writing behavior.</p>
 */
public final class ExportRuntime implements AutoCloseable {
    public final EntityManagerFactory entityManagerFactory;
    public final EntityManager entityManager;
    public final PluginRegistry registry;
    public final Map<Plugin, PluginExporter> activePlugins;
    public final List<PluginTiming> pluginTimings = new ArrayList<PluginTiming>();

    private ExportRuntime(
            EntityManagerFactory entityManagerFactory,
            EntityManager entityManager,
            PluginRegistry registry,
            Map<Plugin, PluginExporter> activePlugins) {
        this.entityManagerFactory = entityManagerFactory;
        this.entityManager = entityManager;
        this.registry = registry;
        this.activePlugins = activePlugins;
    }

    public static ExportRuntime create(String databaseUrl) {
        return create(databaseUrl, ImmutableMap.<String, String>of());
    }

    public static ExportRuntime create(String databaseUrl, Map<String, String> additionalProperties) {
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        properties.put("hibernate.connection.url", databaseUrl);
        properties.putAll(additionalProperties);

        EntityManagerFactory entityManagerFactory =
                new HibernatePersistenceProvider()
                        .createEntityManagerFactory("NESQL", ImmutableMap.copyOf(properties));
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        ExporterState exporterState = new ExporterState(entityManager);
        PluginRegistry registry = new PluginRegistry();
        Map<Plugin, PluginExporter> activePlugins =
                new LinkedHashMap<>(registry.initialize(exporterState));

        return new ExportRuntime(entityManagerFactory, entityManager, registry, activePlugins);
    }

    public EntityTransaction beginTransaction() {
        EntityTransaction transaction = entityManager.getTransaction();
        transaction.begin();
        return transaction;
    }

    public void runPluginPipeline() {
        pluginTimings.clear();
        for (ExportPluginLifecycleCatalog.PhaseDescriptor phase :
                ExportPluginLifecycleCatalog.phases()) {
            runPluginPhase(phase);
        }
    }

    private void runPluginPhase(ExportPluginLifecycleCatalog.PhaseDescriptor phase) {
        for (Map.Entry<Plugin, PluginExporter> entry : activePlugins.entrySet()) {
            long startedAt = System.currentTimeMillis();
            try {
                phase.invoke(entry.getValue());
            } finally {
                pluginTimings.add(
                        new PluginTiming(
                                entry.getKey().name(),
                                entry.getValue().getClass().getName(),
                                phase.id(),
                                System.currentTimeMillis() - startedAt));
            }
        }
    }

    @Override
    public void close() {
        entityManager.close();
        entityManagerFactory.close();
    }

    public static final class PluginTiming {
        public final String plugin;
        public final String exporterClass;
        public final String phase;
        public final long elapsedMs;
        public final String elapsed;

        PluginTiming(String plugin, String exporterClass, String phase, long elapsedMs) {
            this.plugin = plugin;
            this.exporterClass = exporterClass;
            this.phase = phase;
            this.elapsedMs = elapsedMs;
            this.elapsed = formatDuration(elapsedMs);
        }

        private static String formatDuration(long elapsedMs) {
            long totalSeconds = Math.max(0L, elapsedMs / 1000L);
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            if (hours > 0L) {
                return String.format("%dh %02dm %02ds", hours, minutes, seconds);
            }
            if (minutes > 0L) {
                return String.format("%dm %02ds", minutes, seconds);
            }
            return String.format("%ds", seconds);
        }
    }
}
