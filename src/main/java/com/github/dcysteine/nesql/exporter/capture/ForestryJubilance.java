package com.github.dcysteine.nesql.exporter.capture;

import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.apiculture.IAlleleBeeSpeciesCustom;
import forestry.api.apiculture.IJubilanceProvider;

/** Native specialty requirements are descriptions, never simulated housing state. */
final class ForestryJubilance {
    private ForestryJubilance() {}

    static String capture(IAlleleBeeSpecies species, Facts facts) {
        if (!(species instanceof IAlleleBeeSpeciesCustom)) return null;
        IJubilanceProvider provider = ((IAlleleBeeSpeciesCustom) species).getJubilanceProvider();
        if (provider == null) return null;
        String description = provider.getDescription();
        return description == null ? null : facts.text(description);
    }
}
