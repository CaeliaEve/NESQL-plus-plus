package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import java.util.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;

/** Native ProjectBlue probes; no game or global subtype enumeration is authorized. */
final class NativeProjectBlueTest {
  @SuppressWarnings("unchecked")
  static void run() throws Exception {
    Class<?> root = type("gcewing.projectblue.ProjectBlue"),
        rules = type("gcewing.projectblue.ControlPanelRecipes");
    List<?> original = (List<?>) field(rules, null, "recipes");
    Object lever = field(root, null, "miniatureLever"),
        can = field(root, null, "sprayCan"),
        empty = field(root, null, "emptySprayCan");
    try {
      Class<?> controlType = type("gcewing.projectblue.ControlPanelPart$ControlType");
      Item control =
          (Item)
              type("gcewing.projectblue.ControlItem")
                  .getConstructor(controlType)
                  .newInstance(Enum.valueOf((Class) controlType, "LEVER"));
      Item paint = (Item) type("gcewing.projectblue.SprayCanItem").newInstance();
      set(root, "miniatureLever", control);
      set(root, "sprayCan", paint);
      set(root, "emptySprayCan", Items.bucket);
      java.lang.reflect.Constructor<?> ctor =
          type("gcewing.projectblue.ControlPanelRecipes$PaintControl").getDeclaredConstructor();
      ctor.setAccessible(true);
      IRecipe nativePaint = (IRecipe) ctor.newInstance();
      InventoryCrafting grid =
          new InventoryCrafting(
              new Container() {
                public boolean canInteractWith(EntityPlayer player) {
                  return false;
                }
              },
              3,
              3);
      ItemStack source = new ItemStack(control, 4, 17);
      source.setTagCompound(new NBTTagCompound());
      source.getTagCompound().setString("discard", "native");
      ItemStack spray = new ItemStack(paint, 2, 5);
      spray.setTagCompound(new NBTTagCompound());
      spray.getTagCompound().setInteger("color", 7);
      spray.getTagCompound().setString("discard", "native");
      grid.setInventorySlotContents(0, source);
      grid.setInventorySlotContents(1, spray);
      grid.setInventorySlotContents(8, new ItemStack(Items.diamond, 9));
      require(
          nativePaint.matches(grid, null), "Native paint checked an otherwise ignored grid cell");
      ItemStack output = nativePaint.getCraftingResult(grid);
      require(
          output.getItem() == control
              && output.stackSize == 1
              && output.getItemDamage() == 7
              && !output.hasTagCompound(),
          "Native paint output metadata/NBT changed");
      require(
          source.stackSize == 4 && source.hasTagCompound(), "Native result changed borrowed input");
      require(paint.hasContainerItem(spray), "Partly used spray has no native return");
      ItemStack returned = paint.getContainerItem(spray);
      require(
          returned.stackSize == 1
              && returned.getItemDamage() == 6
              && returned.getTagCompound().getInteger("color") == 7
              && !returned.getTagCompound().hasKey("discard"),
          "Native spray return was simplified to preserved NBT");
      spray.setItemDamage(255);
      require(
          paint.hasContainerItem(spray) && paint.getContainerItem(spray).getItemDamage() == 256,
          "Native last reusable spray return changed");
      spray.setItemDamage(256);
      require(!paint.hasContainerItem(spray), "Exhausted spray falsely promises an empty can");
      System.out.println(
          "Native ProjectBlue paint: unrelated grid cell accepted, result drops NBT, spray returns color-only damage+1 through 256, no empty-can return at exhaustion");
      set(rules, "recipes", new ArrayList<>());
      TemplateRecipeHandler handler =
          (TemplateRecipeHandler) type("gcewing.projectblue.nei.NEIRecipeHandler").newInstance();
      require(
          Recipes.adapter(handler) == Recipes.Adapter.PROJECT_BLUE,
          "ProjectBlue native route missing");
      SubtypeProbe probe = new SubtypeProbe();
      Item.itemRegistry.addObject(12434, "nesql_projectblue_unrelated", probe);
      // A ready-empty native registry needs no unrelated mod's subtype callback.
      try (NativeReferences reader = new ProjectBlueRecipes(handler, origin())) {
        require(
            reader.counts().get("rules").getAsInt() == 0,
            "Ready-empty ProjectBlue rules should remain empty");
        reader.finishVerification();
        RegistrationBindings bindings = RegistrationBindings.read(handler);
        for (int i = 0; i < 4; i++)
          require(
              bindings.same(
                  RegistrationBindings.read(
                      (TemplateRecipeHandler) handler.getClass().newInstance())),
              "ProjectBlue registration sources differ");
        Object emptyRules = field(rules, null, "recipes");
        try {
          set(rules, "recipes", new ArrayList<>());
          fails("recipe_changed", reader::verify);
          require(
              !bindings.same(RegistrationBindings.read(handler)),
              "ProjectBlue registration lost same-content list replacement");
        } finally {
          set(rules, "recipes", emptyRules);
        }
        try {
          set(rules, "recipes", null);
          fails("handler_not_ready", () -> new ProjectBlueRecipes(handler, origin()));
        } finally {
          set(rules, "recipes", emptyRules);
        }
        reader.close();
        reader.close();
        fails("capture_closed", reader::size);
      }
      require(probe.calls == 0, "ProjectBlue enumerated unrelated native item subtypes");
      set(rules, "recipes", new ArrayList<>(Collections.singletonList(nativePaint)));
      TemplateRecipeHandler.CachedRecipe sentinel =
          handler.new CachedRecipe() {
            public codechicken.nei.PositionedStack getResult() {
              throw new AssertionError("ProjectBlue read borrowed GUI cache");
            }
          };
      handler.arecipes.add(sentinel);
      // Complete non-empty source fixtures are exercised below. An unavailable
      // item catalogue is readiness failure, never an invitation to scan subtypes.
      boolean ready = codechicken.nei.ItemList.loadFinished;
      try {
        codechicken.nei.ItemList.loadFinished = false;
        fails("handler_not_ready", () -> new ProjectBlueRecipes(handler, origin()));
      } finally {
        codechicken.nei.ItemList.loadFinished = ready;
      }
      require(
          handler.arecipes.size() == 1 && handler.arecipes.get(0) == sentinel,
          "ProjectBlue changed borrowed native GUI cache");
      require(probe.calls == 0, "Nonempty ProjectBlue reader called an unrelated subtype callback");
      System.out.println(
          "ProjectBlue native paint output, ignored cells and spray-return boundary probes passed");
    } finally {
      set(rules, "recipes", original);
      set(root, "miniatureLever", lever);
      set(root, "sprayCan", can);
      set(root, "emptySprayCan", empty);
    }
    materialsOnly();
  }

