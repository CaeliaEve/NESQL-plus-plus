package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;

/**
 * Pinned registry/cache getters and Platform comparator on owned objects, without an AE network.
 */
final class NativeInscriberTest {
  @SuppressWarnings({"unchecked", "rawtypes"})
  static void run() throws Exception {
    String root = "appeng.";
    Class<?> handlerType = type(root + "integration.modules.NEIHelpers.NEIInscriberRecipeHandler");
    require(
        handlerType
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toString()
            .contains("/native-tests/ae2.jar!"),
        "Pinned AE jar required");
    TemplateRecipeHandler handler = (TemplateRecipeHandler) handlerType.newInstance();
    require(Recipes.adapter(handler) != null, "Missing inscriber adapter");
    ItemStack center = new ItemStack(Items.iron_ingot, 17),
        top = new ItemStack(Items.paper, 9),
        bottom = new ItemStack(Items.gold_ingot),
        output = new ItemStack(Items.diamond, 3);
    ItemStack empty = center.copy();
    empty.setTagCompound(new NBTTagCompound());
    require(same(center, empty), "Native null/empty NBT equivalence changed");
    empty.setTagInfo("owner", new NBTTagString("other"));
    require(!same(center, empty), "Native precise matcher ignored NBT");
    ItemStack wildcard = center.copy();
    wildcard.setItemDamage(32767);
    require(!same(center, wildcard), "AE precise metadata is literal, not a wildcard");
    NBTTagList typed = new NBTTagList();
    typed.appendTag(new NBTTagString("x"));
    typed.removeTag(0);
    ItemStack a = center.copy(), b = center.copy();
    a.setTagInfo("list", typed);
    b.setTagInfo("list", new NBTTagList());
    require(same(a, b), "AE empty-list element type must be ignored");
    a.setTagInfo("number", new NBTTagFloat(-0f));
    b.setTagInfo("number", new NBTTagFloat(0f));
    require(same(a, b), "AE float signed zero must compare equal");
    Class<?> recipeType = type(root + "core.features.registries.entries.InscriberRecipe"),
        modeType = type(root + "api.features.InscriberProcessType");
    List<Object> nativeRecipes = new ArrayList<>();
    for (String mode : new String[] {"Inscribe", "Press"})
      nativeRecipes.add(
          recipeType
              .getConstructor(
                  Collection.class, ItemStack.class, ItemStack.class, ItemStack.class, modeType)
              .newInstance(
                  Arrays.asList(center, empty),
                  output,
                  top,
                  bottom,
                  Enum.valueOf((Class) modeType, mode)));
    nativeRecipes.add(
        recipeType
            .getConstructor(
                Collection.class, ItemStack.class, ItemStack.class, ItemStack.class, modeType)
            .newInstance(
                Collections.singletonList(center),
                output,
                null,
                bottom,
                Enum.valueOf((Class) modeType, "Press")));
    TemplateRecipeHandler.CachedRecipe sentinel =
        handler.new CachedRecipe() {
          @Override
          public codechicken.nei.PositionedStack getResult() {
            throw new AssertionError("Borrowed inscriber GUI accessed");
          }
        };
    handler.arecipes.add(sentinel);
    production(handler, nativeRecipes, center, empty, top, bottom, output);
    require(
        handler.arecipes.size() == 1 && handler.arecipes.get(0) == sentinel,
        "Inscriber changed borrowed GUI cache");
    require(
        center.stackSize == 17 && top.stackSize == 9 && output.stackSize == 3,
        "Source-owned stacks were mutated");
    System.out.println(
        "Inscriber native registry/cache and comparator: modes, plate omission, exact metadata, null/empty NBT, empty lists, signed zero, count and ownership passed");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void production(
      TemplateRecipeHandler handler, List<Object> recipes, ItemStack... known) throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeCoreFixesTest.version("appliedenergistics2", "rv3-beta-695-GTNH");
      Class<?> apiClass = type("appeng.api.AEApi"),
          recipeApi = type("appeng.api.features.IInscriberRecipe");
      Object api = scope.allocate(type("appeng.core.Api")),
          definitions = scope.allocate(type("appeng.core.ApiDefinitions")),
          materials = scope.allocate(type("appeng.core.api.definitions.ApiMaterials")),
          registries = scope.allocate(type("appeng.core.features.registries.RegistryContainer"));
      Object registry = type("appeng.core.features.registries.InscriberRegistry").newInstance();
      for (Object recipe : recipes)
        invoke(registry.getClass(), registry, "addRecipe", new Class<?>[] {recipeApi}, recipe);
      Class enumType = type("appeng.core.features.ActivityState");
      Object stackSource =
          type("appeng.core.features.ItemStackSrc")
              .getConstructor(Item.class, int.class, enumType)
              .newInstance(Items.name_tag, 0, Enum.valueOf(enumType, "Enabled"));
      Object nameDefinition =
          type("appeng.core.features.DamagedItemDefinition")
              .getConstructor(type("appeng.core.features.IStackSrc"))
              .newInstance(stackSource);
      scope.replace(materials.getClass(), materials, "namePress", nameDefinition);
      scope.replace(definitions.getClass(), definitions, "materials", materials);
      scope.replace(registries.getClass(), registries, "inscriber", registry);
      scope.replace(api.getClass(), api, "definitions", definitions);
      scope.replace(api.getClass(), api, "registryContainer", registries);
      scope.replace(apiClass, null, "HELD_API", api);
      List<ItemStack> specimens = new ArrayList<>(Arrays.asList(known));
      specimens.add(new ItemStack(Items.name_tag));
      List<?> nativeOrder =
          new ArrayList<>(
              (Collection<?>) invoke(registry.getClass(), registry, "getRecipes", new Class<?>[0]));
      InscriberRecipes reader = new InscriberRecipes(handler);
      require(reader.size() == nativeOrder.size(), "Production omitted native HashSet entries");
      List<String> english = new ArrayList<>();
      for (String locale : new String[] {"en_US", "zh_CN"})
        for (int index = 0; index < reader.size(); index++) {
          RecipeRow row = NativeCoreFixesTest.row(locale, specimens.toArray(new ItemStack[0]));
          JsonObject origin =
              object(
                  "owner",
                  "appliedenergistics2",
                  "handler",
                  handler.getClass().getName(),
                  "key",
                  "inscriber");
          row.record.add("source", origin);
          row.record.addProperty("category", Identity.origin("category", origin));
          row.record.addProperty("order", index);
          reader.capture(index, row);
          row.finish();
          validateRow(row);
          require(!row.record.has("view"), "Production emitted a retired NEI GUI field");
          require(
              row.record
                  .getAsJsonObject("process")
                  .get("mode")
                  .getAsString()
                  .equals(
                      ((Enum<?>) field(nativeOrder.get(index), "type"))
                          .name()
                          .toLowerCase(Locale.ROOT)),
              "HashSet encounter order changed");
          if (locale.equals("en_US")) english.add(row.record.get("id").getAsString());
          else
            require(
                english.get(index).equals(row.record.get("id").getAsString()),
                "Inscriber identity changed with locale");
          NativeContractFixture.writeOwned(
              "inscriber-native-" + index + "-" + locale, row, specimens);
        }
      RegistrationBindings registration = RegistrationBindings.read(handler);
      for (int route = 0; route < 4; route++)
        require(
            registration.same(
                RegistrationBindings.read(
                    (TemplateRecipeHandler) handler.getClass().newInstance())),
            "Inscriber four registrations have different sources");
      try (Recipes.Cursor production =
          new Recipes.Handler(handler, 0)
              .open(NativeCoreFixesTest.row(specimens.toArray(new ItemStack[0])).facts)) {
        require(field(production, "handler") == null, "Production inscriber retains GUI handler");
        production.capture(0);
        production.finishVerification();
      }
      ItemStack output = (ItemStack) field(nativeOrder.get(0), "output");
      int oldCount = output.stackSize;
      output.stackSize++;
      try {
        expectFault("environment_changed", reader::finishVerification);
      } finally {
        output.stackSize = oldCount;
      }
      List<ItemStack> inputs = (List<ItemStack>) field(nativeOrder.get(0), "inputs");
      ItemStack first = inputs.get(0);
      inputs.set(0, first.copy());
      try {
        expectFault("environment_changed", reader::finishVerification);
      } finally {
        inputs.set(0, first);
      }
      inputs.set(0, new ItemStack(new UnadaptedDamage()));
      UnadaptedDamage.calls = 0;
      try {
        expectFault("recipe_unsupported", () -> new InscriberRecipes(handler));
        require(UnadaptedDamage.calls == 0, "Unknown metadata callback executed before rejection");
      } finally {
        inputs.set(0, first);
      }
      try (NativeRegistryScope drift = new NativeRegistryScope()) {
        drift.replace(
            registry.getClass(),
            registry,
            "recipes",
            new HashSet<>((Set<?>) field(registry, "recipes")));
        require(
            !registration.same(RegistrationBindings.read(handler)),
            "Equal-content set replacement preserved source identity");
        expectFault("environment_changed", reader::verify);
      }
      try (NativeRegistryScope drift = new NativeRegistryScope()) {
        drift.replace(stackSource.getClass(), stackSource, "damage", 1);
        expectFault("environment_changed", reader::verify);
      }
      nativeTasks(scope, registry, nativeOrder);
      reader.finishVerification();
      reader.close();
      reader.close();
      expectFault("capture_closed", reader::verify);
      try (NativeRegistryScope empty = new NativeRegistryScope()) {
        empty.replace(registry.getClass(), registry, "recipes", new HashSet<>());
        try (InscriberRecipes blank = new InscriberRecipes(handler)) {
          require(blank.size() == 0, "Ready-empty inscriber invented rows");
          blank.finishVerification();
        }
        empty.replace(registry.getClass(), registry, "recipes", null);
        expectFault("handler_not_ready", () -> new InscriberRecipes(handler));
      }
      try (NativeRegistryScope disabled = new NativeRegistryScope()) {
        disabled.replace(
            nameDefinition.getClass(),
            nameDefinition,
            "source",
            com.google.common.base.Optional.absent());
        try (InscriberRecipes noName = new InscriberRecipes(handler)) {
          require(
              field(noName, "namePress") == null,
              "Disabled native name definition gained a name press");
          noName.finishVerification();
        }
      }
      try (NativeRegistryScope materialScope = new NativeRegistryScope()) {
        Class<?> materialType = type("appeng.items.materials.MaterialType");
        Object material = materialScope.allocate(materialType);
        materialScope.replace(materialType, material, "itemInstance", Items.name_tag);
        materialScope.replace(materialType, material, "damageValue", 0);
        Object materialSource =
            type("appeng.core.features.MaterialStackSrc")
                .getConstructor(materialType)
                .newInstance(material);
        Object materialDefinition =
            type("appeng.core.features.DamagedItemDefinition")
                .getConstructor(type("appeng.core.features.IStackSrc"))
                .newInstance(materialSource);
        materialScope.replace(materials.getClass(), materials, "namePress", materialDefinition);
        try (InscriberRecipes materialReader = new InscriberRecipes(handler)) {
          ItemStack actual =
              (ItemStack)
                  ((com.google.common.base.Optional<?>)
                          invoke(
                              materialDefinition.getClass(),
                              materialDefinition,
                              "maybeStack",
                              new Class<?>[] {int.class},
                              1))
                      .orNull();
          require(
              ItemStack.areItemStacksEqual(actual, (ItemStack) field(materialReader, "namePress")),
              "Native MaterialStackSrc name press differs");
          materialReader.finishVerification();
        }
      }
      ItemStack predicate = known[0];
      predicate.setTagInfo("float", new NBTTagFloat(Float.NaN));
      try {
        expectFault("recipe_unsupported", () -> new InscriberRecipes(handler));
      } finally {
        predicate.setTagCompound(null);
      }
      try (NativeRegistryScope foreign = new NativeRegistryScope()) {
        foreign.replace(
            registry.getClass(),
            registry,
            "recipes",
            new HashSet<>(Collections.singletonList(new Object())));
        expectFault("recipe_unsupported", () -> new InscriberRecipes(handler));
      }
      Object named =
          type("appeng.core.features.registries.entries.InscriberRecipe")
              .getConstructor(
                  Collection.class,
                  ItemStack.class,
                  ItemStack.class,
                  ItemStack.class,
                  type("appeng.api.features.InscriberProcessType"))
              .newInstance(
                  Collections.singletonList(known[0]),
                  known[4],
                  new ItemStack(Items.name_tag),
                  null,
                  Enum.valueOf((Class) type("appeng.api.features.InscriberProcessType"), "Press"));
      try (NativeRegistryScope shadowed = new NativeRegistryScope()) {
        shadowed.replace(
            registry.getClass(),
            registry,
            "recipes",
            new HashSet<>(Collections.singletonList(named)));
        try (InscriberRecipes namedReader = new InscriberRecipes(handler)) {
          RecipeRow row = NativeCoreFixesTest.row(specimens.toArray(new ItemStack[0]));
          require(
              !namedReader.capture(0, row) && row.exclusion != null,
              "Native rename branch did not exclude shadowed row");
        }
      }
    }
  }

  public static final class UnadaptedDamage extends Item {
    static int calls;

    @Override
    public int getDamage(ItemStack stack) {
      calls++;
      throw new AssertionError("Unknown metadata callback");
    }
  }

  private static void validateRow(RecipeRow row) {
    JsonObject process = row.record.getAsJsonObject("process");
    boolean top = !process.get("top").isJsonNull(),
        keep = process.get("mode").getAsString().equals("inscribe");
    require(row.inputs.size() == (top ? 3 : 1), "Absent top incorrectly requires declared bottom");
    require(
        row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"),
        "Native output count changed");
    require(
        row.record.get("duration").isJsonNull() && row.record.get("energy").isJsonNull(),
        "Invented fixed tick/EU cost");
    for (com.google.gson.JsonElement value : row.inputs) {
      JsonObject in = value.getAsJsonObject();
      for (com.google.gson.JsonElement raw : in.getAsJsonArray("choices")) {
        JsonObject choice = raw.getAsJsonObject();
        require(
            choice.get("amount").getAsString().equals("1")
                && choice.getAsJsonObject("rule").get("kind").getAsString().equals("ae"),
            "Native single-stack precise matching lost");
        require(
            choice
                .getAsJsonObject("consume")
                .get("kind")
                .getAsString()
                .equals(keep && in.get("slot").getAsInt() != 2 ? "keep" : "consume"),
            "Press/inscribe plate consumption lost");
        require(
            choice.getAsJsonArray("returns").size() == 0,
            "Machine gained crafting container returns");
      }
    }
  }

  private static void nativeTasks(NativeRegistryScope scope, Object registry, List<?> order)
      throws Exception {
    Object tile = scope.allocate(type("appeng.tile.misc.TileInscriber"));
    Object inventory =
        type("appeng.tile.inventory.AppEngInternalInventory")
            .getConstructor(type("appeng.tile.inventory.IAEAppEngInventory"), int.class)
            .newInstance(null, 4);
    scope.replace(tile.getClass(), tile, "inv", inventory);
    ItemStack[] slots = (ItemStack[]) field(inventory, "inv");
    ItemStack center = new ItemStack(Items.iron_ingot),
        top = new ItemStack(Items.paper),
        bottom = new ItemStack(Items.gold_ingot);
    slots[0] = top;
    slots[1] = bottom;
    slots[2] = center;
    Object selected = invoke(tile.getClass(), tile, "getTask", new Class<?>[0]);
    require(
        selected != null
            && selected
                == order.stream()
                    .filter(
                        r ->
                            field(r, "maybeTop") != null
                                && ((com.google.common.base.Optional<?>) field(r, "maybeTop"))
                                    .isPresent())
                    .findFirst()
                    .get(),
        "Native first registry match changed");
    slots[0] = bottom;
    slots[1] = top;
    require(
        invoke(tile.getClass(), tile, "getTask", new Class<?>[0]) == selected,
        "Native swapped plates did not select same first row");
    slots[0] = null;
    slots[1] = new ItemStack(Items.apple);
    Object absent = invoke(tile.getClass(), tile, "getTask", new Class<?>[0]);
    require(
        absent != null
            && !((com.google.common.base.Optional<?>) field(absent, "maybeTop")).isPresent(),
        "Native top-absent branch unexpectedly required declared bottom");
    slots[0] = new ItemStack(Items.name_tag);
    slots[1] = null;
    Object renamed = invoke(tile.getClass(), tile, "getTask", new Class<?>[0]);
    require(
        renamed != null
            && !order.contains(renamed)
            && ((Enum<?>) field(renamed, "type")).name().equals("Inscribe"),
        "Native name branch did not preempt registry");
    slots[0].stackSize = 2;
    require(
        invoke(tile.getClass(), tile, "getTask", new Class<?>[0]) == null,
        "Native count>1 guard was lost");
  }

  private static void expectFault(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected inscriber fault " + code);
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(
          expected.code.equals(code),
          "Wrong inscriber fault " + expected.code + ", wanted " + code);
    }
  }

  private static boolean same(ItemStack a, ItemStack b) {
    return (Boolean)
        invoke(
            type("appeng.util.Platform"),
            null,
            "isSameItemPrecise",
            new Class<?>[] {ItemStack.class, ItemStack.class},
            a,
            b);
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
