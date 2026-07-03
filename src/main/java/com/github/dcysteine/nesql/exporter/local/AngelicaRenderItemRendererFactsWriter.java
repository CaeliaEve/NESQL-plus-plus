package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.sql.base.item.Item;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.registry.GameRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.util.List;

/** Streams native item renderer and shader item facts from the live Minecraft renderer registry. */
final class AngelicaRenderItemRendererFactsWriter {
    private static final int ITEM_BATCH_SIZE = 4096;
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final EntityManager entityManager;
    private final String schemaRoot;

    AngelicaRenderItemRendererFactsWriter(EntityManager entityManager, String schemaRoot) {
        this.entityManager = entityManager;
        this.schemaRoot = schemaRoot;
    }

    AngelicaItemRendererStreamCounts write(File out, File shaderOut) throws IOException {
        AngelicaItemRendererStreamCounts counts = new AngelicaItemRendererStreamCounts();
        AngelicaRenderFactFileOps.ensureOutputFile(out);
        AngelicaRenderFactFileOps.ensureOutputFile(shaderOut);
        try (OutputStreamWriter writer = AngelicaRenderFactFileOps.createUtf8JsonlWriter(out);
             OutputStreamWriter shaderWriter = AngelicaRenderFactFileOps.createUtf8JsonlWriter(shaderOut)) {
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
        row.addProperty("schemaVersion", schemaRoot + "/item-renderer");
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
        AngelicaRendererClassification classification = AngelicaRendererClassificationCatalog.classifyItemRenderer(item, rendererClass);
        row.addProperty("rendererClass", rendererClass);
        row.addProperty("rendererKind", classification.kind);
        row.addProperty("usesShader", classification.usesShader);
        row.addProperty("requiresFramebufferCapture", classification.requiresFramebufferCapture);
        row.addProperty("supportsNativeAtlas", renderer == null || "ae2.native-sprite-item-renderer".equals(classification.kind));
        row.addProperty("knownSpecialRendererUnclassified", AngelicaRendererClassificationCatalog.isKnownSpecialRendererGap(item, rendererClass, classification));
        row.addProperty("notes", classification.notes);
        return row;
    }

    private JsonObject toShaderItemRow(Item item, JsonObject rendererRow) {
        String rendererKind = stringValue(rendererRow, "rendererKind");
        AngelicaRendererClassification classification = AngelicaRendererClassificationCatalog.byKind(rendererKind);
        if (classification == null || !classification.shaderExportEligible) {
            return null;
        }
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", schemaRoot + "/shader-item");
        row.addProperty("itemId", item.getId());
        row.addProperty("modId", item.getModId());
        row.addProperty("internalName", item.getInternalName());
        row.addProperty("damage", item.getItemDamage());
        row.addProperty("localizedName", item.getLocalizedName());
        row.addProperty("rendererClass", stringValue(rendererRow, "rendererClass"));
        row.addProperty("rendererKind", rendererKind);
        row.addProperty("shaderFamily", classification.shaderFamily);
        row.addProperty("timeSource", classification.shaderTimeSource);
        row.addProperty("captureRequired", booleanValue(rendererRow, "requiresFramebufferCapture"));
        row.addProperty("preferredExport", "angelica-framebuffer-capture");
        row.addProperty("browserReimplementationAllowed", false);
        row.add("textureHints", shaderTextureHints(item));
        row.addProperty("notes", "Native renderer requires shader/capture facts; do not replace with static fallback.");
        return row;
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

    private static int safeInt(IntSupplier supplier, int fallback) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return fallback;
        }
    }


    interface IntSupplier { int get(); }
    interface ObjectSupplier { Object get(); }
}
