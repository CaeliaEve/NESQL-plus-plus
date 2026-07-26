package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.local.RawExportFileCatalog;
import com.github.dcysteine.nesql.exporter.local.RawExportGeneration;
import com.github.dcysteine.nesql.exporter.plugin.ExporterState;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;
import com.github.dcysteine.nesql.exporter.plugin.nei.FailingNeiPluginExporterFixture;
import com.github.dcysteine.nesql.exporter.registry.PluginRegistry;
import com.github.dcysteine.nesql.sql.Plugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/** Production-chain regression for fail-closed NEI publication and generation cleanup. */
public final class NeiPluginFailurePipelineTest {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private NeiPluginFailurePipelineTest() {}

    public static void main(String[] args) throws Exception {
        File repository = Files.createTempDirectory("nesql-nei-plugin-failure-").toFile();
        try {
            File oldCurrent = createCurrentGeneration(repository, "plugin-old-generation", "plugin-old");
            EntityManager entityManager = noOpProxy(EntityManager.class);
            EntityManagerFactory entityManagerFactory = noOpProxy(EntityManagerFactory.class);
            FailingNeiPluginExporterFixture neiExporter =
                    new FailingNeiPluginExporterFixture(new ExporterState(entityManager));
            Map<Plugin, PluginExporter> activePlugins = new LinkedHashMap<Plugin, PluginExporter>();
            activePlugins.put(Plugin.NEI, neiExporter);
            ExportRuntime runtime = createRuntime(
                    entityManagerFactory,
                    entityManager,
                    new PluginRegistry(),
                    activePlugins);
            ExportContext context = ExportContext.forRepositoryDirectoryForTest(
                    ExportProfile.DATA_ONLY_V104,
                    "nei-plugin-failure",
                    repository);

            try {
                ExportStageRunner.run(context, new FailingNeiExecutionStrategy(runtime));
                throw new AssertionError("Expected NEI plugin pipeline failure");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().contains("partial result is not allowed"),
                        "runtime rejects the real NeiPluginExporter partial result");
            }

            require(runtime.pluginExecutions.size() == 2,
                    "initialize and failing process executions are recorded");
            ExportRuntime.PluginExecution processExecution = runtime.pluginExecutions.get(1);
            require(processExecution.plugin.equals(Plugin.NEI.name()), "NEI execution is recorded");
            require(processExecution.phase.equals(ExportPluginLifecycleCatalog.PHASE_PROCESS),
                    "NEI process phase is recorded");
            require(processExecution.result.status == PluginExportResult.Status.PARTIAL,
                    "NEI handler failure remains partial at runtime boundary");
            require(processExecution.result.errors.get(0).code.equals("nei-handler-export-failed"),
                    "NEI handler failure code reaches runtime boundary");

            File currentAfterFailure = RawExportGeneration.requireCurrentDirectory(repository);
            require(currentAfterFailure.getCanonicalFile().equals(oldCurrent.getCanonicalFile()),
                    "stage failure keeps the exact current generation pointer");
            require(readText(new File(currentAfterFailure, "generation.txt")).equals("plugin-old"),
                    "previous current generation remains readable");
            require(countStagingGenerations(repository) == 0,
                    "ExportStageRunner failure cleanup removes the staging generation");
        } finally {
            deleteRecursively(repository);
        }
    }

    private static File createCurrentGeneration(
            File repository, String generationId, String marker) throws Exception {
        File authorityRoot = RawExportFileCatalog.rawExportRootDirectory(repository);
        File generation = new File(new File(authorityRoot, "generations"), generationId);
        if (!generation.mkdirs() && !generation.isDirectory()) {
            throw new IllegalStateException("Failed to create fixture generation");
        }
        Files.write(
                new File(generation, "generation.txt").toPath(),
                marker.getBytes(StandardCharsets.UTF_8));

        JsonObject pointer = new JsonObject();
        pointer.addProperty("schemaVersion", "nesqlpp/raw-export-generation-pointer/v1");
        pointer.addProperty("generationId", generationId);
        pointer.addProperty("relativePath", "generations/" + generationId);
        pointer.addProperty("publishedAtEpochMs", 1L);
        File pointerFile = new File(authorityRoot, "current.json");
        try (FileOutputStream output = new FileOutputStream(pointerFile);
             OutputStreamWriter writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            GSON.toJson(pointer, writer);
        }
        return generation.getCanonicalFile();
    }

    private static int countStagingGenerations(File repository) {
        File generations = new File(
                RawExportFileCatalog.rawExportRootDirectory(repository),
                "generations");
        File[] matches = generations.listFiles(
                (directory, name) -> name.startsWith(".staging-"));
        return matches == null ? 0 : matches.length;
    }

    private static String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static ExportRuntime createRuntime(
            EntityManagerFactory entityManagerFactory,
            EntityManager entityManager,
            PluginRegistry registry,
            Map<Plugin, PluginExporter> activePlugins) throws Exception {
        java.lang.reflect.Constructor<ExportRuntime> constructor =
                ExportRuntime.class.getDeclaredConstructor(
                        EntityManagerFactory.class,
                        EntityManager.class,
                        PluginRegistry.class,
                        Map.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                entityManagerFactory,
                entityManager,
                registry,
                activePlugins);
    }

    @SuppressWarnings("unchecked")
    private static <T> T noOpProxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, args) -> primitiveDefault(method.getReturnType()));
    }

    private static Object primitiveDefault(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Float.TYPE) {
            return 0.0f;
        }
        return 0.0d;
    }

    private static void deleteRecursively(File file) throws Exception {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IllegalStateException("Failed to list test directory " + file);
            }
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        Files.delete(file.toPath());
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("NEI plugin failure pipeline regression failed: " + label);
        }
    }

    private static final class FailingNeiExecutionStrategy implements ExportExecutionStrategy {
        private final ExportRuntime runtime;

        FailingNeiExecutionStrategy(ExportRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public void announceStartup(ExportContext exportContext, File repositoryDirectory) {}

        @Override
        public void announceCompletion(ExportContext exportContext, File repositoryDirectory) {}

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
            return runtime;
        }

        @Override
        public boolean initializeRendering(ExportContext exportContext, File imageDirectory) {
            return false;
        }

        @Override
        public ExportSession startSession(ExportContext exportContext, ExportRuntime exportRuntime) {
            return new ExportSession(exportRuntime, null);
        }

        @Override
        public void runCollectionStage(ExportContext exportContext, ExportRuntime exportRuntime) {
            exportRuntime.runPluginPipeline();
        }

        @Override
        public void finishTransaction(
                ExportContext exportContext, EntityTransaction transaction) {}
    }
}
