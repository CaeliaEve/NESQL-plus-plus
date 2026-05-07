package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.item.Item;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.persistence.EntityManager;
import net.minecraft.util.EnumChatFormatting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Mod-split item writer for the NESQL++ v1.04 export chain.
 *
 * <p>This writer remains compatible with the existing mod-split output layout,
 * but item field mapping now flows through the NESQL++ canonical mapper.</p>
 */
public class ModBasedItemExporter {

    private final EntityManager entityManager;
    private final File exportDirectory;
    private final ModBasedItemGroupingBuilder groupingBuilder;

    public ModBasedItemExporter(EntityManager entityManager, File exportDirectory) {
        this.entityManager = entityManager;
        this.exportDirectory = exportDirectory;
        this.groupingBuilder = new ModBasedItemGroupingBuilder(new ModBasedItemDtoAssembler());
    }

    public void exportItems() throws IOException {
        Logger.MOD.info("============================================================");
        Logger.MOD.info("=== Starting Item Export ===");
        Logger.MOD.info("============================================================");
        Logger.chatMessage(EnumChatFormatting.AQUA + "=== Starting item export ===");
        Logger.chatMessage(EnumChatFormatting.YELLOW + "Grouping output by mod...");

        try {
            ModBasedItemDataset dataset = ModBasedItemDatasetLoader.load(entityManager);
            Logger.chatMessage(EnumChatFormatting.AQUA + "Found " + dataset.items.size() + " items");

            if (dataset.items.isEmpty()) {
                Logger.MOD.warn("No items found in database!");
                return;
            }

            Logger.MOD.info("Converting items to DTO and grouping by mod...");
            Map<String, List<ItemDTO>> itemsByMod = groupingBuilder.groupByMod(dataset.items);

            File itemsDir = new File(exportDirectory, "items");
            if (!itemsDir.exists()) {
                itemsDir.mkdirs();
            }

            Gson gson = new GsonBuilder()
                    .serializeNulls()
                    .create();

            int modCount = 0;
            int totalItems = 0;
            for (Map.Entry<String, List<ItemDTO>> entry : itemsByMod.entrySet()) {
                String modId = entry.getKey();
                List<ItemDTO> modItems = entry.getValue();

                modCount++;
                totalItems += modItems.size();

                Logger.MOD.info("[{}/{}] Exporting mod: {} ({} items)",
                        modCount, itemsByMod.size(), modId, modItems.size());
                Logger.chatMessage(EnumChatFormatting.YELLOW +
                        String.format("[%d/%d] Exporting mod: %s (%d items)...",
                                modCount, itemsByMod.size(), modId, modItems.size()));

                String safeModId = ModBasedRecipeFileSupport.sanitizeModId(modId);
                File modDir = new File(itemsDir, safeModId);
                if (!modDir.exists()) {
                    modDir.mkdirs();
                }

                File compressedFile = ModBasedRecipeFileSupport.jsonGzipFile(modDir, "items.json");
                long compressedSize = ModBasedRecipeFileSupport.writeCompressedJson(gson, modItems, compressedFile);

                Logger.chatMessage(EnumChatFormatting.GREEN
                        + String.format("OK %s (%d items)", modId, modItems.size()));
                Logger.chatMessage(EnumChatFormatting.YELLOW
                        + "  Wrote "
                        + ModBasedRecipeFileSupport.formatSize(compressedSize)
                        + " compressed");
            }

            Logger.MOD.info("============================================================");
            Logger.MOD.info("=== Item Export Complete ===");
            Logger.MOD.info("============================================================");
            Logger.MOD.info("Total mods: {}", itemsByMod.size());
            Logger.MOD.info("Total items: {}", totalItems);
            Logger.chatMessage(EnumChatFormatting.AQUA + "Item export complete");
            Logger.chatMessage(EnumChatFormatting.YELLOW +
                    String.format("Total: %d mods, %d items", itemsByMod.size(), totalItems));

        } catch (Exception e) {
            Logger.MOD.error("Item export FAILED", e);
            Logger.chatMessage(EnumChatFormatting.RED + "Item export failed: " + e.getMessage());
            throw e;
        }
    }

    public static class ItemDTO {
        public String itemId;
        public String modId;
        public String internalName;
        public String localizedName;
        public String renderAssetRef;
        public int damage;
        public int stackSize;
        public int maxStackSize;
        public int maxDamage;
        public String nbt;
        public String imageFileName;
        public String tooltip;
    }

}
