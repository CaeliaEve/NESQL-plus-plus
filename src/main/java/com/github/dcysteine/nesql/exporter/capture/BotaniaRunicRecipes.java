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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

/**
 * Botania's packed, stable runic altar inventory, including rune refunds and dropped livingrock.
 */
final class BotaniaRunicRecipes implements RegistryRecipes {
  private static final String HANDLER =
      "vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar";
  private static final String RECIPE = "vazkii.botania.api.recipe.RecipeRuneAltar";
  private static final String HEAD = "vazkii.botania.common.crafting.recipe.HeadRecipe";
  private static final String API = "vazkii.botania.api.BotaniaAPI";
  private static final String BLOCKS = "vazkii.botania.common.block.ModBlocks";
  private static final String ITEMS = "vazkii.botania.common.item.ModItems";
  private static final String DEFAULT_NOTE = "botania:headDefaultName";
  private static final String DEFAULT_SOURCE =
      "net.minecraft.util.StatCollector#translateToLocal(item.nameTag.name)";
  private List<?> registry;
  private final List<Entry> entries = new ArrayList<>(), visible = new ArrayList<>();
  private Block rock, altar;
  private Item rune;
  private String defaultName;
  private boolean closed;
  private BotaniaRecipeBindings bindings;

  static boolean supports(ICraftingHandler handler) {
    return handler.getClass().getName().equals(HANDLER);
  }

  BotaniaRunicRecipes(TemplateRecipeHandler handler) {
    if (!supports(handler)) throw fault("Invalid runic altar handler");
    bindings = new BotaniaRecipeBindings(handler);
    registry = (List<?>) field(type(API), null, "runeAltarRecipes");
    if (registry.size() > 4096) throw fault("Invalid runic altar registry");
    rock = (Block) field(type(BLOCKS), null, "livingrock");
    altar = (Block) field(type(BLOCKS), null, "runeAltar");
    rune = (Item) field(type(ITEMS), null, "rune");
    if (rock == null || altar == null || rune == null || Item.getItemFromBlock(rock) == null)
      throw fault("Runic altar native item identities are unavailable");
    BotaniaBorrowed.Budget work = new BotaniaBorrowed.Budget();
    for (Object raw : registry) {
      Jobs.checkpoint();
      Entry entry = new Entry(raw, work);
      entries.add(entry);
      // Ordinary skulls are hidden. A head's bound name determines whether an NBT result query
      // can expose it; retain its source position so a hidden head gets exclusion evidence.
      if (entry.head || entry.output.getItem() != Items.skull) visible.add(entry);
    }
    for (Entry entry : entries) if (entry.head) {
      defaultName = nativeDefault();
      break;
    }
  }

  private void alive() {
    if (closed) throw new Jobs.Fault("capture_closed", "Native runic altar reader is closed");
  }

  public int size() {
    alive();
    return visible.size();
  }

  public void verify() {
    alive();
    if (bindings != null) bindings.verify();
    if (field(type(API), null, "runeAltarRecipes") != registry || registry.size() != entries.size())
      changed();
    if (field(type(BLOCKS), null, "livingrock") != rock
        || field(type(BLOCKS), null, "runeAltar") != altar
        || field(type(ITEMS), null, "rune") != rune) changed();
    if (defaultName != null && !defaultName.equals(nativeDefault())) changed();
    BotaniaBorrowed.Budget work = new BotaniaBorrowed.Budget();
    for (int i = 0; i < entries.size(); i++) {
      Jobs.checkpoint();
      Entry entry = entries.get(i);
      if (registry.get(i) != entry.source || !entry.same(new Entry(entry.source, work))) changed();
      for (Group group : entry.inputs)
        for (Pattern pattern : group.patterns)
          if (pattern.candidates != null) pattern.candidates.verify();
    }
  }

