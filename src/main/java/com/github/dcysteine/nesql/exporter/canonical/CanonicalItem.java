package com.github.dcysteine.nesql.exporter.canonical;

import java.util.Map;

/**
 * NESQL++ canonical item contract.
 *
 * <p>This is a schema anchor for the refactor. Writers and family mappers should
 * converge on this shape instead of defining ad-hoc DTOs per export path.</p>
 */
public class CanonicalItem {
    public String itemId;
    public String modId;
    public String internalName;
    public String localizedName;
    public String unlocalizedName;
    public Integer damage;
    public Integer maxStackSize;
    public Integer maxDamage;
    public String nbtDescriptor;
    public String tooltip;
    public String searchTerms;
    public Map<String, Integer> toolClasses;
    public String renderAssetRef;
}
