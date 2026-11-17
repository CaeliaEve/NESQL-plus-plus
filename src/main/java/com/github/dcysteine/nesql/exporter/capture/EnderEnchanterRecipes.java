package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.capture.EnderInputs.Pattern;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Native ordered enchanter registry, expanded by level with an explicit offered-count gate. */
final class EnderEnchanterRecipes implements RegistryRecipes {
  private static final String HANDLER = "crazypants.enderio.nei.EnchanterRecipeHandler";
  private static final String INPUT = "crazypants.enderio.machine.recipe.RecipeInput";
  private static final String ORE = "crazypants.enderio.machine.recipe.OreDictionaryRecipeInput";
  private static final String RECIPE = "crazypants.enderio.machine.enchanter.EnchanterRecipe";
  private final List<Entry> entries = new ArrayList<>();
  private final List<Level> levels = new ArrayList<>();
  private final int baseCost;
  private EnchanterSource source;
  private boolean closed;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  EnderEnchanterRecipes(TemplateRecipeHandler handler) {
    this(handler, EnchanterSource.open());
  }

  EnderEnchanterRecipes(TemplateRecipeHandler handler, List<?> registry, int baseCost) {
    this(handler, new EnchanterSource(registry, baseCost));
  }

  private EnderEnchanterRecipes(TemplateRecipeHandler handler, EnchanterSource source) {
    this.source = source;
    this.baseCost = source.baseCost;
    try {
      if (!supports(handler)) throw fault("Unknown enchanter handler");
      long patterns = 0;
      for (Object record : source.records) {
        Jobs.checkpoint();
        if (record == null || !record.getClass().getName().equals(RECIPE))
          throw fault("Unknown enchanter recipe implementation");
        Entry entry = new Entry(record, source);
        if ((patterns += entry.patterns.size()) > 262144)
          throw new Jobs.Fault("source_budget", "Enchanter aggregate input expansion exceeds its budget");
        int at = entries.size();
        entries.add(entry);
        if (entry.exclusion != null) {
          if (levels.size() >= 262144) throw fault("Enchanter row census exceeds its budget");
          levels.add(new Level(at, 0));
          continue;
        }
        for (int level = 1; level <= entry.maxLevel; level++) {
          if (levels.size() >= 262144) throw fault("Enchanter level expansion exceeds its budget");
          levels.add(new Level(at, level));
        }
      }
      source.verify();
    } catch (RuntimeException | Error failure) {
      close();
      throw failure;
    }
  }

  public int size() {
    verify();
    return levels.size();
  }

  public void verify() {
    Jobs.checkpoint();
    if (closed) throw new Jobs.Fault("capture_closed", "Enchanter reader closed");
    source.verifyRoots();
  }

  public void finishVerification() {
    verify();
    source.verify();
  }

  public void close() {
    if (closed) return;
    closed = true;
    entries.clear();
    levels.clear();
    if (source != null) source.close();
    source = null;
  }

