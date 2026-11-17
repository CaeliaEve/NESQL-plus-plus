package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.Array;
import java.util.*;
import net.minecraft.item.ItemStack;

/** Published predictor tables. Never starts a calculation or calls crop/display callbacks. */
final class Ic2CropSource implements AutoCloseable {
  private static final String BREEDER = "speiger.src.crops.prediction.Breeder",
      RESULT = "speiger.src.crops.prediction.BreedResult",
      API = "speiger.src.crops.api.CropPluginAPI",
      CROPS = "ic2.api.crops.Crops", REGISTRY = "ic2.core.crop.IC2Crops",
      CROP = "ic2.api.crops.CropCard";
  final List<Ic2CropBreedingRecipes.Entry> entries = new ArrayList<>();
  private final IdentityHashMap<Object, JsonObject> keys = new IdentityHashMap<>();
  private final IdentityHashMap<Object, ItemStack> displays = new IdentityHashMap<>();
  private final IdentityHashMap<Object, String> displayIds = new IdentityHashMap<>();
  private final IdentityHashMap<Object, Prediction> predictions = new IdentityHashMap<>();
  private final List<Group> groups = new ArrayList<>();
  private Object[] roots;
  private int[] sizes;
  private NativeDefinitionGraph graph;
  private long members, definitionBytes;

  static Object[] registrationAnchors() {
    // Every reviewed handler instance reads these shared authorities. Discovery
    // need not wait for their background calculation; opening the reader does.
    Object[] roots = owners(), result = new Object[roots.length + 1];
    result[0] = type("speiger.src.crops.prediction.NEIPlugin");
    System.arraycopy(roots, 0, result, 1, roots.length);
    return result;
  }

  Ic2CropSource() {
    try {
      roots = roots(); sizes = sizes(roots);
      Map<String, String[]> shapes = new HashMap<>();
      for (Object names : map(roots[6]).values())
        for (Object crop : map(names).values()) crop(crop, shapes);
      for (Object crop : map(roots[4]).keySet()) crop(crop, shapes);
      graph = new NativeDefinitionGraph(() -> {
        Object[] current = roots(); return new Object[] {current[6], current[4]};
      }, shapes);
      for (Map.Entry<?, ?> owner : map(roots[6]).entrySet()) {
        String namespace = text(owner.getKey());
        for (Map.Entry<?, ?> name : map(owner.getValue()).entrySet()) {
          Jobs.checkpoint(); Object crop = name.getValue();
          if (keys.size() >= 65536 || keys.put(crop, object("owner", namespace, "name", text(name.getKey()))) != null)
            throw fault("Ambiguous or over-budget IC2 crop registry identity");
        }
      }
      for (Map.Entry<?, ?> display : map(roots[4]).entrySet()) {
        Object value = display.getValue();
        if (!(value instanceof ItemStack)) throw fault("Missing IC2 crop display stack");
        NativeStackAudit.audit((ItemStack) value);
        ItemStack owned=((ItemStack) value).copy();
        displays.put(display.getKey(), owned);
        displayIds.put(display.getKey(), Ic2CropBreedingRecipes.stackKey(owned));
      }

      IdentityHashMap<Object, Set<String>> usageClasses = new IdentityHashMap<>();
      Set<String> craftingClasses = new HashSet<>();
      for (Group group : readGroups(map(roots[1]))) {
        for (Object raw : group.members) {
          Prediction prediction = prediction(raw);
          if (prediction.result != group.crop) throw nativeError("Crafting group has a different result crop");
          prediction.entry.craftingListed=true;craftingClasses.add(prediction.nativeClass);
        }
      }
      for (Group group : readGroups(map(roots[2]))) {
        for (Object raw : group.members) {
          Prediction prediction = prediction(raw);
          if (!contains(prediction.parents, group.crop))
            throw nativeError("Usage prediction is inconsistent with the published crafting table");
          usageClasses.computeIfAbsent(group.crop,ignored->new HashSet<>()).add(prediction.nativeClass);
          JsonObject parent=key(group.crop);
          if(!prediction.entry.usageListedFor.contains(parent))prediction.entry.usageListedFor.add(parent);
        }
      }
      Map<String,Ic2CropBreedingRecipes.Entry> union=new TreeMap<>();
      for(Prediction prediction:predictions.values()){
        if(!craftingClasses.contains(prediction.nativeClass))throw nativeError("Published crafting table omits a native prediction class");
        for(Object parent:prediction.parents)if(!usageClasses.getOrDefault(parent,Collections.emptySet()).contains(prediction.nativeClass))throw nativeError("Published usage table omits a native parent prediction class");
        Ic2CropBreedingRecipes.Entry entry=union.putIfAbsent(prediction.entry.order,prediction.entry);
        if(entry!=null){entry.craftingListed|=prediction.entry.craftingListed;for(JsonObject parent:prediction.entry.usageListedFor)if(!entry.usageListedFor.contains(parent))entry.usageListedFor.add(parent);}
      }
      entries.addAll(union.values());
      for(Ic2CropBreedingRecipes.Entry entry:entries)entry.usageListedFor.sort(Comparator.comparing(CanonicalJson::digest));
      entries.sort(Comparator.comparing(entry -> entry.order));
      verify(); graph.verify();
    } catch (RuntimeException | Error failure) { close(); throw failure; }
  }

