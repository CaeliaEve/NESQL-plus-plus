package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/** Native compact input order and absolute occupancy masks; never NEI GUI geometry. */
final class Ic2AdvancedMasks implements AutoCloseable {
  private Object recipe;
  final boolean shapeless;
  final int width, height;
  private final Object[] inputs, mirroredInputs;
  private final int[] masks, mirroredMasks, mirroredOrder;
  private Object[] originalInputs, originalMirroredInputs;
  private int[] originalMasks, originalMirroredMasks;

  Ic2AdvancedMasks(Object recipe) {
    String name = recipe == null ? "" : recipe.getClass().getName();
    if (!name.equals("ic2.core.AdvRecipe") && !name.equals("ic2.core.AdvShapelessRecipe"))
      throw fault("Unadapted IC2 crafting rule class: " + name);
    this.recipe = recipe;
    shapeless = name.endsWith("AdvShapelessRecipe");
    Object source = field(recipe, "input");
    if (source == null)
      throw new Jobs.Fault("handler_not_ready", "IC2 advanced input definitions are unavailable");
    if (source.getClass() != Object[].class) throw fault("Unadapted IC2 native input array");
    originalInputs = (Object[]) source;
    inputs = originalInputs.clone();
    if (inputs.length == 0 || inputs.length > 9)
      throw fault("IC2 advanced input count is outside the native workbench");
    for (Object input : inputs) if (input == null) throw fault("Null IC2 compact input predicate");
    if (shapeless) {
      width = height = 0;
      originalMasks = originalMirroredMasks = masks = mirroredMasks = mirroredOrder = null;
      originalMirroredInputs = mirroredInputs = null;
    } else {
      width = (Integer) field(recipe, "inputWidth");
      height = (Integer) field(recipe, "inputHeight");
      if (width < 1 || width > 3 || height < 1 || height > 3)
        throw fault("Invalid IC2 native recipe dimensions");
      originalMasks = nativeMasks(field(recipe, "masks"));
      masks = originalMasks.clone();
      Object mirrored = field(recipe, "inputMirrored"),
          mirroredLayout = field(recipe, "masksMirrored");
      if ((mirrored == null) != (mirroredLayout == null))
        throw fault("IC2 mirrored input/layout readiness differs");
      if (mirrored == null) {
        originalMirroredInputs = mirroredInputs = null;
        originalMirroredMasks = mirroredMasks = mirroredOrder = null;
      } else {
        if (mirrored.getClass() != Object[].class)
          throw fault("Unadapted IC2 mirrored input array");
        originalMirroredInputs = (Object[]) mirrored;
        mirroredInputs = originalMirroredInputs.clone();
        originalMirroredMasks = nativeMasks(mirroredLayout);
        mirroredMasks = originalMirroredMasks.clone();
        if (mirroredInputs.length != inputs.length)
          throw fault("IC2 mirrored compact input count differs");
        mirroredOrder = new int[inputs.length];
        boolean[] used = new boolean[inputs.length];
        for (int index = 0; index < mirroredInputs.length; index++) {
          int found = -1;
          for (int candidate = 0; candidate < inputs.length; candidate++)
            if (!used[candidate] && inputs[candidate] == mirroredInputs[index]) {
              found = candidate;
              break;
            }
          if (found < 0)
            throw fault("IC2 mirrored inputs are not a permutation of the native source");
          used[found] = true;
          mirroredOrder[index] = found;
        }
      }
      for (int[] layouts : new int[][] {masks, mirroredMasks})
        if (layouts != null)
          for (int mask : layouts)
            if (Integer.bitCount(mask) != inputs.length)
              throw fault("IC2 native mask differs from its compact input count");
    }
    verify();
  }

  int size() {
    check();
    return inputs.length;
  }
  /** Every successful native path in its real priority; matching inputs is a separate phase. */
  List<int[]> paths(boolean[] occupied) {
    check();
    if (occupied.length != 4 && occupied.length != 9)
      throw fault("IC2 examples require a 2x2 or 3x3 inventory");
    int mask = 0, count = 0;
    for (boolean present : occupied) {
      mask = (mask << 1) | (present ? 1 : 0);
      if (present) count++;
    }
    List<int[]> result = new ArrayList<>();
    if (shapeless) {
      if (count == inputs.length) result.add(sequence());
      return result;
    }
    if (occupied.length == 4) mask = ((mask & 12) << 5) | ((mask & 3) << 4);
    if (contains(masks, mask)) result.add(sequence());
    if (mirroredMasks != null && contains(mirroredMasks, mask)) result.add(mirroredOrder.clone());
    return result;
  }

