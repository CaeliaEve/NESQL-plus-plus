package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Writing-table alchemy is distinct from its higher-priority flask operations and crafting grids. */
final class BloodAlchemyRecipes implements RegistryRecipes {
  private static final String ROOT="WayofTime.alchemicalWizardry.",HANDLER=ROOT+"client.nei.NEIAlchemyRecipeHandler",
      REGISTRY=ROOT+"api.alchemy.AlchemyRecipeRegistry",RECIPE=ROOT+"api.alchemy.AlchemyRecipe";
  private final List<Entry> recipes=new ArrayList<>();
  private final List<Item> orbs=new ArrayList<>();
  private final NativeItemExamples examples=new NativeItemExamples();
  private NativeDefinitionGraph bindings;
  private String orbSignature;
  private boolean closed;

  static boolean supports(ICraftingHandler value){return value!=null&&supportsClass(value.getClass().getName());}
  static boolean supportsClass(String name){return HANDLER.equals(name);}
  static Object[] registrationAnchors(){
    Object raw=field(type(REGISTRY),null,"recipes");
    if(raw==null)throw new Jobs.Fault("handler_not_ready","Blood alchemy registry is unavailable");
    if(raw.getClass()!=ArrayList.class||((List<?>)raw).size()>65536)throw fault("Unadapted Blood alchemy storage");
    return new Object[]{raw};
  }
  BloodAlchemyRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unknown Blood alchemy handler");MagicApi.version("BloodAlchemyRecipes","AWWayofTime");
    try{
      bindings=new NativeDefinitionGraph(BloodAlchemyRecipes::registrationAnchors,
          NativeDefinitionGraph.shapes(RECIPE,"output","recipe","amountNeeded","bloodOrbLevel"));
      orbs.addAll(BloodOrbShapedRecipes.orbs());orbSignature=orbSignature(orbs);
      for(Object raw:(List<?>)registrationAnchors()[0])recipes.add(new Entry(raw));
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  public int size(){alive();return recipes.size();}
  public void verify(){alive();bindings.verifyRoots();}
  public void finishVerification(){
    verify();bindings.verify();examples.verify();List<Item> current=BloodOrbShapedRecipes.orbs();
    if(current.size()!=orbs.size()||!orbSignature.equals(orbSignature(current)))throw changed();
    for(int i=0;i<orbs.size();i++)if(orbs.get(i)!=current.get(i))throw changed();
  }
  public boolean capture(int index,RecipeRow row){
    alive();Entry recipe=recipes.get(index);JsonArray templates=array(),containers=array(),prior=array();int slot=0;
    for(ItemStack template:recipe.inputs){
      templates.add(selector(template));if(template==null)continue;
      List<RecipeRow.Ingredient> choices=new ArrayList<>();JsonArray returns=array();
      for(ItemStack copy:examples.copies(template)){
        choices.add(new RecipeRow.Ingredient(copy,1,false,object("kind","wildcard","meta",Items.feather.getDamage(template)==32767,"nbt",true)));
        ItemStack returned=ItemCallbacks.container(copy,false);
        returns.add(returned==null?JsonNull.INSTANCE:object("id",row.facts.item(returned),"amount",Integer.toString(returned.stackSize)));
      }
      row.itemInput(slot++,choices,false);
      for(JsonElement c:row.inputs.get(row.inputs.size()-1).getAsJsonObject().getAsJsonArray("choices"))c.getAsJsonObject().add("consume",object("kind","allocated"));
      containers.add(returns);
    }
    List<RecipeRow.Ingredient> eligible=new ArrayList<>();JsonArray levels=array();
    for(Item orb:orbs){int level=BloodOrbShapedRecipes.orbLevel(orb);if(level<recipe.tier)continue;
      eligible.add(new RecipeRow.Ingredient(new ItemStack(orb),1,true,object("kind","wildcard","meta",true,"nbt",true)));
      levels.add(object("registry",Item.itemRegistry.getNameForObject(orb),"level",level));
    }
    if(eligible.isEmpty())return row.exclude(object("reason","no_registered_blood_orb_reaches_native_minimum_tier","minimumTier",recipe.tier));
    row.itemInput(5,eligible,false);
    for(int i=0;i<index;i++){Entry earlier=recipes.get(i);JsonArray input=array();for(ItemStack template:earlier.inputs)input.add(selector(template));prior.add(object("minimumOrb",earlier.tier,"templates",input));}
    if(recipe.output.stackSize>0)row.itemOutput(0,recipe.output,10000,object("kind","potential","stat","bloodmagic:writingTable","nominal",Integer.toString(recipe.output.stackSize),"sample",null,"condition","native_table_state","parameters",null));
    row.record.add("process",object("kind","bloodAlchemy","version","1.7.52","templates",templates,"earlier",prior,"minimumOrb",recipe.tier,
        "orbs",levels,"orb",5,"lpPerProgress",recipe.lp,"workProgress",100,"acceleratedStep",5,"containers",containers,
        "result",object("id",row.facts.item(recipe.output),"amount",Integer.toString(recipe.output.stackSize))));
    row.note("bloodmagic:writingTable",ROOT+"common.tileEntity.TEWritingTable#updateEntity,decrementSlots",row.facts.locale().equals("zh_CN")
        ?"炼金分支按注册顺序首匹配：每个非空模板依次占用五个实际槽中的首个剩余匹配槽，忽略 NBT、数量，模板 32767 才是通配，额外材料可保留。保留满足等级的血之宝珠。烧瓶药剂、空白石板、填充剂和复合药剂分支先于本分支；需确认没有进入这些模式。工作进度为 100，成功 tick 按加速 1 或 5 推进并扣有符号 LP×步长（int 运算）；LP 费用只在开始时缓存，切换有效配方可携带进度和旧费用。失配清零，缺 LP 或输出空间暂停。已有输出只比较物品并检查空间，合并后使用新产物的元数据与标签。扣料按模板顺序，每槽最多一次；有容器时替换整个原料堆，而非扣一件再返还。这里只给出条件原型。"
        :"The first matching registration wins. Each non-null template takes the first unused matching slot among five physical slots, ignoring count and NBT; only template metadata 32767 is a wildcard. Extra stock may remain. The tiered blood orb is retained. Flask/potion, blank-slate, filling-agent and combined-potion modes run before this branch and must be absent. Completion needs 100 progress; successful ticks advance by 1 or 5 and debit signed LP times that step using int arithmetic. LP cost is cached only at start, so switching valid recipes may carry progress and old cost. Invalid recipes reset progress; missing LP or output space pauses it. Existing output compares item only and checks space; merging uses the new result metadata/NBT. Depletion follows template order, at most once per slot; a container replaces the entire offered stack. The displayed result is conditional.");
    return true;
  }
  private static JsonElement selector(ItemStack item){return item==null?JsonNull.INSTANCE:object("registry",Item.itemRegistry.getNameForObject(item.getItem()),"metadata",Items.feather.getDamage(item));}
  private static String orbSignature(List<Item> values){JsonArray rows=array();for(Item item:values)rows.add(object("registry",Item.itemRegistry.getNameForObject(item),"level",BloodOrbShapedRecipes.orbLevel(item)));return CanonicalJson.digest(rows);}
  private static final class Entry{
    final ItemStack[] inputs=new ItemStack[5];final ItemStack output;final int lp,tier;
    Entry(Object raw){
      if(raw==null||raw.getClass()!=type(RECIPE))throw fault("Unadapted alchemy recipe owner");
      Object source=field(raw,"recipe");if(source==null||source.getClass()!=ItemStack[].class||((ItemStack[])source).length>65536)throw fault("Invalid alchemy template array");
      ItemStack[] values=(ItemStack[])source;for(int i=0;i<Math.min(5,values.length);i++){if(values[i]!=null){NativeStackAudit.audit(values[i]);inputs[i]=values[i].copy();}}
      Object result=field(raw,"output");if(result==null||result.getClass()!=ItemStack.class)throw fault("Invalid alchemy output");NativeStackAudit.audit((ItemStack)result);output=((ItemStack)result).copy();
      lp=(Integer)field(raw,"amountNeeded");tier=(Integer)field(raw,"bloodOrbLevel");
    }
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Blood alchemy reader is closed");}
  public void close(){closed=true;if(bindings!=null)bindings.close();examples.close();recipes.clear();orbs.clear();}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Blood orb identity or tier changed");}
}
