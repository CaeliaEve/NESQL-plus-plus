package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.*;
import thaumcraft.api.crafting.*;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.api.wands.*;
import thaumcraft.common.config.ConfigItems;
import thaumcraft.common.items.wands.ItemWandCasting;

/** Source/callback binding for the pinned Salis replacement implementation, not GUI state. */
final class SalisWandBindings implements AutoCloseable {
  private static final String ROOT="dev.rndmorris.salisarcana.";
  private static final String CONFIG=ROOT+"config.SalisConfig";
  private static final String HELPER=ROOT+"lib.WandHelper";
  private static final String TC="com.gtnewhorizons.tcwands.api.";
  private static final String WRAPPER=TC+"wrappers.AbstractWandWrapper";
  private static final String BRACELET="tb.common.item.ItemCastingBracelet";
  private static final String DISPOSABLE="com.kentington.thaumichorizons.common.items.ItemWandCastingDisposable";
  private final Map<String,String[]> shapes=new HashMap<>();
  private final List<ItemStack> samples=new ArrayList<>();
  private final List<Object> sampleRecipes=new ArrayList<>();
  private final List<Object> materials=new ArrayList<>();
  private final Set<String> ores=new TreeSet<>();
  private List<ItemStack> itemList;
  private Object oreNames,oreStacks;
  private int itemCount;
  private NativeDefinitionGraph graph;
  private Object[] primary;
  private int[] primarySizes;
  private boolean gtCost;
  private final Map<WandRod,Object[]> wrapperCache=new IdentityHashMap<>();
  private final Map<WandCap,Object> capCache=new IdentityHashMap<>();
  private boolean closed;

