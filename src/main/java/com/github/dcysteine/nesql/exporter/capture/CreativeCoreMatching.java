package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeStackAudit.identityOwner;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

/** Owned matching law for the pinned recipes. Never calls StackInfo display/matching methods. */
final class CreativeCoreMatching {
  private static final String ROOT = "com.creativemd.creativecore.";
  private static final String STACK = ROOT + "common.utils.stack.";
  final boolean shaped;
  final int width, height;
  final List<Predicate> inputs = new ArrayList<>();
  // Forge's baked lookup is independent of idToStack. It is the actual ore matcher input.
  private final Map<Integer, Set<String>> ores;

  CreativeCoreMatching(Object recipe) {
    this(recipe, new OreLookup());
  }

  CreativeCoreMatching(Object recipe, OreLookup lookup) {
    String owner = recipe.getClass().getName();
    shaped = owner.equals(ROOT + "common.recipe.BetterShapedRecipe");
    if (!shaped && !owner.equals(ROOT + "common.recipe.entry.BetterShapelessRecipe"))
      throw unsupported("recipe owner");
    width = (Integer) field(recipe, "width");
    height = shaped ? (Integer) field(recipe, "height") : 0;
    Object raw = field(recipe, "info");
    List<?> values;
    if (shaped) {
      if (raw == null
          || raw.getClass()
              != java.lang.reflect.Array.newInstance(type(STACK + "StackInfo"), 0).getClass())
        throw unsupported("shaped input storage");
      values = Arrays.asList((Object[]) raw);
    } else {
      if (raw == null || raw.getClass() != ArrayList.class)
        throw unsupported("shapeless input storage");
      values = (List<?>) raw;
    }
    if (values.size() > 65536) throw unsupported("predicate budget");
    boolean ore = false;
    for (Object value : values) {
      Predicate p = value == null ? null : new Predicate(value);
      inputs.add(p);
      ore |= p != null && p.kind.equals("Ore");
    }
    ores = ore ? lookup.table() : Collections.emptyMap();
  }

  /** One owned baked index per registry observation, shared by all ore predicates. */
  static final class OreLookup {
    private Map<Integer, Set<String>> table;

    Map<Integer, Set<String>> table() {
      if (table == null) table = bakedOres();
      return table;
    }
  }

  boolean accepts(int input, ItemStack offered) {
    if (offered == null) return inputs.get(input) == null;
    NativeStackAudit.audit(offered);
    Predicate predicate = inputs.get(input);
    return predicate != null && predicate.accepts(offered, ores);
  }

  JsonObject describe() {
    JsonArray predicates = array();
    ProgramBudget budget = new ProgramBudget();
    for (Predicate predicate : inputs) {
      budget.add(128);
      if (predicate == null) {
        predicates.add(JsonNull.INSTANCE);
        continue;
      }
      JsonObject record =
          object("kind", predicate.kind.toLowerCase(Locale.ROOT), "minimum", predicate.minimum);
      switch (predicate.kind) {
        case "Item":
          record.addProperty("registry", registry(predicate.item));
          break;
        case "ItemStack":
          record.addProperty("registry", registry(predicate.item));
          record.addProperty("metadata", predicate.metadata);
          record.addProperty("needNBT", predicate.needNBT);
          record.add("nbt", TypedNbt.encode(predicate.tag));
          break;
        case "Ore":
          record.addProperty("name", (String) predicate.criterion);
          JsonArray selectors = array();
          List<Integer> keys = new ArrayList<>(ores.keySet());
          Collections.sort(keys);
          for (int key : keys)
            if (ores.get(key).contains(predicate.criterion)) {
              Item item = Item.getItemById(key & 65535);
              String name = registry(item);
              budget.add(128 + 6L * name.length());
              selectors.add(object("registry", name, "code", key >>> 16));
            }
          record.add("selectors", selectors);
          break;
        case "Block":
        case "Material":
          TreeSet<String> identities = new TreeSet<>(), members = new TreeSet<>();
          for (Object raw : Item.itemRegistry) {
            Jobs.checkpoint();
            Item item = (Item) raw;
            Block block = Block.getBlockFromItem(item);
            if (predicate.blockMatches(block)) {
              String name = block == null ? null : Block.blockRegistry.getNameForObject(block);
              if (name == null) throw unsupported("unregistered reachable block/material identity");
              identities.add(name);
              members.add(registry(item));
            }
          }
          if (identities.size() > 65536 || members.size() > 65536)
            throw unsupported("block/material census budget");
          JsonArray identity = array(), names = array();
          for (String name : identities) {
            budget.add(64 + 6L * name.length());
            identity.add(value(name));
          }
          for (String name : members) {
            budget.add(64 + 6L * name.length());
            names.add(value(name));
          }
          record.add("identity", identity);
          record.add("members", names);
          break;
        default:
          throw unsupported("predicate description");
      }
      predicates.add(record);
    }
    return object(
        "kind",
        "creativeCoreCrafting",
        "version",
        "1.5.14-GTNH",
        "shaped",
        shaped,
        "width",
        width,
        "height",
        shaped ? height : null,
        "predicates",
        predicates,
        "matching",
        shaped ? "columnOffsetsUnmirroredThenMirror" : "rowMajorFirstMatchRemoveFirstEqual",
        "consumption",
        "workbenchOnePerOccupiedCellThenNativeContainer");
  }

