package com.github.dcysteine.nesql.exporter.util.render;

import com.github.dcysteine.nesql.exporter.util.IdUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Extracts generic 3D entity model contracts that NeoNEI can render through WebGL/Three.js.
 *
 * <p>This is intentionally reflective and defensive because GTNH mixes many renderer styles.
 * When a renderer exposes a {@link ModelBase} plus texture resource, we capture a scene graph of
 * textured quads. Unsupported entities return an explicit skipped result; IO, path, and input
 * contract failures are propagated to the caller.</p>
 */
public final class EntityModelContractExporter {
    private static final Gson GSON =
            new GsonBuilder().disableHtmlEscaping().serializeNulls().create();

    private static final String MODEL_SCHEMA_VERSION = "nesqlpp/entity-model/v1";
    private static final String DEFAULT_RENDER_MODE = "captured_entity_model";

    private EntityModelContractExporter() {}

    public static EntityModelExportResult export(
            File repositoryDirectory,
            File imageDirectory,
            String mobName,
            String localizedName,
            String modId,
            EntityLiving entity,
            Set<String> usedRelativeModelPaths) throws Exception {
        requireExportInput(repositoryDirectory, imageDirectory, mobName, entity, usedRelativeModelPaths);

        tickEntityForModelCapture(entity);

        EntityModelFile file = new EntityModelFile();
        file.schemaVersion = MODEL_SCHEMA_VERSION;
        file.mobName = mobName;
        file.localizedName = localizedName;
        file.modId = modId;
        file.unitScale = 16;

        addComponent(file.components, "base", entity, new ComponentTransform(), imageDirectory);

        Entity[] parts = entity.getParts();
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                Entity part = parts[i];
                if (part == null || part == entity) {
                    continue;
                }
                tickPartForModelCapture(part);
                ComponentTransform transform = ComponentTransform.relativeTo(entity, part);
                addComponent(file.components, "part_" + i, part, transform, imageDirectory);
            }
        }

        if (file.components.isEmpty()) {
            return EntityModelExportResult.skipped("no-model-components");
        }

        String relativeModelPath = allocateRelativeModelPath(mobName, modId, usedRelativeModelPaths);
        File canonicalDir = new File(repositoryDirectory, "canonical");
        File outputFile = new File(canonicalDir, relativeModelPath);
        ensureOutputFile(outputFile);

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(outputFile, false), StandardCharsets.UTF_8)) {
            GSON.toJson(file, writer);
        }

        return EntityModelExportResult.exported(
                new ExportedEntityModel(relativeModelPath, file.components.size(), DEFAULT_RENDER_MODE));
    }

    private static void requireExportInput(
            File repositoryDirectory,
            File imageDirectory,
            String mobName,
            EntityLiving entity,
            Set<String> usedRelativeModelPaths) {
        if (repositoryDirectory == null) {
            throw new IllegalArgumentException("Entity model repository directory must not be null");
        }
        if (imageDirectory == null) {
            throw new IllegalArgumentException("Entity model image directory must not be null");
        }
        if (mobName == null || mobName.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity model mob name must be non-empty");
        }
        if (entity == null) {
            throw new IllegalArgumentException("Entity model source entity must not be null");
        }
        if (usedRelativeModelPaths == null) {
            throw new IllegalArgumentException("Entity model path ownership set must not be null");
        }
    }

    private static void addComponent(
            List<EntityModelComponent> sink,
            String suggestedName,
            Entity entity,
            ComponentTransform transform,
            File imageDirectory) throws Exception {
        if (entity == null) {
            return;
        }

        Render renderer = resolveRenderer(entity);
        if (renderer == null) {
            return;
        }

        ModelBase model = resolvePrimaryModel(renderer);
        if (model == null) {
            return;
        }

        prepareModelPose(model, entity);

        List<ModelRenderer> roots = collectRootRenderers(model);
        if (roots.isEmpty()) {
            return;
        }

        ResourceLocation textureLocation = resolveTextureLocation(renderer, entity);
        String relativeTexturePath = exportTexture(textureLocation, imageDirectory);
        int textureWidth = Math.max(1, Math.round(readFloatField(model, 64.0f, "textureWidth")));
        int textureHeight = Math.max(1, Math.round(readFloatField(model, 32.0f, "textureHeight")));

        EntityModelComponent component = new EntityModelComponent();
        component.name = suggestedName;
        component.texturePath = relativeTexturePath;
        component.textureWidth = textureWidth;
        component.textureHeight = textureHeight;
        component.translation = new float[] {
                round(transform.translationX),
                round(transform.translationY),
                round(transform.translationZ),
        };
        component.rotation = new float[] {
                round(transform.rotationX),
                round(transform.rotationY),
                round(transform.rotationZ),
        };

        IdentityHashMap<ModelRenderer, Boolean> visited = new IdentityHashMap<ModelRenderer, Boolean>();
        for (ModelRenderer root : roots) {
            EntityModelNode node = exportNode(root, textureWidth, textureHeight, visited);
            if (node != null) {
                component.nodes.add(node);
            }
        }

        if (!component.nodes.isEmpty()) {
            sink.add(component);
        }
    }

    private static EntityModelNode exportNode(
            ModelRenderer modelRenderer,
            int textureWidth,
            int textureHeight,
            IdentityHashMap<ModelRenderer, Boolean> visited) {
        if (modelRenderer == null || visited.containsKey(modelRenderer)) {
            return null;
        }
        visited.put(modelRenderer, Boolean.TRUE);

        boolean showModel = readBooleanField(modelRenderer, true, "showModel", "field_78806_j");
        boolean hidden = readBooleanField(modelRenderer, false, "isHidden", "field_78807_k");
        if (!showModel || hidden) {
            return null;
        }

        EntityModelNode node = new EntityModelNode();
        node.name = readStringField(modelRenderer, "boxName", "field_78802_n");
        if (node.name == null || node.name.trim().isEmpty()) {
            node.name = "node";
        }
        node.pivot = new float[] {
                round(readFloatField(modelRenderer, 0.0f, "rotationPointX", "field_78800_c")),
                round(readFloatField(modelRenderer, 0.0f, "rotationPointY", "field_78797_d")),
                round(readFloatField(modelRenderer, 0.0f, "rotationPointZ", "field_78798_e")),
        };
        node.rotation = new float[] {
                round(readFloatField(modelRenderer, 0.0f, "rotateAngleX", "field_78795_f")),
                round(readFloatField(modelRenderer, 0.0f, "rotateAngleY", "field_78796_g")),
                round(readFloatField(modelRenderer, 0.0f, "rotateAngleZ", "field_78808_h")),
        };
        node.offset = new float[] {
                round(readFloatField(modelRenderer, 0.0f, "offsetX", "field_82906_o") * 16.0f),
                round(readFloatField(modelRenderer, 0.0f, "offsetY", "field_82908_p") * 16.0f),
                round(readFloatField(modelRenderer, 0.0f, "offsetZ", "field_82907_q") * 16.0f),
        };

        List<?> cubes = readListField(modelRenderer, "cubeList", "field_78804_l");
        if (cubes != null) {
            for (Object cube : cubes) {
                if (cube == null) {
                    continue;
                }
                appendCubeQuads(node.quads, cube, textureWidth, textureHeight);
            }
        }

        List<?> children = readListField(modelRenderer, "childModels", "field_78805_m");
        if (children != null) {
            for (Object child : children) {
                if (!(child instanceof ModelRenderer)) {
                    continue;
                }
                EntityModelNode childNode = exportNode((ModelRenderer) child, textureWidth, textureHeight, visited);
                if (childNode != null) {
                    node.children.add(childNode);
                }
            }
        }

        return node.quads.isEmpty() && node.children.isEmpty() ? null : node;
    }

    private static void appendCubeQuads(
            List<EntityModelQuad> sink,
            Object cube,
            int textureWidth,
            int textureHeight) {
        Object quadArray = readFieldValue(cube, "quadList", "field_78254_i");
        if (quadArray == null || !quadArray.getClass().isArray()) {
            return;
        }

        int quadCount = Array.getLength(quadArray);
        for (int i = 0; i < quadCount; i++) {
            Object quad = Array.get(quadArray, i);
            if (quad == null) {
                continue;
            }

            Object verticesArray = readFieldValue(quad, "vertexPositions", "field_78239_a");
            if (verticesArray == null || !verticesArray.getClass().isArray()) {
                continue;
            }

            int vertexCount = Array.getLength(verticesArray);
            if (vertexCount < 4) {
                continue;
            }

            EntityModelQuad exportedQuad = new EntityModelQuad();
            for (int v = 0; v < 4; v++) {
                Object vertex = Array.get(verticesArray, v);
                if (vertex == null) {
                    continue;
                }
                Object vector = readFieldValue(vertex, "vector3D", "field_78243_a");
                double x = readDoubleField(vector, 0.0d, "xCoord", "field_72450_a");
                double y = readDoubleField(vector, 0.0d, "yCoord", "field_72448_b");
                double z = readDoubleField(vector, 0.0d, "zCoord", "field_72449_c");
                float u = readFloatField(vertex, 0.0f, "texturePositionX", "field_78241_b");
                float t = readFloatField(vertex, 0.0f, "texturePositionY", "field_78242_c");
                exportedQuad.vertices.add(
                        new EntityModelVertex(
                                round((float) x),
                                round((float) y),
                                round((float) z),
                                round(textureWidth <= 0 ? 0.0f : (u / (float) textureWidth)),
                                round(textureHeight <= 0 ? 0.0f : (t / (float) textureHeight))));
            }

            if (exportedQuad.vertices.size() == 4) {
                sink.add(exportedQuad);
            }
        }
    }

    private static void prepareModelPose(ModelBase model, Entity entity) {
        if (model == null || entity == null) {
            return;
        }

        try {
            if (entity instanceof EntityLivingBase) {
                model.setLivingAnimations((EntityLivingBase) entity, 0.0f, 0.0f, 0.0f);
            }
        } catch (Throwable ignored) {
        }

        try {
            model.setRotationAngles(0.0f, 0.0f, entity.ticksExisted, 0.0f, 0.0f, 0.0625f, entity);
        } catch (Throwable ignored) {
        }
    }

    private static void tickEntityForModelCapture(EntityLiving entity) {
        try {
            entity.ticksExisted += 1;
        } catch (Throwable ignored) {
        }
    }

    private static void tickPartForModelCapture(Entity entity) {
        try {
            entity.ticksExisted += 1;
        } catch (Throwable ignored) {
        }
    }

    private static Render resolveRenderer(Entity entity) {
        try {
            Render render = RenderManager.instance.getEntityRenderObject(entity);
            if (render != null) {
                return render;
            }
        } catch (Throwable ignored) {
        }
        try {
            return RenderManager.instance.getEntityClassRenderObject(entity.getClass());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ModelBase resolvePrimaryModel(Render renderer) {
        if (renderer == null) {
            return null;
        }

        Object direct = readFieldValue(renderer, "mainModel", "field_77045_g");
        if (direct instanceof ModelBase) {
            return (ModelBase) direct;
        }

        Class<?> current = renderer.getClass();
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (!ModelBase.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(renderer);
                    if (value instanceof ModelBase) {
                        return (ModelBase) value;
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static List<ModelRenderer> collectRootRenderers(ModelBase model) {
        LinkedHashSet<ModelRenderer> all = new LinkedHashSet<ModelRenderer>();
        collectModelRenderersFromFields(all, model);

        // Some legacy 1.7.10 models reassign their ModelRenderer fields after first constructing
        // placeholder parts (for example Galacticraft's Slimeling model). ModelBase#boxList keeps
        // those stale placeholders forever, so treating boxList as a primary source produces
        // duplicate / outdated roots and a visibly wrong shape on the web viewer.
        //
        // Prefer the current field graph first, and only fall back to boxList when the model does
        // not expose any field-backed ModelRenderer instances.
        if (all.isEmpty()) {
            collectModelRenderersFromObject(all, readFieldValue(model, "boxList", "field_78092_r"));
        }

        if (all.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<ModelRenderer> childSet = new LinkedHashSet<ModelRenderer>();
        for (ModelRenderer renderer : all) {
            List<?> children = readListField(renderer, "childModels", "field_78805_m");
            if (children == null) {
                continue;
            }
            for (Object child : children) {
                if (child instanceof ModelRenderer) {
                    childSet.add((ModelRenderer) child);
                }
            }
        }

        List<ModelRenderer> roots = new ArrayList<ModelRenderer>();
        for (ModelRenderer renderer : all) {
            if (!childSet.contains(renderer)) {
                roots.add(renderer);
            }
        }
        return roots.isEmpty() ? new ArrayList<ModelRenderer>(all) : roots;
    }

    private static void collectModelRenderersFromFields(Set<ModelRenderer> sink, Object holder) {
        if (holder == null) {
            return;
        }

        Class<?> current = holder.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (shouldSkipRendererHarvestField(field)) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(holder);
                    collectModelRenderersFromObject(sink, value);
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
    }

    private static boolean shouldSkipRendererHarvestField(Field field) {
        if (field == null) {
            return false;
        }
        String name = field.getName();
        if (name == null) {
            return false;
        }
        return "boxList".equals(name)
                || "field_78092_r".equals(name)
                || "bipedEars".equals(name)
                || "field_78121_j".equals(name)
                || "bipedCloak".equals(name)
                || "field_78122_k".equals(name);
    }

    private static void collectModelRenderersFromObject(Set<ModelRenderer> sink, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof ModelRenderer) {
            sink.add((ModelRenderer) value);
            return;
        }
        if (value instanceof Collection<?>) {
            for (Object child : (Collection<?>) value) {
                collectModelRenderersFromObject(sink, child);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                collectModelRenderersFromObject(sink, Array.get(value, i));
            }
        }
    }

    private static ResourceLocation resolveTextureLocation(Render renderer, Entity entity) {
        if (renderer == null || entity == null) {
            return null;
        }

        Method method = findCompatibleMethod(renderer.getClass(), "getEntityTexture", entity.getClass());
        if (method == null) {
            method = findCompatibleMethod(renderer.getClass(), "func_110775_a", entity.getClass());
        }
        if (method == null) {
            return null;
        }

        try {
            method.setAccessible(true);
            Object value = method.invoke(renderer, entity);
            return value instanceof ResourceLocation ? (ResourceLocation) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?> parameterType) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (!name.equals(method.getName())) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != 1) {
                    continue;
                }
                if (parameterTypes[0].isAssignableFrom(parameterType)
                        || parameterType.isAssignableFrom(parameterTypes[0])) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String exportTexture(ResourceLocation textureLocation, File imageDirectory) throws IOException {
        if (textureLocation == null || imageDirectory == null) {
            return null;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return null;
        }

        String domain = sanitizePathSegment(textureLocation.getResourceDomain());
        String resourcePath = normalizeResourcePath(textureLocation.getResourcePath());
        String relativePath = "entity-model-textures/" + domain + "/" + resourcePath;
        File outputFile = new File(imageDirectory, relativePath.replace('/', File.separatorChar));
        if (outputFile.exists()) {
            if (!outputFile.isFile()) {
                throw new IOException(
                        "Entity model texture output path exists but is not a file: "
                                + outputFile.getAbsolutePath());
            }
            return relativePath;
        }

        ensureOutputFile(outputFile);
        IResource resource = minecraft.getResourceManager().getResource(textureLocation);
        if (resource == null) {
            return null;
        }

        try (InputStream inputStream = resource.getInputStream();
             FileOutputStream outputStream = new FileOutputStream(outputFile, false)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                outputStream.write(buffer, 0, read);
            }
        }

        return relativePath;
    }

    private static String allocateRelativeModelPath(
            String mobName,
            String modId,
            Set<String> usedRelativeModelPaths) {
        String safeModId = sanitizePathSegment(modId == null || modId.trim().isEmpty() ? "misc" : modId);
        String safeMobName = sanitizePathSegment((mobName == null ? "mob" : mobName).replace('.', '_'));
        String baseName = safeMobName.isEmpty() ? "mob" : safeMobName;
        String candidate = "entity-models/" + safeModId + "/" + baseName + ".json";

        if (usedRelativeModelPaths.add(candidate)) {
            return candidate;
        }

        int suffix = 2;
        while (true) {
            String variant = "entity-models/" + safeModId + "/" + baseName + "~" + suffix + ".json";
            if (usedRelativeModelPaths.add(variant)) {
                return variant;
            }
            suffix++;
        }
    }

    private static String normalizeResourcePath(String resourcePath) {
        if (resourcePath == null || resourcePath.trim().isEmpty()) {
            return "texture.png";
        }

        String normalized = resourcePath.replace('\\', '/').replaceAll("^/+", "");
        String[] parts = normalized.split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = sanitizePathSegment(parts[i]);
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(part);
        }

        String result = builder.length() == 0 ? "texture.png" : builder.toString();
        return result.toLowerCase(Locale.ROOT).endsWith(".png") ? result : (result + ".png");
    }

    private static String sanitizePathSegment(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("[<>:\"\\\\|?*]", "").replace(':', '_');
    }

    private static void ensureOutputFile(File outputFile) throws IOException {
        if (outputFile == null) {
            throw new IOException("Entity model output file must not be null");
        }
        if (outputFile.exists() && !outputFile.isFile()) {
            throw new IOException(
                    "Entity model output path exists but is not a file: " + outputFile.getAbsolutePath());
        }
        File parentDir = outputFile.getParentFile();
        if (parentDir == null) {
            throw new IOException("Entity model output parent must not be null: " + outputFile.getAbsolutePath());
        }
        if (parentDir.exists()) {
            if (!parentDir.isDirectory()) {
                throw new IOException(
                        "Entity model output parent exists but is not a directory: "
                                + parentDir.getAbsolutePath());
            }
            return;
        }
        if (!parentDir.mkdirs() && !parentDir.isDirectory()) {
            throw new IOException("Failed to create entity model output parent: " + parentDir.getAbsolutePath());
        }
    }

    private static Object readFieldValue(Object target, String... fieldNames) {
        if (target == null) {
            return null;
        }

        Class<?> current = target.getClass();
        while (current != null && current != Object.class) {
            for (String fieldName : fieldNames) {
                try {
                    Field field = current.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<?> readListField(Object target, String... fieldNames) {
        Object value = readFieldValue(target, fieldNames);
        return value instanceof List<?> ? (List<?>) value : null;
    }

    private static String readStringField(Object target, String... fieldNames) {
        Object value = readFieldValue(target, fieldNames);
        return value == null ? null : String.valueOf(value);
    }

    private static boolean readBooleanField(Object target, boolean fallback, String... fieldNames) {
        Object value = readFieldValue(target, fieldNames);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
    }

    private static float readFloatField(Object target, float fallback, String... fieldNames) {
        Object value = readFieldValue(target, fieldNames);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return fallback;
    }

    private static double readDoubleField(Object target, double fallback, String... fieldNames) {
        Object value = readFieldValue(target, fieldNames);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return fallback;
    }

    private static float round(float value) {
        return Math.round(value * 10000.0f) / 10000.0f;
    }

    public static final class EntityModelExportResult {
        private final ExportedEntityModel model;
        private final String skipReason;

        private EntityModelExportResult(ExportedEntityModel model, String skipReason) {
            this.model = model;
            this.skipReason = skipReason;
        }

        private static EntityModelExportResult exported(ExportedEntityModel model) {
            if (model == null) {
                throw new IllegalArgumentException("Exported entity model must not be null");
            }
            return new EntityModelExportResult(model, null);
        }

        private static EntityModelExportResult skipped(String reason) {
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("Entity model skip reason must be non-empty");
            }
            return new EntityModelExportResult(null, reason);
        }

        public boolean exported() {
            return model != null;
        }

        public ExportedEntityModel model() {
            return model;
        }

        public String skipReason() {
            return skipReason;
        }
    }

    public static final class ExportedEntityModel {
        public final String relativeModelPath;
        public final int componentCount;
        public final String renderMode;

        private ExportedEntityModel(String relativeModelPath, int componentCount, String renderMode) {
            this.relativeModelPath = relativeModelPath;
            this.componentCount = componentCount;
            this.renderMode = renderMode;
        }
    }

    private static final class ComponentTransform {
        float translationX;
        float translationY;
        float translationZ;
        float rotationX;
        float rotationY;
        float rotationZ;

        static ComponentTransform relativeTo(Entity anchor, Entity target) {
            ComponentTransform transform = new ComponentTransform();
            if (anchor == null || target == null) {
                return transform;
            }

            transform.translationX = (float) ((target.posX - anchor.posX) * 16.0d);
            transform.translationY = (float) ((target.posY - anchor.posY) * 16.0d);
            transform.translationZ = (float) ((target.posZ - anchor.posZ) * 16.0d);
            transform.rotationX = (float) Math.toRadians(target.rotationPitch);
            transform.rotationY = (float) Math.toRadians(target.rotationYaw);
            transform.rotationZ = 0.0f;
            return transform;
        }
    }

    private static final class EntityModelFile {
        String schemaVersion;
        String mobName;
        String localizedName;
        String modId;
        int unitScale;
        List<EntityModelComponent> components = new ArrayList<EntityModelComponent>();
    }

    private static final class EntityModelComponent {
        String name;
        String texturePath;
        int textureWidth;
        int textureHeight;
        float[] translation;
        float[] rotation;
        List<EntityModelNode> nodes = new ArrayList<EntityModelNode>();
    }

    private static final class EntityModelNode {
        String name;
        float[] pivot;
        float[] rotation;
        float[] offset;
        List<EntityModelQuad> quads = new ArrayList<EntityModelQuad>();
        List<EntityModelNode> children = new ArrayList<EntityModelNode>();
    }

    private static final class EntityModelQuad {
        List<EntityModelVertex> vertices = new ArrayList<EntityModelVertex>(4);
    }

    private static final class EntityModelVertex {
        float x;
        float y;
        float z;
        float u;
        float v;

        private EntityModelVertex(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }
}
