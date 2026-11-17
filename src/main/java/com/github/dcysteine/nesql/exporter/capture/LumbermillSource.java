package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.*;

/** Owned view of Binnie's actual table, including its audited lazy derivation. */
final class LumbermillSource implements AutoCloseable {
  private static final String LUMBER = "binnie.extratrees.machines.lumbermill.Lumbermill";
  private static final String WOOD = "binnie.extratrees.block.WoodManager";
  private static final String EXTRA = "binnie.extratrees.ExtraTrees";
  private static final String PLANK = "binnie.extratrees.block.PlankType$";
  private final boolean production;
  private Map<?, ?> table;
  private ItemStack rawBark, rawDust;
  private FluidStack rawWater;
  private Object[] anchors;
  private int[] counts;
  private List<Object> identities;
  private String signature;
  List<Row> rows;
  ItemStack bark, dust;
  FluidStack water;
  boolean derived, closed;

  LumbermillSource() {
    this(true, null, null, null, null);
  }

  LumbermillSource(Map<?, ?> table, ItemStack bark, ItemStack dust, FluidStack water) {
    this(false, table, bark, dust, water);
  }

  private LumbermillSource(
      boolean production, Map<?, ?> table, ItemStack bark, ItemStack dust, FluidStack water) {
    this.production = production;
    this.table = table;
    rawBark = bark;
    rawDust = dust;
    rawWater = water;
    try {
      anchors = roots();
      counts = counts(anchors);
      Observation first = observe();
      identities = first.refs;
      signature = first.signature;
      rows = first.rows;
      this.bark = first.bark;
      this.dust = first.dust;
      this.water = first.water;
      derived = first.derived;
      verifyRoots();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static Object[] registrationAnchors(IRecipeHandler handler) {
    List<Object> roots = new ArrayList<>();
    roots.add(handler.getClass());
    for (Object value : productionRoots()) if (value != null) roots.add(value);
    return roots.toArray();
  }

  private Object[] roots() {
    if (production) return productionRoots();
    storage(table, HashMap.class, LinkedHashMap.class);
    return new Object[] {
      table,
      rawBark,
      rawDust,
      rawWater,
      Item.itemRegistry,
      Block.blockRegistry,
      field(OreDictionary.class, null, "stackToId"),
      field(OreDictionary.class, null, "idToName"),
      FluidRegistry.getFluid("water")
    };
  }

  private static Object[] productionRoots() {
    MagicApi.version("LumbermillSource","BinnieCore");
    MagicApi.version("LumbermillSource","ExtraTrees");
    Object source = field(type(LUMBER), null, "recipes");
    if (source == null) throw notReady("recipe map");
    storage(source, HashMap.class, LinkedHashMap.class);
    Object misc = field(type(EXTRA), null, "itemMisc");
    if (misc == null) throw notReady("byproduct item");
    if (!misc.getClass().getName().equals("binnie.core.item.ItemMisc"))
      throw unsupported("byproduct callback owner");
    List<Object> roots =
        new ArrayList<>(
            Arrays.asList(
                source,
                misc,
                field(type("binnie.extratrees.item.ExtraTreeItems"), null, "Bark"),
                field(type("binnie.extratrees.item.ExtraTreeItems"), null, "Sawdust"),
                FluidRegistry.getFluid("water"),
                Item.itemRegistry,
                Block.blockRegistry,
                field(OreDictionary.class, null, "stackToId"),
                field(OreDictionary.class, null, "idToName")));
    if (roots.get(4) == null) throw notReady("water");
    if (((Map<?, ?>) source).isEmpty()) {
      CraftingManager manager = CraftingManager.getInstance();
      if (manager == null) throw notReady("crafting manager");
      Object recipes = manager.getRecipeList();
      if (recipes == null) throw notReady("crafting recipes");
      storage(recipes, ArrayList.class);
      Object planks = field(type(WOOD), null, "allPlankTypes");
      if (planks != null) storage(planks, ArrayList.class);
      Object extraBiomes = field(type("binnie.core.Mods"), null, "extraBiomes");
      if (extraBiomes == null || !extraBiomes.getClass().getName().equals("binnie.core.Mods$Mod"))
        throw unsupported("optional biome role");
      Collections.addAll(
          roots,
          manager,
          recipes,
          planks,
          extraBiomes,
          Boolean.valueOf((Boolean) field(extraBiomes, "isLoaded")),
          field(type(EXTRA), null, "blockPlanks"),
          Blocks.planks,
          cpw.mods.fml.common.registry.GameRegistry.findItem(
              (String) field(modRole("forestry"), "modid"), "planks"),
          field(type(PLANK + "ExtraTreePlanks"), null, "$VALUES"),
          field(type(PLANK + "ForestryPlanks"), null, "$VALUES"),
          field(type(PLANK + "VanillaPlanks"), null, "$VALUES"));
      if ((Boolean) roots.get(13)) {
        roots.add(field(type(PLANK + "ExtraBiomesPlank"), null, "$VALUES"));
        roots.add(field(type("extrabiomes.api.Stuff"), null, "planks"));
      }
      roots.add(modRole("forestry"));
      roots.add(field(modRole("forestry"), "modid"));
    }
    Object miscItems = field(misc, "items");
    if (!(miscItems instanceof Object[]) || ((Object[]) miscItems).length > 65536)
      throw notReady("byproduct definitions");
    roots.add(miscItems);
    return roots.toArray();
  }

  private static Object modRole(String name) {
    Object role = field(type("binnie.core.Mods"), null, name);
    if (role == null || !role.getClass().getName().equals("binnie.core.Mods$Mod"))
      throw unsupported("native mod role");
    Object id = field(role, "modid");
    if (!(id instanceof String) || ((String) id).length() > 256) throw unsupported("native mod ID");
    return role;
  }

  private static int[] counts(Object[] roots) {
    int[] result = new int[roots.length];
    for (int i = 0; i < roots.length; i++) {
      Object v = roots[i];
      result[i] =
          v instanceof Map
              ? ((Map<?, ?>) v).size()
              : v instanceof List
                  ? ((List<?>) v).size()
                  : v instanceof Object[] ? ((Object[]) v).length : -1;
    }
    return result;
  }

  void verifyRoots() {
    check();
    Object[] current;
    try {
      current = roots();
    } catch (Jobs.Fault failure) {
      throw changed(failure);
    }
    if (current.length != anchors.length || !Arrays.equals(counts, counts(current)))
      throw changed(null);
    for (int i = 0; i < anchors.length; i++) if (anchors[i] != current[i]) throw changed(null);
  }

  void verify() {
    verifyRoots();
    Observation current;
    try {
      current = observe();
    } catch (Jobs.Fault failure) {
      throw changed(failure);
    }
    if (!signature.equals(current.signature) || identities.size() != current.refs.size())
      throw changed(null);
    for (int i = 0; i < identities.size(); i++)
      if (identities.get(i) != current.refs.get(i)) throw changed(null);
  }

  private Observation observe() {
    Observation scan = new Observation();
    Map<?, ?> original = (Map<?, ?>) anchors[0];
    if (original.size() > 65536) throw budget();
    if (production) {
      Item misc = (Item) anchors[1];
      scan.ref(misc);
      NativeStackAudit.identityOwner(misc);
      if (Item.itemRegistry.getObject(Item.itemRegistry.getNameForObject(misc)) != misc)
        throw notReady("registered byproduct item");
      scan.ref(anchors[2]);
      scan.ref(anchors[3]);
      Object[] definitions = (Object[]) anchors[anchors.length - 1];
      scan.ref(definitions);
      for (int role : new int[] {2, 3}) {
        if (!(anchors[role] instanceof Enum)
            || !anchors[role].getClass().getName().equals("binnie.extratrees.item.ExtraTreeItems"))
          throw unsupported("byproduct definition owner");
        int ordinal = ((Enum<?>) anchors[role]).ordinal();
        if (ordinal >= definitions.length || definitions[ordinal] != anchors[role])
          throw unsupported("byproduct metadata role");
        scan.ref(definitions[ordinal]);
      }
      scan.bark = new ItemStack(misc, 1, ((Enum<?>) anchors[2]).ordinal());
      scan.dust = new ItemStack(misc, 1, ((Enum<?>) anchors[3]).ordinal());
      scan.water = new FluidStack((Fluid) anchors[4], 300);
    } else {
      scan.bark = scan.stack(rawBark);
      scan.dust = scan.stack(rawDust);
      if (rawWater == null || rawWater.getClass() != FluidStack.class)
        throw unsupported("water stack");
      scan.ref(rawWater);
      scan.ref(rawWater.getFluid());
      NativeStackAudit.nbt(rawWater.tag);
      scan.nbt(rawWater.tag);
      scan.water = rawWater.copy();
    }
    if (scan.water.getFluid() != FluidRegistry.getFluid("water") || scan.water.tag != null)
      throw unsupported("water role");
    scan.bark.stackSize = 1;
    scan.dust.stackSize = 1;
    scan.water.amount = 300;
    scan.auditOwned(scan.bark);
    scan.auditOwned(scan.dust);
    JsonArray sourceRows = array();
    Map<?, ?> selected = original;
    if (production && original.isEmpty()) {
      scan.derived = true;
      selected = derive(scan, original, sourceRows);
    }
    Set<String> seen = new HashSet<>();
    JsonArray entries = array();
    for (Map.Entry<?, ?> entry : selected.entrySet()) {
      Jobs.checkpoint();
      if (!(entry.getKey() instanceof ItemStack) || !(entry.getValue() instanceof ItemStack))
        throw unsupported("map member");
      ItemStack input = scan.stack((ItemStack) entry.getKey());
      ItemStack output =
          scan.derived
              ? ((ItemStack) entry.getValue()).copy()
              : scan.stack((ItemStack) entry.getValue());
      scan.auditOwned(output);
      String firstOre = scan.firstOre(input);
      String key =
          Item.itemRegistry.getNameForObject(input.getItem()) + ":" + input.getItemDamage();
      boolean shadowed = !seen.add(key);
      entries.add(
          object(
              "input",
              RecipeRow.stackEvidence(input),
              "output",
              RecipeRow.stackEvidence(output),
              "firstOre",
              firstOre,
              "shadowed",
              shadowed));
      output.stackSize = 6;
      scan.rows.add(new Row(input, output, firstOre, shadowed));
      scan.add(128);
      if (scan.rows.size() > 65536) throw budget();
    }
    scan.signature =
        CanonicalJson.digest(
            object(
                "derived",
                scan.derived,
                "source",
                sourceRows,
                "entries",
                entries,
                "bark",
                RecipeRow.stackEvidence(scan.bark),
                "dust",
                RecipeRow.stackEvidence(scan.dust),
                "water",
                object(
                    "registry",
                    scan.water.getFluid().getName(),
                    "rawAmount",
                    production ? 300 : rawWater.amount)));
    return scan;
  }

  private Map<ItemStack, ItemStack> derive(
      Observation scan, Map<?, ?> original, JsonArray evidence) {
    Object[] table = (Object[]) field(HashMap.class, original, "table");
    int threshold = (Integer) field(HashMap.class, original, "threshold");
    float load = (Float) field(HashMap.class, original, "loadFactor");
    if (load != 0.75f || threshold < 0 || table != null && table.length > 1048576)
      throw unsupported("lazy map storage");
    int capacity = table == null ? threshold : table.length;
    Map<ItemStack, ItemStack> result =
        original.getClass() == LinkedHashMap.class
            ? new LinkedHashMap<>(
                capacity == 0 ? 16 : capacity, 0.75f, (Boolean) field(original, "accessOrder"))
            : capacity == 0 ? new HashMap<>() : new HashMap<>(capacity);
    evidence.add(
        object(
            "capacity",
            capacity,
            "threshold",
            threshold,
            "linked",
            original.getClass() == LinkedHashMap.class));
    scan.ref(table);
    List<?> nativePlanks = (List<?>) anchors[11];
    List<Object> planks = new ArrayList<>();
    if (nativePlanks != null) planks.addAll(nativePlanks);
    else {
      Collections.addAll(planks, (Object[]) anchors[17]);
      Collections.addAll(planks, (Object[]) anchors[18]);
      if ((Boolean) anchors[13]) Collections.addAll(planks, (Object[]) anchors[20]);
      Collections.addAll(planks, (Object[]) anchors[19]);
    }
    if (planks.size() > 65536) throw budget();
    List<Candidate> candidates = crafting(scan, evidence);
    if ((long) planks.size() * candidates.size() > 4194304) throw budget();
    for (Object plank : planks) {
      Jobs.checkpoint();
      scan.ref(plank);
      ItemStack prototype = plank(scan, plank);
      evidence.add(
          object(
              "plank",
              plank.getClass().getName(),
              "ordinal",
              ((Enum<?>) plank).ordinal(),
              "stack",
              RecipeRow.stackEvidence(prototype)));
      if (prototype == null) continue;
      for (Candidate recipe : candidates) {
        if (recipe.output != null && recipe.output.isItemEqual(prototype)) {
          if (recipe.input == null) throw unsupported("native lazy recipe has a null input");
          result.put(recipe.input, prototype.copy());
          if (result.size() > 65536) throw budget();
        }
      }
    }
    return result;
  }

  private ItemStack plank(Observation scan, Object value) {
    if (value == null || !(value instanceof Enum)) throw unsupported("plank callback owner");
    int meta = ((Enum<?>) value).ordinal();
    Item item;
    switch (value.getClass().getName()) {
      case "binnie.extratrees.block.PlankType$ExtraTreePlanks":
        if (anchors[14] == null) throw notReady("ExtraTrees planks");
        NativeStackAudit.identityOwner(anchors[14]);
        item = Item.getItemFromBlock((Block) anchors[14]);
        scan.ref(anchors[14]);
        break;
      case "binnie.extratrees.block.PlankType$ForestryPlanks":
        item = (Item) anchors[16];
        break;
      case "binnie.extratrees.block.PlankType$VanillaPlanks":
        item = Item.getItemFromBlock((Block) anchors[15]);
        scan.ref(anchors[15]);
        break;
      case "binnie.extratrees.block.PlankType$ExtraBiomesPlank":
        if (!(Boolean) anchors[13]) return null;
        Object optional = anchors[21];
        if (optional != null
            && optional.getClass().getName().equals("com.google.common.base.Absent")) return null;
        if (optional == null
            || !optional.getClass().getName().equals("com.google.common.base.Present"))
          throw notReady("optional biome planks");
        Object block = field(optional, "reference");
        scan.ref(optional);
        scan.ref(block);
        if (!(block instanceof Block)) throw unsupported("optional biome block");
        NativeStackAudit.identityOwner(block);
        item = Item.getItemFromBlock((Block) block);
        break;
      default:
        throw unsupported("plank callback owner: " + value.getClass().getName());
    }
    if (item == null) throw notReady("plank item");
    ItemStack stack = new ItemStack(item, 1, meta);
    scan.auditOwned(stack);
    scan.ref(item);
    return stack;
  }

  private List<Candidate> crafting(Observation scan, JsonArray evidence) {
    List<?> recipes = (List<?>) anchors[10];
    if (recipes.size() > 262144) throw budget();
    List<Candidate> candidates = new ArrayList<>();
    for (Object recipe : recipes) {
      Jobs.checkpoint();
      scan.ref(recipe);
      if (recipe == null) throw unsupported("null crafting record");
      Object input = null, output = null;
      if (recipe instanceof ShapedRecipes) {
        ItemStack[] array = (ItemStack[]) field(ShapedRecipes.class, recipe, "recipeItems");
        scan.ref(array);
        if (array.length != 1) continue;
        getter(recipe, "getRecipeOutput", "func_77571_b", ShapedRecipes.class);
        input = array[0];
        output = field(ShapedRecipes.class, recipe, "recipeOutput");
      } else if (recipe instanceof ShapelessRecipes) {
        Object raw = field(ShapelessRecipes.class, recipe, "recipeItems");
        storage(raw, ArrayList.class);
        scan.ref(raw);
        List<?> values = (List<?>) raw;
        if (values.size() != 1 || !(values.get(0) instanceof ItemStack)) continue;
        getter(recipe, "getRecipeOutput", "func_77571_b", ShapelessRecipes.class);
        input = values.get(0);
        output = field(ShapelessRecipes.class, recipe, "recipeOutput");
      } else if (recipe instanceof ShapelessOreRecipe) {
        getter(recipe, "getInput", "getInput", ShapelessOreRecipe.class);
        Object raw = field(ShapelessOreRecipe.class, recipe, "input");
        storage(raw, ArrayList.class);
        scan.ref(raw);
        List<?> values = (List<?>) raw;
        if (values.size() != 1 || !(values.get(0) instanceof ItemStack)) continue;
        getter(recipe, "getRecipeOutput", "func_77571_b", ShapelessOreRecipe.class);
        input = values.get(0);
        output = field(ShapelessOreRecipe.class, recipe, "output");
      } else continue;
      if (output != null && !(output instanceof ItemStack)) throw unsupported("crafting output");
      ItemStack ownedInput = input == null ? null : scan.stack((ItemStack) input),
          ownedOutput = output == null ? null : scan.stack((ItemStack) output);
      candidates.add(new Candidate((ItemStack) input, ownedOutput));
      evidence.add(
          object(
              "candidate",
              recipe.getClass().getName(),
              "input",
              RecipeRow.stackEvidence(ownedInput),
              "output",
              RecipeRow.stackEvidence(ownedOutput)));
    }
    return candidates;
  }

  private static void getter(Object target, String mcp, String srg, Class<?> owner) {
    Method method = null;
    for (String name : new String[] {mcp, srg})
      try {
        method = target.getClass().getMethod(name);
        break;
      } catch (NoSuchMethodException ignored) {
      }
    if (method == null || method.getDeclaringClass() != owner)
      throw unsupported("crafting getter owner");
  }

  static final class Row {
    final ItemStack input, output;
    final String firstOre;
    final boolean shadowed;

    Row(ItemStack input, ItemStack output, String firstOre, boolean shadowed) {
      this.input = input;
      this.output = output;
      this.firstOre = firstOre;
      this.shadowed = shadowed;
    }
  }

  private static final class Candidate {
    final ItemStack input, output;

    Candidate(ItemStack input, ItemStack output) {
      this.input = input;
      this.output = output;
    }
  }

  private static final class Observation {
    final List<Object> refs = new ArrayList<>();
    final List<Row> rows = new ArrayList<>();
    ItemStack bark, dust;
    FluidStack water;
    String signature;
    boolean derived;
    long bytes;
    int nodes;

    void add(long n) {
      if ((bytes += n) > 32L * 1024 * 1024 || ++nodes > 262144) throw budget();
    }

    void ref(Object v) {
      if (v != null) {
        add(16);
        refs.add(v);
      }
    }

    void auditOwned(ItemStack value) {
      NativeStackAudit.audit(value);
      NativeStackAudit.identityOwner(value.getItem());
      String name = Item.itemRegistry.getNameForObject(value.getItem());
      if (name == null
          || name.length() > 4096
          || Item.itemRegistry.getObject(name) != value.getItem())
        throw unsupported("item registry identity");
      if (value.getItemDamage() < 0) throw unsupported("negative copied metadata");
      add(256 + 6L * name.length());
    }

    ItemStack stack(ItemStack raw) {
      if (raw == null) throw unsupported("null source stack");
      auditOwned(raw);
      ref(raw);
      ref(raw.getItem());
      nbt(raw.getTagCompound());
      return raw.copy();
    }

    void nbt(net.minecraft.nbt.NBTBase tag) {
      if (tag == null) return;
      ref(tag);
      add(64);
      if (tag.getClass() == net.minecraft.nbt.NBTTagCompound.class) {
        Map<?, ?> map = (Map<?, ?>) field(tag, "tagMap");
        ref(map);
        for (Map.Entry<?, ?> e : map.entrySet()) {
          add(6L * ((String) e.getKey()).length());
          nbt((net.minecraft.nbt.NBTBase) e.getValue());
        }
      } else if (tag.getClass() == net.minecraft.nbt.NBTTagList.class) {
        List<?> list = (List<?>) field(tag, "tagList");
        ref(list);
        for (Object v : list) nbt((net.minecraft.nbt.NBTBase) v);
      } else if (tag.getClass() == net.minecraft.nbt.NBTTagString.class)
        add(6L * ((net.minecraft.nbt.NBTTagString) tag).func_150285_a_().length());
      else if (tag.getClass() == net.minecraft.nbt.NBTTagByteArray.class) {
        byte[] v = ((net.minecraft.nbt.NBTTagByteArray) tag).func_150292_c();
        ref(v);
        add(8L * v.length);
      } else if (tag.getClass() == net.minecraft.nbt.NBTTagIntArray.class) {
        int[] v = ((net.minecraft.nbt.NBTTagIntArray) tag).func_150302_c();
        ref(v);
        add(16L * v.length);
      }
    }

    String firstOre(ItemStack item) {
      Object rawIndex = field(OreDictionary.class, null, "stackToId"),
          rawNames = field(OreDictionary.class, null, "idToName");
      if(!NativeOreStorage.reverse(rawIndex))throw unsupported("baked ore reverse storage");
      storage(rawNames, ArrayList.class);
      Map<?, ?> index = (Map<?, ?>) rawIndex;
      List<?> names = (List<?>) rawNames;
      int id = Item.getIdFromItem(item.getItem());
      for (int key : new int[] {id, id | ((item.getItemDamage() + 1) << 16)}) {
        Object ids = index.get(key);
        if (ids == null) continue;
        if(!NativeOreStorage.indices(rawIndex,ids))throw unsupported("baked ore ID storage");
        ref(ids);
        for (Object ore : (List<?>) ids) {
          if (!(ore instanceof Integer) || (Integer) ore < 0 || (Integer) ore >= names.size())
            throw unsupported("baked ore ID");
          Object name = names.get((Integer) ore);
          if (!(name instanceof String) || ((String) name).length() > 65535)
            throw unsupported("ore name");
          ref(name);
          add(16 + 6L * ((String) name).length());
        }
      }
      String name = OreDictionary.getOreName(OreDictionary.getOreID(item));
      add(6L * name.length());
      return name;
    }
  }

  private static void storage(Object value, Class<?>... allowed) {
    for (Class<?> type : allowed) if (value != null && value.getClass() == type) return;
    throw unsupported("source storage");
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Lumbermill source closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    anchors = null;
    counts = null;
    table = null;
    rawBark = null;
    rawDust = null;
    rawWater = null;
    if (identities != null) identities.clear();
    identities = null;
    if (rows != null) rows.clear();
    rows = null;
    bark = null;
    dust = null;
    water = null;
    signature = null;
  }

  private static Jobs.Fault notReady(String detail) {
    return new Jobs.Fault("handler_not_ready", "Native lumbermill " + detail + " unavailable");
  }

  private static Jobs.Fault unsupported(String detail) {
    return new Jobs.Fault("recipe_unsupported", "Unadapted lumbermill " + detail);
  }

  private static Jobs.Fault budget() {
    return new Jobs.Fault("source_budget", "Lumbermill source exceeds cumulative budget");
  }

  private static Jobs.Fault changed(Throwable cause) {
    Jobs.Fault fault =
        new Jobs.Fault("environment_changed", "Native lumbermill source identity or facts changed");
    if (cause != null) fault.initCause(cause);
    return fault;
  }
}
