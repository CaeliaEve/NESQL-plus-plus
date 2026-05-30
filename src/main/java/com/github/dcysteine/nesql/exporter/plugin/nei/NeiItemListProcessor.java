package com.github.dcysteine.nesql.exporter.plugin.nei;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.exporter.plugin.PluginExporter;
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

    public void process() {
        List<ItemStack> exportItems = NeiItemUniverse.getItems();
        int total = exportItems.size();
        logger.info("Processing {} NEI items...", total);

        if (total == 0) {
            Logger.chatMessage(
                    EnumChatFormatting.RED + "NEI item list is empty; did you forget to load it?");
        }

        ItemFactory itemFactory = new ItemFactory(exporter);
        int count = 0;
        for (ItemStack itemStack : exportItems) {
            count++;
            try {
                RenderDiagnosticsSupport.writeCurrentNeiItem(itemStack, count, total);
                itemFactory.get(itemStack);
            } catch (Exception e) {
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
    }

    private String safeItemId(ItemStack itemStack) {
        try {
            return itemStack == null ? "<null>" : IdUtil.itemId(itemStack);
        } catch (Throwable t) {
            return "<item-id-error:" + t.getClass().getName() + ">";
        }
    }
}
