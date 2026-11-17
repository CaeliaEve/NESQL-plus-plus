package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import blockrenderer6343.client.utils.ConstructableData;
import codechicken.nei.ItemList;
import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.structure.IChannelDefinition;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Native component lookup and construction information, never a consuming recipe. */
final class StructureReferences implements NativeReferences {
  static final String GT = "blockrenderer6343.integration.gregtech.GTNEIMultiblockHandler";
  static final String COMPAT =
      "blockrenderer6343.integration.structurelib.StructureCompatNEIHandler";
  private static final Class<?> BOUNDED =
      IChannelDefinition.bounded("nesql-definition-identity", 1, 1).getClass();
  private static final Class<?> UNBOUNDED =
      IChannelDefinition.unbounded("nesql-definition-identity").getClass();
  private final JsonObject source;
  private final String family, domain, signature;
  private final Class<?> handler;
  private final Map<?, ?> nativeComponents;
  private final List<ItemStack> panel;
  private final List<Binding> bindings;
  private final JsonArray records;
  private final Map<String, ItemStack> specimens = new TreeMap<>();
  private final Map<ConstructableData, Map<String, IChannelDefinition>> definitionObjects =
      new IdentityHashMap<>();

  static boolean supportsClass(String name) {
    return GT.equals(name) || COMPAT.equals(name);
  }

  static boolean supports(ICraftingHandler handler) {
    return handler != null && supportsClass(handler.getClass().getName());
  }

  StructureReferences(ICraftingHandler value, JsonObject source) {
    if (!supports(value)) throw fault("Unknown native structure handler");
    Structures.version("structurelib");
    Structures.version("blockrenderer6343");
    this.source = copy(source);
    handler = value.getClass();
    family = GT.equals(handler.getName()) ? "gt" : "compat";
    Object components = MagicApi.field(handler, null, "multiBlockComponents");
    if (!(components instanceof Map) || !ItemList.loadFinished)
      throw new Jobs.Fault(
          "handler_not_ready", "Native structure scan or item panel has not completed");
    nativeComponents = (Map<?, ?>) components;
    panel = ItemList.items;
    bindings = currentBindings();
    for (Binding binding : bindings)
      definitionObjects.put(
          binding.tiers, new TreeMap<>(binding.tiers.getChannelDefinitions()));
    records = snapshot(this.source, family, bindings, nativeComponents, panel, specimens);
    domain = records.get(0).getAsJsonObject().get("domain").getAsString();
    signature = CanonicalJson.digest(records);
  }

  private List<Binding> currentBindings() {
    List<Binding> result = new ArrayList<>();
    if (family.equals("gt")) {
      Object nativeList = MagicApi.field(handler, null, "multiblocksList");
      if (!(nativeList instanceof List)) throw fault("Missing native GT controller list");
      Map<Object, Structures.Machine> registered = new IdentityHashMap<>();
      for (Structures.Machine machine : Structures.all()) registered.put(machine.machine, machine);
      for (Object constructable : (List<?>) nativeList) {
        Structures.Machine machine = registered.remove(constructable);
        if (machine == null || !(constructable instanceof IConstructable))
          throw changed("GT controller scan differs from live registry");
        result.add(
            new Binding(
                machine,
                constructable,
                ConstructableData.getTierData((IConstructable) constructable)));
      }
      if (!registered.isEmpty()) throw changed("GT controller registered after native scan");
    } else {
      Object raw = MagicApi.field(handler, null, "stacks");
      if (!(raw instanceof Map))
        throw new Jobs.Fault(
            "handler_not_ready", "Native compatibility controller scan has not completed");
      Map<?, ?> stacks = (Map<?, ?>) raw;
      List<Structures.Machine> machines = Structures.compatAll();
      int index = 0;
      for (Object constructable : stacks.keySet()) {
        if (!(constructable instanceof IConstructable) || index >= machines.size())
          throw changed("Native compatibility wrapper changed");
        result.add(
            new Binding(
                machines.get(index++),
                constructable,
                ConstructableData.getTierData((IConstructable) constructable)));
      }
      if (index != machines.size())
        throw changed("Native compatibility scan changed while opening");
    }
    return result;
  }

  static final class Binding {
    final Structures.Machine machine;
    final Object nativeConstructable;
    final ConstructableData tiers;

    Binding(Structures.Machine machine, Object constructable, ConstructableData tiers) {
      if (machine == null || !(constructable instanceof IConstructable)
          || tiers == null || tiers.getClass() != ConstructableData.class)
        throw fault("Invalid native structure binding");
      this.machine = machine;
      nativeConstructable = constructable;
      this.tiers = tiers;
    }
  }

