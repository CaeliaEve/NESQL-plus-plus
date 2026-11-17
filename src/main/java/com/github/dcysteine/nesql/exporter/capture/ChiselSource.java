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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraftforge.oredict.OreDictionary;

/** Audited native carving tables. Only the pinned HashMap fallback is queried in place. */
final class ChiselSource implements AutoCloseable {
  private static final String CARVING = "team.chisel.carving.Carving";
  private static final String TOOL = "team.chisel.item.chisel.ItemChisel";
  private static final String SIMPLE =
      "com.cricketcraft.chisel.api.carving.CarvingUtils$SimpleCarving";
  private static final String TWEAKER = "modtweaker2.mods.chisel.ChiselHelper$Carving";
  private static final String LEAVES = "team.chisel.Features$57$1";
  private static final String[] ORE = {"nameToId", "idToName", "idToStack", "stackToId"};
  private Object[] anchors;
  private int[] counts;
  private List<Object> identities;
  private String signature;
  private boolean closed;
  ChiselRecipes.Snapshot snapshot;

  ChiselSource() {
    try {
      anchors = roots();
      counts = counts(anchors);
      Observation first = observe();
      identities = first.identities;
      signature = first.signature;
      snapshot = first.snapshot;
      verifyRoots();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static Object[] registrationAnchors(IRecipeHandler handler) {
    Object[] roots = roots(), result = new Object[roots.length + 1];
    result[0] = handler.getClass();
    System.arraycopy(roots, 0, result, 1, roots.length);
    return result;
  }

  private static Object[] roots() {
    Object registry = field(type(CARVING), null, "chisel");
    if (registry == null) throw notReady();
    exact(registry, CARVING);
    Object list = field(registry, "groups");
    if (list == null) throw notReady();
    exact(list, "team.chisel.carving.GroupList");
    Object[] result = new Object[13];
    result[0] = registry;
    result[1] = list;
    result[2] = storage(field(list, "groups"), HashMap.class);
    result[3] = storage(field(list, "lookup"), HashMap.class);
    result[4] = Item.itemRegistry;
    result[5] = Block.blockRegistry;
    for (int i = 0; i < ORE.length; i++) {
      Object value = field(OreDictionary.class, null, ORE[i]);
      if (value == null) throw notReady();
      boolean accepted = i == 0 ? NativeOreStorage.names(value)
          : i == 3 ? NativeOreStorage.reverse(value) : value.getClass() == ArrayList.class;
      if (!accepted) throw unsupported("ore table storage");
      result[i + 6] = value;
    }
    result[10] =
        Boolean.valueOf(
            (Boolean) field(type("team.chisel.config.Configurations"), null, "allowChiselDamage"));
    result[11] = field(type(TOOL), null, "carving");
    result[12] = Boolean.valueOf((Boolean)field(type("team.chisel.config.Configurations"), null, "chiselBackToVanillaLeaves"));
    if (result[11] == null) throw notReady();
    if (result[11] != registry
        || field(type("com.cricketcraft.chisel.api.carving.CarvingUtils"), null, "chisel")
            != registry) throw unsupported("inconsistent carving roles");
    return result;
  }

  private static int[] counts(Object[] roots) {
    int[] counts = new int[roots.length + 2];
    for (int i = 0; i < roots.length; i++) {
      Object value = roots[i];
      counts[i] =
          value instanceof Map
              ? ((Map<?, ?>) value).size()
              : value.getClass() == ArrayList.class ? ((List<?>) value).size() : -1;
      if (counts[i] > 262144) throw budget();
    }
    counts[roots.length] = Item.itemRegistry.getKeys().size();
    counts[roots.length + 1] = Block.blockRegistry.getKeys().size();
    return counts;
  }

  void verifyRoots() {
    check();
    Object[] current;
    try {
      current = roots();
    } catch (Jobs.Fault cause) {
      throw changed(cause);
    }
    for (int i = 0; i < anchors.length; i++) if (anchors[i] != current[i]) throw changed(null);
    if (!Arrays.equals(counts, counts(current))) throw changed(null);
  }

  void verify() {
    verifyRoots();
    Observation current;
    try {
      current = observe();
    } catch (Jobs.Fault cause) {
      throw changed(cause);
    }
    if (!signature.equals(current.signature) || identities.size() != current.identities.size())
      throw changed(null);
    for (int i = 0; i < identities.size(); i++)
      if (identities.get(i) != current.identities.get(i)) throw changed(null);
  }

  private Observation observe() {
    Scan scan = new Scan((Boolean)anchors[12]);
    Map<?, ?> names = (Map<?, ?>) anchors[2], lookup = (Map<?, ?>) anchors[3];
    if (names.isEmpty() && lookup.isEmpty()) {
      ChiselRecipes.Snapshot empty = new ChiselRecipes.Snapshot((Boolean) anchors[10]);
      empty.groups = array();
      return new Observation(empty, scan.refs, CanonicalJson.digest(empty.json()));
    }
    Map<String, Group> oreGroups = new HashMap<>();
    JsonArray named = array(), fallback = array();
    // Native getGroupByOre uses this HashMap encounter order, not sorted group names.
    for (Map.Entry<?, ?> entry : names.entrySet()) {
      String key = scan.text(entry.getKey(), false);
      Group group = scan.group(entry.getValue());
      if (!key.equals(group.name)) throw unsupported("group key/name mismatch");
      named.add(object("key", key, "group", group.index));
      if (group.ore != null) oreGroups.putIfAbsent(group.ore, group);
    }
    for (Map.Entry<?, ?> entry : lookup.entrySet()) {
      Object wrapper = entry.getKey();
      exact(wrapper, "team.chisel.carving.GroupList$VariationWrapper");
      if (field(wrapper, "this$0") != anchors[1]) throw unsupported("foreign lookup wrapper");
      scan.ref(wrapper);
      Variation variation = scan.variation(field(wrapper, "v"));
      Group group = scan.group(entry.getValue());
      fallback.add(object("variation", variation.raw, "group", group.index));
    }
    // Group census includes stale lookup groups left behind by native GroupList.clear().
    for (Group group : scan.groups.values())
      for (Variation variation : group.variations) {
        scan.source(new ItemStack(variation.block, 1, variation.blockMeta));
        scan.source(new ItemStack(variation.block, 1, variation.itemMeta));
      }
    for (Variation variation : scan.variations.values()) {
      scan.source(new ItemStack(variation.block, 1, variation.blockMeta));
      scan.source(new ItemStack(variation.block, 1, variation.itemMeta));
    }
    Map<?, ?> oreNames = (Map<?, ?>) anchors[6];
    List<?> idNames = (List<?>) anchors[7], oreStacks = (List<?>) anchors[8];
    Map<?, ?> stackIds = (Map<?, ?>) anchors[9];
    if (oreNames.size() != idNames.size() || oreStacks.size() != idNames.size())
      throw unsupported("ore census");
    JsonArray ores = array(), oreLookup = array();
    Map<String, List<ItemStack>> ownedOres = new HashMap<>();
    Set<String> referencedOres = new HashSet<>();
    for (Group group : scan.orderedGroups) if (group.ore != null) referencedOres.add(group.ore);
    for (int id = 0; id < idNames.size(); id++) {
      String name = scan.text(idNames.get(id), false);
      if (!Integer.valueOf(id).equals(oreNames.get(name))) throw unsupported("ore ID/name");
      if (!referencedOres.contains(name)) continue;
      Object raw = oreStacks.get(id);
      if (raw == null) {
        ores.add(object("name", name, "stacks", null));
        continue;
      }
      storage(raw, ArrayList.class);
      scan.ref(raw);
      JsonArray entries = array();
      List<ItemStack> owned = new ArrayList<>();
      for (Object member : (List<?>) raw) {
        if (!(member instanceof ItemStack)) throw unsupported("ore stack");
        ItemStack stack = scan.borrowedStack((ItemStack) member);
        entries.add(ChiselRecipes.raw(stack));
        owned.add(stack);
        if (oreGroups.containsKey(name)) scan.source(stack);
      }
      ownedOres.put(name, owned);
      ores.add(object("name", name, "stacks", entries));
    }
    for (Map.Entry<?, ?> entry : stackIds.entrySet()) {
      if (!(entry.getKey() instanceof Integer)) throw unsupported("ore lookup key");
      int key = (Integer) entry.getKey();
      Object ids = entry.getValue();
      if (!NativeOreStorage.indices(stackIds,ids)) throw unsupported("ore lookup ID storage");
      scan.ref(ids);
      JsonArray values = array();
      boolean relevant = false;
      for (Object id : (List<?>) ids) {
        if (!(id instanceof Integer) || (Integer) id < 0 || (Integer) id >= idNames.size())
          throw unsupported("ore lookup ID");
        scan.add(16);
        values.add(value(id));
        relevant |= oreGroups.containsKey(idNames.get((Integer) id));
      }
      oreLookup.add(object("key", key, "ids", values));
      if (relevant) {
        Item item = Item.getItemById(key & 65535);
        int meta = (key >>> 16) == 0 ? 32767 : (key >>> 16) - 1;
        if (item == null) throw unsupported("unregistered ore lookup item");
        scan.source(new ItemStack(item, 1, meta));
      }
    }
    scan.hashLayout(lookup);
    ChiselRecipes.Snapshot snapshot = new ChiselRecipes.Snapshot((Boolean) anchors[10]);
    JsonArray tools = array();
    Class<?> api = type("com.cricketcraft.chisel.api.IChiselItem");
    for (Object member : Item.itemRegistry) {
      if (!(member instanceof Item)) throw unsupported("item registry member");
      Item item = (Item) member;
      if (!api.isInstance(item)) continue;
      exact(item, TOOL);
      scan.ref(item);
      Object kind = field(item, "type");
      exact(kind, TOOL + "$ChiselType");
      scan.ref(kind);
      int maximum = (Integer) field(kind, "maxDamage");
      if (maximum < 0) throw unsupported("negative tool durability");
      ItemStack stack = scan.stack(new ItemStack(item, 1, 0));
      snapshot.tools.add(new ChiselRecipes.Tool(stack, snapshot.damage ? maximum : 0));
      tools.add(
          object(
              "item",
              ChiselRecipes.raw(stack),
              "type",
              ((Enum<?>) kind).name(),
              "maxDamage",
              maximum));
    }
    snapshot.tools.sort(Comparator.comparing(tool -> ChiselRecipes.key(tool.stack)));
    Map<Item, Set<Integer>> exactMetadata = new IdentityHashMap<>();
    for (ItemStack source : scan.sources.values())
      exactMetadata
          .computeIfAbsent(source.getItem(), unused -> new HashSet<>())
          .add(source.getItemDamage());
    for (ItemStack source : scan.sources.values()) {
      Jobs.checkpoint();
      if (source.getItem() instanceof ItemTool || api.isInstance(source.getItem())) continue;
      List<ItemStack> actual = targets(source, scan, stackIds, idNames, oreGroups, ownedOres);
      if (source.getItemDamage() != 32767) {
        snapshot.sources.add(new ChiselRecipes.Decision(source, false, actual));
        continue;
      }
      // 32767 is literal in native GroupList. Only the ore lookup supplies a default
      // metadata partition; its own exact-32767 entry can override that partition.
      Set<Integer> metadata = exactMetadata.get(source.getItem());
      int witness = 0;
      while (witness < 32767 && metadata.contains(witness)) witness++;
      if (witness == 32767) {
        snapshot.sources.add(new ChiselRecipes.Decision(source, false, actual));
        continue;
      }
      ItemStack probe = new ItemStack(source.getItem(), 1, witness);
      List<ItemStack> defaults = targets(probe, scan, stackIds, idNames, oreGroups, ownedOres);
      if (sameTargets(actual, defaults)) {
        snapshot.sources.add(new ChiselRecipes.Decision(source, true, defaults));
      } else {
        snapshot.sources.add(new ChiselRecipes.Decision(source, false, actual));
        if (!defaults.isEmpty())
          snapshot.sources.add(new ChiselRecipes.Decision(source, true, defaults));
      }
    }
    if (snapshot.sources.size() > 65536) throw budget();
    if (!snapshot.sources.isEmpty() && snapshot.tools.isEmpty()) throw notReady();
    JsonArray groups = array();
    for (Group group : scan.orderedGroups) groups.add(group.raw);
    snapshot.groups = groups;
    return new Observation(
        snapshot,
        scan.refs,
        CanonicalJson.digest(
            object(
                "named",
                named,
                "lookup",
                fallback,
                "ores",
                ores,
                "oreLookup",
                oreLookup,
                "items",
                scan.itemFacts,
                "tools",
                tools,
                "snapshot",
                snapshot.json())));
  }

  private List<ItemStack> targets(
      ItemStack source,
      Scan scan,
      Map<?, ?> stackIds,
      List<?> idNames,
      Map<String, Group> oreGroups,
      Map<String, List<ItemStack>> ownedOres) {
    Group group = oreGroup(source, stackIds, idNames, oreGroups);
    if (group == null) {
      Block block = Block.getBlockFromItem(source.getItem());
      scan.block(block);
      group =
          oreGroup(new ItemStack(block, 1, source.getItemDamage()), stackIds, idNames, oreGroups);
      // All map keys/variations/hash owners were audited before this native lookup. Keeping
      // the actual HashMap is necessary: tree bins can resolve ambiguous OR keys differently.
      if (group == null) {
        Object selected =
            invoke(
                anchors[1].getClass(),
                anchors[1],
                "getGroup",
                new Class<?>[] {Block.class, int.class},
                block,
                source.getItemDamage());
        group = selected == null ? null : scan.groups.get(selected);
        if (selected != null && group == null)
          throw unsupported("lookup result outside audited census");
      }
    }
    if (group == null) return Collections.emptyList();
    List<ItemStack> cached=scan.targetSets.get(group);
    if(cached!=null)return cached;
    List<ItemStack> targets = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    if (group != null) {
      for (Variation variation : group.variations) {
        String key = Block.getIdFromBlock(variation.block) + "|" + variation.itemMeta;
        if (seen.add(key))
          targets.add(scan.stack(new ItemStack(variation.block, 1, variation.itemMeta)));
      }
      List<ItemStack> members = ownedOres.get(group.ore);
      if (members != null)
        for (ItemStack target : members)
          if (seen.add(Item.getIdFromItem(target.getItem()) + "|" + target.getItemDamage()))
            targets.add(target);
    }
    for (ItemStack target : targets) {
      scan.add(128);
      scan.projection.nbt(target.getTagCompound());
      if (Block.blockRegistry.getNameForObject(Block.getBlockFromItem(target.getItem())) == null)
        throw unsupported("native target without registered block stalls selection");
    }
    List<ItemStack> result=Collections.unmodifiableList(targets);
    scan.targetSets.put(group,result);
    return result;
  }

  private static boolean sameTargets(List<ItemStack> left, List<ItemStack> right) {
    if (left.size() != right.size()) return false;
    for (int index = 0; index < left.size(); index++)
      if (!ChiselRecipes.key(left.get(index)).equals(ChiselRecipes.key(right.get(index))))
        return false;
    return true;
  }

  private static Group oreGroup(
      ItemStack stack, Map<?, ?> stackIds, List<?> names, Map<String, Group> groups) {
    if (stack.getItem() == null) return null;
    int id = Item.getIdFromItem(stack.getItem());
    Object wild = stackIds.get(id), exact = stackIds.get(id | ((stack.getItemDamage() + 1) << 16));
    Set<Integer> ids = NativeOreStorage.orderedIds(stackIds,wild,exact);
    for (int ore : ids) {
      Group result = groups.get(names.get(ore));
      if (result != null) return result;
    }
    return null;
  }

  private static final class Scan {
    final boolean vanillaLeaves;
    final List<Object> refs = new ArrayList<>();
    final IdentityHashMap<Object, Group> groups = new IdentityHashMap<>();
    final List<Group> orderedGroups = new ArrayList<>();
    final IdentityHashMap<Object, Variation> variations = new IdentityHashMap<>();
    final IdentityHashMap<Group,List<ItemStack>> targetSets = new IdentityHashMap<>();
    final Map<String, ItemStack> sources = new TreeMap<>();
    final Set<Item> items = Collections.newSetFromMap(new IdentityHashMap<>());
    final Set<Block> blocks = Collections.newSetFromMap(new IdentityHashMap<>());
    final JsonArray itemFacts = array();
    final Budget projection = new Budget();

    Scan(boolean vanillaLeaves) { this.vanillaLeaves = vanillaLeaves; }

    void add(long bytes) {
      projection.add(bytes);
    }

    void ref(Object value) {
      if (refs.size() >= 262144) throw budget();
      refs.add(value);
    }

    String text(Object value, boolean nullable) {
      if (nullable && value == null) return null;
      if (!(value instanceof String) || ((String) value).length() > 4096) throw unsupported("text");
      add(64 + 6L * ((String) value).length());
      return (String) value;
    }

    Group group(Object value) {
      if (value == null) throw unsupported("null group");
      Group known = groups.get(value);
      if (known != null) return known;
      owner(value, "Group");
      ref(value);
      String name = text(field(value, "name"), false),
          ore = text(field(value, "oreName"), true),
          sound = text(field(value, "sound"), true);
      Object list = storage(field(value, "variations"), ArrayList.class);
      ref(list);
      Group group = new Group(orderedGroups.size(), name, ore);
      groups.put(value, group);
      orderedGroups.add(group);
      JsonArray members = array();
      for (Object member : (List<?>) list) {
        Variation variation = variation(member);
        // The native leaf feature filters its copied list, retaining the stored census.
        if (!value.getClass().getName().equals(LEAVES) || vanillaLeaves
            || variation.block != net.minecraft.init.Blocks.leaves && variation.block != net.minecraft.init.Blocks.leaves2)
          group.variations.add(variation);
        members.add(variation.raw);
      }
      group.raw =
          object(
              "owner",
              value.getClass().getName(),
              "name",
              name,
              "ore",
              ore,
              "sound",
              sound,
              "variations",
              members);
      return group;
    }

    Variation variation(Object value) {
      if (value == null) throw unsupported("null variation");
      Variation known = variations.get(value);
      if (known != null) return known;
      owner(value, "Variation");
      ref(value);
      Object raw = field(value, "block");
      if (!(raw instanceof Block)) throw unsupported("variation block");
      Block block = (Block) raw;
      block(block);
      int meta = (Integer) field(value, "meta");
      boolean simple = value.getClass().getName().equals(SIMPLE + "Variation");
      int damage = simple ? (Integer) field(value, "damage") : meta;
      int order = simple ? (Integer) field(value, "order") : 99;
      if (meta < 0 || meta > 32767 || damage < 0 || damage > 32767)
        throw unsupported("signed/out-of-range variation metadata");
      add(256);
      Variation result =
          new Variation(
              block,
              meta,
              damage,
              object(
                  "owner",
                  value.getClass().getName(),
                  "block",
                  Block.blockRegistry.getNameForObject(block),
                  "blockId",
                  Block.getIdFromBlock(block),
                  "meta",
                  meta,
                  "itemMeta",
                  damage,
                  "order",
                  order));
      variations.put(value, result);
      return result;
    }

    void block(Block block) {
      if (block == null || !blocks.add(block)) return;
      hashOwner(block);
      ref(block);
      if (Block.blockRegistry.getNameForObject(block) == null)
        throw unsupported("unregistered block");
      Item item = Item.getItemFromBlock(block);
      if (item == null && block != net.minecraft.init.Blocks.air)
        throw unsupported("block has no native item");
      if (item != null) item(item);
    }

    void hashOwner(Object value) {
      try {
        for (String method : new String[] {"hashCode", "equals"}) {
          Method owner =
              value
                  .getClass()
                  .getMethod(
                      method,
                      method.equals("equals") ? new Class<?>[] {Object.class} : new Class<?>[0]);
          if (owner.getDeclaringClass() != Object.class)
            throw unsupported("hash/equality callback: " + value.getClass().getName());
        }
      } catch (NoSuchMethodException impossible) {
        throw new AssertionError(impossible);
      }
    }

    void item(Item item) {
      if (!items.add(item)) return;
      hashOwner(item);
      ref(item);
      Object delegate = field(item, "delegate");
      exact(delegate, "cpw.mods.fml.common.registry.RegistryDelegate$Delegate");
      ref(delegate);
      String name = Item.itemRegistry.getNameForObject(item);
      if (name == null
          || Item.itemRegistry.getObject(name) != item
          || Item.getIdFromItem(item) < 0
          || Item.getIdFromItem(item) > 32767) throw unsupported("item identity");
      // Forge getOreIDs resolves the delegate name through GameData, not a raw Object hash.
      if (!name.equals(field(delegate, "name"))
          || field(delegate, "referant") != item
          || field(delegate, "type") != Item.class)
        throw unsupported("item delegate name/target/type");
      int id = Item.getIdFromItem(item);
      if (cpw.mods.fml.common.registry.GameData.getItemRegistry().getId(name) != id)
        throw unsupported("Forge/item numeric identity mismatch");
      itemFacts.add(object("name", name, "nativeId", id));
    }

    ItemStack stack(ItemStack value) {
      NativeStackAudit.audit(value);
      if (value.stackSize <= 0 || value.getItemDamage() < 0 || value.getItemDamage() > 32767)
        throw unsupported("stack amount/metadata");
      item(value.getItem());
      add(192);
      projection.nbt(value.getTagCompound());
      return value.copy();
    }

    ItemStack borrowedStack(ItemStack value) {
      ItemStack owned = stack(value);
      ref(value);
      nbtRefs(value.getTagCompound());
      return owned;
    }

    void hashLayout(Map<?, ?> map) {
      Object[] table = (Object[]) field(HashMap.class, map, "table");
      ref(table);
      if (table == null) return;
      if (table.length > 1048576) throw budget();
      for (Object first : table) {
        ref(first);
        for (Object node = first; node != null; node = field(node, "next")) {
          ref(node);
          if (node.getClass().getName().equals("java.util.HashMap$TreeNode"))
            for (String link : new String[] {"parent", "left", "right", "prev"})
              ref(field(node, link));
          else exact(node, "java.util.HashMap$Node");
        }
      }
    }

    void nbtRefs(net.minecraft.nbt.NBTBase value) {
      if (value == null) return;
      ref(value);
      if (value.getClass() == net.minecraft.nbt.NBTTagCompound.class) {
        Object map = field(value, "tagMap");
        ref(map);
        for (Object child : ((Map<?, ?>) map).values()) nbtRefs((net.minecraft.nbt.NBTBase) child);
      } else if (value.getClass() == net.minecraft.nbt.NBTTagList.class) {
        Object list = field(value, "tagList");
        ref(list);
        for (Object child : (List<?>) list) nbtRefs((net.minecraft.nbt.NBTBase) child);
      } else if (value.getClass() == net.minecraft.nbt.NBTTagByteArray.class)
        ref(((net.minecraft.nbt.NBTTagByteArray) value).func_150292_c());
      else if (value.getClass() == net.minecraft.nbt.NBTTagIntArray.class)
        ref(((net.minecraft.nbt.NBTTagIntArray) value).func_150302_c());
    }

    void source(ItemStack value) {
      ItemStack owned = stack(value);
      owned.stackSize = 1;
      owned.setTagCompound(null);
      sources.putIfAbsent(ChiselRecipes.key(owned), owned);
      if (sources.size() > 65536) throw budget();
    }
  }

  private static void owner(Object value, String suffix) {
    String name = value.getClass().getName();
    if (name.equals(SIMPLE + suffix)) return;
    if (suffix.equals("Group") && name.equals(LEAVES)) {
      MagicApi.version("ChiselSource","chisel");
      return;
    }
    if (name.equals(TWEAKER + suffix)) {
      MagicApi.version("ChiselSource","modtweaker2");
      return;
    }
    throw unsupported("callback owner: " + name);
  }

  private static Object storage(Object value, Class<?> owner) {
    if (value == null) throw notReady();
    if (value.getClass() != owner) throw unsupported("storage: " + value.getClass().getName());
    if ((value instanceof Map ? ((Map<?, ?>) value).size() : ((List<?>) value).size()) > 262144)
      throw budget();
    return value;
  }

  private static void exact(Object value, String name) {
    if (value == null || !value.getClass().getName().equals(name))
      throw unsupported("owner: " + name);
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Chisel source closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    anchors = null;
    counts = null;
    signature = null;
    if (identities != null) identities.clear();
    identities = null;
    snapshot = null;
  }

  private static Jobs.Fault unsupported(String detail) {
    return new Jobs.Fault("recipe_unsupported", "Unadapted Chisel source " + detail);
  }

  private static Jobs.Fault notReady() {
    return new Jobs.Fault("handler_not_ready", "Chisel native source unavailable");
  }

  private static Jobs.Fault budget() {
    return new Jobs.Fault("source_budget", "Chisel source exceeds cumulative budget");
  }

  private static Jobs.Fault changed(Throwable cause) {
    Jobs.Fault result =
        new Jobs.Fault("recipe_changed", "Chisel native source/member/config changed");
    if (cause != null) result.initCause(cause);
    return result;
  }

  private static final class Group {
    final int index;
    final String name, ore;
    final List<Variation> variations = new ArrayList<>();
    JsonObject raw;

    Group(int index, String name, String ore) {
      this.index = index;
      this.name = name;
      this.ore = ore;
    }
  }

  private static final class Variation {
    final Block block;
    final int blockMeta, itemMeta;
    final JsonObject raw;

    Variation(Block block, int blockMeta, int itemMeta, JsonObject raw) {
      this.block = block;
      this.blockMeta = blockMeta;
      this.itemMeta = itemMeta;
      this.raw = raw;
    }
  }

  private static final class Observation {
    final ChiselRecipes.Snapshot snapshot;
    final List<Object> identities;
    final String signature;

    Observation(ChiselRecipes.Snapshot snapshot, List<Object> identities, String signature) {
      this.snapshot = snapshot;
      this.identities = identities;
      this.signature = signature;
    }
  }
  /** Bound raw NBT before typed JSON projection and bound cumulative graph expansion. */
  private static final class Budget {
    long bytes;
    int nodes;

    void add(long value) {
      Jobs.checkpoint();
      if ((bytes += value) > 32L * 1024 * 1024 || ++nodes > 262144) throw budget();
    }

    void nbt(net.minecraft.nbt.NBTBase value) {
      if (value == null) return;
      add(64);
      if (value.getClass() == net.minecraft.nbt.NBTTagCompound.class)
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) field(value, "tagMap")).entrySet()) {
          add(6L * ((String) entry.getKey()).length());
          nbt((net.minecraft.nbt.NBTBase) entry.getValue());
        }
      else if (value.getClass() == net.minecraft.nbt.NBTTagList.class)
        for (Object child : (List<?>) field(value, "tagList"))
          nbt((net.minecraft.nbt.NBTBase) child);
      else if (value.getClass() == net.minecraft.nbt.NBTTagString.class)
        add(6L * ((net.minecraft.nbt.NBTTagString) value).func_150285_a_().length());
      else if (value.getClass() == net.minecraft.nbt.NBTTagByteArray.class)
        add(8L * ((net.minecraft.nbt.NBTTagByteArray) value).func_150292_c().length);
      else if (value.getClass() == net.minecraft.nbt.NBTTagIntArray.class)
        add(16L * ((net.minecraft.nbt.NBTTagIntArray) value).func_150302_c().length);
    }
  }
}
