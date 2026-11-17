package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.Array;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Read AdvRecipe.expand's candidate lists without predicate/cache/registry writes.
 * IRecipeInput.matches/getAmount are NOT used by native Advanced crafting.
 */
final class Ic2AdvancedInputs implements AutoCloseable {
  private Object[] source;
  private final List<Object> anchors = new ArrayList<>();
  private final List<List<ItemStack>> groups = new ArrayList<>();
  private JsonArray declaration;
  private String signature;

  Ic2AdvancedInputs(Object[] source) {
    if (source == null)
      throw new Jobs.Fault("handler_not_ready", "IC2 Advanced input source is unavailable");
    this.source = source;
    declaration = read(anchors, groups);
    signature = CanonicalJson.digest(declaration);
    verify();
  }

  List<ItemStack> copies(int index) {
    check();
    List<ItemStack> result = new ArrayList<>();
    for (ItemStack item : groups.get(index)) result.add(item.copy());
    return result;
  }

  JsonArray declaration() {
    check();
    return new JsonParser().parse(declaration.toString()).getAsJsonArray();
  }

  void verify() {
    check();
    List<Object> current = new ArrayList<>();
    JsonArray facts = read(current, null);
    if (!signature.equals(CanonicalJson.digest(facts)) || anchors.size() != current.size())
      throw changed();
    for (int index = 0; index < anchors.size(); index++)
      if (anchors.get(index) != current.get(index)) throw changed();
  }

  private JsonArray read(List<Object> bindings, List<List<ItemStack>> output) {
    if (source.getClass() != Object[].class || source.length > 9)
      throw fault("Unadapted IC2 compact input array");
    bindings.add(source);
    JsonArray rows = array();
    int[] nodes = {0};
    for (Object raw : source) {
      List<ItemStack> candidates = new ArrayList<>();
      rows.add(
          read(
              raw,
              bindings,
              candidates,
              0,
              nodes,
              Collections.newSetFromMap(new IdentityHashMap<>())));
      if (output != null) output.add(candidates);
    }
    return rows;
  }

  private JsonObject read(
      Object raw,
      List<Object> bindings,
      List<ItemStack> output,
      int depth,
      int[] nodes,
      Set<Object> visiting) {
    Jobs.checkpoint();
    if (raw == null) throw fault("Null native IC2 expansion node");
    if (depth > 32 || ++nodes[0] > 65536) throw fault("IC2 expansion graph exceeds budget");
    if (!visiting.add(raw)) throw fault("Cyclic native IC2 alternatives");
    bindings.add(raw);
    try {
      if (raw instanceof ItemStack) {
        ItemStack stack = (ItemStack) raw;
        JsonObject facts = stack(stack, bindings);
        output.add(stack.copy());
        return object("kind", "stack", "value", facts);
      }
      if (raw instanceof String) {
        String name = (String) raw;
        if (name.startsWith("liquid$"))
          return object(
              "kind",
              "liquidName",
              "name",
              name.substring(7),
              "members",
              fluidCandidates(null, name.substring(7), bindings, output));
        Object list = ore(name, bindings);
        return object("kind", "oreName", "name", name, "members", stacks(list, bindings, output));
      }
      String name = raw.getClass().getName();
      if (name.equals("ic2.api.recipe.RecipeInputItemStack")) {
        Object stack = field(raw, "input");
        if (!(stack instanceof ItemStack)) throw fault("Invalid native IC2 stack predicate");
        JsonObject facts = stack((ItemStack) stack, bindings);
        output.add(((ItemStack) stack).copy());
        return object("kind", "itemPredicate", "amount", field(raw, "amount"), "value", facts);
      }
      if (name.equals("ic2.api.recipe.RecipeInputOreDict")) {
        String key = (String) field(raw, "input");
        if (key == null) throw fault("Null native IC2 ore key");
        Object cached = field(raw, "ores"),
            lookup = ore(key, bindings),
            list = cached == null ? lookup : cached;
        // Native getInputs may memoize the same dictionary view. Binding the
        // resolved view pins its priority and accepts only equivalent memoization.
        return object(
            "kind",
            "orePredicate",
            "name",
            key,
            "declaredAmount",
            field(raw, "amount"),
            "declaredMetadata",
            field(raw, "meta"),
            "members",
            stacks(list, bindings, output));
      }
      if (name.equals("ic2.api.recipe.RecipeInputFluidContainer")) {
        Object fluid = field(raw, "fluid");
        if (fluid != null && !(fluid instanceof Fluid))
          throw fault("Invalid native IC2 fluid predicate");
        bindings.add(fluid);
        return object(
            "kind",
            "fluidPredicate",
            "declaredAmount",
            field(raw, "amount"),
            "fluid",
            fluid == null ? null : fluidName((Fluid) fluid),
            "members",
            fluidCandidates((Fluid) fluid, null, bindings, output));
      }
      JsonArray members = array();
      if (raw.getClass().isArray()) {
        int length = Array.getLength(raw);
        if (length > 65536) throw fault("IC2 alternatives exceed budget");
        for (int index = 0; index < length; index++)
          members.add(read(Array.get(raw, index), bindings, output, depth + 1, nodes, visiting));
      } else if (raw instanceof Iterable<?>) {
        Object sequence = sequence(raw, bindings);
        for (Object item : (Iterable<?>) sequence)
          members.add(read(item, bindings, output, depth + 1, nodes, visiting));
      } else throw fault("Unadapted native IC2 expansion node: " + name);
      return object("kind", "alternatives", "class", name, "members", members);
    } finally {
      visiting.remove(raw);
    }
  }

