package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Audited native records; neither recipe GUI state nor a borrowed ItemStack is retained as data.
 */
final class DistillerySource implements AutoCloseable {
  static final String REGISTRY = "com.emoniph.witchery.crafting.DistilleryRecipes";
  private final boolean fixture;
  private Object[] roots;
  private List<?> table;
  private ItemStack fixtureJar;
  private List<Object> identities;
  private String signature;
  private int count;
  private boolean closed;
  final List<Rule> rules = new ArrayList<>();
  ItemStack jar;

  DistillerySource() {
    this(null, null);
  }

  DistillerySource(List<?> fixtureTable, ItemStack fixtureJar) {
    fixture = fixtureTable != null;
    try {
      this.fixtureJar = fixtureJar;
      roots = fixture ? new Object[] {fixtureTable, fixtureJar, Item.itemRegistry} : roots();
      table = list(roots[fixture ? 0 : 1], fixture);
      count = table.size();
      Observation first = observe();
      rules.addAll(first.rules);
      jar = first.jar;
      identities = first.identities;
      signature = first.signature;
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static Object[] registrationAnchors(IRecipeHandler handler) {
    Object[] raw = roots(), result = new Object[raw.length + 1];
    result[0] = handler.getClass();
    System.arraycopy(raw, 0, result, 1, raw.length);
    return result;
  }

  private static Object[] roots() {
    Object manager = field(type(REGISTRY), null, "INSTANCE");
    if (manager == null) throw notReady();
    if (manager.getClass() != type(REGISTRY)) throw unsupported("manager owner");
    Object table = field(manager, "recipes");
    list(table, false);
    Object items = field(type("com.emoniph.witchery.Witchery"), null, "Items");
    Object generic = items == null ? null : field(items, "GENERIC");
    Object role = generic == null ? null : field(generic, "itemEmptyClayJar");
    if (!((List<?>) table).isEmpty()) {
      if (items == null || generic == null || role == null) throw notReady();
      if (items.getClass() != type("com.emoniph.witchery.WitcheryItems")
          || generic.getClass() != type("com.emoniph.witchery.item.ItemGeneral")
          || role.getClass() != type("com.emoniph.witchery.item.ItemGeneral$SubItem"))
        throw unsupported("jar role owner");
    }
    return new Object[] {
      manager,
      table,
      Item.itemRegistry,
      items == null ? Boolean.FALSE : items,
      generic == null ? Boolean.FALSE : generic,
      role == null ? Boolean.FALSE : role
    };
  }

  void verifyRoots() {
    check();
    if (table.size() != count) throw changed(null);
    if (fixture) {
      if (Item.itemRegistry != roots[2]) throw changed(null);
      return;
    }
    Object[] actual;
    try {
      actual = roots();
    } catch (Jobs.Fault cause) {
      throw changed(cause);
    }
    for (int index = 0; index < roots.length; index++)
      if (roots[index] != actual[index]) throw changed(null);
  }

  void verify() {
    verifyRoots();
    Observation next;
    try {
      next = observe();
    } catch (Jobs.Fault cause) {
      throw changed(cause);
    }
    if (!signature.equals(next.signature) || identities.size() != next.identities.size())
      throw changed(null);
    for (int index = 0; index < identities.size(); index++)
      if (identities.get(index) != next.identities.get(index)) throw changed(null);
  }

  private Observation observe() {
    Observation result = new Observation();
    JsonArray records = array();
    if (count > 0) {
      ItemStack original = fixtureJar;
      if (!fixture) {
        Object generic = roots[4], role = roots[5];
        int metadata = (Integer) field(role, "damageValue");
        if (metadata < 0) throw unsupported("jar metadata changed by copy");
        original = new ItemStack((Item) generic, 1, metadata);
      }
      result.jar = result.stack(original, fixture);
    }
    for (Object member : table) {
      Jobs.checkpoint();
      if (member == null || member.getClass() != type(REGISTRY + "$DistilleryRecipe"))
        throw unsupported("recipe callback owner");
      result.ref(member);
      Object rawInputs = field(member, "inputs"), rawOutputs = field(member, "outputs");
      if (rawInputs == null
          || rawInputs.getClass() != ItemStack[].class
          || ((ItemStack[]) rawInputs).length != 2
          || rawOutputs == null
          || rawOutputs.getClass() != ItemStack[].class
          || ((ItemStack[]) rawOutputs).length != 4) throw unsupported("native slot arrays");
      result.ref(rawInputs);
      result.ref(rawOutputs);
      ItemStack[] inputs = new ItemStack[2], outputs = new ItemStack[4];
      boolean[] subtypes = new boolean[2];
      JsonArray in = array(), out = array();
      for (int slot = 0; slot < 2; slot++) {
        inputs[slot] = result.stack(((ItemStack[]) rawInputs)[slot], true);
        subtypes[slot] = inputs[slot] != null && inputs[slot].getHasSubtypes();
        in.add(
            inputs[slot] == null
                ? value(null)
                : object("stack", raw(inputs[slot]), "subtypes", inputs[slot].getHasSubtypes()));
      }
      for (int slot = 0; slot < 4; slot++) {
        outputs[slot] = result.stack(((ItemStack[]) rawOutputs)[slot], true);
        out.add(raw(outputs[slot]));
      }
      int jars = (Integer) field(member, "jars");
      result.rules.add(new Rule(inputs, outputs, subtypes, jars));
      records.add(object("inputs", in, "outputs", out, "jars", jars));
    }
    result.signature = CanonicalJson.digest(object("records", records, "jar", raw(result.jar)));
    return result;
  }

  private static List<?> list(Object value, boolean fixture) {
    if (value == null) throw notReady();
    if (value.getClass() != ArrayList.class
        && !(fixture && value.getClass().getName().equals("java.util.Collections$SingletonList"))
        && !(fixture && value.getClass().getName().equals("java.util.Arrays$ArrayList")))
      throw unsupported("list storage");
    List<?> list = (List<?>) value;
    if (list.size() > 65536) throw budget();
    return list;
  }

  private static JsonElement raw(ItemStack value) {
    if (value == null) return JsonNull.INSTANCE;
    return object(
        "item",
        Item.itemRegistry.getNameForObject(value.getItem()),
        "id",
        Item.getIdFromItem(value.getItem()),
        "meta",
        value.getItemDamage(),
        "amount",
        value.stackSize,
        "nbt",
        TypedNbt.encode(value.getTagCompound()));
  }

  private static final class Observation {
    final List<Rule> rules = new ArrayList<>();
    final List<Object> identities = new ArrayList<>();
    long bytes;
    int nodes;
    ItemStack jar;
    String signature;

    void add(long amount) {
      if ((bytes += amount) > 32L * 1024 * 1024 || ++nodes > 262144) throw budget();
    }

    void ref(Object value) {
      if (identities.size() >= 262144) throw budget();
      identities.add(value);
    }

    ItemStack stack(ItemStack original, boolean borrowed) {
      if (original == null) return null;
      NativeStackAudit.audit(original);
      if (original.getItemDamage() < 0)
        throw unsupported("negative metadata changes under native copy");
      Item item = original.getItem();
      try {
        if (item.getClass().getMethod("hashCode").getDeclaringClass() != Object.class
            || item.getClass().getMethod("equals", Object.class).getDeclaringClass()
                != Object.class) throw unsupported("item hash/equality callback");
      } catch (NoSuchMethodException impossible) {
        throw new AssertionError(impossible);
      }
      if (ItemCallbacks.method(item, "getHasSubtypes", "func_77614_k").getDeclaringClass()
          != Item.class) throw unsupported("metadata subtype callback");
      String registry = Item.itemRegistry.getNameForObject(item);
      if (registry == null || registry.length() > 4096)
        throw unsupported("unregistered or oversized item name");
      add(256 + 6L * registry.length());
      nbt(original.getTagCompound(), borrowed);
      ref(item);
      if (borrowed) ref(original);
      return original.copy();
    }

    void nbt(net.minecraft.nbt.NBTBase tag, boolean borrowed) {
      if (tag == null) return;
      add(64);
      if (borrowed) ref(tag);
      if (tag.getClass() == net.minecraft.nbt.NBTTagCompound.class) {
        Map<?, ?> map = (Map<?, ?>) field(tag, "tagMap");
        if (borrowed) ref(map);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          add(6L * ((String) entry.getKey()).length());
          nbt((net.minecraft.nbt.NBTBase) entry.getValue(), borrowed);
        }
      } else if (tag.getClass() == net.minecraft.nbt.NBTTagList.class) {
        List<?> list = (List<?>) field(tag, "tagList");
        if (borrowed) ref(list);
        for (Object child : list) nbt((net.minecraft.nbt.NBTBase) child, borrowed);
      } else if (tag.getClass() == net.minecraft.nbt.NBTTagString.class)
        add(6L * ((net.minecraft.nbt.NBTTagString) tag).func_150285_a_().length());
      else if (tag.getClass() == net.minecraft.nbt.NBTTagByteArray.class) {
        byte[] values = ((net.minecraft.nbt.NBTTagByteArray) tag).func_150292_c();
        add(8L * values.length);
        if (borrowed) ref(values);
      } else if (tag.getClass() == net.minecraft.nbt.NBTTagIntArray.class) {
        int[] values = ((net.minecraft.nbt.NBTTagIntArray) tag).func_150302_c();
        add(16L * values.length);
        if (borrowed) ref(values);
      }
    }
  }

  static final class Rule {
    final ItemStack[] inputs, outputs;
    final boolean[] subtypes;
    final int jars;

    Rule(ItemStack[] inputs, ItemStack[] outputs, boolean[] subtypes, int jars) {
      this.inputs = inputs;
      this.outputs = outputs;
      this.subtypes = subtypes;
      this.jars = jars;
    }
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Distillery source closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    rules.clear();
    jar = null;
    roots = null;
    table = null;
    fixtureJar = null;
    signature = null;
    if (identities != null) identities.clear();
    identities = null;
  }

  private static Jobs.Fault notReady() {
    return new Jobs.Fault(
        "handler_not_ready", "Native distillery registry or jar role unavailable");
  }

  private static Jobs.Fault unsupported(String detail) {
    return new Jobs.Fault("recipe_unsupported", "Unadapted distillery source " + detail);
  }

  private static Jobs.Fault budget() {
    return new Jobs.Fault("source_budget", "Distillery observation exceeds cumulative budget");
  }

  private static Jobs.Fault changed(Throwable cause) {
    Jobs.Fault fault =
        new Jobs.Fault("environment_changed", "Distillery native source/member changed");
    if (cause != null) fault.initCause(cause);
    return fault;
  }
}
