package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.main.Logger;
import com.github.dcysteine.nesql.sql.base.item.Item;
import jakarta.persistence.EntityManager;

import java.util.List;

final class ModBasedItemDatasetLoader {

    private ModBasedItemDatasetLoader() {}

    static ModBasedItemDataset load(EntityManager entityManager) {
        Logger.MOD.info("Loading items from database...");
        long startTime = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        List<Item> items = entityManager
                .createQuery("SELECT i FROM Item i ORDER BY i.modId, i.localizedName", Item.class)
                .getResultList();

        long endTime = System.currentTimeMillis();
        Logger.MOD.info("Database query completed: {} ms, {} items", endTime - startTime, items.size());
        return new ModBasedItemDataset(items);
    }
}
