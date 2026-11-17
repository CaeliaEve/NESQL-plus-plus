package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import thaumcraft.api.aspects.*;

/** Definition-only costs. Never invoke Gadomancy's RNG-backed AspectList getters. */
final class InfusionCostRules {
  static final String RANDOM = "makeo.gadomancy.common.utils.RandomizedAspectList";
  private InfusionCostRules() {}

  static JsonObject random(AspectList list) {
    if (list != null && list.getClass() == AspectList.class) return null;
    if (list == null || list.getClass() != type(RANDOM)) throw fault("Unknown native infusion cost owner");
    MagicApi.version("InfusionCostRules","gadomancy");
    Object raw = field(list, "aspectMap");
    if (raw == null || raw.getClass() != HashMap.class || ((Map<?,?>)raw).size() > 4096)
      throw fault("Unadapted randomized aspect base table");
    boolean half = (Boolean)field(list, "halfCap");
    SortedMap<String,Integer> bases = new TreeMap<>();
    for (Map.Entry<?,?> entry : ((Map<?,?>)raw).entrySet()) {
      // The native refresh explicitly skips a null aspect before reading its base.
      if (entry.getKey() == null) continue;
      if (!(entry.getKey() instanceof Aspect) || !(entry.getValue() instanceof Integer))
        throw fault("Invalid randomized aspect definition");
      Aspect aspect = (Aspect)entry.getKey();
      if (aspect.getClass() != Aspect.class && !Aspects.nativeDefinition(aspect))
        throw fault("Unadapted randomized aspect callback owner");
      int base = (Integer)entry.getValue();
      if ((half ? base / 2 : base) <= 0) throw fault("Native randomized aspect has a nonpositive RNG bound");
      if (bases.put(Aspects.id(aspect), base) != null) throw fault("Duplicate randomized aspect identity");
    }
    JsonArray rows = array();
    for (Map.Entry<String,Integer> base : bases.entrySet())
      rows.add(object("aspect", base.getKey(), "amount", Integer.toString(base.getValue())));
    return object("kind", "gadomancy", "semantics", "gadomancyRandomCostsV1", "halfCap", half,
        "refreshMillis", 500, "bases", rows);
  }

  static void shapes(Map<String,String[]> shapes, JsonObject random) {
    // lastRandomization and inherited aspects are derived UI/RNG caches.
    if (random != null) shapes.put(RANDOM, new String[]{"aspectMap", "halfCap"});
  }
  private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
