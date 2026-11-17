package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.*;

/** WitcheryExtras' actual tile Mixin, not OvenRecipe.isInputMatch or its NEI projection. */
final class WitcheryOvenRecipes implements RegistryRecipes {
  private static final String HANDLER="alkalus.main.nei.NEI_Handler_Oven";
  private static final String SOURCE="alkalus.main.core.crafting.OvenRecipes";
  private static final String RECIPE=SOURCE+"$OvenRecipe";
  private static final String WITCHERY="com.emoniph.witchery.Witchery";
  private static final String CONFIG="com.emoniph.witchery.util.Config";
  private static final String SUBITEM="com.emoniph.witchery.item.ItemGeneral$SubItem";
  private static final String[] ROLES={"itemEmptyClayJar","itemExhaleOfTheHornedOne","itemHintOfRebirth",
      "itemBreathOfTheGoddess","itemWhiffOfMagic","itemReekOfMisfortune","itemOdourOfPurity","itemFoulFume"};
  private final List<Entry> entries=new ArrayList<>();
  private final NativeItemExamples examples=new NativeItemExamples();
  private final Map<Item,List<FurnaceRow>> furnaceRows=new IdentityHashMap<>();
  private final Map<ItemStack,String> nameBindings=new IdentityHashMap<>();
  private NativeDefinitionGraph definitions;
  private List<?> registry;
  private NativeSmeltingTable furnace;
  private ItemStack jar,exhale,hint,breath,whiff,reek,odour,foul;
  private boolean filteredDouble,closed;

  static boolean supportsClass(String name){return HANDLER.equals(name);}
  static boolean supports(ICraftingHandler handler){return handler!=null&&supportsClass(handler.getClass().getName());}
  static Object[] registrationAnchors(){
    Object raw=field(type(SOURCE),null,"mRecipeMap");
    if(raw==null||raw.getClass()!=ArrayList.class)throw fault("Uninitialized native oven registration");
    return new Object[]{raw,NativeSmeltingTable.furnaceManager(),NativeSmeltingTable.furnaceItems()};
  }
  WitcheryOvenRecipes(TemplateRecipeHandler handler){
    if(!supports(handler))throw fault("Unknown Witchery oven handler");
    MagicApi.version("WitcheryOvenRecipes","witchery");MagicApi.version("WitcheryOvenRecipes","WitcheryExtras");
    try{
      registry=(List<?>)registrationAnchors()[0];
      if(registry.size()>4096)throw fault("Native oven definition budget exceeded");
      furnace=new NativeSmeltingTable(NativeSmeltingTable.furnaceItems());
      jar=sub(ROLES[0]);exhale=sub(ROLES[1]);hint=sub(ROLES[2]);breath=sub(ROLES[3]);
      whiff=sub(ROLES[4]);reek=sub(ROLES[5]);odour=sub(ROLES[6]);foul=sub(ROLES[7]);
      filteredDouble=(Boolean)field(field(type(CONFIG),null,"INSTANCE"),"doubleFumeFilterChance");
      for(ItemStack key:furnace.keys()){
        Jobs.checkpoint();ItemStack output=nullable((ItemStack)furnace.get(key));
        furnaceRows.computeIfAbsent(key.getItem(),ignored->new ArrayList<>()).add(new FurnaceRow(Items.feather.getDamage(key),output));
      }
      for(Object raw:registry)entries.add(new Entry(raw));
      Map<String,String[]> shapes=NativeDefinitionGraph.shapes(RECIPE,"inputs","jars","output","outputJar","outputAmount1","outputAmountJar","validOreDictInput");
      NativeSmeltingTable.shapes(shapes);
      shapes.put(FurnaceRecipes.class.getName(),new String[]{"smeltingList","experienceList"});
      shapes.put(CONFIG,new String[]{"doubleFumeFilterChance"});
      shapes.put(SUBITEM,new String[]{"damageValue"});
      for(String role:ROLES){Object sub=field(generic(),role);admitSubitem(sub);shapes.put(sub.getClass().getName(),new String[]{"damageValue"});}
      definitions=new NativeDefinitionGraph(this::roots,shapes);
    }catch(RuntimeException|Error error){close();throw error;}
  }
  private static Object generic(){
    Object items=field(type(WITCHERY),null,"Items");
    if(items==null||items.getClass()!=type("com.emoniph.witchery.WitcheryItems"))throw fault("Uninitialized native Witchery item roles");
    Object generic=field(items,"GENERIC");
    if(generic==null||generic.getClass()!=type("com.emoniph.witchery.item.ItemGeneral"))throw fault("Unadapted Witchery generic item");
    return generic;
  }
  private Object[] roots(){
    List<Object> roots=new ArrayList<>(Arrays.asList(registrationAnchors()));
    roots.add(field(type(CONFIG),null,"INSTANCE"));
    Object generic=generic();roots.add(generic);
    for(String role:ROLES)roots.add(field(generic,role));
    roots.add(sapling());roots.add(Item.getItemFromBlock(sapling()));roots.add(Item.getItemFromBlock(Blocks.sapling));return roots.toArray();
  }
  private static Block sapling(){
    Object blocks=field(type(WITCHERY),null,"Blocks");
    if(blocks==null||blocks.getClass()!=type("com.emoniph.witchery.WitcheryBlocks"))throw fault("Uninitialized Witchery block roles");
    Object sapling=field(blocks,"SAPLING");
    if(!(sapling instanceof Block))throw fault("Missing Witchery sapling role");
    return (Block)sapling;
  }
  private static ItemStack sub(String name){
    Object generic=generic(),sub=field(generic,name);
    admitSubitem(sub);
    return valid(new ItemStack((Item)generic,1,(Integer)field(type(SUBITEM),sub,"damageValue")));
  }
  private static void admitSubitem(Object sub){
    if(sub==null||!type(SUBITEM).isInstance(sub))throw fault("Missing native oven subitem role");
    try{if(sub.getClass().getMethod("createStack",int.class).getDeclaringClass()!=type(SUBITEM))throw fault("Unadapted native oven subitem factory");}
    catch(NoSuchMethodException error){throw fault("Missing native oven subitem factory");}
    for(Class<?> owner=sub.getClass();owner!=type(SUBITEM);owner=owner.getSuperclass())try{
      owner.getDeclaredField("damageValue");throw fault("Oven subitem shadows the bound native damage field");
    }catch(NoSuchFieldException ignored){}
  }

