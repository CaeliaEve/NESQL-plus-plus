package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.Array;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Same-session source binding for explicitly declared native fields. This is not a recipe reader:
 * adapters still own predicates, callbacks, selection, consumption and output semantics.
 */
final class NativeDefinitionGraph implements AutoCloseable {
  interface Roots {Object[] read();}
  private final Roots reader;
  private final Map<String,String[]> fields;
  private Object[] roots;
  private int[] sizes;
  private List<Object> references;
  private String signature;

  NativeDefinitionGraph(Roots reader,Map<String,String[]> fields){
    this.reader=reader;this.fields=new HashMap<>(fields);roots=reader.read();
    if(roots==null||roots.length>4096)throw fault("Invalid native definition roots");
    sizes=new int[roots.length];for(int i=0;i<roots.length;i++)sizes[i]=size(roots[i]);
    Snapshot snapshot=observe();references=snapshot.references;signature=snapshot.signature;
  }
  static Map<String,String[]> shapes(String owner,String... fields){Map<String,String[]> map=new HashMap<>();map.put(owner,fields);return map;}
  void verifyRoots(){
    alive();Object[] current=reader.read();if(current==null||current.length!=roots.length)throw changed();
    for(int i=0;i<roots.length;i++)if(!sameRoot(current[i],roots[i])||size(current[i])!=sizes[i])throw changed();
  }
  void verify(){
    verifyRoots();Snapshot current=observe();
    if(!signature.equals(current.signature)||references.size()!=current.references.size())throw changed();
    for(int i=0;i<references.size();i++)if(references.get(i)!=current.references.get(i))throw changed();
  }
  private Snapshot observe(){Snapshot snapshot=new Snapshot();JsonArray values=array();for(Object root:roots)values.add(snapshot.value(root,0));if(CanonicalJson.bytes(values).length>32*1024*1024)throw fault("Native definition snapshot exceeds byte budget");snapshot.signature=CanonicalJson.digest(values);return snapshot;}
  private static int size(Object root){
    if(root==null)return -1;Class<?> owner=root.getClass();
    // Do not call virtual methods on an unadmitted collection during the cheap guard.
    if(NativeOreStorage.oreView(root))return NativeOreStorage.arrayList(root).size();
    if(owner==ArrayList.class||owner==LinkedList.class||owner==HashSet.class||owner==LinkedHashSet.class||NativeOreStorage.optimizedList(root))return ((Collection<?>)root).size();
    if(owner==HashMap.class||owner==LinkedHashMap.class||owner==IdentityHashMap.class||owner==Hashtable.class||NativeOreStorage.optimizedMap(root))return ((Map<?,?>)root).size();
    return owner.isArray()?Array.getLength(root):-1;
  }
  /** Exact immutable scalar values; all registry/source objects retain identity semantics. */
  static boolean sameRoot(Object left,Object right){
    if(left==right)return true;if(left==null||right==null||left.getClass()!=right.getClass())return false;
    Class<?> type=left.getClass();
    if(type==Float.class)return Float.floatToRawIntBits((Float)left)==Float.floatToRawIntBits((Float)right);
    if(type==Double.class)return Double.doubleToRawLongBits((Double)left)==Double.doubleToRawLongBits((Double)right);
    return (type==String.class||type==Integer.class||type==Long.class||type==Short.class||type==Byte.class||type==Boolean.class||type==Character.class)&&left.equals(right);
  }

