package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;

/** Read-only views of pinned smelting maps, including their different wildcard priority. */
final class NativeSmeltingTable {
  static final String EFR = "ganymedes01.etfuturum.core.utils.ItemStackMap";
  static final String LIB = "com.gtnewhorizon.gtnhlib.util.map.ItemStackMap";
  private final boolean ordinary;
  private final Map<?, ?> items;

  static FurnaceRecipes furnaceManager(){
    FurnaceRecipes manager=FurnaceRecipes.smelting();
    if(manager==null||manager.getClass()!=FurnaceRecipes.class)throw fault("Unadapted native furnace manager");
    return manager;
  }
  static Object furnaceItems(){return field(furnaceManager(),"smeltingList");}
  static Object furnaceExperience(){return field(furnaceManager(),"experienceList");}

  NativeSmeltingTable(Object source) {
    if (source == null) throw fault("Smelting table is not initialized");
    String owner = source.getClass().getName();
    ordinary = source.getClass() == HashMap.class || source.getClass() == LinkedHashMap.class;
    if (ordinary) {
      items = (Map<?, ?>) source;
      if (items.size() > 262144) throw fault("Smelting definition budget exceeded");
      for (Map.Entry<?, ?> entry : items.entrySet()) {
        Jobs.checkpoint();
        if (!(entry.getKey() instanceof ItemStack) || entry.getValue() == null) throw fault("Invalid furnace key/value");
        NativeStackAudit.audit((ItemStack) entry.getKey());
      }
      return;
    }
    if ((!owner.equals(EFR) && !owner.equals(EFR.replace("ItemStackMap", "ItemStackSet")) && !owner.equals(LIB))
        || source.getClass() != type(owner) || !Boolean.FALSE.equals(field(source, "NBTSensitive")))
      throw fault("Unadapted native smelting map: " + owner);
    Object raw = field(source, "itemMap");
    if (raw == null || raw.getClass() != HashMap.class || ((Map<?, ?>) raw).size() > 65536) throw fault("Invalid smelting item storage");
    items = (Map<?, ?>) raw;
    for (Map.Entry<?, ?> entry : items.entrySet()) {
      Jobs.checkpoint();
      if (!(entry.getKey() instanceof Item)) throw fault("Invalid smelting item identity");
      NativeStackAudit.identityOwner(entry.getKey());
      Object detail = entry.getValue(); String expected = (owner.equals(LIB) ? LIB : EFR) + "$DetailMap";
      if (detail == null || detail.getClass() != type(expected) || !Boolean.FALSE.equals(field(detail, "NBTSensitive"))
          || field(detail, "tagMap") != null || field(detail, "metaMap") != null) throw fault("Unadapted smelting detail-map predicates");
      Object damages = field(detail, "damageMap");
      if (damages != null && (damages.getClass() != HashMap.class || ((Map<?, ?>) damages).size() > 65536)) throw fault("Invalid smelting metadata storage");
      if (damages != null) for (Map.Entry<?, ?> value : ((Map<?, ?>) damages).entrySet())
        if (!(value.getKey() instanceof Integer) || (Integer) value.getKey() < 0 || value.getValue() == null) throw fault("Invalid smelting metadata definition");
    }
  }

  Object get(ItemStack input) {
    if (ordinary) {
      // Vanilla walks the real map order; it does not prefer an exact key over a wildcard.
      for (Map.Entry<?, ?> entry : items.entrySet()) {
        ItemStack key = (ItemStack) entry.getKey();
        if (key.getItem() == input.getItem() && (Items.feather.getDamage(key) == 32767
            || Items.feather.getDamage(key) == Items.feather.getDamage(input))) return entry.getValue();
      }
      return null;
    }
    Object detail = items.get(input.getItem()); if (detail == null) return null;
    Object wildcard = field(detail, "wildcard");
    if (wildcard != null) return wildcard; // These maps prefer wildcard even over an exact key.
    Object damages = field(detail, "damageMap");
    return damages == null ? null : ((Map<?, ?>) damages).get(Items.feather.getDamage(input));
  }

  /** Raw registered value, before wildcard-first lookup. Exact rows must keep their own output. */
  Object definitionValue(ItemStack input) {
    if(ordinary)return items.get(input);
    Object detail=items.get(input.getItem());if(detail==null)return null;
    int metadata=Items.feather.getDamage(input);
    if(metadata==32767)return field(detail,"wildcard");
    Object damages=field(detail,"damageMap");
    return damages==null?null:((Map<?,?>)damages).get(metadata);
  }
  List<Map.Entry<ItemStack,Object>> entries(){
    List<Map.Entry<ItemStack,Object>> result=new ArrayList<>();
    if(ordinary)for(Map.Entry<?,?> entry:items.entrySet())
      result.add(new AbstractMap.SimpleImmutableEntry<>((ItemStack)entry.getKey(),entry.getValue()));
    else for(ItemStack key:keys())result.add(new AbstractMap.SimpleImmutableEntry<>(key,definitionValue(key)));
    return result;
  }

  List<ItemStack> keys() {
    List<ItemStack> keys = new ArrayList<>();
    if (ordinary) for (Object key : items.keySet()) keys.add(((ItemStack) key).copy());
    else for (Map.Entry<?, ?> entry : items.entrySet()) {
      Jobs.checkpoint(); Item item = (Item) entry.getKey(); Object detail = entry.getValue();
      if (field(detail, "wildcard") != null) keys.add(new ItemStack(item, 1, 32767));
      Object damages = field(detail, "damageMap");
      if (damages != null) for (Object metadata : ((Map<?, ?>) damages).keySet()) keys.add(new ItemStack(item, 1, (Integer) metadata));
    }
    if (keys.size() > 262144) throw fault("Smelting key budget exceeded"); return keys;
  }

  static void shapes(Map<String, String[]> shapes) {
    for (String name : new String[] {EFR, LIB}) {
      shapes.put(name, new String[] {"itemMap", "size", "NBTSensitive"});
      shapes.put(name + "$DetailMap", new String[] {"hasWildcard", "wildcard", "damageMap", "tagMap", "metaMap", "size", "NBTSensitive"});
    }
    shapes.put(EFR.replace("ItemStackMap", "ItemStackSet"), shapes.get(EFR));
  }
  private static Jobs.Fault fault(String text) { return new Jobs.Fault("recipe_unsupported", text); }
}
