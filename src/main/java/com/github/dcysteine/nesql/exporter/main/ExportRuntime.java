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
        registry.initializePlugins();
        registry.processPlugins();
        registry.postProcessPlugins();
    }

    @Override
    public void close() {
        entityManager.close();
        entityManagerFactory.close();
    }
}
