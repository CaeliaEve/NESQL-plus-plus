package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Small shared fixture scope: clone mutable backing stores, preserve static-final registry aliases.
 */
final class NativeRegistryScope implements AutoCloseable {
  private final List<Field> fields = new ArrayList<>();
  private final List<Object> targets = new ArrayList<>(), originals = new ArrayList<>();
  private final sun.misc.Unsafe memory;

  NativeRegistryScope() throws Exception {
    Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    memory = (sun.misc.Unsafe) access.get(null);
    try {
      registry(Item.itemRegistry);
      registry(Block.blockRegistry);
      Class<?> data = cpw.mods.fml.common.registry.GameData.class;
      Object current = field(data, null, "mainData");
      replace(
          data, current, "availabilityMap", ((BitSet) field(current, "availabilityMap")).clone());
      for (String name :
          new String[] {"nameToId", "idToName", "idToStack", "idToStackUn", "stackToId"})
        replace(
            OreDictionary.class,
            null,
            name,
            emptyOreStorage(name));
      Object loader = cpw.mods.fml.common.Loader.instance();
      replace(
          loader.getClass(),
          loader,
          "namedMods",
          new HashMap<>((Map<?, ?>) field(loader, "namedMods")));
    } catch (Exception | Error failure) {
      close();
      throw failure;
    }
  }

  static Object emptyOreStorage(String name) throws Exception {
    Object original=field(OreDictionary.class,null,name);
    if(name.equals("nameToId")){
      if(!NativeOreStorage.names(original))throw new AssertionError("Unknown ore fixture name map");
      if(original.getClass()==HashMap.class)return new HashMap<>();
      Object owned=original.getClass().newInstance();
      owned.getClass().getMethod("defaultReturnValue",int.class).invoke(owned,-1);
      return owned;
    }
    if(name.equals("stackToId")){
      if(!NativeOreStorage.reverse(original))throw new AssertionError("Unknown ore fixture reverse map");
      return original.getClass().newInstance();
    }
    if(original.getClass()!=ArrayList.class)throw new AssertionError("Unknown indexed ore fixture table");
    return new ArrayList<>();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void registry(net.minecraft.util.RegistryNamespaced registry) throws Exception {
    com.google.common.collect.BiMap names =
        com.google.common.collect.HashBiMap.create((Map) field(registry, "registryObjects"));
    net.minecraft.util.ObjectIntIdentityMap ids = new net.minecraft.util.ObjectIntIdentityMap();
    for (Object value : names.values()) ids.func_148746_a(value, registry.getIDForObject(value));
    replace(net.minecraft.util.RegistrySimple.class, registry, "registryObjects", names);
    replace(
        net.minecraft.util.RegistryNamespaced.class, registry, "field_148758_b", names.inverse());
    replace(net.minecraft.util.RegistryNamespaced.class, registry, "underlyingIntegerMap", ids);
  }

  void replace(Class<?> owner, Object target, String name, Object value) throws Exception {
    Field f = owner.getDeclaredField(name);
    f.setAccessible(true);
    fields.add(f);
    targets.add(target);
    originals.add(f.get(target));
    put(f, target, value);
  }

  <T> T allocate(Class<T> type) throws InstantiationException {
    return type.cast(memory.allocateInstance(type));
  }

  private void put(Field f, Object target, Object value) {
    boolean global = Modifier.isStatic(f.getModifiers());
    Object base = global ? memory.staticFieldBase(f) : target;
    long offset = global ? memory.staticFieldOffset(f) : memory.objectFieldOffset(f);
    if (f.getType() == int.class) memory.putInt(base, offset, (Integer) value);
    else if (f.getType() == boolean.class) memory.putBoolean(base, offset, (Boolean) value);
    else memory.putObject(base, offset, value);
  }

  public void close() throws Exception {
    for (int i = fields.size() - 1; i >= 0; i--)
      put(fields.get(i), targets.get(i), originals.get(i));
    for (int i = 0; i < fields.size(); i++) {
      Field f = fields.get(i);
      boolean originalAlreadyChecked = false;
      for (int earlier = 0; earlier < i; earlier++)
        if (fields.get(earlier).equals(f) && targets.get(earlier) == targets.get(i)) {
          originalAlreadyChecked = true;
          break;
        }
      if (originalAlreadyChecked) continue;
      Object result = f.get(targets.get(i)), expected = originals.get(i);
      if (f.getType().isPrimitive() ? !Objects.equals(result, expected) : result != expected)
        throw new AssertionError("Native fixture did not restore borrowed " + f);
    }
  }
}
