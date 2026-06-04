package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Map;

final class FluidContainerFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "fluid.container";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String internal = lower(item.getInternalName());
        return (nbt.contains("fluidname") || (nbt.contains("fluid") && nbt.contains("amount")))
                && (internal.contains("cell")
                || internal.contains("bucket")
                || internal.contains("capsule")
                || internal.contains("tank")
                || internal.contains("container")
                || nbt.contains("capacity"));
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "fluid", firstNbtValue(nbt, "FluidName", "fluidName", "Name"));
        putIfPresent(facets, "amount", firstNbtValue(nbt, "Amount", "amount"));
        putIfPresent(facets, "capacity", firstNbtValue(nbt, "Capacity", "capacity"));
        return facets;
    }
}
