package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Map;

abstract class GeneticsSemanticFamily extends AbstractSemanticFamily {
    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "root", firstNbtValue(nbt, "root"));
        putIfPresent(facets, "species", firstNbtValue(nbt, "species", "Species"));
        putIfPresent(facets, "allele", firstNbtValue(nbt, "allele"));
        putIfPresent(facets, "chromosomes", firstNbtValue(nbt, "Chromosomes", "chromo"));
        return facets;
    }
}

final class ForestryGeneticsFamily extends GeneticsSemanticFamily {
    @Override
    public String familyId() {
        return "genetics.forestry";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        return mod.contains("forestry") && (internal.contains("bee") || nbt.contains("chromosomes") || nbt.contains("genome"));
    }
}

final class BinnieGendustryGeneticsFamily extends GeneticsSemanticFamily {
    @Override
    public String familyId() {
        return "genetics.binnie-gendustry";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        return (mod.contains("binnie") || mod.contains("genetics") || mod.contains("gendustry"))
                && (nbt.contains("gene")
                || nbt.contains("species")
                || nbt.contains("allele")
                || internal.contains("serum")
                || internal.contains("template"));
    }
}
