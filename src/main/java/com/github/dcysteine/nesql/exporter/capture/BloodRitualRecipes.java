package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** Pinned Blood Magic 1.7.52 altar and world rituals, captured without running a world effect. */
final class BloodRitualRecipes implements RegistryRecipes {
  private static final String ROOT = "WayofTime.alchemicalWizardry.";
  private static final String NEI = ROOT + "client.nei.";
  private static final String ALTAR = ROOT + "api.altarRecipeRegistry.AltarRecipe";
  private static final String BINDING = ROOT + "api.bindingRegistry.BindingRecipe";
  private static final String METEOR = ROOT + "common.summoning.meteor.Meteor";
  private static final String COMPONENT = ROOT + "common.summoning.meteor.MeteorComponent";
  private BloodRitualBindings source;
  private NativeDefinitions definitions;
  private boolean closed;
  private final String family;
  private final List<Entry> entries;
  private final List<Component> modifierComponents = new ArrayList<>();
  private final IdentityHashMap<Object, String> reagentIds = new IdentityHashMap<>();
  private JsonArray reagentDefinitions = array();
  private JsonObject ritual;
  private Component fallback;
  private JsonArray modifiers;
  private final boolean destroyBlocks;
  private final String fingerprint;
  private List<Object> references, observing;
  private int capturedMembers;

  static boolean supports(ICraftingHandler value) {
    return supportsClass(value.getClass().getName());
  }

  static boolean supportsClass(String name) {
    return (NEI + "NEIAltarRecipeHandler").equals(name)
        || (NEI + "NEIBindingRitualHandler").equals(name)
        || (NEI + "NEIMeteorRecipeHandler").equals(name);
  }

  BloodRitualRecipes(TemplateRecipeHandler handler) {
    source = new BloodRitualBindings(handler);
    family = source.family;
    definitions =
        new NativeDefinitions("Blood " + family, source.recipes, source::current, this::definition);
    ritual = family.equals("altar") || source.recipes.isEmpty() ? null : ritualSnapshot();
    List<Entry> captured = new ArrayList<>();
    List<?> registry = registry();
    for (int index = 0; index < registry.size(); index++) {
      Jobs.checkpoint();
      Object recipe = registry.get(index);
      // A fillable altar entry charges a network; its null item result is intentional.
      if (recipe == null) throw fault("Null entry interrupts native Blood registry selection");
      if (family.equals("altar")
          && field(recipe, "result") == null
          && !(Boolean) field(recipe, "canBeFilled")) continue;
      if (family.equals("binding") && field(recipe, "outputItem") == null) continue;
      captured.add(new Entry(recipe, index));
    }
    if (family.equals("meteor"))
      captured.sort(Comparator.comparingInt(entry -> entry.cost)); // Native stable cost ordering.
    entries = captured;
    if (family.equals("meteor") && !registry.isEmpty()) {
      Object nativeFallback = field(type(COMPONENT), null, "defaultMeteorBlock");
      if (nativeFallback == null) throw fault("Meteor default fallback block is unavailable");
      fallback = new Component(nativeFallback);
      modifiers = modifierSnapshot();
      destroyBlocks =
          (Boolean) field(type(ROOT + "AlchemicalWizardry"), null, "doMeteorsDestroyBlocks");
    } else {
      fallback = null;
      modifiers = array();
      destroyBlocks = false;
    }
    fingerprint = signature();
    finishVerification();
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
    definitions.finishVerification();
    if (!fingerprint.equals(signature()))
      throw new Jobs.Fault(
          "registry_drift",
          "Blood Magic " + family + " registry or ritual configuration changed during capture");
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (definitions != null) definitions.close();
    definitions = null;
    source = null;
    entries.clear();
    modifierComponents.clear();
    reagentIds.clear();
    reagentDefinitions = null;
    if (references != null) references.clear();
    references = null;
    observing = null;
    ritual = null;
    fallback = null;
    modifiers = null;
  }

