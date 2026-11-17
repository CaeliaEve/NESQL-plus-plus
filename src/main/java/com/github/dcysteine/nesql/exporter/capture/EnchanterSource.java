package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Source graph for the installed enchanter. No native recipe/display callbacks. */
final class EnchanterSource implements AutoCloseable {
  private static final String MANAGER =
      "crazypants.enderio.machine.enchanter.EnchanterRecipeManager";
  private static final String RECIPE = "crazypants.enderio.machine.enchanter.EnchanterRecipe";
  List<?> records;
  final int baseCost;
  private final boolean production;
  private Object[] roots;
  private List<Object> identities;
  private String signature;
  private int count;
  private boolean closed;
  private Map<Integer, Set<Integer>> ownedOreIds;
  private Object oreRoot, namesRoot;
  private int oreCount, namesCount;

  static EnchanterSource open() {
    return new EnchanterSource();
  }

  private EnchanterSource() {
    MagicApi.version("EnchanterSource","EnderIO");
    production = true;
    roots = roots();
    records = (List<?>) roots[1];
    baseCost = (Integer) roots[2];
    initialize();
  }

  EnchanterSource(List<?> records, int baseCost) {
    this.records = records;
    this.baseCost = baseCost;
    production = false;
    if (records == null)
      throw new Jobs.Fault("handler_not_ready", "Native enchanter table unavailable");
    // Controlled native tests may use JDK fixed-size lists. Production requires exact ArrayList.
    if (records.getClass() != ArrayList.class
        && !records.getClass().getName().equals("java.util.Arrays$ArrayList"))
      throw unsupported("test registry storage");
    initialize();
  }

