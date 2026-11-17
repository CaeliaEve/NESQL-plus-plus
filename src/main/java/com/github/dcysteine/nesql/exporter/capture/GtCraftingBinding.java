package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.item.Item;

/** Compact same-process native dependency; no recipe/result evidence retained. */
interface GtCraftingBinding {
    Item item();
    String key();
    boolean mutable();
    void verify();
}
