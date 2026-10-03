package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.JsonObject;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native BuildCraft preview/completion differences on owned tanks. */
final class NativeRefiningTest {
    @SuppressWarnings("unchecked") static void run() throws Exception {
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("buildcraft.compat.nei.RecipeHandlerRefinery").newInstance();
        require(Recipes.adapter(handler)!=null,"BuildCraft refinery lacks a registry adapter");
        Class<?> managerType=Class.forName("buildcraft.core.recipes.RefineryRecipeManager");
        Constructor<?> ctor=managerType.getDeclaredConstructor();ctor.setAccessible(true);Object manager=ctor.newInstance();
        FluidStack water=new FluidStack(FluidRegistry.WATER,700), lava=new FluidStack(FluidRegistry.LAVA,50);
        Method add=managerType.getMethod("addRecipe",String.class,FluidStack.class,FluidStack.class,FluidStack.class,int.class,int.class);
        add.invoke(manager,"duplicate",water,water.copy(),lava,30,5);
        List<?> recipes=new ArrayList<>((Collection<?>)managerType.getMethod("getRecipes").invoke(manager));
        Object recipe=recipes.get(0);Class<?> api=Class.forName("buildcraft.api.recipes.IFlexibleCrafter");
        Method craft=recipe.getClass().getMethod("craft",api,boolean.class);
        List<FluidStack> tanks=new ArrayList<>(Arrays.asList(new FluidStack(FluidRegistry.WATER,1000),null));
        Object owned=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(p,m,a)->{
            switch(m.getName()) {
                case "getCraftingItemStackSize":return 0;
                case "getCraftingFluidStackSize":return tanks.size();
                case "getCraftingFluidStack":return tanks.get((Integer)a[0]);
                case "decrCraftingFluidStack":{
                    int index=(Integer)a[0],n=(Integer)a[1];FluidStack value=tanks.get(index),removed=value.copy();
                    removed.amount=Math.min(n,value.amount);value.amount-=removed.amount;if(value.amount==0)tanks.set(index,null);return removed;
                }
                default:throw new AssertionError("Unexpected refinery callback "+m);
            }
        });
        require(craft.invoke(recipe,owned,true)!=null && tanks.get(0).amount==1000,"Native preview must recheck duplicate fluid against undecremented stock");
        require(craft.invoke(recipe,owned,false)==null && tanks.get(0)==null,"Native completion must retain partial drain on failure");
        RegistryRecipes adapter=adapter(handler,manager);
        RecipeRow row=row(water,lava);require(adapter.capture(0,row),"Refinery recipe omitted");
        JsonObject process=row.record.getAsJsonObject("process");
        require(process.get("kind").getAsString().equals("buildcraftRefinery") && process.get("energy").getAsInt()==30
                && process.get("delay").getAsString().equals("5") && process.get("capacity").getAsInt()==4000,"Refinery process cost/delay lost");
        require(row.inputs.size()==2 && row.outputs.size()==1 && row.record.get("energy").isJsonNull() && row.record.get("duration").isJsonNull(),"Refinery lost duplicate requirements or invented RF/t");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsInt()==700,"Refinery amount changed");
        Object cached=handler.arecipes.get(0);List<?> display=(List<?>)MagicApi.field(cached,"tanks");
        require(display.size()==3,"Native refinery layout changed");
        ((net.minecraftforge.fluids.FluidTank)MagicApi.field(display.get(0),"tank")).getFluid().amount=1;
        RecipeRow repeat=row(water,lava);adapter.capture(0,repeat);
        require(repeat.inputs.equals(row.inputs),"Cached refinery tank changed the captured source facts");
        managerType.getMethod("removeRecipe",String.class).invoke(manager,"duplicate");
        require(((List<?>)managerType.getMethod("getValidFluidStacks1").invoke(manager)).contains(new FluidStack(FluidRegistry.WATER,1)),
                "Native valid-fluid list unexpectedly tracks recipe removal or quantity");
        add.invoke(manager,"later",new FluidStack(FluidRegistry.LAVA,10),new FluidStack(FluidRegistry.WATER,20),lava,0,7);
        Object battery=Class.forName("buildcraft.core.lib.RFBattery").getConstructor(int.class,int.class,int.class).newInstance(10000,1500,0);
        Method use=battery.getClass().getMethod("useEnergy",int.class,int.class,boolean.class);
        require((Integer)use.invoke(battery,0,0,true)==0 && (Integer)use.invoke(battery,-7,-7,true)<=0,"Nonpositive refinery energy gate became executable");
        RegistryRecipes zero=adapter(handler,manager);
        require(!zero.capture(0,row(water,lava)) && zero.exclusion(0)!=null,"Native zero-energy gate must retain an exclusion proof");
        add.invoke(manager,"future",water,water.copy(),lava,30,9);
        List<?> ordered=new ArrayList<>((Collection<?>)managerType.getMethod("getRecipes").invoke(manager));
        int at=0;while(!MagicApi.field(ordered.get(at),"id").equals("future"))at++;
        RecipeRow future=row(water,lava);adapter(handler,manager).capture(at,future);
        JsonObject futureProcess=future.record.getAsJsonObject("process");
        require(futureProcess.getAsJsonArray("earlier").size()==at && futureProcess.getAsJsonArray("filling").get(0).getAsJsonArray().size()==2,
                "Native map priority or fill permissions from removed recipes were lost");
        require(water.amount==700 && lava.amount==50,"Refinery capture mutated native templates");
        System.out.println("Native BuildCraft refinery: duplicate-fluid preview vs partial completion, exact amounts, owned layout, retained fill eligibility and zero-energy gate passed");
    }
    private static RegistryRecipes adapter(TemplateRecipeHandler h,Object manager)throws Exception{
        Constructor<?> c=Class.forName("com.github.dcysteine.nesql.exporter.capture.RefiningRecipes").getDeclaredConstructor(TemplateRecipeHandler.class,Object.class,int.class);c.setAccessible(true);
        try{return (RegistryRecipes)c.newInstance(h,manager,4000);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    @SuppressWarnings("unchecked") private static RecipeRow row(FluidStack... stacks){Facts f=new Facts("en_US");Set<String> known=(Set<String>)MagicApi.field(f,"fluids");for(FluidStack s:stacks)known.add(Identity.fluid(s.getFluid().getName(),TypedNbt.encode(s.tag)));return new RecipeRow(f,object("owner","fixture","handler","refinery","key","refinery"),"category_test",0);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
