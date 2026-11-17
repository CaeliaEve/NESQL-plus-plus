package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.item.crafting.CraftingManager;

/** Exact registered XU template sources; observing a recipe never opens its NEI handler. */
final class XuMicroblockSource implements AutoCloseable {
  static final String RECIPE = "com.rwtema.extrautils.multipart.microblock.RecipeMicroBlocks";
  private CraftingManager manager;
  private List<?> crafting;
  private NativeDefinitions definitions;
  private final List<Entry> entries = new ArrayList<>();
  private final IdentityHashMap<Object, Entry> byRecipe = new IdentityHashMap<>();

  XuMicroblockSource() {
    try {
      Object[] roots = registrationRoots();
      manager = (CraftingManager) roots[0];
      crafting = (List<?>) roots[1];
      for (int index = 0; index < crafting.size(); index++) {
        Jobs.checkpoint();
        Object recipe = crafting.get(index);
        if (recipe != null && recipe.getClass().getName().equals(RECIPE)) {
          Entry entry = new Entry(recipe, index);
          entries.add(entry);
          byRecipe.put(recipe, entry);
          if (entries.size() > 4096) throw fault("XU native template registry exceeds budget");
        }
      }
      definitions =
          new NativeDefinitions(
              "XU microblocks",
              crafting,
              this::current,
              raw -> {
                Entry entry = byRecipe.get(raw);
                return entry == null
                    ? object("owner", raw == null ? null : raw.getClass().getName())
                    : entry.definition();
              });
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static Object[] registrationRoots() {
    CraftingManager manager = CraftingManager.getInstance();
    if (manager == null || manager.getRecipeList() == null)
      throw new Jobs.Fault("handler_not_ready", "XU crafting registry is unavailable");
    if (manager.getClass() != CraftingManager.class
        || manager.getRecipeList().getClass() != ArrayList.class)
      throw fault("Unadapted XU crafting registry storage");
    if (manager.getRecipeList().size() > 262144) throw fault("XU crafting registry exceeds budget");
    return new Object[] {manager, manager.getRecipeList()};
  }

  static Object[] registrationAnchors() {
    Object[] roots = registrationRoots();
    for (Object recipe : (List<?>) roots[1])
      if (recipe != null
          && recipe.getClass().getName().equals(RECIPE)
          && (Integer) field(recipe, "recipeWidth") <= 3
          && (Integer) field(recipe, "recipeHeight") <= 3) {
        try (XuMicroblockItem micro = new XuMicroblockItem()) {
          return new Object[] {roots[0], roots[1], Item.itemRegistry, micro.item()};
        }
      }
    return roots;
  }

  private List<?> current() {
    check();
    Object[] actual;
    try {
      actual = registrationRoots();
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (actual[0] != manager || actual[1] != crafting) throw changed();
    return crafting;
  }

  List<Entry> entries() {
    check();
    return Collections.unmodifiableList(entries);
  }

  void verify() {
    check();
    definitions.verify();
    for (Entry entry : entries) definitions.record(entry.index);
  }

  void verify(Entry entry) {
    check();
    definitions.record(entry.index);
  }

  void finishVerification() {
    check();
    definitions.finishVerification();
  }

  private void check() {
    Jobs.checkpoint();
    if (manager == null) throw new Jobs.Fault("capture_closed", "XU template source is closed");
  }

  public void close() {
    if (definitions != null) definitions.close();
    definitions = null;
    entries.clear();
    byRecipe.clear();
    manager = null;
    crafting = null;
  }

  static final class Entry {
    final Object recipe;
    final int index, width, height;
    final Object[] nativeCells;
    final ItemStack nativeOutput;
    final Item outputItem;
    final IdentityHashMap<ItemStack, Item> items = new IdentityHashMap<>();
    final List<Object> members = new ArrayList<>();

    Entry(Object recipe, int index) {
      if (recipe.getClass() != type(RECIPE)) throw fault("Unadapted XU native template subclass");
      this.recipe = recipe;
      this.index = index;
      width = (Integer) field(recipe, "recipeWidth");
      height = (Integer) field(recipe, "recipeHeight");
      Object cells = field(recipe, "recipeItems"), output = field(recipe, "recipeOutput");
      if (cells == null || cells.getClass() != Object[].class || ((Object[]) cells).length > 65536)
        throw fault("Unadapted XU native template cells");
      if (!(output instanceof ItemStack)) throw fault("XU native output is unavailable");
      nativeCells = (Object[]) cells;
      nativeOutput = (ItemStack) output;
      NativeStackAudit.audit(nativeOutput);
      outputItem = nativeOutput.getItem();
      Collections.addAll(members, nativeCells);
      for (Object cell : nativeCells)
        if (cell != null && cell.getClass() == ItemStack.class) {
          NativeStackAudit.audit((ItemStack) cell);
          items.put((ItemStack) cell, ((ItemStack) cell).getItem());
        }
      definition();
    }

    JsonObject definition() {
      if (field(recipe, "recipeItems") != nativeCells
          || field(recipe, "recipeOutput") != nativeOutput
          || (Integer) field(recipe, "recipeWidth") != width
          || (Integer) field(recipe, "recipeHeight") != height
          || nativeOutput.getItem() != outputItem) throw changed();
      JsonArray cells = array();
      for (int index = 0; index < nativeCells.length; index++) {
        Jobs.checkpoint();
        Object cell = nativeCells[index];
        if (cell != members.get(index)) throw changed();
        if (cell == null) cells.add(JsonNull.INSTANCE);
        else if (cell.getClass() == Integer.class) cells.add(value(cell));
        else if (cell.getClass() == ItemStack.class) {
          NativeStackAudit.audit((ItemStack) cell);
          if (((ItemStack) cell).getItem() != items.get((ItemStack) cell)) throw changed();
          cells.add(RecipeRow.stackEvidence((ItemStack) cell));
        } else throw fault("Unadapted XU native template cell owner: " + cell.getClass().getName());
      }
      NativeStackAudit.audit(nativeOutput);
      return object(
          "width",
          width,
          "height",
          height,
          "cells",
          cells,
          "output",
          RecipeRow.stackEvidence(nativeOutput));
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "XU native template source, array, output or member changed");
  }
}
