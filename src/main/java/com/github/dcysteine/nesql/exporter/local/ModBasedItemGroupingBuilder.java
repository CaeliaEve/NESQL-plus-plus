package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class ModBasedItemGroupingBuilder {

    private final ModBasedItemDtoAssembler dtoAssembler;

    ModBasedItemGroupingBuilder(ModBasedItemDtoAssembler dtoAssembler) {
        this.dtoAssembler = dtoAssembler;
    }

    Map<String, List<ModBasedItemExporter.ItemDTO>> groupByMod(List<Item> items) {
        Map<String, List<ModBasedItemExporter.ItemDTO>> itemsByMod = new HashMap<>();
        for (Item item : items) {
            ModBasedItemExporter.ItemDTO dto = dtoAssembler.toDto(item);
            itemsByMod.computeIfAbsent(item.getModId(), ignored -> new ArrayList<>()).add(dto);
        }
        return itemsByMod;
    }
}
