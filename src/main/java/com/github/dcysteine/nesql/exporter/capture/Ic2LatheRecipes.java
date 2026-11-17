package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.*;

/**
 * IC2 lathe is a stateful workpiece transition, not a normal fixed recipe. We enumerate only
 * concrete registered ILatheItem stacks and expose one bounded state transition per native
 * workpiece lane. Unknown callbacks and malformed state arrays fail closed.
 */
final class Ic2LatheRecipes extends ReaderSnapshot implements NativeReferences {
  private static final String HANDLER = "ic2.neiIntegration.core.recipehandler.LatheRecipeHandler";
  private  JsonObject source;
  private  List<ItemStack> panel;
  private final int panelSize;
  private  Snapshot snapshot;
  private final String signature;

  static boolean supports(ICraftingHandler value) {
    return supportsClass(value.getClass().getName());
  }

  static boolean supportsClass(String name) {
    return HANDLER.equals(name);
  }

  Ic2LatheRecipes(ICraftingHandler handler, JsonObject source) {
    if (!supports(handler)) throw fault("Unknown IC2 lathe handler");
    if (!ItemList.loadFinished || ItemList.items == null)
      throw new Jobs.Fault("handler_not_ready", "NEI lathe item catalogue is unavailable");
    this.source = copy(source);
    panel = ItemList.items;
    panelSize = panel.size();
    snapshot = snapshot(this.source, panel);
    signature = CanonicalJson.digest(snapshot.records);
  }

  public int size() { checkOpen();
    return snapshot.records.size();
  }

  public String domain() { checkOpen();
    return snapshot.records.get(0).getAsJsonObject().get("domain").getAsString();
  }

  public JsonObject counts() { checkOpen();
    return copy(
        snapshot
            .records
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("data")
            .getAsJsonObject("counts"));
  }

  public void verify() { checkOpen();
    if (!ItemList.loadFinished || panel != ItemList.items || panel.size() != panelSize)
      throw new Jobs.Fault("environment_changed", "Native lathe item catalogue changed");
  }

  public void finishVerification() { checkOpen();
    verify();
    if (!signature.equals(CanonicalJson.digest(snapshot(source, panel).records)))
      throw new Jobs.Fault(
          "environment_changed", "Native lathe states, tools or callbacks changed");
  }

  public void capture(int index, Facts facts) { checkOpen();
    verify();
    for (String id : snapshot.references.get(index))
      if (!id.equals(facts.item(snapshot.specimens.get(id).copy())))
        throw fault("Native lathe specimen identity changed");
    facts.row("references", copy(snapshot.records.get(index).getAsJsonObject()));
  }

