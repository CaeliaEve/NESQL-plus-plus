package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ReportedException;

/** Pinned BaseItemTickable + decay chest batch, on owned NBT only. No item/tile callbacks. */
final class DecayState {
  private DecayState() {}

  static JsonObject batch(NBTTagCompound input, int threshold) {
    if (threshold <= 0) throw new Jobs.Fault("recipe_unsupported", "Invalid decay threshold");
    NativeStackAudit.nbt(input);
    State state = new State(input == null ? null : (NBTTagCompound) input.copy(), threshold);
    int steps = 0;
    boolean ticking = false, result = false;
    String outcome;
    try {
      ticking = state.ticking();
      while (steps < 20 && ticking) {
        ticking = state.ticking();
        result = state.tick();
        steps++;
      }
      outcome = !ticking && !result ? "complete" : "ongoing";
    } catch (ReportedException malformed) {
      // Native getCompoundTag reports a wrong-type TickableItem. It must not
      // become a successful conversion or a swallowed capture exception.
      outcome = "fault";
    }
    return object(
        "input",
        TypedNbt.encode(input),
        "state",
        TypedNbt.encode(state.root),
        "outcome",
        outcome,
        "steps",
        steps);
  }

  private static final class State {
    NBTTagCompound root;
    final int threshold;

    State(NBTTagCompound root, int threshold) {
      this.root = root;
      this.threshold = threshold;
    }

    void create() {
      NBTTagCompound value = new NBTTagCompound();
      value.setLong("Tick", 0);
      value.setLong("maxTick", threshold);
      value.setBoolean("isActive", true);
      root = new NBTTagCompound();
      root.setTag("TickableItem", value);
    }

    boolean ticking() {
      if (root == null) {
        create();
        return true;
      }
      return root.getCompoundTag("TickableItem").getBoolean("isActive");
    }

    boolean tick() {
      if (root == null || !root.hasKey("TickableItem")) {
        create();
        return true;
      }
      NBTTagCompound value = root.getCompoundTag("TickableItem");
      // Java signed-long subtraction, not an arbitrary-precision comparison.
      long tick = value.getLong("Tick");
      if ((long) threshold - tick <= 0) {
        value.setBoolean("isActive", false);
        return false;
      }
      if (!ticking()) return false;
      value.setLong("Tick", tick + 1);
      return true;
    }
  }
}
