package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.OreDictionary;

/** Pinned BetterQuesting declaration readers. No detect, submit, claim or GUI callback is run. */
final class BetterQuestingRules {
  private BetterQuestingRules() {}

  /** Count visits, including repeated candidates; unique fact counts alone do not bound work. */
  static final class Budget {
    private int remaining;

    Budget() {
      this(262144);
    }

    Budget(int limit) {
      remaining = limit;
    }

    void claim() {
      Jobs.checkpoint();
      if (remaining-- <= 0) throw fault("Quest declaration traversal budget exceeded");
    }
  }

  static JsonObject task(
      Object value, Map<String, ItemStack> items, Map<String, FluidStack> fluids) {
    return task(value, items, fluids, new Budget());
  }

  static JsonObject task(
      Object value, Map<String, ItemStack> items, Map<String, FluidStack> fluids, Budget budget) {
    budget.claim();
    if (value == null) throw fault("Null task declaration");
    String name = value.getClass().getName();
    if (!name.startsWith("bq_standard.tasks.Task")) throw fault("Unadapted task owner: " + name);
    String suffix = name.substring("bq_standard.tasks.Task".length());
    JsonObject rule;
    switch (suffix) {
      case "Retrieval":
      case "OptionalRetrieval":
        rule =
            object(
                "type",
                "retrieval",
                "optional",
                suffix.equals("OptionalRetrieval"),
                "items",
                stacks(field(value, "requiredItems"), items, budget));
        booleans(rule, value, "partialMatch", "ignoreNBT", "consume", "groupDetect", "autoConsume");
        return rule;
      case "Crafting":
        rule =
            object(
                "type", "crafting", "items", stacks(field(value, "requiredItems"), items, budget));
        booleans(
            rule,
            value,
            "partialMatch",
            "ignoreNBT",
            "allowAnvil",
            "allowSmelt",
            "allowCraft",
            "allowCraftedFromStatistics");
        return rule;
      case "Fluid":
        JsonArray required = array();
        for (Object raw : list(field(value, "requiredFluids"))) {
          budget.claim();
          if (!(raw instanceof FluidStack)) throw fault("Invalid native required fluid");
          FluidStack stack = ((FluidStack) raw).copy();
          String id = fluid(stack, fluids);
          required.add(object("fluid", id, "amount", stack.amount));
        }
        rule = object("type", "fluid", "fluids", required, "ignoreNBT", bool(value, "ignoreNbt"));
        booleans(rule, value, "consume", "groupDetect", "autoConsume");
        return rule;
      case "Meeting":
        rule =
            object(
                "type",
                "meeting",
                "entity",
                string(field(value, "idName")),
                "range",
                integer(value, "range"),
                "amount",
                integer(value, "amount"),
                "targetNBT",
                nbt(field(value, "targetTags")));
        booleans(rule, value, "ignoreNBT", "subtypes");
        return rule;
      case "Hunt":
        rule =
            object(
                "type",
                "hunt",
                "entity",
                string(field(value, "idName")),
                "required",
                integer(value, "required"),
                "damageType",
                string(field(value, "damageType")),
                "targetNBT",
                nbt(field(value, "targetTags")));
        booleans(rule, value, "ignoreNBT", "subtypes");
        return rule;
      case "InteractItem":
        rule =
            object(
                "type",
                "interactItem",
                "item",
                nullableStack(field(value, "targetItem"), items, budget),
                "block",
                block(field(value, "targetBlock"), items, budget),
                "required",
                integer(value, "required"));
        booleans(rule, value, "partialMatch", "ignoreNBT", "onInteract", "onHit");
        return rule;
      case "InteractEntity":
        rule =
            object(
                "type",
                "interactEntity",
                "item",
                nullableStack(field(value, "targetItem"), items, budget),
                "entity",
                string(field(value, "entityID")),
                "targetNBT",
                nbt(field(value, "entityTags")),
                "required",
                integer(value, "required"));
        booleans(
            rule,
            value,
            "ignoreItemNBT",
            "partialItemMatch",
            "entitySubtypes",
            "ignoreEntityNBT",
            "onInteract",
            "onHit");
        return rule;
      case "Checkbox":
        return object("type", "checkbox");
      case "Location":
        rule = object("type", "location");
        for (String key : new String[] {"name", "structure"})
          rule.addProperty(key, string(field(value, key)));
        for (String key : new String[] {"biome", "x", "y", "z", "dim", "range"})
          rule.addProperty(key, integer(value, key));
        booleans(rule, value, "visible", "hideInfo", "invert", "taxiCab");
        return rule;
      case "XP":
        return object(
            "type",
            "xp",
            "levels",
            bool(value, "levels"),
            "amount",
            integer(value, "amount"),
            "consume",
            bool(value, "consume"));
      case "BlockBreak":
        JsonArray blocks = array();
        for (Object raw : list(field(value, "blockTypes"))) blocks.add(block(raw, items, budget));
        return object("type", "blockBreak", "blocks", blocks);
      case "Scoreboard":
        Object conversion = field(value, "conversion");
        if (!(conversion instanceof Float)) throw fault("Invalid native scoreboard conversion");
        return object(
            "type",
            "scoreboard",
            "scoreName",
            string(field(value, "scoreName")),
            "scoreDisp",
            string(field(value, "scoreDisp")),
            "criteria",
            string(field(value, "type")),
            "target",
            integer(value, "target"),
            "conversionBits",
            String.format(Locale.ROOT, "%08x", Float.floatToRawIntBits((Float) conversion)),
            "suffix",
            string(field(value, "suffix")),
            "operation",
            enumName(field(value, "operation")));
      default:
        throw fault("Unadapted task callback: " + name);
    }
  }

