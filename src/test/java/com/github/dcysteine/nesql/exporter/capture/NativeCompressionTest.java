package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;

/** Native disabled lookup, source seal and safeOre filtering, through real scoped registries. */
final class NativeCompressionTest {
  static void run() throws Exception {
    Object loader=cpw.mods.fml.common.Loader.instance();
    Field named=loader.getClass().getDeclaredField("namedMods"); named.setAccessible(true);
    Object oldMods=named.get(loader); Class<?> owner;
    try {named.set(loader,Collections.emptyMap());owner=Class.forName("fox.spiteful.avaritia.Avaritia");}
    finally {named.set(loader,oldMods);}
    try (NativeRegistryScope scope=new NativeRegistryScope()) {
      NativeCoreFixesTest.version("Avaritia","1.77");
      Class<?> manager=Class.forName("fox.spiteful.avaritia.crafting.CompressorManager");
      ArrayList<Object> recipes=new ArrayList<>(); scope.replace(manager,null,"recipes",recipes);
      scope.replace(net.minecraft.client.Minecraft.class,null,"theMinecraft",
          scope.allocate(net.minecraft.client.Minecraft.class));
      TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName(
          "fox.spiteful.avaritia.compat.nei.CompressionHandler").newInstance();
      scope.replace(owner,null,"isDreamCraftLoaded",false);
      require(Recipes.adapter(handler)==null,"Enabled compressor was claimed as disabled");
      scope.replace(owner,null,"isDreamCraftLoaded",true);
      require(Recipes.adapter(handler)!=null,"Disabled compressor has no evidence route");
      Class<?> recipeType=Class.forName("fox.spiteful.avaritia.crafting.CompressorRecipe");
      // The native output getter would throw: disabled evidence must not invoke it.
      Object recipe=recipeType.getConstructor(ItemStack.class,int.class,ItemStack.class,boolean.class)
          .newInstance(null,9,new ItemStack(Items.paper),true);
      recipes.add(recipe);
      require(manager.getMethod("getOutput",ItemStack.class).invoke(null,new ItemStack(Items.paper))==null
          &&(Integer)manager.getMethod("getCost",ItemStack.class).invoke(null,new ItemStack(Items.paper))==0,
          "Native lookup is not gated by DreamCraft");
      Object emptyOre=Class.forName("fox.spiteful.avaritia.crafting.CompressOreRecipe")
          .getConstructor(ItemStack.class,int.class,String.class,boolean.class)
          .newInstance(null,4,"nesqlAbsentCompressionOre",true);
      recipes.add(0,emptyOre);
      try (DisabledRecipes registry=new DisabledRecipes(handler)) {
        require(registry.size()==1,"Native safeOre filter changed diagnostic indices");
        Facts facts=new Facts("en_US");
        try (Recipes.Cursor cursor=new Recipes.Cursor(new Recipes.Handler(handler,0), facts, registry, null)) {
          cursor.capture(0); registry.finishVerification();
          require(facts.drain().records.isEmpty(),"Disabled compressor emitted recipe facts");
          JsonObject proof=cursor.exclusions().getAsJsonObject("0");
          require(proof.get("reason").getAsString().equals("native_recipe_lookup_disabled")
              &&proof.get("nativeIndex").getAsInt()==1,"Disabled proof lost its original registry index");
          require(cursor.exclusions().has("handler"),"Whole-handler proof missing");
          scope.replace(manager,null,"recipes",new ArrayList<>(recipes));
          expect("environment_changed",registry::verify);
          scope.replace(manager,null,"recipes",recipes);
        }
      }
      int[] calls={0};
      ArrayList<Object> unknown=new ArrayList<Object>() {
        @Override public int size(){calls[0]++;throw new AssertionError("Foreign list callback ran");}
      };
      scope.replace(manager,null,"recipes",unknown);
      expect("recipe_unsupported",()->new DisabledRecipes(handler));
      require(calls[0]==0,"Unknown native list executed before admission");
      scope.replace(manager,null,"recipes",recipes);
      recipes.clear();
      DisabledRecipes empty=new DisabledRecipes(handler);
      require(empty.size()==0&&empty.handlerExclusion()!=null,"Empty disabled registry lost its proof");
      empty.finishVerification();
      scope.replace(owner,null,"isDreamCraftLoaded",false);
      expect("environment_changed",empty::verify);
      empty.close();empty.close();
      expect("capture_closed",empty::size);
    }
  }
  private static void expect(String code,Runnable action) {
    try {action.run();throw new AssertionError("Expected "+code);}
    catch(Jobs.Fault expected){require(expected.code.equals(code),"Unexpected failure: "+expected);}
  }
  private static void require(boolean value,String message) {
    if(!value)throw new AssertionError(message);
  }
}
