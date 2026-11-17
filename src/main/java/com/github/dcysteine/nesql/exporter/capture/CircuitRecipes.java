package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;
import net.minecraft.item.ItemStack;

/**
 * Galacticraft's five fixed inputs, including AmunRa's registrations in the same native machine.
 */
final class CircuitRecipes implements RegistryRecipes {
  private static final String GC = "micdoodle8.mods.galacticraft.";
  private static final String AR = "de.katzenpapst.amunra.";
  private static final String HANDLER = GC + "core.nei.CircuitFabricatorRecipeHandler";
  private final List<Definition> recipes = new ArrayList<>();
  private final boolean quick, hard;
  private CircuitSource nativeSource;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    String name = handler.getClass().getName();
    return name.equals(HANDLER) || name.equals(AR + "nei.recipehandler.ARCircuitFab");
  }

  CircuitRecipes(TemplateRecipeHandler handler) {
    MagicApi.version("CircuitRecipes","GalacticraftCore");
    if (!supports(handler)) throw fault("Unadapted circuit fabricator handler");
    if (handler.getClass().getName().startsWith(AR)) {
      MagicApi.version("CircuitRecipes","GalacticraftAmunRa");
    }
    try {
      nativeSource = new CircuitSource(handler);
      quick = nativeSource.quick;
      hard = nativeSource.hard;
      recipes.addAll(census(nativeSource.recipes, nativeSource.category));
      finishVerification();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static List<Map.Entry<ItemStack[], ItemStack>> snapshot(Map<?, ?> registry, List<?> sources) {
    List<Map.Entry<ItemStack[], ItemStack>> result = new ArrayList<>();
    for (Definition definition : census(registry, sources))
      if (definition.reason == null) result.add(definition.recipe);
    return result;
  }

  private static List<Definition> census(Map<?, ?> registry, List<?> sources) {
    if (registry == null) throw new Jobs.Fault("handler_not_ready", "Circuit registry unavailable");
    if (registry.getClass() != HashMap.class && registry.getClass() != LinkedHashMap.class)
      throw fault("Unadapted circuit registry implementation");
    if (registry.size() > 262144 || sources != null && sources.size() > 262144)
      throw fault("Circuit registry exceeds its budget");
    if (sources != null)
      for (Object source : sources) {
        if (source == null
            || !source.getClass().getName().equals(AR + "crafting.CircuitFabricatorRecipe"))
          throw fault("Unadapted AmunRa circuit registration");
      }
    IdentityHashMap<Object, List<Object>> categoryByOutput = new IdentityHashMap<>();
    if (sources != null)
      for (Object source : sources)
        categoryByOutput
            .computeIfAbsent(field(source, "output"), unused -> new ArrayList<>())
            .add(source);
    int[] linkWork = {0};
    Map<List<String>, Integer> seen = new HashMap<>();
    IdentityHashMap<net.minecraft.item.Item, Integer> items = new IdentityHashMap<>();
    List<Definition> result = new ArrayList<>();
    for (Map.Entry<?, ?> entry : registry.entrySet()) {
      Jobs.checkpoint();
      if (!(entry.getKey() instanceof ItemStack[]) || ((ItemStack[]) entry.getKey()).length != 5)
        throw fault("Circuit registry requires five fixed slots");
      ItemStack[] original = (ItemStack[]) entry.getKey();
      if (!(entry.getValue() instanceof ItemStack))
        throw fault("Circuit registry has no fixed result");
      NativeStackAudit.audit((ItemStack) entry.getValue());
      if (((ItemStack) entry.getValue()).getItemDamage() < 0)
        throw fault("Circuit signed result metadata would be changed by native ItemStack.copy");
      List<String> key = new ArrayList<>();
      ItemStack[] input = new ItemStack[5];
      for (int slot = 0; slot < 5; slot++) {
        ItemStack stack = original[slot];
        if (stack != null) NativeStackAudit.audit(stack);
        if (stack != null && stack.getItemDamage() < 0)
          throw fault("Circuit signed input metadata would be changed by native ItemStack.copy");
        if (stack != null && stack.getItem() == null)
          throw fault("Circuit registry has an invalid item");
        // The native matcher ignores count and NBT; 32767 is an exact metadata value here.
        key.add(
            stack == null
                ? null
                : items.computeIfAbsent(stack.getItem(), item -> items.size())
                    + "/"
                    + stack.getItemDamage());
        if (stack != null) {
          input[slot] = stack.copy();
          input[slot].stackSize = 1;
          input[slot].setTagCompound(null);
        }
      }
      // Preserve native HashMap encounter precedence BEFORE selecting the AmunRa category.
      int index = result.size();
      Integer earlier = seen.putIfAbsent(key, index);
      String reason =
          earlier != null
              ? "earlier_native_item_metadata_match_claims_input"
              : sources != null
                      && !linked(
                          categoryByOutput.get(entry.getValue()),
                          original,
                          entry.getValue(),
                          linkWork)
                  ? "outside_amunra_registered_category"
                  : null;
      result.add(
          new Definition(
              new AbstractMap.SimpleImmutableEntry<>(input, ((ItemStack) entry.getValue()).copy()),
              reason,
              earlier));
    }
    return result;
  }

  private static boolean linked(List<?> sources, ItemStack[] input, Object output, int[] work) {
    if (sources == null) return false;
    for (Object source : sources) {
      Jobs.checkpoint();
      linkWork(work);
      // RecipeHelper passes these very objects to addRecipe; no NEI list or candidate cross-product
      // is executed.
      if (field(source, "output") != output) continue;
      String[] names = {"crystal", "silicon1", "silicon2", "redstone", "optional"};
      boolean matches = true;
      for (int slot = 0; slot < 5; slot++) {
        ItemStack[] options = (ItemStack[]) field(source, names[slot]);
        boolean found = (options == null || options.length == 0) && input[slot] == null;
        if (options != null)
          for (ItemStack option : options)
            if (linkedOption(option, input[slot], work)) {
              found = true;
              break;
            }
        if (!found) {
          matches = false;
          break;
        }
      }
      if (matches) return true;
    }
    return false;
  }

  private static boolean linkedOption(ItemStack option, ItemStack input, int[] work) {
    linkWork(work);
    return option == input;
  }

  private static void linkWork(int[] work) {
    if (++work[0] > 4 * 1024 * 1024)
      throw new Jobs.Fault("source_budget", "Circuit category membership work exceeds budget");
  }

  public int size() {
    check();
    nativeSource.verifyRoots();
    return recipes.size();
  }

  public void verify() {
    check();
    nativeSource.verify();
  }

  public void finishVerification() {
    verify();
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Circuit reader closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    recipes.clear();
    if (nativeSource != null) nativeSource.close();
    nativeSource = null;
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    nativeSource.verifyRoots();
    Definition definition = recipes.get(index);
    if (definition.reason != null)
      return row.exclude(
          object(
              "native",
              GC + "api.recipe.CircuitFabricatorRecipes",
              "reason",
              definition.reason,
              "nativeIndex",
              index,
              "earlierIndex",
              definition.earlier));
    boolean captured =
        capture(
            definition.recipe, quick, hard, row, nativeSource.admitted, nativeSource.basic, index);
    nativeSource.verifyRoots();
    return captured;
  }

  static boolean capture(
      Map.Entry<ItemStack[], ItemStack> recipe, boolean quick, boolean hard, RecipeRow row) {
    List<?> admitted =
        (List<?>) field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "slotValidItems");
    return capture(
        recipe,
        quick,
        hard,
        row,
        admitted,
        (net.minecraft.item.Item) field(type(GC + "core.items.GCItems"), null, "basicItem"),
        0);
  }

  private static boolean capture(
      Map.Entry<ItemStack[], ItemStack> recipe,
      boolean quick,
      boolean hard,
      RecipeRow row,
      List<?> admitted,
      net.minecraft.item.Item basic,
      int nativeIndex) {
    ItemStack[] input = recipe.getKey();
    if (admitted == null)
      throw new Jobs.Fault("handler_not_ready", "Circuit slot admission unavailable");
    if (admitted.getClass() != ArrayList.class)
      throw fault("Unadapted circuit slot admission implementation");
    if (input.length != 5 || admitted.size() != 5)
      throw fault("Circuit inventory registration changed");
    com.google.gson.JsonArray slots = array();
    for (int slot = 0; slot < 5; slot++) {
      if (input[slot] == null) {
        slots.add(value(null));
        continue;
      }
      boolean valid = false;
      if (admitted.get(slot) == null || admitted.get(slot).getClass() != ArrayList.class)
        throw fault("Unadapted circuit slot admission list");
      for (Object option : (List<?>) admitted.get(slot)) {
        if (!(option instanceof ItemStack)) throw fault("Invalid circuit slot admission stack");
        NativeStackAudit.audit((ItemStack) option);
        if (input[slot].isItemEqual((ItemStack) option)) {
          valid = true;
          break;
        }
      }
      if (!valid)
        return row.exclude(
            object(
                "native",
                GC + "api.recipe.CircuitFabricatorRecipes.slotValidItems",
                "reason",
                "neither_manual_nor_sided_slot_accepts_registered_input",
                "nativeIndex",
                nativeIndex,
                "slot",
                slot + 1,
                "input",
                RecipeRow.stackEvidence(input[slot])));
      ItemStack stack = input[slot].copy();
      stack.stackSize = 1;
      stack.setTagCompound(null);
      row.itemInput(slot + 1,
          Collections.singletonList(
              new RecipeRow.Ingredient(
                  stack, 1, false, object("kind", "wildcard", "meta", false, "nbt", true))),
          false);
      slots.add(value(row.facts.item(stack)));
    }
    ItemStack output = recipe.getValue().copy();
    int rawAmount = output.stackSize;
    if (rawAmount < 1) throw fault("Circuit has a nonpositive native result count");
    if (quick && output.getItem() == basic) {
      if (output.getItemDamage() == 13) output.stackSize = 5;
      else if (output.getItemDamage() == 14) output.stackSize = 2;
    }
    row.itemOutput(6,
        output,
        10000,
        Amounts.potential(
            "galacticraft:circuitOutputState",
            "nativeCircuitOutputState",
            Integer.toString(output.stackSize),
            output.stackSize));
    row.record.add(
        "process",
        object(
            "kind",
            "circuitFabricator",
            "nativeRegistry",
            GC + "api.recipe.CircuitFabricatorRecipes",
            "nativeTile",
            GC + "core.tile.TileEntityCircuitFabricator",
            "version",
            "3.3.13-GTNH",
            "nativeIndex",
            nativeIndex,
            "slots",
            slots,
            "output",
            row.facts.item(output),
            "rawOutputAmount",
            Integer.toString(rawAmount),
            "outputAmount",
            Integer.toString(output.stackSize),
            "quick",
            quick,
            "hard",
            hard,
            "basicItem",
            basic == null ? null : net.minecraft.item.Item.itemRegistry.getNameForObject(basic),
            "processingTicks",
            300,
            "maxExtractGj",
            hard ? 40 : 20,
            "work",
            "consecutiveEligibleTicksResetOnBlocked",
            "priority",
            "firstMapEncounter",
            "matcher",
            "itemLiteralMetadataIgnoreCountAndTags",
            "inputDebit",
            "onCompletionOneWithoutContainers",
            "capacity",
            "emptyOrOriginalCountSignedIntWithinLimits",
            "outputPolicy",
            "emptyCopiesPrototypeMergeKeepsExistingTags",
            "overflow",
            "mergeAbove64SpawnsUntaggedUnitsIgnoringSpawnResult",
            "directCall",
            "debitsEvenIfOutputRejected"));
    row.note(
        "galacticraft:layout",
        GC + "core.tile.TileEntityCircuitFabricator.updateInput",
        "Input slots are native inventory indices 1 through 5, with required empty slots retained. Matching ignores count and NBT but uses literal metadata, including 32767, and the first HashMap encounter takes precedence. Each occupied slot loses one item at completion without crafting container returns.");
    row.note(
        "galacticraft:outputState",
        GC + "core.tile.TileEntityCircuitFabricator.compressItems",
        "Quick mode changes basic wafer metadata 13 to count 5 and metadata 14 to count 2. An empty output copies the result prototype; merging into an existing item/meta match preserves its NBT. Capacity admission uses the original result count with signed-int addition. Merged excess above 64 attempts single-item world spawns without prototype NBT, ignoring the spawn return value; the retained stack is capped at 64. Empty-output placement is not capped. Work requires 300 consecutive eligible ticks, and a blocked tick resets progress. The normal tick path gates completion; calling compressItems directly still debits inputs even if output is rejected. The displayed result is conditional, not a fixed final tag/placement guarantee.");
    // compressItems decrements each occupied slot by one; it does not return containers.
    return true;
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private static final class Definition {
    final Map.Entry<ItemStack[], ItemStack> recipe;
    final String reason;
    final Integer earlier;

    Definition(Map.Entry<ItemStack[], ItemStack> recipe, String reason, Integer earlier) {
      this.recipe = recipe;
      this.reason = reason;
      this.earlier = earlier;
    }
  }
}