  static JsonObject reward(Object value, Map<String, ItemStack> items) {
    return reward(value, items, new Budget());
  }

  static JsonObject reward(Object value, Map<String, ItemStack> items, Budget budget) {
    budget.claim();
    if (value == null) throw fault("Null reward declaration");
    String name = value.getClass().getName();
    if (!name.startsWith("bq_standard.rewards.Reward"))
      throw fault("Unadapted reward owner: " + name);
    String suffix = name.substring("bq_standard.rewards.Reward".length());
    JsonObject rule;
    switch (suffix) {
      case "Item":
        rule =
            object(
                "type",
                "item",
                "items",
                stacks(field(value, "items"), items, budget),
                "grantPolicy",
                "baseStackSplit");
        break;
      case "Choice":
        rule =
            object(
                "type",
                "choice",
                "items",
                stacks(field(value, "choices"), items, budget),
                "selectionCount",
                1,
                "grantPolicy",
                "baseStackSplit");
        break;
      case "XP":
        rule =
            object(
                "type", "xp", "levels", bool(value, "levels"), "amount", integer(value, "amount"));
        break;
      case "Command":
        rule =
            object(
                "type",
                "command",
                "command",
                string(field(value, "command")),
                "hideCommand",
                bool(value, "hideCmd"),
                "viaPlayer",
                bool(value, "viaPlayer"));
        break;
      case "Scoreboard":
        // Native writeToNBT always writes "dummy"; the running rule reads the actual type field.
        rule =
            object(
                "type",
                "scoreboard",
                "score",
                string(field(value, "score")),
                "criteria",
                string(field(value, "type")),
                "relative",
                bool(value, "relative"),
                "value",
                integer(value, "value"));
        break;
      case "QuestCompletion":
        Object target = field(value, "questNum");
        if (target != null && !(target instanceof UUID))
          throw fault("Invalid completion reward quest UUID");
        rule =
            object(
                "type", "questCompletion", "questUuid", target == null ? null : target.toString());
        break;
      default:
        throw fault("Unadapted reward callback: " + name);
    }
    Object ignore = invoke(value.getClass(), value, "isIgnoreDisabled", new Class<?>[0]);
    if (!(ignore instanceof Boolean)) throw fault("Invalid native reward enable policy");
    rule.addProperty("ignoreDisabled", (Boolean) ignore);
    return rule;
  }

  private static JsonArray stacks(Object raw, Map<String, ItemStack> items, Budget budget) {
    JsonArray result = array();
    for (Object stack : list(raw)) result.add(stack(stack, items, budget));
    return result;
  }

  static JsonElement nullableStack(Object raw, Map<String, ItemStack> items, Budget budget) {
    return raw == null ? JsonNull.INSTANCE : stack(raw, items, budget);
  }

  private static JsonObject stack(Object raw, Map<String, ItemStack> items, Budget budget) {
    budget.claim();
    if (raw == null || !raw.getClass().getName().equals("betterquesting.api.utils.BigItemStack"))
      throw fault("Unadapted BigItemStack owner");
    Object nativeBase = call(raw, "getBaseStack");
    if (!(nativeBase instanceof ItemStack)) throw fault("Missing native BigItemStack base");
    ItemStack base = ((ItemStack) nativeBase).copy();
    Object active = call(raw, "hasOreDict");
    if (!(active instanceof Boolean)) throw fault("Invalid native ore availability");
    JsonArray candidates = array();
    if ((Boolean) active) {
      Object ingredient = call(raw, "getOreIngredient"),
          matched = call(ingredient, "getMatchingStacks");
      if (!(matched instanceof ItemStack[])
          || ((ItemStack[]) matched).length > 65536
          || ((ItemStack[]) matched).length == 0) throw fault("Invalid native ore alternatives");
      for (ItemStack candidate : (ItemStack[]) matched)
        candidates.add(pattern(candidate, items, budget));
    } else candidates.add(pattern(base, items, budget));
    return object(
        "base",
        pattern(base, items, budget),
        "amount",
        integer(raw, "stackSize"),
        "oreKey",
        string(call(raw, "getOreDict")),
        "oreActive",
        active,
        "candidates",
        candidates);
  }

