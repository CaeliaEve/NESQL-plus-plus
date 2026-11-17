package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagString;
import thaumcraft.api.crafting.InfusionRecipe;

/** Native behavior oracles for the second set of returned failures, without a live game. */
final class NativeReturnedStateTest {
  interface Check { void run(NativeRegistryScope scope) throws Exception; }
  static void run() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    for (Check check : new Check[]{NativeReturnedStateTest::canister, NativeReturnedStateTest::potion,
        NativeReturnedStateTest::firestone, NativeReturnedStateTest::furnace,
        NativeReturnedStateTest::nullInfusion, NativeReturnedStateTest::mirror})
      try (NativeRegistryScope scope = new NativeRegistryScope()) { check.run(scope); }
      catch (Exception | AssertionError failure) { failures.add(failure); }
    if (!failures.isEmpty()) {
      AssertionError failure = new AssertionError("Returned state boundaries: " + failures.size() + " failures");
      for (Throwable cause : failures) failure.addSuppressed(cause);
      throw failure;
    }
    System.out.println("Returned state: canisters, potion configuration, stochastic container, furnace counter, unreachable infusion and computed metadata passed");
  }
  private static void canister(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("GalacticraftCore", "3.3.13-GTNH");
    Class<?> owner = type("micdoodle8.mods.galacticraft.core.items.ItemCanisterGeneric");
    scope.replace(owner, null, "isTELoaded", false);
    Item oil = NativeCoreFixesTest.item("micdoodle8.mods.galacticraft.core.items.ItemOilCanister", "returned_oil_canister");
    Item fuel = NativeCoreFixesTest.item("micdoodle8.mods.galacticraft.core.items.ItemFuelCanister", "returned_fuel_canister");
    oil.setContainerItem(oil); fuel.setContainerItem(oil);
    for (Item item : Arrays.asList(oil, fuel)) for (int damage : new int[]{1, 500, 1001}) {
      ItemStack input = new ItemStack(item, 1, damage);
      input.setTagInfo("discarded", new NBTTagString("native-canister-creates-fresh-tags"));
      ItemStack expected = item.getContainerItem(input.copy()), actual = ItemCallbacks.container(input, false);
      require(ItemStack.areItemStacksEqual(expected, actual), "Canister null/full/fuel return differs");
      require(input.hasTagCompound() && input.getItemDamage() == damage, "Canister changed offered input");
    }
  }
  private static void potion(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("EnderZoo", "1.3.3");
    Item item = NativeCoreFixesTest.item("crazypants.enderzoo.item.ItemWitheringDust", "returned_withering");
    Method read;
    try { read = type("com.github.dcysteine.nesql.exporter.capture.BrewingItemCallbacks").getDeclaredMethod("effect", ItemStack.class); }
    catch (Jobs.Fault absent) { throw new AssertionError("Missing read-only potion ingredient adapter", absent); }
    read.setAccessible(true);
    for (boolean alternative : new boolean[]{false, true}) {
      scope.replace(type("crazypants.enderzoo.config.Config"), null, "useAltWitherPotionEffectMask", alternative);
      ItemStack input = new ItemStack(item);
      require(Objects.equals(read.invoke(null, input), item.getPotionEffect(input.copy())),
          "Withering potion configuration was flattened");
    }
  }
  private static void firestone(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("Railcraft", "9.16.33");
    Item item = NativeCoreFixesTest.item("mods.railcraft.common.items.firestone.ItemFirestoneRefined", "returned_firestone");
    com.google.gson.JsonObject use = NativeCraftingContainers.consumption(new ItemStack(item, 1, 7));
    require(use != null && use.get("callback").getAsString().equals("railFirestone"),
        "Random native firestone return was sampled or omitted");
  }
  @SuppressWarnings("unchecked") private static void furnace(NativeRegistryScope scope) throws Exception {
    Constructor<FurnaceRecipes> factory = FurnaceRecipes.class.getDeclaredConstructor(); factory.setAccessible(true);
    FurnaceRecipes manager = factory.newInstance();
    Map<ItemStack, ItemStack> map = (Map<ItemStack, ItemStack>)type(NativeSmeltingTable.LIB).getConstructor(boolean.class).newInstance(false);
    map.put(new ItemStack(Items.paper, 1, 2), new ItemStack(Items.diamond));
    // The native raw table and its stored bookkeeping counter are separate bindings.
    scope.replace(map.getClass(), map, "size", 7);
    scope.replace(FurnaceRecipes.class, manager, "smeltingList", map);
    scope.replace(FurnaceRecipes.class, manager, "experienceList", new HashMap<ItemStack, Float>());
    try (VanillaFurnaceRecipes reader = new VanillaFurnaceRecipes(manager)) {
      require(reader.size() == 1, "Furnace used the bookkeeping counter as raw definition coverage");
      reader.finishVerification();
      scope.replace(map.getClass(), map, "size", 8);
      try { reader.verify(); throw new AssertionError("Subsequent furnace counter drift was ignored"); }
      catch (Jobs.Fault expected) { require(expected.code.equals("recipe_changed"), expected.toString()); }
    }
  }
  private static void nullInfusion(NativeRegistryScope scope) throws Exception {
    InfusionRecipe raw = new InfusionRecipe(null, new ItemStack(Items.paper), 0, null, null, null);
    require(!raw.matches(null, null, null, null), "Native null-center gate is no longer first");
    try (NativeItemExamples examples = new NativeItemExamples();
         NativeInfusionRecipes reader = new NativeInfusionRecipes(raw, examples, null)) {
      require(!reader.capture(NativeCoreFixesTest.row(new ItemStack(Items.paper)), null)
          && reader.exclusion().get("reason").getAsString().equals("native_infusion_null_recipe_input"),
          "Unreachable null-center infusion was not recorded");
      reader.finishVerification();
    }
  }
  private static void mirror(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("Botania", "1.12.28-GTNH");
    Item item = NativeCoreFixesTest.item("vazkii.botania.common.item.ItemManaMirror", "Botania:manaMirror");
    Method read;
    try { read = type("com.github.dcysteine.nesql.exporter.capture.NativeItemMetadata").getDeclaredMethod("value", ItemStack.class); }
    catch (Jobs.Fault absent) { throw new AssertionError("Missing computed native metadata adapter", absent); }
    read.setAccessible(true);
    for (int mana : new int[]{0, 1, 999, 1000, 999999, 1000000, 16777217, -1, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
      ItemStack input = new ItemStack(item, 1, 123);
      input.setTagInfo("mana", new net.minecraft.nbt.NBTTagInt(mana));
      try { NativeStackAudit.audit(input); throw new AssertionError("Computed metadata escaped into a reader without a logical-getter contract"); }
      catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), expected.toString()); }
      require(((Integer)read.invoke(null, input)) == input.getItemDamage(), "Mana mirror logical metadata differs at " + mana);
      require(Items.feather.getDamage(input) == 123, "Metadata read overwrote physical identity");
    }
    ItemStack template=new ItemStack(item,1,123),output=new ItemStack(Items.paper);
    template.setTagInfo("mana",new net.minecraft.nbt.NBTTagInt(1000));
    net.minecraft.item.crafting.ShapedRecipes recipe=new net.minecraft.item.crafting.ShapedRecipes(1,1,new ItemStack[]{template},output);
    RecipeRow row=NativeCraftingTest.row(true,template,output);
    try(RegistryRecipes reader=NativeCraftingTest.reader(java.util.Collections.singletonList(recipe),true)){
      require(reader.capture(0,row),"Mana mirror was excluded from its actual native crafting reader");
      com.google.gson.JsonObject rule=row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("rule");
      require(rule.get("kind").getAsString().equals("nativeItemMetadata")&&rule.get("value").getAsInt()==999,
          "Native crafting lost its logical metadata predicate");
      reader.finishVerification();
      NativeContractFixture.writeOwned("native-metadata-mirror",row,java.util.Arrays.asList(template,output));
    }
  }
  private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
