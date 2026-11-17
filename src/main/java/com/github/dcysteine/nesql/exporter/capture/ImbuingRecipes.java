package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Native RandomThings identity-HashMap matching, including overlap, key reuse and stale output. */
final class ImbuingRecipes implements RegistryRecipes {
  private static final String HANDLER="lumien.randomthings.Handler.ModCompability.NEI.ImbuingStationRecipeHandler",
      RECIPE="lumien.randomthings.Handler.ImbuingStation.ImbuingRecipe",REGISTRY="lumien.randomthings.Handler.ImbuingStation.ImbuingRecipeHandler",
      SETTINGS="lumien.randomthings.Configuration.Settings";
  private final List<Entry> entries=new ArrayList<>();
  private final NativeItemExamples examples=new NativeItemExamples();
  private NativeDefinitionGraph bindings;
  private final IdentityHashMap<ItemStack,Integer> outputAliases=new IdentityHashMap<>();
  private JsonArray definitions;
  private int thresholdBits;
  private boolean closed;
  private Object[] anchors;
  private final Set<String> oreNames=new TreeSet<>();
  static boolean supports(ICraftingHandler handler){return handler!=null&&supportsClass(handler.getClass().getName());}
  static boolean supportsClass(String name){return HANDLER.equals(name);}
  static Object[] registrationAnchors(){
    Object source=field(type(REGISTRY),null,"imbuingRecipes");if(source==null)throw new Jobs.Fault("handler_not_ready","Imbuing source is unavailable");
    if(source.getClass()!=ArrayList.class||((List<?>)source).size()>4096)throw fault("Unadapted imbuing registry storage");
    return new Object[]{source,field(OreDictionary.class,null,"nameToId"),field(OreDictionary.class,null,"idToStack")};
  }
  ImbuingRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unknown imbuing handler");MagicApi.version("ImbuingRecipes","RandomThings");
    try{
      anchors=registrationAnchors();
      thresholdBits=Float.floatToRawIntBits((Float)field(type(SETTINGS),null,"IMBUING_LENGTH"));
      for(Object raw:(List<?>)registrationAnchors()[0])entries.add(new Entry(raw));
      definitions=array();for(Entry entry:entries){definitions.add(entry.definition());oreNames.addAll(entry.base.ores);for(Filter filter:entry.ingredients)oreNames.addAll(filter.ores);}
      bindings=new NativeDefinitionGraph(this::definitionRoots,NativeDefinitionGraph.shapes(RECIPE,"toImbue","ingredients","result"));
      if(com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(definitions).length>32*1024*1024)throw new Jobs.Fault("recipe_limit","Imbuing native descriptions exceed byte budget");
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  private Object[] definitionRoots(){List<Object> roots=new ArrayList<>();roots.add(registrationAnchors()[0]);for(String name:oreNames)roots.add(NativeOreTable.members(name));return roots.toArray();}
  public int size(){alive();return entries.size();}
  public void verify(){alive();Object[] current=registrationAnchors();for(int i=0;i<anchors.length;i++)if(anchors[i]!=current[i])throw new Jobs.Fault("environment_changed","Imbuing native registry roots changed");bindings.verifyRoots();if(Float.floatToRawIntBits((Float)field(type(SETTINGS),null,"IMBUING_LENGTH"))!=thresholdBits)throw new Jobs.Fault("environment_changed","Imbuing timer configuration changed");}
  public void finishVerification(){verify();bindings.verify();examples.verify();}
  public boolean capture(int index,RecipeRow row){
    alive();Entry entry=entries.get(index);
    for(int slot=0;slot<entry.ingredients.size();slot++)input(row,entry.ingredients.get(slot),slot);
    input(row,entry.base,65535);
    if(entry.output.stackSize>0)row.itemOutput(0,entry.output,10000,object("kind","potential","stat","randomthings:imbuingState","nominal",Integer.toString(Math.min(64,entry.output.stackSize)),"sample",null,"condition","native_identity_order_and_cached_output_state","parameters",null));
    row.record.add("process",object("kind","randomThingsImbuing","version","2.6.6","thresholdBits",String.format(Locale.ROOT,"%08x",thresholdBits),"selected",index,
        "definitions",definitions,"baseInput",65535,"baseSlot",3,"reagentSlots",3,"outputSlot",4,"result",object("id",row.facts.item(entry.output),"amount",Integer.toString(entry.output.stackSize))));
    row.note("randomthings:imbuing", "lumien.randomthings.Handler.ImbuingStation.ImbuingRecipe#matchesInventory;lumien.randomthings.TileEntities.TileEntityImbuingStation#updateEntity,imbue",row.facts.locale().equals("zh_CN")
        ?"原生配方按注册顺序首匹配。中心在槽 3，三个材料槽按 ItemStack 对象身份放入 HashMap；每个要求总取 HashMap 遍历中的首个匹配键，即使该键已使用也会再次匹配，最后每个非空键都必须被标记。重复对象别名折叠为一个键，重叠材料的结果取决于运行时对象哈希顺序，不能用普通无序分配代替。匹配为内容相同（含标签、忽略数量）或共享矿辞。完成扣槽 0..3 每槽一件，不返还容器。输出对象引用变化且新输出能装入时才重置并更新缓存；同一输出引用不会重检空间，不能装入另一输出时可能继续旧缓存产物。计时按原生 float 阈值与 signed int 进度比较，非有限阈值也保留。首次落空槽原生限 64，已有输出直接做有符号数量变更。这里只展示条件原型和完整规则。"
        :"The first matching registration wins. Slot 3 is the base; reagent stacks in slots 0..2 become identity-keyed HashMap entries. Every requirement chooses the first matching key in native map order, even if already used; every non-null key must eventually be marked. Reused stack-object aliases collapse into one key. Overlap therefore depends on runtime identity-hash order, not ordinary shapeless allocation. Matching uses full content equality (ignoring count) or shared ore membership. Completion removes one from each slot 0..3 without containers. A changed result object resets/updates the cache only if its output fits. The same reference does not recheck output space; an incompatible new result may leave the old cached result running. The signed-int timer compares to the native float threshold, including non-finite values. First insertion clamps to 64; existing output applies signed count mutation. Displayed output is a conditional prototype.");
    return true;
  }
  private void input(RecipeRow row,Filter filter,int slot){
    List<RecipeRow.Ingredient> choices=new ArrayList<>();choices.add(new RecipeRow.Ingredient(filter.direct.copy(),1,false,object("kind","exact")));
    Set<String> seen=new HashSet<>();for(String ore:filter.ores)for(ItemStack template:NativeOreTable.copies(ore))for(ItemStack example:examples.permutations(template)){
      if(choices.size()>=65536)throw new Jobs.Fault("recipe_limit","Imbuing candidates exceed budget");String key=Item.itemRegistry.getNameForObject(example.getItem())+"/"+Items.feather.getDamage(example)+"/"+ore;
      if(seen.add(key))choices.add(new RecipeRow.Ingredient(example,1,false,object("kind","ore","name",ore,"exclusive",false)));
    }
    row.itemInput(slot,choices,false);for(JsonElement c:row.inputs.get(row.inputs.size()-1).getAsJsonObject().getAsJsonArray("choices"))c.getAsJsonObject().add("consume",object("kind","allocated"));
  }
  private final class Entry{
    final Filter base;final List<Filter> ingredients=new ArrayList<>();final ItemStack output;final int alias;
    Entry(Object raw){
      if(raw==null||raw.getClass()!=type(RECIPE))throw fault("Unadapted imbuing callback owner");base=new Filter(field(raw,"toImbue"));Object inputs=field(raw,"ingredients");
      if(inputs==null||inputs.getClass()!=ArrayList.class||((List<?>)inputs).size()>65536)throw fault("Invalid imbuing input definitions");for(Object value:(List<?>)inputs)ingredients.add(new Filter(value));
      Object result=field(raw,"result");if(result==null||result.getClass()!=ItemStack.class)throw fault("Invalid imbuing output");NativeStackAudit.audit((ItemStack)result);output=((ItemStack)result).copy();
      Integer group=outputAliases.get(result);if(group==null){group=outputAliases.size();outputAliases.put((ItemStack)result,group);}alias=group;
    }
    JsonObject definition(){JsonArray inputs=array();for(Filter filter:ingredients)inputs.add(filter.definition());return object("base",base.definition(),"ingredients",inputs,"outputAlias",alias);}
  }
  private static final class Filter{
    final ItemStack direct;final List<String> ores=new ArrayList<>();
    Filter(Object value){if(value==null||value.getClass()!=ItemStack.class)throw fault("Invalid imbuing predicate stack");NativeStackAudit.audit((ItemStack)value);direct=((ItemStack)value).copy();direct.stackSize=1;
      for(int id:OreDictionary.getOreIDs(direct))ores.add(OreDictionary.getOreName(id));if(ores.size()>4096)throw fault("Imbuing ore memberships exceed budget");
    }
    JsonObject definition(){JsonArray names=array();for(String ore:ores)names.add(new JsonPrimitive(ore));return object("registry",Item.itemRegistry.getNameForObject(direct.getItem()),"metadata",Items.feather.getDamage(direct),"nbt",TypedNbt.encode(direct.getTagCompound()),"ores",names);}
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Imbuing reader is closed");}
  public void close(){closed=true;if(bindings!=null)bindings.close();examples.close();entries.clear();outputAliases.clear();definitions=null;anchors=null;oreNames.clear();}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
