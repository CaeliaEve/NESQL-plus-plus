package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** World transforms, packed altar inventories, portal trades and native brew containers. */
final class BotaniaWorldRecipes implements RegistryRecipes {
  private static final String HANDLERS="vazkii.botania.client.integration.nei.recipe.RecipeHandler";
  private static final String API="vazkii.botania.api.BotaniaAPI",RECIPES="vazkii.botania.api.recipe.",BREW="vazkii.botania.api.brew.Brew";
  private final Class<?> handlerClass;
  private final String mode,table;
  private final List<Entry> entries=new ArrayList<>();
  private final List<Page> pages=new ArrayList<>();
  private final NativeItemExamples examples=new NativeItemExamples();
  private NativeDefinitionGraph bindings;
  private String signature;
  private JsonObject speciesDominance=object();
  private Object registry;
  private Object alleleRegistry,alleleMap;
  private boolean closed;

  static boolean supportsClass(String name){return (HANDLERS+"PureDaisy").equals(name)||(HANDLERS+"PetalApothecary").equals(name)||(HANDLERS+"ElvenTrade").equals(name)||(HANDLERS+"Brewery").equals(name);}
  private static String mode(String name){if(name.endsWith("PureDaisy"))return "daisy";if(name.endsWith("PetalApothecary"))return "petal";if(name.endsWith("ElvenTrade"))return "trade";if(name.endsWith("Brewery"))return "brewery";throw fault("Unknown Botania world family");}
  private static String table(String mode){return mode.equals("daisy")?"pureDaisyRecipes":mode.equals("petal")?"petalRecipes":mode.equals("trade")?"elvenTradeRecipes":"brewRecipes";}
  static Object[] registrationAnchors(Class<?> handler){
    if(!supportsClass(handler.getName()))throw fault("Unknown Botania world handler");
    String mode=mode(handler.getName());Object source=field(type(API),null,table(mode));
    if(source==null)throw new Jobs.Fault("handler_not_ready","Botania native source is unavailable");
    if(source.getClass()!=ArrayList.class&&source.getClass()!=LinkedList.class||((List<?>)source).size()>4096)throw fault("Unadapted Botania native source storage");
    List<Object> roots=new ArrayList<>();roots.add(source);
    if(mode.equals("brewery")){
      roots.add(field(type(API),null,"brewMap"));
      for(String role:new String[]{"vial","incenseStick","brewVial","brewFlask"}){
        Object item=field(type("vazkii.botania.common.item.ModItems"),null,role);if(item==null)throw new Jobs.Fault("handler_not_ready","Botania brew item role is unavailable");roots.add(item);
      }
    }
    return roots.toArray();
  }

