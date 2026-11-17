package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Registered AE2 recipes. Name presses preempt this registry; see the Inscriber process contract.
 */
final class InscriberRecipes implements RegistryRecipes {
  private static final String HANDLER =
      "appeng.integration.modules.NEIHelpers.NEIInscriberRecipeHandler";
  private static final String RECIPE = "appeng.core.features.registries.entries.InscriberRecipe";
  private final List<Entry> entries = new ArrayList<>();
  private final ItemStack namePress;
  private final InscriberSource source;
  private final NativeDefinitions definitions;
  private final IdentityHashMap<Object, List<Object>> bindings = new IdentityHashMap<>();
  private long definitionBytes;
  private int bindingCount;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  InscriberRecipes(TemplateRecipeHandler handler) {
    if (!supports(handler)) throw fault("Invalid inscriber handler");
    this.source = new InscriberSource();
    this.namePress = source.namePress();
    try {
      List<?> recipes = source.current();
      definitions =
          new NativeDefinitions(
              "AE2 inscriber", recipes, source::current, this::definition, "environment_changed");
      for (int index = 0; index < recipes.size(); index++)
        entries.add(new Entry(definitions.record(index)));
      finishVerification();
    } catch (RuntimeException | Error failure) {
      source.close();
      entries.clear();
      bindings.clear();
      throw failure;
    }
  }

  private void alive() {
    if (closed) throw new Jobs.Fault("capture_closed", "Native inscriber reader is closed");
  }

  public int size() {
    alive();
    return entries.size();
  }

  public void verify() {
    alive();
    source.verify();
    definitions.verify();
  }

  public void finishVerification() {
    verify();
    source.finishVerification();
    definitions.finishVerification();
  }

  public void close() {
    if (closed) return;
    closed = true;
    definitions.close();
    source.close();
    entries.clear();
    bindings.clear();
  }

  public boolean capture(int index, RecipeRow row) {
    alive();
    return definitions.checked(index, () -> capture(entries.get(index), row));
  }

  private boolean capture(Entry entry, RecipeRow row) {
    // Every matching plate arrangement is claimed by the preceding rename branch.
    if (isName(entry.top) && (entry.bottom == null || isName(entry.bottom)))
      return row.exclude(
          object(
              "native",
              "appeng.tile.misc.TileInscriber",
              "reason",
              "preceding_native_rename_branch_claims_plate_arrangement",
              "top",
              RecipeRow.stackEvidence(entry.top),
              "bottom",
              RecipeRow.stackEvidence(entry.bottom)));
    row.record.add(
        "process",
        object(
            "kind",
            "inscriber",
            "mode",
            entry.mode,
            "top",
            entry.top == null ? null : row.facts.item(entry.top),
            "bottom",
            entry.bottom == null ? null : row.facts.item(entry.bottom),
            "namePress",
            namePress == null ? null : row.facts.item(namePress)));
    boolean keep = entry.mode.equals("inscribe");
    if (entry.top != null) {
      input(row, 0, Collections.singletonList(entry.top), keep);
      if (entry.bottom != null) input(row, 1, Collections.singletonList(entry.bottom), keep);
    }
    // Native top-absent precedence ignores the declared bottom and requires an empty plate slot.
    input(row, 2, entry.inputs, false);
    row.itemOutput(0, entry.output.copy(), 10000);
    return true;
  }

  private JsonElement definition(Object recipe) {
    try {
      return observe(recipe);
    } catch (Jobs.Fault cause) {
      if (!bindings.containsKey(recipe) || cause.code.equals("environment_changed")) throw cause;
      Jobs.Fault drift = changed();
      drift.initCause(cause);
      throw drift;
    }
  }

  private JsonElement observe(Object recipe) {
    if (recipe == null || recipe.getClass() != type(RECIPE))
      throw fault("Unadapted native inscriber recipe owner");
    List<Object> identities = new ArrayList<>();
    Object raw = field(recipe, "inputs");
    if (raw == null || raw.getClass() != ArrayList.class)
      throw fault("Unadapted inscriber alternatives storage");
    identities.add(raw);
    List<?> inputs = (List<?>) raw;
    if (inputs.isEmpty() || inputs.size() > 65536) throw fault("Invalid inscriber alternatives");
    com.google.gson.JsonArray choices = array();
    for (Object value : inputs) choices.add(observeStack(value, identities, true));
    Object top = field(recipe, "maybeTop"),
        bottom = field(recipe, "maybeBot"),
        mode = field(recipe, "type");
    identities.add(top);
    identities.add(bottom);
    identities.add(mode);
    if (mode == null || mode.getClass() != type("appeng.api.features.InscriberProcessType"))
      throw fault("Unadapted inscriber mode");
    JsonObject result =
        object(
            "inputs",
            choices,
            "top",
            observeStack(InscriberSource.optional(top), identities, true),
            "bottom",
            observeStack(InscriberSource.optional(bottom), identities, true),
            "output",
            observeStack(field(recipe, "output"), identities, false),
            "mode",
            ((Enum<?>) mode).name());
    List<Object> expected = bindings.get(recipe);
    if (expected == null) {
      bindingCount += identities.size();
      definitionBytes += CanonicalJson.bytes(result).length;
      if (bindingCount > 262144 || definitionBytes > 32L * 1024 * 1024)
        throw new Jobs.Fault("recipe_limit", "Native inscriber snapshot exceeds budget");
      bindings.put(recipe, identities);
    } else {
      if (expected.size() != identities.size()) throw changed();
      for (int i = 0; i < expected.size(); i++)
        if (expected.get(i) != identities.get(i)) throw changed();
    }
    return result;
  }

