package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;

/** Native DreamCraft lookup gate; no game world, ticks or recipe callbacks are executed. */
final class NativeCompressionTest {
    @SuppressWarnings("unchecked") static void run() throws Exception {
        Object loader=cpw.mods.fml.common.Loader.instance();
        Field named=loader.getClass().getDeclaredField("namedMods"); named.setAccessible(true);
        Object oldMods=named.get(loader);
        Class<?> owner;
        try {named.set(loader,Collections.emptyMap());owner=Class.forName("fox.spiteful.avaritia.Avaritia");}
        finally {named.set(loader,oldMods);}
        Field flag = owner.getField("isDreamCraftLoaded");
        boolean previous = flag.getBoolean(null);
        Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
        Object base = unsafe.staticFieldBase(flag); long offset = unsafe.staticFieldOffset(flag);
        Class<?> manager = Class.forName("fox.spiteful.avaritia.crafting.CompressorManager");
        List<Object> recipes = (List<Object>) manager.getMethod("getRecipes").invoke(null);
        List<Object> old = new ArrayList<>(recipes);
        Field singleton=net.minecraft.client.Minecraft.class.getDeclaredField("theMinecraft");singleton.setAccessible(true);
        Object oldClient=singleton.get(null);
        try {
            singleton.set(null,unsafe.allocateInstance(net.minecraft.client.Minecraft.class));
            TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("fox.spiteful.avaritia.compat.nei.CompressionHandler").newInstance();
            unsafe.putBoolean(base,offset,false);
            require(Recipes.adapter(handler)==null,"Enabled compressor incorrectly claimed as disabled");
            unsafe.putBoolean(base,offset,true);
            require(Recipes.adapter(handler)!=null,"DreamCraft-disabled compressor lacks an evidence route");
            recipes.clear();
            Class<?> recipeType = Class.forName("fox.spiteful.avaritia.crafting.CompressorRecipe");
            // Its output getter would throw on null: a disabled lookup must not invoke it.
            Object recipe = recipeType.getConstructor(ItemStack.class,int.class,ItemStack.class,boolean.class)
                    .newInstance(null,9,new ItemStack(Items.paper),true);
            recipes.add(recipe);
            require(manager.getMethod("getOutput",ItemStack.class).invoke(null,new ItemStack(Items.paper))==null
                    && (Integer)manager.getMethod("getCost",ItemStack.class).invoke(null,new ItemStack(Items.paper))==0,
                    "Pinned native lookup is not gated by DreamCraft");
            Object emptyOre = Class.forName("fox.spiteful.avaritia.crafting.CompressOreRecipe")
                    .getConstructor(ItemStack.class,int.class,String.class,boolean.class).newInstance(null,4,"nesqlAbsentCompressionOre",true);
            recipes.add(0,emptyOre);
            RegistryRecipes registry = registry(handler,recipes);
            require(registry.size()==1,"Native safeOre filter changed the diagnostic indices");
            Facts facts = new Facts("en_US");
            try(Recipes.Cursor cursor=new Recipes.Cursor(new Recipes.Handler(handler,0),handler,facts,false,null,null,null,registry,null)){
                cursor.capture(0);
                require(facts.drain().records.isEmpty(),"Disabled compressor emitted recipe facts");
                JsonObject proof = cursor.exclusions().getAsJsonObject("0");
                require(proof.get("reason").getAsString().equals("native_recipe_lookup_disabled")
                        && proof.get("nativeIndex").getAsInt()==1,"Disabled compressor lost gate or original registry index");
                require(cursor.exclusions().has("handler"),"Whole-handler exclusion proof missing");
            }
            RegistryRecipes empty=registry(handler,Collections.emptyList());
            try(Recipes.Cursor cursor=new Recipes.Cursor(new Recipes.Handler(handler,0),handler,new Facts("en_US"),false,null,null,null,empty,null)){
                require(cursor.size()==0 && cursor.exclusions().has("handler"),"An empty disabled registry lost its reason");
                unsafe.putBoolean(base,offset,false);
                try{cursor.size();throw new AssertionError("Empty registry skipped environment guard");}
                catch(Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong changed gate failure");}
            }
        } finally {unsafe.putBoolean(base,offset,previous);recipes.clear();recipes.addAll(old);singleton.set(null,oldClient);}
        System.out.println("Native Avaritia: DreamCraft gate, skipped unsafe getters, safeOre indices, zero-recipe evidence and drift protection passed");
    }
    private static RegistryRecipes registry(TemplateRecipeHandler handler,List<?> source)throws Exception {
        Constructor<?> constructor=Class.forName("com.github.dcysteine.nesql.exporter.capture.DisabledRecipes").getDeclaredConstructor(TemplateRecipeHandler.class,List.class);
        constructor.setAccessible(true);
        try{return (RegistryRecipes)constructor.newInstance(handler,source);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
