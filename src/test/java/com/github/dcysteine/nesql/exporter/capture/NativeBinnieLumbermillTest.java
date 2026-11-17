package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.*;

/** Exercises the pinned Binnie recipe lookup and actual machine finish/drain methods. */
public final class NativeBinnieLumbermillTest {
  public static void run() throws Exception {
    java.io.File configRoot =
        java.nio.file.Files.createTempDirectory("binnie-native-game-").toFile();
    java.io.File oldLaunch = net.minecraft.launchwrapper.Launch.minecraftHome;
    java.io.File oldFml =
        (java.io.File)
            MagicApi.field(cpw.mods.fml.relauncher.FMLInjectionData.class, null, "minecraftHome");
    net.minecraft.launchwrapper.Launch.minecraftHome = configRoot;
    cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
        cpw.mods.fml.relauncher.FMLInjectionData.class, null, configRoot, "minecraftHome");
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      test(scope);
    } finally {
      net.minecraft.launchwrapper.Launch.minecraftHome = oldLaunch;
      cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
          cpw.mods.fml.relauncher.FMLInjectionData.class, null, oldFml, "minecraftHome");
    }
  }

  private static void test(NativeRegistryScope scope) throws Exception {
    Class<?> lumbermill = Class.forName("binnie.extratrees.machines.lumbermill.Lumbermill");
    Field recipes = lumbermill.getField("recipes");
    Object saved = recipes.get(null);
    Class<?> extraTrees = Class.forName("binnie.extratrees.ExtraTrees");
    Field miscField = extraTrees.getField("itemMisc");
    Object savedMisc = miscField.get(null);
    Item misc = NativeCoreFixesTest.item("binnie.core.item.ItemMisc", "binnie_misc");
    Object[] types =
        (Object[])
            Class.forName("binnie.extratrees.item.ExtraTreeItems").getMethod("values").invoke(null);
    Field miscTypes = misc.getClass().getDeclaredField("items");
    miscTypes.setAccessible(true);
    miscTypes.set(misc, types);
    miscField.set(null, misc);
    Item woodItem = NativeCoreFixesTest.item("net.minecraft.item.Item", "binnie_wood");
    woodItem.setHasSubtypes(true);
    ItemStack wood = new ItemStack(woodItem, 17, 2), plank = new ItemStack(Items.paper, 11);
    plank.setTagInfo("product", new net.minecraft.nbt.NBTTagInt(7));
    Map<ItemStack, ItemStack> registry = new LinkedHashMap<>();
    registry.put(wood, plank);
    recipes.set(null, registry);
    net.minecraftforge.oredict.OreDictionary.registerOre("logWood", new ItemStack(woodItem, 1, 2));
    ItemStack bark =
        (ItemStack) types[2].getClass().getMethod("get", int.class).invoke(types[2], 1);
    ItemStack dust =
        (ItemStack) types[1].getClass().getMethod("get", int.class).invoke(types[1], 1);
    try {
      Method product = lumbermill.getMethod("getPlankProduct", ItemStack.class);
      ItemStack tagged = new ItemStack(woodItem, 1, 2);
      tagged.setTagInfo("different", new net.minecraft.nbt.NBTTagInt(9));
      ItemStack nativeProduct = (ItemStack) product.invoke(null, tagged);
      require(
          nativeProduct != null
              && nativeProduct.stackSize == 6
              && nativeProduct.getTagCompound().getInteger("product") == 7,
          "Native lookup must ignore input NBT/count and force six planks while preserving output NBT");
      require(
          product.invoke(null, new ItemStack(woodItem, 1, 3)) == null,
          "Native metadata distinction lost");
      Object validator =
          Class.forName("binnie.extratrees.machines.lumbermill.LogSlotValidator").newInstance();
      String firstOre =
          net.minecraftforge.oredict.OreDictionary.getOreName(
              net.minecraftforge.oredict.OreDictionary.getOreID(tagged));
      require(
          (Boolean)
              validator.getClass().getMethod("isValid", ItemStack.class).invoke(validator, tagged),
          "Native log insertion gate changed; firstOre=" + firstOre);
      ItemStack sentinel = new ItemStack(woodItem, 1, 32767);
      registry.put(sentinel, new ItemStack(Items.diamond));
      require(
          product.invoke(null, new ItemStack(woodItem, 1, 4)) == null,
          "Literal 32767 became an ore wildcard");
      registry.remove(sentinel);
      nativeFinish(registry, wood, bark, dust);
      Class<?> adapter;
      try {
        adapter =
            Class.forName("com.github.dcysteine.nesql.exporter.capture.BinnieLumbermillRecipes");
      } catch (ClassNotFoundException e) {
        throw new AssertionError("Missing native Binnie lumbermill adapter", e);
      }
      TemplateRecipeHandler handler =
          (TemplateRecipeHandler)
              Class.forName("binnie.extratrees.nei.NEIHandlerLumbermill").newInstance();
      TemplateRecipeHandler.CachedRecipe borrowed =
          handler.new CachedRecipe() {
            @Override
            public codechicken.nei.PositionedStack getResult() {
              return null;
            }
          };
      handler.arecipes.add(borrowed);
      Constructor<?> ctor =
          adapter.getDeclaredConstructor(
              TemplateRecipeHandler.class,
              Map.class,
              ItemStack.class,
              ItemStack.class,
              FluidStack.class);
      ctor.setAccessible(true);
      NativeCoreFixesTest.version("BinnieCore", "2.5.24");
      NativeCoreFixesTest.version("ExtraTrees", "2.5.24");
      Constructor<?> nativeCtor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class);
      nativeCtor.setAccessible(true);
      RegistryRecipes nativeCursor = (RegistryRecipes) nativeCtor.newInstance(handler);
      recipes.set(null, new LinkedHashMap<>(registry));
      try {
        nativeCursor.verify();
        throw new AssertionError("Native static map replacement accepted");
      } catch (Jobs.Fault e) {
        require(e.code.equals("environment_changed"), "Wrong map replacement drift error");
      } finally {
        recipes.set(null, registry);
      }
      nativeCursor.close();
      RegistryRecipes cursor =
          (RegistryRecipes)
              ctor.newInstance(
                  handler, registry, bark, dust, new FluidStack(FluidRegistry.WATER, 300));
      RecipeRow row =
          NativeCoreFixesTest.row(new ItemStack(woodItem, 1, 2), nativeProduct, bark, dust);
      require(cursor.size() == 1 && cursor.capture(0, row), "Native registry entry lost");
      require(
          handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
          "Lumbermill capture replaced the borrowed NEI GUI cache");
      JsonObject choice =
          row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
      require(
          choice.get("amount").getAsString().equals("1")
              && choice.getAsJsonObject("rule").get("nbt").getAsBoolean()
              && !choice.getAsJsonObject("rule").get("meta").getAsBoolean(),
          "Native input predicate/count lost");
      require(row.inputs.size() == 2 && row.outputs.size() == 3, "Native material slots lost");
      JsonObject water =
          row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
      require(
          water.get("amount").getAsString().equals("5")
              && water.getAsJsonObject("consume").get("kind").getAsString().equals("allocated")
              && water.getAsJsonObject("rule").get("kind").getAsString().equals("lumbermillTank"),
          "Water gate flattened into fixed consumption");
      for (int i = 0; i < 3; i++) {
        JsonObject out = row.outputs.get(i).getAsJsonObject();
        require(
            out.get("amount").isJsonNull()
                && out.getAsJsonObject("quantity").get("kind").getAsString().equals("potential")
                && out.getAsJsonObject("quantity")
                    .get("stat")
                    .getAsString()
                    .equals("binnie:lumbermillOutputState")
                && out.getAsJsonObject("quantity")
                    .get("nominal")
                    .getAsString()
                    .equals(i == 0 ? "6" : "1"),
            "Native conditional inventory prototype lost");
        require(
            out.getAsJsonObject("chance").get("numerator").getAsString().equals("1")
                && out.getAsJsonObject("chance").get("denominator").getAsString().equals("1"),
            "Guaranteed native output became probabilistic");
      }
      JsonObject process = row.record.getAsJsonObject("process");
      row.record.add(
          "source",
          com.github.dcysteine.nesql.exporter.source.Json.object(
              "owner",
              "ExtraTrees",
              "handler",
              handler.getClass().getName(),
              "key",
              "binnie-lumbermill-semantic"));
      row.record.addProperty(
          "category",
          com.github.dcysteine.nesql.exporter.source.Identity.content(
              "category", row.record.getAsJsonObject("source")));
      NativeContractFixture.writeOwned(
          "binnie-lumbermill-semantic",
          row,
          Arrays.asList(new ItemStack(woodItem, 1, 2), nativeProduct, bark, dust),
          new FluidStack(FluidRegistry.WATER, 5));
      RecipeRow chinese =
          NativeCoreFixesTest.row(
              "zh_CN", new ItemStack(woodItem, 1, 2), nativeProduct, bark, dust);
      require(cursor.capture(0, chinese), "Chinese lumbermill row omitted");
      chinese.record.add("source", row.record.get("source"));
      chinese.record.add("category", row.record.get("category"));
      NativeContractFixture.writeOwned(
          "binnie-lumbermill-semantic-zh_CN",
          chinese,
          Arrays.asList(new ItemStack(woodItem, 1, 2), nativeProduct, bark, dust),
          new FluidStack(FluidRegistry.WATER, 5));
      require(
          Recipes.adapter(handler) != null,
          "Native lumbermill adapter is not routed in production");
      require(
          row.record.get("duration").isJsonNull()
              && process.has("queuedTicks")
              && process.get("queuedTicks").getAsInt() == 30
              && process
                  .get("workPolicy")
                  .getAsString()
                  .equals("cachedStateFloatProgressPauseResetAndReselectAtFinish"),
          "Lumbermill exported first-start duration as fixed timing for every cycle");
      require(
          process.get("kind").getAsString().equals("binnieLumbermill")
              && process.get("workTicks").getAsInt() == 30
              && process.get("startTicks").getAsInt() == 1
              && process.get("rfPerWorkTick").getAsInt() == 30
              && process.get("fluidGate").getAsInt() == 5
              && process.get("fluidDrainLimit").getAsInt() == 10,
          "Native work/resource process lost");
      require(
          process
                  .get("inputPolicy")
                  .getAsString()
                  .equals("normalFirstOreLogWoodRuntimeItemAndLiteralMetadata")
              && process
                  .get("outputPolicy")
                  .getAsString()
                  .equals("emptyPrototypeMatchingMergeKeepsTagsBestEffortThenDebit"),
          "Native inventory gates lost");
      require(row.record.get("energy").isJsonNull(), "RF became EU/t");
      RecipeRow fresh =
          NativeCoreFixesTest.row(new ItemStack(woodItem, 1, 2), nativeProduct, bark, dust);
      cursor.capture(0, fresh);
      require(
          fresh
                  .outputs
                  .get(0)
                  .getAsJsonObject()
                  .getAsJsonObject("quantity")
                  .get("nominal")
                  .getAsString()
                  .equals("6")
              && wood.stackSize == 17
              && plank.stackSize == 11,
          "View mutated snapshot/source");
      ItemStack shadow = wood.copy();
      shadow.stackSize = 1;
      shadow.setTagInfo("shadow", new net.minecraft.nbt.NBTTagInt(1));
      registry.put(shadow, new ItemStack(Items.diamond));
      RegistryRecipes priority =
          (RegistryRecipes)
              ctor.newInstance(
                  handler, registry, bark, dust, new FluidStack(FluidRegistry.WATER, 300));
      require(
          ((ItemStack) product.invoke(null, tagged)).getItem() == Items.paper
              && priority.size() == 2
              && !priority.capture(1, fresh)
              && fresh
                  .exclusion
                  .get("reason")
                  .getAsString()
                  .equals("earlier_map_entry_matches_same_item_and_literal_metadata"),
          "Native first-map-match priority lost");
      priority.finishVerification();
      priority.close();
      registry.remove(shadow);
      ItemStack inadmissible = new ItemStack(woodItem, 1, 3);
      net.minecraftforge.oredict.OreDictionary.registerOre("binnieWrongFirst", inadmissible);
      net.minecraftforge.oredict.OreDictionary.registerOre("logWood", inadmissible);
      registry.put(inadmissible, new ItemStack(Items.paper));
      require(
          product.invoke(null, inadmissible) != null
              && !(Boolean)
                  validator
                      .getClass()
                      .getMethod("isValid", ItemStack.class)
                      .invoke(validator, inadmissible),
          "Fixture must distinguish registry matching from native first-ore admission");
      RegistryRecipes admission =
          (RegistryRecipes)
              ctor.newInstance(
                  handler, registry, bark, dust, new FluidStack(FluidRegistry.WATER, 300));
      RecipeRow savedRow =
          NativeCoreFixesTest.row(inadmissible, new ItemStack(Items.paper, 6), bark, dust);
      require(
          admission.capture(1, savedRow)
              && savedRow
                  .record
                  .getAsJsonObject("process")
                  .get("firstOreName")
                  .getAsString()
                  .equals("binnieWrongFirst"),
          "Normal insertion gate incorrectly excluded a saved runtime inventory");
      write(
          "native-lumbermill-saved-input",
          savedRow,
          Arrays.asList(inadmissible, new ItemStack(Items.paper, 6), bark, dust));
      admission.finishVerification();
      admission.close();
      ItemStack savedInventory = inadmissible.copy();
      savedInventory.stackSize = 17;
      nativeFinish(registry, savedInventory, bark, dust);
      registry.remove(inadmissible);
      cursor.close();
      cursor =
          (RegistryRecipes)
              ctor.newInstance(
                  handler, registry, bark, dust, new FluidStack(FluidRegistry.WATER, 300));
      ItemStack sameValue = plank.copy();
      registry.put(wood, sameValue);
      try {
        cursor.finishVerification();
        throw new AssertionError("Same-value native lumbermill output replacement accepted");
      } catch (Jobs.Fault expected) {
        require(expected.code.equals("environment_changed"), "Wrong lumbermill instance drift");
      }
      registry.put(wood, plank);
      cursor.finishVerification();
      try (Recipes.Cursor production =
          new Recipes.Handler(handler, 0)
              .open(
                  NativeCoreFixesTest.row(new ItemStack(woodItem, 1, 2), nativeProduct, bark, dust)
                      .facts)) {
        require(
            MagicApi.field(production, "handler") == null,
            "Production lumbermill retains native GUI handler");
        production.capture(0);
        production.finishVerification();
      }
      RegistrationBindings registration = RegistrationBindings.read(handler);
      for (int route = 0; route < 4; route++)
        require(
            registration.same(
                RegistrationBindings.read(
                    (TemplateRecipeHandler) handler.getClass().newInstance())),
            "Lumbermill registration sources do not bind the same data");
      lazySource(scope, recipes, handler, wood, bark, dust);
      plank.getTagCompound().setInteger("product", 8);
      try {
        cursor.finishVerification();
        throw new AssertionError("Native registry NBT drift accepted");
      } catch (Jobs.Fault e) {
        require(e.code.equals("environment_changed"), "Wrong registry drift error");
      }
      cursor.close();
      cursor.close();
      try {
        cursor.size();
        throw new AssertionError("Closed lumbermill exposes rows");
      } catch (Jobs.Fault expected) {
        require(expected.code.equals("capture_closed"), "Wrong lumbermill closed failure");
      }
      System.out.println(
          "Native Binnie lumbermill: actual literal-meta/NBT matcher, first-ore admission/map priority, one wood, 6/1/1 nominal outputs, partial water drain, owned tank/layout and drift passed");
    } finally {
      recipes.set(null, saved);
      miscField.set(null, savedMisc);
    }
  }

  private static void lazySource(
      NativeRegistryScope scope,
      Field table,
      TemplateRecipeHandler handler,
      ItemStack wood,
      ItemStack bark,
      ItemStack dust)
      throws Exception {
    Class<?> woodManager = Class.forName("binnie.extratrees.block.WoodManager");
    Object oak =
        Enum.valueOf(
            (Class) Class.forName("binnie.extratrees.block.PlankType$VanillaPlanks"), "OAK");
    List<Object> planks = new ArrayList<>(Arrays.asList(oak));
    scope.replace(woodManager, null, "allPlankTypes", planks);
    net.minecraft.item.crafting.CraftingManager manager =
        net.minecraft.item.crafting.CraftingManager.getInstance();
    List<Object> crafting = new ArrayList<>();
    crafting.add(
        new net.minecraft.item.crafting.ShapelessRecipes(
            new ItemStack(net.minecraft.init.Blocks.planks), new ArrayList<>(Arrays.asList(wood))));
    scope.replace(manager.getClass(), manager, "recipes", crafting);
    Object old = table.get(null);
    Map<ItemStack, ItemStack> cold = new HashMap<>();
    table.set(null, cold);
    try {
      List<LumbermillSource.Row> observed;
      try (LumbermillSource source = new LumbermillSource()) {
        require(
            source.derived
                && source.rows.size() == 1
                && cold.isEmpty()
                && MagicApi.field(woodManager, null, "allPlankTypes") == planks,
            "Lumbermill lazy source mutated borrowed native initialization");
        observed = new ArrayList<>(source.rows);
        source.verify();
      }
      try (RegistryRecipes lazy = new BinnieLumbermillRecipes(handler)) {
        RecipeRow row =
            NativeCoreFixesTest.row(
                wood, new ItemStack(net.minecraft.init.Blocks.planks, 6), bark, dust);
        lazy.capture(0, row);
        write(
            "native-lumbermill-lazy",
            row,
            Arrays.asList(wood, new ItemStack(net.minecraft.init.Blocks.planks, 6), bark, dust));
        lazy.finishVerification();
      }
      Class.forName("binnie.extratrees.machines.lumbermill.Lumbermill")
          .getMethod("calculateLumbermillProducts")
          .invoke(null);
      require(
          cold.size() == 1
              && cold.keySet().iterator().next() == wood
              && observed.get(0).output.isItemEqual(cold.values().iterator().next()),
          "Owned lazy derivation differs from actual installed calculation");
      scope.replace(
          Class.forName("binnie.extratrees.ExtraTrees"),
          null,
          "blockPlanks",
          net.minecraft.init.Blocks.planks);
      Object forestry = MagicApi.field(Class.forName("binnie.core.Mods"), null, "forestry");
      scope.replace(forestry.getClass(), forestry, "modid", "minecraft");
      scope.replace(woodManager, null, "allPlankTypes", null);
      Map<ItemStack, ItemStack> defaults = new HashMap<>();
      table.set(null, defaults);
      try (LumbermillSource source = new LumbermillSource()) {
        require(
            source.derived
                && source.rows.size() == 1
                && defaults.isEmpty()
                && MagicApi.field(woodManager, null, "allPlankTypes") == null,
            "Default enum derivation changed native lazy cache or table");
        observed = new ArrayList<>(source.rows);
        source.verify();
      }
      Class.forName("binnie.extratrees.machines.lumbermill.Lumbermill")
          .getMethod("calculateLumbermillProducts")
          .invoke(null);
      require(
          defaults.size() == 1
              && observed.get(0).output.isItemEqual(defaults.values().iterator().next()),
          "Default owned plank census differs from installed native calculation");
      scope.replace(woodManager, null, "allPlankTypes", planks);
      table.set(null, new HashMap<>());
      crafting.clear();
      try (LumbermillSource empty = new LumbermillSource()) {
        require(
            empty.rows.isEmpty() && empty.derived, "Proven empty lazy derivation invented recipes");
        empty.verify();
      }
      UnknownOutput unknown = new UnknownOutput(wood);
      crafting.add(unknown);
      try {
        new LumbermillSource();
        throw new AssertionError("Unknown lazy crafting getter accepted");
      } catch (Jobs.Fault expected) {
        require(
            expected.code.equals("recipe_unsupported") && unknown.calls == 0,
            "Unknown callback executed before rejection");
      }
      table.set(null, null);
      try {
        new LumbermillSource();
        throw new AssertionError("Not-ready lumbermill became an empty table");
      } catch (Jobs.Fault expected) {
        require(expected.code.equals("handler_not_ready"), "Wrong lumbermill readiness failure");
      }
    } finally {
      table.set(null, old);
    }
  }

  public static final class UnknownOutput extends net.minecraft.item.crafting.ShapelessRecipes {
    int calls;

    UnknownOutput(ItemStack wood) {
      super(new ItemStack(net.minecraft.init.Blocks.planks), new ArrayList<>(Arrays.asList(wood)));
    }

    @Override
    public ItemStack getRecipeOutput() {
      calls++;
      return super.getRecipeOutput();
    }
  }

  private static void write(String name, RecipeRow row, List<ItemStack> items) throws Exception {
    row.record.add(
        "source",
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner",
            "ExtraTrees",
            "handler",
            "binnie.extratrees.nei.NEIHandlerLumbermill",
            "key",
            name));
    row.record.addProperty(
        "category",
        com.github.dcysteine.nesql.exporter.source.Identity.content(
            "category", row.record.getAsJsonObject("source")));
    NativeContractFixture.writeOwned(name, row, items, new FluidStack(FluidRegistry.WATER, 5));
  }

  private static void nativeFinish(
      Map<ItemStack, ItemStack> registry, ItemStack wood, ItemStack bark, ItemStack dust)
      throws Exception {
    ItemStack[] slots = {wood.copy(), null, null, null};
    IInventory inventory =
        (IInventory)
            Proxy.newProxyInstance(
                IInventory.class.getClassLoader(),
                new Class<?>[] {IInventory.class},
                (p, m, a) -> {
                  if (m.getName().equals("getStackInSlot")) return slots[(Integer) a[0]];
                  if (m.getName().equals("setInventorySlotContents")) {
                    slots[(Integer) a[0]] = (ItemStack) a[1];
                    return null;
                  }
                  if (m.getName().equals("decrStackSize")) {
                    ItemStack result = slots[(Integer) a[0]].splitStack((Integer) a[1]);
                    return result;
                  }
                  return primitive(m.getReturnType());
                });
    FluidTank tank = new FluidTank(new FluidStack(FluidRegistry.WATER, 5), 10000);
    Class<?> tanks = Class.forName("binnie.core.machines.power.ITankMachine"),
        machine = Class.forName("binnie.core.machines.IMachine");
    Object tankMachine =
        Proxy.newProxyInstance(
            tanks.getClassLoader(),
            new Class<?>[] {tanks},
            (p, m, a) -> {
              if (m.getName().equals("getTank")) return tank;
              if (m.getName().equals("getTanks")) return new IFluidTank[] {tank};
              return primitive(m.getReturnType());
            });
    Class<?> powered = Class.forName("binnie.core.machines.power.IPoweredMachine"),
        powerType = Class.forName("binnie.core.machines.power.PowerInterface");
    Object power = powerType.getConstructor(int.class).newInstance(100000);
    powerType.getMethod("addEnergy", int.class, boolean.class).invoke(power, 10000000, true);
    Object poweredMachine =
        Proxy.newProxyInstance(
            powered.getClassLoader(),
            new Class<?>[] {powered},
            (p, m, a) -> m.getName().equals("getInterface") ? power : primitive(m.getReturnType()));
    Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
    NativeWorkWorld world = (NativeWorkWorld) unsafe.allocateInstance(NativeWorkWorld.class);
    net.minecraft.tileentity.TileEntity tile = new net.minecraft.tileentity.TileEntity();
    Object[] util = {null};
    Object instance =
        Proxy.newProxyInstance(
            machine.getClassLoader(),
            new Class<?>[] {machine},
            (p, m, a) -> {
              if (m.getName().equals("getMachineUtil")) return util[0];
              if (m.getName().equals("getWorld")) return world;
              if (m.getName().equals("getTileEntity")) return tile;
              if (m.getName().equals("getInterface")) {
                if (a[0] == IInventory.class) return inventory;
                if (a[0] == tanks) return tankMachine;
                if (a[0] == powered) return poweredMachine;
              }
              return primitive(m.getReturnType());
            });
    util[0] =
        Class.forName("binnie.core.machines.MachineUtil")
            .getConstructor(machine)
            .newInstance(instance);
    Class<?> logicType =
        Class.forName("binnie.extratrees.machines.lumbermill.LumbermillComponentLogic");
    Object logic = unsafe.allocateInstance(logicType);
    logicType.getMethod("setMachine", machine).invoke(logic, instance);
    Class<?> cost = Class.forName("binnie.core.machines.power.ComponentProcessSetCost");
    for (String field : new String[] {"processLength", "processEnergy"}) {
      Field value = cost.getDeclaredField(field);
      value.setAccessible(true);
      value.setInt(logic, field.equals("processLength") ? 30 : 900);
    }
    Method finish = logicType.getDeclaredMethod("onFinishTask");
    finish.setAccessible(true);
    finish.invoke(logic);
    require(
        slots[0].stackSize == 16
            && slots[1].stackSize == 6
            && slots[2].isItemEqual(bark)
            && slots[2].stackSize == 1
            && slots[3].isItemEqual(dust)
            && slots[3].stackSize == 1,
        "Actual native finish differs from 1 wood -> 6 planks, bark, sawdust");
    slots[1].setTagInfo("existing", new net.minecraft.nbt.NBTTagString("retained"));
    slots[2] = new ItemStack(Items.diamond);
    slots[3].stackSize = 64;
    finish.invoke(logic);
    require(
        slots[0].stackSize == 15
            && slots[1].stackSize == 12
            && slots[1].getTagCompound().getString("existing").equals("retained")
            && slots[2].getItem() == Items.diamond
            && slots[3].stackSize == 64,
        "Native merge did not preserve existing tags or skipped byproducts prevented wood debit");
    slots[1] = null;
    slots[2] = null;
    slots[3] = null;
    Method tick = logicType.getDeclaredMethod("onTickTask");
    tick.setAccessible(true);
    tick.invoke(logic);
    require(tank.getFluidAmount() == 0, "Actual native tick did not accept partial five-mB drain");
    tank.setFluid(new FluidStack(FluidRegistry.LAVA, 5));
    require(
        logicType.getMethod("canProgress").invoke(logic) == null,
        "Runtime volume gate incorrectly checks water identity");
    tick.invoke(logic);
    require(tank.getFluidAmount() == 0, "Runtime non-water inventory did not drain");
    tank.setFluid(new FluidStack(FluidRegistry.WATER, 10000));
    Method update = logicType.getMethod("onUpdate");
    int previous = slots[0].stackSize;
    int first = 0;
    do {
      update.invoke(logic);
      first++;
    } while (slots[0].stackSize == previous && first < 40);
    previous = slots[0].stackSize;
    int queued = 0;
    do {
      update.invoke(logic);
      queued++;
    } while (slots[0].stackSize == previous && queued < 40);
    System.out.println(
        "Binnie actual update loop: firstUpdates="
            + first
            + ", queuedUpdates="
            + queued
            + ", waterSpent="
            + (10000 - tank.getFluidAmount()));
    require(
        first == 31 && queued == 30 && 10000 - tank.getFluidAmount() == 600,
        "Native fresh/queued completion updates or work-drain counts changed");
  }

  public static final class NativeWorkWorld extends net.minecraft.client.multiplayer.WorldClient {
    private NativeWorkWorld() {
      super(null, null, 0, null, null);
    }

    @Override
    public void markBlockForUpdate(int x, int y, int z) {}
  }

  private static Object primitive(Class<?> type) {
    if (type == boolean.class) return false;
    if (type == int.class) return 0;
    return null;
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
