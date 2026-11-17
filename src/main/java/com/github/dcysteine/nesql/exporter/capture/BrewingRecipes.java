package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import codechicken.nei.ItemStackSet;
import codechicken.nei.recipe.BrewingRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.potion.PotionHelper;

/** Reachable native potion transforms. Never initializes or reads recipe GUI caches. */
final class BrewingRecipes implements RegistryRecipes {
  private final List<Entry> entries=new ArrayList<>();
  private final NativeItemExamples examples=new NativeItemExamples();
  private NativeDefinitionGraph bindings;
  private String signature;
  private boolean closed;
  static boolean supports(ICraftingHandler handler){return handler!=null&&supportsClass(handler.getClass().getName());}
  static boolean supportsClass(String name){return BrewingRecipeHandler.class.getName().equals(name);}
  static Object[] registrationAnchors(){
    if(!ItemList.loadFinished)throw new Jobs.Fault("handler_not_ready","Native brewing ingredients require completed item discovery");
    Object ingredients=field(BrewingRecipeHandler.class,null,"ingredients");
    if(ingredients==null||ingredients.getClass()!=ItemStackSet.class)throw fault("Unadapted native brewing ingredient storage");
    return new Object[]{ingredients,field(PotionHelper.class,null,"potionRequirements"),field(PotionHelper.class,null,"potionAmplifiers"),Item.itemRegistry.getObject("etfuturum:dragon_breath"),Item.itemRegistry.getObject("etfuturum:lingering_potion"),BrewingItemCallbacks.configuration()};
  }
  BrewingRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unknown native brewing handler");
    try{
      Map<String,String[]> shapes=NativeDefinitionGraph.shapes("codechicken.nei.ItemStackSet","itemMap","size");
      shapes.put("codechicken.nei.ItemStackMap$DetailMap",new String[]{"wildcard","damageMap","tagMap","metaMap","size","hasWildcard"});
      shapes.put("codechicken.nei.ItemStackMap$StackMetaKey",new String[]{"damage","tag"});
      bindings=new NativeDefinitionGraph(BrewingRecipes::registrationAnchors,shapes);
      JsonArray facts=read(entries);signature=CanonicalJson.digest(facts);
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  private JsonArray read(List<Entry> output){
    Object[] roots=registrationAnchors();List<ItemStack> nativeIngredients=((ItemStackSet)roots[0]).values();
    if(nativeIngredients.size()>4096)throw fault("Native brewing ingredient budget exceeded");
    List<Ingredient> ingredients=new ArrayList<>();
    for(ItemStack raw:nativeIngredients)for(ItemStack stack:examples.copies(raw)){
      if(stack.getItem()==roots[3])continue;
      String effect=BrewingItemCallbacks.effect(stack);
      if(effect==null)continue;if(effect.length()>16384)throw fault("Native potion expression exceeds budget");
      stack.stackSize=1;ingredients.add(new Ingredient(stack.copy(),effect));
    }
    ingredients.sort(Comparator.comparing(i->CanonicalJson.digest(RecipeRow.stackEvidence(i.stack))));
    TreeSet<Integer> known=new TreeSet<>(),pending=new TreeSet<>();known.add(0);pending.add(0);long work=0;
    Set<String> dedupe=new HashSet<>();
    while(!pending.isEmpty()){
      int base=pending.pollFirst();
      for(Ingredient ingredient:ingredients){Jobs.checkpoint();if(++work>8388608)throw fault("Native potion graph exceeds work budget");int result=PotionHelper.applyIngredient(base,ingredient.effect);
        List<PotionEffect> before=effects(base),after=effects(result);
        boolean changes=!ItemPotion.isSplash(base)&&ItemPotion.isSplash(result)
            ||(base<=0||before!=after)&&(before==null||!before.equals(after)&&after!=null)&&base!=result;
        if(!changes)continue;
        add(output,dedupe,new Entry(ingredient.stack,new ItemStack(Items.potionitem,1,base),new ItemStack(Items.potionitem,1,result),ingredient.effect,false));
        if(known.add(result)){if(known.size()>65536)throw fault("Native potion state budget exceeded");pending.add(result);}
      }
    }
    if(roots[3]!=null&&roots[4]!=null){
      Item breath=(Item)roots[3],lingering=(Item)roots[4];
      if(!breath.getClass().getName().startsWith("ganymedes01.etfuturum."))throw fault("Unadapted dragon breath native role");
      for(int base:known)if(ItemPotion.isSplash(base))add(output,dedupe,new Entry(new ItemStack(breath),new ItemStack(Items.potionitem,1,base),new ItemStack(lingering,1,base),"",true));
    }
    JsonArray facts=array();for(Entry entry:output)facts.add(entry.json());if(CanonicalJson.bytes(facts).length>32*1024*1024)throw fault("Native brewing graph exceeds byte budget");return facts;
  }
  private static List<PotionEffect> effects(int metadata){
    List<PotionEffect> values=PotionHelper.getPotionEffects(metadata,false);if(values==null)return null;
    if(values.getClass()!=ArrayList.class||values.size()>256)throw fault("Unadapted native potion effect storage");for(PotionEffect value:values)if(value==null||value.getClass()!=PotionEffect.class)throw fault("Unadapted native potion effect equality");return values;
  }
  private static void add(List<Entry> output,Set<String> seen,Entry entry){if(seen.add(CanonicalJson.digest(entry.json())))output.add(entry);if(output.size()>262144)throw fault("Native brewing operation budget exceeded");}
  public int size(){alive();return entries.size();}
  public void verify(){alive();bindings.verifyRoots();}
  public void finishVerification(){verify();bindings.verify();List<Entry> actual=new ArrayList<>();if(!signature.equals(CanonicalJson.digest(read(actual))))throw changed();examples.verify();}
  public boolean capture(int index,RecipeRow row){
    alive();Entry entry=entries.get(index);
    row.itemInput(0,Collections.singletonList(new RecipeRow.Ingredient(entry.ingredient.copy(),1,false,object("kind","wildcard","meta",false,"nbt",true))),false);
    ItemStack returned=ItemCallbacks.container(entry.ingredient.copy(),false);
    if(returned!=null)row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonArray("returns").add(object("kind","item","id",row.facts.item(returned),"amount",Integer.toString(returned.stackSize)));
    row.itemInput(1,Collections.singletonList(new RecipeRow.Ingredient(entry.input.copy(),1,false,object("kind","wildcard","meta",false,"nbt",true))),false);
    row.itemOutput(0,entry.output.copy(),10000,object("kind","potential","stat","minecraft:brewingInventory","condition","native_brewing_inventory_power_and_event_gates","nominal","1"));
    row.record.add("process",object("kind","nativeBrewing","version","1.7.10","ingredient",0,"potion",1,"inputMetadata",entry.input.getItemDamage(),"outputMetadata",entry.output.getItemDamage(),
        "resultRegistry",Item.itemRegistry.getNameForObject(entry.output.getItem()),"expression",entry.expression,"lingering",entry.lingering,"preserveNbt",!entry.lingering,"workTicks",400,"maximumPotions",3));
    row.note("minecraft:brewing",entry.lingering?"ganymedes01.etfuturum.tileentities.TileEntityNewBrewingStand#canBrew,brewPotions":"net.minecraft.tileentity.TileEntityBrewingStand#canBrew,brewPotions",
        row.facts.locale().equals("zh_CN")?"正常酿造一轮共同取用一份材料，处理最多三个药水槽；此行只展示一个槽的变换，不代表每瓶都消耗一份材料。原生工作计数为 400，输入变化、条件或事件可中断／取消。普通药水在原槽修改元数据，保留原 NBT；龙息分支仅适用于带燃料的新酿造台，原生创建滞留药水并保留带喷溅位的元数据、清除原 NBT。不能按 NEI 预览清除喷溅位来改写实机结果。":"One brewing cycle shares one ingredient across up to three potion slots; this row shows one slot and is not one ingredient per bottle. The native work counter is 400; inventory changes, conditions or events can interrupt or cancel it. Ordinary brewing changes metadata in place and preserves input NBT. The dragon-breath branch requires the fuelled new brewing stand; native code creates the lingering item with the splash metadata bit retained and fresh NBT. The NEI preview's cleared splash bit must not replace the native result.");
    return true;
  }
  private static final class Ingredient{final ItemStack stack;final String effect;Ingredient(ItemStack stack,String effect){this.stack=stack;this.effect=effect;}}
  private static final class Entry{final ItemStack ingredient,input,output;final String expression;final boolean lingering;Entry(ItemStack ingredient,ItemStack input,ItemStack output,String expression,boolean lingering){this.ingredient=ingredient.copy();this.input=input;this.output=output;this.expression=expression;this.lingering=lingering;}JsonObject json(){return object("ingredient",RecipeRow.stackEvidence(ingredient),"input",RecipeRow.stackEvidence(input),"output",RecipeRow.stackEvidence(output),"expression",expression,"lingering",lingering);}}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Native brewing reader is closed");}
  public void close(){closed=true;if(bindings!=null)bindings.close();examples.close();entries.clear();signature=null;}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native potion graph changed");}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
