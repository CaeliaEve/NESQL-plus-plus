package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;

/** Focused boundary checks for the native ore-group registry budget. */
public final class OreGroupsBudgetTest {
    private OreGroupsBudgetTest() {}

    public static void main(String[] args) {
        run();
        System.out.println("Ore group budget: 262144-entry boundary passed");
    }

    static void run() {
        OreGroups.validateCount(0);
        OreGroups.validateCount(65536);
        OreGroups.validateCount(65537);
        OreGroups.validateCount(OreGroups.MAX_GROUPS);
        try {
            OreGroups.validateCount(OreGroups.MAX_GROUPS + 1);
            throw new AssertionError("Ore group budget accepted an oversized registry");
        } catch (Jobs.Fault failure) {
            if (!failure.code.equals("ore_groups")) throw new AssertionError("Wrong ore group budget fault", failure);
        }
    }
}
