package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.ItemStack;

/** Audited Advanced result on owned stacks, preserving native row-major double arithmetic. */
final class Ic2AdvancedLogic implements AutoCloseable {
  private Object recipe;
  private ItemStack output;
  private String outputSignature;
  private Ic2AdvancedMatch matcher;
  private final Map<String, GtElectricBindings> electric = new LinkedHashMap<>();

  Ic2AdvancedLogic(Object recipe) {
    try {
      this.recipe = recipe;
      matcher = new Ic2AdvancedMatch(recipe);
      Object raw = field(recipe, "output");
      if (!(raw instanceof ItemStack))
        throw new Jobs.Fault("recipe_unsupported", "Missing native IC2 Advanced output");
      output = (ItemStack) raw;
      NativeStackAudit.audit(output);
      outputSignature = CanonicalJson.digest(RecipeRow.stackEvidence(output));
      verify();
    } catch (RuntimeException | Error error) {
      close();
      throw error;
    }
  }

  Outcome evaluate(ItemStack[] cells) {
    verify();
    int[] mapping = matcher.match(cells);
    if (mapping == null) return null;
    double total = 0;
    JsonArray charges = array();
    for (ItemStack cell : cells) {
      if (cell == null) charges.add(value(null));
      else {
        double charge = binding(cell, false).advancedCharge();
        charges.add(value(bits(charge)));
        total += charge;
      }
    }
    ItemStack result = binding(output, true).advancedResult(total);
    NativeStackAudit.audit(result);
    verify();
    return new Outcome(mapping, charges, bits(total), result);
  }

  private GtElectricBindings binding(ItemStack stack, boolean result) {
    String key = result + ":" + CanonicalJson.digest(RecipeRow.stackEvidence(stack));
    GtElectricBindings prior = electric.get(key);
    if (prior != null) {
      prior.verify();
      return prior;
    }
    GtElectricBindings next = GtElectricBindings.ic2Advanced(stack, result);
    electric.put(key, next);
    return next;
  }

  double charge(ItemStack offered) {
    check();
    return binding(offered, false).advancedCharge();
  }

  Ic2AdvancedMatch matcher() {
    check();
    return matcher;
  }

  JsonObject declaration() {
    check();
    return object(
        "layout",
        matcher.masks().declaration(),
        "nativeInputs",
        matcher.inputs().declaration(),
        "output",
        RecipeRow.stackEvidence(output),
        "matching",
        "exactOrCandidateWildcardIgnoreNbtCount",
        "shapelessOrder",
        "rowMajorFirstRemainingGroup",
        "consumePerOccupiedCell",
        1,
        "electric",
        "rowMajorDoubleGetChargeThenAddToTemplate",
        "fluidOutputNbt",
        "originalItemRequiresCompound");
  }

  void verify() {
    check();
    matcher.verify();
    if (field(recipe, "output") != output
        || !outputSignature.equals(CanonicalJson.digest(RecipeRow.stackEvidence(output))))
      throw new Jobs.Fault("recipe_changed", "Native IC2 Advanced result template changed");
    for (GtElectricBindings binding : electric.values()) binding.verify();
  }

  private void check() {
    Jobs.checkpoint();
    if (recipe == null)
      throw new Jobs.Fault("capture_closed", "IC2 Advanced result reader is closed");
  }

  public void close() {
    if (matcher != null) matcher.close();
    matcher = null;
    recipe = null;
    output = null;
    outputSignature = null;
    electric.clear();
  }

  static String bits(double value) {
    return String.format(java.util.Locale.ROOT, "%016x", Double.doubleToRawLongBits(value));
  }

  static final class Outcome {
    final int[] mapping;
    final JsonArray charges;
    final String total;
    final ItemStack output;

    Outcome(int[] mapping, JsonArray charges, String total, ItemStack output) {
      this.mapping = mapping;
      this.charges = charges;
      this.total = total;
      this.output = output;
    }
  }
}
