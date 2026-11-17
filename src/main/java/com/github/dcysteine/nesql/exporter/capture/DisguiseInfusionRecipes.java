package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.potion.*;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.aspects.*;
import thaumcraft.api.crafting.InfusionRecipe;

/** Pure projection of matches -> components -> output -> aspects; never touches cachedItems. */
final class DisguiseInfusionRecipes implements AutoCloseable {
  static final String RECIPE="makeo.gadomancy.common.crafting.InfusionDisguiseArmor";
  static final String VISUAL="makeo.gadomancy.common.crafting.InfusionVisualDisguiseArmor";
  static final class Branch{final DisguiseInfusionRecipes reader;final int position;Branch(DisguiseInfusionRecipes reader,int position){this.reader=reader;this.position=position;}}
  private InfusionRecipe recipe;
  private ItemStack[] components;
  private AspectList aspects;
  private final NativeItemExamples examples;
  private final InfusionLegacyInputs.Context context;
  private NativeDefinitionGraph graph;
  private final List<Item> armorItems=new ArrayList<>();
  private final Map<Integer,List<ItemStack>> armors=new TreeMap<>();
  private final List<ItemStack> potions=new ArrayList<>();
  private final Map<String,ItemStack> potionInputs=new TreeMap<>();
  private final Map<String,Boolean> potionResults=new TreeMap<>();
  private final Map<String,ItemStack> observed=new TreeMap<>();
  private final Map<String,JsonObject> observations=new TreeMap<>();
  private final List<GtItemListBinding> containers=new ArrayList<>();
  private final List<ItemStack> returnInputs=new ArrayList<>();
  private final List<String> returnSignatures=new ArrayList<>();
  private String research;
  private long observationBytes;
  private boolean closed;

