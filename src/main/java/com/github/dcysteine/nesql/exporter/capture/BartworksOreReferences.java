package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Native Bartworks mineral rules and lookup provenance, not consuming recipes. */
final class BartworksOreReferences extends ReaderSnapshot implements NativeReferences {
  static final String HANDLER = "bartworks.neiHandler.OreNEIHandler";
  private  JsonObject source;
  private  JsonArray rows;
  private final Map<String, ItemStack> specimens = new TreeMap<>();
  private final String signature, domain;
  private final int activeSize, materialSize, lookupSize;

  static boolean supports(ICraftingHandler handler) {
    return handler != null && HANDLER.equals(handler.getClass().getName());
  }

  BartworksOreReferences(ICraftingHandler handler, JsonObject source) {
    if (!supports(handler)) throw fault("Unknown Bartworks ore handler");
    this.source = copy(source);
    rows = current(specimens);
    signature = CanonicalJson.digest(rows);
    domain = rows.get(0).getAsJsonObject().get("domain").getAsString();
    activeSize = active().size();
    materialSize = materialRegistry().size();
    lookupSize = lookup().size();
  }

  private static void readiness() {
    if (!Loader.instance().hasReachedState(LoaderState.AVAILABLE))
      throw new Jobs.Fault(
          "handler_not_ready", "Bartworks world-generation registration is unfinished");
  }

  private static List<?> active() {
    return list(field(type("bartworks.system.oregen.BWOreLayer"), null, "sList"));
  }

  private static Set<?> materialRegistry() {
    Object raw = field(type("bartworks.system.material.Werkstoff"), null, "werkstoffHashSet");
    if (!(raw instanceof Set)) throw fault("Missing Werkstoff registry");
    return (Set<?>) raw;
  }

  private static Map<?, ?> lookup() {
    Object map = field(type("bartworks.system.oregen.BWOreLayer"), null, "NEIMAP");
    Object raw = invoke(map.getClass(), map, "asMap", new Class<?>[0]);
    if (!(raw instanceof Map)) throw fault("Missing Bartworks metadata lookup");
    return (Map<?, ?>) raw;
  }

  private JsonArray current(Map<String, ItemStack> visuals) {
    readiness();
    return snapshot(
        true,
        source,
        active(),
        new ArrayList<>(materialRegistry()),
        lookup(),
        number(field(type("bartworks.system.oregen.BWOreLayer"), null, "sWeight")),
        visuals);
  }

  public int size() { checkOpen();
    return rows.size();
  }

  public String domain() { checkOpen();
    return domain;
  }

  public JsonObject counts() { checkOpen();
    return copy(rows.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("counts"));
  }

  public void verify() { checkOpen();
    readiness();
    if (activeSize != active().size()
        || materialSize != materialRegistry().size()
        || lookupSize != lookup().size())
      throw new Jobs.Fault("environment_changed", "Bartworks mineral registration changed");
  }

  public void finishVerification() { checkOpen();
    verify();
    if (!signature.equals(CanonicalJson.digest(current(new TreeMap<>()))))
      throw new Jobs.Fault(
          "environment_changed", "Bartworks layer, material, native samples or lookup changed");
  }

  public void capture(int index, Facts facts) { checkOpen();
    if (index < 0 || index >= rows.size()) throw fault("Invalid Bartworks reference index");
    JsonObject row = copy(rows.get(index).getAsJsonObject()), data = row.getAsJsonObject("data");
    String kind = data.get("kind").getAsString();
    if (kind.equals("bartworksOreLayer"))
      for (JsonElement role : data.getAsJsonArray("roles"))
        emit(role.getAsJsonObject().get("item").getAsString(), facts);
    if (kind.equals("bartworksOreMaterial"))
      for (JsonElement sample : data.getAsJsonArray("samples")) {
        JsonElement item = sample.getAsJsonObject().get("item");
        if (!item.isJsonNull()) emit(item.getAsString(), facts);
      }
    facts.row("references", row);
  }

  private void emit(String id, Facts facts) {
    ItemStack stack = specimens.get(id);
    if (stack == null || !id.equals(facts.item(stack.copy())))
      throw fault("Native Bartworks item identity changed");
  }