  private void alive() {
    if (closed) throw new Jobs.Fault("capture_closed", "Blood ritual reader is closed");
  }

  public boolean capture(int index, RecipeRow row) {
    verify();
    Entry entry = entries.get(index);
    return definitions.checked(
        entry.index,
        () -> {
          boolean result =
              family.equals("altar")
                  ? altar(entry, row)
                  : family.equals("binding") ? binding(entry, row) : meteor(entry, row);
          source.verify();
          return result;
        });
  }

  private void input(Entry entry, RecipeRow row, boolean keep, JsonObject rule) {
    row.itemInput(0,
        Collections.singletonList(new RecipeRow.Ingredient(entry.input.copy(), 1, keep, rule)),
        false);
  }

  private boolean altar(Entry entry, RecipeRow row) {
    JsonArray keys = array();
    boolean tagged = entry.useTag && entry.input.hasTagCompound();
    if (tagged) {
      List<String> names = new ArrayList<>();
      for (Object key : entry.input.getTagCompound().func_150296_c()) names.add((String) key);
      Collections.sort(names);
      for (String key : names) keys.add(value(key));
    }
    JsonObject process =
        object(
            "kind",
            "bloodmagic-altar",
            "nativeIndex",
            entry.index,
            "tier",
            entry.tier,
            "earlier",
            entry.earlier,
            "lp",
            entry.lp,
            "consumptionRate",
            entry.rate,
            "drainRate",
            entry.drain,
            "canBeFilled",
            entry.fill,
            "inputCount",
            "whole-stack",
            "selection",
            "first-matching-registry-entry",
            "nbt",
            tagged ? "native-presence-no-compound-at-required-keys" : "ignore",
            "nbtKeys",
            keys,
            "result",
            entry.fill ? "soul-network-charge" : "stack-conversion",
            "serverWorld",
            true,
            "startRequiresPositiveTank",
            true,
            "upgrades",
            altarUpgrades(),
            "numeric",
            "java-int-float-operations",
            "recheckResultOnCompletion",
            true);
    boolean orb = type(ROOT + "api.items.interfaces.IBloodOrb").isInstance(entry.input.getItem());
    if (entry.fill) {
      if (!orb)
        throw fault(
            "Altar entry "
                + entry.index
                + " is fillable but TEAltar returns without changing non-IBloodOrb input "
                + entry.input.getItem().getClass().getName());
      process.add("orb", copy(entry.orbParameters));
    }
    input(
        entry, row, entry.fill, object("kind", "bloodmagic-altar", "tagged", tagged, "keys", keys));
    if (!entry.fill)
      row.inputs
          .get(0)
          .getAsJsonObject()
          .getAsJsonArray("choices")
          .get(0)
          .getAsJsonObject()
          .add("consume", object("kind", "stack"));
    if (!entry.fill)
      row.itemOutput(0,
          entry.output.copy(),
          10000,
          object("kind", "bloodAltar", "nominal", Integer.toString(entry.output.stackSize)));
    row.record.add("process", process);
    return true;
  }

  private boolean binding(Entry entry, RecipeRow row) {
    JsonObject process = copy(ritual);
    process.addProperty("kind", "bloodmagic-binding");
    process.addProperty("nativeIndex", entry.index);
    process.add("earlier", copyArray(entry.earlier));
    process.addProperty("selection", "first-matching-registry-entry");
    process.addProperty("result", "world-entity-after-cooldown");
    process.add("itemBounds", array(0, 1, 0, 1, 2, 1));
    process.addProperty("state", "var1-is-selected-index-plus-one");
    process.addProperty(
        "cooldownRule", "decrement-per-affordable-refresh-then-spawn-at-nonpositive");
    process.addProperty("refreshDebit", "only-idle-scan-branch-including-no-match");
    process.addProperty("networkGate", "refresh-cost-required-in-both-idle-and-pending-branches");
    process.add("spawnOffset", array(0.5, 1, 0.5));
    input(entry, row, false, object("kind", "wildcard", "meta", false, "nbt", true));
    row.itemOutput(0, entry.output.copy(), 10000);
    row.record.add("process", process);
    return true;
  }

