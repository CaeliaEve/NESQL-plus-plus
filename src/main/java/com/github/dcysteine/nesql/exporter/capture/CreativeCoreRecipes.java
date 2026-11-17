package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

/** Pinned CreativeCore native predicates; IRecipeInfo's displayed stacks are not its matcher. */
final class CreativeCoreRecipes implements RegistryRecipes {
  private static final String ROOT = "com.creativemd.creativecore.";
  private static final String HANDLER = ROOT + "api.nei.NEIRecipeInfoHandler";
  private static final String SHAPED = ROOT + "common.recipe.BetterShapedRecipe";
  private static final String SHAPELESS = ROOT + "common.recipe.entry.BetterShapelessRecipe";
  private final List<Entry> entries = new ArrayList<>();
  private CreativeCoreBindings bindings;
  private String signature;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  CreativeCoreRecipes(TemplateRecipeHandler handler) {
    MagicApi.version("CreativeCoreRecipes","creativecore");
    if (!supports(handler)) throw fault("Unadapted CreativeCore recipe handler");
    try {
      bindings = new CreativeCoreBindings();
      entries.addAll(registry(bindings.table()));
      signature = signature(entries);
      bindings.verify();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  public int size() {
    check();
    bindings.verifyRoots();
    return entries.size();
  }

  public boolean capture(int index, RecipeRow row) {
    check();
    bindings.verifyRoots();
    return capture(entries.get(index), row);
  }

  public void verify() {
    check();
    bindings.verifyRoots();
  }

  public void finishVerification() {
    verify();
    bindings.verify();
    if (!signature.equals(signature(registry(bindings.table()))))
      throw new Jobs.Fault(
          "environment_changed",
          "CreativeCore native predicates, registry candidates or returns changed");
  }

  private static List<Entry> registry(List<?> table) {
    List<Entry> result = new ArrayList<>();
    CreativeCoreMatching.OreLookup ores = new CreativeCoreMatching.OreLookup();
    long bytes = 0;
    Class<?> api = type(ROOT + "common.recipe.IRecipeInfo");
    for (Object recipe : table)
      if (api.isInstance(recipe)) {
        Jobs.checkpoint();
        Entry entry = new Entry((IRecipe) recipe, ores);
        bytes += CanonicalJson.bytes(entry.process).length + 256L;
        if (bytes > 32L * 1024 * 1024)
          throw new Jobs.Fault(
              "source_budget", "CreativeCore registry programs exceed cumulative budget");
        result.add(entry);
        if (result.size() > 262144) throw fault("CreativeCore native registry exceeds its budget");
      }
    return result;
  }

  static boolean capture(IRecipe source, TemplateRecipeHandler handler, RecipeRow row) {
    if (!supports(handler)) throw fault("Unadapted CreativeCore handler");
    return capture(new Entry(source), row);
  }

  private static boolean capture(Entry entry, RecipeRow row) {
    if (entry.unreachable)
      return row.exclude(
          object(
              "native",
              entry.shaped ? SHAPED : SHAPELESS,
              "reason",
              "creativecore_workbench_cell_bound",
              "width",
              entry.width,
              "height",
              entry.height));
    for (Group group : entry.groups)
      if (group != null && group.patterns.isEmpty())
        throw fault("Empty CreativeCore sample requires a complete native reachability proof");
    JsonArray cells = array();
    int slot = 0;
    for (int cell = 0; cell < entry.groups.size(); cell++) {
      Group group = entry.groups.get(cell);
      if (group == null) {
        cells.add(value(null));
        continue;
      }
      List<RecipeRow.Ingredient> choices = new ArrayList<>();
      Map<String, ItemStack> returns = new HashMap<>();
      for (Pattern pattern : group.patterns) {
        ItemStack offered = pattern.item.copy();
        offered.stackSize = Math.max(1, entry.matching.inputs.get(cell).minimum);
        if (!entry.matching.accepts(cell, offered))
          throw fault("CreativeCore display specimen is rejected by the audited native predicate");
        choices.add(
            new RecipeRow.Ingredient(
                pattern.item.copy(), 1, false, object("kind", "creativeCore", "input", cell)));
        if (pattern.returned != null) returns.put(row.facts.item(pattern.item), pattern.returned);
      }
      row.itemInput(slot, choices, false);
      for (com.google.gson.JsonElement value :
          row.inputs.get(row.inputs.size() - 1).getAsJsonObject().getAsJsonArray("choices")) {
        JsonObject choice = value.getAsJsonObject();
        ItemStack returned = returns.get(choice.get("id").getAsString());
        if (returned != null)
          choice
              .getAsJsonArray("returns")
              .add(
                  object(
                      "kind",
                      "item",
                      "id",
                      row.facts.item(returned),
                      "amount",
                      Integer.toString(returned.stackSize)));
      }
      cells.add(value(slot++));
    }
    if (entry.shaped && entry.width > 0 && entry.height > 0)
      row.record.add(
          "grid",
          object("width", entry.width, "height", entry.height, "cells", cells, "mirror", true));
    row.itemOutput(0, entry.output.copy(), 10000);
    row.record.add("process", new com.google.gson.JsonParser().parse(entry.process.toString()));
    row.note(
        "creativecore:matching",
        entry.shaped ? SHAPED + ".matches" : SHAPELESS + ".matches",
        row.facts.locale().equals("zh_CN")
            ? "原生数量门槛是库存要求，不是每次消耗量。工作台每个占用格扣一件，再按已审查的原生规则返还容器。无序合成按行读取格子，选择剩余列表中首个匹配谓词，再删除首个与其相等的谓词；删除的实例可能不同。有序合成按列偏移、行偏移依次尝试，每个位置先原形后镜像；源数组未参与匹配的尾部不占网格。"
            : "Native quantity thresholds are stock requirements, not per-craft consumption. Each occupied workbench cell loses one item, then its audited native container is returned. Shapeless matching reads occupied cells row by row, selects the first matching remaining predicate, and removes the first predicate equal to that selection; the removed instance can differ. Shaped matching tries column offsets, row offsets and then unmirrored before mirrored placement. Unused source-array tail predicates do not occupy the grid.");
    return true;
  }

  private void check() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "CreativeCore reader closed");
  }

