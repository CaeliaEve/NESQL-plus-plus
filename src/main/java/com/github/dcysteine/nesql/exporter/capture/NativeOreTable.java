package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Read the registered backing table without registering a missing ore name. */
final class NativeOreTable {
  private NativeOreTable(){}
  static List<?> members(String name){
    Object names=field(OreDictionary.class,null,"nameToId"),table=field(OreDictionary.class,null,"idToStack");
    if(names==null||table==null)throw new Jobs.Fault("handler_not_ready","Native ore table is unavailable");
    if(!NativeOreStorage.names(names)||table.getClass()!=ArrayList.class)throw fault("Unadapted native ore storage");
    Object id=((Map<?,?>)names).get(name);if(id==null)return Collections.emptyList();
    if(!(id instanceof Integer)||(Integer)id<0||(Integer)id>=((List<?>)table).size())throw fault("Invalid native ore ID");
    Object entries=((List<?>)table).get((Integer)id);
    if(entries==null||entries.getClass()!=ArrayList.class||((List<?>)entries).size()>65536)throw fault("Unadapted native ore members");
    return (List<?>)entries;
  }
  static List<ItemStack> copies(String name){
    List<ItemStack> result=new ArrayList<>();for(Object raw:members(name)){Jobs.checkpoint();if(!(raw instanceof ItemStack))throw fault("Invalid native ore member");NativeStackAudit.audit((ItemStack)raw);result.add(((ItemStack)raw).copy());}return result;
  }
  private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
