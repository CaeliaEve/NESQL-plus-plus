package com.github.dcysteine.nesql.exporter.local;

import buildcraft.api.facades.FacadeAPI;
import buildcraft.api.facades.IFacadeItem;
import com.github.dcysteine.nesql.exporter.canonical.ResourceAuthorityContract;
import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.registry.GameRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;

/** Streams BuildCraft facade targets resolved exclusively through the live facade API. */
final class RawExportFacadeResolutionWriter {
    private static final int BATCH_SIZE = 4096;
    private static final String SCHEMA_VERSION =
            "nesqlpp/raw-export/alpha1/facade-resolution";
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final EntityManager entityManager;

    RawExportFacadeResolutionWriter(EntityManager entityManager) {
        if (entityManager == null) {
            throw new IllegalArgumentException("Facade resolution entity manager must not be null");
        }
        this.entityManager = entityManager;
    }

    RawFacadeResolutionCounts write(File out) throws IOException {
        RawFacadeResolutionCounts counts = new RawFacadeResolutionCounts();
        IFacadeItem facadeApi = FacadeAPI.facadeItem;
        net.minecraft.item.Item facadeRuntimeItem =
                facadeApi instanceof net.minecraft.item.Item
                        ? (net.minecraft.item.Item) facadeApi
                        : null;
        try (OutputStreamWriter writer = RawExportSidecarFileOps.createUtf8JsonlWriter(out)) {
            if (facadeApi == null || facadeRuntimeItem == null) {
                Logger.MOD.warn("BuildCraft FacadeAPI.facadeItem is unavailable; facade authority stream is empty");
                return counts;
            }
            String lastId = null;
            while (true) {
                List<com.github.dcysteine.nesql.sql.base.item.Item> batch =
                        loadItemBatch(lastId, BATCH_SIZE);
                if (batch.isEmpty()) {
                    break;
                }
                for (com.github.dcysteine.nesql.sql.base.item.Item item : batch) {
                    net.minecraft.item.Item runtimeItem =
                            net.minecraft.item.Item.getItemById(item.getItemId());
                    if (runtimeItem != facadeRuntimeItem) {
                        continue;
                    }
                    JsonObject row = resolve(item, facadeApi, runtimeItem);
                    GSON.toJson(row, writer);
                    writer.write('\n');
                    updateCounts(counts, row.get("status").getAsString());
                }
                lastId = batch.get(batch.size() - 1).getId();
                entityManager.clear();
            }
        }
        return counts;
    }

    private JsonObject resolve(
            com.github.dcysteine.nesql.sql.base.item.Item item,
            IFacadeItem facadeApi,
            net.minecraft.item.Item runtimeItem) {
        JsonObject row = baseRow(item);
        ItemStack facadeStack;
        try {
            facadeStack = reconstruct(item, runtimeItem);
        } catch (Exception e) {
            finish(row, 0, 0, "facade-item-stack-reconstruction-failed");
            row.addProperty("errorType", e.getClass().getName());
            return row;
        }

        Block[] blocks;
        int[] metas;
        try {
            blocks = facadeApi.getBlocksForFacade(facadeStack);
            metas = facadeApi.getMetaValuesForFacade(facadeStack);
        } catch (Throwable error) {
            finish(row, 0, 0, "buildcraft-facade-api-call-failed");
            row.addProperty("errorType", error.getClass().getName());
            return row;
        }

        int blockCount = blocks == null ? 0 : blocks.length;
        int metaCount = metas == null ? 0 : metas.length;
        int targetCount = Math.max(blockCount, metaCount);
        int resolvedTargetCount = 0;
        JsonArray targets = new JsonArray();
        for (int index = 0; index < targetCount; index++) {
            Block block = index < blockCount ? blocks[index] : null;
            Integer meta = index < metaCount ? Integer.valueOf(metas[index]) : null;
            JsonObject target = new JsonObject();
            target.addProperty("targetIndex", index);
            if (meta == null) {
                target.add("meta", null);
            } else {
                target.addProperty("meta", meta);
            }
            String registryName = blockRegistryName(block);
            if (registryName == null) {
                target.add("blockRegistryName", null);
            } else {
                target.addProperty("blockRegistryName", registryName);
            }
            if (block == null || meta == null || registryName == null) {
                target.addProperty("status", ResourceAuthorityContract.FACADE_UNRESOLVED);
                target.addProperty("reason", "facade-target-block-or-meta-unavailable");
                targets.add(target);
                continue;
            }
            try {
                ItemStack sourceStack = new ItemStack(block, 1, meta.intValue());
                String sourceItemId = ResourceAuthorityContract.canonicalItemId(
                        IdUtil.itemId(sourceStack));
                target.addProperty("sourceItemId", sourceItemId);
                target.addProperty(
                        "sourceAssetId",
                        ResourceAuthorityContract.canonicalItemAssetId(sourceItemId));
                target.addProperty("status", ResourceAuthorityContract.FACADE_RESOLVED);
                target.addProperty("reason", "source-item-resolved-from-buildcraft-target");
                resolvedTargetCount++;
            } catch (Throwable error) {
                target.addProperty("status", ResourceAuthorityContract.FACADE_UNRESOLVED);
                target.addProperty("reason", "facade-target-source-item-unavailable");
                target.addProperty("errorType", error.getClass().getName());
            }
            targets.add(target);
        }
        row.add("targets", targets);
        String reason;
        if (targetCount == 0) {
            reason = "buildcraft-facade-api-returned-no-targets";
        } else if (resolvedTargetCount == targetCount) {
            reason = "all-facade-targets-resolved-by-buildcraft-api";
        } else if (resolvedTargetCount > 0) {
            reason = "some-facade-targets-unresolved-by-buildcraft-api";
        } else {
            reason = "no-facade-targets-resolved-by-buildcraft-api";
        }
        finish(row, targetCount, resolvedTargetCount, reason);
        return row;
    }

