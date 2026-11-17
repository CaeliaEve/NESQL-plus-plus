package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import java.util.*;
import net.minecraft.item.ItemStack;

/** Pinned Witchery distillation: unit reagents, consumed jars and independent output slots. */
final class DistilleryRecipes implements RegistryRecipes {
  private static final String HANDLER = "alkalus.main.nei.NEI_Handler_Distillery";
  private DistillerySource nativeSource;
  private List<DistillerySource.Rule> source;
  private ItemStack jar;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  DistilleryRecipes(TemplateRecipeHandler handler) {
    this(handler, productionSource());
  }

  private static DistillerySource productionSource() {
    MagicApi.version("DistilleryRecipes","witchery");
    MagicApi.version("DistilleryRecipes","WitcheryExtras");
    return new DistillerySource();
  }

  DistilleryRecipes(TemplateRecipeHandler handler, List<?> table, ItemStack jar) {
    this(handler, new DistillerySource(table, jar));
  }

  private DistilleryRecipes(TemplateRecipeHandler handler, DistillerySource nativeSource) {
    this.nativeSource = nativeSource;
    try {
      if (!supports(handler)) throw fault("Unknown distillery handler");
      this.source = nativeSource.rules;
      this.jar = nativeSource.jar;
      if (source.size() * (long) source.size() > 4194304)
        throw new Jobs.Fault("source_budget", "Distillery native priority context exceeds budget");
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  public int size() {
    check();
    nativeSource.verifyRoots();
    return source.size();
  }

  public void verify() {
    check();
    nativeSource.verifyRoots();
  }

  public void finishVerification() {
    check();
    nativeSource.verify();
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Distillery reader closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (nativeSource != null) nativeSource.close();
    nativeSource = null;
    source = null;
    jar = null;
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    nativeSource.verifyRoots();
    DistillerySource.Rule rule = source.get(index);
    ItemStack[] inputs = rule.inputs, outputs = rule.outputs;
    int jars = rule.jars;
    if (inputs[0] == null && inputs[1] == null)
      return row.exclude(
          object(
              "kind",
              "native",
              "owner",
              "witchery",
              "source",
              "com.emoniph.witchery.blocks.BlockDistillery$TileEntityDistillery.getActiveRecipe",
              "reason",
              "native_empty_reagent_inventory_has_no_active_recipe",
              "index",
              index));
    for (int i = 0; i < 2; i++)
      if (inputs[i] != null) {
        ItemStack item = inputs[i].copy();
        item.stackSize = 1;
        item.setTagCompound(null);
        boolean meta = !rule.subtypes[i];
        if (meta) item.setItemDamage(0);
        row.itemInput(i,
            Collections.singletonList(
                new RecipeRow.Ingredient(
                    item, 1, false, object("kind", "wildcard", "meta", meta, "nbt", true))),
            false);
      }
    if (jars != 0) {
      ItemStack item = jar.copy();
      item.stackSize = Math.max(jars, 1);
      row.itemInput(2,
          Collections.singletonList(
              new RecipeRow.Ingredient(
                  item, Math.max(jars, 1), false, object("kind", "distilleryJar"))),
          false);
    }
    for (int i = 0; i < 4; i++)
      if (outputs[i] != null && outputs[i].stackSize > 0) {
        ItemStack item = outputs[i].copy();
        row.itemOutput(i,
            item,
            10000,
            Amounts.potential(
                "witchery:distilleryOutputState",
                "nativeDistillerySlotAccepts",
                Integer.toString(item.stackSize),
                item.stackSize));
      }
    for (com.google.gson.JsonElement input : row.inputs)
      for (com.google.gson.JsonElement choice : input.getAsJsonObject().getAsJsonArray("choices"))
        choice.getAsJsonObject().add("consume", object("kind", "allocated"));
    JsonArray prior = array(), outputsDefinition = array();
    for (int previous = 0; previous < index; previous++)
      prior.add(definition(source.get(previous), row));
    for (ItemStack output : outputs)
      outputsDefinition.add(
          output == null
              ? value(null)
              : object("id", row.facts.item(output), "amount", Integer.toString(output.stackSize)));
    row.record.add(
        "process",
        object(
            "kind",
            "witcheryDistillery",
            "nativeTile",
            "com.emoniph.witchery.blocks.BlockDistillery$TileEntityDistillery",
            "nativeIndex",
            index,
            "nativeCount",
            source.size(),
            "reagents",
            definition(rule, row).getAsJsonArray("reagents"),
            "jars",
            jars,
            "jar",
            row.facts.item(jar),
            "outputs",
            outputsDefinition,
            "earlier",
            prior,
            "matching",
            "subtypeMetadataIgnoreCountAndTagsEitherOrder",
            "jarPolicy",
            "runtimeCountOnlyClayJarAdmission",
            "debit",
            "oneEachOccupiedSignedJarDeltaNoContainers",
            "outputPolicy",
            "emptyCopyMatchingMergeKeepsTagsMismatchedSkipThenDebit",
            "workPolicy",
            "successTicksPauseOnPowerResetBlockedCarryRecipeChanges",
            "cookTicks",
            800,
            "powerPerWorkTickBits",
            "3f19999a"));
    row.note(
        "witchery:distillery",
        "com.emoniph.witchery.blocks.BlockDistillery$TileEntityDistillery.smeltItem",
        "The first native matching recipe wins, including its jar-count threshold; swapped reagents are allowed and null slots must be empty. Each occupied reagent loses one item and jars are adjusted by the signed native count, without container returns. Each empty output slot copies its prototype, matching item/meta slots add counts while preserving existing NBT, and incompatible slots skip placement while inputs and jars are still debited. Capacity checks only matching occupied outputs and uses native signed-int addition. Requires 800 successful altar-power ticks at native float0.6 per tick; missing power pauses progress, blocked processing resets it, and eligible recipe changes can carry existing progress. Output records are conditional prototypes, not guaranteed inventory results.");
    return true;
  }

  private com.google.gson.JsonObject definition(DistillerySource.Rule rule, RecipeRow row) {
    JsonArray reagents = array();
    for (int slot = 0; slot < 2; slot++) {
      ItemStack raw = rule.inputs[slot];
      if (raw == null) {
        reagents.add(value(null));
        continue;
      }
      ItemStack item = raw.copy();
      item.stackSize = 1;
      item.setTagCompound(null);
      if (!rule.subtypes[slot]) item.setItemDamage(0);
      reagents.add(
          object(
              "item", row.facts.item(item), "metadata", rule.subtypes[slot] ? "exact" : "ignored"));
    }
    return object("reagents", reagents, "jars", rule.jars);
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
