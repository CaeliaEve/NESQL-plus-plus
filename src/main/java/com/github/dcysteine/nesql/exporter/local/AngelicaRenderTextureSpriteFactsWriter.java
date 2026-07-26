package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.util.render.RuntimeFieldResolver;
import com.github.dcysteine.nesql.exporter.util.render.NativeSpriteFrameMaterializer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.data.AnimationMetadataSection;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Streams native texture atlas sprite facts captured from the live Minecraft/Angelica runtime.
 *
 * <p>Sprite discovery and animation timeline reconstruction are runtime-probing concerns. Keeping
 * them outside {@link AngelicaRenderFactsWriter} prevents the render fact coordinator from growing
 * into another monolith as additional atlas backends and native animation metadata variants are
 * supported.</p>
 */
final class AngelicaRenderTextureSpriteFactsWriter {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final String schemaRoot;

    AngelicaRenderTextureSpriteFactsWriter(String schemaRoot) {
        this.schemaRoot = schemaRoot;
    }

    AngelicaTextureSpriteStreamCounts write(File out) throws IOException {
        AngelicaTextureSpriteStreamCounts counts = new AngelicaTextureSpriteStreamCounts();
        try (OutputStreamWriter writer = AngelicaRenderFactFileOps.createUtf8JsonlWriter(out)) {
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
                    row.addProperty("schemaVersion", schemaRoot + "/texture-sprite");
                    row.addProperty("atlas", atlas);
                    row.addProperty("spriteKey", String.valueOf(entry.getKey()));
                    row.addProperty("iconName", sprite.getIconName());
                    row.addProperty("spriteClass", sprite.getClass().getName());
                    row.addProperty("originX", safeInt(new IntSupplier() { public int get() { return sprite.getOriginX(); } }, -1));
                    row.addProperty("originY", safeInt(new IntSupplier() { public int get() { return sprite.getOriginY(); } }, -1));
                    row.addProperty("width", safeInt(new IntSupplier() { public int get() { return sprite.getIconWidth(); } }, -1));
                    row.addProperty("height", safeInt(new IntSupplier() { public int get() { return sprite.getIconHeight(); } }, -1));
                    int frameCount = safeInt(new IntSupplier() { public int get() { return sprite.getFrameCount(); } }, 0);
                    Object frames = readField(sprite, "framesTextureData", "field_110976_a");
                    int runtimeFrameCount = Math.max(frameCount, collectionSize(frames));
                    int width = safeInt(new IntSupplier() { public int get() { return sprite.getIconWidth(); } }, -1);
                    int height = safeInt(new IntSupplier() { public int get() { return sprite.getIconHeight(); } }, -1);
                    AnimationMetadataSection metadata = readAnimationMetadata(sprite);
                    int declaredFrameCount = declaredFrameCount(metadata, runtimeFrameCount);
                    NativeSpriteFrameMaterializer.Result materialization =
                            NativeSpriteFrameMaterializer.inspect(
                                    sprite,
                                    width,
                                    height,
                                    Math.max(1, Math.max(runtimeFrameCount, declaredFrameCount)));
                    boolean nativeAnimated =
                            com.github.dcysteine.nesql.exporter.canonical.ResourceAuthorityContract
                                    .isNativeSpriteAnimation(materialization.status());
                    row.addProperty("animated", nativeAnimated);
                    row.addProperty("frameCount", runtimeFrameCount);
                    row.addProperty("runtimeFrameCount", runtimeFrameCount);
                    row.addProperty("declaredFrameCount", declaredFrameCount);
                    row.addProperty("materializedFrameCount", materialization.materializedFrameCount());
                    row.addProperty("distinctFrameCount", materialization.distinctFrameCount());
                    row.addProperty("materializationStatus", materialization.status());
                    row.addProperty("materializationReason", materialization.reason());
                    row.add("materializedFrames", GSON.toJsonTree(materialization.descriptors(width, height)));
                    row.addProperty("runtimeFrameCounter", intField(sprite, -1, "frameCounter", "field_110973_g"));
                    row.addProperty("runtimeTickCounter", intField(sprite, -1, "tickCounter", "field_110983_h"));
                    boolean missingNativeTiming = false;
                    if (metadata != null) {
                        row.addProperty("defaultFrameTimeTicks", metadata.getFrameTime());
                        row.addProperty("metadataFrameCount", metadata.getFrameCount());
                        row.add("timeline", animationTimeline(metadata, runtimeFrameCount));
                        row.addProperty("timelineStatus", "native-metadata");
                        row.addProperty("interpolate", booleanMethod(metadata, "isInterpolate", false));
                    } else {
                        row.addProperty("defaultFrameTimeTicks", (Number) null);
                        row.addProperty("metadataFrameCount", (Number) null);
                        row.add("timeline", fallbackTimeline(nativeAnimated ? Math.max(frameCount, collectionSize(frames)) : 1));
                        missingNativeTiming = nativeAnimated;
                        row.addProperty("timelineStatus", missingNativeTiming ? "missing-native-metadata" : "static");
                        row.addProperty("interpolate", false);
                    }
                    if (missingNativeTiming) {
                        counts.missingTiming++;
                    }
                    writer.write(GSON.toJson(row));
                    writer.write('\n');
                    counts.textureSprites++;
                }
            }
        }
        return counts;
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
        Object value = readField(textureMap, "mapUploadedSprites", "field_94252_e");
        if (value instanceof Map<?, ?>) {
            return (Map<?, ?>) value;
        }
        value = readField(textureMap, "mapRegisteredSprites", "field_110574_e");
        if (value instanceof Map<?, ?>) {
            return (Map<?, ?>) value;
        }
        Map<?, ?> best = Collections.emptyMap();
        Class<?> type = textureMap.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object candidate = field.get(textureMap);
                    if (!(candidate instanceof Map<?, ?>)) {
                        continue;
                    }
                    Map<?, ?> map = (Map<?, ?>) candidate;
                    if (map.size() <= best.size()) {
                        continue;
                    }
                    for (Object mapValue : map.values()) {
                        if (mapValue instanceof TextureAtlasSprite) {
                            best = map;
                            break;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        if (!best.isEmpty()) {
            return best;
        }
        return Collections.emptyMap();
    }

    private static String atlasName(TextureMap textureMap) {
        Object location = readField(textureMap, "basePath", "field_94254_c");
        if (location != null) {
            return String.valueOf(location);
        }
        return textureMap.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(textureMap));
    }

    private static AnimationMetadataSection readAnimationMetadata(TextureAtlasSprite sprite) {
        Object value = readField(sprite, "animationMetadata", "field_110982_k");
        return value instanceof AnimationMetadataSection ? (AnimationMetadataSection) value : null;
    }

    private static JsonArray animationTimeline(AnimationMetadataSection metadata, int physicalFrameCount) {
        JsonArray out = new JsonArray();
        if (metadata == null) {
            return fallbackTimeline(physicalFrameCount);
        }
        int metadataFrameCount = safeInt(new IntSupplier() { public int get() { return metadata.getFrameCount(); } }, 0);
        int timelineLength = Math.max(metadataFrameCount, 0);
        if (timelineLength == 0) {
            timelineLength = Math.max(physicalFrameCount, 0);
        }
        for (int timelineIndex = 0; timelineIndex < timelineLength; timelineIndex++) {
            final int index = timelineIndex;
            JsonObject frame = new JsonObject();
            frame.addProperty("timelineIndex", timelineIndex);
            frame.addProperty("frameIndex", intMethod(metadata, "getFrameIndex", new Class[] { int.class }, new Object[] { Integer.valueOf(index) }, index));
            frame.addProperty("durationTicks", intMethod(metadata, "getFrameTimeSingle", new Class[] { int.class }, new Object[] { Integer.valueOf(index) }, metadata.getFrameTime()));
            frame.addProperty("durationMs", frame.get("durationTicks").getAsInt() * 50);
            out.add(frame);
        }
        return out;
    }

    private static JsonArray fallbackTimeline(int physicalFrameCount) {
        JsonArray out = new JsonArray();
        int count = Math.max(physicalFrameCount, 0);
        for (int index = 0; index < count; index++) {
            JsonObject frame = new JsonObject();
            frame.addProperty("timelineIndex", index);
            frame.addProperty("frameIndex", index);
            frame.addProperty("durationTicks", 1);
            frame.addProperty("durationMs", 50);
            out.add(frame);
        }
        return out;
    }

    private static int intMethod(Object target, String methodName, Class[] parameterTypes, Object[] args, int fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            Object value = method.invoke(target, args);
            return value instanceof Number ? ((Number) value).intValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static boolean booleanMethod(Object target, String methodName, boolean fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return value instanceof Boolean ? ((Boolean) value).booleanValue() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int intField(Object target, int fallback, String... fieldNames) {
        Object value = readField(target, fieldNames);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static int declaredFrameCount(
            AnimationMetadataSection metadata,
            int runtimeFrameCount) {
        if (metadata == null) {
            return Math.max(0, runtimeFrameCount);
        }
        int timelineLength = safeInt(
                new IntSupplier() { public int get() { return metadata.getFrameCount(); } },
                0);
        int declared = 0;
        for (int timelineIndex = 0; timelineIndex < timelineLength; timelineIndex++) {
            final int index = timelineIndex;
            int frameIndex = safeInt(
                    new IntSupplier() { public int get() { return metadata.getFrameIndex(index); } },
                    index);
            declared = Math.max(declared, frameIndex + 1);
        }
        return declared > 0 ? declared : Math.max(0, runtimeFrameCount);
    }

    private static Object readField(Object target, String... fieldNames) {
        return RuntimeFieldResolver.read(target, fieldNames);
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


    interface IntSupplier { int get(); }
    interface BooleanSupplier { boolean get(); }
}
