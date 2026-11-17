package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonElement;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraftforge.oredict.OreDictionary;

/** Bounded same-session identities, checked before native stacks/NBT are copied. */
final class BotaniaBorrowed {
  private final List<Object> references = new ArrayList<>();
  private final List<Integer> oreIds = new ArrayList<>();
  private final Budget budget;

  BotaniaBorrowed(Budget budget) {
    this.budget = budget;
  }

  void bind(Object value) {
    if (references.size() >= 262144)
      throw fault("Botania native definition identity budget exceeded");
    if (++budget.identities > 262144)
      throw fault("Botania native definitions exceed cumulative identity budget");
    references.add(value);
  }

  ItemStack stack(Object value) {
    if (value == null || value.getClass() != ItemStack.class)
      throw fault("Unadapted Botania native stack owner");
    ItemStack stack = (ItemStack) value;
    if (stack.getItem() == null) throw fault("Botania native stack has no item");
    NativeStackAudit.nbt(stack.getTagCompound());
    budget.payloadBytes +=
        CanonicalJson.bytes(
                com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(stack.getTagCompound()))
            .length;
    if (budget.payloadBytes > 32L * 1024 * 1024)
      throw fault("Botania native stack snapshots exceed cumulative NBT budget");
    bind(stack);
    bind(stack.getItem());
    nbt(stack.getTagCompound());
    return stack;
  }

  private void nbt(NBTBase tag) {
    bind(tag);
    if (tag == null) return;
    if (tag instanceof NBTTagCompound) {
      Map<?, ?> map = (Map<?, ?>) field(tag, "tagMap");
      bind(map);
      for (Object child : map.values()) nbt((NBTBase) child);
    } else if (tag instanceof NBTTagList) {
      List<?> list = (List<?>) field(tag, "tagList");
      bind(list);
      for (Object child : list) nbt((NBTBase) child);
    } else if (tag instanceof NBTTagByteArray) bind(((NBTTagByteArray) tag).func_150292_c());
    else if (tag instanceof NBTTagIntArray) bind(((NBTTagIntArray) tag).func_150302_c());
  }

  List<?> ore(String name) {
    Object names = field(OreDictionary.class, null, "nameToId"),
        table = field(OreDictionary.class, null, "idToStack"),
        views = field(OreDictionary.class, null, "idToStackUn");
    if (names == null || table == null || views == null)
      throw new Jobs.Fault("handler_not_ready", "Botania ore registry is unavailable");
    if (!NativeOreStorage.names(names)
        || table.getClass() != ArrayList.class
        || views.getClass() != ArrayList.class) throw fault("Unadapted Botania ore registry owner");
    bind(names);
    bind(table);
    bind(views);
    Object key = ((Map<?, ?>) names).get(name);
    // Primitive maps create new Integer wrappers for a stable ID. Pin the actual
    // registry roots/member lists by identity, and the scalar lookup by value.
    if (key == null) { oreIds.add(null); return Collections.emptyList(); }
    if (!(key instanceof Integer) || (Integer) key < 0 || (Integer) key >= ((List<?>) table).size())
      throw fault("Invalid Botania native ore ID");
    oreIds.add((Integer) key);
    Object entries = ((List<?>) table).get((Integer) key);
    if (entries == null || entries.getClass() != ArrayList.class)
      throw fault("Unadapted Botania native ore members owner");
    if ((Integer) key >= ((List<?>) views).size())
      throw fault("Botania native ore view is missing");
    Object view = ((List<?>) views).get((Integer) key);
    if (view == null
        || view.getClass()
            != MagicApi.type("net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList")
        || field(view, "list") != entries)
      throw fault("Botania native ore view does not reference its source members");
    bind(view);
    bind(entries);
    return (List<?>) entries;
  }

  boolean same(BotaniaBorrowed other) {
    if (!oreIds.equals(other.oreIds)) return false;
    if (references.size() != other.references.size()) return false;
    for (int i = 0; i < references.size(); i++)
      if (references.get(i) != other.references.get(i)) return false;
    return true;
  }

  static final class Budget {
    int remaining = 1048576, selectors, identities;
    long bytes, payloadBytes;

    void step() {
      if (--remaining < 0) throw fault("Botania native matching proof exceeds comparison budget");
      if ((remaining & 1023) == 0) Jobs.checkpoint();
    }

    void definition(JsonElement definition) {
      bytes += CanonicalJson.bytes(definition).length;
      if (identities > 262144 || bytes > 32L * 1024 * 1024)
        throw fault("Botania native definition snapshots exceed cumulative budget");
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
