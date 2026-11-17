package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/** Isolated real AdvancedSolarPanel registry and machine methods; no world or energy network. */
final class NativeSolarTest {
  static void run() throws Exception {
    cpw.mods.fml.common.Loader loader = cpw.mods.fml.common.Loader.instance();
    Map<String, cpw.mods.fml.common.ModContainer> mods =
        (Map<String, cpw.mods.fml.common.ModContainer>) field(loader, "namedMods");
    cpw.mods.fml.common.ModContainer oldMod = mods.get("AdvancedSolarPanel");
    NativeCoreFixesTest.version("AdvancedSolarPanel", "1.7.10-3.5.1");
    try {
      test();
    } finally {
      if (oldMod == null) mods.remove("AdvancedSolarPanel");
      else mods.put("AdvancedSolarPanel", oldMod);
    }
  }

  private static void test() throws Exception {
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler) type("advsolar.client.nei.MTRecipeHandler").newInstance();
    require(Recipes.adapter(handler) != null, "Molecular transformer has no production adapter");
    Field registry = type("advsolar.utils.MTRecipeManager").getField("transformerRecipes");
    Object saved = registry.get(null);
    try {
      ItemStack input = new ItemStack(Items.milk_bucket, 3, 32767);
      input.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(7));
      ItemStack output = new ItemStack(Items.diamond, 2);
      output.setTagInfo("result", new net.minecraft.nbt.NBTTagString("preserved"));
      Object first = recipe(input, output, Integer.MAX_VALUE);
      Object later =
          recipe(new ItemStack(Items.milk_bucket, 1, 32767), new ItemStack(Items.gold_ingot), 1);
      List<Object> records = Arrays.asList(first, later);
      registry.set(null, new ArrayList<>(records));
      RegistryRecipes cursor = adapter(handler, records);
      RecipeRow row = row(input, output);
      require(
          cursor.size() == 2 && cursor.capture(0, row) && !cursor.capture(1, row(input, output)),
          "First native identity must block later quantity/output variants");
      require(
          handler.arecipes.isEmpty(),
          "Solar semantic capture still writes the native NEI GUI cache");
      sourceBoundaries(registry, cursor, first, input, output);
      JsonObject choice =
          row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
      require(
          choice.get("amount").getAsInt() == 3
              && choice.getAsJsonObject("rule").get("nbt").getAsBoolean()
              && !choice.getAsJsonObject("rule").get("meta").getAsBoolean()
              && choice.getAsJsonArray("returns").size() == 0,
          "Native counts, literal 32767, ignored NBT or no-container consumption changed");
      require(
          row.record.get("energy").isJsonNull()
              && row.record.get("duration").isJsonNull()
              && row.record.getAsJsonObject("process").get("energyPerOperation").getAsInt()
                  == Integer.MAX_VALUE,
          "Total EU became a per-tick rate or fixed duration");
      require(
          row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull()
              && row.outputs
                      .get(0)
                      .getAsJsonObject()
                      .getAsJsonObject("quantity")
                      .get("nominal")
                      .getAsInt()
                  == 2,
          "Output count changed");
      Class<?> machineType = type("advsolar.common.tiles.TileEntityMolecularTransformer");
      Object machine = machineType.newInstance();
      ItemStack[] slots = (ItemStack[]) field(machine, "workSlots");
      for (int meta : new int[] {0, 32767})
        for (int count : new int[] {1, 3, 5}) {
          slots[0] = new ItemStack(Items.milk_bucket, count, meta);
          boolean matches = (Boolean) invoke(machineType, machine, "canSmelt", new Class<?>[0]);
          require(
              matches == (meta == 32767 && count >= 3),
              "Native metadata/quantity predicate differs");
          if (matches)
            require(
                machineType.getField("lastRecipeNumber").getInt(machine) == 0,
                "Native manager chose a later duplicate");
        }
      slots[1] = new ItemStack(Items.gold_ingot);
      require(
          !(Boolean) invoke(machineType, machine, "canSmelt", new Class<?>[0]),
          "Output rejection unexpectedly falls through to later recipe");
      slots[1] = null;
      require(
          (Boolean) invoke(machineType, machine, "canSmelt", new Class<?>[0]),
          "Owned machine cannot prepare recipe");
      machineType.getField("doWork").setBoolean(machine, true);
      require(
          (Integer)
                      invoke(
                          machineType,
                          machine,
                          "gainFuel",
                          new Class<?>[] {int.class},
                          Integer.MAX_VALUE - 1)
                  == 0
              && slots[1] == null,
          "Native craft finished before total energy was supplied");
      require(
          (Integer) invoke(machineType, machine, "gainFuel", new Class<?>[] {int.class}, 2) == 1
              && ItemStack.areItemStacksEqual(slots[1], output),
          "Energy remainder or native output changed");
      ItemStack existing = new ItemStack(Items.diamond, 1);
      existing.setTagInfo("existing", new net.minecraft.nbt.NBTTagInt(9));
      slots[1] = existing;
      require(
          (Boolean) invoke(machineType, machine, "canSmelt", new Class<?>[0]),
          "NBT-different native output rejected");
      machineType.getField("lastRecipeEnergyUsed").setInt(machine, 0);
      invoke(machineType, machine, "gainFuel", new Class<?>[] {int.class}, Integer.MAX_VALUE);
      require(
          slots[1] == existing
              && existing.stackSize == 3
              && existing.getTagCompound().getInteger("existing") == 9
              && !existing.getTagCompound().hasKey("result"),
          "Native merge replaced existing output tags with the recipe prototype");
      writeFixture("native-solar", row, handler, input, output);
      RecipeRow chinese = row("zh_CN", input, output);
      cursor.capture(0, chinese);
      writeFixture("native-solar-zh_CN", chinese, handler, input, output);
      require(
          chinese.record.get("id").equals(row.record.get("id")),
          "Solar recipe identity changes with language");
      try (Recipes.Cursor production =
          new Recipes.Handler(handler, 0).open(row(input, output).facts)) {
        require(
            field(production, "handler") == null, "Production solar cursor retains a GUI handler");
        production.finishVerification();
      }
      cursor.finishVerification();
      cursor.close();
      cursor.close();
      expect("capture_closed", cursor::size);
      require(
          input.stackSize == 3
              && input.getTagCompound().getInteger("ignored") == 7
              && output.hasTagCompound(),
          "Capture mutates native recipe");

