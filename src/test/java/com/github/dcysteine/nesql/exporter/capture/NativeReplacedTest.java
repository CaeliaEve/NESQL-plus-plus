package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.JsonObject;
import gregtech.api.objects.ItemData;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import net.minecraft.init.Items;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Actual native matchers and Malisis lookup; owned test registry entries, no live replacement. */
final class NativeReplacedTest {
    @SuppressWarnings("unchecked")
    static void run() throws Exception {
        Class<?> replacement = type("net.malisis.core.util.replacement.ReplacementTool");
        String location = replacement.getProtectionDomain().getCodeSource().getLocation().toString();
        require(location.contains("/native-tests/malisisdoors.jar!"), "Use pinned derived Malisis jar: " + location);
        Object tool = invoke(replacement, null, "instance", new Class<?>[0]);
        Map<Item,Item> originals = (Map<Item,Item>) field(tool, "originalItems");
        Item old = new ItemDoor(Material.iron);
        originals.put(Items.iron_door, old);
        ItemStack ghost = new ItemStack(old,1,0);
        java.util.function.Function<Item,Item> lookup = item -> (Item) invoke(replacement, null, "originalItem", new Class<?>[]{Item.class},item);
        // Own only the matching fixture; the normal constructor needs the running GT proxy.
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,
                cpw.mods.fml.common.Loader.instance(), new HashMap<String, cpw.mods.fml.common.ModContainer>(), "namedMods");
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); access.setAccessible(true);
        GTRecipe recipe = (GTRecipe) ((sun.misc.Unsafe) access.get(null)).allocateInstance(GTRecipe.class);
        recipe.mInputs = new ItemStack[]{ghost}; recipe.mOutputs = new ItemStack[]{new ItemStack(Items.iron_ingot)};
        recipe.mFluidInputs = new FluidStack[0]; recipe.mFluidOutputs = new FluidStack[0];
        require(recipe.isRecipeInputEqual(false,new FluidStack[0],ghost.copy()), "Native obsolete item no longer matches itself");
        require(!recipe.isRecipeInputEqual(false,new FluidStack[0],new ItemStack(Items.iron_door)), "Replacement name was treated as object identity");
        JsonObject proof = Replaced.inspect(recipe, lookup);
        require(proof != null && proof.get("replacement").getAsString().equals("minecraft:iron_door"), "Missing scoped reachability proof");
        require(ghost.getItem()==old && ghost.stackSize==1, "Proof mutated native source");
        Map<ItemStack,ItemData> data = (Map<ItemStack,ItemData>) field(GTOreDictUnificator.class,null,"sItemStack2DataMap");
        Map<String,ItemStack> names = (Map<String,ItemStack>) field(GTOreDictUnificator.class,null,"sName2StackMap");
        ItemStack registered = new ItemStack(Items.diamond);
        ItemData association = new ItemData(OrePrefixes.ingot,Materials.Iron);
        association.mUnificationTarget = ghost.copy(); data.put(registered,association);
        require(recipe.isRecipeInputEqual(false,new FluidStack[0],registered.copy()), "Native reverse unification path was not exercised");
        require(Replaced.inspect(recipe,lookup)==null, "Excluded a recipe reachable through cached association target");
        association.mUnificationTarget=null; names.put(association.toString(),ghost.copy());
        require(Replaced.inspect(recipe,lookup)==null, "Excluded a lazy named-target route");
        names.remove(association.toString()); data.remove(registered);
        require(Replaced.inspect(recipe,lookup)!=null, "Removing the route did not restore scoped proof");
        require(Replaced.inspect(recipe, item -> null)==null, "Guessed an unknown replacement");
        require(Replaced.inspect(recipe,"gt.recipe.macerator/gt.recipe.macerator")==null, "Exclusion escaped recycling scope");
        require(Replaced.inspect(recipe,"gt.recipe.macerator/gt.recipe.category.macerator_recycling")==null,
                "Production proof ignored absent pinned mod versions");
        association.mUnificationTarget=registered.copy(); data.put(ghost.copy(),association);
        require(Replaced.inspect(recipe,lookup)==null, "Ignored source normalization to a registered input");
        data.remove(ghost);
        GTRecipe.RecipeItemInput cached = new GTRecipe.RecipeItemInput(registered.copy(),false);
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(GTRecipe.class,recipe,new GTRecipe.RecipeItemInput[]{cached},"mergedInputCache");
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(GTRecipe.class,recipe,recipe.mInputs,"inputsAtCacheTime");
        require(Replaced.inspect(recipe,lookup)==null, "Ignored an existing registered cached input");
        cached = new GTRecipe.RecipeItemInput(ghost.copy(),false);
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(GTRecipe.class,recipe,new GTRecipe.RecipeItemInput[]{cached},"mergedInputCache");
        require(Replaced.inspect(recipe,lookup)!=null, "Valid obsolete cache not proved");
        recipe.mInputs=recipe.mInputs.clone();
        require(Replaced.inspect(recipe,lookup)==null, "Ignored the native cache's changed input-array binding");
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(GTRecipe.class,recipe,null,"mergedInputCache");
        ghost.setTagInfo("owner",new net.minecraft.nbt.NBTTagString("keep"));
        require(Replaced.inspect(recipe,lookup)!=null && ghost.getTagCompound().getString("owner").equals("keep"),
                "Proof normalized source-owned NBT");
        recipe.mFakeRecipe=true;
        require(Replaced.inspect(recipe,lookup)==null, "Fake recipe inherited normal matching proof");
        recipe.mFakeRecipe=false;
        recipe.mInputs=new ItemStack[]{new ItemStack(Items.iron_door)};
        require(Replaced.inspect(recipe,lookup)==null, "Excluded a registered input");
        originals.remove(Items.iron_door);
        require(Item.itemRegistry.getObject("minecraft:iron_door")==Items.iron_door, "Test altered vanilla registry");
        System.out.println("Replaced inputs: actual GT matcher, Malisis original lookup, direct/lazy reverse targets and ownership passed");
    }
    private static void require(boolean test,String message) { if(!test) throw new AssertionError(message); }
}
