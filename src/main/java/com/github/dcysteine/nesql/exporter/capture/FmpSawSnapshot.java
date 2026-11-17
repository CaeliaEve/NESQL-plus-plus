package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.*;

/** Read-only parameters of the installed native saw implementations, never Saw callbacks. */
final class FmpSawSnapshot implements AutoCloseable {
  private Item item;
  private final List<Object> anchors = new ArrayList<>();
  private final String signature;
  final int strength, durability;
  final boolean damageable;
  final String policy;

  FmpSawSnapshot(Item item) {
    this.item = item;
    JsonObject record = read(anchors);
    signature = CanonicalJson.digest(record);
    strength = record.get("strength").getAsInt();
    durability = record.get("durability").getAsInt();
    damageable = record.get("damageable").getAsBoolean();
    policy = record.get("nbt").getAsString();
  }

  Item item() {
    check();
    return item;
  }

  JsonObject declaration() {
    check();
    return signatureRecord();
  }

  private JsonObject signatureRecord() {
    return object(
        "registry",
        Item.itemRegistry.getNameForObject(item),
        "owner",
        item.getClass().getName(),
        "strength",
        strength,
        "maxStrength",
        strength,
        "durability",
        durability,
        "damageable",
        damageable,
        "nbt",
        policy);
  }

  private JsonObject read(List<Object> bindings) {
    String owner = item.getClass().getName(), nbt;
    int level;
    if (owner.equals("codechicken.microblock.ItemSaw")) {
      level = (Integer) field(item, "harvestLevel");
      nbt = "freshWhenDamageable";
    } else if (owner.equals("mrtjp.projectred.exploration.ItemGemSaw")) {
      MagicApi.version("FmpSawSnapshot","ProjRed|Exploration");
      Object tool = field(item, "toolDef");
      if (tool == null || tool.getClass() != type("mrtjp.projectred.exploration.ToolDefs$ToolDef"))
        throw fault("Unadapted native FMP gem saw definition");
      Object material = field(tool, "mat");
      if (material == null || material.getClass() != Item.ToolMaterial.class)
        throw fault("Unadapted native FMP gem saw material");
      Collections.addAll(bindings, tool, material);
      level = ((Item.ToolMaterial) material).getHarvestLevel();
      nbt = "preserve";
    } else throw fault("Unadapted FMP saw callback owner: " + owner);
    // Both exact implementations inherit these getters from Item. This also
    // binds native container damage behaviour without executing it on borrowed stacks.
    for (String[] method :
        new String[][] {{"getMaxDamage", "func_77612_l"}, {"isDamageable", "func_77645_m"}}) {
      if (ItemCallbacks.method(item, method[0], method[1]).getDeclaringClass() != Item.class)
        throw fault("Unadapted FMP saw durability callback");
    }
    int maxDamage = item.getMaxDamage();
    if (Item.itemRegistry.getNameForObject(item) == null) throw fault("Unregistered FMP saw");
    return object(
        "registry",
        Item.itemRegistry.getNameForObject(item),
        "owner",
        owner,
        "strength",
        level,
        "maxStrength",
        level,
        "durability",
        maxDamage,
        "damageable",
        item.isDamageable(),
        "nbt",
        nbt);
  }

  void verify() {
    check();
    List<Object> current = new ArrayList<>();
    JsonObject record;
    try {
      record = read(current);
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (!signature.equals(CanonicalJson.digest(record)) || current.size() != anchors.size())
      throw changed();
    for (int index = 0; index < anchors.size(); index++)
      if (current.get(index) != anchors.get(index)) throw changed();
  }

  ItemStack remainder(ItemStack source) {
    check();
    NativeStackAudit.audit(source);
    if (source.getItem() != item) throw fault("FMP saw remainder belongs to another item");
    ItemStack result;
    if (policy.equals("freshWhenDamageable") && damageable)
      result = new ItemStack(item, 1, source.getItemDamage() + 1);
    else {
      result = source.copy();
      if (policy.equals("preserve")) result.setItemDamage(source.getItemDamage() + 1);
    }
    return result;
  }

  private void check() {
    Jobs.checkpoint();
    if (item == null) throw new Jobs.Fault("capture_closed", "FMP saw snapshot is closed");
  }

  public void close() {
    item = null;
    anchors.clear();
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault("recipe_changed", "FMP saw parameters or definition changed");
  }
}
