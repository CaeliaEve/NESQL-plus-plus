package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagString;

/** Reproduce the concrete callback/storage boundaries from the October 31 return. */
final class NativeReturnedFailuresTest {
  interface Check { void run(NativeRegistryScope scope) throws Exception; }

  static void run() throws Exception {
    List<Throwable> failures = new ArrayList<>();
    Check[] checks = {
        NativeReturnedFailuresTest::bloodBattery,
        NativeReturnedFailuresTest::amulets,
        NativeReturnedFailuresTest::mortar,
        NativeReturnedFailuresTest::enchantment,
        NativeReturnedFailuresTest::loot,
        NativeReturnedFailuresTest::food,
        NativeReturnedFailuresTest::centrifuge,
        NativeReturnedFailuresTest::blockField
    };
    for (Check check : checks) try (NativeRegistryScope scope = new NativeRegistryScope()) {
      check.run(scope);
    } catch (Exception | AssertionError failure) { failures.add(failure); }
    if (!failures.isEmpty()) {
      AssertionError error = new AssertionError("Returned native boundaries: " + failures.size() + " failures");
      for (Throwable failure : failures) error.addSuppressed(failure);
      throw error;
    }
    System.out.println("Returned native callbacks: eight concrete boundaries passed");
  }

  private static void bloodBattery(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("AWWayofTime", "1.7.52");
    Item item = NativeCoreFixesTest.item("WayofTime.alchemicalWizardry.common.items.EnergyBattery", "return_battery");
    ownedSelf(item);
  }

  private static void amulets(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("BloodArsenal", "1.4.10");
    for (String name : new String[]{"SacrificeAmulet", "SelfSacrificeAmulet"})
      ownedSelf(NativeCoreFixesTest.item("com.arc.bloodarsenal.common.items.bauble." + name, "return_" + name));
  }

  private static void ownedSelf(Item item) {
    ItemStack input = new ItemStack(item, 1, 7);
    input.setTagInfo("owner", new NBTTagString("retained"));
    ItemStack actual = ItemCallbacks.container(input, false);
    require(ItemStack.areItemStacksEqual(actual, input) && actual != input, "Self-container lost native stack");
    actual.getTagCompound().setString("owner", "owned");
    require(input.getTagCompound().getString("owner").equals("retained"), "Self-container mutated offered NBT");
  }

  private static void mortar(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("thaumicbases", "1.8.13");
    Item item = NativeCoreFixesTest.item("tb.common.item.ItemMortarAndPestle", "return_mortar");
    item.setMaxDamage(7);
    ItemStack input = new ItemStack(item, 1, 6);
    input.setTagInfo("discarded", new NBTTagString("native-return-has-no-tags"));
    com.google.gson.JsonObject use = NativeCraftingContainers.consumption(input);
    require(use != null && use.get("callback").getAsString().equals("thaumicMortar"),
        "Mortar was flattened to one fixed durability/NBT return");
    ItemStack returned = item.getContainerItem(input.copy());
    require(returned.getItemDamage() == 7 && returned.getTagCompound() == null
        && !item.hasContainerItem(new ItemStack(item, 1, 7)),
        "Pinned mortar boundary differs from the recorded contract");
    require(input.getItemDamage() == 6 && input.hasTagCompound(), "Mortar probe changed input");
  }

  private static void enchantment(NativeRegistryScope scope) {
    require(EnchanterSource.maximum(Enchantment.aquaAffinity) == 1, "Aqua Affinity's native single level was rejected");
  }

  private static void loot(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("Thaumcraft", "4.2.3.5");
    Item item = NativeCoreFixesTest.item("thaumcraft.common.items.ItemResource", "return_loot_resource");
    item.setMaxStackSize(37);
    LootBagItems.audit(item);
    require(new ItemStack(item, 1, 15).getMaxStackSize() == 1
        && new ItemStack(item, 1, 14).getMaxStackSize() == 37,
        "Thaumcraft resource native metadata stack limit was flattened");
  }

  private static void food(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("gregtech_nh", "5.09.51.482");
    Class<?> owner = type("gtPlusPlus.core.item.food.BaseItemMetaFood");
    Map<Integer, Integer> hunger = new HashMap<>();
    hunger.put(91, 9); hunger.put(92, 0);
    scope.replace(owner, null, "mHealAmountMap", hunger);
    Item item = NativeCoreFixesTest.item(owner.getName(), "return_meta_food");
    require(SmeltingItemCallbacks.healing(new ItemStack(item, 1, 91)) == 9
        && SmeltingItemCallbacks.healing(new ItemStack(item, 1, 92)) == 0,
        "GT++ native food metadata table was not respected");
  }

  private static void centrifuge(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("Forestry", "4.10.17");
    Class<?> owner = type("forestry.factory.recipes.CentrifugeRecipe");
    Map<ItemStack, Float> products = Collections.singletonMap(new ItemStack(Items.paper), 0.25f);
    Object nativeRecipe = owner.getConstructor(int.class, ItemStack.class, Map.class)
        .newInstance(3, new ItemStack(Items.apple), products);
    Method read = ForestryRecipes.class.getDeclaredMethod("products", forestry.api.recipes.ICentrifugeRecipe.class);
    read.setAccessible(true);
    try {
      require(read.invoke(null, nativeRecipe) == products, "Centrifuge reader replaced native product storage");
    } catch (InvocationTargetException failure) { throw (RuntimeException) failure.getCause(); }
    try (NativeDefinitionGraph graph = new NativeDefinitionGraph(() -> new Object[]{products}, Collections.emptyMap())) {
      graph.verify();
      products.keySet().iterator().next().stackSize = 2;
      try { graph.verify(); throw new AssertionError("Singleton centrifuge product drift was ignored"); }
      catch (Jobs.Fault expected) { require(expected.code.equals("environment_changed"), expected.toString()); }
    }
  }

  private static void blockField(NativeRegistryScope scope) throws Exception {
    org.objectweb.asm.ClassWriter writer = new org.objectweb.asm.ClassWriter(0);
    writer.visit(52, 1, "net/minecraft/block/Block", null, "java/lang/Object", null);
    writer.visitField(1, "field_149764_J", "Ljava/lang/Object;", null, null).visitEnd();
    writer.visitEnd();
    byte[] bytes = writer.toByteArray();
    Class<?> owner = new ClassLoader(NativeReturnedFailuresTest.class.getClassLoader()) {
      Class<?> load() { return defineClass("net.minecraft.block.Block", bytes, 0, bytes.length); }
    }.load();
    Object block = scope.allocate(owner), material = new Object();
    owner.getField("field_149764_J").set(block, material);
    require(field(owner, block, "blockMaterial") == material, "Runtime SRG Block material was not resolved");
  }

  private static void require(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }
}
