package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

/** Native last-special conversion. NBT getString coercion is observed without a string-only gate. */
final class BotaniaFloatingRecipes implements RegistryRecipes {
  private static final String HANDLER="vazkii.botania.client.integration.nei.recipe.RecipeHandlerFloatingFlowers",
      RECIPE="vazkii.botania.common.crafting.recipe.SpecialFloatingFlowerRecipe",BLOCKS="vazkii.botania.common.block.ModBlocks",
      ITEM="vazkii.botania.common.item.block.ItemBlockSpecialFlower";
  private static final String[] KEYS={"floatingFlower","specialFlower","floatingSpecialFlower"};
  private List<?> registry,catalog;
  private final List<Object> registered=new ArrayList<>();
  private final List<Integer> indices=new ArrayList<>();
  private final List<Object> selectedDefinitions=new ArrayList<>();
  private final List<ItemStack> content=new ArrayList<>();
  private final Block[] blocks=new Block[3];private final Item[] items=new Item[3];
  private final List<ItemStack> special=new ArrayList<>();
  private ItemStack floating;
  private NativeDefinitionGraph definitions;
  private BotaniaRecipeBindings bindings;
  private boolean closed;
  static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals(HANDLER);}
  BotaniaFloatingRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unknown floating-flower handler");
    try{
      bindings=new BotaniaRecipeBindings(handler);registry=CraftingManager.getInstance().getRecipeList();catalog=ItemList.items;
      if(!ItemList.loadFinished||catalog==null)throw new Jobs.Fault("handler_not_ready","Floating flower content catalogue is not ready");
      if(registry.size()>262144||catalog.size()>262144)throw fault("Floating flower source exceeds budget");registered.addAll(registry);
      for(int i=0;i<registry.size();i++){Object raw=registry.get(i);if(raw!=null&&type(RECIPE).isInstance(raw)){if(raw.getClass()!=type(RECIPE))throw fault("Unadapted floating-flower recipe owner");indices.add(i);}}
      for(int i=0;i<3;i++){blocks[i]=(Block)field(type(BLOCKS),null,KEYS[i]);items[i]=blocks[i]==null?null:Item.getItemFromBlock(blocks[i]);if(items[i]==null)throw new Jobs.Fault("handler_not_ready","Floating-flower item role is unavailable");}
      if(items[0]==items[1]||items[0]==items[2]||items[1]==items[2])throw fault("Floating-flower roles are not distinct");
      List<ItemStack> candidates=candidates();content.addAll(candidates);for(int index:indices)selectedDefinitions.add(registry.get(index));
      for(ItemStack candidate:candidates){if(candidate.getItem()==items[0]&&floating==null)floating=unit(candidate);else if(candidate.getItem()==items[1])special.add(candidate);}
      if(!indices.isEmpty()&&(floating==null||special.isEmpty()))throw new Jobs.Fault("handler_not_ready","Floating flowers lack native concrete content examples");
      for(ItemStack input:candidates)if(ItemCallbacks.container(input,false)!=null)throw fault("Floating flower container callback changed");
      definitions=new NativeDefinitionGraph(()->{verify();return new Object[]{selectedDefinitions,content};},NativeDefinitionGraph.shapes(RECIPE));
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  private List<ItemStack> candidates(){
    List<ItemStack> values=new ArrayList<>();for(Object raw:catalog){
      if(raw==null||raw.getClass()!=ItemStack.class)throw fault("Unadapted native floating flower content stack");ItemStack stack=(ItemStack)raw;
      if(stack.getItem()!=items[0]&&stack.getItem()!=items[1])continue;NativeStackAudit.audit(stack);
      if(stack.stackSize>0&&Items.feather.getDamage(stack)!=32767){values.add(stack);if(values.size()>4096)throw fault("Floating flower content examples exceed budget");}
    }return values;
  }
  public int size(){alive();return indices.size()*36;}
  public void verify(){alive();bindings.verify();if(!ItemList.loadFinished||ItemList.items!=catalog||CraftingManager.getInstance().getRecipeList()!=registry||registry.size()!=registered.size())throw changed();}
  public void finishVerification(){verify();definitions.verify();for(int i=0;i<registry.size();i++)if(registry.get(i)!=registered.get(i))throw changed();List<ItemStack> current=candidates();if(current.size()!=content.size())throw changed();for(int i=0;i<current.size();i++)if(current.get(i)!=content.get(i))throw changed();}
  public boolean capture(int index,RecipeRow row){
    alive();if(index<0||index>=size())throw fault("Floating-flower branch outside native registrations");
    int branch=index%36,total=2;while(branch>=total-1){branch-=total-1;total++;}int firstSpecial=total-branch-1;
    JsonArray specialSlots=array(),coercions=array(),samples=array();List<RecipeRow.Ingredient> finalChoices=new ArrayList<>();
    ItemStack firstOutput=null;long coercionBytes=0;
    for(ItemStack input:special){
      // The pinned getType only reads existing NBT (detectNBT + getString). Read the
      // bound original: copying a compound can change the storage order we observe.
      String text=(String)invoke(type(ITEM),null,"getType",new Class<?>[]{ItemStack.class},input);
      if(text==null||text.length()>16384)throw fault("Native flower coercion exceeds text budget");
      ItemStack output=(ItemStack)invoke(type(ITEM),null,"ofType",new Class<?>[]{ItemStack.class,String.class},new ItemStack(items[2]),text);
      NativeStackAudit.audit(output);if(firstOutput==null)firstOutput=output.copy();
      finalChoices.add(new RecipeRow.Ingredient(unit(input),1,false,object("kind","wildcard","meta",true,"nbt",true)));
      String inputId=row.facts.item(input);boolean seen=false;for(JsonElement value:coercions){JsonObject prior=value.getAsJsonObject();if(prior.get("input").getAsString().equals(inputId)){
        if(!prior.get("value").getAsString().equals(text))throw fault("Equal canonical flower tags have different native string order; no deterministic sample can be published");seen=true;break;
      }}
      if(!seen){coercionBytes+=text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;if(coercionBytes>1024*1024)throw fault("Native flower coercions exceed byte budget");coercions.add(object("input",inputId,"value",text));samples.add(object("id",row.facts.item(output),"amount","1"));}
    }
    for(int slot=0;slot<total;slot++){
      if(slot==total-1)row.itemInput(slot,finalChoices,false);
      else row.itemInput(slot,Collections.singletonList(new RecipeRow.Ingredient((slot<firstSpecial?floating:special.get(0)).copy(),1,false,object("kind","wildcard","meta",true,"nbt",true))),false);
      if(slot>=firstSpecial)specialSlots.add(value(slot));
    }
    row.record.add("process",object("kind","nativeFloatingFlowers","version","1.12.28-GTNH","special",specialSlots,"coercions",coercions));
    row.itemOutput(0,firstOutput,10000);String base=row.outputs.get(0).getAsJsonObject().get("id").getAsString();
    row.outputs.get(0).getAsJsonObject().add("change",object("input",total-1,"action",object("kind","nativeFloatingFlower","base",base),"samples",samples));
    row.note("botania:floatingType",RECIPE+"#getCraftingResult;"+ITEM+"#getType",row.facts.locale().equals("zh_CN")
        ?"任意非空槽只能是浮空花或特殊花，且至少各有一件；忽略数量／元数据／NBT。结果取实际顺序中最后一朵特殊花的 type，经原生 NBT getString 转换，生成仅含该字符串的新标签，不复制其他标签。字符串／缺失值可独立求值，非字符串保留实际原生读取值；复合标签的原生遍历顺序不从规范 JSON 猜测。列出的候选是原生物品表中的观测，规则本身不限制为字符串 type。更早工作台注册可能优先。"
        :"Every occupied cell must be a floating or special flower, with at least one of each; count, metadata and NBT do not restrict matching. The physically last special flower supplies type through native NBT getString coercion. Output gets a fresh tag containing only that string. String/absent values can be evaluated independently; non-string examples retain the observed native read, without guessing compound iteration order from canonical JSON. Candidates are observations from the native content table; the matching law does not restrict type to strings. Earlier workbench registrations may have priority.");
    return true;
  }
  private static ItemStack unit(ItemStack input){ItemStack copy=input.copy();copy.stackSize=1;return copy;}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Floating-flower reader is closed");}
  public void close(){closed=true;if(definitions!=null)definitions.close();if(bindings!=null)bindings.close();registry=null;catalog=null;registered.clear();indices.clear();selectedDefinitions.clear();content.clear();special.clear();floating=null;Arrays.fill(items,null);Arrays.fill(blocks,null);}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native floating-flower source changed");}
}
