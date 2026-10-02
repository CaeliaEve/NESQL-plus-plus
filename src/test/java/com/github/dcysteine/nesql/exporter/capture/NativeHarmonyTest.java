package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import gregtech.api.enums.Materials;
import gregtech.api.enums.MaterialsUEVplus;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.*;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Owned native recipe/lookup records. Does not construct, tick or run a live machine. */
final class NativeHarmonyTest {
    static void run() throws Exception {
        set(cpw.mods.fml.common.Loader.instance(), "namedMods", new HashMap<String, cpw.mods.fml.common.ModContainer>());
        Materials.Hydrogen.toString(); // Material bootstrap owns initialization of MaterialsUEVplus.
        for (Materials material : new Materials[]{Materials.Helium, Materials.Iron, Materials.Calcium, Materials.Niobium,
                Materials.Nitrogen, Materials.Zinc, Materials.Silver, Materials.Titanium, Materials.Radon, Materials.Nickel,
                Materials.Boron, Materials.Sulfur, Materials.Americium, Materials.Bismuth, Materials.Oxygen, Materials.Tin}) {
            Fluid plasma = new Fluid("fixture.plasma." + material.mName.toLowerCase(java.util.Locale.ROOT));
            FluidRegistry.registerFluid(plasma); material.mPlasma = plasma;
        }
        for (Materials material : new Materials[] {MaterialsUEVplus.WhiteDwarfMatter, MaterialsUEVplus.BlackDwarfMatter,
                MaterialsUEVplus.Universium, MaterialsUEVplus.RawStarMatter, MaterialsUEVplus.SpaceTime}) {
            Fluid fluid = new Fluid(material == MaterialsUEVplus.SpaceTime ? "molten.spacetime" : material == MaterialsUEVplus.RawStarMatter ? "rawstarmatter" : material.mName.toLowerCase(java.util.Locale.ROOT));
            FluidRegistry.registerFluid(fluid); material.mFluid = fluid; material.mStandardMoltenFluid = fluid;
        }
        for (Materials material : new Materials[] {Materials.Hydrogen, Materials.Helium}) {
            Fluid fluid = new Fluid(material.mName.toLowerCase(java.util.Locale.ROOT)); FluidRegistry.registerFluid(fluid); material.mGas = fluid;
        }
        Class<?> nativeType = type("tectech.recipe.EyeOfHarmonyRecipe");
        Object nativeRecipe = allocate(nativeType);
        ItemStack trigger = new ItemStack(Blocks.stone);
        set(nativeRecipe, "recipeTriggerItem", trigger);
        set(nativeRecipe, "hydrogenRequirement", 1_000_000_000L); set(nativeRecipe, "heliumRequirement", 2_000_000_000L);
        set(nativeRecipe, "miningTimeSeconds", 18000L); set(nativeRecipe, "euStartCost", 123456789012345L);
        set(nativeRecipe, "euOutput", 234567890123456L); set(nativeRecipe, "baseSuccessChance", .95d);
        set(nativeRecipe, "rocketTier", 1L); set(nativeRecipe, "spacetimeCasingTierRequired", 1L);
        ItemStack product = new ItemStack(Items.diamond, 1);
        Object wrapped = type("tectech.util.ItemStackLong").getConstructor(ItemStack.class, long.class).newInstance(product, 9_007_199_254_740_993L);
        set(nativeRecipe, "outputItems", new ArrayList<>(Collections.singletonList(wrapped)));
        List<Object> nativeFluids = new ArrayList<>();
        for (FluidStack fluid : new FluidStack[] {MaterialsUEVplus.RawStarMatter.getFluid(1), MaterialsUEVplus.WhiteDwarfMatter.getFluid(1)})
            nativeFluids.add(type("tectech.util.FluidStackLong").getConstructor(FluidStack.class, long.class).newInstance(fluid, 8000000L));
        set(nativeRecipe, "outputFluids", nativeFluids);
        Object storage = allocate(type("tectech.recipe.EyeOfHarmonyRecipeStorage"));
        set(storage, "blocksMapInverted", new HashMap<>(Collections.singletonMap(Blocks.stone, "planet")));
        set(storage, "recipeHashMap", new HashMap<>(Collections.singletonMap("planet", nativeRecipe)));
        ItemStack different = trigger.copy(); different.setItemDamage(7); different.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(1));
        require(invoke(storage.getClass(), storage, "recipeLookUp", new Class<?>[]{ItemStack.class}, different) == nativeRecipe, "Native trigger ignores meta/NBT");
        Class<?> adapter = type("com.github.dcysteine.nesql.exporter.capture.Harmony");
        RecipeRow row = row(trigger, product);
        @SuppressWarnings("unchecked") List<RecipeRow> rows = (List<RecipeRow>) invoke(adapter, null, "rows",
                new Class<?>[] {nativeType, RecipeRow.class}, nativeRecipe, row);
        require(rows.size() == 2, "Missing single/parallel branches");
        JsonObject single = rows.get(0).record, parallel = rows.get(1).record;
        require(single.get("duration").isJsonNull() && single.get("energy").isJsonNull(), "False fixed timing or EU/t");
        require(single.getAsJsonArray("inputs").size() == 3 && parallel.getAsJsonArray("inputs").size() == 2, "Fluid buffers not mode specific");
        JsonObject choice = parallel.getAsJsonArray("inputs").get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        require(choice.get("amount").getAsLong() == 198400L && choice.getAsJsonObject("consume").get("kind").getAsString().equals("buffer"), "Parallel threshold or whole-buffer consumption lost");
        require(single.getAsJsonArray("outputs").size() == 4, "Failure output missing");
        JsonObject output = single.getAsJsonArray("outputs").get(0).getAsJsonObject();
        require(output.get("amount").isJsonNull() && output.getAsJsonObject("quantity").get("nominal").getAsString().equals("9007199254740993"), "Long output base rounded or made fixed");
        require(single.getAsJsonArray("outputs").get(3).getAsJsonObject().getAsJsonObject("quantity").get("nominal").getAsLong() == 57600L, "Wrong failure exponent");
        require(product.stackSize == 1 && trigger.stackSize == 1 && (Long)field(wrapped, "stackSize") == 9007199254740993L, "Capture mutated native recipe");
        require(!Identity.recipe(single).equals(Identity.recipe(parallel)), "Modes collide in recipe identity");
        gregtech.api.util.GTRecipe projection = (gregtech.api.util.GTRecipe)allocate(gregtech.api.util.GTRecipe.class);
        projection.mSpecialItems = nativeRecipe;
        projection.mInputs = new ItemStack[]{trigger.copy()}; projection.mInputs[0].stackSize=0;
        projection.mOutputs = new ItemStack[]{product.copy()};
        projection.mFluidInputs = new FluidStack[]{Materials.Hydrogen.getGas(0),Materials.Helium.getGas(0),MaterialsUEVplus.RawStarMatter.getFluid(0)};
        projection.mFluidOutputs = new FluidStack[]{MaterialsUEVplus.RawStarMatter.getFluid(1),MaterialsUEVplus.WhiteDwarfMatter.getFluid(1)};
        List<GtRecipes.Placement> inputSlots=Collections.singletonList(new GtRecipes.Placement(new GtRecipes.Binding(0,false,false,false,true,4,4),trigger,new codechicken.nei.PositionedStack(trigger,4,4)));
        List<GtRecipes.Placement> outputSlots=Collections.singletonList(new GtRecipes.Placement(new GtRecipes.Binding(0,false,false,false,false,24,4),product,new codechicken.nei.PositionedStack(product,24,4)));
        List<RecipeRow> projected=Harmony.capture(projection,inputSlots,outputSlots,row(trigger,product),(tectech.recipe.EyeOfHarmonyRecipeStorage)storage);
        require(projected.get(0).outputs.size()==4 && projected.get(0).elements.size()==2, "Hidden native outputs or original slots lost");
        projection.mOutputs[0]=new ItemStack(Items.gold_ingot);
        try { Harmony.capture(projection,inputSlots,outputSlots,row(trigger,product),(tectech.recipe.EyeOfHarmonyRecipeStorage)storage); throw new AssertionError("Changed projection accepted"); }
        catch(Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"),"Unexpected projection rejection"); }
        set(nativeRecipe, "baseSuccessChance", Double.NaN);
        try { invoke(adapter, null, "rows", new Class<?>[] {nativeType, RecipeRow.class}, nativeRecipe, row(trigger, product)); throw new AssertionError("Invalid process accepted"); }
        catch (Jobs.Fault expected) { require(expected.code.equals("recipe_unsupported"), "Unexpected rejection " + expected); }
        System.out.println("Native Harmony lookup/getters: two modes, whole buffers, long bases, failure output, identity and ownership passed");
    }
    private static Object allocate(Class<?> type) throws Exception { Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return ((sun.misc.Unsafe)f.get(null)).allocateInstance(type); }
    private static void set(Object target, String name, Object value) throws Exception { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
    @SuppressWarnings("unchecked") private static RecipeRow row(ItemStack... stacks) {
        Facts facts = new Facts("en_US");
        for (ItemStack item : stacks) ((Set<String>)field(facts,"items")).add(Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()), Items.feather.getDamage(item), null));
        for (String name : new String[]{"hydrogen","helium","rawstarmatter","whitedwarfmatter","molten.spacetime"})
            ((Set<String>)field(facts,"fluids")).add(Identity.fluid(name, null));
        return new RecipeRow(facts, object("owner","fixture","handler","harmony","key","planet"), "fixture", 0);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