  public void close() {
    if (closed) return;
    closed = true;
    entries.clear();
    if (bindings != null) bindings.close();
    bindings = null;
    signature = null;
  }

  private static final class Entry {
    final CreativeCoreMatching matching;
    final JsonObject process;
    final boolean shaped;
    final boolean unreachable;
    final int width, height;
    final List<Group> groups = new ArrayList<>();
    final ItemStack output;
    final String signature;

    Entry(IRecipe recipe) {
      this(recipe, new CreativeCoreMatching.OreLookup());
    }

    Entry(IRecipe recipe, CreativeCoreMatching.OreLookup ores) {
      if (recipe == null
          || !(recipe.getClass().getName().equals(SHAPED)
              || recipe.getClass().getName().equals(SHAPELESS)))
        throw fault("Unadapted CreativeCore IRecipeInfo implementation");
      matching = new CreativeCoreMatching(recipe, ores);
      shaped = recipe.getClass().getName().equals(SHAPED);
      List<?> raw =
          shaped
              ? Arrays.asList((Object[]) field(recipe, "info"))
              : (List<?>) field(recipe, "info");
      width = (Integer) field(recipe, "width");
      height = shaped ? (Integer) field(recipe, "height") : 0;
      unreachable = shaped ? width > 3 || height > 3 : raw.size() > 9;
      if (!unreachable && shaped && (width < 0 || height < 0 || (long) width * height > raw.size()))
        throw fault("Invalid CreativeCore native crafting grid");
      Work work = new Work();
      JsonArray inputs = array();
      int inputIndex = 0;
      for (Object input :
          unreachable ? Collections.emptyList() : shaped ? raw.subList(0, width * height) : raw) {
        int currentInput = inputIndex++;
        if (input == null) {
          if (!shaped)
            throw fault("Native CreativeCore shapeless recipe contains a null predicate");
          groups.add(null);
          inputs.add(value(null));
          continue;
        }
        Group group = new Group(input, work, matching, currentInput);
        groups.add(group);
        inputs.add(value(group.signature));
      }
      ItemStack result = (ItemStack) field(recipe, "output");
      if (result == null || result.getItem() == null || result.stackSize <= 0)
        throw fault("Invalid CreativeCore fixed recipe output");
      NativeStackAudit.audit(result);
      if (result.getItemDamage() < 0)
        throw fault("Negative output metadata changes under native copy");
      output = result.copy();
      process =
          unreachable ? object("unreachable", true, "inputCount", raw.size()) : matching.describe();
      signature =
          CanonicalJson.digest(
              object(
                  "class",
                  recipe.getClass().getName(),
                  "width",
                  width,
                  "height",
                  height,
                  "inputs",
                  inputs,
                  "process",
                  process,
                  "output",
                  stack(output)));
    }
  }

  private static final class Group {
    final List<Pattern> patterns = new ArrayList<>();
    final String signature;