  public boolean capture(int index, RecipeRow row) {
    alive();
    if (defaultName != null && !defaultName.equals(nativeDefault())) changed();
    Entry entry = visible.get(index);
    if (!entry.same(new Entry(entry.source, new BotaniaBorrowed.Budget()))) changed();
    if (entry.head && entry.name.isEmpty())
      return row.exclude(object(
          "native", HEAD,
          "reason", "native_nei_player_skull_without_owner_is_hidden",
          "initialName", entry.name,
          "output", stackValue(entry.output),
          "entrypoints", array(
              "RecipeHandlerPetalApothecary#loadCraftingRecipes(String,Object[])",
              "RecipeHandlerPetalApothecary#loadCraftingRecipes(ItemStack)",
              "RecipeHandlerPetalApothecary#loadUsageRecipes(ItemStack)"),
          "proof", "classification_and_usage_skip_skulls_and_result_query_has_no_nbt_branch",
          "signature", entry.signature));
    if (!entry.reachable())
      return row.exclude(
          object(
              "native",
              entry.source.getClass().getName(),
              "reason",
              entry.mana <= 0
                  ? "native_runic_altar_requires_positive_mana"
                  : "empty_native_required_input_predicate",
              "mana",
              entry.mana,
              "signature",
              entry.signature));
    String sampleName = entry.name;
    if (entry.head && sampleName.equals(defaultName))
      sampleName = sampleName.equals("NESQL") ? "NESQL Head" : "NESQL";
    boolean offeredName = false;
    List<List<RecipeRow.Ingredient>> inputs = new ArrayList<>();
    for (int i = 0; i < entry.inputs.size(); i++) {
      List<RecipeRow.Ingredient> choices = new ArrayList<>();
      for (Pattern pattern : entry.inputs.get(i).patterns) {
        ItemStack sample = pattern.example();
        if (entry.head && sample.getItem() == Items.name_tag) {
          sample.setStackDisplayName(sampleName);
          offeredName = true;
        }
        choices.add(new RecipeRow.Ingredient(sample, 1, sample.getItem() == rune, pattern.rule()));
      }
      inputs.add(choices);
    }
    for (int i = 0; i < inputs.size(); i++) {
      row.itemInput(i, inputs.get(i), false);
    }
    // Completion uses an entity in the altar AABB, rather than an altar inventory slot.
    row.itemInput(16,
        Collections.singletonList(
            new RecipeRow.Ingredient(
                new ItemStack(rock),
                1,
                false,
                object("kind", "wildcard", "meta", true, "nbt", true))),
        false);
    if (entry.head && !offeredName) sampleName = entry.name;
    row.itemOutput(0, entry.head ? headOutput(sampleName) : entry.output.copy(), 10000);
    JsonArray earlier = new JsonArray(), earlierHeads = new JsonArray();
    BotaniaBorrowed.Budget budget = new BotaniaBorrowed.Budget();
    for (Entry prior : entries) {
      if (prior == entry) break;
      if (prior.head) earlierHeads.add(value(earlier.size()));
      earlier.add(predicates(prior, budget));
    }
    JsonObject process = object(
            "kind",
            "botaniaRunic",
            "version",
            "1.12.28-GTNH",
            "nativeRecipe",
            entry.source.getClass().getName(),
            "mana",
            entry.mana,
            "cooldown",
            60,
            "scope",
            "stablePackedInputsWithoutCachedOverride",
            "predicates",
            predicates(entry, budget),
            "earlier",
            earlier,
            "rune",
            Item.itemRegistry.getNameForObject(rune),
            "livingrock",
            Item.itemRegistry.getNameForObject(Item.getItemFromBlock(rock)),
            "matching",
            "physicalFirstRemainingPredicate",
            "settlement",
            "clearAltarRefundRunesAndConsumeDroppedLivingrock");
    if (earlierHeads.size() != 0) process.add("earlierHeads", earlierHeads);
    if (entry.head || earlierHeads.size() != 0) {
      process.addProperty("nameTagDefault", DEFAULT_NOTE);
      row.note(DEFAULT_NOTE, DEFAULT_SOURCE, defaultName);
    }
    if (entry.head) {
      process.add("head", object(
          "initialName", entry.name,
          "sampleName", sampleName,
          "nameTag", Item.itemRegistry.getNameForObject(Items.name_tag),
          "defaultNameKey", "item.nameTag.name",
          "matching", "packedNameTagsEachRejectLocalizedDefault",
          "output", "freshPlayerSkullFromLastNameTagOrInitialName",
          "visibility", "craftingNbtOutputQueryOnly"));
    }
    row.record.add("process", process);
    return true;
  }

