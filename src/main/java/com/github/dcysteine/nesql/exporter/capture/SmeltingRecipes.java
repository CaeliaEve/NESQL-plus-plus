package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import codechicken.nei.recipe.FurnaceRecipeHandler;
import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.oredict.OreDictionary;

/** Actual machine selection, without NEI display caches or writes to native runtime caches. */
final class SmeltingRecipes implements RegistryRecipes {
  private static final String PREFIX = "ganymedes01.etfuturum.";
  private static final String CONFIG = PREFIX + "configuration.configs.ConfigFunctions";
  private final String nativeClass;
  private final boolean smoker;
  private Object registry;
  private NativeSmeltingTable custom, cached, blacklist, experience, experienceCache, furnace, furnaceExperience;
  private NativeDefinitionGraph definitions;
  private final NativeItemExamples examples = new NativeItemExamples();
  private final List<Entry> entries = new ArrayList<>();
  private final Map<String, FoodWitness> foodDependencies = new LinkedHashMap<>();
  private static final class FoodWitness {
    final ItemStack stack; final int healing;
    FoodWitness(ItemStack stack,int healing){this.stack=stack.copy();this.healing=healing;}
  }
  private final Map<Item, SortedSet<Integer>> partitions = new LinkedHashMap<>();
  private final Set<Item> wildcards = Collections.newSetFromMap(new IdentityHashMap<Item, Boolean>());
  private Object oreRoot, oreNames;
  private String oreSignature;
  private List<Object> oreIdentities;
  private boolean closed;

  static boolean supportsClass(String name) {
    return name.equals(PREFIX + "compat.nei.SmokerRecipeHandler")
        || name.equals(PREFIX + "compat.nei.BlastFurnaceRecipeHandler");
  }
  static boolean supports(ICraftingHandler handler) { return handler != null && supportsClass(handler.getClass().getName()); }
  static Object[] registrationAnchors(String handler) {
    if (!supportsClass(handler)) throw fault("Unknown Et Futurum smelting handler");
    String kind = handler.endsWith(".SmokerRecipeHandler") ? "SmokerRecipes" : "BlastFurnaceRecipes";
    Class<?> owner = type(PREFIX + "recipes." + kind);
    Object manager = field(owner, null, "smeltingBase");
    if (manager == null || manager.getClass() != owner) throw fault("Et Futurum registry is unavailable");
    return new Object[] {manager, field(manager, "smeltingList"), field(manager, "experienceList"),
        field(manager, "smeltingBlacklist"), field(manager, "smeltingListCache"), field(manager, "experienceListCache"),
        NativeSmeltingTable.furnaceManager(), NativeSmeltingTable.furnaceItems()};
  }