  @SuppressWarnings("unchecked")
  static void materialsOnly() throws Exception {
    Class<?> owner = type("gcewing.projectblue.ControlPanelMaterial"),
        module = type("codechicken.microblock.MicroMaterialRegistry$");
    Object provider = field(module, null, "MODULE$"),
        cache = field(owner, null, "cache"),
        types = field(provider, "codechicken$microblock$MicroMaterialRegistry$$typeMap"),
        remap = field(provider, "remap");
    java.lang.reflect.Field
        nativeTypes =
            module.getDeclaredField("codechicken$microblock$MicroMaterialRegistry$$typeMap"),
        nativeRemap = module.getDeclaredField("remap");
    nativeTypes.setAccessible(true);
    nativeRemap.setAccessible(true);
    Map<String, Object> owned = new HashMap<>();
    Object table = types.getClass().newInstance(), aliases = remap.getClass().newInstance();
    try {
      set(owner, "cache", owned);
      nativeTypes.set(provider, table);
      nativeRemap.set(provider, aliases);
      Object wool =
          type("codechicken.microblock.BlockMicroMaterial")
              .getConstructor(net.minecraft.block.Block.class, int.class)
              .newInstance(net.minecraft.init.Blocks.wool, 5);
      invoke(
          table.getClass(),
          table,
          "put",
          new Class<?>[] {Object.class, Object.class},
          "fixture.wool",
          wool);
      invoke(
          aliases.getClass(),
          aliases,
          "put",
          new Class<?>[] {Object.class, Object.class},
          "alias.wool",
          "fixture.wool");
      invoke(
          aliases.getClass(),
          aliases,
          "put",
          new Class<?>[] {Object.class, Object.class},
          "alias.chain",
          "alias.wool");
      Object stale = invoke(owner, null, "forName", new Class<?>[] {String.class}, "stale");
      java.lang.reflect.Field name = owner.getField("name"),
          block = owner.getField("block"),
          metadata = owner.getField("metadata");
      name.set(stale, "retained-name");
      block.set(stale, net.minecraft.init.Blocks.stone);
      metadata.setInt(stale, 12);
      try (ProjectBlueMaterials reader = new ProjectBlueMaterials()) {
        int count = owned.size();
        for (String key :
            new String[] {"fixture.wool", "alias.wool", "alias.chain", "missing", "stale", ""}) {
          ProjectBlueMaterials.Resolved resolution = reader.resolve(key);
          ItemStack observed = resolution.stack(Items.paper, 3);
          require(owned.size() == count, "Read-only material resolver populated native cache");
          Object nativeMaterial =
              invoke(owner, null, "forName", new Class<?>[] {String.class}, key);
          ItemStack nativeStack =
              (ItemStack)
                  invoke(
                      owner, nativeMaterial, "newStack", new Class<?>[] {Item.class}, Items.paper);
          require(
              observed.getItemDamage() == nativeStack.getItemDamage()
                  && observed.getTagCompound().equals(nativeStack.getTagCompound())
                  && resolution.block == block.get(nativeMaterial),
              "Native material cache/alias/default resolution differs for " + key);
          if (!key.equals("stale")) owned.remove(key);
        }
        reader.verify();
        Object original = owned.get("stale");
        try {
          owned.put(
              "stale",
              invoke(owner, null, "forName", new Class<?>[] {String.class}, "replacement"));
          owned.remove("replacement");
          fails("recipe_changed", reader::verify);
        } finally {
          owned.put("stale", original);
        }
        try {
          metadata.setInt(stale, 13);
          fails("recipe_changed", reader::verify);
        } finally {
          metadata.setInt(stale, 12);
        }
        Object prior =
            invoke(
                aliases.getClass(),
                aliases,
                "put",
                new Class<?>[] {Object.class, Object.class},
                "alias.wool",
                "missing");
        try {
          fails("recipe_changed", reader::verify);
        } finally {
          invoke(
              aliases.getClass(),
              aliases,
              "put",
              new Class<?>[] {Object.class, Object.class},
              "alias.wool",
              "fixture.wool");
        }
        reader.verify();
        reader.close();
        reader.close();
        fails("capture_closed", reader::records);
      }
      // Native naming/rendering can populate this memoization cache. Only entries
      // equivalent to the pinned resolver may appear; replacement/drift is rejected.
      owned.put("null-cache", null);
      try (ProjectBlueMaterials reader = new ProjectBlueMaterials()) {
        String frozen = reader.records().toString();
        for (String key : new String[] {"null-cache", "fixture.wool", "lazy-missing"})
          invoke(owner, null, "forName", new Class<?>[] {String.class}, key);
        reader.verify();
        require(
            frozen.equals(reader.records().toString()),
            "Lazy native memoization changed semantic records");
        Object added = owned.get("lazy-missing");
        metadata.setInt(added, 9);
        try {
          fails("recipe_changed", reader::verify);
        } finally {
          metadata.setInt(added, 0);
        }
        owned.remove("lazy-missing");
        fails("recipe_changed", reader::verify);
        owned.put("lazy-missing", added);
      }
      owned.remove("null-cache");
      owned.remove("fixture.wool");
      owned.remove("lazy-missing");
      semanticCases();
      java.util.concurrent.atomic.AtomicInteger calls =
          new java.util.concurrent.atomic.AtomicInteger();
      Class<?> api = type("codechicken.microblock.MicroMaterialRegistry$IMicroMaterial");
      Object foreign =
          java.lang.reflect.Proxy.newProxyInstance(
              api.getClassLoader(),
              new Class<?>[] {api},
              (proxy, method, args) -> {
                calls.incrementAndGet();
                throw new AssertionError("Foreign material callback executed");
              });
      invoke(
          table.getClass(),
          table,
          "put",
          new Class<?>[] {Object.class, Object.class},
          "unknown",
          foreign);
      fails("recipe_unsupported", ProjectBlueMaterials::new);
      require(calls.get() == 0, "Unknown native material callback executed");
      System.out.println(
          "ProjectBlue material lookup: actual native cache priority, single alias hop, default planks, preserved cached name/meta, zero cache writes, drift and unknown callbacks passed");
    } finally {
      set(owner, "cache", cache);
      nativeTypes.set(provider, types);
      nativeRemap.set(provider, remap);
    }
  }

