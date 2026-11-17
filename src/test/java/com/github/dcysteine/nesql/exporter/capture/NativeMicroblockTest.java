package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;

/**
 * Actual XU recipe plus actual FMP material lookup: metadata omission and first-NBT copy are
 * intentional.
 */
final class NativeMicroblockTest {
  static void run() throws Exception {
    NativeCoreFixesTest.version("ExtraUtilities", "1.2.12");
    NativeCoreFixesTest.version("ForgeMultipart", "1.7.2");
    TemplateRecipeHandler handler =
        (TemplateRecipeHandler) type("com.rwtema.extrautils.nei.MicroBlocksHandler").newInstance();
    require(
        Recipes.adapter(handler) != null,
        "XU microblock handler lacks native material/template semantics");
    try (RegistryRecipes empty = new XuMicroblockRecipes(handler)) {
      require(empty.size() == 0, "Ready-empty XU native registry was not empty");
      empty.verify();
      empty.close();
      empty.close();
      expect("capture_closed", empty::size);
    }
    TemplateRecipeHandler fmpHandler =
        (TemplateRecipeHandler)
            type("com.rwtema.extrautils.nei.FMPMicroBlocksHandler").newInstance();
    require(
        Recipes.adapter(fmpHandler) != null,
        "FMP microblock handler lacks procedural branch adapter");
    Object registry = field(type("codechicken.microblock.MicroMaterialRegistry$"), null, "MODULE$");
    Class<?> mat = type("codechicken.microblock.MicroMaterialRegistry$IMicroMaterial");
    final int[] materialCalls = {0};
    for (int n = 0; n < 2; n++) {
      final ItemStack block = new ItemStack(n == 0 ? Blocks.dirt : Blocks.stone);
      Object material =
          Proxy.newProxyInstance(
              mat.getClassLoader(),
              new Class<?>[] {mat},
              (p, m, a) -> {
                if (m.getName().equals("getItem")) {
                  materialCalls[0]++;
                  return block.copy();
                }
                if (m.getName().equals("getCutterStrength")) {
                  materialCalls[0]++;
                  return 1;
                }
                if (m.getName().equals("toString")) return "fixture-material";
                throw new AssertionError("Unexpected native callback " + m);
              });
      invoke(
          registry.getClass(),
          registry,
          "registerMaterial",
          new Class<?>[] {mat, String.class},
          material,
          n == 0 ? "a_dirt" : "b_stone");
    }
    invoke(registry.getClass(), registry, "setupIDMap", new Class<?>[0]);
    net.minecraft.item.Item micro =
        NativeCoreFixesTest.item("codechicken.microblock.ItemMicroPart", "micro_part");
    Object proxy = field(type("codechicken.microblock.handler.MicroblockProxy$"), null, "MODULE$");
    invoke(proxy.getClass(), proxy, "itemMicro_$eq", new Class<?>[] {micro.getClass()}, micro);
    materialLookupCases(micro);
    nativeNbtTextCases();
    net.minecraft.item.Item saw =
        NativeCoreFixesTest.item("codechicken.microblock.ItemSaw", "micro_saw");
    Field level = saw.getClass().getDeclaredField("harvestLevel");
    level.setAccessible(true);
    level.setInt(saw, 3);
    saw.setMaxDamage(100);
    invoke(
        proxy.getClass(),
        proxy,
        "sawDiamond_$eq",
        new Class<?>[] {net.minecraft.item.Item.class},
        saw);
    invoke(registry.getClass(), registry, "calcMaxCuttingStrength", new Class<?>[0]);
    sourceCases(registry, materialCalls);
    harvestCases();
    materialSnapshotCases();
    net.minecraft.item.crafting.CraftingManager.getInstance()
        .getRecipeList()
        .add(field(type("codechicken.microblock.MicroRecipe$"), null, "MODULE$"));
    int beforeUnknown = materialCalls[0];
    expect("recipe_unsupported", () -> new FmpMicroblockRecipes(fmpHandler));
    require(
        materialCalls[0] == beforeUnknown,
        "FMP production reader invoked unknown material callbacks before rejecting them");
    // Replace isolated fixture proxies with actual installed native material instances.
    Object[] nativeIds =
        (Object[]) field(registry, "codechicken$microblock$MicroMaterialRegistry$$idMap");
    Object nativeTypes = field(registry, "codechicken$microblock$MicroMaterialRegistry$$typeMap");
    for (int id = 0; id < nativeIds.length; id++) {
      Object tuple = nativeIds[id];
      String name = (String) field(tuple, "_1");
      net.minecraft.block.Block block = id == 0 ? Blocks.dirt : Blocks.stone;
      int[] harvest = (int[]) field(net.minecraft.block.Block.class, block, "harvestLevel");
      Arrays.fill(harvest, 1);
      Object actual =
          type("codechicken.microblock.BlockMicroMaterial")
              .getConstructor(net.minecraft.block.Block.class, int.class)
              .newInstance(block, 0);
      nativeIds[id] =
          type("scala.Tuple2").getConstructor(Object.class, Object.class).newInstance(name, actual);
      invoke(
          nativeTypes.getClass(),
          nativeTypes,
          "put",
          new Class<?>[] {Object.class, Object.class},
          name,
          actual);
    }
    rulesParityCases(micro, saw);
    RegistryRecipes fmp = new FmpMicroblockRecipes(fmpHandler);
    require(fmp.size() > 0, "FMP native material branches were empty");
    ItemStack full = new ItemStack(Blocks.dirt), slice = new ItemStack(micro, 2, 4);
    slice.setTagInfo("mat", new net.minecraft.nbt.NBTTagString("a_dirt"));
    RecipeRow fmpRow =
        NativeCoreFixesTest.row(full, slice, new ItemStack(saw), new ItemStack(Blocks.stone));
    fmp.program(fmpRow.facts);
    require(fmp.capture(0, fmpRow), "FMP material zero thinning was discarded");
    require(fmpHandler.arecipes.isEmpty(), "FMP capture still mutates the NEI GUI recipe cache");
    require(
        fmpRow.record.getAsJsonObject("process").get("kind").getAsString().equals("fmpMicroblock"),
        "FMP procedure flattened into fixed crafting");
    fmpRow.record.add(
        "source",
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner",
            "ExtraUtilities",
            "handler",
            fmpHandler.getClass().getName(),
            "key",
            "fmp-microblock"));
    fmpRow.record.addProperty(
        "category",
        com.github.dcysteine.nesql.exporter.source.Identity.content(
            "category", fmpRow.record.getAsJsonObject("source")));
    NativeContractFixture.writeOwnedWithDurability(
        "fmp-microblock",
        fmpRow,
        Arrays.asList(full, slice, new ItemStack(saw), new ItemStack(Blocks.stone)));
    fmp.verify();
    List<ItemStack> specimens=new ArrayList<>(Arrays.asList(full,slice,new ItemStack(saw),new ItemStack(Blocks.stone)));
    for(String name:Arrays.asList("a_dirt","b_stone"))for(int cls=0;cls<4;cls++)for(int size:new int[]{1,2,4}){
      ItemStack part=new ItemStack(micro,1,(cls<<8)|size);
      part.setTagInfo("mat",new net.minecraft.nbt.NBTTagString(name));specimens.add(part);
    }
    ItemStack missingPart=new ItemStack(micro,1,260);specimens.add(missingPart);
    Map<String,ItemStack> originals=new HashMap<>();
    for(ItemStack specimen:specimens)originals.put(com.github.dcysteine.nesql.exporter.source.Identity.item(
        net.minecraft.item.Item.itemRegistry.getNameForObject(specimen.getItem()),Items.feather.getDamage(specimen),
        com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(specimen.getTagCompound())),specimen);
    boolean missingWritten = false;
    for (int index = 0; index < fmp.size(); index++) {
      RecipeRow missingRow = NativeCoreFixesTest.row(specimens.toArray(new ItemStack[0]));
      fmp.program(missingRow.facts);
      fmp.capture(index, missingRow);
      InventoryCrafting inventory=new InventoryCrafting(new Container(){
        public boolean canInteractWith(EntityPlayer player){return false;}
      },3,3);
      com.google.gson.JsonArray layout=missingRow.record.getAsJsonObject("process").getAsJsonArray("layout");
      boolean missing=false;
      for(int cell=0;cell<9;cell++)if(!layout.get(cell).isJsonNull()){
        int slot=layout.get(cell).getAsInt();
        String id=missingRow.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("id").getAsString();
        ItemStack input=originals.get(id);require(input!=null,"FMP emitted an undeclared native specimen");
        inventory.setInventorySlotContents(cell,input.copy());
        missing|=input.getItem()==micro&&input.getTagCompound()==null;
      }
      ItemStack expected=((IRecipe)field(type("codechicken.microblock.MicroRecipe$"),null,"MODULE$")).getCraftingResult(inventory);
      require(expected!=null,"FMP emitted a native-unmatched grid");
      String expectedId=com.github.dcysteine.nesql.exporter.source.Identity.item(
          net.minecraft.item.Item.itemRegistry.getNameForObject(expected.getItem()),Items.feather.getDamage(expected),
          com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(expected.getTagCompound()));
      com.google.gson.JsonObject output=missingRow.outputs.get(0).getAsJsonObject();
      require(expectedId.equals(output.get("id").getAsString())&&Integer.toString(expected.stackSize).equals(output.get("amount").getAsString()),
          "FMP emitted grid/result differs from native MicroRecipe");
      if(!missing||missingWritten)continue;
      missingRow.record.add(
          "source",
          com.github.dcysteine.nesql.exporter.source.Json.object(
              "owner",
              "ExtraUtilities",
              "handler",
              fmpHandler.getClass().getName(),
              "key",
              "fmp-missing-material"));
      missingRow.record.addProperty(
          "category",
          com.github.dcysteine.nesql.exporter.source.Identity.content(
              "category", missingRow.record.getAsJsonObject("source")));
      NativeContractFixture.writeOwnedWithDurability("fmp-missing-material", missingRow, specimens);
      missingWritten = true;
    }
    require(missingWritten, "FMP producer lacks a native ID-zero/no-mat branch witness");
    // Native maximum records saws present at the last calculation. A later
    // registration does not recompute it.
    net.minecraft.item.Item stronger =
        NativeCoreFixesTest.item("codechicken.microblock.ItemSaw", "micro_saw_stronger");
    level.setInt(stronger, 5);
    stronger.setMaxDamage(100);
    ItemStack[] strongerCells = new ItemStack[9];
    strongerCells[0] = new ItemStack(stronger);
    strongerCells[3] = full.copy();
    InventoryCrafting strongerGrid =
        new InventoryCrafting(
            new Container() {
              public boolean canInteractWith(EntityPlayer p) {
                return false;
              }
            },
            3,
            3);
    for (int cell = 0; cell < 9; cell++)
      if (strongerCells[cell] != null)
        strongerGrid.setInventorySlotContents(cell, strongerCells[cell]);
    require(
        invoke(
                type("codechicken.microblock.MicroRecipe"),
                null,
                "getCraftingResult",
                new Class<?>[] {InventoryCrafting.class},
                strongerGrid)
            != null,
        "Native stronger external saw should work without recalculating the cached maximum");
    try (RegistryRecipes withStronger = new FmpMicroblockRecipes(fmpHandler)) {
      require(withStronger.size() > fmp.size(), "FMP lost a legal stronger external saw");
      RecipeRow strongerRow =
          NativeCoreFixesTest.row(full, slice, new ItemStack(saw), new ItemStack(Blocks.stone));
      withStronger.program(strongerRow.facts);
      require(
          withStronger.capture(0, strongerRow),
          "FMP stronger-saw source rejected a valid native example");
      strongerRow.record.add(
          "source",
          com.github.dcysteine.nesql.exporter.source.Json.object(
              "owner",
              "ExtraUtilities",
              "handler",
              fmpHandler.getClass().getName(),
              "key",
              "fmp-stronger-saw"));
      strongerRow.record.addProperty(
          "category",
          com.github.dcysteine.nesql.exporter.source.Identity.content(
              "category", strongerRow.record.getAsJsonObject("source")));
      NativeContractFixture.writeOwnedWithDurability(
          "fmp-stronger-saw",
          strongerRow,
          Arrays.asList(full, slice, new ItemStack(saw), new ItemStack(Blocks.stone)));
    }
    type("com.rwtema.extrautils.multipart.microblock.RecipeMicroBlocks")
        .getField("microID")
        .set(null, micro);
    ItemStack piece = new ItemStack(micro, 1, 513);
    piece.setTagInfo("mat", new net.minecraft.nbt.NBTTagString("b_stone"));
    piece.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("copied"));
    Object[] template =
        new Object[] {1, null, null, null, new ItemStack(Items.redstone), null, null, null, null};
    IRecipe recipe =
        (IRecipe)
            type("com.rwtema.extrautils.multipart.microblock.RecipeMicroBlocks")
                .getConstructor(int.class, int.class, Object[].class, ItemStack.class)
                .newInstance(3, 3, template, new ItemStack(Items.paper, 8, 2));
    xuMicroItemCases(recipe, micro);
    xuLogicCases(micro);
    InventoryCrafting grid =
        new InventoryCrafting(
            new Container() {
              public boolean canInteractWith(EntityPlayer p) {
                return false;
              }
            },
            3,
            3);
    grid.setInventorySlotContents(0, piece.copy());
    grid.setInventorySlotContents(4, new ItemStack(Items.redstone));
    require(recipe.matches(grid, null), "Native nonzero shape accidentally checks shape metadata");
    ItemStack output = recipe.getCraftingResult(grid);
    require(
        output.getTagCompound().getString("owner").equals("copied"),
        "Native XU output lost entire first micro NBT");
    grid.getStackInSlot(0).setTagInfo("mat", new net.minecraft.nbt.NBTTagString("a_dirt"));
    require(!recipe.matches(grid, null), "Native material zero unexpectedly accepted");
    net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList().add(recipe);
    RegistryRecipes adapter = new XuMicroblockRecipes(handler);
    require(adapter.size() == 1, "Native zero material was not excluded from XU expansion");
    ItemStack canonical = piece.copy();
    canonical.setItemDamage(1);
    canonical.getTagCompound().removeTag("owner");
    grid.setInventorySlotContents(0, canonical);
    ItemStack example = recipe.getCraftingResult(grid);
    RecipeRow row =
        NativeCoreFixesTest.row(
            canonical,
            example,
            new ItemStack(Items.paper, 8, 2),
            new ItemStack(Blocks.dirt),
            new ItemStack(Blocks.stone),
            new ItemStack(Items.redstone));
    adapter.program(row.facts);
    adapter.capture(0, row);
    require(handler.arecipes.isEmpty(), "XU native capture still writes the NEI GUI recipe cache");
    require(
        row.outputs
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("change")
            .getAsJsonObject("action")
            .get("kind")
            .getAsString()
            .equals("xuResult"),
        "XU first-input NBT was exported as fixed-result crafting");
    row.record.add(
        "source",
        com.github.dcysteine.nesql.exporter.source.Json.object(
            "owner",
            "ExtraUtilities",
            "handler",
            handler.getClass().getName(),
            "key",
            "xu-microblock"));
    row.record.addProperty(
        "category",
        com.github.dcysteine.nesql.exporter.source.Identity.content(
            "category", row.record.getAsJsonObject("source")));
    NativeContractFixture.writeOwnedWithDurability(
        "xu-microblock",
        row,
        Arrays.asList(
            canonical,
            example,
            new ItemStack(Items.paper, 8, 2),
            new ItemStack(Blocks.dirt),
            new ItemStack(Blocks.stone),
            new ItemStack(Items.redstone)));
    adapter.verify();
    Field templateField = recipe.getClass().getDeclaredField("recipeItems");
    templateField.setAccessible(true);
    templateField.set(recipe, template.clone());
    try {
      expect("recipe_changed", adapter::verify);
    } finally {
      templateField.set(recipe, template);
    }
    Object literal = template[4];
    template[4] = ((ItemStack) literal).copy();
    try {
      expect("recipe_changed", adapter::verify);
    } finally {
      template[4] = literal;
    }
    ((ItemStack) template[4]).setItemDamage(7);
    try {
      adapter.verify();
      throw new AssertionError("Native XU template drift accepted");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected) {
      require(expected.code.equals("recipe_changed"), "Wrong microblock drift fault");
    }
    fmp.close();
    adapter.close();
    adapter.close();
    expect("capture_closed", adapter::size);
    fmp.close();
    try {
      fmp.size();
      throw new AssertionError("Closed FMP reader remained usable");
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault error) {
      require(error.code.equals("capture_closed"), error.code);
    }
    require(fmpHandler.arecipes.isEmpty(), "FMP close or later capture wrote the GUI recipe cache");
    xuLiteralFirstFixture(handler, micro);
    xuLiteralScopeFixtures(handler, micro, registry);
    xuNativeBoundaries(handler);
    gemSawCases();
    lightMaterialCases();
    System.out.println("Native XU microblock callback probes passed");
  }

  static void require(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
  }

  private static void xuMicroItemCases(Object recipe, net.minecraft.item.Item micro)
      throws Exception {
    Class<?> owner = type(XuMicroblockSource.RECIPE),
        proxy = type("com.rwtema.extrautils.ExtraUtilsProxy");
    Field cache = owner.getDeclaredField("microID"),
        checked = proxy.getDeclaredField("checked2"),
        item = proxy.getDeclaredField("MicroBlockId");
    cache.setAccessible(true);
    checked.setAccessible(true);
    item.setAccessible(true);
    Object originalCache = cache.get(null), originalItem = item.get(null);
    boolean originalChecked = checked.getBoolean(null);
    try {
      cache.set(null, micro);
      try (XuMicroblockItem binding = new XuMicroblockItem()) {
        require(
            binding.item() == invoke(owner, recipe, "getMicroID", new Class<?>[0]),
            "XU cached micro identity differs");
        cache.set(null, Items.paper);
        expect("recipe_changed", binding::verify);
        cache.set(null, micro);
        binding.verify();
      }
      cache.set(null, null);
      checked.setBoolean(null, true);
      item.set(null, micro);
      try (XuMicroblockItem binding = new XuMicroblockItem()) {
        require(cache.get(null) == null, "XU item binding wrote the native recipe cache");
        require(
            binding.item() == invoke(owner, recipe, "getMicroID", new Class<?>[0]),
            "XU provider micro identity differs");
        binding.verify();
        binding.close();
        binding.close();
        expect("capture_closed", binding::item);
      }
      cache.set(null, null);
      item.set(null, null);
      expect("handler_not_ready", XuMicroblockItem::new);
      System.out.println(
          "XU native micro provider: cached/provider identities, read-only observation, equivalent lazy memoization, missing role, drift and close passed");
    } finally {
      cache.set(null, originalCache);
      item.set(null, originalItem);
      checked.setBoolean(null, originalChecked);
    }
  }

  private static void xuLogicCases(net.minecraft.item.Item micro) throws Exception {
    try (MicroblockMaterialTable materials = new MicroblockMaterialTable()) {
      ItemStack a = new ItemStack(micro, 4, 513);
      a.setTagInfo("mat", new net.minecraft.nbt.NBTTagString("b_stone"));
      a.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("left"));
      ItemStack b = a.copy();
      b.setItemDamage(1029);
      b.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("right"));
      Object[] one = new Object[9];
      one[0] = 1;
      ItemStack[] grid = new ItemStack[9];
      grid[0] = a;
      xuParity(materials, micro, one, grid);
      grid[0] = null;
      grid[2] = a;
      xuParity(materials, micro, one, grid);
      Object[] two = new Object[9];
      two[0] = 1;
      two[2] = 2;
      grid = new ItemStack[9];
      grid[0] = a;
      grid[2] = b;
      xuParity(materials, micro, two, grid);
      Object[] literal = two.clone();
      literal[0] = new ItemStack(micro, 99, 513);
      xuParity(materials, micro, literal, grid);
      grid[0] = new ItemStack(micro, 7, 513);
      xuParity(materials, micro, literal, grid);
      Object[] fixed = new Object[9];
      fixed[0] = new ItemStack(micro, 99, 513);
      grid = new ItemStack[9];
      grid[0] = new ItemStack(micro, 7, 513);
      xuParity(materials, micro, fixed, grid);
      Object[] full = new Object[9];
      full[0] = 0;
      grid = new ItemStack[9];
      grid[0] = new ItemStack(Blocks.stone, 4);
      xuParity(materials, micro, full, grid);
      full[2] = 1;
      grid[2] = a;
      xuParity(materials, micro, full, grid);
      grid[0] = new ItemStack(Blocks.dirt, 4);
      xuParity(materials, micro, full, grid);
      grid = new ItemStack[9];
      grid[0] = a.copy();
      grid[0].getTagCompound().setString("mat", "a_dirt");
      xuParity(materials, micro, one, grid);
      Object[] wildcard = new Object[9];
      wildcard[0] = new ItemStack(Items.paper, 99, 32767);
      grid = new ItemStack[9];
      grid[0] = new ItemStack(Items.paper, 4, 77);
      xuParity(materials, micro, wildcard, grid);
      grid[8] = new ItemStack(Items.redstone);
      xuParity(materials, micro, wildcard, grid);
      materials.finishVerification();
      materials.close();
      expect("capture_closed", materials::materials);
    }
    System.out.println(
        "XU pure matcher helper: 12 installed native mirror/shape-omission/literal-micro/first-tagged/full-block/ID-zero/wildcard/count/NBT cases passed; production transport integration pending");
  }

  private static void xuLiteralFirstFixture(
      TemplateRecipeHandler handler, net.minecraft.item.Item micro) throws Exception {
    ItemStack literal = new ItemStack(micro, 1, 513);
    literal.setTagInfo("mat", new net.minecraft.nbt.NBTTagString("b_stone"));
    literal.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("literal-first"));
    ItemStack part = new ItemStack(micro, 1, 1);
    part.setTagInfo("mat", new net.minecraft.nbt.NBTTagString("b_stone"));
    Object[] template = new Object[9];
    template[0] = literal;
    template[2] = 1;
    ItemStack prototype = new ItemStack(Items.paper, 8, 2);
    IRecipe recipe =
        (IRecipe)
            type(XuMicroblockSource.RECIPE)
                .getConstructor(int.class, int.class, Object[].class, ItemStack.class)
                .newInstance(3, 3, template, prototype);
    net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList().add(recipe);
    InventoryCrafting grid =
        new InventoryCrafting(
            new Container() {
              public boolean canInteractWith(EntityPlayer p) {
                return false;
              }
            },
            3,
            3);
    grid.setInventorySlotContents(0, literal.copy());
    grid.setInventorySlotContents(2, part.copy());
    require(recipe.matches(grid, null), "Installed XU literal-micro witness did not match");
    ItemStack nativeResult = recipe.getCraftingResult(grid);
    grid.setInventorySlotContents(0, part.copy());
    grid.setInventorySlotContents(2, literal.copy());
    require(
        recipe.matches(grid, null), "Installed XU mirrored literal-micro witness did not match");
    ItemStack mirroredResult = recipe.getCraftingResult(grid);
    require(
        !ItemStack.areItemStackTagsEqual(nativeResult, mirroredResult),
        "Fixture must distinguish mirrored first-tagged outputs");
    List<ItemStack> specimens =
        Arrays.asList(
            literal,
            part,
            prototype,
            nativeResult,
            mirroredResult,
            new ItemStack(Blocks.dirt),
            new ItemStack(Blocks.stone));
    try (RegistryRecipes cursor = new XuMicroblockRecipes(handler)) {
      RecipeRow row = NativeCoreFixesTest.row(specimens.toArray(new ItemStack[0]));
      cursor.program(row.facts);
      cursor.capture(cursor.size() - 1, row);
      require(
          row.record
                  .getAsJsonObject("process")
                  .getAsJsonArray("states")
                  .get(0)
                  .getAsJsonObject()
                  .get("firstPart")
                  .getAsInt()
              == 0,
          "XU result incorrectly skipped the first literal micro input");
      row.record.add(
          "source",
          com.github.dcysteine.nesql.exporter.source.Json.object(
              "owner",
              "ExtraUtilities",
              "handler",
              handler.getClass().getName(),
              "key",
              "xu-literal-first"));
      row.record.addProperty(
          "category",
          com.github.dcysteine.nesql.exporter.source.Identity.content(
              "category", row.record.getAsJsonObject("source")));
      NativeContractFixture.writeOwnedWithDurability("xu-literal-first", row, specimens);
      cursor.finishVerification();
    }
  }

  private static void xuLiteralScopeFixtures(
      TemplateRecipeHandler handler, net.minecraft.item.Item micro, Object registry)
      throws Exception {
    List live = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList();
    List saved = new ArrayList(live);
    Field idsField =
        registry.getClass().getDeclaredField("codechicken$microblock$MicroMaterialRegistry$$idMap");
    idsField.setAccessible(true);
    Object savedIds = idsField.get(registry);
    try {
      for (int test = 0; test < 3; test++) {
        live.clear();
        ItemStack literal =
            new ItemStack(test == 0 ? Items.redstone : micro, 7, test == 0 ? 0 : 513);
        ItemStack part = new ItemStack(micro, 1, 1);
        part.setTagInfo("mat", new net.minecraft.nbt.NBTTagString("b_stone"));
        ItemStack prototype = new ItemStack(Items.paper, 8, 2);
        prototype.setTagInfo(
            "prototype", new net.minecraft.nbt.NBTTagString("retained-if-no-tagged-micro"));
        Object[] template = new Object[9];
        template[0] = literal;
        if (test == 1) template[2] = 1;
        ItemStack[] cells = new ItemStack[9];
        cells[0] = literal.copy();
        cells[0].stackSize = 1;
        if (test == 1) cells[2] = part;
        IRecipe recipe =
            (IRecipe)
                type(XuMicroblockSource.RECIPE)
                    .getConstructor(int.class, int.class, Object[].class, ItemStack.class)
                    .newInstance(3, 3, template, prototype);
        live.add(recipe);
        if (test != 1)
          idsField.set(
              registry,
              java.lang.reflect.Array.newInstance(savedIds.getClass().getComponentType(), 0));
        InventoryCrafting grid =
            new InventoryCrafting(
                new Container() {
                  public boolean canInteractWith(EntityPlayer p) {
                    return false;
                  }
                },
                3,
                3);
        List<ItemStack> specimens =
            new ArrayList<>(
                Arrays.asList(
                    literal,
                    part,
                    prototype,
                    new ItemStack(Blocks.dirt),
                    new ItemStack(Blocks.stone)));
        List<ItemStack> expected = new ArrayList<>();
        for (boolean mirrored : new boolean[] {false, true}) {
          for (int cell = 0; cell < 9; cell++) grid.setInventorySlotContents(cell, null);
          for (int cell = 0; cell < 9; cell++)
            grid.setInventorySlotContents(
                mirrored ? cell / 3 * 3 + 2 - cell % 3 : cell, cells[cell]);
          require(recipe.matches(grid, null), "Native literal-scope witness rejected");
          ItemStack result = recipe.getCraftingResult(grid);
          specimens.add(result);
          expected.add(result);
        }
        try (RegistryRecipes cursor = new XuMicroblockRecipes(handler)) {
          require(
              cursor.size() == 1,
              "XU literal-scope template was multiplied by materials or lost with empty material table");
          RecipeRow row = NativeCoreFixesTest.row(specimens.toArray(new ItemStack[0]));
          cursor.program(row.facts);
          cursor.capture(0, row);
          com.google.gson.JsonArray states =
              row.record.getAsJsonObject("process").getAsJsonArray("states");
          for (int state = 0; state < 2; state++)
            require(
                states
                    .get(state)
                    .getAsJsonObject()
                    .getAsJsonObject("output")
                    .get("id")
                    .getAsString()
                    .equals(row.facts.item(expected.get(state))),
                "XU state lost native first-tagged/prototype result");
          String key =
              test == 0 ? "xu-literal-only" : test == 1 ? "xu-untagged-first" : "xu-untagged-only";
          row.record.add(
              "source",
              com.github.dcysteine.nesql.exporter.source.Json.object(
                  "owner", "ExtraUtilities", "handler", handler.getClass().getName(), "key", key));
          row.record.addProperty(
              "category",
              com.github.dcysteine.nesql.exporter.source.Identity.content(
                  "category", row.record.getAsJsonObject("source")));
          NativeContractFixture.writeOwnedWithDurability(key, row, specimens);
          cursor.finishVerification();
        } finally {
          idsField.set(registry, savedIds);
        }
      }
    } finally {
      live.clear();
      live.addAll(saved);
      idsField.set(registry, savedIds);
    }
  }

  private static void xuNativeBoundaries(TemplateRecipeHandler handler) throws Exception {
    List live = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList(),
        saved = new ArrayList(live);
    com.google.common.collect.ListMultimap<net.minecraft.item.Item, ItemStack> oldMap =
        codechicken.nei.ItemList.itemMap;
    boolean oldReady = codechicken.nei.ItemList.loadFinished;
    try {
      live.clear();
      Object[] cells = new Object[10];
      cells[0] = new ItemStack(Items.redstone, 4, 32767);
      cells[9] = new ItemStack(Items.coal);
      IRecipe recipe =
          (IRecipe)
              type(XuMicroblockSource.RECIPE)
                  .getConstructor(int.class, int.class, Object[].class, ItemStack.class)
                  .newInstance(3, 3, cells, new ItemStack(Items.paper));
      live.add(recipe);
      codechicken.nei.ItemList.itemMap = com.google.common.collect.ArrayListMultimap.create();
      codechicken.nei.ItemList.loadFinished = true;
      expect("recipe_unsupported", () -> new XuMicroblockRecipes(handler));
      ItemStack observed = new ItemStack(Items.redstone, 3, 7);
      codechicken.nei.ItemList.itemMap.put(Items.redstone, observed);
      try (RegistryRecipes cursor = new XuMicroblockRecipes(handler)) {
        RecipeRow row =
            NativeCoreFixesTest.row(
                new ItemStack(Items.redstone, 1, 7), new ItemStack(Items.paper));
        cursor.program(row.facts);
        cursor.capture(0, row);
        require(
            row.record
                    .getAsJsonObject("process")
                    .getAsJsonArray("cells")
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonObject("stack")
                    .get("meta")
                    .getAsInt()
                == 32767,
            "XU wildcard source became concrete");
        require(
            row.inputs.size() == 1
                && row.inputs
                    .get(0)
                    .getAsJsonObject()
                    .getAsJsonArray("choices")
                    .get(0)
                    .getAsJsonObject()
                    .get("id")
                    .getAsString()
                    .equals(row.facts.item(new ItemStack(Items.redstone, 1, 7))),
            "XU invented a metadata-zero witness or consumed unused array tail");
        Object tail = cells[9];
        cells[9] = ((ItemStack) tail).copy();
        try {
          expect("recipe_changed", cursor::verify);
        } finally {
          cells[9] = tail;
        }
        observed.setItemDamage(8);
        try {
          expect("recipe_changed", cursor::verify);
        } finally {
          observed.setItemDamage(7);
        }
        cursor.finishVerification();
      }
      live.clear();
      Object unreachable =
          type(XuMicroblockSource.RECIPE)
              .getConstructor(int.class, int.class, Object[].class, ItemStack.class)
              .newInstance(4, 3, new Object[12], new ItemStack(Items.paper));
      live.add(unreachable);
      Field micro = type(XuMicroblockSource.RECIPE).getField("microID");
      Object original = micro.get(null);
      micro.set(null, null);
      try (RegistryRecipes cursor = new XuMicroblockRecipes(handler)) {
        require(
            cursor.size() == 1 && cursor.exclusion(0) != null,
            "XU impossible native dimensions lost scoped exclusion");
        require(
            XuMicroblockSource.registrationAnchors().length == 2,
            "Unreachable XU dimensions incorrectly require native micro readiness");
      } finally {
        micro.set(null, original);
      }
      live.clear();
      live.add(
          type(XuMicroblockSource.RECIPE)
              .getConstructor(int.class, int.class, Object[].class, ItemStack.class)
              .newInstance(2, 3, new Object[6], new ItemStack(Items.paper)));
      expect("recipe_unsupported", () -> new XuMicroblockRecipes(handler));
    } finally {
      live.clear();
      live.addAll(saved);
      codechicken.nei.ItemList.itemMap = oldMap;
      codechicken.nei.ItemList.loadFinished = oldReady;
    }
  }

  private static void xuParity(
      MicroblockMaterialTable materials,
      net.minecraft.item.Item micro,
      Object[] cells,
      ItemStack[] offered)
      throws Exception {
    ItemStack prototype = new ItemStack(Items.paper, 8, 2);
    prototype.setTagInfo("prototype", new net.minecraft.nbt.NBTTagInt(1));
    Object recipe =
        type(XuMicroblockSource.RECIPE)
            .getConstructor(int.class, int.class, Object[].class, ItemStack.class)
            .newInstance(3, 3, cells, prototype);
    XuMicroblockSource.Entry source = new XuMicroblockSource.Entry(recipe, 0);
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
      if (offered[i] != null) grid.setInventorySlotContents(i, offered[i].copy());
    boolean matched =
        (Boolean)
            invoke(
                recipe.getClass(),
                recipe,
                "matches",
                new Class<?>[] {InventoryCrafting.class, net.minecraft.world.World.class},
                grid,
                null);
    try (XuMicroblockLogic logic = new XuMicroblockLogic(source, micro, materials)) {
      XuMicroblockLogic.Outcome actual = logic.evaluate(offered);
      require((actual != null) == matched, "XU pure matcher differs from installed native result");
      if (matched) {
        ItemStack result =
            (ItemStack)
                invoke(
                    recipe.getClass(),
                    recipe,
                    "getCraftingResult",
                    new Class<?>[] {InventoryCrafting.class},
                    grid);
        require(
            ItemStack.areItemStacksEqual(result, actual.output),
            "XU pure result changed first tagged physical micro input or prototype");
        boolean mirrored =
            (Boolean)
                invoke(
                    recipe.getClass(),
                    recipe,
                    "checkMatch",
                    new Class<?>[] {InventoryCrafting.class, int.class, int.class, boolean.class},
                    grid,
                    0,
                    0,
                    true);
        require(
            mirrored == actual.mirrored,
            "XU pure matcher changed native mirror-before-base priority");
      }
      require(
          prototype.getTagCompound().hasKey("prototype"),
          "XU helper/native callback changed borrowed prototype");
      logic.close();
      logic.close();
      expect("capture_closed", logic::prototype);
    }
  }

  private static void gemSawCases() throws Exception {
    NativeCoreFixesTest.version("ProjRed|Exploration", "4.12.6-GTNH");
    net.minecraft.item.Item item =
        NativeCoreFixesTest.item("mrtjp.projectred.exploration.ItemGemSaw", "micro_gem_saw");
    item.setMaxDamage(100);
    Class<?> definition = type("mrtjp.projectred.exploration.ToolDefs$ToolDef");
    Object tool =
        definition
            .getConstructor(
                String.class, net.minecraft.item.Item.ToolMaterial.class, ItemStack.class)
            .newInstance(
                "owned",
                net.minecraft.item.Item.ToolMaterial.IRON,
                new ItemStack(Items.iron_ingot));
    Field field = item.getClass().getDeclaredField("toolDef");
    field.setAccessible(true);
    field.set(item, tool);
    try (FmpSawSnapshot snapshot = new FmpSawSnapshot(item)) {
      ItemStack input = new ItemStack(item, 3, 7);
      input.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("preserved"));
      require(
          snapshot.strength
                  == (Integer)
                      invoke(
                          item.getClass(),
                          item,
                          "getCuttingStrength",
                          new Class<?>[] {ItemStack.class},
                          input.copy())
              && snapshot.strength
                  == (Integer)
                      invoke(item.getClass(), item, "getMaxCuttingStrength", new Class<?>[0]),
          "FMP gem saw strength differs from installed native class");
      require(
          ItemStack.areItemStacksEqual(
              item.getContainerItem(input.copy()), snapshot.remainder(input)),
          "FMP gem saw container count/NBT/damage differs");
      Object equal =
          definition
              .getConstructor(
                  String.class, net.minecraft.item.Item.ToolMaterial.class, ItemStack.class)
              .newInstance(
                  "owned",
                  net.minecraft.item.Item.ToolMaterial.IRON,
                  new ItemStack(Items.iron_ingot));
      field.set(item, equal);
      try {
        expect("recipe_changed", snapshot::verify);
      } finally {
        field.set(item, tool);
      }
      snapshot.verify();
    }
    System.out.println(
        "FMP installed ProjectRed gem saw: native strength/max, preserved NBT/count/damage and equal definition replacement passed");
  }

  private static void lightMaterialCases() throws Exception {
    NativeCoreFixesTest.version("ProjRed|Illumination", "4.12.6-GTNH");
    Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    Object material =
        ((sun.misc.Unsafe) unsafeField.get(null))
            .allocateInstance(type("mrtjp.projectred.illumination.LightMicroMaterial"));
    Class<?> base = type("codechicken.microblock.BlockMicroMaterial");
    Field block = base.getDeclaredField("block"), meta = base.getDeclaredField("meta");
    block.setAccessible(true);
    meta.setAccessible(true);
    block.set(material, Blocks.stone);
    meta.setInt(material, 31);
    try (FmpMaterialSnapshot snapshot = new FmpMaterialSnapshot(material)) {
      require(
          ItemStack.areItemStacksEqual(
                  snapshot.stack(),
                  (ItemStack) invoke(material.getClass(), material, "getItem", new Class<?>[0]))
              && snapshot.cutter()
                  == (Integer)
                      invoke(material.getClass(), material, "getCutterStrength", new Class<?>[0]),
          "FMP native LightMicroMaterial projection differs");
    }
    System.out.println(
        "FMP installed LightMicroMaterial: owned inherited block/raw-meta and native cutter body passed; renderer not exercised");
  }

  private static void rulesParityCases(net.minecraft.item.Item micro, net.minecraft.item.Item saw)
      throws Exception {
    int cases = 0;
    try (FmpRules rules = new FmpRules()) {
      for (FmpRules.Material material : rules.materials())
        for (int cls = 0; cls < 4; cls++)
          for (int size = 1; size <= 7; size++) {
            ItemStack part = rules.create(1, cls, size, material.id);
            part.stackSize = 4;
            part.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("ignored"));
            List<ItemStack[]> grids = new ArrayList<>();
            for (int count : new int[] {1, 2, 3, 4, 8}) {
              ItemStack[] grid = new ItemStack[9];
              for (int i = 0; i < count; i++) grid[i] = part.copy();
              grids.add(grid);
            }
            if (cls == 0) {
              ItemStack[] grid = new ItemStack[9];
              for (int i = 0; i < 9; i++) if (i != 4) grid[i] = part.copy();
              grids.add(grid);
            }
            for (int cell = 0; cell < 9; cell++) {
              if (cell / 3 < 2) {
                ItemStack[] grid = new ItemStack[9];
                grid[cell] = new ItemStack(saw);
                grid[cell + 3] = part.copy();
                grids.add(grid);
              }
              if (cell % 3 < 2) {
                ItemStack[] grid = new ItemStack[9];
                grid[cell] = new ItemStack(saw);
                grid[cell + 1] = part.copy();
                grids.add(grid);
              }
            }
            for (ItemStack[] cells : grids) {
              assertRulesParity(rules, cells);
              cases++;
            }
          }
      // Native no-mat falls back to ID zero, without adding a compound to the offered input.
      ItemStack[] missing = new ItemStack[9];
      missing[0] = new ItemStack(micro, 3, 260);
      assertRulesParity(rules, missing);
      require(missing[0].getTagCompound() == null, "FMP lookup wrote borrowed input NBT");
      cases++;
      ItemStack[] full = new ItemStack[9];
      full[0] = new ItemStack(saw);
      full[3] = new ItemStack(Blocks.dirt);
      assertRulesParity(rules, full);
      cases++;
      full[8] = new ItemStack(Items.paper);
      assertRulesParity(rules, full);
      cases++;
      rules.finishVerification();
    }
    for (int durability : new int[] {0, 100}) {
      saw.setMaxDamage(durability);
      try (FmpSawSnapshot binding = new FmpSawSnapshot(saw)) {
        ItemStack offered = new ItemStack(saw, 3, 7);
        offered.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("keptOnlyIfNative"));
        ItemStack nativeRemainder = saw.getContainerItem(offered.copy());
        require(
            ItemStack.areItemStacksEqual(nativeRemainder, binding.remainder(offered)),
            "FMP saw remainder amount/NBT/damage differs");
        binding.verify();
        saw.setMaxDamage(durability + 1);
        expect("recipe_changed", binding::verify);
        saw.setMaxDamage(durability);
      }
    }
    saw.setMaxDamage(100);
    System.out.println(
        "FMP pure rules: "
            + cases
            + " installed native branch/grid/count/NBT cases and damageable/non-damageable saw remainders passed");
  }

  private static void nativeNbtTextCases() throws Exception {
    net.minecraft.nbt.NBTTagCompound compound = new net.minecraft.nbt.NBTTagCompound();
    compound.setInteger("z", 1);
    net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
    list.appendTag(new net.minecraft.nbt.NBTTagString("x"));
    com.google.gson.JsonArray rows = com.github.dcysteine.nesql.exporter.source.Json.array();
    for (net.minecraft.nbt.NBTBase value :
        new net.minecraft.nbt.NBTBase[] {
          new net.minecraft.nbt.NBTTagString("stone"),
          new net.minecraft.nbt.NBTTagInt(3),
          new net.minecraft.nbt.NBTTagByte((byte) 1),
          new net.minecraft.nbt.NBTTagShort((short) 2),
          new net.minecraft.nbt.NBTTagLong(4),
          new net.minecraft.nbt.NBTTagFloat(5),
          new net.minecraft.nbt.NBTTagDouble(6),
          new net.minecraft.nbt.NBTTagByteArray(new byte[] {1}),
          new net.minecraft.nbt.NBTTagIntArray(new int[] {2}),
          list,
          compound
        }) {
      net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
      tag.setTag("mat", value);
      rows.add(
          com.github.dcysteine.nesql.exporter.source.Json.object(
              "tag",
              com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(value),
              "text",
              tag.getString("mat")));
    }
    java.nio.file.Files.write(
        java.nio.file.Paths.get("build/native-tests/fmp-native-nbt-text-observations.json"),
        com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(rows));
  }

  private static void assertRulesParity(FmpRules rules, ItemStack[] cells) {
    InventoryCrafting grid =
        new InventoryCrafting(
            new Container() {
              public boolean canInteractWith(EntityPlayer p) {
                return false;
              }
            },
            3,
            3);
    for (int cell = 0; cell < 9; cell++)
      if (cells[cell] != null) grid.setInventorySlotContents(cell, cells[cell].copy());
    ItemStack nativeResult =
        (ItemStack)
            invoke(
                type("codechicken.microblock.MicroRecipe"),
                null,
                "getCraftingResult",
                new Class<?>[] {InventoryCrafting.class},
                grid);
    FmpRules.Outcome actual = rules.evaluate(cells);
    require(
        ItemStack.areItemStacksEqual(nativeResult, actual == null ? null : actual.stack),
        "Owned FMP rules differ from installed MicroRecipe result");
    if (actual != null) {
      String branch = null;
      for (String name : new String[] {"hollow", "gluing", "thinning", "splitting", "hollowFill"})
        if (invoke(
                type("codechicken.microblock.MicroRecipe"),
                null,
                "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "Result",
                new Class<?>[] {InventoryCrafting.class},
                grid)
            != null) {
          branch = name;
          break;
        }
      require(actual.branch.equals(branch), "Owned FMP rules changed native branch priority");
    }
  }

  private static void sourceCases(Object registry, int[] calls) throws Exception {
    List<Object> source = net.minecraft.item.crafting.CraftingManager.getInstance().getRecipeList(),
        saved = new ArrayList<>(source);
    try {
      source.clear();
      expect("handler_not_ready", FmpNativeSource::new);
      source.add(field(type("codechicken.microblock.MicroRecipe$"), null, "MODULE$"));
      int before = calls[0];
      try (FmpNativeSource binding = new FmpNativeSource()) {
        require(calls[0] == before, "FMP source observation invoked a material callback");
        binding.verify();
        binding.finishVerification();
        String name = "codechicken$microblock$MicroMaterialRegistry$$idMap";
        Field mapField = registry.getClass().getDeclaredField(name);
        mapField.setAccessible(true);
        Object[] ids = (Object[]) mapField.get(registry);
        mapField.set(registry, ids.clone());
        try {
          expect("recipe_changed", binding::verify);
        } finally {
          mapField.set(registry, ids);
        }
        Object tuple = ids[0];
        Object replacement =
            type("scala.Tuple2")
                .getConstructor(Object.class, Object.class)
                .newInstance(field(tuple, "_1"), field(tuple, "_2"));
        ids[0] = replacement;
        try {
          expect("recipe_changed", binding::finishVerification);
        } finally {
          ids[0] = tuple;
        }
        binding.finishVerification();
        binding.close();
        binding.close();
        expect("capture_closed", binding::verify);
        require(calls[0] == before, "FMP sealing invoked a material callback");
      }
      System.out.println(
          "FMP source binding helper: true MicroRecipe registration, equal root/member replacement, callback-free observation and owned close passed; production integration remains pending");
    } finally {
      source.clear();
      source.addAll(saved);
    }
  }

  private static void expect(String code, Runnable call) {
    try {
      call.run();
      throw new AssertionError("Expected " + code);
    } catch (com.github.dcysteine.nesql.exporter.task.Jobs.Fault error) {
      require(error.code.equals(code), error.code);
    }
  }

  private static void harvestCases() throws Exception {
    try (NativeHarvest binding = new NativeHarvest(Blocks.stone, 0)) {
      require(
          binding.level() == Blocks.stone.getHarvestLevel(0),
          "Native Forge harvest-level observation differs");
      Field levels = net.minecraft.block.Block.class.getDeclaredField("harvestLevel");
      levels.setAccessible(true);
      int[] values = (int[]) levels.get(Blocks.stone);
      levels.set(Blocks.stone, values.clone());
      try {
        expect("recipe_changed", binding::verify);
      } finally {
        levels.set(Blocks.stone, values);
      }
      binding.verify();
      binding.close();
      binding.close();
      expect("capture_closed", binding::verify);
    }
    UnknownHarvest unknown = new UnknownHarvest();
    expect("recipe_unsupported", () -> new NativeHarvest(unknown, 0));
    require(unknown.calls == 0, "Unadapted block harvest callback executed");
    NativeCoreFixesTest.version("gregtech", "MC1710");
    NativeCoreFixesTest.version("gregtech_nh", "5.09.51.482");
    Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
    access.setAccessible(true);
    sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
    int cases = 0;
    for (String name :
        new String[] {
          "BlockStorage",
          "BlockStonesAbstract",
          "BlockCasings1",
          "BlockStones",
          "BlockGranites",
          "BlockFrameBox",
          "BlockOres",
          "BlockReinforced",
          "BlockCasings4"
        }) {
      Class<?> owner = type("gregtech.common.blocks." + name);
      require(
          owner
              .getProtectionDomain()
              .getCodeSource()
              .getLocation()
              .toString()
              .contains("/native-tests/gregtech-space.jar!"),
          "Pinned installed GT JAR is required for harvest proof");
      net.minecraft.block.Block block = (net.minecraft.block.Block) unsafe.allocateInstance(owner);
      for (int meta : new int[] {-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 15, 16})
        try (NativeHarvest binding = new NativeHarvest(block, meta)) {
          require(
              binding.level() == block.getHarvestLevel(meta),
              "Native GT harvest metadata rule differs: " + name + "/" + meta);
          cases++;
        }
    }
    System.out.println(
        "FMP transitive harvest helper: "
            + cases
            + " actual installed GT metadata cases, native Forge array drift, close and zero unknown callback invocation passed; producer integration remains pending");
  }

  private static final class UnknownHarvest extends net.minecraft.block.Block {
    int calls;

    UnknownHarvest() {
      super(net.minecraft.block.material.Material.rock);
    }

    @Override
    public int getHarvestLevel(int metadata) {
      calls++;
      throw new AssertionError("Unknown native harvest getter was invoked");
    }
  }

  private static void materialSnapshotCases() throws Exception {
    Class<?> owner = type("codechicken.microblock.BlockMicroMaterial");
    int cases = 0;
    for (int metadata : new int[] {0, 1, 15, 16, 31}) {
      Object material =
          owner
              .getConstructor(net.minecraft.block.Block.class, int.class)
              .newInstance(Blocks.stone, metadata);
      try (FmpMaterialSnapshot snapshot = new FmpMaterialSnapshot(material)) {
        require(
            ItemStack.areItemStacksEqual(
                    snapshot.stack(),
                    (ItemStack) invoke(owner, material, "getItem", new Class<?>[0]))
                && snapshot.cutter()
                    == (Integer) invoke(owner, material, "getCutterStrength", new Class<?>[0]),
            "FMP native material raw metadata/item/cutter projection differs");
        Field meta = owner.getDeclaredField("meta");
        meta.setAccessible(true);
        meta.setInt(material, metadata + 1);
        try {
          expect("recipe_changed", snapshot::verify);
        } finally {
          meta.setInt(material, metadata);
        }
        snapshot.verify();
        snapshot.close();
        snapshot.close();
        expect("capture_closed", snapshot::stack);
        cases++;
      }
    }
    final int[] calls = {0};
    Class<?> api = type("codechicken.microblock.MicroMaterialRegistry$IMicroMaterial");
    Object unknown =
        Proxy.newProxyInstance(
            api.getClassLoader(),
            new Class<?>[] {api},
            (target, method, args) -> {
              calls[0]++;
              throw new AssertionError("Unknown FMP material callback executed");
            });
    expect("recipe_unsupported", () -> new FmpMaterialSnapshot(unknown));
    require(calls[0] == 0, "FMP material owner audit invoked a callback");
    System.out.println(
        "FMP immutable material helper: "
            + cases
            + " actual native raw-meta/item/cutter cases, field drift, owned close and unknown material zero-call rejection passed; production integration remains pending");
  }

  private static void materialLookupCases(net.minecraft.item.Item micro) throws Exception {
    Object module = field(type("codechicken.microblock.handler.MicroblockProxy$"), null, "MODULE$");
    Field logger = null;
    for (Class<?> owner = module.getClass();
        owner != null && logger == null;
        owner = owner.getSuperclass())
      try {
        logger = owner.getDeclaredField("logger");
      } catch (NoSuchFieldException ignored) {
      }
    if (logger == null) throw new AssertionError("Missing actual native FMP logger field");
    logger.setAccessible(true);
    Object savedLogger = logger.get(module);
    logger.set(module, org.apache.logging.log4j.LogManager.getLogger("nesql_fmp_fixture"));
    try {
      Object registry =
          field(type("codechicken.microblock.MicroMaterialRegistry$"), null, "MODULE$");
      Field aliasField = registry.getClass().getDeclaredField("remap");
      aliasField.setAccessible(true);
      Object savedAliases = aliasField.get(registry),
          ownedAliases = type("scala.collection.mutable.HashMap").newInstance();
      invoke(
          ownedAliases.getClass(),
          ownedAliases,
          "put",
          new Class<?>[] {Object.class, Object.class},
          "alias1",
          "alias2");
      invoke(
          ownedAliases.getClass(),
          ownedAliases,
          "put",
          new Class<?>[] {Object.class, Object.class},
          "alias2",
          "b_stone");
      aliasField.set(registry, ownedAliases);
      try (FmpMaterialIds lookup = new FmpMaterialIds()) {
        com.google.gson.JsonArray observations =
            com.github.dcysteine.nesql.exporter.source.Json.array();
        for (String mode :
            new String[] {
              "missingCompound", "missingMaterial", "unknownName", "knownName", "alias1", "alias2"
            }) {
          ItemStack source = new ItemStack(micro, 1, 4);
          if (!mode.equals("missingCompound"))
            source.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
          if (mode.equals("unknownName"))
            source.setTagInfo("mat", new net.minecraft.nbt.NBTTagString("nesql_unknown_material"));
          if (mode.equals("knownName"))
            source.setTagInfo("mat", new net.minecraft.nbt.NBTTagString("b_stone"));
          if (mode.startsWith("alias"))
            source.setTagInfo("mat", new net.minecraft.nbt.NBTTagString(mode));
          ItemStack owned = source.copy();
          int id =
              (Integer)
                  invoke(
                      type("codechicken.microblock.ItemMicroPart"),
                      null,
                      "getMaterialID",
                      new Class<?>[] {ItemStack.class},
                      owned);
          require(
              id == (mode.equals("knownName") || mode.equals("alias2") ? 1 : 0),
              "FMP native missing/unknown material did not fall back to ID zero");
          require(
              lookup.resolve(source) == id,
              "Read-only native FMP lookup changed missing/unknown/one-hop alias semantics");
          if (mode.equals("missingCompound"))
            require(
                source.getTagCompound() == null && owned.getTagCompound() != null,
                "Native FMP compound initialization did not stay on the owned specimen");
          observations.add(
              com.github.dcysteine.nesql.exporter.source.Json.object(
                  "case",
                  mode,
                  "inputNbt",
                  com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(
                      source.getTagCompound()),
                  "nativeMaterialId",
                  id,
                  "afterOwnedNbt",
                  com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(
                      owned.getTagCompound())));
        }
        java.nio.file.Files.write(
            java.nio.file.Paths.get("build/native-tests/fmp-material-lookup-observations.json"),
            com.github.dcysteine.nesql.exporter.source.CanonicalJson.bytes(observations));
        lookup.verify();
        Field names =
            registry
                .getClass()
                .getDeclaredField("codechicken$microblock$MicroMaterialRegistry$$nameMap");
        names.setAccessible(true);
        Object original = names.get(registry),
            replacement = invoke(original.getClass(), original, "clone", new Class<?>[0]);
        names.set(registry, replacement);
        try {
          expect("recipe_changed", lookup::verify);
        } finally {
          names.set(registry, original);
        }
        lookup.verify();
        lookup.close();
        lookup.close();
        expect("capture_closed", lookup::records);
        System.out.println(
            "FMP native material lookup: missing compound/tag and unknown names resolve to zero; existing finite alias-only rule still needs replacement");
      } finally {
        aliasField.set(registry, savedAliases);
      }
    } finally {
      logger.set(module, savedLogger);
    }
  }
}
