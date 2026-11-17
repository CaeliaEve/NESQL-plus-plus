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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagInt;
import net.minecraftforge.oredict.OreDictionary;

/** Checks catalyst-dependent precedence and native NBT/count semantics against the pinned API. */
final class NativeBotaniaPoolTest {
  static void special() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeBotaniaSpecialTest.active(scope);
      Class<?> api = Class.forName("vazkii.botania.api.BotaniaAPI");
      Class<?> blocks = Class.forName("vazkii.botania.common.block.ModBlocks");
      scope.replace(blocks, null, "pool", Blocks.stone);
      scope.replace(blocks, null, "alchemyCatalyst", Blocks.gold_block);
      scope.replace(blocks, null, "conjurationCatalyst", Blocks.iron_block);
      scope.replace(blocks, null, "specialFlower", Blocks.yellow_flower);
      scope.replace(api, null, "internalHandler",
          Class.forName("vazkii.botania.common.core.handler.InternalMethodHandler").newInstance());
      Class<?> miniType = Class.forName("vazkii.botania.api.recipe.RecipeMiniFlower");
      Object mini = miniType.getConstructor(String.class, String.class, int.class)
          .newInstance("daybloomChibi", "daybloom", 100);
      ItemStack input = (ItemStack) MagicApi.field(mini, "input");
      ItemStack output = (ItemStack) MagicApi.field(mini, "output");
      ItemStack impostor = new ItemStack(Items.apple, 5, 9);
      impostor.setTagInfo("type", new net.minecraft.nbt.NBTTagString("daybloom"));
      require((Boolean) miniType.getMethod("matches", ItemStack.class).invoke(mini, impostor),
          "Pinned MiniFlower fixture must ignore item and metadata when the subtile key agrees");
      TemplateRecipeHandler handler = (TemplateRecipeHandler)
          Class.forName("vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool")
              .newInstance();
      try (RegistryRecipes reader = BotaniaTestSource.open(handler,
          Collections.singletonList(mini))) {
        RecipeRow row = NativeCoreFixesTest.row(input, output, impostor);
        require(reader.size() == 1 && reader.capture(0, row),
            "MiniFlower native alchemy conversion was omitted");
        JsonObject process = row.record.getAsJsonObject("process");
        require(process.get("nativeRecipe").getAsString().equals(miniType.getName())
                && process.has("selection"),
            "MiniFlower matching was flattened into item/meta matching");
        JsonObject selector = process.getAsJsonObject("selection")
            .getAsJsonArray("current").get(0).getAsJsonObject();
        require(selector.get("kind").getAsString().equals("subTileKey")
                && selector.get("key").getAsString().equals("daybloom"),
            "MiniFlower lost its NBT subtile selection key");
        JsonObject origin = object("owner", "Botania", "handler", handler.getClass().getName(),
            "key", "mini-special");
        row.record.add("source", origin);
        row.record.addProperty("category", Identity.origin("category", origin));
        NativeContractFixture.writeOwned("botania-mini-special", row,
            Arrays.asList(input, output, impostor));
        reader.finishVerification();
        require(input.getTagCompound().getString("type").equals("daybloom"),
            "MiniFlower capture changed the borrowed input NBT");
      }
      ItemStack plain = new ItemStack(Blocks.yellow_flower), result = new ItemStack(Items.diamond);
      Class<?> baseType = Class.forName("vazkii.botania.api.recipe.RecipeManaInfusion");
      Object later = baseType.getConstructor(ItemStack.class, Object.class, int.class)
          .newInstance(result, plain, 50);
      baseType.getMethod("setAlchemy", boolean.class).invoke(later, true);
      try (RegistryRecipes reader = BotaniaTestSource.open(handler, Arrays.asList(mini, later))) {
        RecipeRow row = NativeCoreFixesTest.row(input, output, plain, result);
        require(reader.size() == 2 && reader.capture(1, row),
            "Ordinary pool entry after MiniFlower was omitted");
        JsonObject selection = row.record.getAsJsonObject("process").getAsJsonObject("selection");
        require(selection.getAsJsonArray("current").get(0).getAsJsonObject()
                .get("kind").getAsString().equals("item")
                && selection.getAsJsonArray("earlier").get(0).getAsJsonObject()
                    .get("key").getAsString().equals("daybloom"),
            "MiniFlower priority was erased from an ordinary pool conversion");
        JsonObject origin = object("owner", "Botania", "handler", handler.getClass().getName(),
            "key", "mini-priority");
        row.record.add("source", origin);
        row.record.addProperty("category", Identity.origin("category", origin));
        NativeContractFixture.writeOwned("botania-mini-priority", row,
            Arrays.asList(input, output, plain, result));
        reader.finishVerification();
      }
    }
  }

  static void run() throws Exception {
    Class<?> adapter;
    try {
      adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.BotaniaPoolRecipes");
    } catch (ClassNotFoundException missing) {
      throw new AssertionError("Mana infusion has no catalyst-aware native capture", missing);
    }
    Class<?> recipe = Class.forName("vazkii.botania.api.recipe.RecipeManaInfusion");
    Class<?> blocks = Class.forName("vazkii.botania.common.block.ModBlocks");
    blocks.getField("pool").set(null, Blocks.stone);
    blocks.getField("alchemyCatalyst").set(null, Blocks.gold_block);
    blocks.getField("conjurationCatalyst").set(null, Blocks.iron_block);
    ItemStack iron = new ItemStack(Items.iron_ingot, 19),
        gold = new ItemStack(Items.gold_ingot, 11);
    ItemStack diamond = new ItemStack(Items.diamond, 3);
    diamond.setTagInfo("native", new NBTTagInt(7));
    ItemStack emerald = new ItemStack(Items.emerald, 2), apple = new ItemStack(Items.apple);
    OreDictionary.registerOre("nesqlPoolFixture", iron);
    OreDictionary.registerOre("nesqlPoolFixture", gold);
    Constructor<?> nativeCtor = recipe.getConstructor(ItemStack.class, Object.class, int.class);
    Object alchemy = nativeCtor.newInstance(diamond, iron, 50);
    recipe.getMethod("setAlchemy", boolean.class).invoke(alchemy, true);
    Object common = nativeCtor.newInstance(emerald, "nesqlPoolFixture", 75);
    Object shadowed = nativeCtor.newInstance(apple, iron, 1);
    List<Object> registry = new ArrayList<>(Arrays.asList(alchemy, common, shadowed));
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler)
            Class.forName("vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool")
                .newInstance();
    RegistryRecipes capture = (RegistryRecipes) BotaniaTestSource.open(handler, registry);
    require(capture.size() == 7, "Unflagged recipes must retain each distinct catalyst state");
    Map<String, ItemStack> known = new HashMap<>();
    for (ItemStack item : Arrays.asList(iron, gold, diamond, emerald, apple))
      known.put(id(item), item);
    JsonArray records = new JsonArray();
    List<RecipeRow> rows = new ArrayList<>();
    for (int i = 0; i < capture.size(); i++) {
      Facts facts = new Facts("en_US");
      @SuppressWarnings("unchecked")
      Set<String> items = (Set<String>) MagicApi.field(facts, "items");
      items.addAll(known.keySet());
      RecipeRow row =
          new RecipeRow(
              facts,
              object("owner", "Botania", "handler", handler.getClass().getName(), "key", "pool"),
              "category_test",
              i);
      if (!capture.capture(i, row)) continue;
      require(
          row.record.has("process") && row.record.get("process").isJsonObject(),
          "Mana pool lacks a typed catalyst, mana and native collision process");
      require(
          row.properties.entrySet().isEmpty(),
          "Mana pool rules still emit fixed English properties into every locale");
      rows.add(row);
      row.finish();
      records.add(row.record);
      require(
          row.inputs.size() == 1 && row.outputs.size() == 1,
          "Machine and catalyst blocks became consumed ingredients");
      JsonObject input = row.inputs.get(0).getAsJsonObject();
      for (JsonElement raw : input.getAsJsonArray("choices")) {
        JsonObject choice = raw.getAsJsonObject();
        require(
            choice.get("amount").getAsString().equals("1")
                && choice.getAsJsonArray("returns").size() == 0,
            "Mana pool used template count or invoked crafting container returns");
      }
      require(handler.arecipes.isEmpty(), "Mana pool capture wrote the borrowed GUI cache");
    }
    require(rows.size() == 4, "First-match native shadowing changed");
    for (String state : Arrays.asList("none", "alchemy", "conjuration"))
      for (ItemStack item : Arrays.asList(iron, gold)) {
        ItemStack offered = item.copy();
        offered.stackSize = 1;
        offered.setTagInfo("unrelated", new NBTTagInt(22));
        Object winner = null;
        for (Object raw : registry) {
          if ((Boolean) recipe.getMethod("isAlchemy").invoke(raw) && !state.equals("alchemy"))
            continue;
          if ((Boolean) recipe.getMethod("isConjuration").invoke(raw)
              && !state.equals("conjuration")) continue;
          if ((Boolean) recipe.getMethod("matches", ItemStack.class).invoke(raw, offered)) {
            winner = raw;
            break;
          }
        }
        List<String> outputs = new ArrayList<>();
        for (RecipeRow row : rows) {
          if (!row.record.getAsJsonObject("process").get("catalyst").getAsString().equals(state))
            continue;
          for (JsonElement raw : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")) {
            JsonObject choice = raw.getAsJsonObject();
            if (NativeRailTest.matches(
                choice.getAsJsonObject("rule"),
                known.get(choice.get("id").getAsString()),
                offered,
                known)) {
              outputs.add(row.outputs.get(0).getAsJsonObject().get("id").getAsString());
              break;
            }
          }
        }
        require(
            winner != null
                && outputs.equals(
                    Collections.singletonList(
                        id((ItemStack) recipe.getMethod("getOutput").invoke(winner)))),
            "Captured catalyst/input predicates differ from native first-match selection");
      }
    require(
        rows.get(0).outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
            && rows.get(0).record.getAsJsonObject("process").get("mana").getAsInt() == 50,
        "Native output quantity or mana cost changed");
    require(
        diamond.stackSize == 3
            && diamond.getTagCompound().getInteger("native") == 7
            && iron.stackSize == 19,
        "Owned capture mutated native stacks");
    capture.verify();
    String unknownOre = "nesqlPoolAbsentReadOnly";
    Map<?, ?> nativeNames = (Map<?, ?>) MagicApi.field(OreDictionary.class, null, "nameToId");
    require(!nativeNames.containsKey(unknownOre), "Missing ore test was already registered");
    try (RegistryRecipes absent =
        (RegistryRecipes)
            BotaniaTestSource.open(
                handler, Collections.singletonList(nativeCtor.newInstance(apple, unknownOre, 1)))) {
      require(!nativeNames.containsKey(unknownOre), "Mana pool read registered a missing ore name");
      require(absent.size() == 3, "Missing ore lost catalyst branches");
      RecipeRow empty = NativeCoreFixesTest.row(apple);
      require(
          !absent.capture(0, empty) && empty.exclusion != null,
          "Missing ore predicate lacks explicit exclusion evidence");
    }
    diamond.stackSize = 4;
    try {
      capture.verify();
      throw new AssertionError("Mutated recipe output did not invalidate capture");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("recipe_changed"), "Wrong registry drift failure");
    }
    diamond.stackSize = 3;
    Field outputField = recipe.getDeclaredField("output");
    outputField.setAccessible(true);
    outputField.set(alchemy, diamond.copy());
    try {
      capture.verify();
      throw new AssertionError(
          "Equal-value native pool output replacement retained source identity");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("recipe_changed"), "Wrong nested pool identity drift");
    } finally {
      outputField.set(alchemy, diamond);
    }
    net.minecraft.nbt.NBTTagCompound nativeTag = diamond.getTagCompound();
    diamond.setTagCompound((net.minecraft.nbt.NBTTagCompound) nativeTag.copy());
    try {
      capture.verify();
      throw new AssertionError("Equal-value native pool NBT replacement retained source identity");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("recipe_changed"), "Wrong pool NBT identity drift");
    } finally {
      diamond.setTagCompound(nativeTag);
    }
    signedMana(handler, nativeCtor, new ItemStack(Items.iron_ingot), new ItemStack(Items.emerald));
    try (RegistryRecipes empty = BotaniaTestSource.open(handler, new ArrayList<>())) {
      require(empty.size() == 0, "Ready empty mana registry was treated as unready");
      empty.finishVerification();
    }
    ItemStack foreignNbt = new ItemStack(Items.emerald);
    UnknownNbt unsafeTag = new UnknownNbt();
    foreignNbt.setTagCompound(unsafeTag);
    try {
      BotaniaTestSource.open(
          handler,
          Collections.singletonList(
              nativeCtor.newInstance(foreignNbt, new ItemStack(Items.iron_ingot), 1)));
      throw new AssertionError("Foreign Botania NBT owner was accepted before copying");
    } catch (Jobs.Fault expected) {
      require(
          expected.code.equals("recipe_unsupported") && unsafeTag.copies == 0,
          "Foreign NBT callback executed before rejection");
    }
    ItemStack largeNbt = new ItemStack(Items.emerald);
    largeNbt.setTagInfo("payload", new net.minecraft.nbt.NBTTagByteArray(new byte[262144]));
    List<Object> largeRegistry = new ArrayList<>();
    for (int i = 0; i < 100; i++)
      largeRegistry.add(nativeCtor.newInstance(largeNbt, new ItemStack(Items.iron_ingot), i));
    try {
      BotaniaTestSource.open(handler, largeRegistry);
      throw new AssertionError("Cumulative Botania NBT snapshots exceeded their bounded budget");
    } catch (Jobs.Fault expected) {
      require(
          expected.code.equals("recipe_unsupported") && expected.getMessage().contains("budget"),
          "Wrong Botania cumulative snapshot failure");
    }
    wildcardCandidates(handler, nativeCtor);
    production(handler, registry, null, new ItemStack[] {iron, gold, diamond, emerald, apple});
    try {
      BotaniaTestSource.open(handler, Collections.singletonList(new Object()));
      throw new AssertionError("Unadapted native callback accepted");
    } catch (Jobs.Fault expected) {
      require(expected instanceof Jobs.Fault, "Wrong native callback guard");
    }
    Item many = new Item().setHasSubtypes(true);
    Item.itemRegistry.addObject(31034, "fixture:pool_many", many);
    for (int meta = 0; meta < 1500; meta++)
      OreDictionary.registerOre("nesqlPoolPriorityBudget", new ItemStack(many, 1, meta));
    RegistryRecipes expensive =
        (RegistryRecipes)
            BotaniaTestSource.open(
                handler,
                Arrays.asList(
                    nativeCtor.newInstance(emerald, "nesqlPoolPriorityBudget", 5),
                    nativeCtor.newInstance(apple, "nesqlPoolPriorityBudget", 10)));
    RecipeRow budgetRow =
        new RecipeRow(
            new Facts("en_US"),
            object("owner", "Botania", "handler", handler.getClass().getName(), "key", "pool"),
            "category_test",
            3);
    try {
      expensive.capture(3, budgetRow);
      throw new AssertionError("Mana pool accepted an unbounded first-match comparison workload");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("recipe_unsupported"), "Wrong mana pool proof-budget failure");
    }
    expensive.close();
    Item clamp = new ClampDamageItem();
    Item.itemRegistry.addObject(31035, "fixture:pool_clamp", clamp);
    Object callback = nativeCtor.newInstance(emerald, new ItemStack(clamp, 1, 32767), 5);
    ItemStack offered = new ItemStack(clamp);
    Items.feather.setDamage(offered, 1);
    require(
        !(Boolean) recipe.getMethod("matches", ItemStack.class).invoke(callback, offered),
        "Native wildcard setter callback fixture no longer narrows matching");
    try {
      BotaniaTestSource.open(handler, Collections.singletonList(callback));
      throw new AssertionError("Mana pool flattened an overridden wildcard metadata setter");
    } catch (Jobs.Fault expected) {
      require(
          expected instanceof Jobs.Fault && expected.code.equals("recipe_unsupported"),
          "Wrong mana pool wildcard callback guard");
    }
    capture.close();
    capture.close();
    try {
      capture.verify();
      throw new AssertionError("Closed mana pool reader remained usable");
    } catch (Jobs.Fault expected) {
      require(expected.code.equals("capture_closed"), "Wrong closed mana pool fault");
    }
    java.nio.file.Files.write(
        java.nio.file.Paths.get("build/native-tests/botania-pool-records.json"),
        CanonicalJson.bytes(records));
    System.out.println(
        "Native Botania pool: catalyst states, native first-match selection, ignored NBT, unit consumption, output NBT/count and drift guards passed");
  }

  private static void signedMana(
      TemplateRecipeHandler handler, Constructor<?> recipe, ItemStack input, ItemStack output)
      throws Exception {
    for (int mana : new int[] {-50, Integer.MIN_VALUE, 0, Integer.MAX_VALUE}) {
      Object nativeRecipe = recipe.newInstance(output, input, mana);
      require(
          (Boolean)
              nativeRecipe
                  .getClass()
                  .getMethod("matches", ItemStack.class)
                  .invoke(nativeRecipe, input),
          "Native signed-mana recipe no longer matches its input");
      require(
          (Integer) nativeRecipe.getClass().getMethod("getManaToConsume").invoke(nativeRecipe)
              == mana,
          "Native mana parameter changed");
      try (RegistryRecipes reader =
          BotaniaTestSource.open(handler, Collections.singletonList(nativeRecipe))) {
        List<String> english = new ArrayList<>();
        for (String locale : new String[] {"en_US", "zh_CN"})
          for (int state = 0; state < reader.size(); state++) {
            RecipeRow row = NativeCoreFixesTest.row(locale, input, output);
            JsonObject origin =
                object(
                    "owner",
                    "Botania",
                    "handler",
                    handler.getClass().getName(),
                    "key",
                    "pool-signed:" + mana);
            row.record.add("source", origin);
            row.record.addProperty("category", Identity.origin("category", origin));
            row.record.addProperty("order", state);
            require(reader.capture(state, row), "Signed-mana native definition was excluded");
            require(
                row.record.getAsJsonObject("process").get("mana").getAsInt() == mana,
                "Signed mana was clamped or turned into a fixed timing cost");
            row.finish();
            if (locale.equals("en_US")) english.add(row.record.get("id").getAsString());
            else
              require(
                  english.get(state).equals(row.record.get("id").getAsString()),
                  "Signed-mana identity depends on language");
            NativeContractFixture.writeOwned(
                "botania-pool-signed-" + mana + "-" + state + "-" + locale,
                row,
                Arrays.asList(input, output));
          }
      }
    }
  }

  private static void wildcardCandidates(TemplateRecipeHandler handler, Constructor<?> recipe)
      throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      com.google.common.collect.ListMultimap<Item, ItemStack> map =
          com.google.common.collect.ArrayListMultimap.create();
      scope.replace(codechicken.nei.ItemList.class, null, "itemMap", map);
      scope.replace(codechicken.nei.ItemList.class, null, "loadFinished", true);
      Object wildcard =
          recipe.newInstance(new ItemStack(Items.diamond), new ItemStack(Items.paper, 1, 32767), 5);
      try (RegistryRecipes capture =
          (RegistryRecipes)
              BotaniaTestSource.open(
                  handler, new ArrayList<>(Collections.singletonList(wildcard)))) {
        RecipeRow row =
            NativeCoreFixesTest.row(new ItemStack(Items.paper), new ItemStack(Items.diamond));
        require(capture.capture(0, row), "Native wildcard fallback recipe disappeared");
        JsonObject choice =
            row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(
            choice.get("id").getAsString().equals(id(new ItemStack(Items.paper)))
                && choice.getAsJsonObject("rule").get("meta").getAsBoolean(),
            "Ready-empty native variants lost metadata-zero witness or wildcard rule");
        capture.finishVerification();
        map.put(Items.paper, new ItemStack(Items.paper, 1, 3));
        try {
          capture.finishVerification();
          throw new AssertionError("Changed wildcard table passed sealing");
        } catch (Jobs.Fault expected) {
          require(expected.code.equals("recipe_changed"), "Wrong wildcard table drift");
        }
        map.clear();
      }
      scope.replace(codechicken.nei.ItemList.class, null, "loadFinished", false);
      try (RegistryRecipes capture =
          (RegistryRecipes)
              BotaniaTestSource.open(
                  handler, new ArrayList<>(Collections.singletonList(wildcard)))) {
        try {
          capture.capture(
              0, NativeCoreFixesTest.row(new ItemStack(Items.paper), new ItemStack(Items.diamond)));
          throw new AssertionError("Unready wildcard table invented a witness");
        } catch (Jobs.Fault expected) {
          require(expected.code.equals("handler_not_ready"), "Wrong unready wildcard fault");
        }
      }
    }
  }

  static void production(
      TemplateRecipeHandler handler, List<?> registry, List<ItemStack> catalog, ItemStack[] known)
      throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeCoreFixesTest.version("Botania", "1.12.28-GTNH");
      cpw.mods.fml.common.Loader loader = cpw.mods.fml.common.Loader.instance();
      Object controller = new cpw.mods.fml.common.LoadController(loader);
      cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
      metadata.modId = "Botania";
      scope.replace(
          controller.getClass(),
          controller,
          "activeContainer",
          new cpw.mods.fml.common.DummyModContainer(metadata));
      scope.replace(loader.getClass(), loader, "modController", controller);
      String name = handler.getClass().getName();
      boolean floating = name.endsWith("FloatingFlowers"), pool = name.endsWith("ManaPool");
      Class<?> owner;
      Object target;
      String sourceField;
      if (floating) {
        owner = net.minecraft.item.crafting.CraftingManager.class;
        target = net.minecraft.item.crafting.CraftingManager.getInstance();
        sourceField = "recipes";
        scope.replace(codechicken.nei.ItemList.class, null, "items", catalog);
      } else {
        owner = Class.forName("vazkii.botania.api.BotaniaAPI");
        target = null;
        sourceField = pool ? "manaInfusionRecipes" : "runeAltarRecipes";
        String ore = pool ? "nesqlPoolFixture" : "nesqlRunicMetal";
        OreDictionary.registerOre(ore, known[0]);
        OreDictionary.registerOre(ore, known[1]);
      }
      scope.replace(owner, target, sourceField, registry);
      RegistrationBindings first = RegistrationBindings.read(handler);
      require(first != null, "Mana pool has no native source binding");
      for (int route = 0; route < 4; route++)
        require(
            first.same(
                RegistrationBindings.read(
                    (TemplateRecipeHandler) handler.getClass().newInstance())),
            "Mana pool registration roots differ");
      try (Recipes.Cursor cursor =
          new Recipes.Handler(handler, 0).open(NativeCoreFixesTest.row(known).facts)) {
        List<TemplateRecipeHandler.CachedRecipe> borrowed = new ArrayList<>(handler.arecipes);
        cursor.capture(0);
        cursor.finishVerification();
        require(handler.arecipes.equals(borrowed),
            "Botania production capture changed the borrowed GUI recipe cache");
      }
      try (RegistryRecipes capture =
          floating
              ? new BotaniaFloatingRecipes(handler)
              : pool ? new BotaniaPoolRecipes(handler) : new BotaniaRunicRecipes(handler)) {
        List<ItemStack> specimens = new ArrayList<>();
        for (ItemStack item : known) {
          specimens.add(item);
          ItemStack clean = item.copy();
          clean.setTagCompound(null);
          specimens.add(clean);
        }
        List<String> english = new ArrayList<>();
        for (String locale : new String[] {"en_US", "zh_CN"})
          for (int index = 0; index < capture.size(); index++) {
            RecipeRow row = NativeCoreFixesTest.row(locale, specimens.toArray(new ItemStack[0]));
            JsonObject origin =
                object(
                    "owner",
                    "Botania",
                    "handler",
                    name,
                    "key",
                    floating ? "floating" : pool ? "pool" : "runic");
            row.record.add("source", origin);
            row.record.addProperty("category", Identity.origin("category", origin));
            row.record.addProperty("order", index);
            if (!capture.capture(index, row)) continue;
            row.finish();
            require(
                row.properties.entrySet().isEmpty(),
                "Botania emits fixed English rules into both languages");
            if (locale.equals("en_US")) english.add(row.record.get("id").getAsString());
            else
              require(
                  english.contains(row.record.get("id").getAsString()),
                  "Botania semantic identity depends on locale");
            NativeContractFixture.writeOwned(
                "botania-"
                    + (floating ? "floating" : pool ? "pool" : "runic")
                    + "-native-"
                    + index
                    + "-"
                    + locale,
                row,
                specimens);
          }
        scope.replace(owner, target, sourceField, new ArrayList<>(registry));
        try {
          require(
              !first.same(RegistrationBindings.read(handler)),
              "Equal-value Botania registry replacement retained identity");
          try {
            capture.verify();
            throw new AssertionError("Changed production source passed");
          } catch (Jobs.Fault expected) {
            require(expected.code.equals("recipe_changed"), "Wrong production root drift");
          }
        } finally {
          scope.replace(owner, target, sourceField, registry);
        }
      }
    }
  }

  static final class ClampDamageItem extends Item {
    @Override
    public void setDamage(ItemStack stack, int damage) {
      super.setDamage(stack, damage == 32767 ? damage : 0);
    }
  }

  static final class UnknownNbt extends net.minecraft.nbt.NBTTagCompound {
    int copies;

    @Override
    public net.minecraft.nbt.NBTBase copy() {
      copies++;
      throw new AssertionError("Foreign NBT copy executed");
    }
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
