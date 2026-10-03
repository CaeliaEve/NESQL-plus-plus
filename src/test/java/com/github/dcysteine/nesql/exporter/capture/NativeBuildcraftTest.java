package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual FlexibleRecipe allocation and matching, no world tick or live inventory. */
final class NativeBuildcraftTest {
    private static final String API="buildcraft.api.recipes.";
    static void run() throws Exception {
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("buildcraft.compat.nei.RecipeHandlerAssemblyTable").newInstance();
        require(Recipes.adapter(handler)!=null,"BuildCraft assembly lacks a native registry adapter");
        ItemStack paper=new ItemStack(Items.paper,3),iron=new ItemStack(Items.iron_ingot,99),output=new ItemStack(Items.diamond,2);
        Object recipe=recipe(output,700,new Object[]{new ArrayList<>(Arrays.asList(paper,iron)),new ArrayList<>(Collections.singletonList(new ItemStack(Items.paper)))});
        List<ItemStack> inventory=new ArrayList<>(Arrays.asList(new ItemStack(Items.paper,2),new ItemStack(Items.iron_ingot,2),new ItemStack(Items.paper)));
        Object crafter=crafter(inventory);
        Method craft=recipe.getClass().getMethod("craft",Class.forName(API+"IFlexibleCrafter"),boolean.class);
        Object preview=craft.invoke(recipe,crafter,true);
        require(preview!=null && inventory.get(0).stackSize==2 && inventory.get(1).stackSize==2,"Native preview changed input stock");
        require(craft.invoke(recipe,crafter,false)!=null && inventory.get(0)==null && inventory.get(1).stackSize==1 && inventory.get(2)==null,
                "Native mixed-candidate greedy allocation differs");
        RegistryRecipes adapter=adapter(handler,Arrays.asList(recipe));
        RecipeRow row=row(paper,iron,output);adapter.capture(0,row);
        require(row.inputs.size()==2 && row.outputs.size()==1 && row.record.get("duration").isJsonNull() && row.record.get("energy").isJsonNull(),
                "Assembly gained a fixed time/EU or lost native requirements");
        require(row.record.getAsJsonObject("process").get("kind").getAsString().equals("buildcraftAssembly")
                && row.record.getAsJsonObject("process").get("energy").getAsInt()==700,"Assembly laser budget lost");
        for(com.google.gson.JsonElement value:row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")){
            JsonObject c=value.getAsJsonObject();require(c.get("amount").getAsInt()==3 && c.getAsJsonObject("consume").get("kind").getAsString().equals("allocated")
                    && c.getAsJsonArray("returns").size()==0,"Alternative example counts replaced first native amount or container returns appeared");
        }
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size()==2 && row.outputs.get(0).getAsJsonObject().get("amount").getAsInt()==2,"Assembly facts collapsed");
        handler.arecipes.get(0).getIngredients().get(0).items[0].stackSize=123;
        handler.arecipes.get(0).getResult().items[0].stackSize=123;
        RecipeRow repeat=row(paper,iron,output);adapter.capture(0,repeat);
        require(row.inputs.equals(repeat.inputs) && row.outputs.equals(repeat.outputs) && paper.stackSize==3 && iron.stackSize==99 && output.stackSize==2,"Owned assembly layout changed registry");
        // Native greedy order can fail even though a backtracking allocator could satisfy both.
        Object greedy=recipe(output,0,new Object[]{Arrays.asList(new ItemStack(Items.paper),new ItemStack(Items.iron_ingot)),new ItemStack(Items.paper)});
        require(craft.invoke(greedy,crafter(new ArrayList<>(Arrays.asList(new ItemStack(Items.paper),new ItemStack(Items.iron_ingot)))),true)!=null,
                "Fixed inputs must be allocated before alternative groups regardless of constructor order");
        Object overlapping=recipe(output,0,new Object[]{Arrays.asList(new ItemStack(Items.paper),new ItemStack(Items.iron_ingot)),Collections.singletonList(new ItemStack(Items.paper))});
        require(craft.invoke(overlapping,crafter(new ArrayList<>(Arrays.asList(new ItemStack(Items.paper),new ItemStack(Items.iron_ingot)))),true)==null,
                "Native assembly changed from greedy to backtracking");
        matching(handler,output);
        ItemStack ignoredCount=new ItemStack(Items.iron_ingot,0);
        Object zeroExample=recipe(output,1,new Object[]{Arrays.asList(new ItemStack(Items.paper,2),ignoredCount)});
        require(craft.invoke(zeroExample,crafter(new ArrayList<>(Collections.singletonList(new ItemStack(Items.iron_ingot,2)))),true)!=null,
                "Native alternatives unexpectedly use later example counts");
        RecipeRow ignored=row(paper,ignoredCount,output);adapter(handler,Collections.singletonList(zeroExample)).capture(0,ignored);
        require(ignored.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(1).getAsJsonObject().get("amount").getAsInt()==2,
                "Later zero-count example was rejected or emitted as a zero amount");
        Object free=recipe(output,0,new Object[0]);
        require(craft.invoke(free,crafter(new ArrayList<>()),true)!=null,"Native no-input plan stopped working");
        RecipeRow freeRow=row(output);adapter(handler,Collections.singletonList(free)).capture(0,freeRow);
        require(freeRow.inputs.size()==0 && freeRow.outputs.size()==1,"No-input assembly plan was lost");
        Object[] many=new Object[13];Arrays.fill(many,new ItemStack(Items.paper));
        RecipeRow overflow=row(paper,output);adapter(handler,Collections.singletonList(recipe(output,1,many))).capture(0,overflow);
        require(overflow.inputs.size()==13 && overflow.elements.size()==13 && handler.arecipes.get(0).getIngredients().size()==12,
                "Native twelve-slot projection dropped a requirement or invented an extra display slot");
        Object fluid=recipe(output,1,new Object[]{new FluidStack(FluidRegistry.WATER,1)});
        RegistryRecipes impossible=adapter(handler,Collections.singletonList(fluid));
        require(!impossible.capture(0,row(output)) && impossible.exclusion(0).get("reason").getAsString().equals("native_machine_has_no_fluid_slots"),
                "Assembly with no fluid inventory emitted a fluid recipe");
        Object unknown=Proxy.newProxyInstance(handler.getClass().getClassLoader(),new Class<?>[]{Class.forName(API+"IFlexibleRecipe"),Class.forName(API+"IFlexibleRecipeViewable")},
                (p,m,a)->{throw new AssertionError("Unknown recipe callback executed");});
        try{adapter(handler,Collections.singletonList(unknown)).capture(0,row(output));throw new AssertionError("Unknown assembly subclass accepted");}
        catch(Jobs.Fault expected){require(expected.code.equals("recipe_unsupported"),"Wrong unknown assembly failure");}
        System.out.println("Native BuildCraft assembly: mixed allocation, fixed-before-alternative order, greedy rejection, native wildcard/NBT, owned layout and no-fluid proof passed");
    }
    private static void matching(TemplateRecipeHandler handler,ItemStack output)throws Exception {
        Method nativeMatch=Class.forName("buildcraft.core.lib.inventory.StackHelper").getMethod("isMatchingItem",ItemStack.class,ItemStack.class);
        ItemStack bare=new ItemStack(Items.paper),empty=bare.copy();empty.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        require(!(Boolean)nativeMatch.invoke(null,bare,empty),"Native missing and empty NBT merged");
        bare.setTagInfo("x",new net.minecraft.nbt.NBTTagFloat(-0.0F));empty.setTagInfo("x",new net.minecraft.nbt.NBTTagFloat(0.0F));
        require((Boolean)nativeMatch.invoke(null,bare,empty),"Native signed zero stopped comparing numerically");
        net.minecraft.nbt.NBTTagList typed=new net.minecraft.nbt.NBTTagList();typed.appendTag(new net.minecraft.nbt.NBTTagString("x"));typed.removeTag(0);
        bare.setTagInfo("list",typed);empty.setTagInfo("list",new net.minecraft.nbt.NBTTagList());
        require(!(Boolean)nativeMatch.invoke(null,bare,empty),"Native empty list element types unexpectedly merged");
        bare.getTagCompound().removeTag("list");empty.getTagCompound().removeTag("list");
        bare.setTagInfo("x",new net.minecraft.nbt.NBTTagFloat(Float.NaN));empty.setTagInfo("x",new net.minecraft.nbt.NBTTagFloat(Float.NaN));
        require(!(Boolean)nativeMatch.invoke(null,bare,empty),"Native NaN unexpectedly compares by value");
        try {adapter(handler,Collections.singletonList(recipe(output,1,new Object[]{bare}))).capture(0,row(bare,output));throw new AssertionError("Identity-dependent NaN input accepted");}
        catch(Jobs.Fault expected){require(expected.code.equals("recipe_unsupported"),"Wrong NaN rejection");}
        for(Item item:new Item[]{Items.paper,Items.coal}) for(int meta:new int[]{0,1,32767}){
            ItemStack anchor=new ItemStack(item,1,meta);anchor.setTagInfo("owner",new net.minecraft.nbt.NBTTagInt(1));
            ItemStack normal=anchor.copy();normal.setItemDamage(0);
            ItemStack subtype=anchor.copy();subtype.setItemDamage(1);
            RecipeRow row=row(anchor,normal,subtype,output);adapter(handler,Collections.singletonList(recipe(output,1,new Object[]{anchor}))).capture(0,row);
            JsonObject rule=row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule");
            require(rule.get("kind").getAsString().equals("buildcraft") && rule.get("subtypes").getAsBoolean()==anchor.getHasSubtypes(),"BuildCraft subtype predicate lost");
            require(rule.get("wildcard").getAsBoolean()==(meta==32767),"BuildCraft wildcard shortcut lost");
            for(int damage:new int[]{0,1,32767})for(int tag:new int[]{0,1}){
                ItemStack offered=new ItemStack(item,1,damage);if(tag==1)offered.setTagCompound((net.minecraft.nbt.NBTTagCompound)anchor.getTagCompound().copy());
                boolean expected=meta==32767||damage==32767||(!anchor.getHasSubtypes()||meta==damage)&&tag==1;
                require((Boolean)nativeMatch.invoke(null,anchor,offered)==expected,"Pinned native matcher differs from explicit predicate");
            }
        }
    }
    private static Object recipe(ItemStack output,int energy,Object[] inputs)throws Exception{return Class.forName("buildcraft.core.recipes.FlexibleRecipe").getConstructor(String.class,Object.class,int.class,long.class,Object[].class).newInstance("fixture",output,energy,999L,inputs);}
    private static Object crafter(List<ItemStack> stock)throws Exception{
        Class<?> api=Class.forName(API+"IFlexibleCrafter");return Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(p,m,a)->{
            switch(m.getName()){
                case "getCraftingItemStackSize":return stock.size();
                case "getCraftingFluidStackSize":return 0;
                case "getCraftingItemStack":return stock.get((Integer)a[0]);
                case "decrCraftingItemStack":{int i=(Integer)a[0],n=(Integer)a[1];ItemStack s=stock.get(i);ItemStack take=s.copy();take.stackSize=Math.min(n,s.stackSize);s.stackSize-=take.stackSize;if(s.stackSize==0)stock.set(i,null);return take;}
                default:throw new AssertionError("Unexpected crafter call "+m);
            }
        });
    }
    private static RegistryRecipes adapter(TemplateRecipeHandler handler,List<?> recipes)throws Exception{
        Constructor<?> c=Class.forName("com.github.dcysteine.nesql.exporter.capture.BuildcraftRecipes").getDeclaredConstructor(TemplateRecipeHandler.class,List.class);c.setAccessible(true);
        try{return (RegistryRecipes)c.newInstance(handler,recipes);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    @SuppressWarnings("unchecked") private static RecipeRow row(ItemStack... stacks){Facts facts=new Facts("en_US");Set<String> known=(Set<String>)MagicApi.field(facts,"items");for(ItemStack s:stacks)known.add(Identity.item(Item.itemRegistry.getNameForObject(s.getItem()),s.getItemDamage(),TypedNbt.encode(s.getTagCompound())));return new RecipeRow(facts,object("owner","BuildCraft|Silicon","handler","fixture","key","assembly"),"category_test",0);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