  public int size() {
    return records.size();
  }

  public String domain() {
    return domain;
  }

  public JsonObject counts() {
    return copy(records.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("counts"));
  }

  public void verify() {
    if (!ItemList.loadFinished
        || ItemList.items != panel
        || MagicApi.field(handler, null, "multiBlockComponents") != nativeComponents)
      throw changed("Native structure scan or item panel was replaced");
    for (Binding binding : bindings) binding.machine.verify();
  }

  public void finishVerification() {
    verify();
    List<Binding> current = currentBindings();
    if (current.size() != bindings.size())
      throw changed("Native structure binding count changed");
    for (int index = 0; index < bindings.size(); index++) {
      Binding binding = bindings.get(index), next = current.get(index);
      if (next.nativeConstructable != binding.nativeConstructable || next.tiers != binding.tiers)
        throw changed("Native structure binding or tier object was replaced");
      Map<String, IChannelDefinition> prior = definitionObjects.get(binding.tiers);
      Map<String, IChannelDefinition> definitions = binding.tiers.getChannelDefinitions();
      if (prior.size() != definitions.size())
        throw changed("Native construction channel definitions changed");
      for (Map.Entry<String, IChannelDefinition> entry : prior.entrySet())
        if (definitions.get(entry.getKey()) != entry.getValue())
          throw changed("Native construction channel definition was replaced");
    }
    if (!signature.equals(
        CanonicalJson.digest(
            snapshot(source, family, current, nativeComponents, panel, new TreeMap<>()))))
      throw changed("Native structure membership, component matching or tier information changed");
  }

  public List<Structures.Machine> structures(int start, int end) {
    List<Structures.Machine> selected = new ArrayList<>();
    for (int index = Math.max(1, start); index < Math.min(1 + bindings.size(), end); index++)
      selected.add(bindings.get(index - 1).machine);
    return selected;
  }

  public void capture(int index, Facts facts) {
    if (index < 0 || index >= size()) throw fault("Native structure record index differs");
    JsonObject row = copy(records.get(index).getAsJsonObject()), data = row.getAsJsonObject("data");
    if (data.has("controller")) captureItem(data.get("controller").getAsString(), facts);
    if (data.has("items"))
      for (JsonElement item : data.getAsJsonArray("items")) captureItem(item.getAsString(), facts);
    if (data.has("tierItems"))
      for (JsonElement raw : data.getAsJsonArray("tierItems"))
        for (JsonElement item : raw.getAsJsonObject().getAsJsonArray("items"))
          captureItem(item.getAsString(), facts);
    facts.row("references", row);
  }

  private void captureItem(String id, Facts facts) {
    ItemStack specimen = specimens.get(id);
    if (specimen == null || !id.equals(facts.item(specimen.copy())))
      throw changed("Native structure specimen identity differs");
  }

