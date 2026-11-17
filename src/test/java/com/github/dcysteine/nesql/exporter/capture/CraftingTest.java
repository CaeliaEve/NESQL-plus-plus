package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagCompound;

/** Native map result on owned inventory; no world lookup or map-ID allocation. */
final class CraftingTest {
    static void run() {
        RecipesMapExtending map = new RecipesMapExtending();
        if (map.getRecipeOutput().stackSize != 0 || map.getRecipeOutput().getItem() != Items.map)
            throw new AssertionError("Native map placeholder changed");
        ItemStack center = new ItemStack(Items.filled_map, 17, 37);
        NBTTagCompound tags = new NBTTagCompound(); tags.setString("owner", "retained"); center.setTagCompound(tags);
        ItemStack pending = Crafting.pending(map, center);
        if (pending.getItem() != Items.filled_map || pending.getItemDamage() != 37 || pending.stackSize != 1
                || !pending.getTagCompound().getBoolean("map_is_scaling")
                || !pending.getTagCompound().getString("owner").equals("retained")
                || center.getTagCompound().hasKey("map_is_scaling") || center.stackSize != 17)
            throw new AssertionError("Pending map lost identity/data or mutated input");
        System.out.println("Crafting: native pending map and owned input preservation passed");
    }
}
