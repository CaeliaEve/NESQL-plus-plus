package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Map;

abstract class FacadeSemanticFamily extends AbstractSemanticFamily {
    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "block", firstNbtValue(nbt, "block", "source_block", "sourceBlock"));
        putIfPresent(facets, "metadata", firstNbtValue(nbt, "metadata", "meta"));
        putIfPresent(facets, "hollow", firstNbtValue(nbt, "hollow"));
        putIfPresent(facets, "transparent", firstNbtValue(nbt, "transparent"));
        putIfPresent(facets, "facadeType", firstNbtValue(nbt, "type", "facadeType"));
        return facets;
    }
}

final class BuildCraftFacadeFamily extends FacadeSemanticFamily {
    @Override
    public String familyId() {
        return "facade.buildcraft";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        String id = lower(item.getId());
        return (mod.contains("buildcraft") || id.contains("buildcraft"))
                && (internal.contains("facade") || nbt.contains("transparent") || nbt.contains("hollow"));
    }
}

final class Ae2FacadeFamily extends FacadeSemanticFamily {
    @Override
    public String familyId() {
        return "facade.ae2";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        String id = lower(item.getId());
        return (mod.contains("appliedenergistics") || mod.contains("appeng") || id.contains("appeng"))
                && internal.contains("facade");
    }
}

final class EnderIoPaintedFacadeFamily extends FacadeSemanticFamily {
    @Override
    public String familyId() {
        return "facade.enderio.paint";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        return mod.contains("enderio")
                && (internal.contains("conduitfacade")
                || nbt.contains("source_block")
                || nbt.contains("sourceblock")
                || nbt.contains("painter"));
    }
}
