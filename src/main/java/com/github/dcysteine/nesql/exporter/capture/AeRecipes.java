package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;

/** Pinned AE2 and Wireless terminal definitions, independent of native display caches. */
final class AeRecipes implements RegistryRecipes {
  private static final String HANDLER = "appeng.integration.modules.NEIHelpers.NEIAE";
  private static final String RECIPE = "appeng.recipes.game.";
  private static final String WIRELESS = "net.p455w0rd.wirelesscraftingterminal.";

  private enum Family {
    SHAPED(
        HANDLER + "ShapedRecipeHandler",
        RECIPE + "ShapedRecipe",
        "appeng.api.recipes.IIngredient",
        true),
    SHAPELESS(
        HANDLER + "ShapelessRecipeHandler",
        RECIPE + "ShapelessRecipe",
        "appeng.api.recipes.IIngredient",
        false),
    TERMINAL(
        WIRELESS + "integration.modules.NEIHelpers.NEIAEShapedRecipeHandler",
        WIRELESS + "api.recipes.game.ShapedRecipe",
        WIRELESS + "api.recipes.IIngredient",
        true);
    final String handler, recipe, api;
    final boolean shaped;

    Family(String handler, String recipe, String api, boolean shaped) {
      this.handler = handler;
      this.recipe = recipe;
      this.api = api;
      this.shaped = shaped;
    }
  }