  public int size(){alive();return entries.size();}
  public void verify(){alive();definitions.verifyRoots();}
  public void finishVerification(){
    verify();definitions.verify();examples.verify();
    for(Map.Entry<ItemStack,String> name:nameBindings.entrySet())
      if(!name.getValue().equals(nativeName(name.getKey())))throw changed();
  }
  public JsonObject exclusion(int index){
    alive();Entry entry=entries.get(index);
    if(entry.input==null)return object("native",SOURCE,"reason","actual_tile_requires_non_null_literal_input;ore_only_helper_recipe_is_unreachable","nativeIndex",index,"oreKey",entry.oreKey);
    if(entry.variants.isEmpty())return object("native",SOURCE,"reason","earlier_literal_definition_covers_the_entire_native_predicate_and_jar_threshold","nativeIndex",index,"earlierIndex",entry.shadow);
    return null;
  }
  public boolean capture(int index,RecipeRow row){
    List<RecipeRow> rows=captureRows(index,row);return !rows.isEmpty();
  }
  public List<RecipeRow> captureRows(int index,RecipeRow row){
    alive();Entry entry=entries.get(index);if(exclusion(index)!=null)return Collections.emptyList();
    List<Variant> variants=new ArrayList<>();List<Boolean> jarred=new ArrayList<>();
    for(Variant variant:entry.variants){
      if(entry.jars<=0&&!shadowed(index,variant.input,0)){variants.add(variant);jarred.add(false);}
      if(!shadowed(index,variant.input,Math.max(1,entry.jars))){variants.add(variant);jarred.add(true);}
    }
    List<RecipeRow> rows=new ArrayList<>();
    for(int i=0;i<variants.size();i++)rows.add(i==0?row:row.branch());
    for(int i=0;i<variants.size();i++)emit(index,variants.get(i),jarred.get(i),rows.get(i));
    return rows;
  }