  JsonArray primaryCells() {
    check();
    if (shapeless) throw fault("Unordered IC2 rule has no shaped grid");
    JsonArray cells = array();
    int index = 0;
    for (int cell = 0; cell < 9; cell++)
      cells.add((masks[0] & (1 << (8 - cell))) == 0 ? value(null) : value(index++));
    return cells;
  }
  /** Absolute inventory position per compact group, in native base/mirror order. */
  List<int[]> placements() {
    check();
    List<int[]> result = new ArrayList<>();
    if (shapeless) return result;
    for (int kind = 0; kind < 2; kind++) {
      int[] layouts = kind == 0 ? masks : mirroredMasks;
      if (layouts == null) continue;
      int[] order = kind == 0 ? sequence() : mirroredOrder;
      for (int mask : layouts) {
        int[] cells = new int[inputs.length];
        int index = 0;
        for (int cell = 0; cell < 9; cell++)
          if ((mask & (1 << (8 - cell))) != 0) cells[order[index++]] = cell;
        result.add(cells);
      }
    }
    return result;
  }

  JsonObject declaration() {
    check();
    return object(
        "class",
        recipe.getClass().getName(),
        "shapeless",
        shapeless,
        "width",
        width,
        "height",
        height,
        "inputCount",
        inputs.length,
        "masks",
        json(masks),
        "mirroredMasks",
        json(mirroredMasks),
        "mirroredOrder",
        json(mirroredOrder));
  }

  void verify() {
    check();
    if (field(recipe, "input") != originalInputs || !same(inputs, originalInputs)) throw changed();
    if (!shapeless
        && (width != (Integer) field(recipe, "inputWidth")
            || height != (Integer) field(recipe, "inputHeight")
            || field(recipe, "masks") != originalMasks
            || !Arrays.equals(masks, originalMasks)
            || field(recipe, "inputMirrored") != originalMirroredInputs
            || !same(mirroredInputs, originalMirroredInputs)
            || field(recipe, "masksMirrored") != originalMirroredMasks
            || !Arrays.equals(mirroredMasks, originalMirroredMasks))) throw changed();
  }

  private int[] sequence() {
    int[] values = new int[inputs.length];
    for (int index = 0; index < values.length; index++) values[index] = index;
    return values;
  }

  private static boolean contains(int[] values, int key) {
    for (int value : values) if (value == key) return true;
    return false;
  }

  private static boolean same(Object[] left, Object[] right) {
    if (left == null || right == null) return left == right;
    if (left.length != right.length) return false;
    for (int index = 0; index < left.length; index++) if (left[index] != right[index]) return false;
    return true;
  }

  private static int[] nativeMasks(Object value) {
    if (value == null || value.getClass() != int[].class)
      throw fault("IC2 native mask array is unavailable");
    int[] values = (int[]) value;
    if (values.length == 0 || values.length > 9)
      throw fault("IC2 mask count exceeds native translations");
    for (int mask : values)
      if (mask <= 0 || mask > 511) throw fault("Invalid IC2 native occupancy mask");
    return values;
  }

  private static JsonArray json(int[] values) {
    if (values == null) return null;
    JsonArray result = array();
    for (int value : values) result.add(value(value));
    return result;
  }

  private void check() {
    Jobs.checkpoint();
    if (recipe == null) throw new Jobs.Fault("capture_closed", "IC2 mask reader is closed");
  }

  public void close() {
    if (recipe == null) return;
    recipe = null;
    Arrays.fill(inputs, null);
    if (mirroredInputs != null) Arrays.fill(mirroredInputs, null);
    originalInputs = originalMirroredInputs = null;
    originalMasks = originalMirroredMasks = null;
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "recipe_changed", "IC2 native compact inputs or mask definitions changed");
  }
}