  private void initialize() {
    try {
      count = records.size();
      if (count > 262144) throw budget();
      Observation value = observe();
      identities = value.refs;
      signature = value.signature;
      ownedOreIds = value.oreIndex;
      if (value.oreRoot != null) {
        oreRoot = value.oreRoot;
        namesRoot = value.namesRoot;
        oreCount = ((Map<?, ?>) oreRoot).size();
        namesCount = ((List<?>) namesRoot).size();
      }
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static Object[] registrationAnchors(IRecipeHandler handler) {
    Object[] roots = roots();
    List<Object> result = new ArrayList<>();
    result.add(handler.getClass());
    for (int i = 0; i < roots.length; i++) if (i != 2 && roots[i] != null) result.add(roots[i]);
    return result.toArray();
  }

  private static Object[] roots() {
    Object manager = field(type(MANAGER), null, "instance");
    if (manager == null)
      throw new Jobs.Fault("handler_not_ready", "Native enchanter manager unavailable");
    if (!manager.getClass().getName().equals(MANAGER)) throw unsupported("manager callback owner");
    Object source = field(manager, "recipes");
    if (source == null)
      throw new Jobs.Fault("handler_not_ready", "Native enchanter table unavailable");
    if (source.getClass() != ArrayList.class) throw unsupported("registry storage");
    return new Object[] {
      manager,
      source,
      Integer.valueOf(
          (Integer)
              field(type("crazypants.enderio.config.Config"), null, "enchanterBaseLevelCost")),
      Enchantment.enchantmentsList,
      Item.itemRegistry,
      field(OreDictionary.class, null, "stackToId"),
      field(OreDictionary.class, null, "idToName"),
      Items.writable_book,
      Items.enchanted_book
    };
  }

  void verifyRoots() {
    check();
    if (records.size() != count) throw changed(null);
    if (oreRoot != null) {
      Object index = field(OreDictionary.class, null, "stackToId"),
          names = field(OreDictionary.class, null, "idToName");
      if (index != oreRoot
          || names != namesRoot
          || ((Map<?, ?>) index).size() != oreCount
          || ((List<?>) names).size() != namesCount) throw changed(null);
    }
    if (production) {
      Object[] current;
      try {
        current = roots();
      } catch (Jobs.Fault failure) {
        throw changed(failure);
      }
      for (int i = 0; i < roots.length; i++)
        if (i == 2 ? !roots[i].equals(current[i]) : roots[i] != current[i]) throw changed(null);
    }
  }

  void verify() {
    verifyRoots();
    Observation value;
    try {
      value = observe();
    } catch (Jobs.Fault failure) {
      throw changed(failure);
    }
    if (!signature.equals(value.signature) || identities.size() != value.refs.size())
      throw changed(null);
    for (int i = 0; i < identities.size(); i++)
      if (identities.get(i) != value.refs.get(i)) throw changed(null);
  }

  private Observation observe() {
    Observation value = new Observation();
    JsonArray rows = array();
    Set<Integer> usedOres = new HashSet<>();
    for (Object record : records) {
      Jobs.checkpoint();
      if (record == null || !record.getClass().getName().equals(RECIPE))
        throw unsupported("recipe callback owner");
      value.ref(record);
      Object input = field(record, "input"), ench = field(record, "enchantment");
      value.ref(input);
      value.ref(ench);
      JsonObject reagent = null;
      if (input != null) {
        EnderInputs.check(input);
        value.add(256);
        Object raw = field(type(EnderInputs.INPUT), input, "input");
        if (input.getClass().getName().equals(EnderInputs.ORE))
          usedOres.add((Integer) field(input, "oreId"));
        if (raw != null && !(raw instanceof ItemStack)) throw unsupported("input stack");
        reagent =
            object(
                "owner",
                input.getClass().getName(),
                "stack",
                value.stack((ItemStack) raw),
                "useMeta",
                field(type(EnderInputs.INPUT), input, "useMeta"),
                "ore",
                input.getClass().getName().equals(EnderInputs.ORE) ? field(input, "oreId") : null);
      }
      int cost = (Integer) field(record, "costPerLevel");
      boolean valid =
          ench != null
              && input != null
              && field(type(EnderInputs.INPUT), input, "input") != null
              && cost >= 0;
      int maximum = valid ? maximum((Enchantment) ench) : 0;
      if (valid) {
        Item[] books = {Items.writable_book, Items.enchanted_book};
        String[] roles = {"minecraft:writable_book", "minecraft:enchanted_book"};
        for (int role = 0; role < books.length; role++) {
          NativeStackAudit.identityOwner(books[role]);
          if (books[role] == null || Item.itemRegistry.getObject(roles[role]) != books[role])
            throw unsupported("book role identity");
          value.ref(books[role]);
        }
        if (Items.enchanted_book.getClass() != net.minecraft.item.ItemEnchantedBook.class)
          throw unsupported("enchanted-book writer callback");
      }
      if (valid) {
        Enchantment enchantment = (Enchantment) ench;
        value.ref(Enchantment.enchantmentsList);
        if (enchantment.effectId < 0
            || enchantment.effectId >= Enchantment.enchantmentsList.length
            || Enchantment.enchantmentsList[enchantment.effectId] != enchantment)
          throw unsupported("unregistered enchantment identity");
        value.ref(Enchantment.enchantmentsList[enchantment.effectId]);
      }
      rows.add(
          object(
              "input",
              reagent,
              "enchantment",
              ench == null ? null : ((Enchantment) ench).effectId,
              "maximum",
              maximum,
              "costPerLevel",
              cost,
              "stackSizePerLevel",
              field(record, "stackSizePerLevel")));
    }
    JsonArray oreRows = array();
    if (!usedOres.isEmpty()) {
      Object rawIndex = field(OreDictionary.class, null, "stackToId"),
          rawNames = field(OreDictionary.class, null, "idToName");
      if (rawIndex == null
          || rawIndex.getClass() != HashMap.class
          || rawNames == null
          || rawNames.getClass() != ArrayList.class) throw unsupported("baked ore storage");
      Map<?, ?> index = (Map<?, ?>) rawIndex;
      List<?> names = (List<?>) rawNames;
      value.ref(index);
      value.ref(names);
      value.oreRoot = index;
      value.namesRoot = names;
      if (index.size() > 262144 || names.size() > 262144) throw budget();
      for (int id : new TreeSet<>(usedOres)) {
        Object name = id >= 0 && id < names.size() ? names.get(id) : null;
        if (name != null && !(name instanceof String)) throw unsupported("ore name");
        oreRows.add(object("id", id, "name", name));
      }
      TreeMap<Integer, Object> ordered = new TreeMap<>();
      for (Map.Entry<?, ?> entry : index.entrySet()) {
        if (!(entry.getKey() instanceof Integer)
            || entry.getValue() == null
            || entry.getValue().getClass() != ArrayList.class)
          throw unsupported("baked ore member");
        ordered.put((Integer) entry.getKey(), entry.getValue());
      }
      for (Map.Entry<Integer, Object> entry : ordered.entrySet()) {
        JsonArray ids = array();
        boolean relevant = false;
        for (Object id : (List<?>) entry.getValue()) {
          value.add(16);
          if (!(id instanceof Integer) || (Integer) id < 0 || (Integer) id >= names.size())
            throw unsupported("baked ore ID");
          ids.add(value(id));
          relevant |= usedOres.contains(id);
        }
        if (relevant) {
          value.ref(entry.getValue());
          int key = entry.getKey();
          Item item = Item.getItemById(key & 65535);
          Set<Integer> members = new HashSet<>();
          for (Object id : (List<?>) entry.getValue()) members.add((Integer) id);
          value.oreIndex.put(key, members);
          if (item == null) throw unsupported("baked ore item");
          NativeStackAudit.identityOwner(item);
          value.ref(item);
          String name = Item.itemRegistry.getNameForObject(item);
          if (name == null || name.length() > 4096) throw unsupported("baked ore item registry");
          value.add(128 + 6L * name.length());
          oreRows.add(object("key", key, "item", name, "ids", ids));
        }
      }
    }
    value.signature = CanonicalJson.digest(object("rows", rows, "ores", oreRows));
    return value;
  }

  List<EnderInputs.Pattern> patterns(Object input) {
    check();
    EnderInputs.check(input);
    if (!input.getClass().getName().equals(EnderInputs.ORE)) return EnderInputs.patterns(input);
    int ore = (Integer) field(input, "oreId");
    List<EnderInputs.Pattern> result = new ArrayList<>();
    if (ore < 0) return result;
    Set<String> seen = new HashSet<>();
    for (int key : new TreeSet<>(ownedOreIds.keySet()))
      if (ownedOreIds.get(key).contains(ore)) {
        Item item = Item.getItemById(key & 65535);
        if (item == null) throw unsupported("baked ore item");
        NativeStackAudit.identityOwner(item);
        int code = key >>> 16;
        String token = Item.getIdFromItem(item) + ":" + code;
        if (seen.add(token))
          result.add(
              new EnderInputs.Pattern(
                  new ItemStack(item, 1, code == 0 ? 0 : code - 1),
                  code == 0,
                  code == 0 ? null : code));
        if (result.size() > 65536) throw budget();
      }
    return result;
  }

  static int maximum(Enchantment enchantment) {
    String method =
        cpw.mods.fml.relauncher.ReflectionHelper.findMethod(
                Enchantment.class, null, new String[] {"getMaxLevel", "func_77325_b"})
            .getName();
    try {
      Method getter = enchantment.getClass().getMethod(method);
      String owner = getter.getDeclaringClass().getName();
      // Installed getters are literal constants (javap); no mod callback needs execution.
      switch (owner) {
        case "com.enderio.core.common.enchant.EnchantXPBoost":
          MagicApi.version("EnchanterSource","endercore");
          return 3;
        case "com.enderio.core.common.enchant.EnchantAutoSmelt":
          MagicApi.version("EnchanterSource","endercore");
          return 1;
        case "crazypants.enderio.enchantment.EnchantmentSoulBound":
          MagicApi.version("EnchanterSource","EnderIO");
          return 1;
        case "crazypants.enderzoo.enchantment.EnchantmentWitherArrow":
        case "crazypants.enderzoo.enchantment.EnchantmentWitherWeapon":
          MagicApi.version("EnchanterSource","EnderZoo");
          return 1;
        case "thaumcraft.common.lib.enchantment.EnchantmentRepair":
          MagicApi.version("EnchanterSource","Thaumcraft");
          return 2;
        case "thaumcraft.common.lib.enchantment.EnchantmentFrugal":
        case "thaumcraft.common.lib.enchantment.EnchantmentPotency":
        case "thaumcraft.common.lib.enchantment.EnchantmentHaste":
        case "thaumcraft.common.lib.enchantment.EnchantmentWandFortune":
          MagicApi.version("EnchanterSource","Thaumcraft");
          return 3;
        case "cofh.core.enchantment.EnchantmentHolding":
        case "cofh.core.enchantment.EnchantmentMultishot":
          MagicApi.version("EnchanterSource","CoFHCore");
          return 4;
        default:
          break;
      }
      // Exact vanilla constant getters. Unknown mod overrides must be reviewed before execution.
      Set<String> owners =
          new HashSet<>(
              Arrays.asList(
                  "Enchantment",
                  "EnchantmentProtection",
                  "EnchantmentDamage",
                  "EnchantmentDigging",
                  "EnchantmentDurability",
                  "EnchantmentLootBonus",
                  "EnchantmentOxygen",
                  "EnchantmentWaterWorker",
                  "EnchantmentUntouching",
                  "EnchantmentThorns",
                  "EnchantmentKnockback",
                  "EnchantmentFireAspect",
                  "EnchantmentArrowDamage",
                  "EnchantmentArrowKnockback",
                  "EnchantmentArrowFire",
                  "EnchantmentArrowInfinite",
                  "EnchantmentFishingSpeed"));
      if (!owner.startsWith("net.minecraft.enchantment.")
          || !owners.contains(owner.substring("net.minecraft.enchantment.".length())))
        throw unsupported("enchantment maximum callback: " + owner);
      return enchantment.getMaxLevel();
    } catch (NoSuchMethodException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static final class Observation {
    final List<Object> refs = new ArrayList<>();
    long bytes;
    int nodes;
    String signature;
    final Map<Integer, Set<Integer>> oreIndex = new HashMap<>();
    Object oreRoot, namesRoot;

    void ref(Object value) {
      if (refs.size() >= 262144) throw budget();
      refs.add(value);
    }

    void add(long amount) {
      if ((bytes += amount) > 32L * 1024 * 1024 || ++nodes > 262144) throw budget();
    }

    JsonElement stack(ItemStack stack) {
      if (stack == null) return JsonNull.INSTANCE;
      NativeStackAudit.audit(stack);
      NativeStackAudit.identityOwner(stack.getItem());
      String name = Item.itemRegistry.getNameForObject(stack.getItem());
      if (name == null || name.length() > 4096) throw unsupported("item identity");
      if (stack.getItemDamage() < 0) throw unsupported("negative copied metadata");
      ref(stack);
      ref(stack.getItem());
      nbt(stack.getTagCompound());
      add(256 + 6L * name.length());
      return RecipeRow.stackEvidence(stack);
    }

    void nbt(net.minecraft.nbt.NBTBase tag) {
      if (tag == null) return;
      ref(tag);
      add(64);
      if (tag.getClass() == net.minecraft.nbt.NBTTagCompound.class) {
        Map<?, ?> map = (Map<?, ?>) field(tag, "tagMap");
        ref(map);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          add(6L * ((String) entry.getKey()).length());
          nbt((net.minecraft.nbt.NBTBase) entry.getValue());
        }
      } else if (tag.getClass() == net.minecraft.nbt.NBTTagList.class) {
        List<?> list = (List<?>) field(tag, "tagList");
        ref(list);
        for (Object member : list) nbt((net.minecraft.nbt.NBTBase) member);
      } else if (tag.getClass() == net.minecraft.nbt.NBTTagString.class)
        add(6L * ((net.minecraft.nbt.NBTTagString) tag).func_150285_a_().length());
      else if (tag.getClass() == net.minecraft.nbt.NBTTagByteArray.class) {
        byte[] raw = ((net.minecraft.nbt.NBTTagByteArray) tag).func_150292_c();
        ref(raw);
        add(8L * raw.length);
      } else if (tag.getClass() == net.minecraft.nbt.NBTTagIntArray.class) {
        int[] raw = ((net.minecraft.nbt.NBTTagIntArray) tag).func_150302_c();
        ref(raw);
        add(16L * raw.length);
      }
    }
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Native enchanter source closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    roots = null;
    records = null;
    oreRoot = null;
    namesRoot = null;
    if (ownedOreIds != null) ownedOreIds.clear();
    ownedOreIds = null;
    if (identities != null) identities.clear();
    identities = null;
    signature = null;
  }

  private static Jobs.Fault unsupported(String message) {
    return new Jobs.Fault("recipe_unsupported", "Unadapted enchanter source " + message);
  }

  private static Jobs.Fault budget() {
    return new Jobs.Fault("source_budget", "Enchanter source exceeds cumulative budget");
  }

  private static Jobs.Fault changed(Throwable cause) {
    Jobs.Fault fault =
        new Jobs.Fault("recipe_changed", "Native enchanter source identity/facts changed");
    if (cause != null) fault.initCause(cause);
    return fault;
  }
}