  private void emit(int index,Variant variant,boolean withJar,RecipeRow row){
    Entry entry=entries.get(index);ItemStack shown=variant.input.copy();shown.stackSize=1;
    // This is a concrete content specimen. The process carries the actual symmetric
    // wildcard/NBT law and all native definitions, rather than pretending exact is the entire domain.
    row.itemInput(0,Collections.singletonList(new RecipeRow.Ingredient(shown,1,false,object("kind","exact"))),false);
    if(withJar){
      int stock=Math.max(1,entry.jars);
      row.itemInput(1,Collections.singletonList(new RecipeRow.Ingredient(jar.copy(),stock,true,object("kind","wildcard","meta",false,"nbt",true))),false);
      row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().add("consume",object("kind","allocated"));
    }
    row.itemOutput(0,variant.main.copy(),10000,object("kind","potential","nominal",Integer.toString(variant.main.stackSize),"stat","witchery:ovenMain","condition","nativeOutputInventory"));
    Byproduct product=withJar?variant.product:new Byproduct(null,"none");
    if(product.item!=null)row.itemOutput(1,product.item.copy(),10000,object("kind","potential","nominal","1","stat","witchery:ovenByproduct","condition","nativeJarRollAndOutputAccepts"));
    JsonArray all=array();for(Entry definition:entries)all.add(object("input",selector(definition.input),"jars",definition.jars,"gate",prototype(definition.gate)));
    JsonArray smelting=array();for(FurnaceRow definition:furnaceRows.getOrDefault(shown.getItem(),Collections.emptyList()))
      smelting.add(object("metadata",definition.metadata,"output",prototype(definition.output)));
    row.record.add("process",object("kind","witcheryNativeOven","version","1.3.8","selected",index,
        "definitions",all,"furnace",smelting,"jarMode",withJar?"conditional":"absent","jarStock",withJar?Math.max(1,entry.jars):0,
        "filteredDouble",filteredDouble,"byproduct",product.kind,"byproductItem",product.item==null?null:row.facts.item(product.item),
        "witcherySapling",Item.itemRegistry.getNameForObject(Item.getItemFromBlock(sapling())),
        "inputName",nameBindings.containsKey(variant.input)?nameBindings.get(variant.input):null,
        "roles",object("jar",row.facts.item(jar),"exhale",row.facts.item(exhale),"hint",row.facts.item(hint),"breath",row.facts.item(breath),
            "whiff",row.facts.item(whiff),"reek",row.facts.item(reek),"odour",row.facts.item(odour),"foul",row.facts.item(foul))));
    row.note("witchery:nativeOven",SOURCE+";alkalus.main.mixins.late.witchery.TileEntityWitchesOvenMixin;generateByProduct",
        row.facts.locale().equals("zh_CN")
          ?"机器按注册顺序选择首条字面物品、两侧32767通配、完全相同NBT及罐子库存满足的定义；仅矿辞的辅助查询项不在机器路径内。源表产物只作已有产物槽的容量门禁，实际主产物取自原版熔炉。所示是原生输入与空产物槽样本，已有产物合并、门禁、燃料和库存会影响结果。罐槽存在时按原生概率抽副产物，只有成功放入才扣一罐；已有副产物合并要求合并后严格小于最大堆叠。"
          :"The machine chooses the first registered literal item with either-side 32767 wildcard, equal NBT and sufficient jar stock. Ore-only helper rows do not enter this tile path. The registered output gates an occupied main-output slot, while vanilla smelting supplies the actual result. Displayed inputs and empty-slot results are specimens; fuel, gates, existing-output merges and inventory affect execution. A present jar slot permits the native byproduct roll and debits one jar only on placement. A byproduct merge requires the new count to be strictly below its stack limit.");
  }