    private static JsonObject baseRow(
            com.github.dcysteine.nesql.sql.base.item.Item item) {
        JsonObject row = new JsonObject();
        row.addProperty("schemaVersion", SCHEMA_VERSION);
        row.addProperty("facadeItemId", item.getId());
        row.addProperty("facadeAssetId", "nesqlpp:item/" + item.getId());
        row.add("targets", new JsonArray());
        return row;
    }

    private static void finish(
            JsonObject row,
            int targetCount,
            int resolvedTargetCount,
            String reason) {
        row.addProperty("targetCount", targetCount);
        row.addProperty("resolvedTargetCount", resolvedTargetCount);
        row.addProperty(
                "status",
                ResourceAuthorityContract.facadeStatus(targetCount, resolvedTargetCount));
        row.addProperty("reason", reason);
    }

    private static ItemStack reconstruct(
            com.github.dcysteine.nesql.sql.base.item.Item item,
            net.minecraft.item.Item runtimeItem) throws Exception {
        if (runtimeItem == null) {
            throw new IllegalStateException("Minecraft item registry lookup returned null");
        }
        ItemStack stack = new ItemStack(runtimeItem, 1, item.getItemDamage());
        String nbtText = item.getNbt();
        if (nbtText != null && !nbtText.trim().isEmpty()) {
            NBTBase parsed = JsonToNBT.func_150315_a(nbtText);
            if (!(parsed instanceof NBTTagCompound)) {
                throw new IllegalArgumentException("Facade NBT root is not a compound");
            }
            stack.setTagCompound((NBTTagCompound) parsed);
        }
        return stack;
    }

    private static String blockRegistryName(Block block) {
        if (block == null) {
            return null;
        }
        try {
            GameRegistry.UniqueIdentifier identifier = GameRegistry.findUniqueIdentifierFor(block);
            if (identifier != null) {
                return identifier.modId + ":" + identifier.name;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private List<com.github.dcysteine.nesql.sql.base.item.Item> loadItemBatch(
            String lastId,
            int batchSize) {
        String jpql = lastId == null
                ? "SELECT i FROM Item i ORDER BY i.id"
                : "SELECT i FROM Item i WHERE i.id > :lastId ORDER BY i.id";
        TypedQuery<com.github.dcysteine.nesql.sql.base.item.Item> query =
                entityManager.createQuery(
                        jpql,
                        com.github.dcysteine.nesql.sql.base.item.Item.class);
        if (lastId != null) {
            query.setParameter("lastId", lastId);
        }
        query.setMaxResults(batchSize);
        List<com.github.dcysteine.nesql.sql.base.item.Item> rows = query.getResultList();
        return rows == null
                ? Collections.<com.github.dcysteine.nesql.sql.base.item.Item>emptyList()
                : rows;
    }

    private static void updateCounts(RawFacadeResolutionCounts counts, String status) {
        counts.total++;
        if (ResourceAuthorityContract.FACADE_RESOLVED.equals(status)) {
            counts.resolved++;
        } else if (ResourceAuthorityContract.FACADE_PARTIAL.equals(status)) {
            counts.partial++;
        } else {
            counts.unresolved++;
        }
    }
}
