package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Map;

final class ThaumcraftWandFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "thaumcraft.wand";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String id = lower(item.getId());
        return (mod.contains("thaumcraft") || mod.contains("tcwands") || id.contains("wand"))
                && (nbt.contains("rod") || nbt.contains("cap") || nbt.contains("sceptre"));
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "rod", firstNbtValue(nbt, "rod"));
        putIfPresent(facets, "cap", firstNbtValue(nbt, "cap"));
        putIfPresent(facets, "focus", firstNbtValue(nbt, "focus", "focusId"));
        String internal = lower(item.getInternalName());
        putIfPresent(facets, "kind", internal.contains("staff") ? "staff" : internal.contains("sceptre") ? "sceptre" : "wand");
        return facets;
    }
}
