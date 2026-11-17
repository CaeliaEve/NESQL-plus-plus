package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Ordered native circuit definitions, slot admission, category sources and configuration. */
final class CircuitSource implements AutoCloseable {
  private static final String GC = "micdoodle8.mods.galacticraft.";
  private static final String AR = "de.katzenpapst.amunra.";
  private static final String[] PARTS = {"crystal", "silicon1", "silicon2", "redstone", "optional"};
  private final boolean amunra;
  final boolean quick, hard;
  Item basic;
  Map<?, ?> recipes;
  List<?> category, admission;
  final List<List<ItemStack>> admitted = new ArrayList<>();
  private List<Object> identities;
  private String signature;
  private boolean closed;

  CircuitSource(TemplateRecipeHandler handler) {
    amunra = handler.getClass().getName().equals(AR + "nei.recipehandler.ARCircuitFab");
    quick = (Boolean) field(type(GC + "core.util.ConfigManagerCore"), null, "quickMode");
    hard = (Boolean) field(type(GC + "core.util.ConfigManagerCore"), null, "hardMode");
    basic = (Item) field(type(GC + "core.items.GCItems"), null, "basicItem");
    try {
      Observation observation = observe();
      identities = observation.identities;
      signature = observation.signature;
      recipes =
          (Map<?, ?>) field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "recipes");
      admission =
          (List<?>) field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "slotValidItems");
      category =
          amunra
              ? (List<?>)
                  field(type(AR + "crafting.RecipeHelper"), null, "circuitFabricatorRecipes")
              : null;
      for (Object raw : admission) {
        List<ItemStack> row = new ArrayList<>();
        for (Object option : (List<?>) raw) row.add(((ItemStack) option).copy());
        admitted.add(row);
      }
      verify();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  static Object[] registrationAnchors(TemplateRecipeHandler handler) {
    boolean ar = handler.getClass().getName().equals(AR + "nei.recipehandler.ARCircuitFab");
    Object recipes = field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "recipes");
    Object admission =
        field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "slotValidItems");
    if (recipes == null || admission == null)
      throw new Jobs.Fault("handler_not_ready", "Circuit registry/admission unavailable");
    List<Object> anchors =
        new ArrayList<>(
            Arrays.asList(
                handler.getClass(),
                recipes,
                admission,
                Boolean.valueOf(
                    (Boolean) field(type(GC + "core.util.ConfigManagerCore"), null, "quickMode")),
                Boolean.valueOf(
                    (Boolean) field(type(GC + "core.util.ConfigManagerCore"), null, "hardMode"))));
    if (ar) {
      Object category = field(type(AR + "crafting.RecipeHelper"), null, "circuitFabricatorRecipes");
      if (category == null)
        throw new Jobs.Fault("handler_not_ready", "AmunRa circuit source unavailable");
      anchors.add(category);
    }
    Object basic = field(type(GC + "core.items.GCItems"), null, "basicItem");
    if (basic != null) anchors.add(basic);
    return anchors.toArray();
  }

  void verifyRoots() {
    check();
    if (field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "recipes") != recipes
        || field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "slotValidItems")
            != admission
        || amunra
            && field(type(AR + "crafting.RecipeHelper"), null, "circuitFabricatorRecipes")
                != category
        || (Boolean) field(type(GC + "core.util.ConfigManagerCore"), null, "quickMode") != quick
        || (Boolean) field(type(GC + "core.util.ConfigManagerCore"), null, "hardMode") != hard
        || field(type(GC + "core.items.GCItems"), null, "basicItem") != basic) throw changed();
  }

  void verify() {
    check();
    Observation actual;
    try {
      actual = observe();
    } catch (Jobs.Fault failure) {
      Jobs.Fault drift = changed();
      drift.initCause(failure);
      throw drift;
    }
    if (identities.size() != actual.identities.size() || !signature.equals(actual.signature))
      throw changed();
    for (int index = 0; index < identities.size(); index++)
      if (identities.get(index) != actual.identities.get(index)) throw changed();
  }

  private Observation observe() {
    Jobs.checkpoint();
    List<Object> refs = new ArrayList<>();
    Budget budget = new Budget();
    Object root = field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "recipes");
    Object slots = field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "slotValidItems");
    if (root == null || slots == null)
      throw new Jobs.Fault("handler_not_ready", "Circuit source unavailable");
    if (root.getClass() != HashMap.class || slots.getClass() != ArrayList.class)
      throw unsupported();
    Map<?, ?> map = (Map<?, ?>) root;
    List<?> lists = (List<?>) slots;
    if (map.size() > 32768 || lists.size() != 5 && !(map.isEmpty() && lists.isEmpty()))
      throw unsupported();
    refs.add(root);
    refs.add(slots);
    JsonArray rows = array(), valid = array(), categories = array();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Jobs.checkpoint();
      Object key = entry.getKey();
      if (key == null || key.getClass() != ItemStack[].class || ((ItemStack[]) key).length != 5)
        throw unsupported();
      refs.add(key);
      JsonArray input = array();
      for (ItemStack stack : (ItemStack[]) key) input.add(stack(stack, refs, budget));
      if (!(entry.getValue() instanceof ItemStack)) throw unsupported();
      rows.add(object("input", input, "output", stack((ItemStack) entry.getValue(), refs, budget)));
    }
    for (Object value : lists) {
      if (value == null || value.getClass() != ArrayList.class || ((List<?>) value).size() > 262144)
        throw unsupported();
      refs.add(value);
      JsonArray values = array();
      for (Object stack : (List<?>) value) {
        if (!(stack instanceof ItemStack)) throw unsupported();
        values.add(stack((ItemStack) stack, refs, budget));
      }
      valid.add(values);
    }
    if (amunra) {
      Object raw = field(type(AR + "crafting.RecipeHelper"), null, "circuitFabricatorRecipes");
      if (raw == null) throw new Jobs.Fault("handler_not_ready", "AmunRa source unavailable");
      if (raw.getClass() != ArrayList.class || ((List<?>) raw).size() > 262144) throw unsupported();
      refs.add(raw);
      for (Object recipe : (List<?>) raw) {
        Jobs.checkpoint();
        if (recipe == null || recipe.getClass() != type(AR + "crafting.CircuitFabricatorRecipe"))
          throw unsupported();
        refs.add(recipe);
        com.google.gson.JsonObject record =
            object("output", stack((ItemStack) field(recipe, "output"), refs, budget));
        for (String part : PARTS) {
          Object options = field(recipe, part);
          refs.add(options);
          JsonArray values = array();
          if (options != null) {
            if (options.getClass() != ItemStack[].class || ((ItemStack[]) options).length > 65536)
              throw unsupported();
            for (ItemStack option : (ItemStack[]) options) values.add(stack(option, refs, budget));
          }
          record.add(part, values);
        }
        categories.add(record);
      }
    }
    if (refs.size() > 262144) throw budgetExceeded();
    Object basic = field(type(GC + "core.items.GCItems"), null, "basicItem");
    refs.add(basic);
    if ((Boolean) field(type(GC + "core.util.ConfigManagerCore"), null, "quickMode")
        && (basic == null || Item.itemRegistry.getNameForObject(basic) == null))
      throw new Jobs.Fault("handler_not_ready", "Quick-mode circuit basic item unavailable");
    return new Observation(
        refs,
        CanonicalJson.digest(
            object(
                "rows",
                rows,
                "admission",
                valid,
                "category",
                categories,
                "quick",
                field(type(GC + "core.util.ConfigManagerCore"), null, "quickMode"),
                "hard",
                field(type(GC + "core.util.ConfigManagerCore"), null, "hardMode"),
                "basic",
                basic == null ? null : Item.itemRegistry.getNameForObject(basic))));
  }

  private static com.google.gson.JsonObject stack(
      ItemStack stack, List<Object> refs, Budget budget) {
    budget.add(128);
    if (refs.size() > 262140) throw budgetExceeded();
    refs.add(stack);
    if (stack == null) return null;
    NativeStackAudit.audit(stack);
    if (stack.getItemDamage() < 0)
      throw new Jobs.Fault(
          "recipe_unsupported",
          "Circuit signed metadata would be changed by native ItemStack.copy");
    budget.nbt(stack.getTagCompound());
    refs.add(stack.getItem());
    refs.add(stack.getTagCompound());
    String registry = Item.itemRegistry.getNameForObject(stack.getItem());
    if (registry == null) throw unsupported();
    return object(
        "item",
        registry,
        "meta",
        stack.getItemDamage(),
        "amount",
        stack.stackSize,
        "nbt",
        TypedNbt.encode(stack.getTagCompound()));
  }

  /** Conservative cumulative projection bound, checked on raw NBT before allocating typed JSON. */
  static final class Budget {
    private long bytes;
    private int nodes;

    void add(long size) {
      if ((bytes += size) > 32L * 1024 * 1024 || ++nodes > 262144) throw budgetExceeded();
    }

    void nbt(net.minecraft.nbt.NBTBase tag) {
      if (tag == null) return;
      add(64);
      if (tag instanceof net.minecraft.nbt.NBTTagCompound) {
        Map<?, ?> entries = (Map<?, ?>) field(tag, "tagMap");
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
          add(6L * ((String) entry.getKey()).length());
          nbt((net.minecraft.nbt.NBTBase) entry.getValue());
        }
      } else if (tag instanceof net.minecraft.nbt.NBTTagList) {
        for (Object child : (List<?>) field(tag, "tagList")) nbt((net.minecraft.nbt.NBTBase) child);
      } else if (tag instanceof net.minecraft.nbt.NBTTagString) {
        add(6L * ((net.minecraft.nbt.NBTTagString) tag).func_150285_a_().length());
      } else if (tag instanceof net.minecraft.nbt.NBTTagByteArray) {
        add(8L * ((net.minecraft.nbt.NBTTagByteArray) tag).func_150292_c().length);
      } else if (tag instanceof net.minecraft.nbt.NBTTagIntArray) {
        add(16L * ((net.minecraft.nbt.NBTTagIntArray) tag).func_150302_c().length);
      }
    }
  }

  private static Jobs.Fault budgetExceeded() {
    return new Jobs.Fault(
        "source_budget", "Circuit observation exceeds cumulative raw/typed projection budget");
  }

  private static final class Observation {
    final List<Object> identities;
    final String signature;

    Observation(List<Object> identities, String signature) {
      this.identities = identities;
      this.signature = signature;
    }
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Circuit source closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    recipes = null;
    category = null;
    admission = null;
    basic = null;
    if (identities != null) identities.clear();
    identities = null;
    signature = null;
    admitted.clear();
  }

  private static Jobs.Fault unsupported() {
    return new Jobs.Fault(
        "recipe_unsupported", "Unadapted circuit source, slot admission or native record");
  }

  private static Jobs.Fault changed() {
    return new Jobs.Fault(
        "environment_changed",
        "Circuit native roots, members, order, configuration or facts changed");
  }
}