  DisguiseInfusionRecipes(InfusionRecipe source,NativeItemExamples examples,InfusionLegacyInputs.Context context){
    recipe=source;this.examples=examples;this.context=context;
    if(source.getClass()!=type(RECIPE))throw fault("Unknown disguise infusion owner");
    try{
      MagicApi.version("DisguiseInfusionRecipes","gadomancy");
      Object raw=field(type(RECIPE),null,"COMPONENTS");
      if(raw==null||raw.getClass()!=ItemStack[].class||((ItemStack[])raw).length!=4)throw fault("Disguise fixed component source changed");
      components=(ItemStack[])raw;aspects=(AspectList)field(type(RECIPE),null,"ASPECTS");
      if(aspects==null||aspects.getClass()!=AspectList.class)throw fault("Unadapted disguise aspect source");
      Object prefix=field(type("makeo.gadomancy.common.research.SimpleResearchItem"),null,"PREFIX");
      if(!(prefix instanceof String))throw fault("Disguise research prefix is unavailable");research=(String)prefix+"ARMORDISGUISE";
      for(Object item:Item.itemRegistry)if(position((Item)item)!=0)armorItems.add((Item)item);
      armorItems.sort(Comparator.comparing(Item.itemRegistry::getNameForObject));
      for(Item item:armorItems)for(ItemStack stack:examples.permutations(new ItemStack(item,1,32767))){
        NativeStackAudit.audit(stack);stack.stackSize=1;if(!disguised(stack))armors.computeIfAbsent(position(item),p->new ArrayList<>()).add(stack);
      }
      for(ItemStack stack:examples.permutations(new ItemStack(Items.potionitem,1,32767))){
        stack.stackSize=1;if(!disguised(stack)&&Boolean.TRUE.equals(invisible(stack)))potions.add(stack);
      }
      for(List<ItemStack> stacks:armors.values())unique(stacks);unique(potions);
      if(armors.isEmpty())throw fault("Disguise infusion has no native armor specimens");
      Map<String,String[]> shapes=new HashMap<>();shapes.put(RECIPE,new String[0]);
      shapes.put(AspectList.class.getName(),new String[]{"aspects"});shapes.put(Aspect.class.getName(),new String[]{"tag"});
      // Cache state and cachedOutput are deliberately outside this definition graph.
      graph=new NativeDefinitionGraph(this::roots,shapes);
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  List<Branch> branches(){alive();List<Branch> result=new ArrayList<>();for(int position:armors.keySet())if(!armors.get(position).isEmpty())result.add(new Branch(this,position));return result;}
  private Object[] roots(){return new Object[]{recipe,field(type(RECIPE),null,"COMPONENTS"),field(type(RECIPE),null,"ASPECTS"),
      field(type("makeo.gadomancy.common.research.SimpleResearchItem"),null,"PREFIX"),Item.getItemFromBlock(Blocks.pumpkin),Items.skull,Items.potionitem};}
  private static int position(Item item){
    if(item==Item.getItemFromBlock(Blocks.pumpkin)||item==Items.skull)return 4;
    if(item instanceof ItemArmor){int type=((ItemArmor)item).armorType;return type>=0&&type<=3?4-type:0;}return 0;
  }
  private static boolean disguised(ItemStack stack){return stack.hasTagCompound()&&stack.getTagCompound().getTag("gadomancy") instanceof NBTTagCompound
      &&stack.getTagCompound().getCompoundTag("gadomancy").hasKey("disguise");}
  private static void unique(List<ItemStack> stacks){
    Map<String,ItemStack> values=new TreeMap<>();
    for(ItemStack stack:stacks){String key=CanonicalJson.digest(RecipeRow.stackEvidence(stack));ItemStack prior=values.putIfAbsent(key,stack);
      if(prior!=null&&(!strings(prior).equals(strings(stack))||!strings(prior.copy()).equals(strings(stack.copy()))
          ||!strings(prior.copy().copy()).equals(strings(stack.copy().copy()))))throw fault("Canonical disguise specimen has different native tag order");}
    stacks.clear();stacks.addAll(values.values());
  }
  private Boolean invisible(ItemStack stack){
    if(stack.getItem()!=Items.potionitem)return null;
    String key=CanonicalJson.digest(RecipeRow.stackEvidence(stack));
    if(!potionResults.containsKey(key)){
      if(potionResults.size()>=65536)throw fault("Disguise potion observation budget exceeded");
      potionInputs.put(key,stack);potionResults.put(key,potionInvisibility(stack));
    }
    return potionResults.get(key);
  }
  /** ItemPotion.getEffects would write its global effectCache on a miss. Compute the same owned list instead. */
  private static Boolean potionInvisibility(ItemStack stack){
    NativeStackAudit.audit(stack);if(stack.getItem()!=Items.potionitem)return null;
    if(Items.potionitem.getClass()!=ItemPotion.class)throw fault("Disguise potion item needs a dedicated callback adapter");
    List<?> effects;
    if(stack.hasTagCompound()&&stack.getTagCompound().hasKey("CustomPotionEffects",9)){
      NBTTagList tags=stack.getTagCompound().getTagList("CustomPotionEffects",10);if(tags.tagCount()>4096)throw fault("Disguise potion effects exceed budget");
      List<PotionEffect> owned=new ArrayList<>();for(int i=0;i<tags.tagCount();i++){PotionEffect effect=PotionEffect.readCustomPotionEffectFromNBT(tags.getCompoundTagAt(i));if(effect!=null)owned.add(effect);}effects=owned;
    }else{
      Object raw=field(ItemPotion.class,Items.potionitem,"effectCache");if(raw==null||raw.getClass()!=HashMap.class)throw fault("Unadapted native potion cache storage");
      effects=(List<?>)((Map<?,?>)raw).get(Items.feather.getDamage(stack));
      if(effects==null){
        // Only pure native getters used transitively by PotionHelper are admitted.
        for(Potion potion:Potion.potionTypes)if(potion!=null)for(String method:Arrays.asList("isUsable","getId","isInstant","getEffectiveness")){
          String owner=potionMethodOwner(potion,method).getName();
          if(!owner.equals(Potion.class.getName())&&!(method.equals("isInstant")&&owner.equals("net.minecraft.potion.PotionHealth")))throw fault("Unadapted disguise potion callback: "+owner+"#"+method);
        }
        effects=PotionHelper.getPotionEffects(Items.feather.getDamage(stack),false);
      }
    }
    if(effects==null)return null;
    if(effects.getClass()!=ArrayList.class||effects.size()>4096)throw fault("Unadapted disguise potion effect list");
    if(potionMethodOwner(Potion.invisibility,"getId")!=Potion.class)throw fault("Disguise invisibility potion overrides its id getter");
    for(Object raw:effects)if(raw instanceof PotionEffect){
      if(raw.getClass()!=PotionEffect.class)throw fault("Unadapted disguise PotionEffect callback");
      if(((PotionEffect)raw).getPotionID()==Potion.invisibility.getId())return true;
    }
    return false;
  }
  private static Class<?> potionMethodOwner(Potion potion,String mcp){
    String srg;
    switch(mcp){case "isUsable":srg="func_76395_i";break;case "getId":srg="func_76396_c";break;
      case "isInstant":srg="func_76403_b";break;case "getEffectiveness":srg="func_76388_g";break;default:throw new AssertionError(mcp);}
    String name=cpw.mods.fml.relauncher.ReflectionHelper.findMethod(Potion.class,null,new String[]{mcp,srg}).getName();
    try{return potion.getClass().getMethod(name).getDeclaringClass();}catch(NoSuchMethodException error){throw fault("Missing native potion method: "+mcp);}
  }
  private static JsonObject strings(ItemStack stack){JsonObject tags=object();if(stack.hasTagCompound())for(Object raw:stack.getTagCompound().func_150296_c()){String key=(String)raw;tags.addProperty(key,stack.getTagCompound().getTag(key).toString());}return tags;}
  private static JsonArray ores(ItemStack stack){JsonArray values=array();for(int id:OreDictionary.getOreIDs(stack))values.add(value(OreDictionary.getOreName(id)));return values;}
  private static int limit(ItemStack stack){
    Item item=stack.getItem();if(ItemCallbacks.method(item,"getItemStackLimit","getItemStackLimit",ItemStack.class).getDeclaringClass()!=Item.class
        ||ItemCallbacks.method(item,"getItemStackLimit","func_77639_j").getDeclaringClass()!=Item.class)throw fault("Disguise stack limit needs a callback adapter: "+item.getClass().getName());
    return stack.getMaxStackSize();
  }
  private JsonObject comparison(ItemStack stack){
    NativeStackAudit.audit(stack);JsonArray groups=array();for(String name:context.bind(stack).groups)groups.add(value(name));
    return object("item",InfusionLegacyInputs.template(stack),"amount",stack.stackSize,"limit",limit(stack),"ores",ores(stack),"defaultOres",groups,"tags",strings(stack));
  }
  private JsonObject state(String id,ItemStack stack){
    NBTTagCompound serialized=new NBTTagCompound();stack.copy().writeToNBT(serialized);
    return object("id",id,"comparison",comparison(stack),"onceTags",strings(stack.copy()),"twiceTags",strings(stack.copy().copy()),
        "armorPosition",position(stack.getItem()),"potion",stack.getItem()==Items.potionitem,"invisible",invisible(stack),"serialized",TypedNbt.encode(serialized));
  }
  private void observe(Facts facts,ItemStack stack){
    String id=facts.item(stack);JsonObject state=state(id,stack),previous=observations.get(id);
    if(previous!=null){if(!previous.equals(state))throw fault("Canonical disguise item has inconsistent native observations");return;}
    observationBytes+=CanonicalJson.bytes(state).length;if(observations.size()>=65536||observationBytes>32L*1024*1024)throw fault("Disguise observations exceed budget");
    observed.put(id,stack);observations.put(id,state);
  }
  private List<ItemStack> select(ItemStack center,List<ItemStack> outer){
    if(disguised(center)||position(center.getItem())==0||outer.size()!=5)return null;
    List<ItemStack> remaining=new ArrayList<>();for(ItemStack offered:outer)remaining.add(offered.copy());List<ItemStack> selected=new ArrayList<>();
    for(ItemStack required:components){int found=-1;for(int i=0;i<remaining.size();i++)if(InfusionRecipe.areItemStacksEqual(required,remaining.get(i),true)){found=i;break;}
      if(found<0)return null;selected.add(remaining.remove(found));}
    ItemStack disguise=remaining.get(0);if(disguised(disguise))return null;
    if(disguise.getItem()==Items.potionitem){Boolean result=invisible(disguise);if(result==null)throw fault("Native disguise potion has null effects");if(!result&&position(disguise.getItem())!=position(center.getItem()))return null;}
    else if(position(disguise.getItem())!=position(center.getItem()))return null;
    selected.add(0,disguise);List<ItemStack> cycle=new ArrayList<>(outer);
    for(ItemStack ingredient:selected){ItemStack required=ingredient.copy();int found=-1;
      for(int i=0;i<cycle.size();i++)if(InfusionRecipe.areItemStacksEqual(cycle.get(i),required,true)){found=i;break;}if(found<0)return null;cycle.remove(found);}
    return selected;
  }
  private static ItemStack output(ItemStack center,ItemStack disguise){
    ItemStack result=center.copy().copy();if(!result.hasTagCompound())result.setTagCompound(new NBTTagCompound());NBTTagCompound root=result.getTagCompound(),data=root.getCompoundTag("gadomancy");
    if(!(root.getTag("gadomancy") instanceof NBTTagCompound))root.setTag("gadomancy",data);
    if(disguise.getItem()==Items.potionitem)data.setBoolean("disguise",true);else data.setTag("disguise",disguise.writeToNBT(new NBTTagCompound()));return result;
  }
  boolean capture(int position,RecipeRow row,InfusionInheritance inheritance){
    verify();inheritance.bindNativeOutput(recipe);List<ItemStack> centers=armors.get(position),disguises=new ArrayList<>(centers);disguises.addAll(potions);
    List<ItemStack> fixed=new ArrayList<>();for(ItemStack raw:components){ItemStack[] choices=examples.copies(raw);if(choices.length==0)throw fault("Disguise fixed input has no specimen");ItemStack specimen=choices[0];specimen.stackSize=1;fixed.add(specimen);}
    List<int[]> valid=new ArrayList<>();Set<Integer> usedCenter=new TreeSet<>(),usedDisguise=new TreeSet<>();
    for(int c=0;c<centers.size();c++)for(int d=0;d<disguises.size();d++){
      Jobs.checkpoint();List<ItemStack> outer=new ArrayList<>();outer.add(disguises.get(d));outer.addAll(fixed);
      if(select(centers.get(c),outer)!=null){if(valid.size()>=65536)throw fault("Disguise correlated specimen budget exceeded");valid.add(new int[]{c,d});usedCenter.add(c);usedDisguise.add(d);}
    }
    if(valid.isEmpty())throw fault("No admitted disguise specimen tuple; this is not proof of an unreachable recipe");
    List<List<ItemStack>> slots=new ArrayList<>();slots.add(new ArrayList<>());slots.add(new ArrayList<>());
    Map<Integer,Integer> centralIndex=new HashMap<>(),disguiseIndex=new HashMap<>();
    for(int c:usedCenter){centralIndex.put(c,slots.get(0).size());slots.get(0).add(centers.get(c));}for(int d:usedDisguise){disguiseIndex.put(d,slots.get(1).size());slots.get(1).add(disguises.get(d));}
    for(ItemStack item:fixed)slots.add(Collections.singletonList(item));
    Map<String,JsonObject> rowStates=new TreeMap<>();
    for(int slot=0;slot<slots.size();slot++){
      List<RecipeRow.Ingredient> choices=new ArrayList<>();for(ItemStack stack:slots.get(slot)){observe(row.facts,stack);String id=row.facts.item(stack);rowStates.put(id,observations.get(id));choices.add(new RecipeRow.Ingredient(stack.copy(),1,false,object("kind","exact")));}
      row.itemInput(slot,choices,false);JsonArray captured=row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices");
      for(int i=0;i<captured.size();i++){JsonObject choice=captured.get(i).getAsJsonObject();choice.add("consume",object("kind","stack"));if(slot==0)continue;
        ItemStack specimen=slots.get(slot).get(i),returned=ItemCallbacks.pedestalContainer(specimen,containers);returnInputs.add(specimen);returnSignatures.add(CanonicalJson.digest(returned==null?JsonNull.INSTANCE:RecipeRow.stackEvidence(returned)));
        if(returned!=null){if(returned.stackSize<=0)throw fault("Invalid disguise pedestal container");if(returned.stackSize>context.pedestalLimit())returned.stackSize=context.pedestalLimit();choice.getAsJsonArray("returns").add(object("kind","item","id",row.facts.item(returned),"amount",Integer.toString(returned.stackSize)));}
      }
    }
    JsonArray samples=array(),bindings=array();ItemStack first=null;
    for(int[] pair:valid){List<ItemStack> outer=new ArrayList<>();outer.add(disguises.get(pair[1]));outer.addAll(fixed);List<ItemStack> selected=select(centers.get(pair[0]),outer);
      if(selected==null)throw changed();ItemStack result=output(centers.get(pair[0]),selected.get(0));if(first==null)first=result;
      samples.add(object("id",row.facts.item(result),"amount","1"));bindings.add(array(centralIndex.get(pair[0]),disguiseIndex.get(pair[1]),0,0,0,0));}
    JsonArray requirements=array(),states=array();for(ItemStack required:components)requirements.add(comparison(required));for(JsonObject state:rowStates.values())states.add(state);
    row.record.add("process",object("kind","disguiseInfusion","nativeRecipe",RECIPE,"mode",context.mode(),"components",requirements,"states",states,"pedestalLimit",context.pedestalLimit()));
    row.record.add("magic",object("kind","infusion","aspects",Aspects.amounts(aspects),"research",array(Magic.study(research)),"central",0,"instability",-20,"payment",null,"creative",false));
    JsonObject change=object("input",0,"action",object("kind","disguiseInfusion"),"samples",samples,"bindings",bindings);
    if(CanonicalJson.bytes(change).length>16L*1024*1024)throw fault("Disguise result samples exceed byte budget");
    row.itemOutput(0,first,10000);row.outputs.get(0).getAsJsonObject().add("change",change);
    row.note("gadomancy:disguise",RECIPE+"#matches/getComponents/getRecipeOutput/getAspects",row.facts.locale().equals("zh_CN")
        ?"中心和伪装候选按原生护甲部位关联。先按定义顺序取走四件材料，再使用剩余一件；原生方向与后续基座比较分别保留。展示单件样本，不穷举全部附加标签。研究、注册优先级和祭坛运行仍是条件。"
        :"Center and disguise choices are correlated by the native armor position. Four materials are greedily removed before the residual disguise is selected. Startup and later pedestal comparisons retain their distinct direction. Choices are one-item specimens, not every possible tag state. Research, registry priority and altar execution remain conditions.");
    return true;
  }
  void verify(){alive();context.verify();graph.verifyRoots();}
  void finishVerification(){verify();graph.verify();List<Item> current=new ArrayList<>();for(Object item:Item.itemRegistry)if(position((Item)item)!=0)current.add((Item)item);current.sort(Comparator.comparing(Item.itemRegistry::getNameForObject));
    if(!current.equals(armorItems))throw changed();for(Map.Entry<Integer,List<ItemStack>> group:armors.entrySet())for(ItemStack stack:group.getValue())if(position(stack.getItem())!=group.getKey())throw changed();
    for(Map.Entry<String,ItemStack> entry:potionInputs.entrySet()){Jobs.checkpoint();if(!Objects.equals(potionResults.get(entry.getKey()),potionInvisibility(entry.getValue())))throw changed();}
    for(Map.Entry<String,ItemStack> entry:observed.entrySet()){Jobs.checkpoint();if(!observations.get(entry.getKey()).equals(state(entry.getKey(),entry.getValue())))throw changed();}
    for(GtItemListBinding binding:containers)binding.verify();for(int i=0;i<returnInputs.size();i++){List<GtItemListBinding> bindings=new ArrayList<>();ItemStack result=ItemCallbacks.pedestalContainer(returnInputs.get(i),bindings);if(!returnSignatures.get(i).equals(CanonicalJson.digest(result==null?JsonNull.INSTANCE:RecipeRow.stackEvidence(result))))throw changed();for(GtItemListBinding binding:bindings)binding.verify();}
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Disguise infusion reader is closed");}
  public void close(){closed=true;if(graph!=null)graph.close();recipe=null;components=null;aspects=null;armorItems.clear();armors.clear();potions.clear();potionInputs.clear();potionResults.clear();observed.clear();observations.clear();containers.clear();returnInputs.clear();returnSignatures.clear();}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native disguise infusion source changed");}
}
