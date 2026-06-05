package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalRenderAsset;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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
import net.minecraft.util.IIcon;
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
    private final List<CanonicalRenderAsset> renderAssets;

    AngelicaRenderFactsWriter(EntityManager entityManager, File rawDir, List<CanonicalRenderAsset> renderAssets) {
        this.entityManager = entityManager;
        this.rawDir = rawDir;
        this.renderAssets = renderAssets == null
                ? Collections.<CanonicalRenderAsset>emptyList()
                : renderAssets;
    }

    Counts write() throws IOException {
        ensureDirectory(new File(rawDir, "facts/render"));
        Counts counts = new Counts();
        counts.backend = writeBackendFacts(new File(rawDir, "facts/render/backend.json"));
        counts.backendFacts = 1L;
        TextureSpriteStreamCounts textureSpriteCounts =
                writeTextureSpriteFacts(new File(rawDir, "facts/render/texture-sprites.jsonl.gz"));
        counts.textureSprites = textureSpriteCounts.textureSprites;
        counts.textureSpritesMissingTiming = textureSpriteCounts.missingTiming;
        ItemRendererStreamCounts itemRendererCounts = writeItemRendererFacts(
                new File(rawDir, "facts/render/item-renderers.jsonl.gz"),
                new File(rawDir, "facts/render/shader-items.jsonl.gz"));
        counts.itemRenderers = itemRendererCounts.itemRenderers;
        counts.shaderItems = itemRendererCounts.shaderItems;
        counts.shaderItemsRequiringCapture = itemRendererCounts.shaderItemsRequiringCapture;
        counts.unknownSpecialRenderers = itemRendererCounts.unknownSpecialRenderers;
        CaptureStreamCounts captureCounts = writeFramebufferCaptureFacts(
                new File(rawDir, "facts/render/framebuffer-captures.jsonl.gz"));
        counts.framebufferCaptures = captureCounts.framebufferCaptures;
        counts.framebufferCapturesWithoutFrames = captureCounts.framebufferCapturesWithoutFrames;
        MissingCaptureResult missingCaptureResult = missingCaptureResult(itemRendererCounts.captureRequiredItemIds, captureCounts.captureAssetIds, captureCounts.captureVariantKeys);
        counts.shaderItemsMissingCapture = missingCaptureResult.count;
        counts.shaderItemsMissingCaptureSamples.addAll(missingCaptureResult.samples);
        counts.framebufferCapturesWithoutFramesSamples.addAll(captureCounts.framebufferCapturesWithoutFramesSamples);
        return counts;
    }

    private String writeBackendFacts(File out) throws IOException {
        boolean angelicaPresent = detectAngelica();
        String backend = angelicaPresent ? "angelica" : "minecraft-legacy";
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_ROOT + "/backend");
        root.addProperty("generatedAt", utcNow());
        root.addProperty("backend", backend);
        root.addProperty("angelicaPresent", angelicaPresent);
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
        return backend;
    }

    private TextureSpriteStreamCounts writeTextureSpriteFacts(File out) throws IOException {
        TextureSpriteStreamCounts counts = new TextureSpriteStreamCounts();
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
                    boolean nativeAnimated = animated || frameCount > 1;
                    row.addProperty("animated", nativeAnimated);
                    row.addProperty("frameCount", nativeAnimated ? Math.max(frameCount, collectionSize(frames)) : Math.max(1, frameCount));
                    row.addProperty("runtimeFrameCounter", intField(sprite, "frameCounter", -1));
                    row.addProperty("runtimeTickCounter", intField(sprite, "tickCounter", -1));
                    AnimationMetadataSection metadata = readAnimationMetadata(sprite);
                    boolean missingNativeTiming = false;
                    if (metadata != null) {
                        row.addProperty("defaultFrameTimeTicks", metadata.getFrameTime());
                        row.addProperty("metadataFrameCount", metadata.getFrameCount());
                        row.add("timeline", animationTimeline(metadata, Math.max(frameCount, collectionSize(frames))));
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
                    if (booleanValue(row, "knownSpecialRendererUnclassified")) {
                        counts.unknownSpecialRenderers++;
                    }
                    JsonObject shaderRow = toShaderItemRow(item, row);
                    if (shaderRow != null) {
                        shaderWriter.write(GSON.toJson(shaderRow));
                        shaderWriter.write('\n');
                        counts.shaderItems++;
                        if (booleanValue(shaderRow, "captureRequired")) {
                            counts.shaderItemsRequiringCapture++;
                            counts.captureRequiredItemIds.add(item.getId());
                        }
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
        row.addProperty("knownSpecialRendererUnclassified", isKnownSpecialRendererGap(item, rendererClass, classification));
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
        row.add("textureHints", shaderTextureHints(item));
        row.addProperty("notes", "Native renderer requires shader/capture facts; do not replace with static fallback.");
        return row;
    }

    private CaptureStreamCounts writeFramebufferCaptureFacts(File out) throws IOException {
        CaptureStreamCounts counts = new CaptureStreamCounts();
        ensureDirectory(out.getParentFile());
        try (OutputStreamWriter writer = createUtf8JsonlWriter(out)) {
            for (CanonicalRenderAsset asset : renderAssets) {
                if (!isFramebufferCaptureAsset(asset)) {
                    continue;
                }
                JsonObject row = new JsonObject();
                row.addProperty("schemaVersion", SCHEMA_ROOT + "/framebuffer-capture");
                row.addProperty("assetId", asset.assetId);
                row.addProperty("variantKey", asset.variantKey);
                row.addProperty("family", asset.family);
                row.addProperty("rendererFamily", asset.rendererFamily);
                row.addProperty("renderMode", asset.renderMode);
                row.addProperty("animationMode", asset.animationMode);
                row.addProperty("captureMethod", asset.captureMethod);
                row.addProperty("captureSource", asset.captureSource);
                row.addProperty("primaryArtifact", asset.primaryArtifact);
                row.addProperty("staticFile", asset.staticFile);
                row.addProperty("framePattern", asset.framePattern);
                row.addProperty("frameCount", asset.frameCount);
                row.addProperty("capturedFrameCount", asset.capturedFrameCount);
                row.addProperty("configuredFrameCount", asset.configuredFrameCount);
                row.addProperty("frameDurationMs", asset.frameDurationMs);
                row.addProperty("frameDurationSource", asset.frameDurationSource);
                row.add("frames", GSON.toJsonTree(asset.frames));
                row.add("timeline", GSON.toJsonTree(asset.timeline));
                row.add("rendererContract", GSON.toJsonTree(asset.rendererContract));
                row.add("shaderContract", GSON.toJsonTree(asset.shaderContract));
                row.add("captureContract", GSON.toJsonTree(asset.captureContract));
                row.addProperty("source", "existing-render-dispatcher-capture");
                writer.write(GSON.toJson(row));
                writer.write('\n');
                counts.framebufferCaptures++;
                if (asset.assetId != null) {
                    counts.captureAssetIds.add(asset.assetId);
                }
                if (asset.variantKey != null) {
                    counts.captureVariantKeys.add(asset.variantKey);
                }
                if (asset.frames == null || asset.frames.isEmpty()) {
                    counts.framebufferCapturesWithoutFrames++;
                    if (counts.framebufferCapturesWithoutFramesSamples.size() < 20) {
                        counts.framebufferCapturesWithoutFramesSamples.add(asset.assetId == null ? String.valueOf(asset.variantKey) : asset.assetId);
                    }
                }
            }
        }
        return counts;
    }

    private static MissingCaptureResult missingCaptureResult(Set<String> requiredItemIds, Set<String> captureAssetIds, Set<String> captureVariantKeys) {
        MissingCaptureResult result = new MissingCaptureResult();
        for (String itemId : requiredItemIds) {
            if (itemId == null) {
                continue;
            }
            String expectedAssetId = "nesqlpp:item/" + itemId;
            if (!captureAssetIds.contains(expectedAssetId) && !captureVariantKeys.contains(itemId)) {
                result.count++;
                if (result.samples.size() < 20) {
                    result.samples.add(itemId);
                }
            }
        }
        return result;
    }

    static RendererClassification classifyRenderer(String rendererClass) {
        if (rendererClass == null || rendererClass.trim().isEmpty()) {
            return new RendererClassification("vanilla.atlas", false, false, "No inventory IItemRenderer registered.");
        }
        String lower = rendererClass.toLowerCase(Locale.ROOT);
        if (lower.contains("cosmicitemrenderer")) {
            return new RendererClassification("avaritia.cosmic", true, true, "Avaritia cosmic shader item.");
        }
        if (lower.contains("cosmicbowrenderer")) {
            return new RendererClassification("avaritia.cosmic-bow", true, true, "Avaritia infinity bow shader item.");
        }
        if (lower.contains("fancyhalorenderer")) {
            return new RendererClassification("avaritia.halo", true, true, "Avaritia halo shader item.");
        }
        if (lower.contains("fracturedorerenderer")) {
            return new RendererClassification("avaritia.fractured-ore", true, true, "Avaritia fractured ore renderer.");
        }
        if (lower.contains("eternalitemrenderer")) {
            return new RendererClassification("eternalsingularity.combined", true, true, "Eternal Singularity animated renderer.");
        }
        if (lower.contains("itemrenderercompressedchest")) {
            return new RendererClassification("avaritiaddons.compressed-chest", false, true, "Avaritiaddons compressed chest renderer.");
        }
        if (lower.contains("itemrendererinfinitychest")) {
            return new RendererClassification("avaritiaddons.infinity-chest", true, true, "Avaritiaddons infinity chest renderer.");
        }
        if (lower.contains("appeng.client.render.itemrenderer")) {
            return new RendererClassification("ae2.item-renderer", false, true, "Applied Energistics 2 custom item renderer.");
        }
        if (lower.contains("renderertrophy")) {
            return new RendererClassification("amazingtrophies.trophy", false, true, "Amazing Trophies item renderer.");
        }
        if (lower.contains("textureditemrenderer")) {
            return new RendererClassification("gtnhlib.textured-item", false, true, "GTNHLib textured item renderer.");
        }
        if (lower.contains("modelisbrh")) {
            return new RendererClassification("gtnhlib.model-isbrh", false, true, "GTNHLib inventory model renderer.");
        }
        return new RendererClassification("generic.iitemrenderer", false, false, "Custom inventory IItemRenderer without known native animation requirements.");
    }

    private static boolean isKnownSpecialRendererGap(
            Item item,
            String rendererClass,
            RendererClassification classification) {
        if (classification == null || !"generic.iitemrenderer".equals(classification.kind)) {
            return false;
        }
        StringBuilder haystack = new StringBuilder();
        if (rendererClass != null) {
            haystack.append(rendererClass).append('|');
        }
        if (item != null) {
            haystack.append(item.getModId()).append('|')
                    .append(item.getInternalName()).append('|')
                    .append(item.getLocalizedName());
        }
        String lower = haystack.toString().toLowerCase(Locale.ROOT);
        return lower.contains("avaritia")
                || lower.contains("gtnhlib")
                || lower.contains("cosmic")
                || lower.contains("halo")
                || lower.contains("singular")
                || lower.contains("universium")
                || lower.contains("infinity")
                || lower.contains("transcendent")
                || lower.contains("glitch")
                || lower.contains("wireframe")
                || lower.contains("rainbow")
                || lower.contains("gaia");
    }

    private static boolean isShaderOrCaptureFamily(String rendererKind) {
        return rendererKind != null
                && !rendererKind.equals("vanilla.atlas")
                && (rendererKind.startsWith("avaritia.")
                        || rendererKind.startsWith("gtnhlib.")
                        || rendererKind.startsWith("eternalsingularity.")
                        || rendererKind.startsWith("avaritiaddons.")
                        || rendererKind.startsWith("ae2.")
                        || rendererKind.startsWith("amazingtrophies."));
    }

    private static boolean isFramebufferCaptureAsset(CanonicalRenderAsset asset) {
        if (asset == null) {
            return false;
        }
        if (containsIgnoreCase(asset.captureMethod, "native_sprite_metadata")
                || containsIgnoreCase(asset.captureSource, "native_sprite_metadata")
                || containsIgnoreCase(asset.renderMode, "native_sprite")) {
            return false;
        }
        return containsIgnoreCase(asset.captureMethod, "framebuffer")
                || containsIgnoreCase(asset.captureSource, "framebuffer")
                || containsIgnoreCase(asset.renderMode, "framebuffer")
                || containsIgnoreCase(asset.animationMode, "framebuffer")
                || containsIgnoreCase(asset.mode, "framebuffer")
                || containsIgnoreCase(asset.primaryArtifact, ".gif")
                || asset.framePattern != null
                || (asset.capturedFrameCount != null && asset.capturedFrameCount > 1);
    }

    private static boolean containsIgnoreCase(String value, String token) {
        return value != null && token != null && value.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
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
        value = readField(textureMap, "mapRegisteredSprites");
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

    private JsonObject shaderTextureHints(Item item) {
        JsonObject hints = new JsonObject();
        ItemStack stack = resolveStack(item);
        if (stack == null || stack.getItem() == null) {
            hints.addProperty("status", "stack-unresolved");
            return hints;
        }

        hints.addProperty("status", "resolved");
        hints.addProperty("spriteNumber", safeInt(new IntSupplier() {
            public int get() {
                return stack.getItem().getSpriteNumber();
            }
        }, -1));
        addTextureHint(hints, "stackIcon", safeIconName(new ObjectSupplier() {
            public Object get() {
                return stack.getIconIndex();
            }
        }));

        Object itemTarget = stack.getItem();
        addTextureHint(hints, "itemMaskTexture", invokeTextureHint(
                itemTarget,
                "getMaskTexture",
                new Class<?>[] { ItemStack.class, net.minecraft.entity.player.EntityPlayer.class },
                new Object[] { stack, null }));
        addTextureHint(hints, "itemHaloTexture", invokeTextureHint(
                itemTarget,
                "getHaloTexture",
                new Class<?>[] { ItemStack.class },
                new Object[] { stack }));
        addTextureHint(hints, "itemOverlayIcon", invokeTextureHint(
                itemTarget,
                "getOverlayIcon",
                new Class<?>[] { ItemStack.class },
                new Object[] { stack }));
        addTextureHint(hints, "itemMaskIcon", invokeTextureHint(
                itemTarget,
                "getMaskIcon",
                new Class<?>[] { ItemStack.class },
                new Object[] { stack }));
        addTextureHint(hints, "itemHaloIcon", invokeTextureHint(
                itemTarget,
                "getHaloIcon",
                new Class<?>[] { ItemStack.class },
                new Object[] { stack }));
        addTextureHint(hints, "itemGlowIcon", invokeTextureHint(
                itemTarget,
                "getGlowIcon",
                new Class<?>[] { ItemStack.class },
                new Object[] { stack }));
        addTextureHint(hints, "itemFrameIcon", invokeTextureHint(
                itemTarget,
                "getFrameIcon",
                new Class<?>[] { ItemStack.class },
                new Object[] { stack }));

        IItemRenderer renderer = null;
        try {
            renderer = MinecraftForgeClient.getItemRenderer(stack, IItemRenderer.ItemRenderType.INVENTORY);
        } catch (Throwable ignored) {
        }
        if (renderer != null) {
            hints.addProperty("rendererClass", renderer.getClass().getName());
            addTextureHint(hints, "rendererMaskTexture", invokeTextureHint(
                    renderer,
                    "getMaskTexture",
                    new Class<?>[] { ItemStack.class },
                    new Object[] { stack }));
            addTextureHint(hints, "rendererHaloTexture", invokeTextureHint(
                    renderer,
                    "getHaloTexture",
                    new Class<?>[] { ItemStack.class },
                    new Object[] { stack }));
            addTextureHint(hints, "rendererOverlayIcon", invokeTextureHint(
                    renderer,
                    "getOverlayIcon",
                    new Class<?>[] { ItemStack.class },
                    new Object[] { stack }));
        }
        return hints;
    }

    private static void addTextureHint(JsonObject hints, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            hints.addProperty(key, value);
        }
    }

    private static String invokeTextureHint(
            Object target,
            String methodName,
            Class<?>[] parameterTypes,
            Object[] args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return textureHintValue(method.invoke(target, args));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeIconName(ObjectSupplier supplier) {
        try {
            return textureHintValue(supplier.get());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String textureHintValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof IIcon) {
            return ((IIcon) value).getIconName();
        }
        return String.valueOf(value);
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
    interface ObjectSupplier { Object get(); }

    static final class Counts {
        String backend;
        long backendFacts;
        long textureSprites;
        long textureSpritesMissingTiming;
        long itemRenderers;
        long shaderItems;
        long shaderItemsRequiringCapture;
        long shaderItemsMissingCapture;
        final List<String> shaderItemsMissingCaptureSamples = new ArrayList<String>();
        long unknownSpecialRenderers;
        long framebufferCaptures;
        long framebufferCapturesWithoutFrames;
        final List<String> framebufferCapturesWithoutFramesSamples = new ArrayList<String>();
    }

    private static final class TextureSpriteStreamCounts {
        long textureSprites;
        long missingTiming;
    }

    private static final class ItemRendererStreamCounts {
        long itemRenderers;
        long shaderItems;
        long shaderItemsRequiringCapture;
        long unknownSpecialRenderers;
        final Set<String> captureRequiredItemIds = new LinkedHashSet<String>();
    }

    private static final class CaptureStreamCounts {
        long framebufferCaptures;
        long framebufferCapturesWithoutFrames;
        final Set<String> captureAssetIds = new LinkedHashSet<String>();
        final Set<String> captureVariantKeys = new LinkedHashSet<String>();
        final List<String> framebufferCapturesWithoutFramesSamples = new ArrayList<String>();
    }

    private static final class MissingCaptureResult {
        long count;
        final List<String> samples = new ArrayList<String>();
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