  private static String registry(Item item) {
    identityOwner(item);
    String name = item == null ? null : Item.itemRegistry.getNameForObject(item);
    if (name == null || name.length() > 4096)
      throw unsupported("unregistered or oversized predicate item");
    return name;
  }

  List<OreExample> oreExamples(int input) {
    Predicate predicate = inputs.get(input);
    if (predicate == null || !predicate.kind.equals("Ore"))
      throw unsupported("not an ore predicate");
    List<OreExample> result = new ArrayList<>();
    List<Integer> keys = new ArrayList<>(ores.keySet());
    Collections.sort(keys);
    for (int key : keys) {
      Jobs.checkpoint();
      if (!ores.get(key).contains(predicate.criterion)) continue;
      Item item = Item.getItemById(key & 65535);
      identityOwner(item);
      if (item == null || Item.itemRegistry.getNameForObject(item) == null)
        throw unsupported("unregistered item in baked ore index");
      int code = key >>> 16;
      result.add(new OreExample(new ItemStack(item, 1, code == 0 ? 0 : code - 1), code == 0));
      if (result.size() > 65536) throw unsupported("ore example budget");
    }
    return result;
  }

  static final class OreExample {
    final ItemStack stack;
    final boolean wildcard;

    OreExample(ItemStack stack, boolean wildcard) {
      this.stack = stack;
      this.wildcard = wildcard;
    }
  }

  private static final class ProgramBudget {
    long bytes;
    int nodes;

    void add(long amount) {
      Jobs.checkpoint();
      if ((bytes += amount) > 32L * 1024 * 1024 || ++nodes > 262144)
        throw new Jobs.Fault(
            "source_budget", "CreativeCore matching program exceeds its cumulative budget");
    }
  }

