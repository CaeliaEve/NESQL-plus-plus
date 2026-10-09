package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;

public final class NativeDistilleryTest {
    public static void run() throws Exception {
        Class<?> recipe = Class.forName("com.emoniph.witchery.crafting.DistilleryRecipes$DistilleryRecipe");
        Constructor<?> make = recipe.getDeclaredConstructor(ItemStack.class, ItemStack.class, int.class,
                ItemStack.class, ItemStack.class, ItemStack.class, ItemStack.class);
        make.setAccessible(true);
        ItemStack a = new ItemStack(Items.paper, 19), b = new ItemStack(Items.dye, 7, 2);
        ItemStack jar = new ItemStack(Items.glass_bottle), out = new ItemStack(Items.diamond, 3);
        Object nativeRecipe = make.newInstance(a, b, 2, out, null, new ItemStack(Items.gold_ingot, 2), null);
        Method match = recipe.getDeclaredMethod("isMatch", ItemStack.class, ItemStack.class, ItemStack.class);
        match.setAccessible(true);
        ItemStack tagged = b.copy(); tagged.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(8));
        require((Boolean)match.invoke(nativeRecipe, tagged, a, new ItemStack(Items.glass_bottle,2)), "Native swap/NBT rule changed");
        require(!(Boolean)match.invoke(nativeRecipe, a, b, jar), "Native jar minimum changed");
        Class<?> adapter;
        try { adapter=Class.forName("com.github.dcysteine.nesql.exporter.capture.DistilleryRecipes"); }
        catch(ClassNotFoundException missing) { throw new AssertionError("Missing distillery adapter",missing); }
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("alkalus.main.nei.NEI_Handler_Distillery").newInstance();
        require(Recipes.adapter(handler)!=null,"Distillery adapter is not reachable from production dispatch");
        Constructor<?> ctor=adapter.getDeclaredConstructor(TemplateRecipeHandler.class,List.class,ItemStack.class);
        ctor.setAccessible(true);
        RegistryRecipes cursor=(RegistryRecipes)ctor.newInstance(handler,Collections.singletonList(nativeRecipe),jar);
        RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.paper), new ItemStack(Items.dye,1,2),jar,out,new ItemStack(Items.gold_ingot,2));
        require(cursor.capture(0,row),"Recipe disappeared");
        require(row.inputs.size()==3 && row.outputs.size()==2,"Jars or secondary output disappeared");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),"Display stack size became consumption");
        require(row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("2"),"Jar consumption lost");
        require(row.outputs.get(1).getAsJsonObject().get("slot").getAsInt()==2,"Sparse native output slot lost");
        require(row.record.get("duration").getAsString().equals("800") && row.record.get("energy").isJsonNull(),"Altar power became EU or duration lost");
        require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("nbt").getAsBoolean(),"Native ignored NBT became exact");
        require(!row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("meta").getAsBoolean(),"Subtype became wildcard");
        require(handler.getOtherStacks(0).size()==2 && handler.getOtherStacks(0).get(1).relx==105 && handler.getOtherStacks(0).get(1).rely==23,"Native sparse view layout lost");
        require(row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("returns").size()==0,"Machine jars acquired crafting returns");
        require(a.stackSize==19 && b.stackSize==7 && jar.stackSize==1,"Capture mutated native registry");
        require(DistilleryRecipes.progressBars().length==2,"Native altar power animation lost");
        Object shadow=make.newInstance(b,a,3,new ItemStack(Items.emerald),null,null,null);
        try { ctor.newInstance(handler,Arrays.asList(nativeRecipe,shadow),jar); throw new AssertionError("Overlapping first-match registry accepted"); }
        catch(InvocationTargetException expected) { require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Wrong overlap failure"); }
        boolean subtypes=Items.paper.getHasSubtypes();
        try {
            Items.paper.setHasSubtypes(!subtypes);
            try { cursor.verify();throw new AssertionError("Metadata predicate drift accepted"); }
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong predicate drift failure");}
        } finally { Items.paper.setHasSubtypes(subtypes); }
        out.stackSize=4;
        try { cursor.verify(); throw new AssertionError("Registry drift accepted"); }
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) { require(expected.code.equals("environment_changed"),"Wrong drift failure"); }
        System.out.println("Native distillery: swapped inputs, NBT, unit consumption, jars, sparse outputs, work duration and drift passed");
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