  private static JsonObject pattern(
      ItemStack original, Map<String, ItemStack> items, Budget budget) {
    budget.claim();
    if (original == null || original.getItem() == null)
      throw fault("Empty native quest item pattern");
    ItemStack stack = original.copy();
    stack.stackSize = 1;
    String registry = Item.itemRegistry.getNameForObject(stack.getItem());
    if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem())
      throw fault("Unregistered quest item");
    JsonArray examples = array();
    if (stack.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
      List<ItemStack> variants = new ArrayList<>();
      stack.getItem().getSubItems(stack.getItem(), CreativeTabs.tabAllSearch, variants);
      if (variants.size() > 65536) throw fault("Quest wildcard examples exceed budget");
      for (ItemStack candidate : variants) {
        budget.claim();
        if (candidate == null
            || candidate.getItem() != stack.getItem()
            || candidate.getItemDamage() < 0
            || candidate.getItemDamage() == OreDictionary.WILDCARD_VALUE)
          throw fault("Invalid native quest wildcard example");
        ItemStack copy = candidate.copy();
        copy.setTagCompound(
            stack.getTagCompound() == null
                ? null
                : (net.minecraft.nbt.NBTTagCompound) stack.getTagCompound().copy());
        examples.add(value(item(copy, items)));
      }
    } else examples.add(value(item(stack, items)));
    return object(
        "registry",
        registry,
        "meta",
        stack.getItemDamage(),
        "nbt",
        TypedNbt.encode(stack.getTagCompound()),
        "damageable",
        stack.getItem().isDamageable(),
        "examples",
        examples);
  }

  private static JsonObject block(Object raw, Map<String, ItemStack> items, Budget budget) {
    budget.claim();
    if (raw == null || !raw.getClass().getName().equals("bq_standard.NbtBlockType"))
      throw fault("Unadapted block declaration");
    Object nativeBlock = field(raw, "b");
    if (nativeBlock != null && !(nativeBlock instanceof Block))
      throw fault("Invalid native quest block");
    String registry =
        nativeBlock == null ? null : Block.blockRegistry.getNameForObject(nativeBlock);
    if (nativeBlock != null
        && (registry == null || Block.blockRegistry.getObject(registry) != nativeBlock))
      throw fault("Unregistered quest block");
    int meta = integer(raw, "m");
    JsonElement specimen = JsonNull.INSTANCE;
    if (nativeBlock != null && Item.getItemFromBlock((Block) nativeBlock) != null) {
      ItemStack stack =
          new ItemStack((Block) nativeBlock, 1, meta < 0 ? OreDictionary.WILDCARD_VALUE : meta);
      specimen = pattern(stack, items, budget);
    }
    Object ore = field(raw, "oreDict");
    return object(
        "registry",
        registry,
        "meta",
        meta,
        "amount",
        integer(raw, "n"),
        "oreKey",
        ore == null ? null : string(ore),
        "nbt",
        nbt(field(raw, "tags")),
        "specimen",
        specimen);
  }

  private static String item(ItemStack original, Map<String, ItemStack> items) {
    ItemStack stack = original.copy();
    stack.stackSize = 1;
    String registry = Item.itemRegistry.getNameForObject(stack.getItem());
    if (registry == null || Item.itemRegistry.getObject(registry) != stack.getItem())
      throw fault("Unregistered quest item sample");
    String id =
        Identity.item(registry, stack.getItemDamage(), TypedNbt.encode(stack.getTagCompound()));
    items.put(id, stack);
    return id;
  }

  private static String fluid(FluidStack original, Map<String, FluidStack> fluids) {
    if (original.getFluid() == null
        || FluidRegistry.getFluid(original.getFluid().getName()) != original.getFluid())
      throw fault("Unregistered quest fluid");
    FluidStack sample = original.copy();
    sample.amount = 1;
    String id = Identity.fluid(sample.getFluid().getName(), TypedNbt.encode(sample.tag));
    fluids.put(id, sample);
    return id;
  }

  static List<?> list(Object raw) {
    if (!(raw instanceof List) || ((List<?>) raw).size() > 65536)
      throw fault("Invalid or oversized native quest declaration list");
    return (List<?>) raw;
  }

  static Object call(Object target, String name) {
    if (target == null) throw fault("Null native quest callback owner");
    return invoke(target.getClass(), target, name, new Class<?>[0]);
  }

  static String string(Object raw) {
    if (!(raw instanceof String) || ((String) raw).length() > 131072)
      throw fault("Invalid native quest string");
    return (String) raw;
  }

  static String enumName(Object raw) {
    if (!(raw instanceof Enum)) throw fault("Invalid native quest enum");
    return ((Enum<?>) raw).name();
  }

  static int integer(Object value, String key) {
    Object raw = field(value, key);
    if (!(raw instanceof Integer)) throw fault("Invalid native quest integer: " + key);
    return (Integer) raw;
  }

  private static boolean bool(Object value, String key) {
    Object raw = field(value, key);
    if (!(raw instanceof Boolean)) throw fault("Invalid native quest boolean: " + key);
    return (Boolean) raw;
  }

  private static void booleans(JsonObject rule, Object value, String... keys) {
    for (String key : keys) rule.addProperty(key, bool(value, key));
  }

  private static JsonElement nbt(Object raw) {
    if (raw != null && !(raw instanceof NBTBase)) throw fault("Invalid native quest NBT");
    return TypedNbt.encode((NBTBase) raw);
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("handler_unsupported", "BetterQuesting: " + message);
  }
}
