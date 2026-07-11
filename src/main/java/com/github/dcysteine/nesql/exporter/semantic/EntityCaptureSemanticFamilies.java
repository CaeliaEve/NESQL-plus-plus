package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Map;

abstract class EntityCaptureSemanticFamily extends AbstractSemanticFamily {
    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "entity", firstNbtValue(nbt, "Name", "EntityId", "EntityID", "mobType", "MobType", "mob", "entity", "id"));
        putIfPresent(facets, "skeletonType", firstNbtValue(nbt, "SkeletonType"));
        return facets;
    }
}

final class EnderIoEntityCaptureFamily extends EntityCaptureSemanticFamily {
    @Override
    public String familyId() {
        return "entity_capture.enderio";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        return mod.contains("enderio") && (internal.contains("soul") || internal.contains("spawner") || nbt.contains("mobtype"));
    }
}

final class GenericEntityCaptureFamily extends EntityCaptureSemanticFamily {
    @Override
    public String familyId() {
        return "entity_capture.generic";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String internal = lower(item.getInternalName());
        return internal.contains("mobsoul")
                || internal.contains("mobcrystal")
                || internal.contains("soulvial")
                || nbt.contains("mobtype")
                || (nbt.contains("entity") && nbt.contains("id"));
    }
}
