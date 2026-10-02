package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import thaumcraft.api.IRunicArmor;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.config.ConfigItems;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;

/** Actual pinned TC getters on owned stacks; no world, player or crafting invocation. */
final class NativeRunicTest {
    static void run() throws Exception {
        String nativeLocation = InfusionRunicAugmentRecipe.class.getProtectionDomain().getCodeSource().getLocation().toString();
        if (!nativeLocation.contains("/native-tests/thaumcraft.jar!")) throw new AssertionError("Use the remapped installed Thaumcraft jar: " + nativeLocation);
        if (ConfigItems.itemResource == null) ConfigItems.itemResource = Items.paper;
        InfusionRunicAugmentRecipe recipe = new InfusionRunicAugmentRecipe();
        try { recipe.getAspects(); throw new AssertionError("Native parameterless failure disappeared"); }
        catch (NullPointerException expected) { }
        Item item = new DynamicArmor();
        for (int charge : new int[] {-8, -5, 0, 3, 20, 30, Integer.MAX_VALUE}) {
            ItemStack center = new ItemStack(item, 1, 7);
            NBTTagCompound nbt = new NBTTagCompound(); nbt.setInteger("charge", charge);
            nbt.setString("owner", "preserved"); center.setTagCompound(nbt);
            Runic.Sample sample = Runic.sample(recipe, center);
            if (sample.charge != charge || sample.pedestals != 1L + Math.max(0, charge)
                    || sample.instability != recipe.getInstability(center)
                    || sample.aspects.getAmount(Aspect.ENERGY) != recipe.getAspects(center).getAmount(Aspect.ENERGY))
                throw new AssertionError("Runic sample differs from native getters");
            if (charge < 100 && recipe.getComponents(center).length != sample.pedestals + 1)
                throw new AssertionError("Repeated components lost their pedestal semantics");
            if (sample.output.getItemDamage() != 7 || !sample.output.getTagCompound().getString("owner").equals("preserved")
                    || center.getTagCompound().hasKey("RS.HARDEN")) throw new AssertionError("Input was changed");
        }
        ItemStack center = new ItemStack(item);
        center.setTagCompound(new NBTTagCompound());
        center.getTagCompound().setInteger("RS.HARDEN", 383); // native getByte: 127
        if (Runic.sample(recipe, center).output.getTagCompound().getByte("RS.HARDEN") != -128)
            throw new AssertionError("Numeric coercion or signed byte overflow changed");
        net.minecraftforge.oredict.OreDictionary.registerOre("runicOther", new ItemStack(Items.gold_ingot));
        net.minecraftforge.oredict.OreDictionary.registerOre("runicDiamond", new ItemStack(Items.diamond));
        net.minecraftforge.oredict.OreDictionary.registerOre("runicDiamond", new ItemStack(Items.gold_ingot));
        Facts facts = new Facts("en_US");
        java.lang.reflect.Field ids = Facts.class.getDeclaredField("items"); ids.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Set<String> known = (java.util.Set<String>) ids.get(facts);
        known.add(com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:diamond", 0, null));
        java.util.List<MagicRecipes.Candidate> component = Runic.component(new ItemStack(Items.diamond), facts);
        if (component.size() != 1 || component.get(0).item.getItem() != Items.diamond
                || !component.get(0).rule.get("kind").getAsString().equals("infusion"))
            throw new AssertionError("A later ore group incorrectly made an ingredient usable");
        net.minecraftforge.oredict.OreDictionary.registerOre("runicDiamond", new ItemStack(Items.milk_bucket));
        known.add(com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:milk_bucket", 0, null));
        known.add(com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:bucket", 0, null));
        java.util.List<MagicRecipes.Candidate> withContainer = Runic.component(new ItemStack(Items.diamond), facts);
        RecipeRow row = new RecipeRow(facts, com.github.dcysteine.nesql.exporter.source.Json.object(), "test", 0);
        java.util.List<java.util.List<MagicRecipes.Candidate>> inputs = java.util.Arrays.asList(component, component, withContainer);
        for (java.util.List<MagicRecipes.Candidate> group : inputs) {
            com.google.gson.JsonArray choices = new com.google.gson.JsonArray();
            for (MagicRecipes.Candidate ignored : group) choices.add(com.github.dcysteine.nesql.exporter.source.Json.object("returns", new com.google.gson.JsonArray()));
            row.inputs.add(com.github.dcysteine.nesql.exporter.source.Json.object("choices", choices));
        }
        Runic.finish(row, inputs, Runic.sample(recipe, center));
        int containers = 0;
        for (com.google.gson.JsonElement value : row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices"))
            containers += value.getAsJsonObject().getAsJsonArray("returns").size();
        if (containers != 1) throw new AssertionError("Native per-pedestal container was lost");
        System.out.println("Native runic: null-overload reproduction, input-dependent charge, pedestal count, saturation and NBT copy passed");
    }
    private static final class DynamicArmor extends Item implements IRunicArmor {
        public int getRunicCharge(ItemStack stack) { return stack.hasTagCompound() ? stack.getTagCompound().getInteger("charge") : 0; }
    }
}
