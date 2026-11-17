package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraftforge.oredict.OreDictionary;

/** Native definition identities, audited before constructing any display witnesses. */
final class CreativeCoreBindings implements AutoCloseable {
  private static final String ROOT = "com.creativemd.creativecore.";
  private Object[] roots;
  private List<Object> identities;
  private String signature;
  private int[] counts;
  private boolean closed;

  CreativeCoreBindings() {
    try {
      roots = roots();
      counts = counts(roots);
      Observation first = observe();
      identities = first.refs;
      signature = first.signature;
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  List<?> table() {
    check();
    return (List<?>) roots[1];
  }

  static Object[] registrationAnchors(IRecipeHandler handler) {
    Object[] roots = roots(), result = new Object[roots.length + 1];
    result[0] = handler.getClass();
    System.arraycopy(roots, 0, result, 1, roots.length);
    return result;
  }

  private static Object[] roots() {
    for (Object registry : new Object[] {Item.itemRegistry, Block.blockRegistry})
      if (registry == null
          || !registry
              .getClass()
              .getName()
              .equals("cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry"))
        throw unsupported("item/block registry callback owner");
    CraftingManager manager = CraftingManager.getInstance();
    if (manager == null)
      throw new Jobs.Fault("handler_not_ready", "CreativeCore crafting manager unavailable");
    if (manager.getClass() != CraftingManager.class) throw unsupported("crafting manager");
    List<?> table = manager.getRecipeList();
    if (table == null)
      throw new Jobs.Fault("handler_not_ready", "CreativeCore crafting table unavailable");
    if (table.getClass() != ArrayList.class) throw unsupported("crafting table storage");
    if (table.size() > 262144) throw budget();
    Object[] result =
        new Object[] {
          manager,
          table,
          Item.itemRegistry,
          net.minecraft.block.Block.blockRegistry,
          field(OreDictionary.class, null, "nameToId"),
          field(OreDictionary.class, null, "idToStack"),
          field(OreDictionary.class, null, "stackToId"),
          field(OreDictionary.class, null, "idToName")
        };
    for (int index = 4; index < result.length; index++) {
      boolean accepted = index == 4 ? NativeOreStorage.names(result[index])
          : index == 6 ? NativeOreStorage.reverse(result[index])
          : result[index] != null && result[index].getClass() == ArrayList.class;
      if (!accepted)
        throw unsupported("Forge ore table storage");
    }
    return result;
  }

  private static int[] counts(Object[] roots) {
    int[] result = new int[roots.length + 2];
    for (int index = 0; index < roots.length; index++) {
      Object value = roots[index];
      result[index] =
          value instanceof Map
              ? ((Map<?, ?>) value).size()
              : value instanceof List ? ((List<?>) value).size() : -1;
      if (result[index] > 262144) throw budget();
    }
    result[roots.length] = Item.itemRegistry.getKeys().size();
    result[roots.length + 1] = Block.blockRegistry.getKeys().size();
    return result;
  }

  void verifyRoots() {
    check();
    Object[] current;
    try {
      current = roots();
    } catch (Jobs.Fault cause) {
      throw changed(cause);
    }
    for (int index = 0; index < roots.length; index++)
      if (roots[index] != current[index]) throw changed(null);
    if (!Arrays.equals(counts, counts(current))) throw changed(null);
  }

  void verify() {
    verifyRoots();
    Observation next;
    try {
      next = observe();
    } catch (Jobs.Fault cause) {
      throw changed(cause);
    }
    if (!signature.equals(next.signature) || identities.size() != next.refs.size())
      throw changed(null);
    for (int index = 0; index < identities.size(); index++)
      if (identities.get(index) != next.refs.get(index)) throw changed(null);
  }

  private Observation observe() {
    Observation result = new Observation();
    JsonArray recipes = array();
    Set<String> referencedOres = new HashSet<>();
    Class<?> api = type(ROOT + "common.recipe.IRecipeInfo");
    for (Object recipe : (List<?>) roots[1]) {
      Jobs.checkpoint();
      result.ref(recipe);
      if (!api.isInstance(recipe)) continue;
      String name = recipe.getClass().getName();
      boolean shaped = name.equals(ROOT + "common.recipe.BetterShapedRecipe");
      if (!shaped && !name.equals(ROOT + "common.recipe.entry.BetterShapelessRecipe"))
        throw unsupported("IRecipeInfo owner: " + name);
      Object raw = field(recipe, "info");
      if (raw == null) throw unsupported("null predicate storage");
      List<?> inputs;
      if (shaped) {
        if (raw.getClass()
            != java.lang.reflect.Array.newInstance(type(ROOT + "common.utils.stack.StackInfo"), 0)
                .getClass()) throw unsupported("shaped predicate array");
        inputs = Arrays.asList((Object[]) raw);
      } else {
        if (raw.getClass() != ArrayList.class) throw unsupported("shapeless predicate list");
        inputs = (List<?>) raw;
      }
      if (inputs.size() > 65536) throw budget();
      result.ref(raw);
      JsonArray predicates = array();
      for (Object input : inputs) {
        if (input == null) {
          predicates.add(JsonNull.INSTANCE);
          continue;
        }
        result.ref(input);
        result.add(256);
        String owner = input.getClass().getName();
        JsonObject predicate = object("owner", owner, "minimum", field(input, "stackSize"));
        switch (owner.substring(owner.lastIndexOf('.') + 1)) {
          case "StackInfoItem":
            require(input, "StackInfoItem");
            Object item = field(input, "item");
            if (!(item instanceof Item)) throw unsupported("item predicate");
            result.ref(item);
            predicate.add("item", result.stack(new ItemStack((Item) item), false));
            break;
          case "StackInfoItemStack":
            require(input, "StackInfoItemStack");
            predicate.add("stack", result.stack((ItemStack) field(input, "stack"), true));
            predicate.add("needNBT", value(field(input, "needNBT")));
            break;
          case "StackInfoOre":
            require(input, "StackInfoOre");
            Object ore = field(input, "ore");
            if (!(ore instanceof String) || ((String) ore).length() > 4096)
              throw unsupported("ore name");
            result.add(6L * ((String) ore).length());
            predicate.add("ore", value(ore));
            referencedOres.add((String) ore);
            break;
          case "StackInfoBlock":
            require(input, "StackInfoBlock");
            result.ref(field(input, "block"));
            break;
          case "StackInfoMaterial":
            require(input, "StackInfoMaterial");
            result.ref(field(input, "material"));
            break;
          case "StackInfoFuel":
            require(input, "StackInfoFuel");
            break;
          default:
            throw unsupported("predicate owner: " + owner);
        }
        predicates.add(predicate);
      }
      recipes.add(
          object(
              "owner",
              name,
              "width",
              field(recipe, "width"),
              "height",
              shaped ? field(recipe, "height") : null,
              "predicates",
              predicates,
              "output",
              result.stack((ItemStack) field(recipe, "output"), true)));
    }
    // Observe actual item/block identities and the baked ore index independently of the display
    // stack table. Mutation of idToStack alone does not change StackInfoOre.matches.
    JsonArray items = array(), blocks = array(), ores = array(), baked = array();
    for (String name : names(Item.itemRegistry.getKeys())) {
      Object raw = Item.itemRegistry.getObject(name);
      if (!(raw instanceof Item)) throw unsupported("item census member");
      Item item = (Item) raw;
      // This census uses string registry keys, native identity IDs and direct fields.
      // It never uses this unrelated item as a hash/equality key. Actual recipe
      // predicates still audit their keys before invoking native lookups.
      result.ref(item);
      result.add(128 + 6L * name.length());
      Object container = field(Item.class, item, "containerItem");
      result.ref(container);
      Block itemBlock = Block.getBlockFromItem(item);
      result.ref(itemBlock);
      items.add(object("name", name, "id", Item.getIdFromItem(item)));
    }
    for (String name : names(Block.blockRegistry.getKeys())) {
      Object raw = Block.blockRegistry.getObject(name);
      if (!(raw instanceof Block)) throw unsupported("block census member");
      Block block = (Block) raw;
      result.ref(block);
      result.ref(field(Block.class, block, "blockMaterial"));
      result.add(128 + 6L * name.length());
      blocks.add(object("name", name, "id", Block.getIdFromBlock(block)));
    }
    Map<?, ?> nameIds = (Map<?, ?>) roots[4];
    List<?> oreNames = (List<?>) roots[7];
    if (nameIds.size() != oreNames.size()) throw unsupported("Forge ore census lengths");
    for (int index = 0; index < oreNames.size(); index++) {
      Object name = oreNames.get(index);
      if (!(name instanceof String)
          || ((String) name).length() > 4096
          || !Integer.valueOf(index).equals(nameIds.get(name)))
        throw unsupported("Forge ore identity");
      if (!referencedOres.contains(name)) continue;
      result.add(64 + 6L * ((String) name).length());
      ores.add(object("name", name, "id", index));
    }
    Map<?, ?> stackIds = (Map<?, ?>) roots[6];
    for (Object rawKey : stackIds.keySet())
      if (!(rawKey instanceof Integer)) throw unsupported("Forge ore index key");
    List<Integer> keys = new ArrayList<>();
    for (Object key : stackIds.keySet()) keys.add((Integer) key);
    Collections.sort(keys);
    for (int key : keys) {
      Object raw = stackIds.get(key);
      if (!NativeOreStorage.indices(stackIds, raw))
        throw unsupported("Forge baked ore list");
      JsonArray ids = array();
      boolean relevant = false;
      for (Object id : (List<?>) raw) {
        if (!(id instanceof Integer) || (Integer) id < 0 || (Integer) id >= oreNames.size())
          throw unsupported("Forge baked ore ID");
        result.add(16);
        ids.add(value(id));
        relevant |= referencedOres.contains(oreNames.get((Integer) id));
      }
      if (relevant) {
        result.ref(raw);
        baked.add(object("key", key, "ids", ids));
      }
    }
    result.signature =
        CanonicalJson.digest(
            object(
                "recipes", recipes, "items", items, "blocks", blocks, "ores", ores, "baked",
                baked));
    return result;
  }

  private static List<String> names(Set<?> raw) {
    if (raw.size() > 262144) throw budget();
    List<String> values = new ArrayList<>();
    for (Object name : raw) {
      if (!(name instanceof String) || ((String) name).length() > 4096)
        throw unsupported("registry name");
      values.add((String) name);
    }
    Collections.sort(values);
    return values;
  }

  private static void require(Object predicate, String suffix) {
    if (!predicate.getClass().getName().equals(ROOT + "common.utils.stack." + suffix))
      throw unsupported("predicate callback owner");
  }

  private static final class Observation {
    final List<Object> refs = new ArrayList<>();
    long bytes;
    int nodes;
    String signature;

    void ref(Object value) {
      if (refs.size() >= 1048576) throw budget();
      refs.add(value);
    }

    void add(long amount) {
      if ((bytes += amount) > 32L * 1024 * 1024 || ++nodes > 262144) throw budget();
    }

    JsonElement stack(ItemStack value, boolean borrowed) {
      if (value == null) return JsonNull.INSTANCE;
      NativeStackAudit.audit(value);
      Item item = value.getItem();
      try {
        if (item.getClass().getMethod("hashCode").getDeclaringClass() != Object.class
            || item.getClass().getMethod("equals", Object.class).getDeclaringClass()
                != Object.class) throw unsupported("item hash/equality callback");
      } catch (NoSuchMethodException impossible) {
        throw new AssertionError(impossible);
      }
      String name = Item.itemRegistry.getNameForObject(item);
      if (name == null || name.length() > 4096) throw unsupported("item identity");
      if (value.getItemDamage() < 0)
        throw unsupported("negative metadata changes under native copy");
      add(256 + 6L * name.length());
      ref(item);
      if (borrowed) ref(value);
      nbt(value.getTagCompound(), borrowed);
      return object(
          "item",
          name,
          "id",
          Item.getIdFromItem(item),
          "meta",
          value.getItemDamage(),
          "amount",
          value.stackSize,
          "nbt",
          TypedNbt.encode(value.getTagCompound()));
    }

    void nbt(net.minecraft.nbt.NBTBase value, boolean borrowed) {
      if (value == null) return;
      add(64);
      if (borrowed) ref(value);
      if (value.getClass() == net.minecraft.nbt.NBTTagCompound.class) {
        Map<?, ?> entries = (Map<?, ?>) field(value, "tagMap");
        if (borrowed) ref(entries);
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
          add(6L * ((String) entry.getKey()).length());
          nbt((net.minecraft.nbt.NBTBase) entry.getValue(), borrowed);
        }
      } else if (value.getClass() == net.minecraft.nbt.NBTTagList.class) {
        List<?> entries = (List<?>) field(value, "tagList");
        if (borrowed) ref(entries);
        for (Object entry : entries) nbt((net.minecraft.nbt.NBTBase) entry, borrowed);
      } else if (value.getClass() == net.minecraft.nbt.NBTTagString.class)
        add(6L * ((net.minecraft.nbt.NBTTagString) value).func_150285_a_().length());
      else if (value.getClass() == net.minecraft.nbt.NBTTagByteArray.class) {
        byte[] values = ((net.minecraft.nbt.NBTTagByteArray) value).func_150292_c();
        add(8L * values.length);
        if (borrowed) ref(values);
      } else if (value.getClass() == net.minecraft.nbt.NBTTagIntArray.class) {
        int[] values = ((net.minecraft.nbt.NBTTagIntArray) value).func_150302_c();
        add(16L * values.length);
        if (borrowed) ref(values);
      }
    }
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "CreativeCore bindings closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    roots = null;
    counts = null;
    signature = null;
    if (identities != null) identities.clear();
    identities = null;
  }

  private static Jobs.Fault unsupported(String detail) {
    return new Jobs.Fault("recipe_unsupported", "Unadapted CreativeCore source " + detail);
  }

  private static Jobs.Fault budget() {
    return new Jobs.Fault(
        "source_budget", "CreativeCore source observation exceeds cumulative budget");
  }

  private static Jobs.Fault changed(Throwable cause) {
    Jobs.Fault failure =
        new Jobs.Fault("environment_changed", "CreativeCore source identity/facts changed");
    if (cause != null) failure.initCause(cause);
    return failure;
  }
}
