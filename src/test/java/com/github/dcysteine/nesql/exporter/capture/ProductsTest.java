package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Exercises the actual item/NBT copy boundary inside the existing source behavior entry point. */
public final class ProductsTest {
    private ProductsTest() {}
    public static void run() {
        ItemStack input = new ItemStack(new Item(), 2, 7);
        NBTTagCompound data = new NBTTagCompound(); data.setLong("energy", Long.MAX_VALUE);
        NBTTagCompound old = new NBTTagCompound(); old.setString("discarded", "old value"); data.setTag("upgrade", old);
        input.setTagCompound(data);
        NBTTagCompound replacement = new NBTTagCompound(); replacement.setInteger("level", 2);
        NBTTagCompound set = new NBTTagCompound(); set.setTag("upgrade", replacement);
        ItemStack result = Products.patch(input, set, java.util.Collections.emptyMap());
        if (result.getItem() != input.getItem() || result.getItemDamage() != 7 || result.stackSize != 2
                || result.getTagCompound().getLong("energy") != Long.MAX_VALUE
                || result.getTagCompound().getCompoundTag("upgrade").hasKey("discarded")
                || result.getTagCompound().getCompoundTag("upgrade").getInteger("level") != 2) throw new AssertionError("Tag replacement lost input data or merged the replaced value");
        result.getTagCompound().getCompoundTag("upgrade").setInteger("level", 9);
        if (replacement.getInteger("level") != 2 || !input.getTagCompound().getCompoundTag("upgrade").hasKey("discarded")) {
            throw new AssertionError("Tag replacement mutated a recipe-owned object");
        }
        input.getTagCompound().setDouble("fire", -3.75);
        input.getTagCompound().setLong("air", 4294967301L);
        java.util.Map<String, Integer> limits = new java.util.TreeMap<>();
        limits.put("fire", 400); limits.put("air", 400); limits.put("water", 400);
        ItemStack capped = Products.patch(input, set, limits);
        if (capped.getTagCompound().getInteger("fire") != -4 || capped.getTagCompound().getInteger("air") != 5
                || capped.getTagCompound().getInteger("water") != 0 || !capped.getTagCompound().hasKey("water", 3)
                || input.getTagCompound().hasKey("water") || input.getTagCompound().getTag("air").getId() != 4) {
            throw new AssertionError("Integer limits changed native NBT conversion or mutated the input");
        }
    }
}
