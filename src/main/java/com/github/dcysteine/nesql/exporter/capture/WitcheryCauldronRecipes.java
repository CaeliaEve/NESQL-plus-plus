package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Native action-list selection; expandedRecipes is only an obsolete NEI projection. */
final class WitcheryCauldronRecipes implements RegistryRecipes {
  private static final String HANDLER="alkalus.main.nei.NEI_Handler_Cauldron";
  private static final String BASE="com.emoniph.witchery.brewing.action.BrewAction";
  private static final String ACTION="com.emoniph.witchery.brewing.action.BrewActionRitualRecipe";
  private static final String RECIPE=ACTION+"$Recipe";
  private static final String REGISTRY="com.emoniph.witchery.brewing.WitcheryBrewRegistry";
  private static final String POWER="com.emoniph.witchery.brewing.AltarPower";
  private List<?> actions;
  private final List<Page> pages=new ArrayList<>();
  private NativeDefinitionGraph definitions;
  private WitcheryBrewActions lookup;
  private Object registry,actionLookup;
  private boolean closed;

  static boolean supportsClass(String name){return HANDLER.equals(name);}
  static boolean supports(ICraftingHandler handler){return handler!=null&&supportsClass(handler.getClass().getName());}
  static Object[] registrationAnchors(){
    Object registry=field(type(REGISTRY),null,"INSTANCE");
    if(registry==null||registry.getClass()!=type(REGISTRY))throw fault("Uninitialized Witchery brew registry");
    Object recipes=field(registry,"recipes"),lookup=field(registry,"ingredients");
    if(recipes==null||recipes.getClass()!=ArrayList.class||lookup==null||lookup.getClass()!=Hashtable.class)
      throw fault("Unadapted Witchery brew backing");
    return new Object[]{registry,recipes,lookup};
  }
  WitcheryCauldronRecipes(TemplateRecipeHandler handler){
    MagicApi.version("WitcheryCauldronRecipes","witchery");MagicApi.version("WitcheryCauldronRecipes","WitcheryExtras");
    if(!supports(handler))throw fault("Unknown Witchery cauldron handler");
    try{
      Object[] roots=registrationAnchors();registry=roots[0];actions=(List<?>)roots[1];actionLookup=roots[2];
      if(actions.size()>4096)throw fault("Native ritual action budget exceeded");
      lookup=new WitcheryBrewActions(actionLookup);
      Map<String,String[]> shapes=NativeDefinitionGraph.shapes(ACTION,"recipes","powerCost","ITEM_KEY");
      shapes.put(RECIPE,new String[]{"result","ingredients"});shapes.put(POWER,new String[]{"power"});
      shapes.put(WitcheryBrewActions.KEY,new String[]{"ITEM","DAMAGE","ITEM_HASH"});
      for(int actionIndex=0;actionIndex<actions.size();actionIndex++){
        Jobs.checkpoint();Object action=actions.get(actionIndex);
        if(action==null||action.getClass()!=type(ACTION))throw fault("Unknown ritual action callback");
        Object power=field(type(BASE),action,"powerCost");
        if(power==null||power.getClass()!=type(POWER))throw fault("Unadapted ritual power callback");
        WitcheryBrewActions.Key trigger=WitcheryBrewActions.key(field(type(BASE),action,"ITEM_KEY"));
        Object raw=field(type(ACTION),action,"recipes");
        if(raw==null||raw.getClass()!=java.lang.reflect.Array.newInstance(type(RECIPE),0).getClass())
          throw fault("Unadapted native ritual definitions");
        Object[] recipes=(Object[])raw;
        if(recipes.length>4096||pages.size()+recipes.length>65536)throw fault("Native ritual definition budget exceeded");
        List<Definition> group=new ArrayList<>();for(Object recipe:recipes)group.add(new Definition(recipe));
        List<ItemStack> triggers=lookup.triggerCandidates(action);
        for(int selected=0;selected<group.size();selected++)
          pages.add(new Page(group,selected,actionIndex,lookup.target(action),trigger,(Integer)field(type(POWER),power,"power"),triggers));
      }
      definitions=new NativeDefinitionGraph(()->new Object[]{field(registry,"recipes")},shapes);
    }catch(RuntimeException|Error error){close();throw error;}
  }
  public int size(){alive();return pages.size();}
  public void verify(){
    alive();Object[] roots=registrationAnchors();
    if(roots[0]!=registry||roots[1]!=actions||roots[2]!=actionLookup)throw changed();
    definitions.verifyRoots();lookup.verify(actionLookup);
  }
  public void finishVerification(){verify();definitions.verify();lookup.finishVerification(field(registry,"ingredients"));}
  public JsonObject exclusion(int index){alive();return pages.get(index).excluded;}
  public boolean capture(int index,RecipeRow row){
    alive();Page page=pages.get(index);if(page.excluded!=null)return false;
    Set<Item> used=Collections.newSetFromMap(new IdentityHashMap<Item,Boolean>());
    for(int slot=0;slot<page.specimens.size();slot++){
      List<RecipeRow.Ingredient> choices=new ArrayList<>();
      for(ItemStack item:page.specimens.get(slot)){
        used.add(item.getItem());choices.add(new RecipeRow.Ingredient(item.copy(),1,false,object("kind","wildcard","meta",false,"nbt",true)));
      }
      row.itemInput(slot,choices,false);
    }
    row.fluidInput(0,new FluidStack(FluidRegistry.WATER,3000),false);
    ItemStack result=page.group.get(page.selected).result.copy();
    row.itemOutput(0,result,10000,object("kind","potential","nominal",Integer.toString(result.stackSize),
        "stat","witchery:ritualWorldItem","condition","admittedActionListAndNativeWorld"));
    JsonArray patterns=array();
    for(Definition definition:page.group){JsonArray keys=array();for(WitcheryBrewActions.Key key:definition.keys)keys.add(key.json());patterns.add(keys);}
    row.record.add("process",object("kind","witcheryNativeRitual","version","0.24.1","scope","postAdmissionActions",
        "actionIndex",page.actionIndex,"selected",page.selected,"trigger",page.trigger.json(),"triggerTarget",page.triggerTarget,
        "triggerPower",page.power,"waterMillibuckets",3000,"capacity",lookup.capacity(),"patterns",patterns,"dispatch",lookup.dispatch(used),"names",lookup.names(used)));
    row.note("witchery:ritualExecution",ACTION+".canAdd;getRecipeResult;updateRitual",row.facts.locale().equals("zh_CN")
        ?"所示为原生动作列表的物品样本，最后一项是仪式触发材料。物品先按酿造注册表转换为动作；原始需求再按动作的字面物品与元数据逐个匹配，32767在这一步不是通配。机器选首条满足的定义，允许额外动作且保留重复需求。触发前准入、抵消动作、沸腾满锅、祭坛与世界条件另行决定是否执行，所示触发动作能量不是整个酿造总耗能。成功时在世界生成物品实体；这里不保证固定投入或产出。"
        :"These are specimens of a native action list; the final item triggers the ritual. Registry dispatch first maps physical items to actions. Original requirements then compare each action's literal item and metadata; 32767 is not a wildcard at that stage. The first satisfied definition wins, allowing extras and preserving multiplicity. Admission before the trigger, nullification, a boiling full cauldron, altar and world conditions determine execution separately. Trigger power is not total brew power. Success spawns a world item; this is not a guaranteed material or output calculation.");
    return true;
  }
  private final class Page{
    final List<Definition> group;final int selected,actionIndex,triggerTarget,power;
    final WitcheryBrewActions.Key trigger;
    final List<List<ItemStack>> specimens=new ArrayList<>();
    JsonObject excluded;
    Page(List<Definition> group,int selected,int actionIndex,int target,WitcheryBrewActions.Key trigger,int power,List<ItemStack> triggers){
      this.group=group;this.selected=selected;this.actionIndex=actionIndex;triggerTarget=target;this.trigger=trigger;this.power=power;
      Definition recipe=group.get(selected);
      if(recipe.result==null){exclude("native_result_is_null",-1);return;}
      if(triggers.isEmpty()){exclude("no_physical_registry_key_reaches_this_trigger_action",-1);return;}
      for(int earlier=0;earlier<selected;earlier++)if(covers(group.get(earlier).keys,recipe.keys,trigger)){
        exclude("earlier_literal_action_multiset_covers_every_current_result_state_with_trigger",earlier);return;
      }
      for(WitcheryBrewActions.Key key:recipe.keys){
        List<ItemStack> candidates=lookup.candidates(key);
        if(candidates.isEmpty()){exclude("required_literal_action_key_is_unreachable_from_native_registry",-1);return;}
        specimens.add(candidates);
      }
      specimens.add(triggers);
    }
    void exclude(String reason,int earlier){excluded=object("native",ACTION,"reason",reason,"actionIndex",actionIndex,"selected",selected,"earlierIndex",earlier);specimens.clear();}
  }
  /** Every completion state contains this trigger; subset coverage proves permanent shadowing. */
  private static boolean covers(List<WitcheryBrewActions.Key> prior,List<WitcheryBrewActions.Key> current,WitcheryBrewActions.Key trigger){
    List<WitcheryBrewActions.Key> offered=new ArrayList<>(current);offered.add(trigger);
    for(WitcheryBrewActions.Key required:prior){
      boolean removed=false;for(int i=0;i<offered.size();i++)if(required.same(offered.get(i))){offered.remove(i);removed=true;break;}
      if(!removed)return false;
    }
    return true;
  }
  private static final class Definition{
    final ItemStack result;final List<WitcheryBrewActions.Key> keys=new ArrayList<>();
    Definition(Object raw){
      if(raw==null||raw.getClass()!=type(RECIPE))throw fault("Unadapted native ritual recipe");
      Object output=field(type(RECIPE),raw,"result");
      if(output!=null&&!(output instanceof ItemStack))throw fault("Invalid native ritual result");
      result=output==null?null:valid((ItemStack)output,true);
      Object ingredients=field(type(RECIPE),raw,"ingredients");
      if(ingredients==null||ingredients.getClass()!=ItemStack[].class||((ItemStack[])ingredients).length>255)
        throw fault("Native ritual requirements exceed supported bound");
      for(ItemStack item:(ItemStack[])ingredients)keys.add(WitcheryBrewActions.key(valid(item,false)));
    }
  }
  private static ItemStack valid(ItemStack item,boolean output){
    NativeStackAudit.audit(item);
    if(output&&item.stackSize<=0)throw fault("Invalid native ritual result count");
    if(Item.itemRegistry.getNameForObject(item.getItem())==null)throw fault("Unregistered native ritual item");
    if(ItemCallbacks.method(item.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
      throw fault("Ritual requirement has dynamic metadata callback");
    return item.copy();
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Witchery cauldron reader is closed");}
  public void close(){closed=true;if(definitions!=null)definitions.close();if(lookup!=null)lookup.close();pages.clear();actions=null;registry=null;actionLookup=null;}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native cauldron registration or definition changed");}
  private static Jobs.Fault fault(String reason){return new Jobs.Fault("recipe_unsupported",reason);}
}
