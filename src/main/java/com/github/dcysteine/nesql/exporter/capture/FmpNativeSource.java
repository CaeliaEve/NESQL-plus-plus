package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.CraftingManager;

/** Native source identity only. Observing a material does not authorize its callbacks. */
final class FmpNativeSource implements AutoCloseable {
  private static final String REGISTRY = "codechicken.microblock.MicroMaterialRegistry$";
  private static final String PROXY = "codechicken.microblock.handler.MicroblockProxy$";
  private final List<Object> roots = new ArrayList<>(), members = new ArrayList<>();
  private String rootSignature, signature;
  private boolean closed;

  FmpNativeSource() {
    rootSignature = CanonicalJson.digest(read(false, roots));
    signature = CanonicalJson.digest(read(true, members));
    finishVerification();
  }

  static Object[] registrationRoots() {
    List<Object> bindings = new ArrayList<>();
    readRoots(bindings);
    return bindings.toArray();
  }

  void verify() {
    check();
    compare(false, roots, rootSignature);
  }

  void finishVerification() {
    verify();
    compare(true, members, signature);
  }

  private void compare(boolean full, List<Object> expected, String signature) {
    List<Object> current = new ArrayList<>();
    JsonObject facts;
    try {
      facts = read(full, current);
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (!signature.equals(CanonicalJson.digest(facts)) || current.size() != expected.size())
      throw changed();
    for (int index = 0; index < expected.size(); index++)
      if (current.get(index) != expected.get(index)) throw changed();
  }

  private static Object[] readRoots(List<Object> bindings) {
    CraftingManager manager = CraftingManager.getInstance();
    if (manager == null || manager.getRecipeList() == null)
      throw notReady("FMP crafting source is unavailable");
    if (manager.getClass() != CraftingManager.class
        || manager.getRecipeList().getClass() != ArrayList.class)
      throw fault("Unadapted FMP crafting registry storage");
    List<?> recipes = manager.getRecipeList();
    if (recipes.size() > 262144) throw fault("FMP crafting source exceeds budget");
    Object registry = field(type(REGISTRY), null, "MODULE$"),
        proxy = field(type(PROXY), null, "MODULE$");
    if (registry == null || proxy == null) throw notReady("FMP registry/proxy is unavailable");
    if (registry.getClass() != type(REGISTRY) || proxy.getClass() != type(PROXY))
      throw fault("Unadapted FMP singleton source");
    Object nativeRecipe = field(type("codechicken.microblock.MicroRecipe$"), null, "MODULE$");
    if (nativeRecipe == null
        || nativeRecipe.getClass() != type("codechicken.microblock.MicroRecipe$"))
      throw notReady("Native FMP recipe singleton is unavailable");
    Object split = field(nativeRecipe, "splitMap");
    if (split == null || split.getClass() != type("scala.collection.immutable.Map$Map3"))
      throw fault("Unadapted FMP split map storage");
    for (int[] pair : new int[][] {{0, 3}, {1, 3}, {3, 2}})
      if (!Integer.valueOf(pair[1])
          .equals(invoke(split.getClass(), split, "apply", new Class<?>[] {Object.class}, pair[0])))
        throw fault("Unadapted native FMP split map");
    Object ids = field(registry, "codechicken$microblock$MicroMaterialRegistry$$idMap"),
        names = field(registry, "codechicken$microblock$MicroMaterialRegistry$$nameMap"),
        types = field(registry, "codechicken$microblock$MicroMaterialRegistry$$typeMap"),
        aliases = field(registry, "remap"),
        micro = field(proxy, "itemMicro");
    if (ids == null || names == null || types == null || aliases == null || micro == null)
      throw notReady("FMP material/name/remap/micro source is unavailable");
    if (ids.getClass() != java.lang.reflect.Array.newInstance(type("scala.Tuple2"), 0).getClass()
        || ((Object[]) ids).length > 65536
        || !(micro instanceof Item)) throw fault("Invalid FMP native ID map or micro item");
    for (Object map : new Object[] {names, types, aliases})
      if (map.getClass() != type("scala.collection.mutable.HashMap"))
        throw fault("Unadapted FMP native Scala registry storage");
    Collections.addAll(
        bindings,
        manager,
        recipes,
        registry,
        proxy,
        ids,
        names,
        types,
        aliases,
        micro,
        Item.itemRegistry,
        nativeRecipe,
        split);
    return new Object[] {recipes, registry, ids, names, types, aliases, micro};
  }

  private static JsonObject read(boolean full, List<Object> bindings) {
    Object[] source = readRoots(bindings);
    List<?> crafting = (List<?>) source[0];
    Object registry = source[1];
    Object[] ids = (Object[]) source[2];
    JsonObject facts =
        object(
            "craftingCount",
            crafting.size(),
            "materialCount",
            ids.length,
            "cachedMaxCuttingStrength",
            field(registry, "maxCuttingStrength"));
    if (!full) return facts;
    JsonArray positions = array(), materials = array();
    Object nativeRecipe = null;
    for (int index = 0; index < crafting.size(); index++) {
      Jobs.checkpoint();
      Object recipe = crafting.get(index);
      bindings.add(recipe);
      if (recipe != null
          && recipe.getClass().getName().equals("codechicken.microblock.MicroRecipe$")) {
        if (recipe != field(type("codechicken.microblock.MicroRecipe$"), null, "MODULE$"))
          throw fault("FMP crafting entry is not its native singleton");
        if (nativeRecipe != null && nativeRecipe != recipe)
          throw fault("Conflicting native FMP singleton recipes");
        nativeRecipe = recipe;
        positions.add(value(index));
      }
    }
    if (nativeRecipe == null)
      throw notReady("Native FMP MicroRecipe is not registered in CraftingManager");
    for (int index = 0; index < ids.length; index++) {
      Jobs.checkpoint();
      Object tuple = ids[index];
      if (tuple == null || tuple.getClass() != type("scala.Tuple2"))
        throw fault("Unadapted FMP ID tuple");
      Object name = field(tuple, "_1"), material = field(tuple, "_2");
      if (!(name instanceof String) || material == null)
        throw fault("Invalid FMP material identity");
      Collections.addAll(bindings, tuple, material);
      materials.add(object("id", index, "name", name, "owner", material.getClass().getName()));
    }
    facts.add("nativePositions", positions);
    facts.add("materials", materials);
    facts.add("names", map(source[3], bindings, "integer"));
    facts.add("types", map(source[4], bindings, "material"));
    facts.add("aliases", map(source[5], bindings, "string"));
    // Iterate native registry instances only. No names, subtype lists, block
    // getters, material methods, saw methods, hashCode or equals callbacks.
    JsonArray itemClasses = array();
    int count = 0;
    for (Object item : Item.itemRegistry) {
      Jobs.checkpoint();
      if (++count > 262144) throw fault("FMP item census exceeds budget");
      bindings.add(item);
      itemClasses.add(value(item == null ? null : item.getClass().getName()));
    }
    facts.add("registeredItemClasses", itemClasses);
    return facts;
  }

  private static JsonArray map(Object source, List<Object> bindings, String kind) {
    Map<String, Object> values = entries(source);
    JsonArray rows = array();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      Object value = entry.getValue();
      if (kind.equals("integer")
          ? !(value instanceof Integer)
          : kind.equals("string") ? !(value instanceof String) : value == null)
        throw fault("Invalid FMP " + kind + " map value");
      if (kind.equals("material")) {
        bindings.add(value);
        value = value.getClass().getName();
      }
      rows.add(object("key", entry.getKey(), "value", value));
    }
    return rows;
  }

  static Map<String, Object> entries(Object source) {
    if (source == null || source.getClass() != type("scala.collection.mutable.HashMap"))
      throw fault("Unadapted FMP lookup storage");
    TreeMap<String, Object> values = new TreeMap<>();
    Object iterator = invoke(source.getClass(), source, "iterator", new Class<?>[0]);
    while ((Boolean) invoke(iterator.getClass(), iterator, "hasNext", new Class<?>[0])) {
      Jobs.checkpoint();
      Object tuple = invoke(iterator.getClass(), iterator, "next", new Class<?>[0]);
      if (tuple == null || tuple.getClass() != type("scala.Tuple2"))
        throw fault("Invalid FMP map tuple");
      Object key = field(tuple, "_1"), value = field(tuple, "_2");
      if (!(key instanceof String) || values.containsKey(key)) throw fault("Invalid FMP map key");
      values.put((String) key, value);
      if (values.size() > 262144) throw fault("FMP lookup map exceeds budget");
    }
    return values;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "FMP source binding is closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    roots.clear();
    members.clear();
    rootSignature = signature = null;
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed",
        "FMP registration, native roots, material/member or lookup order changed");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault notReady(String message) {
    return new Jobs.Fault("handler_not_ready", message);
  }
}
