package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/** Small, real dependency regressions for callbacks observed in the returned native run. */
final class NativeObservedCallbacksTest {
  static void run() throws Exception {
    String family = System.getProperty("nesql.nativeFamily");
    try (NativeRegistryScope scope = new NativeRegistryScope()) {
      NativeCoreFixesTest.version("gregtech_nh", "5.09.51.482");
      switch (family.substring("observed-callbacks-".length())) {
        case "blood-tier": blood(scope, false); break;
        case "blood-capacity": blood(scope, true); break;
        case "armok": armok(); break;
        case "gt": containers(scope); break;
        case "gt99": meta99(scope); break;
        case "gt98": meta98(scope); break;
        case "gt-visual": gtVisual(scope); break;
        case "rail-bucket": railBucket(); break;
        case "enchanter": enchanter(scope); break;
        case "garden": gardens(scope); break;
        case "loot": loot(scope); break;
        case "loot-physical": lootPhysical(); break;
        case "smelting": smelting(scope); break;
        case "metadata": metadata(scope); break;
        case "blood-container": bloodContainers(); break;
        case "angle-grinder": angleGrinder(scope); break;
        case "gt-aspect": gtAspect(scope); break;
        case "dynamic-container": dynamicContainers(scope); break;
        case "focus-container": focusContainer(); break;
        case "witchery": witchery(scope); break;
        case "chisel": chisel(scope); break;
        case "chisel-scale": chiselScale(scope); break;
        case "creative-census": creativeCensus(scope); break;
        case "bracelet": bracelet(scope); break;
        default: throw new AssertionError("Unknown observed callback family: " + family);
      }
    }
    System.out.println(family + ": native behavior, owned results and callback boundaries passed");
  }

  private static void bracelet(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("thaumicbases", "1.8.13");
    Class<?> rodType=thaumcraft.api.wands.WandRod.class, capType=thaumcraft.api.wands.WandCap.class;
    Object rod=scope.allocate(rodType),cap=scope.allocate(capType);
    for(String name:new String[]{"WOOD","GREATWOOD","SILVERWOOD","REED","BONE","OBSIDIAN","BLAZE","ICE","QUARTZ"})
      scope.replace(thaumcraft.common.config.ConfigItems.class,null,"WAND_ROD_"+name,rod);
    for(String name:new String[]{"IRON","GOLD","THAUMIUM","VOID"})
      scope.replace(thaumcraft.common.config.ConfigItems.class,null,"WAND_CAP_"+name,cap);
    Object staff=scope.allocate(thaumcraft.api.wands.StaffRod.class);
    scope.replace(thaumcraft.common.config.ConfigItems.class,null,"STAFF_ROD_PRIMAL",staff);
    Class<?> roles=type("tb.init.TBItems");
    Class<?> braceletType=type("tb.common.item.ItemCastingBracelet");
    require(((Object[])field(braceletType,null,"rods"))[3]==null
        &&((Object[])field(braceletType,null,"rods"))[11]==null
        &&((Object[])field(braceletType,null,"caps"))[3]==null,
        "Native initialization did not retain pre-setup bracelet slots");
    scope.replace(roles,null,"WAND_ROD_THAUMIUM",rod);
    scope.replace(roles,null,"WAND_ROD_VOID",rod);
    scope.replace(roles,null,"WAND_CAP_THAUMINITE",cap);
    thaumcraft.common.items.wands.ItemWandCasting item=
        (thaumcraft.common.items.wands.ItemWandCasting)NativeCoreFixesTest.item(
            braceletType.getName(),"nesqltest:bracelet_native_roles");
    for(int meta:new int[]{3,11}){
      ItemStack stack=new ItemStack(item,1,meta);
      require(item.getRod(stack)==rod&&item.getCap(stack)==cap,
          "Pinned bracelet did not bypass its stale role array entries");
    }
    SalisWandBindings bindings=scope.allocate(SalisWandBindings.class);
    Map<String,String[]> shapes=new HashMap<>();
    scope.replace(SalisWandBindings.class,bindings,"shapes",shapes);
    Method read=SalisWandBindings.class.getDeclaredMethod("braceletComponents",List.class);
    read.setAccessible(true);
    List<Object> roots=new ArrayList<>();
    invokeChecked(read,bindings,roots);
    try(NativeDefinitionGraph graph=new NativeDefinitionGraph(()->roots.toArray(),shapes)){
      graph.verify();
      Object[] rods=(Object[])field(braceletType,null,"rods");
      Object previous=rods[3];rods[3]=rod;
      fails("environment_changed",graph::verify);
      rods[3]=previous;graph.verify();
      previous=rods[2];rods[2]=null;
      fails("recipe_unsupported",()->braceletRead(read,bindings));
      rods[2]=previous;
      scope.replace(roles,null,"WAND_ROD_VOID",null);
      fails("recipe_unsupported",()->braceletRead(read,bindings));
    }
  }

  private static void braceletRead(Method read,Object bindings){
    try{invokeChecked(read,bindings,new ArrayList<>());}
    catch(RuntimeException failure){throw failure;}
    catch(Exception failure){throw new AssertionError(failure);}
  }

