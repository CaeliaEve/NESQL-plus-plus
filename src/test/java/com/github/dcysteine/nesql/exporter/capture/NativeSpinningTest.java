package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.*;
import java.util.*;

/** Small conformance probe using the actual pinned native matcher. */
public final class NativeSpinningTest {
    public static void run() throws Exception {
        Class<?> type=Class.forName("witchinggadgets.common.util.recipe.SpinningRecipe");
        Constructor<?> make=type.getConstructor(ItemStack.class,Object[].class);
        ItemStack paper=new ItemStack(Items.paper,19),dye=new ItemStack(Items.dye,7,2),out=new ItemStack(Items.string,3);
        Object recipe=make.newInstance(out,new Object[]{paper,dye});
        Method match=type.getMethod("inputsMatch",ItemStack[].class);
        ItemStack tagged=dye.copy();tagged.setTagInfo("ignored",new net.minecraft.nbt.NBTTagInt(1));
        require((Boolean)match.invoke(recipe,(Object)new ItemStack[]{tagged,paper}),"Native swap/NBT predicate changed");
        require(!(Boolean)match.invoke(recipe,(Object)new ItemStack[]{paper,new ItemStack(Items.dye,1,3)}),"Native metadata predicate changed");
        Class<?> adapter;
        try{adapter=Class.forName("com.github.dcysteine.nesql.exporter.capture.SpinningRecipes");}
        catch(ClassNotFoundException e){throw new AssertionError("Missing spinning adapter",e);}
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("witchinggadgets.client.nei.NEISpinningWheelHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"Spinning dispatch missing");
        Constructor<?> ctor=adapter.getDeclaredConstructor(TemplateRecipeHandler.class,List.class);ctor.setAccessible(true);
        RegistryRecipes cursor=(RegistryRecipes)ctor.newInstance(handler,Collections.singletonList(recipe));
        RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.paper),new ItemStack(Items.dye,1,2),out);
        require(cursor.capture(0,row),"Spinning recipe lost");
        require(row.inputs.size()==2&&row.outputs.size()==1,"Spinning slots lost");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),"Registry display count became consumption");
        require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule").get("nbt").getAsBoolean(),"Ignored input NBT became exact");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"),"Fixed result count lost");
        require(row.record.get("duration").getAsString().equals("121"),"Native completion tick lost");
        require(handler.getIngredientStacks(0).get(1).relx==21&&handler.getIngredientStacks(0).get(1).rely==27&&handler.getResultStack(0).relx==126,"Native layout lost");
        require(paper.stackSize==19&&dye.stackSize==7,"Native source mutated");
        OreDictionary.registerOre("nesqlSpinningFiber",new ItemStack(Items.paper));
        OreDictionary.registerOre("nesqlSpinningFiber",new ItemStack(Items.leather));
        Object ore=make.newInstance(out,new Object[]{"nesqlSpinningFiber",dye});
        cursor=(RegistryRecipes)ctor.newInstance(handler,Collections.singletonList(ore));
        row=NativeCoreFixesTest.row(new ItemStack(Items.paper),new ItemStack(Items.leather),new ItemStack(Items.dye,1,2),out);
        require(cursor.capture(0,row)&&row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size()==2,"Native ore choices lost");
        // Native list removal can consume multiple requirements for one offered slot.
        Object unsafe=make.newInstance(out,new Object[]{"nesqlSpinningFiber","nesqlSpinningFiber"});
        require((Boolean)match.invoke(unsafe,(Object)new ItemStack[]{paper,new ItemStack(Items.diamond)}),"Native greedy counterexample changed");
        try{ctor.newInstance(handler,Collections.singletonList(unsafe));throw new AssertionError("Order-dependent ore overlap accepted");}
        catch(InvocationTargetException e){require(e.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Wrong overlap failure");}
        out.stackSize=4;
        try{cursor.verify();throw new AssertionError("Registry drift accepted");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault e){require(e.code.equals("environment_changed"),"Wrong drift failure");}
        System.out.println("Native spinning: swapped inputs, NBT, unit consumption, ore choices, greedy counterexample, layout and drift passed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
