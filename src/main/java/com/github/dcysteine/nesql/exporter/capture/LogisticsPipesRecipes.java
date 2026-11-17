package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Native soldering inventory and callback law; no GUI recipe cache or fabricated random UUID. */
final class LogisticsPipesRecipes implements RegistryRecipes {
  private static final String HANDLER="logisticspipes.nei.NEISolderingStationRecipeManager",
      REGISTRY="logisticspipes.recipes.SolderingStationRecipes",RECORD=REGISTRY+"$SolderingStationRecipe";
  private final List<Entry> recipes=new ArrayList<>();
  private NativeDefinitionGraph bindings;
  private JsonArray definitions;
  private boolean closed;
  static boolean supports(ICraftingHandler value){return value!=null&&supportsClass(value.getClass().getName());}
  static boolean supportsClass(String name){return HANDLER.equals(name);}
  static Object[] registrationAnchors(){
    Object table=field(type(REGISTRY),null,"recipes");
    if(table==null)throw new Jobs.Fault("handler_not_ready","Logistics soldering source is unavailable");
    if(table.getClass()!=ArrayList.class||((List<?>)table).size()>65536)throw fault("Unadapted Logistics soldering storage");
    return new Object[]{table,field(type("logisticspipes.config.Configs"),null,"LOGISTICS_POWER_USAGE_DISABLED")};
  }
  LogisticsPipesRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unknown Logistics soldering handler");MagicApi.version("LogisticsPipesRecipes","LogisticsPipes");
    try{
      bindings=new NativeDefinitionGraph(LogisticsPipesRecipes::registrationAnchors,NativeDefinitionGraph.shapes(RECORD,"source","result"));
      for(Object raw:(List<?>)registrationAnchors()[0])recipes.add(new Entry(raw));
      definitions=array();for(Entry entry:recipes){JsonArray cells=array();for(ItemStack item:entry.inputs)cells.add(selector(item));definitions.add(object("inputs",cells,"result",selector(entry.output)));}
      if(com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(definitions).length>32*1024*1024)throw new Jobs.Fault("recipe_limit","Logistics source description exceeds byte budget");
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  public int size(){alive();return recipes.size();}
  public void verify(){alive();bindings.verifyRoots();}
  public void finishVerification(){verify();bindings.verify();for(Entry entry:recipes)if(field(entry.raw,"handler")!=entry.callback)throw new Jobs.Fault("environment_changed","Soldering result callback identity changed");}
  public boolean capture(int index,RecipeRow row){
    alive();Entry recipe=recipes.get(index);JsonArray cells=array();int slot=0;
    for(ItemStack item:recipe.inputs){
      if(item==null){cells.add(JsonNull.INSTANCE);continue;}cells.add(value(slot));
      row.itemInput(slot++,Collections.singletonList(new RecipeRow.Ingredient(item.copy(),1,false,object("kind","wildcard","meta",false,"nbt",true))),false);
      for(JsonElement choice:row.inputs.get(row.inputs.size()-1).getAsJsonObject().getAsJsonArray("choices"))choice.getAsJsonObject().add("consume",object("kind","allocated"));
    }
    row.record.add("grid",object("width",3,"height",3,"cells",cells,"mirror",false));
    if(recipe.output.stackSize>0)row.itemOutput(0,recipe.output,10000,object("kind","potential","stat","logisticspipes:solderingInventory","nominal",Integer.toString(recipe.output.stackSize),"sample",null,"condition","native_heat_power_output_and_callback_state","parameters",null));
    row.record.add("process",object("kind","logisticsSoldering","version","1.4.24-GTNH","selected",index,"definitions",definitions,
        "solderSlot",9,"solderPredicate","anyItem","result",object("id",row.facts.item(recipe.output),"amount",Integer.toString(recipe.output.stackSize)),
        "resultTag",recipe.callback==null?"prototype":"freshRandomUuid","heat",100,"workProgress",100,"powerDisabled",registrationAnchors()[1]));
    row.note("logisticspipes:soldering","logisticspipes.blocks.LogisticsSolderingTileEntity#updateEntity,tryCraft,getTagetForRecipe",row.facts.locale().equals("zh_CN")
        ?"固定 3×3 实际格子，空位必须空；只比较物品与字面元数据，32767 在这里不是通配，忽略 NBT 和原料堆数量。槽 9 接受任意非空物品，每次成功扣一件，没有容器返还。100 热量后工作，每个邻接物流管可按 30／20／10 能量推进 3／2／1，达到 100 才尝试；供能设置免除的是升温费用，不免除工作阶段的管道供能。缺工作清进度；输出不兼容尝试失败，进度减 50。取料方法会边遍历注册表边修改库存：结果物品与元数据相同的前序甚至不匹配项也可能扣走已匹配的格子，不能用普通逐槽固定消耗代替。动态卡片在尝试时替换整个标签为新随机 UUID；不导出伪造 UUID，图标只是产物原型。"
        :"Uses absolute 3×3 cells with mandatory empty holes. Matching compares item and literal metadata (32767 is not a wildcard), ignoring NBT and stock count. Slot 9 accepts any non-null item and loses one per successful operation, without container return. At heat 100 each adjacent logistics pipe can supply 30/20/10 energy for 3/2/1 progress. Progress 100 triggers an attempt. The power-disabled setting waives heating cost, not working pipe energy. No work resets progress; an incompatible output makes an attempt fail and subtracts 50 progress. Native removal mutates inventory while scanning all registrations: earlier even nonmatching rows sharing result item/metadata may remove matching cells. This is not ordinary fixed per-cell depletion. Dynamic cards replace the entire tag with a fresh random UUID per attempt. No UUID is invented during capture; the displayed item is a conditional prototype.");
    return true;
  }
  private static JsonElement selector(ItemStack item){return item==null?JsonNull.INSTANCE:object("registry",Item.itemRegistry.getNameForObject(item.getItem()),"metadata",Items.feather.getDamage(item));}
  private static final class Entry{
    final Object raw,callback;final ItemStack[] inputs=new ItemStack[9];final ItemStack output;
    Entry(Object raw){
      if(raw==null||raw.getClass()!=type(RECORD))throw fault("Unadapted soldering recipe owner");this.raw=raw;
      Object source=field(raw,"source"),result=field(raw,"result");
      if(source==null||source.getClass()!=ItemStack[].class||((ItemStack[])source).length<9||((ItemStack[])source).length>65536||result==null||result.getClass()!=ItemStack.class)throw fault("Invalid native soldering fields");
      for(int i=0;i<9;i++){ItemStack item=((ItemStack[])source)[i];if(item!=null){NativeStackAudit.audit(item);inputs[i]=item.copy();}}
      NativeStackAudit.audit((ItemStack)result);output=((ItemStack)result).copy();callback=field(raw,"handler");if(callback!=null){auditCallback(callback,output);output.setTagCompound(null);}
    }
  }
  private static void auditCallback(Object callback,ItemStack result){
    // The pinned 1.4.24 JAR contains the anonymous and stateless lambda forms of the same UUID law.
    // Neither callback is executed: random production state must not become a captured observation.
    Class<?> owner=callback.getClass(),registry=type(REGISTRY);
    boolean anonymous=owner==type(REGISTRY+"$1");
    boolean lambda=owner.isSynthetic()&&owner.getName().startsWith(REGISTRY+"$$Lambda$")&&owner.getDeclaredFields().length==0;
    if((!anonymous&&!lambda)||owner.getClassLoader()!=registry.getClassLoader()||owner.getProtectionDomain().getCodeSource()==null
        ||!owner.getProtectionDomain().getCodeSource().getLocation().equals(registry.getProtectionDomain().getCodeSource().getLocation())
        ||result.getItem()!=field(type("logisticspipes.LogisticsPipes"),null,"LogisticsItemCard"))throw fault("Unadapted soldering result callback owner: "+owner.getName());
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Logistics soldering reader is closed");}
  public void close(){closed=true;if(bindings!=null)bindings.close();recipes.clear();definitions=null;}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
