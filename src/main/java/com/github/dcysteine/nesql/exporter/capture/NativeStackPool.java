package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Reader-owned immutable witnesses. Budget unique payloads, not repeated rule edges. */
final class NativeStackPool {
  private final Map<String,ItemStack> values=new HashMap<>();
  private long bytes;
  String key(ItemStack stack){
    if(stack==null)return null;
    NativeStackAudit.audit(stack);String registry=Item.itemRegistry.getNameForObject(stack.getItem());
    if(registry==null||Item.itemRegistry.getObject(registry)!=stack.getItem())throw new Jobs.Fault("recipe_unsupported","Unregistered genetics witness");
    byte[] raw=CanonicalJson.bytes(object("registry",registry,"metadata",net.minecraft.init.Items.feather.getDamage(stack),"amount",stack.stackSize,"nbt",TypedNbt.encode(stack.getTagCompound())));
    String key=CanonicalJson.digest(raw);
    if(!values.containsKey(key)){
      if(values.size()>=65536||bytes+raw.length>32L*1024*1024)throw new Jobs.Fault("recipe_limit","Gendustry unique catalogue facts exceed budget");
      bytes+=raw.length;values.put(key,stack.copy());
    }
    return key;
  }
  ItemStack intern(ItemStack stack){return stack==null?null:values.get(key(stack));}
  void clear(){values.clear();bytes=0;}
}