  static JsonArray snapshot(
      JsonObject source,
      String family,
      List<Binding> bindings,
      Map<?, ?> components,
      List<ItemStack> panel,
      Map<String, ItemStack> specimens) {
    if (!(family.equals("gt") || family.equals("compat"))
        || bindings.size() > 8192
        || components.size() > 65536
        || panel.size() > 1048576) throw fault("Native structure catalogue exceeds its budget");
    Map<Object, String> owners = new IdentityHashMap<>();
    Set<String> identities = new HashSet<>();
    TreeSet<Long> keys = new TreeSet<>();
    Map<Long, JsonArray> matches = new TreeMap<>();
    for (Object raw : components.keySet()) keys.add(hashKey(raw));
    for (Binding binding : bindings) {
      binding.machine.verify();
      String id = binding.machine.identity();
      if (!identities.add(id) || owners.put(binding.nativeConstructable, id) != null)
        throw fault("Duplicate native controller binding");
      if (binding.tiers.getClass() != ConstructableData.class)
        throw fault("Unadapted native tier information");
      for (Object raw : binding.tiers.getItemTiers().keySet())
        keys.add(hashKey(raw));
      for (Object raw : binding.tiers.getItemChannels().keySet())
        keys.add(hashKey(raw));
    }
    Map<Long, TreeSet<String>> itemSets = new TreeMap<>();
    for (ItemStack item : panel) {
      Jobs.checkpoint();
      if (item == null || item.getItem() == null) throw fault("Invalid native panel item");
      // Native lookup hashes Object.hashCode + damage, ignoring NBT and count.
      // Do not initialize BRUtil's GUI fake-player merely to evaluate this expression.
      long hash = item.getItem().hashCode() * 31L + item.getItemDamage() * 31L;
      if (keys.contains(hash))
        itemSets.computeIfAbsent(hash, unused -> new TreeSet<>()).add(item(item, specimens));
    }
    for (long key : keys) {
      JsonArray items = array();
      for (String id : itemSets.getOrDefault(key, new TreeSet<>())) items.add(value(id));
      matches.put(key, items);
    }
    JsonArray data = array();
    JsonObject counts =
        object(
            "controllers",
            bindings.size(),
            "lookups",
            components.size(),
            "unresolvedLookups",
            0,
            "tierLookups",
            0,
            "panelItems",
            panel.size());
    data.add(
        object(
            "kind",
            "structureCatalogDefinition",
            "source",
            source,
            "family",
            family,
            "ready",
            true,
            "consuming",
            false,
            "matching",
            "nativeItemHashDamageSum",
            "projection",
            "completeNativeMapAndItemPanel",
            "counts",
            counts));
    int tierCount = 0;
    for (int index = 0; index < bindings.size(); index++) {
      Binding binding = bindings.get(index);
      ItemStack controller = binding.machine.controller();
      JsonObject channels = object();
      TreeMap<String, Integer> channelMap = new TreeMap<>();
      for (Map.Entry<?, ?> entry : binding.tiers.getChannelMaxTierMap().entrySet())
        channelMap.put(channelName(entry.getKey()), integer(entry.getValue()));
      if (channelMap.size() > 256 || binding.tiers.getMaxTotalTier() < 1)
        throw fault("Invalid native structure maximum tier");
      for (Map.Entry<String, Integer> channel : channelMap.entrySet())
        channels.addProperty(channel.getKey(), channel.getValue());
      JsonObject definitions = object();
      Map<?, ?> nativeDefinitions = binding.tiers.getChannelDefinitions();
      if (nativeDefinitions.size() > 256)
        throw fault("Native construction channel definition budget exceeded");
      TreeMap<String, IChannelDefinition> orderedDefinitions = new TreeMap<>();
      for (Map.Entry<?, ?> entry : nativeDefinitions.entrySet()) {
        String name = channelName(entry.getKey());
        if (!(entry.getValue() instanceof IChannelDefinition) || !channelMap.containsKey(name))
          throw fault("Invalid native construction channel definition");
        orderedDefinitions.put(name, (IChannelDefinition) entry.getValue());
      }
      for (Map.Entry<String, IChannelDefinition> entry : orderedDefinitions.entrySet()) {
        JsonObject definition = channelDefinition(entry.getKey(), entry.getValue());
        if (!definition.get("maximum").isJsonNull()
            && definition.get("maximum").getAsInt() != channelMap.get(entry.getKey()))
          throw fault("Native channel definition differs from its stored maximum");
        definitions.add(entry.getKey(), definition);
      }
      Map<?, ?> tiers = binding.tiers.getItemTiers(),
          nativeChannels = binding.tiers.getItemChannels();
      if (tiers.size() > 4096 || !tiers.keySet().containsAll(nativeChannels.keySet()))
        throw fault("Invalid native structure tier lookup");
      JsonArray tierItems = array();
      TreeSet<Long> tierKeys = new TreeSet<>();
      for (Object key : tiers.keySet()) tierKeys.add(hashKey(key));
      for (long hash : tierKeys) {
        Object rawChannel = nativeChannels.get(hash);
        JsonArray itemChannels = array();
        if (rawChannel != null) {
          if (!(rawChannel instanceof Collection) || ((Collection<?>) rawChannel).size() > 256)
            throw fault("Invalid native item channel collection");
          Set<String> seen = new HashSet<>();
          for (Object raw : (Collection<?>) rawChannel) {
            String name = channelName(raw);
            if (!seen.add(name)) throw fault("Duplicate native item channel");
            itemChannels.add(value(name));
          }
        }
        tierItems.add(
            object(
                "nativeHash",
                Long.toString(hash),
                "items",
                matches.get(hash),
                "tier",
                integer(tiers.get(hash)),
                "channels",
                itemChannels));
        tierCount++;
      }
      data.add(
          object(
              "kind",
              "structureCatalogController",
              "ordinal",
              index,
              "structure",
              binding.machine.identity(),
              "controller",
              item(controller, specimens),
              "origin",
              binding.machine.origin(),
              "hasData",
              binding.tiers.hasData(),
              "hasTierData",
              binding.tiers.hasTierData(),
              "maxTier",
              binding.tiers.getMaxTotalTier(),
              "channels",
              channels,
              "channelDefinitions",
              definitions,
              "tierItems",
              tierItems));
    }
    int unresolved = 0, memberships = 0;
    TreeMap<Long, Object> ordered = new TreeMap<>();
    for (Map.Entry<?, ?> entry : components.entrySet())
      ordered.put(hashKey(entry.getKey()), entry.getValue());
    for (Map.Entry<Long, Object> entry : ordered.entrySet()) {
      if (!(entry.getValue() instanceof Set) || ((Set<?>) entry.getValue()).isEmpty())
        throw fault("Invalid native component association");
      TreeSet<String> linked = new TreeSet<>();
      for (Object constructable : (Set<?>) entry.getValue()) {
        String id = owners.get(constructable);
        if (id == null) throw changed("Native component association has no registered controller");
        linked.add(id);
        if (++memberships > 2097152) throw fault("Native component membership budget exceeded");
      }
      JsonArray controllers = array();
      for (String id : linked) controllers.add(value(id));
      JsonArray items = matches.get(entry.getKey());
      if (items.size() == 0) unresolved++;
      data.add(
          object(
              "kind",
              "structureComponentLookup",
              "nativeHash",
              Long.toString(entry.getKey()),
              "items",
              items,
              "structures",
              controllers));
    }
    counts.addProperty("unresolvedLookups", unresolved);
    counts.addProperty("tierLookups", tierCount);
    String domain =
        Identity.content("reference", object("kind", "nativeStructures", "records", data));
    JsonArray rows = array();
    long bytes = 0;
    for (int index = 0; index < data.size(); index++) {
      JsonObject row = object("domain", domain, "order", index, "data", data.get(index));
      row.addProperty("id", Identity.content("refrow", row));
      int length = CanonicalJson.bytes(row).length;
      bytes += length;
      if (length > 1048576 || bytes > 64L * 1024 * 1024)
        throw fault("Native structure information exceeds its declaration budget");
      rows.add(row);
    }
    return rows;
  }

