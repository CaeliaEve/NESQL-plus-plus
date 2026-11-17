package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Real Chisel lookup/callbacks, then adapter ownership and drift regressions. */
public final class NativeChiselTest {
  public static void run() throws Exception {
    try (NativeScope owned = new NativeScope()) {
      runChecks();
    }
    System.out.println("Chisel fixture returned all borrowed Forge/item/block/ore/Loader roots");
  }

  @SuppressWarnings("unchecked")
  private static void runChecks() throws Exception {
    Object registry = field(type("team.chisel.carving.Carving"), null, "chisel");
    Object previous = field(registry, "groups");
    set(registry, "groups", type("team.chisel.carving.GroupList").newInstance());
    Class<?> config = type("team.chisel.config.Configurations");
    boolean oldDamage = (Boolean) field(config, null, "allowChiselDamage");
    int oldMax = (Integer) field(config, null, "ironChiselMaxDamage");
    Map<String, cpw.mods.fml.common.ModContainer> mods =
        (Map<String, cpw.mods.fml.common.ModContainer>)
            field(cpw.mods.fml.common.Loader.instance(), "namedMods");
    cpw.mods.fml.common.ModContainer previousMod = mods.get("chisel");
    NativeCoreFixesTest.version("chisel", "2.16.15-GTNH");
    try {
      config.getField("allowChiselDamage").setBoolean(null, true);
      config.getField("ironChiselMaxDamage").setInt(null, 5);
      Item tool = nativeTool();
      ItemStack chisel = new ItemStack(tool, 1, 3);
      add(registry, "fixture-a", Blocks.stone, 0);
      add(registry, "fixture-a", Blocks.stone, 1);
      add(registry, "fixture-b", Blocks.stone, 2);
      add(registry, "fixture-b", Blocks.stone, 3);
      invoke(
          registry.getClass(),
          registry,
          "registerOre",
          new Class<?>[] {String.class, String.class},
          "fixture-b",
          "chiselNativePriorityB");
      ItemStack oreTarget = new ItemStack(Blocks.stone, 4, 0);
      oreTarget.setTagInfo("nativeTarget", new net.minecraft.nbt.NBTTagInt(9));
      OreDictionary.registerOre("chiselNativePriorityB", oreTarget);
      List<ItemStack> targets = targets(registry, new ItemStack(Blocks.stone, 9, 0));
      require(
          targets.size() == 3 && targets.get(0).getItemDamage() == 2,
          "Native first ore group did not override block/meta fallback");
      ItemStack tagged = new ItemStack(Blocks.stone, 8, 0);
      tagged.setTagInfo("fixture", new net.minecraft.nbt.NBTTagInt(42));
      require(
          targets(registry, tagged).get(0).getItemDamage() == 2,
          "Native registry unexpectedly inspected source NBT");
      Object variation =
          invoke(
              type("team.chisel.utils.General"),
              null,
              "getVariation",
              new Class<?>[] {ItemStack.class},
              tagged);
      Class<?> variationType = type("com.cricketcraft.chisel.api.carving.ICarvingVariation");
      require(
          (Boolean)
              invoke(
                  tool.getClass(),
                  tool,
                  "canChisel",
                  new Class<?>[] {net.minecraft.world.World.class, ItemStack.class, variationType},
                  null,
                  chisel,
                  variation),
          "Native tool admission changed");
      require(
          (Boolean)
              invoke(
                  tool.getClass(),
                  tool,
                  "onChisel",
                  new Class<?>[] {net.minecraft.world.World.class, ItemStack.class, variationType},
                  null,
                  chisel,
                  variation),
          "Native damage callback ignored enabled config");
      config.getField("allowChiselDamage").setBoolean(null, false);
      require(
          !(Boolean)
                  invoke(
                      tool.getClass(),
                      tool,
                      "onChisel",
                      new Class<?>[] {
                        net.minecraft.world.World.class, ItemStack.class, variationType
                      },
                      null,
                      chisel,
                      variation)
              && tool.getMaxDamage(chisel) == 0,
          "Native disabled damage remained unconditional");
      config.getField("allowChiselDamage").setBoolean(null, true);
      invoke(
          type("team.chisel.utils.General"),
          null,
          "setChiselTarget",
          new Class<?>[] {ItemStack.class, ItemStack.class},
          chisel,
          tagged);
      ItemStack retained =
          (ItemStack)
              invoke(
                  type("team.chisel.utils.General"),
                  null,
                  "getChiselTarget",
                  new Class<?>[] {ItemStack.class},
                  chisel);
      require(
          retained.stackSize == 8 && retained.getTagCompound().getInteger("fixture") == 42,
          "Native tool target lost remaining source count or NBT");
      invoke(
          type("team.chisel.utils.General"),
          null,
          "setChiselTarget",
          new Class<?>[] {ItemStack.class, ItemStack.class},
          chisel,
          null);
      require(
          chisel.getTagCompound().getCompoundTag("chiselTarget").hasNoTags(),
          "Exhausted source did not clear native target compound");
      pickup(tool, tagged, false);
      pickup(tool, tagged, true);
      TemplateRecipeHandler handler =
          (TemplateRecipeHandler) type("team.chisel.compat.nei.RecipeHandlerChisel").newInstance();
      sourceBoundaries(registry, handler);
      lookupBoundaries(registry, handler);
      ChiselRecipes adapter = new ChiselRecipes(handler);
      TemplateRecipeHandler.CachedRecipe borrowed =
          handler.new CachedRecipe() {
            @Override
            public codechicken.nei.PositionedStack getResult() {
              return null;
            }
          };
      handler.arecipes.add(borrowed);
      ItemStack returned = new ItemStack(tool, 1, 1);
      invoke(
          type("team.chisel.utils.General"),
          null,
          "setChiselTarget",
          new Class<?>[] {ItemStack.class, ItemStack.class},
          returned,
          null);
      ItemStack[] specimens = {
        new ItemStack(Blocks.stone, 1, 0),
        new ItemStack(Blocks.stone, 1, 1),
        new ItemStack(Blocks.stone, 1, 2),
        new ItemStack(Blocks.stone, 1, 3),
        new ItemStack(tool, 1, 0),
        returned,
        oreTarget
      };
      RecipeRow row = NativeCoreFixesTest.row(specimens);
      require(adapter.capture(0, row), "Native Chisel row missing");
      require(
          handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
          "Chisel capture replaces borrowed GUI cache");
      require(row.record.has("process"), "Chisel lacks executable native process");
      require(
          row.record.getAsJsonObject("process").getAsJsonArray("sources").size() > 0,
          "Native source partition evidence missing");
      require(row.inputs.size() == 2, "Compatible native tool is absent from allocation");
      require(
          row.outputs.size() == 2
              && row.outputs.get(1).getAsJsonObject().getAsJsonObject("change") != null,
          "Conditional tool state return missing");
      require(
          row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull(),
          "Chisel target advertised an unconditional fixed yield");
      require(
          handler.arecipes.size() == 1 && handler.arecipes.get(0) == borrowed,
          "Chisel retained another target or page");
      for (JsonElement choice : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")) {
        JsonObject rule = choice.getAsJsonObject().getAsJsonObject("rule");
        require(
            !rule.get("meta").getAsBoolean() && rule.get("nbt").getAsBoolean(),
            "Multi-choice input was mistaken for metadata wildcard or exact NBT");
      }
      String before = row.record.toString();
      tagged.stackSize = 33;
      require(before.equals(row.record.toString()), "Export retained mutable native view state");
      write("chisel-semantic", row, Arrays.asList(specimens));
      RecipeRow chinese = NativeCoreFixesTest.row("zh_CN", specimens);
      adapter.capture(0, chinese);
      write("chisel-semantic-zh_CN", chinese, Arrays.asList(specimens));
      require(
          row.record.get("id").equals(chinese.record.get("id")),
          "Chisel raw identity changed with locale");
      checkBranches(adapter, handler, specimens);
      try (Recipes.Cursor production =
          new Recipes.Handler(handler, 0).open(NativeCoreFixesTest.row(specimens).facts)) {
        require(
            field(production, "registry") instanceof ChiselRecipes,
            "Production Chisel cursor did not use its dedicated native reader");
        production.finishVerification();
      }
      config.getField("allowChiselDamage").setBoolean(null, false);
      drift(adapter);
      config.getField("allowChiselDamage").setBoolean(null, true);
      add(registry, "fixture-b", Blocks.stone, 4);
      drift(adapter);
      adapter.close();
      adapter.close();
      try {
        adapter.size();
        throw new AssertionError("Closed Chisel exposes rows");
      } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
        require(expected.code.equals("capture_closed"), expected.toString());
      }
      wildcard(registry, tool, returned);
    } finally {
      set(registry, "groups", previous);
      config.getField("allowChiselDamage").setBoolean(null, oldDamage);
      config.getField("ironChiselMaxDamage").setInt(null, oldMax);
      if (previousMod == null) mods.remove("chisel");
      else mods.put("chisel", previousMod);
    }
    System.out.println(
        "Native Chisel: ore priority, ignored NBT, native tool admission, conditional damage, target state, owned rows/views and drift passed");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static Item nativeTool() throws Exception {
    Class<?> cls = type("team.chisel.item.chisel.ItemChisel"),
        kind = type("team.chisel.item.chisel.ItemChisel$ChiselType");
    Item tool = (Item) cls.getConstructor(kind).newInstance(Enum.valueOf((Class) kind, "IRON"));
    Item.itemRegistry.addObject(31990, "chisel:native_fixture", tool);
    return tool;
  }

