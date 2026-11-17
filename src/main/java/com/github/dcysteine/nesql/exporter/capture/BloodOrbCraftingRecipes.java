package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

/** Both Blood Orb workbench laws, including shapeless integer early failure and overlapping groups. */
final class BloodOrbCraftingRecipes implements RegistryRecipes {
  private static final String ROOT="WayofTime.alchemicalWizardry.",SHAPED=ROOT+"api.items.ShapedBloodOrbRecipe",SHAPELESS=ROOT+"api.items.ShapelessBloodOrbRecipe";
  private final boolean shaped;
  private final List<Object> registrations=new ArrayList<>();
  private final List<Integer> indices=new ArrayList<>();
  private final List<Item> orbs=new ArrayList<>();
  private final List<Entry> entries=new ArrayList<>();
  private final NativeItemExamples examples=new NativeItemExamples();
  private NativeDefinitionGraph definitions;
  private List<?> source;
  private JsonArray orbLevels;
  private boolean closed;
  static boolean supportsClass(String name){return name.equals(ROOT+"client.nei.NEIBloodOrbShapedHandler")||name.equals(ROOT+"client.nei.NEIBloodOrbShapelessHandler");}
  static Object[] registrationAnchors(){
    CraftingManager manager=CraftingManager.getInstance();if(manager==null)throw new Jobs.Fault("handler_not_ready","Blood Orb crafting manager is unavailable");
    Object list=manager.getRecipeList();if(list==null||list.getClass()!=ArrayList.class||((List<?>)list).size()>262144)throw fault("Unadapted Blood Orb crafting storage");return new Object[]{manager,list};
  }
  BloodOrbCraftingRecipes(TemplateRecipeHandler handler){
    if(!supportsClass(handler.getClass().getName()))throw fault("Unknown Blood Orb crafting handler");MagicApi.version("BloodOrbCraftingRecipes","AWWayofTime");
    shaped=handler.getClass().getName().endsWith("ShapedHandler");
    try{
      source=(List<?>)registrationAnchors()[1];registrations.addAll(source);orbs.addAll(BloodOrbShapedRecipes.orbs());orbLevels=levels(orbs);
      for(int i=0;i<source.size();i++){Object raw=source.get(i);if(raw!=null&&type(shaped?SHAPED:SHAPELESS).isInstance(raw)){
        if(raw.getClass()!=type(shaped?SHAPED:SHAPELESS))throw fault("Unadapted Blood Orb recipe callback owner");indices.add(i);entries.add(new Entry(raw));
      }}
      Map<String,String[]> fields=NativeDefinitionGraph.shapes(SHAPED,"width","height","mirrored","input","output");fields.put(SHAPELESS,new String[]{"input","output"});
      fields.put("net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList",new String[]{"list"});
      definitions=new NativeDefinitionGraph(()->{verify();Object[] roots=new Object[indices.size()];for(int i=0;i<indices.size();i++)roots[i]=source.get(indices.get(i));return roots;},fields);
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  public int size(){alive();return entries.size();}
  public void verify(){alive();if(registrationAnchors()[1]!=source||source.size()!=registrations.size())throw changed();}
  public void finishVerification(){
    verify();definitions.verify();examples.verify();for(int i=0;i<source.size();i++)if(source.get(i)!=registrations.get(i))throw changed();
    List<Item> current=BloodOrbShapedRecipes.orbs();if(current.size()!=orbs.size()||!levels(current).equals(orbLevels))throw changed();for(int i=0;i<current.size();i++)if(current.get(i)!=orbs.get(i))throw changed();
  }
  public boolean capture(int index,RecipeRow row){
    alive();Entry entry=entries.get(index);JsonArray predicates=array(),cells=array();int slot=0;
    for(Object raw:entry.input){
      if(raw==null){predicates.add(JsonNull.INSTANCE);cells.add(JsonNull.INSTANCE);continue;}
      Integer tier=raw instanceof Integer?(Integer)raw:null;List<ItemStack> templates=new ArrayList<>();JsonArray selectors=array();
      if(tier!=null){for(Item orb:orbs)if(BloodOrbShapedRecipes.orbLevel(orb)>=tier)templates.add(new ItemStack(orb));}
      else if(raw.getClass()==ItemStack.class)templates.add((ItemStack)raw);
      else if(BloodOrbShapedRecipes.oreGroup(raw)){for(Object value:(List<?>)raw){if(value==null||value.getClass()!=ItemStack.class)throw fault("Invalid Blood Orb ore alternative");templates.add((ItemStack)value);}}
      else throw fault("Unadapted Blood Orb input owner: "+raw.getClass().getName());
      if(templates.size()>65536)throw new Jobs.Fault("recipe_limit","Blood Orb candidates exceed budget");
      List<RecipeRow.Ingredient> choices=new ArrayList<>();
      for(ItemStack template:templates){
        NativeStackAudit.audit(template);if(tier==null)selectors.add(object("registry",Item.itemRegistry.getNameForObject(template.getItem()),"metadata",Items.feather.getDamage(template)));
        for(ItemStack copy:examples.copies(template)){
          boolean keep=type(ROOT+"api.items.interfaces.IBloodOrb").isInstance(copy.getItem());if(keep)BloodOrbShapedRecipes.orbLevel(copy.getItem());
          choices.add(new RecipeRow.Ingredient(copy,1,keep,object("kind","wildcard","meta",tier!=null||Items.feather.getDamage(template)==32767,"nbt",true)));
        }
      }
      if(choices.isEmpty())return row.exclude(object("reason","native_input_domain_empty","nativeClass",shaped?SHAPED:SHAPELESS,"input",slot,"minimumOrb",tier));
      cells.add(value(slot));predicates.add(object("minimumOrb",tier,"alternatives",selectors));row.nativeCraftingInput(slot++,choices);
    }
    if(shaped)row.record.add("grid",object("width",entry.width,"height",entry.height,"cells",cells,"mirror",entry.mirror));
    row.itemOutput(0,entry.output,10000);
    row.record.add("process",object("kind","bloodOrbCrafting","version","1.7.52","shaped",shaped,"width",entry.width,"height",entry.height,"mirror",entry.mirror,"predicates",predicates,"orbs",orbLevels));
    row.note("bloodmagic:orbCrafting",shaped?SHAPED+"#checkMatch":SHAPELESS+"#matches",row.facts.locale().equals("zh_CN")
        ?"血之宝珠材料以最低等级匹配并保留；其他材料只比较物品和模板元数据（32767 通配），忽略 NBT／数量。有序配方可在 3×3 平移并按原生设置镜像；无序按实际格顺序扫描剩余谓词，遇到整数等级要求而物品不是宝珠或等级不足时立即失败，不会尝试后续要求。重叠材料保留此贪心及提前失败规则，不再强制排除。每个实际占用格按原生工作台扣料及容器行为处理；更早工作台注册仍可能优先。"
        :"Blood orb requirements use a minimum tier and retain the orb. Other predicates compare item and template metadata (32767 wildcard), ignoring NBT/count. Shaped recipes translate in a 3×3 grid and mirror only when enabled. Shapeless matching scans physical slots against remaining predicates; encountering an integer requirement with a non-orb or insufficient tier immediately fails instead of trying later requirements. Overlapping groups keep this greedy/early-failure law. Each occupied workbench cell uses native consumption/container behavior; earlier workbench registrations may still take priority.");
    return true;
  }
  private static JsonArray levels(List<Item> orbs){JsonArray rows=array();for(Item orb:orbs)rows.add(object("registry",Item.itemRegistry.getNameForObject(orb),"level",BloodOrbShapedRecipes.orbLevel(orb)));return rows;}
  private final class Entry{
    final Object[] input;final ItemStack output;final int width,height;final boolean mirror;
    Entry(Object raw){
      Object inputs=field(raw,"input");
      if(shaped){if(inputs==null||inputs.getClass()!=Object[].class)throw fault("Invalid shaped Blood Orb input array");input=((Object[])inputs).clone();width=(Integer)field(raw,"width");height=(Integer)field(raw,"height");mirror=(Boolean)field(raw,"mirrored");if(width<1||height<1||width>3||height>3||input.length!=width*height)throw fault("Invalid Blood Orb grid");}
      else{if(inputs==null||inputs.getClass()!=ArrayList.class)throw fault("Invalid shapeless Blood Orb input storage");input=((List<?>)inputs).toArray();width=0;height=0;mirror=false;for(Object value:input)if(value==null)throw fault("Null native shapeless Blood Orb predicate");}
      if(input.length>9)throw fault("Blood Orb predicates exceed nine slots");
      Object result=field(raw,"output");if(result==null||result.getClass()!=ItemStack.class)throw fault("Invalid Blood Orb output");NativeStackAudit.audit((ItemStack)result);output=((ItemStack)result).copy();if(output.stackSize<=0)throw fault("Blood Orb output is not a positive craft result");
    }
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Blood Orb crafting reader is closed");}
  public void close(){closed=true;if(definitions!=null)definitions.close();examples.close();source=null;registrations.clear();indices.clear();entries.clear();orbs.clear();orbLevels=null;}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Blood Orb native source or tier registry changed");}
}
