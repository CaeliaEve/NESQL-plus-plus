package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.block.Block;

/** Pinned FMP transitive block-harvest getters. Unknown owners are never invoked. */
final class NativeHarvest implements AutoCloseable {
  private Block block;
  private final int metadata;
  private final List<Object> anchors = new ArrayList<>();
  private final String signature;
  private final int level;

  NativeHarvest(Block block, int metadata) {
    if (block == null) throw fault("Missing native harvest block");
    this.block = block;
    this.metadata = metadata;
    JsonObject facts = read(anchors);
    signature = CanonicalJson.digest(facts);
    level = facts.get("level").getAsInt();
  }

  int level() {
    verify();
    return level;
  }

  void verify() {
    if (block == null) throw new Jobs.Fault("capture_closed", "Native harvest binding is closed");
    Jobs.checkpoint();
    List<Object> current = new ArrayList<>();
    JsonObject facts;
    try {
      facts = read(current);
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (!signature.equals(CanonicalJson.digest(facts)) || current.size() != anchors.size())
      throw changed();
    for (int index = 0; index < anchors.size(); index++)
      if (anchors.get(index) != current.get(index)) throw changed();
  }

  private JsonObject read(List<Object> bindings) {
    bindings.add(block);
    String owner;
    try {
      Method method = block.getClass().getMethod("getHarvestLevel", int.class);
      owner = method.getDeclaringClass().getName();
    } catch (NoSuchMethodException error) {
      throw fault("Missing native block harvest getter");
    }
    int result;
    if (owner.equals(Block.class.getName())) {
      Object values = field(Block.class, block, "harvestLevel");
      if (values == null) {
        // NotEnoughIDs replaces the vanilla array and getter together; null alone is not a default.
        MagicApi.version("NativeHarvest","neid");
        Object map = field(Block.class, block, "harvestLevelMap");
        Class<?> expected = type("it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap");
        if (map == null || map.getClass() != expected) throw fault("Unadapted NotEnoughIDs harvest storage");
        bindings.add(map);
        int fallback = (Integer) field(Block.class, block, "neid$defaultHarvestLevel");
        result = (Integer) invoke(expected, map, "getOrDefault", new Class<?>[] {int.class, int.class}, metadata, fallback);
        return object("owner", owner, "metadata", metadata, "level", result, "default", fallback);
      }
      if (values.getClass() != int[].class || ((int[]) values).length > 65536)
        throw fault("Invalid native block harvest-level storage");
      bindings.add(values);
      if (metadata < 0 || metadata >= ((int[]) values).length)
        throw fault("Native block harvest metadata is outside its array");
      result = ((int[]) values)[metadata];
    } else {
      if (!owner.startsWith("gregtech.common.blocks."))
        throw fault("Unadapted native block harvest callback: " + owner);
      MagicApi.version("NativeHarvest","gregtech");
      MagicApi.version("NativeHarvest","gregtech_nh");
      switch (owner.substring("gregtech.common.blocks.".length())) {
        case "BlockStorage":
        case "BlockStonesAbstract":
          result = 1;
          break;
        case "BlockCasingsAbstract":
        case "BlockStones":
          result = 2;
          break;
        case "BlockGranites":
        case "BlockFrameBox":
          result = 3;
          break;
        case "BlockOresAbstract":
          result = metadata == 5 || metadata == 6 ? 2 : metadata % 8;
          break;
        case "BlockReinforced":
          switch (metadata) {
            case 4:
            case 5:
            case 6:
            case 7:
              result = 1;
              break;
            case 2:
              result = 2;
              break;
            case 1:
            case 3:
            case 9:
              result = 5;
              break;
            case 10:
            case 11:
              result = 7;
              break;
            default:
              result = 4;
          }
          break;
        case "BlockCasings4":
          if (metadata != 15) {
            result = 2;
            break;
          }
          Object entry = field(type("gregtech.api.enums.HarvestTool"), null, "PickaxeLevel2");
          if (entry == null || entry.getClass() != type("gregtech.api.enums.HarvestTool"))
            throw fault("Unadapted native GT harvest-tool constant");
          bindings.add(entry);
          result = (Integer) field(entry, "harvestLevel");
          break;
        default:
          throw fault("Unadapted native GT block harvest callback: " + owner);
      }
    }
    return object("owner", owner, "metadata", metadata, "level", result);
  }

  public void close() {
    block = null;
    anchors.clear();
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault("recipe_changed", "Native block harvest parameters or source changed");
  }
}
