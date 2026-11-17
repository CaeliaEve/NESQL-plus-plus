package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** AdvancedSolarPanel ordered native definitions, independent of the NEI display cache. */
final class SolarRecipes implements RegistryRecipes {
  private static final String HANDLER = "advsolar.client.nei.MTRecipeHandler";
  private static final String RECORD = "advsolar.utils.MTRecipeRecord";
  private List<?> source;
  private final List<Entry> entries = new ArrayList<>();
  private final IdentityHashMap<Object, Entry> byRecipe = new IdentityHashMap<>();
  private NativeDefinitions definitions;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  SolarRecipes(TemplateRecipeHandler handler) {
    this(admittedSource(handler));
  }

  private static List<?> admittedSource(TemplateRecipeHandler handler) {
    MagicApi.version("SolarRecipes","AdvancedSolarPanel");
    if (!supports(handler)) throw fault("Unknown molecular transformer handler");
    return registrationSource();
  }

  private SolarRecipes(List<?> source) {
    try {
      this.source = source;
      Map<Item, Set<Integer>> seen = new IdentityHashMap<>();
      for (Object recipe : source) {
        Jobs.checkpoint();
        Entry entry = new Entry(recipe);
        entry.shadowed =
            !seen.computeIfAbsent(entry.input.getItem(), ignored -> new HashSet<>())
                .add(entry.input.getItemDamage());
        entries.add(entry);
        byRecipe.put(recipe, entry);
      }
      definitions =
          new NativeDefinitions(
              "Molecular transformer",
              source,
              this::current,
              recipe -> {
                Entry entry = byRecipe.get(recipe);
                if (entry == null) throw changed();
                return entry.definition();
              },
              "environment_changed");
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static List<?> registrationSource() {
    Object source = field(type("advsolar.utils.MTRecipeManager"), null, "transformerRecipes");
    if (source == null)
      throw new Jobs.Fault("handler_not_ready", "Molecular transformer registry unavailable");
    if (source.getClass() != ArrayList.class)
      throw fault("Unadapted molecular transformer registry implementation");
    if (((List<?>) source).size() > 262144)
      throw fault("Molecular transformer registry exceeds budget");
    return (List<?>) source;
  }

  private List<?> current() {
    check();
    List<?> actual;
    try {
      actual = registrationSource();
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (actual != source) throw changed();
    return actual;
  }

  public int size() {
    check();
    return entries.size();
  }

  public void verify() {
    check();
    definitions.verify();
  }

  public void finishVerification() {
    check();
    definitions.finishVerification();
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    return definitions.checked(index, () -> captureRow(index, row));
  }

  private boolean captureRow(int index, RecipeRow row) {
    Entry entry = entries.get(index);
    if (entry.shadowed)
      return row.exclude(
          object(
              "native",
              HANDLER,
              "reason",
              "earlier_native_item_metadata_match_claims_input",
              "input",
              RecipeRow.stackEvidence(entry.input),
              "nativeIndex",
              index,
              "earlierEntries",
              index));
    if (entry.input.stackSize < 0 || entry.output.stackSize <= 0 || entry.energy < 0)
      throw fault("Molecular transformer has a negative input/energy or nonpositive result");
    row.itemInput(0,
        Collections.singletonList(
            new RecipeRow.Ingredient(
                entry.input,
                Math.max(1, entry.input.stackSize),
                entry.input.stackSize == 0,
                object("kind", "wildcard", "meta", false, "nbt", true))),
        false);
    row.itemOutput(0,
        entry.output.copy(),
        10000,
        Amounts.potential(
            "advancedsolar:outputState",
            "nativeEnergyAndOutputAccepts",
            Integer.toString(entry.output.stackSize),
            entry.output.stackSize));
    row.record.add(
        "process",
        object(
            "kind",
            "molecularTransformer",
            "nativeRecord",
            RECORD,
            "nativeTile",
            "advsolar.common.tiles.TileEntityMolecularTransformer",
            "version",
            "1.7.10-3.5.1",
            "nativeIndex",
            index,
            "input",
            row.facts.item(entry.input),
            "output",
            row.facts.item(entry.output),
            "inputAmount",
            Integer.toString(entry.input.stackSize),
            "outputAmount",
            Integer.toString(entry.output.stackSize),
            "energyPerOperation",
            entry.energy,
            "priority",
            "firstItemMetadataNoFallback",
            "inputDebit",
            "onWorkStartWithoutContainers",
            "outputPolicy",
            "emptyCopiesPrototypeMergeKeepsExistingTags",
            "energyPolicy",
            "totalEuBufferedAndOutputSpace"));
    row.note(
        "advancedsolar:processing",
        "advsolar.common.tiles.TileEntityMolecularTransformer.updateEntity",
        "The first matching item and literal metadata claims the input, including metadata 32767. NBT is ignored. Too few items or a blocked output rejects that first recipe without trying a later duplicate. Input is consumed when work starts, without crafting container returns. Zero-count definitions require one present item but consume none. Completion depends on total supplied energy and output space, not a fixed duration. An empty output copies the native result tags; merging into an existing item/meta-matching stack preserves that stack's tags.");
    return true;
  }

  private static final class Entry {
    final Object recipe;
    final ItemStack inputSource, outputSource, input, output;
    final Item inputItem, outputItem;
    final Object inputTag, outputTag;
    final int energy;
    boolean shadowed;

    Entry(Object recipe) {
      if (recipe == null || recipe.getClass() != type(RECORD))
        throw fault("Unknown molecular transformer recipe implementation");
      this.recipe = recipe;
      inputSource = (ItemStack) field(recipe, "inputStack");
      outputSource = (ItemStack) field(recipe, "outputStack");
      NativeStackAudit.audit(inputSource);
      NativeStackAudit.audit(outputSource);
      if (Item.itemRegistry.getNameForObject(inputSource.getItem()) == null
          || Item.itemRegistry.getNameForObject(outputSource.getItem()) == null
          || inputSource.getItemDamage() < 0)
        throw fault("Invalid molecular transformer item reference");
      input = inputSource.copy();
      output = outputSource.copy();
      inputItem = inputSource.getItem();
      outputItem = outputSource.getItem();
      inputTag = inputSource.getTagCompound();
      outputTag = outputSource.getTagCompound();
      energy = (Integer) field(recipe, "energyPerOperation");
    }

    JsonObject definition() {
      if (field(recipe, "inputStack") != inputSource
          || field(recipe, "outputStack") != outputSource
          || inputSource.getItem() != inputItem
          || outputSource.getItem() != outputItem
          || inputSource.getTagCompound() != inputTag
          || outputSource.getTagCompound() != outputTag) throw changed();
      return object(
          "input",
          stack(inputSource),
          "output",
          stack(outputSource),
          "energy",
          (Integer) field(recipe, "energyPerOperation"));
    }
  }

  private static JsonObject stack(ItemStack stack) {
    NativeStackAudit.audit(stack);
    return object(
        "item",
        Item.itemRegistry.getNameForObject(stack.getItem()),
        "meta",
        stack.getItemDamage(),
        "amount",
        stack.stackSize,
        "nbt",
        TypedNbt.encode(stack.getTagCompound()));
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Molecular transformer reader closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (definitions != null) definitions.close();
    definitions = null;
    source = null;
    entries.clear();
    byRecipe.clear();
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "environment_changed",
        "Molecular transformer native source, instance, order or facts changed");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