  private static ItemStack headOutput(String name) {
    ItemStack result = new ItemStack(Items.skull, 1, 3);
    if (!name.isEmpty()) {
      NBTTagCompound tag = new NBTTagCompound(); tag.setString("SkullOwner", name);
      result.setTagCompound(tag);
    }
    return result;
  }

  private static String nativeDefault() {
    String value = StatCollector.translateToLocal("item.nameTag.name");
    if (value == null || value.isEmpty() || value.length() > 16384)
      throw fault("Native name-tag default observation is unavailable");
    String[][] names = {
        {"getItemStackDisplayName", "func_77653_i"},
        {"getUnlocalizedNameInefficiently", "func_77657_g"},
        {"getUnlocalizedName", "func_77667_c"}};
    for (String[] name : names)
      if (ItemCallbacks.method(Items.name_tag, name[0], name[1], ItemStack.class)
          .getDeclaringClass() != Item.class)
        throw fault("Unadapted native name-tag display callback");
    // The audited base Item method trims the localized fallback; HeadRecipe compares its own
    // untrimmed translation. Preserve both native operations without guessing a display label.
    if (!new ItemStack(Items.name_tag).getDisplayName().equals(value.trim()))
      throw fault("Native name-tag display key differs from HeadRecipe's default key");
    return value;
  }

  public void close() {
    if (closed) return;
    closed = true;
    entries.clear();
    visible.clear();
    registry = null;
    if (bindings != null) {
      bindings.close();
      bindings = null;
    }
    rock = null;
    altar = null;
    rune = null;
    defaultName = null;
  }

  private static JsonArray predicates(Entry entry, BotaniaBorrowed.Budget budget) {
    JsonArray groups = new JsonArray();
    for (Group group : entry.inputs) {
      JsonArray alternatives = new JsonArray();
      for (Pattern pattern : group.patterns) {
        budget.step();
        if (++budget.selectors > 65536) throw fault("Runic priority selector budget exceeded");
        String registry = Item.itemRegistry.getNameForObject(pattern.item.getItem());
        if (registry == null || registry.isEmpty())
          throw fault("Unregistered runic predicate item");
        alternatives.add(
            object(
                "registry",
                registry,
                "metadata",
                pattern.wildcard ? 32767 : pattern.item.getItemDamage()));
      }
      groups.add(alternatives);
    }
    return groups;
  }

  private static final class Entry {
    final Object source;
    final List<Group> inputs = new ArrayList<>();
    final ItemStack output;
    final boolean head;
    final String name;
    final int mana;
    final String signature;
    final BotaniaBorrowed borrowed;

    Entry(Object source, BotaniaBorrowed.Budget work) {
      this.source = source;
      borrowed = new BotaniaBorrowed(work);
      if (source == null
          || source.getClass() != type(RECIPE) && source.getClass() != type(HEAD))
        throw fault("Unadapted runic altar recipe callback");
      head = source.getClass() == type(HEAD);
      Object rawName = head ? field(type(HEAD), source, "name") : null;
      if (head && (!(rawName instanceof String) || ((String) rawName).length() > 16384))
        throw fault("Invalid native HeadRecipe bound name");
      name = (String) rawName;
      if (head) borrowed.bind(name);
      List<?> raw = (List<?>) field(source, "inputs");
      if (raw == null || raw.getClass() != ArrayList.class)
        throw fault("Unadapted native runic requirements owner");
      borrowed.bind(raw);
      if (raw == null || raw.size() > 16)
        throw fault("Runic altar requirements exceed its physical inventory");
      JsonArray values = new JsonArray();
      for (Object input : raw) {
        Group group = new Group(input, work, borrowed);
        inputs.add(group);
        values.add(value(group.signature));
      }
      Object prototype = field(source, "output");
      ItemStack result;
      if (head && prototype == null) { borrowed.bind(null); result = null; }
      else result = borrowed.stack(prototype);
      if (!head && (result == null || result.getItem() == null || result.stackSize <= 0))
        throw fault("Invalid runic altar output");
      // HeadRecipe ignores the inherited prototype and builds a fresh player skull. Its matches
      // method also mutates name, so neither borrowed callback may be used for discovery.
      output = head ? headOutput(name) : result.copy();
      mana = (Integer) field(source, "mana");
      JsonObject definition = object("nativeRecipe", source.getClass().getName(), "inputs", values,
          "output", stackValue(output), "mana", mana);
      if (head) {
        definition.add("prototype", result == null ? value(null) : stackValue(result));
        definition.addProperty("name", name);
      }
      work.definition(definition);
      signature = CanonicalJson.digest(definition);
    }

