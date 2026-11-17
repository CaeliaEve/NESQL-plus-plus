package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Exact native name-map lookup, without checkTagCompound writes or missing-name logging. */
final class FmpMaterialIds implements AutoCloseable {
  private final List<Object> anchors = new ArrayList<>();
  private final Map<String, Integer> names = new TreeMap<>();
  private final Map<String, String> aliases = new TreeMap<>();
  private JsonObject records;
  private String signature;
  private boolean closed;

  FmpMaterialIds() {
    records = read(anchors, names, aliases);
    signature = CanonicalJson.digest(records);
  }

  int resolveName(String name) {
    check();
    if (name == null) throw fault("Null FMP native material key");
    return names.getOrDefault(aliases.getOrDefault(name, name), 0);
  }

  int resolve(ItemStack source) {
    check();
    if (source == null || source.getClass() != ItemStack.class)
      throw fault("Unadapted FMP lookup stack");
    NBTTagCompound nbt = source.getTagCompound();
    NativeStackAudit.nbt(nbt);
    // Native checkTagCompound would add an empty compound to this stack.
    // Its lookup result for no compound/no mat is still exactly ID zero.
    if (nbt == null || !nbt.hasKey("mat")) return 0;
    return resolveName(nbt.getString("mat"));
  }

  static String text(ItemStack source) {
    NativeStackAudit.audit(source);
    NBTTagCompound nbt = source.getTagCompound();
    return nbt == null || !nbt.hasKey("mat") ? null : nbt.getString("mat");
  }

  JsonObject records() {
    check();
    return new JsonParser().parse(records.toString()).getAsJsonObject();
  }

  void verify() {
    check();
    List<Object> current = new ArrayList<>();
    JsonObject facts;
    try {
      facts = read(current, null, null);
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (!signature.equals(CanonicalJson.digest(facts)) || anchors.size() != current.size())
      throw changed();
    for (int index = 0; index < anchors.size(); index++)
      if (anchors.get(index) != current.get(index)) throw changed();
  }

  private static JsonObject read(
      List<Object> bindings, Map<String, Integer> nameOut, Map<String, String> aliasOut) {
    Class<?> owner = type("codechicken.microblock.MicroMaterialRegistry$");
    Object registry = field(owner, null, "MODULE$");
    if (registry == null) throw notReady("FMP material registry is unavailable");
    if (registry.getClass() != owner) throw fault("Unadapted FMP registry owner");
    Object names = field(registry, "codechicken$microblock$MicroMaterialRegistry$$nameMap"),
        aliases = field(registry, "remap"),
        ids = field(registry, "codechicken$microblock$MicroMaterialRegistry$$idMap");
    if (names == null || aliases == null || ids == null)
      throw notReady("FMP ID/name/remap tables are unavailable");
    if (ids.getClass() != java.lang.reflect.Array.newInstance(type("scala.Tuple2"), 0).getClass()
        || ((Object[]) ids).length > 65536) throw fault("Unadapted FMP ID table");
    Collections.addAll(bindings, registry, names, aliases, ids);
    JsonArray idRows = array(), nameRows = array(), aliasRows = array();
    for (Object tuple : (Object[]) ids) {
      Jobs.checkpoint();
      if (tuple == null || tuple.getClass() != type("scala.Tuple2"))
        throw fault("Unadapted FMP ID member");
      Object key = field(tuple, "_1"), material = field(tuple, "_2");
      if (!(key instanceof String) || material == null) throw fault("Invalid FMP ID member");
      Collections.addAll(bindings, tuple, material);
      idRows.add(value(key));
    }
    for (Map.Entry<String, Object> entry : FmpNativeSource.entries(names).entrySet()) {
      if (!(entry.getValue() instanceof Integer)) throw fault("Invalid FMP native name ID");
      int id = (Integer) entry.getValue();
      nameRows.add(object("name", entry.getKey(), "id", id));
      if (nameOut != null) nameOut.put(entry.getKey(), id);
    }
    for (Map.Entry<String, Object> entry : FmpNativeSource.entries(aliases).entrySet()) {
      if (!(entry.getValue() instanceof String)) throw fault("Invalid FMP native material alias");
      String target = (String) entry.getValue();
      aliasRows.add(object("name", entry.getKey(), "target", target));
      if (aliasOut != null) aliasOut.put(entry.getKey(), target);
    }
    return object(
        "idNames",
        idRows,
        "names",
        nameRows,
        "aliases",
        aliasRows,
        "aliasPolicy",
        "singleHop",
        "missingPolicy",
        "idZero",
        "missingCompoundPolicy",
        "ownedEmptyCompoundForNativeCall");
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "FMP material ID reader is closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    anchors.clear();
    names.clear();
    aliases.clear();
    records = null;
    signature = null;
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault notReady(String message) {
    return new Jobs.Fault("handler_not_ready", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault("recipe_changed", "FMP native ID/name/remap source or member changed");
  }
}