  private boolean meteor(Entry entry, RecipeRow row) {
    JsonObject process = copy(ritual);
    process.add("earlier", copyArray(entry.earlier));
    process.addProperty("kind", "bloodmagic-meteor");
    process.addProperty("nativeIndex", entry.index);
    process.addProperty("cost", entry.cost);
    process.addProperty("radius", entry.radius);
    process.addProperty("fillerChance", entry.fillerChance);
    process.addProperty("result", "spatial-weighted-blocks");
    process.addProperty("selection", "first-matching-registry-entry");
    process.add("itemBounds", array(0, 1, 0, 1, 2, 1));
    process.addProperty("reagentCost", 1000);
    process.add("ores", components(entry.ores, row.facts));
    process.add("filler", components(entry.filler, row.facts));
    process.add("fallback", component(fallback, row.facts));
    process.add("reagents", copyArray(modifiers));
    // Modifier pools carry facts too; no sample block is promoted to a guaranteed item output.
    for (int modifier = 0, offset = 0; modifier < modifiers.size(); modifier++) {
      JsonObject value = process.getAsJsonArray("reagents").get(modifier).getAsJsonObject();
      int count = value.get("fillerCount").getAsInt();
      JsonArray pool = array();
      for (int item = 0; item < count; item++)
        pool.add(component(modifierComponents.get(offset++), row.facts));
      value.add("filler", pool);
      value.remove("fillerCount");
    }
    process.add("reagentIdentities", copyArray(reagentDefinitions));
    process.addProperty("destroyBlocks", destroyBlocks);
    process.add(
        "generation",
        object(
            "shape",
            "integer-offsets-strict-sphere-radius-plus-half",
            "eligibility",
            "air-after-optional-explosion",
            "poolChoice",
            "world-nextFloat-times-100-less-than-modified-fillerChance",
            "componentChoice",
            "world-nextInt-totalWeight-cumulative-subtraction",
            "reagentPredicate",
            "any-required-reagent-identity-or-no-requirement",
            "emptyPool",
            "native-default-fallback",
            "fillerOverride",
            "concatenate-active-reagent-fillers-in-active-reagent-order-unless-filtered-first-is-default",
            "radiusRule",
            "max-1-base-plus-largest-positive-and-most-negative-radiusChange",
            "fillerRule",
            "native-float-extrema-raw-adjustment-then-relative-increase-else-decrease-clamp-0-100",
            "explosion",
            "unless-any-active-disableExplosions-strength-modified-radius-times-4",
            "destroyRule",
            "invert-config-on-any-active-invertExplosionBlockDamage",
            "placement",
            "subtract-every-weight-skip-null-or-nonblock-then-call-native-placeBlockAt-stop-even-if-false",
            "placementSuccess",
            "runtime-world-and-native-block-callback-gate",
            "jointCounts",
            "one-selected-block-per-eligible-site-not-independent-yields"));
    process.addProperty("spawnY", 257);
    process.addProperty("deactivateAfterSummon", true);
    input(
        entry,
        row,
        false,
        object(
            "kind",
            "wildcard",
            "meta",
            Items.feather.getDamage(entry.input) == 32767,
            "nbt",
            true));
    row.record.add("process", process);
    return true;
  }

  private List<?> registry() {
    return source.current();
  }

