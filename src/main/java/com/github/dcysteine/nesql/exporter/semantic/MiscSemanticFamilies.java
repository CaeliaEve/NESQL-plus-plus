package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Map;

final class Ic2CropSeedFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "crop.ic2";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        return mod.equals("ic2")
                && internal.contains("cropseed")
                && (nbt.contains("growth") || nbt.contains("gain") || nbt.contains("resistance") || nbt.contains("scan"));
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "crop", firstNbtValue(nbt, "name"));
        putIfPresent(facets, "growth", firstNbtValue(nbt, "growth"));
        putIfPresent(facets, "gain", firstNbtValue(nbt, "gain"));
        putIfPresent(facets, "resistance", firstNbtValue(nbt, "resistance"));
        putIfPresent(facets, "scan", firstNbtValue(nbt, "scan"));
        putIfPresent(facets, "owner", firstNbtValue(nbt, "owner"));
        return facets;
    }
}

final class EncodedPatternFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "data_carrier.encoded-pattern";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String internal = lower(item.getInternalName());
        return nbt.contains("encodedpattern") || internal.contains("encoded") || internal.contains("pattern");
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "encodedPattern", firstNbtValue(nbt, "encodedPattern", "EncodedPattern"));
        putIfPresent(facets, "output", firstNbtValue(nbt, "out", "output"));
        return facets;
    }
}

final class CosmeticColorFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "cosmetic.color";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        return (nbt.contains("color") || nbt.contains("colour"))
                && (mod.contains("botania") || internal.contains("wand") || nbt.contains("display"));
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "color", firstNbtValue(nbt, "color", "colour"));
        putIfPresent(facets, "color1", firstNbtValue(nbt, "color1"));
        putIfPresent(facets, "color2", firstNbtValue(nbt, "color2"));
        return facets;
    }
}