  private boolean shadowed(int index,ItemStack offered,int jars){
    for(int i=0;i<index;i++){Entry previous=entries.get(i);if(previous.jars<=jars&&matches(previous.input,offered))return true;}return false;
  }
  private static boolean matches(ItemStack expected,ItemStack offered){
    return expected!=null&&expected.getItem()==offered.getItem()&&ItemStack.areItemStackTagsEqual(expected,offered)
        &&(Items.feather.getDamage(expected)==32767||Items.feather.getDamage(offered)==32767||Items.feather.getDamage(expected)==Items.feather.getDamage(offered));
  }
  private String name(ItemStack stack){String result=nativeName(stack);nameBindings.put(stack,result);return result;}
  private static String nativeName(ItemStack stack){
    Class<?> owner=ItemCallbacks.method(stack.getItem(),"getUnlocalizedName","func_77667_c",ItemStack.class).getDeclaringClass();
    boolean forestry=owner.getName().equals("forestry.core.items.ItemBlockForestry");
    if(owner!=Item.class&&owner!=ItemBlock.class&&owner!=ItemMultiTexture.class&&!forestry)throw fault("Oven byproduct name callback requires an adapter: "+owner.getName());
    if(owner==Item.class){
      if(ItemCallbacks.method(stack.getItem(),"getUnlocalizedName","func_77658_a").getDeclaringClass()!=Item.class)
        throw fault("Oven item name delegates to an unadapted override");
    }else{
      if(forestry){
        MagicApi.version("WitcheryOvenRecipes","Forestry");
        for(Class<?> current=stack.getItem().getClass();current!=null;current=current.getSuperclass())try{
          current.getDeclaredMethod("getBlock");if(current!=owner)throw fault("Oven Forestry block getter was overridden");break;
        }catch(NoSuchMethodException ignored){}
      }
      Object rawBlock=field(ItemBlock.class,stack.getItem(),"field_150939_a");
      if(!(rawBlock instanceof Block))throw fault("Missing oven item block binding");
      String name=cpw.mods.fml.relauncher.ReflectionHelper.findMethod(Block.class,null,new String[]{"getUnlocalizedName","func_149739_a"}).getName();
      try{if(rawBlock.getClass().getMethod(name).getDeclaringClass()!=Block.class)throw fault("Oven block name requires an adapter");}
      catch(NoSuchMethodException error){throw fault("Missing oven block name callback");}
      if(owner==ItemMultiTexture.class){
        Object names=field(stack.getItem(),"field_150942_c");
        if(names==null||names.getClass()!=String[].class||((String[])names).length==0||((String[])names).length>65536)throw fault("Invalid native multi-texture names");
      }
    }
    return stack.getUnlocalizedName();
  }
  private Byproduct byproduct(ItemStack input){
    int meta=Items.feather.getDamage(input);
    if(input.getItem()==Item.getItemFromBlock(Blocks.sapling)&&meta!=3)
      return meta==0?new Byproduct(exhale,"exhale"):meta==1?new Byproduct(hint,"hint"):meta==2?new Byproduct(breath,"breath"):new Byproduct(null,"none");
    if(input.getItem()==Item.getItemFromBlock(sapling()))
      return meta==0?new Byproduct(whiff,"whiff"):meta==1?new Byproduct(reek,"reek"):meta==2?new Byproduct(odour,"odour"):new Byproduct(null,"none");
    if(name(input).equals("tile.bop.saplings")&&meta==6)return new Byproduct(hint,"hint");
    NBTTagCompound tag=input.getTagCompound();
    if(tag==null||!tag.hasKey("Genome"))return new Byproduct(foul,"foul");
    if(!tag.hasKey("Genome",10))return new Byproduct(null,"none");
    NBTTagCompound genome=tag.getCompoundTag("Genome");
    if(!genome.hasKey("Chromosomes",9))return new Byproduct(null,"none");
    NBTTagList chromosomes=genome.getTagList("Chromosomes",10);
    if(chromosomes.tagCount()==0)return new Byproduct(null,"none");
    NBTTagCompound first=chromosomes.getCompoundTagAt(0);
    if(!first.hasKey("UID0",8))return new Byproduct(null,"none");
    String uid=first.getString("UID0");
    return uid.equals("forestry.treeOak")?new Byproduct(exhale,"exhale"):uid.equals("forestry.treeSpruce")?new Byproduct(hint,"hint")
        :uid.equals("forestry.treeBirch")?new Byproduct(breath,"breath"):new Byproduct(null,"none");
  }

