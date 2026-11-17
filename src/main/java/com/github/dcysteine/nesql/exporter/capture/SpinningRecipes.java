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

/** Pinned list-removal matching and five physical-slot consumption, without GUI recipe caches. */
final class SpinningRecipes implements RegistryRecipes {
  private static final String HANDLER="witchinggadgets.client.nei.NEISpinningWheelHandler",
      RECIPE="witchinggadgets.common.util.recipe.SpinningRecipe";
  private List<?> source;
  private final List<Entry> entries=new ArrayList<>();
  private final NativeItemExamples examples=new NativeItemExamples();
  private NativeDefinitionGraph graph;
  private JsonArray definitions;
  private boolean closed;

  static boolean supportsClass(String name){return HANDLER.equals(name);}
  static boolean supports(ICraftingHandler handler){return supportsClass(handler.getClass().getName());}
  static Object[] registrationAnchors(){
    MagicApi.version("SpinningRecipes","WitchingGadgets");
    Object raw=field(type(RECIPE),null,"recipeList");
    if(raw==null)throw new Jobs.Fault("handler_not_ready","Witching Gadgets spinning registry is unavailable");
    if(raw.getClass()!=ArrayList.class&&raw.getClass()!=LinkedList.class)throw fault("Unadapted spinning registry storage");
    if(((List<?>)raw).size()>4096)throw new Jobs.Fault("recipe_limit","Spinning registration budget exceeded");
    return new Object[]{type(HANDLER),raw};
  }

  SpinningRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unknown Witching Gadgets spinning handler");
    try{
      source=(List<?>)registrationAnchors()[1];definitions=array();int total=0;
      for(Object raw:source){
        Jobs.checkpoint();Entry entry=new Entry(raw);entries.add(entry);definitions.add(entry.definition);
        for(Need need:entry.inputs)if((total+=need.templates.size())>262144)throw new Jobs.Fault("recipe_limit","Spinning cumulative predicates exceed budget");
      }
      Map<String,String[]> shapes=NativeDefinitionGraph.shapes(RECIPE,"input","output");
      shapes.put("net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList",new String[]{"list"});
      graph=new NativeDefinitionGraph(()->new Object[]{registrationAnchors()[1]},shapes);
    }catch(RuntimeException|Error failure){close();throw failure;}
  }

  public int size(){alive();return entries.size();}
  public void verify(){alive();if(registrationAnchors()[1]!=source)throw changed();graph.verifyRoots();}
  public void finishVerification(){verify();graph.verify();examples.verify();}
  public boolean capture(int index,RecipeRow row){
    alive();Entry entry=entries.get(index);
    if(entry.absent||entry.inputs.size()>5||entry.inputs.stream().anyMatch(n->n.never||n.templates.isEmpty()))
      return row.exclude(object("native",RECIPE+"#inputsMatch","reason","native_requirement_cannot_be_removed_in_five_slots","index",index,"definition",entry.definition));
    if(entry.output==null||entry.output.stackSize<=0)throw new Jobs.Fault("native_error","Selected spinning rule has no positive native output");
    for(int slot=0;slot<entry.inputs.size();slot++){
      List<RecipeRow.Ingredient> choices=new ArrayList<>();
      for(ItemStack raw:entry.inputs.get(slot).templates){
        ItemStack template=raw.copy();template.stackSize=1;template.setTagCompound(null);
        boolean wildcard=net.minecraft.init.Items.feather.getDamage(template)==32767;
        for(ItemStack shown:examples.copies(template))choices.add(new RecipeRow.Ingredient(shown,1,false,object("kind","wildcard","meta",wildcard,"nbt",true)));
      }
      row.itemInput(slot,choices,false);
    }
    row.itemOutput(0,entry.output.copy(),10000);
    row.record.add("process",object("kind","witchingSpinning","version","1.7.25-GTNH","selected",index,"definitions",definitions));
    row.note("witchinggadgets:spinning",RECIPE+"#inputsMatch;witchinggadgets.common.blocks.tiles.TileEntitySpinningWheel#updateEntity",
        row.facts.locale().equals("zh_CN")
          ?"按五个物理输入槽顺序压紧非空物品，选择首个匹配注册。数量与 NBT 不参与匹配，模板 32767 是矿辞通配值。普通需求匹配后退出当前扫描；列表需求匹配并移除后继续扫描其他需求，因此一个物品可能移除多个列表需求。匹配成功标志不在下一个物品前重置。完成时所有非空输入槽各减一件，包含未匹配的物品，不返还容器。默认从 0 进度需 121 次连续有效工作调用，中断会清零；实际机器进度与 maxProgress 仍是外部状态。图示是空产物槽的结果原型；同物品／元数据合并忽略 NBT，保留已有标签并检查双方原生堆叠上限。候选组合可能被更早注册优先匹配，不保证当前展示产物。"
          :"Pack occupied entries in the order of the five physical input slots and select the first matching registration. Count and NBT do not restrict matching; template metadata 32767 is an ore wildcard. A direct stack match removes one requirement and breaks; a list match removes that requirement and keeps scanning, so one offer may remove several list requirements. The matched flag stays set for later offers. Completion consumes one item from every occupied input slot, including unmatched offers, without containers. Default progress from zero needs 121 consecutive valid work calls; interruption resets it, while the actual progress and maxProgress remain external state. The picture is an empty-output-slot prototype. Existing equal item/metadata stock merges without comparing NBT, keeps its tags and checks both native stack limits. An earlier registration may claim a candidate combination; the shown result is conditional.");
    return true;
  }

  private static final class Need {
    final boolean list,never;
    final List<ItemStack> templates=new ArrayList<>();
    final JsonObject definition;
    Need(Object raw){
      list=raw instanceof ArrayList;never=!(raw instanceof ItemStack)&&!list;
      if(never){if(raw!=null&&raw.getClass()!=LinkedList.class)throw fault("Unadapted spinning ignored-input owner");definition=object("kind","never");return;}
      List<?> values;
      if(list){
        Object backing=raw;
        if(raw.getClass().getName().equals("net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList"))backing=field(raw,"list");
        else if(raw.getClass()!=ArrayList.class)throw fault("Unadapted spinning ingredient-list callback");
        if(backing==null||backing.getClass()!=ArrayList.class)throw fault("Unadapted spinning ingredient-list backing");
        values=(List<?>)backing;
      }else values=Collections.singletonList(raw);
      if(values.size()>65536)throw new Jobs.Fault("recipe_limit","Spinning alternatives exceed budget");
      JsonArray selectors=array();
      for(Object value:values){
        Jobs.checkpoint();if(!(value instanceof ItemStack))throw fault("Invalid native spinning list member");
        ItemStack item=(ItemStack)value;NativeStackAudit.audit(item);String registry=Item.itemRegistry.getNameForObject(item.getItem());
        int metadata=net.minecraft.init.Items.feather.getDamage(item);
        if(registry==null||metadata<0)throw fault("Unregistered or negative native spinning selector");
        templates.add(item.copy());selectors.add(object("registry",registry,"metadata",metadata));
      }
      definition=list?object("kind","list","templates",selectors):object("kind","stack","template",selectors.get(0));
    }
  }
  private static final class Entry {
    final List<Need> inputs=new ArrayList<>();final ItemStack output;final boolean absent;final JsonObject definition;
    Entry(Object recipe){
      if(recipe==null||recipe.getClass()!=type(RECIPE))throw fault("Unadapted native spinning recipe callback owner");
      Object raw=field(recipe,"input");absent=raw==null;
      if(raw!=null){
        if(!(raw instanceof Object[])||((Object[])raw).length>65536)throw fault("Invalid native spinning requirement array");
        for(Object value:(Object[])raw)inputs.add(new Need(value));
      }
      Object result=field(recipe,"output");
      if(result!=null&&!(result instanceof ItemStack))throw fault("Invalid native spinning result owner");
      if(result!=null)NativeStackAudit.audit((ItemStack)result);
      output=result==null?null:((ItemStack)result).copy();
      JsonArray values=array();for(Need need:inputs)values.add(need.definition);
      definition=object("inputs",absent?null:values);
    }
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Witching Gadgets spinning reader is closed");}
  public void close(){if(closed)return;closed=true;if(graph!=null)graph.close();graph=null;source=null;definitions=null;entries.clear();examples.close();}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Witching Gadgets spinning source changed");}
}