  @SuppressWarnings("unchecked")
  private static void sourceBoundaries(Object registry, TemplateRecipeHandler handler)
      throws Exception {
    Object list = field(registry, "groups");
    Map<String, Object> groups = (Map<String, Object>) field(list, "groups");
    try (ChiselRecipes reader = new ChiselRecipes(handler)) {
      try {
        set(list, "groups", new HashMap<>(groups));
        drift(reader);
      } finally {
        set(list, "groups", groups);
      }
      Object group = groups.get("fixture-a"), variations = field(group, "variations");
      try {
        set(group, "variations", new ArrayList<>((List<?>) variations));
        drift(reader);
      } finally {
        set(group, "variations", variations);
      }
      reader.finishVerification();
    }
    List<?> oreTables = (List<?>) field(OreDictionary.class, null, "idToStack");
    List<ItemStack> oreMembers =
        (List<ItemStack>) oreTables.get(OreDictionary.getOreID("chiselNativePriorityB"));
    try (ChiselRecipes reader = new ChiselRecipes(handler)) {
      ItemStack original = oreMembers.get(0);
      try {
        oreMembers.set(0, original.copy());
        drift(reader);
      } finally {
        oreMembers.set(0, original);
      }
      net.minecraft.nbt.NBTTagCompound tag = original.getTagCompound();
      try {
        original.setTagCompound((net.minecraft.nbt.NBTTagCompound) tag.copy());
        drift(reader);
      } finally {
        original.setTagCompound(tag);
      }
      reader.finishVerification();
    }
    final int[] calls = {0};
    Object foreign =
        Proxy.newProxyInstance(
            NativeChiselTest.class.getClassLoader(),
            new Class<?>[] {type("com.cricketcraft.chisel.api.carving.ICarvingGroup")},
            (proxy, method, args) -> {
              calls[0]++;
              throw new AssertionError("Unreviewed Chisel group callback " + method.getName());
            });
    groups.put("fixture-foreign", foreign);
    try {
      fails("recipe_unsupported", () -> new ChiselRecipes(handler));
      require(calls[0] == 0, "Foreign Chisel callback executed before rejection");
    } finally {
      groups.remove("fixture-foreign");
    }
    try {
      set(registry, "groups", null);
      fails("handler_not_ready", () -> new ChiselRecipes(handler));
      set(registry, "groups", type("team.chisel.carving.GroupList").newInstance());
      try (ChiselRecipes empty = new ChiselRecipes(handler)) {
        require(empty.size() == 0, "Ready-empty Chisel registry is not empty");
        empty.finishVerification();
      }
    } finally {
      set(registry, "groups", list);
    }
    System.out.println("Chisel source identities, readiness and unknown callback-zero passed");
  }

