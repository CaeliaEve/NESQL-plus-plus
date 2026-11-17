package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;

/**
 * Native Advanced definitions and complete finite candidate result states, without a NEI GUI
 * factory.
 */
final class Ic2AdvancedRecipes implements RegistryRecipes {
  private static final String HANDLERS = "ic2.neiIntegration.core.recipehandler.";
  private CraftingManager manager;
  private List<?> source;
  private final List<Object> order = new ArrayList<>();
  private final List<Entry> entries = new ArrayList<>();
  private boolean closed;

  static boolean supports(ICraftingHandler value) {
    return value != null && supportsClass(value.getClass().getName());
  }

  static boolean supportsClass(String name) {
    return (HANDLERS + "AdvRecipeHandler").equals(name)
        || (HANDLERS + "AdvShapelessRecipeHandler").equals(name);
  }

  static Object[] registrationAnchors(IRecipeHandler handler) {
    if (!supportsClass(handler.getClass().getName())) throw fault("Unknown IC2 Advanced handler");
    CraftingManager manager = manager();
    return new Object[] {handler.getClass(), manager, registry(manager)};
  }

  private static CraftingManager manager() {
    CraftingManager value = CraftingManager.getInstance();
    if (value == null) throw notReady("Native crafting manager is unavailable");
    if (value.getClass() != CraftingManager.class)
      throw fault("Unadapted native IC2 crafting manager");
    return value;
  }

  private static List<?> registry(CraftingManager manager) {
    List<?> source = manager.getRecipeList();
    if (source == null) throw notReady("Native IC2 crafting registry is unavailable");
    if (source.getClass() != ArrayList.class)
      throw fault("Unadapted native IC2 crafting registry storage");
    if (source.size() > 262144) throw fault("IC2 Advanced crafting registry exceeds budget");
    return source;
  }

  Ic2AdvancedRecipes(TemplateRecipeHandler handler) {
    if (!supports(handler)) throw fault("Unknown IC2 Advanced handler");
    try {
      manager = manager();
      source = registry(manager);
      order.addAll(source);
      boolean shapeless = handler.getClass().getName().endsWith("AdvShapelessRecipeHandler");
      Class<?> selected = type(shapeless ? "ic2.core.AdvShapelessRecipe" : "ic2.core.AdvRecipe");
      for (int index = 0; index < source.size(); index++) {
        Jobs.checkpoint();
        Object value = source.get(index);
        if (value == null) throw fault("Null native IC2 crafting registry member");
        if (selected.isInstance(value)) {
          if (value.getClass() != selected)
            throw fault("Unadapted native IC2 Advanced subclass: " + value.getClass().getName());
          entries.add(new Entry(value, index));
        }
      }
      finishVerification();
    } catch (RuntimeException | Error error) {
      close();
      throw error;
    }
  }

  public int size() {
    verify();
    return entries.size();
  }

  public void verify() {
    check();
    if (manager() != manager || registry(manager) != source || source.size() != order.size())
      throw changed();
  }

  public void finishVerification() {
    verify();
    for (int index = 0; index < order.size(); index++) {
      Jobs.checkpoint();
      if (source.get(index) != order.get(index)) throw changed();
    }
    for (Entry entry : entries) entry.verify();
  }

  public boolean capture(int index, RecipeRow row) {
    verify();
    Entry entry = entries.get(index);
    entry.verify();
    try {
      boolean result = entry.capture(row);
      entry.verify();
      verify();
      return result;
    } catch (RuntimeException | Error error) {
      try {
        entry.verify();
        verify();
      } catch (RuntimeException | Error drift) {
        if (drift != error) error.addSuppressed(drift);
      }
      throw error;
    }
  }

