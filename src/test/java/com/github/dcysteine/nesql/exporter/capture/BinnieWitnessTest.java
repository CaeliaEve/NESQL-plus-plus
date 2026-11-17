package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Small, owned source witnesses replay the exact base/capture boundary that exhausted the live heap. */
final class BinnieWitnessTest {
  static void run() throws Exception {
    try(NativeRegistryScope scope=new NativeRegistryScope()){
      BinnieGeneticsRecipes reader=scope.allocate(BinnieGeneticsRecipes.class);
      Class<?> domainType=Class.forName(BinnieGeneticsRecipes.class.getName()+"$Domain");
      Object domain=scope.allocate(domainType);
      JsonObject selected=object("item","minecraft:wheat","meta",0,"amount",1,"nbt",null);
      JsonArray members=array();members.add(object("stack",selected,"type",0));
      for(int i=0;i<4096;i++)members.add(object("stack",object("item","minecraft:paper","meta",i,"amount",1,"nbt",null),"type",0));
      JsonObject data=object("root","owned-memory-test","members",members,"conversions",array(),"chromosomes",array());
      String before=CanonicalJson.digest(data);scope.replace(domainType,domain,"data",data);
      Method base=BinnieGeneticsRecipes.class.getDeclaredMethod("base",domainType,Object[].class);base.setAccessible(true);
      JsonObject process=(JsonObject)base.invoke(reader,domain,new Object[0]);
      if(process.get("domain")!=data)throw new AssertionError("Every rule duplicates the full native genetics catalogue at open");
      Class<?> entryType=Class.forName(BinnieGeneticsRecipes.class.getName()+"$Entry");
      Constructor<?> constructor=entryType.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);Object entry=constructor.newInstance(process);
      Method in=entryType.getDeclaredMethod("in",ItemStack.class,int.class,String.class,boolean.class);in.setAccessible(true);
      in.invoke(entry,new ItemStack(Items.wheat),4,"staged",false);
      scope.replace(BinnieGeneticsRecipes.class,reader,"entries",new ArrayList<>(Collections.singletonList(entry)));
      scope.replace(BinnieGeneticsRecipes.class,reader,"machine","acclimatiser");
      RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.wheat));reader.capture(0,row);
      JsonObject witness=row.record.getAsJsonObject("process").getAsJsonObject("domain");
      if(witness.getAsJsonArray("members").size()!=1||!witness.getAsJsonArray("members").get(0).getAsJsonObject().get("stack").equals(selected))throw new AssertionError("Captured row retains unrelated members instead of its exact native witness");
      if(!before.equals(CanonicalJson.digest(data)))throw new AssertionError("Projecting one recipe mutated the full native domain");
      witness.getAsJsonArray("members").get(0).getAsJsonObject().addProperty("type",99);
      RecipeRow next=NativeCoreFixesTest.row(new ItemStack(Items.wheat));reader.capture(0,next);
      if(next.record.getAsJsonObject("process").getAsJsonObject("domain").getAsJsonArray("members").get(0).getAsJsonObject().get("type").getAsInt()!=0)throw new AssertionError("One captured row mutated later witnesses");
    }
    System.out.println("Binnie witnesses: shared 4097-member source; exact one-member row; full-domain and captured-row isolation passed");
  }
}
