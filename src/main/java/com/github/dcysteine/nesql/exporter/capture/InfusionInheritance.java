package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.ref.Reference;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import thaumcraft.api.crafting.InfusionRecipe;

/** Bound TC4Tweaks inheritance with no ClassValue, weak-map or conversion-cache writes. */
final class InfusionInheritance implements AutoCloseable {
  private static final String CARRY="net.glease.tc4tweak.modules.infusionRecipe.InfusionRecipeGetOutput";
  private static final String CONFIG="net.glease.tc4tweak.ConfigurationHandler";
  private static final String BEHAVIOR="net.glease.tc4tweak.api.InfusionExtAPI$RecipeNBTBehavior";
  private Object config,merger;
  private WeakHashMap<?,?> overrides;
  private Set<?> nativeKeys;
  private SortedSet<String> keys;
  private boolean carry,tools,closed;
  private NativeDefinitionGraph graph;
  private final Set<InfusionRecipe> recipes=Collections.newSetFromMap(new IdentityHashMap<InfusionRecipe,Boolean>());
  private final Map<Item,ToolBinding> toolBindings=new IdentityHashMap<>();
  private final NativeBatteryCapacities capacities=new NativeBatteryCapacities();

  static final class ToolResult {
    final boolean eligible;final JsonObject rule;
    ToolResult(boolean eligible,JsonObject rule){this.eligible=eligible;this.rule=rule;}
  }
  static final class Projection {
    final ItemStack output;final JsonObject queries;
    Projection(ItemStack output,JsonObject queries){this.output=output;this.queries=queries;}
  }

