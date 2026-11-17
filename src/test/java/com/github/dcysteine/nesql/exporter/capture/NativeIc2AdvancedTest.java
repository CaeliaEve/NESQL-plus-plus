package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;

/** Actual pinned IC2 rules, isolated native registry, no running Minecraft. */
final class NativeIc2AdvancedTest {
  static void run() throws Exception {
    run(false);
  }

  static void geometryOnly() throws Exception {
    run(true);
  }

  @SuppressWarnings("unchecked")
  private static void run(boolean geometryOnly) throws Exception {
    List<Object> crafting = CraftingManager.getInstance().getRecipeList(),
        saved = new ArrayList<>(crafting);
    Class<?> electric = type("ic2.api.item.ElectricItem");
    Object manager = field(electric, null, "manager"),
        rawManager = field(electric, null, "rawManager");
    java.lang.reflect.Field managerField = electric.getField("manager"),
        rawField = electric.getField("rawManager");
    try {
      managerField.set(null, type("ic2.core.item.GatewayElectricItemManager").newInstance());
      rawField.set(null, type("ic2.core.item.ElectricItemManager").newInstance());
      ItemStack template = new ItemStack(Items.iron_ingot, 7, 2),
          output = new ItemStack(Items.paper, 3);
      template.setTagCompound(new NBTTagCompound());
      template.getTagCompound().setString("native", "template");
      IRecipe recipe =
          (IRecipe)
              type("ic2.core.AdvRecipe")
                  .getConstructor(ItemStack.class, Object[].class)
                  .newInstance(output, new Object[] {"A ", " A", Character.valueOf('A'), template});
      require(
          ((Object[]) field(recipe, "input")).length == 2
              && ((Integer) field(recipe, "inputWidth")) == 2
              && ((Integer) field(recipe, "inputHeight")) == 2,
          "Native compact mask fixture changed");
      InventoryCrafting grid =
          new InventoryCrafting(
              new Container() {
                public boolean canInteractWith(EntityPlayer player) {
                  return false;
                }
              },
              3,
              3);
      ItemStack offered = template.copy();
      offered.stackSize = 1;
      offered.getTagCompound().setString("native", "offered");
      grid.setInventorySlotContents(0, offered);
      grid.setInventorySlotContents(4, offered.copy());
      require(recipe.matches(grid, null), "Native IC2 compact shaped mask should match");
      require(recipe.getCraftingResult(grid).stackSize == 3, "Native result count changed");
      grid.getStackInSlot(4).setItemDamage(3);
      require(!recipe.matches(grid, null), "Native IC2 exact metadata became wildcard");
      grid.getStackInSlot(4).setItemDamage(2);
      try (Ic2AdvancedMasks masks = new Ic2AdvancedMasks(recipe)) {
        require(
            masks.size() == 2
                && masks.primaryCells().get(0).getAsInt() == 0
                && masks.primaryCells().get(4).getAsInt() == 1
                && masks.primaryCells().get(1).isJsonNull(),
            "IC2 compact inputs became a dense width-times-height array");
        require(
            !masks
                .paths(new boolean[] {true, false, false, false, true, false, false, false, false})
                .isEmpty(),
            "IC2 native mask path was lost");
        InventoryCrafting small =
            new InventoryCrafting(
                new Container() {
                  public boolean canInteractWith(EntityPlayer p) {
                    return false;
                  }
                },
                2,
                2);
        small.setInventorySlotContents(0, offered.copy());
        small.setInventorySlotContents(3, offered.copy());
        require(
            recipe.matches(small, null)
                && !masks.paths(new boolean[] {true, false, false, true}).isEmpty(),
            "Native IC2 2x2 mask normalization differs");
        int[] raw = (int[]) field(recipe, "masks");
        int prior = raw[0];
        raw[0] = 1;
        try {
          fails("recipe_changed", masks::verify);
        } finally {
          raw[0] = prior;
        }
        masks.verify();
        masks.close();
        masks.close();
        fails("capture_closed", masks::declaration);
      }
      inputCases(template);
      fluidCases();
      matchingCases();
      electricCases();
      visibilityCases();
      if (geometryOnly) {
        System.out.println(
            "IC2 actual compact masks, empty cells, 2x2 normalization and source drift passed; production capture remains pending");
        return;
      }
      crafting.clear();
      crafting.add(recipe);
      TemplateRecipeHandler handler =
          (TemplateRecipeHandler)
              type("ic2.neiIntegration.core.recipehandler.AdvRecipeHandler").newInstance();
      Facts facts = new Facts("en_US");
      Set<String> known = (Set<String>) field(facts, "items");
      for (ItemStack stack : new ItemStack[] {template, output})
        known.add(
            Identity.item(
                net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
                stack.getItemDamage(),
                TypedNbt.encode(stack.getTagCompound())));
      try (RegistryRecipes reader = new Ic2AdvancedRecipes(handler)) {
        com.google.gson.JsonObject origin =
            object(
                "owner",
                "IC2",
                "handler",
                handler.getClass().getName(),
                "key",
                handler.getHandlerId());
        RecipeRow row = new RecipeRow(facts, origin, Identity.origin("category", origin), 0);
        require(reader.size() == 1 && reader.capture(0, row), "Native IC2 compact recipe was lost");
        require(row.inputs.size() == 2, "Native IC2 compact inputs were lost");
        require(
            row.inputs
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("choices")
                .get(0)
                .getAsJsonObject()
                .get("amount")
                .getAsString()
                .equals("1"),
            "IC2 Advanced consumed getAmount or template count instead of one per cell");
        require(
            !row.inputs
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("choices")
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("rule")
                .get("meta")
                .getAsBoolean(),
            "IC2 exact metadata became an unconditional wildcard");
        require(
            row.record.getAsJsonObject("grid").getAsJsonArray("cells").size() == 9,
            "Native IC2 holes were lost");
        NativeContractFixture.writeOwned(
            "native-ic2-advanced-shaped", row, Arrays.asList(template, output));
        reader.finishVerification();
        crafting.set(
            0,
            (IRecipe)
                type("ic2.core.AdvRecipe")
                    .getConstructor(ItemStack.class, Object[].class)
                    .newInstance(output, new Object[] {"A ", " A", 'A', template}));
        try {
          fails("recipe_changed", reader::finishVerification);
        } finally {
          crafting.set(0, recipe);
        }
        reader.finishVerification();
        reader.close();
        reader.close();
        fails("capture_closed", reader::size);
      }
      System.out.println(
          "IC2 native compact mask and exact metadata/ignored NBT conformance passed");
    } finally {
      crafting.clear();
      crafting.addAll(saved);
      managerField.set(null, manager);
      rawField.set(null, rawManager);
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
  /** Forge rewrites this old IC2 field access at class load; test that exact transformer. */
  private static void fluidCases() throws Exception {
    Class<?> containers = net.minecraftforge.fluids.FluidContainerRegistry.class;
    String[] fields = {"filledContainerMap", "containerFluidMap", "emptyContainers"};
    Object[] saved = new Object[fields.length];
    for (int index = 0; index < fields.length; index++)
      saved[index] = field(containers, null, fields[index]);
    try {
      for (int index = 0; index < fields.length; index++) {
        java.lang.reflect.Field f = containers.getDeclaredField(fields[index]);
        f.setAccessible(true);
        f.set(null, index == 2 ? new HashSet<>() : new HashMap<>());
      }
      net.minecraftforge.fluids.FluidContainerRegistry.registerFluidContainer(
          new net.minecraftforge.fluids.FluidStack(
              net.minecraftforge.fluids.FluidRegistry.WATER, 1000),
          new ItemStack(Items.water_bucket),
          new ItemStack(Items.bucket));
      Object predicate =
          type("ic2.api.recipe.RecipeInputFluidContainer")
              .getConstructor(net.minecraftforge.fluids.Fluid.class, int.class)
              .newInstance(net.minecraftforge.fluids.FluidRegistry.WATER, 250);
      Object[] source = {predicate, "liquid$water"};
      byte[] original;
      try (java.io.InputStream input =
              type("ic2.core.AdvRecipe").getResourceAsStream("/ic2/core/AdvRecipe.class");
          java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        original = output.toByteArray();
      }
      Object transformer = type("net.minecraftforge.classloading.FluidIdTransformer").newInstance();
      byte[] transformed =
          (byte[])
              invoke(
                  transformer.getClass(),
                  transformer,
                  "transform",
                  new Class<?>[] {String.class, String.class, byte[].class},
                  "ic2.core.AdvRecipe",
                  "ic2.core.AdvRecipe",
                  original);
      Class<?> patched =
          new ClassLoader(NativeIc2AdvancedTest.class.getClassLoader()) {
            Class<?> define() {
              return defineClass("ic2.core.AdvRecipe", transformed, 0, transformed.length);
            }
          }.define();
      boolean rawFailed = false;
      try {
        invoke(
            type("ic2.core.AdvRecipe"), null, "expand", new Class<?>[] {Object.class}, source[1]);
      } catch (NoSuchFieldError expected) {
        rawFailed = true;
      }
      require(
          rawFailed,
          "Untransformed legacy fluidID branch no longer demonstrates the Forge loading boundary");
      try (Ic2AdvancedInputs inputs = new Ic2AdvancedInputs(source)) {
        for (int index = 0; index < source.length; index++) {
          List<?> nativeRows =
              (List<?>)
                  invoke(patched, null, "expand", new Class<?>[] {Object.class}, source[index]);
          List<ItemStack> actual = inputs.copies(index);
          require(
              nativeRows.size() == 1
                  && actual.size() == 1
                  && ItemStack.areItemStacksEqual((ItemStack) nativeRows.get(0), actual.get(0)),
              "Nonempty native IC2 fluid candidate differs");
        }
        inputs.verify();
        Object row =
            net.minecraftforge.fluids.FluidContainerRegistry.getRegisteredFluidContainerData()[0];
        net.minecraftforge.fluids.FluidStack fluid =
            (net.minecraftforge.fluids.FluidStack) field(row, "fluid");
        int amount = fluid.amount;
        fluid.amount = 999;
        try {
          fails("recipe_changed", inputs::verify);
        } finally {
          fluid.amount = amount;
        }
        inputs.verify();
      }
      require(
          !Arrays.equals(original, transformed),
          "Forge native transformer did not rewrite the legacy field access");
      java.nio.file.Files.write(
          java.nio.file.Paths.get("build/native-tests/ic2-advanced-forge-fluid-observation.json"),
          com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(
              object(
                  "legacyUntransformed",
                  "NoSuchFieldError: fluidID",
                  "transformer",
                  transformer.getClass().getName(),
                  "legacySha256",
                  sha(original),
                  "transformedSha256",
                  sha(transformed),
                  "nonemptyCases",
                  2)));
      System.out.println(
          "IC2 nonempty fluid predicate and liquid$ candidates match actual Forge FluidIdTransformer output; fluid drift rejected");
    } finally {
      for (int index = 0; index < fields.length; index++) {
        java.lang.reflect.Field f = containers.getDeclaredField(fields[index]);
        f.setAccessible(true);
        f.set(null, saved[index]);
      }
    }
  }

  private static String sha(byte[] value) throws Exception {
    byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value);
    StringBuilder result = new StringBuilder();
    for (byte b : digest) result.append(String.format("%02x", b & 255));
    return result.toString();
  }

  private static void matchingCases() throws Exception {
    ItemStack exact = new ItemStack(Items.iron_ingot, 77, 2),
        wildcard = new ItemStack(Items.iron_ingot, 0, 32767);
    IRecipe shaped =
        (IRecipe)
            type("ic2.core.AdvRecipe")
                .getConstructor(ItemStack.class, Object[].class)
                .newInstance(
                    new ItemStack(Items.paper),
                    new Object[] {"A ", " B", 'A', exact, 'B', new ItemStack(Items.gold_ingot)});
    int cases = 0;
    try (Ic2AdvancedMatch matcher = new Ic2AdvancedMatch(shaped)) {
      for (int width : new int[] {2, 3})
        for (int offset : new int[] {0, 1})
          for (boolean mirrored : new boolean[] {false, true})
            for (boolean wrong : new boolean[] {false, true}) {
              ItemStack[] cells = new ItemStack[width * width];
              int start = width == 2 ? 0 : offset;
              ItemStack offered = exact.copy();
              offered.stackSize = 0;
              offered.setTagCompound(new NBTTagCompound());
              offered.getTagCompound().setString("ignored", "native NBT");
              if (wrong) offered.setItemDamage(3);
              cells[start + (mirrored ? 1 : 0)] = offered;
              cells[start + width + (mirrored ? 0 : 1)] = new ItemStack(Items.gold_ingot, 42);
              compareMatch(shaped, matcher, cells);
              cases++;
            }
      ItemStack[] extra = new ItemStack[9];
      extra[0] = exact.copy();
      extra[4] = new ItemStack(Items.gold_ingot);
      extra[8] = new ItemStack(Items.paper);
      compareMatch(shaped, matcher, extra);
      cases++;
    }
    // Wildcard first greedily takes metadata 2. A general maximum matcher would
    // incorrectly accept [2,3]; native accepts [3,2] and rejects [2,3].
    IRecipe unordered =
        (IRecipe)
            type("ic2.core.AdvShapelessRecipe")
                .getConstructor(ItemStack.class, Object[].class)
                .newInstance(new ItemStack(Items.paper), new Object[] {wildcard, exact});
    try (Ic2AdvancedMatch matcher = new Ic2AdvancedMatch(unordered)) {
      for (boolean reverse : new boolean[] {false, true}) {
        ItemStack[] cells = new ItemStack[9];
        cells[0] = new ItemStack(Items.iron_ingot, 32, reverse ? 3 : 2);
        cells[4] = new ItemStack(Items.iron_ingot, 1, reverse ? 2 : 3);
        compareMatch(unordered, matcher, cells);
        require(
            (matcher.match(cells) != null) == reverse,
            "IC2 shapeless greedy native priority was replaced");
        cases++;
      }
      matcher.close();
      matcher.close();
      fails("capture_closed", matcher::verify);
    }
    java.nio.file.Files.write(
        java.nio.file.Paths.get("build/native-tests/ic2-advanced-match-observations.json"),
        com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(
            object(
                "actualNativeCases",
                cases,
                "shaped",
                "baseThenMirrorAbsoluteMask",
                "shapeless",
                "rowMajorFirstRemainingGroup",
                "consumePerOccupiedCell",
                1,
                "metadata",
                "exactOrCandidate32767",
                "nbt",
                "ignored",
                "rawCounts",
                "notUsedForMatching")));
    System.out.println(
        "IC2 Advanced native matching conformance: "
            + cases
            + " shaped translations/mirrors/2x2, empty cells and shapeless greedy overlap cases passed");
  }

  private static void compareMatch(IRecipe recipe, Ic2AdvancedMatch matcher, ItemStack[] cells) {
    int width = cells.length == 4 ? 2 : 3;
    InventoryCrafting grid =
        new InventoryCrafting(
            new Container() {
              public boolean canInteractWith(EntityPlayer p) {
                return false;
              }
            },
            width,
            width);
    for (int cell = 0; cell < cells.length; cell++)
      if (cells[cell] != null) grid.setInventorySlotContents(cell, cells[cell].copy());
    ItemStack nativeResult = recipe.getCraftingResult(grid);
    require(
        (nativeResult != null) == (matcher.match(cells) != null),
        "IC2 matcher differs from actual native getCraftingResult");
    try (Ic2AdvancedLogic logic = new Ic2AdvancedLogic(recipe)) {
      Ic2AdvancedLogic.Outcome result = logic.evaluate(cells);
      require((result == null) == (nativeResult == null), "IC2 evaluator changed native matching");
      if (result != null)
        require(
            ItemStack.areItemStacksEqual(result.output, nativeResult),
            "IC2 evaluator differs from native getCraftingResult");
    }
  }

  private static void electricCases() throws Exception {
    java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
    net.minecraft.item.Item battery =
        (net.minecraft.item.Item) unsafe.allocateInstance(type("ic2.core.item.ItemBattery"));
    net.minecraft.item.Item empty =
        (net.minecraft.item.Item)
            unsafe.allocateInstance(type("ic2.core.item.ItemBatteryDischarged"));
    java.lang.reflect.Field delegate = net.minecraft.item.Item.class.getField("delegate");
    delegate.setAccessible(true);
    Class<?> base = type("ic2.core.item.BaseElectricItem");
    for (net.minecraft.item.Item item : new net.minecraft.item.Item[] {battery, empty}) {
      delegate.set(
          item,
          new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(
              item, net.minecraft.item.Item.class));
      for (String name : new String[] {"maxCharge", "transferLimit", "tier"}) {
        java.lang.reflect.Field f = base.getDeclaredField(name);
        f.setAccessible(true);
        if (name.equals("tier")) f.setInt(item, 1);
        else f.setDouble(item, name.equals("maxCharge") ? 1000 : 32);
      }
      item.setMaxDamage(27);
    }
    net.minecraft.item.Item.itemRegistry.addObject(31330, "nesql_ic2_fixture:charged", battery);
    net.minecraft.item.Item.itemRegistry.addObject(31331, "nesql_ic2_fixture:empty", empty);
    Class<?> itemApi = type("ic2.core.Ic2Items");
    java.lang.reflect.Field charged = itemApi.getField("chargedReBattery"),
        discharged = itemApi.getField("reBattery");
    Object savedCharged = charged.get(null), savedEmpty = discharged.get(null);
    try {
      charged.set(null, new ItemStack(battery));
      discharged.set(null, new ItemStack(empty));
      Object gateway = field(type("ic2.api.item.ElectricItem"), null, "manager");
      int cases = 0;
      for (int count : new int[] {0, 1, 2})
        for (double value : new double[] {0, 0.25, 75.75}) {
          ItemStack input = new ItemStack(battery, count);
          input.setTagInfo("charge", new net.minecraft.nbt.NBTTagDouble(value));
          GtElectricBindings bound = GtElectricBindings.ic2Advanced(input, false);
          double actual =
              ((Number)
                      invoke(
                          gateway.getClass(),
                          gateway,
                          "getCharge",
                          new Class<?>[] {ItemStack.class},
                          input.copy()))
                  .doubleValue();
          require(
              Double.doubleToLongBits(bound.advancedCharge()) == Double.doubleToLongBits(actual),
              "IC2 Advanced lost double getCharge or offered stack count");
          require(
              input.getTagCompound().getDouble("charge") == value && input.stackSize == count,
              "Advanced charge probe mutated borrowed input");
          fails("recipe_unsupported", bound::transfer);
          cases++;
        }
      for (int count : new int[] {0, 1, 2})
        for (double existing : new double[] {0, 80.25})
          for (double incoming : new double[] {0, 0.5, 100.125, 5000}) {
            ItemStack output = new ItemStack(battery, count);
            output.setTagInfo("charge", new net.minecraft.nbt.NBTTagDouble(existing));
            output.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("retained"));
            GtElectricBindings bound = GtElectricBindings.ic2Advanced(output, true);
            ItemStack actual = output.copy();
            invoke(
                gateway.getClass(),
                gateway,
                "charge",
                new Class<?>[] {
                  ItemStack.class, double.class, int.class, boolean.class, boolean.class
                },
                actual,
                incoming,
                Integer.MAX_VALUE,
                true,
                false);
            require(
                ItemStack.areItemStacksEqual(bound.advancedResult(incoming), actual),
                "IC2 Advanced incorrectly cleared template charge, truncated a double, or lost output transition");
            require(
                output.getTagCompound().getDouble("charge") == existing,
                "Advanced result probe changed native output template");
            cases++;
          }
      GtElectricBindings bound = GtElectricBindings.ic2Advanced(new ItemStack(battery), false);
      java.lang.reflect.Field capacity = base.getDeclaredField("maxCharge");
      capacity.setAccessible(true);
      capacity.setDouble(battery, 999);
      try {
        fails("recipe_changed", bound::verify);
      } finally {
        capacity.setDouble(battery, 1000);
      }
      bound.verify();
      finiteProducer(battery);
      ItemStack chargedOutput = new ItemStack(battery);
      chargedOutput.setTagInfo("charge", new net.minecraft.nbt.NBTTagDouble(80.25));
      ItemStack template = new ItemStack(battery, 1, 32767);
      IRecipe recipe =
          (IRecipe)
              type("ic2.core.AdvShapelessRecipe")
                  .getConstructor(ItemStack.class, Object[].class)
                  .newInstance(chargedOutput, new Object[] {template, template, template});
      try (Ic2AdvancedLogic logic = new Ic2AdvancedLogic(recipe)) {
        for (double[] amounts :
            new double[][] {{0.25, 0.5, 75.75}, {1e16, -1e16, 1}, {1e16, 1, -1e16}}) {
          ItemStack[] cells = new ItemStack[9];
          double total = 0;
          InventoryCrafting grid =
              new InventoryCrafting(
                  new Container() {
                    public boolean canInteractWith(EntityPlayer p) {
                      return false;
                    }
                  },
                  3,
                  3);
          for (int i = 0; i < amounts.length; i++) {
            cells[i] = new ItemStack(battery);
            cells[i].setTagInfo("charge", new net.minecraft.nbt.NBTTagDouble(amounts[i]));
            grid.setInventorySlotContents(i, cells[i].copy());
            total += amounts[i];
          }
          Ic2AdvancedLogic.Outcome outcome = logic.evaluate(cells);
          require(
              outcome.total.equals(Ic2AdvancedLogic.bits(total))
                  && ItemStack.areItemStacksEqual(outcome.output, recipe.getCraftingResult(grid)),
              "IC2 result lost native floating-point sum order or additive output charge");
          cases++;
        }
        ItemStack nativeOutput = (ItemStack) field(recipe, "output");
        nativeOutput.stackSize = 2;
        try {
          fails("recipe_changed", logic::verify);
        } finally {
          nativeOutput.stackSize = 1;
        }
        logic.verify();
        logic.close();
        logic.close();
        fails("capture_closed", logic::verify);
      }
      net.minecraft.item.Item fluid = new net.minecraftforge.fluids.ItemFluidContainer(1000);
      ItemStack output = new ItemStack(fluid);
      require(
          GtElectricBindings.ic2Advanced(output, true).advancedResult(0).getTagCompound() != null
              && output.getTagCompound() == null,
          "Native fluid output did not receive an owned empty NBT compound");
      cases++;
      @SuppressWarnings("unchecked")
      List<Object> backups =
          (List<Object>) field(type("ic2.api.item.ElectricItem"), null, "backupManagers");
      final int[] calls = {0};
      Object unknown =
          java.lang.reflect.Proxy.newProxyInstance(
              NativeIc2AdvancedTest.class.getClassLoader(),
              new Class<?>[] {type("ic2.api.item.IBackupElectricItemManager")},
              (proxy, method, args) -> {
                calls[0]++;
                throw new AssertionError("Unknown electric backup was invoked");
              });
      backups.add(unknown);
      try {
        fails(
            "recipe_unsupported",
            () -> GtElectricBindings.ic2Advanced(new ItemStack(Items.paper), false));
      } finally {
        backups.remove(backups.size() - 1);
      }
      require(calls[0] == 0, "Unknown native backup callback executed");
      java.nio.file.Files.write(
          java.nio.file.Paths.get("build/native-tests/ic2-advanced-electric-observations.json"),
          com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(
              object(
                  "actualNativeCases",
                  cases,
                  "input",
                  "getChargeDoubleWithOfferedCount",
                  "output",
                  "addToOriginalTemplateWithoutClearing",
                  "unknownBackupCalls",
                  calls[0],
                  "ownedFluidOutputNbt",
                  true)));
      System.out.println(
          "IC2 Advanced direct electric stage: "
              + cases
              + " actual native double/count/additive output/transition/fluid-NBT cases, drift and unknown callback rejection passed");
    } finally {
      charged.set(null, savedCharged);
      discharged.set(null, savedEmpty);
    }
  }

