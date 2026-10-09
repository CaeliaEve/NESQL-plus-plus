package com.github.dcysteine.nesql.exporter.capture;
import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;

public final class NativeWitcherySpinningTest {
    public static void run() throws Exception {
        Class<?> type=Class.forName("com.emoniph.witchery.crafting.SpinningRecipes$SpinningRecipe");
        Constructor<?> make=type.getDeclaredConstructor(ItemStack.class,ItemStack.class,ItemStack[].class);make.setAccessible(true);
        ItemStack fibre=new ItemStack(Items.string,3),modifier=new ItemStack(Items.dye,9,2),out=new ItemStack(Items.paper,2);
        Object recipe=make.newInstance(out,fibre,new ItemStack[]{modifier});
        Method match=type.getDeclaredMethod("isMatch",ItemStack.class,ItemStack[].class);match.setAccessible(true);
        ItemStack tagged=modifier.copy();tagged.stackSize=1;tagged.setTagInfo("ignored",new net.minecraft.nbt.NBTTagInt(1));
        require((Boolean)match.invoke(recipe,fibre,new ItemStack[]{new ItemStack(Items.diamond),tagged,null}),"Native extra modifier/NBT/count behavior changed");
        require(!(Boolean)match.invoke(recipe,new ItemStack(Items.string,2),new ItemStack[]{tagged}),"Native fibre minimum lost");
        Class<?> adapter;
        try{adapter=Class.forName("com.github.dcysteine.nesql.exporter.capture.WitcherySpinningRecipes");}
        catch(ClassNotFoundException e){throw new AssertionError("Missing Witchery spinning adapter",e);}
        TemplateRecipeHandler h=(TemplateRecipeHandler)Class.forName("alkalus.main.nei.NEI_Handler_SpinningWheel").newInstance();
        require(Recipes.adapter(h)!=null,"Witchery spinning dispatch missing");
        Constructor<?> ctor=adapter.getDeclaredConstructor(TemplateRecipeHandler.class,List.class);ctor.setAccessible(true);
        RegistryRecipes cursor=(RegistryRecipes)ctor.newInstance(h,Collections.singletonList(recipe));
        RecipeRow row=NativeCoreFixesTest.row(fibre,new ItemStack(Items.dye,1,2),out);
        require(cursor.capture(0,row),"Spinning recipe lost");
        require(row.inputs.size()==2&&row.outputs.size()==1,"Spinning slots lost");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("3"),"Fibre consumption lost");
        require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),"Modifier display count became consumption");
        require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("nbt").getAsBoolean(),"Input NBT became exact");
        require(row.record.get("duration").getAsString().equals("300"),"Native work duration lost");
        require(h.getIngredientStacks(0).get(1).relx==51&&h.getIngredientStacks(0).get(1).rely==42&&h.getResultStack(0).relx==113,"Native view lost");
        require(modifier.stackSize==9&&fibre.stackSize==3,"Native template mutated");
        Object shadow=make.newInstance(new ItemStack(Items.emerald),fibre,new ItemStack[]{modifier,new ItemStack(Items.diamond)});
        try{ctor.newInstance(h,Arrays.asList(recipe,shadow));throw new AssertionError("Registry precedence ignored");}
        catch(InvocationTargetException e){require(e.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Wrong overlap failure");}
        out.stackSize=4;
        try{cursor.verify();throw new AssertionError("Registry drift accepted");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault e){require(e.code.equals("environment_changed"),"Wrong drift failure");}
        System.out.println("Native Witchery spinning: fibre count, unit modifiers, extras, NBT, registry precedence, layout and drift passed");
    }
    private static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
}
