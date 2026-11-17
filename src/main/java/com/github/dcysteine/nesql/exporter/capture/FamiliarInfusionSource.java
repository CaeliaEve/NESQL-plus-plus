package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Gadomancy familiar conditions on owned projections, without invoking its NBT-creating getters. */
final class FamiliarInfusionSource implements AutoCloseable {
  static final String RECIPE="makeo.gadomancy.common.crafting.EtherealFamiliarUpgradeRecipe";
  private static final String AUGMENT="makeo.gadomancy.common.familiar.FamiliarAugment",
      ITEM="makeo.gadomancy.common.items.baubles.ItemEtherealFamiliar";
  private Object recipe,target;
  private Map<?,?> names;
  private Locale locale;
  private final List<Object> nodes=new ArrayList<>();
  private final List<Item> items=new ArrayList<>();
  private NativeDefinitionGraph graph;
  private boolean closed;

  FamiliarInfusionSource(Object recipe){
    this.recipe=recipe;
    try{
      if(recipe.getClass()!=type(RECIPE))throw fault("Unadapted familiar infusion class");
      target=field(recipe,"toAdd");Object raw=field(type(AUGMENT),null,"BY_NAME");
      if(target==null)throw fault("Native familiar infusion has a null target augment");
      if(raw==null||raw.getClass()!=HashMap.class||((Map<?,?>)raw).size()>4096)throw fault("Invalid familiar augment name registry");
      names=(Map<?,?>)raw;locale=Locale.getDefault();
      List<String> keys=new ArrayList<>();for(Object key:names.keySet()){if(!(key instanceof String))throw fault("Invalid familiar name key");keys.add((String)key);}Collections.sort(keys);
      for(String key:keys)bind(names.get(key));bind(target);
      for(int i=0;i<nodes.size();i++){
        Object node=nodes.get(i);for(Object other:list(node,"conflicts"))bind(other);
        for(Object condition:list(node,"preconditions"))if(condition!=null){
          String name=condition.getClass().getName();if(!name.equals(AUGMENT+"$PreconditionAny")&&!name.equals(AUGMENT+"$PreconditionAll"))throw fault("Unknown familiar augment condition");
          Object[] required=required(condition);if(required!=null)for(Object other:required)bind(other);
        }
      }
      items.addAll(items());
      Map<String,String[]> shapes=new HashMap<>();shapes.put(AUGMENT,new String[]{"unlocalizedName","preconditions","requiresPrevLevel","conflicts"});
      shapes.put(AUGMENT+"$PreconditionAny",new String[]{"anyPrevAugment"});shapes.put(AUGMENT+"$PreconditionAll",new String[]{"prevAugments"});shapes.put(Locale.class.getName(),new String[0]);
      graph=new NativeDefinitionGraph(this::roots,shapes);
    }catch(RuntimeException|Error failure){close();throw failure;}
  }
  private static List<?> list(Object node,String member){Object raw=field(node,member);if(raw==null||raw.getClass()!=ArrayList.class||((List<?>)raw).size()>4096)throw fault("Invalid familiar augment list");return (List<?>)raw;}
  private static Object[] required(Object condition){Object raw=field(condition,condition.getClass().getName().endsWith("$PreconditionAny")?"anyPrevAugment":"prevAugments");
    if(raw==null)return null;if(!raw.getClass().isArray()||java.lang.reflect.Array.getLength(raw)>4096)throw fault("Invalid familiar condition array");return (Object[])raw;}
  private void bind(Object raw){if(raw==null)return;if(raw.getClass()!=type(AUGMENT))throw fault("Familiar augment overrides native callbacks");if(index(raw)<0){if(nodes.size()>=4096)throw fault("Familiar augment graph exceeds budget");nodes.add(raw);}}
  private int index(Object raw){for(int i=0;i<nodes.size();i++)if(nodes.get(i)==raw)return i;return -1;}
  private static List<Item> items(){List<Item> result=new ArrayList<>();for(Object raw:Item.itemRegistry)if(type(ITEM).isInstance(raw))result.add((Item)raw);result.sort(Comparator.comparing(Item.itemRegistry::getNameForObject));return result;}
  private Object[] roots(){return new Object[]{field(recipe,"toAdd"),field(recipe,"requiredPreviousLevel"),field(type(AUGMENT),null,"BY_NAME"),Locale.getDefault(),items};}
  private static final class Pair {final Object augment;final int level;Pair(Object augment,int level){this.augment=augment;this.level=level;}}
  private List<Pair> read(ItemStack stack){
    List<Pair> result=new ArrayList<>();if(!items.contains(stack.getItem()))return result;
    NBTTagCompound augments=stack.hasTagCompound()?stack.getTagCompound().getCompoundTag("augments"):new NBTTagCompound();
    if(augments.func_150296_c().size()>4096)throw fault("Familiar input augment NBT exceeds budget");
    for(Object raw:augments.func_150296_c()){String key=(String)raw;result.add(new Pair(names.get(key.toLowerCase(locale)),augments.getInteger(key)));}
    return result;
  }
  private static Pair first(List<Pair> pairs,Object target){for(Pair pair:pairs){if(pair.augment==null)throw fault("Native familiar upgrade lookup dereferences an unknown augment");if(pair.augment==target)return pair;}return null;}
  private static boolean containsIdentity(List<?> values,Object target){for(Object value:values)if(value==target)return true;return false;}
  boolean acceptsClass(ItemStack stack){return items.contains(stack.getItem());}
  boolean admitted(ItemStack stack){
    verify();if(!acceptsClass(stack))return false;List<Pair> pairs=read(stack);Pair existing=first(pairs,target);
    int level=existing==null?0:existing.level,next=level+1;
    if((Integer)field(recipe,"requiredPreviousLevel")>level||next<=0)return false;
    for(Pair pair:pairs)if(containsIdentity(list(target,"conflicts"),pair.augment))return false;
    if((Boolean)field(target,"requiresPrevLevel")){
      Pair last=null;for(Pair pair:pairs){if(pair.augment==null)throw fault("Native familiar previous-level lookup dereferences an unknown augment");if(pair.augment==target)last=pair;}
      if(last==null?next!=1:last.level!=next-1)return false;
    }
    for(Object condition:list(target,"preconditions")){
      if(condition==null)continue;Object[] required=required(condition);if(required==null)continue;
      boolean any=condition.getClass().getName().endsWith("$PreconditionAny"),matched=!any;
      for(Object augment:required)if(augment!=null){boolean found=first(pairs,augment)!=null;if(any&&found){matched=true;break;}if(!any&&!found){matched=false;break;}}
      if(!matched)return false;
    }
    return true;
  }
  /** Products.observe has already made the one copy used by the native output method. */
  ItemStack outputOwned(ItemStack copy){
    verify();Pair found=first(read(copy),target);int next=found==null?1:found.level+1;
    if(next<=0)return copy;
    if(!copy.hasTagCompound())copy.setTagCompound(new NBTTagCompound());NBTTagCompound root=copy.getTagCompound(),augments=root.getCompoundTag("augments");
    augments.setInteger((String)field(target,"unlocalizedName"),next);root.setTag("augments",augments);return copy;
  }
  /** Complete an original central specimen with the target's actual registered preconditions. */
  ItemStack specimen(ItemStack original){
    if(admitted(original))return original.copy();
    NBTTagCompound base=new NBTTagCompound();int required=(Integer)field(recipe,"requiredPreviousLevel");
    if(required>0)base.setInteger((String)field(target,"unlocalizedName"),required);
    List<NBTTagCompound> candidates=new ArrayList<>();candidates.add(base);
    for(Object condition:list(target,"preconditions")){
      Jobs.checkpoint();if(condition==null)continue;Object[] previous=required(condition);if(previous==null)continue;
      boolean any=condition.getClass().getName().endsWith("$PreconditionAny");List<NBTTagCompound> next=new ArrayList<>();
      for(NBTTagCompound candidate:candidates){
        Jobs.checkpoint();if(any){for(Object other:previous)if(other!=null){if(next.size()>=4096)throw fault("Familiar specimen search exceeds budget");NBTTagCompound added=(NBTTagCompound)candidate.copy();if(!added.hasKey((String)field(other,"unlocalizedName")))added.setInteger((String)field(other,"unlocalizedName"),1);next.add(added);}}
        else{if(next.size()>=4096)throw fault("Familiar specimen search exceeds budget");NBTTagCompound added=(NBTTagCompound)candidate.copy();for(Object other:previous)if(other!=null&&!added.hasKey((String)field(other,"unlocalizedName")))added.setInteger((String)field(other,"unlocalizedName"),1);next.add(added);}
      }
      if(next.size()>4096)throw fault("Familiar condition specimen search exceeds budget");candidates=next;
    }
    for(NBTTagCompound candidate:candidates){ItemStack copy=original.copy();if(!copy.hasTagCompound())copy.setTagCompound(new NBTTagCompound());copy.getTagCompound().setTag("augments",candidate);if(admitted(copy))return copy;}
    throw fault("No admitted familiar specimen; do not classify this as an empty environment");
  }
  JsonObject describe(){
    verify();JsonObject mapping=object();List<String> keys=new ArrayList<>();for(Object key:names.keySet())keys.add((String)key);Collections.sort(keys);
    for(String key:keys){Object value=names.get(key);if(value==null)mapping.add(key,JsonNull.INSTANCE);else mapping.addProperty(key,index(value));}
    JsonArray definitions=array(),accepted=array();for(Item item:items)accepted.add(value(Item.itemRegistry.getNameForObject(item)));
    for(Object node:nodes){JsonArray conflicts=array(),conditions=array();for(Object conflict:list(node,"conflicts"))conflicts.add(value(conflict==null?null:index(conflict)));
      for(Object condition:list(node,"preconditions")){
        if(condition==null){conditions.add(object("kind","skip"));continue;}Object[] raw=required(condition);JsonArray required=raw==null?null:array();
        if(raw!=null)for(Object other:raw)required.add(value(other==null?null:index(other)));
        conditions.add(object("kind",condition.getClass().getName().endsWith("$PreconditionAny")?"any":"all","required",required));
      }
      definitions.add(object("name",field(node,"unlocalizedName"),"requiresPrevious",field(node,"requiresPrevLevel"),"conflicts",conflicts,"conditions",conditions));
    }
    return object("kind","familiar","target",index(target),"names",mapping,"augments",definitions,"items",accepted,"requiredPreviousLevel",field(recipe,"requiredPreviousLevel"),"locale",locale.toLanguageTag());
  }
  void verify(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Familiar infusion source is closed");graph.verifyRoots();}
  void finishVerification(){verify();graph.verify();List<Item> current=items();if(current.size()!=items.size())throw changed();for(int i=0;i<items.size();i++)if(items.get(i)!=current.get(i))throw changed();}
  public void close(){closed=true;if(graph!=null)graph.close();recipe=null;target=null;names=null;locale=null;nodes.clear();items.clear();}
  private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
  private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native familiar item scope changed");}
}