  InfusionInheritance(){
    try{
      config=field(type(CONFIG),null,"INSTANCE");if(config==null||config.getClass()!=type(CONFIG))throw fault("Uninitialized infusion inheritance configuration");
      Object raw=field(type(CARRY),null,"overrides");if(raw==null||raw.getClass()!=WeakHashMap.class)throw fault("Unadapted infusion override registry");overrides=(WeakHashMap<?,?>)raw;
      carry=(Boolean)field(type(CONFIG),config,"infusionRecipeNBTCarryOver");tools=(Boolean)field(type(CONFIG),config,"infusionRecipeNBTModifyArmorToolOnly");
      merger=field(type(CARRY),null,"MERGER");
      Object probe=invoke(type(BEHAVIOR),null,"mergeNBT",new Class<?>[]{Set.class},new HashSet<String>());
      if(merger==null||merger.getClass()!=probe.getClass())throw fault("Infusion merger needs an explicit behavior adapter");
      Field capture=null;for(Field field:probe.getClass().getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())){
        if(capture!=null||field.getType()!=Set.class||!Modifier.isFinal(field.getModifiers()))throw fault("Unadapted pinned inheritance closure");capture=field;
      }
      if(capture==null)throw fault("Missing pinned inheritance key capture");capture.setAccessible(true);
      try{raw=capture.get(merger);}catch(IllegalAccessException error){throw fault("Cannot read pinned inheritance keys");}
      if(raw!=null&&raw.getClass()!=HashSet.class)throw fault("Unadapted native inheritance key filter");nativeKeys=(Set<?>)raw;
      keys=nativeKeys==null?null:readKeys(nativeKeys);
      if(keys!=null&&keys.isEmpty())throw fault("Native reload produced an unexpected non-null empty inheritance filter");
      Map<String,String[]> shapes=new HashMap<>();shapes.put(config.getClass().getName(),new String[0]);
      graph=new NativeDefinitionGraph(this::roots,shapes);
    }catch(RuntimeException|Error error){close();throw error;}
  }
  private Object[] roots(){return new Object[]{field(type(CONFIG),null,"INSTANCE"),
      field(type(CONFIG),config,"infusionRecipeNBTCarryOver"),field(type(CONFIG),config,"infusionRecipeNBTModifyArmorToolOnly"),
      field(type(CONFIG),config,"infusionRecipeNBTWhitelist"),nativeKeys};}
  private static SortedSet<String> readKeys(Set<?> source){
    if(source.size()>4096)throw fault("Infusion inheritance key budget exceeded");SortedSet<String> result=new TreeSet<>();long bytes=0;
    for(Object key:source){if(!(key instanceof String)||((String)key).length()>65535)throw fault("Invalid native inheritance key");bytes+=((String)key).length()*2L;if(bytes>4L*1024*1024)throw fault("Inheritance keys exceed byte budget");result.add((String)key);}return result;
  }
  /** WeakHashMap.get/iteration expunges stale entries. Traverse its pinned JDK storage read-only. */
  private Object override(InfusionRecipe recipe){
    NativeStackAudit.identityOwner(recipe);Object table=field(WeakHashMap.class,overrides,"table");
    if(table==null||table.getClass()!=type("[Ljava.util.WeakHashMap$Entry;"))throw fault("Unadapted infusion weak-map backing");
    int length=Array.getLength(table);if(length==0||length>65536||(length&(length-1))!=0)throw fault("Invalid native weak-map capacity");
    int hash=System.identityHashCode(recipe);hash^=(hash>>>20)^(hash>>>12);hash^=(hash>>>7)^(hash>>>4);
    Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    for(Object entry=Array.get(table,hash&(length-1));entry!=null;entry=field(type("java.util.WeakHashMap$Entry"),entry,"next")){
      if(entry.getClass()!=type("java.util.WeakHashMap$Entry")||!seen.add(entry)||seen.size()>65536)throw fault("Invalid infusion override chain");
      if((Integer)field(type("java.util.WeakHashMap$Entry"),entry,"hash")==hash&&((Reference<?>)entry).get()==recipe)return field(type("java.util.WeakHashMap$Entry"),entry,"value");
    }return null;
  }
  private boolean hasCustomOutput(InfusionRecipe recipe){
    try{return recipe.getClass().getMethod("getRecipeOutput",ItemStack.class).getDeclaringClass()!=InfusionRecipe.class;}
    catch(NoSuchMethodException error){throw fault("Missing native infusion output method");}
  }
  /** TileInfusionMatrix's output hook checks API overrides before the custom-output exemption. */
  void bindNativeOutput(InfusionRecipe recipe){
    alive();if(override(recipe)!=null)throw fault("Native infusion output has an unadapted API override: "+recipe.getClass().getName());recipes.add(recipe);
  }
  JsonObject describe(InfusionRecipe recipe,ItemStack base,Facts facts){
    alive();if(override(recipe)!=null)throw fault("Infusion output has an unadapted custom override: "+recipe.getClass().getName());recipes.add(recipe);
    if(!carry||hasCustomOutput(recipe))return null;
    JsonArray filter=array();if(keys!=null)for(String key:keys)filter.add(value(key));
    return object("kind","nativeInfusionMerge","base",object("id",facts.item(base),"amount",Integer.toString(base.stackSize)),
        "keys",keys==null?null:filter,"tools",tools,"queries",tools?object():null);
  }
  Projection project(InfusionRecipe recipe,ItemStack offered,ItemStack declared){
    alive();NativeStackAudit.audit(offered);NativeStackAudit.audit(declared);ItemStack input=offered.copy(),base=declared.copy();
    if(!carry||hasCustomOutput(recipe))return new Projection(base,null);
    JsonObject queries=null;
    if(tools){
      ToolResult central=queryOwned(input);queries=object("input",central.rule,"output",null);
      if(!central.eligible)return new Projection(base,queries);
      ToolResult output=queryOwned(base);queries.add("output",output.rule);
      if(!output.eligible)return new Projection(base,queries);
    }
    if(!input.hasTagCompound()||input.getTagCompound().hasNoTags())return new Projection(base,queries);
    ItemStack result=base.copy();
    if(base.hasTagCompound())merge(input.getTagCompound(),result.getTagCompound(),keys,0,new int[1]);
    else{
      // The pinned native filtered branch dereferences its freshly untagged output.
      if(keys!=null)throw fault("Native filtered inheritance into an untagged output would fail");
      result=new ItemStack(base.getItem(),input.stackSize,net.minecraft.init.Items.feather.getDamage(input));result.setTagCompound((NBTTagCompound)input.getTagCompound().copy());
    }return new Projection(result,queries);
  }
  private static void merge(NBTTagCompound source,NBTTagCompound dest,Set<String> filter,int depth,int[] nodes){
    if(depth>48)throw fault("Infusion inheritance depth exceeded");
    for(Object raw:source.func_150296_c()){
      Jobs.checkpoint();if(++nodes[0]>65536)throw fault("Infusion inheritance node budget exceeded");String key=(String)raw;if(filter!=null&&!filter.contains(key))continue;
      NBTBase from=source.getTag(key),to=dest.getTag(key);
      if(to==null)dest.setTag(key,from.copy());
      else if(from.getId()==to.getId()){
        if(from instanceof NBTTagCompound)merge((NBTTagCompound)from,(NBTTagCompound)to,null,depth+1,nodes);
        else if(from instanceof NBTTagList){NBTTagList incoming=(NBTTagList)from.copy(),out=(NBTTagList)to;
          if(incoming.func_150303_d()==out.func_150303_d())while(incoming.tagCount()>0){if(++nodes[0]>65536)throw fault("Infusion list budget exceeded");out.appendTag(incoming.removeTag(0));}}
      }
    }
  }
  private ToolResult queryOwned(ItemStack item){
    NativeStackAudit.audit(item);if(item.getItem() instanceof ItemArmor)return new ToolResult(true,object("kind","stable","eligible",true));
    ToolBinding binding=toolBindings.get(item.getItem());if(binding==null){if(toolBindings.size()>=65536)throw fault("Infusion tool binding budget exceeded");binding=new ToolBinding(item.getItem());toolBindings.put(item.getItem(),binding);}return binding.queryOwned(item,capacities);
  }
  private static final class ToolBinding{
    private static final Set<String> CONSTANT=new HashSet<>(Arrays.asList(
      "com.thecodewarrior.catwalks.item.ItemBlowtorch","gravisuite.ItemAdvChainsaw","gravisuite.ItemAdvDDrill",
      "chylex.hee.item.ItemScorchingPickaxe",
      "thaumcraft.common.items.equipment.ItemElementalAxe","thaumcraft.common.items.equipment.ItemElementalPickaxe",
      "thaumcraft.common.items.equipment.ItemElementalShovel","thaumcraft.common.items.equipment.ItemPrimalCrusher",
      "thaumcraft.common.items.equipment.ItemThaumiumAxe","thaumcraft.common.items.equipment.ItemThaumiumPickaxe",
      "thaumcraft.common.items.equipment.ItemThaumiumShovel","thaumcraft.common.items.equipment.ItemVoidAxe",
      "thaumcraft.common.items.equipment.ItemVoidPickaxe","thaumcraft.common.items.equipment.ItemVoidShovel",
      "com.riciJak.Ztones.item.tool.ItemDiamondZane","com.riciJak.Ztones.item.tool.ItemSplatAxe","com.riciJak.Ztones.item.tool.ItemTerrainEater"));
    private static final String COFH="cofh.core.item.tool.ItemToolAdv";
    private static final String DARK_STEEL="crazypants.enderio.item.darksteel.ItemDarkSteelPickaxe";
    private static final String SPOON="crazypants.enderio.item.darksteel.upgrade.SpoonUpgrade";
    private static final String SPELL="WayofTime.alchemicalWizardry.api.items.ItemSpellMultiTool";
    private static final String NANO="de.katzenpapst.amunra.item.ItemNanotool";
    private static final String AMUN="de.katzenpapst.amunra.item.ItemAbstractBatteryUser";
    final Item item;private Map<?,?> source;private Map<String,Integer> values;final boolean harvest,constant,cofh,darkSteel,itemTool,spell,nano;
    final Set<?> extra;final Object wrapper;final Set<String> extraNames;
    final String spoon,toolClass;
    private final Map<Integer,NanoMode> modes=new HashMap<>();
    ToolBinding(Item item){
      this.item=item;Class<?> owner=ItemCallbacks.method(item,"getToolClasses","getToolClasses",ItemStack.class).getDeclaringClass();
      harvest=owner.getName().equals("tconstruct.library.tools.HarvestTool");
      constant=CONSTANT.contains(owner.getName());cofh=owner.getName().equals(COFH);darkSteel=owner.getName().equals(DARK_STEEL);
      spell=owner.getName().equals(SPELL);nano=owner.getName().equals(NANO);
      if(spell&&NativeBatteryCapacities.method(item,"getHarvestLevel",ItemStack.class,String.class)!=type(SPELL))throw fault("BloodMagic spell tool overrides its harvest-level query");
      if(nano&&(NativeBatteryCapacities.method(item,"getElectricityStored",ItemStack.class)!=type("micdoodle8.mods.galacticraft.core.energy.item.ItemElectricBase")
          ||NativeBatteryCapacities.method(item,"getMaxElectricityStored",ItemStack.class)!=type(AMUN)
          ||NativeBatteryCapacities.method(item,"setDamage",ItemStack.class,int.class)!=Item.class))throw fault("AmunRa tool overrides its native energy/metadata query");
      itemTool=owner==ItemTool.class||cofh;
      if(itemTool){Object raw=field(ItemTool.class,item,"toolClass");if(raw!=null&&(!(raw instanceof String)||((String)raw).length()>256))throw fault("Invalid native ItemTool class");toolClass=(String)raw;}
      else toolClass=null;
      if(darkSteel){Object raw=field(type(SPOON),null,"UPGRADE_NAME");if(!(raw instanceof String)||((String)raw).length()>256)throw fault("Invalid native spoon upgrade key");spoon=(String)raw;}
      else spoon=null;
      if(cofh){
        Object raw=field(type(COFH),item,"toolClasses");
        if(raw==null||!raw.getClass().getName().equals("gnu.trove.set.hash.TLinkedHashSet"))throw fault("Unadapted CoFH tool class set");
        extra=(Set<?>)raw;wrapper=field(type(COFH),item,"immutableClasses");
        if(wrapper==null||!wrapper.getClass().getName().equals("java.util.Collections$UnmodifiableSet")||field(wrapper,"c")!=extra)
          throw fault("CoFH immutable tool classes no longer wrap their native set");
        extraNames=readNames(extra);
      }else{extra=null;wrapper=null;extraNames=Collections.emptySet();}
      if(harvest||constant||darkSteel||spell||nano){source=null;values=Collections.emptyMap();return;}
      if(owner!=Item.class&&!itemTool)throw fault("Infusion tool classification needs an adapter: "+item.getClass().getName());
      Object raw=field(Item.class,item,"toolClasses");if(raw==null||raw.getClass()!=HashMap.class)throw fault("Unadapted native item tool classes");source=(Map<?,?>)raw;values=read(source);
    }
    boolean applies(ItemStack stack){
      if(stack==null||stack.getItem()!=item)throw fault("Infusion tool predicate received another item");
      if(spell||nano)throw fault("Stateful tool classification requires its owned query and effects");
      if(constant)return true;
      if(darkSteel){
        // Native always includes pickaxe, but still loads the spoon tag before
        // returning. Do not turn its compound cast failure into success.
        String key="enderio.darksteel.upgrade."+spoon;
        if(stack.hasTagCompound()&&stack.getTagCompound().hasKey(key)&&!(stack.getTagCompound().getTag(key) instanceof NBTTagCompound))
          throw fault("Native spoon upgrade tag would fail its compound cast");
        return true;
      }
      if(cofh&&!extraNames.isEmpty())return true;
      if(itemTool&&toolClass!=null)return true;
      if(!harvest)return !values.isEmpty();
      // The installed 1.13.57 getToolClasses only checks stack/item type. NBT
      // and Broken affect getHarvestLevel, which is a different predicate.
      for(Class<?> current=item.getClass();current!=null;current=current.getSuperclass())try{
        current.getDeclaredMethod("getHarvestType");
        if(!(Arrays.asList("Battleaxe","Excavator","Hammer","Hatchet","Mattock","Pickaxe","LumberAxe","Shovel").contains(current.getSimpleName())
            &&current.getName().equals("tconstruct.items.tools."+current.getSimpleName()))
            &&!Arrays.asList("gmail.Lance5057.items.HeaterShield","gmail.Lance5057.items.RoundShield").contains(current.getName()))throw fault("Infusion harvest type needs an adapter: "+current.getName());
        return true; // Each admitted getter returns one constant, non-null class name.
      }catch(NoSuchMethodException absent){ }
      throw fault("Missing native harvest type getter");
    }
    ToolResult queryOwned(ItemStack stack,NativeBatteryCapacities capacities){
      if(stack==null||stack.getItem()!=item)throw fault("Infusion tool query received another item");NativeStackAudit.audit(stack);
      if(spell){
        if(!stack.hasTagCompound())stack.setTagCompound(new NBTTagCompound());NBTTagCompound stats=stack.getTagCompound().getCompoundTag("BloodMagicTool");
        boolean eligible=false;for(String key:new String[]{"pickaxe","axe","shovel"})if(stats.hasKey("harvestLvl"+key)&&stats.getInteger("harvestLvl"+key)>-1)eligible=true;
        return new ToolResult(eligible,object("kind","spell"));
      }
      if(nano){
        if(!stack.hasTagCompound())stack.setTagCompound(new NBTTagCompound());NBTTagCompound tags=stack.getTagCompound();NBTBase stored=tags.getTag("electricity");
        float energy=stored instanceof NBTTagDouble?((NBTTagDouble)stored).func_150288_h():stored instanceof NBTTagFloat?((NBTTagFloat)stored).func_150288_h():0F;
        NativeBatteryCapacities.Value maximum=capacities.resolveOwned(stack);
        net.minecraft.init.Items.feather.setDamage(stack,(int)(100F-energy/maximum.capacity*100F));
        if(!(energy>0F)){
          if(source==null)bindBase();return new ToolResult(!values.isEmpty(),object("kind","nanotool","capacity",maximum.rule,"mode",null,"nonempty",null,"fallback",!values.isEmpty()));
        }
        if(NativeBatteryCapacities.method(item,"getMode",ItemStack.class)!=type(NANO)||protectedOwner(item,"getModeInt",ItemStack.class)!=type(NANO))throw fault("AmunRa tool overrides its mode query");
        int mode=tags.getInteger("toolMode");NanoMode observation=modes.get(mode);
        if(observation==null){observation=new NanoMode(item,mode);modes.put(mode,observation);}else observation.verify();
        return new ToolResult(observation.nonempty,object("kind","nanotool","capacity",maximum.rule,"mode",mode,"nonempty",observation.nonempty,"fallback",null));
      }
      boolean eligible=applies(stack);
      return new ToolResult(eligible,darkSteel?object("kind","darkSteel","spoon",spoon):object("kind","stable","eligible",eligible));
    }
    private void bindBase(){Object raw=field(Item.class,item,"toolClasses");if(raw==null||raw.getClass()!=HashMap.class)throw fault("Unadapted native item tool classes");source=(Map<?,?>)raw;values=read(source);}
    private static Class<?> protectedOwner(Item item,String method,Class<?>...args){for(Class<?> owner=item.getClass();owner!=null;owner=owner.getSuperclass())try{owner.getDeclaredMethod(method,args);return owner;}catch(NoSuchMethodException absent){}throw fault("Missing native tool method: "+method);}
    private static final class NanoMode {
      final Item item;final int mode;final Object[] values;final Object selected,root,set;final Set<String> names;final boolean nonempty;
      NanoMode(Item item,int mode){
        this.item=item;this.mode=mode;Object raw=field(type(NANO+"$Mode"),null,"$VALUES");
        if(!(raw instanceof Object[])||((Object[])raw).length!=7||mode<0||mode>=7)throw fault("Native nanotool mode would fail its array lookup");
        values=(Object[])raw;selected=values[mode];if(selected==null||selected.getClass()!=type(NANO+"$Mode"))throw fault("Invalid native nanotool mode enum");
        root=field(type(NANO),item,"toolClassesSet");if(root==null||root.getClass()!=HashMap.class||((Map<?,?>)root).size()>256)throw fault("Unadapted native nanotool class map");
        set=((Map<?,?>)root).get(selected);if(set==null)throw fault("Native nanotool class set would fail its null dereference");
        if(set.getClass()!=HashSet.class)throw fault("Unadapted native nanotool class set");names=readNames((Set<?>)set);nonempty=!names.isEmpty();
      }
      void verify(){if(field(type(NANO+"$Mode"),null,"$VALUES")!=values||values[mode]!=selected||field(type(NANO),item,"toolClassesSet")!=root
          ||((Map<?,?>)root).get(selected)!=set||!names.equals(readNames((Set<?>)set)))throw changed();}
    }
    private static Map<String,Integer> read(Map<?,?> source){if(source.size()>256)throw fault("Infusion tool classes exceed budget");Map<String,Integer> values=new TreeMap<>();
      for(Map.Entry<?,?> entry:source.entrySet()){if(!(entry.getKey() instanceof String)||((String)entry.getKey()).length()>256||!(entry.getValue() instanceof Integer))throw fault("Invalid native tool class");values.put((String)entry.getKey(),(Integer)entry.getValue());}return values;}
    private static Set<String> readNames(Set<?> source){
      if(source.size()>256)throw fault("CoFH tool classes exceed budget");Set<String> values=new TreeSet<>();
      for(Object value:source){if(!(value instanceof String)||((String)value).length()>256)throw fault("Invalid CoFH tool class");values.add((String)value);}return values;
    }
    void verify(){
      if(darkSteel&&!Objects.equals(field(type(SPOON),null,"UPGRADE_NAME"),spoon))throw changed();
      if(itemTool&&!Objects.equals(field(ItemTool.class,item,"toolClass"),toolClass))throw changed();
      if(source!=null&&(field(Item.class,item,"toolClasses")!=source||!values.equals(read(source))))throw changed();
      if(cofh&&(field(type(COFH),item,"toolClasses")!=extra||field(type(COFH),item,"immutableClasses")!=wrapper||field(wrapper,"c")!=extra
          ||!extraNames.equals(readNames(extra))))throw changed();
      for(NanoMode mode:modes.values())mode.verify();
    }
  }
  void verify(){alive();if(field(type(CARRY),null,"overrides")!=overrides||field(type(CARRY),null,"MERGER")!=merger)throw changed();graph.verifyRoots();capacities.verify();}
  void finishVerification(){verify();graph.verify();capacities.finishVerification();for(InfusionRecipe recipe:recipes)if(override(recipe)!=null)throw changed();for(ToolBinding tool:toolBindings.values())tool.verify();}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Infusion inheritance is closed");}
  public void close(){closed=true;if(graph!=null)graph.close();capacities.close();recipes.clear();toolBindings.clear();if(keys!=null)keys.clear();keys=null;nativeKeys=null;config=null;merger=null;overrides=null;}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native inheritance configuration, merger, override or tool binding changed");}
}
