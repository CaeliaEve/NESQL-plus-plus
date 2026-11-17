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
        try(RunicChargeSource charges=new RunicChargeSource()){
        for (String name : new String[]{"ItemCultistBoots", "ItemCultistLeaderArmor", "ItemCultistPlateArmor", "ItemCultistRobeArmor"}) {
            Item item = (Item) MagicApi.type("thaumcraft.common.items.armor." + name)
                    .getConstructor(net.minecraft.item.ItemArmor.ArmorMaterial.class, int.class, int.class)
                    .newInstance(net.minecraft.item.ItemArmor.ArmorMaterial.IRON, 0, 0);
            ItemStack stack = new ItemStack(item); stack.setTagCompound(new NBTTagCompound());
            stack.getTagCompound().setByte("RS.HARDEN", (byte) -3);
            if (charges.finalCharge(stack) != ((IRunicArmor) item).getRunicCharge(stack) - 3)
                throw new AssertionError("Native cultist runic provider differs: " + name);
        }
        for (int charge : new int[] {-8, -5, 0, 3, 20, 30, Integer.MAX_VALUE}) {
            Item item = new thaumcraft.api.ItemRunic(charge);
            ItemStack center = new ItemStack(item, 1, 7);
            NBTTagCompound nbt = new NBTTagCompound(); nbt.setInteger("charge", charge);
            nbt.setString("owner", "preserved"); center.setTagCompound(nbt);
            Runic.Sample sample = Runic.sample(recipe, center,charges);
            if (sample.charge != charge || sample.pedestals != 1L + Math.max(0, charge)
                    || sample.instability != recipe.getInstability(center)
                    || sample.aspects.getAmount(Aspect.ENERGY) != recipe.getAspects(center).getAmount(Aspect.ENERGY))
                throw new AssertionError("Runic sample differs from native getters");
            if (charge < 100 && recipe.getComponents(center).length != sample.pedestals + 1)
                throw new AssertionError("Repeated components lost their pedestal semantics");
            if (sample.output.getItemDamage() != 7 || !sample.output.getTagCompound().getString("owner").equals("preserved")
                    || center.getTagCompound().hasKey("RS.HARDEN")) throw new AssertionError("Input was changed");
        }
        DynamicArmor unknown = new DynamicArmor();
        try (RunicChargeSource source = new RunicChargeSource()) {
            try { source.finalCharge(new ItemStack(unknown)); throw new AssertionError("Unknown provider was admitted"); }
            catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
                if (!expected.code.equals("recipe_unsupported") || unknown.calls != 0) throw new AssertionError("Unknown charge callback was executed");
            }
        }
        thaumcraft.api.ItemRunic bound = new thaumcraft.api.ItemRunic(2);
        try (RunicChargeSource source = new RunicChargeSource()) {
            source.finalCharge(new ItemStack(bound));
            java.lang.reflect.Field charge = thaumcraft.api.ItemRunic.class.getDeclaredField("charge");
            charge.setAccessible(true); charge.setInt(bound, 3);
            try { source.finishVerification(); throw new AssertionError("Mutable provider field escaped sealing"); }
            catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
                if (!expected.code.equals("environment_changed")) throw expected;
            }
        }
        ItemStack center = new ItemStack(new thaumcraft.api.ItemRunic(0));
        center.setTagCompound(new NBTTagCompound());
        center.getTagCompound().setInteger("RS.HARDEN", 383); // native getByte: 127
        if (Runic.sample(recipe, center,charges).output.getTagCompound().getByte("RS.HARDEN") != -128)
            throw new AssertionError("Numeric coercion or signed byte overflow changed");
        net.minecraftforge.oredict.OreDictionary.registerOre("runicOther", new ItemStack(Items.gold_ingot));
        net.minecraftforge.oredict.OreDictionary.registerOre("runicDiamond", new ItemStack(Items.diamond));
        net.minecraftforge.oredict.OreDictionary.registerOre("runicDiamond", new ItemStack(Items.gold_ingot));
        Facts facts = new Facts("en_US");
        java.lang.reflect.Field ids = Facts.class.getDeclaredField("items"); ids.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Set<String> known = (java.util.Set<String>) ids.get(facts);
        known.add(com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:diamond", 0, null));
        Class<?> mode=MagicApi.type("net.glease.tc4tweak.modules.infusionRecipe.InfusionOreDictMode");
        java.lang.reflect.Field configured=MagicApi.type("net.glease.tc4tweak.network.NetworkedConfiguration").getDeclaredField("infusionOreDictMode");
        configured.setAccessible(true);
        @SuppressWarnings({"rawtypes","unchecked"}) Object defaultMode=Enum.valueOf((Class)mode,"Default");
        configured.set(null,defaultMode);
        java.util.List<MagicRecipes.Candidate> component = components();
        if (component.size() != 1 || component.get(0).item.getItem() != Items.diamond
                || !component.get(0).rule.get("kind").getAsString().equals("exact"))
            throw new AssertionError("A later ore group incorrectly made an ingredient usable");
        net.minecraftforge.oredict.OreDictionary.registerOre("runicDiamond", new ItemStack(Items.milk_bucket));
        known.add(com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:milk_bucket", 0, null));
        known.add(com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:bucket", 0, null));
        java.util.List<MagicRecipes.Candidate> withContainer = components();
        RecipeRow row = new RecipeRow(facts, com.github.dcysteine.nesql.exporter.source.Json.object(), "test", 0);
        java.util.List<java.util.List<MagicRecipes.Candidate>> inputs = java.util.Arrays.asList(component, component, withContainer);
        for (java.util.List<MagicRecipes.Candidate> group : inputs) {
            com.google.gson.JsonArray choices = new com.google.gson.JsonArray();
            for (MagicRecipes.Candidate ignored : group) choices.add(com.github.dcysteine.nesql.exporter.source.Json.object("returns", new com.google.gson.JsonArray()));
            row.inputs.add(com.github.dcysteine.nesql.exporter.source.Json.object("choices", choices));
        }
        java.util.List<Runic.ContainerReturn> returned=new java.util.ArrayList<>();
        Runic.finish(row,inputs,Runic.sample(recipe,center,charges),1,returned);
        int containers = 0;
        for (com.google.gson.JsonElement value : row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices"))
            containers += value.getAsJsonObject().getAsJsonArray("returns").size();
        if (containers != 1) throw new AssertionError("Native per-pedestal container was lost");
        for(Runic.ContainerReturn observation:returned)observation.verify();
        charges.finishVerification();
        }
        System.out.println("Native runic: source-bound charge, callback rejection, pedestal count, saturation and NBT copy passed");
    }
    private static java.util.List<MagicRecipes.Candidate> components(){
        try(NativeItemExamples examples=new NativeItemExamples();InfusionLegacyInputs.Context context=new InfusionLegacyInputs.Context();
            InfusionLegacyInputs inputs=InfusionLegacyInputs.runic(examples,context)){
            java.util.List<MagicRecipes.Candidate> result=new java.util.ArrayList<>();
            for(ItemStack stack:inputs.componentCopies().get(0))result.add(new MagicRecipes.Candidate(stack,com.github.dcysteine.nesql.exporter.source.Json.object("kind","exact")));
            return result;
        }
    }
    private static final class DynamicArmor extends Item implements IRunicArmor {
        int calls;
        public int getRunicCharge(ItemStack stack) { calls++; throw new AssertionError("Unknown runic provider callback"); }
    }
}
