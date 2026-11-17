package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.version;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

/** Native tank conversion; the output remains conditional on the actual output tank. */
final class RefineryRecipes implements RegistryRecipes {
  private static final String HANDLER =
      "micdoodle8.mods.galacticraft.core.nei.RefineryRecipeHandler";
  private RefinerySource source;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  RefineryRecipes(TemplateRecipeHandler handler) {
    version("RefineryRecipes", "GalacticraftCore");
    if (!supports(handler))
      throw new Jobs.Fault("recipe_unsupported", "Unadapted refinery handler");
    source = new RefinerySource();
  }

  public int size() {
    check();
    source.verifyRoots();
    return source.inputs.size();
  }

  public void verify() {
    check();
    source.verify();
  }

  public void finishVerification() {
    verify();
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (source != null) source.close();
    source = null;
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    source.verifyRoots();
    Fluid input = source.inputs.get(index);
    try {
      row.fluidInput(0,
          new FluidStack(input, 1),
          false,
          object("kind", "wildcard", "meta", false, "nbt", true));
      row.fluidOutput(0,
          new FluidStack(source.output, 1),
          Amounts.potential(
              "galacticraft:refineryOutputState", "nativeRefineryTankAccepts", "1", 1));
      String tile = "micdoodle8.mods.galacticraft.core.tile.TileEntityRefinery";
      row.record.add(
          "process",
          object(
              "kind",
              "galacticraftRefinery",
              "nativeTile",
              tile,
              "version",
              "3.3.13-GTNH",
              "scope",
              "fluidTankConversion",
              "input",
              row.facts.fluid(new FluidStack(input, 1)),
              "normalizedOil",
              row.facts.fluid(new FluidStack(source.oil, 1)),
              "output",
              row.facts.fluid(new FluidStack(source.output, 1)),
              "legacyFuel",
              source.old,
              "hard",
              source.hard,
              "maxExtractGj",
              source.hard ? 90 : 60,
              "tankCapacity",
              24000,
              "countdownTicks",
              2,
              "freshStartupTicks",
              1,
              "transferAmount",
              "1",
              "admission",
              "caseSensitiveOilPrefixCanonicalKeepsTagsAlternateDiscardsTags",
              "work",
              "poweredEnabledCountdownResetOnBlocked",
              "outputPolicy",
              "drainBeforeTryFillIgnoringRejectedFuel",
              "containerPolicy",
              "tankOperationHasNoConsumedItemContainers"));
      row.note(
          "galacticraft:refinery",
          "micdoodle8.mods.galacticraft.core.tile.TileEntityRefinery.updateEntity",
          "The tank operation consumes up to one mB of oil, then attempts one mB of configured fuel. It requires positive oil, free output volume and an enabled powered machine. Incompatible output fluid or NBT can reject the fuel after oil was already drained. The shown fuel is a conditional prototype. A fresh run has one startup tick then two countdown ticks; uninterrupted queued runs use two ticks, while a blocked tick resets progress. The configured energy value is a maximum extraction ceiling, not a fixed measured debit or wall-clock duration. Piped names must start with case-sensitive oil; the canonical oil retains offered NBT, alternatives normalize to canonical oil without tags. Item canisters and container transfer are separate machine I/O, not consumed recipe ingredients.");
    } catch (RuntimeException | Error failure) {
      try {
        source.verifyRoots();
      } catch (RuntimeException | Error drift) {
        if (drift != failure) failure.addSuppressed(drift);
      }
      throw failure;
    }
    source.verifyRoots();
    return true;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Native refinery reader closed");
  }
}
