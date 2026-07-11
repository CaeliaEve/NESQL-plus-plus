package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Map;

abstract class GeneticsSemanticFamily extends AbstractSemanticFamily {
    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "root", firstNbtValue(nbt, "root"));
        putIfPresent(facets, "species", firstNbtValue(nbt, "species", "Species", "UID0"));
        putIfPresent(facets, "allele", firstNbtValue(nbt, "allele", "UID1"));
        putIfPresent(facets, "chromosomes", firstNbtValue(nbt, "Chromosomes", "chromo"));
        putIfPresent(facets, "analyzed", firstNbtValue(nbt, "IsAnalyzed"));
        putIfPresent(facets, "health", firstNbtValue(nbt, "Health", "MaxH"));
        putIfPresent(facets, "kind", geneticsKind(item));
        return facets;
    }

    private static String geneticsKind(Item item) {
        String internal = lower(item.getInternalName());
        if (internal.contains("queen")) {
            return "queen";
        }
        if (internal.contains("princess")) {
            return "princess";
        }
        if (internal.contains("drone")) {
            return "drone";
        }
        if (internal.contains("larvae") || internal.contains("larva")) {
            return "larvae";
        }
        if (internal.contains("serum")) {
            return "serum";
        }
        if (internal.contains("template")) {
            return "template";
        }
        return null;
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