  private CraftingManager manager;
  private List<?> source;
  private final List<Object> nativeOrder = new ArrayList<>();
  private final List<IRecipe> recipes = new ArrayList<>();
  private final IdentityHashMap<IRecipe, Integer> positions = new IdentityHashMap<>();
  private final IdentityHashMap<IRecipe, List<Object>> bindings = new IdentityHashMap<>();
  private final Map<net.minecraft.item.Item, NativeItemVariants> metadata = new IdentityHashMap<>();
  private NativeItemVariants wildcardSource;
  private NativeDefinitions definitions;
  private final Family selected;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return family(handler) != null;
  }

  private static Family family(IRecipeHandler handler) {
    for (Family family : Family.values())
      if (handler.getClass().getName().equals(family.handler)) return family;
    return null;
  }

  AeRecipes(TemplateRecipeHandler handler) {
    this(handler, admittedAnchors(handler));
  }

  private static Object[] admittedAnchors(TemplateRecipeHandler handler) {
    MagicApi.version("AeRecipes","appliedenergistics2");
    if (family(handler) == Family.TERMINAL) MagicApi.version("AeRecipes","ae2wct");
    return registrationAnchors(handler);
  }

  private AeRecipes(TemplateRecipeHandler handler, Object[] roots) {
    selected = family(handler);
    if (selected == null) throw fault("Unadapted AE recipe handler");
    try {
      manager = (CraftingManager) roots[1];
      source = (List<?>) roots[2];
      nativeOrder.addAll(source);
      Class<?> recipeType = type(selected.recipe);
      for (int index = 0; index < source.size(); index++) {
        Jobs.checkpoint();
        Object value = source.get(index);
        if (value == null) throw fault("Null native AE crafting registry member");
        if (recipeType.isInstance(value)) {
          if (value.getClass() != recipeType)
            throw fault("Unadapted AE recipe subclass: " + value.getClass().getName());
          IRecipe recipe = (IRecipe) value;
          recipes.add(recipe);
          positions.put(recipe, index);
        }
      }
      definitions =
          new NativeDefinitions(
              "AE native crafting", recipes, this::current, raw -> definition((IRecipe) raw));
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static Object[] registrationAnchors(IRecipeHandler handler) {
    if (family(handler) == null) throw fault("Unknown AE recipe registration");
    CraftingManager manager = CraftingManager.getInstance();
    if (manager == null)
      throw new Jobs.Fault("handler_not_ready", "Native AE crafting manager is unavailable");
    if (manager.getClass() != CraftingManager.class)
      throw fault("Unadapted native AE crafting manager callback");
    if (manager.getRecipeList() == null)
      throw new Jobs.Fault("handler_not_ready", "Native AE crafting registry is unavailable");
    if (manager.getRecipeList().getClass() != ArrayList.class)
      throw fault("Unadapted native AE crafting manager/list");
    if (manager.getRecipeList().size() > 262144)
      throw new Jobs.Fault("recipe_limit", "AE crafting registry exceeds budget");
    return new Object[] {handler.getClass(), manager, manager.getRecipeList()};
  }

  private List<IRecipe> current() {
    if (closed) throw new Jobs.Fault("capture_closed", "AE native reader is closed");
    if (manager != CraftingManager.getInstance()
        || source != manager.getRecipeList()
        || source.size() != nativeOrder.size())
      throw new Jobs.Fault("recipe_changed", "AE native crafting source changed");
    return recipes;
  }

  private JsonObject definition(IRecipe recipe) {
    if (source.get(positions.get(recipe)) != recipe)
      throw new Jobs.Fault("recipe_changed", "AE native recipe order changed");
    List<Object> actual = new ArrayList<>();
    actual.add(recipe);
    boolean disabled = (Boolean) field(recipe, "disable");
    if (disabled) return object("disabled", true);
    Object supplied = field(recipe, "input");
    actual.add(supplied);
    checkStorage(supplied, selected.shaped);
    Object[] raw = selected.shaped ? ((Object[]) supplied) : ((List<?>) supplied).toArray();
    if (raw.length > 9) throw fault("AE native input budget exceeded");
    JsonArray inputs = new JsonArray();
    for (Object ingredient : raw) {
      actual.add(ingredient);
      if (ingredient == null) {
        inputs.add(value(null));
        continue;
      }
      JsonArray candidates = new JsonArray();
      for (ItemStack stack :
          AeIngredients.read(ingredient, type(selected.api), selected.shaped, actual))
        candidates.add(RecipeRow.stackEvidence(stack));
      inputs.add(candidates);
    }
    ItemStack output = (ItemStack) field(recipe, "output");
    actual.add(output);
    if (output != null) NativeStackAudit.audit(output);
    List<Object> initial = bindings.get(recipe);
    if (initial == null) bindings.put(recipe, actual);
    else {
      if (initial.size() != actual.size())
        throw new Jobs.Fault("recipe_changed", "AE native ingredient structure changed");
      for (int index = 0; index < actual.size(); index++)
        if (initial.get(index) != actual.get(index))
          throw new Jobs.Fault("recipe_changed", "AE native ingredient identity changed");
    }
    return object(
        "disabled",
        false,
        "inputs",
        inputs,
        "output",
        RecipeRow.stackEvidence(output),
        "width",
        selected.shaped ? field(recipe, "width") : null,
        "height",
        selected.shaped ? field(recipe, "height") : null,
        "mirror",
        selected.shaped ? field(recipe, "mirrored") : null);
  }

  public int size() {
    current();
    return recipes.size();
  }

  public void verify() {
    current();
    definitions.verify();
    if (wildcardSource != null) wildcardSource.verifyRoot();
  }

  public void finishVerification() {
    verify();
    for (int index = 0; index < nativeOrder.size(); index++) {
      Jobs.checkpoint();
      if (source.get(index) != nativeOrder.get(index))
        throw new Jobs.Fault("recipe_changed", "AE full crafting registry order changed");
    }
    definitions.finishVerification();
    for (NativeItemVariants variants : metadata.values()) variants.verify();
  }

  public boolean capture(int index, RecipeRow row) {
    verify();
    Set<NativeItemVariants> used = new HashSet<>();
    boolean result;
    try {
      result =
          definitions.checked(
              index,
              () -> {
                IRecipe recipe = recipes.get(index);
                if ((Boolean) field(recipe, "disable"))
                  return row.exclude(
                      object(
                          "native",
                          selected.recipe,
                          "nativeIndex",
                          positions.get(recipe),
                          "reason",
                          "native_bake_disabled"));
                return capture(recipe, selected, row, metadata, used);
              });
    } catch (RuntimeException | Error failure) {
      try {
        verifyVariants(used);
      } catch (RuntimeException | Error drift) {
        if (drift != failure) failure.addSuppressed(drift);
      }
      throw failure;
    }
    verifyVariants(used);
    return result;
  }

  private void verifyVariants(Set<NativeItemVariants> used) {
    for (NativeItemVariants variants : used) {
      if (wildcardSource == null) wildcardSource = variants;
      variants.verify();
    }
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (definitions != null) definitions.close();
    definitions = null;
    nativeOrder.clear();
    recipes.clear();
    positions.clear();
    bindings.clear();
    metadata.clear();
    wildcardSource = null;
    source = null;
    manager = null;
  }

  @SuppressWarnings("unchecked")
  static void capture(IRecipe source, TemplateRecipeHandler handler, RecipeRow row) {
    Family family = family(handler);
    Map<net.minecraft.item.Item, NativeItemVariants> metadata = new IdentityHashMap<>();
    Set<NativeItemVariants> used = new HashSet<>();
    capture(source, family, row, metadata, used);
    for (NativeItemVariants variants : used) variants.verify();
  }

  private static boolean capture(
      IRecipe source,
      Family family,
      RecipeRow row,
      Map<net.minecraft.item.Item, NativeItemVariants> metadata,
      Set<NativeItemVariants> used) {
    if (family == null || !source.getClass().getName().equals(family.recipe)) {
      throw fault(
          "AE recipe/handler mismatch or overridden native semantics: "
              + source.getClass().getName());
    }
    boolean shaped = family.shaped;
    Class<?> api = type(family.api);
    if ((Boolean) field(source, "disable")) {
      throw new Jobs.Fault("slot_changed", "AE2 recipe was disabled after enumeration");
    }
    int width = shaped ? (Integer) field(source, "width") : 3;
    int height = shaped ? (Integer) field(source, "height") : 3;
    Object supplied = field(source, "input");
    checkStorage(supplied, shaped);
    Object[] raw = shaped ? ((Object[]) supplied).clone() : ((List<?>) supplied).toArray();
    if (width < 1
        || width > 3
        || height < 1
        || height > 3
        || raw.length < 1
        || raw.length > 9
        || shaped && raw.length != width * height) throw fault("Invalid AE2 crafting grid");
    ItemStack output = (ItemStack) field(source, "output");
    if (output == null || output.getItem() == null || output.stackSize <= 0)
      throw fault("AE2 recipe has no fixed positive output");
    NativeStackAudit.audit(output);
    output = output.copy();
    List<List<RecipeRow.Ingredient>> inputs = new ArrayList<>();
    JsonArray cells = new JsonArray();
    JsonArray predicates = new JsonArray();
    for (int cell = 0; cell < raw.length; cell++) {
      Object ingredient = raw[cell];
      if (ingredient == null) {
        cells.add(value(null));
        predicates.add(value(null));
        continue;
      }
      cells.add(value(inputs.size()));
      ItemStack[] stacks = AeIngredients.read(ingredient, api, shaped, null);
      if (stacks.length == 0)
        return row.exclude(
            object(
                "native",
                family.recipe,
                "reason",
                "empty_native_required_input_predicate",
                "cell",
                cell));
      inputs.add(ingredients(stacks, metadata, used));
      JsonArray selectors = new JsonArray();
      for (ItemStack stack : stacks)
        selectors.add(
            object(
                "registry",
                net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
                "metadata",
                stack.getItemDamage()));
      predicates.add(selectors);
    }
    for (int slot = 0; slot < inputs.size(); slot++)
      row.nativeCraftingInput(slot, inputs.get(slot));
    if (shaped)
      row.record.add(
          "grid",
          object(
              "width",
              width,
              "height",
              height,
              "cells",
              cells,
              "mirror",
              field(source, "mirrored")));
    row.record.add(
        "process",
        object(
            "kind",
            "aeCrafting",
            "version",
            family == Family.TERMINAL ? "1.12.7" : "rv3-beta-695-GTNH",
            "nativeRecipe",
            family.recipe,
            "shaped",
            shaped,
            "width",
            shaped ? width : 0,
            "height",
            shaped ? height : 0,
            "mirror",
            shaped && (Boolean) field(source, "mirrored"),
            "predicates",
            predicates,
            "matching",
            shaped
                ? "columnOffsetsUnmirroredThenOptionalMirror"
                : "rowMajorFirstRemainingPredicate",
            "consumption",
            "workbenchOnePerOccupiedCellThenNativeContainer"));
    row.itemOutput(0, output, 10000);
    return true;
  }

  private static List<RecipeRow.Ingredient> ingredients(
      ItemStack[] raw,
      Map<net.minecraft.item.Item, NativeItemVariants> metadata,
      Set<NativeItemVariants> used) {
    List<RecipeRow.Ingredient> result = new ArrayList<>();
    for (ItemStack source : raw) {
      ItemStack copy = source.copy();
      copy.stackSize = 1;
      boolean wildcard = Items.feather.getDamage(copy) == OreDictionary.WILDCARD_VALUE;
      ItemStack[] variants;
      if (wildcard) {
        NativeItemVariants snapshot = metadata.get(copy.getItem());
        if (snapshot == null) {
          snapshot = new NativeItemVariants(copy);
          metadata.put(copy.getItem(), snapshot);
          used.add(snapshot);
        } else if (used.add(snapshot)) {
          snapshot.verify();
        }
        variants = snapshot.copies();
      } else {
        variants = new ItemStack[] {copy};
      }
      if (variants == null || variants.length == 0 || variants.length + result.size() > 65536)
        throw fault("AE2 ingredient expansion exceeds its budget");
      for (ItemStack variant : variants)
        result.add(
            new RecipeRow.Ingredient(
                variant.copy(),
                1,
                false,
                object("kind", "wildcard", "meta", wildcard, "nbt", true)));
    }
    return result;
  }

  private static void checkStorage(Object source, boolean shaped) {
    if (source == null || source.getClass() != (shaped ? Object[].class : ArrayList.class))
      throw fault("Unadapted AE native input array/list storage");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