  private List<Group> readGroups(Map<?, ?> map) {
    List<Group> result = new ArrayList<>();
    for (Map.Entry<?, ?> pair : map.entrySet()) {
      Jobs.checkpoint(); Object crop = pair.getKey(); key(crop);
      Object raw = pair.getValue();
      if (raw == null || raw.getClass() != ArrayList.class)
        throw fault("Unadapted IC2 crop prediction group storage");
      List<?> list = (List<?>) raw;
      if (list.size() > 262144 || (members += list.size()) > 1310720)
        throw limit("IC2 crop prediction memberships exceed budget");
      Group group = new Group(map, crop, list); result.add(group); groups.add(group);
    }
    result.sort(Comparator.comparing(group -> CanonicalJson.digest(key(group.crop))));
    return result;
  }

  private Prediction prediction(Object raw) {
    if (raw == null || raw.getClass() != type(RESULT))
      throw fault("Unadapted IC2 crop prediction object owner");
    Prediction found = predictions.get(raw);
    if (found != null) return found;
    if (predictions.size() >= 262144) throw limit("IC2 crop predictions exceed entry budget");
    Prediction prediction = new Prediction(raw);
    definitionBytes += CanonicalJson.bytes(prediction.entry.snapshot).length;
    if (definitionBytes > 64L * 1024 * 1024) throw limit("IC2 crop prediction snapshots exceed byte budget");
    predictions.put(raw, prediction); return prediction;
  }

  private JsonObject key(Object crop) {
    JsonObject key = keys.get(crop);
    if (key == null) throw fault("Prediction references an unregistered native crop");
    return key;
  }
  private ItemStack display(Object crop) {
    ItemStack stack = displays.get(crop);
    if (stack == null) throw nativeError("Published prediction has no display item in CropPluginAPI");
    return stack;
  }

  void verify() {
    Jobs.checkpoint();
    if (roots == null) throw new Jobs.Fault("capture_closed", "IC2 crop source is closed");
    Object[] current = roots();
    for (int i = 0; i < roots.length; i++) if (roots[i] != current[i]) throw changed();
    if (!Arrays.equals(sizes, sizes(current))) throw changed();
  }
  void finishVerification() {
    verify(); graph.verify();
    IdentityHashMap<Object, Object> craft = identityEntries(map(roots[1])),
        usage = identityEntries(map(roots[2]));
    for (Group group : groups) group.verify(group.table == roots[1] ? craft : usage);
    for (Prediction prediction : predictions.values()) prediction.verify();
    for (Map.Entry<Object, ItemStack> display : displays.entrySet()) {
      Jobs.checkpoint();
      if (!Ic2CropBreedingRecipes.stackKey(display.getValue()).equals(displayIds.get(display.getKey()))) throw changed();
    }
  }
  public void close() {
    if (graph != null) graph.close(); graph = null;
    roots = null; sizes = null;
    entries.clear(); keys.clear(); displays.clear(); displayIds.clear(); predictions.clear(); groups.clear();
  }

