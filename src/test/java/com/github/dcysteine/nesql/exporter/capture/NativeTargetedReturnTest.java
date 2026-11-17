package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

/** Replays the concrete boundaries in the targeted October 31 return, without a game. */
final class NativeTargetedReturnTest {
  interface Check { void run(NativeRegistryScope scope) throws Exception; }

  static void run() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (Check check : new Check[]{NativeTargetedReturnTest::potion, NativeTargetedReturnTest::enchantments,
        NativeTargetedReturnTest::loot, NativeTargetedReturnTest::emptyCanister,
        NativeTargetedReturnTest::centrifuge, NativeTargetedReturnTest::creativeOres,
        NativeTargetedReturnTest::unrelatedMiniature, NativeTargetedReturnTest::furnaceVariants,
        NativeTargetedReturnTest::diagramContents})
      try (NativeRegistryScope scope = new NativeRegistryScope()) { check.run(scope); }
      catch (Exception | AssertionError failure) { failures.add(failure); }
    if (!failures.isEmpty()) {
      AssertionError failure = new AssertionError("Targeted return: " + failures.size() + " boundaries failed");
      for (Throwable cause : failures) failure.addSuppressed(cause);
      throw failure;
    }
    System.out.println("Targeted return: nine callback, storage, wildcard and compact-content boundaries passed");
  }

  static void containers() throws Exception {
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeCoreFixesTest.version("Botania", "1.12.28-GTNH");
      Item planet = NativeCoreFixesTest.item("vazkii.botania.common.item.equipment.bauble.ItemTinyPlanet", "Botania:tinyPlanet");
      JsonObject use = NativeCraftingContainers.consumption(new ItemStack(planet));
      require(use != null && use.get("callback").getAsString().equals("botaniaCosmetic"),
          "Tiny Planet lost its input-dependent cosmetic return");
      Item talisman = NativeCoreFixesTest.item("vazkii.botania.common.item.ItemBlackHoleTalisman", "Botania:blackHoleTalisman");
      use = NativeCraftingContainers.consumption(new ItemStack(talisman));
      require(use != null && use.get("callback").getAsString().equals("botaniaBlackHole"),
          "Black hole talisman was flattened into a fixed empty return");
      for (int[] pair : new int[][]{{-1,0},{0,0},{1,0},{63,0},{64,0},{65,1},{Integer.MAX_VALUE,2147483583},{Integer.MIN_VALUE,0}}) {
        ItemStack offered = new ItemStack(talisman, 3, 1);
        offered.setTagCompound(new NBTTagCompound());
        offered.getTagCompound().setInteger("blockCount", pair[0]);
        offered.getTagCompound().setString("retained", "source");
        ItemStack result = talisman.getContainerItem(offered);
        require((result != null) == (pair[0] != 0), "Talisman zero-count predicate differs");
        if (result != null)
          require(result.getTagCompound().getInteger("blockCount") == pair[1]
              && result.getItemDamage() == 0 && result.stackSize == 3
              && result.getTagCompound().getString("retained").equals("source"),
              "Talisman return lost native count, metadata or NBT");
        require(offered.getItemDamage() == 1 && offered.getTagCompound().getInteger("blockCount") == pair[0],
            "Talisman return mutated the offered stack");
      }
      for (Item item : new Item[]{planet, talisman}) {
        ItemStack input = new ItemStack(item), output = new ItemStack(Items.paper);
        IRecipe nativeRecipe = new ShapelessRecipes(output, Collections.singletonList(input));
        RecipeRow row = NativeCraftingTest.row(false, input, output);
        try (RegistryRecipes reader = NativeCraftingTest.reader(Collections.singletonList(nativeRecipe), false)) {
          require(reader.capture(0, row), "Native Botania container craft was excluded");
          reader.finishVerification();
          NativeContractFixture.writeOwned(item == planet ? "targeted-container-planet" : "targeted-container-blackhole",
              row, Arrays.asList(input, output));
        }
      }
    }
    System.out.println("Targeted native containers: Tiny Planet cosmetic and signed black-hole count boundaries passed");
  }

  private static void potion(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("EnderZoo", "1.3.3");
    Item item = NativeCoreFixesTest.item("crazypants.enderzoo.item.ItemConfusingDust", "target_confusing");
    ItemStack input = new ItemStack(item);
    require("+0+1+2-3&4-4+13".equals(item.getPotionEffect(input)), "Native confusing potion expression changed");
    require("+0+1+2-3&4-4+13".equals(BrewingItemCallbacks.effect(input)), "Confusing dust expression was rejected");
  }

  private static void enchantments(NativeRegistryScope scope) {
    for (Enchantment enchantment : Enchantment.enchantmentsList)
      if (enchantment != null && enchantment.getClass().getName().startsWith("net.minecraft.enchantment."))
        require(EnchanterSource.maximum(enchantment) == enchantment.getMaxLevel(),
            "Vanilla enchantment maximum was not retained: " + enchantment.getClass().getName());
  }

  private static void loot(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("BiomesOPlenty", "2.1.0.2308");
    Item item = NativeCoreFixesTest.item("biomesoplenty.common.items.ItemBOPFood", "target_bop_food");
    LootBagItems.audit(item);
    for (int meta = 0; meta < 16; meta++)
      require(new ItemStack(item, 1, meta).getMaxStackSize()
          == (meta == 4 || meta == 5 || meta == 6 || meta == 10 || meta == 13 ? 1 : 64),
          "BOP food's metadata stack limit was flattened");
  }

  private static void emptyCanister(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("GalacticraftCore", "3.3.13-GTNH");
    scope.replace(type("micdoodle8.mods.galacticraft.core.items.ItemCanisterGeneric"), null, "isTELoaded", false);
    Item item = NativeCoreFixesTest.item("micdoodle8.mods.galacticraft.core.items.ItemOilCanister", "target_empty_oil");
    item.setContainerItem(item);
    ItemStack empty = new ItemStack(item, 1, 1001);
    require(item.hasContainerItem(empty) && item.getContainerItem(empty) == null,
        "Native canister fixture no longer has a nullable return");
    JsonObject result = (JsonObject)callPrivate(ForestryRemainingRecipes.class, "container",
        new Class<?>[]{ItemStack.class}, empty);
    require(result.get("kind").getAsString().equals("none"), "Null native return was not preserved");
  }

  private static void centrifuge(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("Forestry", "4.10.17");
    Map<ItemStack, Float> products = new Hashtable<>();
    ItemStack output = new ItemStack(Items.paper); products.put(output, 0.25f);
    Object recipe = type("forestry.factory.recipes.CentrifugeRecipe")
        .getConstructor(int.class, ItemStack.class, Map.class).newInstance(3, new ItemStack(Items.apple), products);
    require(callPrivate(ForestryRecipes.class, "products",
        new Class<?>[]{forestry.api.recipes.ICentrifugeRecipe.class}, recipe) == products,
        "Native Hashtable output storage was replaced");
    try (NativeDefinitionGraph graph = new NativeDefinitionGraph(() -> new Object[]{products}, Collections.emptyMap())) {
      output.stackSize = 2;
      try { graph.verify(); throw new AssertionError("Hashtable output drift was ignored"); }
      catch (Jobs.Fault expected) { require(expected.code.equals("environment_changed"), expected.toString()); }
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static void creativeOres(NativeRegistryScope scope) throws Exception {
    scope.replace(CraftingManager.class, CraftingManager.getInstance(), "recipes", new ArrayList<>());
    OreDictionary.registerOre("oreTargetedCreative", new ItemStack(Items.paper));
    Map<Integer, List<Integer>> reverse = (Map<Integer, List<Integer>>)field(OreDictionary.class, null, "stackToId");
    Map optimized = (Map)type("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap").newInstance();
    for (Map.Entry<Integer, List<Integer>> entry : reverse.entrySet()) {
      List ids = (List)type("it.unimi.dsi.fastutil.ints.IntArrayList").newInstance();
      ids.addAll(entry.getValue()); optimized.put(entry.getKey(), ids);
    }
    scope.replace(OreDictionary.class, null, "stackToId", optimized);
    try (CreativeCoreBindings reader = new CreativeCoreBindings()) { reader.verify(); }
  }

  private static void unrelatedMiniature(NativeRegistryScope scope) throws Exception {
    Item saw = NativeCoreFixesTest.item("codechicken.microblock.ItemSaw", "target_saw");
    Item mirror = NativeCoreFixesTest.item("vazkii.botania.common.item.ItemManaMirror", "Botania:manaMirror");
    Map<String, Item> core = new HashMap<>();
    core.put("itemMicroPart", Items.paper); core.put("itemPartFixture", Items.book);
    ItemStack[] grid = new ItemStack[9]; grid[0] = new ItemStack(saw); grid[4] = new ItemStack(mirror);
    ProjectBlueLogic logic = new ProjectBlueLogic(core, null);
    require(logic.evaluate("miniature", grid).status.equals("unmatched"),
        "Unrelated computed-metadata item was treated as a miniature input");
    require(ProjectBlueLogic.materialKey("miniature", grid) == null, "Unrelated item produced a material key");
  }

  private static void furnaceVariants(NativeRegistryScope scope) throws Exception {
    Constructor<FurnaceRecipes> constructor = FurnaceRecipes.class.getDeclaredConstructor(); constructor.setAccessible(true);
    FurnaceRecipes furnace = constructor.newInstance();
    Map<ItemStack, ItemStack> definitions = new LinkedHashMap<>();
    ItemStack template = new ItemStack(Items.paper, 1, 32767), output = new ItemStack(Items.diamond);
    definitions.put(template, output);
    scope.replace(FurnaceRecipes.class, furnace, "smeltingList", definitions);
    scope.replace(FurnaceRecipes.class, furnace, "experienceList", new HashMap<ItemStack, Float>());
    com.google.common.collect.ArrayListMultimap<Item, ItemStack> candidates = com.google.common.collect.ArrayListMultimap.create();
    ItemStack observed = new ItemStack(Items.paper, 1, 7);
    observed.setTagCompound(new NBTTagCompound()); observed.getTagCompound().setString("gene", "observed-only");
    candidates.put(Items.paper, observed);
    scope.replace(codechicken.nei.ItemList.class, null, "itemMap", candidates);
    scope.replace(codechicken.nei.ItemList.class, null, "loadFinished", true);
    RecipeRow row = NativeCoreFixesTest.row(template, observed, output);
    try (VanillaFurnaceRecipes reader = new VanillaFurnaceRecipes(furnace)) {
      require(reader.capture(0, row), "Native wildcard furnace recipe was excluded");
      JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
      require(choice.get("id").getAsString().equals(row.facts.item(observed))
          && choice.getAsJsonObject("rule").get("meta").getAsBoolean()
          && choice.getAsJsonObject("rule").get("nbt").getAsBoolean(),
          "Wildcard furnace display lost observed metadata/NBT or narrowed its native predicate");
      require(template.getTagCompound() == null && template.getItemDamage() == 32767, "Furnace changed its source template");
      reader.finishVerification();
    }
  }

  private static void diagramContents(NativeRegistryScope scope) throws Exception {
    ItemStack stack = new ItemStack(Items.paper, 1, 7);
    stack.setTagCompound(new NBTTagCompound()); stack.getTagCompound().setString("toolData", "complete-owned-data");
    Object component = invoke(type(NativeDiagramContents.API + "diagram.component.ItemComponent"), null,
        "createWithNbt", new Class<?>[]{ItemStack.class}, stack);
    NativeDiagramContents reader = new NativeDiagramContents();
    JsonObject encoded = reader.component(component);
    require(encoded.get("kind").getAsString().equals("itemFact"), "Diagram content duplicated the complete tool NBT");
    String fact = encoded.get("fact").getAsString();
    require(ItemStack.areItemStacksEqual(stack, reader.items.get(fact)), "Compaction lost the complete item fact");
    require(reader.component(component, false).getAsJsonObject("nbt") != null, "Query normalization lost its literal NBT");
  }

  private static Object callPrivate(Class<?> owner, String name, Class<?>[] args, Object... values) throws Exception {
    Method method = owner.getDeclaredMethod(name, args); method.setAccessible(true);
    try { return method.invoke(null, values); }
    catch (InvocationTargetException cause) {
      if (cause.getCause() instanceof Exception) throw (Exception)cause.getCause();
      throw (Error)cause.getCause();
    }
  }
  private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
