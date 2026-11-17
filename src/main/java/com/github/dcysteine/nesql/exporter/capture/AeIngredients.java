package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Pure observation of AE's baked/unbaked alternatives. Native getters can mutate registry stacks.
 */
final class AeIngredients {
  private AeIngredients() {}

  static ItemStack[] read(Object source, Class<?> api, boolean shaped, List<Object> bindings) {
    try {
      List<ItemStack> result =
          read(
              source,
              api,
              shaped,
              bindings,
              Collections.newSetFromMap(new IdentityHashMap<>()),
              new int[1],
              0);
      return result.toArray(new ItemStack[0]);
    } catch (RegistrationRejected rejected) {
      // The recipe matcher catches RegistrationError. A group only catches MissingIngredientError,
      // so an invalid single-item branch rejects that whole group, including valid siblings.
      return new ItemStack[0];
    }
  }

  private static final class RegistrationRejected extends RuntimeException {
    RegistrationRejected() {
      super(null, null, false, false);
    }
  }

  private static List<ItemStack> read(
      Object raw,
      Class<?> api,
      boolean shaped,
      List<Object> bindings,
      Set<Object> visiting,
      int[] nodes,
      int depth) {
    Jobs.checkpoint();
    if (raw == null || depth > 32 || ++nodes[0] > 65536)
      throw fault("Invalid or oversized AE ingredient graph");
    bind(bindings, raw);
    if (shaped && raw.getClass() == ArrayList.class) return stacks((List<?>) raw, bindings, nodes);
    String owner = raw.getClass().getName();
    if (!api.getName().equals("appeng.api.recipes.IIngredient")
        || !api.isInstance(raw)
        || !Arrays.asList(
                "appeng.recipes.Ingredient",
                "appeng.recipes.IngredientSet",
                "appeng.recipes.GroupIngredient")
            .contains(owner)) throw fault("Unadapted AE ingredient callback: " + owner);
    Object baked = field(raw, "baked");
    if (baked != null) {
      if (baked.getClass() != ItemStack[].class) throw fault("Unadapted AE baked input storage");
      bind(bindings, baked);
      return stacks(Arrays.asList((ItemStack[]) baked), bindings, nodes);
    }
    int quantity = (Integer) field(raw, "qty");
    if (owner.endsWith(".Ingredient")) {
      String namespace = (String) field(raw, "nameSpace"), name = (String) field(raw, "itemName");
      if (namespace.equalsIgnoreCase("oreDictionary")) {
        Object ids = field(OreDictionary.class, null, "nameToId"),
            table = field(OreDictionary.class, null, "idToStack");
        if (ids == null || table == null)
          throw new Jobs.Fault("handler_not_ready", "AE ore source is unavailable");
        if (!NativeOreStorage.names(ids) || table.getClass() != ArrayList.class)
          throw fault("Unadapted AE ore storage");
        bind(bindings, ids);
        bind(bindings, table);
        Object index = ((Map<?, ?>) ids).get(name);
        if (index == null)
          return new ArrayList<>(); // Never call getOres, which registers an unknown name.
        if (!(index instanceof Integer)
            || (Integer) index < 0
            || (Integer) index >= ((List<?>) table).size()) throw fault("Invalid AE ore ID");
        Object values = ((List<?>) table).get((Integer) index);
        bind(bindings, values);
        List<ItemStack> out = stacks(sequence(values), bindings, nodes);
        for (ItemStack item : out) item.stackSize = quantity;
        return out;
      }
      if ((Boolean) field(raw, "isAir")) throw new RegistrationRejected();
      Block block = cpw.mods.fml.common.registry.GameRegistry.findBlock(namespace, name);
      if (block == null)
        block = cpw.mods.fml.common.registry.GameRegistry.findBlock(namespace, "tile." + name);
      Item item = block == null ? null : Item.getItemFromBlock(block);
      if (item == null) item = cpw.mods.fml.common.registry.GameRegistry.findItem(namespace, name);
      if (item == null)
        item = cpw.mods.fml.common.registry.GameRegistry.findItem(namespace, "item." + name);
      bind(bindings, Block.blockRegistry);
      bind(bindings, Item.itemRegistry);
      bind(bindings, block);
      bind(bindings, item);
      if (item == null) return new ArrayList<>();
      NBTTagCompound nbt = (NBTTagCompound) field(raw, "nbt");
      NativeStackAudit.nbt(nbt);
      bind(bindings, nbt);
      ItemStack stack = new ItemStack(item, quantity, (Integer) field(raw, "meta"));
      stack.setTagCompound(nbt == null ? null : (NBTTagCompound) nbt.copy());
      NativeStackAudit.audit(stack);
      return new ArrayList<>(Collections.singletonList(stack));
    }
    // IngredientSet.isInside is final false and absent from the installed method body.
    // Only GroupIngredient's mutable guard controls the native expansion.
    if (owner.endsWith(".GroupIngredient") && (Boolean) field(raw, "isInside"))
      return new ArrayList<>();
    if (!visiting.add(raw)) return new ArrayList<>(); // Same as the native group's recursion guard.
    try {
      List<ItemStack> out = new ArrayList<>();
      if (owner.endsWith(".IngredientSet")) {
        Object values = field(raw, "items");
        bind(bindings, values);
        out.addAll(stacks(sequence(values), bindings, nodes));
      } else {
        Object values = field(raw, "ingredients");
        bind(bindings, values);
        for (Object child : sequence(values)) {
          List<ItemStack> group = read(child, api, false, bindings, visiting, nodes, depth + 1);
          if (out.size() + group.size() > 65536)
            throw fault("AE grouped alternatives exceed budget");
          out.addAll(group);
        }
      }
      for (ItemStack item : out) item.stackSize = quantity;
      return out;
    } finally {
      visiting.remove(raw);
    }
  }

  private static List<?> sequence(Object source) {
    if (source == null) throw fault("Missing AE candidate storage");
    String owner = source.getClass().getName();
    if (source.getClass() != ArrayList.class
        && source.getClass() != LinkedList.class
        && !owner.equals("java.util.Arrays$ArrayList")
        && !owner.equals("java.util.Collections$SingletonList")
        && !owner.equals("java.util.Collections$EmptyList"))
      throw fault("Unadapted AE candidate list callback: " + owner);
    List<?> values = (List<?>) source;
    if (values.size() > 65536) throw fault("AE candidate list exceeds budget");
    return values;
  }

  private static List<ItemStack> stacks(List<?> source, List<Object> bindings, int[] nodes) {
    if (source.size() > 65536) throw fault("AE candidate list exceeds budget");
    List<ItemStack> out = new ArrayList<>();
    for (Object value : source) {
      Jobs.checkpoint();
      if (++nodes[0] > 65536 || !(value instanceof ItemStack))
        throw fault("Invalid AE candidate stack");
      ItemStack stack = (ItemStack) value;
      NativeStackAudit.audit(stack);
      bind(bindings, stack);
      bind(bindings, stack.getItem());
      bind(bindings, stack.getTagCompound());
      out.add(stack.copy());
    }
    return out;
  }

  private static void bind(List<Object> bindings, Object source) {
    if (bindings != null) bindings.add(source);
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
