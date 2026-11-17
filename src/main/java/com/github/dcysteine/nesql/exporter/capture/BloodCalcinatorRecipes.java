package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Native item-to-internal-reagent conversion. The reagent display item is never an item result. */
final class BloodCalcinatorRecipes implements RegistryRecipes {
  private static final String ROOT="WayofTime.alchemicalWizardry.",HANDLER=ROOT+"client.nei.NEICalcinatorHandler",REGISTRY=ROOT+"api.alchemy.energy.ReagentRegistry";
  private final List<Entry> entries=new ArrayList<>();
  private NativeDefinitionGraph bindings;
  private boolean closed;
  static boolean supports(ICraftingHandler value){return value!=null&&supportsClass(value.getClass().getName());}
  static boolean supportsClass(String name){return HANDLER.equals(name);}
  static Object[] registrationAnchors(){Object map=field(type(REGISTRY),null,"itemToReagentMap"),names=field(type(REGISTRY),null,"reagentList");if(map==null||names==null)throw new Jobs.Fault("handler_not_ready","Blood Magic reagent tables are unavailable");if(map.getClass()!=HashMap.class||names.getClass()!=HashMap.class||((Map<?,?>)map).size()>65536||((Map<?,?>)names).size()>4096)throw fault("Unadapted Blood Magic reagent storage");return new Object[]{map,names};}
  BloodCalcinatorRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unknown calcinator handler");MagicApi.version("BloodCalcinatorRecipes","AWWayofTime");
    try{
      Map<String,String[]> shapes=NativeDefinitionGraph.shapes(ROOT+"api.alchemy.energy.ReagentStack","reagent","amount");shapes.put(ROOT+"api.alchemy.energy.Reagent",new String[]{"name","colourRed","colourGreen","colourBlue","colourIntensity"});
      bindings=new NativeDefinitionGraph(BloodCalcinatorRecipes::registrationAnchors,shapes);
      Map<?,?> map=(Map<?,?>)registrationAnchors()[0];
      for(Map.Entry<?,?> row:map.entrySet())entries.add(new Entry(row.getKey(),row.getValue()));
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  public int size(){alive();return entries.size();}
  public void verify(){alive();bindings.verifyRoots();}
  public void finishVerification(){verify();bindings.verify();}
  public boolean capture(int index,RecipeRow row){
    alive();Entry entry=entries.get(index);
    if(entry.input==null)return row.exclude(object("reason","native_reagent_lookup_skips_null_item_key","nativeClass",REGISTRY));
    for(int earlier=0;earlier<index;earlier++){Entry previous=entries.get(earlier);if(previous.input!=null&&previous.input.getItem()==entry.input.getItem()&&previous.input.getItemDamage()==entry.input.getItemDamage())return row.exclude(object("reason","earlier_native_reagent_key_claims_same_item_and_metadata","earlierIndex",earlier));}
    List<RecipeRow.Ingredient> orbs=new ArrayList<>();for(Item orb:BloodOrbShapedRecipes.orbs())orbs.add(new RecipeRow.Ingredient(new ItemStack(orb),1,true,object("kind","wildcard","meta",true,"nbt",true)));
    if(orbs.isEmpty())throw new Jobs.Fault("handler_not_ready","Calcinator has no registered blood orb examples");
    row.itemInput(0,orbs,false);row.itemInput(1,Collections.singletonList(new RecipeRow.Ingredient(entry.input.copy(),1,false,object("kind","wildcard","meta",false,"nbt",true))),false);
    row.record.add("process",object("kind","bloodCalcinator","version","1.7.52","input",1,"orb",0,"reagent",entry.reagent,"name",entry.name,
        "amount",entry.amount,"color",entry.color,"workTicks",200,"lpPerTick",10,"bufferCapacity",2000,"mainCapacity",4000,"transferPerTick",20));
    row.note("bloodmagic:calcinator",ROOT+"common.tileEntity.TEAlchemicCalcinator#tickProgress,moveBufferToMain",
        row.facts.locale().equals("zh_CN")?"原料在槽 1，每次完成取用一件；槽 0 血之宝珠保留并提供有效灵魂网络所有者。每个成功工作 tick 扣 10 LP，进度达到 200 后向内部缓冲加入原生有符号试剂数量；没有物品产物。缓冲需能容纳完整试剂且类型兼容，缺网络／LP／空间时进度暂停，缺原料才归零。换为另一有效原料可保留进度。每 tick 最多向主罐转移 20 试剂；非正注册值保留原生储罐算术，不当成正产量。":"Slot 1 supplies one item per completed operation; the slot-0 blood orb is retained and must bind a valid soul-network owner. Each successful work tick costs 10 LP; at 200 progress the signed native reagent amount enters the internal buffer. No item is produced. Full compatible buffer space is required; missing network, LP or space pauses progress, while absent material resets it. Changing valid materials can retain progress. Up to 20 reagent units transfer to the main tank per tick. Nonpositive registered values retain native tank arithmetic rather than becoming positive yields.");
    return true;
  }
  private static final class Entry {
    final ItemStack input;final String reagent,name;final int amount;final JsonArray color;
    Entry(Object input,Object stack){
      if(input!=null&&input.getClass()!=ItemStack.class)throw fault("Unadapted calcinator item key");
      if(input!=null)NativeStackAudit.audit((ItemStack)input);this.input=input==null?null:((ItemStack)input).copy();
      if(stack==null||stack.getClass()!=type(ROOT+"api.alchemy.energy.ReagentStack"))throw fault("Unadapted calcinator reagent stack");
      Object actual=field(stack,"reagent");if(actual==null||actual.getClass()!=type(ROOT+"api.alchemy.energy.Reagent"))throw fault("Unadapted calcinator reagent identity");
      String found=null;for(Map.Entry<?,?> entry:((Map<?,?>)registrationAnchors()[1]).entrySet())if(entry.getValue()==actual){if(!(entry.getKey() instanceof String))throw fault("Invalid reagent key");if(found==null)found=(String)entry.getKey();}
      if(found==null||found.isEmpty())throw fault("Calcinator reagent is not registered");reagent=found;name=(String)field(actual,"name");amount=(Integer)field(stack,"amount");
      color=array();for(String key:new String[]{"colourRed","colourGreen","colourBlue","colourIntensity"})color.add(value((Integer)field(actual,key)));
    }
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Calcinator reader is closed");}
  public void close(){closed=true;if(bindings!=null)bindings.close();entries.clear();}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
