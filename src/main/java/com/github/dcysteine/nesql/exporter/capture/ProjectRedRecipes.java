package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

/** Absolute-grid and first-remaining native matchers; no recipe display constructors. */
final class ProjectRedRecipes implements RegistryRecipes {
  private static final String ROOT="mrtjp.projectred.core.libmc.recipe.";
  private final boolean shaped;
  private final Class<?> handlerClass;
  private final List<Entry> entries=new ArrayList<>();
  private final NativeItemExamples examples=new NativeItemExamples();
  private Object manager,registry;
  private final String signature;
  private boolean closed;

  static boolean supports(ICraftingHandler value){return value!=null&&supportsClass(value.getClass().getName());}
  static boolean supportsClass(String name){return (ROOT+"PRShapedRecipeHandler").equals(name)||(ROOT+"PRShapelessRecipeHandler").equals(name);}
  static Object[] registrationAnchors(Class<?> handler){
    if(!supportsClass(handler.getName()))throw fault("Unknown ProjectRed family");
    CraftingManager manager=CraftingManager.getInstance();Object registry=manager.getRecipeList();
    if(registry==null)throw new Jobs.Fault("handler_not_ready","ProjectRed crafting registry is unavailable");
    if(registry.getClass()!=ArrayList.class||((List<?>)registry).size()>262144)throw fault("Unadapted crafting registry storage");
    return new Object[]{handler,manager,registry};
  }

  ProjectRedRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unknown ProjectRed handler");
    handlerClass=handler.getClass();shaped=handlerClass.getName().endsWith("PRShapedRecipeHandler");
    Object[] roots=registrationAnchors(handlerClass);manager=roots[1];registry=roots[2];
    JsonArray facts=read(entries);signature=CanonicalJson.digest(facts);
  }
  public int size(){alive();return entries.size();}
  public void verify(){alive();Object[] actual=registrationAnchors(handlerClass);if(actual[1]!=manager||actual[2]!=registry)throw changed();}
  public void finishVerification(){verify();List<Entry> actual=new ArrayList<>();if(!signature.equals(CanonicalJson.digest(read(actual)))||actual.size()!=entries.size())throw changed();for(int i=0;i<actual.size();i++)if(!entries.get(i).same(actual.get(i)))throw changed();examples.verify();}
  private JsonArray read(List<Entry> target){
    JsonArray rows=array();String expected=ROOT+(shaped?"ShapedBuilderRecipe":"ShapelessBuilderRecipe");long bytes=0;
    for(Object source:(List<?>)registry){Jobs.checkpoint();if(source==null||!type(expected).isInstance(source))continue;
      if(source.getClass()!=type(expected))throw fault("Unadapted ProjectRed recipe owner");
      Entry entry=new Entry(source,shaped);target.add(entry);JsonObject row=entry.json();rows.add(row);bytes+=CanonicalJson.bytes(row).length;
      if(target.size()>262144||bytes>32L*1024*1024)throw fault("ProjectRed source snapshot exceeds budget");
    }return rows;
  }
  public boolean capture(int index,RecipeRow row){
    alive();Entry entry=entries.get(index);
    if(entry.minimumSlots>9)return row.exclude(object("reason","native_workbench_inventory_too_small","nativeClass",entry.source.getClass().getName(),"minimumSlots",entry.minimumSlots,"availableSlots",9));
    JsonArray predicates=array(),cells=array();int slot=0;
    for(Predicate predicate:entry.predicates){
      if(predicate==null){predicates.add(value(null));cells.add(value(null));continue;}
      predicates.add(predicate.json());List<RecipeRow.Ingredient> choices=predicate.choices(examples);
      if(choices.isEmpty())return row.exclude(object("reason","native_projectred_predicate_has_no_registered_examples","nativeClass",predicate.source.getClass().getName(),"predicate",predicate.json()));
      cells.add(value(slot));row.nativeCraftingInput(slot++,choices);
    }
    row.itemOutput(0,entry.output.copy(),10000);
    if(shaped)row.record.add("grid",object("width",3,"height",3,"cells",cells,"mirror",false));
    row.record.add("process",object("kind","projectRedCrafting","version","4.12.6-GTNH","shaped",shaped,
        "minimumSlots",entry.minimumSlots,"predicates",predicates,"ignoredOutputs",entry.outputCount-1));
    row.note("projectred:workbench",ROOT+(shaped?"ShapedBuilderRecipe":"ShapelessBuilderRecipe")+"#matches,getRecipeOutput",
        row.facts.locale().equals("zh_CN")?"有序配方固定检查工作台绝对格子，不平移、不镜像；无序配方逐格取首个匹配的剩余谓词。每个占用格消耗一件，容器／工具规则另计。原生只返回第一个输出定义，其余定义不会形成额外产物。矿辞按当前注册名称匹配，物品与微方块按各自原生谓词判断。":"Shaped recipes check absolute workbench cells without translation or mirroring; shapeless recipes take the first matching remaining predicate in physical cell order. Each occupied cell uses one item, with separate container/tool behavior. Native crafting returns only the first output definition; remaining definitions do not become extra products. Ore names, item rules and microblock keys retain their native predicates.");
    return true;
  }

  private static final class Entry {
    final Object source,builder,inputRoot,outputRoot;final List<Predicate> predicates=new ArrayList<>();final ItemStack output;final int minimumSlots,outputCount;
    Entry(Object source,boolean shaped){
      this.source=source;builder=field(source,"builder");if(builder==null||builder.getClass()!=type(ROOT+(shaped?"ShapedRecipeBuilder":"ShapelessRecipeBuilder")))throw fault("Unadapted ProjectRed builder");
      inputRoot=field(builder,shaped?"inputMap":"inResult");outputRoot=field(builder,"outResult");
      if(shaped){String map=(String)field(builder,"map");if(map==null)throw new Jobs.Fault("handler_not_ready","ProjectRed builder has not computed its map");minimumSlots=map.length();
        for(int i=0;i<9;i++)predicates.add(null);
        for(Object pair:NativeScala.values(inputRoot,9)){Object key=NativeScala.pair(pair,1),input=NativeScala.pair(pair,2);if(!(key instanceof Integer)||(Integer)key<0||(Integer)key>=9)throw fault("ProjectRed input lies outside the native workbench");if(input!=null)predicates.set((Integer)key,new Predicate(input));}
      }else{List<Object> inputs=NativeScala.values(inputRoot,9);minimumSlots=inputs.size();for(Object input:inputs)predicates.add(new Predicate(input));}
      List<Object> outputs=NativeScala.values(outputRoot,65536);outputCount=outputs.size();if(outputs.isEmpty())throw fault("ProjectRed has no native first output");
      Object first=outputs.get(0);if(first==null||first.getClass()!=type(ROOT+"ItemOut"))throw fault("Unadapted ProjectRed output callback");
      output=keyStack(field(first,"key"));if(output.stackSize<=0)throw fault("ProjectRed first output is nonpositive");
    }
    JsonObject json(){JsonArray inputs=array(),oreMembers=array();for(Predicate predicate:predicates){inputs.add(predicate==null?value(null):predicate.json());if(predicate!=null)for(String name:predicate.ores){JsonArray members=array();for(ItemStack stack:NativeOreTable.copies(name))members.add(RecipeRow.stackEvidence(stack));oreMembers.add(object("name",name,"members",members));}}return object("minimumSlots",minimumSlots,"predicates",inputs,"output",RecipeRow.stackEvidence(output),"outputCount",outputCount,"oreMembers",oreMembers);}
    boolean same(Entry other){if(source!=other.source||builder!=other.builder||inputRoot!=other.inputRoot||outputRoot!=other.outputRoot||predicates.size()!=other.predicates.size())return false;for(int i=0;i<predicates.size();i++){Predicate a=predicates.get(i),b=other.predicates.get(i);if(a==null?b!=null:b==null||a.source!=b.source||a.key!=b.key)return false;}return true;}
  }

  private static final class Predicate {
    final Object source,key;final String kind;final ItemStack item;final boolean checkNbt;final List<String> ores=new ArrayList<>();final List<ItemStack> baked=new ArrayList<>();
    Predicate(Object source){
      if(source==null)throw fault("Null ProjectRed ingredient");this.source=source;String name=source.getClass().getName();
      if(name.equals(ROOT+"ItemIn")){kind="item";key=field(source,"key");item=keyStack(key);checkNbt=(Boolean)field(source,"nbt");}
      else if(name.equals(ROOT+"MicroIn")){kind="micro";key=field(source,"sample");item=keyStack(key);checkNbt=true;}
      else if(name.equals(ROOT+"OreIn")){kind="ore";key=field(source,"oreIDs");item=null;checkNbt=false;for(Object id:NativeScala.values(key,4096)){
        if(!(id instanceof Integer)||(Integer)id<0)throw fault("Invalid ProjectRed ore ID");String ore=OreDictionary.getOreName((Integer)id);if(ore==null||ore.isEmpty()||ore.equals("Unknown"))throw fault("Unregistered ProjectRed ore ID");if(!ores.contains(ore))ores.add(ore);
      }for(Object value:NativeScala.values(field(source,"ins"),65536)){if(!(value instanceof ItemStack))throw fault("Invalid ProjectRed baked ore example");NativeStackAudit.audit((ItemStack)value);baked.add(((ItemStack)value).copy());}}
      else throw fault("Unadapted ProjectRed ingredient owner: "+name);
    }
    JsonObject json(){JsonArray names=array();for(String ore:ores)names.add(value(ore));return object("kind",kind,"registry",item==null?null:Item.itemRegistry.getNameForObject(item.getItem()),"metadata",item==null?null:net.minecraft.init.Items.feather.getDamage(item),"nbt",item==null?null:TypedNbt.encode(item.getTagCompound()),"checkNbt",checkNbt,"ores",names);}
    List<RecipeRow.Ingredient> choices(NativeItemExamples examples){
      List<RecipeRow.Ingredient> choices=new ArrayList<>();
      if(item!=null){for(ItemStack copy:kind.equals("micro")?new ItemStack[]{item.copy()}:examples.copies(item)){copy.stackSize=1;choices.add(new RecipeRow.Ingredient(copy,1,false,object("kind","wildcard","meta",kind.equals("item")&&item.getItemDamage()==32767,"nbt",!checkNbt)));}}
      else for(String ore:ores)for(ItemStack raw:NativeOreTable.copies(ore))for(ItemStack copy:examples.copies(raw))choices.add(new RecipeRow.Ingredient(copy,1,false,object("kind","ore","name",ore,"exclusive",false)));
      return choices;
    }
  }
  private static ItemStack keyStack(Object source){
    if(source==null||source.getClass()!=type("mrtjp.core.item.ItemKeyStack"))throw fault("Unadapted ProjectRed item-key stack");
    Object key=field(source,"key");if(key==null||key.getClass()!=type("mrtjp.core.item.ItemKey"))throw fault("Unadapted ProjectRed item key");
    Object item=field(key,"item"),tag=field(key,"tag");if(!(item instanceof Item)||Item.itemRegistry.getNameForObject(item)==null)throw fault("ProjectRed key has no registered item");NativeStackAudit.identityOwner(item);
    if(tag!=null&&tag.getClass()!=NBTTagCompound.class)throw fault("Unadapted ProjectRed key NBT");NativeStackAudit.nbt((NBTTagCompound)tag);
    ItemStack stack=new ItemStack((Item)item,(Integer)field(source,"stackSize"),(Integer)field(key,"itemDamage"));stack.setTagCompound(tag==null?null:(NBTTagCompound)((NBTTagCompound)tag).copy());NativeStackAudit.audit(stack);return stack;
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","ProjectRed reader is closed");}
  public void close(){closed=true;entries.clear();examples.close();manager=null;registry=null;}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","ProjectRed source, builder or predicates changed");}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
