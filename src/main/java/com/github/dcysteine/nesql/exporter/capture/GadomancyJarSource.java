package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** The matching registry includes entries without GUI recipeStack examples. */
final class GadomancyJarSource implements AutoCloseable {
  private static final String OWNER="makeo.gadomancy.common.registration.RegisteredItems",ENTRY=OWNER+"$StickyJarItemInfo";
  private NativeDefinitionGraph definitions;
  private List<?> entries;
  private boolean closed;
  GadomancyJarSource(){
    try{entries=read();definitions=new NativeDefinitionGraph(()->new Object[]{read()},NativeDefinitionGraph.shapes(ENTRY,"item","damage","recipeStack","research"));}
    catch(RuntimeException|Error failure){close();throw failure;}
  }
  private static List<?> read(){Object raw=field(type(OWNER),null,"stickyJarItems");if(raw==null)throw new Jobs.Fault("handler_not_ready","Sticky jar registry is unavailable");if(raw.getClass()!=ArrayList.class||((List<?>)raw).size()>4096)throw fault("Unadapted sticky jar registry backing");return (List<?>)raw;}
  List<MagicRecipes.Candidate> choices(NativeItemExamples examples){
    verify();List<MagicRecipes.Candidate> result=new ArrayList<>();
    for(Object entry:entries){
      if(entry==null||entry.getClass()!=type(ENTRY))throw fault("Unadapted sticky jar entry owner");
      Item item=(Item)field(entry,"item");int meta=(Integer)field(entry,"damage");ItemStack visual=(ItemStack)field(entry,"recipeStack");
      if(item==null||meta<0||item==Items.slime_ball)throw fault("Invalid native sticky jar item predicate");
      ItemStack template=visual!=null&&visual.getItem()==item&&(meta==32767||Items.feather.getDamage(visual)==meta)?visual.copy():new ItemStack(item,1,meta);
      template.stackSize=1;
      if(template.hasTagCompound()){NBTTagCompound tag=(NBTTagCompound)template.getTagCompound().copy();tag.removeTag("isStickyJar");template.setTagCompound(tag);}
      for(ItemStack stack:examples.copies(template))result.add(new MagicRecipes.Candidate(stack,object("kind","wildcard","meta",meta==32767,"nbt",true)));
      if(result.size()>65536)throw fault("Sticky jar examples exceed cumulative budget");
    }return result;
  }
  JsonArray predicates(){verify();JsonArray result=array();for(Object entry:entries){Item item=(Item)field(entry,"item");result.add(object("registry",Item.itemRegistry.getNameForObject(item),"metadata",field(entry,"damage")));}return result;}
  void verify(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Sticky jar source is closed");definitions.verifyRoots();}
  void finishVerification(){verify();definitions.verify();}
  public void close(){closed=true;if(definitions!=null)definitions.close();entries=null;}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
