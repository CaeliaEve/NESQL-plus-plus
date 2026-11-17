package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.ItemStack;

/** Complete finite count-one result states, retaining greedy feasibility and double sum order. */
final class Ic2AdvancedStates {
  static final int LIMIT = 65536;

  static final class Candidate {
    final ItemStack stack;
    final double charge;
    final int matches;

    Candidate(ItemStack stack, double charge, int matches) {
      this.stack = stack.copy();
      this.charge = charge;
      this.matches = matches;
    }
  }

  static final class State {
    final double total;
    final int used, remaining;
    final int[] choices, positions;

    State(double total, int used, int remaining, int[] choices, int[] positions) {
      this.total = total;
      this.used = used;
      this.remaining = remaining;
      this.choices = choices;
      this.positions = positions;
    }

    String key() {
      return used + ":" + remaining + ":" + Ic2AdvancedLogic.bits(total);
    }
  }

  static List<State> prepare(
      List<List<Candidate>> groups, List<int[]> placements, boolean shapeless) {
    int count = groups.size();
    if (count < 1 || count > 9)
      throw new Jobs.Fault("recipe_unsupported", "Invalid IC2 Advanced finite input count");
    Map<String, State> outputs = new LinkedHashMap<>();
    long transitions = 0;
    List<int[]> paths = shapeless ? Collections.singletonList(null) : placements;
    for (int[] placement : paths) {
      int[] unset = new int[count];
      Arrays.fill(unset, -1);
      List<State> states =
          Collections.singletonList(
              new State(0, 0, shapeless ? (1 << count) - 1 : 0, unset, unset.clone()));
      int[] order = new int[count];
      if (!shapeless) {
        Integer[] slots = new Integer[count];
        for (int i = 0; i < count; i++) slots[i] = i;
        Arrays.sort(slots, Comparator.comparingInt(slot -> placement[slot]));
        for (int i = 0; i < count; i++) order[i] = slots[i];
      }
      for (int depth = 0; depth < count; depth++) {
        Map<String, State> next = new LinkedHashMap<>();
        for (State state : states)
          for (int slot = 0; slot < count; slot++) {
            if ((state.used & (1 << slot)) != 0 || !shapeless && order[depth] != slot) continue;
            for (int choice = 0; choice < groups.get(slot).size(); choice++) {
              Jobs.checkpoint();
              if (++transitions > 8388608)
                throw new Jobs.Fault(
                    "recipe_limit", "IC2 Advanced finite state transitions exceed budget");
              Candidate candidate = groups.get(slot).get(choice);
              int remaining = state.remaining;
              if (shapeless) {
                int available = remaining & candidate.matches;
                if (available == 0) continue;
                remaining &= ~Integer.lowestOneBit(available);
              }
              int[] choices = state.choices.clone(), positions = state.positions.clone();
              choices[slot] = choice;
              positions[slot] = shapeless ? depth : placement[slot];
              State value =
                  new State(
                      state.total + candidate.charge,
                      state.used | (1 << slot),
                      remaining,
                      choices,
                      positions);
              next.putIfAbsent(value.key(), value);
              if (next.size() > LIMIT)
                throw new Jobs.Fault(
                    "recipe_limit", "IC2 Advanced finite state count exceeds budget");
            }
          }
        states = new ArrayList<>(next.values());
      }
      for (State state : states) {
        if (state.remaining != 0)
          throw new AssertionError("IC2 Advanced incomplete native allocation");
        outputs.putIfAbsent(Ic2AdvancedLogic.bits(state.total), state);
      }
      if (outputs.size() > LIMIT)
        throw new Jobs.Fault("recipe_limit", "IC2 Advanced output states exceed budget");
    }
    return new ArrayList<>(outputs.values());
  }

  static boolean matches(ItemStack offered, ItemStack template) {
    return offered.getItem() == template.getItem()
        && (offered.getItemDamage() == template.getItemDamage()
            || template.getItemDamage() == 32767);
  }
}