    Group(Object source, Work work, CreativeCoreMatching matching, int inputIndex) {
      String name = source.getClass().getName();
      switch (name) {
        case ROOT + "common.utils.stack.StackInfoItem":
          patterns.add(new Pattern(new ItemStack((Item) field(source, "item")), true, true));
          break;
        case ROOT + "common.utils.stack.StackInfoItemStack":
          // Metadata 32767 is literal in this native predicate, not Forge's wildcard.
          patterns.add(
              new Pattern(
                  (ItemStack) field(source, "stack"), false, !(Boolean) field(source, "needNBT")));
          break;
        case ROOT + "common.utils.stack.StackInfoOre":
          for (CreativeCoreMatching.OreExample example : matching.oreExamples(inputIndex)) {
            work.step();
            patterns.add(new Pattern(example.stack, example.wildcard, true));
          }
          break;
        case ROOT + "common.utils.stack.StackInfoBlock":
        case ROOT + "common.utils.stack.StackInfoMaterial":
          boolean material = name.endsWith("StackInfoMaterial");
          Object criterion = field(source, material ? "material" : "block");
          if (criterion == null) throw fault("CreativeCore block/material predicate has no target");
          String getter =
              cpw.mods.fml.relauncher.ReflectionHelper.findMethod(
                      Block.class, null, new String[] {"getMaterial", "func_149688_o"})
                  .getName();
          for (Object registered : Item.itemRegistry) {
            work.step();
            Item candidate = (Item) registered;
            Block block = Block.getBlockFromItem(candidate);
            if (block == null) continue;
            if (material) {
              try {
                if (block.getClass().getMethod(getter).getDeclaringClass() != Block.class)
                  throw fault(
                      "Unadapted CreativeCore block material callback: "
                          + block.getClass().getName());
              } catch (NoSuchMethodException error) {
                throw fault("Missing native block material getter");
              }
            }
            if ((material ? block.getMaterial() : block) == criterion)
              patterns.add(new Pattern(new ItemStack(candidate), true, true));
          }
          break;
        default:
          throw fault("Unadapted CreativeCore StackInfo predicate: " + name);
      }
      TreeMap<String, Pattern> unique = new TreeMap<>();
      for (Pattern pattern : patterns) {
        work.step();
        unique.put(CanonicalJson.digest(pattern.record()), pattern);
      }
      patterns.clear();
      patterns.addAll(unique.values());
      JsonArray values = array();
      for (Pattern pattern : patterns) values.add(pattern.record());
      // Display samples always carry unit consumption. The separate native program
      // retains signed stock thresholds and order-dependent predicate equality.
      signature = CanonicalJson.digest(values);
    }
  }

  private static final class Pattern {
    final ItemStack item, returned;
    final boolean meta, nbt;

    Pattern(ItemStack source, boolean meta, boolean nbt) {
      if (source != null) NativeStackAudit.audit(source);
      if (source != null) NativeStackAudit.identityOwner(source.getItem());
      if (source != null && source.getItemDamage() < 0)
        throw fault("Negative metadata would change under native copy");
      if (source == null
          || source.getItem() == null
          || Item.itemRegistry.getNameForObject(source.getItem()) == null)
        throw fault("Invalid CreativeCore ingredient stack");
      if (ItemCallbacks.method(source.getItem(), "getDamage", "getDamage", ItemStack.class)
              .getDeclaringClass()
          != Item.class) throw fault("Unadapted CreativeCore ingredient metadata callback");
      this.meta = meta;
      this.nbt = nbt;
      item = source.copy();
      item.stackSize = 1;
      if (meta) item.setItemDamage(0);
      if (nbt) item.setTagCompound(null);
      returned = ItemCallbacks.container(item, meta);
    }

    JsonObject rule() {
      return meta || nbt
          ? object("kind", "wildcard", "meta", meta, "nbt", nbt)
          : object("kind", "exact");
    }

    JsonObject record() {
      return object(
          "item",
          stack(item),
          "rule",
          rule(),
          "returned",
          returned == null ? null : stack(returned));
    }
  }

  private static JsonObject stack(ItemStack item) {
    return object(
        "item",
        Item.itemRegistry.getNameForObject(item.getItem()),
        "meta",
        Items.feather.getDamage(item),
        "amount",
        item.stackSize,
        "nbt",
        TypedNbt.encode(item.getTagCompound()));
  }

  private static String signature(List<Entry> entries) {
    JsonArray values = array();
    for (Entry entry : entries) values.add(value(entry.signature));
    return CanonicalJson.digest(values);
  }

  private static final class Work {
    int count;

    void step() {
      Jobs.checkpoint();
      if (++count > 262144) throw fault("CreativeCore predicate work exceeds its budget");
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