  BotaniaWorldRecipes(TemplateRecipeHandler handler){
    handlerClass=handler.getClass();if(!supportsClass(handlerClass.getName()))throw fault("Unknown Botania world handler");MagicApi.version("BotaniaWorldRecipes","Botania");
    mode=mode(handlerClass.getName());table=table(mode);registry=registrationAnchors(handlerClass)[0];
    try{
      read(entries);Map<String,String[]> shapes=shapes();
      bindings=new NativeDefinitionGraph(()->registrationAnchors(handlerClass),shapes);
      speciesDominance=dominance();signature=CanonicalJson.digest(object("entries",snapshot(entries),"dominance",speciesDominance));
      for(int index=0;index<entries.size();index++){
        Entry entry=entries.get(index);
        if(mode.equals("brewery"))for(Container container:containers(entry))pages.add(new Page(index,container));
        else pages.add(new Page(index,null));
      }
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  private void read(List<Entry> target){for(Object source:(List<?>)registry){Jobs.checkpoint();target.add(new Entry(source,mode));}}
  private Map<String,String[]> shapes(){
    Map<String,String[]> shapes=new HashMap<>();
    shapes.put(RECIPES+"RecipePureDaisy",new String[]{"input","output","outputMeta"});
    shapes.put(RECIPES+"RecipePetals",new String[]{"inputs","output"});
    shapes.put(RECIPES+"RecipeElvenTrade",new String[]{"inputs","output"});
    shapes.put("magicbees.main.utils.compat.botania.SpeciesRecipeElvenTrade",new String[]{"inputs","output","inputSpecies"});
    shapes.put(RECIPES+"RecipeBrew",new String[]{"inputs","brew"});
    for(String brew:new String[]{BREW,"vazkii.botania.common.brew.BrewMod","vazkii.botania.common.brew.BrewModPotion"})shapes.put(brew,new String[]{"key","cost","canInfuseIncense","effects"});
    shapes.put("net.minecraft.potion.PotionEffect",new String[]{"potionID","duration","amplifier","isAmbient"});
    for(Entry entry:entries)if(entry.species!=null){Object allele=field(entry.source,"inputSpecies");Class<?> definition=speciesDefinition(allele);shapes.put(allele.getClass().getName(),new String[]{speciesUidField(definition),"isDominant"});}
    return shapes;
  }
  private JsonArray snapshot(List<Entry> source){JsonArray rows=array();for(Entry entry:source){JsonArray containers=array();if(mode.equals("brewery"))for(Container c:containers(entry))containers.add(c.json());rows.add(object("rule",entry.json(),"containers",containers));}if(CanonicalJson.bytes(rows).length>32*1024*1024)throw fault("Botania world definitions exceed byte budget");return rows;}
  public int size(){alive();return pages.size();}
  public void verify(){alive();bindings.verifyRoots();if(field(type(API),null,table)!=registry)throw changed();
    if(alleleRegistry!=null&&(forestry.api.genetics.AlleleManager.alleleRegistry!=alleleRegistry||field(alleleRegistry,"alleleMap")!=alleleMap))throw changed();}
  public void finishVerification(){verify();bindings.verify();List<Entry> actual=new ArrayList<>();read(actual);if(!signature.equals(CanonicalJson.digest(object("entries",snapshot(actual),"dominance",dominance()))))throw changed();examples.verify();}
  private JsonObject dominance(){
    JsonObject result=object();if(entries.stream().noneMatch(entry->entry.species!=null))return result;
    Object registry=forestry.api.genetics.AlleleManager.alleleRegistry;if(registry==null||!registry.getClass().getName().equals("forestry.core.genetics.alleles.AlleleRegistry"))throw fault("Unadapted elven bee allele registry");
    Object raw=field(registry,"alleleMap");if(raw==null||raw.getClass()!=LinkedHashMap.class||((Map<?,?>)raw).size()>262144)throw fault("Unadapted elven bee allele storage");
    if(alleleRegistry==null){alleleRegistry=registry;alleleMap=raw;}
    else if(alleleRegistry!=registry||alleleMap!=raw)throw changed();
    Map<?,?> alleles=(Map<?,?>)raw;TreeMap<String,Boolean> ordered=new TreeMap<>();
    for(Map.Entry<?,?> registered:alleles.entrySet())if(registered.getValue() instanceof forestry.api.apiculture.IAlleleBeeSpecies){
      Object allele=registered.getValue();
      Class<?> base=speciesDefinition(allele);String uid=(String)field(base,allele,speciesUidField(base));
      if(uid==null||!uid.equals(registered.getKey()))throw fault("Native elven allele key disagrees with UID");
      ordered.put(uid,(Boolean)field(base,allele,"isDominant"));
    }
    for(Entry entry:entries)if(entry.species!=null&&alleles.get(entry.species)!=field(entry.source,"inputSpecies"))throw changed();
    for(Map.Entry<String,Boolean> value:ordered.entrySet())result.addProperty(value.getKey(),value.getValue());return result;
  }

  /** Pinned getters are field reads; discovering a recipe must not execute foreign callbacks. */
  private static Class<?> speciesDefinition(Object allele){
    if(!(allele instanceof forestry.api.apiculture.IAlleleBeeSpecies))throw fault("Missing native bee species");
    try{
      Class<?> identity=allele.getClass().getMethod("getUID").getDeclaringClass();
      Class<?> dominant=allele.getClass().getMethod("isDominant").getDeclaringClass();
      if(identity.getName().equals("forestry.core.genetics.alleles.Allele")&&identity==dominant)return identity;
      if(allele.getClass().getName().equals("net.bdew.gendustry.custom.BeeSpecies")&&identity==allele.getClass()&&dominant==identity){MagicApi.version("BotaniaWorldRecipes","gendustry");return identity;}
      throw fault("Unadapted elven bee dominance callback");
    }catch(NoSuchMethodException failure){throw fault("Missing native allele identity");}
  }
  private static String speciesUidField(Class<?> definition){return definition.getName().equals("net.bdew.gendustry.custom.BeeSpecies")?"getUID":"uid";}

  public boolean capture(int index,RecipeRow row){
    alive();Page page=pages.get(index);Entry entry=entries.get(page.selector);
    if(entry.groups.size()>(mode.equals("petal")?16:mode.equals("brewery")?6:4096))return row.exclude(object("reason","native_inventory_cannot_hold_all_required_groups","groups",entry.groups.size(),"mode",mode));
    int slot=0;JsonArray inputSlots=array();
    for(List<Selector> group:entry.groups){List<RecipeRow.Ingredient> choices=new ArrayList<>();for(Selector selector:group)choices.addAll(selector.choices(examples));
      if(choices.isEmpty()){
        if(mode.equals("daisy")&&!group.isEmpty()){inputSlots.add(value(null));continue;}
        return row.exclude(object("reason","native_required_input_domain_empty","mode",mode,"input",slot));
      }
      inputSlots.add(value(slot));row.itemInput(slot++,choices,false);
    }
    String seed=null;Integer container=null,mana=null;String brew=null;ItemStack output=entry.output;
    if(mode.equals("petal")){
      seed=row.facts.item(new ItemStack(Items.wheat_seeds));row.itemInput(16,Collections.singletonList(new RecipeRow.Ingredient(new ItemStack(Items.wheat_seeds),1,false,object("kind","wildcard","meta",true,"nbt",true))),false);
    }
    if(page.container!=null){container=6;row.itemInput(container,Collections.singletonList(new RecipeRow.Ingredient(page.container.input.copy(),1,false,object("kind","wildcard","meta",false,"nbt",true))),false);output=page.container.output;mana=page.container.mana;brew=(String)field(entry.brew,"key");}
    if(output!=null)row.itemOutput(0,output.copy(),10000);
    JsonArray priority=array();
    for(int at=0;at<entries.size();at++)if(mode.equals("brewery")?at>page.selector:at<page.selector)priority.add(entries.get(at).predicates());
    row.record.add("process",object("kind","botaniaWorld","version","1.12.28-GTNH","machine",mode,"selector",page.selector,
        "predicates",entry.predicates(),"inputSlots",inputSlots,"priority",priority,"speciesDominance",GendustryGeneticsRecipes.owned(speciesDominance),"seed",seed,"container",container,"mana",mana,"brew",brew,"worldResult",entry.worldResult));
    row.note("botania:native-world",mode.equals("daisy")?"vazkii.botania.common.block.subtile.SubTilePureDaisy#onUpdate":mode.equals("petal")?"vazkii.botania.common.block.tile.TileAltar#collideEntityItem":mode.equals("trade")?"vazkii.botania.common.block.tile.TileAlfPortal#resolveRecipes":"vazkii.botania.common.block.tile.TileBrewery#addItem,updateEntity",
        row.facts.locale().equals("zh_CN")?noteZh(mode):noteEn(mode));
    return true;
  }

  private static String noteZh(String mode){
    switch(mode){case "daisy":return "白雏菊按八个相邻位置轮询，每处累计 150 次有效检查后替换世界方块，通常需 1200 tick；不是物品库存合成。按原生注册顺序取首个匹配项；失配重置该位置计数，换为另一匹配配方可保留计数。结果保留实际输出元数据。";
      case "petal":return "花药台需有水。普通加入先通过原生花朵组件准入，材料稳定紧密排列；逐格移除首个匹配的剩余谓词，首个空格终止。种子触发需不是可加入组件且原生未本地化名称符合种子正则；小麦种子仅为实例，不限制其他合法种子。完成取用一粒种子、清空花药台库存并清水，无合成容器返还。较早配方优先，不提供固定加工进度。";
      case "trade":return "精灵门把掉入物拆为单位物品，按原生注册顺序处理。每次成功匹配取用各个需求；不匹配的额外物品可跳过。一次成功后会继续尝试，最后一次失败的 remove 调用也可能移除部分匹配材料，因此不是保证批量换算。魔法蜜蜂分支按雄蜂主物种判断，忽略其他基因。需要有效开启的精灵门，知识解锁和世界／事件条件仍适用。";
      default:return "酿造台容器须先放在槽 0，原料每槽一件紧密排列，首个空格终止匹配。正常准入拒绝已有非回退酿造内容；已知瓶／熏香容器分别按原生回调确定结果与有符号魔力需求。扫描取最后一个匹配配方；恢复与已有配方缓存可影响状态。魔力达到需求后服务端清空全部槽并生成结果，无容器返还、无固定时长。熏香的可注入性和单个非瞬时药效条件保留。";}
  }
  private static String noteEn(String mode){
    switch(mode){case "daisy":return "The Pure Daisy rotates through eight neighboring positions, replacing each world block after 150 matching checks (normally 1200 ticks). This is not inventory crafting. The first matching registry entry wins; a mismatch resets that position while a different matching recipe can retain its counter. Actual output metadata is preserved.";
      case "petal":return "The apothecary must contain water. Normal insertion first applies native flower-component admission. Stable ingredients are packed, and each physical slot removes the first matching remaining predicate; the first empty slot stops matching. A seed trigger must not be an admitted component and its native unlocalized key must match the seed regex. Wheat seeds are an example, not the entire seed domain. Completion consumes one seed, clears the inventory and water, and returns no crafting containers. Earlier recipes take priority; no fixed processing progress is asserted.";
      case "trade":return "The open elven portal splits dropped stacks into units and processes recipes in registry order. A successful match removes its requirements and can skip unrelated stock. Processing repeats; the final failed remove attempt can also take partially matched inputs, so this is not a guaranteed bulk conversion. Magic Bees branches inspect a drone's primary species, ignoring other genes. Portal, knowledge and world/event conditions still apply.";
      default:return "The brewery container enters slot 0 first; unit ingredients stay packed and matching stops at the first empty slot. Normal admission rejects existing non-fallback brew contents. Audited vial/incense callbacks determine the result and signed mana requirement. Scanning chooses the last matching recipe; recovery and an existing cached selection can affect state. Once mana reaches the requirement, the server clears every slot and spawns the result without container returns or a fixed duration. Incense keeps its infusability and single non-instant effect condition.";}
  }

  private static final class Entry {
    final Object source,brew;final List<List<Selector>> groups=new ArrayList<>();final ItemStack output;final JsonObject worldResult;final String species;
    Entry(Object source,String mode){
      if(source==null)throw fault("Null native Botania world definition");this.source=source;String expected=RECIPES+(mode.equals("daisy")?"RecipePureDaisy":mode.equals("petal")?"RecipePetals":mode.equals("trade")?"RecipeElvenTrade":"RecipeBrew");
      boolean bees=mode.equals("trade")&&source.getClass().getName().equals("magicbees.main.utils.compat.botania.SpeciesRecipeElvenTrade");
      if(source.getClass()!=type(expected)&&!bees)throw fault("Unadapted native Botania world recipe: "+source.getClass().getName());
      if(bees){Object allele=field(source,"inputSpecies");Class<?> definition=speciesDefinition(allele);species=(String)field(definition,allele,speciesUidField(definition));}else species=null;
      if(mode.equals("daisy")){
        Object input=field(source,"input");List<Selector> selectors=new ArrayList<>();
        if(input instanceof Block)selectors.add(new Selector((Block)input));
        else if(input instanceof String){for(ItemStack stack:NativeOreTable.copies((String)input))if(Block.getBlockFromItem(stack.getItem())!=Blocks.air)selectors.add(new Selector(stack,stack.getItemDamage()==32767,null,Block.blockRegistry.getNameForObject(Block.getBlockFromItem(stack.getItem()))));}
        else throw fault("Unadapted Pure Daisy world predicate");
        groups.add(selectors);Block result=(Block)field(source,"output");int metadata=(Integer)field(source,"outputMeta");String registry=Block.blockRegistry.getNameForObject(result);if(registry==null)throw fault("Unregistered Pure Daisy world result");
        worldResult=object("registry",registry,"metadata",metadata);Item resultItem=Item.getItemFromBlock(result);output=resultItem==null?null:new ItemStack(resultItem,1,metadata);brew=null;
      }else{
        Object inputs=field(source,"inputs");if(inputs==null||inputs.getClass()!=ArrayList.class||((List<?>)inputs).size()>4096)throw fault("Unadapted Botania ingredient storage");
        for(Object input:(List<?>)inputs){List<Selector> selectors=new ArrayList<>();if(input instanceof ItemStack)selectors.add(new Selector((ItemStack)input,species!=null,species));else if(input instanceof String)for(ItemStack stack:NativeOreTable.copies((String)input))selectors.add(new Selector(stack,stack.getItemDamage()==32767,null));else throw fault("Unadapted Botania ingredient predicate");groups.add(selectors);}
        worldResult=null;
        if(mode.equals("brewery")){brew=field(source,"brew");auditBrew(brew);output=null;}else{brew=null;output=owned((ItemStack)field(source,"output"));}
      }
    }
    JsonArray predicates(){JsonArray rows=array();for(List<Selector> group:groups){JsonArray alternatives=array();for(Selector selector:group)alternatives.add(selector.json());rows.add(alternatives);}return rows;}
    JsonObject json(){return object("nativeClass",source.getClass().getName(),"predicates",predicates(),"output",output==null?null:RecipeRow.stackEvidence(output),"worldResult",worldResult,"brew",brew==null?null:field(brew,"key"));}
  }
  private static final class Selector {
    final ItemStack item;final boolean wildcard;final String species,block;
    Selector(ItemStack item,boolean wildcard,String species){this(item,wildcard,species,null);}
    Selector(ItemStack item,boolean wildcard,String species,String block){NativeStackAudit.audit(item);this.item=item.copy();this.item.stackSize=1;this.wildcard=wildcard;this.species=species;this.block=block;}
    Selector(Block block){this.block=Block.blockRegistry.getNameForObject(block);if(this.block==null)throw fault("Unregistered Pure Daisy world block");Item item=Item.getItemFromBlock(block);this.item=item==null?null:new ItemStack(item);wildcard=true;species=null;}
    JsonObject json(){String registry=item==null?null:Item.itemRegistry.getNameForObject(item.getItem());if(item!=null&&registry==null)throw fault("Unregistered Botania ingredient");return object("registry",registry,"metadata",item==null?0:net.minecraft.init.Items.feather.getDamage(item),"wildcard",wildcard,"species",species,"block",block);}
    List<RecipeRow.Ingredient> choices(NativeItemExamples examples){List<RecipeRow.Ingredient> choices=new ArrayList<>();if(item==null)return choices;for(ItemStack stack:wildcard&&item.getItemDamage()==32767?examples.copies(item):new ItemStack[]{item.copy()})choices.add(new RecipeRow.Ingredient(stack,1,false,object("kind","wildcard","meta",wildcard,"nbt",true)));return choices;}
  }
  private static void auditBrew(Object brew){if(brew==null)throw fault("Missing native brew");for(String method:new String[]{"getKey","getManaCost","canInfuseIncense","getPotionEffects"})try{Class<?>[] parameters=method.equals("getPotionEffects")?new Class<?>[]{ItemStack.class}:new Class<?>[0];if(brew.getClass().getMethod(method,parameters).getDeclaringClass()!=type(BREW))throw fault("Unadapted brew callback: "+method);}catch(NoSuchMethodException failure){throw fault("Missing brew callback: "+method);}}
  private static List<Container> containers(Entry entry){
    List<Container> result=new ArrayList<>();Class<?> items=type("vazkii.botania.common.item.ModItems");
    Item vial=(Item)field(items,null,"vial"),incense=(Item)field(items,null,"incenseStick");
    for(ItemStack input:new ItemStack[]{new ItemStack(vial,1,0),new ItemStack(vial,1,1),new ItemStack(incense)}){
      String owner=input.getItem()==vial?"vazkii.botania.common.item.brew.ItemVial":"vazkii.botania.common.item.brew.ItemIncenseStick";
      for(String method:new String[]{"getItemForBrew","getManaCost"})try{if(input.getItem().getClass().getMethod(method,type(BREW),ItemStack.class).getDeclaringClass()!=type(owner))throw fault("Unadapted native brew container callback");}catch(NoSuchMethodException failure){throw fault("Missing native brew container callback");}
      Object output=invoke(input.getItem().getClass(),input.getItem(),"getItemForBrew",new Class<?>[]{type(BREW),ItemStack.class},entry.brew,input.copy());
      if(output==null)continue;if(!(output instanceof ItemStack))throw fault("Invalid native brew result");
      int mana=(Integer)invoke(input.getItem().getClass(),input.getItem(),"getManaCost",new Class<?>[]{type(BREW),ItemStack.class},entry.brew,input.copy());
      result.add(new Container(input,owned((ItemStack)output),mana));
    }return result;
  }
  private static ItemStack owned(ItemStack stack){NativeStackAudit.audit(stack);if(stack.stackSize<=0)throw fault("Invalid native Botania result");return stack.copy();}
  private static final class Container {final ItemStack input,output;final int mana;Container(ItemStack input,ItemStack output,int mana){this.input=input;this.output=output;this.mana=mana;}JsonObject json(){return object("input",RecipeRow.stackEvidence(input),"output",RecipeRow.stackEvidence(output),"mana",mana);}}
  private static final class Page {final int selector;final Container container;Page(int selector,Container container){this.selector=selector;this.container=container;}}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Botania world reader is closed");}
  public void close(){closed=true;if(bindings!=null)bindings.close();examples.close();entries.clear();pages.clear();registry=null;alleleRegistry=null;alleleMap=null;signature=null;}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Botania world rule or container changed");}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
