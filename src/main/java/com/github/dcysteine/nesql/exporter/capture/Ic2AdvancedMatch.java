package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.ItemStack;

/**
 * Pure native Advanced matcher: absolute masks and ordered greedy removal, not bipartite matching.
 */
final class Ic2AdvancedMatch implements AutoCloseable {
  private Ic2AdvancedMasks masks;
  private Ic2AdvancedInputs inputs;

  Ic2AdvancedMatch(Object recipe) {
    try {
      masks = new Ic2AdvancedMasks(recipe);
      inputs = new Ic2AdvancedInputs((Object[]) field(recipe, "input"));
    } catch (RuntimeException | Error error) {
      close();
      throw error;
    }
  }
  /** Group index per actual inventory cell, or null. Counts/NBT do not enter native matching. */
  int[] match(ItemStack[] cells) {
    verify();
    if (cells == null || (cells.length != 4 && cells.length != 9))
      throw new Jobs.Fault("recipe_unsupported", "IC2 matching requires a 2x2 or 3x3 inventory");
    boolean[] occupied = new boolean[cells.length];
    for (int cell = 0; cell < cells.length; cell++)
      if (cells[cell] != null) {
        NativeStackAudit.audit(cells[cell]);
        occupied[cell] = true;
      }
    List<List<ItemStack>> candidates = new ArrayList<>();
    for (int group = 0; group < masks.size(); group++) candidates.add(inputs.copies(group));
    for (int[] path : masks.paths(occupied)) {
      int[] mapping = new int[cells.length];
      Arrays.fill(mapping, -1);
      boolean[] used = new boolean[masks.size()];
      int next = 0;
      boolean accepted = true;
      for (int cell = 0; cell < cells.length; cell++) {
        if (cells[cell] == null) continue;
        int group = -1;
        if (masks.shapeless) {
          for (int index = 0; index < candidates.size(); index++)
            if (!used[index] && matches(cells[cell], candidates.get(index))) {
              group = index;
              break;
            }
        } else if (next < path.length && matches(cells[cell], candidates.get(path[next])))
          group = path[next];
        if (group < 0) {
          accepted = false;
          break;
        }
        used[group] = true;
        mapping[cell] = group;
        next++;
      }
      if (accepted) {
        verify();
        return mapping;
      }
    }
    verify();
    return null;
  }

  private static boolean matches(ItemStack offered, List<ItemStack> choices) {
    for (ItemStack choice : choices)
      if (offered.getItem() == choice.getItem()
          && (offered.getItemDamage() == choice.getItemDamage() || choice.getItemDamage() == 32767))
        return true;
    return false;
  }

  Ic2AdvancedMasks masks() {
    check();
    return masks;
  }

  Ic2AdvancedInputs inputs() {
    check();
    return inputs;
  }

  void verify() {
    check();
    masks.verify();
    inputs.verify();
  }

  private void check() {
    Jobs.checkpoint();
    if (masks == null || inputs == null)
      throw new Jobs.Fault("capture_closed", "IC2 matcher is closed");
  }

  public void close() {
    if (inputs != null) inputs.close();
    if (masks != null) masks.close();
    inputs = null;
    masks = null;
  }
}