      ItemStack largeInput = new ItemStack(Items.paper, 65);
      try (RegistryRecipes large =
          adapter(handler, Collections.singletonList(recipe(largeInput, output, 1)))) {
        RecipeRow largeRow = row(largeInput, output);
        require(
            large.capture(0, largeRow)
                && largeRow
                        .inputs
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonArray("choices")
                        .get(0)
                        .getAsJsonObject()
                        .get("amount")
                        .getAsInt()
                    == 65,
            "Native loaded count above 64 was discarded");
        Object loaded = machineType.newInstance();
        ItemStack[] loadedSlots = (ItemStack[]) field(loaded, "workSlots");
        loadedSlots[0] = largeInput.copy();
        require(
            (Boolean) invoke(machineType, loaded, "canSmelt", new Class<?>[0]),
            "Native larger stored count does not match");
      }

      Object zero = recipe(new ItemStack(Items.paper, 0), new ItemStack(Items.gold_ingot), 0);
      RegistryRecipes free = adapter(handler, Collections.singletonList(zero));
      RecipeRow retained = row(new ItemStack(Items.paper), new ItemStack(Items.gold_ingot));
      free.capture(0, retained);
      JsonObject kept =
          retained
              .inputs
              .get(0)
              .getAsJsonObject()
              .getAsJsonArray("choices")
              .get(0)
              .getAsJsonObject();
      require(
          kept.get("amount").getAsInt() == 1
              && kept.getAsJsonObject("consume").get("kind").getAsString().equals("keep"),
          "Zero-count template lost its required present stack");
      writeFixture(
          "native-solar-zero",
          retained,
          handler,
          new ItemStack(Items.paper),
          new ItemStack(Items.gold_ingot));
      free.finishVerification();
      free.close();
      registry.set(null, new ArrayList<>(Collections.singletonList(zero)));
      machine = machineType.newInstance();
      slots = (ItemStack[]) field(machine, "workSlots");
      slots[0] = new ItemStack(Items.paper);
      require(
          (Boolean) invoke(machineType, machine, "canSmelt", new Class<?>[0]),
          "Native zero-cost recipe rejected");
      machineType.getField("doWork").setBoolean(machine, true);
      require(
          (Integer) invoke(machineType, machine, "gainFuel", new Class<?>[] {int.class}, 0) == 0
              && slots[1] != null,
          "Native zero-energy operation stalled");
      reject(() -> adapter(handler, Collections.singletonList(new Object())));
      for (Object invalid :
          Arrays.asList(
              recipe(new ItemStack(Items.paper, -1), output, 1), recipe(input, output, -1)))
        reject(
            () ->
                adapter(handler, Collections.singletonList(invalid))
                    .capture(0, row(input, output)));
      registry.set(null, new ArrayList<>());
      try (RegistryRecipes empty = new SolarRecipes(handler)) {
        require(empty.size() == 0, "Ready-empty solar registry was rejected");
        empty.finishVerification();
      }
      registry.set(null, null);
      expect("handler_not_ready", () -> new SolarRecipes(handler));
      UnknownList unknown = new UnknownList();
      registry.set(null, unknown);
      reject(() -> new SolarRecipes(handler));
      require(unknown.calls == 0, "Unknown solar registry callback was invoked");
      net.minecraft.item.Item foreign =
          NativeCoreFixesTest.item(UnknownMetadata.class.getName(), "solar_unknown_metadata");
      reject(
          () ->
              adapter(
                  handler,
                  Collections.singletonList(uncheckedRecipe(new ItemStack(foreign), output))));
      require(UnknownMetadata.calls == 0, "Unknown solar metadata callback was invoked");
      registry.set(null, new ArrayList<>(records));
      require(
          RegistrationBindings.read(handler)
              .same(
                  RegistrationBindings.read(
                      (codechicken.nei.recipe.IRecipeHandler) handler.getClass().newInstance())),
          "Independent solar registrations lost the same native source");
      System.out.println(
          "Native molecular transformer: literal metadata, ordered blocking, zero/large energy, quantities, owned inputs and outputs passed");
    } finally {
      registry.set(null, saved);
    }
  }

  private static void sourceBoundaries(
      Field registry, RegistryRecipes cursor, Object recipe, ItemStack input, ItemStack output)
      throws Exception {
    Object originalRoot = registry.get(null);
    registry.set(null, new ArrayList<>((List<?>) originalRoot));
    try {
      expect("environment_changed", cursor::verify);
    } finally {
      registry.set(null, originalRoot);
    }
    List source = (List) originalRoot;
    Object originalMember = source.get(0);
    source.set(0, recipe(input.copy(), output.copy(), Integer.MAX_VALUE));
    try {
      expect("environment_changed", cursor::finishVerification);
    } finally {
      source.set(0, originalMember);
    }
    Field field = recipe.getClass().getField("inputStack");
    field.set(recipe, input.copy());
    try {
      expect("environment_changed", () -> cursor.capture(0, row(input, output)));
    } finally {
      field.set(recipe, input);
    }
    net.minecraft.nbt.NBTTagCompound tag = input.getTagCompound();
    input.setTagCompound((net.minecraft.nbt.NBTTagCompound) tag.copy());
    try {
      expect("environment_changed", () -> cursor.capture(0, row(input, output)));
    } finally {
      input.setTagCompound(tag);
    }
    Field energy = recipe.getClass().getField("energyPerOperation");
    energy.setInt(recipe, 1);
    try {
      expect("environment_changed", () -> cursor.capture(0, row(input, output)));
    } finally {
      energy.setInt(recipe, Integer.MAX_VALUE);
    }
    cursor.finishVerification();
  }

  private static void expect(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected solar fault " + code);
    } catch (Jobs.Fault failure) {
      require(failure.code.equals(code), "Wrong solar fault: " + failure);
    }
  }

  public static final class UnknownList extends ArrayList<Object> {
    int calls;

    @Override
    public Iterator<Object> iterator() {
      calls++;
      throw new AssertionError("Unknown solar list callback");
    }
  }

  public static final class UnknownMetadata extends net.minecraft.item.Item {
    static int calls;

    @Override
    public int getDamage(ItemStack stack) {
      calls++;
      throw new AssertionError("Unknown solar metadata callback");
    }
  }

  private static Object uncheckedRecipe(ItemStack input, ItemStack output) {
    try {
      return recipe(input, output, 1);
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
  }

  private static Object recipe(ItemStack input, ItemStack output, int energy) throws Exception {
    Class<?> type = type("advsolar.utils.MTRecipeRecord");
    Object record = type.newInstance();
    type.getField("inputStack").set(record, input);
    type.getField("outputStack").set(record, output);
    type.getField("energyPerOperation").setInt(record, energy);
    return record;
  }

  private static RegistryRecipes adapter(TemplateRecipeHandler handler, List<?> records) {
    try {
      type("advsolar.utils.MTRecipeManager")
          .getField("transformerRecipes")
          .set(null, new ArrayList<>(records));
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
    return new SolarRecipes(handler);
  }

  @SuppressWarnings("unchecked")
  private static RecipeRow row(ItemStack... stacks) {
    return row("en_US", stacks);
  }

  private static RecipeRow row(String locale, ItemStack... stacks) {
    Facts facts = new Facts(locale);
    Set<String> known = (Set<String>) field(facts, "items");
    for (ItemStack stack : stacks)
      known.add(
          Identity.item(
              net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
              Items.feather.getDamage(stack),
              TypedNbt.encode(stack.getTagCompound())));
    return new RecipeRow(
        facts, object("owner", "fixture", "handler", "solar", "key", "native"), "fixture", 0);
  }

  private static void writeFixture(
      String name, RecipeRow row, TemplateRecipeHandler handler, ItemStack... stacks)
      throws Exception {
    row.record.add(
        "source",
        object(
            "owner",
            "AdvancedSolarPanel",
            "handler",
            handler.getClass().getName(),
            "key",
            name.endsWith("-zh_CN") ? name.substring(0, name.length() - 6) : name));
    row.record.addProperty(
        "category", Identity.content("category", row.record.getAsJsonObject("source")));
    NativeContractFixture.writeOwnedWithDurability(name, row, Arrays.asList(stacks));
  }

  private static void reject(Runnable action) {
    try {
      action.run();
    } catch (Jobs.Fault failure) {
      require(failure.code.equals("recipe_unsupported"), "Wrong rejection: " + failure);
      return;
    }
    throw new AssertionError("Invalid native recipe accepted");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
