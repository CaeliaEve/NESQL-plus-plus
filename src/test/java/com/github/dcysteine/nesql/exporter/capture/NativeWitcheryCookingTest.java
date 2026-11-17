package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/** Native fixture: each advertised handler must have an owned registry cursor. */
public final class NativeWitcheryCookingTest {
    public static void run() throws Exception {
        try(WitcheryTestSource source=new WitcheryTestSource()){
        String[] names={"alkalus.main.nei.NEI_Handler_Oven", "alkalus.main.nei.NEI_Handler_Cauldron",
                "alkalus.main.nei.NEI_Handler_Kettle", "witchinggadgets.client.nei.NEIInfernalBlastfurnaceHandler"};
        for(String name:names){
            TemplateRecipeHandler h=(TemplateRecipeHandler)Class.forName(name).newInstance();
            require(WitcheryCookingRecipes.supports(h)||WitcheryCauldronRecipes.supports(h)||WitcheryOvenRecipes.supports(h),
                    "Missing native cooking handler: "+name);
            require(Recipes.adapter(h)!=null,"Missing cooking dispatch: "+name);
        }
        Class<?> nativeRecipe=Class.forName("witchinggadgets.common.util.recipe.InfernalBlastfurnaceRecipe");
        Constructor<?> constructor=nativeRecipe.getConstructor(ItemStack.class,Object.class,int.class,boolean.class);
        Object fixture=constructor.newInstance(new ItemStack(Items.iron_ingot,2),new ItemStack(Blocks.iron_ore,3),1200,false);
        TemplateRecipeHandler h=(TemplateRecipeHandler)Class.forName(names[3]).newInstance();
        RegistryRecipes cursor=source.open(h,Collections.singletonList(fixture));
        RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Blocks.iron_ore),new ItemStack(Items.iron_ingot));
        require(cursor.capture(0,row),"Infernal recipe disappeared");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject()
                .get("amount").getAsString().equals("3"),"Infernal native input count lost");
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("2"),"Infernal output count lost");
        require(row.record.get("duration").isJsonNull(),"Infernal duration must reflect variable native time");
        require(row.record.getAsJsonObject("process").get("baseTicks").getAsInt()==1200,"Infernal base ticks lost");
        require((Boolean)nativeRecipe.getMethod("matches",ItemStack.class).invoke(fixture,new ItemStack(Blocks.iron_ore,3)),
                "Infernal native matcher rejected counted stack");
        NativeContractFixture.writeOwned("witchery-infernal",row,Arrays.asList(new ItemStack(Blocks.iron_ore),new ItemStack(Items.iron_ingot)));
        Object later=constructor.newInstance(new ItemStack(Items.gold_ingot),new ItemStack(Blocks.gold_ore),1000,false);
        RegistryRecipes ordered=source.open(h,Arrays.asList(fixture,later));
        RecipeRow laterRow=NativeCoreFixesTest.row(new ItemStack(Blocks.gold_ore),new ItemStack(Items.gold_ingot));
        require(ordered.capture(1,laterRow),"Later infernal recipe disappeared");
        require(laterRow.record.getAsJsonObject("process").getAsJsonArray("earlier").size()==1,
                "Infernal first-match mask lost");
        List<ItemStack> previousCatalog=codechicken.nei.ItemList.items;
        try {
            codechicken.nei.ItemList.items=new ArrayList<>(Collections.singletonList(new ItemStack(Items.dye,1,5)));
            source.candidates(new ItemStack(Items.dye,1,5));
            Object wildcard=constructor.newInstance(new ItemStack(Items.iron_ingot),new ItemStack(Items.dye,3,32767),1200,false);
            RegistryRecipes wildcardCursor=source.open(h,Collections.singletonList(wildcard));
            RecipeRow wildcardRow=NativeCoreFixesTest.row(new ItemStack(Items.dye,1,0),new ItemStack(Items.dye,1,5),new ItemStack(Items.iron_ingot));
            wildcardCursor.capture(0,wildcardRow);
            com.google.gson.JsonObject choice=wildcardRow.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            String expected=com.github.dcysteine.nesql.exporter.source.Identity.item("minecraft:dye",5,null);
            require(choice.get("id").getAsString().equals(expected)&&choice.getAsJsonObject("rule").get("meta").getAsBoolean(),
                    "Infernal wildcard invented meta zero instead of using native NEI variant");
            NativeContractFixture.writeOwned("witchery-infernal-wildcard",wildcardRow,Arrays.asList(new ItemStack(Items.dye,1,5),new ItemStack(Items.iron_ingot)));
            codechicken.nei.ItemList.items=new ArrayList<>(Collections.singletonList(new ItemStack(Items.dye,1,7)));
            source.candidates(new ItemStack(Items.dye,1,7));
            try{wildcardCursor.finishVerification();throw new AssertionError("Infernal wildcard example drift escaped snapshot");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expectedFault){require(expectedFault.code.equals("recipe_changed"),"Wrong wildcard drift fault");}
        } finally {codechicken.nei.ItemList.items=previousCatalog;}
        oven(source,names[0]);
        kettle(source,names[2]);
        cauldron(source,names[1]);
        System.out.println("Native Witchery cooking: four native callback families and fixture transport passed");
        }
    }
    private static void kettle(WitcheryTestSource source,String name) throws Exception {
        TemplateRecipeHandler h=(TemplateRecipeHandler)Class.forName(name).newInstance();
        Class<?> type=Class.forName("com.emoniph.witchery.crafting.KettleRecipes$KettleRecipe");
        Constructor<?> ctor=type.getDeclaredConstructor(ItemStack.class,int.class,int.class,float.class,int.class,int.class,
                boolean.class,ItemStack[].class);ctor.setAccessible(true);
        Object nativeRecipe=ctor.newInstance(new ItemStack(Items.blaze_powder,2),0,1,0.5f,0,0,true,
                new ItemStack[]{new ItemStack(Items.redstone),new ItemStack(Items.glowstone_dust)});
        ItemStack nativeOutput=(ItemStack)type.getMethod("getOutput",net.minecraft.entity.player.EntityPlayer.class,boolean.class)
                .invoke(nativeRecipe,null,true);
        require(nativeOutput.stackSize==2,"Kettle native output count changed");
        RegistryRecipes cursor=source.open(h,Collections.singletonList(nativeRecipe));
        RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.redstone),new ItemStack(Items.glowstone_dust),
                new ItemStack(Items.glass_bottle),new ItemStack(Items.blaze_powder));
        require(cursor.capture(0,row),"Kettle recipe disappeared");
        require(row.record.getAsJsonObject("process").get("waterMillibuckets").getAsInt()==1000,"Kettle water lost");
        require(row.inputs.get(0).getAsJsonObject().get("kind").getAsString().equals("item"),"Kettle ingredient lost");
        NativeContractFixture.writeOwned("witchery-kettle",row,Arrays.asList(new ItemStack(Items.redstone),new ItemStack(Items.glowstone_dust),new ItemStack(Items.glass_bottle),new ItemStack(Items.blaze_powder)),new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,1000));
    }
    private static void cauldron(WitcheryTestSource source,String name) throws Exception {
        TemplateRecipeHandler h=(TemplateRecipeHandler)Class.forName(name).newInstance();
        Class<?> key=Class.forName("com.emoniph.witchery.brewing.BrewItemKey");
        Class<?> power=Class.forName("com.emoniph.witchery.brewing.AltarPower");
        Class<?> recipe=Class.forName("com.emoniph.witchery.brewing.action.BrewActionRitualRecipe$Recipe");
        Class<?> action=Class.forName("com.emoniph.witchery.brewing.action.BrewActionRitualRecipe");
        Object nativeRecipe=recipe.getConstructor(ItemStack.class,ItemStack[].class).newInstance(
                new ItemStack(Items.ender_pearl,2),new ItemStack[]{new ItemStack(Items.redstone),new ItemStack(Items.glowstone_dust)});
        Object array=java.lang.reflect.Array.newInstance(recipe,1);java.lang.reflect.Array.set(array,0,nativeRecipe);
        Object nativeAction=action.getConstructor(key,power,array.getClass()).newInstance(
                key.getConstructor(net.minecraft.item.Item.class).newInstance(Items.ender_pearl),
                power.getConstructor(int.class).newInstance(17),array);
        require((Boolean)action.getMethod("triggersRitual").invoke(nativeAction),"Cauldron native ritual trigger changed");
        require(((List<?>)action.getMethod("getExpandedRecipes").invoke(nativeAction)).size()==1,
                "Cauldron native expansion changed");
        RegistryRecipes cursor=source.open(h,Collections.singletonList(nativeAction));
        RecipeRow row=NativeCoreFixesTest.row(new ItemStack(Items.redstone),new ItemStack(Items.glowstone_dust),
                new ItemStack(Items.ender_pearl));
        require(cursor.capture(0,row),"Cauldron recipe disappeared");
        require(row.record.getAsJsonObject("process").get("triggerPower").getAsInt()==17,"Cauldron altar gate lost");
        NativeContractFixture.writeOwned("witchery-ritual",row,Arrays.asList(new ItemStack(Items.redstone),new ItemStack(Items.glowstone_dust),new ItemStack(Items.ender_pearl)),new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER,3000));
    }
    private static void oven(WitcheryTestSource source,String name) throws Exception {
        Class<?> witchery=Class.forName("com.emoniph.witchery.Witchery");
        if(MagicApi.field(witchery,null,"Fluids")==null)
            witchery.getField("Fluids").set(null,Class.forName("com.emoniph.witchery.WitcheryFluids").newInstance());
        if(MagicApi.field(witchery,null,"Items")==null){
            Class<?> generalType=Class.forName("com.emoniph.witchery.item.ItemGeneral");
            net.minecraft.item.Item generalItem=(net.minecraft.item.Item)generalType.newInstance();
            generalItem.setUnlocalizedName("witchery:ingredient");
            java.lang.reflect.Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
            Class<?> itemsType=Class.forName("com.emoniph.witchery.WitcheryItems");
            Object items=unsafe.allocateInstance(itemsType);
            java.lang.reflect.Field genericField=itemsType.getDeclaredField("GENERIC");genericField.setAccessible(true);
            genericField.set(items,generalItem);
            witchery.getField("Items").set(null,items);
        }
        if(MagicApi.field(witchery,null,"Blocks")==null){
            java.lang.reflect.Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
            Object blocks=unsafe.allocateInstance(Class.forName("com.emoniph.witchery.WitcheryBlocks"));
            java.lang.reflect.Field sapling=blocks.getClass().getDeclaredField("SAPLING");sapling.setAccessible(true);
            sapling.set(blocks,Blocks.sapling);
            witchery.getField("Blocks").set(null,blocks);
        }
        net.minecraft.item.Item generic=(net.minecraft.item.Item)MagicApi.field(MagicApi.field(witchery,null,"Items"),"GENERIC");
        if(net.minecraft.item.Item.itemRegistry.getNameForObject(generic)==null)
            net.minecraft.item.Item.itemRegistry.addObject(31971,"witchery:ingredient",generic);
        TemplateRecipeHandler h=(TemplateRecipeHandler)Class.forName(name).newInstance();
        Class<?> type=Class.forName("alkalus.main.core.crafting.OvenRecipes$OvenRecipe");
        Constructor<?> ctor=type.getDeclaredConstructor(ItemStack.class,int.class,ItemStack.class,int.class,ItemStack.class,int.class);
        ctor.setAccessible(true);
        Object nativeRecipe=ctor.newInstance(new ItemStack(Blocks.log,1,0),1,new ItemStack(Items.coal,1,1),1,
                new ItemStack(Items.clay_ball),1);
        require((Boolean)type.getMethod("uses",ItemStack.class).invoke(nativeRecipe,new ItemStack(Blocks.log,1,0)),
                "Oven native input match changed");
        RegistryRecipes cursor=source.open(h,Collections.singletonList(nativeRecipe));
        Object jarSub=MagicApi.field(generic,"itemEmptyClayJar");
        Object foulSub=MagicApi.field(generic,"itemFoulFume");
        ItemStack jar=(ItemStack)MagicApi.invoke(jarSub.getClass(),jarSub,"createStack",new Class<?>[]{int.class},1);
        ItemStack foul=(ItemStack)MagicApi.invoke(foulSub.getClass(),foulSub,"createStack",new Class<?>[]{int.class},1);
        List<ItemStack> ovenFacts=Arrays.asList(new ItemStack(Blocks.log),new ItemStack(Blocks.log,1,1),new ItemStack(Items.coal,1,1),jar,foul,
                sub(generic,"itemExhaleOfTheHornedOne"),sub(generic,"itemHintOfRebirth"),sub(generic,"itemBreathOfTheGoddess"),
                sub(generic,"itemWhiffOfMagic"),sub(generic,"itemReekOfMisfortune"),sub(generic,"itemOdourOfPurity"));
        RecipeRow row=NativeCoreFixesTest.row(ovenFacts.toArray(new ItemStack[0]));
        require(cursor.capture(0,row),"Oven recipe disappeared");
        require(row.record.getAsJsonObject("process").getAsJsonArray("definitions").get(0).getAsJsonObject().get("jars").getAsInt()==1,"Oven jar gate lost");
        NativeContractFixture.writeOwned("witchery-oven",row,ovenFacts);
        List<RecipeRow> classified=cursor.captureRows(0,NativeCoreFixesTest.row(ovenFacts.toArray(new ItemStack[0])));
        require(classified.size()==1,"An exact untagged literal acquired unrelated NBT states");
        com.google.gson.JsonObject process=classified.get(0).record.getAsJsonObject("process");
        require(process.get("kind").getAsString().equals("witcheryNativeOven")
                &&process.get("byproduct").getAsString().equals("foul"),"Native untagged specimen lost its byproduct");
        require(row.outputs.get(0).getAsJsonObject().getAsJsonObject("quantity").get("kind").getAsString().equals("potential"),
                "Oven output ignored existing inventory and placement gates");
        nativeOvenByproducts(generic,jar);
        net.minecraftforge.oredict.OreDictionary.registerOre("fixtureOvenLog",new ItemStack(Blocks.log,1,0));
        Constructor<?> oreCtor=type.getDeclaredConstructor(String.class,int.class,ItemStack.class,int.class,ItemStack.class,int.class);
        oreCtor.setAccessible(true);
        Object oreRecipe=oreCtor.newInstance("fixtureOvenLog",1,new ItemStack(Items.coal,1,1),1,
                new ItemStack(Items.clay_ball),1);
        Class<?> registry=Class.forName("alkalus.main.core.crafting.OvenRecipes");
        @SuppressWarnings("unchecked") List<Object> nativeMap=(List<Object>)registry.getMethod("getRecipeMap").invoke(null);
        nativeMap.add(oreRecipe);
        try{require(registry.getMethod("getOvenResult",ItemStack.class,int.class)
                .invoke(null,new ItemStack(Blocks.log,1,0),0)==oreRecipe,"Ore-only oven callback did not bypass jars");}
        finally{nativeMap.remove(oreRecipe);}
        RegistryRecipes oreCursor=source.open(h,Collections.singletonList(oreRecipe));
        RecipeRow oreRow=NativeCoreFixesTest.row(ovenFacts.toArray(new ItemStack[0]));
        List<RecipeRow> oreRows=oreCursor.captureRows(0,oreRow);
        require(oreRows.isEmpty()&&oreCursor.exclusion(0).get("reason").getAsString().contains("actual_tile_requires_non_null_literal_input"),
                "An ore-only helper definition was mistaken for the actual tile path");
        List<ItemStack> previousCatalog=codechicken.nei.ItemList.items;
        try {
            codechicken.nei.ItemList.items=new ArrayList<>(Arrays.asList(new ItemStack(Blocks.log,1,0),new ItemStack(Blocks.log,1,1)));
            source.candidates(new ItemStack(Blocks.log,1,0),new ItemStack(Blocks.log,1,1));
            net.minecraftforge.oredict.OreDictionary.registerOre("fixtureOvenWildcard",new ItemStack(Blocks.log,1,32767));
            Object wildcard=ctor.newInstance(new ItemStack(Blocks.log,1,32767),0,new ItemStack(Items.coal,1,1),1,new ItemStack(Items.clay_ball),1);
            RegistryRecipes expanded=source.open(h,Collections.singletonList(wildcard));
            RecipeRow expandedRow=NativeCoreFixesTest.row(ovenFacts.toArray(new ItemStack[0]));
            List<RecipeRow> rows=expanded.captureRows(0,expandedRow);
            require(rows.size()==4,"Oven literal wildcard lost concrete metadata and absent/conditional jar states");
            Set<String> inputs=new HashSet<>();
            for(RecipeRow branch:rows)inputs.add(branch.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("id").getAsString());
            require(inputs.size()==2,"Oven wildcard collapsed two native metadata variants");
            codechicken.nei.ItemList.items=new ArrayList<>(Collections.singletonList(new ItemStack(Blocks.log,1,0)));
            source.candidates(new ItemStack(Blocks.log,1,0));
            try{expanded.finishVerification();throw new AssertionError("Oven wildcard variant drift escaped snapshot");}
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expectedFault){require(expectedFault.code.equals("recipe_changed"),"Wrong oven drift fault");}
        } finally {codechicken.nei.ItemList.items=previousCatalog;}
    }
    private static ItemStack sub(Object generic,String name){Object value=MagicApi.field(generic,name);
        return (ItemStack)MagicApi.invoke(value.getClass(),value,"createStack",new Class<?>[]{int.class},1);}
    private static void nativeOvenByproducts(Object generic,ItemStack jar) throws Exception {
        Class<?> tileType=Class.forName("com.emoniph.witchery.blocks.BlockWitchesOven$TileEntityWitchesOven");
        net.minecraft.tileentity.TileEntity tile=(net.minecraft.tileentity.TileEntity)tileType.newInstance();
        java.lang.reflect.Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        EmptyOvenWorld world=(EmptyOvenWorld)unsafe.allocateInstance(EmptyOvenWorld.class);
        java.lang.reflect.Field rand=net.minecraft.world.World.class.getDeclaredField("rand");rand.setAccessible(true);
        rand.set(world,new Random(){@Override public double nextDouble(){return 0.0;}});tile.setWorldObj(world);
        Method generate=tileType.getDeclaredMethod("generateByProduct",ItemStack.class);generate.setAccessible(true);
        Method put=tileType.getMethod("setInventorySlotContents",int.class,ItemStack.class),get=tileType.getMethod("getStackInSlot",int.class);
        String[] uids={null,"","missing","forestry.treeOak","forestry.treeSpruce","forestry.treeBirch","other","malformed"};
        String[] expected={"itemFoulFume","itemFoulFume","itemFoulFume","itemExhaleOfTheHornedOne","itemHintOfRebirth","itemBreathOfTheGoddess",null,null};
        for(int i=0;i<uids.length;i++){
            ItemStack input=new ItemStack(Blocks.log);
            if(uids[i]!=null){net.minecraft.nbt.NBTTagCompound root=new net.minecraft.nbt.NBTTagCompound();
                if(uids[i].equals("malformed"))root.setString("Genome","wrong type");
                else if(!uids[i].isEmpty()&&!uids[i].equals("missing")){
                    net.minecraft.nbt.NBTTagCompound first=new net.minecraft.nbt.NBTTagCompound();first.setString("UID0",uids[i]);
                    net.minecraft.nbt.NBTTagList chromosomes=new net.minecraft.nbt.NBTTagList();chromosomes.appendTag(first);
                    net.minecraft.nbt.NBTTagCompound genome=new net.minecraft.nbt.NBTTagCompound();genome.setTag("Chromosomes",chromosomes);root.setTag("Genome",genome);
                }else if(uids[i].equals("missing"))root.setString("unrelated","tag");
                input.setTagCompound(root);
            }
            put.invoke(tile,0,input);put.invoke(tile,3,null);put.invoke(tile,4,jar.copy());generate.invoke(tile,input);
            ItemStack result=(ItemStack)get.invoke(tile,3);
            require(expected[i]==null?result==null:result!=null&&result.isItemEqual(sub(generic,expected[i])),"Native Oven NBT classification differs for "+uids[i]);
            ItemStack leftover=(ItemStack)get.invoke(tile,4);
            require(expected[i]==null?leftover!=null&&leftover.stackSize==1:leftover==null,"Native Oven consumed a jar without placing byproduct");
        }
    }
    public static final class EmptyOvenWorld extends net.minecraft.client.multiplayer.WorldClient {
        private EmptyOvenWorld(){super(null,null,0,null,null);}
        @Override public net.minecraft.block.Block getBlock(int x,int y,int z){return Blocks.air;}
        @Override public int getBlockMetadata(int x,int y,int z){return 0;}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