  /** Isolate setup's item construction only; keep actual bracelet <clinit> and getters intact. */
  public static final class BraceletRoles implements net.minecraft.launchwrapper.IClassTransformer {
    public byte[] transform(String name,String transformed,byte[] bytes){
      if(!"tb.init.TBItems".equals(name))return bytes;
      org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();
      new org.objectweb.asm.ClassReader(bytes).accept(node,0);
      for(Object raw:node.methods){org.objectweb.asm.tree.MethodNode method=(org.objectweb.asm.tree.MethodNode)raw;
        if(method.name.equals("<clinit>")){method.instructions.clear();method.tryCatchBlocks.clear();
          if(method.localVariables!=null)method.localVariables.clear();
          method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));method.maxStack=0;method.maxLocals=0;}}
      org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(0);node.accept(writer);return writer.toByteArray();
    }
  }

  private static void creativeCensus(NativeRegistryScope scope) throws Exception {
    scope.replace(net.minecraft.item.crafting.CraftingManager.class,
        net.minecraft.item.crafting.CraftingManager.getInstance(), "recipes", new ArrayList<>());
    CensusItem unrelated = new CensusItem();
    Item.itemRegistry.addObject(31902, "nesqltest:unrelated_registry_item", unrelated);
    unrelated.guarded = true;
    try (CreativeCoreBindings binding = new CreativeCoreBindings()) {
      binding.verify();
      require(unrelated.calls == 0,
          "CreativeCore census evaluated an unrelated item's hash/equality callbacks");
      scope.replace(Item.class, unrelated, "containerItem", Items.bucket);
      fails("environment_changed", binding::verify);
      scope.replace(Item.class, unrelated, "containerItem", null);
      binding.verify();
      fails("recipe_unsupported", () -> NativeStackAudit.identityOwner(unrelated));
      require(unrelated.calls == 0,
          "Rejecting an active foreign hash/equality callback executed it");
    }
  }

  static final class CensusItem extends Item {
    boolean guarded;
    int calls;
    @Override public int hashCode() {
      if (guarded) { calls++; throw new AssertionError("Unused item hash executed"); }
      return System.identityHashCode(this);
    }
    @Override public boolean equals(Object other) {
      if (guarded) { calls++; throw new AssertionError("Unused item equality executed"); }
      return this == other;
    }
  }

  private static void metadata(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("universalsingularities", "8.10.0");
    NativeCoreFixesTest.version("MagicBees", "2.9.4-GTNH");
    NativeCoreFixesTest.version("Botania", "1.12.28-GTNH");
    NativeCoreFixesTest.version("ae2wct", "1.12.7");
    Item singularity = NativeCoreFixesTest.item("wealthyturtle.uiesingularities.UniversalSingularityItem", "metadata_singularity");
    scope.replace(singularity.getClass(), singularity, "universalSingularities", new ArrayList<>(Arrays.asList(null, null, null)));
    Item comb = NativeCoreFixesTest.item("magicbees.item.ItemComb", "metadata_comb");
    Item tablet = NativeCoreFixesTest.item("vazkii.botania.common.item.ItemManaTablet", "metadata_tablet");
    Item wireless = NativeCoreFixesTest.item("net.p455w0rd.wirelesscraftingterminal.items.ItemWirelessCraftingTerminal", "metadata_wireless");
    for (Item item : new Item[]{singularity, comb, tablet, wireless}) {
      ItemStack input = new ItemStack(item, 3, item == singularity ? 2 : 0);
      input.setTagInfo("retained", new net.minecraft.nbt.NBTTagString("native"));
      ItemStack before = input.copy();
      require(input.getItemDamage() == Items.feather.getDamage(before), "Native fixed metadata differs");
      NativeStackAudit.audit(input);
      require(ItemStack.areItemStacksEqual(input, before), "Metadata admission changed a borrowed stack");
      ItemStack normalizing = new ItemStack(item, 1, 32767);
      fails("recipe_unsupported", () -> NativeStackAudit.audit(normalizing));
      require(Items.feather.getDamage(normalizing) == 32767, "Rejected normalization mutated borrowed metadata");
    }
    ItemStack input = new ItemStack(wireless), output = new ItemStack(Items.paper);
    try (RegistryRecipes reader = NativeCraftingTest.reader(Collections.singletonList(
        new net.minecraftforge.oredict.ShapelessOreRecipe(output, input)), false)) {
      require(reader.capture(0, NativeCraftingTest.row(false, input, output)), "Native fixed metadata crafting was rejected");
      reader.finishVerification();
    }
  }

  private static void angleGrinder(NativeRegistryScope scope) throws Exception {
    Item tool = NativeCoreFixesTest.item("gregtech.common.items.MetaGeneratedTool01", "native_angle_grinder");
    Object stats = type("gtPlusPlus.xmod.gregtech.common.tools.ToolAngleGrinder").newInstance();
    Map<Short,Object> table = new ConcurrentHashMap<>(); table.put((short)0, stats);
    scope.replace(type("gregtech.api.items.MetaGeneratedTool"), tool, "mToolStats", table);
    ItemStack physical = new ItemStack(tool);
    net.minecraft.nbt.NBTTagCompound nativeStats = new net.minecraft.nbt.NBTTagCompound();
    nativeStats.setLong("Damage", 10); nativeStats.setLong("MaxDamage", 1000);
    physical.setTagInfo("GT.ToolStats", nativeStats);
    ItemStack before = physical.copy();
    require((Boolean)call(stats, "isWeapon") && (Integer)call(stats, "getToolDamagePerContainerCraft") == 400,
        "Pinned GT++ angle grinder constants differ");
    GtToolConsumption binding = new GtToolConsumption(physical);
    JsonObject rule = binding.declaration();
    ItemStack returned = tool.getContainerItem(physical.copy());
    require(rule.get("cost").getAsInt() == 400 && !rule.get("electric").getAsBoolean()
        && rule.getAsJsonObject("wearChance").get("denominator").getAsString().equals("1")
        && returned.getTagCompound().getCompoundTag("GT.ToolStats").getLong("Damage") == 410,
        "GT++ angle grinder native wear differs from declaration");
    require(ItemStack.areItemStacksEqual(physical, before), "Tool declaration mutated borrowed input");
    table.put((short)0, type("gregtech.common.tools.ToolSaw").newInstance());
    fails("recipe_changed", binding::verify);
  }

  private static void gtAspect(NativeRegistryScope scope) throws Exception {
    Class<?> base = type("thaumcraft.api.aspects.Aspect");
    Object aspect = scope.allocate(type("gregtech.common.GTThaumcraftCompat$LocalizedCustomAspectName"));
    set(base, aspect, "tag", "nesql_gt_custom_aspect");
    Object components = java.lang.reflect.Array.newInstance(base, 1);
    java.lang.reflect.Array.set(components, 0, thaumcraft.api.aspects.Aspect.AIR);
    set(base, aspect, "components", components);
    Map<String,String[]> shapes = NativeDefinitionGraph.shapes(base.getName(), "tag", "components");
    try (NativeDefinitionGraph graph = new NativeDefinitionGraph(() -> new Object[]{aspect}, shapes)) {
      graph.verify();
      set(base, aspect, "tag", "changed");
      fails("environment_changed", graph::verify);
      set(base, aspect, "tag", "nesql_gt_custom_aspect");
      graph.verify();
      java.lang.reflect.Array.set(components, 0, thaumcraft.api.aspects.Aspect.FIRE);
      fails("environment_changed", graph::verify);
    }
  }

  private static void smelting(NativeRegistryScope scope) throws Exception {
    java.io.File home = new java.io.File("build/native-tests/smelting-boundary-profile").getAbsoluteFile();
    home.mkdirs();
    cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLInjectionData.class, null, home, "minecraftHome");
    net.minecraft.launchwrapper.Launch.minecraftHome = home;
    NativeCoreFixesTest.version("Natura", "2.8.9");
    NativeCoreFixesTest.version("Railcraft", "9.16.33");
    Item food = NativeCoreFixesTest.item("mods.natura.items.ImpMeat", "smoker_imp_meat");
    int[] hunger = {3, 8};
    scope.replace(type("mods.natura.items.NSpecialFood"), food, "hunger", hunger);
    SmeltingRecipes smoker = scope.allocate(SmeltingRecipes.class);
    scope.replace(SmeltingRecipes.class, smoker, "smoker", true);
    scope.replace(SmeltingRecipes.class, smoker, "foodDependencies", new LinkedHashMap<>());
    scope.replace(type("ganymedes01.etfuturum.configuration.configs.ConfigFunctions"), null, "enableAutoAddSmoker", true);
    Method canAdd = SmeltingRecipes.class.getDeclaredMethod("canAdd", ItemStack.class, ItemStack.class);
    canAdd.setAccessible(true);
    for (int meta : new int[]{0, 1}) {
      ItemStack output = new ItemStack(food, 1, meta);
      require(((net.minecraft.item.ItemFood)food).func_150905_g(output) == hunger[meta], "Pinned Natura healing differs");
      require((Boolean)invokeChecked(canAdd, smoker, new ItemStack(Items.beef), output), "Native Natura food was rejected by smoker");
    }
    hunger[1] = 0;
    require(!(Boolean)invokeChecked(canAdd, smoker, new ItemStack(Items.beef), new ItemStack(food, 1, 1)),
        "Smoker ignored current native hunger table");

    Item firestone = NativeCoreFixesTest.item("mods.railcraft.common.items.firestone.ItemFirestoneRaw", "furnace_firestone");
    ItemStack output = new ItemStack(firestone);
    Class<?> rail = type("mods.railcraft.common.items.ItemRailcraft");
    Field experienceField = rail.getDeclaredField("smeltingExperience");
    experienceField.setAccessible(true);
    Constructor<net.minecraft.item.crafting.FurnaceRecipes> factory = net.minecraft.item.crafting.FurnaceRecipes.class.getDeclaredConstructor();
    factory.setAccessible(true);
    net.minecraft.item.crafting.FurnaceRecipes manager = factory.newInstance();
    ((Map<?, ?>)field(manager, "smeltingList")).clear();
    @SuppressWarnings("unchecked") Map<ItemStack, Float> nativeXp = (Map<ItemStack, Float>)field(manager, "experienceList");
    nativeXp.clear(); nativeXp.put(output, .7f);
    VanillaFurnaceRecipes vanilla = scope.allocate(VanillaFurnaceRecipes.class);
    scope.replace(VanillaFurnaceRecipes.class, vanilla, "manager", manager);
    Map<ItemStack, Float> cached = new LinkedHashMap<>(), custom = new LinkedHashMap<>();
    cached.put(output, .3f); custom.put(output, .5f);
    scope.replace(SmeltingRecipes.class, smoker, "experienceCache", new NativeSmeltingTable(cached));
    scope.replace(SmeltingRecipes.class, smoker, "experience", new NativeSmeltingTable(custom));
    scope.replace(SmeltingRecipes.class, smoker, "furnaceExperience", new NativeSmeltingTable(nativeXp));
    Method vanillaExperience = VanillaFurnaceRecipes.class.getDeclaredMethod("experience", ItemStack.class);
    Method smokerExperience = SmeltingRecipes.class.getDeclaredMethod("experience", ItemStack.class);
    vanillaExperience.setAccessible(true); smokerExperience.setAccessible(true);
    for (float value : new float[]{.9f, 0f, -1f}) {
      experienceField.setFloat(firestone, value);
      require(firestone.getSmeltingExperience(output) == value, "Pinned Railcraft experience differs");
      require((Float)invokeChecked(vanillaExperience, vanilla, output) == (value == -1f ? .7f : value),
          "Vanilla furnace lost Railcraft override/fallback");
      require((Float)invokeChecked(smokerExperience, smoker, output) == (value == -1f ? .3f : value),
          "Et Futurum lost Railcraft override or native cache priority");
    }
    cached.clear();
    require((Float)invokeChecked(smokerExperience, smoker, output) == .5f, "Et Futurum skipped custom experience");
    custom.clear();
    require((Float)invokeChecked(smokerExperience, smoker, output) == .7f, "Et Futurum skipped vanilla experience fallback");
  }

  private static void armok() throws Exception {
    NativeCoreFixesTest.version("Avaritia", "1.77");
    Item orb = NativeCoreFixesTest.item("fox.spiteful.avaritia.compat.bloodmagic.ItemOrbArmok", "armok_return");
    ItemStack offered = new ItemStack(orb, 1, 9);
    offered.setTagInfo("ownerName", new net.minecraft.nbt.NBTTagString("native-owner"));
    require((Integer) call(orb, "getOrbLevel") == 6
        && (Integer) call(orb, "getMaxEssence") == 1000000000
        && (Boolean) call(orb, "isFilledForFree")
        && orb.hasContainerItem(offered) && orb.getContainerItem(offered) == offered,
        "Pinned Armok tier/capacity/returned-stack contract differs");
    require(BloodOrbShapedRecipes.orbLevel(orb) == 6
        && BloodOrbShapedRecipes.maxEssence(orb) == 1000000000,
        "Armok was omitted from native orb facts");
    Method read = BloodRitualRecipes.class.getDeclaredMethod("orb", Item.class);
    JsonObject result = (JsonObject) invokeChecked(read, null, orb);
    require(result.get("maxEssence").getAsInt() == 1000000000
        && result.get("free").getAsBoolean() && result.get("requireOwner").getAsBoolean(),
        "Altar lost Armok free charging, capacity or owner requirement");
    require(offered.stackSize == 1 && Items.feather.getDamage(offered) == 9
        && offered.getTagCompound().getString("ownerName").equals("native-owner"),
        "Armok inspection mutated borrowed input");
  }

  private static void bloodContainers() throws Exception {
    NativeCoreFixesTest.version("AWWayofTime", "1.7.51");
    NativeCoreFixesTest.version("Avaritia", "1.77");
    for (String name : new String[]{"WayofTime.alchemicalWizardry.common.items.EnergyBattery",
        "WayofTime.alchemicalWizardry.common.items.TranscendentBloodOrb",
        "fox.spiteful.avaritia.compat.bloodmagic.ItemOrbArmok"}) {
      Item item = NativeCoreFixesTest.item(name, "returned_" + name.substring(name.lastIndexOf('.') + 1));
      ItemStack offered = new ItemStack(item, 3, 9);
      offered.setTagInfo("ownerName", new net.minecraft.nbt.NBTTagString("native-owner"));
      ItemStack before = offered.copy();
      require(item.hasContainerItem(offered) && item.getContainerItem(offered) == offered
          && !item.doesContainerItemLeaveCraftingGrid(offered), "Pinned blood container contract differs");
      ItemStack result = ItemCallbacks.container(offered, false);
      require(result != offered && ItemStack.areItemStacksEqual(result, before),
          "Blood container lost native count, metadata or owner NBT");
      result.stackSize = 1;
      result.getTagCompound().setString("ownerName", "changed");
      require(ItemStack.areItemStacksEqual(offered, before), "Blood container result aliases borrowed input");
    }
    UnknownContainer unknown = new UnknownContainer();
    fails("recipe_unsupported", () -> ItemCallbacks.container(new ItemStack(unknown), false));
    require(unknown.calls == 0, "Unknown container was called before rejection");
  }

  private static void focusContainer() throws Exception {
    NativeCoreFixesTest.version("Thaumcraft", "4.2.3.5");
    NativeCoreFixesTest.version("dreamcraft", "2.7.268");
    Item focus=NativeCoreFixesTest.item("thaumcraft.common.items.wands.foci.ItemFocusWarding","native_focus");
    // The actual transformer injects this constructor assignment and the copy getter.
    focus.setContainerItem(focus);
    ItemStack input=new ItemStack(focus,3,9);
    input.setTagInfo("focusState",new net.minecraft.nbt.NBTTagString("native"));
    ItemStack before=input.copy(),nativeResult=focus.getContainerItem(input);
    require(nativeResult!=input && ItemStack.areItemStacksEqual(nativeResult,before)
        && focus.hasContainerItem(input),"GTNH transformed focus copy contract differs");
    ItemStack result=ItemCallbacks.container(input,false);
    require(result!=input && ItemStack.areItemStacksEqual(result,before),"Focus return lost native state or ownership");
    result.getTagCompound().setString("focusState","changed");
    require(ItemStack.areItemStacksEqual(input,before),"Focus return aliases borrowed state");
    focus.setContainerItem(null);
    fails("recipe_unsupported",()->ItemCallbacks.container(input,false));
  }

  /** Invoke the installed GTNH transformer itself; no callback body is reimplemented. */
  public static final class FocusDefinitions implements net.minecraft.launchwrapper.IClassTransformer {
    @Override public byte[] transform(String name,String transformed,byte[] bytes) {
      if(!name.equals("thaumcraft.common.items.wands.foci.ItemFocusWarding"))return bytes;
      try {
        Class<?> core=Class.forName("com.dreammaster.coremod.DreamCoreMod");
        Field obf=core.getDeclaredField("isObf");obf.setAccessible(true);obf.set(null,false);
        core.getField("patchItemFocusWarding").setBoolean(null,true);
        org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(bytes).accept(node,0);
        Object transformer=Class.forName("com.dreammaster.coremod.transformers.ItemFocusWardingTransformer").newInstance();
        transformer.getClass().getMethod("transform",org.objectweb.asm.tree.ClassNode.class).invoke(transformer,node);
        org.objectweb.asm.ClassWriter writer=new org.objectweb.asm.ClassWriter(org.objectweb.asm.ClassWriter.COMPUTE_MAXS);
        node.accept(writer);return writer.toByteArray();
      }catch(Exception failure){throw new IllegalStateException("Pinned focus transformer failed",failure);}
    }
  }

  private static void dynamicContainers(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("IC2", "2.2.828-experimental");
    NativeCoreFixesTest.version("Botania", "1.12.28-GTNH");
    Item cutter = NativeCoreFixesTest.item("ic2.core.item.tool.ItemToolCutter", "IC2:itemToolCutter");
    cutter.setMaxDamage(10);
    Item charm = NativeCoreFixesTest.item("vazkii.botania.common.item.equipment.bauble.ItemDivaCharm", "Botania:divaCharm");
    for (Item item : new Item[]{cutter, charm}) {
      ItemStack input = new ItemStack(item, 1, item == cutter ? 4 : 0);
      input.setTagInfo("retained", new net.minecraft.nbt.NBTTagString("native"));
      ItemStack before = input.copy();
      ItemStack output = new ItemStack(Items.paper);
      RecipeRow row = NativeCraftingTest.row(false, input, output);
      row.nativeCraftingInput(0, Collections.singletonList(new RecipeRow.Ingredient(input, 1, false,
          com.github.dcysteine.nesql.exporter.source.Json.object("kind", "wildcard", "meta", false, "nbt", true))));
      JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
      require(choice.getAsJsonObject("consume").get("kind").getAsString().equals("nativeContainer")
          && choice.getAsJsonObject("consume").get("callback").getAsString().equals(item == cutter ? "ic2Cutter" : "botaniaCosmetic")
          && choice.getAsJsonArray("returns").size() == 0,
          "Dynamic container was flattened into a deterministic sample return");
      require(ItemStack.areItemStacksEqual(input, before), "Dynamic container declaration mutated the borrowed input");
      row.itemOutput(0, output, 10000);
      NativeContractFixture.writeOwnedWithDurability("native-container-" + (item == cutter ? "cutter" : "cosmetic"), row, Arrays.asList(input, output));
    }
    // A real cosmetic is returned, while the unadorned charm has no container.
    ItemStack input = new ItemStack(charm), cosmetic = new ItemStack(Items.paper, 2, 7);
    cosmetic.setTagInfo("decoration", new net.minecraft.nbt.NBTTagString("native"));
    net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
    input.setTagInfo("cosmeticItem", cosmetic.writeToNBT(tag));
    require(ItemStack.areItemStacksEqual(charm.getContainerItem(input.copy()), cosmetic)
        && charm.getContainerItem(new ItemStack(charm)) == null, "Pinned cosmetic return contract differs");
  }

  private static void meta99(NativeRegistryScope scope) throws Exception {
    Class<?> prefixes = type("gregtech.api.enums.OrePrefixes");
    Item item = NativeCoreFixesTest.item("gregtech.common.items.MetaGeneratedItem99", "meta99_return");
    Object molten = field(prefixes, null, "cellMolten");
    ItemStack stored = new ItemStack(Items.bucket, 3, 7);
    stored.setTagInfo("proof", new net.minecraft.nbt.NBTTagString("native-container"));
    scope.replace(prefixes, molten, "mContainerItem", stored);
    List<?> cracked = (List<?>) field(type("gregtech.common.items.MetaGeneratedItem99"), null, "CRACKED_CELL_TYPES");
    scope.replace(prefixes, cracked.get(0), "mContainerItem", new ItemStack(Items.bowl, 2, 4));
    for (int meta : new int[]{-1, 0, 999, 1000, 9999, 10000, 10999, 10000 + cracked.size()*1000}) {
      ItemStack query = new ItemStack(item, 1, meta);
      List<GtItemListBinding> bindings = new ArrayList<>();
      ItemStack actual = ItemCallbacks.container(query, false, bindings);
      ItemStack expected = item.getContainerItem(query);
      require(ItemStack.areItemStacksEqual(actual, expected), "GT99 prefix container differs at " + meta);
      if (expected != null) {
        require(actual != expected, "GT99 returned a borrowed native container");
        actual.stackSize = 19;
        require(expected.stackSize != 19, "Owned GT99 result mutated native prefix container");
      }
    }
    List<GtItemListBinding> bindings = new ArrayList<>();
    ItemCallbacks.container(new ItemStack(item, 1, 19), false, bindings);
    require(bindings.size() == 1, "GT99 prefix result lost its source binding");
    stored.getTagCompound().setString("proof", "changed");
    fails("recipe_changed", bindings.get(0)::verify);
  }

  private static void meta98(NativeRegistryScope scope) throws Exception {
    Item item = NativeCoreFixesTest.item("gregtech.common.items.MetaGeneratedItem98", "meta98_return");
    Class<?> list = type("gregtech.api.enums.ItemList");
    Object entry = field(list, null, "Cell_Empty");
    ItemStack stored = new ItemStack(Items.bucket, 4, 7);
    stored.setTagInfo("proof", new net.minecraft.nbt.NBTTagString("native-empty-cell"));
    scope.replace(list, entry, "mStack", stored);
    scope.replace(list, entry, "mHasNotBeenSet", false);
    scope.replace(list, entry, "mDeprecated", false);
    for (int meta : new int[]{0, 999, 32767}) {
      ItemStack query = new ItemStack(item, 2, meta);
      List<GtItemListBinding> bindings = new ArrayList<>();
      ItemStack actual = ItemCallbacks.container(query, false, bindings);
      ItemStack expected = item.getContainerItem(query);
      require(ItemStack.areItemStacksEqual(actual, expected) && actual.stackSize == 1
          && actual != stored && actual.getTagCompound() != stored.getTagCompound(),
          "GT98 empty-cell count, metadata, NBT or ownership differs");
      actual.getTagCompound().setString("proof", "owned");
      require(stored.getTagCompound().getString("proof").equals("native-empty-cell")
          && query.stackSize == 2 && Items.feather.getDamage(query) == meta, "GT98 mutated native input/result");
      require(bindings.size() == 1, "GT98 empty-cell result lost its binding");
      stored.getTagCompound().setString("proof", "changed");
      fails("recipe_changed", bindings.get(0)::verify);
      stored.getTagCompound().setString("proof", "native-empty-cell");
    }
  }

  private static void lootPhysical() {
    UnknownMetadata item = new UnknownMetadata();
    cpw.mods.fml.common.registry.GameRegistry.registerItem(item, "nesql_loot_physical");
    Object descriptor = invoke(type("eu.usrv.yamcore.auxiliary.ItemDescriptor"), null, "fromString",
        new Class<?>[]{String.class, boolean.class}, "minecraft:nesql_loot_physical:19", false);
    ItemStack resolved = (ItemStack)invoke(descriptor.getClass(), descriptor, "getItemStack",
        new Class<?>[]{int.class}, 3);
    require(resolved != null && Items.feather.getDamage(resolved) == 19 && resolved.stackSize == 3
        && item.calls == 0, "Native loot resolver unexpectedly invokes semantic damage");
    LootBagItems.audit(item);
    require(resolved.getMaxStackSize() == 64 && item.calls == 0,
        "Loot stack-limit inspection invoked unrelated metadata");
    UnknownLimit unknown = new UnknownLimit();
    fails("recipe_unsupported", () -> LootBagItems.audit(unknown));
    require(unknown.calls == 0, "Loot admission executed an unknown stack-limit callback");
  }

  private static void gtVisual(NativeRegistryScope scope) throws Exception {
    Class<?> dispatcherType = type("gregtech.common.render.items.MetaGeneratedItemRenderer"),
        rendererType = type("gregtech.common.render.items.GeneratedMaterialRenderer"),
        api = type("gregtech.api.GregTechAPI"), materials = type("gregtech.api.enums.Materials");
    Object dispatcher = scope.allocate(dispatcherType), selected = scope.allocate(rendererType),
        fallback = scope.allocate(rendererType), plain = scope.allocate(rendererType),
        iron = field(materials, null, "Iron");
    scope.replace(dispatcherType, null, "specialRenderers", new HashMap<>());
    scope.replace(dispatcherType, dispatcher, "mMaterialRenderer", fallback);
    scope.replace(dispatcherType, dispatcher, "mItemRenderer", plain);
    Object table = java.lang.reflect.Array.newInstance(materials, 1000);
    java.lang.reflect.Array.set(table, 19, iron);
    scope.replace(api, null, "sGeneratedMaterials", table);
    scope.replace(materials, iron, "renderer", selected);
    Item item = NativeCoreFixesTest.item("gregtech.common.items.MetaGeneratedItem01", "visual_x32");
    set(item, "mOffset", (short)32000);
    Method nativeSelect = dispatcherType.getDeclaredMethod("getRendererForItemStack", ItemStack.class);
    ItemStack query = new ItemStack(item, 1, 1019);
    require(invokeChecked(nativeSelect, dispatcher, query) == selected,
        "Native X32 does not select the generated material renderer");
    GtVisualBinding binding = new GtVisualBinding(query, dispatcher, selected);
    binding.check();
    scope.replace(materials, iron, "renderer", fallback);
    fails("animation_changed", binding::check);
    scope.replace(materials, iron, "renderer", selected);
    java.lang.reflect.Array.set(table, 19, null);
    fails("animation_changed", binding::check);
    java.lang.reflect.Array.set(table, 19, iron);
    ItemStack aboveOffset = new ItemStack(item, 1, 32019);
    require(invokeChecked(nativeSelect, dispatcher, aboveOffset) == plain,
        "Native above-offset item unexpectedly uses a material renderer");
    new GtVisualBinding(aboveOffset, dispatcher, plain).check();
  }

  private static void blood(NativeRegistryScope scope, boolean capacity) throws Exception {
    NativeCoreFixesTest.version("AWWayofTime", "1.7.52");
    NativeCoreFixesTest.version("ForbiddenMagic", "0.8.4-GTNH");
    Item orb = NativeCoreFixesTest.item("fox.spiteful.forbidden.items.ItemDivineOrb", "observed_divine_orb");
    ItemStack offered = new ItemStack(orb, 1, 9);
    offered.setTagInfo("ownerName", new net.minecraft.nbt.NBTTagString("retained-owner"));
    require((Integer) call(orb, "getOrbLevel") == 6
        && (Integer) call(orb, "getMaxEssence") == 80000000,
        "Pinned Divine Orb constants differ");
    require(orb.hasContainerItem(offered) && orb.getContainerItem(offered) == offered,
        "Native Divine Orb does not preserve the offered stack");
    if (capacity) {
      Method read = BloodRitualRecipes.class.getDeclaredMethod("orb", Item.class);
      JsonObject result = (JsonObject) invokeChecked(read, null, orb);
      require(result.get("maxEssence").getAsInt() == 80000000
          && !result.get("free").getAsBoolean() && result.get("requireOwner").getAsBoolean(),
          "Altar lost Divine Orb capacity or ownership requirement");
    } else {
      require(BloodOrbShapedRecipes.orbLevel(orb) == 6,
          "Native Divine Orb is absent from minimum-tier crafting alternatives");
      require(BloodOrbShapedRecipes.orbs().contains(orb), "Native orb census lost Divine Orb");
    }
    require(offered.getItemDamage() == 9
        && offered.getTagCompound().getString("ownerName").equals("retained-owner"),
        "Reading Divine Orb facts changed offered metadata/NBT");
  }

  @SuppressWarnings("unchecked")
  private static void containers(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("bartworks", "5.09.51.482");
    Class<?> bw = type("bartworks.system.material.BWMetaGeneratedItems"),
        prefixes = type("gregtech.api.enums.OrePrefixes"),
        unifier = type("gregtech.api.util.GTOreDictUnificator"),
        itemList = type("gregtech.api.enums.ItemList");
    Item item = NativeCoreFixesTest.item(bw.getName(), "observed_bw_container");
    Map<String, ItemStack> names = new HashMap<>((Map<String, ItemStack>)field(unifier, null, "sName2StackMap"));
    scope.replace(unifier, null, "sName2StackMap", names);
    ItemStack emptyCell = new ItemStack(Items.bucket, 4, 3);
    emptyCell.setTagInfo("retained", new net.minecraft.nbt.NBTTagString("cell-empty"));
    names.put("cellEmpty", emptyCell);
    for (String entryName : new String[]{"FR_WaxCapsule", "FR_RefractoryCapsule"}) {
      Object entry = field(itemList, null, entryName);
      ItemStack original = new ItemStack(Items.paper, 0, entryName.equals("FR_WaxCapsule") ? 7 : 8);
      original.setTagInfo("owner", new net.minecraft.nbt.NBTTagString("native"));
      scope.replace(itemList, entry, "mStack", original);
      scope.replace(itemList, entry, "mHasNotBeenSet", false);
      scope.replace(itemList, entry, "mDeprecated", true);
      scope.replace(itemList, entry, "mWarned", false);
    }
    for (String prefix : new String[]{"cell", "cellPlasma", "cellMolten", "capsule", "capsuleMolten", "dust"}) {
      set(item, "orePrefixes", field(prefixes, null, prefix));
      ItemStack offered = new ItemStack(item, 0, 17);
      List<GtItemListBinding> bindings = new ArrayList<>();
      ItemStack actual = ItemCallbacks.container(offered, false, bindings);
      require(prefix.equals("dust") ? actual == null
          : actual != null && actual.stackSize == 1
              && actual.getItem() == (prefix.startsWith("cell") ? Items.bucket : Items.paper)
              && actual.getItemDamage() == (prefix.startsWith("cell") ? 3 : prefix.equals("capsule") ? 7 : 8),
          "BW native container selection/count/meta differs for " + prefix);
      require(ItemCallbacks.hasContainer(offered) == !prefix.equals("dust"),
          "BW presence and direct container result disagree");
      Object observedEntry = prefix.equals("capsule") || prefix.equals("capsuleMolten")
          ? field(itemList, null, prefix.equals("capsule") ? "FR_WaxCapsule" : "FR_RefractoryCapsule") : null;
      if (observedEntry != null) {
        require(!(Boolean)field(observedEntry, "mWarned"), "BW capture wrote native deprecation warning state");
        set(observedEntry, "mWarned", true);
      }
      try {
        require(ItemStack.areItemStacksEqual(actual, item.getContainerItem(offered.copy())),
            "Read-only BW result differs from pinned native callback");
      } finally { if (observedEntry != null) set(observedEntry, "mWarned", false); }
      if (!prefix.equals("dust")) require(bindings.size() == 1, "BW container lost its native binding");
      if (prefix.startsWith("cell")) {
        require(actual.getTagCompound().getString("retained").equals("cell-empty"), "BW cell NBT lost");
        names.put("cellEmpty", emptyCell.copy());
        fails("recipe_changed", () -> bindings.get(0).verify());
        names.put("cellEmpty", emptyCell);
        Object originalPrefix = field(item, "orePrefixes");
        set(item, "orePrefixes", field(prefixes, null, "capsule"));
        fails("recipe_changed", () -> bindings.get(0).verify());
        set(item, "orePrefixes", originalPrefix);
      } else if (!prefix.equals("dust")) {
        Object entry = field(itemList, null, prefix.equals("capsule") ? "FR_WaxCapsule" : "FR_RefractoryCapsule");
        // Native get's deprecation warning is an intentional observation; restore it before binding checks.
        set(entry, "mWarned", false);
        require(actual.getTagCompound().getString("owner").equals("native"), "BW capsule NBT lost");
        capsuleUnification(scope, item, offered, entry);
      }
      require(offered.stackSize == 0 && offered.getItemDamage() == 17, "BW query changed offered stack");
    }
    set(item, "orePrefixes", field(prefixes, null, "cell"));
    names.remove("cellEmpty");
    int oreNames = OreDictionary.getOreNames().length;
    require(ItemCallbacks.container(new ItemStack(item, 1, 17), false) == null
        && OreDictionary.getOreNames().length == oreNames, "BW query registered an absent ore name");
    OreDictionary.registerOre("cellEmpty", new ItemStack(Items.glass_bottle, 2, 5));
    ItemStack fallback = ItemCallbacks.container(new ItemStack(item, 1, 17), false);
    require(fallback.getItem() == Items.glass_bottle && fallback.stackSize == 1 && fallback.getItemDamage() == 5,
        "BW Empty material lost native first-ore fallback");
    List<ItemStack> fallbackMembers = (List<ItemStack>)(List<?>)NativeOreTable.members("cellEmpty");
    fallbackMembers.add(0, new ItemStack(Items.bucket, -1, 3));
    try {
      require(item.getContainerItem(new ItemStack(item)) == null
          && ItemCallbacks.container(new ItemStack(item), false) == null,
          "BW first invalid ore stack incorrectly fell through to a later candidate");
    } finally { fallbackMembers.remove(0); }
    Object cell = field(prefixes, null, "cell");
    Set<Object> disabled = new HashSet<>((Collection<?>)field(cell, "mDisabledItems"));
    scope.replace(prefixes, cell, "mDisabledItems", disabled);
    disabled.add(field(type("gregtech.api.enums.Materials"), null, "Empty"));
    List<GtItemListBinding> disabledBindings = new ArrayList<>();
    require(ItemCallbacks.container(new ItemStack(item, 1, 17), false, disabledBindings) == null,
        "BW empty cell ignored native disabled material membership");
    disabled.clear();
    fails("recipe_changed", disabledBindings.get(0)::verify);
    UnknownContainer unknown = new UnknownContainer();
    fails("recipe_unsupported", () -> ItemCallbacks.container(new ItemStack(unknown), false));
    require(unknown.calls == 0, "Unknown container callback executed before rejection");
  }

  @SuppressWarnings("unchecked")
  private static void capsuleUnification(NativeRegistryScope scope, Item item, ItemStack offered, Object entry) throws Exception {
    Class<?> unifier = type("gregtech.api.util.GTOreDictUnificator"),
        dataType = type("gregtech.api.objects.ItemData"),
        prefixes = type("gregtech.api.enums.OrePrefixes"), materials = type("gregtech.api.enums.Materials");
    Object borrowed = field(unifier, null, "sItemStack2DataMap");
    Map<ItemStack, Object> associations = (Map<ItemStack, Object>)call(borrowed, "clone");
    scope.replace(unifier, null, "sItemStack2DataMap", associations);
    scope.replace(type("gregtech.api.GregTechAPI"), null, "sUnificationEntriesRegistered", true);
    Object data = dataType.getConstructor(prefixes, materials).newInstance(field(prefixes, null, "dust"), field(materials, null, "Iron"));
    ItemStack source = (ItemStack)field(entry, "mStack"), target = new ItemStack(Items.iron_ingot, 4, 6);
    target.setTagInfo("discarded", new net.minecraft.nbt.NBTTagString("target-only"));
    associations.put(source, data);
    Map<String, ItemStack> names = (Map<String, ItemStack>)field(unifier, null, "sName2StackMap");
    names.put("dustIron", target);
    List<GtItemListBinding> bindings = new ArrayList<>();
    ItemStack result = ItemCallbacks.container(offered, false, bindings);
    require(result.getItem() == Items.iron_ingot && result.stackSize == 1 && result.getItemDamage() == 6
        && result.getTagCompound().getString("owner").equals("native") && !result.getTagCompound().hasKey("discarded"),
        "BW capsule bypassed native ItemList unification or original NBT");
    require(field(data, "mUnificationTarget") == null && !(Boolean)field(entry, "mWarned")
        && field(entry, "mStack") == source && source.stackSize == 0,
        "BW capsule lookup wrote native lazy cache, warning or borrowed stack");
    names.put("dustIron", target.copy());
    fails("recipe_changed", bindings.get(0)::verify);
    names.put("dustIron", target);
    set(data, "mBlackListed", true);
    require(ItemCallbacks.container(offered, false).getItem() == Items.paper, "BW capsule ignored native blacklist");
    associations.remove(source);
  }

  private static void enchanter(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("endercore", "0.4.8");
    scope.replace(Enchantment.class, null, "enchantmentsList", Enchantment.enchantmentsList.clone());
    Enchantment xp = (Enchantment)scope.allocate(type("com.enderio.core.common.enchant.EnchantXPBoost"));
    require(xp.getMaxLevel() == 3, "Pinned XP Boost maximum differs");
    require(EnchanterSource.maximum(xp) == 3, "XP Boost cannot generate all three enchanter levels");
    Enchantment autoSmelt = (Enchantment)scope.allocate(type("com.enderio.core.common.enchant.EnchantAutoSmelt"));
    require(autoSmelt.getMaxLevel() == 1, "Pinned Auto Smelt maximum differs");
    require(EnchanterSource.maximum(autoSmelt) == 1, "Auto Smelt cannot generate its native enchanter level");
  }

  private static void railBucket() throws Exception {
    NativeCoreFixesTest.version("Railcraft", "9.16.33");
    Item item = NativeCoreFixesTest.item("mods.railcraft.common.fluids.ItemBucketRailcraft", "observed_rail_bucket");
    item.setContainerItem(Items.bucket);
    ItemStack stored = new ItemStack(Items.glass_bottle, 2, 4);
    stored.setTagInfo("retained", new net.minecraft.nbt.NBTTagString("configured-container"));
    set(item, "container", stored);
    ItemStack offered = new ItemStack(item, 0, 19);
    offered.setTagInfo("ignored", new net.minecraft.nbt.NBTTagString("offered"));
    ItemStack nativeResult = item.getContainerItem(offered);
    ItemStack actual = FuelContainers.fixed(offered);
    require(ItemStack.areItemStacksEqual(nativeResult, actual) && actual != stored && actual.stackSize == 2
        && actual.getItemDamage() == 4 && actual.getTagCompound().getString("retained").equals("configured-container"),
        "Railcraft fuel bucket lost its configured copied return");
    actual.stackSize = 31;
    require(stored.stackSize == 2 && offered.stackSize == 0 && offered.getItemDamage() == 19,
        "Railcraft bucket query mutated stored/offered stack");
    item.setContainerItem(null);
    require(!item.hasContainerItem(offered) && FuelContainers.fixed(offered).stackSize == 2,
        "Railcraft fuel getter was incorrectly gated by the separate vanilla container predicate");
    codechicken.nei.recipe.FurnaceRecipeHandler.FuelPair pair =
        new codechicken.nei.recipe.FurnaceRecipeHandler.FuelPair(new ItemStack(item), 300);
    try (FuelRecipes reader = new FuelRecipes(com.github.dcysteine.nesql.exporter.source.Json.object(
        "owner", "NEI", "handler", FuelRecipes.HANDLER, "key", "fuel"),
        new ArrayList<>(Collections.singletonList(pair)), () -> true)) {
      stored.stackSize = 3;
      fails("environment_changed", reader::finishVerification);
      stored.stackSize = 2;
    }
  }

  private static void gardens(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("harvestcraft", "1.3.2-GTNH");
    Method audit = HarvestCraftRecipes.class.getDeclaredMethod("admitDamageDropped", Block.class);
    for (String kind : new String[]{"Normal", "Water", "Mushroom", "Desert"}) {
      Block garden = (Block)scope.allocate(type("com.pam.harvestcraft.BlockPam" + kind + "Garden"));
      invokeChecked(audit, null, garden);
      for (int metadata : new int[]{0, 5, 32767})
        require(garden.damageDropped(metadata) == metadata, "Native garden metadata changed");
    }
    NativeCoreFixesTest.version("harvestthenether", "1.7.10");
    Class<?> blocks = type("com.pam.harvestcraft.BlockRegistry");
    for (Field field : blocks.getFields())
      if (field.getType() == Block.class && field.getName().endsWith("Garden"))
        scope.replace(blocks, null, field.getName(), null);
    Method register = NativeHarvestCraftTest.class.getDeclaredMethod("block", String.class, String.class);
    register.setAccessible(true);
    Block nether = (Block)invokeChecked(register, null, "com.pam.harvestthenether.BlockNetherGarden", "harvestthenether:netherGarden");
    Class<?> netherBlocks = type("com.pam.harvestthenether.BlockRegistry"), netherItems = type("com.pam.harvestthenether.ItemRegistry");
    scope.replace(netherBlocks, null, "netherGarden", nether);
    scope.replace(netherBlocks, null, "gardendropAmount", 2);
    scope.replace(netherBlocks, null, "gardensdropSeeds", false);
    for (String name : new String[]{"bloodleaf", "fleshroot", "marrowberry"})
      scope.replace(netherItems, null, name + "Item", Items.paper);
    Method read = HarvestCraftRecipes.class.getDeclaredMethod("read", String.class);
    read.setAccessible(true);
    require(((List<?>)invokeChecked(read, null, "com.pam.harvestcraft.GardenRecipeHandler")).size() == 1,
        "Installed Nether runtime version rejected its native garden");
    UnknownGarden unknown = new UnknownGarden();
    try { invokeChecked(audit, null, unknown); throw new AssertionError("Unknown garden callback accepted"); }
    catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported") && unknown.calls == 0,
        "Unknown garden metadata callback executed before rejection"); }
  }

  private static void loot(NativeRegistryScope scope) throws Exception {
    Item item = NativeCoreFixesTest.item("gregtech.common.items.MetaGeneratedItem01", "observed_loot_item");
    Class<?> prefixType = type("gregtech.api.enums.OrePrefixes");
    Object dust = field(prefixType, null, "dust"), prefixes = Array.newInstance(prefixType, 32);
    Array.set(prefixes, 0, dust);
    set(item, "mGeneratedPrefixList", prefixes);
    set(item, "mElectricStats", new ConcurrentHashMap<>());
    set(item, "mFluidContainerStats", new ConcurrentHashMap<>());
    Field limit = declared(prefixType, "mDefaultStackSize");
    byte previous = limit.getByte(dust);
    try {
      limit.setByte(dust, (byte)7);
      LootBagItems.audit(item);
      require(new ItemStack(item, 1, 0).getMaxStackSize() == 7,
          "Loot splitting lost native generated-prefix stack limit");
    } finally { limit.setByte(dust, previous); }
    UnknownLimit unknown = new UnknownLimit();
    fails("recipe_unsupported", () -> LootBagItems.audit(unknown));
    require(unknown.calls == 0, "Unknown loot limit callback executed before rejection");
  }

  private static void witchery(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("witchery", "0.24.1");
    Item item = NativeCoreFixesTest.item("gregtech.api.items.GTGenericItem", "observed_brew_item");
    set(item, "mName", "item.nesqltest.brew-key");
    Method name = WitcheryBrewActions.class.getDeclaredMethod("baseName", Item.class);
    require(invokeChecked(name, null, item).equals("item.nesqltest.brew-key")
        && item.getUnlocalizedName().equals("item.nesqltest.brew-key"),
        "Witchery native GT key hash lost the exact base item name");
    Class<?> keyType = type(WitcheryBrewActions.KEY);
    Object physical = keyType.getConstructor(Item.class, int.class).newInstance(item, 9),
        target = keyType.getConstructor(Item.class).newInstance(Items.paper),
        action = scope.allocate(type("com.emoniph.witchery.brewing.action.BrewActionRitualRecipe"));
    set(action, "ITEM_KEY", target);
    set(action, "powerCost", type("com.emoniph.witchery.brewing.AltarPower").getConstructor(int.class).newInstance(17));
    require(physical.hashCode() == 629 + "item.nesqltest.brew-key".hashCode(), "Native BrewItemKey hash differs");
    Hashtable<Object, Object> table = new Hashtable<>(); table.put(physical, action);
    require(table.get(keyType.getConstructor(Item.class, int.class).newInstance(item, 9)) == action
        && table.get(keyType.getConstructor(Item.class, int.class).newInstance(item, 8)) == null,
        "Native GT brew-key fixture does not distinguish literal metadata");
    try (WitcheryBrewActions lookup = new WitcheryBrewActions(table)) {
      WitcheryBrewActions.Key resolved = lookup.resolve(new ItemStack(item, 1, 9));
      require(resolved != null && resolved.item == Items.paper, "GT brew key did not resolve its native bucket/action");
      require(lookup.resolve(new ItemStack(item, 1, 8)) == null, "GT brew key widened native literal metadata");
      lookup.finishVerification(table);
      set(item, "mName", "item.nesqltest.changed-brew-key");
      fails("environment_changed", () -> lookup.finishVerification(table));
    }
    UnknownName unknown = new UnknownName();
    try { invokeChecked(name, null, unknown); throw new AssertionError("Unknown brew name accepted"); }
    catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported") && unknown.calls == 0,
        "Unknown brew name callback executed before rejection"); }
  }

  private static void chiselScale(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("chisel", "2.16.15-GTNH");
    Class<?> carving=type("team.chisel.carving.Carving");Object registry=field(carving,null,"chisel");
    scope.replace(carving,registry,"groups",type("team.chisel.carving.GroupList").newInstance());
    Method tool=NativeChiselTest.class.getDeclaredMethod("nativeTool");invokeChecked(tool,null);
    for(int meta=0;meta<600;meta++)invoke(carving,registry,"addVariation",
        new Class<?>[]{String.class,Block.class,int.class,int.class},"native-shared-large",Blocks.stone,meta,meta);
    TemplateRecipeHandler handler=(TemplateRecipeHandler)type("team.chisel.compat.nei.RecipeHandlerChisel").newInstance();
    try(ChiselRecipes reader=new ChiselRecipes(handler)) {
      require(reader.size()==1200,"Large native group lost single/stack target pages");
      ChiselRecipes.Snapshot snapshot=(ChiselRecipes.Snapshot)field(reader,"snapshot");
      require(snapshot.sources.size()==600,"Large group lost source metadata partitions");
      for(ChiselRecipes.Decision decision:snapshot.sources)require(decision.targets.size()==600,
          "Large group lost native ordered target candidates");
      reader.finishVerification();
      invoke(carving,registry,"addVariation",new Class<?>[]{String.class,Block.class,int.class,int.class},
          "native-shared-large",Blocks.stone,600,600);
      fails("recipe_changed",reader::finishVerification);
    }
  }

  private static void chisel(NativeRegistryScope scope) throws Exception {
    NativeCoreFixesTest.version("chisel", "2.16.15-GTNH");
    Class<?> carvingType = type("team.chisel.carving.Carving"), config = type("team.chisel.config.Configurations");
    Object registry = field(carvingType, null, "chisel");
    scope.replace(carvingType, registry, "groups", type("team.chisel.carving.GroupList").newInstance());
    scope.replace(config, null, "chiselBackToVanillaLeaves", true);
    Method tool = NativeChiselTest.class.getDeclaredMethod("nativeTool");
    invokeChecked(tool, null);
    Class<?> groupType = type("team.chisel.Features$57$1"), variationType = type("com.cricketcraft.chisel.api.carving.ICarvingVariation");
    Constructor<?> constructor = groupType.getDeclaredConstructors()[0]; constructor.setAccessible(true);
    Object group = constructor.newInstance(null, "nesqltest:observed_leaves");
    Class<?> simple = type("com.cricketcraft.chisel.api.carving.CarvingUtils$SimpleCarvingVariation");
    for (Block block : new Block[]{Blocks.stone, Blocks.leaves, Blocks.leaves2}) {
      Object variation = simple.getConstructor(Block.class, int.class, int.class).newInstance(block, 0, 0);
      Method add = groupType.getMethod("addVariation", variationType); add.setAccessible(true); add.invoke(group, variation);
    }
    Object groups = field(registry, "groups");
    invoke(groups.getClass(), groups, "add", new Class<?>[]{type("com.cricketcraft.chisel.api.carving.ICarvingGroup")}, group);
    TemplateRecipeHandler handler = (TemplateRecipeHandler)type("team.chisel.compat.nei.RecipeHandlerChisel").newInstance();
    Method parity = NativeChiselTest.class.getDeclaredMethod("parity", Object.class, ChiselRecipes.class);
    for (boolean vanillaLeaves : new boolean[]{false, true}) {
      set(config, null, "chiselBackToVanillaLeaves", vanillaLeaves);
      Method nativeGet = groupType.getMethod("getVariations"); nativeGet.setAccessible(true);
      require(((List<?>)nativeGet.invoke(group)).size() == (vanillaLeaves ? 3 : 1), "Native leaf filtering differs");
      try (ChiselRecipes reader = new ChiselRecipes(handler)) {
        invokeChecked(parity, null, registry, reader);
        ChiselRecipes.Snapshot snapshot = (ChiselRecipes.Snapshot)field(reader, "snapshot");
        require(snapshot.sources.stream().anyMatch(decision -> decision.source.getItem() == Item.getItemFromBlock(Blocks.stone)
            && decision.targets.size() == (vanillaLeaves ? 3 : 1)), "Chisel leaf targets ignore native config");
        set(config, null, "chiselBackToVanillaLeaves", !vanillaLeaves);
        fails("recipe_changed", reader::finishVerification);
      }
    }
  }

  private static Field declared(Class<?> owner, String name) throws Exception {
    Field result = owner.getDeclaredField(name); result.setAccessible(true); return result;
  }
  private static Object call(Object target, String name) {
    return invoke(target.getClass(), target, name, new Class<?>[0]);
  }
  private static void set(Object target, String name, Object value) throws Exception {
    for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
      try { declared(owner, name).set(target, value); return; } catch (NoSuchFieldException missing) { }
    }
    throw new NoSuchFieldException(name);
  }
  private static void set(Class<?> owner, Object target, String name, Object value) throws Exception {
    declared(owner, name).set(target, value);
  }
  private static Object invokeChecked(Method method, Object target, Object... args) throws Exception {
    method.setAccessible(true);
    try { return method.invoke(target, args); }
    catch (InvocationTargetException failure) {
      if (failure.getCause() instanceof Exception) throw (Exception)failure.getCause();
      if (failure.getCause() instanceof Error) throw (Error)failure.getCause();
      throw failure;
    }
  }
  private static void fails(String code, Runnable operation) {
    try { operation.run(); throw new AssertionError("Expected " + code); }
    catch (Jobs.Fault expected) { require(expected.code.equals(code), "Wrong callback failure: " + expected.code); }
  }
  private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
  private static final class UnknownContainer extends Item {
    int calls;
    @Override public boolean hasContainerItem(ItemStack stack) { calls++; throw new AssertionError("Unknown container executed"); }
  }
  private static final class UnknownGarden extends Block {
    int calls;
    UnknownGarden() { super(Material.plants); }
    @Override public int damageDropped(int metadata) { calls++; throw new AssertionError("Unknown garden metadata executed"); }
  }
  private static final class UnknownLimit extends Item {
    int calls;
    @Override public int getItemStackLimit(ItemStack stack) { calls++; throw new AssertionError("Unknown loot limit executed"); }
  }
  private static final class UnknownMetadata extends Item {
    int calls;
    @Override public int getDamage(ItemStack stack) { calls++; throw new AssertionError("Unrelated metadata callback executed"); }
  }
  private static final class UnknownName extends Item {
    int calls;
    @Override public String getUnlocalizedName() { calls++; throw new AssertionError("Unknown brew name executed"); }
  }
}
