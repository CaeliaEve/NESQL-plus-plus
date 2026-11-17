package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.crafting.InfusionRecipe;

/** Actual InfusionRecipe.matches inputs. The NEI conversion factory has different predicates. */
final class InfusionLegacyInputs implements AutoCloseable {
  private static final String NETWORK="net.glease.tc4tweak.network.NetworkedConfiguration";
  static final class Context implements AutoCloseable {
    private Object names,stacks,mode;
    private Locale locale;
    private thaumcraft.common.tiles.TilePedestal pedestal;
    private int pedestalLimit;
    private int nameCount,stackCount;
    private final Map<Item,Map<Integer,Binding>> bindings=new IdentityHashMap<>();
    private final Map<String,NativeDefinitionGraph> oreBindings=new TreeMap<>();
    private final Map<Item,List<Member>> memberIndex=new IdentityHashMap<>();
    private int count;
    private boolean closed;
    Context(){
      names=field(OreDictionary.class,null,"nameToId");stacks=field(OreDictionary.class,null,"idToStack");
      mode=field(type(NETWORK),null,"infusionOreDictMode");
      locale=Locale.getDefault();
      if(names==null||stacks==null||mode==null)throw new Jobs.Fault("handler_not_ready","Native legacy infusion sources are not initialized");
      if(!NativeOreStorage.names(names)||stacks.getClass()!=ArrayList.class||!(mode instanceof Enum<?>))
        throw fault("Unadapted native legacy infusion source backing");
      String owner=mode.getClass().getName();
      if(!owner.startsWith("net.glease.tc4tweak.modules.infusionRecipe.InfusionOreDictMode$")
          ||!Arrays.asList("Default","Strict","Relaxed","None").contains(((Enum<?>)mode).name()))throw fault("Unadapted native infusion ore mode");
      nameCount=((Map<?,?>)names).size();stackCount=((List<?>)stacks).size();
      // The pinned constructor only creates owned inventory storage. No world is
      // attached and no native inventory is read or written through this object.
      pedestal=new thaumcraft.common.tiles.TilePedestal();pedestalLimit=pedestal.getInventoryStackLimit();
      if(pedestalLimit<=0)throw fault("Native pedestal has a nonpositive inventory limit");
    }
    String mode(){verify();return ((Enum<?>)mode).name();}
    String lower(String key){verify();return key.toLowerCase(locale);}
    int pedestalLimit(){verify();return pedestalLimit;}
    Binding bind(ItemStack template){
      verify();NativeStackAudit.audit(template);int meta=Items.feather.getDamage(template);
      Map<Integer,Binding> byMeta=bindings.computeIfAbsent(template.getItem(),item->new HashMap<>());
      Binding found=byMeta.get(meta);if(found!=null)return found;
      if(++count>65536)throw fault("Native infusion predicate binding budget exceeded");
      found=new Binding(template.getItem(),meta);byMeta.put(meta,found);return found;
    }
    private List<String> groups(Item item,int metadata){
      SortedSet<String> result=new TreeSet<>();List<Member> members=memberIndex.computeIfAbsent(item,this::readMembers);
      if(members!=null)for(Member entry:members){
        NativeStackAudit.audit(entry.stack);
        // containsMatch(false, {recipeSpec}, ores): the ORE MEMBER is the target.
        int damage=Items.feather.getDamage(entry.stack);if(damage==32767||damage==metadata)result.add(entry.name);
      }
      return new ArrayList<>(result);
    }
    private static final class Member{final String name;final ItemStack stack;Member(String name,ItemStack stack){this.name=name;this.stack=stack;}}
    private List<Member> readMembers(Item item){
      // Default infusion checks actual member lists, which can differ from the baked reverse index.
      // Retain only this predicate's item; unrelated global names consume no binding budget.
      List<Member> result=new ArrayList<>();
      for(Object key:((Map<?,?>)names).keySet()){
        Jobs.checkpoint();if(!(key instanceof String))throw fault("Invalid ore name");
        for(Object raw:NativeOreTable.members((String)key)){
          if(raw==null||raw.getClass()!=ItemStack.class)throw fault("Unadapted native ore member stack");
          ItemStack member=(ItemStack)raw;
          if(member.getItem()!=item)continue;
          if(result.size()>=65536)throw fault("Native infusion predicate membership exceeds budget");
          result.add(new Member((String)key,member));
        }
      }
      return result;
    }
    final class Binding implements AutoCloseable {
      final Item item;final int metadata;final List<String> groups;
      Binding(Item item,int metadata){this.item=item;this.metadata=metadata;groups=groups(item,metadata);
        for(String name:groups)if(!oreBindings.containsKey(name))oreBindings.put(name,new NativeDefinitionGraph(()->new Object[]{NativeOreTable.members(name)},Collections.emptyMap()));}
      void finish(){if(!groups.equals(groups(item,metadata)))throw changed();}
      public void close(){groups.clear();}
    }
    void verify(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Legacy infusion context is closed");
      if(field(OreDictionary.class,null,"nameToId")!=names||field(OreDictionary.class,null,"idToStack")!=stacks
          ||field(type(NETWORK),null,"infusionOreDictMode")!=mode||Locale.getDefault()!=locale||((Map<?,?>)names).size()!=nameCount||((List<?>)stacks).size()!=stackCount
          ||pedestal.getInventoryStackLimit()!=pedestalLimit)throw changed();}
    void finishVerification(){verify();for(NativeDefinitionGraph source:oreBindings.values())source.verify();memberIndex.clear();for(Map<Integer,Binding> row:bindings.values())for(Binding binding:row.values())binding.finish();}
    public void close(){closed=true;for(Map<Integer,Binding> row:bindings.values())for(Binding binding:row.values())binding.close();for(NativeDefinitionGraph source:oreBindings.values())source.close();oreBindings.clear();bindings.clear();memberIndex.clear();names=null;stacks=null;mode=null;locale=null;pedestal=null;}
  }

