package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Catches lost rune refunds, missing dropped livingrock, and greedy native matching approximations.
 */
final class NativeBotaniaRunicTest {
  static void special() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeBotaniaSpecialTest.active(scope);
      Class<?> blocks = Class.forName("vazkii.botania.common.block.ModBlocks");
      scope.replace(blocks, null, "runeAltar", Blocks.gold_block);
      scope.replace(blocks, null, "livingrock", Blocks.stone);
      scope.replace(Class.forName("vazkii.botania.common.item.ModItems"), null, "rune", Items.feather);
      Class<?> headType = Class.forName("vazkii.botania.common.crafting.recipe.HeadRecipe");
      ItemStack tag = new ItemStack(Items.name_tag);
      ItemStack iron = new ItemStack(Items.iron_ingot);
      Object head = headType.getConstructor(ItemStack.class, int.class, Object[].class)
          .newInstance(new ItemStack(Items.diamond, 7), 100, new Object[] {tag, iron});
      scope.replace(headType, head, "name", "BoundPlayer");
      TemplateRecipeHandler handler = (TemplateRecipeHandler)
          Class.forName("vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar")
              .newInstance();
      ItemStack named = tag.copy(); named.setStackDisplayName("BoundPlayer");
      ItemStack output = new ItemStack(Items.skull, 1, 3);
      output.setTagInfo("SkullOwner", new net.minecraft.nbt.NBTTagString("BoundPlayer"));
      try (RegistryRecipes reader = BotaniaTestSource.open(handler,
          Collections.singletonList(head))) {
        RecipeRow row = NativeCoreFixesTest.row(tag, named, iron,
            new ItemStack(Blocks.stone), output);
        require(reader.size() == 1 && reader.capture(0, row),
            "NBT skull crafting-query branch was silently hidden");
        JsonObject process = row.record.getAsJsonObject("process");
        require(process.get("nativeRecipe").getAsString().equals(headType.getName())
                && process.has("head")
                && process.get("nameTagDefault").getAsString().equals("botania:headDefaultName")
                && row.record.getAsJsonArray("notes").get(0).getAsJsonObject()
                    .get("source").getAsString().equals(
                        "net.minecraft.util.StatCollector#translateToLocal(item.nameTag.name)"),
            "HeadRecipe lost its named-tag condition and dynamic SkullOwner output");
        require(row.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(id(output))
                && row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("1"),
            "HeadRecipe used the inherited prototype instead of a fresh named player skull");
        JsonObject origin = object("owner", "Botania", "handler", handler.getClass().getName(),
            "key", "head-special");
        row.record.add("source", origin);
        row.record.addProperty("category", Identity.origin("category", origin));
        NativeContractFixture.writeOwned("botania-head-special", row,
            Arrays.asList(tag, named, iron, new ItemStack(Blocks.stone), output));
        reader.finishVerification();
        require(MagicApi.field(head, "name").equals("BoundPlayer"),
            "HeadRecipe borrowed callback changed the global recipe's player name");
      }
      Object isolated = headType.getConstructor(ItemStack.class, int.class, Object[].class)
          .newInstance(new ItemStack(Items.apple, 19), 100, new Object[] {tag.copy(), iron.copy()});
      InventoryBasic nativeInventory = new InventoryBasic("isolated-head", true, 16);
      nativeInventory.setInventorySlotContents(0, named.copy());
      nativeInventory.setInventorySlotContents(1, iron.copy());
      require((Boolean) headType.getMethod("matches", IInventory.class)
              .invoke(isolated, nativeInventory)
              && id((ItemStack) headType.getMethod("getOutput").invoke(isolated)).equals(id(output)),
          "Owned HeadRecipe output differs from the pinned native callback");
      nativeInventory.setInventorySlotContents(0, tag.copy());
      require(!(Boolean) headType.getMethod("matches", IInventory.class)
              .invoke(isolated, nativeInventory),
          "Native HeadRecipe default-name rejection fixture changed");
      require(MagicApi.field(head, "name").equals("BoundPlayer"),
          "Owned HeadRecipe parity probe mutated the registered source");
      scope.replace(headType, head, "name", "");
      Object plain = Class.forName("vazkii.botania.api.recipe.RecipeRuneAltar")
          .getConstructor(ItemStack.class, int.class, Object[].class)
          .newInstance(new ItemStack(Items.diamond), 100, new Object[] {tag, iron});
      try (RegistryRecipes reader = BotaniaTestSource.open(handler, Arrays.asList(head, plain))) {
        RecipeRow hidden = NativeCoreFixesTest.row(tag, iron, new ItemStack(Blocks.stone));
        require(reader.size() == 2 && !reader.capture(0, hidden) && hidden.exclusion != null,
            "Hidden empty-name HeadRecipe lacks explicit native exclusion evidence");
        java.nio.file.Files.write(NativeContractFixture.directory()
                .resolve("botania-head-hidden-evidence.json"), CanonicalJson.bytes(hidden.exclusion));
        RecipeRow later = NativeCoreFixesTest.row(tag, iron, new ItemStack(Blocks.stone),
            new ItemStack(Items.diamond));
        require(reader.capture(1, later)
                && later.record.getAsJsonObject("process").getAsJsonArray("earlierHeads")
                    .get(0).getAsInt() == 0,
            "Hidden named-head condition was lost from later native priority");
        JsonObject origin = object("owner", "Botania", "handler", handler.getClass().getName(),
            "key", "head-priority");
        later.record.add("source", origin);
        later.record.addProperty("category", Identity.origin("category", origin));
        NativeContractFixture.writeOwned("botania-head-priority", later,
            Arrays.asList(tag, iron, new ItemStack(Blocks.stone), new ItemStack(Items.diamond)));
        reader.finishVerification();
      }
    }
  }

  static void run() throws Exception {
    Class<?> adapter;
    try {
      adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.BotaniaRunicRecipes");
    } catch (ClassNotFoundException missing) {
      throw new AssertionError(
          "Runic altar lacks native inventory, rune refund and livingrock semantics", missing);
    }
    Class<?> type = Class.forName("vazkii.botania.api.recipe.RecipeRuneAltar");
    Constructor<?> nativeCtor = type.getConstructor(ItemStack.class, int.class, Object[].class);
    Class<?> blocks = Class.forName("vazkii.botania.common.block.ModBlocks");
    blocks.getField("runeAltar").set(null, Blocks.gold_block);
    blocks.getField("livingrock").set(null, Blocks.stone);
    Class.forName("vazkii.botania.common.item.ModItems").getField("rune").set(null, Items.feather);
    primitiveOreIdentity(nativeCtor);
    ItemStack iron = new ItemStack(Items.iron_ingot, 19), gold = new ItemStack(Items.gold_ingot, 7);
    ItemStack rune = new ItemStack(Items.feather, 11, 4), rock = new ItemStack(Blocks.stone);
    rune.setTagInfo("templateOnly", new NBTTagInt(6));
    ItemStack output = new ItemStack(Items.diamond, 3);
    output.setTagInfo("native", new NBTTagInt(91));
    OreDictionary.registerOre("nesqlRunicMetal", iron);
    OreDictionary.registerOre("nesqlRunicMetal", gold);
    Object recipe =
        nativeCtor.newInstance(
            output, 7500, new Object[] {"nesqlRunicMetal", "nesqlRunicMetal", rune});
    List<Object> registry = new ArrayList<>(Collections.singletonList(recipe));
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler)
            Class.forName("vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar")
                .newInstance();
    RegistryRecipes capture = (RegistryRecipes) BotaniaTestSource.open(handler, registry);
    require(capture.size() == 1, "Runic altar registry enumeration changed");
    Map<String, ItemStack> known = new HashMap<>();
    for (ItemStack stack : Arrays.asList(iron, gold, rune, rock, output)) {
      known.put(id(stack), stack);
      ItemStack clean = stack.copy();
      clean.setTagCompound(null);
      known.put(id(clean), clean);
    }
    RecipeRow row = row(handler, known);
    require(capture.capture(0, row), "Reachable native rune recipe was omitted");
    require(handler.arecipes.isEmpty(), "Runic altar capture wrote the borrowed GUI cache");
    require(
        row.inputs.size() == 4 && row.outputs.size() == 1,
        "Runic altar lost completion stone or gained a consumed altar/wand");
    require(
        row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
            && row.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(id(output)),
        "Native rune output amount/NBT changed");
    require(
        row.record.has("process") && row.record.get("process").isJsonObject(),
        "Runic altar lacks typed greedy matching, priority and completion semantics");
    require(
        row.record.getAsJsonObject("process").get("mana").getAsInt() == 7500
            && row.record.getAsJsonObject("process").get("cooldown").getAsInt() == 60
            && row.properties.entrySet().isEmpty(),
        "Runic altar mana or post-craft cooldown disappeared");
    require(
        row.record.get("duration").isJsonNull(),
        "Mana accumulation or player trigger became a fixed craft duration");
    for (int index = 0; index < 4; index++)
      for (JsonElement raw : row.inputs.get(index).getAsJsonObject().getAsJsonArray("choices")) {
        JsonObject choice = raw.getAsJsonObject();
        require(
            choice.get("amount").getAsInt() == 1 && choice.getAsJsonArray("returns").size() == 0,
            "Runic inputs used template quantities or crafting container callbacks");
        require(
            choice
                .getAsJsonObject("consume")
                .get("kind")
                .getAsString()
                .equals(index == 2 ? "keep" : "consume"),
            "Runic native refund became consumed input");
        require(
            choice.getAsJsonObject("rule").get("nbt").getAsBoolean(),
            "Native rune matching stopped ignoring offered NBT");
      }
    JsonObject stone =
        row.inputs.get(3).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
    require(
        row.inputs.get(3).getAsJsonObject().get("slot").getAsInt() == 16
            && stone.getAsJsonObject("rule").get("meta").getAsBoolean(),
        "Dropped livingrock was narrowed to metadata zero or placed in altar inventory");
    Method nativeMatches = type.getMethod("matches", IInventory.class);
    for (ItemStack first : Arrays.asList(iron, gold))
      for (ItemStack second : Arrays.asList(iron, gold)) {
        ItemStack offeredRune = rune.copy();
        offeredRune.stackSize = 1;
        offeredRune.setTagInfo("offered", new NBTTagInt(42));
        ItemStack[] offered = {offeredRune, first.copy(), second.copy()};
        InventoryBasic inventory = new InventoryBasic("runic-test", true, 16);
        for (int i = 0; i < offered.length; i++) {
          offered[i].stackSize = 1;
          inventory.setInventorySlotContents(i, offered[i]);
        }
        require(
            (Boolean) nativeMatches.invoke(recipe, inventory),
            "Native packed/repeated requirement fixture rejected");
        require(
            allocation(row, offered, 0, 0, known),
            "Exported rune predicate differs from native greedy allocation");
        offered[2] = new ItemStack(Items.apple);
        inventory.setInventorySlotContents(2, offered[2]);
        require(
            !(Boolean) nativeMatches.invoke(recipe, inventory)
                && !allocation(row, offered, 0, 0, known),
            "Unmatched packed inventory was accepted");
      }
    capture.verify();
    String unknownOre = "nesqlRunicAbsentReadOnly";
    Map<?, ?> nativeNames = (Map<?, ?>) MagicApi.field(OreDictionary.class, null, "nameToId");
    require(!nativeNames.containsKey(unknownOre), "Missing runic ore test was already registered");
    try (RegistryRecipes absent =
        (RegistryRecipes)
            BotaniaTestSource.open(
                handler,
                Collections.singletonList(
                    nativeCtor.newInstance(output, 20, new Object[] {unknownOre})))) {
      require(
          !nativeNames.containsKey(unknownOre), "Runic altar read registered a missing ore name");
      RecipeRow empty = row(handler, known);
      require(
          !absent.capture(0, empty) && empty.exclusion != null,
          "Missing runic predicate lacks explicit exclusion evidence");
    }
    output.stackSize = 4;
    try {
      capture.verify();
      throw new AssertionError("Rune output drift was ignored");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("recipe_changed"), "Wrong rune drift fault");
    }
    output.stackSize = 3;
    Field outputField = type.getSuperclass().getDeclaredField("output");
    outputField.setAccessible(true);
    outputField.set(recipe, output.copy());
    try {
      capture.verify();
      throw new AssertionError("Equal-value runic output replacement retained source identity");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("recipe_changed"), "Wrong nested runic identity drift");
    } finally {
      outputField.set(recipe, output);
    }
    List<ItemStack> productionKnown = new ArrayList<>(Arrays.asList(iron, gold));
    productionKnown.addAll(known.values());
    NativeBotaniaPoolTest.production(
        handler, registry, null, productionKnown.toArray(new ItemStack[0]));
    Object partial = nativeCtor.newInstance(output, 100, new Object[] {"nesqlRunicMetal", iron});
    Object competing =
        nativeCtor.newInstance(
            new ItemStack(Items.apple),
            20,
            new Object[] {"nesqlRunicMetal", "nesqlRunicMetal", rune});
    Object hidden =
        nativeCtor.newInstance(
            new ItemStack(Items.skull),
            20,
            new Object[] {"nesqlRunicMetal", "nesqlRunicMetal", rune});
    greedyPriority(
        handler,
        Arrays.asList(partial, hidden, competing, recipe),
        new ItemStack[] {iron, gold, rune, new ItemStack(Items.apple)},
        nativeMatches,
        known);
    Object impossible = nativeCtor.newInstance(output, 0, new Object[] {iron});
    RegistryRecipes zero =
        (RegistryRecipes) BotaniaTestSource.open(handler, Collections.singletonList(impossible));
    require(
        !zero.capture(0, row(handler, known)),
        "Zero-mana rune recipe was claimed reachable despite native manaToGet > 0 gate");
    zero.close();
    try (RegistryRecipes empty = BotaniaTestSource.open(handler, new ArrayList<>())) {
      require(empty.size() == 0, "Ready empty rune registry was treated as unready");
      empty.finishVerification();
    }
    require(
        iron.stackSize == 19
            && rune.stackSize == 11
            && rune.getTagCompound().getInteger("templateOnly") == 6,
        "Runic capture mutated registry stacks");
    capture.close();
    capture.close();
    try {
      capture.verify();
      throw new AssertionError("Closed runic reader remained usable");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("capture_closed"), "Wrong closed runic fault");
    }
    row.finish();
    java.nio.file.Files.write(
        java.nio.file.Paths.get("build/native-tests/botania-runic-record.json"),
        CanonicalJson.bytes(row.record));
    System.out.println(
        "Native Botania runic: actual greedy matching, repeated equivalent groups, rune refunds, dropped livingrock, mana/cooldown, hidden-priority guards and drift passed");
  }

  private static void primitiveOreIdentity(Constructor<?> nativeCtor) throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      for (int i = 0; i < 256; i++) OreDictionary.getOreID("nesqlRunicUnused" + i);
      OreDictionary.registerOre("nesqlRunicHigh", new ItemStack(Items.iron_ingot));
      int id = OreDictionary.getOreID("nesqlRunicHigh");
      require(id > 255, "Runic fixture did not exercise an uncached ore ID");
      Class<?> owner = Class.forName("it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap");
      Object primitive = owner.getConstructor(Map.class).newInstance(
          MagicApi.field(OreDictionary.class, null, "nameToId"));
      owner.getMethod("defaultReturnValue", int.class).invoke(primitive, -1);
      scope.replace(OreDictionary.class, null, "nameToId", primitive);
      Map<?, ?> names = (Map<?, ?>) primitive;
      require(names.get("nesqlRunicHigh") != names.get("nesqlRunicHigh"),
          "Primitive native lookup unexpectedly reused a boxed identity");
      Object recipe = nativeCtor.newInstance(new ItemStack(Items.diamond), 100,
          new Object[] {"nesqlRunicHigh"});
      TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(
          "vazkii.botania.client.integration.nei.recipe.RecipeHandlerRunicAltar").newInstance();
      try (RegistryRecipes reader = BotaniaTestSource.open(handler, Collections.singletonList(recipe))) {
        reader.verify();
        require(reader.capture(0, NativeCoreFixesTest.row(new ItemStack(Items.iron_ingot),
            new ItemStack(Blocks.stone), new ItemStack(Items.diamond))),
            "Stable primitive ore ID blocked runic capture");
        reader.finishVerification();
        ((Map<String, Integer>) primitive).put("nesqlRunicHigh", id - 1);
        try { reader.verify(); throw new AssertionError("Changed native ore ID was accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), expected.toString()); }
      }
    }
    System.out.println("Runic primitive ore IDs: unchanged uncached value accepted and ID drift rejected");
  }

  private static void greedyPriority(
      TemplateRecipeHandler handler,
      List<?> registry,
      ItemStack[] offeredTypes,
      Method matches,
      Map<String, ItemStack> known)
      throws Exception {
    try (RegistryRecipes reader = (RegistryRecipes) BotaniaTestSource.open(handler, registry)) {
      require(reader.size() == 3, "Native skull visibility changed");
      List<ItemStack> specimens = new ArrayList<>(known.values());
      for (Object nativeRecipe : registry)
        specimens.add((ItemStack) MagicApi.field(nativeRecipe, "output"));
      Map<String, ItemStack> facts = new HashMap<>(known);
      for (ItemStack item : specimens) facts.put(id(item), item);
      for (int index = 0; index < reader.size(); index++) {
        RecipeRow captured = row(handler, facts);
        captured.record.getAsJsonObject("source").addProperty("key", "runic-greedy");
        captured.record.addProperty(
            "category", Identity.origin("category", captured.record.getAsJsonObject("source")));
        captured.record.addProperty("order", index);
        require(reader.capture(index, captured), "A conditional native row was silently omitted");
        JsonObject process = captured.record.getAsJsonObject("process");
        require(
            process.getAsJsonArray("earlier").size() == (index == 0 ? 0 : index + 1),
            "Hidden entries or native registry priority were omitted");
        NativeContractFixture.writeOwned("botania-runic-greedy-" + index, captured, specimens);
      }
      JsonArray probes = new JsonArray();
      for (int length = 0; length <= 4; length++) {
        int combinations = 1;
        for (int i = 0; i < length; i++) combinations *= offeredTypes.length;
        for (int encoded = 0; encoded < combinations; encoded++) {
          InventoryBasic inventory = new InventoryBasic("runic-greedy", true, 16);
          JsonArray offered = new JsonArray();
          int remainder = encoded;
          for (int cell = 0; cell < length; cell++) {
            ItemStack stack = offeredTypes[remainder % offeredTypes.length].copy();
            remainder /= offeredTypes.length;
            stack.stackSize = 1;
            stack.setTagInfo("ignored", new NBTTagInt(42));
            inventory.setInventorySlotContents(cell, stack);
            offered.add(
                object(
                    "registry",
                    Item.itemRegistry.getNameForObject(stack.getItem()),
                    "meta",
                    Items.feather.getDamage(stack)));
          }
          int winner = -1;
          for (int index = 0; index < registry.size(); index++)
            if ((Boolean) matches.invoke(registry.get(index), inventory)) {
              winner = index;
              break;
            }
          probes.add(object("offered", offered, "winner", winner));
        }
      }
      require(probes.size() == 341, "Greedy parity input coverage changed");
      java.nio.file.Files.write(
          java.nio.file.Paths.get("build/native-tests/botania-runic-greedy-parity.json"),
          CanonicalJson.bytes(probes));
    }
  }

  private static RecipeRow row(TemplateRecipeHandler handler, Map<String, ItemStack> known) {
    Facts facts = new Facts("en_US");
    @SuppressWarnings("unchecked")
    Set<String> ids = (Set<String>) MagicApi.field(facts, "items");
    ids.addAll(known.keySet());
    return new RecipeRow(
        facts,
        object("owner", "Botania", "handler", handler.getClass().getName(), "key", "runic"),
        "category_test",
        0);
  }

  private static boolean allocation(
      RecipeRow row, ItemStack[] offered, int at, int used, Map<String, ItemStack> known) {
    if (at == offered.length) return true;
    for (int index = 0; index < offered.length; index++)
      if ((used & 1 << index) == 0) {
        for (JsonElement raw : row.inputs.get(at).getAsJsonObject().getAsJsonArray("choices")) {
          JsonObject choice = raw.getAsJsonObject();
          if (NativeRailTest.matches(
                  choice.getAsJsonObject("rule"),
                  known.get(choice.get("id").getAsString()),
                  offered[index],
                  known)
              && allocation(row, offered, at + 1, used | 1 << index, known)) return true;
        }
      }
    return false;
  }

  private static String id(ItemStack stack) {
    return Identity.item(
        Item.itemRegistry.getNameForObject(stack.getItem()),
        Items.feather.getDamage(stack),
        TypedNbt.encode(stack.getTagCompound()));
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
