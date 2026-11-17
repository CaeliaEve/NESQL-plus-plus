package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.Array;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.*;

/** Pinned brew-key dispatch only. Never constructs BrewItemKey or invokes brew/world callbacks. */
final class WitcheryBrewActions implements AutoCloseable {
  static final String KEY="com.emoniph.witchery.brewing.BrewItemKey";
  private static final String BASE="com.emoniph.witchery.brewing.action.BrewAction";
  private Hashtable<?,?> source;
  private Object table;
  private int capacity;
  private final List<Binding> bindings=new ArrayList<>();
  private final Map<Item,String> names=new IdentityHashMap<>();
  private final Map<Item,Map<Integer,List<ItemStack>>> choices=new IdentityHashMap<>();
  private final Map<Object,List<ItemStack>> triggers=new IdentityHashMap<>();
  private final Map<Object,Integer> targets=new IdentityHashMap<>();
  private final NativeItemExamples examples=new NativeItemExamples();
  private long nameBytes;
  private boolean closed;

  WitcheryBrewActions(Object source){
    if(source==null||source.getClass()!=Hashtable.class)throw fault("Unadapted native brew lookup");
    this.source=(Hashtable<?,?>)source;
    try{table=field(Hashtable.class,source,"table");
      if(table==null||table.getClass()!=type("[Ljava.util.Hashtable$Entry;")||(capacity=Array.getLength(table))==0||capacity>262144)throw fault("Unadapted native brew bucket storage");
      bindings.addAll(read());for(Binding binding:bindings)if(!targets.containsKey(binding.actionRef))targets.put(binding.actionRef,targets.size());}
    catch(RuntimeException|Error error){close();throw error;}
  }
  private List<Binding> read(){
    if(source.size()>65536)throw fault("Native brew action budget exceeded");
    List<Binding> result=new ArrayList<>();
    Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());Class<?> entryType=type("java.util.Hashtable$Entry");
    // Same descending bucket/chain order as Hashtable's Enumerator, without entrySet's lazy wrapper write.
    for(int bucket=capacity-1;bucket>=0;bucket--)for(Object entry=Array.get(table,bucket);entry!=null;entry=field(entryType,entry,"next")){
      Jobs.checkpoint();if(entry.getClass()!=entryType||!seen.add(entry)||seen.size()>65536)throw fault("Invalid native brew bucket chain");
      result.add(new Binding(entry,bucket,field(entryType,entry,"key"),field(entryType,entry,"value"),(Integer)field(entryType,entry,"hash")));
    }
    if(result.size()!=source.size())throw changed();
    return result;
  }
  void verify(Object current){alive();if(current!=source||field(Hashtable.class,source,"table")!=table||source.size()!=bindings.size())throw changed();}
  int capacity(){alive();return capacity;}
  void finishVerification(Object current){
    verify(current);List<Binding> now=read();
    for(int i=0;i<bindings.size();i++)if(!bindings.get(i).same(now.get(i)))throw changed();
    for(Map.Entry<Item,String> name:names.entrySet())if(!name.getValue().equals(baseName(name.getKey())))throw changed();
    examples.verify();
  }

  /** Native Hashtable uses its bucket/chain order and the cached key hash, not a sorted key scan. */
  private Binding binding(ItemStack offered){
    alive();
    NativeStackAudit.audit(offered);String name=name(offered.getItem());int hash=629+name.hashCode();
    int bucket=(hash&0x7fffffff)%capacity;
    int metadata=Items.feather.getDamage(offered);
    for(Binding binding:bindings)if(binding.bucket==bucket&&binding.hash==hash&&binding.physical.item==offered.getItem()
        &&(binding.physical.metadata==32767||metadata==32767||metadata==binding.physical.metadata))return binding;
    return null;
  }
  Key resolve(ItemStack offered){Binding binding=binding(offered);return binding==null?null:binding.action;}
  int target(Object action){alive();Integer target=targets.get(action);return target==null?-1:target;}
  List<ItemStack> triggerCandidates(Object action){
    alive();List<ItemStack> stored=triggers.get(action);
    if(stored==null){stored=collect(null,action);triggers.put(action,stored);}return copies(stored);
  }
  List<ItemStack> candidates(Key desired){
    alive();Map<Integer,List<ItemStack>> byMeta=choices.computeIfAbsent(desired.item,ignored->new HashMap<>());
    List<ItemStack> stored=byMeta.get(desired.metadata);if(stored!=null)return copies(stored);
    stored=collect(desired,null);byMeta.put(desired.metadata,stored);return copies(stored);
  }
  private boolean wanted(Binding binding,Key desired,Object target){
    return binding!=null&&(target==null?desired.same(binding.action):binding.actionRef==target);
  }
  private List<ItemStack> collect(Key desired,Object target){
    LinkedHashMap<String,ItemStack> result=new LinkedHashMap<>();
    Set<Item> possible=Collections.newSetFromMap(new IdentityHashMap<Item,Boolean>());
    for(Binding binding:bindings)if(wanted(binding,desired,target)){
      Jobs.checkpoint();possible.add(binding.physical.item);
      if(binding.physical.metadata<0)continue;
      ItemStack template=new ItemStack(binding.physical.item,1,binding.physical.metadata);
      for(ItemStack sample:examples.copies(template))if(wanted(binding(sample),desired,target)){
        sample.stackSize=1;sample.setTagCompound(null);
        result.putIfAbsent(CanonicalJson.digest(RecipeRow.stackEvidence(sample)),sample.copy());
        if(result.size()>65536)throw fault("Native brew specimen budget exceeded");
      }
    }
    if(result.isEmpty())for(Item item:possible)if(reachable(item,desired,target))
      throw fault("Native brew action is reachable but has no safe content specimen");
    return new ArrayList<>(result.values());
  }
  private boolean reachable(Item item,Key desired,Object target){
    SortedSet<Integer> partitions=new TreeSet<>();partitions.add(32767);
    for(Binding binding:bindings)if(binding.physical.item==item&&binding.physical.metadata>=0)partitions.add(binding.physical.metadata);
    int other=0;while(partitions.contains(other))other++;partitions.add(other);
    // Negative metadata is also a literal native partition, even though no safe icon is made for it.
    for(Binding binding:bindings)if(binding.physical.item==item)partitions.add(binding.physical.metadata);
    for(int metadata:partitions)if(wanted(binding(new ItemStack(item,1,metadata)),desired,target))return true;
    return false;
  }
  JsonArray dispatch(Set<Item> used){
    alive();JsonArray result=array();
    for(Binding binding:bindings)if(used.contains(binding.physical.item))result.add(object(
        "registry",binding.physical.registry(),"metadata",binding.physical.metadata,"hash",binding.hash,"bucket",binding.bucket,
        "action",binding.action.json(),"target",targets.get(binding.actionRef),"power",binding.power));
    return result;
  }
  JsonObject names(Set<Item> used){
    alive();JsonObject result=object();
    for(Item item:used)result.addProperty(registered(item),name(item));return result;
  }
  private String name(Item item){
    String result=names.get(item);if(result==null){result=baseName(item);nameBytes+=result.length()*2L;
      if(nameBytes>8L*1024*1024)throw fault("Native brew names exceed cumulative budget");names.put(item,result);}return result;
  }
  private static String baseName(Item item){
    Class<?> owner=ItemCallbacks.method(item,"getUnlocalizedName","func_77658_a").getDeclaringClass();
    if(owner==Item.class)return checkedName(item.getUnlocalizedName());
    if(owner.getName().equals("gregtech.api.items.GTGenericItem")){
      MagicApi.version("WitcheryBrewActions","gregtech_nh");
      // The installed final zero-argument getter returns mName without callbacks.
      return checkedName((String)field(owner,item,"mName"));
    }
    if(owner!=ItemBlock.class)throw fault("Native brew key hash needs an item-name adapter: "+owner.getName());
    Object block=field(ItemBlock.class,item,"field_150939_a");
    if(!(block instanceof Block))throw fault("Missing native brew block role");
    String method=cpw.mods.fml.relauncher.ReflectionHelper.findMethod(Block.class,null,new String[]{"getUnlocalizedName","func_149739_a"}).getName();
    try{if(block.getClass().getMethod(method).getDeclaringClass()!=Block.class)throw fault("Native brew hash needs a block-name adapter");}
    catch(NoSuchMethodException error){throw fault("Missing native brew block-name callback");}
    return checkedName(item.getUnlocalizedName());
  }
  private static String checkedName(String name){if(name==null||name.length()>65536)throw fault("Invalid native brew hash name");return name;}
  private static List<ItemStack> copies(List<ItemStack> stored){List<ItemStack> result=new ArrayList<>();for(ItemStack item:stored)result.add(item.copy());return result;}
  static Key key(Object raw){
    if(raw==null||raw.getClass()!=type(KEY))throw fault("Unadapted native brew key");
    Object item=field(type(KEY),raw,"ITEM");
    if(!(item instanceof Item))throw fault("Native brew key has no item");
    NativeStackAudit.identityOwner(item);
    return new Key((Item)item,(Integer)field(type(KEY),raw,"DAMAGE"));
  }
  static Key key(ItemStack stack){NativeStackAudit.audit(stack);return new Key(stack.getItem(),Items.feather.getDamage(stack));}
  static final class Key{
    final Item item;final int metadata;
    Key(Item item,int metadata){this.item=item;this.metadata=metadata;registered(item);}
    boolean same(Key other){return other!=null&&item==other.item&&metadata==other.metadata;}
    String registry(){return registered(item);}
    JsonObject json(){return object("registry",registry(),"metadata",metadata);}
  }
  private static final class Binding{
    final Object entryRef,physicalRef,actionRef,actionKeyRef,powerRef;
    final Key physical,action;final int hash,keyHash,bucket,power;
    Binding(Object entry,int bucket,Object raw,Object target,int hash){
      if(target==null||!type(BASE).isInstance(target))throw fault("Invalid native brew action value");
      entryRef=entry;this.bucket=bucket;this.hash=hash;physicalRef=raw;actionRef=target;physical=key(raw);
      keyHash=629+(Integer)field(type(KEY),raw,"ITEM_HASH");
      actionKeyRef=field(type(BASE),target,"ITEM_KEY");action=key(actionKeyRef);
      powerRef=field(type(BASE),target,"powerCost");
      Class<?> altar=type("com.emoniph.witchery.brewing.AltarPower");
      if(powerRef==null||!altar.isInstance(powerRef))throw fault("Invalid native brew altar power");
      power=(Integer)field(altar,powerRef,"power");
    }
    boolean same(Binding other){return entryRef==other.entryRef&&physicalRef==other.physicalRef&&actionRef==other.actionRef&&actionKeyRef==other.actionKeyRef
        &&powerRef==other.powerRef&&physical.same(other.physical)&&action.same(other.action)&&hash==other.hash&&keyHash==other.keyHash&&bucket==other.bucket&&power==other.power;}
  }
  private static String registered(Item item){String name=Item.itemRegistry.getNameForObject(item);if(name==null||name.length()>4096)throw fault("Unregistered native brew item");return name;}
  private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Native brew lookup is closed");}
  public void close(){closed=true;bindings.clear();names.clear();choices.clear();triggers.clear();targets.clear();nameBytes=0;examples.close();source=null;table=null;capacity=0;}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native brew dispatch, key hash or action binding changed");}
}
