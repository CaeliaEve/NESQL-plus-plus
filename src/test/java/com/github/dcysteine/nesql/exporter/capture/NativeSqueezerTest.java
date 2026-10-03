package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fluids.*;
import forestry.api.recipes.ISqueezerRecipe;
import java.nio.file.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real Forestry methods are the oracle; the compiler independently replays these observations. */
final class NativeSqueezerTest {
    private static final JsonArray cases = new JsonArray();
    private static final String UTIL = "forestry.core.utils.ItemStackUtil";
    static void run() throws Exception {
        OreDictionary.registerOre("nesqlSqueezerMetal", new ItemStack(Items.iron_ingot));
        OreDictionary.registerOre("nesqlSqueezerMetal", new ItemStack(Items.gold_ingot));
        ItemStack iron = stack(Items.iron_ingot, 3), gold = stack(Items.gold_ingot, 3);
        check("direct", array(iron), array(stack(Items.iron_ingot, 5)), 1, 1, true, 2);
        check("retained-ore-only", array(iron), array(gold), 0, 1, true, 0);
        check("ore-variants-not-summed", array(iron), array(stack(Items.iron_ingot, 2), stack(Items.gold_ingot, 2)), 0, 0, false, 4);
        check("same-type-stock-condensed", array(iron), array(stack(Items.iron_ingot, 1), stack(Items.iron_ingot, 2)), 1, 1, true, 0);
        check("repeated-requirement-condensed", array(iron, iron.copy()), array(stack(Items.iron_ingot, 5)), 0, 0, false, 5);
        check("overlap-preflight-reuses-stock", array(iron, gold), array(iron.copy()), 0, 1, true, 0);
        // Exact pass reaches a later slot before ore fallback touches the earlier slot.
        check("direct-before-ore", array(iron), array(stack(Items.gold_ingot, 4), stack(Items.iron_ingot, 4)), 1, 1, true, 5);
        check("partial-direct-then-ore", array(iron), array(stack(Items.iron_ingot, 1), gold), 0, 1, true, 1);
        ItemStack tagged = iron.copy(); tagged.setTagInfo("grade", new NBTTagString("pure"));
        ItemStack otherTagged = gold.copy(); otherTagged.setTagCompound((NBTTagCompound) tagged.getTagCompound().copy());
        check("tagged-ore-is-rejected", array(tagged), array(otherTagged), 0, 0, false, 3);
        check("tagged-direct-count-independent", array(tagged), array(withCount(tagged, 4)), 1, 1, true, 1);
        check("untagged-requirement-ignores-tags", array(iron), array(tagged), 1, 1, true, 0);
        ItemStack emptyTag = iron.copy(); emptyTag.setTagCompound(new NBTTagCompound());
        check("empty-compound-requirement-ignores-tags", array(emptyTag), array(tagged), 1, 1, true, 0);
        check("null-and-empty-stock-tags-stay-separate", array(iron), array(stack(Items.iron_ingot, 1), withCount(emptyTag, 2)), 0, 0, false, 3);
        ItemStack wildcard = new ItemStack(Items.dye, 2, OreDictionary.WILDCARD_VALUE);
        check("wildcard", array(wildcard), array(new ItemStack(Items.dye, 2, 4)), 1, 1, true, 0);
        check("wildcard-types-not-summed", array(wildcard), array(new ItemStack(Items.dye, 1, 4), new ItemStack(Items.dye, 1, 5)), 0, 0, false, 2);
        check("null-requirement-hole", array(null, iron), array(iron.copy()), 1, 1, true, 0);
        check("empty-requirements", new ItemStack[0], array(iron.copy()), 0, 0, false, 3);
        check("nonpositive-requirements", array(stack(Items.paper, 0)), array(iron.copy()), 0, 0, false, 3);
        check("mixed-negative-demand",array(stack(Items.iron_ingot,-2),stack(Items.paper,1)),array(stack(Items.iron_ingot,3),stack(Items.paper,1)),1,1,true,5);
        check("mixed-zero-demand",array(stack(Items.iron_ingot,0),stack(Items.paper,1)),array(stack(Items.iron_ingot,3),stack(Items.paper,1)),1,1,true,3);
        check("float-count-rounds-up", array(stack(Items.iron_ingot, 16777217)), array(stack(Items.iron_ingot, 16777216)), 1, 1, true, 0);
        check("condensed-stock-overflow", array(iron), array(stack(Items.iron_ingot, Integer.MAX_VALUE), stack(Items.iron_ingot, 1)), 0, 0, false, 2147483648L);
        check("condensed-requirement-overflow", array(stack(Items.iron_ingot, Integer.MAX_VALUE), stack(Items.iron_ingot, 1)), array(iron.copy()), 0, 0, false, 3);
        ItemStack positiveZero=iron.copy();positiveZero.setTagInfo("number",new NBTTagFloat(0.0f));
        ItemStack negativeZero=iron.copy();negativeZero.setTagInfo("number",new NBTTagFloat(-0.0f));
        check("native-signed-zero-tags",array(positiveZero),array(negativeZero),1,1,true,0);
        ItemStack nan=iron.copy();nan.setTagInfo("number",new NBTTagFloat(Float.NaN));
        check("native-independent-nan-tags",array(nan),array(nan.copy()),0,0,false,3);
        check("container-return-is-not-stowed",array(stack(Items.water_bucket,1)),array(stack(Items.water_bucket,1)),1,1,true,0);
        // Each stock/requirement is independently owned, as in the exporter boundary.
        Paths.get("build/native-tests").toFile().mkdirs();
        Files.write(Paths.get("build/native-tests/forestry-stock-observations.json"), CanonicalJson.bytes(object("native", "Forestry 4.10.17", "cases", cases)));
        System.out.println("Native Forestry squeezer stock: " + cases.size() + " matching/allocation observations recorded");
        selection();
    }
    @SuppressWarnings("unchecked")
    private static void selection() throws Exception {
        Class<?> managerType=type("forestry.factory.recipes.SqueezerRecipeManager");
        Object manager=managerType.newInstance();
        Set<ISqueezerRecipe> recipes=(Set<ISqueezerRecipe>)field(managerType,null,"recipes");
        Map<Object,Object> containers=(Map<Object,Object>)field(managerType,null,"containerRecipes");
        if(!recipes.isEmpty()||!containers.isEmpty())throw new AssertionError("Expected isolated squeezer registry");
        ISqueezerRecipe iron=recipe(10,array(stack(Items.iron_ingot,1)),new FluidStack(FluidRegistry.WATER,100),stack(Items.paper,1),0.1f);
        ISqueezerRecipe gold=recipe(20,array(stack(Items.gold_ingot,2)),new FluidStack(FluidRegistry.LAVA,200),null,0f);
        ISqueezerRecipe overlapping=recipe(30,array(stack(Items.iron_ingot,2)),new FluidStack(FluidRegistry.LAVA,300),null,Float.NaN);
        recipes.add(iron);recipes.add(gold);recipes.add(overlapping);
        invoke(managerType,manager,"addContainerRecipe",new Class<?>[]{int.class,ItemStack.class,ItemStack.class,float.class},
            7,stack(Items.bucket,1),stack(Items.paper,2),0.05f);
        invoke(managerType,manager,"addContainerRecipe",new Class<?>[]{int.class,ItemStack.class,ItemStack.class,float.class},
            8,stack(Items.glass_bottle,1),null,0f);
        boundary(recipes,containers);
        JsonArray observed=new JsonArray();
        select(observed,"ordinary-first-match",array(stack(Items.iron_ingot,3)),null,managerType,recipes,containers);
        select(observed,"retained-overrides-earlier-rule",array(stack(Items.iron_ingot,3)),overlapping,managerType,recipes,containers);
        select(observed,"retained-accepts-ore",array(stack(Items.gold_ingot,1)),iron,managerType,recipes,containers);
        select(observed,"fresh-rejects-ore",array(stack(Items.gold_ingot,1)),null,managerType,recipes,containers);
        select(observed,"container-before-static",array(stack(Items.iron_ingot,3),stack(Items.water_bucket,3)),null,managerType,recipes,containers);
        select(observed,"retained-before-container",array(stack(Items.iron_ingot,3),stack(Items.water_bucket,3)),iron,managerType,recipes,containers);
        select(observed,"first-physical-container",array(stack(Items.lava_bucket,3),stack(Items.water_bucket,3)),null,managerType,recipes,containers);
        select(observed,"reverse-physical-container",array(stack(Items.water_bucket,3),stack(Items.lava_bucket,3)),null,managerType,recipes,containers);
        ItemStack tagged=stack(Items.water_bucket,4);tagged.setTagInfo("owner",new NBTTagString("preserved"));
        select(observed,"container-nbt-copied-to-demand",array(tagged),null,managerType,recipes,containers);
        select(observed,"empty-container-not-a-fluid-source",array(stack(Items.bucket,1),stack(Items.iron_ingot,1)),null,managerType,recipes,containers);
        select(observed,"nothing-clears-retention",array((ItemStack)null),iron,managerType,recipes,containers);
        select(observed,"null-hole-before-container",array(null,stack(Items.water_bucket,1)),null,managerType,recipes,containers);
        // The native map accepts three different key types, preserving actual iteration priority.
        Object rule=containers.values().iterator().next();
        containers.clear();containers.put(Items.bucket,rule);
        select(observed,"item-key-container",array(stack(Items.water_bucket,1)),null,managerType,recipes,containers);
        containers.clear();OreDictionary.registerOre("nesqlSqueezerEmpty",stack(Items.bucket,1));containers.put("nesqlSqueezerEmpty",rule);
        select(observed,"ore-key-container",array(stack(Items.water_bucket,1)),null,managerType,recipes,containers);
        containers.clear();ItemStack mismatched=stack(Items.bucket,1);mismatched.setTagCompound(new NBTTagCompound());containers.put(mismatched,rule);
        select(observed,"empty-map-key-tags-are-exact",array(stack(Items.water_bucket,1)),null,managerType,recipes,containers);
        recipes.clear();containers.clear();
        Files.write(Paths.get("build/native-tests/forestry-selection-observations.json"),CanonicalJson.bytes(object("native","Forestry 4.10.17 / Forge 10.13.4.1614","cases",observed)));
        System.out.println("Native Forestry squeezer selection: "+observed.size()+" registry/retention/container observations recorded");
    }
    private static void boundary(Set<ISqueezerRecipe> recipes,Map<Object,Object> containers)throws Exception{
        Class<?> type;
        try{type=Class.forName("com.github.dcysteine.nesql.exporter.capture.SqueezerRules");}
        catch(ClassNotFoundException missing){throw new AssertionError("Squeezer lacks an owned, guarded native rule snapshot",missing);}
        java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(Collection.class,Map.class);constructor.setAccessible(true);
        Item callback=new ItemFluidContainer(1000){@Override public FluidStack getFluid(ItemStack stack){throw new AssertionError("Snapshot executed a container callback");}};
        Item.itemRegistry.addObject(30100,"fixture:squeezer_callback",callback);
        ItemStack callbackStack=stack(callback,1);
        FluidContainerRegistry.registerFluidContainer(new FluidStack(FluidRegistry.WATER,1000),callbackStack,stack(Items.bucket,1));
        Object snapshot=constructor.newInstance(recipes,containers);
        JsonObject context=(JsonObject)invoke(type,snapshot,"context",new Class<?>[0]);
        if(context.getAsJsonArray("ordinary").size()!=3||context.getAsJsonArray("containers").size()!=2)throw new AssertionError("Squeezer snapshot lost native rules");
        if(!context.getAsJsonArray("dynamic").toString().contains("fixture:squeezer_callback"))throw new AssertionError("Squeezer lost a dynamic container callback");
        for(JsonElement value:context.getAsJsonArray("filled"))if(value.getAsJsonObject().getAsJsonObject("filled").get("registry").getAsString().equals("fixture:squeezer_callback"))throw new AssertionError("Squeezer flattened a callback as a fixed container");
        context.getAsJsonArray("ordinary").get(0).getAsJsonObject().addProperty("time",-99);
        if(((JsonObject)invoke(type,snapshot,"context",new Class<?>[0])).getAsJsonArray("ordinary").get(0).getAsJsonObject().get("time").getAsInt()==-99)throw new AssertionError("Squeezer context leaked mutable state");
        invoke(type,snapshot,"checkUnchanged",new Class<?>[0]);
        ISqueezerRecipe first=recipes.iterator().next();int original=first.getResources()[0].stackSize;first.getResources()[0].stackSize++;
        try{invoke(type,snapshot,"checkUnchanged",new Class<?>[0]);throw new AssertionError("Squeezer ignored registry amount mutation");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure){if(!failure.code.equals("recipe_changed"))throw failure;}
        finally{first.getResources()[0].stackSize=original;}
        try{constructor.newInstance(Arrays.asList(new Object()),containers);throw new AssertionError("Squeezer invoked an unadapted recipe class");}
        catch(java.lang.reflect.InvocationTargetException expected){if(!(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault))throw expected;}
        Files.write(Paths.get("build/native-tests/forestry-squeezer-context.json"),CanonicalJson.bytes((JsonElement)invoke(type,snapshot,"context",new Class<?>[0])));
    }
    private static void select(JsonArray observations,String name,ItemStack[] stock,ISqueezerRecipe retained,Class<?> managerType,Set<ISqueezerRecipe> recipes,Map<Object,Object> containers){
        JsonArray ordinary=new JsonArray(),rules=new JsonArray(),filled=new JsonArray(),dynamic=new JsonArray();
        for(ISqueezerRecipe value:recipes)ordinary.add(recipeJson(value));
        for(Map.Entry<Object,Object> entry:containers.entrySet()){
            Object key=entry.getKey(),rule=entry.getValue();JsonObject predicate;
            if(key instanceof ItemStack)predicate=object("kind","stack","stack",stacks(array((ItemStack)key)).get(0));
            else if(key instanceof Item)predicate=object("kind","item","registry",Item.itemRegistry.getNameForObject((Item)key));
            else {JsonArray members=stacks(OreDictionary.getOres((String)key).toArray(new ItemStack[0]));predicate=object("kind","ore","members",members);}
            rules.add(object("key",predicate,"empty",stacks(array((ItemStack)field(rule,"emptyContainer"))).get(0),"time",field(rule,"processingTime"),"remnant",stacks(array((ItemStack)field(rule,"remnants"))).get(0),
                "chance",String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits((Float)field(rule,"remnantsChance")))));
        }
        for(FluidContainerRegistry.FluidContainerData value:FluidContainerRegistry.getRegisteredFluidContainerData()){
            if(value.filledContainer.getItem() instanceof IFluidContainerItem)continue;
            filled.add(object("filled",stacks(array(value.filledContainer)).get(0),"empty",stacks(array(value.emptyContainer)).get(0),"fluid",fluidJson(value.fluid)));
        }
        for(Object item:Item.itemRegistry)if(item instanceof IFluidContainerItem)dynamic.add(new JsonPrimitive(Item.itemRegistry.getNameForObject((Item)item)));
        ItemStack[] offered=copy(stock);ISqueezerRecipe selected=null;
        boolean keep=retained!=null&&(Integer)invoke(type(UTIL),null,"containsSets",new Class<?>[]{ItemStack[].class,ItemStack[].class,boolean.class,boolean.class},retained.getResources(),offered,true,false)>0;
        if(keep)selected=retained;
        else selected=(ISqueezerRecipe)invoke(managerType,null,"findMatchingRecipe",new Class<?>[]{ItemStack[].class},(Object)offered);
        observations.add(object("name",name,"program",object("ordinary",ordinary,"containers",rules,"filled",filled,"dynamic",dynamic),"stock",stacks(stock),"retained",recipeJson(retained),"selected",recipeJson(selected)));
    }
    private static ISqueezerRecipe recipe(int time,ItemStack[] inputs,FluidStack fluid,ItemStack remnant,float chance)throws Exception{
        return (ISqueezerRecipe)type("forestry.factory.recipes.SqueezerRecipe").getConstructor(int.class,ItemStack[].class,FluidStack.class,ItemStack.class,float.class).newInstance(time,inputs,fluid,remnant,chance);
    }
    static JsonElement recipeJson(ISqueezerRecipe recipe){
        return recipe==null?JsonNull.INSTANCE:object("time",recipe.getProcessingTime(),"requirements",stacks(recipe.getResources()),"fluid",fluidJson(recipe.getFluidOutput()),
            "remnant",stacks(array(recipe.getRemnants())).get(0),"chance",String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits(recipe.getRemnantsChance())));
    }
    static JsonElement fluidJson(FluidStack fluid){return fluid==null?JsonNull.INSTANCE:object("registry",fluid.getFluid().getName(),"amount",fluid.amount,"nbt",TypedNbt.encode(fluid.tag));}
    private static void check(String name, ItemStack[] requirements, ItemStack[] stock, int direct, int ore, boolean removed, long remaining) {
        ItemStack[] wanted=copy(requirements),offered=copy(stock);
        int nativeDirect=(Integer)invoke(type(UTIL),null,"containsSets",new Class<?>[]{ItemStack[].class,ItemStack[].class,boolean.class,boolean.class},wanted,offered,false,false);
        int nativeOre=(Integer)invoke(type(UTIL),null,"containsSets",new Class<?>[]{ItemStack[].class,ItemStack[].class,boolean.class,boolean.class},wanted,offered,true,false);
        InventoryCrafting inventory=new InventoryCrafting(new Container(){@Override public boolean canInteractWith(EntityPlayer player){return true;}},3,3);
        for(int i=0;i<offered.length;i++)inventory.setInventorySlotContents(i,offered[i]);
        boolean nativeRemoved=(Boolean)invoke(type("forestry.core.utils.InventoryUtil"),null,"removeSets",
            new Class<?>[]{IInventory.class,int.class,ItemStack[].class,EntityPlayer.class,boolean.class,boolean.class,boolean.class,boolean.class},
            inventory,1,wanted,null,false,true,false,true);
        ItemStack[] after=new ItemStack[9];long total=0;
        for(int i=0;i<after.length;i++){after[i]=inventory.getStackInSlot(i);if(after[i]!=null)total+=after[i].stackSize;}
        if(nativeDirect!=direct||nativeOre!=ore||nativeRemoved!=removed||total!=remaining)throw new AssertionError(name+": native="+nativeDirect+","+nativeOre+","+nativeRemoved+","+total);
        cases.add(object("name",name,"requirements",stacks(requirements),"stock",stacks(stock),"direct",nativeDirect,"ore",nativeOre,"removed",nativeRemoved,"remaining",stacks(after)));
    }
    private static ItemStack stack(Item item,int count){return new ItemStack(item,count);}
    private static ItemStack withCount(ItemStack item,int count){ItemStack value=item.copy();value.stackSize=count;return value;}
    private static ItemStack[] array(ItemStack... values){return values;}
    private static ItemStack[] copy(ItemStack[] values){return Arrays.stream(values).map(v->v==null?null:v.copy()).toArray(ItemStack[]::new);}
    static JsonArray stacks(ItemStack[] values){
        JsonArray result=new JsonArray();
        for(ItemStack value:values){
            if(value==null){result.add(JsonNull.INSTANCE);continue;}
            JsonArray ores=new JsonArray();TreeSet<String> names=new TreeSet<>();
            for(int id:OreDictionary.getOreIDs(value))names.add(OreDictionary.getOreName(id));
            for(String name:names)ores.add(new JsonPrimitive(name));
            result.add(object("registry",Item.itemRegistry.getNameForObject(value.getItem()),"meta",Items.feather.getDamage(value),"amount",value.stackSize,"nbt",TypedNbt.encode(value.getTagCompound()),"ores",ores));
        }
        return result;
    }
}