  private static void finiteProducer(net.minecraft.item.Item battery) throws Exception {
    ItemStack a = new ItemStack(battery),
        b = new ItemStack(battery),
        prototype = new ItemStack(battery),
        paper = new ItemStack(Items.paper, 23);
    a.setTagInfo("charge", new net.minecraft.nbt.NBTTagDouble(0.25));
    b.setTagInfo("charge", new net.minecraft.nbt.NBTTagDouble(75.75));
    prototype.setTagInfo("charge", new net.minecraft.nbt.NBTTagDouble(80.5));
    IRecipe recipe =
        (IRecipe)
            type("ic2.core.AdvShapelessRecipe")
                .getConstructor(ItemStack.class, Object[].class)
                .newInstance(prototype, new Object[] {new ItemStack[] {a, b}, paper});
    List<ItemStack> specimens = new ArrayList<>(Arrays.asList(a, b, prototype, paper));
    for (ItemStack offered : new ItemStack[] {a, b}) {
      InventoryCrafting grid =
          new InventoryCrafting(
              new Container() {
                public boolean canInteractWith(EntityPlayer p) {
                  return false;
                }
              },
              3,
              3);
      grid.setInventorySlotContents(0, offered.copy());
      grid.setInventorySlotContents(1, paper.copy());
      specimens.add(recipe.getCraftingResult(grid));
    }
    List<Object> source = CraftingManager.getInstance().getRecipeList(),
        saved = new ArrayList<>(source);
    try {
      source.clear();
      source.add(recipe);
      TemplateRecipeHandler handler =
          (TemplateRecipeHandler)
              type("ic2.neiIntegration.core.recipehandler.AdvShapelessRecipeHandler").newInstance();
      Facts facts = new Facts("en_US");
      @SuppressWarnings("unchecked")
      Set<String> known = (Set<String>) field(facts, "items");
      for (ItemStack item : specimens)
        known.add(
            Identity.item(
                net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()),
                item.getItemDamage(),
                TypedNbt.encode(item.getTagCompound())));
      com.google.gson.JsonObject origin =
          object(
              "owner",
              "IC2",
              "handler",
              handler.getClass().getName(),
              "key",
              handler.getHandlerId());
      RecipeRow row = new RecipeRow(facts, origin, Identity.origin("category", origin), 0);
      try (Ic2AdvancedRecipes reader = new Ic2AdvancedRecipes(handler)) {
        require(
            reader.capture(0, row)
                && row.record.getAsJsonObject("process").getAsJsonArray("states").size() == 2,
            "IC2 Advanced finite state folding lost distinct double-charge outputs");
        NativeContractFixture.writeOwned("native-ic2-advanced-electric", row, specimens);
        reader.finishVerification();
        RegistrationBindings bindings = RegistrationBindings.read(handler);
        for (int index = 0; index < 4; index++)
          require(
              bindings.same(
                  RegistrationBindings.read(
                      (TemplateRecipeHandler) handler.getClass().newInstance())),
              "IC2 Advanced four-source native bindings differ");
      }
      require(
          handler.arecipes.isEmpty(), "IC2 Advanced capture initialized the NEI GUI recipe cache");
    } finally {
      source.clear();
      source.addAll(saved);
    }
    System.out.println(
        "IC2 Advanced actual production electric transport: two distinct double-charge states, four-source bindings and no GUI-cache writes passed");
  }

  private static void visibilityCases() throws Exception {
    Class<?> main = type("ic2.core.init.MainConfig"),
        configType = type("ic2.core.util.Config"),
        valueType = type("ic2.core.util.Config$Value");
    java.lang.reflect.Field configField = main.getDeclaredField("config");
    configField.setAccessible(true);
    Object saved = configField.get(null);
    IRecipe recipe =
        (IRecipe)
            type("ic2.core.AdvShapelessRecipe")
                .getConstructor(ItemStack.class, Object[].class)
                .newInstance(
                    new ItemStack(Items.paper), new Object[] {new ItemStack(Items.iron_ingot)});
    java.lang.reflect.Field hidden = recipe.getClass().getField("hidden");
    try {
      configField.set(null, null);
      try (Ic2AdvancedVisibility visible = new Ic2AdvancedVisibility(recipe)) {
        require(visible.shown(), "Ordinary IC2 rule unnecessarily required secret config");
      }
      hidden.setBoolean(recipe, true);
      fails("handler_not_ready", () -> new Ic2AdvancedVisibility(recipe));
      Object config = configType.getConstructor(String.class).newInstance("owned fixture");
      configField.set(null, config);
      int cases = 0;
      for (String value : new String[] {"true", "TRUE", "false", "invalid", null}) {
        Object entry =
            valueType
                .getConstructor(String.class, String.class, String.class)
                .newInstance("hideSecretRecipes", "", value);
        invoke(
            configType,
            config,
            "set",
            new Class<?>[] {String.class, valueType},
            "misc/hideSecretRecipes",
            entry);
        try (Ic2AdvancedVisibility visible = new Ic2AdvancedVisibility(recipe)) {
          require(
              visible.shown()
                  == (Boolean) invoke(recipe.getClass(), recipe, "canShow", new Class<?>[0]),
              "IC2 native hidden/config visibility differs");
          java.lang.reflect.Field raw = valueType.getField("value");
          raw.set(entry, "changed");
          try {
            fails("recipe_changed", visible::verify);
          } finally {
            raw.set(entry, value);
          }
          visible.verify();
          visible.close();
          visible.close();
          fails("capture_closed", visible::verify);
        }
        cases++;
      }
      System.out.println(
          "IC2 native visibility: "
              + cases
              + " hidden/config Boolean cases, no config initialization, readiness, drift and close passed");
    } finally {
      hidden.setBoolean(recipe, false);
      configField.set(null, saved);
    }
  }

  private static void fails(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected " + code);
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault error) {
      require(error.code.equals(code), error.code);
    }
  }

  private static void inputCases(ItemStack template) throws Exception {
    Class<?> ore = net.minecraftforge.oredict.OreDictionary.class;
    String[] roots = {"nameToId", "idToName", "idToStack", "idToStackUn", "stackToId"};
    Object[] original = new Object[roots.length];
    for (int index = 0; index < roots.length; index++)
      original[index] = field(ore, null, roots[index]);
    try {
      for (int index = 0; index < roots.length; index++) {
        java.lang.reflect.Field root = ore.getDeclaredField(roots[index]);
        root.setAccessible(true);
        root.set(null, index == 0 || index == 4 ? new HashMap<>() : new ArrayList<>());
      }
      net.minecraftforge.oredict.OreDictionary.registerOre("ic2FixtureOre", template.copy());
      Object itemPredicate =
          type("ic2.api.recipe.RecipeInputItemStack")
              .getConstructor(ItemStack.class, int.class)
              .newInstance(template, 99);
      Object orePredicate =
          type("ic2.api.recipe.RecipeInputOreDict")
              .getConstructor(String.class, int.class, Integer.class)
              .newInstance("ic2FixtureOre", 77, Integer.valueOf(5));
      Object fluidPredicate =
          type("ic2.api.recipe.RecipeInputFluidContainer")
              .getConstructor(net.minecraftforge.fluids.Fluid.class, int.class)
              .newInstance(net.minecraftforge.fluids.FluidRegistry.WATER, 250);
      Object[] source = {
        template,
        Arrays.asList(itemPredicate, new Object[] {orePredicate}),
        "ic2FixtureMissing",
        fluidPredicate
      };
      int names = net.minecraftforge.oredict.OreDictionary.getOreNames().length;
      try (Ic2AdvancedInputs inputs = new Ic2AdvancedInputs(source)) {
        require(
            field(orePredicate, "ores") == null,
            "Read-only IC2 expansion initialized the native predicate cache");
        require(
            inputs.copies(0).get(0).stackSize == 7
                && inputs.copies(1).size() == 2
                && inputs.copies(1).get(1).getItemDamage() == 2,
            "IC2 expansion applied predicate amount/meta instead of native getInputs semantics");
        require(
            inputs.copies(2).isEmpty()
                && net.minecraftforge.oredict.OreDictionary.getOreNames().length == names,
            "Read-only IC2 expansion registered a missing ore name");
        for (int index : new int[] {0, 1, 3}) {
          List<?> nativeRows =
              (List<?>)
                  invoke(
                      type("ic2.core.AdvRecipe"),
                      null,
                      "expand",
                      new Class<?>[] {Object.class},
                      source[index]);
          List<ItemStack> observed = inputs.copies(index);
          require(nativeRows.size() == observed.size(), "Native IC2 candidate count differs");
          for (int row = 0; row < nativeRows.size(); row++)
            require(
                ItemStack.areItemStacksEqual((ItemStack) nativeRows.get(row), observed.get(row)),
                "Native IC2 expanded candidate differs");
        }
        inputs.verify();
        require(
            !(Boolean)
                invoke(
                    orePredicate.getClass(),
                    orePredicate,
                    "matches",
                    new Class<?>[] {ItemStack.class},
                    template.copy()),
            "Native metadata override fixture changed");
        IRecipe nativeRecipe =
            (IRecipe)
                type("ic2.core.AdvShapelessRecipe")
                    .getConstructor(ItemStack.class, Object[].class)
                    .newInstance(new ItemStack(Items.paper), new Object[] {orePredicate});
        InventoryCrafting grid =
            new InventoryCrafting(
                new Container() {
                  public boolean canInteractWith(EntityPlayer p) {
                    return false;
                  }
                },
                3,
                3);
        ItemStack one = template.copy();
        one.stackSize = 1;
        grid.setInventorySlotContents(0, one);
        require(
            nativeRecipe.matches(grid, null),
            "Native Advanced crafting unexpectedly used IRecipeInput.matches/getAmount");
        template.stackSize = 6;
        try {
          fails("recipe_changed", inputs::verify);
        } finally {
          template.stackSize = 7;
        }
        Object first = source[0];
        source[0] = template.copy();
        try {
          fails("recipe_changed", inputs::verify);
        } finally {
          source[0] = first;
        }
        inputs.verify();
        java.nio.file.Files.write(
            java.nio.file.Paths.get("build/native-tests/ic2-advanced-input-observations.json"),
            com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(inputs.declaration()));
        inputs.close();
        inputs.close();
        fails("capture_closed", inputs::declaration);
      }
      final int[] calls = {0};
      Iterable<ItemStack> unknown =
          () -> {
            calls[0]++;
            throw new AssertionError("Unknown iterator executed");
          };
      fails("recipe_unsupported", () -> new Ic2AdvancedInputs(new Object[] {unknown}));
      require(calls[0] == 0, "IC2 expanded an unknown callback");
      Object[] cycle = new Object[1];
      cycle[0] = cycle;
      fails("recipe_unsupported", () -> new Ic2AdvancedInputs(cycle));
      System.out.println(
          "IC2 native expansion: raw counts, ignored predicate amount/meta, fluid containers, zero cache/ore writes, drift, lifecycle and unknown callback rejection passed");
    } finally {
      for (int index = 0; index < roots.length; index++) {
        java.lang.reflect.Field root = ore.getDeclaredField(roots[index]);
        root.setAccessible(true);
        root.set(null, original[index]);
      }
    }
  }
}