  static JsonArray snapshot(
      boolean ready,
      JsonObject source,
      List<?> active,
      List<?> materials,
      Map<?, ?> rawLookup,
      int globalWeight,
      Map<String, ItemStack> visuals) {
    if (!ready)
      throw new Jobs.Fault(
          "handler_not_ready", "Bartworks world-generation registration is unfinished");
    if (active.size() > 65536 || materials.size() > 65536 || rawLookup.size() > 65536)
      throw fault("Bartworks mineral registry exceeds budget");
    List<Object> layers = new ArrayList<>();
    IdentityHashMap<Object, Integer> activeOrdinals = new IdentityHashMap<>();
    Set<Object> discovered = Collections.newSetFromMap(new IdentityHashMap<>());
    for (int ordinal = 0; ordinal < active.size(); ordinal++) {
      Object layer = active.get(ordinal);
      if (activeOrdinals.put(layer, ordinal) != null)
        throw fault("Duplicate active Bartworks generator");
      layers.add(layer);
      discovered.add(layer);
    }
    int memberships = 0;
    TreeMap<Integer, Collection<?>> lookup = new TreeMap<>();
    for (Map.Entry<?, ?> entry : rawLookup.entrySet()) {
      if (!(entry.getKey() instanceof Short) || !(entry.getValue() instanceof Collection))
        throw fault("Invalid Bartworks metadata lookup");
      Collection<?> members = (Collection<?>) entry.getValue();
      if (members.isEmpty() || members.size() > 262144)
        throw fault("Invalid Bartworks lookup members");
      lookup.put(number(entry.getKey()), members);
      memberships = Math.addExact(memberships, members.size());
      if (memberships > 262144) throw fault("Bartworks total lookup membership budget exceeded");
      for (Object layer : members) if (discovered.add(layer)) layers.add(layer);
    }
    if (layers.size() > 65536) throw fault("Bartworks layer budget exceeded");
    JsonObject definition =
        object(
            "kind",
            "bartworksOresDefinition",
            "source",
            source,
            "ready",
            true,
            "consuming",
            false,
            "nativeOverlayPolicy",
            "oreFeatureForBothVariantsThenLayerOnlyDedup",
            "layerDiscovery",
            "activeListThenMetadataLookup",
            "enabledWeight",
            globalWeight,
            "registeredMaterials",
            materials.size());
    JsonArray data = array(definition);
    IdentityHashMap<Object, String> keys = new IdentityHashMap<>();
    Set<String> unique = new HashSet<>();
    Map<Integer, List<String>> expected = new TreeMap<>();
    int sum = 0;
    String[] roles = {"primary", "secondary", "between", "sporadic"},
        metaFields = {"mPrimaryMeta", "mSecondaryMeta", "mBetweenMeta", "mSporadicMeta"};
    for (int ordinal = 0; ordinal < layers.size(); ordinal++) {
      Jobs.checkpoint();
      Object layer = layers.get(ordinal);
      String cls = layer.getClass().getName();
      boolean moon = cls.equals("bartworks.system.oregen.BWWorldGenRoss128ba");
      if (!moon && !cls.equals("bartworks.system.oregen.BWWorldGenRoss128b"))
        throw fault("Unadapted Bartworks generator subclass: " + cls);
      String key = text(field(layer, "mWorldGenName"));
      if (!unique.add(key)) throw fault("Ambiguous Bartworks layer name");
      keys.put(layer, key);
      boolean enabled = bool(field(layer, "mEnabled"));
      Integer activeOrdinal = activeOrdinals.get(layer);
      if (enabled != (activeOrdinal != null))
        throw new Jobs.Fault(
            "environment_changed", "Bartworks enabled state and active list differ");
      int weight = number(field(layer, "mWeight"));
      if (enabled) sum += weight;
      Object config =
          field(type("bartworks.common.configs.Configuration"), null, "crossModInteractions");
      int dimension = number(field(config, moon ? "ross128BAID" : "ross128BID"));
      String provider =
          "bwcrossmod.galacticraft.planets."
              + (moon ? "ross128ba.WorldProviderRoss128ba" : "ross128b.WorldProviderRoss128b");
      if (!bool(
              invoke(
                  layer.getClass(),
                  layer,
                  "isGenerationAllowed",
                  new Class<?>[] {String.class, int.class, int.class},
                  "native-policy",
                  dimension,
                  0))
          || bool(
              invoke(
                  layer.getClass(),
                  layer,
                  "isGenerationAllowed",
                  new Class<?>[] {String.class, int.class, int.class},
                  "native-policy",
                  dimension ^ 1,
                  0))) throw fault("Bartworks dimension predicate differs");
      Object rawBlock =
          invoke(layer.getClass(), layer, "getDefaultBlockToReplace", new Class<?>[0]);
      String block = null;
      if (rawBlock != null) {
        if (!(rawBlock instanceof Block)) throw fault("Invalid replacement block");
        block = text(Block.blockRegistry.getNameForObject(rawBlock));
      }
      Object rawDamage =
          invoke(layer.getClass(), layer, "getDefaultDamageToReplace", new Class<?>[0]);
      if (!(rawDamage instanceof int[]) || ((int[]) rawDamage).length > 256)
        throw fault("Invalid replacement metadata");
      JsonArray damages = array();
      for (int damage : (int[]) rawDamage) damages.add(new JsonPrimitive(damage));
      int mask = number(field(layer, "bwOres"));
      if (mask < 0 || mask > 15) throw fault("Unknown Bartworks ownership mask");
      List<?> stacks = list(invoke(layer.getClass(), layer, "getStacks", new Class<?>[0])),
          raw = list(invoke(layer.getClass(), layer, "getStacksRawData", new Class<?>[0]));
      if (stacks.size() != 4 || raw.size() != 4) throw fault("Bartworks role count differs");
      JsonArray samples = array();
      for (int role = 0; role < 4; role++) {
        int meta = number(field(layer, metaFields[role]));
        boolean bw = (mask & (8 >> role)) != 0;
        Object pair = raw.get(role);
        if (number(invoke(pair.getClass(), pair, "getLeft", new Class<?>[0])) != meta
            || bool(invoke(pair.getClass(), pair, "getRight", new Class<?>[0])) != bw)
          throw fault("Bartworks raw role differs");
        ItemStack stack = stack(stacks.get(role));
        if (stack.stackSize != 1 || stack.getItemDamage() != meta)
          throw fault("Bartworks layer specimen differs");
        Block expectedBlock =
            (Block)
                field(
                    type(
                        bw
                            ? "bartworks.system.material.WerkstoffLoader"
                            : "gregtech.api.GregTechAPI"),
                    null,
                    bw ? "BWOres" : "sBlockOres1");
        if (Item.getItemFromBlock(expectedBlock) != stack.getItem())
          throw fault("Bartworks role item owner differs");
        samples.add(
            object(
                "role", roles[role], "meta", meta, "bartworks", bw, "item", item(stack, visuals)));
        expected.computeIfAbsent((int) (short) meta, ignored -> new ArrayList<>()).add(key);
      }
      data.add(
          object(
              "kind",
              "bartworksOreLayer",
              "key",
              key,
              "ordinal",
              ordinal,
              "activeOrdinal",
              activeOrdinal,
              "generatorClass",
              cls,
              "enabled",
              enabled,
              "minY",
              number(field(layer, "mMinY")),
              "maxY",
              number(field(layer, "mMaxY")),
              "weight",
              weight,
              "density",
              number(field(layer, "mDensity")),
              "size",
              number(field(layer, "mSize")),
              "bwMask",
              mask,
              "dimension",
              object(
                  "key",
                  moon ? "moon.Ross128ba" : "planet.Ross128b",
                  "name",
                  text(invoke(layer.getClass(), layer, "getDimName", new Class<?>[0])),
                  "id",
                  dimension,
                  "provider",
                  provider),
              "replacement",
              object("registry", block, "metas", damages),
              "roles",
              samples));
    }
    if (sum != globalWeight)
      throw new Jobs.Fault("environment_changed", "Bartworks native enabled weight differs");
    Map<Integer, List<String>> actual = new TreeMap<>();
    for (Map.Entry<Integer, Collection<?>> entry : lookup.entrySet()) {
      JsonArray names = array();
      List<String> list = new ArrayList<>();
      for (Object layer : entry.getValue()) {
        String key = keys.get(layer);
        if (key == null) throw fault("Unknown Bartworks lookup layer");
        names.add(new JsonPrimitive(key));
        list.add(key);
      }
      actual.put(entry.getKey(), list);
      // Membership multiplicity must be complete; native order is preserved separately.
      List<String>
          left = new ArrayList<>(expected.getOrDefault(entry.getKey(), Collections.emptyList())),
          right = new ArrayList<>(list);
      Collections.sort(left);
      Collections.sort(right);
      if (!left.equals(right))
        throw new Jobs.Fault("environment_changed", "Bartworks metadata membership differs");
    }
    if (!actual.keySet().equals(expected.keySet()))
      throw new Jobs.Fault("environment_changed", "Bartworks metadata coverage differs");
    int captured = 0;
    Set<Integer> ids = new HashSet<>();
    Class<?> prefix = type("gregtech.api.enums.OrePrefixes");
    Object ore = field(prefix, null, "ore"), small = field(prefix, null, "oreSmall");
    if (!materials.isEmpty()
        && number(
                invoke(
                    type("bartworks.system.material.Werkstoff$GenerationFeatures"),
                    null,
                    "getPrefixDataRaw",
                    new Class<?>[] {prefix},
                    ore))
            == 0)
      throw new Jobs.Fault(
          "handler_not_ready", "Werkstoff generation-prefix registration is unfinished");
    for (int ordinal = 0; ordinal < materials.size(); ordinal++) {
      Jobs.checkpoint();
      Object material = materials.get(ordinal);
      if (material == null
          || !material.getClass().getName().equals("bartworks.system.material.Werkstoff"))
        throw fault("Unadapted Werkstoff material");
      int id = number(invoke(material.getClass(), material, "getmID", new Class<?>[0]));
      if (!ids.add(id)) throw fault("Duplicate native Werkstoff ID");
      boolean
          hasOre =
              bool(
                  invoke(
                      material.getClass(),
                      material,
                      "hasGenerationFeature",
                      new Class<?>[] {prefix},
                      ore)),
          hasSmall =
              bool(
                  invoke(
                      material.getClass(),
                      material,
                      "hasGenerationFeature",
                      new Class<?>[] {prefix},
                      small));
      if (!hasOre && !hasSmall) continue;
      JsonArray variants = array();
      Object[] types = {ore, small};
      String[] names = {"ore", "smallOre"};
      for (int variant = 0; variant < 2; variant++) {
        Object raw =
            invoke(material.getClass(), material, "get", new Class<?>[] {prefix}, types[variant]);
        ItemStack sample = raw == null ? null : stack(raw);
        // Native Werkstoff.get prefers bridge/unification entries. Their item metadata
        // belongs to the selected item and need not equal this Werkstoff's registry ID.
        variants.add(
            object(
                "variant",
                names[variant],
                "nativeOverlayEligible",
                hasOre,
                "item",
                sample == null ? null : item(sample, visuals),
                "amount",
                sample == null ? null : sample.stackSize));
      }
      data.add(
          object(
              "kind",
              "bartworksOreMaterial",
              "key",
              "werkstoff:" + id,
              "meta",
              id,
              "ordinal",
              ordinal,
              "defaultName",
              text(invoke(material.getClass(), material, "getDefaultName", new Class<?>[0])),
              "name",
              text(invoke(material.getClass(), material, "getLocalizedName", new Class<?>[0])),
              "oreFeature",
              hasOre,
              "smallOreFeature",
              hasSmall,
              "samples",
              variants));
      captured++;
    }
    int ordinal = 0;
    for (Map.Entry<Integer, List<String>> entry : actual.entrySet()) {
      JsonArray members = array();
      for (String key : entry.getValue()) members.add(new JsonPrimitive(key));
      data.add(
          object(
              "kind",
              "bartworksOreLookup",
              "meta",
              entry.getKey(),
              "ordinal",
              ordinal++,
              "layers",
              members));
    }
    definition.add(
        "counts", object("layers", layers.size(), "materials", captured, "lookups", actual.size()));
    String domain = Identity.content("reference", object("kind", "bartworksOres", "records", data));
    JsonArray rows = array();
    for (int order = 0; order < data.size(); order++) {
      JsonObject row = object("domain", domain, "order", order, "data", data.get(order));
      row.addProperty("id", Identity.content("refrow", row));
      if (CanonicalJson.bytes(row).length > 1048576) throw fault("Bartworks record exceeds budget");
      rows.add(row);
    }
    return rows;
  }