  public void close() {
    if (closed) return;
    closed = true;
    for (Entry entry : entries) entry.close();
    entries.clear();
    order.clear();
    source = null;
    manager = null;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "IC2 Advanced reader is closed");
  }

  private final class Entry implements AutoCloseable {
    private Object recipe;
    private final int nativeIndex;
    private final Ic2AdvancedVisibility visibility;
    private Ic2AdvancedLogic logic;
    private final Map<String, NativeItemVariants> variants = new LinkedHashMap<>();

    Entry(Object recipe, int index) {
      this.recipe = recipe;
      nativeIndex = index;
      visibility = new Ic2AdvancedVisibility(recipe);
    }

    void verify() {
      if (source.get(nativeIndex) != recipe) throw changed();
      visibility.verify();
      if (logic != null) logic.verify();
      for (NativeItemVariants variant : variants.values()) variant.verify();
    }

    boolean capture(RecipeRow row) {
      if (!visibility.shown())
        return row.exclude(
            object(
                "native",
                recipe.getClass().getName(),
                "nativeIndex",
                nativeIndex,
                "reason",
                "native_secret_recipe_hidden_by_config",
                "visibility",
                visibility.declaration()));
      if (logic == null) logic = new Ic2AdvancedLogic(recipe);
      Ic2AdvancedMasks masks = logic.matcher().masks();
      Ic2AdvancedInputs inputs = logic.matcher().inputs();
      List<List<ItemStack>> groups = new ArrayList<>();
      List<List<Ic2AdvancedStates.Candidate>> candidates = new ArrayList<>();
      JsonArray chargeRows = array();
      int totalCandidates = 0;
      Map<Item, Map<Integer, Integer>> matches = new IdentityHashMap<>();
      for (int group = 0; group < masks.size(); group++)
        for (ItemStack template : inputs.copies(group)) {
          Map<Integer, Integer> metadata =
              matches.computeIfAbsent(template.getItem(), unused -> new HashMap<>());
          int damage = template.getItemDamage();
          metadata.put(damage, metadata.getOrDefault(damage, 0) | (1 << group));
        }
      for (int group = 0; group < masks.size(); group++) {
        List<ItemStack> raw = inputs.copies(group);
        if (raw.isEmpty())
          return row.exclude(
              object(
                  "native",
                  recipe.getClass().getName(),
                  "nativeIndex",
                  nativeIndex,
                  "reason",
                  "empty_native_required_input_predicate",
                  "group",
                  group));
        LinkedHashMap<String, RecipeRow.Ingredient> unique = new LinkedHashMap<>();
        for (ItemStack template : raw) {
          boolean wildcard = template.getItemDamage() == 32767;
          ItemStack[] offered;
          if (wildcard) {
            String key = CanonicalJson.digest(RecipeRow.stackEvidence(template));
            NativeItemVariants bound = variants.get(key);
            if (bound == null) {
              bound = new NativeItemVariants(template);
              variants.put(key, bound);
            }
            offered = bound.copies();
          } else offered = new ItemStack[] {template.copy()};
          for (ItemStack item : offered) {
            NativeStackAudit.audit(item);
            item.stackSize = 1;
            JsonObject rule = object("kind", "wildcard", "meta", wildcard, "nbt", true);
            String key =
                CanonicalJson.digest(object("stack", RecipeRow.stackEvidence(item), "rule", rule));
            unique.putIfAbsent(key, new RecipeRow.Ingredient(item, 1, false, rule));
            if (unique.size() > Ic2AdvancedStates.LIMIT)
              throw fault("IC2 Advanced candidates exceed budget");
          }
        }
        List<RecipeRow.Ingredient> ingredients = new ArrayList<>(unique.values());
        row.nativeCraftingInput(group, ingredients);
        totalCandidates += ingredients.size();
        if (totalCandidates > Ic2AdvancedStates.LIMIT)
          throw fault("IC2 Advanced total candidates exceed budget");
        if (row.inputs.get(group).getAsJsonObject().getAsJsonArray("choices").size()
            != ingredients.size())
          throw new Jobs.Fault(
              "slot_changed", "IC2 Advanced candidate order/deduplication changed");
        List<ItemStack> stacks = new ArrayList<>();
        for (RecipeRow.Ingredient ingredient : ingredients) stacks.add(ingredient.item.copy());
        groups.add(stacks);
      }
      for (List<ItemStack> group : groups) {
        List<Ic2AdvancedStates.Candidate> prepared = new ArrayList<>();
        JsonArray values = array();
        for (ItemStack offered : group) {
          double charge = logic.charge(offered);
          Map<Integer, Integer> metadata = matches.get(offered.getItem());
          int matching =
              metadata.getOrDefault(offered.getItemDamage(), 0) | metadata.getOrDefault(32767, 0);
          prepared.add(new Ic2AdvancedStates.Candidate(offered, charge, matching));
          values.add(value(Ic2AdvancedLogic.bits(charge)));
        }
        candidates.add(prepared);
        chargeRows.add(values);
      }
      if (!masks.shapeless)
        row.record.add(
            "grid",
            object(
                "width",
                3,
                "height",
                3,
                "cells",
                masks.primaryCells(),
                "mirror",
                !masks.declaration().get("mirroredOrder").isJsonNull()));
      List<Ic2AdvancedStates.State> states =
          Ic2AdvancedStates.prepare(candidates, masks.placements(), masks.shapeless);
      if (states.isEmpty())
        throw new Jobs.Fault(
            "recipe_unrepresented",
            "IC2 Advanced candidates have no native greedy match; this is not an unreachable-recipe proof");
      JsonArray witnesses = array(), samples = array();
      ItemStack first = null;
      for (Ic2AdvancedStates.State state : states) {
        ItemStack[] cells = new ItemStack[9];
        for (int group = 0; group < groups.size(); group++)
          cells[state.positions[group]] = groups.get(group).get(state.choices[group]).copy();
        Ic2AdvancedLogic.Outcome outcome = logic.evaluate(cells);
        if (outcome == null || !outcome.total.equals(Ic2AdvancedLogic.bits(state.total)))
          throw new Jobs.Fault(
              "slot_changed", "IC2 Advanced finite witness differs from native rule");
        if (first == null) first = outcome.output;
        JsonArray choices = array(), positions = array();
        for (int choice : state.choices) choices.add(value(choice));
        for (int position : state.positions) positions.add(value(position));
        witnesses.add(object("choices", choices, "positions", positions));
        samples.add(stack(row, outcome.output));
      }
      row.itemOutput(0, first, 10000);
      row.outputs
          .get(0)
          .getAsJsonObject()
          .add(
              "change",
              object("input", 0, "action", object("kind", "ic2Advanced"), "samples", samples));
      row.record.add(
          "process",
          object(
              "kind",
              "ic2Advanced",
              "version",
              "2.2.828-experimental",
              "scope",
              "exportedCountOneCandidates",
              "nativeIndex",
              nativeIndex,
              "layout",
              masks.declaration(),
              "visibility",
              visibility.declaration(),
              "prototype",
              stack(row, (ItemStack) field(recipe, "output")),
              "charges",
              chargeRows,
              "states",
              witnesses));
      return true;
    }

    public void close() {
      if (logic != null) logic.close();
      visibility.close();
      variants.clear();
      recipe = null;
      logic = null;
    }
  }

  private static JsonObject stack(RecipeRow row, ItemStack stack) {
    return object("id", row.facts.item(stack), "amount", Integer.toString(stack.stackSize));
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault notReady(String message) {
    return new Jobs.Fault("handler_not_ready", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "IC2 Advanced native registry, instance or order changed");
  }
}