  private final class Snapshot {
    final List<Object> references=new ArrayList<>();
    final IdentityHashMap<Object,Integer> seen=new IdentityHashMap<>();
    int nodes;long bytes;String signature;
    void pin(Object value){if(references.size()>=262144)throw fault("Native definition identity budget exceeded");references.add(value);}
    JsonElement value(Object raw,int depth){
      Jobs.checkpoint();if(++nodes>262144||depth>48)throw fault("Native definition graph exceeds budget");
      if(raw==null)return JsonNull.INSTANCE;
      Class<?> owner=raw.getClass();
      if(owner==String.class){bytes+=((String)raw).length()*2L;if(((String)raw).length()>1048576||bytes>32L*1024*1024)throw fault("Native definition text exceeds budget");return com.github.dcysteine.nesql.exporter.source.Json.value((String)raw);}
      if(owner==Float.class)return object("f32",String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits((Float)raw)));
      if(owner==Double.class)return object("f64",String.format(Locale.ROOT,"%016x",Double.doubleToRawLongBits((Double)raw)));
      if(owner==Byte.class||owner==Short.class||owner==Integer.class||owner==Long.class||owner==Boolean.class||owner==Character.class)return object("scalar",owner.getName(),"value",raw.toString());
      pin(raw);Integer prior=seen.get(raw);if(prior!=null)return object("reference",prior);
      int ordinal=seen.size();seen.put(raw,ordinal);
      if(raw instanceof Item||raw instanceof Block||raw instanceof Fluid){NativeStackAudit.identityOwner(raw);return object("leaf",owner.getName(),"reference",ordinal);}
      if(owner==ItemStack.class){ItemStack stack=(ItemStack)raw;NativeStackAudit.auditRaw(stack);JsonElement item=value(stack.getItem(),depth+1);JsonElement tag=nbt(stack.getTagCompound(),depth+1);return object("item",item,"metadata",net.minecraft.init.Items.feather.getDamage(stack),"amount",stack.stackSize,"nbt",tag);}
      if(owner==FluidStack.class){FluidStack stack=(FluidStack)raw;return object("fluid",value(stack.getFluid(),depth+1),"amount",stack.amount,"nbt",nbt(stack.tag,depth+1));}
      if(raw instanceof NBTBase)return nbt((NBTBase)raw,depth+1);
      if(owner.isEnum()){if(!fields.containsKey(owner.getName()))throw fault("Unadapted native enum owner: "+owner.getName());return object("enum",owner.getName(),"ordinal",((Enum<?>)raw).ordinal());}
      if(owner.isArray()){
        int length=Array.getLength(raw);if(length>262144)throw fault("Native definition array exceeds budget");JsonArray array=array();
        for(int i=0;i<length;i++)array.add(value(Array.get(raw,i),depth+1));return object("array",owner.getName(),"values",array);
      }
      if(NativeOreStorage.oreView(raw))return object("oreView",value(NativeOreStorage.arrayList(raw),depth+1));
      if(owner==ArrayList.class||owner==LinkedList.class||owner==HashSet.class||owner==LinkedHashSet.class||owner.getName().equals("java.util.Arrays$ArrayList")||NativeOreStorage.optimizedList(raw)){
        Collection<?> list=(Collection<?>)raw;if(list.size()>262144)throw fault("Native definition collection exceeds budget");JsonArray values=array();for(Object child:list)values.add(value(child,depth+1));return object("collection",owner.getName(),"values",values);
      }
      if(owner==HashMap.class||owner==LinkedHashMap.class||owner==IdentityHashMap.class||owner==TreeMap.class||owner==Hashtable.class
          ||owner.getName().equals("java.util.Collections$SingletonMap")
          ||owner.getName().equals("com.google.common.collect.RegularImmutableMap")||owner.getName().equals("com.google.common.collect.SingletonImmutableBiMap")||owner.getName().equals("com.google.common.collect.EmptyImmutableBiMap")||NativeOreStorage.optimizedMap(raw)){
        if(owner==TreeMap.class&&((TreeMap<?,?>)raw).comparator()!=null)throw fault("Unadapted native map comparator");
        Map<?,?> map=(Map<?,?>)raw;if(map.size()>262144)throw fault("Native definition map exceeds budget");JsonArray values=array();for(Map.Entry<?,?> entry:map.entrySet())values.add(object("key",value(entry.getKey(),depth+1),"value",value(entry.getValue(),depth+1)));
        JsonObject result=object("map",owner.getName(),"values",values);
        if(NativeOreStorage.optimizedMap(raw))result.add("nativeDefault",value(NativeOreStorage.defaultValue(raw),depth+1));
        return result;
      }
      Class<?> definition=owner;
      if(raw instanceof thaumcraft.api.aspects.Aspect && fields.containsKey(thaumcraft.api.aspects.Aspect.class.getName())
          && Aspects.nativeDefinition((thaumcraft.api.aspects.Aspect)raw))definition=thaumcraft.api.aspects.Aspect.class;
      String[] members=fields.get(definition.getName());if(members==null||owner!=MagicApi.type(owner.getName()))throw fault("Unadapted native definition owner: "+owner.getName());
      JsonObject value=object("owner",owner.getName());for(String member:members)value.add(member,value(field(definition,raw,member),depth+1));return value;
    }
    JsonElement nbt(NBTBase tag,int depth){
      NativeStackAudit.nbt(tag);JsonElement data=TypedNbt.encode(tag);bytes+=CanonicalJson.bytes(data).length;
      if(bytes>32L*1024*1024)throw fault("Native definition NBT exceeds cumulative budget");nbtNodes(tag,depth);return data;
    }
    void nbtNodes(NBTBase tag,int depth){
      if(tag==null)return;if(++nodes>262144||depth>48)throw fault("Native definition NBT graph exceeds budget");pin(tag);
      if(tag.getClass()==NBTTagCompound.class){Map<?,?> map=(Map<?,?>)field(tag,"tagMap");pin(map);for(Object child:map.values())nbtNodes((NBTBase)child,depth+1);}
      else if(tag.getClass()==NBTTagList.class){List<?> list=(List<?>)field(tag,"tagList");pin(list);for(Object child:list)nbtNodes((NBTBase)child,depth+1);}
      else if(tag.getClass()==NBTTagByteArray.class)pin(((NBTTagByteArray)tag).func_150292_c());
      else if(tag.getClass()==NBTTagIntArray.class)pin(((NBTTagIntArray)tag).func_150302_c());
    }
  }
  private void alive(){Jobs.checkpoint();if(roots==null)throw new Jobs.Fault("capture_closed","Native source bindings are closed");}
  public void close(){roots=null;sizes=null;if(references!=null)references.clear();references=null;signature=null;fields.clear();}
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native definition root, member identity or value changed");}
}