  private static JsonObject stack(ItemStack stack, List<Object> bindings) {
    NativeStackAudit.audit(stack);
    Collections.addAll(bindings, stack, stack.getItem(), stack.getTagCompound());
    String registry = net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem());
    if (registry == null
        || net.minecraft.item.Item.itemRegistry.getObject(registry) != stack.getItem())
      throw fault("Unregistered IC2 expansion stack");
    return RecipeRow.stackEvidence(stack);
  }

  private static Object sequence(Object value, List<Object> bindings) {
    if (value == null) throw fault("Missing native IC2 sequence");
    bindings.add(value);
    String name = value.getClass().getName();
    if (name.equals("net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList")) {
      Object list = field(value, "list");
      if (list == null || list.getClass() != ArrayList.class)
        throw fault("Unadapted native ore view backing table");
      bindings.add(list);
      return list;
    }
    if (value.getClass() == HashSet.class || value.getClass() == LinkedHashSet.class) {
      Object backing = field(value, "map");
      if (backing == null
          || backing.getClass()
              != (value.getClass() == HashSet.class ? HashMap.class : LinkedHashMap.class))
        throw fault("Unadapted IC2 alternative set backing table");
      bindings.add(backing);
      return value;
    }
    if (value.getClass() == ArrayList.class
        || value.getClass() == LinkedList.class
        || value.getClass() == Vector.class
        || name.equals("java.util.Arrays$ArrayList")
        || name.equals("java.util.Collections$SingletonList")
        || name.equals("java.util.Collections$EmptyList")) return value;
    throw fault("Unadapted native IC2 sequence callback: " + name);
  }

  private static JsonArray stacks(Object source, List<Object> bindings, List<ItemStack> output) {
    Object values = sequence(source, bindings);
    JsonArray rows = array();
    int count = 0;
    for (Object value : (Iterable<?>) values) {
      Jobs.checkpoint();
      if (++count > 65536 || !(value instanceof ItemStack))
        throw fault("Invalid or oversized native IC2 ore candidate list");
      ItemStack stack = (ItemStack) value;
      if (stack.getClass() != ItemStack.class)
        throw fault("Unadapted native IC2 ore stack callback");
      bindings.add(stack);
      if (stack.getItem() == null) {
        rows.add(object("invalidItem", true, "amount", stack.stackSize));
        continue;
      }
      rows.add(stack(stack, bindings));
      output.add(stack.copy());
    }
    return rows;
  }

  private static Object ore(String key, List<Object> bindings) {
    Object names = field(OreDictionary.class, null, "nameToId"),
        views = field(OreDictionary.class, null, "idToStackUn");
    if (names == null || views == null)
      throw new Jobs.Fault("handler_not_ready", "Native ore dictionary is unavailable");
    if (!NativeOreStorage.names(names) || views.getClass() != ArrayList.class)
      throw fault("Unadapted native ore lookup storage");
    Collections.addAll(bindings, names, views);
    Object id = ((Map<?, ?>) names).get(key);
    if (id == null) return field(OreDictionary.class, null, "EMPTY_LIST");
    if (!(id instanceof Integer) || (Integer) id < 0 || (Integer) id >= ((List<?>) views).size())
      throw fault("Invalid native ore lookup ID");
    return ((List<?>) views).get((Integer) id);
  }

  private static JsonArray fluidCandidates(
      Fluid wanted, String name, List<Object> bindings, List<ItemStack> output) {
    for (String field :
        new String[] {"filledContainerMap", "containerFluidMap", "emptyContainers"}) {
      Object root = field(FluidContainerRegistry.class, null, field);
      if (root == null)
        throw new Jobs.Fault("handler_not_ready", "Native fluid container table is unavailable");
      bindings.add(root);
      if (root.getClass() != (field.equals("emptyContainers") ? HashSet.class : HashMap.class))
        throw fault("Unadapted fluid container lookup storage");
    }
    for (String field : new String[] {"fluidIDs", "fluidNames", "fluids"}) {
      Object root = field(FluidRegistry.class, null, field);
      if (root == null)
        throw new Jobs.Fault("handler_not_ready", "Native fluid registry is unavailable");
      if (root.getClass() != com.google.common.collect.HashBiMap.class)
        throw fault("Unadapted native fluid registry storage");
      bindings.add(root);
    }
    JsonArray rows = array();
    FluidContainerRegistry.FluidContainerData[] entries =
        FluidContainerRegistry.getRegisteredFluidContainerData();
    if (entries.length > 262144) throw fault("Native fluid container table exceeds budget");
    for (FluidContainerRegistry.FluidContainerData entry : entries) {
      Jobs.checkpoint();
      if (entry == null
          || entry.getClass() != FluidContainerRegistry.FluidContainerData.class
          || entry.fluid == null
          || entry.fluid.getClass() != FluidStack.class)
        throw fault("Invalid native IC2 fluid container entry");
      Fluid actual = entry.fluid.getFluid();
      String actualName = fluidName(actual);
      if (name == null ? actual != wanted : !name.equals(actualName)) continue;
      Collections.addAll(bindings, entry, entry.fluid, entry.fluid.tag, actual);
      NativeStackAudit.nbt(entry.fluid.tag);
      rows.add(
          object(
              "fluid",
              actualName,
              "amount",
              entry.fluid.amount,
              "nbt",
              com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(entry.fluid.tag),
              "filled",
              stack(entry.filledContainer, bindings)));
      output.add(entry.filledContainer.copy());
    }
    return rows;
  }

  private static String fluidName(Fluid fluid) {
    Object root = field(FluidRegistry.class, null, "fluids");
    if (root == null)
      throw new Jobs.Fault("handler_not_ready", "Native fluid registry is unavailable");
    if (root.getClass() != com.google.common.collect.HashBiMap.class)
      throw fault("Unadapted native fluid registry storage");
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) root).entrySet())
      if (entry.getValue() == fluid) return (String) entry.getKey();
    return null;
  }

  private void check() {
    Jobs.checkpoint();
    if (source == null) throw new Jobs.Fault("capture_closed", "IC2 input reader is closed");
  }

  public void close() {
    if (source == null) return;
    source = null;
    anchors.clear();
    groups.clear();
    declaration = null;
    signature = null;
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "Native IC2 input, ore/cache/container source or candidate changed");
  }
}