  /** The order contains the predicate actually removed, which may differ from the matched one. */
  int[] match(ItemStack[] offered) {
    if (offered == null || offered.length != 9) throw unsupported("physical workbench size");
    for (ItemStack stack : offered) if (stack != null) NativeStackAudit.audit(stack);
    if (!shaped) {
      if (inputs.size() > 9) return null;
      List<Integer> left = new ArrayList<>();
      for (int index = 0; index < inputs.size(); index++) left.add(index);
      int[] removed = empty();
      for (int cell = 0; cell < 9; cell++) {
        if (offered[cell] == null) continue;
        int selected = -1;
        for (int index : left) {
          Predicate predicate = inputs.get(index);
          if (predicate == null) throw unsupported("native null shapeless predicate dereference");
          if (predicate.accepts(offered[cell], ores)) {
            selected = index;
            break;
          }
        }
        if (selected < 0) return null;
        // ArrayList.remove(Object) invokes selected.equals(candidate), not identity removal.
        Predicate matched = inputs.get(selected);
        int position = 0;
        while (position < left.size() && !matched.nativeEquals(inputs.get(left.get(position))))
          position++;
        if (position == left.size()) throw unsupported("native nonreflexive predicate equality");
        removed[cell] = left.remove(position);
      }
      return left.isEmpty() ? removed : null;
    }
    // Exactly the installed x/y/false/true search order. Empty/zero dimensions are not GUI errors.
    if (width > 3 || height > 3) return null;
    if (width < 0 || height < 0)
      throw unsupported("negative native dimensions require overflow proof");
    for (int x = 0; x <= 3 - width; x++)
      for (int y = 0; y <= 3 - height; y++)
        for (boolean mirror : new boolean[] {false, true}) {
          int[] result = empty();
          boolean valid = true;
          for (int column = 0; column < 3 && valid; column++)
            for (int row = 0; row < 3; row++) {
              int sx = column - x, sy = row - y, index = -1, cell = column + row * 3;
              if (sx >= 0 && sy >= 0 && sx < width && sy < height) {
                long raw = (mirror ? width - sx - 1L : sx) + (long) sy * width;
                if (raw < 0 || raw >= inputs.size())
                  throw unsupported("native shaped input array bounds");
                index = (int) raw;
              }
              Predicate p = index < 0 ? null : inputs.get(index);
              if (p == null
                  ? offered[cell] != null
                  : offered[cell] == null || !p.accepts(offered[cell], ores)) {
                valid = false;
                break;
              }
              if (p != null) result[cell] = index;
            }
          if (valid) return result;
        }
    return null;
  }

  private static int[] empty() {
    int[] values = new int[9];
    Arrays.fill(values, -1);
    return values;
  }

  private static Map<Integer, Set<String>> bakedOres() {
    Object rawMap = field(OreDictionary.class, null, "stackToId");
    Object rawNames = field(OreDictionary.class, null, "idToName");
    if (rawMap == null
        || !NativeOreStorage.reverse(rawMap)
        || rawNames == null
        || rawNames.getClass() != ArrayList.class) throw unsupported("Forge baked ore storage");
    List<?> names = (List<?>) rawNames;
    Map<?, ?> source = (Map<?, ?>) rawMap;
    if (source.size() > 262144 || names.size() > 262144) throw unsupported("Forge ore budget");
    Map<Integer, Set<String>> result = new HashMap<>();
    int count = 0;
    for (Map.Entry<?, ?> entry : source.entrySet()) {
      if (!(entry.getKey() instanceof Integer)
          || entry.getValue() == null
          || !NativeOreStorage.indices(rawMap,entry.getValue()))
        throw unsupported("Forge baked ore member");
      Set<String> ids = new HashSet<>();
      for (Object value : (List<?>) entry.getValue()) {
        if (++count > 1048576
            || !(value instanceof Integer)
            || (Integer) value < 0
            || (Integer) value >= names.size()) throw unsupported("Forge baked ore ID");
        Object name = names.get((Integer) value);
        if (!(name instanceof String) || ((String) name).length() > 4096)
          throw unsupported("Forge ore name");
        ids.add((String) name);
      }
      result.put((Integer) entry.getKey(), ids);
    }
    return result;
  }

  static final class Predicate {
    final String kind;
    final int minimum, metadata;
    final Item item;
    final NBTTagCompound tag;
    final boolean needNBT;
    final Object criterion;

