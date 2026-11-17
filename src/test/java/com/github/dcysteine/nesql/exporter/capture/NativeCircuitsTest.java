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

/**
 * Actual Galacticraft code with owned inventories and a bounded spawn sink; no game is launched.
 */
final class NativeCircuitsTest {
  private static final String GC = "micdoodle8.mods.galacticraft.";

  @SuppressWarnings("unchecked")
  static void run() throws Exception {
    Object savedMods = field(cpw.mods.fml.common.Loader.instance(), "namedMods");
    Object savedApis = field(cpw.mods.fml.common.ModAPIManager.INSTANCE, "apiContainers");
    net.minecraft.launchwrapper.LaunchClassLoader savedLaunch =
        net.minecraft.launchwrapper.Launch.classLoader;
    try {
      net.minecraft.launchwrapper.Launch.classLoader =
          (net.minecraft.launchwrapper.LaunchClassLoader) NativeCircuitsTest.class.getClassLoader();
      cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
          cpw.mods.fml.common.Loader.class,
          cpw.mods.fml.common.Loader.instance(),
          Collections.emptyMap(),
          "namedMods");
      cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
          cpw.mods.fml.common.ModAPIManager.class,
          cpw.mods.fml.common.ModAPIManager.INSTANCE,
          Collections.emptyMap(),
          "apiContainers");
      try (NativeScope owned = new NativeScope()) {
        checks();
      }
    } finally {
      net.minecraft.launchwrapper.Launch.classLoader = savedLaunch;
      cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
          cpw.mods.fml.common.Loader.class,
          cpw.mods.fml.common.Loader.instance(),
          savedMods,
          "namedMods");
      cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
          cpw.mods.fml.common.ModAPIManager.class,
          cpw.mods.fml.common.ModAPIManager.INSTANCE,
          savedApis,
          "apiContainers");
    }
  }

  @SuppressWarnings("unchecked")
  private static void checks() throws Exception {
    cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
        cpw.mods.fml.common.Loader.class,
        cpw.mods.fml.common.Loader.instance(),
        Collections.emptyMap(),
        "namedMods");
    cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
        cpw.mods.fml.common.ModAPIManager.class,
        cpw.mods.fml.common.ModAPIManager.INSTANCE,
        Collections.emptyMap(),
        "apiContainers");
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler) type(GC + "core.nei.CircuitFabricatorRecipeHandler").newInstance();
    TemplateRecipeHandler amunra =
        (TemplateRecipeHandler)
            type("de.katzenpapst.amunra.nei.recipehandler.ARCircuitFab").newInstance();
    require(
        Recipes.adapter(handler) != null && Recipes.adapter(amunra) != null,
        "Missing circuit adapters");
    require(
        Recipes.adapter(
                (TemplateRecipeHandler)
                    type("de.katzenpapst.amunra.nei.recipehandler.ARNasaWorkbenchShuttle")
                        .newInstance())
            != null,
        "AmunRa shuttle has no native adapter");
    Class<?> registryType = type(GC + "api.recipe.CircuitFabricatorRecipes");
    Map<ItemStack[], ItemStack> registry =
        (Map<ItemStack[], ItemStack>) field(registryType, null, "recipes");
    ItemStack paper = new ItemStack(Items.paper, 17, 32767);
    paper.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(42));
    ItemStack output = new ItemStack(Items.diamond, 3);
    output.setTagInfo("preserved", new net.minecraft.nbt.NBTTagInt(9));
    ItemStack[] input = {
      paper,
      new ItemStack(Items.iron_ingot, 9),
      new ItemStack(Items.milk_bucket),
      new ItemStack(Items.redstone),
      null
    };
    Method add = registryType.getMethod("addRecipe", ItemStack.class, ItemStack[].class);
    Method match = registryType.getMethod("getOutputForInput", ItemStack[].class);
    add.invoke(null, output, input);
    ItemStack[] offered = new ItemStack[5];
    for (int i = 0; i < 5; i++)
      if (input[i] != null) {
        offered[i] = input[i].copy();
        offered[i].stackSize = 1;
        offered[i].setTagCompound(null);
      }
    require(
        match.invoke(null, (Object) offered) == output,
        "Native matcher unexpectedly requires quantity or NBT");
    offered[0].setItemDamage(0);
    require(
        match.invoke(null, (Object) offered) == null,
        "32767 unexpectedly acts as a native wildcard");
    offered[0].setItemDamage(32767);
    offered[4] = new ItemStack(Items.paper);
    require(match.invoke(null, (Object) offered) == null, "Native matcher ignored an empty slot");
    offered[4] = null;
    List<Map.Entry<ItemStack[], ItemStack>> snapshot = CircuitRecipes.snapshot(registry, null);
    RecipeRow row = row(offered, output);
    TemplateRecipeHandler.CachedRecipe borrowed =
        handler.new CachedRecipe() {
          @Override
          public codechicken.nei.PositionedStack getResult() {
            return null;
          }
        };
    handler.arecipes.add(borrowed);
    require(
        CircuitRecipes.capture(snapshot.get(0), false, true, row),
        "Native registered recipe rejected");
    require(
        handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
        "Circuit semantic capture still replaces the borrowed NEI GUI cache");
    JsonObject choice =
        row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
    require(
        choice.get("amount").getAsString().equals("1")
            && choice.getAsJsonObject("rule").get("nbt").getAsBoolean()
            && !choice.getAsJsonObject("rule").get("meta").getAsBoolean(),
        "Native amount/NBT/meta rules lost");
    require(
        row.inputs.size() == 4
            && row.inputs
                    .get(2)
                    .getAsJsonObject()
                    .getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("returns")
                    .size()
                == 0,
        "Machine input count or no-container-return behavior changed");
    require(
        row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull()
            && row.outputs
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("quantity")
                .get("nominal")
                .getAsString()
                .equals("3"),
        "Native time/result/empty requirements lost");
    require(
        paper.stackSize == 17
            && paper.getTagCompound().getInteger("ignored") == 42
            && output.getTagCompound().getInteger("preserved") == 9,
        "View changed live registration");
    require(
        row.record.has("process")
            && row.record
                .getAsJsonObject("process")
                .get("kind")
                .getAsString()
                .equals("circuitFabricator")
            && row.properties.entrySet().isEmpty()
            && row.record.get("duration").isJsonNull(),
        "Circuit needs a typed five-slot/output-state law, not generic time/properties");
    row.record.add(
        "source",
        object(
            "owner",
            "GalacticraftCore",
            "handler",
            handler.getClass().getName(),
            "key",
            "native-circuit-gui-free"));
    row.record.addProperty(
        "category", Identity.content("category", row.record.getAsJsonObject("source")));
    List<ItemStack> specimens = new ArrayList<>();
    for (ItemStack stack : offered) if (stack != null) specimens.add(stack);
    specimens.add(output);
    NativeContractFixture.writeOwnedWithDurability("native-circuit-law", row, specimens);
    RecipeRow chinese = row("zh_CN", offered, output);
    CircuitRecipes.capture(snapshot.get(0), false, true, chinese);
    chinese.record.add("source", row.record.get("source"));
    chinese.record.add("category", row.record.get("category"));
    NativeContractFixture.writeOwnedWithDurability("native-circuit-law-zh_CN", chinese, specimens);
    require(
        row.record.get("id").equals(chinese.record.get("id")),
        "Circuit language changes semantic identity");

    // Exercise native quick-mode output and actual consume-one/no-container path without world
    // callbacks.
    Class<?> itemsType = type(GC + "core.items.GCItems");
    Field basic = itemsType.getField("basicItem");
    basic.set(null, Items.diamond);
    Class<?> config = type(GC + "core.util.ConfigManagerCore");
    Field quick = config.getField("quickMode");
    quick.setBoolean(null, true);
    Class<?> machineType = type(GC + "core.tile.TileEntityCircuitFabricator");
    Object machine = machineType.newInstance();
    Field inventoryField = machineType.getDeclaredField("containingItems");
    inventoryField.setAccessible(true);
    Method update = machineType.getMethod("updateInput"),
        compress = machineType.getMethod("compressItems");
    for (int meta : new int[] {13, 14}) {
      registry.clear();
      output.setItemDamage(meta);
      add.invoke(null, output, input);
      ItemStack[] inventory = new ItemStack[7];
      for (int slot = 0; slot < 5; slot++)
        if (offered[slot] != null) {
          inventory[slot + 1] = offered[slot].copy();
          inventory[slot + 1].stackSize = 4;
        }
      inventoryField.set(machine, inventory);
      update.invoke(machine);
      compress.invoke(machine);
      RecipeRow quickRow = row(offered, output);
      CircuitRecipes.capture(CircuitRecipes.snapshot(registry, null).get(0), true, false, quickRow);
      require(
          inventory[6].stackSize == (meta == 13 ? 5 : 2)
              && inventory[3].getItem() == Items.milk_bucket
              && inventory[3].stackSize == 3,
          "Native quick output or consumption changed");
      require(
          quickRow
                  .outputs
                  .get(0)
                  .getAsJsonObject()
                  .getAsJsonObject("quantity")
                  .get("nominal")
                  .getAsInt()
              == inventory[6].stackSize,
          "Export differs from native quick output");
      ItemStack existing = new ItemStack(Items.diamond, 1, meta);
      existing.setTagInfo("existing", new net.minecraft.nbt.NBTTagInt(19));
      inventory[6] = existing;
      update.invoke(machine);
      compress.invoke(machine);
      require(
          inventory[6] == existing
              && existing.stackSize == (meta == 13 ? 6 : 3)
              && existing.getTagCompound().getInteger("existing") == 19
              && !existing.getTagCompound().hasKey("preserved"),
          "Native circuit merge did not keep existing output tags");
    }
    // Actual native world-spawn path: original count=1 admits existing63, quick5 attempts four
    // untagged unit drops. The native method ignores a false spawn return value.
    output.setItemDamage(13);
    output.stackSize = 1;
    registry.clear();
    add.invoke(null, output, input);
    ItemStack[] overflowInventory = new ItemStack[7];
    for (int slot = 0; slot < 5; slot++)
      if (offered[slot] != null) {
        overflowInventory[slot + 1] = offered[slot].copy();
        overflowInventory[slot + 1].stackSize = 4;
      }
    ItemStack retained = new ItemStack(Items.diamond, 63, 13);
    retained.setTagInfo("existing", new net.minecraft.nbt.NBTTagInt(19));
    overflowInventory[6] = retained;
    inventoryField.set(machine, overflowInventory);
    Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    DropWorld world =
        (DropWorld) ((sun.misc.Unsafe) access.get(null)).allocateInstance(DropWorld.class);
    world.drops = new ArrayList<>();
    Field rand = net.minecraft.world.World.class.getDeclaredField("rand");
    rand.setAccessible(true);
    rand.set(world, new Random(1));
    Field provider = net.minecraft.world.World.class.getDeclaredField("provider");
    provider.setAccessible(true);
    provider.set(world, new net.minecraft.world.WorldProviderSurface());
    Field worldField = net.minecraft.tileentity.TileEntity.class.getDeclaredField("worldObj");
    worldField.setAccessible(true);
    worldField.set(machine, world);
    update.invoke(machine);
    compress.invoke(machine);
    require(
        retained.stackSize == 64
            && retained.getTagCompound().getInteger("existing") == 19
            && world.drops.size() == 4
            && overflowInventory[3].stackSize == 3,
        "Native overflow capacity/drop/debit changed");
    for (net.minecraft.entity.item.EntityItem drop : world.drops)
      require(
          drop.getEntityItem().stackSize == 1
              && drop.getEntityItem().getItemDamage() == 13
              && drop.getEntityItem().getTagCompound() == null
              && drop.delayBeforeCanPickup == 10,
          "Native overflow copied template tags or changed unit/pickup semantics");
    RecipeRow quickLaw = row(offered, output);
    CircuitRecipes.capture(CircuitRecipes.snapshot(registry, null).get(0), true, false, quickLaw);
    writeFixture("native-circuit-quick", quickLaw, handler, offered, output);
    output.stackSize = 3;
    overflowInventory[6] = new ItemStack(Items.diamond, 63, 13);
    update.invoke(machine);
    compress.invoke(machine);
    require(
        overflowInventory[6].stackSize == 63
            && world.drops.size() == 4
            && overflowInventory[3].stackSize == 2,
        "Direct rejected compressItems does not match native debit law");
    registry.clear();
    ItemStack[] emptyInputs = new ItemStack[5];
    add.invoke(null, output, emptyInputs);
    require(
        match.invoke(null, (Object) emptyInputs) == output,
        "Native empty-five-slot definition rejected");
    ItemStack[] emptyInventory = new ItemStack[7];
    inventoryField.set(machine, emptyInventory);
    update.invoke(machine);
    compress.invoke(machine);
    require(emptyInventory[6].stackSize == 5, "Native all-empty quick output changed");
    RecipeRow emptyLaw = row(emptyInputs, output);
    CircuitRecipes.capture(CircuitRecipes.snapshot(registry, null).get(0), true, false, emptyLaw);
    require(
        emptyLaw.inputs.size() == 0
            && emptyLaw.record.getAsJsonObject("process").getAsJsonArray("slots").size() == 5,
        "All-empty native law was weakened or lost");
    writeFixture("native-circuit-empty", emptyLaw, handler, emptyInputs, output);
    registry.clear();
    add.invoke(null, output, input);
    quick.setBoolean(null, false);
    // Same predicate with different count/NBT: native encounter order wins, including another
    // category's row.
    Map<ItemStack[], ItemStack> ordered = new LinkedHashMap<>();
    ordered.put(offered, new ItemStack(Items.gold_ingot));
    ordered.put(input, output);
    require(
        CircuitRecipes.snapshot(ordered, null).size() == 1
            && CircuitRecipes.snapshot(ordered, null).get(0).getValue().getItem()
                == Items.gold_ingot,
        "Native encounter precedence changed");
    Class<?> sourceType = type("de.katzenpapst.amunra.crafting.CircuitFabricatorRecipe");
    Object source =
        sourceType
            .getConstructor(
                ItemStack.class,
                ItemStack[].class,
                ItemStack[].class,
                ItemStack[].class,
                ItemStack[].class,
                ItemStack[].class)
            .newInstance(
                output,
                new ItemStack[] {input[0]},
                new ItemStack[] {input[1]},
                new ItemStack[] {input[2]},
                new ItemStack[] {input[3]},
                new ItemStack[0]);
    List<?> sources = Collections.singletonList(source);
    require(
        CircuitRecipes.snapshot(registry, sources).size() == 1
            && CircuitRecipes.snapshot(ordered, sources).isEmpty(),
        "AmunRa projection bypasses native registration or precedence");
    Map<String, cpw.mods.fml.common.ModContainer> mods = new HashMap<>();
    for (String[] version :
        new String[][] {{"GalacticraftCore", "3.3.13-GTNH"}, {"GalacticraftAmunRa", "0.8.2"}}) {
      cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
      metadata.modId = version[0];
      metadata.version = version[1];
      mods.put(version[0], new cpw.mods.fml.common.DummyModContainer(metadata));
    }
    cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
        cpw.mods.fml.common.Loader.class, cpw.mods.fml.common.Loader.instance(), mods, "namedMods");
    ((List<Object>)
            field(
                type("de.katzenpapst.amunra.crafting.RecipeHelper"),
                null,
                "circuitFabricatorRecipes"))
        .add(source);
    try (CircuitRecipes gcReader = new CircuitRecipes(handler);
        CircuitRecipes arReader = new CircuitRecipes(amunra)) {
      require(
          gcReader.size() == 1 && arReader.size() == 1,
          "Production circuit cursor or pinned identities changed");
      RecipeRow arRow = row(offered, output);
      require(arReader.capture(0, arRow), "Native AmunRa row was not admitted");
      writeFixture("native-circuit-amunra", arRow, amunra, offered, output);
      gcReader.finishVerification();
      arReader.finishVerification();
      ItemStack originalValue = registry.get(input);
      registry.put(input, originalValue.copy());
      try {
        expect("environment_changed", gcReader::verify);
      } finally {
        registry.put(input, originalValue);
      }
      ItemStack originalPart = input[0];
      input[0] = originalPart.copy();
      try {
        expect("environment_changed", gcReader::verify);
      } finally {
        input[0] = originalPart;
      }
      net.minecraft.nbt.NBTTagCompound oldTag = paper.getTagCompound();
      paper.setTagCompound((net.minecraft.nbt.NBTTagCompound) oldTag.copy());
      try {
        expect("environment_changed", gcReader::verify);
      } finally {
        paper.setTagCompound(oldTag);
      }
      quick.setBoolean(null, true);
      try {
        expect("environment_changed", gcReader::verify);
      } finally {
        quick.setBoolean(null, false);
      }
      List slot0 = (List) ((List) field(registryType, null, "slotValidItems")).get(0);
      ItemStack originalAllowed = (ItemStack) slot0.get(0);
      slot0.set(0, originalAllowed.copy());
      try {
        expect("environment_changed", gcReader::verify);
      } finally {
        slot0.set(0, originalAllowed);
      }
      require(
          RegistrationBindings.read(handler)
              .same(
                  RegistrationBindings.read(
                      (codechicken.nei.recipe.IRecipeHandler) handler.getClass().newInstance())),
          "Independent GC registrations lost their source");
      require(
          RegistrationBindings.read(amunra)
              .same(
                  RegistrationBindings.read(
                      (codechicken.nei.recipe.IRecipeHandler) amunra.getClass().newInstance())),
          "Independent AmunRa registrations lost their source");
      gcReader.finishVerification();
      arReader.finishVerification();
      gcReader.close();
      gcReader.close();
      expect("capture_closed", gcReader::size);
      try (Recipes.Cursor production =
          new Recipes.Handler(handler, 0).open(row(offered, output).facts)) {
        require(
            field(production, "handler") == null,
            "Production circuit cursor retains NEI GUI handler");
        production.finishVerification();
      }
    }
    registry.clear();
    try (CircuitRecipes empty = new CircuitRecipes(handler)) {
      require(empty.size() == 0, "Ready-empty circuit registry rejected");
      empty.finishVerification();
    } finally {
      registry.put(input, output);
    }
    ItemStack[] duplicate = input.clone();
    add.invoke(null, output, duplicate);
    try (CircuitRecipes ledger = new CircuitRecipes(handler);
        CircuitRecipes arLedger = new CircuitRecipes(amunra)) {
      require(
          ledger.size() == 2 && arLedger.size() == 2,
          "Circuit source census silently omitted duplicate/category entries");
      int accepted = 0, excluded = 0;
      for (int i = 0; i < ledger.size(); i++) {
        RecipeRow audit = row(offered, output);
        if (ledger.capture(i, audit)) accepted++;
        else {
          excluded++;
          require(audit.exclusion != null, "Missing native duplicate disposition");
        }
        RecipeRow arAudit = row(offered, output);
        if (!arLedger.capture(i, arAudit))
          require(arAudit.exclusion != null, "Missing AR category disposition");
      }
      require(accepted == 1 && excluded == 1, "Circuit native first-match ledger changed");
    } finally {
      registry.remove(duplicate);
    }
    expect(
        "source_budget",
        () ->
            new CircuitSource.Budget()
                .nbt(new net.minecraft.nbt.NBTTagByteArray(new byte[6 * 1024 * 1024])));
    ItemStack originalMetadataInput = input[0];
    ItemStack negative = originalMetadataInput.copy();
    cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
        ItemStack.class, negative, -1, "itemDamage", "field_77991_e");
    input[0] = negative;
    try {
      require(
          match.invoke(null, (Object) input) == output, "Native signed metadata matcher changed");
      expect("recipe_unsupported", () -> new CircuitRecipes(handler));
    } finally {
      input[0] = originalMetadataInput;
    }
    ItemStack negativeOutput = output.copy();
    cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
        ItemStack.class, negativeOutput, -1, "itemDamage", "field_77991_e");
    registry.put(input, negativeOutput);
    try {
      expect("recipe_unsupported", () -> new CircuitRecipes(handler));
    } finally {
      registry.put(input, output);
    }
    Field arField =
        type("de.katzenpapst.amunra.crafting.RecipeHelper")
            .getDeclaredField("circuitFabricatorRecipes");
    arField.setAccessible(true);
    Object arRoot = arField.get(null);
    arField.set(null, null);
    try {
      expect("handler_not_ready", () -> new CircuitRecipes(amunra));
    } finally {
      arField.set(null, arRoot);
    }
    Field admittedField = registryType.getField("slotValidItems");
    Object admittedRoot = admittedField.get(null);
    UnknownSlots unknownSlots = new UnknownSlots();
    admittedField.set(null, unknownSlots);
    try {
      expect("recipe_unsupported", () -> new CircuitRecipes(handler));
      require(unknownSlots.calls == 0, "Unknown circuit slot iterator called");
    } finally {
      admittedField.set(null, admittedRoot);
    }
    net.minecraft.item.Item unknownItem = new UnknownMetadata();
    ItemStack[] badInput = {new ItemStack(unknownItem), null, null, null, null};
    registry.put(badInput, output);
    try {
      expect("recipe_unsupported", () -> new CircuitRecipes(handler));
      require(UnknownMetadata.calls == 0, "Unknown circuit metadata callback called");
    } finally {
      registry.remove(badInput);
    }
    try {
      CircuitRecipes.snapshot(registry, Collections.singletonList(new Object()));
      throw new AssertionError("Unknown AmunRa source accepted");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("recipe_unsupported"), "Wrong unknown-source error");
    }
    ((List<?>) ((List<?>) field(registryType, null, "slotValidItems")).get(0)).clear();
    require(
        !CircuitRecipes.capture(
            snapshot.get(0), false, false, row(offered, snapshot.get(0).getValue())),
        "Inaccessible native input accepted");
    // Preserve the existing small space/refinery checks inside the same owned native scope.
    NativeSpaceTest.amunra();
    refinery();
    System.out.println(
        "Native circuits: GC/AmunRa routes, fixed/empty inputs, native quick mode, encounter precedence, consume-one/no returns, ownership passed");
  }

  private static void refinery() throws Exception {
    Class<?> core = type(GC + "core.GalacticraftCore"),
        config = type(GC + "core.util.ConfigManagerCore");
    for (String name : new String[] {"oil", "oil_fixture", "water_fixture", "fuel", "fuelgc"})
      if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered(name))
        net.minecraftforge.fluids.FluidRegistry.registerFluid(
            new net.minecraftforge.fluids.Fluid(name));
    net.minecraftforge.fluids.Fluid oil = net.minecraftforge.fluids.FluidRegistry.getFluid("oil");
    core.getField("fluidOil").set(null, oil);
    Class<?> machineType = type(GC + "core.tile.TileEntityRefinery");
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler) type(GC + "core.nei.RefineryRecipeHandler").newInstance();
    TemplateRecipeHandler.CachedRecipe borrowed =
        handler.new CachedRecipe() {
          @Override
          public codechicken.nei.PositionedStack getResult() {
            return null;
          }
        };
    handler.arecipes.add(borrowed);
    for (boolean old : new boolean[] {false, true}) {
      String fuelName = old ? "fuelgc" : "fuel";
      config.getField("useOldFuelFluidID").setBoolean(null, old);
      config.getField("hardMode").setBoolean(null, old);
      core.getField("fluidFuel")
          .set(null, net.minecraftforge.fluids.FluidRegistry.getFluid(fuelName));
      Object machine = machineType.newInstance();
      Field metadata = net.minecraft.tileentity.TileEntity.class.getDeclaredField("blockMetadata");
      metadata.setAccessible(true);
      metadata.setInt(machine, 0);
      net.minecraftforge.fluids.FluidTank input =
          (net.minecraftforge.fluids.FluidTank) field(machine, "oilTank");
      net.minecraftforge.fluids.FluidTank output =
          (net.minecraftforge.fluids.FluidTank) field(machine, "fuelTank");
      input.setFluid(null);
      net.minecraftforge.fluids.FluidStack offered =
          new net.minecraftforge.fluids.FluidStack(
              net.minecraftforge.fluids.FluidRegistry.getFluid("oil_fixture"), 3);
      offered.tag = new net.minecraft.nbt.NBTTagCompound();
      offered.tag.setString("batch", "native");
      require(
          (Integer)
                  invoke(
                      machineType,
                      machine,
                      "fill",
                      new Class<?>[] {
                        net.minecraftforge.common.util.ForgeDirection.class,
                        net.minecraftforge.fluids.FluidStack.class,
                        boolean.class
                      },
                      net.minecraftforge.common.util.ForgeDirection.NORTH,
                      offered,
                      true)
              == 3,
          "Native refinery rejected an oil-prefixed fluid");
      require(
          input.getFluid().getFluid() == oil && input.getFluid().tag == null,
          "Native alternate oil was not normalized");
      invoke(machineType, machine, "smeltItem", new Class<?>[0]);
      require(
          input.getFluidAmount() == 2
              && output.getFluidAmount() == 1
              && output.getFluid().getFluid().getName().equals(fuelName),
          "Native refinery did not consume/produce exactly one mB");
      output.setFluid(
          new net.minecraftforge.fluids.FluidStack(
              output.getFluid().getFluid(), output.getCapacity()));
      invoke(machineType, machine, "smeltItem", new Class<?>[0]);
      require(
          input.getFluidAmount() == 2
              && !(Boolean) invoke(machineType, machine, "canProcess", new Class<?>[0]),
          "Full tank consumed more oil");
      require(
          offered.amount == 3 && offered.tag.getString("batch").equals("native"),
          "Refinery modified offered fluid");
      net.minecraftforge.fluids.FluidStack taggedFuel =
          new net.minecraftforge.fluids.FluidStack(output.getFluid().getFluid(), 1);
      taggedFuel.tag = new net.minecraft.nbt.NBTTagCompound();
      taggedFuel.tag.setString("existing", "not-the-prototype");
      output.setFluid(taggedFuel);
      invoke(machineType, machine, "smeltItem", new Class<?>[0]);
      require(
          input.getFluidAmount() == 1
              && output.getFluid() == taggedFuel
              && output.getFluidAmount() == 1,
          "Native refinery did not retain drain-before-rejected-NBT-fill behavior");
      input.setFluid(null);
      net.minecraftforge.fluids.FluidStack canonical =
          new net.minecraftforge.fluids.FluidStack(oil, 1);
      canonical.tag = new net.minecraft.nbt.NBTTagCompound();
      canonical.tag.setString("batch", "canonical");
      invoke(
          machineType,
          machine,
          "fill",
          new Class<?>[] {
            net.minecraftforge.common.util.ForgeDirection.class,
            net.minecraftforge.fluids.FluidStack.class,
            boolean.class
          },
          net.minecraftforge.common.util.ForgeDirection.NORTH,
          canonical,
          true);
      require(
          input.getFluidAmount() == 1
              && input.getFluid().tag.getString("batch").equals("canonical")
              && canonical.amount == 1
              && canonical.tag.getString("batch").equals("canonical"),
          "Canonical oil lost offered NBT or changed caller stack");
      require(
          (Integer)
                  invoke(
                      machineType,
                      machine,
                      "fill",
                      new Class<?>[] {
                        net.minecraftforge.common.util.ForgeDirection.class,
                        net.minecraftforge.fluids.FluidStack.class,
                        boolean.class
                      },
                      net.minecraftforge.common.util.ForgeDirection.NORTH,
                      new net.minecraftforge.fluids.FluidStack(
                          net.minecraftforge.fluids.FluidRegistry.getFluid("water_fixture"), 1),
                      true)
              == 0,
          "Native refinery accepted a non-oil fluid");
      require(Recipes.adapter(handler) != null, "Refinery has no native adapter");
      Class<?> adapter = type("com.github.dcysteine.nesql.exporter.capture.RefineryRecipes");
      Constructor<?> constructor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class);
      constructor.setAccessible(true);
      RegistryRecipes cursor = (RegistryRecipes) constructor.newInstance(handler);
      int expected =
          (int)
              net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids().keySet().stream()
                  .filter(name -> name.startsWith("oil"))
                  .count();
      require(
          cursor.size() == expected, "Refinery did not enumerate exact native prefix acceptance");
      for (int index = 0; index < cursor.size(); index++) {
        RecipeRow row =
            new RecipeRow(
                new Facts("en_US"),
                object("owner", "GalacticraftCore", "handler", "native", "key", "refinery"),
                "category_test",
                index);
        require(cursor.capture(index, row), "Refinery skipped an admitted oil");
        require(
            row.record.has("process")
                && row.record
                    .getAsJsonObject("process")
                    .get("kind")
                    .getAsString()
                    .equals("galacticraftRefinery")
                && row.record.get("duration").isJsonNull()
                && row.properties.entrySet().isEmpty()
                && row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull(),
            "Refinery needs drain-before-fill uncertainty and native countdown/extraction law");
        require(
            handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
            "Refinery semantic reader mutated a borrowed GUI/cache object");
        JsonObject choice =
            row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(
            row.inputs.size() == 1
                && row.inputs.get(0).getAsJsonObject().get("kind").getAsString().equals("fluid")
                && choice.get("amount").getAsString().equals("1")
                && choice.getAsJsonObject("rule").get("nbt").getAsBoolean(),
            "Refinery invented consumed containers or lost fluid matching");
        require(
            row.outputs
                    .get(0)
                    .getAsJsonObject()
                    .get("id")
                    .getAsString()
                    .equals(Identity.fluid(fuelName, null))
                && row.outputs
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("quantity")
                    .get("nominal")
                    .getAsString()
                    .equals("1"),
            "Refinery conversion differs from native processing");
        require(
            row.record.getAsJsonObject("process").get("maxExtractGj").getAsInt() == (old ? 90 : 60)
                && row.record.getAsJsonObject("process").get("freshStartupTicks").getAsInt() == 1,
            "Refinery lost native power mode or startup delay");
        require(
            row.inputs.size() == 1
                && row.outputs.size() == 1
                && row.inputs.get(0).getAsJsonObject().get("kind").getAsString().equals("fluid")
                && row.outputs.get(0).getAsJsonObject().get("kind").getAsString().equals("fluid"),
            "Refinery fluid semantics changed");
        String name = "native-refinery" + (old ? "-legacy" : "") + (index == 0 ? "" : "-alternate");
        writeRefineryFixture(name, row, handler);
        if (!old && index == 0) {
          RecipeRow chinese = row("zh_CN", new ItemStack[0], null);
          cursor.capture(index, chinese);
          chinese.record.add("source", row.record.get("source"));
          chinese.record.add("category", row.record.get("category"));
          writeRefineryFixture("native-refinery-zh_CN", chinese, handler);
          require(
              chinese.record.get("id").equals(row.record.get("id")),
              "Refinery language changes native recipe identity");
        }
      }
      cursor.finishVerification();
      Class<?> fluidRegistry = net.minecraftforge.fluids.FluidRegistry.class;
      require(
          RegistrationBindings.read(handler)
              .same(
                  RegistrationBindings.read(
                      (TemplateRecipeHandler) handler.getClass().newInstance())),
          "Refinery independent discovery routes lost their source binding");
      Object originalFluidRoot = field(fluidRegistry, null, "fluids");
      cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
          (Class) fluidRegistry,
          null,
          com.google.common.collect.HashBiMap.create((Map) originalFluidRoot),
          "fluids");
      try {
        expect("environment_changed", cursor::verify);
      } finally {
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
            (Class) fluidRegistry, null, originalFluidRoot, "fluids");
      }
      config.getField("hardMode").setBoolean(null, !old);
      try {
        cursor.verify();
        throw new AssertionError("Refinery configuration drift was ignored");
      } catch (Jobs.Fault failure) {
        require(failure.code.equals("environment_changed"), failure.toString());
      } finally {
        config.getField("hardMode").setBoolean(null, old);
      }
      int density = oil.getDensity();
      oil.setDensity(density + 1);
      try {
        expect("environment_changed", cursor::verify);
      } finally {
        oil.setDensity(density);
      }
      Map delegateMap = (Map) field(fluidRegistry, null, "delegates");
      Object nativeDelegate = delegateMap.get(oil);
      Constructor<?> delegateCtor =
          type("net.minecraftforge.fluids.FluidRegistry$FluidDelegate")
              .getDeclaredConstructor(net.minecraftforge.fluids.Fluid.class, String.class);
      delegateCtor.setAccessible(true);
      delegateMap.put(oil, delegateCtor.newInstance(oil, oil.getName()));
      try {
        expect("environment_changed", cursor::verify);
      } finally {
        delegateMap.put(oil, nativeDelegate);
      }
      cursor.close();
      cursor.close();
      require(
          handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
          "Refinery close cleared borrowed GUI cache");
      try {
        cursor.size();
        throw new AssertionError("Closed refinery reader exposed rows");
      } catch (Jobs.Fault failure) {
        require(failure.code.equals("capture_closed"), failure.toString());
      }
    }
    core.getField("fluidFuel").set(null, net.minecraftforge.fluids.FluidRegistry.getFluid("fuel"));
    try {
      new RefineryRecipes(handler);
      throw new AssertionError("Inconsistent fuel configuration accepted");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("recipe_unsupported"), "Wrong inconsistent-refinery failure");
    }
    config.getField("useOldFuelFluidID").setBoolean(null, false);
    config.getField("hardMode").setBoolean(null, false);
    Object savedOil = core.getField("fluidOil").get(null);
    core.getField("fluidOil").set(null, null);
    try {
      expect(
          "handler_not_ready",
          () ->
              new RefineryRecipes(
                  (TemplateRecipeHandler) newHandler(GC + "core.nei.RefineryRecipeHandler")));
    } finally {
      core.getField("fluidOil").set(null, savedOil);
    }
    UnknownFluid fluid = new UnknownFluid();
    Map nativeFluids = (Map) field(net.minecraftforge.fluids.FluidRegistry.class, null, "fluids");
    nativeFluids.put(fluid.getName(), fluid);
    UnknownFluid.calls = 0;
    UnknownFluid.reject = true;
    try {
      expect(
          "recipe_unsupported",
          () ->
              new RefineryRecipes(
                  (TemplateRecipeHandler) newHandler(GC + "core.nei.RefineryRecipeHandler")));
      require(UnknownFluid.calls == 0, "Unknown refinery fluid hash callback executed");
    } finally {
      UnknownFluid.reject = false;
      nativeFluids.remove(fluid.getName());
    }
    System.out.println(
        "Native refinery: fluid prefix admission, normalization, old/new fuel IDs, one-mB conversion, capacity and input ownership passed");
  }

  private static Object newHandler(String name) {
    try {
      return type(name).newInstance();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  public static final class UnknownFluid extends net.minecraftforge.fluids.Fluid {
    static int calls;
    static boolean reject;

    public UnknownFluid() {
      super("oil_unknown_fixture");
    }

    @Override
    public int hashCode() {
      if (reject) {
        calls++;
        throw new AssertionError("Unknown fluid hash");
      }
      return super.hashCode();
    }
  }

  @SuppressWarnings("unchecked")
  private static RecipeRow row(ItemStack[] inputs, ItemStack output) {
    return row("en_US", inputs, output);
  }

  private static void writeRefineryFixture(
      String name, RecipeRow row, TemplateRecipeHandler handler) throws Exception {
    if (!row.record.has("id")) {
      String key = name.endsWith("-zh_CN") ? name.substring(0, name.length() - 6) : name;
      row.record.add(
          "source",
          object("owner", "GalacticraftCore", "handler", handler.getClass().getName(), "key", key));
      row.record.addProperty(
          "category", Identity.content("category", row.record.getAsJsonObject("source")));
    }
    List<net.minecraftforge.fluids.FluidStack> fluids = new ArrayList<>();
    for (String idKey : new String[] {"input", "normalizedOil", "output"}) {
      String id = row.record.getAsJsonObject("process").get(idKey).getAsString();
      for (net.minecraftforge.fluids.Fluid fluid :
          net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids().values())
        if (Identity.fluid(fluid.getName(), null).equals(id))
          fluids.add(new net.minecraftforge.fluids.FluidStack(fluid, 1));
    }
    NativeContractFixture.writeOwned(
        name,
        row,
        Collections.emptyList(),
        fluids.toArray(new net.minecraftforge.fluids.FluidStack[0]));
  }

  @SuppressWarnings("unchecked")
  private static RecipeRow row(String locale, ItemStack[] inputs, ItemStack output) {
    Facts facts = new Facts(locale);
    Set<String> known = (Set<String>) field(facts, "items");
    List<ItemStack> all = new ArrayList<>(Arrays.asList(inputs));
    all.add(output);
    for (ItemStack stack : all)
      if (stack != null)
        known.add(
            Identity.item(
                net.minecraft.item.Item.itemRegistry.getNameForObject(stack.getItem()),
                stack.getItemDamage(),
                TypedNbt.encode(stack.getTagCompound())));
    return new RecipeRow(
        facts,
        object("owner", "GalacticraftCore", "handler", "native", "key", "circuits"),
        "category_test",
        0);
  }

  private static void writeFixture(
      String name,
      RecipeRow row,
      TemplateRecipeHandler handler,
      ItemStack[] inputs,
      ItemStack output)
      throws Exception {
    row.record.add(
        "source",
        object(
            "owner",
            handler.getClass().getName().startsWith("de.katzenpapst.amunra.")
                ? "GalacticraftAmunRa"
                : "GalacticraftCore",
            "handler",
            handler.getClass().getName(),
            "key",
            name));
    row.record.addProperty(
        "category", Identity.content("category", row.record.getAsJsonObject("source")));
    List<ItemStack> specimens = new ArrayList<>();
    for (ItemStack input : inputs) if (input != null) specimens.add(input);
    specimens.add(output);
    NativeContractFixture.writeOwnedWithDurability(name, row, specimens);
  }

  public static final class DropWorld extends net.minecraft.client.multiplayer.WorldClient {
    List<net.minecraft.entity.item.EntityItem> drops;

    private DropWorld() {
      super(null, null, 0, null, null);
    }

    @Override
    public int getBlockMetadata(int x, int y, int z) {
      return 0;
    }

    @Override
    public net.minecraft.block.Block getBlock(int x, int y, int z) {
      return net.minecraft.init.Blocks.air;
    }

    @Override
    public void markTileEntityChunkModified(
        int x, int y, int z, net.minecraft.tileentity.TileEntity tile) {}

    @Override
    public boolean spawnEntityInWorld(net.minecraft.entity.Entity entity) {
      require(
          drops.size() < 8 && entity instanceof net.minecraft.entity.item.EntityItem,
          "Unbounded/unknown native spawn");
      drops.add((net.minecraft.entity.item.EntityItem) entity);
      return false;
    }
  }

  /**
   * Replace borrowed roots instead of clearing and reconstructing their HashMap encounter order.
   */
  private static final class NativeScope implements AutoCloseable {
    final List<Field> fields = new ArrayList<>();
    final List<Object> values = new ArrayList<>();
    final sun.misc.Unsafe memory;

    NativeScope() throws Exception {
      Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      access.setAccessible(true);
      memory = (sun.misc.Unsafe) access.get(null);
      try {
        replace(
            GC + "api.recipe.CircuitFabricatorRecipes",
            "recipes",
            new HashMap<ItemStack[], ItemStack>());
        replace(
            GC + "api.recipe.CircuitFabricatorRecipes",
            "slotValidItems",
            new ArrayList<ArrayList<ItemStack>>());
        replace(
            "de.katzenpapst.amunra.crafting.RecipeHelper",
            "circuitFabricatorRecipes",
            new ArrayList<>());
        replace(GC + "core.items.GCItems", "basicItem", null);
        replace(GC + "core.util.ConfigManagerCore", "quickMode", false);
        replace(GC + "core.util.ConfigManagerCore", "hardMode", false);
        replace(GC + "core.util.ConfigManagerCore", "useOldFuelFluidID", false);
        replace(
            "de.katzenpapst.amunra.crafting.RecipeHelper", "nasaWorkbenchRecipes", new HashMap<>());
        replace("de.katzenpapst.amunra.item.ARItems", "shuttleItem", null);
        replace(
            GC + "core.GalacticraftCore",
            "fluidOil",
            field(type(GC + "core.GalacticraftCore"), null, "fluidOil"));
        replace(
            GC + "core.GalacticraftCore",
            "fluidFuel",
            field(type(GC + "core.GalacticraftCore"), null, "fluidFuel"));
        net.minecraft.util.RegistryNamespaced ownedItems =
            new net.minecraft.util.RegistryNamespaced();
        for (Object item : net.minecraft.item.Item.itemRegistry)
          ownedItems.addObject(
              net.minecraft.item.Item.itemRegistry.getIDForObject(item),
              net.minecraft.item.Item.itemRegistry.getNameForObject(item),
              item);
        replace("net.minecraft.item.Item", "itemRegistry", ownedItems);
        String fluidRegistry = "net.minecraftforge.fluids.FluidRegistry";
        for (String name :
            new String[] {
              "fluids",
              "fluidIDs",
              "fluidNames",
              "fluidBlocks",
              "masterFluidReference",
              "defaultFluidName"
            }) {
          Object original = field(type(fluidRegistry), null, name);
          replace(
              fluidRegistry,
              name,
              original == null ? null : com.google.common.collect.HashBiMap.create((Map) original));
        }
        Object delegates = field(type(fluidRegistry), null, "delegates");
        replace(
            fluidRegistry, "delegates", delegates == null ? null : new HashMap<>((Map) delegates));
        replace(fluidRegistry, "maxID", field(type(fluidRegistry), null, "maxID"));
      } catch (Exception | Error failure) {
        close();
        throw failure;
      }
    }

    void replace(String owner, String name, Object value) throws Exception {
      Field f = type(owner).getDeclaredField(name);
      f.setAccessible(true);
      fields.add(f);
      values.add(f.get(null));
      put(f, value);
    }

    void put(Field f, Object value) throws Exception {
      if (f.getType() == boolean.class) f.setBoolean(null, (Boolean) value);
      else if (f.getType() == int.class) f.setInt(null, (Integer) value);
      else memory.putObject(memory.staticFieldBase(f), memory.staticFieldOffset(f), value);
    }

    public void close() throws Exception {
      for (int i = fields.size() - 1; i >= 0; i--) put(fields.get(i), values.get(i));
      for (int i = 0; i < fields.size(); i++)
        require(
            fields.get(i).get(null) == values.get(i)
                || fields.get(i).getType().isPrimitive()
                    && fields.get(i).get(null).equals(values.get(i)),
            "Native circuit fixture failed to return a borrowed root");
    }
  }

  private static void expect(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected circuit fault " + code);
    } catch (Jobs.Fault failure) {
      require(failure.code.equals(code), "Wrong circuit failure " + failure);
    }
  }

  public static final class UnknownSlots extends ArrayList<ArrayList<ItemStack>> {
    int calls;

    @Override
    public Iterator<ArrayList<ItemStack>> iterator() {
      calls++;
      throw new AssertionError("Unknown circuit iterator");
    }
  }

  public static final class UnknownMetadata extends net.minecraft.item.Item {
    static int calls;

    @Override
    public int getDamage(ItemStack stack) {
      calls++;
      throw new AssertionError("Unknown circuit metadata");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