  private static void fails(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected Chisel fault " + code);
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault failure) {
      require(failure.code.equals(code), "Wrong Chisel failure " + failure);
    }
  }

  @SuppressWarnings("unchecked")
  private static void lookupBoundaries(Object registry, TemplateRecipeHandler handler)
      throws Exception {
    Object previous = field(registry, "groups");
    try {
      Object list = type("team.chisel.carving.GroupList").newInstance();
      set(registry, "groups", list);
      add(registry, "stale", Blocks.stone, 0);
      add(registry, "stale", Blocks.stone, 1);
      invoke(list.getClass(), list, "clear", new Class<?>[0]);
      try (ChiselRecipes reader = new ChiselRecipes(handler)) {
        require(reader.size() == 4, "Stale native fallback groups were dropped after clear");
        parity(registry, reader);
      }
      set(registry, "groups", type("team.chisel.carving.GroupList").newInstance());
      add(registry, "split", Blocks.stone, 9);
      Object group = ((Map<?, ?>) field(field(registry, "groups"), "groups")).get("split");
      Object variation = ((List<?>) field(group, "variations")).get(0);
      set(variation, "damage", 2);
      try (ChiselRecipes reader = new ChiselRecipes(handler)) {
        ChiselRecipes.Snapshot snapshot = (ChiselRecipes.Snapshot) field(reader, "snapshot");
        require(
            snapshot.sources.stream().anyMatch(d -> d.source.getItemDamage() == 9),
            "Block metadata input differs from output metadata and was omitted");
        parity(registry, reader);
      }
      // Enough colliding native block-hash entries force HashMap's tree lookup path.
      for (int index = 10; index < 32; index++) add(registry, "tree-" + index, Blocks.stone, index);
      for (Object value : ((Map<?, ?>) field(field(registry, "groups"), "groups")).values())
        for (Object member : (List<?>) field(value, "variations")) set(member, "damage", 9);
      Map<?, ?> nativeLookup = (Map<?, ?>) field(field(registry, "groups"), "lookup");
      Object[] table = (Object[]) field(HashMap.class, nativeLookup, "table");
      require(
          Arrays.stream(table)
              .filter(Objects::nonNull)
              .anyMatch(n -> n.getClass().getName().endsWith("$TreeNode")),
          "Native ambiguous lookup fixture did not reach tree bins");
      try (ChiselRecipes reader = new ChiselRecipes(handler)) {
        parity(registry, reader);
      }
      // A missing ore name must remain missing: getOres(name) would mutate Forge's registry.
      set(group, "oreName", "chiselNativeMissingDoNotRegister");
      int before = OreDictionary.getOreNames().length;
      try (ChiselRecipes reader = new ChiselRecipes(handler)) {
        reader.finishVerification();
      }
      require(
          OreDictionary.getOreNames().length == before,
          "Chisel read registered an absent ore name");
      RegistrationBindings first = RegistrationBindings.read(handler);
      for (int source = 0; source < 4; source++) {
        TemplateRecipeHandler independent =
            (TemplateRecipeHandler) handler.getClass().newInstance();
        require(
            first.same(RegistrationBindings.read(independent)),
            "Four discovery sources do not bind the same native Chisel table");
      }
      // Literal metadata 32767 is not a wildcard in GroupList's native OR matcher.
      set(registry, "groups", type("team.chisel.carving.GroupList").newInstance());
      add(registry, "literal-wild-value", Blocks.stone, 32767);
      try (ChiselRecipes reader = new ChiselRecipes(handler)) {
        parity(registry, reader);
        ChiselRecipes.Snapshot snapshot = (ChiselRecipes.Snapshot) field(reader, "snapshot");
        require(
            snapshot.sources.stream()
                .anyMatch(d -> d.source.getItemDamage() == 32767 && !d.wildcard),
            "Literal native metadata32767 was widened to every metadata");
        Item tool = snapshot.tools.get(0).stack.getItem();
        ItemStack returned = new ItemStack(tool, 1, 1);
        returned.setTagInfo("chiselTarget", new net.minecraft.nbt.NBTTagCompound());
        ItemStack[] known = {new ItemStack(Blocks.stone, 1, 32767), new ItemStack(tool), returned};
        RecipeRow row = NativeCoreFixesTest.row(known);
        reader.capture(0, row);
        write("chisel-literal-metadata-semantic", row, Arrays.asList(known));
      }
      modTweaker(registry, handler);
    } finally {
      set(registry, "groups", previous);
    }
    System.out.println(
        "Chisel stale lookup, split metadata, real HashMap tree bins, readonly ores and four-source identity passed");
  }

  private static void modTweaker(Object registry, TemplateRecipeHandler handler) throws Exception {
    set(registry, "groups", type("team.chisel.carving.GroupList").newInstance());
    NativeCoreFixesTest.version("modtweaker2", "0.12.0");
    Class<?> groupType = type("modtweaker2.mods.chisel.ChiselHelper$CarvingGroup");
    Class<?> variationType = type("modtweaker2.mods.chisel.ChiselHelper$CarvingVariation");
    Constructor<?> groupConstructor = groupType.getDeclaredConstructor(String.class);
    groupConstructor.setAccessible(true);
    Constructor<?> variationConstructor =
        variationType.getDeclaredConstructor(Block.class, int.class);
    variationConstructor.setAccessible(true);
    Object group = groupConstructor.newInstance("actual-tweaker");
    invoke(
        groupType,
        group,
        "addVariation",
        new Class<?>[] {type("com.cricketcraft.chisel.api.carving.ICarvingVariation")},
        variationConstructor.newInstance(Blocks.stone, 3));
    invoke(
        registry.getClass(),
        registry,
        "addGroup",
        new Class<?>[] {type("com.cricketcraft.chisel.api.carving.ICarvingGroup")},
        group);
    try (ChiselRecipes reader = new ChiselRecipes(handler)) {
      parity(registry, reader);
    }
    System.out.println("Chisel actual installed ModTweaker0.12.0 group/variation parity passed");
  }

  private static void parity(Object registry, ChiselRecipes reader) {
    ChiselRecipes.Snapshot snapshot = (ChiselRecipes.Snapshot) field(reader, "snapshot");
    for (ChiselRecipes.Decision decision : snapshot.sources) {
      List<ItemStack> expected = targets(registry, decision.source.copy());
      require(expected.size() == decision.targets.size(), "Native Chisel target census differs");
      for (int index = 0; index < expected.size(); index++)
        require(
            ChiselRecipes.key(expected.get(index))
                .equals(ChiselRecipes.key(decision.targets.get(index))),
            "Chisel target encounter/count/NBT differs from actual native lookup");
    }
    reader.finishVerification();
  }

  private static void add(Object registry, String group, Block block, int meta) {
    invoke(
        registry.getClass(),
        registry,
        "addVariation",
        new Class<?>[] {String.class, Block.class, int.class, int.class},
        group,
        block,
        meta,
        meta);
  }

  private static void checkBranches(
      ChiselRecipes adapter, TemplateRecipeHandler handler, ItemStack[] specimens)
      throws Exception {
    int oreSingle = 0, oreStack = 0;
    for (int index = 0; index < adapter.size(); index++) {
      RecipeRow row = NativeCoreFixesTest.row(specimens);
      require(adapter.capture(index, row), "Native branch disappeared");
      JsonObject process = row.record.getAsJsonObject("process");
      projected(process);
      require(row.outputs.size() == 2, "Targets became simultaneous outputs");
      String outputId = row.outputs.get(0).getAsJsonObject().get("id").getAsString();
      ItemStack target =
          Arrays.stream(specimens)
              .filter(stack -> id(stack).equals(outputId))
              .findFirst()
              .get()
              .copy();
      target.stackSize =
          row.outputs
              .get(0)
              .getAsJsonObject()
              .getAsJsonObject("quantity")
              .get("nominal")
              .getAsInt();
      if (target.getItemDamage() == 1)
        for (JsonElement choice : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices"))
          require(
              choice
                  .getAsJsonObject()
                  .get("id")
                  .getAsString()
                  .equals(
                      com.github.dcysteine.nesql.exporter.source.Identity.item(
                          "minecraft:stone", 1, com.google.gson.JsonNull.INSTANCE)),
              "Ore-priority source was admitted to direct-group target");
      if (process.get("targetCount").getAsInt() == 4) {
        if (process.get("mode").getAsString().equals("single")) {
          require(
              target.stackSize == 4 && target.getTagCompound().getInteger("nativeTarget") == 9,
              "Single pickup discarded native ore target count/NBT");
          oreSingle++;
          write("chisel-ore-single-semantic", row, Arrays.asList(specimens));
        } else {
          require(
              target.stackSize == 1 && target.getTagCompound() == null,
              "Whole-stack target sample retained ghost NBT/count");
          oreStack++;
          write("chisel-ore-stack-semantic", row, Arrays.asList(specimens));
        }
      }
    }
    require(oreSingle == 1 && oreStack == 1, "Native ore count branches missing");
  }

  private static void wildcard(Object registry, Item tool, ItemStack returned) throws Exception {
    add(registry, "fixture-d", Blocks.dirt, 2);
    add(registry, "fixture-d", Blocks.dirt, 3);
    add(registry, "fixture-c", Blocks.dirt, 0);
    add(registry, "fixture-c", Blocks.dirt, 1);
    invoke(
        registry.getClass(),
        registry,
        "registerOre",
        new Class<?>[] {String.class, String.class},
        "fixture-d",
        "chiselNativeExactD");
    OreDictionary.registerOre("chiselNativeExactD", new ItemStack(Blocks.dirt, 1, 4));
    invoke(
        registry.getClass(),
        registry,
        "registerOre",
        new Class<?>[] {String.class, String.class},
        "fixture-c",
        "chiselNativeWildcardC");
    OreDictionary.registerOre("chiselNativeWildcardC", new ItemStack(Blocks.dirt, 1, 32767));
    List<ItemStack> exactTargets=targets(registry,new ItemStack(Blocks.dirt,1,4));
    require(
        exactTargets.get(0).getItemDamage() == (Boolean.getBoolean("nesql.nativeOreOptimized") ? 0 : 2)
            && targets(registry, new ItemStack(Blocks.dirt, 1, 5)).get(0).getItemDamage() == 0,
        "Native exact/wildcard lookup priority changed");
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler) type("team.chisel.compat.nei.RecipeHandlerChisel").newInstance();
    ChiselRecipes adapter = new ChiselRecipes(handler);
    parity(registry,adapter);
    List<ItemStack> known = new ArrayList<>();
    known.add(new ItemStack(tool, 1, 0));
    known.add(returned);
    for (int meta = 0; meta <= 4; meta++) {
      known.add(new ItemStack(Blocks.stone, 1, meta));
      known.add(new ItemStack(Blocks.dirt, 1, meta));
    }
    known.add(new ItemStack(Blocks.dirt, 1, 32767));
    known.addAll(OreDictionary.getOres("chiselNativePriorityB"));
    String targetId =
        com.github.dcysteine.nesql.exporter.source.Identity.item(
            "minecraft:dirt", 0, JsonNull.INSTANCE);
    boolean seen = false;
    for (int index = 0; index < adapter.size(); index++) {
      RecipeRow row = NativeCoreFixesTest.row(known.toArray(new ItemStack[0]));
      adapter.capture(index, row);
      JsonObject process = row.record.getAsJsonObject("process");
      if (!process.get("target").getAsString().equals(targetId)
          || !process.get("mode").getAsString().equals("single")) continue;
      boolean wildcard = false;
      for (JsonElement choice : row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices"))
        if (choice.getAsJsonObject().getAsJsonObject("rule").get("meta").getAsBoolean())
          wildcard = true;
      require(
          wildcard, "Native wildcard ore membership was collapsed to a literal metadata example");
      projected(process);
      String excluded =
          com.github.dcysteine.nesql.exporter.source.Identity.item(
              "minecraft:dirt", 4, JsonNull.INSTANCE);
      boolean exclusion = false;
      for (JsonElement source : process.getAsJsonArray("sources"))
        if (source.getAsJsonObject().get("item").getAsString().equals(excluded)) {
          boolean nativeReachesTarget=exactTargets.stream().anyMatch(item->item.getItemDamage()==0);
          boolean capturedReachesTarget=false;
          for(JsonElement target:source.getAsJsonObject().getAsJsonArray("targets"))capturedReachesTarget|=target.getAsJsonObject().get("item").getAsString().equals(targetId);
          require(capturedReachesTarget==nativeReachesTarget,
              "Exact/wildcard projection disagrees with the actual native lookup order");
          exclusion = true;
        }
      require(exclusion, "Target projection dropped exact-over-wildcard exclusion partition");
      write("chisel-wildcard-semantic", row, known);
      seen = true;
      break;
    }
    require(seen, "Wildcard native target branch missing");
    adapter.close();
  }

  private static String id(ItemStack stack) {
    return com.github.dcysteine.nesql.exporter.source.Identity.item(
        Item.itemRegistry.getNameForObject(stack.getItem()),
        stack.getItemDamage(),
        com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(stack.getTagCompound()));
  }

  private static void write(String name, RecipeRow row, Collection<ItemStack> specimens)
      throws Exception {
    row.record.add(
        "source",
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner",
            "chisel",
            "handler",
            "team.chisel.compat.nei.RecipeHandlerChisel",
            "key",
            name.endsWith("-zh_CN") ? name.substring(0, name.length() - 6) : name));
    row.record.addProperty(
        "category",
        com.github.dcysteine.nesql.exporter.source.Identity.content(
            "category", row.record.getAsJsonObject("source")));
    NativeContractFixture.writeOwnedWithDurability(Boolean.getBoolean("nesql.nativeOreOptimized")?"hodgepodge-"+name:name, row, specimens);
  }

  private static void projected(JsonObject process) {
    for (JsonElement decision : process.getAsJsonArray("sources")) {
      JsonArray targets = decision.getAsJsonObject().getAsJsonArray("targets");
      require(
          targets.size() <= 1,
          "Row repeats unrelated native targets instead of selected-target membership");
      for (JsonElement observed : targets) {
        JsonObject target = observed.getAsJsonObject();
        require(
            target.get("item").equals(process.get("target"))
                && target.get("count").equals(process.get("targetCount")),
            "Target projection changed ghost identity/count");
      }
    }
  }

  private static void pickup(Item tool, ItemStack source, boolean bulk) throws Exception {
    NativePlayer player = (NativePlayer) allocate(NativePlayer.class);
    player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
    player.capabilities = new net.minecraft.entity.player.PlayerCapabilities();
    player.worldObj =
        (net.minecraft.world.World) allocate(NativeForestryProcessingTest.NativeWorld.class);
    setInherited(player, "rand", new Random(7));
    ItemStack chisel = new ItemStack(tool, 1, 3);
    player.inventory.setInventorySlotContents(0, chisel);
    Object selection =
        type("team.chisel.inventory.InventoryChiselSelection")
            .getConstructor(ItemStack.class)
            .newInstance(chisel);
    Object container =
        type("team.chisel.inventory.ContainerChisel")
            .getConstructor(net.minecraft.entity.player.InventoryPlayer.class, selection.getClass())
            .newInstance(player.inventory, selection);
    invoke(
        selection.getClass(),
        selection,
        "setInventorySlotContents",
        new Class<?>[] {int.class, ItemStack.class},
        60,
        source.copy());
    invoke(selection.getClass(), selection, "updateItems", new Class<?>[0]);
    net.minecraft.inventory.Slot slot = ((net.minecraft.inventory.Container) container).getSlot(0);
    ItemStack target = slot.getStack().copy();
    if (bulk) player.inventory.setItemStack(target.copy());
    slot.onPickupFromSlot(player, target);
    require(
        chisel.getItemDamage() == 4,
        "Native pickup did not apply exactly one conditional wear event");
    ItemStack remaining =
        (ItemStack)
            invoke(
                type("team.chisel.utils.General"),
                null,
                "getChiselTarget",
                new Class<?>[] {ItemStack.class},
                chisel);
    if (bulk)
      require(
          remaining == null
              && player.inventory.getItemStack().stackSize == 8
              && player.inventory.getItemStack().getTagCompound() == null,
          "Native whole-stack pickup count/NBT/target changed");
    else
      require(
          remaining.stackSize == 7 && remaining.getTagCompound().getInteger("fixture") == 42,
          "Native single pickup did not retain remaining source NBT/count");
  }

  private static Object allocate(Class<?> cls) throws Exception {
    Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return ((sun.misc.Unsafe) f.get(null)).allocateInstance(cls);
  }

  private static void setInherited(Object owner, String key, Object value) throws Exception {
    for (Class<?> cls = owner.getClass(); cls != null; cls = cls.getSuperclass())
      try {
        Field f = cls.getDeclaredField(key);
        f.setAccessible(true);
        f.set(owner, value);
        return;
      } catch (NoSuchFieldException ignored) {
      }
    throw new NoSuchFieldException(key);
  }

  public static final class NativePlayer extends net.minecraft.entity.player.EntityPlayerMP {
    private NativePlayer() {
      super(null, null, null, null);
    }

    @Override
    public void addStat(net.minecraft.stats.StatBase stat, int amount) {}
  }

  @SuppressWarnings("unchecked")
  private static List<ItemStack> targets(Object registry, ItemStack source) {
    return (List<ItemStack>)
        invoke(
            registry.getClass(),
            registry,
            "getItemsForChiseling",
            new Class<?>[] {ItemStack.class},
            source);
  }

  private static void set(Object object, String key, Object value) throws Exception {
    Field field = object.getClass().getDeclaredField(key);
    field.setAccessible(true);
    field.set(object, value);
  }

  private static final class NativeScope implements AutoCloseable {
    final List<Field> fields = new ArrayList<>();
    final List<Object> targets = new ArrayList<>(), originals = new ArrayList<>();
    final sun.misc.Unsafe memory;

    NativeScope() throws Exception {
      Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
      access.setAccessible(true);
      memory = (sun.misc.Unsafe) access.get(null);
      // Initialize cached enum state using the borrowed native configuration, before overrides.
      Class<?> kinds = type("team.chisel.item.chisel.ItemChisel$ChiselType");
      try {
        replace(kinds, field(kinds, null, "IRON"), "maxDamage", 5);
        Class<?> data = cpw.mods.fml.common.registry.GameData.class;
        Object previous = field(data, null, "mainData");
        // Keep final registry objects in place; replace their mutable backing stores. This also
        // preserves GameData/Item aliases and avoids relying on rewriting an inlined final root.
        registry(Item.itemRegistry);
        registry(Block.blockRegistry);
        replace(
            data,
            previous,
            "availabilityMap",
            ((BitSet) field(previous, "availabilityMap")).clone());
        for (String name :
            new String[] {"nameToId", "idToName", "idToStack", "idToStackUn", "stackToId"})
          replace(
              OreDictionary.class,
              null,
              name,
              NativeRegistryScope.emptyOreStorage(name));
        Object loader = cpw.mods.fml.common.Loader.instance();
        replace(
            loader.getClass(),
            loader,
            "namedMods",
            new HashMap<>((Map<?, ?>) field(loader, "namedMods")));
      } catch (Exception | Error failure) {
        close();
        throw failure;
      }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void registry(net.minecraft.util.RegistryNamespaced registry) throws Exception {
      com.google.common.collect.BiMap map =
          com.google.common.collect.HashBiMap.create((Map) field(registry, "registryObjects"));
      net.minecraft.util.ObjectIntIdentityMap ids = new net.minecraft.util.ObjectIntIdentityMap();
      for (Object item : map.values()) ids.func_148746_a(item, registry.getIDForObject(item));
      replace(net.minecraft.util.RegistrySimple.class, registry, "registryObjects", map);
      replace(
          net.minecraft.util.RegistryNamespaced.class, registry, "field_148758_b", map.inverse());
      replace(net.minecraft.util.RegistryNamespaced.class, registry, "underlyingIntegerMap", ids);
    }

    void replace(Class<?> owner, Object target, String name, Object value) throws Exception {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      fields.add(field);
      targets.add(target);
      originals.add(field.get(target));
      put(field, target, value);
    }

    void put(Field field, Object target, Object value) {
      if (field.getType() == int.class) {
        memory.putInt(target, memory.objectFieldOffset(field), (Integer) value);
      } else if (Modifier.isStatic(field.getModifiers()))
        memory.putObject(memory.staticFieldBase(field), memory.staticFieldOffset(field), value);
      else memory.putObject(target, memory.objectFieldOffset(field), value);
    }

    public void close() throws Exception {
      for (int index = fields.size() - 1; index >= 0; index--)
        put(fields.get(index), targets.get(index), originals.get(index));
      for (int index = 0; index < fields.size(); index++)
        require(
            fields.get(index).get(targets.get(index)) == originals.get(index)
                || fields.get(index).getType().isPrimitive()
                    && fields.get(index).get(targets.get(index)).equals(originals.get(index)),
            "Chisel fixture did not restore borrowed " + fields.get(index));
    }
  }

  private static void drift(RegistryRecipes adapter) {
    try {
      adapter.finishVerification();
      throw new AssertionError("Chisel registry/config drift accepted");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(expected.code.equals("recipe_changed"), "Wrong Chisel drift fault");
    }
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
