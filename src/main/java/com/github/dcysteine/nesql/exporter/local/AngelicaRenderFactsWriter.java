package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.registry.GameRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

/**
 * Exports Angelica-native render facts for raw-export.
 *
 * <p>This writer records facts that NeoNEI can consume directly instead of guessing from static
 * images: active render backend, native atlas sprite animation timelines, and inventory
 * IItemRenderer classifications.</p>
 */
final class AngelicaRenderFactsWriter {
    private static final String SCHEMA_ROOT = "nesqlpp/raw-export/alpha1/render";
    private static final int ITEM_BATCH_SIZE = 4096;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private final EntityManager entityManager;
    private final File rawDir;

    AngelicaRenderFactsWriter(EntityManager entityManager, File rawDir) {
        this.entityManager = entityManager;
        this.rawDir = rawDir;
    }

    Counts write() throws IOException {
        ensureDirectory(new File(rawDir, "facts/render"));
        Counts counts = new Counts();
        writeBackendFacts(new File(rawDir, "facts/render/backend.json"));
        counts.backendFacts = 1L;
        counts.textureSprites = writeTextureSpriteFacts(new File(rawDir, "facts/render/texture-sprites.jsonl.gz"));
        ItemRendererStreamCounts itemRendererCounts = writeItemRendererFacts(
                new File(rawDir, "facts/render/item-renderers.jsonl.gz"),
                new File(rawDir, "facts/render/shader-items.jsonl.gz"));
        counts.itemRenderers = itemRendererCounts.itemRenderers;
        counts.shaderItems = itemRendererCounts.shaderItems;
        return counts;
    }

