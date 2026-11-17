package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.FurnaceRecipeHandler;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import com.google.gson.JsonArray;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** One focused native family, not a live world or renderer test. */
final class NativeFuelContainersTest {
    static final String RAIL="mods.railcraft.common.items.firestone.";
    static void run() throws Exception {
        NativeCoreFixesTest.version("Railcraft","9.16.33");
        NativeCoreFixesTest.version("AWWayofTime","1.7.52");
        Item refined=NativeCoreFixesTest.item(RAIL+"ItemFirestoneRefined","fuel_refined").setMaxDamage(5000),
                cracked=NativeCoreFixesTest.item(RAIL+"ItemFirestoneCracked","fuel_cracked").setMaxDamage(5000),
                raw=NativeCoreFixesTest.item(RAIL+"ItemFirestoneRaw","fuel_raw");
        Field refinedField=refined.getClass().getField("item"),crackedField=cracked.getClass().getField("item"),rawField=raw.getClass().getField("item");
        Object oldRefined=refinedField.get(null),oldCracked=crackedField.get(null),oldRaw=rawField.get(null);
        Random random=(Random)MagicApi.field(Class.forName("mods.railcraft.common.util.misc.MiscTools"),null,"RANDOM");
        Field seed=Random.class.getDeclaredField("seed");seed.setAccessible(true);
        java.util.concurrent.atomic.AtomicLong randomSeed=(java.util.concurrent.atomic.AtomicLong)seed.get(random);long oldSeed=randomSeed.get();
        try{
            refinedField.set(null,refined);crackedField.set(null,cracked);rawField.set(null,raw);
            List<FurnaceRecipeHandler.FuelPair> table=new ArrayList<>();
            for(Item item:new Item[]{refined,cracked})for(int damage:new int[]{0,2500,4999,5000,5001,32767}){
                ItemStack sample=new ItemStack(item,1,damage);sample.setStackDisplayName("native firestone");sample.setTagInfo("retained",new net.minecraft.nbt.NBTTagInt(7));
                table.add(new FurnaceRecipeHandler.FuelPair(sample,250));
            }
            ItemStack unbreakable=new ItemStack(refined,1,2500);unbreakable.setTagInfo("Unbreakable",new net.minecraft.nbt.NBTTagByte((byte)1));
            table.add(new FurnaceRecipeHandler.FuelPair(unbreakable,250));
            Item crystal=NativeCoreFixesTest.item("WayofTime.alchemicalWizardry.common.items.LavaCrystal","fuel_crystal");
            Field cost=Class.forName("WayofTime.alchemicalWizardry.common.items.EnergyItems").getDeclaredField("energyUsed");cost.setAccessible(true);cost.setInt(crystal,25);
            ItemStack bound=new ItemStack(crystal);bound.setTagInfo("ownerName",new net.minecraft.nbt.NBTTagString("native-owner"));
            table.add(new FurnaceRecipeHandler.FuelPair(bound,200));
            JsonObject source=object("owner","NEI","handler",FuelRecipes.HANDLER,"key","fuel");
            FuelRecipes.Snapshot captured=FuelRecipes.snapshot(source,table);
            JsonObject candidate=captured.records.get(1).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("candidates").get(0).getAsJsonObject();
            require(candidate.has("containerRule")&&candidate.get("container").isJsonNull(),"Random firestone was flattened to one fixed return");
            try(FuelRecipes reader=new FuelRecipes(source,table,()->true)){
                Facts facts=new Facts("en_US");((Set<String>)MagicApi.field(facts,"items")).addAll(captured.specimens.keySet());
                for(int row=0;row<reader.size();row++)reader.capture(row,facts);
                reader.finishVerification();
                require(randomSeed.get()==oldSeed,"Reading dynamic fuel rules consumed native RNG");
                refined.setMaxDamage(4999);
                try{reader.verify();throw new AssertionError("Fuel rule lost durability binding");}
                catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_changed"),"Wrong dynamic fuel drift code");}
                finally{refined.setMaxDamage(5000);}
                cost.setInt(crystal,26);
                try{reader.verify();throw new AssertionError("Fuel rule lost resource-cost binding");}
                catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_changed"),"Wrong resource drift code");}
                finally{cost.setInt(crystal,25);}
                reader.finishVerification();
            }
            for(int index=0;index<table.size()-1;index++){
                JsonObject rule=captured.records.get(index+1).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("containerRule");
                double threshold=Double.parseDouble(rule.get("degradationThreshold").getAsString());
                for(boolean degrade:new boolean[]{false,true}){
                    Long chosen=null;
                    for(long value=0;value<100000;value++)if((new Random(value).nextDouble()<threshold)==degrade){chosen=value;break;}
                    if(chosen==null)continue;
                    random.setSeed(chosen);ItemStack exhausted=table.get(index).stack.items[0].copy();exhausted.stackSize=0;
                    ItemStack nativeReturn=exhausted.getItem().getContainerItem(exhausted);
                    com.google.gson.JsonElement expected=rule.get(degrade?"degradedReturn":"normalReturn");
                    require(expected.isJsonNull()?nativeReturn==null:nativeReturn!=null&&nativeReturn.stackSize==1
                            &&expected.getAsJsonObject().get("item").getAsString().equals(id(nativeReturn)),"Native firestone branch differs at "+index+" degrade="+degrade);
                }
            }
            JsonObject crystalRule=captured.records.get(table.size()).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("candidates").get(0).getAsJsonObject().getAsJsonObject("containerRule");
            require(crystalRule.get("energyUsed").getAsInt()==25&&crystalRule.getAsJsonObject("returned").get("item").getAsString().equals(id(bound)),"Crystal resource rule lost owner/state/return");
            JsonArray items=array();
            for(Map.Entry<String,ItemStack> entry:captured.specimens.entrySet()){
                ItemStack stack=entry.getValue();items.add(object("id",entry.getKey(),"registry",Item.itemRegistry.getNameForObject(stack.getItem()),
                        "meta",Items.feather.getDamage(stack),"nbt",TypedNbt.encode(stack.getTagCompound()),"durability",stack.getMaxDamage()));
            }
            java.nio.file.Files.write(java.nio.file.Paths.get("fuel-dynamic-semantic-contract.json"),
                    object("records",captured.records,"items",items).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("Native fuel containers: 14 sample rules, zero capture RNG/network callbacks, seeded firestone branches, resource/state drift and transport passed");
        }finally{randomSeed.set(oldSeed);refinedField.set(null,oldRefined);crackedField.set(null,oldCracked);rawField.set(null,oldRaw);}
    }
    static String id(ItemStack stack){return Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()),Items.feather.getDamage(stack),TypedNbt.encode(stack.getTagCompound()));}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
