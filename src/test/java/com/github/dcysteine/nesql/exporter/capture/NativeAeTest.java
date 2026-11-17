package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

/**
 * Installed AE2 handler capability regression; further semantic checks run after capability
 * registration.
 */
public final class NativeAeTest {
  private NativeAeTest() {}

  public static void main(String[] args) throws Exception {
    GameTest.run();
  }

  public static void run() throws Exception {
    NativeCoreFixesTest.version("appliedenergistics2", "rv3-beta-695-GTNH");
    if (wireless()) NativeCoreFixesTest.version("ae2wct", "1.12.7");
    if (wireless()) {
      Class<?> nativeHandler =
          Class.forName(
              "net.p455w0rd.wirelesscraftingterminal.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler");
      if (!nativeHandler
          .getProtectionDomain()
          .getCodeSource()
          .getLocation()
          .toString()
          .contains("/native-tests/wireless.jar!"))
        throw new AssertionError("Wireless test must use the pinned derived jar");
      if (Recipes.adapter((ICraftingHandler) nativeHandler.newInstance()) == null)
        throw new AssertionError("Wireless crafting terminal has no production adapter");
    }
    for (String kind : new String[] {"Shaped", "Shapeless"}) {
      ICraftingHandler handler =
          (ICraftingHandler)
              Class.forName("appeng.integration.modules.NEIHelpers.NEIAE" + kind + "RecipeHandler")
                  .newInstance();
      if (Recipes.adapter(handler) == null)
        throw new AssertionError("Pinned AE2 " + kind + " handler has no production adapter");
    }
    Class<?> config = Class.forName("appeng.core.AEConfig");
    java.lang.reflect.Field unsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafe.setAccessible(true);
    Object oldConfig = config.getField("instance").get(null);
    config
        .getField("instance")
        .set(null, ((sun.misc.Unsafe) unsafe.get(null)).allocateInstance(config));
    // The colored-cable preference initializes AE2's complete block/API registry;
    // that branch belongs to live-game coverage, not this isolated fixture.
    try {
      readOnlyIngredients();
      greedyParity();
      semantics();
    } finally {
      config.getField("instance").set(null, oldConfig);
    }
  }

