package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Native GT++ decay chest definition and full saved-state batch law. */
final class DecayableRecipes implements RegistryRecipes {
  private static final String HANDLER = "gtPlusPlus.nei.DecayableRecipeHandler";
  private static final String RECIPE = "gtPlusPlus.core.handler.Recipes.DecayableRecipe";
  private static final String DUST = "gtPlusPlus.core.item.materials.DustDecayable";
  private List<?> source;
  private final List<Entry> entries = new ArrayList<>();
  private final IdentityHashMap<Object, Entry> byRecipe = new IdentityHashMap<>();
  private NativeDefinitions definitions;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  DecayableRecipes(TemplateRecipeHandler handler) {
    MagicApi.version("DecayableRecipes","gregtech_nh");
    if (!supports(handler)) throw fault("Unadapted GT++ decay handler");
    try {
      source = registrationSource();
      for (Object recipe : source) {
        Jobs.checkpoint();
        Entry entry = new Entry(recipe);
        entries.add(entry);
        byRecipe.put(recipe, entry);
      }
      definitions =
          new NativeDefinitions(
              "GT++ decay",
              source,
              this::current,
              recipe -> {
                Entry entry = byRecipe.get(recipe);
                if (entry == null) throw changed();
                return entry.definition();
              },
              "environment_changed");
      finishVerification();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  public int size() {
    check();
    return entries.size();
  }

  public void verify() {
    check();
    definitions.verify();
    for (int index = 0; index < entries.size(); index++) definitions.record(index);
  }

  public void finishVerification() {
    verify();
    definitions.finishVerification();
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    definitions.record(index);
    Entry entry = entries.get(index);
    if (!entry.valid) return false;
    // The real tile mutates its shared result template to size one. A capture
    // must never call that path; both native display stacks below are owned.
    ItemStack anchor = new ItemStack(entry.input.getItem());
    // Admission covers any native NBT; completion is conditional on the pinned
    // batch state machine. A malformed tag is a native fault, never an output.
    row.itemInput(0,
        Collections.singletonList(
            new RecipeRow.Ingredient(
                anchor, 1, false, object("kind", "wildcard", "meta", true, "nbt", true))),
        false);
    row.inputs
        .get(0)
        .getAsJsonObject()
        .getAsJsonArray("choices")
        .get(0)
        .getAsJsonObject()
        .add("consume", object("kind", "stack"));
    ItemStack result = entry.result.copy();
    result.stackSize = 1;
    row.itemOutput(0,
        result,
        10000,
        Amounts.potential("miscutils:decayState", "nativeBatchCompletes", "1", 1));
    row.record.add(
        "process",
        object(
            "kind",
            "decayChest",
            "nativeRecipe",
            RECIPE,
            "nativeTile",
            "gtPlusPlus.core.tileentities.general.TileEntityDecayablesChest",
            "nativeItem",
            DUST,
            "version",
            "5.09.51.482",
            "nativeIndex",
            index,
            "scope",
            "nativeStateLaw",
            "stateLaw",
            "tickableItemBatchV1",
            "input",
            row.facts.item(anchor),
            "output",
            row.facts.item(result),
            "maxTicks",
            entry.maxTicks,
            "displayedTicks",
            entry.displayedTicks,
            "freshBatches",
            Long.toString((entry.maxTicks + 21L) / 20L),
            "batchSteps",
            20,
            "inventoryLimit",
            1,
            "scheduler",
            "loadedServerInt32Modulo20",
            "replacement",
            "wholeStoredStackToOne"));
    row.note(
        "decay:scope",
        "gtPlusPlus.core.tileentities.general.TileEntityDecayablesChest.tryUpdateDecayable",
        "Any metadata and native root NBT is admitted to the pinned batch state law. Fresh dust creates progress tags; saved state preserves unrelated tags; empty or missing TickableItem is inactive; wrong-type TickableItem reports a native fault. A result is conditional on completing the state law, never guaranteed for every admitted input.");
    row.note(
        "decay:scheduler",
        "gtPlusPlus.core.tileentities.general.TileEntityDecayablesChest.updateEntity",
        "Loaded server tile updates increment a signed Java int tickCount, then run a batch when tickCount % 20 == 0. Normal batch spacing is 20 updates; signed-int wrap produces a 16-update spacing. Initial phase, unloading and lag prevent a fixed duration.");
    row.note(
        "decay:progress",
        "gtPlusPlus.core.item.base.BaseItemTickable.tickItemTag",
        "The first batch creates TickableItem with Tick=0L, maxTick=(long)maxTicks and isActive=true. Each of at most 20 steps reads isTicking before tickItemTag. The item threshold, not the stored maxTick tag, governs deactivation; completion waits until both the last pre-step ticking flag and step result are false. Fresh input therefore needs ceil((maxTicks+2)/20) batches.");
    row.note(
        "decay:replacement",
        "gtPlusPlus.core.tileentities.general.TileEntityDecayablesChest.tryUpdateDecayable",
        "Completion replaces the entire stored input stack with exactly one copied result, without crafting container returns. Ordinary insertion clamps to one; native NBT inventory loading preserves larger stored counts. Dust onUpdate does not advance decay in ordinary player inventory.");
    definitions.record(index);
    return true;
  }

  public JsonObject exclusion(int index) {
    check();
    definitions.record(index);
    Entry entry = entries.get(index);
    return entry.valid
        ? null
        : object(
            "reason",
            "native_recipe_invalid",
            "scope",
            "registered_entry",
            "nativeClass",
            RECIPE,
            "condition",
            "DecayableRecipe.isValid requires mTime > 0 and non-null mInput and mOutput",
            "displayedTicks",
            entry.displayedTicks,
            "inputPresent",
            entry.input != null,
            "outputPresent",
            entry.displayOutput != null);
  }

  static List<?> registrationSource() {
    Object source = field(type(RECIPE), null, "mRecipes");
    if (source == null)
      throw new Jobs.Fault("handler_not_ready", "GT++ decay registry is unavailable");
    if (source.getClass() != ArrayList.class)
      throw fault("Unadapted GT++ decay registry implementation");
    if (((List<?>) source).size() > 262144) throw fault("GT++ decay registry exceeds its budget");
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

  private static final class Entry {
    final int displayedTicks, maxTicks;
    final boolean valid;
    final ItemStack input, displayOutput, result;
    final Object recipe;
    final ItemStack inputSource, outputSource, resultSource;
    final Item inputItem, outputItem, resultItem;
    final Object inputTag, outputTag, resultTag;

    Entry(Object recipe) {
      if (recipe == null || recipe.getClass() != type(RECIPE))
        throw fault("Unadapted GT++ decay recipe override");
      this.recipe = recipe;
      displayedTicks = (Integer) field(recipe, "mTime");
      inputSource = (ItemStack) field(recipe, "mInput");
      outputSource = (ItemStack) field(recipe, "mOutput");
      input = owned(inputSource);
      displayOutput = owned(outputSource);
      inputItem = inputSource == null ? null : inputSource.getItem();
      outputItem = outputSource == null ? null : outputSource.getItem();
      inputTag = inputSource == null ? null : inputSource.getTagCompound();
      outputTag = outputSource == null ? null : outputSource.getTagCompound();
      valid = displayedTicks > 0 && input != null && displayOutput != null;
      if (valid) {
        Item dust = input.getItem();
        // Exact ownership guards getMaxTicks, createNBT, getDecayResult,
        // onUpdate and all inherited tick behavior before any callback.
        if (dust.getClass() != type(DUST))
          throw fault(
              "Unadapted GT++ decay item or callback override: " + dust.getClass().getName());
        maxTicks = (Integer) field(dust, "maxTicks");
        if (maxTicks <= 0) throw fault("GT++ decay item has a nonpositive native threshold");
        resultSource = (ItemStack) field(dust, "turnsIntoItem");
        result = owned(resultSource);
        resultItem = resultSource == null ? null : resultSource.getItem();
        resultTag = resultSource == null ? null : resultSource.getTagCompound();
        if (result == null
            || result.getItem() != displayOutput.getItem()
            || Items.feather.getDamage(result) != Items.feather.getDamage(displayOutput)
            || !ItemStack.areItemStackTagsEqual(result, displayOutput))
          throw fault("GT++ registered decay output differs from the native item result");
      } else {
        maxTicks = 0;
        result = null;
        resultSource = null;
        resultItem = null;
        resultTag = null;
      }
    }

    JsonObject definition() {
      if (field(recipe, "mInput") != inputSource
          || field(recipe, "mOutput") != outputSource
          || inputSource != null
              && (inputSource.getItem() != inputItem || inputSource.getTagCompound() != inputTag)
          || outputSource != null
              && (outputSource.getItem() != outputItem
                  || outputSource.getTagCompound() != outputTag)) throw changed();
      if (valid
          && ((Integer) field(inputItem, "maxTicks") != maxTicks
              || field(inputItem, "turnsIntoItem") != resultSource
              || resultSource.getItem() != resultItem
              || resultSource.getTagCompound() != resultTag)) throw changed();
      return object(
          "displayedTicks",
          (Integer) field(recipe, "mTime"),
          "input",
          stack(inputSource),
          "displayOutput",
          stack(outputSource),
          "maxTicks",
          maxTicks,
          "nativeResult",
          stack(resultSource));
    }
  }

  private static ItemStack owned(ItemStack stack) {
    if (stack == null) return null;
    NativeStackAudit.audit(stack);
    if (stack.getItem() == null || Item.itemRegistry.getNameForObject(stack.getItem()) == null)
      throw fault("Unregistered GT++ decay stack");
    return stack.copy();
  }

  private static JsonObject stack(ItemStack stack) {
    if (stack != null) NativeStackAudit.audit(stack);
    return stack == null
        ? null
        : object(
            "item",
            Item.itemRegistry.getNameForObject(stack.getItem()),
            "metadata",
            Items.feather.getDamage(stack),
            "amount",
            stack.stackSize,
            "nbt",
            TypedNbt.encode(stack.getTagCompound()));
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "GT++ decay reader is closed");
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
        "environment_changed", "GT++ decay native source, instance, threshold or output changed");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