  private final class Entry{
    final ItemStack input,gate;final int jars;final String oreKey;final List<Variant> variants=new ArrayList<>();int shadow=-1;
    Entry(Object raw){
      if(raw==null||raw.getClass()!=type(RECIPE))throw fault("Unadapted native oven definition");
      input=nullable((ItemStack)field(raw,"inputs"));gate=nullable((ItemStack)field(raw,"output"));
      jars=(Integer)field(raw,"jars");oreKey=(String)field(raw,"validOreDictInput");
      if(input==null)return;
      int index=entries.size();
      for(int i=0;i<index;i++){
        Entry prior=entries.get(i);
        if(prior.input!=null&&prior.jars<=Math.max(0,jars)&&prior.input.getItem()==input.getItem()
            &&ItemStack.areItemStackTagsEqual(prior.input,input)
            &&(Items.feather.getDamage(prior.input)==32767||Items.feather.getDamage(prior.input)==Items.feather.getDamage(input))){shadow=i;return;}
      }
      ItemStack[] candidates=examples.copies(input);
      if(candidates.length==0)throw fault("Oven definition has no native content specimen or proof of unreachability");
      for(ItemStack example:candidates){
        Jobs.checkpoint();
        if(shadowed(index,example,Math.max(0,jars))&&shadowed(index,example,Math.max(1,jars)))continue;
        Object result=furnace.get(example);
        if(result==null)throw fault("Reachable oven definition has no native furnace result; actual smeltItem would fail");
        ItemStack main=valid((ItemStack)result);
        if(main.stackSize<=0)throw fault("Native oven main output has no positive inventory specimen");
        ItemStack specimen=example.copy();variants.add(new Variant(specimen,main,byproduct(specimen)));
      }
      if(variants.isEmpty())throw fault("All displayed oven variants are shadowed, but the full source predicate is not proven unreachable");
    }
  }
  private static JsonObject selector(ItemStack stack){
    return stack==null?null:object("registry",Item.itemRegistry.getNameForObject(stack.getItem()),"metadata",Items.feather.getDamage(stack),
        "nbt",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(stack.getTagCompound()));
  }
  private static JsonObject prototype(ItemStack stack){
    if(stack==null)return null;JsonObject value=selector(stack);value.addProperty("count",stack.stackSize);return value;
  }
  private static ItemStack nullable(ItemStack value){return value==null?null:valid(value);}
  private static ItemStack valid(ItemStack value){
    NativeStackAudit.audit(value);
    if(Item.itemRegistry.getNameForObject(value.getItem())==null)throw fault("Unregistered native oven item");
    return value.copy();
  }
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Witchery oven reader is closed");}
  public void close(){
    closed=true;if(definitions!=null)definitions.close();examples.close();entries.clear();furnaceRows.clear();nameBindings.clear();registry=null;furnace=null;
    jar=exhale=hint=breath=whiff=reek=odour=foul=null;
  }
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native oven registration, furnace, roles, config or names changed");}
  private static final class Variant{final ItemStack input,main;final Byproduct product;Variant(ItemStack input,ItemStack main,Byproduct product){this.input=input;this.main=main;this.product=product;}}
  private static final class FurnaceRow{final int metadata;final ItemStack output;FurnaceRow(int metadata,ItemStack output){this.metadata=metadata;this.output=output;}}
  private static final class Byproduct{final ItemStack item;final String kind;Byproduct(ItemStack item,String kind){this.item=item;this.kind=kind;}}
}