  private static void readOnlyIngredients() throws Exception {
    Class<?> api = Class.forName("appeng.api.recipes.IIngredient"),
        type = Class.forName("appeng.recipes.game.ShapedRecipe");
    ItemStack first = new ItemStack(Items.apple, 13),
        second = new ItemStack(Items.carrot, 7),
        out = new ItemStack(Items.paper);
    Class<?> set = Class.forName("appeng.api.recipes.ResolverResultSet");
    Object result =
        set.getConstructor(String.class, ItemStack[].class)
            .newInstance("fixture", new ItemStack[] {first, second});
    Object raw =
        Class.forName("appeng.recipes.IngredientSet")
            .getConstructor(set, int.class)
            .newInstance(result, 13);
    IRecipe recipe =
        (IRecipe)
            type.getConstructor(ItemStack.class, Object[].class)
                .newInstance(out, new Object[] {new String[] {"A"}, 'A', raw});
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler)
            Class.forName("appeng.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler")
                .newInstance();
    Facts facts = new Facts("en_US");
    Set<String> known = (Set<String>) MagicApi.field(facts, "items");
    for (ItemStack stack : new ItemStack[] {first, second, out})
      known.add(Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), 0, null));
    AeRecipes.capture(
        recipe,
        handler,
        new RecipeRow(
            facts,
            object("owner", "ae", "handler", "native", "key", "read-only"),
            "category_test",
            0));
    if (first.stackSize != 13 || second.stackSize != 7)
      throw new AssertionError("AE IngredientSet capture mutated native source quantities");
    if (MagicApi.field(raw, "baked") != null)
      throw new AssertionError("AE capture wrote native ingredient cache");
    Field foldedGuard = raw.getClass().getDeclaredField("isInside");
    foldedGuard.setAccessible(true);
    foldedGuard.setBoolean(raw, true);
    int nativeCount =
        ((ItemStack[]) raw.getClass().getMethod("getItemStackSet").invoke(raw)).length;
    if (AeIngredients.read(raw, api, true, null).length != nativeCount)
      throw new AssertionError(
          "AE IngredientSet used a reflection value that the native bytecode constant-folded away");
    foldedGuard.setBoolean(raw, false);
    int[] calls = {0};
    Object foreign =
        java.lang.reflect.Proxy.newProxyInstance(
            api.getClassLoader(),
            new Class<?>[] {api},
            (p, m, a) -> {
              calls[0]++;
              return new ItemStack[] {first};
            });
    try {
      AeIngredients.read(foreign, api, true, null);
      throw new AssertionError("AE accepted an unknown ingredient callback");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      if (!expected.code.equals("recipe_unsupported")) throw expected;
    }
    if (calls[0] != 0)
      throw new AssertionError("AE unknown ingredient callback ran before rejection");
    Class<?> groupType = Class.forName("appeng.recipes.GroupIngredient");
    List<Object> children = new ArrayList<>(Collections.singletonList(raw));
    Object group =
        groupType
            .getConstructor(String.class, List.class, int.class)
            .newInstance("fixture", children, 1);
    Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    Object air =
        ((sun.misc.Unsafe) access.get(null))
            .allocateInstance(Class.forName("appeng.recipes.Ingredient"));
    Field airFlag = air.getClass().getDeclaredField("isAir");
    airFlag.setAccessible(true);
    airFlag.setBoolean(air, true);
    Field quantity = air.getClass().getDeclaredField("qty");
    quantity.setAccessible(true);
    quantity.setInt(air, 1);
    for (String name : new String[] {"nameSpace", "itemName"}) {
      Field field = air.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(air, "");
    }
    children.add(air);
    try {
      groupType.getMethod("getItemStackSet").invoke(group);
      throw new AssertionError("Native AE group unexpectedly swallowed a registration error");
    } catch (java.lang.reflect.InvocationTargetException expected) {
      if (!expected
          .getCause()
          .getClass()
          .getName()
          .equals("appeng.api.exceptions.RegistrationError")) throw expected;
    }
    if (AeIngredients.read(group, api, true, null).length != 0)
      throw new AssertionError(
          "AE group converted registration failure into admitted sibling candidates");
  }

  @SuppressWarnings("unchecked")
  private static void semantics() throws Exception {
    ItemStack apple = new ItemStack(Items.apple, 13, 0), paper = new ItemStack(Items.paper, 1, 0);
    ItemStack carrot = new ItemStack(Items.carrot, 7, 0);
    ItemStack tagged = apple.copy();
    tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("any"));
    ItemStack output = new ItemStack(Items.diamond, 3);
    for (String kind :
        wireless()
            ? new String[] {"Shaped", "Shapeless", "Wireless"}
            : new String[] {"Shaped", "Shapeless"}) {
      boolean wct = kind.equals("Wireless"), shaped = !kind.equals("Shapeless");
      String root = "net.p455w0rd.wirelesscraftingterminal.";
      Class<?> api =
          Class.forName(wct ? root + "api.recipes.IIngredient" : "appeng.api.recipes.IIngredient");
      Object apples = ingredient(api, apple, carrot), papers = ingredient(api, paper);
      Class<?> type =
          Class.forName(
              wct
                  ? root + "api.recipes.game.ShapedRecipe"
                  : "appeng.recipes.game." + kind + "Recipe");
      Object[] recipeArgs =
          shaped
              ? new Object[] {new String[] {"AP", " A"}, 'A', apples, 'P', papers}
              : new Object[] {apples, papers, apples};
      IRecipe recipe =
          (IRecipe)
              type.getConstructor(ItemStack.class, Object[].class).newInstance(output, recipeArgs);
      IRecipe disabled =
          (IRecipe)
              type.getConstructor(ItemStack.class, Object[].class).newInstance(output, recipeArgs);
      java.lang.reflect.Field disabledFlag = type.getDeclaredField("disable");
      disabledFlag.setAccessible(true);
      disabledFlag.setBoolean(disabled, true);
      InventoryCrafting inventory =
          new InventoryCrafting(
              new Container() {
                @Override
                public boolean canInteractWith(EntityPlayer player) {
                  return true;
                }
              },
              3,
              3);
      inventory.setInventorySlotContents(0, tagged.copy());
      inventory.setInventorySlotContents(1, paper.copy());
      inventory.setInventorySlotContents(shaped ? 4 : 2, new ItemStack(Items.apple));
      if (!recipe.matches(inventory, null))
        throw new AssertionError("Pinned native recipe should ignore offered NBT/count");
      if (recipe.getCraftingResult(inventory).stackSize != 3)
        throw new AssertionError("Native result count changed");
      if (wct) {
        Object[] raw = (Object[]) MagicApi.field(recipe, "input");
        for (int cell = 0; cell < raw.length; cell++)
          if (raw[cell] != null)
            raw[cell] =
                new ArrayList<>(
                    Arrays.asList(
                        (ItemStack[]) api.getMethod("getItemStackSet").invoke(raw[cell])));
      }
      TemplateRecipeHandler handler =
          (TemplateRecipeHandler)
              Class.forName(
                      wct
                          ? root + "integration.modules.NEIHelpers.NEIAEShapedRecipeHandler"
                          : "appeng.integration.modules.NEIHelpers.NEIAE" + kind + "RecipeHandler")
                  .newInstance();
      TemplateRecipeHandler.CachedRecipe borrowed =
          handler.new CachedRecipe() {
            public codechicken.nei.PositionedStack getResult() {
              throw new AssertionError("AE semantic capture requested borrowed GUI");
            }
          };
      handler.arecipes.add(borrowed);
      Facts facts = new Facts("en_US");
      Set<String> known = (Set<String>) MagicApi.field(facts, "items");
      for (ItemStack stack : new ItemStack[] {apple, carrot, paper, output})
        known.add(
            Identity.item(
                Item.itemRegistry.getNameForObject(stack.getItem()),
                stack.getItemDamage(),
                TypedNbt.encode(stack.getTagCompound())));
      RecipeRow row =
          new RecipeRow(
              facts, object("owner", "ae2", "handler", kind, "key", kind), "category_test", 0);
      AeRecipes.capture(recipe, handler, row);
      if (!row.record.has("process")
          || !row.record.getAsJsonObject("process").get("kind").getAsString().equals("aeCrafting"))
        throw new AssertionError("AE native greedy/translated matching has no typed law");
      if (handler.arecipes.size() != 1 || handler.arecipes.get(0) != borrowed)
        throw new AssertionError("AE semantic capture changed borrowed GUI cache");
      if (row.inputs.size() != 3
          || row.outputs.size() != 1
          || !"3".equals(row.outputs.get(0).getAsJsonObject().get("amount").getAsString())) {
        throw new AssertionError("AE2 projection lost inputs or native output quantity");
      }
      for (com.google.gson.JsonElement value : row.inputs) {
        com.google.gson.JsonObject choice =
            value.getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        if (!"1".equals(choice.get("amount").getAsString())
            || !choice.getAsJsonObject("rule").get("nbt").getAsBoolean()) {
          throw new AssertionError("AE2 consumes one item per cell and ignores NBT");
        }
      }
      if (row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() != 2) {
        throw new AssertionError(
            "AE2 alternative inputs must not collapse to their first display stack");
      }
      String witness = "ae-native-" + kind.toLowerCase(Locale.ROOT);
      com.google.gson.JsonObject origin =
          object(
              "owner",
              wct ? "ae2wct" : "appliedenergistics2",
              "handler",
              handler.getClass().getName(),
              "key",
              witness);
      row.record.add("source", origin);
      row.record.addProperty("category", Identity.origin("category", origin));
      NativeContractFixture.writeOwnedWithDurability(
          witness, row, Arrays.asList(apple, carrot, paper, output));
      if (shaped) {
        com.google.gson.JsonObject grid = row.record.getAsJsonObject("grid");
        if (grid.get("width").getAsInt() != 2
            || !grid.getAsJsonArray("cells").get(2).isJsonNull()
            || !grid.get("mirror").getAsBoolean()) {
          throw new AssertionError("AE2 shaped grid holes or mirroring were lost");
        }
        java.lang.reflect.Field mirror = type.getDeclaredField("mirrored");
        mirror.setAccessible(true);
        mirror.setBoolean(recipe, false);
        RecipeRow unmirrored =
            new RecipeRow(
                facts, object("owner", "ae2", "handler", kind, "key", kind), "category_test", 1);
        AeRecipes.capture(recipe, handler, unmirrored);
        if (unmirrored.record.getAsJsonObject("grid").get("mirror").getAsBoolean())
          throw new AssertionError("AE2 disabled mirroring was lost");
      } else if (!row.record
          .getAsJsonObject("process")
          .get("matching")
          .getAsString()
          .equals("rowMajorFirstRemainingPredicate"))
        throw new AssertionError("Shapeless semantics were lost");
      if (apple.stackSize != 13
          || carrot.stackSize != 7
          || apple.hasTagCompound()
          || output.stackSize != 3) throw new AssertionError("Projection mutated registry stacks");
      if (shaped) boundaries(type, api, handler, facts, known, output, disabled);
      try (NativeRegistryScope scope = new NativeRegistryScope()) {
        scope.replace(
            net.minecraft.item.crafting.CraftingManager.class,
            net.minecraft.item.crafting.CraftingManager.getInstance(),
            "recipes",
            new ArrayList<>(Arrays.asList(disabled, recipe)));
        RegistrationBindings bindings = RegistrationBindings.read(handler);
        if (bindings == null)
          throw new AssertionError("AE has no native four-registration source binding");
        for (int registration = 0; registration < 4; registration++)
          if (!bindings.same(
              RegistrationBindings.read((ICraftingHandler) handler.getClass().newInstance())))
            throw new AssertionError("AE native source changed across registration paths");
        try (Recipes.Cursor production = new Recipes.Handler(handler, 0).open(facts)) {
          if (MagicApi.field(production, "handler") != null)
            throw new AssertionError("AE production retained a GUI handler");
          if (production.size() != 2
              || production.capture(0).kind
                  != com.github.dcysteine.nesql.exporter.task.CaptureOutcome.Kind.EXCLUDED)
            throw new AssertionError("AE native disabled entry lost count or exclusion proof");
          production.capture(1);
          production.finishVerification();
        }
        AeRecipes reader = new AeRecipes(handler);
        ItemStack registered = (ItemStack) MagicApi.field(recipe, "output");
        int oldAmount = registered.stackSize;
        registered.stackSize++;
        try {
          expectDrift(reader);
        } finally {
          registered.stackSize = oldAmount;
        }
        Object sourceInputs = MagicApi.field(recipe, "input");
        Object first = shaped ? ((Object[]) sourceInputs)[0] : ((List<?>) sourceInputs).get(0);
        List<ItemStack> originals =
            (List<ItemStack>) (wct ? first : MagicApi.field(first, "items"));
        ItemStack original = originals.get(0);
        originals.set(0, original.copy());
        try {
          expectDrift(reader);
        } finally {
          originals.set(0, original);
        }
        reader.finishVerification();
        reader.close();
        reader.close();
        try {
          reader.size();
          throw new AssertionError("Closed AE reader remained readable");
        } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
          if (!expected.code.equals("capture_closed")) throw expected;
        }
        scope.replace(
            net.minecraft.item.crafting.CraftingManager.class,
            net.minecraft.item.crafting.CraftingManager.getInstance(),
            "recipes",
            new ArrayList<>());
        try (AeRecipes empty = new AeRecipes(handler)) {
          if (empty.size() != 0)
            throw new AssertionError("Ready empty AE registry invented entries");
          empty.finishVerification();
        }
        scope.replace(
            net.minecraft.item.crafting.CraftingManager.class,
            net.minecraft.item.crafting.CraftingManager.getInstance(),
            "recipes",
            null);
        try {
          new AeRecipes(handler);
          throw new AssertionError("Missing AE registry became empty success");
        } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
          if (!expected.code.equals("handler_not_ready")) throw expected;
        }
      }
    }
    System.out.println(
        "Native AE2"
            + (wireless() ? " and Wireless terminal" : "")
            + ": enabled handlers, shaped holes/mirroring, shapeless inputs, NBT rules, native count and immutable source stacks passed");
  }

  private static boolean wireless() {
    return "wireless".equals(System.getProperty("nesql.nativeFamily"));
  }

  private static void greedyParity() throws Exception {
    Class<?> api = Class.forName("appeng.api.recipes.IIngredient"),
        type = Class.forName("appeng.recipes.game.ShapelessRecipe");
    ItemStack apple = new ItemStack(Items.apple),
        carrot = new ItemStack(Items.carrot),
        paper = new ItemStack(Items.paper),
        out = new ItemStack(Items.diamond);
    IRecipe recipe =
        (IRecipe)
            type.getConstructor(ItemStack.class, Object[].class)
                .newInstance(
                    out, new Object[] {ingredient(api, apple, carrot), ingredient(api, apple)});
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler)
            Class.forName("appeng.integration.modules.NEIHelpers.NEIAEShapelessRecipeHandler")
                .newInstance();
    Facts facts = new Facts("en_US");
    Set<String> known = (Set<String>) MagicApi.field(facts, "items");
    for (ItemStack stack : new ItemStack[] {apple, carrot, paper, out})
      known.add(Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()), 0, null));
    com.google.gson.JsonObject origin =
        object(
            "owner",
            "appliedenergistics2",
            "handler",
            handler.getClass().getName(),
            "key",
            "ae-native-greedy");
    RecipeRow row = new RecipeRow(facts, origin, Identity.origin("category", origin), 0);
    AeRecipes.capture(recipe, handler, row);
    NativeContractFixture.writeOwnedWithDurability(
        "ae-native-greedy", row, Arrays.asList(apple, carrot, paper, out));
    InventoryCrafting grid =
        new InventoryCrafting(
            new Container() {
              public boolean canInteractWith(EntityPlayer player) {
                return true;
              }
            },
            3,
            3);
    com.google.gson.JsonArray cases = array();
    ItemStack[] offered = {apple, carrot, paper};
    for (int left = 0; left < 9; left++)
      for (int right = 0; right < 9; right++)
        if (left != right)
          for (ItemStack a : offered)
            for (ItemStack b : offered) {
              for (int cell = 0; cell < 9; cell++) grid.setInventorySlotContents(cell, null);
              grid.setInventorySlotContents(left, a.copy());
              grid.setInventorySlotContents(right, b.copy());
              com.google.gson.JsonArray cells = array();
              for (int cell = 0; cell < 9; cell++) {
                ItemStack item = grid.getStackInSlot(cell);
                cells.add(
                    item == null
                        ? value(null)
                        : object(
                            "registry",
                            Item.itemRegistry.getNameForObject(item.getItem()),
                            "meta",
                            item.getItemDamage()));
              }
              cases.add(object("offered", cells, "matched", recipe.matches(grid, null)));
            }
    java.nio.file.Files.write(
        java.nio.file.Paths.get("build/native-tests/ae-native-greedy-parity.json"),
        com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(cases));
    if (cases.size() != 648) throw new AssertionError("Incomplete native AE greedy parity probes");
  }

  private static void expectDrift(AeRecipes reader) {
    try {
      reader.finishVerification();
      throw new AssertionError("AE native source identity or value drift passed sealing");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      if (!expected.code.equals("recipe_changed")) throw expected;
    }
  }

  private static void boundaries(
      Class<?> type,
      Class<?> api,
      TemplateRecipeHandler handler,
      Facts facts,
      Set<String> known,
      ItemStack output,
      IRecipe disabled)
      throws Exception {
    try {
      AeRecipes.capture(
          disabled,
          handler,
          new RecipeRow(
              facts, object("owner", "ae", "handler", "test", "key", "test"), "category_test", 2));
      throw new AssertionError("A disabled recipe became exportable");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      if (!expected.code.equals("slot_changed")) throw expected;
    }
    ItemStack wildcard = new ItemStack(Items.apple, 17, 32767),
        milk = new ItemStack(Items.milk_bucket);
    for (ItemStack stack : new ItemStack[] {wildcard, milk, new ItemStack(Items.bucket)})
      known.add(
          Identity.item(
              Item.itemRegistry.getNameForObject(stack.getItem()), stack.getItemDamage(), null));
    IRecipe recipe =
        (IRecipe)
            type.getConstructor(ItemStack.class, Object[].class)
                .newInstance(
                    output,
                    new Object[] {
                      new String[] {"WB"},
                      'W',
                      ingredient(api, wildcard),
                      'B',
                      ingredient(api, milk)
                    });
    if (wireless() && type.getName().contains("wirelesscraftingterminal")) {
      Object[] raw = (Object[]) MagicApi.field(recipe, "input");
      for (int cell = 0; cell < raw.length; cell++)
        if (raw[cell] != null)
          raw[cell] =
              new ArrayList<>(
                  Arrays.asList((ItemStack[]) api.getMethod("getItemStackSet").invoke(raw[cell])));
    }
    InventoryCrafting grid =
        new InventoryCrafting(
            new Container() {
              @Override
              public boolean canInteractWith(EntityPlayer player) {
                return true;
              }
            },
            3,
            3);
    ItemStack offered = new ItemStack(Items.apple, 1, 19);
    offered.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("ignored"));
    grid.setInventorySlotContents(0, offered);
    grid.setInventorySlotContents(1, milk.copy());
    if (!recipe.matches(grid, null))
      throw new AssertionError("Native wildcard did not ignore metadata and NBT");
    grid.setInventorySlotContents(2, new ItemStack(Items.paper));
    if (recipe.matches(grid, null))
      throw new AssertionError("Native shaped recipe accepted an extra occupied cell");
    RecipeRow row =
        new RecipeRow(
            facts, object("owner", "ae", "handler", "test", "key", "wildcard"), "category_test", 3);
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      scope.replace(codechicken.nei.ItemList.class, null, "loadFinished", false);
      try {
        AeRecipes.capture(recipe, handler, row);
        throw new AssertionError("AE wildcard accepted an unfinished native item list");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        if (!expected.code.equals("handler_not_ready")) throw expected;
      }
      scope.replace(codechicken.nei.ItemList.class, null, "loadFinished", true);
      com.google.common.collect.ArrayListMultimap<Item, ItemStack> itemMap =
          com.google.common.collect.ArrayListMultimap.create();
      itemMap.put(Items.apple, new ItemStack(Items.apple));
      scope.replace(codechicken.nei.ItemList.class, null, "itemMap", itemMap);
      row =
          new RecipeRow(
              facts,
              object("owner", "ae", "handler", "test", "key", "wildcard"),
              "category_test",
              3);
      AeRecipes.capture(recipe, handler, row);
    }
    com.google.gson.JsonObject first =
        row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
    com.google.gson.JsonObject second =
        row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
    if (!first.getAsJsonObject("rule").get("meta").getAsBoolean()
        || !first.getAsJsonObject("rule").get("nbt").getAsBoolean()
        || second.getAsJsonArray("returns").size() != 1
        || !second
            .getAsJsonArray("returns")
            .get(0)
            .getAsJsonObject()
            .get("id")
            .getAsString()
            .equals(Identity.item("minecraft:bucket", 0, null)))
      throw new AssertionError("Wildcard or native crafting container return lost");
    if (wildcard.stackSize != 17 || milk.stackSize != 1)
      throw new AssertionError("Boundary projection mutated native sources");
    if (wireless() && type.getName().contains("wirelesscraftingterminal")) {
      TemplateRecipeHandler foreign =
          (TemplateRecipeHandler)
              Class.forName("appeng.integration.modules.NEIHelpers.NEIAEShapedRecipeHandler")
                  .newInstance();
      try {
        AeRecipes.capture(recipe, foreign, row);
        throw new AssertionError("Foreign native API silently accepted");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        if (!expected.code.equals("recipe_unsupported")) throw expected;
      }
    }
  }

  private static Object ingredient(Class<?> api, ItemStack... stacks) throws Exception {
    if (api.getName().startsWith("appeng.")) {
      Class<?> set = Class.forName("appeng.api.recipes.ResolverResultSet");
      ItemStack[] owned = Arrays.stream(stacks).map(ItemStack::copy).toArray(ItemStack[]::new);
      Object result =
          set.getConstructor(String.class, ItemStack[].class).newInstance("fixture", owned);
      return Class.forName("appeng.recipes.IngredientSet")
          .getConstructor(set, int.class)
          .newInstance(result, stacks[0].stackSize);
    }
    ItemStack stack = stacks[0];
    return java.lang.reflect.Proxy.newProxyInstance(
        api.getClassLoader(),
        new Class<?>[] {api},
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "equals":
              return proxy == args[0];
            case "hashCode":
              return System.identityHashCode(proxy);
            case "getItemStackSet":
              return stacks;
            case "getItemStack":
              return stack;
            case "isAir":
              return false;
            case "getQty":
              return stack.stackSize;
            case "getDamageValue":
              return stack.getItemDamage();
            case "getNameSpace":
              return "minecraft";
            case "getItemName":
              return "test";
            case "bake":
              return null;
            default:
              throw new AssertionError("Unexpected ingredient API: " + method.getName());
          }
        });
  }
}
