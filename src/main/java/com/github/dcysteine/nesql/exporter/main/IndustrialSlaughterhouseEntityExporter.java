package com.github.dcysteine.nesql.exporter.main;

import com.github.dcysteine.nesql.exporter.main.config.ConfigOptions;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import com.github.dcysteine.nesql.exporter.util.render.EntityModelContractExporter;
import com.github.dcysteine.nesql.exporter.util.render.EntityPreviewRequest;
import com.github.dcysteine.nesql.exporter.util.render.RenderDispatcher;
import com.github.dcysteine.nesql.exporter.util.render.RenderJob;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exports live-rendered mob previews for Extreme Entity Crusher / industrial slaughterhouse recipes. */
public final class IndustrialSlaughterhouseEntityExporter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final String ENTITY_PREVIEW_MANIFEST_RELATIVE_PATH =
            "canonical" + File.separator + "entity-previews.json";
    private static final String ENTITY_MODEL_MANIFEST_RELATIVE_PATH =
            "canonical" + File.separator + "entity-models.json";

    private final String repositoryName;
    private final ExportPaths exportPaths;

    public IndustrialSlaughterhouseEntityExporter() {
        this(ConfigOptions.REPOSITORY_NAME.get());
    }

    public IndustrialSlaughterhouseEntityExporter(String repositoryName) {
        this.repositoryName = repositoryName;
        this.exportPaths = ExportPaths.forRepository(repositoryName);
    }

    public void exportReportException() {
        try {
            export();
        } catch (Exception e) {
            Logger.MOD.error("Industrial slaughterhouse entity preview export failed", e);
            Logger.chatMessage(
                    EnumChatFormatting.RED
                            + "Industrial slaughterhouse entity preview export failed: "
                            + e.getMessage());
        }
    }

    public void export() throws Exception {
        Logger.MOD.info("============================================================");
        Logger.MOD.info("=== NESQL++ EEC Entity Preview Export STARTED ===");
        Logger.MOD.info("============================================================");
        Logger.MOD.info("Repository: {}", exportPaths.repositoryDirectory.getAbsolutePath());

        Logger.chatMessage(EnumChatFormatting.AQUA + "Starting industrial slaughterhouse entity preview export...");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Repository: " + repositoryName);

        ensureDirectory(exportPaths.repositoryDirectory, "repository");
        ensureDirectory(exportPaths.imageDirectory, "image root");
        File canonicalDir = new File(exportPaths.repositoryDirectory, "canonical");
        ensureDirectory(canonicalDir, "canonical");

        Map<String, ?> recipeMap = readKubaTechRecipeMap();
        if (recipeMap.isEmpty()) {
            throw new IllegalStateException("KubaTech recipeMap is empty; no EEC mobs were discovered.");
        }

        List<String> mobNames = new ArrayList<String>(recipeMap.keySet());
        Collections.sort(mobNames);
        Logger.chatMessage(EnumChatFormatting.GRAY + "Discovered " + mobNames.size() + " mob previews to render.");

        List<RenderJob> jobs = new ArrayList<RenderJob>(mobNames.size());
        EntityPreviewManifest manifest = new EntityPreviewManifest();
        manifest.schemaVersion = "nesqlpp/entity-previews/v1";
        manifest.generatedAtEpochMs = System.currentTimeMillis();
        manifest.repositoryName = repositoryName;
        manifest.iconDimension = ConfigOptions.ICON_DIMENSION.get();
        EntityModelManifest modelManifest = new EntityModelManifest();
        modelManifest.schemaVersion = "nesqlpp/entity-models/v1";
        modelManifest.generatedAtEpochMs = System.currentTimeMillis();
        modelManifest.repositoryName = repositoryName;

        Set<String> usedRelativePaths = new LinkedHashSet<String>();
        Set<String> usedRelativeModelPaths = new LinkedHashSet<String>();
        int prepared = 0;
        int exportedModels = 0;

        for (String mobName : mobNames) {
            Object recipeEntry = recipeMap.get(mobName);
            EntityPreviewRequest request = buildRequest(mobName, recipeEntry, usedRelativePaths);
            if (request == null) {
                continue;
            }

            EntityModelContractExporter.ExportedEntityModel exportedEntityModel =
                    EntityModelContractExporter.export(
                            exportPaths.repositoryDirectory,
                            exportPaths.imageDirectory,
                            request.getMobName(),
                            request.getLocalizedName(),
                            request.getModId(),
                            request.getEntity(),
                            usedRelativeModelPaths);
            if (exportedEntityModel != null) {
                modelManifest.entries.add(buildModelManifestEntry(request, exportedEntityModel));
                exportedModels++;
            }

            jobs.add(RenderJob.ofEntity(request));
            manifest.entries.add(buildManifestEntry(request));
            prepared++;

            if (prepared % 25 == 0) {
                Logger.chatMessage(EnumChatFormatting.GRAY + "Prepared mob previews: " + prepared);
            }
        }

        if (jobs.isEmpty()) {
            throw new IllegalStateException("No valid industrial slaughterhouse entity previews were prepared.");
        }

        RenderLifecycleSupport.initializeRendererOrThrow(exportPaths.imageDirectory);
        try {
            RenderDispatcher.INSTANCE.addAllJob(jobs);
            Logger.chatMessage(EnumChatFormatting.AQUA + "Queued " + jobs.size() + " entity preview render jobs.");
            RenderLifecycleSupport.awaitRenderCompletion();
        } finally {
            if (RenderDispatcher.INSTANCE.getRendererState() == RenderDispatcher.RendererState.INITIALIZING
                    || RenderDispatcher.INSTANCE.getRendererState() == RenderDispatcher.RendererState.INITIALIZED) {
                RenderDispatcher.INSTANCE.setRendererState(RenderDispatcher.RendererState.DESTROYING);
            }
        }

        File manifestFile = new File(exportPaths.repositoryDirectory, ENTITY_PREVIEW_MANIFEST_RELATIVE_PATH);
        writeManifest(manifestFile, manifest);
        File modelManifestFile = new File(exportPaths.repositoryDirectory, ENTITY_MODEL_MANIFEST_RELATIVE_PATH);
        writeModelManifest(modelManifestFile, modelManifest);

        Logger.MOD.info("Industrial slaughterhouse entity preview export complete: {}", manifestFile.getAbsolutePath());
        Logger.MOD.info("Rendered entity previews: {}", manifest.entries.size());
        Logger.MOD.info("Exported entity 3D models: {}", modelManifest.entries.size());
        Logger.chatMessage(EnumChatFormatting.GREEN + "Industrial slaughterhouse entity preview export complete!");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Output: " + manifestFile.getAbsolutePath());
        Logger.chatMessage(EnumChatFormatting.YELLOW + "3D model manifest: " + modelManifestFile.getAbsolutePath());
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Rendered previews: " + manifest.entries.size());
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Exported 3D models: " + exportedModels);
    }

    private static void ensureDirectory(File directory, String label) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Failed to create " + label + " directory: " + directory.getAbsolutePath());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> readKubaTechRecipeMap() throws Exception {
        Class<?> loaderClass = Class.forName("kubatech.loaders.MobHandlerLoader");
        Field recipeMapField;
        try {
            recipeMapField = loaderClass.getField("recipeMap");
        } catch (NoSuchFieldException ignored) {
            recipeMapField = loaderClass.getDeclaredField("recipeMap");
            recipeMapField.setAccessible(true);
        }
        Object value = recipeMapField.get(null);
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalStateException("kubatech.loaders.MobHandlerLoader.recipeMap is unavailable");
        }

        Map<String, Object> converted = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry.getKey() instanceof String && entry.getValue() != null) {
                converted.put((String) entry.getKey(), entry.getValue());
            }
        }
        return converted;
    }

    private static EntityPreviewRequest buildRequest(
            String mobName,
            Object recipeEntry,
            Set<String> usedRelativePaths) {
        if (mobName == null || mobName.trim().isEmpty() || recipeEntry == null) {
            return null;
        }

        try {
            Object recipe = readFieldValue(recipeEntry, "recipe");
            EntityLiving entity = createEntityCopy(recipeEntry, recipe);
            if (entity == null) {
                return null;
            }

            String localizedName = safeEntityName(entity, mobName);
            String modId = deriveMobModId(mobName, entity);
            String relativeImagePath = allocateRelativeImagePath(mobName, modId, usedRelativePaths);
            return new EntityPreviewRequest(
                    mobName,
                    localizedName,
                    modId,
                    entity,
                    relativeImagePath,
                    16,
                    75);
        } catch (Exception e) {
            Logger.MOD.warn("Failed to prepare entity preview request for {}", mobName, e);
            return null;
        }
    }

    private static EntityLiving createEntityCopy(Object recipeEntry, Object recipe) throws Exception {
        if (recipe != null) {
            try {
                Method createEntityCopy = recipe.getClass().getMethod("createEntityCopy");
                Object value = createEntityCopy.invoke(recipe);
                if (value instanceof EntityLiving) {
                    return (EntityLiving) value;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }

        Object entityCopy = readFieldValue(recipeEntry, "entityCopy");
        return entityCopy instanceof EntityLiving ? (EntityLiving) entityCopy : null;
    }

    private static Object readFieldValue(Object target, String fieldName) {
        if (target == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }

        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Exception ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String safeEntityName(EntityLiving entity, String fallback) {
        try {
            String localized = entity.getCommandSenderName();
            if (localized != null && !localized.trim().isEmpty()) {
                return localized.trim();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static String deriveMobModId(String mobName, EntityLiving entity) {
        if (mobName != null) {
            int dotIndex = mobName.indexOf('.');
            if (dotIndex > 0) {
                return IdUtil.sanitize(mobName.substring(0, dotIndex));
            }

            int colonIndex = mobName.indexOf(':');
            if (colonIndex > 0) {
                return IdUtil.sanitize(mobName.substring(0, colonIndex));
            }
        }

        if (entity != null && entity.getClass() != null && entity.getClass().getPackage() != null) {
            String packageName = entity.getClass().getPackage().getName();
            if (packageName != null && !packageName.isEmpty()) {
                String[] parts = packageName.split("\\.");
                if (parts.length > 0) {
                    return IdUtil.sanitize(parts[0]);
                }
            }
        }

        return "misc";
    }

    private static String allocateRelativeImagePath(String mobName, String modId, Set<String> usedRelativePaths) {
        String safeModId = modId == null || modId.trim().isEmpty() ? "misc" : IdUtil.sanitize(modId.trim());
        String safeMobName = IdUtil.sanitize((mobName == null ? "mob" : mobName).replace('.', '_'));
        String baseName = safeMobName.isEmpty() ? "mob" : safeMobName;
        String candidate =
                "entity"
                        + File.separator
                        + safeModId
                        + File.separator
                        + baseName
                        + ".png";

        if (usedRelativePaths.add(candidate)) {
            return candidate;
        }

        int suffix = 2;
        while (true) {
            String variant =
                    "entity"
                            + File.separator
                            + safeModId
                            + File.separator
                            + baseName
                            + "~"
                            + suffix
                            + ".png";
            if (usedRelativePaths.add(variant)) {
                return variant;
            }
            suffix++;
        }
    }

    private static EntityPreviewManifestEntry buildManifestEntry(EntityPreviewRequest request) {
        EntityPreviewManifestEntry entry = new EntityPreviewManifestEntry();
        entry.mobName = request.getMobName();
        entry.localizedName = request.getLocalizedName();
        entry.modId = request.getModId();
        entry.relativeGifPath = stripEntityFamilyPrefix(normalizePath(request.getOutputGifPath()));
        entry.frameCount = request.getFrameCount();
        entry.frameDurationMs = request.getFrameDelayMs();
        entry.width = ConfigOptions.ICON_DIMENSION.get();
        entry.height = ConfigOptions.ICON_DIMENSION.get();
        entry.renderMode = "captured_entity_turntable";
        return entry;
    }

    private static EntityModelManifestEntry buildModelManifestEntry(
            EntityPreviewRequest request,
            EntityModelContractExporter.ExportedEntityModel exportedEntityModel) {
        EntityModelManifestEntry entry = new EntityModelManifestEntry();
        entry.mobName = request.getMobName();
        entry.localizedName = request.getLocalizedName();
        entry.modId = request.getModId();
        entry.relativeModelPath = normalizePath(exportedEntityModel.relativeModelPath);
        entry.componentCount = exportedEntityModel.componentCount;
        entry.renderMode = exportedEntityModel.renderMode;
        return entry;
    }

    private static void writeManifest(File manifestFile, EntityPreviewManifest manifest) throws Exception {
        File parent = manifestFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Collections.sort(
                manifest.entries,
                new Comparator<EntityPreviewManifestEntry>() {
                    @Override
                    public int compare(EntityPreviewManifestEntry left, EntityPreviewManifestEntry right) {
                        return left.mobName.compareTo(right.mobName);
                    }
                });

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(manifestFile, false), StandardCharsets.UTF_8)) {
            GSON.toJson(manifest, writer);
        }
    }

    private static void writeModelManifest(File manifestFile, EntityModelManifest manifest) throws Exception {
        File parent = manifestFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Collections.sort(
                manifest.entries,
                new Comparator<EntityModelManifestEntry>() {
                    @Override
                    public int compare(EntityModelManifestEntry left, EntityModelManifestEntry right) {
                        return left.mobName.compareTo(right.mobName);
                    }
                });

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(manifestFile, false), StandardCharsets.UTF_8)) {
            GSON.toJson(manifest, writer);
        }
    }

    private static String normalizePath(String path) {
        return path == null ? null : path.replace(File.separatorChar, '/');
    }

    private static String stripEntityFamilyPrefix(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("entity/")) {
            return normalized.substring("entity/".length());
        }
        return normalized;
    }

    private static final class EntityPreviewManifest {
        String schemaVersion;
        long generatedAtEpochMs;
        String repositoryName;
        int iconDimension;
        List<EntityPreviewManifestEntry> entries = new ArrayList<EntityPreviewManifestEntry>();
    }

    private static final class EntityPreviewManifestEntry {
        String mobName;
        String localizedName;
        String modId;
        String relativeGifPath;
        int frameCount;
        int frameDurationMs;
        int width;
        int height;
        String renderMode;
    }

    private static final class EntityModelManifest {
        String schemaVersion;
        long generatedAtEpochMs;
        String repositoryName;
        List<EntityModelManifestEntry> entries = new ArrayList<EntityModelManifestEntry>();
    }

    private static final class EntityModelManifestEntry {
        String mobName;
        String localizedName;
        String modId;
        String relativeModelPath;
        int componentCount;
        String renderMode;
    }
}
