package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

/**
 * Native CreativeCore matching, cache layout and durable facts, including guarded greedy matching.
 */
public final class NativeCreativeCoreTest {
  private static final String ROOT = "com.creativemd.creativecore.";

  public static void main(String[] args) throws Exception {
    NativeGendustryFluidsTest.main(new String[] {"creativecore"});
  }

  static void run() throws Exception {
    try (NativeScope scope = new NativeScope()) {
      runOwned();
    }
  }

  private static void runOwned() throws Exception {
    NativeCoreFixesTest.version("creativecore", "1.5.14-GTNH");
    ItemStack exact = new ItemStack(Items.dye, 11, 32767);
    exact.setTagInfo("typed", new net.minecraft.nbt.NBTTagShort((short) 5));
    ItemStack output = new ItemStack(Items.diamond, 3);
    output.setTagInfo("result", new net.minecraft.nbt.NBTTagByte((byte) 7));
    Object paper = info("StackInfoItem", new Class<?>[] {Item.class, int.class}, Items.paper, 0);
    Object dye =
        info(
            "StackInfoItemStack",
            new Class<?>[] {ItemStack.class, boolean.class, int.class},
            exact,
            true,
            1);
    Object bucket =
        info("StackInfoItem", new Class<?>[] {Item.class, int.class}, Items.water_bucket, 1);
    IRecipe recipe = shaped(2, new Object[] {paper, dye, null, bucket}, output);
    InventoryCrafting inventory = inventory();
    inventory.setInventorySlotContents(0, new ItemStack(Items.paper, 1, 7));
    inventory.setInventorySlotContents(1, exact.copy());
    inventory.setInventorySlotContents(4, new ItemStack(Items.water_bucket));
    require(
        recipe.matches(inventory, null),
        "Native shaped recipe rejected wildcard item and exact typed NBT");
    inventory.getStackInSlot(1).setItemDamage(0);
    require(
        !recipe.matches(inventory, null),
        "Native StackInfoItemStack treated metadata 32767 as wildcard");
    inventory.setInventorySlotContents(1, exact.copy());
    inventory.getStackInSlot(1).setTagInfo("typed", new net.minecraft.nbt.NBTTagInt(5));
    require(!recipe.matches(inventory, null), "Native typed NBT did not distinguish short and int");
    Class<?> adapter;
    try {
      adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.CreativeCoreRecipes");
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("Missing native CreativeCore recipe adapter", missing);
    }
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler) Class.forName(ROOT + "api.nei.NEIRecipeInfoHandler").newInstance();
    Method capture =
        adapter.getDeclaredMethod(
            "capture", IRecipe.class, TemplateRecipeHandler.class, RecipeRow.class);
    capture.setAccessible(true);
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
            exact,
            new ItemStack(Items.water_bucket),
            new ItemStack(Items.bucket),
            output);
    require(
        (Boolean) capture.invoke(null, recipe, handler, row),
        "Valid native shaped recipe excluded");
    require(
        handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
        "CreativeCore capture replaced borrowed GUI cache");
    JsonObject a = choice(row, 0), b = choice(row, 1), c = choice(row, 2);
    require(
        a.get("amount").getAsString().equals("1")
            && a.getAsJsonObject("consume").get("kind").getAsString().equals("consume"),
        "Native zero matcher threshold became a free/catalyst input");
    require(
        a.getAsJsonObject("rule").get("kind").getAsString().equals("creativeCore")
            && b.getAsJsonObject("rule").get("input").getAsInt() == 1
            && row.record
                    .getAsJsonObject("process")
                    .getAsJsonArray("predicates")
                    .get(1)
                    .getAsJsonObject()
                    .get("metadata")
                    .getAsInt()
                == 32767,
        "Item wildcard or exact typed predicate changed");
    require(
        c.getAsJsonArray("returns").size() == 1
            && c.getAsJsonArray("returns")
                .get(0)
                .getAsJsonObject()
                .get("id")
                .getAsString()
                .equals(Identity.item("minecraft:bucket", 0, null)),
        "Bucket return missing");
    require(
        row.record.getAsJsonObject("grid").get("mirror").getAsBoolean()
            && row.record.getAsJsonObject("grid").getAsJsonArray("cells").get(2).isJsonNull(),
        "Native mirror/grid hole changed");
    require(
        row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3"),
        "Output count changed");
    require(handler.arecipes.get(0) == borrowed, "CreativeCore retained another page cache");
    require(
        recipe.getRecipeOutput().stackSize == 3 && exact.stackSize == 11,
        "Owned native view mutated source");
    row.finish();
    write(
        "creativecore-owned-shape",
        row,
        Arrays.asList(
            new ItemStack(Items.paper),
            exact,
            new ItemStack(Items.water_bucket),
            new ItemStack(Items.bucket),
            output));
    java.nio.file.Path file = java.nio.file.Files.createTempFile("creativecore-facts-", ".json");
    try {
      java.nio.file.Files.write(file, CanonicalJson.bytes(row.record));
      require(
          new com.google.gson.JsonParser()
                  .parse(
                      new String(
                          java.nio.file.Files.readAllBytes(file),
                          java.nio.charset.StandardCharsets.UTF_8))
                  .getAsJsonObject()
                  .getAsJsonArray("inputs")
                  .size()
              == 3,
          "Durable facts lost native inputs");
    } finally {
      java.nio.file.Files.delete(file);
    }
    Object many = info("StackInfoItem", new Class<?>[] {Item.class, int.class}, Items.paper, 3);
    IRecipe thresholdRecipe = shaped(1, new Object[] {many}, output);
    InventoryCrafting thresholdInventory = inventory();
    thresholdInventory.setInventorySlotContents(0, new ItemStack(Items.paper, 2));
    require(!thresholdRecipe.matches(thresholdInventory, null), "Native minimum ignored");
    thresholdInventory.getStackInSlot(0).stackSize = 3;
    require(thresholdRecipe.matches(thresholdInventory, null), "Native minimum is not a threshold");
    matching(thresholdRecipe, thresholdInventory);
    thresholdInventory.getStackInSlot(0).stackSize = 2;
    matching(thresholdRecipe, thresholdInventory);
    nativeRemovalEquals(output, handler);
    matchingMatrix(output);
    RecipeRow minimumRow = NativeCoreFixesTest.row(new ItemStack(Items.paper), output);
    require(
        (Boolean) capture.invoke(null, shaped(1, new Object[] {many}, output), handler, minimumRow),
        "CreativeCore stock threshold remains rejected");
    require(
        minimumRow
                    .record
                    .getAsJsonObject("process")
                    .getAsJsonArray("predicates")
                    .get(0)
                    .getAsJsonObject()
                    .get("minimum")
                    .getAsInt()
                == 3
            && choice(minimumRow, 0).get("amount").getAsString().equals("1"),
        "Stock threshold became three consumed items");
    write(
        "creativecore-owned-stock", minimumRow, Arrays.asList(new ItemStack(Items.paper), output));
    RecipeRow translated = NativeCoreFixesTest.row("zh_CN", new ItemStack(Items.paper), output);
    CreativeCoreRecipes.capture(shaped(1, new Object[] {many}, output), handler, translated);
    write(
        "creativecore-owned-stock-zh_CN",
        translated,
        Arrays.asList(new ItemStack(Items.paper), output));
    require(
        translated.record.get("id").equals(minimumRow.record.get("id")),
        "CreativeCore locale changed semantic recipe identity");
    Object paper2 =
        info(
            "StackInfoItemStack",
            new Class<?>[] {ItemStack.class, boolean.class, int.class},
            new ItemStack(Items.paper, 1, 2),
            false,
            1);
    IRecipe greedy = shapeless(new Object[] {paper, paper2}, output);
    inventory = inventory();
    inventory.setInventorySlotContents(0, new ItemStack(Items.paper, 1, 2));
    inventory.setInventorySlotContents(1, new ItemStack(Items.paper, 1, 4));
    require(
        !greedy.matches(inventory, null),
        "Native greedy overlap fixture should fail in this offered order");
    inventory.setInventorySlotContents(0, new ItemStack(Items.paper, 1, 4));
    inventory.setInventorySlotContents(1, new ItemStack(Items.paper, 1, 2));
    require(
        greedy.matches(inventory, null),
        "Native greedy overlap fixture should accept reversed offered order");
    matching(greedy, inventory);
    RecipeRow greedyRow =
        NativeCoreFixesTest.row(
            new ItemStack(Items.paper), new ItemStack(Items.paper, 1, 2), output);
    require(
        (Boolean) capture.invoke(null, greedy, handler, greedyRow)
            && greedyRow
                .record
                .getAsJsonObject("process")
                .get("matching")
                .getAsString()
                .equals("rowMajorFirstMatchRemoveFirstEqual"),
        "Order-dependent CreativeCore overlap lacks its native matching program");
    write(
        "creativecore-owned-greedy",
        greedyRow,
        Arrays.asList(new ItemStack(Items.paper), new ItemStack(Items.paper, 1, 2), output));
    Object iron =
        info("StackInfoItem", new Class<?>[] {Item.class, int.class}, Items.iron_ingot, 1);
    Object gold =
        info("StackInfoItem", new Class<?>[] {Item.class, int.class}, Items.gold_ingot, 1);
    IRecipe simple = shapeless(new Object[] {paper, bucket, iron, gold}, output);
    RecipeRow simpleRow =
        NativeCoreFixesTest.row(
            new ItemStack(Items.paper),
            new ItemStack(Items.water_bucket),
            new ItemStack(Items.bucket),
            new ItemStack(Items.iron_ingot),
            new ItemStack(Items.gold_ingot),
            output);
    require(
        (Boolean) capture.invoke(null, simple, handler, simpleRow)
            && simpleRow.inputs.size() == 4
            && simpleRow.record.get("grid").isJsonNull(),
        "Disjoint shapeless facts lost fourth native ingredient");
    require(handler.arecipes.get(0) == borrowed, "Shapeless CreativeCore changed borrowed cache");
    net.minecraftforge.oredict.OreDictionary.registerOre(
        "nesqlCreativeInputs", new ItemStack(Items.apple, 7, 32767));
    net.minecraftforge.oredict.OreDictionary.registerOre(
        "nesqlCreativeInputs", new ItemStack(Items.stick, 2, 3));
    Object ore =
        info("StackInfoOre", new Class<?>[] {String.class, int.class}, "nesqlCreativeInputs", 1);
    IRecipe oreRecipe = shaped(1, new Object[] {ore}, output);
    inventory = inventory();
    inventory.setInventorySlotContents(0, new ItemStack(Items.apple, 1, 5));
    require(oreRecipe.matches(inventory, null), "Native ore wildcard fixture rejected");
    inventory.setInventorySlotContents(0, new ItemStack(Items.stick, 1, 4));
    require(
        !oreRecipe.matches(inventory, null),
        "Native exact ore metadata fixture accepted wrong metadata");
    matching(oreRecipe, inventory);
    inventory.setInventorySlotContents(0, new ItemStack(Items.apple, 1, 5));
    matching(oreRecipe, inventory);
    int oreId = net.minecraftforge.oredict.OreDictionary.getOreID("nesqlCreativeInputs");
    List<ItemStack> oreDisplay =
        (List<ItemStack>)
            ((List<?>)
                    MagicApi.field(
                        net.minecraftforge.oredict.OreDictionary.class, null, "idToStack"))
                .get(oreId);
    List<ItemStack> savedDisplay = new ArrayList<>(oreDisplay);
    try {
      oreDisplay.clear();
      require(
          oreRecipe.matches(inventory, null),
          "Native ore matching incorrectly follows display list");
      matching(oreRecipe, inventory);
      RecipeRow bakedRow =
          NativeCoreFixesTest.row(
              new ItemStack(Items.apple), new ItemStack(Items.stick, 1, 3), output);
      require(
          (Boolean) capture.invoke(null, oreRecipe, handler, bakedRow)
              && bakedRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() == 2,
          "CreativeCore capture still depends on ore display stacks instead of baked lookup");
    } finally {
      oreDisplay.addAll(savedDisplay);
    }
    RecipeRow oreRow =
        NativeCoreFixesTest.row(
            new ItemStack(Items.apple), new ItemStack(Items.stick, 1, 3), output);
    require(
        (Boolean) capture.invoke(null, oreRecipe, handler, oreRow)
            && oreRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() == 2,
        "Native ore alternatives lost");
    write(
        "creativecore-owned-ore",
        oreRow,
        Arrays.asList(new ItemStack(Items.apple), new ItemStack(Items.stick, 1, 3), output));
    Item unsafe =
        new Item() {
          @Override
          public boolean hasContainerItem(ItemStack stack) {
            throw new AssertionError("Unknown container callback executed");
          }
        };
    Item.itemRegistry.addObject(31889, "nesqltest:creative_unsafe", unsafe);
    Object unknown = info("StackInfoItem", new Class<?>[] {Item.class, int.class}, unsafe, 1);
    rejected(
        capture,
        shaped(1, new Object[] {unknown}, output),
        handler,
        NativeCoreFixesTest.row(new ItemStack(unsafe), output),
        "unknown native companion callback executed");
    net.minecraft.block.material.Material material =
        new net.minecraft.block.material.Material(net.minecraft.block.material.MapColor.airColor);
    net.minecraft.block.Block block = new net.minecraft.block.Block(material) {};
    Item blockItem = new net.minecraft.item.ItemBlock(block);
    net.minecraft.block.Block.blockRegistry.addObject(4090, "nesqltest:creative_block", block);
    Item.itemRegistry.addObject(31890, "nesqltest:creative_block", blockItem);
    for (String kind : new String[] {"StackInfoBlock", "StackInfoMaterial"}) {
      Object predicate =
          kind.equals("StackInfoBlock")
              ? info(kind, new Class<?>[] {net.minecraft.block.Block.class, int.class}, block, 1)
              : info(
                  kind,
                  new Class<?>[] {net.minecraft.block.material.Material.class, int.class},
                  material,
                  1);
      IRecipe blockRecipe = shaped(1, new Object[] {predicate}, output);
      inventory = inventory();
      inventory.setInventorySlotContents(0, new ItemStack(blockItem, 1, 7));
      require(
          blockRecipe.matches(inventory, null),
          "Native block/material predicate rejected metadata variant");
      RecipeRow blockRow = NativeCoreFixesTest.row(new ItemStack(blockItem), output);
      require(
          (Boolean) capture.invoke(null, blockRecipe, handler, blockRow)
              && blockRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() == 1
              && choice(blockRow, 0)
                  .getAsJsonObject("rule")
                  .get("kind")
                  .getAsString()
                  .equals("creativeCore"),
          "Native block/material alternatives changed");
      write(
          "creativecore-owned-" + kind.toLowerCase(Locale.ROOT),
          blockRow,
          Arrays.asList(new ItemStack(blockItem), output));
    }
    List registry = CraftingManager.getInstance().getRecipeList();
    registry.add(recipe);
    registry.add(oreRecipe);
    try {
      Constructor<?> constructor = adapter.getDeclaredConstructor(TemplateRecipeHandler.class);
      constructor.setAccessible(true);
      RegistryRecipes cursor = (RegistryRecipes) constructor.newInstance(handler);
      require(cursor.size() == 2, "Live CraftingManager interface discovery lost recipe");
      cursor.finishVerification();
      Map<Integer, List<Integer>> baked =
          (Map<Integer, List<Integer>>)
              MagicApi.field(net.minecraftforge.oredict.OreDictionary.class, null, "stackToId");
      int bakedKey = Item.getIdFromItem(Items.apple);
      List<Integer> previousIds = baked.get(bakedKey);
      try {
        baked.put(bakedKey, new ArrayList<>(previousIds));
        sourceChanged(cursor);
      } finally {
        baked.put(bakedKey, previousIds);
      }
      Object priorContainer = MagicApi.field(Item.class, Items.paper, "containerItem");
      try {
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
            Item.class, Items.paper, Items.bucket, "containerItem");
        sourceChanged(cursor);
      } finally {
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(
            Item.class, Items.paper, priorContainer, "containerItem");
      }
      Field nativeInfo = recipe.getClass().getDeclaredField("info");
      nativeInfo.setAccessible(true);
      Object rawInfo = nativeInfo.get(recipe);
      try {
        nativeInfo.set(recipe, ((Object[]) rawInfo).clone());
        sourceChanged(cursor);
      } finally {
        nativeInfo.set(recipe, rawInfo);
      }
      Object firstPredicate = ((Object[]) rawInfo)[0];
      try {
        ((Object[]) rawInfo)[0] =
            info("StackInfoItem", new Class<?>[] {Item.class, int.class}, Items.paper, 0);
        sourceChanged(cursor);
      } finally {
        ((Object[]) rawInfo)[0] = firstPredicate;
      }
      Object oldNBT = exact.getTagCompound().getTag("typed");
      try {
        exact.setTagInfo("typed", new net.minecraft.nbt.NBTTagShort((short) 5));
        sourceChanged(cursor);
      } finally {
        exact.setTagInfo("typed", (net.minecraft.nbt.NBTBase) oldNBT);
      }
      try (Recipes.Cursor production =
          new Recipes.Handler(handler, 0)
              .open(
                  NativeCoreFixesTest.row(
                          new ItemStack(Items.paper),
                          exact,
                          new ItemStack(Items.water_bucket),
                          new ItemStack(Items.bucket),
                          output)
                      .facts)) {
        production.capture(0);
        production.finishVerification();
        require(handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
            "Production CreativeCore capture replaced borrowed GUI cache");
      }
      RegistrationBindings binding = RegistrationBindings.read(handler);
      for (int source = 0; source < 4; source++)
        require(
            binding.same(
                RegistrationBindings.read(
                    (TemplateRecipeHandler) handler.getClass().newInstance())),
            "CreativeCore discovery sources do not bind same native table");
      output.stackSize = 4;
      try {
        cursor.finishVerification();
        throw new AssertionError("Registry result drift accepted");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        require(expected.code.equals("environment_changed"), "Wrong registry drift fault");
      }
      cursor.close();
      cursor.close();
      try {
        cursor.size();
        throw new AssertionError("Closed CreativeCore exposes rows");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure) {
        require(failure.code.equals("capture_closed"), "Wrong CreativeCore close code");
      }
      registry.clear();
      try (RegistryRecipes empty = (RegistryRecipes) constructor.newInstance(handler)) {
        require(empty.size() == 0, "Ready empty CreativeCore table invented recipes");
        empty.finishVerification();
      }
      registry.add(recipe);
    } finally {
      registry.remove(recipe);
      output.stackSize = 3;
    }
    System.out.println(
        "Native CreativeCore: GUI-free owned samples, item/exact predicates, containers, shape/mirror and guarded count/greedy branches passed; complete semantic/source audit pending");
  }

  private static Object info(String suffix, Class<?>[] types, Object... args) throws Exception {
    return Class.forName(ROOT + "common.utils.stack." + suffix)
        .getConstructor(types)
        .newInstance(args);
  }

  private static void write(String name, RecipeRow row, Collection<ItemStack> specimens)
      throws Exception {
    row.record.add(
        "source",
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner",
            "creativecore",
            "handler",
            ROOT + "api.nei.NEIRecipeInfoHandler",
            "key",
            name.endsWith("-zh_CN") ? name.substring(0, name.length() - 6) : name));
    row.record.addProperty(
        "category", Identity.content("category", row.record.getAsJsonObject("source")));
    NativeContractFixture.writeOwned(name, row, specimens);
  }

  private static void nativeRemovalEquals(ItemStack output, TemplateRecipeHandler handler)
      throws Exception {
    ItemStack a = new ItemStack(Items.paper);
    a.setTagInfo("marker", new net.minecraft.nbt.NBTTagString("a"));
    ItemStack b = new ItemStack(Items.paper);
    b.setTagInfo("marker", new net.minecraft.nbt.NBTTagString("b"));
    Class<?>[] signature = {ItemStack.class, boolean.class, int.class};
    Object strict = info("StackInfoItemStack", signature, a, true, 1);
    Object loose = info("StackInfoItemStack", signature, a.copy(), false, 1);
    Object looseB = info("StackInfoItemStack", signature, b.copy(), false, 1);
    require(
        strict.equals(looseB) && !looseB.equals(strict), "Native equality should be asymmetric");
    ArrayList<Object> remaining = new ArrayList<>(Arrays.asList(strict, loose));
    remaining.remove(loose);
    require(
        remaining.size() == 1 && remaining.get(0) == loose,
        "ArrayList.remove did not remove the earlier equal predicate");
    InventoryCrafting offered = inventory();
    offered.setInventorySlotContents(0, b.copy());
    offered.setInventorySlotContents(1, b.copy());
    IRecipe recipe = shapeless(new Object[] {strict, loose}, output);
    require(
        recipe.matches(offered, null),
        "Native greedy removal must keep matching loose predicate after removing strict one");
    matching(recipe, offered);
    int[] removed = new CreativeCoreMatching(recipe).match(stacks(offered));
    require(
        removed[0] == 0 && removed[1] == 1,
        "Owned matching removed the selected predicate rather than first equal one");
    RecipeRow equalityRow = NativeCoreFixesTest.row(a, b, new ItemStack(Items.paper), output);
    CreativeCoreRecipes.capture(recipe, handler, equalityRow);
    write(
        "creativecore-owned-remove-equals",
        equalityRow,
        Arrays.asList(a, b, new ItemStack(Items.paper), output));
    IRecipe tail = shaped(2, new Object[] {strict, loose, strict}, output);
    offered.setInventorySlotContents(0, a.copy());
    matching(tail, offered);
    require(tail.matches(offered, null), "Native shaped tail should be ignored");
    RecipeRow tailRow = NativeCoreFixesTest.row(a, new ItemStack(Items.paper), output);
    CreativeCoreRecipes.capture(tail, handler, tailRow);
    require(
        tailRow.inputs.size() == 2
            && tailRow.record.getAsJsonObject("process").getAsJsonArray("predicates").size() == 3,
        "Ignored tail was lost from source law or became an input");
    write("creativecore-owned-tail", tailRow, Arrays.asList(a, new ItemStack(Items.paper), output));
    IRecipe empty = shaped(1, new Object[0], output);
    matching(empty, inventory());
    require(empty.matches(inventory(), null), "Native zero-height shape should match empty grid");
    RecipeRow emptyRow = NativeCoreFixesTest.row(output);
    CreativeCoreRecipes.capture(empty, handler, emptyRow);
    require(
        emptyRow.inputs.size() == 0 && emptyRow.record.get("grid").isJsonNull(),
        "Native empty shape turned into a GUI error");
    write("creativecore-owned-empty", emptyRow, Arrays.asList(output));
    System.out.println(
        "CreativeCore native probe: equality can be asymmetric and remove an earlier predicate; two nonmatching strict-tag inputs still match");
  }

  private static ItemStack[] stacks(InventoryCrafting inventory) {
    ItemStack[] result = new ItemStack[9];
    for (int cell = 0; cell < 9; cell++) result[cell] = inventory.getStackInSlot(cell);
    return result;
  }

  private static void matchingMatrix(ItemStack output) throws Exception {
    ItemStack a = new ItemStack(Items.paper, 1, 2);
    a.setTagInfo("marker", new net.minecraft.nbt.NBTTagString("a"));
    ItemStack b = a.copy();
    b.setTagInfo("marker", new net.minecraft.nbt.NBTTagString("b"));
    Object item = info("StackInfoItem", new Class<?>[] {Item.class, int.class}, Items.paper, 0);
    Object exact =
        info(
            "StackInfoItemStack",
            new Class<?>[] {ItemStack.class, boolean.class, int.class},
            a,
            true,
            1);
    Object loose =
        info(
            "StackInfoItemStack",
            new Class<?>[] {ItemStack.class, boolean.class, int.class},
            a.copy(),
            false,
            1);
    ItemStack[] candidates = {
      null,
      new ItemStack(Items.paper, 0, 2),
      new ItemStack(Items.paper, 1, 2),
      new ItemStack(Items.paper, 3, 2),
      new ItemStack(Items.paper, 1, 4),
      a,
      b,
      new ItemStack(Items.apple)
    };
    int comparisons = 0;
    for (int minimum : new int[] {-2, 0, 1, 3, 65}) {
      Field count = item.getClass().getSuperclass().getDeclaredField("stackSize");
      count.setAccessible(true);
      count.setInt(item, minimum);
      for (IRecipe recipe :
          new IRecipe[] {
            shaped(2, new Object[] {item, exact, null, loose}, output),
            shaped(2, new Object[] {item, exact, loose}, output),
            shapeless(new Object[] {exact, loose, item}, output)
          }) {
        CreativeCoreMatching law = new CreativeCoreMatching(recipe);
        for (ItemStack first : candidates)
          for (ItemStack second : candidates)
            for (ItemStack third : candidates) {
              InventoryCrafting inv = inventory();
              inv.setInventorySlotContents(0, first);
              inv.setInventorySlotContents(1, second);
              inv.setInventorySlotContents(4, third);
              require(
                  recipe.matches(inv, null) == (law.match(stacks(inv)) != null),
                  "CreativeCore finite native comparison failed at "
                      + comparisons
                      + " minimum="
                      + minimum);
              comparisons++;
            }
      }
    }
    System.out.println(
        "CreativeCore matching: "
            + comparisons
            + " actual native comparisons passed, including stored signed thresholds, zero-count presence, holes, tails, typed NBT and greedy equality");
  }

  private static void matching(IRecipe recipe, InventoryCrafting offered) {
    require(
        recipe.matches(offered, null)
            == (new CreativeCoreMatching(recipe).match(stacks(offered)) != null),
        "Owned CreativeCore matching differs from installed native recipe");
  }

  private static void sourceChanged(RegistryRecipes reader) {
    try {
      reader.finishVerification();
      throw new AssertionError("Same-content CreativeCore native graph replacement accepted");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure) {
      require(failure.code.equals("environment_changed"), "Wrong CreativeCore drift code");
    }
  }

  private static IRecipe shaped(int width, Object[] inputs, ItemStack output) throws Exception {
    Class<?> info = Class.forName(ROOT + "common.utils.stack.StackInfo");
    Object array = Array.newInstance(info, inputs.length);
    for (int i = 0; i < inputs.length; i++) Array.set(array, i, inputs[i]);
    return (IRecipe)
        Class.forName(ROOT + "common.recipe.BetterShapedRecipe")
            .getConstructor(int.class, array.getClass(), ItemStack.class)
            .newInstance(width, array, output);
  }

  private static IRecipe shapeless(Object[] inputs, ItemStack output) throws Exception {
    return (IRecipe)
        Class.forName(ROOT + "common.recipe.entry.BetterShapelessRecipe")
            .getConstructor(ArrayList.class, ItemStack.class)
            .newInstance(new ArrayList<>(Arrays.asList(inputs)), output);
  }

  private static InventoryCrafting inventory() {
    return new InventoryCrafting(
        new net.minecraft.inventory.Container() {
          public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
            return true;
          }
        },
        3,
        3);
  }

  private static JsonObject choice(RecipeRow row, int i) {
    return row.inputs.get(i).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
  }

  private static void rejected(
      Method capture, IRecipe recipe, TemplateRecipeHandler handler, RecipeRow row, String message)
      throws Exception {
    try {
      capture.invoke(null, recipe, handler, row);
      throw new AssertionError(message);
    } catch (InvocationTargetException expected) {
      require(
          expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,
          "Wrong guarded failure: " + message);
    }
  }

  /** Every mutation in this offline fixture is scoped; keep registry root aliases intact. */
  private static final class NativeScope implements AutoCloseable {
    final List<Field> fields = new ArrayList<>();
    final List<Object> targets = new ArrayList<>(), originals = new ArrayList<>();
    final sun.misc.Unsafe memory;

    NativeScope() throws Exception {
      Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      access.setAccessible(true);
      memory = (sun.misc.Unsafe) access.get(null);
      try {
        registry(Item.itemRegistry);
        registry(net.minecraft.block.Block.blockRegistry);
        Class<?> data = cpw.mods.fml.common.registry.GameData.class;
        Object state = MagicApi.field(data, null, "mainData");
        replace(
            data,
            state,
            "availabilityMap",
            ((BitSet) MagicApi.field(state, "availabilityMap")).clone());
        for (String name :
            new String[] {"nameToId", "idToName", "idToStack", "idToStackUn", "stackToId"})
          replace(
              net.minecraftforge.oredict.OreDictionary.class,
              null,
              name,
              name.equals("nameToId") || name.equals("stackToId")
                  ? new HashMap<>()
                  : new ArrayList<>());
        Object loader = cpw.mods.fml.common.Loader.instance();
        replace(
            loader.getClass(),
            loader,
            "namedMods",
            new HashMap<>((Map<?, ?>) MagicApi.field(loader, "namedMods")));
        Object manager = CraftingManager.getInstance();
        replace(CraftingManager.class, manager, "recipes", new ArrayList<>());
      } catch (Exception | Error failure) {
        close();
        throw failure;
      }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void registry(net.minecraft.util.RegistryNamespaced registry) throws Exception {
      com.google.common.collect.BiMap map =
          com.google.common.collect.HashBiMap.create(
              (Map) MagicApi.field(registry, "registryObjects"));
      net.minecraft.util.ObjectIntIdentityMap ids = new net.minecraft.util.ObjectIntIdentityMap();
      for (Object value : map.values()) ids.func_148746_a(value, registry.getIDForObject(value));
      replace(net.minecraft.util.RegistrySimple.class, registry, "registryObjects", map);
      replace(
          net.minecraft.util.RegistryNamespaced.class, registry, "field_148758_b", map.inverse());
      replace(net.minecraft.util.RegistryNamespaced.class, registry, "underlyingIntegerMap", ids);
    }

    void replace(Class<?> owner, Object target, String name, Object value) throws Exception {
      Field f = owner.getDeclaredField(name);
      f.setAccessible(true);
      fields.add(f);
      targets.add(target);
      originals.add(f.get(target));
      put(f, target, value);
    }

    void put(Field f, Object target, Object value) {
      if (Modifier.isStatic(f.getModifiers()))
        memory.putObject(memory.staticFieldBase(f), memory.staticFieldOffset(f), value);
      else memory.putObject(target, memory.objectFieldOffset(f), value);
    }

    public void close() throws Exception {
      for (int index = fields.size() - 1; index >= 0; index--)
        put(fields.get(index), targets.get(index), originals.get(index));
      for (int index = 0; index < fields.size(); index++)
        require(
            fields.get(index).get(targets.get(index)) == originals.get(index),
            "CreativeCore fixture did not restore " + fields.get(index));
    }
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
