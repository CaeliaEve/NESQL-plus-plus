package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.Map;

final class GregTechToolFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "tool.gregtech";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String internal = lower(item.getInternalName());
        return internal.contains("metatool")
                || nbt.contains("gt.toolstats")
                || (nbt.contains("primarymaterial") && nbt.contains("secondarymaterial"))
                || (nbt.contains("uid0") && nbt.contains("uid1") && nbt.contains("slot") && nbt.contains("gt."));
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "primaryMaterial", firstNbtValue(nbt, "PrimaryMaterial", "primaryMaterial"));
        putIfPresent(facets, "secondaryMaterial", firstNbtValue(nbt, "SecondaryMaterial", "secondaryMaterial"));
        putIfPresent(facets, "voltage", firstNbtValue(nbt, "Voltage", "voltage"));
        putIfPresent(facets, "charge", firstNbtValue(nbt, "Charge", "Energy", "Electric", "GT.ItemCharge"));
        putIfPresent(facets, "fluid", firstNbtValue(nbt, "mFluid", "FluidName", "fluidName"));
        putIfPresent(facets, "fluidAmount", firstNbtValue(nbt, "mFluidAmount", "Amount", "amount"));
        putIfPresent(facets, "capacity", firstNbtValue(nbt, "mCapacity", "Capacity", "capacity"));
        putIfPresent(facets, "meta", firstNbtValue(nbt, "mMeta", "meta"));
        putIfPresent(facets, "toolDamage", String.valueOf(item.getItemDamage()));
        return facets;
    }
}

final class GregTechChargeFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "charge.gregtech";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        return isChargedStateVariant(nbt)
                && (mod.contains("gregtech") || internal.contains("gt.") || internal.contains("meta"));
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        return new GregTechToolFamily().facets(item, nbt);
    }

    static boolean isChargedStateVariant(ParsedNbt nbt) {
        return (nbt.contains("electric") || nbt.contains("energy"))
                && (nbt.contains("maxcharge") || nbt.contains("maxdamage") || nbt.contains("voltage"));
    }
}

final class GenericChargeFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "charge.generic";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        return GregTechChargeFamily.isChargedStateVariant(nbt);
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "charge", firstNbtValue(nbt, "Charge", "Energy", "Electric"));
        putIfPresent(facets, "voltage", firstNbtValue(nbt, "Voltage", "voltage"));
        return facets;
    }
}

final class TConstructToolFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "tool.tconstruct";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        return (mod.contains("tconstruct") || mod.contains("tinkers"))
                && (nbt.contains("infitool") || nbt.contains("renderhead") || nbt.contains("head"));
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "head", firstNbtValue(nbt, "RenderHead", "Head"));
        putIfPresent(facets, "handle", firstNbtValue(nbt, "RenderHandle", "Handle"));
        putIfPresent(facets, "accessory", firstNbtValue(nbt, "RenderAccessory", "Accessory"));
        putIfPresent(facets, "durability", firstNbtValue(nbt, "TotalDurability", "Durability"));
        putIfPresent(facets, "loaded", firstNbtValue(nbt, "Loaded"));
        putIfPresent(facets, "toolType", item.getInternalName());
        return facets;
    }
}

final class TConstructPartFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "toolpart.tconstruct";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        if (!(mod.contains("tconstruct") || mod.contains("tinkers"))) {
            return false;
        }
        return internal.endsWith("part")
                || internal.contains("part")
                || nbt.contains("dualmat")
                || nbt.contains("material2")
                || nbt.contains("renderhandle")
                || nbt.contains("renderaccessory");
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "partType", item.getInternalName());
        putIfPresent(facets, "material", String.valueOf(item.getItemDamage()));
        putIfPresent(facets, "material2", firstNbtValue(nbt, "Material2", "material2"));
        return facets;
    }
}

final class TGregworksPartFamily extends AbstractSemanticFamily {
    @Override
    public String familyId() {
        return "toolpart.tgregworks";
    }

    @Override
    public boolean matches(Item item, ParsedNbt nbt) {
        String mod = lower(item.getModId());
        String internal = lower(item.getInternalName());
        if (!(mod.contains("tgregworks") || internal.contains("tgregtoolpart"))) {
            return false;
        }
        return internal.contains("toolpart") || nbt.contains("material");
    }

    @Override
    public Map<String, String> facets(Item item, ParsedNbt nbt) {
        Map<String, String> facets = newFacets();
        putIfPresent(facets, "partType", item.getInternalName());
        putIfPresent(facets, "material", firstNbtValue(nbt, "material"));
        return facets;
    }
}
