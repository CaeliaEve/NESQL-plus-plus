package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.JsonObject;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Conformance against unmodified native registry predicates and ritual parameters. */
public final class NativeBloodRitualTest {
  private static final String BASE = "WayofTime.alchemicalWizardry.";

  public static void run() throws Exception {
    Class<?> localeType = Class.forName("net.minecraft.client.resources.Locale");
    java.lang.reflect.Field localeField = null;
    for (java.lang.reflect.Field candidate :
        Class.forName("net.minecraft.client.resources.I18n").getDeclaredFields())
      if (candidate.getType() == localeType) {
        localeField = candidate;
        break;
      }
    require(localeField != null, "Native I18n locale field is missing");
    localeField.setAccessible(true);
    Object localeBefore = localeField.get(null);
    localeField.set(null, localeType.newInstance());
    Class<?> neiConfig = Class.forName(BASE + "client.nei.NEIConfig");
    java.lang.reflect.Field orbCache = neiConfig.getDeclaredField("bloodOrbs"),
        capacityCache = neiConfig.getDeclaredField("byCapacity");
    orbCache.setAccessible(true);
    capacityCache.setAccessible(true);
    Object orbsBefore = orbCache.get(null), capacityBefore = capacityCache.get(null);
    net.minecraft.item.Item orb =
        (net.minecraft.item.Item)
            Class.forName(BASE + "common.items.EnergyBattery")
                .getConstructor(int.class)
                .newInstance(100000);
    ArrayList<net.minecraft.item.Item> orbs = new ArrayList<>();
    orbs.add(orb);
    orbCache.set(null, orbs);
    capacityCache.set(null, new ArrayList<>(orbs));
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      net.minecraft.item.Item.itemRegistry.addObject(31991, "nesql_native:blood_orb", orb);
      altar();
      binding();
      meteor();
      priority();
      charging(orb);
      lifecycle();
    } finally {
      localeField.set(null, localeBefore);
      orbCache.set(null, orbsBefore);
      capacityCache.set(null, capacityBefore);
    }
    System.out.println(
        "Native Blood rituals: altar tier/count/NBT and upgrade parameters, binding world gates, meteor ordered weighted spatial process, immutable snapshots passed");
  }

  private static void altar() throws Exception {
    Class<?> nativeType = Class.forName(BASE + "api.altarRecipeRegistry.AltarRecipe");
    Constructor<?> constructor =
        nativeType.getConstructor(
            ItemStack.class,
            ItemStack.class,
            int.class,
            int.class,
            int.class,
            int.class,
            boolean.class,
            boolean.class);
    ItemStack input = new ItemStack(Items.paper, 7, 0), output = new ItemStack(Items.diamond, 2);
    input.setTagCompound(new NBTTagCompound());
    input.getTagCompound().setInteger("native", 10);
    Object recipe = constructor.newInstance(output, input, 3, 500, 20, 4, false, true);
    ItemStack differentTag = new ItemStack(Items.paper, 1);
    differentTag.setTagCompound(new NBTTagCompound());
    differentTag.getTagCompound().setInteger("native", 99);
    require(
        matches(nativeType, recipe, differentTag, 3),
        "Pinned altar leaf NBT comparator unexpectedly requires equality");
    require(
        !matches(nativeType, recipe, new ItemStack(Items.paper), 3),
        "Native tagged altar accepted absent NBT");
    require(!matches(nativeType, recipe, differentTag, 2), "Native altar ignored its tier gate");
    require(
        !matches(nativeType, recipe, new ItemStack(Items.paper, 1, 1), 3),
        "Native altar treated exact metadata as wildcard");
    Object wildcard =
        constructor.newInstance(
            output, new ItemStack(Items.paper, 1, 32767), 1, 1, 1, 1, false, false);
    require(
        !matches(nativeType, wildcard, new ItemStack(Items.paper), 1),
        "Native altar incorrectly gained ore wildcard semantics");
    List<Object> registry = registry("api.altarRecipeRegistry.AltarRecipeRegistry", "altarRecipes");
    List<Object> before = new ArrayList<>(registry);
    registry.clear();
    registry.add(recipe);
    try {
      TemplateRecipeHandler handler = handler("NEIAltarRecipeHandler");
      require(
          Recipes.adapter(handler) != null,
          "Native Blood ritual adapter is not routed in production");
      RegistryRecipes adapter = adapter(handler);
      require(adapter.size() == 1, "Altar semantic row count changed");
      RecipeRow row = NativeCoreFixesTest.row(input, output);
      require(adapter.capture(0, row), "Altar recipe disappeared");
      JsonObject process = row.record.getAsJsonObject("process");
      require(
          process.get("kind").getAsString().equals("bloodmagic-altar"), "Altar process missing");
      require(
          process.get("tier").getAsInt() == 3
              && process.get("lp").getAsInt() == 500
              && process.get("consumptionRate").getAsInt() == 20
              && process.get("drainRate").getAsInt() == 4,
          "Native altar parameters changed");
      require(
          process.get("inputCount").getAsString().equals("whole-stack")
              && process
                  .get("nbt")
                  .getAsString()
                  .equals("native-presence-no-compound-at-required-keys"),
          "Altar stack scaling or pinned NBT behavior was flattened");
      require(
          choice(row).get("amount").getAsString().equals("1")
              && choice(row).getAsJsonObject("rule").get("tagged").getAsBoolean()
              && choice(row).getAsJsonObject("consume").get("kind").getAsString().equals("stack"),
          "Altar NBT/whole-stack predicate changed");
      require(
          row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull()
              && row.outputs.get(0).getAsJsonObject().getAsJsonObject("quantity") != null
              && row.outputs
                  .get(0)
                  .getAsJsonObject()
                  .getAsJsonObject("quantity")
                  .get("kind")
                  .getAsString()
                  .equals("bloodAltar")
              && row.outputs
                  .get(0)
                  .getAsJsonObject()
                  .getAsJsonObject("quantity")
                  .get("nominal")
                  .getAsString()
                  .equals("2"),
          "Whole-stack altar output was flattened into a guaranteed base amount");
      write("blood-altar", row, handler, input, output);
      input.stackSize = 19;
      output.stackSize = 11;
      expectDrift(adapter);
      adapter.close();
      adapter.close();
      expect("capture_closed", () -> adapter.capture(0, row));
    } finally {
      registry.clear();
      registry.addAll(before);
    }
  }

  private static void binding() throws Exception {
    ItemStack input = new ItemStack(Items.iron_ingot, 9, 0),
        output = new ItemStack(Items.diamond, 3);
    Class<?> nativeType = Class.forName(BASE + "api.bindingRegistry.BindingRecipe");
    Object recipe =
        nativeType.getConstructor(ItemStack.class, ItemStack.class).newInstance(output, input);
    ItemStack tagged = new ItemStack(Items.iron_ingot, 1);
    tagged.setTagCompound(new NBTTagCompound());
    tagged.getTagCompound().setString("extra", "ignored");
    require(
        (Boolean)
            nativeType.getMethod("doesRequiredItemMatch", ItemStack.class).invoke(recipe, tagged),
        "Native binding matcher unexpectedly checks count or NBT");
    List<Object> registry = registry("api.bindingRegistry.BindingRegistry", "bindingRecipes");
    List<Object> before = new ArrayList<>(registry);
    Map<String, Object> rituals = rituals();
    Map<String, Object> ritualsBefore = new java.util.HashMap<>(rituals);
    Class<?> mod = Class.forName(BASE + "AlchemicalWizardry");
    Object costsBefore = field(mod, null, "ritualCostBinding");
    mod.getField("ritualCostBinding").set(null, new int[] {5000, 250});
    Object effect = Class.forName(BASE + "common.rituals.RitualEffectBinding").newInstance();
    rituals.put("AW006Binding", ritual(2, 5000, effect));
    registry.clear();
    registry.add(recipe);
    try {
      TemplateRecipeHandler handler = handler("NEIBindingRitualHandler");
      RegistryRecipes adapter = adapter(handler);
      RecipeRow row = NativeCoreFixesTest.row(input, output);
      require(adapter.capture(0, row), "Binding excluded");
      JsonObject process = row.record.getAsJsonObject("process");
      require(
          process.get("kind").getAsString().equals("bloodmagic-binding")
              && process.get("activationCost").getAsInt() == 5000
              && process.get("refreshCost").getAsInt() == 250
              && process.get("cooldown").getAsInt() == 200
              && process.get("crystalLevel").getAsInt() == 2,
          "Binding activation/refresh/cooldown requirements changed");
      require(
          process.getAsJsonArray("itemBounds").toString().equals("[0,1,0,1,2,1]")
              && process.get("result").getAsString().equals("world-entity-after-cooldown")
              && process.getAsJsonArray("components").size() > 0,
          "Binding world gates missing");
      require(
          choice(row).get("amount").getAsString().equals("1")
              && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"),
          "Binding unit input/output changed");
      write("blood-binding", row, handler, input, output);
      adapter.finishVerification();
      adapter.close();
    } finally {
      registry.clear();
      registry.addAll(before);
      rituals.clear();
      rituals.putAll(ritualsBefore);
      mod.getField("ritualCostBinding").set(null, costsBefore);
    }
  }

  private static void meteor() throws Exception {
    Class<?> component = Class.forName(BASE + "common.summoning.meteor.MeteorComponent");
    ItemStack ore = new ItemStack(Blocks.iron_ore), filler = new ItemStack(Blocks.stone);
    Object oreComponent = component.getConstructor(ItemStack.class, int.class).newInstance(ore, 3);
    Object otherComponent =
        component
            .getConstructor(ItemStack.class, int.class)
            .newInstance(new ItemStack(Blocks.gold_ore), 1);
    Object fillerComponent =
        component.getConstructor(ItemStack.class, int.class).newInstance(filler, 7);
    Object fallbackBefore = component.getField("defaultMeteorBlock").get(null);
    component.getField("defaultMeteorBlock").set(null, fillerComponent);
    List<Object> ores = new ArrayList<>();
    ores.add(oreComponent);
    ores.add(otherComponent);
    List<Object> fillers = new ArrayList<>();
    fillers.add(fillerComponent);
    require(
        ((Number) component.getMethod("getTotalListWeight", List.class).invoke(null, ores))
                .intValue()
            == 4,
        "Native meteor weights changed");
    Class<?> meteorType = Class.forName(BASE + "common.summoning.meteor.Meteor");
    Constructor<?> constructor =
        meteorType.getConstructor(
            ItemStack.class, int.class, int.class, int.class, List.class, List.class);
    ItemStack firstFocus = new ItemStack(Items.paper, 12, 32767),
        secondFocus = new ItemStack(Items.diamond);
    Object large = constructor.newInstance(firstFocus, 8, 9000, 25, ores, fillers);
    Object small = constructor.newInstance(secondFocus, 2, 1000, 0, ores, fillers);
    List<Object> registry = registry("common.summoning.meteor.MeteorRegistry", "meteorList");
    List<Object> before = new ArrayList<>(registry);
    Map<String, Object> rituals = rituals();
    Map<String, Object> ritualsBefore = new java.util.HashMap<>(rituals);
    Object effect = Class.forName(BASE + "common.rituals.RitualEffectSummonMeteor").newInstance();
    rituals.put("AW019FallingTower", ritual(1, 15000, effect));
    registry.clear();
    registry.add(large);
    registry.add(small);
    try {
      Class<?> registryType = Class.forName(BASE + "common.summoning.meteor.MeteorRegistry");
      require(
          ((Number)
                      registryType
                          .getMethod("getMeteorIDForItem", ItemStack.class)
                          .invoke(null, new ItemStack(Items.paper, 1, 17)))
                  .intValue()
              == 0,
          "Native meteor focus wildcard changed");
      TemplateRecipeHandler handler = handler("NEIMeteorRecipeHandler");
      RegistryRecipes adapter = adapter(handler);
      require(adapter.size() == 2, "Meteor semantic row count changed");
      RecipeRow row =
          NativeCoreFixesTest.row(
              firstFocus, secondFocus, ore, filler, new ItemStack(Blocks.gold_ore));
      require(adapter.capture(0, row), "Meteor excluded");
      JsonObject process = row.record.getAsJsonObject("process");
      require(
          process.get("kind").getAsString().equals("bloodmagic-meteor")
              && process.get("radius").getAsInt() == 2
              && process.get("cost").getAsInt() == 1000
              && process.get("nativeIndex").getAsInt() == 1,
          "Meteor sorted page does not own its native entry");
      require(
          row.outputs.size() == 0
              && process.get("result").getAsString().equals("spatial-weighted-blocks"),
          "Meteor was flattened into guaranteed item outputs");
      require(
          process.getAsJsonArray("ores").size() == 2
              && process.getAsJsonArray("ores").get(0).getAsJsonObject().get("weight").getAsInt()
                  == 3,
          "Meteor weighted pool changed");
      write(
          "blood-meteor",
          row,
          handler,
          firstFocus,
          secondFocus,
          ore,
          filler,
          new ItemStack(Blocks.gold_ore));
      RecipeRow second =
          NativeCoreFixesTest.row(
              firstFocus, secondFocus, ore, filler, new ItemStack(Blocks.gold_ore));
      require(adapter.capture(1, second), "Second meteor excluded");
      require(
          choice(second).getAsJsonObject("rule").get("meta").getAsBoolean()
              && second.record.getAsJsonObject("process").get("fillerChance").getAsInt() == 25,
          "Meteor focus or filler probability changed");
      ((List<?>) field(large, "ores")).clear();
      require(
          second.record.getAsJsonObject("process").getAsJsonArray("ores").size() == 2,
          "Meteor snapshot aliases registry");
      expectDrift(adapter);
      adapter.close();
    } finally {
      registry.clear();
      registry.addAll(before);
      rituals.clear();
      rituals.putAll(ritualsBefore);
      component.getField("defaultMeteorBlock").set(null, fallbackBefore);
    }
  }

  private static void priority() throws Exception {
    Class<?> nativeType = Class.forName(BASE + "api.altarRecipeRegistry.AltarRecipe");
    Constructor<?> ctor =
        nativeType.getConstructor(
            ItemStack.class,
            ItemStack.class,
            int.class,
            int.class,
            int.class,
            int.class,
            boolean.class,
            boolean.class);
    ItemStack input = new ItemStack(Items.paper), out = new ItemStack(Items.diamond);
    Object hidden = ctor.newInstance(null, input.copy(), 1, 100, 1, 1, false, false);
    Object later = ctor.newInstance(out, input.copy(), 3, 200, 2, 2, false, false);
    List<Object> registry = registry("api.altarRecipeRegistry.AltarRecipeRegistry", "altarRecipes");
    List<Object> before = new ArrayList<>(registry);
    registry.clear();
    registry.add(hidden);
    registry.add(later);
    try {
      Object selected =
          Class.forName(BASE + "api.altarRecipeRegistry.AltarRecipeRegistry")
              .getMethod("getAltarRecipeForItemAndTier", ItemStack.class, int.class)
              .invoke(null, input, 3);
      require(selected == hidden, "Native altar hidden predecessor did not win selection");
      TemplateRecipeHandler handler = handler("NEIAltarRecipeHandler");
      RegistryRecipes adapter = adapter(handler);
      require(adapter.size() == 1, "Native hidden entry became a visible recipe");
      RecipeRow row = NativeCoreFixesTest.row(input, out);
      require(adapter.capture(0, row), "Later altar view vanished");
      JsonObject process = row.record.getAsJsonObject("process");
      require(
          process.has("earlier")
              && process.getAsJsonArray("earlier").size() == 1
              && process.getAsJsonArray("earlier").get(0).getAsJsonObject().get("tier").getAsInt()
                  == 1,
          "Altar erased an earlier native predicate because NEI hides its null result");
    } finally {
      registry.clear();
      registry.addAll(before);
    }
  }

  private static boolean matches(Class<?> type, Object recipe, ItemStack item, int tier)
      throws Exception {
    return (Boolean)
        type.getMethod("doesRequiredItemMatch", ItemStack.class, int.class)
            .invoke(recipe, item, tier);
  }

  private static TemplateRecipeHandler handler(String name) throws Exception {
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler) Class.forName(BASE + "client.nei." + name).newInstance();
    handler.arecipes.add(
        handler.new CachedRecipe() {
          @Override
          public codechicken.nei.PositionedStack getResult() {
            throw new AssertionError("Blood semantic reader requested a borrowed GUI page");
          }
        });
    return handler;
  }

  private static RegistryRecipes adapter(TemplateRecipeHandler handler) throws Exception {
    Class<?> type = Class.forName("com.github.dcysteine.nesql.exporter.capture.BloodRitualRecipes");
    Constructor<?> constructor = type.getDeclaredConstructor(TemplateRecipeHandler.class);
    constructor.setAccessible(true);
    List<TemplateRecipeHandler.CachedRecipe> borrowed = new ArrayList<>(handler.arecipes);
    RegistryRecipes result = (RegistryRecipes) constructor.newInstance(handler);
    require(
        handler.arecipes.equals(borrowed), "Blood semantic reader changed the borrowed NEI cache");
    return result;
  }

  private static void expectDrift(RegistryRecipes adapter) {
    try {
      adapter.finishVerification();
      throw new AssertionError("Blood native registry mutation was not detected");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(
          expected.code.equals("registry_drift") || expected.code.equals("recipe_changed"),
          "Wrong Blood registry drift error: " + expected.code);
    }
  }

  private static void charging(net.minecraft.item.Item orb) throws Exception {
    Class<?> type = Class.forName(BASE + "api.altarRecipeRegistry.AltarRecipe");
    ItemStack input = new ItemStack(orb);
    Object recipe =
        type.getConstructor(
                ItemStack.class,
                ItemStack.class,
                int.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                boolean.class)
            .newInstance(null, input, 2, 0, 30, 0, true, false);
    List<Object> registry = registry("api.altarRecipeRegistry.AltarRecipeRegistry", "altarRecipes");
    List<Object> before = new ArrayList<>(registry);
    registry.clear();
    registry.add(recipe);
    try {
      TemplateRecipeHandler handler = handler("NEIAltarRecipeHandler");
      try (RegistryRecipes adapter = adapter(handler)) {
        require(
            adapter.size() == 1, "Native altar charging was lost because it has no result stack");
        RecipeRow row = NativeCoreFixesTest.row(input);
        require(adapter.capture(0, row), "Native altar charging was excluded");
        require(
            row.outputs.size() == 0
                && choice(row).getAsJsonObject("consume").get("kind").getAsString().equals("keep")
                && row.record
                    .getAsJsonObject("process")
                    .get("result")
                    .getAsString()
                    .equals("soul-network-charge"),
            "Native charging became a crafted output or consumed the orb");
        adapter.finishVerification();
        write("blood-charging", row, handler, input);
      }
    } finally {
      registry.clear();
      registry.addAll(before);
    }
  }

  private static void lifecycle() throws Exception {
    Class<?> owner = Class.forName(BASE + "api.altarRecipeRegistry.AltarRecipeRegistry");
    TemplateRecipeHandler handler = handler("NEIAltarRecipeHandler");
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      scope.replace(owner, null, "altarRecipes", null);
      expect("handler_not_ready", () -> new BloodRitualRecipes(handler));
      List<Object> empty = new ArrayList<>();
      scope.replace(owner, null, "altarRecipes", empty);
      try (RegistryRecipes adapter = new BloodRitualRecipes(handler)) {
        require(adapter.size() == 0, "Ready empty altar is not empty");
        adapter.verify();
        adapter.finishVerification();
        scope.replace(owner, null, "altarRecipes", new ArrayList<>());
        expect("registry_drift", adapter::verify);
      }
      List<Object> source = new ArrayList<>();
      Object recipe =
          Class.forName(BASE + "api.altarRecipeRegistry.AltarRecipe")
              .getConstructor(
                  ItemStack.class,
                  ItemStack.class,
                  int.class,
                  int.class,
                  int.class,
                  int.class,
                  boolean.class)
              .newInstance(
                  new ItemStack(Items.diamond), new ItemStack(Items.paper), 1, 100, 1, 0, false);
      source.add(recipe);
      scope.replace(owner, null, "altarRecipes", source);
      RegistrationBindings binding = RegistrationBindings.read(handler);
      for (int registration = 0; registration < 4; registration++)
        require(
            binding.same(RegistrationBindings.read(handler("NEIAltarRecipeHandler"))),
            "Blood registration bindings differ");
      try (Recipes.Cursor cursor =
          new Recipes.Handler(handler, 0)
              .open(
                  NativeCoreFixesTest.row(new ItemStack(Items.paper), new ItemStack(Items.diamond))
                      .facts)) {
        require(field(cursor, "handler") == null, "Blood production cursor retained a GUI handler");
        cursor.capture(0);
        cursor.finishVerification();
        require(
            handler.arecipes.size() == 1, "Blood production cursor altered a borrowed GUI cache");
      }
      try (RegistryRecipes adapter = new BloodRitualRecipes(handler)) {
        ItemStack input = (ItemStack) field(recipe, "requiredItem");
        recipe.getClass().getField("requiredItem").set(recipe, input.copy());
        expect("registry_drift", adapter::finishVerification);
        recipe.getClass().getField("requiredItem").set(recipe, input);
        adapter.finishVerification();
      }
      UnknownDamage unknown = new UnknownDamage();
      net.minecraft.item.Item.itemRegistry.addObject(
          31992, "nesql_native:unknown_blood_metadata", unknown);
      recipe.getClass().getField("requiredItem").set(recipe, new ItemStack(unknown));
      expect("recipe_unsupported", () -> new BloodRitualRecipes(handler));
      require(unknown.calls == 0, "Unknown Blood metadata callback executed before rejection");
    }
  }

  private static void write(
      String name, RecipeRow row, TemplateRecipeHandler handler, ItemStack... specimens)
      throws Exception {
    require(row.properties.entrySet().isEmpty(),"Blood typed rules still inject fixed-language duplicate prose");
    row.record.add(
        "source",
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner", "AWWayofTime", "handler", handler.getClass().getName(), "key", name));
    row.record.addProperty(
        "category",
        com.github.dcysteine.nesql.exporter.source.Identity.origin(
            "category", row.record.getAsJsonObject("source")));
    row.record.addProperty(
        "id", com.github.dcysteine.nesql.exporter.source.Identity.recipe(row.record));
    NativeContractFixture.writeOwnedWithDurability(
        name + "-semantic", row, java.util.Arrays.asList(specimens));
  }

  private static void expect(String code, Runnable operation) {
    try {
      operation.run();
      throw new AssertionError("Missing Blood fault " + code);
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(expected.code.equals(code), "Expected " + code + ", got " + expected.code);
    }
  }

  public static final class UnknownDamage extends net.minecraft.item.Item {
    int calls;

    @Override
    public int getDamage(ItemStack stack) {
      calls++;
      throw new AssertionError("Unknown metadata callback");
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Object> registry(String type, String name) throws Exception {
    return (List<Object>) field(Class.forName(BASE + type), null, name);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> rituals() throws Exception {
    return (Map<String, Object>)
        field(Class.forName(BASE + "api.rituals.Rituals"), null, "ritualMap");
  }

  private static Object ritual(int level, int cost, Object effect) throws Exception {
    return Class.forName(BASE + "api.rituals.Rituals")
        .getConstructor(
            int.class, int.class, Class.forName(BASE + "api.rituals.RitualEffect"), String.class)
        .newInstance(level, cost, effect, "Native fixture");
  }

  private static JsonObject choice(RecipeRow row) {
    return row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