  private JsonObject ritualSnapshot() {
    String key = family.equals("binding") ? "AW006Binding" : "AW019FallingTower";
    Object raw = field(type(ROOT + "api.rituals.Rituals"), null, "ritualMap");
    if (!(raw instanceof Map)) throw fault("Invalid Blood ritual map");
    Object definition = ((Map<?, ?>) raw).get(key);
    if (definition == null || !definition.getClass().getName().equals(ROOT + "api.rituals.Rituals"))
      throw fault("Missing native ritual " + key);
    Object effect = field(definition, "effect");
    String effectClass =
        ROOT
            + "common.rituals."
            + (family.equals("binding") ? "RitualEffectBinding" : "RitualEffectSummonMeteor");
    if (effect == null || !effect.getClass().getName().equals(effectClass))
      throw fault("Unadapted ritual effect for " + key);
    JsonArray components = array();
    for (Object component :
        list(
            invoke(effect.getClass(), effect, "getRitualComponentList", new Class<?>[0]),
            "Ritual components")) {
      if (component == null
          || !component.getClass().getName().equals(ROOT + "api.rituals.RitualComponent"))
        throw fault("Unknown ritual component callback");
      components.add(
          object(
              "x",
              field(component, "x"),
              "y",
              field(component, "y"),
              "z",
              field(component, "z"),
              "stone",
              field(component, "stoneType")));
    }
    int activation = integer(definition, "actCost"), crystal = integer(definition, "crystalLevel");
    int cooldown =
        ((Number) invoke(effect.getClass(), effect, "getInitialCooldown", new Class<?>[0]))
            .intValue();
    int refresh =
        family.equals("binding")
            ? ((Number) invoke(effect.getClass(), effect, "getCostPerRefresh", new Class<?>[0]))
                .intValue()
            : 0;
    if (activation < 0 || crystal < 0 || refresh < 0 || cooldown < 0 || components.size() == 0)
      throw fault("Invalid native ritual parameters for " + key);
    return object(
        "ritual",
        key,
        "activationCost",
        activation,
        "crystalLevel",
        crystal,
        "refreshCost",
        refresh,
        "cooldown",
        cooldown,
        "components",
        components,
        "ownerNetwork",
        true,
        "activeRitual",
        true,
        "eventGate",
        true,
        "structureRule",
        "native-valid-rotated-component-structure",
        "activationGate",
        "native-crystal-level-and-owner-network-and-event");
  }

  private JsonArray modifierSnapshot() {
    Object raw =
        field(type(ROOT + "common.summoning.meteor.MeteorReagentRegistry"), null, "reagents");
    if (!(raw instanceof Map) || ((Map<?, ?>) raw).size() > 65536)
      throw fault("Invalid meteor reagent registry");
    JsonArray result = array();
    for (Map.Entry<?, ?> modifier : ((Map<?, ?>) raw).entrySet()) {
      String id = reagent(modifier.getKey());
      Object nativeModifier = modifier.getValue();
      if (nativeModifier == null
          || !nativeModifier
              .getClass()
              .getName()
              .equals(ROOT + "common.summoning.meteor.MeteorReagent"))
        throw fault("Unknown meteor reagent modifier");
      Object filler = field(nativeModifier, "filler");
      List<Component> pool = filler == null ? Collections.emptyList() : readComponents(filler);
      modifierComponents.addAll(pool);
      result.add(
          object(
              "reagent",
              id,
              "radiusChange",
              field(nativeModifier, "radiusChange"),
              "fillerChanceChange",
              field(nativeModifier, "fillerChanceChange"),
              "rawFillerChanceChange",
              finite(nativeModifier, "rawFillerChanceChange"),
              "disableExplosions",
              field(nativeModifier, "disableExplosions"),
              "invertExplosionBlockDamage",
              field(nativeModifier, "invertExplosionBlockDamage"),
              "fillerCount",
              pool.size()));
    }
    return result;
  }

  private JsonArray components(List<Component> pool, Facts facts) {
    JsonArray values = array();
    for (Component entry : pool) values.add(component(entry, facts));
    return values;
  }