  private static JsonObject observeStack(Object value, List<Object> identities, boolean predicate) {
    if (value == null) {
      identities.add(null);
      return null;
    }
    if (value.getClass() != ItemStack.class) throw fault("Unadapted native inscriber stack");
    ItemStack raw = (ItemStack) value;
    copy(raw, predicate);
    identities.add(raw);
    identities.add(raw.getItem());
    identities.add(raw.getTagCompound());
    return RecipeRow.stackEvidence(raw);
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault("environment_changed", "Native inscriber member identity changed");
  }

  private boolean isName(ItemStack item) {
    return item != null
        && namePress != null
        && item.getItem() == namePress.getItem()
        && item.getItemDamage() == namePress.getItemDamage();
  }

  private static void input(RecipeRow row, int slot, List<ItemStack> items, boolean keep) {
    List<RecipeRow.Ingredient> choices = new ArrayList<>();
    for (ItemStack item : items)
      choices.add(new RecipeRow.Ingredient(item.copy(), 1, keep, object("kind", "ae")));
    row.itemInput(slot, choices, false);
  }

  private static final class Entry {
    final List<ItemStack> inputs = new ArrayList<>();
    final ItemStack top, bottom, output;
    final String mode;

    Entry(Object recipe) {
      Collection<?> raw = (Collection<?>) field(recipe, "inputs");
      if (raw == null || raw.isEmpty() || raw.size() > 65536)
        throw fault("Invalid inscriber alternatives");
      for (Object item : raw) inputs.add(copy((ItemStack) item, true));
      ItemStack a = (ItemStack) InscriberSource.optional(field(recipe, "maybeTop"));
      ItemStack b = (ItemStack) InscriberSource.optional(field(recipe, "maybeBot"));
      top = a == null ? null : copy(a, true);
      bottom = b == null ? null : copy(b, true);
      output = copy((ItemStack) field(recipe, "output"), false);
      Object nativeMode = field(recipe, "type");
      if (!(nativeMode instanceof Enum)) throw fault("Missing inscriber process mode");
      mode = ((Enum<?>) nativeMode).name().toLowerCase(Locale.ROOT);
      if (!mode.equals("press") && !mode.equals("inscribe"))
        throw fault("Unknown inscriber process mode");
    }
  }

  private static ItemStack copy(ItemStack item, boolean predicate) {
    if (item == null || item.getItem() == null || !predicate && item.stackSize <= 0)
      throw fault("Invalid inscriber item");
    NativeStackAudit.audit(item);
    String registry = Item.itemRegistry.getNameForObject(item.getItem());
    if (registry == null || Item.itemRegistry.getObject(registry) != item.getItem())
      throw fault("Unregistered native inscriber item");
    if (predicate) {
      try {
        if (item.getItem().getClass().getMethod("getDamage", ItemStack.class).getDeclaringClass()
            != Item.class) throw fault("Inscriber predicate has a custom metadata getter");
      } catch (NoSuchMethodException error) {
        throw fault("Missing native metadata getter");
      }
      if (item.getTagCompound() != null && item.getTagCompound().getClass() != NBTTagCompound.class)
        throw fault("Inscriber predicate uses identity-dependent NBT");
      rejectNaN(TypedNbt.encode(item.getTagCompound()));
    }
    ItemStack copy = item.copy();
    if (predicate) copy.stackSize = 1;
    return copy;
  }

  private static void rejectNaN(JsonElement value) {
    if (value == null || value.isJsonNull()) return;
    JsonObject object = value.getAsJsonObject();
    String kind = object.get("type").getAsString();
    if (kind.equals("float")
            && Float.isNaN(
                Float.intBitsToFloat(
                    (int) Long.parseUnsignedLong(object.get("value").getAsString(), 16)))
        || kind.equals("double")
            && Double.isNaN(
                Double.longBitsToDouble(
                    Long.parseUnsignedLong(object.get("value").getAsString(), 16))))
      throw fault("Inscriber NaN predicate depends on object identity");
    if (kind.equals("list"))
      for (JsonElement child : object.getAsJsonArray("value")) rejectNaN(child);
    if (kind.equals("compound"))
      for (Map.Entry<String, JsonElement> child : object.getAsJsonObject("value").entrySet())
        rejectNaN(child.getValue());
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
