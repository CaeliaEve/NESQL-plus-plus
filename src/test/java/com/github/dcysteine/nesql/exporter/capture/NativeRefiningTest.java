package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
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
        TemplateRecipeHandler.CachedRecipe borrowed=handler.new CachedRecipe(){public codechicken.nei.PositionedStack getResult(){return null;}};
        handler.arecipes.add(borrowed);
        RegistryRecipes adapter=adapter(handler,manager);
        RecipeRow row=row(water,lava);require(adapter.capture(0,row),"Refinery recipe omitted");
        require(handler.arecipes.size()==1&&handler.arecipes.get(0)==borrowed,"Refining semantic reader rewrote borrowed GUI cache");
        JsonObject process=row.record.getAsJsonObject("process");
        require(process.get("kind").getAsString().equals("buildcraftRefinery") && process.get("energy").getAsInt()==30
                && process.get("delay").getAsString().equals("5") && process.get("capacity").getAsInt()==4000,"Refinery process cost/delay lost");
        require(row.inputs.size()==2 && row.outputs.size()==1 && row.record.get("energy").isJsonNull() && row.record.get("duration").isJsonNull(),"Refinery lost duplicate requirements or invented RF/t");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsInt()==700,"Refinery amount changed");
        RecipeRow repeat=row(water,lava);adapter.capture(0,repeat);
        require(repeat.inputs.equals(row.inputs),"Cached refinery tank changed the captured source facts");
        lifecycle(handler,manager,adapter,water,lava);
        actualSource(handler,manager,water,lava);
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
        System.out.println("Native BuildCraft refinery: duplicate preview/partial completion, borrowed GUI preservation, native priority/fill drift, actual source binding, ready-empty/close and zero-energy gate passed");
    }
    @SuppressWarnings("unchecked") private static void lifecycle(TemplateRecipeHandler handler,Object manager,RegistryRecipes reader,FluidStack... fluids)throws Exception{
        reader.finishVerification();
        Map<String,Object> registry=(Map<String,Object>)MagicApi.field(manager,"recipes");String key=registry.keySet().iterator().next();Object recipe=registry.get(key);
        FluidStack output=(FluidStack)MagicApi.field(recipe,"output");int amount=output.amount;
        try{output.amount++;expect("recipe_changed",()->reader.capture(0,row(fluids)));}finally{output.amount=amount;}
        int energy=(Integer)MagicApi.field(recipe,"energyCost");
        try{set(recipe,"energyCost",energy+1);expect("recipe_changed",reader::finishVerification);}finally{set(recipe,"energyCost",energy);}
        List<FluidStack> filling=(List<FluidStack>)MagicApi.field(manager,"validFluids1");FluidStack template=filling.get(0);int count=template.amount;
        try{template.amount++;expect("recipe_changed",reader::finishVerification);}finally{template.amount=count;}
        try{filling.set(0,template.copy());expect("recipe_changed",reader::finishVerification);}finally{filling.set(0,template);}
        try{set(manager,"validFluids1",new ArrayList<>(filling));expect("recipe_changed",reader::verify);}finally{set(manager,"validFluids1",filling);}
        try{set(manager,"recipes",new HashMap<>(registry));expect("recipe_changed",reader::verify);}finally{set(manager,"recipes",registry);}
        reader.finishVerification();reader.close();reader.close();
        expect("capture_closed",reader::size);expect("capture_closed",()->reader.capture(0,row(fluids)));
        require(registry.get(key)==recipe&&handler.arecipes.size()==1,"Closing refinery reader changed borrowed native/GUI records");
        Constructor<?> constructor=manager.getClass().getDeclaredConstructor();constructor.setAccessible(true);
        try(RegistryRecipes empty=adapter(handler,constructor.newInstance())){require(empty.size()==0,"Refinery ready-empty manager failed");empty.finishVerification();}
        expect("handler_not_ready",()->adapter(handler,null));
    }
    private static void actualSource(TemplateRecipeHandler handler,Object fixture,FluidStack... fluids)throws Exception{
        Object singleton=MagicApi.field(fixture.getClass(),null,"INSTANCE");
        Field api=Class.forName("buildcraft.api.recipes.BuildcraftRecipeRegistry").getField("refinery");Object priorApi=api.get(null);
        String[] keys={"recipes","validFluids1","validFluids2"};Object[] prior=new Object[keys.length];
        cpw.mods.fml.common.Loader loader=cpw.mods.fml.common.Loader.instance();
        Map<String,cpw.mods.fml.common.ModContainer> old=cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(cpw.mods.fml.common.Loader.class,loader,"namedMods");
        Map<String,cpw.mods.fml.common.ModContainer> configured=new HashMap<>(old);
        for(String[] version:new String[][]{{"BuildCraft|Factory","7.1.44"},{"BuildCraft|Compat","7.1.18"}}){
            cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();metadata.modId=version[0];metadata.version=version[1];
            configured.put(version[0],new cpw.mods.fml.common.DummyModContainer(metadata));
        }
        for(int index=0;index<keys.length;index++)prior[index]=MagicApi.field(singleton,keys[index]);
        try{
            for(String key:keys)set(singleton,key,MagicApi.field(fixture,key));api.set(null,singleton);
            cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,configured,"namedMods");
            RegistrationBindings first=RegistrationBindings.read(handler);
            require(first.same(RegistrationBindings.read((TemplateRecipeHandler)handler.getClass().newInstance())),"Refinery independent registrations differ");
            try(RegistryRecipes reader=new RefiningRecipes(handler)){
                require(reader.size()==1&&reader.capture(0,row(fluids)),"Actual refinery machine/API source failed");reader.finishVerification();
                Object nativeMap=MagicApi.field(singleton,"recipes");
                try{set(singleton,"recipes",new HashMap<>((Map<?,?>)nativeMap));require(!first.same(RegistrationBindings.read(handler)),"Refinery replacement map stayed equivalent");expect("recipe_changed",reader::verify);}
                finally{set(singleton,"recipes",nativeMap);}
                try{api.set(null,fixture);expect("recipe_changed",reader::verify);}finally{api.set(null,singleton);}
                reader.finishVerification();
            }
        }finally{
            for(int index=0;index<keys.length;index++)set(singleton,keys[index],prior[index]);api.set(null,priorApi);
            cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,loader,old,"namedMods");
        }
    }
    private interface Probe{void run()throws Exception;}
    private static void expect(String code,Probe probe)throws Exception{try{probe.run();throw new AssertionError("Expected refinery failure: "+code);}catch(Jobs.Fault failure){require(failure.code.equals(code),"Expected "+code+": "+failure);}}
    private static void set(Object owner,String key,Object value)throws Exception{Field field=owner.getClass().getDeclaredField(key);field.setAccessible(true);field.set(owner,value);}
    private static RegistryRecipes adapter(TemplateRecipeHandler h,Object manager)throws Exception{
        Constructor<?> c=Class.forName("com.github.dcysteine.nesql.exporter.capture.RefiningRecipes").getDeclaredConstructor(TemplateRecipeHandler.class,Object.class,int.class);c.setAccessible(true);
        try{return (RegistryRecipes)c.newInstance(h,manager,4000);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    @SuppressWarnings("unchecked") private static RecipeRow row(FluidStack... stacks){Facts f=new Facts("en_US");Set<String> known=(Set<String>)MagicApi.field(f,"fluids");for(FluidStack s:stacks)known.add(Identity.fluid(s.getFluid().getName(),TypedNbt.encode(s.tag)));return new RecipeRow(f,object("owner","fixture","handler","refinery","key","refinery"),"category_test",0);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