    private void writeBackendFacts(File out) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_ROOT + "/backend");
        root.addProperty("generatedAt", utcNow());
        root.addProperty("backend", detectAngelica() ? "angelica" : "minecraft-legacy");
        root.addProperty("angelicaPresent", detectAngelica());
        root.addProperty("irisPresent", classPresent("net.irisshaders.iris.api.v0.IrisApi"));
        root.addProperty("shaderPackInUse", detectShaderPackInUse());
        root.addProperty("shadersEnabled", detectShadersEnabled());
        root.addProperty("optifinePresent", detectOptifine());
        root.addProperty("glVendor", glString(GL11.GL_VENDOR));
        root.addProperty("glRenderer", glString(GL11.GL_RENDERER));
        root.addProperty("glVersion", glString(GL11.GL_VERSION));
        JsonObject evidence = new JsonObject();
        evidence.addProperty("angelicaAccessTransformer", classPresent("com.gtnewhorizons.angelica.loading.AngelicaTweaker")
                || classPresent("com.gtnewhorizons.angelica.Tags")
                || classPresent("com.gtnewhorizons.angelica.glsm.GLStateManager"));
        evidence.addProperty("irisApiClass", "net.irisshaders.iris.api.v0.IrisApi");
        evidence.addProperty("glsmClass", "com.gtnewhorizons.angelica.glsm.GLStateManager");
        root.add("evidence", evidence);
        writeJson(out, root);
    }

    private long writeTextureSpriteFacts(File out) throws IOException {
        long count = 0L;
        ensureDirectory(out.getParentFile());
        try (OutputStreamWriter writer = createUtf8JsonlWriter(out)) {
            Set<TextureMap> maps = collectTextureMaps();
            for (TextureMap textureMap : maps) {
                if (textureMap == null) {
                    continue;
                }
                String atlas = atlasName(textureMap);
                Map<?, ?> uploaded = readUploadedSprites(textureMap);
                for (Map.Entry<?, ?> entry : uploaded.entrySet()) {
                    Object value = entry.getValue();
                    if (!(value instanceof TextureAtlasSprite)) {
                        continue;
                    }
                    TextureAtlasSprite sprite = (TextureAtlasSprite) value;
                    JsonObject row = new JsonObject();
                    row.addProperty("schemaVersion", SCHEMA_ROOT + "/texture-sprite");
                    row.addProperty("atlas", atlas);
                    row.addProperty("spriteKey", String.valueOf(entry.getKey()));
                    row.addProperty("iconName", sprite.getIconName());
                    row.addProperty("spriteClass", sprite.getClass().getName());
                    row.addProperty("originX", safeInt(new IntSupplier() { public int get() { return sprite.getOriginX(); } }, -1));
                    row.addProperty("originY", safeInt(new IntSupplier() { public int get() { return sprite.getOriginY(); } }, -1));
                    row.addProperty("width", safeInt(new IntSupplier() { public int get() { return sprite.getIconWidth(); } }, -1));
                    row.addProperty("height", safeInt(new IntSupplier() { public int get() { return sprite.getIconHeight(); } }, -1));
                    boolean animated = safeBoolean(new BooleanSupplier() { public boolean get() { return sprite.hasAnimationMetadata(); } }, false);
                    int frameCount = safeInt(new IntSupplier() { public int get() { return sprite.getFrameCount(); } }, 0);
                    Object frames = readField(sprite, "framesTextureData");
                    row.addProperty("animated", animated || frameCount > 1 || collectionSize(frames) > 1);
                    row.addProperty("frameCount", Math.max(frameCount, collectionSize(frames)));
                    row.addProperty("runtimeFrameCounter", intField(sprite, "frameCounter", -1));
                    row.addProperty("runtimeTickCounter", intField(sprite, "tickCounter", -1));
                    AnimationMetadataSection metadata = readAnimationMetadata(sprite);
                    if (metadata != null) {
                        row.addProperty("defaultFrameTimeTicks", metadata.getFrameTime());
                        row.addProperty("metadataFrameCount", metadata.getFrameCount());
                    } else {
                        row.addProperty("defaultFrameTimeTicks", (Number) null);
                        row.addProperty("metadataFrameCount", (Number) null);
                    }
                    writer.write(GSON.toJson(row));
                    writer.write('\n');
                    count++;
                }
            }
        }
        return count;
    }

    private ItemRendererStreamCounts writeItemRendererFacts(File out, File shaderOut) throws IOException {
        ItemRendererStreamCounts counts = new ItemRendererStreamCounts();
        ensureDirectory(out.getParentFile());
        ensureDirectory(shaderOut.getParentFile());
        try (OutputStreamWriter writer = createUtf8JsonlWriter(out);
             OutputStreamWriter shaderWriter = createUtf8JsonlWriter(shaderOut)) {
            long offset = 0L;
            while (true) {
                TypedQuery<Item> query = entityManager.createQuery(
                        "SELECT i FROM Item i ORDER BY i.id", Item.class);
                query.setFirstResult((int) offset);
                query.setMaxResults(ITEM_BATCH_SIZE);
                List<Item> items = query.getResultList();
                if (items.isEmpty()) {
                    break;
                }
                for (Item item : items) {
                    JsonObject row = toItemRendererRow(item);
                    writer.write(GSON.toJson(row));
                    writer.write('\n');
                    counts.itemRenderers++;
                    JsonObject shaderRow = toShaderItemRow(item, row);
                    if (shaderRow != null) {
                        shaderWriter.write(GSON.toJson(shaderRow));
                        shaderWriter.write('\n');
                        counts.shaderItems++;
                    }
                }
                offset += items.size();
                entityManager.clear();
            }
        }
        return counts;
    }

    private JsonObject toItemRendererRow(Item item) {
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", SCHEMA_ROOT + "/item-renderer");
        row.addProperty("itemId", item.getId());
        row.addProperty("modId", item.getModId());
        row.addProperty("internalName", item.getInternalName());
        row.addProperty("damage", item.getItemDamage());
        row.addProperty("localizedName", item.getLocalizedName());
        row.addProperty("hasNbt", item.hasNbt());
        ItemStack stack = resolveStack(item);
        row.addProperty("stackResolved", stack != null);
        row.addProperty("nbtApplied", false);
        IItemRenderer renderer = null;
        if (stack != null) {
            try {
                renderer = MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
            } catch (Throwable ignored) {
            }
        }
        String rendererClass = renderer == null ? null : renderer.getClass().getName();
        RendererClassification classification = classifyRenderer(rendererClass);
        row.addProperty("rendererClass", rendererClass);
        row.addProperty("rendererKind", classification.kind);
        row.addProperty("usesShader", classification.usesShader);
        row.addProperty("requiresFramebufferCapture", classification.requiresFramebufferCapture);
        row.addProperty("supportsNativeAtlas", renderer == null);
        row.addProperty("notes", classification.notes);
        return row;
    }

    private JsonObject toShaderItemRow(Item item, JsonObject rendererRow) {
        String rendererKind = stringValue(rendererRow, "rendererKind");
        if (!isShaderOrCaptureFamily(rendererKind)) {
            return null;
        }
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", SCHEMA_ROOT + "/shader-item");
        row.addProperty("itemId", item.getId());
        row.addProperty("modId", item.getModId());
        row.addProperty("internalName", item.getInternalName());
        row.addProperty("damage", item.getItemDamage());
        row.addProperty("localizedName", item.getLocalizedName());
        row.addProperty("rendererClass", stringValue(rendererRow, "rendererClass"));
        row.addProperty("rendererKind", rendererKind);
        row.addProperty("shaderFamily", shaderFamily(rendererKind));
        row.addProperty("timeSource", shaderTimeSource(rendererKind));
        row.addProperty("captureRequired", booleanValue(rendererRow, "requiresFramebufferCapture"));
        row.addProperty("preferredExport", "angelica-framebuffer-capture");
        row.addProperty("browserReimplementationAllowed", false);
        row.addProperty("notes", "Native renderer requires shader/capture facts; do not replace with static fallback.");
        return row;
    }

    static RendererClassification classifyRenderer(String rendererClass) {
        if (rendererClass == null || rendererClass.trim().isEmpty()) {
            return new RendererClassification("vanilla.atlas", false, false, "No inventory IItemRenderer registered.");
        }
        String lower = rendererClass.toLowerCase(Locale.ROOT);
        if (lower.contains("cosmicitemrenderer")) {
            return new RendererClassification("avaritia.cosmic", true, true, "Avaritia cosmic shader item.");
        }
        if (lower.contains("fancyhalorenderer")) {
            return new RendererClassification("avaritia.halo", true, true, "Avaritia halo shader item.");
        }
        if (lower.contains("fracturedorerenderer")) {
            return new RendererClassification("avaritia.fractured-ore", true, true, "Avaritia fractured ore renderer.");
        }
        if (lower.contains("textureditemrenderer")) {
            return new RendererClassification("gtnhlib.textured-item", false, true, "GTNHLib textured item renderer.");
        }
        if (lower.contains("modelisbrh")) {
            return new RendererClassification("gtnhlib.model-isbrh", false, true, "GTNHLib inventory model renderer.");
        }
        return new RendererClassification("generic.iitemrenderer", false, true, "Custom inventory IItemRenderer.");
    }

    private static boolean isShaderOrCaptureFamily(String rendererKind) {
        return rendererKind != null
                && !rendererKind.equals("vanilla.atlas")
                && (rendererKind.startsWith("avaritia.")
                        || rendererKind.startsWith("gtnhlib.")
                        || rendererKind.equals("generic.iitemrenderer"));
    }

    private static String shaderFamily(String rendererKind) {
        if (rendererKind == null) {
            return "unknown";
        }
        if (rendererKind.equals("avaritia.cosmic")) {
            return "avaritia.cosmic";
        }
        if (rendererKind.equals("avaritia.halo")) {
            return "avaritia.halo";
        }
        if (rendererKind.equals("avaritia.fractured-ore")) {
            return "avaritia.fractured-ore";
        }
        if (rendererKind.startsWith("gtnhlib.")) {
            return rendererKind;
        }
        return "custom.inventory-renderer";
    }

    private static String shaderTimeSource(String rendererKind) {
        if (rendererKind != null && rendererKind.startsWith("avaritia.")) {
            return "native-render-tick";
        }
        return "native-renderer";
    }

    private ItemStack resolveStack(Item item) {
        try {
            net.minecraft.item.Item mcItem = GameRegistry.findItem(item.getModId(), item.getInternalName());
            if (mcItem == null) {
                return null;
            }
            return new ItemStack(mcItem, 1, item.getItemDamage());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Set<TextureMap> collectTextureMaps() {
        Set<TextureMap> maps = Collections.newSetFromMap(new IdentityHashMap<TextureMap, Boolean>());
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return maps;
            }
            try {
                TextureMap blocks = minecraft.getTextureMapBlocks();
                if (blocks != null) {
                    maps.add(blocks);
                }
            } catch (Throwable ignored) {
            }
            Class<?> type = minecraft.getClass();
            while (type != null && type != Object.class) {
                for (Field field : type.getDeclaredFields()) {
                    if (!TextureMap.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        Object value = field.get(minecraft);
                        if (value instanceof TextureMap) {
                            maps.add((TextureMap) value);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                type = type.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return maps;
    }

    private Map<?, ?> readUploadedSprites(TextureMap textureMap) {
        Object value = readField(textureMap, "mapUploadedSprites");
        if (value instanceof Map<?, ?>) {
            return (Map<?, ?>) value;
        }
        return Collections.emptyMap();
    }

    private static String atlasName(TextureMap textureMap) {
        Object location = readField(textureMap, "basePath");
        if (location != null) {
            return String.valueOf(location);
        }
        return textureMap.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(textureMap));
    }

    private static boolean detectAngelica() {
        return classPresent("com.gtnewhorizons.angelica.glsm.GLStateManager")
                || classPresent("com.gtnewhorizons.angelica.Tags")
                || classPresent("com.gtnewhorizons.angelica.loading.AngelicaTweaker");
    }

    private static boolean detectOptifine() {
        return classPresent("optifine.OptiFineForgeTweaker")
                || classPresent("Config")
                || classPresent("net.optifine.Config");
    }

    private static boolean detectShaderPackInUse() {
        try {
            Object api = irisApi();
            if (api == null) {
                return false;
            }
            Method method = api.getClass().getMethod("isShaderPackInUse");
            Object value = method.invoke(api);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean detectShadersEnabled() {
        try {
            Object api = irisApi();
            if (api == null) {
                return false;
            }
            Method getConfig = api.getClass().getMethod("getConfig");
            Object config = getConfig.invoke(api);
            if (config == null) {
                return false;
            }
            Method enabled = config.getClass().getMethod("areShadersEnabled");
            Object value = enabled.invoke(config);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object irisApi() {
        try {
            Class<?> type = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method method = type.getMethod("getInstance");
            return method.invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String glString(int name) {
        try {
            return GL11.glGetString(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static AnimationMetadataSection readAnimationMetadata(TextureAtlasSprite sprite) {
        Object value = readField(sprite, "animationMetadata");
        return value instanceof AnimationMetadataSection ? (AnimationMetadataSection) value : null;
    }

    private static int intField(Object target, String fieldName, int fallback) {
        Object value = readField(target, fieldName);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static int collectionSize(Object value) {
        if (value instanceof java.util.Collection<?>) {
            return ((java.util.Collection<?>) value).size();
        }
        if (value != null && value.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(value);
        }
        return 0;
    }

    private static int safeInt(IntSupplier supplier, int fallback) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean safeBoolean(BooleanSupplier supplier, boolean fallback) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String stringValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    private static boolean booleanValue(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return false;
        }
        return object.get(key).getAsBoolean();
    }

    private static OutputStreamWriter createUtf8JsonlWriter(File out) throws IOException {
        FileOutputStream fos = new FileOutputStream(out, false);
        if (out.getName().endsWith(".gz")) {
            return new OutputStreamWriter(new GZIPOutputStream(fos), StandardCharsets.UTF_8);
        }
        return new OutputStreamWriter(fos, StandardCharsets.UTF_8);
    }

    private static void writeJson(File out, Object value) throws IOException {
        ensureDirectory(out.getParentFile());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(out, false), StandardCharsets.UTF_8)) {
            PRETTY_GSON.toJson(value, writer);
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getAbsolutePath());
        }
    }

    private static String utcNow() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    interface IntSupplier { int get(); }
    interface BooleanSupplier { boolean get(); }

    static final class Counts {
        long backendFacts;
        long textureSprites;
        long itemRenderers;
        long shaderItems;
    }

    private static final class ItemRendererStreamCounts {
        long itemRenderers;
        long shaderItems;
    }

    static final class RendererClassification {
        final String kind;
        final boolean usesShader;
        final boolean requiresFramebufferCapture;
        final String notes;

        RendererClassification(String kind, boolean usesShader, boolean requiresFramebufferCapture, String notes) {
            this.kind = kind;
            this.usesShader = usesShader;
            this.requiresFramebufferCapture = requiresFramebufferCapture;
            this.notes = notes;
        }
    }
}
