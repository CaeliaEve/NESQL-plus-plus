package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.List;

final class ModBasedItemDataset {
    final List<Item> items;

    ModBasedItemDataset(List<Item> items) {
        this.items = items;
    }
}