  SmeltingRecipes(FurnaceRecipeHandler handler) {
    if (!supports(handler)) throw fault("Unknown Et Futurum smelting handler");
    MagicApi.version("SmeltingRecipes","etfuturum");
    smoker = handler.getClass().getName().endsWith(".SmokerRecipeHandler");
    nativeClass = PREFIX + "recipes." + (smoker ? "SmokerRecipes" : "BlastFurnaceRecipes");
    try {
      registry = field(type(nativeClass), null, "smeltingBase");
      if (registry == null || registry.getClass() != type(nativeClass)) throw fault("Uninitialized Et Futurum registry");
      custom = table("smeltingList"); cached = table("smeltingListCache"); blacklist = table("smeltingBlacklist");
      experience = table("experienceList"); experienceCache = table("experienceListCache");
      furnace = new NativeSmeltingTable(NativeSmeltingTable.furnaceItems());
      furnaceExperience = new NativeSmeltingTable(NativeSmeltingTable.furnaceExperience());
      for (NativeSmeltingTable table : new NativeSmeltingTable[] {furnace, custom, cached, blacklist})
        for (ItemStack key : table.keys()) {
          NativeStackAudit.audit(key); Item item = key.getItem();
          if (ItemCallbacks.method(item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
            throw fault("Et Futurum furnace/ore metadata callback requires an adapter: " + item.getClass().getName());
          SortedSet<Integer> known = partitions.computeIfAbsent(item, ignored -> new TreeSet<>());
          int metadata = Items.feather.getDamage(key);
          if (metadata == 32767) wildcards.add(item); else known.add(metadata);
        }
      bindOreTable(true);
      long partitionWork=0;
      for (Map.Entry<Item, SortedSet<Integer>> partition : partitions.entrySet()) {
        Jobs.checkpoint(); Item item = partition.getKey(); SortedSet<Integer> specific = partition.getValue();
        if((partitionWork+=specific.size()+1L)>262144)throw fault("Et Futurum aggregate metadata work budget exceeded");
        if (wildcards.contains(item)) {
          int probe = 0; while (specific.contains(probe) || probe == 32767) probe++;
          Entry remainder = resolve(new ItemStack(item, 1, probe), true, specific);
          List<Entry> exact = new ArrayList<>();
          SortedSet<Integer> except = new TreeSet<>();
          for(int metadata:specific){
            Entry entry=resolve(new ItemStack(item,1,metadata),false,Collections.emptySortedSet());
            if(!sameSelection(entry,remainder)){except.add(metadata);exact.add(entry);}
          }
          ItemStack specimen = null;
          for (ItemStack candidate : examples.copies(new ItemStack(item, 1, 32767)))
            if (!except.contains(Items.feather.getDamage(candidate))) { specimen = candidate; break; }
          // No visible example is not proof that every other integer metadata is impossible.
          if (specimen == null && remainder.output != null) throw fault("Et Futurum wildcard remainder has no native content specimen: " + Item.itemRegistry.getNameForObject(item));
          compact(exact);
          entries.add(new Entry(specimen == null ? remainder.input : specimen, remainder.output, true, except, remainder.reason, remainder.xp));
        } else {
          List<Entry> exact=new ArrayList<>();
          for(int metadata:specific)exact.add(resolve(new ItemStack(item,1,metadata),false,Collections.emptySortedSet()));
          compact(exact);
        }
        if (entries.size() > 262144) throw fault("Et Futurum partition budget exceeded");
      }
      Map<String, String[]> shapes = NativeDefinitionGraph.shapes(nativeClass, "smeltingList", "experienceList",
          "smeltingBlacklist", "smeltingListCache", "experienceListCache", "reloadingCT");
      NativeSmeltingTable.shapes(shapes);
      shapes.put(FurnaceRecipes.class.getName(), new String[] {"smeltingList", "experienceList"});
      definitions = new NativeDefinitionGraph(this::roots, shapes);
    } catch (RuntimeException | Error error) { close(); throw error; }
  }

  /** Equivalent raw metadata selections share a bounded integer-set predicate, not thousands of item facts. */
  private void compact(List<Entry> exact){
    Map<String,List<Entry>> groups=new LinkedHashMap<>();
    for(Entry entry:exact){
      String key=CanonicalJson.digest(object("reason",entry.reason,"xp",Float.floatToRawIntBits(entry.xp),
          "output",RecipeRow.stackEvidence(entry.output)));
      groups.computeIfAbsent(key,ignored->new ArrayList<>()).add(entry);
    }
    for(List<Entry> group:groups.values()){
      Entry first=group.get(0);
      if(group.size()==1){entries.add(first);continue;}
      SortedSet<Integer> metadata=new TreeSet<>();
      for(Entry entry:group)metadata.add(Items.feather.getDamage(entry.input));
      entries.add(new Entry(first.input,first.output,false,metadata,first.reason,first.xp));
    }
  }

  private NativeSmeltingTable table(String member) { return new NativeSmeltingTable(field(registry, member)); }
  private Object[] roots() {
    return new Object[] {field(type(nativeClass), null, "smeltingBase"), NativeSmeltingTable.furnaceManager(),
        NativeSmeltingTable.furnaceItems(), NativeSmeltingTable.furnaceExperience(),
        field(type(CONFIG), null, smoker ? "enableAutoAddSmoker" : "enableAutoAddBlastFurnace")};
  }

  private Entry resolve(ItemStack input, boolean wildcard, SortedSet<Integer> exceptions) {
    Jobs.checkpoint();
    input.stackSize = 1; String reason = null; Object output = null;
    if (blacklist.get(input) != null) reason = "native_blacklist_rejects_partition";
    else {
      output = cached.get(input);
      if (output == null) output = custom.get(input);
      if (output == null) {
        output = furnace.get(input);
        if (output == null) reason = "no_native_smelting_result";
        else if (!canAdd(input, stack(output))) reason = "native_auto_add_condition_rejects_partition";
      }
    }
    ItemStack product = reason == null ? stack(output).copy() : null;
    return new Entry(input.copy(), product, wildcard, wildcard ? new TreeSet<>(exceptions) : Collections.emptySortedSet(),
        reason, product == null ? 0 : experience(product));
  }

  private static boolean sameSelection(Entry left, Entry right) {
    return Objects.equals(left.reason, right.reason) && Float.floatToRawIntBits(left.xp) == Float.floatToRawIntBits(right.xp)
        && Objects.equals(RecipeRow.stackEvidence(left.output), RecipeRow.stackEvidence(right.output));
  }

  private boolean canAdd(ItemStack input, ItemStack output) {
    if (!Boolean.TRUE.equals(field(type(CONFIG), null, smoker ? "enableAutoAddSmoker" : "enableAutoAddBlastFurnace"))) return false;
    if (smoker) {
      Item item = output.getItem(); if (!(item instanceof ItemFood)) return false;
      int healing = SmeltingItemCallbacks.healing(output);
      foodDependencies.put(CanonicalJson.digest(RecipeRow.stackEvidence(output)), new FoodWitness(output,healing)); return healing > 0;
    }
    for (String name : oreNames(input)) if (name.startsWith("ore") || name.matches("raw[A-Z].*") || name.startsWith("cluster") || name.startsWith("crushed")) return true;
    for (String name : oreNames(output)) if (name.startsWith("ingot") || name.startsWith("chunkGt") || name.startsWith("nugget") || name.equalsIgnoreCase("quicksilver")) return true;
    return false;
  }

  private List<String> oreNames(ItemStack input) {
    if (ItemCallbacks.method(input.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
      throw fault("Et Futurum output ore metadata callback requires an adapter");
    List<String> names = new ArrayList<>();
    for (int id : OreDictionary.getOreIDs(input.copy())) names.add(OreDictionary.getOreName(id));
    return names;
  }

  private float experience(ItemStack output) {
    float override = SmeltingItemCallbacks.experience(output);
    if (override != -1f) return override;
    if (ItemCallbacks.method(output.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
      throw fault("Et Futurum experience metadata callback requires an adapter");
    Object amount = experienceCache.get(output);
    if (amount == null) amount = experience.get(output);
    if (amount == null) amount = furnaceExperience.get(output);
    if (amount == null) return 0;
    if (!(amount instanceof Float)) throw fault("Invalid native smelting experience type");
    return (Float) amount; // Preserve the actual signed float; do not clamp it.
  }

  /** Forge's baked lookup affects canAdd, including metadata absent from recipe-table keys. */
  private void bindOreTable(boolean opening) {
    if (smoker) return;
    Object lookup = field(OreDictionary.class, null, "stackToId"), names = field(OreDictionary.class, null, "idToName");
    if (!NativeOreStorage.reverse(lookup) || names == null || names.getClass() != ArrayList.class)
      throw fault("Unadapted baked Forge ore storage");
    if (((Map<?, ?>) lookup).size() > 262144 || ((List<?>) names).size() > 262144) throw fault("Forge ore budget exceeded");
    List<Object> identities = new ArrayList<>(); JsonArray values = array();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) lookup).entrySet()) {
      Jobs.checkpoint(); if (!(entry.getKey() instanceof Integer)) throw fault("Invalid baked ore key");
      int key = (Integer) entry.getKey(); Item item = Item.getItemById(key & 65535);
      if (item == null || !partitions.containsKey(item)) continue;
      Object ids = entry.getValue(); if (!NativeOreStorage.indices(lookup, ids)) throw fault("Invalid baked ore IDs");
      identities.add(ids); JsonArray ores = array();
      for (Object id : (List<?>) ids) {
        if (!(id instanceof Integer) || (Integer) id < 0 || (Integer) id >= ((List<?>) names).size()
            || !(((List<?>) names).get((Integer) id) instanceof String)) throw fault("Invalid baked ore name");
        ores.add(object("id", id, "name", ((List<?>) names).get((Integer) id)));
      }
      values.add(object("key", key, "ores", ores));
      int code = key >>> 16;
      if (opening && code != 0 && code - 1 != 32767) partitions.get(item).add(code - 1);
    }
    JsonArray allNames = array();
    for (Object name : (List<?>) names) { if (!(name instanceof String)) throw fault("Invalid ore name table"); allNames.add(value((String) name)); }
    String signature = CanonicalJson.digest(object("inputs", values, "names", allNames));
    if (opening) { oreRoot = lookup; oreNames = names; oreSignature = signature; oreIdentities = identities; }
    else {
      if (lookup != oreRoot || names != oreNames || !signature.equals(oreSignature) || identities.size() != oreIdentities.size()) throw changed();
      for (int i = 0; i < identities.size(); i++) if (identities.get(i) != oreIdentities.get(i)) throw changed();
    }
  }

  public int size() { verify(); return entries.size(); }
  public void verify() {
    alive(); definitions.verifyRoots();
    if (!smoker && (field(OreDictionary.class, null, "stackToId") != oreRoot || field(OreDictionary.class, null, "idToName") != oreNames)) throw changed();
  }
  public void finishVerification() {
    verify(); definitions.verify(); examples.verify(); bindOreTable(false);
    for (FoodWitness witness : foodDependencies.values())
      if (SmeltingItemCallbacks.healing(witness.stack) != witness.healing) throw changed();
    // Resolve again at sealing, never on each emitted row or batch.
    for (Entry entry : entries) {
      Jobs.checkpoint(); Object selected = blacklist.get(entry.input) == null ? cached.get(entry.input) : null;
      if (selected == null && blacklist.get(entry.input) == null) selected = custom.get(entry.input);
      if (selected == null && blacklist.get(entry.input) == null) {
        selected = furnace.get(entry.input); if (selected != null && !canAdd(entry.input, stack(selected))) selected = null;
      }
      if (entry.output != null && (selected == null || !RecipeRow.stackEvidence(entry.output).equals(RecipeRow.stackEvidence(stack(selected)))
          || Float.floatToRawIntBits(entry.xp) != Float.floatToRawIntBits(experience(entry.output)))) throw changed();
      if (entry.output == null && selected != null) throw changed();
    }
  }

  public boolean capture(int index, RecipeRow row) {
    verify(); Entry entry = entries.get(index);
    if (entry.reason != null) {
      JsonArray except = array(); for (int metadata : entry.exceptions) except.add(value(metadata));
      return row.exclude(object("native", nativeClass, "reason", entry.reason,
          "input", RecipeRow.stackEvidence(entry.input), "wildcardRemainder", entry.wildcard,
          "metadataValues", except, "metadataRole",entry.wildcard?"excluded":"included"));
    }
    JsonObject match = object("kind", "wildcard", "meta", entry.wildcard, "nbt", true);
    if (!entry.exceptions.isEmpty()) {
      JsonArray values=array();for(int metadata:entry.exceptions)values.add(value(metadata));
      match=object("kind","metadataSet","values",values,"exclude",entry.wildcard,"nbt",true);
    }
    row.itemInput(0, Collections.singletonList(new RecipeRow.Ingredient(entry.input.copy(), 1, false, match)), false);
    row.itemOutput(0, entry.output.copy(), 10000);
    row.record.addProperty("duration", "100");
    row.property("minecraft:experience", "zh_CN".equals(row.facts.locale()) ? "每件产物的经验" : "Experience per output item", Float.toString(entry.xp));
    row.note("etfuturum:selection", nativeClass + ".getSmeltingResult",
        "zh_CN".equals(row.facts.locale()) ? "黑名单先于运行时缓存、自定义配方和原版熔炉回退；此行保留当前会话中的有效匹配分区。"
            : "Blacklist precedes the runtime result cache, custom definitions and vanilla fallback; this row preserves an effective match partition in this session.");
    return true;
  }

  private static ItemStack stack(Object value) {
    if (!(value instanceof ItemStack)) throw fault("Invalid native smelting result");
    NativeStackAudit.audit((ItemStack) value);
    if (((ItemStack) value).stackSize <= 0) throw fault("Non-positive native smelting result");
    return (ItemStack) value;
  }
  private void alive() { Jobs.checkpoint(); if (closed) throw new Jobs.Fault("capture_closed", "Et Futurum smelting reader is closed"); }
  public void close() {
    closed = true; if (definitions != null) definitions.close(); examples.close(); entries.clear(); foodDependencies.clear(); partitions.clear(); wildcards.clear();
    registry = null; custom = cached = blacklist = experience = experienceCache = furnace = furnaceExperience = null;
    oreRoot = oreNames = null; oreSignature = null; if (oreIdentities != null) oreIdentities.clear();
  }
  private static Jobs.Fault fault(String text) { return new Jobs.Fault("recipe_unsupported", text); }
  private static Jobs.Fault changed() { return new Jobs.Fault("environment_changed", "Et Futurum smelting sources, conditions or effective selection changed"); }
  private static final class Entry {
    final ItemStack input, output; final boolean wildcard; final SortedSet<Integer> exceptions; final String reason; final float xp;
    Entry(ItemStack input, ItemStack output, boolean wildcard, SortedSet<Integer> exceptions, String reason, float xp) {
      this.input = input; this.output = output; this.wildcard = wildcard; this.exceptions = exceptions; this.reason = reason; this.xp = xp;
    }
  }
}
