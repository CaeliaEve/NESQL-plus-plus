package com.github.dcysteine.nesql.exporter.capture;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.aspects.AspectList;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Actual installed query methods are the oracle, on separate owned stacks.
 * Catches discarding query writes, wrong float order and touching borrowed NBT. */
final class NativeInfusionToolTest {
    static void run() throws Exception {
        Object previous=field(cpw.mods.fml.common.ModAPIManager.class,cpw.mods.fml.common.ModAPIManager.INSTANCE,"apiContainers");
        set(cpw.mods.fml.common.ModAPIManager.class,cpw.mods.fml.common.ModAPIManager.INSTANCE,"apiContainers",Collections.emptyMap());
        try{tools();}finally{set(cpw.mods.fml.common.ModAPIManager.class,cpw.mods.fml.common.ModAPIManager.INSTANCE,"apiContainers",previous);}
    }
    private static void tools() throws Exception {
        require(InfusionRecipe.class.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/thaumcraft.jar!"),"Pinned Thaumcraft jar required");
        Class<?> binding=Class.forName(InfusionInheritance.class.getName()+"$ToolBinding");
        Constructor<?> create=binding.getDeclaredConstructor(Item.class);create.setAccessible(true);
        Method query=binding.getDeclaredMethod("queryOwned",ItemStack.class,NativeBatteryCapacities.class);query.setAccessible(true);
        Item spell=(Item)type("WayofTime.alchemicalWizardry.api.items.ItemSpellMultiTool").newInstance();
        Item nano=(Item)type("de.katzenpapst.amunra.item.ItemNanotool").getConstructor(String.class).newInstance("nesql-tool-query");
        Object spellSource=create.newInstance(spell),nanoSource=create.newInstance(nano);
        try(NativeBatteryCapacities capacities=new NativeBatteryCapacities()){
            parity(query,spellSource,new ItemStack(spell,2,7),capacities);
            ItemStack tagged=new ItemStack(spell,3,9);NBTTagCompound level=new NBTTagCompound();level.setInteger("harvestLvlaxe",0);
            tagged.setTagCompound(new NBTTagCompound());tagged.getTagCompound().setTag("BloodMagicTool",level);
            parity(query,spellSource,tagged,capacities);
            for(float capacity:new float[]{500,Float.POSITIVE_INFINITY,0}){
                ItemStack stack=new ItemStack(nano,2,7);stack.setTagCompound(new NBTTagCompound());
                stack.getTagCompound().setFloat("electricity",100);stack.getTagCompound().setFloat("maxEnergy",capacity);
                stack.getTagCompound().setInteger("toolMode",3);parity(query,nanoSource,stack,capacities);
            }
            Class<?> gc=type("micdoodle8.mods.galacticraft.core.items.GCItems");Object prior=field(gc,null,"battery");
            Item battery=(Item)type("micdoodle8.mods.galacticraft.core.items.ItemBattery").getConstructor(String.class).newInstance("nesql-tool-query-battery");
            Item.itemRegistry.addObject(30000,"nesql_fixture:tool_query_battery",battery);
            Field root=gc.getField("battery");root.set(null,battery);
            try{parity(query,nanoSource,new ItemStack(nano),capacities);
                // Present child compound aliases its loaded stack tag. The
                // nested capacity cache must survive in the parent's NBT.
                Item.itemRegistry.addObject(30001,"nesql_fixture:tool_query_nested",nano);
                NBTTagCompound stored=new NBTTagCompound();stored.setShort("id",(short)30001);stored.setByte("Count",(byte)1);
                stored.setTag("tag",new NBTTagCompound());
                ItemStack nested=new ItemStack(nano);nested.setTagCompound(new NBTTagCompound());
                nested.getTagCompound().setTag("batteryStack",stored);nested.getTagCompound().setFloat("electricity",1);
                nested.getTagCompound().setInteger("toolMode",1);parity(query,nanoSource,nested,capacities);
                stored.getCompoundTag("tag").setLong("maxEnergy",4611686293305294849L);
                parity(query,nanoSource,nested,capacities);
                capacities.finishVerification();
            }finally{root.set(null,prior);}
        }
        inheritanceParity(spell,nano);
        countedSamples();
    }
    private static void inheritanceParity(Item spell,Item nano)throws Exception{
        Class<?> configType=type("net.glease.tc4tweak.ConfigurationHandler"),carryType=type("net.glease.tc4tweak.modules.infusionRecipe.InfusionRecipeGetOutput");
        Object config=field(configType,null,"INSTANCE"),oldCarry=field(configType,config,"infusionRecipeNBTCarryOver"),oldTools=field(configType,config,"infusionRecipeNBTModifyArmorToolOnly"),oldKeys=field(configType,config,"infusionRecipeNBTWhitelist"),oldMerger=field(carryType,null,"MERGER");
        try{
            set(configType,config,"infusionRecipeNBTCarryOver",true);set(configType,config,"infusionRecipeNBTModifyArmorToolOnly",true);
            set(configType,config,"infusionRecipeNBTWhitelist",new ArrayList<String>());
            Object merger=invoke(type("net.glease.tc4tweak.api.InfusionExtAPI$RecipeNBTBehavior"),null,"mergeNBT",new Class<?>[]{Set.class},(Object)null);
            set(carryType,null,"MERGER",merger);
            Method nativeOutput=carryType.getMethod("getOutput",InfusionRecipe.class,ItemStack.class,ItemStack.class);
            ItemStack tagged=new ItemStack(spell,3,9);tagged.setTagCompound(new NBTTagCompound());
            NBTTagCompound level=new NBTTagCompound();level.setInteger("harvestLvlaxe",0);tagged.getTagCompound().setTag("BloodMagicTool",level);
            ItemStack charged=new ItemStack(nano,3,7);charged.setTagCompound(new NBTTagCompound());
            charged.getTagCompound().setFloat("electricity",100);charged.getTagCompound().setFloat("maxEnergy",500);charged.getTagCompound().setInteger("toolMode",3);
            ItemStack[][] cases={{new ItemStack(spell),new ItemStack(spell)},{tagged,new ItemStack(spell)},{charged,tagged}};
            for(ItemStack[] pair:cases){
                InfusionRecipe recipe=new InfusionRecipe("",pair[1],0,new AspectList(),pair[0],new ItemStack[0]);
                ItemStack expected=(ItemStack)nativeOutput.invoke(null,recipe,pair[0].copy(),pair[1].copy());
                try(InfusionInheritance source=new InfusionInheritance()){
                    InfusionInheritance.Projection observed=source.project(recipe,pair[0],pair[1]);ItemStack actual=observed.output;
                    require(actual.getItem()==expected.getItem()&&actual.stackSize==expected.stackSize&&actual.getItemDamage()==expected.getItemDamage()&&ItemStack.areItemStackTagsEqual(actual,expected),
                        "Ordered tool effects or inheritance differ from installed TC4Tweaks output");
                    source.finishVerification();
                }
            }
        }finally{
            set(carryType,null,"MERGER",oldMerger);set(configType,config,"infusionRecipeNBTCarryOver",oldCarry);
            set(configType,config,"infusionRecipeNBTModifyArmorToolOnly",oldTools);set(configType,config,"infusionRecipeNBTWhitelist",oldKeys);
        }
    }
    private static void set(Class<?> owner,Object target,String name,Object value)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    private static void countedSamples(){
        ItemStack one=new ItemStack(Items.paper,1),three=new ItemStack(Items.paper,3);
        NBTTagCompound tag=new NBTTagCompound();tag.setInteger("marker",7);one.setTagCompound(tag);three.setTagCompound((NBTTagCompound)tag.copy());
        RecipeRow row=NativeCoreFixesTest.row(one,three);Facts facts=row.facts;
        String id=com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:paper",0,com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(tag));
        Products result=Products.observe(Arrays.asList(one,three),com.github.dcysteine.nesql.exporter.source.Json.object("kind","patch","set",com.github.dcysteine.nesql.exporter.source.Json.object(),"limits",com.github.dcysteine.nesql.exporter.source.Json.object()),ItemStack::copy,facts);
        row.inputs.add(com.github.dcysteine.nesql.exporter.source.Json.object("kind","item","slot",0,"choices",com.github.dcysteine.nesql.exporter.source.Json.array(
            com.github.dcysteine.nesql.exporter.source.Json.object("id",id,"amount","1"),com.github.dcysteine.nesql.exporter.source.Json.object("id",id,"amount","3"))));
        row.outputs.add(com.github.dcysteine.nesql.exporter.source.Json.object());result.attach(row);
        com.google.gson.JsonArray samples=row.outputs.get(0).getAsJsonObject().getAsJsonObject("change").getAsJsonArray("samples");
        require(samples.get(0).getAsJsonObject().get("amount").getAsString().equals("1")&&samples.get(1).getAsJsonObject().get("amount").getAsString().equals("3"),
            "Equal item identities collapsed different offered counts");
    }
    private static void parity(Method query,Object source,ItemStack borrowed,NativeBatteryCapacities capacities)throws Exception{
        ItemStack before=borrowed.copy(),expected=borrowed.copy(),actual=borrowed.copy();
        boolean eligible=!expected.getItem().getToolClasses(expected).isEmpty();
        InfusionInheritance.ToolResult observed;
        try{observed=(InfusionInheritance.ToolResult)query.invoke(source,actual,capacities);}
        catch(InvocationTargetException failure){throw new AssertionError("Admitted stateful query failed",failure.getCause());}
        require(observed.eligible==eligible,"Tool-query predicate differs from installed native method");
        require(actual.stackSize==expected.stackSize&&actual.getItemDamage()==expected.getItemDamage()&&ItemStack.areItemStackTagsEqual(actual,expected),
                "Tool-query metadata/NBT effects differ from native");
        require(borrowed.getItemDamage()==before.getItemDamage()&&ItemStack.areItemStackTagsEqual(borrowed,before),"Query changed a borrowed stack");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