  static Snapshot snapshot(JsonObject source, List<ItemStack> panel) {
    if (panel == null
        || panel.size() > 1_000_000
        || !HANDLER.equals(source.get("handler").getAsString()))
      throw fault("Invalid native lathe source or panel budget");
    Class<?> blankApi = type("ic2.api.item.ILatheItem"),
        toolApi = type("ic2.api.item.ILatheItem$ILatheTool");
    JsonArray directions = array();
    for (net.minecraftforge.common.util.ForgeDirection direction :
        net.minecraftforge.common.util.ForgeDirection.VALID_DIRECTIONS)
      directions.add(new JsonPrimitive(direction.name().toLowerCase(Locale.ROOT)));
    Snapshot result = new Snapshot();
    JsonArray data = array();
    JsonObject counts =
        object(
            "samples",
            0,
            "workpieces",
            0,
            "tools",
            0,
            "states",
            0,
            "toolStates",
            0,
            "transitions",
            0);
    add(
        data,
        result,
        object(
            "kind",
            "latheDefinition",
            "source",
            source,
            "ready",
            true,
            "consuming",
            false,
            "apiClass",
            blankApi.getName(),
            "toolApiClass",
            toolApi.getName(),
            "machineClass",
            "ic2.core.block.machine.tileentity.TileEntityLathe",
            "energyPerCut",
            1000,
            "bufferCapacity",
            10000,
            "toolWear",
            1,
            "hardnessGate",
            "strictlyGreater",
            "outputSpaceCheck",
            "beforeStateChange",
            "byproduct",
            "afterStateChange",
            "minimumRadius",
            1,
            "bufferLeakDivisor",
            100,
            "bufferLeakBiasBits",
            "3f000000",
            "bufferLeakCast",
            "truncateToInt",
            "bufferLeakPhase",
            "beforeIntake",
            "intakeDirections",
            directions,
            "intakeSide",
            "opposite",
            "intakeSource",
            "firstNonzeroAdvertisedSource",
            "counts",
            counts),
        Collections.emptySet());
    Map<String, ItemStack> blanks = new LinkedHashMap<>(), tools = new LinkedHashMap<>();
    int samples = 0, states = 0, toolStates = 0, transitions = 0;
    for (int order = 0; order < panel.size(); order++) {
      ItemStack original = panel.get(order);
      if (original == null || original.getItem() == null) continue;
      boolean work = blankApi.isInstance(original.getItem()),
          tool = toolApi.isInstance(original.getItem());
      if (!work && !tool) continue;
      if (work && tool || original.stackSize <= 0)
        throw fault("Ambiguous or empty native lathe sample");
      if (++samples > 262144) throw fault("Native lathe samples exceed budget");
      int rawMeta =
          cpw.mods.fml.relauncher.ReflectionHelper.getPrivateValue(
              ItemStack.class, original, "itemDamage", "field_77991_e");
      JsonElement rawNbt = TypedNbt.encode(original.getTagCompound());
      ItemStack normalized = original.copy();
      String id = specimen(normalized, result);
      add(
          data,
          result,
          object(
              "kind",
              "latheSample",
              "panelOrder",
              order,
              "role",
              work ? "workpiece" : "tool",
              "item",
              id,
              "rawMeta",
              rawMeta,
              "rawNbt",
              rawNbt,
              "amount",
              Integer.toString(original.stackSize)),
          Collections.singleton(id));
      (work ? blanks : tools).putIfAbsent(id, result.specimens.get(id));
    }
    for (Map.Entry<String, ItemStack> entry : blanks.entrySet()) {
      ItemStack initial = entry.getValue();
      Object item = initial.getItem();
      int width = number(item, "getWidth", initial),
          hardness = number(item, "getHardness", initial);
      int[] initialState = state(item, initial, width);
      add(
          data,
          result,
          object(
              "kind",
              "latheWorkpiece",
              "workpiece",
              entry.getKey(),
              "callbackClass",
              item.getClass().getName(),
              "width",
              width,
              "hardness",
              hardness,
              "initial",
              ints(initialState)),
          Collections.singleton(entry.getKey()));
      Map<String, ItemStack> visited = new LinkedHashMap<>();
      ArrayDeque<ItemStack> pending = new ArrayDeque<>();
      visited.put(entry.getKey(), initial);
      pending.add(initial);
      while (!pending.isEmpty()) {
        Jobs.checkpoint();
        ItemStack before = pending.removeFirst();
        String beforeId = specimen(before, result);
        int currentWidth = number(item, "getWidth", before);
        int[] beforeState = state(item, before, currentWidth);
        if (currentWidth != width || beforeState.length != initialState.length)
          throw fault("Native lathe changes workpiece width or lane count");
        JsonArray edges = array();
        Set<String> referenced = new LinkedHashSet<>();
        referenced.add(beforeId);
        for (int lane = 0; lane < beforeState.length; lane++) {
          if (beforeState[lane] <= 1) continue;
          JsonObject preflight = output(item, before, lane, result);
          referenced.add(preflight.get("item").getAsString());
          ItemStack after = before.copy();
          invoke(
              item.getClass(),
              item,
              "setState",
              new Class<?>[] {ItemStack.class, int.class, int.class},
              after,
              lane,
              beforeState[lane] - 1);
          String afterId = specimen(after, result);
          int[] afterState = state(item, after, width), expected = beforeState.clone();
          expected[lane]--;
          if (!Arrays.equals(afterState, expected))
            throw fault("Native lathe callback does not represent a single-lane decrement");
          JsonObject byproduct = output(item, after, lane, result);
          referenced.add(byproduct.get("item").getAsString());
          referenced.add(afterId);
          float chance =
              (Float)
                  invoke(
                      item.getClass(),
                      item,
                      "getOutputChance",
                      new Class<?>[] {ItemStack.class, int.class},
                      after.copy(),
                      lane);
          // Java Random.nextFloat supplies exactly 24 bits. Derive behavior without changing the
          // source float.
          int tickets =
              Float.isNaN(chance) || chance <= 0
                  ? 0
                  : chance >= 1 ? 16777216 : (int) Math.ceil((double) chance * 16777216L);
          JsonObject roll =
              object(
                  "thresholdBits",
                  String.format(Locale.ROOT, "%08x", Float.floatToRawIntBits(chance)),
                  "tickets",
                  tickets,
                  "samples",
                  16777216,
                  "comparison",
                  "lessThan");
          edges.add(
              object(
                  "lane",
                  lane,
                  "after",
                  afterId,
                  "preflightOutput",
                  preflight,
                  "byproduct",
                  byproduct,
                  "roll",
                  roll));
          if (++transitions > 262144) throw fault("Native lathe transitions exceed budget");
          if (!visited.containsKey(afterId)) {
            if (visited.size() >= 32768)
              throw fault("Native workpiece reachable state budget exceeded");
            visited.put(afterId, after);
            pending.addLast(after);
          }
        }
        if (++states > 262144) throw fault("Native lathe states exceed budget");
        add(
            data,
            result,
            object(
                "kind",
                "latheState",
                "workpiece",
                entry.getKey(),
                "item",
                beforeId,
                "width",
                width,
                "hardness",
                number(item, "getHardness", before),
                "lanes",
                ints(beforeState),
                "transitions",
                edges),
            referenced);
      }
    }
    for (Map.Entry<String, ItemStack> entry : tools.entrySet()) {
      ItemStack tool = entry.getValue().copy();
      Object item = tool.getItem();
      int initialDamage = number(item, "getCustomDamage", tool),
          maximum = number(item, "getMaxCustomDamage", tool);
      if (initialDamage < 0 || maximum <= 0 || maximum > 65536)
        throw fault("Invalid native lathe tool damage bounds");
      add(
          data,
          result,
          object(
              "kind",
              "latheTool",
              "tool",
              entry.getKey(),
              "callbackClass",
              item.getClass().getName(),
              "damage",
              initialDamage,
              "maximum",
              maximum),
          Collections.singleton(entry.getKey()));
      for (; ; ) {
        Jobs.checkpoint();
        String id = specimen(tool, result);
        int damage = number(item, "getCustomDamage", tool),
            max = number(item, "getMaxCustomDamage", tool);
        if (max != maximum || damage < 0 || damage == Integer.MAX_VALUE)
          throw fault("Native tool durability changed or overflowed");
        ItemStack after = tool.copy();
        invoke(
            item.getClass(),
            item,
            "setCustomDamage",
            new Class<?>[] {ItemStack.class, int.class},
            after,
            damage + 1);
        int afterDamage = number(item, "getCustomDamage", after);
        if (afterDamage != damage + 1)
          throw fault("Native tool wear is not one custom damage unit");
        boolean broken = afterDamage >= number(item, "getMaxCustomDamage", after);
        String next = broken ? null : specimen(after, result);
        Set<String> referenced = new LinkedHashSet<>();
        referenced.add(id);
        if (next != null) referenced.add(next);
        add(
            data,
            result,
            object(
                "kind",
                "latheToolState",
                "tool",
                entry.getKey(),
                "item",
                id,
                "damage",
                damage,
                "maximum",
                max,
                "hardness",
                number(item, "getHardness", tool),
                "after",
                next),
            referenced);
        if (++toolStates > 262144) throw fault("Native tool state budget exceeded");
        if (broken) break;
        tool = after;
      }
    }
    counts.addProperty("samples", samples);
    counts.addProperty("workpieces", blanks.size());
    counts.addProperty("tools", tools.size());
    counts.addProperty("states", states);
    counts.addProperty("toolStates", toolStates);
    counts.addProperty("transitions", transitions);
    String domain = Identity.content("reference", object("kind", "latheRules", "records", data));
    long bytes = 0;
    for (int order = 0; order < data.size(); order++) {
      JsonObject row = object("domain", domain, "order", order, "data", data.get(order));
      row.addProperty("id", Identity.content("refrow", row));
      int length = CanonicalJson.bytes(row).length;
      bytes += length;
      if (length > 1048576 || bytes > 32L * 1024 * 1024)
        throw fault("Native lathe references exceed byte budget");
      result.records.add(row);
    }
    return result;
  }

