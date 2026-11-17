package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Pinned NEI content variants. No PositionedStack, GUI, name or item callbacks. */
final class NativeItemVariants {
  private final Item item;
  private final List<ItemStack> samples = new ArrayList<>();
  private final List<Object> anchors = new ArrayList<>();
  private final String signature;
  private final boolean observedCandidates;

  NativeItemVariants(ItemStack template) {
    if (template == null || template.getClass() != ItemStack.class)
      throw fault("Unadapted native wildcard template stack");
    item = template.getItem();
    if (item == null) throw fault("Native wildcard item is unavailable");
    NativeStackAudit.auditRaw(template);
    JsonArray observed = read(anchors, samples);
    observedCandidates = observed.size() > 0;
    signature = CanonicalJson.digest(observed);
  }

  ItemStack[] copies() {
    ItemStack[] result = new ItemStack[samples.size()];
    for (int index = 0; index < result.length; index++) result[index] = samples.get(index).copy();
    return result;
  }
  /** Rules which have no native zero-variant fallback must not invent one. */
  ItemStack[] observedCopies() {
    return observedCandidates ? copies() : new ItemStack[0];
  }
  boolean hasObservedCandidates(){return observedCandidates;}

  ItemStack firstObserved() {
    return observedCandidates ? samples.get(0).copy() : null;
  }

  void verifyRoot() {
    Jobs.checkpoint();
    if (!ItemList.loadFinished
        || ItemList.itemMap != anchors.get(0)
        || field(ItemList.itemMap, "map") != anchors.get(1)) throw changed();
  }

  void verify() {
    List<Object> actual = new ArrayList<>();
    JsonArray facts;
    try {
      facts = read(actual, null);
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (!signature.equals(CanonicalJson.digest(facts)) || anchors.size() != actual.size())
      throw changed();
    for (int index = 0; index < anchors.size(); index++)
      if (anchors.get(index) != actual.get(index)) throw changed();
  }

  private JsonArray read(List<Object> bindings, List<ItemStack> output) {
    Jobs.checkpoint();
    if (!ItemList.loadFinished || ItemList.itemMap == null)
      throw new Jobs.Fault(
          "handler_not_ready", "Wildcard candidates require a finished native item list");
    if (ItemList.itemMap.getClass() != com.google.common.collect.ArrayListMultimap.class)
      throw fault("Unadapted native item-map storage");
    try {
      if (item.getClass().getMethod("hashCode").getDeclaringClass() != Object.class
          || item.getClass().getMethod("equals", Object.class).getDeclaringClass() != Object.class)
        throw fault("Wildcard item requires an audited key predicate");
    } catch (NoSuchMethodException error) {
      throw fault("Missing native item key predicate");
    }
    bindings.add(ItemList.itemMap);
    Object map = field(ItemList.itemMap, "map");
    bindings.add(map);
    if (map == null || map.getClass() != HashMap.class)
      throw fault("Unadapted native item-map backing table");
    Object values = ((Map<?, ?>) map).get(item);
    bindings.add(values);
    if (values != null && values.getClass() != ArrayList.class)
      throw fault("Unadapted native item-map candidates");
    List<?> candidates = values == null ? Collections.emptyList() : (List<?>) values;
    if (candidates.size() > 65536)
      throw new Jobs.Fault("recipe_limit", "Native wildcard candidates exceed budget");
    JsonArray facts = array();
    for (Object value : candidates) {
      Jobs.checkpoint();
      if (value == null || value.getClass() != ItemStack.class)
        throw fault("Unadapted native candidate stack callback");
      ItemStack stack = (ItemStack) value;
      NativeStackAudit.auditRaw(stack);
      int metadata = net.minecraft.init.Items.feather.getDamage(stack);
      if (stack.getItem() != item || metadata == 32767)
        throw new Jobs.Fault(
            "slot_changed",
            "Wildcard candidate substituted its native item or remained a wildcard");
      bindings.add(stack);
      bindings.add(stack.getItem());
      bindings.add(stack.getTagCompound());
      facts.add(RecipeRow.stackEvidence(stack));
      if (output != null) output.add(stack.copy());
    }
    // The pinned native permutation branch uses metadata zero only when its
    // finished table has no variants. Missing readiness must never use it.
    if (output != null && output.isEmpty()) output.add(new ItemStack(item, 1, 0));
    return facts;
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "Native wildcard source, readiness, member or facts changed");
  }
}