  SalisWandBindings(){
    try{
      itemList=ItemList.items;if(itemList==null||itemList.getClass()!=ArrayList.class)throw fault("Unadapted NEI wand specimen backing");
      if(!ItemList.loadFinished)throw new Jobs.Fault("handler_not_ready","Salis wand specimens require a finished native item list");
      if(itemList.size()>1048576)throw fault("Native wand specimen list exceeds budget");
      itemCount=itemList.size();for(ItemStack sample:itemList)if(sample!=null&&sample.getItem() instanceof ItemWandCasting){NativeStackAudit.audit(sample);samples.add(sample);}
      for(Object recipe:ThaumcraftApi.getCraftingRecipes())if(recipe!=null&&(recipe.getClass()==ShapedArcaneRecipe.class||recipe.getClass()==ShapelessArcaneRecipe.class))sampleRecipes.add(recipe);
      shapes.put(ShapedArcaneRecipe.class.getName(),new String[]{"output"});shapes.put(ShapelessArcaneRecipe.class.getName(),new String[]{"output"});
      shapes.put(Aspect.class.getName(),new String[]{"tag","components"});
      shapes.put("java.util.UUID",new String[]{"mostSigBits","leastSigBits"});
      shapes.put("net.minecraft.entity.ai.attributes.RangedAttribute",new String[]{"unlocalizedName"});
      shapes.put(TC+"wandinfo.WandDetails",new String[]{"name","tier","conductor"});
      shapes.put(TC+"wandinfo.WandProps",new String[]{"baseCost","capCost"});
      shapes.put(TC+"wrappers.CapWrapper",new String[]{"name","costMultiplier","itemStack"});
      shapes.put(TC+"GTTier",new String[0]);
      for(Object rod:WandRod.rods.values())component(rod,true);
      for(Object cap:WandCap.caps.values())component(cap,false);
      for(Object item:Item.itemRegistry)if(item instanceof ItemWandCasting)wand((ItemWandCasting)item);
      Object raw=field(type(TC+"TCWandAPI"),null,"wandWrappers");
      if(raw==null||raw.getClass()!=ArrayList.class||((List<?>)raw).size()>4096)throw fault("Unadapted GTNH wand wrappers");
      for(Object wrapper:(List<?>)raw){
        Jobs.checkpoint();String name=wrapper.getClass().getName();
        if(!Arrays.asList(TC+"wrappers.WandWrapper",TC+"wrappers.StaffWrapper",TC+"wrappers.SceptreWrapper",TC+"wrappers.StaffSceptreWrapper").contains(name))
          throw fault("GTNH wand wrapper needs a callback adapter: "+name);
        List<String> members=new ArrayList<>(Arrays.asList("wandDetails","wandProps"));
        if(type(TC+"wrappers.SceptreWrapper").isInstance(wrapper))members.add("sceptreCostMultiplier");
        noShadows(wrapper,type(WRAPPER),"wandDetails","wandProps");shapes.put(name,members.toArray(new String[0]));
        Object details=field(type(WRAPPER),wrapper,"wandDetails"),props=field(type(WRAPPER),wrapper,"wandProps");
        exact(details,TC+"wandinfo.WandDetails");exact(props,TC+"wandinfo.WandProps");
        Object tier=field(details,"tier");exact(tier,TC+"GTTier");Object material=field(tier,"material");
        if(material==null||material.getClass()!=type("gregtech.api.enums.Materials"))throw fault("Unadapted wand screw material");
        if(!materials.contains(material))materials.add(material);shapes.put(material.getClass().getName(),new String[0]);
        String ore="screw"+(String)field(material,"mName");if(ore.length()>4096)throw fault("Wand screw key exceeds budget");ores.add(ore);
        Object conductor=field(details,"conductor");if(conductor!=null)NativeStackAudit.audit((ItemStack)conductor);
      }
      Object caps=field(type(TC+"TCWandAPI"),null,"caps");
      if(caps==null||caps.getClass()!=ArrayList.class||((List<?>)caps).size()>4096)throw fault("Unadapted GTNH cap wrappers");
      for(Object cap:(List<?>)caps)exact(cap,TC+"wrappers.CapWrapper");
      oreNames=field(OreDictionary.class,null,"nameToId");oreStacks=field(OreDictionary.class,null,"idToStack");
      if(!NativeOreStorage.names(oreNames)||oreStacks==null||oreStacks.getClass()!=ArrayList.class)throw fault("Unadapted wand ore registration");
      // Populate config dependency owner schemas without calling isEnabled (which lazily mutates mod loadState).
      roots();gtCost=enabled(compatibility(),"cost");primary=primary();primarySizes=new int[primary.length];
      for(int i=0;i<primary.length;i++)primarySizes[i]=count(primary[i]);
      graph=new NativeDefinitionGraph(this::roots,shapes);
    }catch(RuntimeException|Error error){close();throw error;}
  }
  private void component(Object raw,boolean rod){
    Class<?> base=rod?WandRod.class:WandCap.class;
    if(raw==null||!base.isInstance(raw))throw fault("Invalid wand component registration");
    for(String method:rod?new String[]{"getTag","getCapacity","getCraftCost","getItem","getResearch"}:new String[]{"getTag","getCraftCost","getItem","getResearch"})
      callback(raw,method,base);
    String[] fields=rod?new String[]{"tag","capacity","craftCost","item"}:new String[]{"tag","craftCost","item","baseCostModifier","specialCostModifierAspects","specialCostModifier"};
    noShadows(raw,base,fields);shapes.put(raw.getClass().getName(),fields);
  }
  private static void wand(ItemWandCasting item){
    String name=item.getClass().getName();boolean bracelet=BRACELET.equals(name),disposable=DISPOSABLE.equals(name);
    if(item.getClass()!=ItemWandCasting.class&&!bracelet&&!disposable)throw fault("Wand item needs a state adapter: "+name);
    for(String method:new String[]{"getRod","getCap","isStaff","isSceptre","getMaxVis","getAllVis","storeAllVis","setRod","setCap"}){
      Class<?>[] parameters=method.equals("storeAllVis")?new Class<?>[]{ItemStack.class,AspectList.class}
          :method.equals("setRod")?new Class<?>[]{ItemStack.class,WandRod.class}:method.equals("setCap")?new Class<?>[]{ItemStack.class,WandCap.class}:new Class<?>[]{ItemStack.class};
      Class<?> expected=bracelet&&Arrays.asList("getRod","getCap","isStaff","isSceptre","getMaxVis").contains(method)
          ||disposable&&method.equals("getMaxVis")?item.getClass():ItemWandCasting.class;
      callback(item,method,expected,parameters);
    }
    if(ItemCallbacks.method(item,"setDamage","setDamage",ItemStack.class,int.class).getDeclaringClass()!=Item.class)
      throw fault("Wand metadata mutation needs an adapter");
    NativeStackAudit.audit(new ItemStack(item,1,0));
  }
  private Object[] roots(){
    List<Object> values=new ArrayList<>();
    Collections.addAll(values,WandRod.rods,WandCap.caps,field(type(HELPER),null,"WAND_RODS"),field(type(HELPER),null,"WAND_CAPS"),
        field(type(HELPER),null,"CAP_UNKNOWN"),field(type(HELPER),null,"ROD_UNKNOWN"),field(type(HELPER),null,"STAFF_UNKNOWN"),
        field(Aspect.class,null,"aspects"),samples,sampleRecipes,ConfigItems.WAND_ROD_WOOD,ConfigItems.WAND_CAP_IRON,
        staffAttribute(),field(Item.class,null,"itemModifierUUID"),
        field(type(TC+"TCWandAPI"),null,"wandWrappers"),field(type(TC+"TCWandAPI"),null,"caps"));
    Object features=field(type(CONFIG),null,"features"),compat=field(type(CONFIG),null,"modCompat");
    opaque(features);opaque(compat);values.add(features);values.add(compat);
    for(String name:new String[]{"preserveWandVis","allowSingleWandReplacement","infiniteCreativeVis","enforceWandCoreTypes"})scanSetting(field(features,name),values,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()),0);
    Object gtnh=field(compat,"gtnhWands");opaque(gtnh);values.add(gtnh);
    for(String name:new String[]{"coreSwapMaterials","cost"})scanSetting(field(gtnh,name),values,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()),0);
    for(String key:new String[]{"replaceCoreResearch","replaceCapsResearch"}){
      Object research=field(type(ROOT+"common.CustomResearch"),null,key);
      if(!(research instanceof ResearchItem))throw fault("Uninitialized Salis research role");opaque(research);values.add(research);values.add(field(ResearchItem.class,research,"key"));
    }
    for(Object material:materials){values.add(material);values.add(field(type("gregtech.api.enums.Materials"),material,"mName"));}
    for(String ore:ores){Object id=((Map<?,?>)oreNames).get(ore);values.add(id);values.add(id==null?null:NativeOreTable.members(ore));}
    for(Object item:Item.itemRegistry)if(item instanceof ItemWandCasting){
      values.add(item);values.add(Item.itemRegistry.getNameForObject(item));
      if(item.getClass().getName().equals(BRACELET)){
        braceletComponents(values);
      }
    }
    for(Object value:values)if(value!=null&&(type(ROOT+"config.settings.Setting").isInstance(value)||type(ROOT+"config.ConfigGroup").isInstance(value)
        ||value.getClass()==WeakReference.class||value.getClass()==type(ROOT+"mixins.TargetedMod")))opaque(value);
    if(values.size()>4096)throw fault("Wand source binding exceeds root budget");return values.toArray();
  }
  private void braceletComponents(List<Object> values){
        MagicApi.version("SalisWandBindings","thaumicbases");
        for(String name:new String[]{"names","rods","caps","capacity"})values.add(field(type(BRACELET),null,name));
        Object names=field(type(BRACELET),null,"names"),rods=field(type(BRACELET),null,"rods"),caps=field(type(BRACELET),null,"caps"),capacity=field(type(BRACELET),null,"capacity");
        if(names==null||names.getClass()!=String[].class||((String[])names).length!=13||rods==null||rods.getClass()!=WandRod[].class||((WandRod[])rods).length<13
            ||caps==null||caps.getClass()!=WandCap[].class||((WandCap[])caps).length<13||capacity==null||capacity.getClass()!=int[].class||((int[])capacity).length<13)
          throw fault("Invalid bracelet role arrays");
        // TBItems.setup assigns these roles after ItemCastingBracelet.<clinit>.
        // Native getters bypass rod slots 3/11 and cap slot 3, so only those
        // unused snapshot entries may remain null. Bind the arrays themselves
        // as well: a later change must still invalidate the source snapshot.
        WandRod[] nativeRods=(WandRod[])rods;WandCap[] nativeCaps=(WandCap[])caps;
        for(int index=0;index<nativeRods.length;index++)
          if(nativeRods[index]!=null||index!=3&&index!=11)component(nativeRods[index],true);
        for(int index=0;index<nativeCaps.length;index++)
          if(nativeCaps[index]!=null||index!=3)component(nativeCaps[index],false);
        Class<?> roles=type("tb.init.TBItems");for(String name:new String[]{"WAND_ROD_THAUMIUM","WAND_ROD_VOID","WAND_CAP_THAUMINITE"}){
          Object role=field(roles,null,name);component(role,!name.startsWith("WAND_CAP"));values.add(role);
        }
  }
  /** Reproduce enabled/dependency/optional-mod semantics without populating native lazy caches. */
  static boolean enabled(Object group,String name){return scanSetting(field(group,name),null,Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>()),0);}
  private static Object compatibility(){return field(field(type(CONFIG),null,"modCompat"),"gtnhWands");}
  /** Same ordered wrapper/type lookup as Salis, without invoking callbacks or lazy config caches. */
  private static Object lookupWrapper(WandRod rod,boolean staff,boolean sceptre){
    String tag=rod.getTag();
    for(Object wrapper:(List<?>)field(type(TC+"TCWandAPI"),null,"wandWrappers")){
      Object details=field(type(WRAPPER),wrapper,"wandDetails");
      String name=(String)field(details,"name");
      if(wrapper.getClass()==type(TC+"wrappers.StaffWrapper")||wrapper.getClass()==type(TC+"wrappers.StaffSceptreWrapper"))name+="_staff";
      if(!name.equals(tag))continue;
      String wanted=staff?(sceptre?"StaffSceptreWrapper":"StaffWrapper"):(sceptre?"SceptreWrapper":"WandWrapper");
      if(type(TC+"wrappers."+wanted).isInstance(wrapper))return wrapper;
    }return null;
  }
  private Object wrapper(WandRod rod,boolean staff,boolean sceptre){
    Object[] values=wrapperCache.get(rod);
    if(values==null){values=new Object[4];for(int i=0;i<4;i++)values[i]=lookupWrapper(rod,(i&2)!=0,(i&1)!=0);wrapperCache.put(rod,values);}
    return values[(staff?2:0)+(sceptre?1:0)];
  }
  int cost(WandRod rod,WandCap cap,boolean staff,boolean sceptre){
    if(gtCost){
      Object wrapper=wrapper(rod,staff,sceptre),capWrapper=capCache.get(cap);
      if(!capCache.containsKey(cap)){for(Object candidate:(List<?>)field(type(TC+"TCWandAPI"),null,"caps"))if(cap.getTag().equals(field(candidate,"name"))){capWrapper=candidate;break;}capCache.put(cap,capWrapper);}
      if(wrapper==null||capWrapper==null)return -1;
      Object props=field(type(WRAPPER),wrapper,"wandProps");
      int cost=(Integer)field(props,"baseCost")+(Integer)field(props,"capCost")*(Integer)field(capWrapper,"costMultiplier");
      if(type(TC+"wrappers.SceptreWrapper").isInstance(wrapper))cost=(int)(cost*(Float)field(type(TC+"wrappers.SceptreWrapper"),wrapper,"sceptreCostMultiplier"));
      return cost;
    }
    if(cap==ConfigItems.WAND_CAP_IRON&&rod==ConfigItems.WAND_ROD_WOOD)return 0;
    int cost=cap.getCraftCost()*rod.getCraftCost();return sceptre?(int)(cost*1.5F):cost;
  }
  Material material(WandRod rod,boolean staff,boolean sceptre){
    Object wrapper=wrapper(rod,staff,sceptre);if(wrapper==null)return null;
    Object details=field(type(WRAPPER),wrapper,"wandDetails"),tier=field(details,"tier"),material=field(tier,"material");
    ItemStack conductor=(ItemStack)field(details,"conductor");
    return new Material("screw"+(String)field(material,"mName"),conductor==null?null:conductor.copy());
  }
  static final class Material{final String screw;final ItemStack conductor;Material(String screw,ItemStack conductor){this.screw=screw;this.conductor=conductor;}}
  private static boolean scanSetting(Object raw,List<Object> values,Set<Object> seen,int depth){
    if(raw==null||depth>32||!seen.add(raw))throw fault("Invalid Salis setting dependency graph");
    Class<?> owner=callbackOwner(raw,"isEnabled");Class<?> setting=type(ROOT+"config.settings.Setting"),group=type(ROOT+"config.ConfigGroup"),compat=type(ROOT+"config.settings.compat.BaseCompatSetting");
    if(owner!=setting&&owner!=group&&owner!=compat)throw fault("Salis setting needs an enable callback adapter: "+owner.getName());
    if(values!=null){values.add(raw);values.add((Boolean)field(owner==group?group:setting,raw,"enabled"));}
    boolean on=(Boolean)field(owner==group?group:setting,raw,"enabled");
    if(owner==group)return on;
    Object ref=field(setting,raw,"enabledDependency");
    if(ref!=null&&ref.getClass()!=WeakReference.class)throw fault("Unadapted Salis setting weak dependency");
    Object dependency=ref==null?null:((WeakReference<?>)ref).get();
    if(values!=null){values.add(ref);values.add(dependency);}
    boolean parent=ref==null||dependency!=null&&scanSetting(dependency,values,seen,depth+1);
    if(owner==compat){Object mod=field(compat,raw,"mod");exact(mod,ROOT+"mixins.TargetedMod");String id=(String)field(mod,"modId");Object cached=field(mod,"loadState");
      if(cached!=null&&cached.getClass()!=Boolean.class)throw fault("Invalid Salis mod-state cache");
      if(values!=null){values.add(mod);values.add(id);values.add(cached);values.add(cpw.mods.fml.common.Loader.isModLoaded(id));}
      on&=cached==null?cpw.mods.fml.common.Loader.isModLoaded(id):(Boolean)cached;
    }
    return on&&parent;
  }
  private void opaque(Object raw){if(raw!=null&&!shapes.containsKey(raw.getClass().getName()))shapes.put(raw.getClass().getName(),new String[0]);}
  private static Object staffAttribute(){
    Object attribute=net.minecraft.entity.SharedMonsterAttributes.attackDamage;
    exact(attribute,"net.minecraft.entity.ai.attributes.RangedAttribute");
    return attribute;
  }
  private static Class<?> callbackOwner(Object raw,String name,Class<?>... parameters){try{return raw.getClass().getMethod(name,parameters).getDeclaringClass();}catch(NoSuchMethodException error){throw fault("Missing native wand callback: "+name);}}
  private static void callback(Object raw,String name,Class<?> expected,Class<?>... parameters){if(callbackOwner(raw,name,parameters)!=expected)throw fault("Unadapted native wand callback: "+raw.getClass().getName()+"."+name);}
  private static void noShadows(Object raw,Class<?> base,String... fields){for(Class<?> owner=raw.getClass();owner!=base;owner=owner.getSuperclass())for(String field:fields)try{owner.getDeclaredField(field);throw fault("Wand source shadows native member: "+field);}catch(NoSuchFieldException ignored){}}
  private static void exact(Object raw,String name){if(raw==null||raw.getClass()!=type(name))throw fault("Unadapted native wand source: "+name);}
  private Object[] primary(){return new Object[]{WandRod.rods,WandCap.caps,field(type(HELPER),null,"WAND_RODS"),field(type(HELPER),null,"WAND_CAPS"),
      field(type(TC+"TCWandAPI"),null,"wandWrappers"),field(type(TC+"TCWandAPI"),null,"caps"),field(type(CONFIG),null,"features"),field(type(CONFIG),null,"modCompat")};}
  private static int count(Object value){if(value==null)return -1;Class<?> owner=value.getClass();if(owner==HashMap.class||owner==LinkedHashMap.class)return ((Map<?,?>)value).size();if(owner==ArrayList.class)return ((List<?>)value).size();return -1;}
  void verify(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Wand bindings are closed");if(!ItemList.loadFinished||ItemList.items!=itemList||itemList.size()!=itemCount||field(OreDictionary.class,null,"nameToId")!=oreNames||field(OreDictionary.class,null,"idToStack")!=oreStacks)throw changed();Object[] now=primary();for(int i=0;i<now.length;i++)if(now[i]!=primary[i]||count(now[i])!=primarySizes[i])throw changed();}
  void finishVerification(){verify();List<ItemStack> now=new ArrayList<>();for(ItemStack sample:itemList)if(sample!=null&&sample.getItem() instanceof ItemWandCasting)now.add(sample);if(now.size()!=samples.size())throw changed();for(int i=0;i<now.size();i++)if(now.get(i)!=samples.get(i))throw changed();graph.verify();}
  public void close(){closed=true;if(graph!=null)graph.close();shapes.clear();samples.clear();sampleRecipes.clear();materials.clear();ores.clear();wrapperCache.clear();capCache.clear();primary=null;primarySizes=null;itemList=null;oreNames=null;oreStacks=null;}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Salis wand registration, setting, dependency or specimen changed");}
  private static Jobs.Fault fault(String reason){return new Jobs.Fault("recipe_unsupported",reason);}
}
