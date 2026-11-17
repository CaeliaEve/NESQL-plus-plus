package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.cache.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Exercises the production registry reader, not a class-name whitelist. A
 * callback can be catalyst-ineligible and still affect later selection. */
final class NativeMagicBeesPoolAdmissionTest {
  @SuppressWarnings({"rawtypes","unchecked"})
  static void run(Object bee,ItemStack offered)throws Exception{
    try(NativeRegistryScope scope=new NativeRegistryScope()){
      Class<?> base=type("vazkii.botania.api.recipe.RecipeManaInfusion");
      ItemStack prototype=offered.copy();prototype.stackSize=1;
      scope.replace(base,bee,"input",prototype);scope.replace(base,bee,"output",prototype);
      scope.replace(bee.getClass(),bee,"outputCache",prototype);
      scope.replace(base,bee,"mana",10);scope.replace(base,bee,"isAlchemy",true);
      scope.replace(base,bee,"isConjuration",false);
      ItemStack fixed=new ItemStack(Items.diamond,3);
      ItemStack ordinaryDisplay=prototype.copy();ordinaryDisplay.setTagCompound(null);
      Object ordinary=base.getConstructor(ItemStack.class,Object.class,int.class).newInstance(fixed,prototype,2);
      List<Object> registry=new ArrayList<>(Arrays.asList(bee,ordinary));
      scope.replace(type("vazkii.botania.api.BotaniaAPI"),null,"manaInfusionRecipes",registry);
      Class<?> blocks=type("vazkii.botania.common.block.ModBlocks");
      scope.replace(blocks,null,"alchemyCatalyst",Blocks.gold_block);
      scope.replace(blocks,null,"conjurationCatalyst",Blocks.iron_block);
      java.lang.reflect.Constructor<?> loader=type("forestry.apiculture.genetics.BeeGenome$1").getDeclaredConstructor();
      loader.setAccessible(true);
      LoadingCache cache=CacheBuilder.newBuilder().maximumSize(128).expireAfterAccess(1,TimeUnit.MINUTES)
          .build((CacheLoader)loader.newInstance());
      scope.replace(type("forestry.apiculture.genetics.BeeGenome"),null,"beeGenomeCache",cache);
      TemplateRecipeHandler handler=(TemplateRecipeHandler)type(
          "vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool").newInstance();
      try(RegistryRecipes reader=new BotaniaPoolRecipes(handler)){
        if(reader.size()!=4)throw new AssertionError("Mixed native registry lost catalyst branches");
        JsonObject origin=object("owner","Botania","handler",handler.getClass().getName(),"key","native-pool");
        JsonArray records=array();String program=null;
        for(int index=0;index<reader.size();index++){
          RecipeRow row=NativeCoreFixesTest.row(prototype,fixed,ordinaryDisplay);
          row.record.add("source",origin);row.record.addProperty("category",Identity.origin("category",origin));
          row.record.addProperty("order",index);
          String next=reader.program(row.facts);
          if(next==null||program!=null&&!program.equals(next))throw new AssertionError("Missing shared native pool program");
          program=next;
          if(!reader.capture(index,row))throw new AssertionError("Dynamic priority was guessed from a fixed sample");
          JsonObject process=row.record.getAsJsonObject("process");
          if(!process.get("kind").getAsString().equals("nativeBotaniaPool")
              ||!next.equals(process.get("program").getAsString())
              ||process.get("index").getAsInt()!=(index==0?0:1))
            throw new AssertionError("Mixed callbacks did not retain source registry order");
          JsonObject change=row.outputs.get(0).getAsJsonObject().getAsJsonObject("change");
          if(!change.getAsJsonObject("action").get("kind").getAsString().equals("nativePoolResult")
              ||change.getAsJsonArray("samples").size()!=0)
            throw new AssertionError("Constructor output was advertised as a successful native result");
          NativeContractFixture.writeOwned("botania-native-pool-"+index,row,Arrays.asList(prototype,fixed,ordinaryDisplay));
          records.add(row.record);
        }
        if(field(bee,"outputCache")!=prototype||cache.size()!=0||!handler.arecipes.isEmpty())
          throw new AssertionError("Production discovery executed a live callback/cache load or GUI handler");
        reader.finishVerification();
        try(NativeRegistryScope changed=new NativeRegistryScope()){
          changed.replace(bee.getClass(),bee,"outputCache",prototype.copy());
          try{reader.finishVerification();throw new AssertionError("Production pool accepted equal-content output-cache drift");}
          catch(Jobs.Fault expected){if(!expected.code.equals("recipe_changed"))throw expected;}
        }
        reader.finishVerification();
      }
    }
    System.out.println("Native pool production admission: mixed registry, shared program, conditional prototypes,"
        +" no live match/load/output calls and output-cache identity drift passed");
  }
}