  private JsonObject component(Component component, Facts facts) {
    JsonArray required = array();
    for (Object value : component.required) required.add(value(reagent(value)));
    boolean block = component.stack != null && component.stack.getItem() instanceof ItemBlock;
    String placement = null;
    if (block) {
      try {
        placement =
            component
                .stack
                .getItem()
                .getClass()
                .getMethod(
                    "placeBlockAt",
                    ItemStack.class,
                    net.minecraft.entity.player.EntityPlayer.class,
                    net.minecraft.world.World.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class,
                    float.class,
                    float.class,
                    float.class,
                    int.class)
                .getDeclaringClass()
                .getName();
      } catch (NoSuchMethodException failure) {
        throw fault("Missing native meteor placement callback");
      }
    }
    return object(
        "id",
        component.stack == null ? null : facts.item(component.stack),
        "weight",
        component.weight,
        "required",
        required,
        "isBlock",
        block,
        "placementCallback",
        placement,
        "placementOutcome",
        "runtime-native-callback",
        "default",
        component.isDefault);
  }

  private String reagent(Object nativeReagent) {
    if (nativeReagent == null
        || !nativeReagent.getClass().getName().equals(ROOT + "api.alchemy.energy.Reagent"))
      throw fault("Unknown native meteor reagent identity");
    String id = reagentIds.get(nativeReagent);
    if (id == null) {
      String name = (String) field(nativeReagent, "name");
      if (name == null || name.length() > 65535) throw fault("Invalid meteor reagent name");
      id = "reagent" + reagentIds.size();
      reagentIds.put(nativeReagent, id);
      reagentDefinitions.add(object("id", id, "name", name));
    }
    return id;
  }

  private List<Component> readComponents(Object raw) {
    List<Component> result = new ArrayList<>();
    for (Object component : list(raw, "Meteor components")) {
      member();
      result.add(new Component(component));
    }
    return Collections.unmodifiableList(result);
  }

  private JsonArray earlier(int limit) {
    JsonArray result = array();
    List<?> registry = registry();
    for (int i = 0; i < limit; i++) {
      member();
      Object source = registry.get(i);
      if (source == null) throw fault("Null entry interrupts native Blood registry selection");
      ItemStack input =
          (ItemStack) field(source, family.equals("meteor") ? "focusItem" : "requiredItem");
      if (input == null) continue; // Native predicates return false for missing templates.
      String name = Item.itemRegistry.getNameForObject(input.getItem());
      if (name == null) throw fault("Unregistered native Blood predecessor input");
      boolean tagged =
          family.equals("altar") && (Boolean) field(source, "useTag") && input.hasTagCompound();
      JsonArray keys = array();
      if (tagged) {
        List<String> names = new ArrayList<>();
        for (Object key : input.getTagCompound().func_150296_c()) names.add((String) key);
        Collections.sort(names);
        for (String key : names) keys.add(value(key));
      }
      result.add(
          object(
              "registry",
              name,
              "meta",
              Items.feather.getDamage(input),
              "metadataWildcard",
              family.equals("meteor") && Items.feather.getDamage(input) == 32767,
              "tier",
              family.equals("altar") ? integer(source, "minTier") : 0,
              "tagged",
              tagged,
              "keys",
              keys));
    }
    return result;
  }

