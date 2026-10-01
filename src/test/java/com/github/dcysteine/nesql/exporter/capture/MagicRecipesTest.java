package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.ShapedArcaneRecipe;

public final class MagicRecipesTest {
    private MagicRecipesTest() {}

    public static void run() {
        ItemStack output = new ItemStack(new Item(), 3, 7);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("owner", "native");
        output.setTagCompound(tag);
        ShapedArcaneRecipe projected;
        try {
            projected = MagicRecipes.shapedProjection("RESEARCH", output, new AspectList());
        } catch (ArrayIndexOutOfBoundsException error) {
            throw new AssertionError("Shaped arcane projection must initialize without reading past native constructor arguments", error);
        }
        if (projected.width != 1 || projected.height != 1 || projected.input.length != 1 || projected.input[0] != null
                || !"RESEARCH".equals(projected.getResearch()) || projected.output.stackSize != 3
                || projected.output.getItemDamage() != 7 || projected.output.getItem() != output.getItem()) {
            throw new AssertionError("Projection initializer changed grid, research or output facts");
        }
        projected.output.getTagCompound().setString("owner", "changed");
        if (!"native".equals(output.getTagCompound().getString("owner"))) {
            throw new AssertionError("Projection mutated source output NBT");
        }
    }
}