  @SuppressWarnings("unchecked")
  private static void semanticCases() throws Exception {
    Class<?> root = type(ProjectBlueLogic.ROOT),
        controlType = type("gcewing.projectblue.ControlPanelPart$ControlType");
    Map<String, Object> originals = new LinkedHashMap<>();
    Map<String, Item> core = new LinkedHashMap<>();
    for (String name : ProjectBlueLogic.CORE) originals.put(name, field(root, null, name));
    Map<String, Object> cache =
        (Map<String, Object>) field(type(ProjectBlueMaterials.MATERIAL), null, "cache");
    Map<String, Object> cacheCopy = new HashMap<>(cache);
    Item saw = NativeCoreFixesTest.item("codechicken.microblock.ItemSaw", "pb_saw");
    saw.setMaxDamage(5);
    try {
      for (String name : ProjectBlueLogic.CORE) {
        String itemClass =
            name.equals("controlPanelItem")
                ? "gcewing.projectblue.ControlPanelItem"
                : name.startsWith("miniature")
                    ? "gcewing.projectblue.ControlItem"
                    : name.equals("sprayCan")
                        ? "gcewing.projectblue.SprayCanItem"
                        : name.equals("itemMicroPart")
                            ? "codechicken.microblock.ItemMicroPart"
                            : "net.minecraft.item.Item";
        Item item = NativeCoreFixesTest.item(itemClass, "pb_" + name);
        core.put(name, item);
        set(root, name, item);
        if (name.startsWith("miniature")) {
          String kind =
              name.equals("miniatureLever")
                  ? "LEVER"
                  : name.equals("miniatureButton")
                      ? "BUTTON"
                      : name.equals("miniatureLamp") ? "LAMP" : "BLANK";
          java.lang.reflect.Field type = type(itemClass).getDeclaredField("type");
          type.setAccessible(true);
          type.set(item, Enum.valueOf((Class) controlType, kind));
        }
      }
      try (ProjectBlueMaterials materials = new ProjectBlueMaterials()) {
        ProjectBlueLogic logic = new ProjectBlueLogic(core, materials);
        com.google.gson.JsonArray cases = new com.google.gson.JsonArray();
        for (String mode : new String[] {"panel", "miniature", "paint"}) {
          String suffix =
              mode.equals("panel")
                  ? "CraftControlPanel"
                  : mode.equals("miniature") ? "CraftMiniatureItem" : "PaintControl";
          java.lang.reflect.Constructor<?> ctor =
              type(ProjectBlueLogic.RULE + suffix).getDeclaredConstructor();
          ctor.setAccessible(true);
          IRecipe nativeRule = (IRecipe) ctor.newInstance();
          List<ItemStack[]> examples = new ArrayList<>();
          if (mode.equals("paint")) {
            for (net.minecraft.nbt.NBTBase color :
                new net.minecraft.nbt.NBTBase[] {
                  null,
                  new net.minecraft.nbt.NBTTagInt(7),
                  new net.minecraft.nbt.NBTTagLong(4294967303L),
                  new net.minecraft.nbt.NBTTagDouble(-2.5),
                  new net.minecraft.nbt.NBTTagFloat(9.75f),
                  new net.minecraft.nbt.NBTTagDouble(Double.NaN),
                  new net.minecraft.nbt.NBTTagString("7")
                }) {
              ItemStack[] cells = new ItemStack[9];
              cells[0] = new ItemStack(core.get("miniatureLever"), 4, 17);
              cells[1] = new ItemStack(core.get("sprayCan"), 2, 255);
              cells[0].setTagCompound(new NBTTagCompound());
              cells[0].getTagCompound().setString("discard", "old");
              if (color != null) {
                cells[1].setTagCompound(new NBTTagCompound());
                cells[1].getTagCompound().setTag("color", color);
              }
              cells[8] = new ItemStack(Items.diamond, 9);
              examples.add(cells);
            }
            ItemStack[] no = new ItemStack[9];
            no[0] = new ItemStack(core.get("miniatureLamp"));
            no[1] = new ItemStack(core.get("sprayCan"));
            examples.add(no);
          } else {
            for (String key :
                new String[] {
                  null, "fixture.wool", "alias.wool", "alias.chain", "missing", "stale", ""
                }) {
              ItemStack[] cells = new ItemStack[9];
              cells[0] = new ItemStack(saw, 3, 6);
              cells[4] =
                  new ItemStack(core.get("itemMicroPart"), 4, mode.equals("panel") ? 257 : 1);
              cells[8] = new ItemStack(Items.diamond, 9);
              if (key != null) {
                cells[4].setTagCompound(new NBTTagCompound());
                cells[4].getTagCompound().setString("mat", key);
                cells[4].getTagCompound().setString("discard", "old");
              }
              examples.add(cells);
            }
            ItemStack[] wrongType = new ItemStack[9];
            wrongType[0] = new ItemStack(saw);
            wrongType[4] =
                new ItemStack(core.get("itemMicroPart"), 1, mode.equals("panel") ? 257 : 1);
            wrongType[4].setTagCompound(new NBTTagCompound());
            wrongType[4].getTagCompound().setInteger("mat", 7);
            examples.add(wrongType);
            if (mode.equals("miniature"))
              for (ItemStack input :
                  new ItemStack[] {
                    new ItemStack(net.minecraft.init.Blocks.lever, 5, 9),
                    new ItemStack(net.minecraft.init.Blocks.stone_button),
                    new ItemStack(net.minecraft.init.Blocks.wooden_button),
                    new ItemStack(core.get("itemPartFixture"), 4, 32767)
                  }) {
                ItemStack[] cells = new ItemStack[9];
                cells[0] = new ItemStack(saw);
                cells[4] = input;
                examples.add(cells);
              }
            ItemStack[] occupied = examples.get(1).clone();
            occupied[1] = new ItemStack(Items.paper);
            examples.add(occupied);
          }
          int index = 0;
          for (ItemStack[] cells : examples) {
            ProjectBlueLogic.Outcome modeled = logic.evaluate(mode, cells);
            String phase = "match";
            ItemStack actual = null;
            boolean matched = false;
            Throwable error = null;
            InventoryCrafting grid =
                new InventoryCrafting(
                    new Container() {
                      public boolean canInteractWith(EntityPlayer p) {
                        return false;
                      }
                    },
                    3,
                    3);
            for (int i = 0; i < 9; i++)
              grid.setInventorySlotContents(i, cells[i] == null ? null : cells[i].copy());
            try {
              matched = nativeRule.matches(grid, null);
              if (matched) {
                phase = "result";
                actual = nativeRule.getCraftingResult(grid);
              }
            } catch (Throwable failure) {
              error = failure;
            } finally {
              cache.clear();
              cache.putAll(cacheCopy);
            }
            if (error != null)
              require(
                  error instanceof NullPointerException
                      && modeled.status.equals("error")
                      && modeled.phase.equals(phase),
                  "Unmodeled native ProjectBlue failure: " + mode + " " + index + " " + error);
            else if (!matched)
              require(
                  modeled.status.equals("unmatched"),
                  "Native unmatched ProjectBlue input was admitted");
            else
              require(
                  modeled.output != null && ItemStack.areItemStacksEqual(modeled.output, actual),
                  "Native ProjectBlue result differs: " + mode + " " + index);
            com.google.gson.JsonArray inputs = new com.google.gson.JsonArray();
            for (ItemStack stack : cells)
              inputs.add(
                  stack == null ? com.google.gson.JsonNull.INSTANCE : ProjectBlueLogic.raw(stack));
            cases.add(
                com.github.dcysteine.nesql.exporter.source.Json.object(
                    "label",
                    mode + "-" + index++,
                    "mode",
                    mode,
                    "inputs",
                    inputs,
                    "materialKey",
                    ProjectBlueLogic.materialKey(mode, cells),
                    "outcome",
                    modeled.json()));
            materials.verify();
          }
        }
        com.google.gson.JsonObject coreJson = new com.google.gson.JsonObject();
        for (Map.Entry<String, Item> entry : core.entrySet())
          coreJson.addProperty(
              entry.getKey(), Item.itemRegistry.getNameForObject(entry.getValue()));
        com.google.gson.JsonArray roles = new com.google.gson.JsonArray();
        roles.add(
            com.github.dcysteine.nesql.exporter.source.Json.object(
                "registry",
                Item.itemRegistry.getNameForObject(saw),
                "roles",
                com.github.dcysteine.nesql.exporter.source.Json.array("saw")));
        for (String[] row :
            new String[][] {
              {"miniatureLever", "lever"}, {"miniatureButton", "button"}, {"sprayCan", "spray"}
            })
          roles.add(
              com.github.dcysteine.nesql.exporter.source.Json.object(
                  "registry",
                  Item.itemRegistry.getNameForObject(core.get(row[0])),
                  "roles",
                  com.github.dcysteine.nesql.exporter.source.Json.array(row[1])));
        for (String[] row :
            new String[][] {
              {"minecraft:lever", "fullLever"},
              {"minecraft:stone_button", "stoneButton"},
              {"minecraft:wooden_button", "woodButton"}
            })
          roles.add(
              com.github.dcysteine.nesql.exporter.source.Json.object(
                  "registry",
                  row[0],
                  "roles",
                  com.github.dcysteine.nesql.exporter.source.Json.array(row[1])));
        java.nio.file.Files.createDirectories(java.nio.file.Paths.get("build/native-tests"));
        java.nio.file.Files.write(
            java.nio.file.Paths.get("build/native-tests/native-projectblue-logic-contract.json"),
            com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(
                com.github.dcysteine.nesql.exporter.source.Json.object(
                    "core",
                    coreJson,
                    "materials",
                    materials.records(),
                    "items",
                    roles,
                    "cases",
                    cases)));
        readerCases(core, saw);
        System.out.println(
            "ProjectBlue native three-rule callback parity: "
                + cases.size()
                + " cases, checked/ignored cells, count, material NBT, numeric color coercion and null-material failures passed");
      }
    } finally {
      for (Map.Entry<String, Object> entry : originals.entrySet())
        set(root, entry.getKey(), entry.getValue());
      cache.clear();
      cache.putAll(cacheCopy);
    }
  }

