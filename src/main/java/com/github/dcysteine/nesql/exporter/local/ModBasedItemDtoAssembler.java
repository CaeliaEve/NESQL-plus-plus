package com.github.dcysteine.nesql.exporter.local;

import com.github.dcysteine.nesql.exporter.canonical.CanonicalExportMapper;
import com.github.dcysteine.nesql.exporter.canonical.CanonicalItem;
import com.github.dcysteine.nesql.sql.base.item.Item;

final class ModBasedItemDtoAssembler {

    ModBasedItemExporter.ItemDTO toDto(Item item) {
        CanonicalItem canonical = CanonicalExportMapper.mapItem(item);
        ModBasedItemExporter.ItemDTO dto = new ModBasedItemExporter.ItemDTO();
        dto.itemId = canonical.itemId;
        dto.modId = canonical.modId;
        dto.internalName = canonical.internalName;
        dto.localizedName = canonical.localizedName;
        dto.renderAssetRef = canonical.renderAssetRef;
        dto.damage = canonical.damage != null ? canonical.damage : 0;
        dto.stackSize = canonical.maxStackSize != null ? canonical.maxStackSize : 64;
        dto.maxStackSize = canonical.maxStackSize != null ? canonical.maxStackSize : 64;
        dto.maxDamage = canonical.maxDamage != null ? canonical.maxDamage : 0;
        dto.nbt = canonical.nbtDescriptor;
        dto.imageFileName = CanonicalExportMapper.normalizeImageFileName(item.getImageFilePath());
        dto.tooltip = canonical.tooltip;
        return dto;
    }
}