  private final class Prediction {
    final Object raw, array, result;
    final Object[] parents;
    final int points, total;
    final Ic2CropBreedingRecipes.Entry entry;
    final String nativeClass;
    Prediction(Object raw) {
      this.raw = raw; array = field(raw, "input"); result = field(raw, "result");
      Class<?> arrayType = Array.newInstance(type(CROP), 0).getClass();
      if (array == null || array.getClass() != arrayType)
        throw fault("Unadapted IC2 crop parent array owner");
      parents = ((Object[]) array).clone();
      if (parents.length < 2 || parents.length > 4) throw fault("Invalid IC2 crop parent count");
      points = (Integer) field(raw, "points"); total = (Integer) field(raw, "total");
      if (points <= 0 || total <= 0 || points > total) throw fault("Invalid IC2 crop prediction weights");
      IdentityHashMap<Object, Boolean> unique = new IdentityHashMap<>();
      com.google.gson.JsonArray parentKeys = array();
      ItemStack[] inputs = new ItemStack[parents.length];
      String[] identities = new String[parents.length];
      for (int i = 0; i < parents.length; i++) {
        if (unique.put(parents[i], true) != null) throw fault("Duplicate IC2 crop parents");
        parentKeys.add(key(parents[i])); inputs[i] = display(parents[i]); identities[i]=displayIds.get(parents[i]);
      }
      if (unique.containsKey(result)) throw fault("IC2 predictor result repeats a parent");
      entry = new Ic2CropBreedingRecipes.Entry(inputs, display(result), points, total, parentKeys, key(result), identities, displayIds.get(result));
      // Target BreedResult.matches ignores the parent vector and denominator. Each native
      // table independently keeps the first result/points/parent-count representative.
      nativeClass=CanonicalJson.digest(object("result",key(result),"points",points,"parents",parents.length));
    }
    void verify() {
      Jobs.checkpoint();
      if (field(raw, "input") != array || field(raw, "result") != result
          || (Integer) field(raw, "points") != points || (Integer) field(raw, "total") != total) throw changed();
      Object[] current = (Object[]) array;
      for (int i = 0; i < parents.length; i++) if (parents[i] != current[i]) throw changed();
    }
  }
  private static final class Group {
    final Map<?, ?> table;
    final Object crop;
    final List<?> list;
    final Object[] members;
    Group(Map<?, ?> table, Object crop, List<?> list) {
      this.table = table; this.crop = crop; this.list = list; members = list.toArray();
    }
    void verify(IdentityHashMap<Object, Object> currentEntries) {
      Jobs.checkpoint();
      if (currentEntries.get(crop) != list || list.size() != members.length) throw changed();
      for (int i = 0; i < members.length; i++) if (list.get(i) != members[i]) throw changed();
    }
  }

  private static Object[] roots() {
    Object[] owners=owners();
    Object breeder=owners[0],api=owners[1],registry=owners[2];
    Object craft = field(breeder, "craft"), usage = field(breeder, "usage"),
        displays = field(api, "displayItems"), cropMap = field(registry, "cropMap");
    map(craft); map(usage); map(displays); map(cropMap);
    return new Object[] {breeder, craft, usage, api, displays, registry, cropMap};
  }
  private static Object[] owners() {
    Object breeder = field(type(BREEDER), null, "INSTANCE"),
        api = field(type(API), null, "instance"), registry = field(type(CROPS), null, "instance");
    if (breeder == null || api == null || registry == null)
      throw new Jobs.Fault("handler_not_ready", "IC2 crop predictor sources are unavailable");
    if (breeder.getClass() != type(BREEDER) || api.getClass() != type(API) || registry.getClass() != type(REGISTRY))
      throw fault("Unadapted IC2 crop predictor source owner");
    return new Object[] {breeder, api, registry};
  }
  private static Map<?, ?> map(Object value) {
    if (value == null) throw new Jobs.Fault("handler_not_ready", "IC2 crop predictor has not published both tables");
    if (value.getClass() != HashMap.class) throw fault("Unadapted IC2 crop source map storage");
    Map<?, ?> map = (Map<?, ?>) value;
    if (map.size() > 65536) throw limit("IC2 crop source map exceeds budget");
    return map;
  }
  private static int[] sizes(Object[] roots) {
    return new int[] {map(roots[1]).size(), map(roots[2]).size(), map(roots[4]).size(), map(roots[6]).size()};
  }
  private static IdentityHashMap<Object, Object> identityEntries(Map<?, ?> table) {
    IdentityHashMap<Object, Object> result = new IdentityHashMap<>();
    for (Map.Entry<?, ?> entry : table.entrySet()) {
      Jobs.checkpoint(); result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }
  private static Object crop(Object value, Map<String, String[]> shapes) {
    if (value == null || !type(CROP).isInstance(value)) throw fault("Invalid native IC2 crop registry member");
    // Crop objects are opaque identity leaves: no owner/name/statistics/GUI callback is executed.
    shapes.put(value.getClass().getName(), new String[0]); return value;
  }
  private static String text(Object value) {
    if (!(value instanceof String) || ((String) value).isEmpty() || ((String) value).length() > 4096)
      throw fault("Invalid registered IC2 crop identity");
    return (String) value;
  }
  private static boolean contains(Object[] values, Object target) {
    for (Object value : values) if (value == target) return true;
    return false;
  }
  private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
  private static Jobs.Fault nativeError(String message) { return new Jobs.Fault("native_error", message); }
  private static Jobs.Fault limit(String message) { return new Jobs.Fault("recipe_limit", message); }
  private static Jobs.Fault changed() { return new Jobs.Fault("environment_changed", "IC2 crop published source, membership or prediction changed"); }
}