  public boolean capture(int index, RecipeRow row) {
    verify();
    Level branch = levels.get(index);
    Entry entry = entries.get(branch.entry);
    if (entry.exclusion != null) {
      JsonObject proof =
          new com.google.gson.JsonParser().parse(entry.exclusion.toString()).getAsJsonObject();
      proof.addProperty("nativeEntry", branch.entry);
      return row.exclude(proof);
    }
    long count = (long) entry.perLevel * branch.level;
    if (count > Integer.MAX_VALUE)
      return row.exclude(
          object(
              "native",
              RECIPE,
              "reason",
              "required_count_exceeds_signed_native_stack",
              "required",
              Long.toString(count),
              "maximumStoredCount",
              Integer.MAX_VALUE,
              "nativeEntry",
              branch.entry,
              "level",
              branch.level));
    List<Pattern> previous = new ArrayList<>();
    for (int i = 0; i < branch.entry; i++) previous.addAll(entries.get(i).patterns);
    List<RecipeRow.Ingredient> choices = new ArrayList<>();
    for (Pattern pattern : entry.patterns) {
      Jobs.checkpoint();
      boolean covered = false;
      for (Pattern prior : previous)
        if (prior.covers(pattern)) {
          covered = true;
          break;
        }
      if (covered) continue;
      JsonArray exclusions = array();
      Set<String> seen = new HashSet<>();
      for (Pattern prior : previous)
        if (prior.overlaps(pattern)) {
          String id = row.facts.item(prior.item);
          if (seen.add(id + ":" + prior.rule().toString()))
            exclusions.add(object("id", id, "rule", prior.rule()));
          if (exclusions.size() > 4096) throw fault("Enchanter priority filter exceeds its budget");
        }
      ItemStack example = pattern.item.copy();
      int attempts = 0;
      while (matchesAny(previous, example)) {
        Jobs.checkpoint();
        if (++attempts > 262144) throw fault("Enchanter witness search exceeds its budget");
        long next = (long) example.getItemDamage() + (pattern.oreCode == null ? 1 : 65536);
        if (next > Integer.MAX_VALUE)
          throw fault("Enchanter predicate needs a signed metadata reachability proof");
        example.setItemDamage((int) next);
      }
      JsonObject rule = pattern.rule();
      if (exclusions.size() > 0)
        rule = object("kind", "except", "base", rule, "exclude", exclusions);
      choices.add(new RecipeRow.Ingredient(example, count, false, rule));
    }
    if (choices.isEmpty())
      return row.exclude(
          object(
              "native",
              RECIPE,
              "reason",
              "all_input_patterns_covered_by_earlier_native_entries",
              "nativeEntry",
              branch.entry,
              "level",
              branch.level,
              "patterns",
              entry.patterns.size(),
              "earlierPatterns",
              previous.size()));
    int cost =
        baseCost + entry.costPerLevel * branch.level * branch.level; // Native Java int wraparound.
    row.record.add(
        "process",
        object(
            "kind",
            "enderioEnchanter",
            "version",
            "2.9.28",
            "level",
            branch.level,
            "maxLevel",
            entry.maxLevel,
            "itemsPerLevel",
            entry.perLevel,
            "cost",
            cost,
            "baseCost",
            baseCost,
            "costPerLevel",
            entry.costPerLevel,
            "enchantment",
            entry.enchantment.effectId,
            "offeredMinimum",
            Long.toString(count),
            "offeredMaximum",
            Integer.toString(
                branch.level == entry.maxLevel
                    ? Integer.MAX_VALUE
                    : (int)
                        Math.min(
                            Integer.MAX_VALUE, (long) entry.perLevel * (branch.level + 1) - 1)),
            "bookPolicy",
            "runtimePresenceClearWholeSlot",
            "materialPolicy",
            "nativeCountDeterminesLevelDebitDivisorTimesLevelNoContainers",
            "pickupPolicy",
            "creativeBypassXpElseSignedLevelDebit"));
    row.itemInput(0,
        Collections.singletonList(
            new RecipeRow.Ingredient(
                new ItemStack(Items.writable_book), 1, false, object("kind", "enchanterBook"))),
        false);
    row.itemInput(1, choices, false);
    for (com.google.gson.JsonElement input : row.inputs)
      for (com.google.gson.JsonElement choice : input.getAsJsonObject().getAsJsonArray("choices"))
        choice.getAsJsonObject().add("consume", object("kind", "allocated"));
    ItemStack output = new ItemStack(Items.enchanted_book);
    Items.enchanted_book.addEnchantment(
        output, new EnchantmentData(entry.enchantment, branch.level));
    row.itemOutput(0,
        output,
        10000,
        Amounts.potential("enderio:enchanterPickup", "nativeEnchanterLevelAndPickup", null, 1));
    row.note(
        "enderio:enchanter",
        "crazypants.enderio.machine.enchanter.ContainerEnchanter$3.onPickupFromSlot",
        row.facts.locale().equals("zh_CN")
            ? "注册首个匹配材料的配方先胜出，再按实际有符号库存数量除以注册时保存的每级数量并封顶到附魔上限。正常书本槽只允许书与笔，运行时只要求非空，领取时清空整个书本槽。材料按保存的每级数量乘实际等级扣除，余料保留；无容器返还。经验费用按原生有符号整数累加，创造模式免经验检查与支付。所示为当前等级分支的条件产物，不假定所有更大库存仍产生相同等级。"
            : "The first matching material recipe wins before its count is checked. Divide the actual signed stored count by the registration-time items-per-level divisor, then cap at the enchantment maximum. Normal book-slot insertion admits writable books, while runtime only requires presence and pickup clears the whole book slot. Materials debit divisor times actual level, retaining any remainder without containers. XP cost uses native signed-int accumulation; creative mode bypasses its check and payment. This is a conditional output for the stated level interval, not every larger inventory.");
    return true;
  }

  private static final class Entry {
    final Enchantment enchantment;
    final int perLevel, maxLevel, costPerLevel;
    final JsonObject exclusion;
    final List<Pattern> patterns = new ArrayList<>();

    Entry(Object record, EnchanterSource source) {
      Object input = field(record, "input");
      enchantment = (Enchantment) field(record, "enchantment");
      perLevel = (Integer) field(record, "stackSizePerLevel");
      costPerLevel = (Integer) field(record, "costPerLevel");
      if (enchantment == null
          || input == null
          || field(type(INPUT), input, "input") == null
          || costPerLevel < 0) {
        maxLevel = 0;
        exclusion =
            object(
                "native",
                RECIPE,
                "reason",
                "native_recipe_isValid_false",
                "hasEnchantment",
                enchantment != null,
                "hasInput",
                input != null,
                "hasInputStack",
                input != null && field(type(INPUT), input, "input") != null,
                "costPerLevel",
                costPerLevel);
        return;
      }
      exclusion = null;
      if (input == null
          || !(input.getClass().getName().equals(INPUT) || input.getClass().getName().equals(ORE)))
        throw fault("Unknown enchanter input predicate");
      ItemStack item = (ItemStack) field(type(INPUT), input, "input");
      if (item == null || item.getItem() == null)
        throw fault("Enchanter input must have a native item");
      if (perLevel <= 0)
        throw fault("Native enchanter level division needs a positive stored divisor");
      maxLevel = EnchanterSource.maximum(enchantment);
      if (maxLevel < 1 || maxLevel > 32767) throw fault("Invalid native enchantment maximum level");
      patterns.addAll(source.patterns(input));
    }
  }

  private static boolean matchesAny(List<Pattern> patterns, ItemStack item) {
    for (Pattern p : patterns) if (p.matches(item)) return true;
    return false;
  }

  private static final class Level {
    final int entry, level;

    Level(int entry, int level) {
      this.entry = entry;
      this.level = level;
    }
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
