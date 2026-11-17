package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public final class NativeDistilleryTest {
  public static void run() throws Exception {
    Object loader = cpw.mods.fml.common.Loader.instance();
    Object mods = MagicApi.field(loader, "namedMods");
    try {
      cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
          cpw.mods.fml.common.Loader.class,
          cpw.mods.fml.common.Loader.instance(),
          new HashMap<>((Map<?, ?>) mods),
          "namedMods");
      checks();
    } finally {
      cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
          cpw.mods.fml.common.Loader.class,
          cpw.mods.fml.common.Loader.instance(),
          mods,
          "namedMods");
    }
  }

  private static void checks() throws Exception {
    Class<?> recipe =
        Class.forName("com.emoniph.witchery.crafting.DistilleryRecipes$DistilleryRecipe");
    Constructor<?> make =
        recipe.getDeclaredConstructor(
            ItemStack.class,
            ItemStack.class,
            int.class,
            ItemStack.class,
            ItemStack.class,
            ItemStack.class,
            ItemStack.class);
    make.setAccessible(true);
    ItemStack a = new ItemStack(Items.paper, 19), b = new ItemStack(Items.dye, 7, 2);
    ItemStack jar = new ItemStack(Items.glass_bottle), out = new ItemStack(Items.diamond, 3);
    Object nativeRecipe =
        make.newInstance(a, b, 2, out, null, new ItemStack(Items.gold_ingot, 2), null);
    nativeOutputStates(nativeRecipe, a, b);
    Method match =
        recipe.getDeclaredMethod("isMatch", ItemStack.class, ItemStack.class, ItemStack.class);
    match.setAccessible(true);
    ItemStack tagged = b.copy();
    tagged.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(8));
    require(
        (Boolean) match.invoke(nativeRecipe, tagged, a, new ItemStack(Items.glass_bottle, 2)),
        "Native swap/NBT rule changed");
    require(!(Boolean) match.invoke(nativeRecipe, a, b, jar), "Native jar minimum changed");
    Class<?> adapter;
    try {
      adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.DistilleryRecipes");
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("Missing distillery adapter", missing);
    }
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler)
            Class.forName("alkalus.main.nei.NEI_Handler_Distillery").newInstance();
    require(
        Recipes.adapter(handler) != null,
        "Distillery adapter is not reachable from production dispatch");
    Constructor<?> ctor =
        adapter.getDeclaredConstructor(TemplateRecipeHandler.class, List.class, ItemStack.class);
    ctor.setAccessible(true);
    RegistryRecipes cursor =
        (RegistryRecipes) ctor.newInstance(handler, Collections.singletonList(nativeRecipe), jar);
    TemplateRecipeHandler.CachedRecipe borrowed =
        handler.new CachedRecipe() {
          @Override
          public codechicken.nei.PositionedStack getResult() {
            return null;
          }
        };
    handler.arecipes.add(borrowed);
    RecipeRow row =
        NativeCoreFixesTest.row(
            new ItemStack(Items.paper),
            new ItemStack(Items.dye, 1, 2),
            jar,
            out,
            new ItemStack(Items.gold_ingot, 2));
    require(cursor.capture(0, row), "Recipe disappeared");
    require(
        handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
        "Distillery capture replaced borrowed GUI cache");
    require(
        row.record.has("process"),
        "Distillery output loss/merge and native allocation law missing");
    cursor.close();
    cursor.close();
    try {
      cursor.size();
      throw new AssertionError("Closed distillery reader exposes rows");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure) {
      require(failure.code.equals("capture_closed"), "Wrong distillery closed fault");
    }
    cursor =
        (RegistryRecipes) ctor.newInstance(handler, Collections.singletonList(nativeRecipe), jar);
    require(
        row.inputs.size() == 3 && row.outputs.size() == 2, "Jars or secondary output disappeared");
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
        "Display stack size became consumption");
    require(
        row.inputs
            .get(2)
            .getAsJsonObject()
            .getAsJsonArray("choices")
            .get(0)
            .getAsJsonObject()
            .get("amount")
            .getAsString()
            .equals("2"),
        "Jar consumption lost");
    require(
        row.outputs.get(1).getAsJsonObject().get("slot").getAsInt() == 2,
        "Sparse native output slot lost");
    require(
        row.record.get("duration").isJsonNull()
            && row.record.get("process").getAsJsonObject().get("cookTicks").getAsInt() == 800
            && row.record.get("energy").isJsonNull(),
        "Altar power became EU or duration lost");
    require(
        row.inputs
            .get(1)
            .getAsJsonObject()
            .getAsJsonArray("choices")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("rule")
            .get("nbt")
            .getAsBoolean(),
        "Native ignored NBT became exact");
    require(
        !row.inputs
            .get(1)
            .getAsJsonObject()
            .getAsJsonArray("choices")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("rule")
            .get("meta")
            .getAsBoolean(),
        "Subtype became wildcard");

    require(
        row.inputs
                .get(2)
                .getAsJsonObject()
                .getAsJsonArray("choices")
                .get(0)
                .getAsJsonObject()
                .getAsJsonArray("returns")
                .size()
            == 0,
        "Machine jars acquired crafting returns");
    require(
        a.stackSize == 19 && b.stackSize == 7 && jar.stackSize == 1,
        "Capture mutated native registry");
    nativeEdgeCases(handler, recipe, make, jar);
    boundaries(handler, nativeRecipe, jar, ctor);
    Object shadow = make.newInstance(b, a, 2, new ItemStack(Items.emerald), null, null, null);
    Object prior = make.newInstance(a, b, 3, out, null, null, null);
    RegistryRecipes ordered =
        (RegistryRecipes) ctor.newInstance(handler, Arrays.asList(prior, shadow), jar);
    RecipeRow later =
        NativeCoreFixesTest.row(
            new ItemStack(Items.paper),
            new ItemStack(Items.dye, 1, 2),
            jar,
            new ItemStack(Items.emerald));
    ordered.capture(1, later);
    require(
        later.record.getAsJsonObject("process").getAsJsonArray("earlier").size() == 1,
        "Native priority context omitted overlapping earlier rule");
    write(
        "native-distillery-priority",
        later,
        Arrays.asList(
            new ItemStack(Items.paper),
            new ItemStack(Items.dye, 1, 2),
            jar,
            new ItemStack(Items.emerald)));
    ordered.close();
    write(
        "native-distillery",
        row,
        Arrays.asList(
            new ItemStack(Items.paper),
            new ItemStack(Items.dye, 1, 2),
            jar,
            out,
            new ItemStack(Items.gold_ingot, 2)));
    RecipeRow chinese =
        NativeCoreFixesTest.row(
            "zh_CN",
            new ItemStack(Items.paper),
            new ItemStack(Items.dye, 1, 2),
            jar,
            out,
            new ItemStack(Items.gold_ingot, 2));
    cursor.capture(0, chinese);
    write(
        "native-distillery-zh_CN",
        chinese,
        Arrays.asList(
            new ItemStack(Items.paper),
            new ItemStack(Items.dye, 1, 2),
            jar,
            out,
            new ItemStack(Items.gold_ingot, 2)));
    require(
        row.record.get("id").equals(chinese.record.get("id")),
        "Distillery raw identity changed by locale");
    boolean subtypes = Items.paper.getHasSubtypes();
    try {
      Items.paper.setHasSubtypes(!subtypes);
      try {
        cursor.finishVerification();
        throw new AssertionError("Metadata predicate drift accepted");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        require(expected.code.equals("environment_changed"), "Wrong predicate drift failure");
      }
    } finally {
      Items.paper.setHasSubtypes(subtypes);
    }
    out.stackSize = 4;
    try {
      cursor.finishVerification();
      throw new AssertionError("Registry drift accepted");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(expected.code.equals("environment_changed"), "Wrong drift failure");
    }
    System.out.println(
        "Native distillery: swapped inputs, NBT, unit consumption, jars, sparse outputs, work duration and drift passed");
  }

  private static void write(String name, RecipeRow row, Collection<ItemStack> specimens)
      throws Exception {
    row.record.add(
        "source",
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner",
            "WitcheryExtras",
            "handler",
            "alkalus.main.nei.NEI_Handler_Distillery",
            "key",
            name.endsWith("-zh_CN") ? name.substring(0, name.length() - 6) : name));
    row.record.addProperty(
        "category",
        com.github.dcysteine.nesql.exporter.source.Identity.content(
            "category", row.record.getAsJsonObject("source")));
    NativeContractFixture.writeOwned(name, row, specimens);
  }

  private static void nativeEdgeCases(
      TemplateRecipeHandler handler, Class<?> type, Constructor<?> make, ItemStack jar)
      throws Exception {
    ItemStack paper = new ItemStack(Items.paper), diamond = new ItemStack(Items.diamond, 2);
    for (int signed : new int[] {0, -2}) {
      Object recipe =
          make.newInstance(paper, null, signed, signed == 0 ? diamond : null, null, null, null);
      try (DistilleryRecipes reader =
          new DistilleryRecipes(handler, Collections.singletonList(recipe), jar)) {
        RecipeRow row = NativeCoreFixesTest.row(paper, diamond, jar);
        reader.capture(0, row);
        require(row.inputs.size() == (signed == 0 ? 1 : 2), "Optional native jar input changed");
        require(
            row.outputs.size() == (signed == 0 ? 1 : 0),
            "Native sink/empty second reagent changed");
        write(
            signed == 0 ? "native-distillery-empty-slot" : "native-distillery-signed-jars",
            row,
            Arrays.asList(paper, diamond, jar));
        reader.finishVerification();
      }
      if (signed < 0) {
        Class<?> registry = Class.forName("com.emoniph.witchery.crafting.DistilleryRecipes");
        Object manager = registry.getMethod("instance").invoke(null);
        Field recipes = registry.getDeclaredField("recipes");
        recipes.setAccessible(true);
        Object previous = recipes.get(manager);
        try {
          recipes.set(manager, new ArrayList<>(Collections.singletonList(recipe)));
          Class<?> tile =
              Class.forName("com.emoniph.witchery.blocks.BlockDistillery$TileEntityDistillery");
          Object machine = tile.newInstance();
          Field inventory = tile.getDeclaredField("furnaceItemStacks");
          inventory.setAccessible(true);
          ItemStack[] slots = (ItemStack[]) inventory.get(machine);
          slots[0] = paper.copy();
          slots[2] = new ItemStack(Items.diamond);
          tile.getMethod("smeltItem").invoke(machine);
          require(
              slots[0] == null && slots[2].getItem() == Items.diamond && slots[2].stackSize == 3,
              "Native signed jars/count-only matcher or sink debit changed");
        } finally {
          recipes.set(manager, previous);
        }
      }
    }
    for (int count : new int[] {0, -2}) {
      ItemStack prototype = new ItemStack(Items.diamond, count);
      Object nativeRecipe = make.newInstance(paper, null, 0, prototype, null, null, null);
      try (DistilleryRecipes reader =
          new DistilleryRecipes(handler, Collections.singletonList(nativeRecipe), jar)) {
        RecipeRow row = NativeCoreFixesTest.row(paper, prototype, jar);
        reader.capture(0, row);
        require(
            row.outputs.size() == 0
                && row.record
                    .getAsJsonObject("process")
                    .getAsJsonArray("outputs")
                    .get(0)
                    .getAsJsonObject()
                    .get("amount")
                    .getAsString()
                    .equals(Integer.toString(count)),
            "Nonpositive inventory prototype became a positive yield or disappeared");
        write(
            count == 0 ? "native-distillery-zero-output" : "native-distillery-negative-output",
            row,
            Arrays.asList(paper, prototype, jar));
      }
      Class<?> registry = Class.forName("com.emoniph.witchery.crafting.DistilleryRecipes");
      Object manager = registry.getMethod("instance").invoke(null);
      Field recipes = registry.getDeclaredField("recipes");
      recipes.setAccessible(true);
      Object previous = recipes.get(manager);
      try {
        recipes.set(manager, new ArrayList<>(Collections.singletonList(nativeRecipe)));
        Class<?> tile =
            Class.forName("com.emoniph.witchery.blocks.BlockDistillery$TileEntityDistillery");
        Object machine = tile.newInstance();
        Field inventory = tile.getDeclaredField("furnaceItemStacks");
        inventory.setAccessible(true);
        ItemStack[] slots = (ItemStack[]) inventory.get(machine);
        slots[0] = paper.copy();
        tile.getMethod("smeltItem").invoke(machine);
        require(
            slots[0] == null && slots[3] != null && slots[3].stackSize == count,
            "Native nonpositive output inventory copy changed");
      } finally {
        recipes.set(manager, previous);
      }
    }
    Object unreachable = make.newInstance(null, null, 0, diamond, null, null, null);
    try (DistilleryRecipes reader =
        new DistilleryRecipes(handler, Collections.singletonList(unreachable), jar)) {
      RecipeRow row = NativeCoreFixesTest.row(jar, diamond);
      require(
          !reader.capture(0, row) && row.exclusion != null,
          "All-empty native input lost its explicit reachability proof");
    }
    UnknownList list = new UnknownList();
    list.add(unreachable);
    try {
      new DistillerySource(list, jar);
      throw new AssertionError("Unknown distillery storage accepted");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(list.calls == 0, "Unknown distillery iterator executed");
    }
    UnknownMetadata item = new UnknownMetadata();
    Object unknown = make.newInstance(new ItemStack(item), null, 0, diamond, null, null, null);
    try {
      new DistillerySource(Collections.singletonList(unknown), jar);
      throw new AssertionError("Unknown distillery metadata accepted");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(item.calls == 0, "Unknown distillery metadata callback executed");
    }
    System.out.println(
        "Native distillery optional/empty/sink/signed jar rules and unknown callbacks0 passed");
  }

  public static final class UnknownList extends ArrayList<Object> {
    int calls;

    @Override
    public Iterator<Object> iterator() {
      calls++;
      throw new AssertionError("Foreign distillery iterator");
    }
  }

  public static final class UnknownMetadata extends net.minecraft.item.Item {
    int calls;

    @Override
    public int getDamage(ItemStack stack) {
      calls++;
      throw new AssertionError("Foreign distillery metadata");
    }
  }

  private static void boundaries(
      TemplateRecipeHandler handler, Object recipe, ItemStack jar, Constructor<?> make)
      throws Exception {
    try (RegistryRecipes reader =
        (RegistryRecipes) make.newInstance(handler, Collections.singletonList(recipe), jar)) {
      Field input = recipe.getClass().getDeclaredField("inputs");
      input.setAccessible(true);
      ItemStack[] previous = (ItemStack[]) input.get(recipe);
      try {
        input.set(recipe, previous.clone());
        changed(reader);
      } finally {
        input.set(recipe, previous);
      }
      ItemStack item = previous[0];
      try {
        previous[0] = item.copy();
        changed(reader);
      } finally {
        previous[0] = item;
      }
      reader.finishVerification();
    }
    NativeCoreFixesTest.version("witchery", "0.24.1");
    NativeCoreFixesTest.version("WitcheryExtras", "1.3.8");
    Class<?> type = Class.forName("com.emoniph.witchery.crafting.DistilleryRecipes");
    Object manager = type.getMethod("instance").invoke(null);
    Field table = type.getDeclaredField("recipes");
    table.setAccessible(true);
    Object previous = table.get(manager);
    try {
      table.set(manager, new ArrayList<>());
      try (DistilleryRecipes reader = new DistilleryRecipes(handler)) {
        require(reader.size() == 0, "Ready-empty distillery requires absent jar role");
        reader.finishVerification();
        Object borrowed = table.get(manager);
        try {
          table.set(manager, new ArrayList<>());
          changed(reader);
        } finally {
          table.set(manager, borrowed);
        }
        RegistrationBindings first = RegistrationBindings.read(handler);
        for (int source = 0; source < 4; source++)
          require(
              first.same(
                  RegistrationBindings.read(
                      (TemplateRecipeHandler) handler.getClass().newInstance())),
              "Distillery discovery sources differ");
        try (Recipes.Cursor production =
            new Recipes.Handler(handler, 0).open(NativeCoreFixesTest.row().facts)) {
          require(
              MagicApi.field(production, "handler") == null, "Distillery production retains GUI");
          production.finishVerification();
        }
      }
      table.set(manager, null);
      try {
        new DistilleryRecipes(handler);
        throw new AssertionError("Missing distillery source accepted");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure) {
        require(failure.code.equals("handler_not_ready"), "Wrong distillery readiness fault");
      }
    } finally {
      table.set(manager, previous);
    }
    productionJar(handler, manager, table, recipe);
    System.out.println(
        "Distillery source/array/stack identities, ready-empty/missing and four-source binding passed");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void productionJar(
      TemplateRecipeHandler handler, Object manager, Field table, Object recipe) throws Exception {
    Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    sun.misc.Unsafe memory = (sun.misc.Unsafe) access.get(null);
    Class<?> witchery = Class.forName("com.emoniph.witchery.Witchery");
    Field itemRole = witchery.getDeclaredField("Items");
    Object oldItems = itemRole.get(null), oldTable = table.get(manager);
    Object registry = net.minecraft.item.Item.itemRegistry;
    Field names = net.minecraft.util.RegistrySimple.class.getDeclaredField("registryObjects"),
        inverse = net.minecraft.util.RegistryNamespaced.class.getDeclaredField("field_148758_b"),
        numeric =
            net.minecraft.util.RegistryNamespaced.class.getDeclaredField("underlyingIntegerMap");
    names.setAccessible(true);
    inverse.setAccessible(true);
    numeric.setAccessible(true);
    Object[] old = {names.get(registry), inverse.get(registry), numeric.get(registry)};
    Object data = MagicApi.field(cpw.mods.fml.common.registry.GameData.class, null, "mainData");
    Field availability = data.getClass().getDeclaredField("availabilityMap");
    availability.setAccessible(true);
    Object oldAvailability = availability.get(data);
    com.google.common.collect.BiMap map = com.google.common.collect.HashBiMap.create((Map) old[0]);
    net.minecraft.util.ObjectIntIdentityMap ids = new net.minecraft.util.ObjectIntIdentityMap();
    for (Object item : map.values())
      ids.func_148746_a(item, net.minecraft.item.Item.itemRegistry.getIDForObject(item));
    try {
      memory.putObject(registry, memory.objectFieldOffset(names), map);
      memory.putObject(registry, memory.objectFieldOffset(inverse), map.inverse());
      memory.putObject(registry, memory.objectFieldOffset(numeric), ids);
      memory.putObject(
          data, memory.objectFieldOffset(availability), ((BitSet) oldAvailability).clone());
      Object items = memory.allocateInstance(Class.forName("com.emoniph.witchery.WitcheryItems"));
      net.minecraft.item.Item generic =
          (net.minecraft.item.Item)
              memory.allocateInstance(Class.forName("com.emoniph.witchery.item.ItemGeneral"));
      net.minecraft.item.Item defaults = new net.minecraft.item.Item().setHasSubtypes(true);
      for (Field member : net.minecraft.item.Item.class.getDeclaredFields())
        if (!Modifier.isStatic(member.getModifiers())) {
          member.setAccessible(true);
          member.set(generic, member.get(defaults));
        }
      Field delegate = net.minecraft.item.Item.class.getDeclaredField("delegate");
      delegate.setAccessible(true);
      delegate.set(
          generic,
          new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(
              generic, net.minecraft.item.Item.class));
      Object jarRole =
          Class.forName("com.emoniph.witchery.item.ItemGeneral$SubItem")
              .getConstructor(int.class, String.class)
              .newInstance(0, "ownedJarWitness");
      Field emptyJar = generic.getClass().getDeclaredField("itemEmptyClayJar");
      emptyJar.setAccessible(true);
      emptyJar.set(generic, jarRole);
      net.minecraft.item.Item.itemRegistry.addObject(31991, "witchery:distillery_fixture", generic);
      Field general = items.getClass().getDeclaredField("GENERIC");
      general.setAccessible(true);
      general.set(items, generic);
      itemRole.set(null, items);
      table.set(manager, new ArrayList<>(Collections.singletonList(recipe)));
      int metadata =
          (Integer) MagicApi.field(MagicApi.field(generic, "itemEmptyClayJar"), "damageValue");
      ItemStack jar = new ItemStack(generic, 1, metadata);
      ItemStack[] known = {
        new ItemStack(Items.paper),
        new ItemStack(Items.dye, 1, 2),
        jar,
        new ItemStack(Items.diamond, 3),
        new ItemStack(Items.gold_ingot, 2)
      };
      try (DistilleryRecipes reader = new DistilleryRecipes(handler)) {
        RecipeRow row = NativeCoreFixesTest.row(known);
        reader.capture(0, row);
        write("native-distillery-production-jar", row, Arrays.asList(known));
        reader.finishVerification();
      }
      try (Recipes.Cursor production =
          new Recipes.Handler(handler, 0).open(NativeCoreFixesTest.row(known).facts)) {
        require(
            MagicApi.field(production, "handler") == null,
            "Nonempty distillery production retained GUI");
        production.capture(0);
        production.finishVerification();
      }
    } finally {
      table.set(manager, oldTable);
      itemRole.set(null, oldItems);
      memory.putObject(data, memory.objectFieldOffset(availability), oldAvailability);
      memory.putObject(registry, memory.objectFieldOffset(names), old[0]);
      memory.putObject(registry, memory.objectFieldOffset(inverse), old[1]);
      memory.putObject(registry, memory.objectFieldOffset(numeric), old[2]);
      require(
          names.get(registry) == old[0]
              && inverse.get(registry) == old[1]
              && numeric.get(registry) == old[2]
              && availability.get(data) == oldAvailability
              && itemRole.get(null) == oldItems
              && table.get(manager) == oldTable,
          "Distillery fixture did not return borrowed globals");
    }
    System.out.println(
        "Actual distillery nonempty production jar source/cursor and owned Forge roots passed");
  }

  private static void changed(RegistryRecipes reader) {
    try {
      reader.finishVerification();
      throw new AssertionError("Distillery same-content source replacement accepted");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure) {
      require(failure.code.equals("environment_changed"), "Wrong distillery source drift");
    }
  }

  @SuppressWarnings("unchecked")
  private static void nativeOutputStates(Object recipe, ItemStack a, ItemStack b) throws Exception {
    Class<?> registry = Class.forName("com.emoniph.witchery.crafting.DistilleryRecipes");
    Object manager = registry.getMethod("instance").invoke(null);
    Field source = registry.getDeclaredField("recipes");
    source.setAccessible(true);
    Object previous = source.get(manager);
    try {
      source.set(manager, new ArrayList<>(Collections.singletonList(recipe)));
      Class<?> tileType =
          Class.forName("com.emoniph.witchery.blocks.BlockDistillery$TileEntityDistillery");
      Object tile = tileType.newInstance();
      Field inventory = tileType.getDeclaredField("furnaceItemStacks");
      inventory.setAccessible(true);
      ItemStack[] stacks = (ItemStack[]) inventory.get(tile);
      stacks[0] = a.copy();
      stacks[1] = b.copy();
      stacks[2] = new ItemStack(Items.glass_bottle, 4);
      stacks[3] = new ItemStack(Items.emerald, 5);
      tileType.getMethod("smeltItem").invoke(tile);
      require(
          stacks[3].getItem() == Items.emerald && stacks[3].stackSize == 5,
          "Native distillery mismatch output unexpectedly replaced");
      require(
          stacks[0].stackSize == 18 && stacks[1].stackSize == 6 && stacks[2].stackSize == 2,
          "Native distillery mismatch output did not debit reagents/jars");
      require(
          stacks[5].getItem() == Items.gold_ingot && stacks[5].stackSize == 2,
          "Independent sparse native output disappeared");
      stacks[3] = new ItemStack(Items.diamond, 1);
      stacks[3].setTagInfo("retained", new net.minecraft.nbt.NBTTagInt(7));
      tileType.getMethod("smeltItem").invoke(tile);
      require(
          stacks[3].stackSize == 4 && stacks[3].getTagCompound().getInteger("retained") == 7,
          "Native distillery merge lost existing output NBT");
      stacks[2] = new ItemStack(Items.glass_bottle, 4);
      stacks[3].stackSize = 64;
      int before = stacks[0].stackSize;
      tileType.getMethod("smeltItem").invoke(tile);
      require(
          stacks[0].stackSize == before && stacks[2].stackSize == 4,
          "Capacity rejection consumed distillery inputs");
    } finally {
      source.set(manager, previous);
    }
    System.out.println(
        "Actual distillery: incompatible output skips placement but still debits inputs/jars; matching output keeps existing NBT");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
