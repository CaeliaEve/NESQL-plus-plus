package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.RecipesMapExtending;

/** One native pending map transformation, on an owned inventory without world side effects. */
final class Crafting {
    private Crafting() {}

    static ItemStack pending(RecipesMapExtending recipe, ItemStack center) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return false; }
        }, 3, 3);
        for (int slot = 0; slot < 9; slot++) inventory.setInventorySlotContents(slot,
                slot == 4 ? center.copy() : new ItemStack(Items.paper));
        // matches/getMapData/onCreated can allocate IDs and modify world data. Never call them here.
        return recipe.getCraftingResult(inventory);
    }
}