    boolean same(Entry other) {
      return signature.equals(other.signature) && borrowed.same(other.borrowed);
    }

    boolean reachable() {
      if (mana <= 0) return false;
      for (Group group : inputs) if (group.patterns.isEmpty()) return false;
      return true;
    }
  }

  private static final class Group {
    final List<Pattern> patterns = new ArrayList<>();
    final String signature;

    Group(Object source, BotaniaBorrowed.Budget work, BotaniaBorrowed borrowed) {
      borrowed.bind(source);
      List<?> raw;
      if (source instanceof String) raw = borrowed.ore((String) source);
      else if (source instanceof ItemStack) raw = Collections.singletonList(source);
      else throw fault("Unadapted runic altar input kind");
      if (raw.size() > 65536) throw fault("Runic altar ore candidates exceed their budget");
      JsonArray fingerprint = new JsonArray();
      for (Object value : raw) {
        work.step();
        if (!(value instanceof ItemStack) || ((ItemStack) value).getItem() == null)
          throw fault("Invalid runic altar candidate");
        ItemStack stack = borrowed.stack(value);
        if (ItemCallbacks.method(stack.getItem(), "getDamage", "getDamage", ItemStack.class)
                .getDeclaringClass()
            != Item.class) throw fault("Runic altar input overrides native metadata matching");
        boolean wildcard = source instanceof String && stack.getItemDamage() == 32767;
        if (wildcard
            && ItemCallbacks.method(
                        stack.getItem(), "setDamage", "setDamage", ItemStack.class, int.class)
                    .getDeclaringClass()
                != Item.class)
          throw fault("Runic altar ore candidate overrides wildcard metadata substitution");
        if (!wildcard && stack.getItemDamage() == 32767)
          throw fault("Literal runic altar wildcard metadata lacks a concrete native display");
        patterns.add(new Pattern(stack, wildcard));
        fingerprint.add(stackValue(stack));
      }
      signature =
          CanonicalJson.digest(
              object("kind", source instanceof String ? source : "stack", "values", fingerprint));
      for (int i = patterns.size() - 1; i >= 0; i--) {
        Pattern candidate = patterns.get(i);
        boolean redundant = false;
        for (int j = 0; j < patterns.size(); j++) {
          work.step();
          if (i != j
              && patterns.get(j).covers(candidate)
              && (j < i || !candidate.covers(patterns.get(j)))) {
            redundant = true;
            break;
          }
        }
        if (redundant) patterns.remove(i);
      }
    }
  }

  private static final class Pattern {
    final ItemStack item;
    final boolean wildcard;
    NativeItemVariants candidates;

    Pattern(ItemStack stack, boolean wildcard) {
      item = stack.copy();
      item.stackSize = 1;
      item.setTagCompound(null);
      this.wildcard = wildcard;
    }

    boolean covers(Pattern other) {
      return item.getItem() == other.item.getItem()
          && (wildcard || !other.wildcard && item.getItemDamage() == other.item.getItemDamage());
    }

    JsonObject rule() {
      return object("kind", "wildcard", "meta", wildcard, "nbt", true);
    }

    ItemStack example() {
      if (!wildcard) return item.copy();
      if (candidates == null) candidates = new NativeItemVariants(item);
      else candidates.verify();
      ItemStack[] choices = candidates.copies();
      if (choices == null || choices.length == 0 || choices.length > 65536)
        throw fault("Runic altar wildcard lacks bounded display candidates");
      for (ItemStack choice : choices)
        if (choice != null
            && choice.getItem() == item.getItem()
            && choice.getItemDamage() != 32767) {
          ItemStack owned = choice.copy();
          owned.stackSize = 1;
          owned.setTagCompound(null);
          return owned;
        }
      throw fault("Runic altar wildcard lacks a concrete native display");
    }
  }

  private static JsonObject stackValue(ItemStack item) {
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

  private static void changed() {
    throw new Jobs.Fault(
        "recipe_changed",
        "Botania runic altar registry or native identities changed during capture");
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }
}
