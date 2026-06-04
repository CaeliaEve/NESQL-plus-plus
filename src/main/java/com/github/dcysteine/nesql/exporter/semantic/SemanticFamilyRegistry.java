package com.github.dcysteine.nesql.exporter.semantic;

import com.github.dcysteine.nesql.sql.base.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered registry for high-confidence GTNH semantic family plugins.
 */
public final class SemanticFamilyRegistry {
    private static final List<SemanticFamily> FAMILIES = createFamilies();

    private SemanticFamilyRegistry() {}

    public static SemanticFamily match(Item item, ParsedNbt nbt) {
        for (SemanticFamily family : FAMILIES) {
            if (family.matches(item, nbt)) {
                return family;
            }
        }
        return null;
    }

    public static SemanticFamily byId(String familyId) {
        for (SemanticFamily family : FAMILIES) {
            if (family.familyId().equals(familyId)) {
                return family;
            }
        }
        return null;
    }

    public static List<SemanticFamily> families() {
        return FAMILIES;
    }

    private static List<SemanticFamily> createFamilies() {
        ArrayList<SemanticFamily> families = new ArrayList<SemanticFamily>();
        families.add(new BuildCraftFacadeFamily());
        families.add(new Ae2FacadeFamily());
        families.add(new EnderIoPaintedFacadeFamily());
        families.add(new ThaumcraftWandFamily());
        families.add(new ForestryGeneticsFamily());
        families.add(new BinnieGendustryGeneticsFamily());
        families.add(new GregTechToolFamily());
        families.add(new TConstructToolFamily());
        families.add(new TConstructPartFamily());
        families.add(new TGregworksPartFamily());
        families.add(new Ic2CropSeedFamily());
        families.add(new FluidContainerFamily());
        families.add(new GregTechChargeFamily());
        families.add(new GenericChargeFamily());
        families.add(new EnderIoEntityCaptureFamily());
        families.add(new GenericEntityCaptureFamily());
        families.add(new CosmeticColorFamily());
        families.add(new EncodedPatternFamily());
        return Collections.unmodifiableList(families);
    }
}