  private static JsonObject copy(JsonObject row) {
    return new JsonParser().parse(row.toString()).getAsJsonObject();
  }

  private static String item(ItemStack stack, Map<String, ItemStack> visuals) {
    String registry = text(Item.itemRegistry.getNameForObject(stack.getItem()));
    String id =
        Identity.item(registry, stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound()));
    visuals.put(id, stack.copy());
    return id;
  }

  private static ItemStack stack(Object raw) {
    if (!(raw instanceof ItemStack)
        || ((ItemStack) raw).getItem() == null
        || ((ItemStack) raw).stackSize <= 0) throw fault("Invalid native Bartworks sample");
    return (ItemStack) raw;
  }

  private static List<?> list(Object raw) {
    if (!(raw instanceof List)) throw fault("Missing native Bartworks list");
    return (List<?>) raw;
  }

  private static int number(Object raw) {
    if (!(raw instanceof Integer) && !(raw instanceof Short) && !(raw instanceof Byte))
      throw fault("Invalid native Bartworks integer");
    return ((Number) raw).intValue();
  }

  private static boolean bool(Object raw) {
    if (!(raw instanceof Boolean)) throw fault("Invalid native Bartworks flag");
    return (Boolean) raw;
  }

  private static String text(Object raw) {
    if (!(raw instanceof String) || ((String) raw).isEmpty() || ((String) raw).length() > 4096)
      throw fault("Invalid native Bartworks text");
    return (String) raw;
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("bartworks_ores", message);
  }

    @Override protected void release(){specimens.clear();source=null;rows=null;}
}