    Predicate(Object source) {
      String owner = source.getClass().getName();
      if (!owner.startsWith(STACK + "StackInfo")) throw unsupported("predicate owner");
      kind = owner.substring((STACK + "StackInfo").length());
      minimum = (Integer) field(source, "stackSize");
      Item stored = null;
      int meta = 0;
      NBTTagCompound nbt = null;
      boolean need = false;
      Object target = null;
      switch (kind) {
        case "Item":
          stored = (Item) field(source, "item");
          break;
        case "ItemStack":
          ItemStack stack = (ItemStack) field(source, "stack");
          if (stack == null) throw unsupported("null native stack predicate");
          NativeStackAudit.audit(stack);
          stored = stack.getItem();
          meta = stack.getItemDamage();
          nbt =
              stack.getTagCompound() == null
                  ? null
                  : (NBTTagCompound) stack.getTagCompound().copy();
          if (nbt != null && !nbt.equals(stack.getTagCompound()))
            throw unsupported(
                "identity-sensitive native NBT predicate requires an explicit identity law");
          need = (Boolean) field(source, "needNBT");
          break;
        case "Block":
          target = field(source, "block");
          break;
        case "Material":
          target = field(source, "material");
          break;
        case "Ore":
          target = field(source, "ore");
          if (!(target instanceof String) || ((String) target).length() > 4096)
            throw unsupported("ore predicate");
          break;
        case "Fuel":
          // The displayed coal is not a complete predicate. Its arbitrary Forge fuel handlers
          // need a separate reviewed program; do not execute them during capture.
          throw unsupported("fuel callback program has not been audited");
        default:
          throw unsupported("predicate owner: " + owner);
      }
      if ((kind.equals("Item") || kind.equals("ItemStack")) && stored == null)
        throw unsupported("null item predicate");
      item = stored;
      identityOwner(item);
      metadata = meta;
      tag = nbt;
      needNBT = need;
      criterion = target;
    }

    boolean accepts(ItemStack stack, Map<Integer, Set<String>> ores) {
      return stack != null && minimum <= stack.stackSize && acceptsIgnoringCount(stack, ores);
    }

    private boolean acceptsIgnoringCount(ItemStack stack, Map<Integer, Set<String>> ores) {
      switch (kind) {
        case "Item":
          return stack.getItem() == item;
        case "ItemStack":
          return stack.getItem() == item
              && stack.getItemDamage() == metadata
              && (!needNBT || Objects.equals(stack.getTagCompound(), tag));
        case "Block":
        case "Material":
          return blockMatches(Block.getBlockFromItem(stack.getItem()));
        case "Ore":
          int id = Item.getIdFromItem(stack.getItem());
          return ores.getOrDefault(id, Collections.emptySet()).contains(criterion)
              || ores.getOrDefault(id | ((stack.getItemDamage() + 1) << 16), Collections.emptySet())
                  .contains(criterion);
        default:
          throw unsupported("predicate owner");
      }
    }

    private boolean blockMatches(Block block) {
      if (kind.equals("Block")) return criterion == block;
      if (block == null) return false;
      String getter =
          cpw.mods.fml.relauncher.ReflectionHelper.findMethod(
                  Block.class, null, new String[] {"getMaterial", "func_149688_o"})
              .getName();
      try {
        if (block.getClass().getMethod(getter).getDeclaringClass() != Block.class)
          throw unsupported("block material callback");
      } catch (NoSuchMethodException impossible) {
        throw new AssertionError(impossible);
      }
      return criterion == block.getMaterial();
    }

    boolean nativeEquals(Predicate other) {
      if (other == null || minimum != other.minimum || !kind.equals(other.kind)) return false;
      switch (kind) {
        case "Item":
          return item == other.item;
        case "ItemStack":
          // equalsIgnoreSize delegates to the OTHER predicate using this predicate's stored stack.
          return item == other.item
              && metadata == other.metadata
              && (!other.needNBT || Objects.equals(tag, other.tag));
        case "Ore":
          return criterion.equals(other.criterion);
        case "Block":
        case "Material":
          return criterion == other.criterion;
        default:
          throw unsupported("predicate equality");
      }
    }
  }

  private static Jobs.Fault unsupported(String message) {
    return new Jobs.Fault("recipe_unsupported", "CreativeCore matching: " + message);
  }
}
