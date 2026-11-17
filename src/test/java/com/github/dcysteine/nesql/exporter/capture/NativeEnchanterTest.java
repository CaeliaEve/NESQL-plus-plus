package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Owned EnderIO registry and native recipe/cache methods; no world, player or GL. */
final class NativeEnchanterTest {
  private static final String ROOT = "crazypants.enderio.machine.";

  @SuppressWarnings("unchecked")
  static void run() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      runOwned(scope);
    }
  }

  @SuppressWarnings("unchecked")
  private static void runOwned(NativeRegistryScope scope) throws Exception {
    Class<?> inputType = type(ROOT + "recipe.RecipeInput"),
        recipeType = type(ROOT + "enchanter.EnchanterRecipe");
    require(
        recipeType
            .getProtectionDomain()
            .getCodeSource()
            .getLocation()
            .toString()
            .contains("/native-tests/enderio.jar!"),
        "Pinned EnderIO jar required");
    NativeCoreFixesTest.version("EnderIO", "2.9.28");
    NativeCoreFixesTest.version("Thaumcraft", "4.2.3.5");
    NativeCoreFixesTest.version("CoFHCore", "3.1.4");
    Enchantment soulBound =
        (Enchantment) scope.allocate(type("crazypants.enderio.enchantment.EnchantmentSoulBound"));
    require(
        EnchanterSource.maximum(soulBound) == 1, "Audited native SoulBound maximum was rejected");
    for (String enchantment :
        new String[] {"Repair", "Frugal", "Potency", "Haste", "WandFortune"}) {
      Enchantment nativeEnchantment =
          (Enchantment)
              scope.allocate(type("thaumcraft.common.lib.enchantment.Enchantment" + enchantment));
      require(
          EnchanterSource.maximum(nativeEnchantment) == nativeEnchantment.getMaxLevel(),
          "Native Thaumcraft maximum differs");
    }
    for (String enchantment : new String[] {"Holding", "Multishot"}) {
      Enchantment nativeEnchantment =
          (Enchantment) scope.allocate(type("cofh.core.enchantment.Enchantment" + enchantment));
      require(
          EnchanterSource.maximum(nativeEnchantment) == nativeEnchantment.getMaxLevel(),
          "Native CoFH maximum differs");
    }
    UnknownEnchantment unknown = scope.allocate(UnknownEnchantment.class);
    try {
      EnchanterSource.maximum(unknown);
      throw new AssertionError("Unknown enchantment maximum executed");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(
          expected.code.equals("recipe_unsupported") && unknown.calls == 0,
          "Unknown maximum callback was executed before rejection");
    }
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler) type("crazypants.enderio.nei.EnchanterRecipeHandler").newInstance();
    TemplateRecipeHandler.CachedRecipe borrowed =
        handler.new CachedRecipe() {
          @Override
          public codechicken.nei.PositionedStack getResult() {
            return null;
          }
        };
    handler.arecipes.add(borrowed);
    require(Recipes.adapter(handler) != null, "Missing EnderIO enchanter adapter");
    ItemStack material = new ItemStack(Items.dye, 3, 4);
    Object input =
        inputType.getConstructor(ItemStack.class, boolean.class).newInstance(material, true);
    Object recipe =
        recipeType
            .getConstructor(inputType, Enchantment.class, int.class)
            .newInstance(input, Enchantment.sharpness, 2);
    ItemStack tagged = material.copy();
    tagged.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("arbitrary"));
    require(
        (Boolean) invoke(inputType, input, "isInput", new Class<?>[] {ItemStack.class}, tagged),
        "Native input must ignore NBT");
    Object wildcard =
        inputType
            .getConstructor(ItemStack.class, boolean.class)
            .newInstance(new ItemStack(Items.dye, 3, 32767), true);
    require(
        !(Boolean)
            invoke(inputType, wildcard, "isInput", new Class<?>[] {ItemStack.class}, material),
        "Native literal 32767 changed");
    for (int count : new int[] {2, 3, 5, 6, 14, 15, 64}) {
      int expected = Math.min(count / 3, 5);
      require(
          (Integer)
                  invoke(
                      recipeType, recipe, "getLevelForStackSize", new Class<?>[] {int.class}, count)
              == expected,
          "Native level boundary changed");
    }
    Class<?> tile = type(ROOT + "enchanter.TileEnchanter"),
        config = type("crazypants.enderio.config.Config");
    Field base = config.getField("enchanterBaseLevelCost");
    int oldBase = base.getInt(null);
    try {
      base.setInt(null, 7);
      for (int level = 1; level <= 5; level++)
        require(
            (Integer)
                    invoke(
                        tile,
                        null,
                        "getEnchantmentCost",
                        new Class<?>[] {recipeType, int.class},
                        recipe,
                        level)
                == 7 + 2 * level * level,
            "Native quadratic XP differs");
    } finally {
      base.setInt(null, oldBase);
    }
    Class<?> adapter = type("com.github.dcysteine.nesql.exporter.capture.EnderEnchanterRecipes");
    Constructor<?> constructor =
        adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class, int.class);
    constructor.setAccessible(true);
    Object unregisteredEnchantment =
        recipeType
            .getConstructor(inputType, Enchantment.class, int.class)
            .newInstance(input, null, 2);
    require(
        !(Boolean) invoke(recipeType, unregisteredEnchantment, "isValid", new Class<?>[0]),
        "Missing native enchantment must be an invalid registration");
    try (RegistryRecipes invalidRows =
        (RegistryRecipes)
            constructor.newInstance(handler, Arrays.asList(unregisteredEnchantment, recipe), 7)) {
      require(
          invalidRows.size() == 6,
          "Native invalid registrations silently vanished from the census");
      RecipeRow invalidRow = NativeCoreFixesTest.row(material);
      require(
          !invalidRows.capture(0, invalidRow)
              && invalidRow.exclusion != null
              && invalidRow
                  .exclusion
                  .get("reason")
                  .getAsString()
                  .equals("native_recipe_isValid_false"),
          "Native invalid registration lost its scoped exclusion proof");
      invalidRows.finishVerification();
    }
    RegistryRecipes cursor =
        (RegistryRecipes) constructor.newInstance(handler, Arrays.asList(recipe), 7);
    require(cursor.size() == 5, "Each native enchantment level needs its own row");
    for (int baseCost : new int[] {-7, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
      try (RegistryRecipes signed =
          (RegistryRecipes) constructor.newInstance(handler, Arrays.asList(recipe), baseCost)) {
        RecipeRow row =
            NativeCoreFixesTest.row(material, new ItemStack(Items.writable_book), book(2));
        signed.capture(1, row);
        require(
            row.record.getAsJsonObject("process").get("cost").getAsInt() == baseCost + 8,
            "Signed native XP changed");
        write(
            "native-enchanter-signed-" + Integer.toHexString(baseCost),
            row,
            Arrays.asList(material, new ItemStack(Items.writable_book), book(2)));
      }
    }
    for (int index = 0; index < 5; index++) {
      Facts facts = facts(material, new ItemStack(Items.writable_book), book(index + 1));
      RecipeRow row =
          new RecipeRow(
              facts,
              object("owner", "fixture", "handler", "enchanter", "key", "enchanter"),
              "fixture",
              index);
      require(cursor.capture(index, row), "Reachable level omitted");
      require(
          handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
          "Ender enchanter capture replaced borrowed GUI cache");
      JsonObject process = row.record.getAsJsonObject("process");
      require(
          process.get("level").getAsInt() == index + 1
              && process.get("cost").getAsInt() == 7 + 2 * (index + 1) * (index + 1),
          "Level or quadratic XP cost lost");
      require(
          row.inputs
                  .get(1)
                  .getAsJsonObject()
                  .getAsJsonArray("choices")
                  .get(0)
                  .getAsJsonObject()
                  .get("amount")
                  .getAsInt()
              == 3 * (index + 1),
          "Material count changed");
      require(
          row.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(id(book(index + 1))),
          "Native enchanted-book NBT differs");
      write(
          "native-enchanter-level-" + (index + 1),
          row,
          Arrays.asList(material, new ItemStack(Items.writable_book), book(index + 1)));
    }
    RecipeRow translated =
        new RecipeRow(
            facts("zh_CN", material, new ItemStack(Items.writable_book), book(2)),
            object(),
            "fixture",
            1);
    require(cursor.capture(1, translated), "Chinese native enchanter row omitted");
    write(
        "native-enchanter-level-2-zh_CN",
        "native-enchanter-level-2",
        translated,
        Arrays.asList(material, new ItemStack(Items.writable_book), book(2)));
    Field inputField = recipeType.getDeclaredField("input");
    inputField.setAccessible(true);
    try {
      inputField.set(
          recipe,
          inputType.getConstructor(ItemStack.class, boolean.class).newInstance(material, true));
      try {
        cursor.finishVerification();
        throw new AssertionError("Same-content enchanter input replacement was accepted");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        require(expected.code.equals("recipe_changed"), "Wrong enchanter drift failure");
      }
    } finally {
      inputField.set(recipe, input);
    }
    ItemStack registeredInput = (ItemStack) field(input, "input");
    int originalCount = registeredInput.stackSize;
    try {
      registeredInput.stackSize = 0;
      require(
          (Integer)
                  invoke(recipeType, recipe, "getLevelForStackSize", new Class<?>[] {int.class}, 6)
              == 2,
          "Native divisor was recomputed from changed display prototype");
      try (RegistryRecipes retained =
          (RegistryRecipes) constructor.newInstance(handler, Arrays.asList(recipe), 7)) {
        RecipeRow stored =
            NativeCoreFixesTest.row(material, new ItemStack(Items.writable_book), book(2));
        retained.capture(1, stored);
        require(
            stored.record.getAsJsonObject("process").get("itemsPerLevel").getAsInt() == 3,
            "Stored registration divisor lost");
        retained.finishVerification();
        write(
            "native-enchanter-stored-divisor",
            stored,
            Arrays.asList(material, new ItemStack(Items.writable_book), book(2)));
      }
    } finally {
      registeredInput.stackSize = originalCount;
    }
    require(
        material.stackSize == 3
            && ((ItemStack) invoke(inputType, input, "getInput", new Class<?>[0])).stackSize == 3,
        "Capture mutated registry input");
    // First matching recipe wins BEFORE quantity checks, even if it requires more items.
    Object firstInput =
        inputType
            .getConstructor(ItemStack.class, boolean.class)
            .newInstance(new ItemStack(Items.dye, 10, 4), true);
    Object first =
        recipeType
            .getConstructor(inputType, Enchantment.class, int.class)
            .newInstance(firstInput, Enchantment.sharpness, 1);
    Object manager = type(ROOT + "enchanter.EnchanterRecipeManager").newInstance();
    ((List<Object>) invoke(manager.getClass(), manager, "getRecipes", new Class<?>[0]))
        .addAll(Arrays.asList(first, recipe));
    require(
        invoke(
                manager.getClass(),
                manager,
                "getEnchantmentRecipeForInput",
                new Class<?>[] {ItemStack.class},
                material)
            == first,
        "Native first-match precedence changed");
    cursor = (RegistryRecipes) constructor.newInstance(handler, Arrays.asList(first, recipe), 0);
    RecipeRow shadowed = new RecipeRow(facts(material), object(), "fixture", 5);
    require(
        !cursor.capture(5, shadowed)
            && shadowed.exclusion != null
            && shadowed
                .exclusion
                .get("reason")
                .getAsString()
                .equals("all_input_patterns_covered_by_earlier_native_entries"),
        "Earlier native input match must retain exclusion evidence regardless of quantity");
    // Exact meta before broad input leaves other metadata reachable, with an explicit exclusion.
    Object broad =
        inputType.getConstructor(ItemStack.class, boolean.class).newInstance(material, false);
    Object later =
        recipeType
            .getConstructor(inputType, Enchantment.class, int.class)
            .newInstance(broad, Enchantment.sharpness, 2);
    cursor = (RegistryRecipes) constructor.newInstance(handler, Arrays.asList(first, later), 0);
    RecipeRow row =
        new RecipeRow(
            facts(
                material,
                new ItemStack(Items.dye, 1, 0),
                new ItemStack(Items.writable_book),
                book(1)),
            object(),
            "fixture",
            5);
    require(cursor.capture(5, row), "Partially shadowed predicate dropped");
    require(
        row.inputs
            .get(1)
            .getAsJsonObject()
            .getAsJsonArray("choices")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("rule")
            .get("kind")
            .getAsString()
            .equals("except"),
        "Priority filter omitted");
    // Ore matching ignores template metadata and uses actual native membership.
    OreDictionary.registerOre("nesqlEnchanterFixture", new ItemStack(Items.gold_ingot));
    int ore = OreDictionary.getOreID("nesqlEnchanterFixture");
    Object oreInput =
        type(ROOT + "recipe.OreDictionaryRecipeInput")
            .getConstructor(ItemStack.class, int.class, int.class)
            .newInstance(new ItemStack(Items.gold_ingot, 2), ore, -1);
    require(
        (Boolean)
            invoke(
                oreInput.getClass(),
                oreInput,
                "isInput",
                new Class<?>[] {ItemStack.class},
                new ItemStack(Items.gold_ingot)),
        "Native ore fixture broken");
    require(
        (Boolean)
            invoke(
                oreInput.getClass(),
                oreInput,
                "isInput",
                new Class<?>[] {ItemStack.class},
                new ItemStack(Items.gold_ingot, 1, 65536)),
        "Baked ore metadata key did not wrap natively");
    Object oreRecipe =
        recipeType
            .getConstructor(inputType, Enchantment.class, int.class)
            .newInstance(oreInput, Enchantment.sharpness, 0);
    cursor = (RegistryRecipes) constructor.newInstance(handler, Arrays.asList(oreRecipe), 0);
    row =
        new RecipeRow(
            facts(new ItemStack(Items.gold_ingot), new ItemStack(Items.writable_book), book(1)),
            object(),
            "fixture",
            0);
    require(
        cursor.capture(0, row) && row.record.getAsJsonObject("process").get("cost").getAsInt() == 0,
        "Ore/free recipe lost");
    write(
        "native-enchanter-ore",
        row,
        Arrays.asList(
            new ItemStack(Items.gold_ingot), new ItemStack(Items.writable_book), book(1)));
    Object literalFirst =
        inputType
            .getConstructor(ItemStack.class, boolean.class)
            .newInstance(new ItemStack(Items.gold_ingot), true);
    Object literalRecipe =
        recipeType
            .getConstructor(inputType, Enchantment.class, int.class)
            .newInstance(literalFirst, Enchantment.sharpness, 0);
    try (RegistryRecipes alias =
        (RegistryRecipes)
            constructor.newInstance(handler, Arrays.asList(literalRecipe, oreRecipe), 0)) {
      RecipeRow aliasRow =
          NativeCoreFixesTest.row(
              new ItemStack(Items.gold_ingot),
              new ItemStack(Items.gold_ingot, 1, 65536),
              new ItemStack(Items.writable_book),
              book(1));
      require(
          alias.capture(5, aliasRow),
          "A literal predecessor incorrectly shadowed the whole native ore metadata class");
      require(
          aliasRow
              .inputs
              .get(1)
              .getAsJsonObject()
              .getAsJsonArray("choices")
              .get(0)
              .getAsJsonObject()
              .get("id")
              .getAsString()
              .equals(id(new ItemStack(Items.gold_ingot, 1, 65536))),
          "Ore witness lost native metadata alias after exact predecessor");
      write(
          "native-enchanter-ore-alias",
          aliasRow,
          Arrays.asList(
              new ItemStack(Items.gold_ingot),
              new ItemStack(Items.gold_ingot, 1, 65536),
              new ItemStack(Items.writable_book),
              book(1)));
      alias.finishVerification();
    }
    cursor.finishVerification();
    List<List<ItemStack>> displays =
        (List<List<ItemStack>>) field(OreDictionary.class, null, "idToStack");
    List<ItemStack> originalDisplay = displays.get(ore);
    displays.set(ore, new ArrayList<>());
    try (RegistryRecipes baked =
        (RegistryRecipes) constructor.newInstance(handler, Arrays.asList(oreRecipe), 0)) {
      RecipeRow fresh =
          new RecipeRow(
              facts(new ItemStack(Items.gold_ingot), new ItemStack(Items.writable_book), book(1)),
              object(),
              "fixture",
              0);
      require(
          baked.capture(0, fresh),
          "Enchanter still uses ore display list instead of native baked index");
      baked.finishVerification();
    } finally {
      displays.set(ore, originalDisplay);
    }
    Map<Integer, List<Integer>> index =
        (Map<Integer, List<Integer>>) field(OreDictionary.class, null, "stackToId");
    int key = Item.getIdFromItem(Items.gold_ingot) | 1 << 16;
    List<Integer> originalIds = index.get(key);
    try {
      index.put(key, new ArrayList<>(originalIds));
      changed(cursor);
    } finally {
      index.put(key, originalIds);
    }
    cursor.close();
    cursor.close();
    try {
      cursor.size();
      throw new AssertionError("Closed enchanter exposes rows");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(expected.code.equals("capture_closed"), "Wrong close code");
    }
    try (RegistryRecipes empty =
        (RegistryRecipes) constructor.newInstance(handler, new ArrayList<>(), 0)) {
      require(empty.size() == 0, "Empty native enchanter invents rows");
      empty.finishVerification();
    }
    NativeCoreFixesTest.version("EnderIO", "2.9.28");
    Class<?> managerType = type(ROOT + "enchanter.EnchanterRecipeManager");
    scope.replace(managerType, null, "instance", manager);
    scope.replace(managerType, manager, "recipes", new ArrayList<>(Arrays.asList(recipe)));
    scope.replace(config, null, "enchanterBaseLevelCost", 7);
    Object nativeTile = tile.newInstance();
    ItemStack[] nativeInventory = (ItemStack[]) field(nativeTile, "inv");
    nativeInventory[0] = new ItemStack(Items.stick, 5);
    nativeInventory[1] = new ItemStack(Items.dye, 7, 4);
    require(
        invoke(tile, nativeTile, "getCurrentEnchantmentRecipe", new Class<?>[0]) == recipe,
        "Runtime enchanter requires book item instead of presence");
    EnchantmentData data =
        (EnchantmentData) invoke(tile, nativeTile, "getCurrentEnchantmentData", new Class<?>[0]);
    require(
        data.enchantmentLevel == 2, "Native stored material count did not select level interval");
    nativeInventory[1].stackSize = 9;
    data = (EnchantmentData) invoke(tile, nativeTile, "getCurrentEnchantmentData", new Class<?>[0]);
    require(data.enchantmentLevel == 3, "Native quantity gate accepted wrong level interval");
    nativeInventory[0] = null;
    require(
        invoke(tile, nativeTile, "getCurrentEnchantmentRecipe", new Class<?>[0]) == null,
        "Native book-slot empty gate missing");
    nativePickup(scope, tile, nativeTile, nativeInventory, false, 7, 1, 15);
    nativePickup(scope, tile, nativeTile, nativeInventory, true, 7, 1, 0);
    nativePickup(scope, tile, nativeTile, nativeInventory, false, 9, 0, 25);
    nativePickup(scope, tile, nativeTile, nativeInventory, false, 65, 50, 57);
    Object largeInput =
        inputType
            .getConstructor(ItemStack.class, boolean.class)
            .newInstance(new ItemStack(Items.dye, 13, 4), true);
    Object largeRecipe =
        recipeType
            .getConstructor(inputType, Enchantment.class, int.class)
            .newInstance(largeInput, Enchantment.sharpness, 1);
    try (RegistryRecipes large =
        (RegistryRecipes) constructor.newInstance(handler, Arrays.asList(largeRecipe), 0)) {
      RecipeRow largeRow =
          NativeCoreFixesTest.row(material, new ItemStack(Items.writable_book), book(5));
      require(
          large.capture(4, largeRow),
          "Legal stored count 65 was treated as unreachable GUI slot limit");
      write(
          "native-enchanter-stored-65",
          largeRow,
          Arrays.asList(material, new ItemStack(Items.writable_book), book(5)));
    }
    try (Recipes.Cursor production =
        new Recipes.Handler(handler, 0)
            .open(facts(material, new ItemStack(Items.writable_book), book(1)))) {
      require(field(production, "handler") == null, "Enchanter production retains GUI handler");
      production.capture(0);
      production.finishVerification();
    }
    RegistrationBindings binding = RegistrationBindings.read(handler);
    for (int source = 0; source < 4; source++)
      require(
          binding.same(
              RegistrationBindings.read((TemplateRecipeHandler) handler.getClass().newInstance())),
          "Enchanter discovery source differs");
    try (RegistryRecipes bound = new EnderEnchanterRecipes(handler)) {
      Object table = field(manager, "recipes");
      scope.replace(managerType, manager, "recipes", new ArrayList<>(Arrays.asList(recipe)));
      changed(bound);
      scope.replace(managerType, manager, "recipes", table);
      base.setInt(null, 8);
      changed(bound);
      base.setInt(null, 7);
      bound.finishVerification();
    }
    Object nativeTable = field(manager, "recipes");
    try {
      scope.replace(managerType, manager, "recipes", new ArrayList<>());
      try (RegistryRecipes emptyNative = new EnderEnchanterRecipes(handler)) {
        require(emptyNative.size() == 0, "Native initialized empty enchanter invented recipes");
        emptyNative.finishVerification();
      }
      scope.replace(managerType, manager, "recipes", null);
      try {
        new EnderEnchanterRecipes(handler);
        throw new AssertionError("Not-ready enchanter silently became an empty registry");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        require(expected.code.equals("handler_not_ready"), "Wrong native readiness failure");
      }
    } finally {
      scope.replace(managerType, manager, "recipes", nativeTable);
    }
    Object zero =
        inputType
            .getConstructor(ItemStack.class, boolean.class)
            .newInstance(new ItemStack(Items.dye, 0, 4), true);
    Object invalid =
        recipeType
            .getConstructor(inputType, Enchantment.class, int.class)
            .newInstance(zero, Enchantment.sharpness, 0);
    try {
      constructor.newInstance(handler, Arrays.asList(invalid), 0);
      throw new AssertionError("Native divide-by-zero input accepted");
    } catch (InvocationTargetException expected) {
      require(
          expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,
          "Wrong invalid recipe error");
    }
    System.out.println(
        "EnderIO enchanter: level boundaries, XP, NBT, metadata, precedence, ore and ownership passed");
  }

  private static ItemStack book(int level) {
    ItemStack book = new ItemStack(Items.enchanted_book);
    Items.enchanted_book.addEnchantment(book, new EnchantmentData(Enchantment.sharpness, level));
    return book;
  }

  public static final class UnknownEnchantment extends Enchantment {
    int calls;

    private UnknownEnchantment() {
      super(0, 0, net.minecraft.enchantment.EnumEnchantmentType.all);
    }

    @Override
    public int getMaxLevel() {
      calls++;
      return 5;
    }
  }

  private static void nativePickup(
      NativeRegistryScope scope,
      Class<?> tile,
      Object machine,
      ItemStack[] inventory,
      boolean creative,
      int count,
      int remainder,
      int debit)
      throws Exception {
    PickupWorld world = scope.allocate(PickupWorld.class);
    ((net.minecraft.tileentity.TileEntity) machine).setWorldObj(world);
    NativeChiselTest.NativePlayer player = scope.allocate(NativeChiselTest.NativePlayer.class);
    player.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
    player.capabilities.isCreativeMode = creative;
    player.experienceLevel = 100;
    player.worldObj = world;
    inventory[0] = new ItemStack(Items.stick, 5);
    inventory[1] = new ItemStack(Items.dye, count, 4);
    Class<?> owner = type(ROOT + "enchanter.ContainerEnchanter"),
        slot = type(ROOT + "enchanter.ContainerEnchanter$3");
    Constructor<?> make =
        slot.getDeclaredConstructor(
            owner, net.minecraft.inventory.IInventory.class, int.class, int.class, int.class, tile);
    make.setAccessible(true);
    net.minecraft.inventory.Slot output =
        (net.minecraft.inventory.Slot) make.newInstance(null, machine, 2, 0, 0, machine);
    output.onPickupFromSlot(player, book(1));
    require(inventory[0] == null, "Native pickup did not clear the entire five-item book slot");
    require(
        remainder == 0
            ? inventory[1] == null
            : inventory[1] != null && inventory[1].stackSize == remainder,
        "Native pickup material remainder differs");
    require(
        player.experienceLevel == 100 - (creative ? 0 : debit), "Native pickup XP debit differs");
    require(world.sounds == 1, "Native pickup server sound path missing");
  }

  public static final class PickupWorld extends net.minecraft.client.multiplayer.WorldClient {
    int sounds;

    private PickupWorld() {
      super(null, null, 0, null, null);
    }

    @Override
    public void markTileEntityChunkModified(
        int x, int y, int z, net.minecraft.tileentity.TileEntity tile) {}

    @Override
    public void playAuxSFX(int code, int x, int y, int z, int data) {
      require(code == 1021, "Unexpected native sound");
      sounds++;
    }

    @Override
    public net.minecraft.block.Block getBlock(int x, int y, int z) {
      return net.minecraft.init.Blocks.air;
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
      return 0;
    }
  }

  private static void write(String name, RecipeRow row, Collection<ItemStack> specimens)
      throws Exception {
    write(name, name, row, specimens);
  }

  private static void write(String name, String key, RecipeRow row, Collection<ItemStack> specimens)
      throws Exception {
    row.record.add(
        "source",
        object(
            "owner",
            "EnderIO",
            "handler",
            "crazypants.enderio.nei.EnchanterRecipeHandler",
            "key",
            key));
    row.record.addProperty(
        "category", Identity.content("category", row.record.getAsJsonObject("source")));
    NativeContractFixture.writeOwned(name, row, specimens);
  }

  private static void changed(RegistryRecipes reader) {
    try {
      reader.finishVerification();
      throw new AssertionError("Enchanter drift accepted");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(expected.code.equals("recipe_changed"), "Wrong drift code");
    }
  }

  private static String id(ItemStack item) {
    return Identity.item(
        Item.itemRegistry.getNameForObject(item.getItem()),
        item.getItemDamage(),
        TypedNbt.encode(item.getTagCompound()));
  }

  @SuppressWarnings("unchecked")
  private static Facts facts(ItemStack... items) {
    return facts("en_US", items);
  }

  @SuppressWarnings("unchecked")
  private static Facts facts(String locale, ItemStack... items) {
    Facts facts = new Facts(locale);
    for (ItemStack item : items) ((Set<String>) field(facts, "items")).add(id(item));
    return facts;
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