  private static com.google.gson.JsonObject origin() {
    return com.github.dcysteine.nesql.exporter.source.Json.object(
        "owner",
        "ProjectBlue",
        "handler",
        "gcewing.projectblue.nei.NEIRecipeHandler",
        "key",
        "projectblue");
  }

  @SuppressWarnings("unchecked")
  private static void readerCases(Map<String, Item> core, Item saw) throws Exception {
    Class<?> rules = type("gcewing.projectblue.ControlPanelRecipes");
    Object original = field(rules, null, "recipes");
    List<Object> crafting =
        net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList();
    List<Object> saved = new ArrayList<>(crafting);
    List<ItemStack> panel = codechicken.nei.ItemList.items;
    boolean ready = codechicken.nei.ItemList.loadFinished;
    try {
      List<Object> definitions = new ArrayList<>();
      for (String name : new String[] {"CraftControlPanel", "CraftMiniatureItem", "PaintControl"}) {
        java.lang.reflect.Constructor<?> ctor =
            type(ProjectBlueLogic.RULE + name).getDeclaredConstructor();
        ctor.setAccessible(true);
        definitions.add(ctor.newInstance());
      }
      set(rules, "recipes", definitions);
      crafting.addAll(definitions);
      List<ItemStack> specimens = new ArrayList<>();
      specimens.add(new ItemStack(saw, 1, 2));
      for (int meta : new int[] {1, 257}) {
        ItemStack stack = new ItemStack(core.get("itemMicroPart"), 1, meta);
        stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setString("mat", "fixture.wool");
        specimens.add(stack);
      }
      for (String name : new String[] {"miniatureLever", "miniatureButton", "itemPartFixture"})
        specimens.add(new ItemStack(core.get(name), 1, 4));
      ItemStack spray = new ItemStack(core.get("sprayCan"), 1, 255);
      spray.setTagCompound(new NBTTagCompound());
      spray.getTagCompound().setInteger("color", 7);
      specimens.add(spray);
      specimens.add(new ItemStack(net.minecraft.init.Blocks.lever));
      specimens.add(new ItemStack(Items.diamond));
      codechicken.nei.ItemList.items = specimens;
      codechicken.nei.ItemList.loadFinished = true;
      TemplateRecipeHandler handler =
          (TemplateRecipeHandler) type("gcewing.projectblue.nei.NEIRecipeHandler").newInstance();
      TemplateRecipeHandler.CachedRecipe sentinel =
          handler.new CachedRecipe() {
            public codechicken.nei.PositionedStack getResult() {
              throw new AssertionError("GUI cache read");
            }
          };
      handler.arecipes.add(sentinel);
      try (ProjectBlueRecipes reader = new ProjectBlueRecipes(handler, origin())) {
        require(
            reader.counts().get("rules").getAsInt() == 3
                && reader.counts().get("examples").getAsInt() >= 6,
            "Native ProjectBlue source lost rules/examples");
        require(
            handler.arecipes.size() == 1 && handler.arecipes.get(0) == sentinel,
            "Reader changed native GUI cache");
        com.google.gson.JsonArray itemRows = new com.google.gson.JsonArray();
        for (Map.Entry<String, ItemStack> entry : reader.specimenCopies().entrySet()) {
          com.google.gson.JsonObject raw = RecipeRow.stackEvidence(entry.getValue());
          raw.remove("amount");
          raw.addProperty("id", entry.getKey());
          raw.add("tags", com.github.dcysteine.nesql.exporter.source.Json.array());
          itemRows.add(raw);
        }
        java.nio.file.Files.write(
            java.nio.file.Paths.get(
                "build/native-tests/native-projectblue-reference-contract.json"),
            com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(
                com.github.dcysteine.nesql.exporter.source.Json.object(
                    "references", reader.records(), "items", itemRows)));
        // Names/icons use separate game capture. Exercise the actual row transport
        // with already known item facts, as in the other native reference fixtures.
        Facts facts = new Facts("en_US");
        ((Set<String>) field(facts, "items")).addAll(reader.specimenCopies().keySet());
        for (int index = 0; index < reader.size(); index++) reader.capture(index, facts);
        com.google.gson.JsonArray transported = new com.google.gson.JsonArray();
        for (Facts.Record record : facts.drain().records)
          if (record.kind.equals("references")) transported.add(record.value);
        require(
            transported.equals(reader.records()),
            "ProjectBlue transport differs from the validated native records");
        reader.finishVerification();
        Object old = definitions.get(1);
        definitions.set(1, definitions.get(0));
        try {
          fails("recipe_changed", reader::finishVerification);
        } finally {
          definitions.set(1, old);
        }
        ItemStack first = specimens.get(0);
        specimens.set(0, first.copy());
        try {
          fails("recipe_changed", reader::finishVerification);
        } finally {
          specimens.set(0, first);
        }
        reader.close();
        reader.close();
        fails("capture_closed", reader::domain);
      }
      System.out.println(
          "ProjectBlue nonempty reader: native registration/crafting bindings, GUI cache sentinel, concrete examples, source drift and owned close passed");
    } finally {
      set(rules, "recipes", original);
      crafting.clear();
      crafting.addAll(saved);
      codechicken.nei.ItemList.items = panel;
      codechicken.nei.ItemList.loadFinished = ready;
    }
  }

  public static final class SubtypeProbe extends Item {
    int calls;

    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
      calls++;
      throw new AssertionError("ProjectBlue entered unrelated subtype callback");
    }
  }

  private static void fails(String code, Runnable action) {
    try {
      action.run();
      throw new AssertionError("Expected " + code);
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault error) {
      require(error.code.equals(code), "Expected " + code + ", got " + error.code);
    }
  }

  private static void set(Class<?> owner, String name, Object value) throws Exception {
    java.lang.reflect.Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, value);
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