  private InfusionRecipe recipe;
  private ItemStack center;
  private ItemStack[] components;
  private final boolean centralPredicate;
  private final boolean allowExtra;
  private final boolean itemAndTags;
  private final Context context;
  private final NativeItemExamples examples;
  private final List<Context.Binding> bindings=new ArrayList<>();
  private final Map<String,JsonObject> states=new TreeMap<>();
  private final Map<String,ItemStack> observed=new TreeMap<>();
  private String described;
  private JsonArray enhancedPredicates;
  private long bytes;
  private boolean closed;

  InfusionLegacyInputs(InfusionRecipe recipe,NativeItemExamples examples,Context context,boolean centralPredicate){
    this(recipe,examples,context,centralPredicate,false);
  }
  /** Two symbolic groups generated by native Runic.getComponents(input), not its GUI array. */
  static InfusionLegacyInputs runic(NativeItemExamples examples,Context context){
    if(thaumcraft.common.config.ConfigItems.itemResource==null)throw new Jobs.Fault("handler_not_ready","Native runic resource item is unavailable");
    ItemStack resource=new ItemStack(thaumcraft.common.config.ConfigItems.itemResource,1,14);
    // This owned definition only reuses comparison observations. The native
    // recipe/current ItemResource remain separately bound by Runic.Source.
    InfusionRecipe generated=new InfusionRecipe("",null,0,new thaumcraft.api.aspects.AspectList(),
        new ItemStack(Items.diamond),new ItemStack[]{new ItemStack(Items.diamond),resource});
    return new InfusionLegacyInputs(generated,examples,context,false);
  }
  InfusionLegacyInputs(InfusionRecipe recipe,NativeItemExamples examples,Context context,boolean centralPredicate,boolean allowExtra){
    this(recipe,examples,context,centralPredicate,allowExtra,false);
  }
  InfusionLegacyInputs(InfusionRecipe recipe,NativeItemExamples examples,Context context,boolean centralPredicate,boolean allowExtra,boolean itemAndTags){
    this.recipe=recipe;this.examples=examples;this.context=context;this.centralPredicate=centralPredicate;this.allowExtra=allowExtra;
    this.itemAndTags=itemAndTags;
    center=(ItemStack)field(InfusionRecipe.class,recipe,"recipeInput");
    Object raw=field(InfusionRecipe.class,recipe,"components");
    if(raw==null||raw.getClass()!=ItemStack[].class||((ItemStack[])raw).length>4095)throw fault("Invalid native infusion component array");
    components=(ItemStack[])raw;
    if(center!=null)bindings.add(context.bind(center));else bindings.add(null);
    for(ItemStack stack:components){if(stack==null)throw fault("Native infusion has a null component");bindings.add(context.bind(stack));}
  }
  boolean hasCenter(){return center!=null;}
  int componentCount(){alive();return components.length;}
  int pedestalLimit(){alive();return context.pedestalLimit();}
  ItemStack center(){alive();return center==null?null:center.copy();}
  static JsonObject template(ItemStack stack){
    NativeStackAudit.audit(stack);String registry=Item.itemRegistry.getNameForObject(stack.getItem());
    if(registry==null||Item.itemRegistry.getObject(registry)!=stack.getItem())throw fault("Unregistered native infusion template item");
    return object("registry",registry,"metadata",Items.feather.getDamage(stack),"nbt",TypedNbt.encode(stack.getTagCompound()));
  }
  ItemStack[] centers(){alive();return center==null?new ItemStack[0]:examples.copies(center);}
  List<List<ItemStack>> componentCopies(){
    return componentCopies(false);
  }
  List<List<ItemStack>> cycleCopies(){return componentCopies(true);}
  private List<List<ItemStack>> componentCopies(boolean cycleOnly){
    alive();List<List<ItemStack>> result=new ArrayList<>();int count=0;
    for(int at=0;at<components.length;at++){
      ItemStack template=components[at];Map<String,ItemStack> pool=new TreeMap<>();
      for(ItemStack stack:examples.copies(template))componentCandidate(pool,stack,template,cycleOnly);
      for(String group:bindings.get(at+1).groups)for(ItemStack member:NativeOreTable.copies(group))
        for(ItemStack stack:examples.copies(member))componentCandidate(pool,stack,template,cycleOnly);
      if(pool.isEmpty())throw fault("Reachable native infusion component has no safe specimen");
      count+=pool.size();if(count>65536)throw fault("Native infusion specimens exceed budget");result.add(new ArrayList<>(pool.values()));
    }
    return result;
  }
  private void componentCandidate(Map<String,ItemStack> pool,ItemStack stack,ItemStack template,boolean cycleOnly){
    if(!cycleOnly){candidate(pool,stack,template,2);return;}
    stack.stackSize=1;if(cycleMatches(stack,template))put(pool,stack);
  }
  List<ItemStack> centralCopies(){
    alive();Map<String,ItemStack> pool=new TreeMap<>();
    for(ItemStack stack:centers())if(!centralPredicate||matches(stack,center,1))put(pool,stack);
    if(centralPredicate)for(String group:bindings.get(0).groups)for(ItemStack member:NativeOreTable.copies(group))
      for(ItemStack stack:examples.copies(member))candidate(pool,stack,center,1);
    return new ArrayList<>(pool.values());
  }
  private void candidate(Map<String,ItemStack> pool,ItemStack stack,ItemStack template,int copies){
    stack.stackSize=1;
    if(startupMatches(stack,template,copies)&&(copies!=2||cycleMatches(stack,template)))put(pool,stack);
  }
  private boolean startupMatches(ItemStack offered,ItemStack template,int copies){
    if(!itemAndTags||copies!=2)return matches(offered,template,copies);
    NativeStackAudit.audit(offered);NativeStackAudit.audit(template);
    ItemStack copied=offered.copy().copy();
    // Machina calls its private comparator with fuzzy=false. After the tag
    // subset check it compares ONLY Item identity, including for damaged items.
    return copied.getItem()==template.getItem()&&thaumcraft.api.ThaumcraftApiHelper.areItemStackTagsEqualForCrafting(copied,template);
  }
  private static void put(Map<String,ItemStack> pool,ItemStack stack){
    if(pool.size()>=65536)throw fault("Native infusion specimen pool exceeds budget");
    ItemStack copy=stack.copy();copy.stackSize=1;pool.putIfAbsent(CanonicalJson.digest(RecipeRow.stackEvidence(copy)),copy);
  }
  static boolean matches(ItemStack offered,ItemStack template,int copies){
    NativeStackAudit.audit(offered);NativeStackAudit.audit(template);ItemStack copy=offered;
    for(int i=0;i<copies;i++)copy=copy.copy();copy.stackSize=1;
    if(Items.feather.getDamage(template)==32767)copy.setItemDamage(32767);
    // Native matches copies the offered stacks, but keeps the registered template.
    // NBTBase.toString can observe a compound's native order after a copy.
    return InfusionRecipe.areItemStacksEqual(copy,template,true);
  }
  private static boolean cycleMatches(ItemStack offered,ItemStack template){
    NativeStackAudit.audit(offered);NativeStackAudit.audit(template);
    // craftingStart stores one owned component copy. craftCycle compares the
    // actual pedestal directly, without the startup wildcard metadata rewrite.
    return InfusionRecipe.areItemStacksEqual(offered,template.copy(),true);
  }
  void validateSpecimen(int slot,ItemStack stack){
    alive();if(slot==0){if(centralPredicate&&!matches(stack,center,1))throw fault("Central infusion specimen changed its native comparison state after copying");}
    else if(!startupMatches(stack,components[slot-1],2)||!cycleMatches(stack,components[slot-1]))
      throw fault("Infusion specimen cannot satisfy both startup and pedestal-cycle predicates");
  }
  boolean matchesCycle(List<ItemStack> outer){
    alive();List<ItemStack> remaining=new ArrayList<>(outer);
    for(ItemStack required:components){int first=-1;for(int i=0;i<remaining.size();i++)if(cycleMatches(remaining.get(i),required)){first=i;break;}
      if(first<0)return false;remaining.remove(first);}
    return true;
  }
  boolean acceptsCycleStock(ItemStack stack){
    alive();for(ItemStack required:components)if(cycleMatches(stack,required))return true;return false;
  }
  void observe(Facts facts,ItemStack stack){
    alive();NativeStackAudit.audit(stack);String id=facts.item(stack);
    JsonObject state=state(id,stack);
    JsonObject prior=states.get(id);if(prior!=null){if(!prior.equals(state))throw fault("Canonical item identity has different native infusion comparison observations");return;}
    bytes+=CanonicalJson.bytes(state).length+CanonicalJson.bytes(RecipeRow.stackEvidence(stack)).length;
    if(states.size()>=65536||bytes>32L*1024*1024)throw fault("Native infusion observations exceed budget");
    states.put(id,state);observed.put(id,stack);
  }
  private JsonObject state(String id,ItemStack stack){
    NativeStackAudit.audit(stack);
    ItemStack copied=stack.copy(),pedestal=copied.copy();
    ItemStack wildcard=stack.copy();wildcard.setItemDamage(32767);
    JsonObject lowered=object();for(JsonElement key:augmentKeys(stack))lowered.addProperty(key.getAsString(),context.lower(key.getAsString()));
    return object("id",id,"ores",oreNames(stack),"wildcardOres",oreNames(wildcard),"tagStrings",tagStrings(stack),"copiedTagStrings",tagStrings(copied),"pedestalTagStrings",tagStrings(pedestal),
        "augmentKeys",augmentKeys(stack),"copiedAugmentKeys",augmentKeys(copied),"lowercaseKeys",lowered);
  }
  private static JsonArray oreNames(ItemStack stack){JsonArray result=array();for(int id:OreDictionary.getOreIDs(stack))result.add(value(OreDictionary.getOreName(id)));return result;}
  private static JsonObject tagStrings(ItemStack stack){JsonObject result=object();if(stack.hasTagCompound())for(Object key:stack.getTagCompound().func_150296_c()){
    NBTBase tag=stack.getTagCompound().getTag((String)key);result.addProperty((String)key,tag.toString());}return result;}
  private static JsonArray augmentKeys(ItemStack stack){JsonArray keys=array();if(stack.hasTagCompound())for(Object key:stack.getTagCompound().getCompoundTag("augments").func_150296_c())keys.add(value((String)key));return keys;}
  JsonObject describe(){
    alive();JsonArray required=array(),observations=array();
    JsonObject central=centralPredicate?requirement(center,bindings.get(0),1):null;
    for(int i=0;i<components.length;i++)required.add(requirement(components[i],bindings.get(i+1),2));
    for(JsonObject state:states.values())observations.add(state);
    JsonObject result=object("mode",context.mode(),"startup",enhancedPredicates!=null?"enhanced":itemAndTags?"itemAndTags":"legacy","predicates",enhancedPredicates,"central",central,"components",required,"states",observations,"allowExtra",allowExtra);
    String signature=CanonicalJson.digest(result);if(described!=null&&!described.equals(signature))throw changed();described=signature;return result;
  }
  JsonObject describe(JsonArray predicates){
    alive();if(predicates==null)throw fault("Missing enhanced infusion startup predicates");
    if(enhancedPredicates==null)enhancedPredicates=new JsonParser().parse(predicates.toString()).getAsJsonArray();
    else if(!enhancedPredicates.equals(predicates))throw changed();
    return describe();
  }
  private JsonObject requirement(ItemStack stack,Context.Binding binding,int copies){
    JsonArray ores=array();for(String name:binding.groups)ores.add(value(name));
    // A wildcard predicate is not a renderable item fact. Never request its name,
    // tooltip or renderer merely to describe a registered matching condition.
    return object("template",template(stack),"defaultOres",ores,"ores",oreNames(stack),"tagStrings",tagStrings(stack),"cycleTagStrings",tagStrings(stack.copy()),"copies",copies);
  }
  void verify(){alive();context.verify();if(field(InfusionRecipe.class,recipe,"recipeInput")!=center||field(InfusionRecipe.class,recipe,"components")!=components)throw changed();}
  void finishVerification(){verify();if(described!=null)describe();for(Map.Entry<String,ItemStack> row:observed.entrySet()){
    Jobs.checkpoint();if(!states.get(row.getKey()).equals(state(row.getKey(),row.getValue())))throw changed();}}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Legacy infusion input reader is closed");}
  public void close(){closed=true;recipe=null;center=null;components=null;bindings.clear();states.clear();observed.clear();described=null;enhancedPredicates=null;}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native infusion matcher source changed");}
}