  private String signature() {
    observing = new ArrayList<>();
    try {
      JsonArray values = array();
      for (Object recipe : registry()) {
        values.add(definition(recipe));
      }
      if (!family.equals("altar") && !registry().isEmpty()) values.add(ritualSnapshot());
      if (family.equals("meteor") && !registry().isEmpty()) {
        values.add(
            componentSignature(
                Collections.singletonList(field(type(COMPONENT), null, "defaultMeteorBlock"))));
        JsonArray currentModifiers = array();
        Object raw =
            field(type(ROOT + "common.summoning.meteor.MeteorReagentRegistry"), null, "reagents");
        if (!(raw instanceof Map)) throw fault("Invalid meteor reagent map");
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
          Jobs.checkpoint();
          track(entry.getKey());
          track(entry.getValue());
          Object modifier = entry.getValue();
          currentModifiers.add(
              object(
                  "reagentIdentity",
                  System.identityHashCode(entry.getKey()),
                  "name",
                  field(entry.getKey(), "name"),
                  "radius",
                  field(modifier, "radiusChange"),
                  "chance",
                  field(modifier, "fillerChanceChange"),
                  "rawChance",
                  field(modifier, "rawFillerChanceChange"),
                  "explosion",
                  field(modifier, "disableExplosions"),
                  "destroy",
                  field(modifier, "invertExplosionBlockDamage"),
                  "filler",
                  field(modifier, "filler") == null
                      ? null
                      : componentSignature(field(modifier, "filler"))));
        }
        values.add(currentModifiers);
        values.add(value(field(type(ROOT + "AlchemicalWizardry"), null, "doMeteorsDestroyBlocks")));
      }
      String result = CanonicalJson.digest(values);
      if (references == null) references = observing;
      else {
        if (references.size() != observing.size())
          throw new Jobs.Fault("registry_drift", "Blood native member identities changed");
        for (int i = 0; i < references.size(); i++)
          if (references.get(i) != observing.get(i))
            throw new Jobs.Fault("registry_drift", "Blood native member identity changed at " + i);
      }
      return result;
    } finally {
      observing = null;
    }
  }

  private JsonElement definition(Object recipe) {
    Jobs.checkpoint();
    track(recipe);
    if (recipe == null) throw fault("Null entry interrupts native Blood registry selection");
    String expected = family.equals("altar") ? ALTAR : family.equals("binding") ? BINDING : METEOR;
    if (!recipe.getClass().getName().equals(expected))
      throw fault("Unadapted native Blood " + family + " recipe: " + recipe.getClass().getName());
    if (family.equals("altar"))
      return object(
          "input",
          stack((ItemStack) field(recipe, "requiredItem")),
          "output",
          stack((ItemStack) field(recipe, "result")),
          "tier",
          field(recipe, "minTier"),
          "lp",
          field(recipe, "liquidRequired"),
          "rate",
          field(recipe, "consumptionRate"),
          "drain",
          field(recipe, "drainRate"),
          "fill",
          field(recipe, "canBeFilled"),
          "tags",
          field(recipe, "useTag"),
          "orb",
          (Boolean) field(recipe, "canBeFilled")
                  && field(recipe, "requiredItem") != null
                  && type(ROOT + "api.items.interfaces.IBloodOrb")
                      .isInstance(((ItemStack) field(recipe, "requiredItem")).getItem())
              ? orb(((ItemStack) field(recipe, "requiredItem")).getItem())
              : null);
    if (family.equals("binding"))
      return object(
          "input",
          stack((ItemStack) field(recipe, "requiredItem")),
          "output",
          stack((ItemStack) field(recipe, "outputItem")));
    track(field(recipe, "ores"));
    track(field(recipe, "filler"));
    return object(
        "focus",
        stack((ItemStack) field(recipe, "focusItem")),
        "radius",
        field(recipe, "radius"),
        "cost",
        field(recipe, "cost"),
        "chance",
        field(recipe, "fillerChance"),
        "ores",
        componentSignature(field(recipe, "ores")),
        "filler",
        componentSignature(field(recipe, "filler")));
  }

  private JsonArray componentSignature(Object raw) {
    JsonArray result = array();
    for (Object entry : list(raw, "Meteor component signature")) {
      Jobs.checkpoint();
      track(entry);
      if (entry == null || !entry.getClass().getName().equals(COMPONENT))
        throw fault("Unknown meteor component in snapshot guard");
      JsonArray required = array();
      Object reagents = field(entry, "reagent");
      track(reagents);
      for (Object reagent : list(reagents, "Meteor required reagents")) {
        Jobs.checkpoint();
        track(reagent);
        required.add(
            object("identity", System.identityHashCode(reagent), "name", field(reagent, "name")));
      }
      result.add(
          object(
              "stack",
              stack((ItemStack) field(entry, "itemStack")),
              "weight",
              field(entry, "weight"),
              "required",
              required));
    }
    return result;
  }

  private static JsonObject altarUpgrades() {
    return object(
        "speedPerRune",
        0.25,
        "decayPerSpeedRune",
        0.8,
        "baseCapacity",
        10000,
        "betterCapacityBase",
        1.14,
        "capacityPerRune",
        0.35,
        "bufferBaseCapacity",
        1000,
        "displacementBase",
        1.5,
        "orbCapacityPerRune",
        0.04,
        "quicknessBaseDelay",
        100,
        "quicknessDecayPerRune",
        0.8,
        "quicknessMinimumDelay",
        1,
        "rateRule",
        "tier-below-2-base-else-int-base-times-1-plus-0.25-speedRunes",
        "drainRule",
        "int-base-drain-times-float-0.8-power-speedRunes",
        "quicknessRule",
        "max-1-floor-100-times-0.8-power-quicknessRunes",
        "acceleration",
        "native-buffer-transfer-tick-period",
        "capacityRule",
        "int-10000-times-float-1.14-power-betterRunes-plus-0.35-capacityRunes");
  }

  private static JsonObject orb(Item item) {
    int maximum = BloodOrbShapedRecipes.maxEssence(item);
    boolean free = BloodOrbShapedRecipes.filledForFree(item);
    return object(
        "maxEssence",
        maximum,
        "free",
        free,
        "ownerTag",
        "ownerName",
        "requireOwner",
        true,
        "input",
        "keep",
        "capacityRule",
        "int-maxEssence-times-float-1-plus-0.04-orbCapacityRunes",
        "debit",
        "only-LP-accepted-by-network");
  }

  private static List<?> list(Object value, String label) {
    if (value == null
        || !(value.getClass() == ArrayList.class
            || value.getClass() == LinkedList.class
            || value.getClass() == Collections.singletonList(null).getClass()
            || value.getClass() == Collections.emptyList().getClass())
        || ((List<?>) value).size() > 65536)
      throw fault(label + " has unknown storage or exceeds capture budget");
    return (List<?>) value;
  }

  private static ItemStack owned(ItemStack value, String label) {
    if (value == null || value.getItem() == null || value.stackSize <= 0)
      throw fault("Invalid native Blood " + label + " stack");
    NativeStackAudit.audit(value);
    return value.copy();
  }

  private JsonElement stack(ItemStack stack) {
    if (stack == null) return value(null);
    track(stack);
    track(stack.getItem());
    track(stack.getTagCompound());
    NativeStackAudit.audit(stack);
    if (Item.itemRegistry.getNameForObject(stack.getItem()) == null)
      throw fault("Unregistered Blood registry stack identity");
    return object(
        "item",
        Item.itemRegistry.getNameForObject(stack.getItem()),
        "meta",
        Items.feather.getDamage(stack),
        "amount",
        stack.stackSize,
        "nbt",
        TypedNbt.encode(stack.getTagCompound()));
  }

  private static int integer(Object object, String fieldName) {
    return ((Number) field(object, fieldName)).intValue();
  }

  private static float finite(Object object, String fieldName) {
    float value = ((Number) field(object, fieldName)).floatValue();
    if (Float.isNaN(value) || Float.isInfinite(value))
      throw fault("Invalid native Blood numeric value: " + fieldName);
    return value;
  }

  private static JsonObject copy(JsonObject object) {
    return new JsonParser().parse(object.toString()).getAsJsonObject();
  }

  private static JsonArray copyArray(JsonArray array) {
    return new JsonParser().parse(array.toString()).getAsJsonArray();
  }

  private static JsonObject object(Object... fields) {
    Object[] values = fields.clone();
    for (int index = 1; index < values.length; index += 2) values[index] = numeric(values[index]);
    return com.github.dcysteine.nesql.exporter.source.Json.object(values);
  }

  private static JsonArray array(Object... values) {
    Object[] normalized = values.clone();
    for (int index = 0; index < normalized.length; index++)
      normalized[index] = numeric(normalized[index]);
    return com.github.dcysteine.nesql.exporter.source.Json.array(normalized);
  }

  private static Object numeric(Object value) {
    if (value instanceof Float || value instanceof Double) {
      if (!Double.isFinite(((Number) value).doubleValue()))
        throw fault("Nonfinite native Blood process parameter");
      return new JsonPrimitive((Number) value);
    }
    return value;
  }

  private static Jobs.Fault fault(String message) {
    return new Jobs.Fault("recipe_unsupported", message);
  }

  private void member() {
    Jobs.checkpoint();
    if (++capturedMembers > 262144)
      throw new Jobs.Fault("recipe_limit", "Blood ritual snapshot member budget exceeded");
  }

  private void track(Object value) {
    if (observing == null) return;
    Jobs.checkpoint();
    if (observing.size() >= 524288)
      throw new Jobs.Fault("recipe_limit", "Blood ritual identity budget exceeded");
    observing.add(value);
  }

  private final class Entry {
    final JsonArray earlier;
    final ItemStack input, output;
    final int index, tier, lp, rate, drain, radius, cost;
    final boolean fill, useTag;
    final JsonObject orbParameters;
    final float fillerChance;
    final List<Component> ores, filler;

    Entry(Object source, int index) {
      this.index = index;
      earlier = earlier(index);
      String expected =
          family.equals("altar") ? ALTAR : family.equals("binding") ? BINDING : METEOR;
      if (source == null || !source.getClass().getName().equals(expected))
        throw fault("Unadapted native Blood " + family + " recipe entry " + index);
      input =
          owned(
              (ItemStack) field(source, family.equals("meteor") ? "focusItem" : "requiredItem"),
              "input");
      ItemStack nativeOutput =
          family.equals("meteor")
              ? null
              : (ItemStack) field(source, family.equals("altar") ? "result" : "outputItem");
      output = nativeOutput == null ? null : owned(nativeOutput, "output");
      tier = family.equals("altar") ? integer(source, "minTier") : 0;
      lp = family.equals("altar") ? integer(source, "liquidRequired") : 0;
      rate = family.equals("altar") ? integer(source, "consumptionRate") : 0;
      drain = family.equals("altar") ? integer(source, "drainRate") : 0;
      fill = family.equals("altar") && (Boolean) field(source, "canBeFilled");
      useTag = family.equals("altar") && (Boolean) field(source, "useTag");
      orbParameters =
          fill && type(ROOT + "api.items.interfaces.IBloodOrb").isInstance(input.getItem())
              ? orb(input.getItem())
              : null;
      radius = family.equals("meteor") ? integer(source, "radius") : 0;
      cost = family.equals("meteor") ? integer(source, "cost") : 0;
      fillerChance = family.equals("meteor") ? finite(source, "fillerChance") : 0;
      ores =
          family.equals("meteor") ? readComponents(field(source, "ores")) : Collections.emptyList();
      filler =
          family.equals("meteor")
              ? readComponents(field(source, "filler"))
              : Collections.emptyList();
      if (tier < 0
          || lp < 0
          || rate < 0
          || drain < 0
          || cost < 0
          || (family.equals("meteor") && (radius < 1 || fillerChance < 0 || fillerChance > 100)))
        throw fault("Invalid native Blood " + family + " numeric recipe fields at " + index);
    }
  }

  private final class Component {
    final ItemStack stack;
    final int weight;
    final boolean isDefault;
    final List<Object> required;

    Component(Object source) {
      if (source == null || !source.getClass().getName().equals(COMPONENT))
        throw fault("Unknown native meteor component");
      isDefault = source == field(type(COMPONENT), null, "defaultMeteorBlock");
      ItemStack raw = (ItemStack) field(source, "itemStack");
      stack = raw == null ? null : owned(raw, "meteor component");
      weight = integer(source, "weight");
      required =
          Collections.unmodifiableList(
              new ArrayList<>(
                  list(field(source, "reagent"), "Meteor component reagent predicate")));
      for (Object reagent : required) reagent(reagent);
    }
  }
}
