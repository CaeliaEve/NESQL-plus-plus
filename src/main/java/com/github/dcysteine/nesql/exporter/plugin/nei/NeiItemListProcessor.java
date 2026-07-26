package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
import com.github.dcysteine.nesql.exporter.plugin.PluginExportResult;
import com.github.dcysteine.nesql.exporter.plugin.PluginHelper;
import com.github.dcysteine.nesql.exporter.plugin.base.factory.ItemFactory;
import com.github.dcysteine.nesql.exporter.util.IdUtil;
import com.github.dcysteine.nesql.exporter.util.render.RenderDiagnosticsSupport;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

public class NeiItemListProcessor extends PluginHelper {

    public NeiItemListProcessor(PluginExporter exporter) {
        super(exporter);
    }

    public PluginExportResult process() {
        NeiItemUniverse.Snapshot itemUniverse = NeiItemUniverse.load();
        List<ItemStack> exportItems = itemUniverse.items();
        int total = exportItems.size();
        int failed = 0;
        PluginExportResult.Builder result = PluginExportResult.builder();
        logger.info("Processing {} NEI items...", total);

        if (total == 0) {
            Logger.chatMessage(
                    EnumChatFormatting.RED + "NEI item list is empty; did you forget to load it?");
            if (itemUniverse.itemUniverseResult().status == PluginExportResult.Status.FAILED) {
                return itemUniverse.itemUniverseResult();
            }
            return PluginExportResult.builder()
                    .merge(itemUniverse.itemUniverseResult())
                    .merge(PluginExportResult.failed(
                            "nei-item-universe-empty",
                            "NEI item list is empty; core item collection cannot continue"))
                    .build();
        }

        ItemFactory itemFactory = new ItemFactory(exporter);
        int count = 0;
        for (ItemStack itemStack : exportItems) {
            count++;
            try {
                RenderDiagnosticsSupport.writeCurrentNeiItem(itemStack, count, total);
                itemFactory.get(itemStack);
            } catch (Exception e) {
                failed++;
                result.error(
                        "nei-item-export-failed",
                        safeItemId(itemStack) + ": " + e.getClass().getName() + ": " + safeMessage(e),
                        true);
                // GTNH has some bad items, so we have to do this =(
                // Avoid getDisplayName() here: some custom items do dangerous work in display-name paths.
                logger.info("Found a bad item: {}", safeItemId(itemStack));
                e.printStackTrace();
            }

            if (Logger.intermittentLog(count)) {
                logger.info("Processed NEI item {} of {}", count, total);
                logger.info("Most recent item id: {}", safeItemId(itemStack));
            }
        }

        exporterState.flushEntityManager();
        logger.info("Finished processing NEI items!");
        PluginExportResult itemProcessingResult = result
                .status(failed == 0 ? PluginExportResult.Status.SUCCESS : PluginExportResult.Status.PARTIAL)
                .count("itemsTotal", total)
                .count("itemsExported", total - failed)
                .count("itemsFailed", failed)
                .build();
        return PluginExportResult.builder()
                .merge(itemUniverse.itemUniverseResult())
                .merge(itemProcessingResult)
                .build();
    }

    private String safeMessage(Throwable error) {
        return error.getMessage() == null ? "<no message>" : error.getMessage();
    }

    private String safeItemId(ItemStack itemStack) {
        try {
            return itemStack == null ? "<null>" : IdUtil.itemId(itemStack);
        } catch (Throwable t) {
            return "<item-id-error:" + t.getClass().getName() + ">";
        }
    }
}
