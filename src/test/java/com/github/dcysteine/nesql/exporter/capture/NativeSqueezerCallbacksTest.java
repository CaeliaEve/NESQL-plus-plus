package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import forestry.api.recipes.ISqueezerRecipe;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;
import java.nio.file.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual Forge/IC2/Forestry callbacks, without constructing a world or ticking it. */
final class NativeSqueezerCallbacksTest {
    @SuppressWarnings("unchecked") static void run() throws Exception {
        cpw.mods.fml.common.ModMetadata metadata=new cpw.mods.fml.common.ModMetadata();metadata.modId="IC2";metadata.version="2.2.828-experimental";
        Map<String,cpw.mods.fml.common.ModContainer> mods=new HashMap<>();mods.put("IC2",new cpw.mods.fml.common.DummyModContainer(metadata));
        cpw.mods.fml.common.ModMetadata enderMetadata=new cpw.mods.fml.common.ModMetadata();enderMetadata.modId="EnderStorage";enderMetadata.version="1.7.7";
        mods.put("EnderStorage",new cpw.mods.fml.common.DummyModContainer(enderMetadata));
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class,cpw.mods.fml.common.Loader.instance(),mods,"namedMods");
        Item forge=new ItemFluidContainer(1000);
        Item ic2=(Item)NativeSqueezerWorkTest.allocate(type("ic2.core.item.ItemFluidCell"));
        require(ic2.getClass().getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/ic2.jar!"),"Pinned IC2 test jar required");
        // Unsafe omits Item's registry delegate, but keeps the real IC2 callback methods.
        java.lang.reflect.Field delegate=Item.class.getField("delegate");delegate.setAccessible(true);
        delegate.set(ic2,new cpw.mods.fml.common.registry.RegistryDelegate.Delegate<>(ic2,Item.class));
        Item unknown=new ItemFluidContainer(1000){public FluidStack getFluid(ItemStack stack){throw new AssertionError("Unknown callback executed during classification");}};
        Item unknownDrain=new ItemFluidContainer(1000){public FluidStack drain(ItemStack stack,int amount,boolean apply){throw new AssertionError("Unknown drain executed during classification");}};
        net.minecraft.block.Block enderBlock=new net.minecraft.block.Block(net.minecraft.block.material.Material.rock){};
        net.minecraft.block.Block.blockRegistry.addObject(3900,"fixture:ender_block",enderBlock);
        Item ender=(Item)type("codechicken.enderstorage.common.ItemEnderStorage").getConstructor(net.minecraft.block.Block.class).newInstance(enderBlock);
        require(ender.getClass().getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/enderstorage.jar!"),"Pinned EnderStorage test jar required");
        Item.itemRegistry.addObject(30101,"fixture:forge_container",forge);
        Item.itemRegistry.addObject(30102,"fixture:ic2_container",ic2);
        Item.itemRegistry.addObject(30103,"fixture:unknown_container",unknown);
        Item.itemRegistry.addObject(30104,"fixture:unknown_drain",unknownDrain);
        Item.itemRegistry.addObject(3900,"fixture:ender_block",ender);
        Class<?> manager=type("forestry.factory.recipes.SqueezerRecipeManager");
        Set<ISqueezerRecipe> ordinary=(Set<ISqueezerRecipe>)field(manager,null,"recipes");
        Map<Object,Object> containers=(Map<Object,Object>)field(manager,null,"containerRecipes");
        ordinary.clear();containers.clear();
        ItemStack bucket=new ItemStack(net.minecraft.init.Items.bucket);
        containers.put(bucket.getItem(),type("forestry.factory.recipes.SqueezerContainerRecipe").getConstructor(ItemStack.class,int.class,ItemStack.class,float.class).newInstance(bucket,5,null,0f));
        ISqueezerRecipe recipe=(ISqueezerRecipe)type("forestry.factory.recipes.SqueezerRecipe").getConstructor(int.class,ItemStack[].class,FluidStack.class,ItemStack.class,float.class)
            .newInstance(10,new ItemStack[]{new ItemStack(ic2,2)},new FluidStack(FluidRegistry.WATER,100),null,0f);
        ordinary.add(recipe);
        SqueezerRules rules=new SqueezerRules(ordinary,containers);
        JsonObject program=rules.context();
        require(kind(program,forge).equals("forge"),"Inherited Forge callback is still unmodeled");
        require(kind(program,ic2).equals("ic2"),"IC2 empty-tag effect is still unmodeled");
        require(kind(program,unknown).equals("unsupported"),"Overridden callback acquired an unproved rule");
        require(kind(program,unknownDrain).equals("unsupported"),"Overridden drain acquired an unproved rule");
        require(kind(program,ender).equals("noFluid"),"Native null reader was not identified");
        codechicken.nei.recipe.TemplateRecipeHandler handler=(codechicken.nei.recipe.TemplateRecipeHandler)type("forestry.factory.recipes.nei.NEIHandlerSqueezer").newInstance();
        require(new SqueezerRecipes(handler,rules).size()==rules.entries().size()+2,"Proved callbacks became fake recipes or unknown callbacks disappeared");
        JsonArray cases=new JsonArray();
        ItemStack empty=new ItemStack(ic2,1);empty.setTagCompound(new NBTTagCompound());
        observe(cases,"empty-tag-read-allows-condensation",program,new ItemStack[]{new ItemStack(ic2,1),empty},null,manager);
        observe(cases,"retained-before-callback",program,new ItemStack[]{new ItemStack(ic2,2)},recipe,manager);
        observe(cases,"forge-reader-keeps-null-tag",program,new ItemStack[]{new ItemStack(forge,1)},null,manager);
        ItemStack filled=new ItemStack(forge);((IFluidContainerItem)forge).fill(filled,new FluidStack(FluidRegistry.WATER,600),true);
        observe(cases,"forge-container-without-matching-key",program,new ItemStack[]{filled},null,manager);
        observe(cases,"ender-reader-never-drains",program,new ItemStack[]{new ItemStack(ender)},null,manager);
        observe(cases,"callback-before-fixed-container",program,new ItemStack[]{new ItemStack(ic2),new ItemStack(net.minecraft.init.Items.water_bucket)},null,manager);
        observe(cases,"fixed-container-before-callback",program,new ItemStack[]{new ItemStack(net.minecraft.init.Items.water_bucket),new ItemStack(ic2)},null,manager);
        metadata.version="unknown";
        require(kind(new SqueezerRules(ordinary,containers).context(),ic2).equals("unsupported"),"Unpinned IC2 version acquired a callback proof");
        metadata.version="2.2.828-experimental";
        containers.put(new ItemStack(forge),type("forestry.factory.recipes.SqueezerContainerRecipe").getConstructor(ItemStack.class,int.class,ItemStack.class,float.class).newInstance(new ItemStack(forge),5,null,0f));
        require(kind(new SqueezerRules(ordinary,containers).context(),forge).equals("unsupported"),"Matching empty key was incorrectly proved irrelevant");
        try{rules.checkUnchanged();throw new AssertionError("Container-key change did not invalidate the proof");}
        catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_changed"),"Wrong proof drift failure");}
        Object rule=containers.values().iterator().next();
        for(Object key:new Object[]{forge,"callbackProofOre"}){
            containers.clear();net.minecraftforge.oredict.OreDictionary.registerOre("callbackProofOre",new ItemStack(forge));containers.put(key,rule);
            require(kind(new SqueezerRules(ordinary,containers).context(),forge).equals("unsupported"),"Item/ore key bypassed disjoint proof");
        }
        Files.write(Paths.get("build/native-tests/forestry-callback-observations.json"),CanonicalJson.bytes(cases));
        System.out.println("Native Forestry callbacks: inherited Forge, IC2 read mutation, retained order, conflicting keys and rejected override passed");
    }
    private static void observe(JsonArray cases,String name,JsonObject program,ItemStack[] input,ISqueezerRecipe retained,Class<?> manager){
        JsonArray before=NativeSqueezerTest.stacks(input);
        boolean keep=retained!=null&&(Integer)invoke(type("forestry.core.utils.ItemStackUtil"),null,"containsSets",new Class<?>[]{ItemStack[].class,ItemStack[].class,boolean.class,boolean.class},retained.getResources(),input,true,false)>0;
        ISqueezerRecipe result=keep?retained:(ISqueezerRecipe)invoke(manager,null,"findMatchingRecipe",new Class<?>[]{ItemStack[].class},(Object)input);
        if(name.equals("empty-tag-read-allows-condensation"))require(result!=null&&input[0].getTagCompound()!=null,"Native IC2 read mutation did not change recipe selection");
        if(name.equals("retained-before-callback"))require(input[0].getTagCompound()==null,"Retained recipe ran a fluid callback");
        if(name.equals("callback-before-fixed-container"))require(result!=null&&input[0].getTagCompound()!=null,"Earlier callback mutation was lost at a later fixed-container match");
        if(name.equals("fixed-container-before-callback"))require(result!=null&&input[1].getTagCompound()==null,"Fixed-container match did not short circuit later callbacks");
        cases.add(object("name",name,"program",program,"before",before,"after",NativeSqueezerTest.stacks(input),"retained",NativeSqueezerTest.recipeJson(retained),"selected",NativeSqueezerTest.recipeJson(result)));
    }
    private static String kind(JsonObject program,Item item){String registry=Item.itemRegistry.getNameForObject(item);for(JsonElement value:program.getAsJsonArray("dynamic")){JsonObject callback=value.getAsJsonObject();if(callback.get("registry").getAsString().equals(registry))return callback.get("kind").getAsString();}throw new AssertionError("Missing callback "+registry);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