  private static String item(ItemStack item, Map<String, ItemStack> specimens) {
    String registry =
        item.getItem() == null ? null : Item.itemRegistry.getNameForObject(item.getItem());
    if (registry == null || Item.itemRegistry.getObject(registry) != item.getItem())
      throw fault("Unregistered native structure specimen");
    String id =
        Identity.item(registry, item.getItemDamage(), TypedNbt.encode(item.getTagCompound()));
    specimens.put(id, item.copy());
    return id;
  }

  private static String channelName(Object raw) {
    if (!(raw instanceof String) || ((String) raw).isEmpty() || ((String) raw).length() > 128)
      throw fault("Invalid native structure channel");
    return (String) raw;
  }

  private static JsonObject channelDefinition(String name, IChannelDefinition definition) {
    Class<?> type = definition.getClass();
    Integer minimum = inherited(type, "getMinimumValue") ? 1 : null;
    Integer maximum = inherited(type, "getMaximumValue") ? Integer.MAX_VALUE : null;
    String kind = "custom";
    if (type == BOUNDED) {
      if (!name.equals(MagicApi.field(definition, "val$channel")))
        throw fault("Bounded construction channel key differs");
      minimum = integer(MagicApi.field(definition, "val$minimumValue"));
      maximum = integer(MagicApi.field(definition, "val$maximumValue"));
      if (minimum < 1 || maximum < minimum)
        throw fault("Invalid native bounded channel definition");
      kind = "bounded";
    } else if (type == UNBOUNDED) {
      kind = "unbounded";
    }
    // Defaults are known independently; unknown getters/callbacks are never invoked.
    return object(
        "kind", kind, "minimum", minimum, "maximum", maximum,
        "validation", inherited(type, "isValueValid", int.class) ? "inclusiveBounds" : "opaqueCustom",
        "normalization", inherited(type, "normalizeValue", int.class) ? "clampToBounds" : "opaqueCustom");
  }

  private static boolean inherited(Class<?> type, String method, Class<?>... parameters) {
    try {
      return type.getMethod(method, parameters).getDeclaringClass() == IChannelDefinition.class;
    } catch (NoSuchMethodException error) {
      throw fault("Missing native construction channel method");
    }
  }

  private static long hashKey(Object raw) {
    if (!(raw instanceof Long)) throw fault("Invalid native component hash");
    return (Long) raw;
  }

  private static int integer(Object raw) {
    if (!(raw instanceof Integer))
      throw fault("Invalid native structure tier");
    return (Integer) raw;
  }

  private static JsonObject copy(JsonObject row) {
    return new JsonParser().parse(row.toString()).getAsJsonObject();
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("reference_unsupported", message);
  }

  private static Jobs.Fault changed(String message) {
    return new Jobs.Fault("environment_changed", message);
  }

  @Override
  public void close() {
    specimens.clear();
    definitionObjects.clear();
  }
}
