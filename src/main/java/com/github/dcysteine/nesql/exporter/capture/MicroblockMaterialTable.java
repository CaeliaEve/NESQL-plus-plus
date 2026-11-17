package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.*;

/** Read-only FMP item/name facts. Consumers which never cut do not inspect harvest or saws. */
final class MicroblockMaterialTable implements AutoCloseable {
  private FmpMaterialIds lookup;
  private final List<Material> materials = new ArrayList<>();
  private final Map<Integer, JsonArray> known = new HashMap<>();
  private final IdentityHashMap<Item, Map<Integer, Integer>> firstBlocks = new IdentityHashMap<>();

  MicroblockMaterialTable() {
    try {
      lookup = new FmpMaterialIds();
      Object registry =
          field(type("codechicken.microblock.MicroMaterialRegistry$"), null, "MODULE$");
      Object[] ids =
          (Object[]) field(registry, "codechicken$microblock$MicroMaterialRegistry$$idMap");
      for (int id = 0; id < ids.length; id++) {
        Jobs.checkpoint();
        Object tuple = ids[id];
        FmpMaterialSnapshot snapshot = new FmpMaterialSnapshot(field(tuple, "_2"), false);
        materials.add(new Material(id, (String) field(tuple, "_1"), snapshot));
      }
      for (Material material : materials) {
        if (material.block.getTagCompound() != null)
          throw fault("Audited FMP block item unexpectedly has NBT");
        firstBlocks
            .computeIfAbsent(material.block.getItem(), ignored -> new HashMap<>())
            .putIfAbsent(material.block.getItemDamage(), lookup.resolveName(material.name));
      }
      JsonObject rows = lookup.records();
      TreeSet<String> keys = new TreeSet<>();
      for (JsonElement row : rows.getAsJsonArray("names"))
        keys.add(row.getAsJsonObject().get("name").getAsString());
      for (JsonElement row : rows.getAsJsonArray("aliases"))
        keys.add(row.getAsJsonObject().get("name").getAsString());
      for (String key : keys)
        known.computeIfAbsent(lookup.resolveName(key), ignored -> array()).add(value(key));
      finishVerification();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  List<Material> materials() {
    check();
    return Collections.unmodifiableList(materials);
  }

  int resolveName(String name) {
    check();
    return lookup.resolveName(name);
  }

  int resolve(ItemStack stack) {
    check();
    return lookup.resolve(stack);
  }

  int find(ItemStack stack) {
    check();
    NativeStackAudit.audit(stack);
    try {
      if (stack.getItem().getClass().getMethod("equals", Object.class).getDeclaringClass()
          != Object.class) throw fault("Unadapted native full-material item equality callback");
    } catch (NoSuchMethodException failure) {
      throw fault("Missing native item equality method");
    }
    if (stack.getTagCompound() != null) return -1;
    Map<Integer, Integer> metadata = firstBlocks.get(stack.getItem());
    return metadata == null ? -1 : metadata.getOrDefault(stack.getItemDamage(), -1);
  }

  JsonObject declaration() {
    check();
    JsonObject ids = lookup.records();
    JsonArray rows = array();
    for (Material material : materials)
      rows.add(
          object(
              "id",
              material.id,
              "name",
              material.name,
              "block",
              RecipeRow.stackEvidence(material.block)));
    return object(
        "materials",
        rows,
        "names",
        ids.get("names"),
        "aliases",
        ids.get("aliases"),
        "aliasPolicy",
        "singleHop",
        "missingPolicy",
        "idZero");
  }

  JsonArray knownNames(int id) {
    check();
    JsonArray result = known.get(id);
    return result == null ? array() : new JsonParser().parse(result.toString()).getAsJsonArray();
  }

  Set<Integer> knownIds() {
    check();
    return Collections.unmodifiableSet(known.keySet());
  }

  void verify() {
    check();
    for (Material material : materials) material.snapshot.verify();
  }

  void finishVerification() {
    verify();
    lookup.verify();
  }

  private void check() {
    Jobs.checkpoint();
    if (lookup == null)
      throw new Jobs.Fault("capture_closed", "Native microblock material table is closed");
  }

  public void close() {
    for (Material material : materials) material.snapshot.close();
    materials.clear();
    known.clear();
    firstBlocks.clear();
    if (lookup != null) lookup.close();
    lookup = null;
  }

  static final class Material {
    final int id;
    final String name;
    final ItemStack block;
    final FmpMaterialSnapshot snapshot;

    Material(int id, String name, FmpMaterialSnapshot snapshot) {
      this.id = id;
      this.name = name;
      this.snapshot = snapshot;
      block = snapshot.stack();
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