  private static void add(
      JsonArray data, Snapshot result, JsonObject value, Set<String> referenced) {
    data.add(value);
    result.references.add(new LinkedHashSet<>(referenced));
  }

  private static JsonArray ints(int[] values) {
    JsonArray result = array();
    for (int value : values) result.add(new JsonPrimitive(value));
    return result;
  }

  private static int number(Object item, String method, ItemStack stack) {
    return (Integer)
        invoke(item.getClass(), item, method, new Class<?>[] {ItemStack.class}, stack.copy());
  }

  private static int[] state(Object item, ItemStack stack, int width) {
    int[] value =
        (int[])
            invoke(
                item.getClass(),
                item,
                "getCurrentState",
                new Class<?>[] {ItemStack.class},
                stack.copy());
    if (width < 1 || width > 64 || value == null || value.length < 1 || value.length > 16)
      throw fault("Invalid native lathe width or lane array");
    for (int radius : value)
      if (radius < 1 || radius > width) throw fault("Illegal native workpiece radius");
    return value.clone();
  }

  private static JsonObject output(Object item, ItemStack stack, int lane, Snapshot result) {
    ItemStack output =
        (ItemStack)
            invoke(
                item.getClass(),
                item,
                "getOutputItem",
                new Class<?>[] {ItemStack.class, int.class},
                stack.copy(),
                lane);
    if (output == null || output.getItem() == null || output.stackSize <= 0)
      throw new Jobs.Fault("handler_not_ready", "Native lathe byproduct is absent or invalid");
    return object(
        "item", specimen(output.copy(), result), "amount", Integer.toString(output.stackSize));
  }

  private static String specimen(ItemStack stack, Snapshot result) {
    ItemStack copy = stack.copy();
    int meta = copy.getItemDamage();
    String registry = Item.itemRegistry.getNameForObject(copy.getItem());
    if (registry == null || Item.itemRegistry.getObject(registry) != copy.getItem())
      throw fault("Unregistered native lathe item");
    String id = Identity.item(registry, meta, TypedNbt.encode(copy.getTagCompound()));
    copy.stackSize = 1;
    result.specimens.putIfAbsent(id, copy);
    return id;
  }

  static final class Snapshot {
    final JsonArray records = array();
    final Map<String, ItemStack> specimens = new TreeMap<>();
    final List<Set<String>> references = new ArrayList<>();
  }

  private static JsonObject copy(JsonObject value) {
    return new JsonParser().parse(value.toString()).getAsJsonObject();
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("reference_invalid", message);
  }

    @Override protected void release(){source=null;panel=null;snapshot=null;}
}
