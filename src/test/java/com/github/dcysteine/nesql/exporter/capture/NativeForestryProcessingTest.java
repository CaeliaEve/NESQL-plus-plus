package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.*;
import forestry.api.recipes.*;
import forestry.api.fuels.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.*;
import net.minecraftforge.fluids.*;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Actual Forestry callbacks, followed by immutable export/priority regressions. */
public final class NativeForestryProcessingTest {
    private static final String PREFIX="forestry.factory.recipes.";
    @SuppressWarnings("unchecked") public static void run()throws Exception{
        Map<String,cpw.mods.fml.common.ModContainer> mods=(Map<String,cpw.mods.fml.common.ModContainer>)field(cpw.mods.fml.common.Loader.instance(),"namedMods");
        cpw.mods.fml.common.ModContainer previous=mods.get("Forestry");NativeCoreFixesTest.version("Forestry","4.10.17");
        HashMap<ItemStack,FermenterFuel> priorFermenter=FuelManager.fermenterFuel;HashMap<ItemStack,MoistenerFuel> priorMoistener=FuelManager.moistenerResource;
        FuelManager.fermenterFuel=(HashMap<ItemStack,FermenterFuel>)type("forestry.core.utils.datastructures.ItemStackMap").newInstance();FuelManager.moistenerResource=(HashMap<ItemStack,MoistenerFuel>)type("forestry.core.utils.datastructures.ItemStackMap").newInstance();
        try(NativeRegistryScope scope=new NativeRegistryScope()){
            for(String machine:new String[]{"Fermenter","Moistener","Carpenter"})scope.replace(RecipeManagers.class,null,Character.toLowerCase(machine.charAt(0))+machine.substring(1)+"Manager",type(PREFIX+machine+"RecipeManager").newInstance());
            fermenter();moistener();carpenter();
        }finally{FuelManager.fermenterFuel=priorFermenter;FuelManager.moistenerResource=priorMoistener;if(previous==null)mods.remove("Forestry");else mods.put("Forestry",previous);}
        System.out.println("Native Forestry processing: fermentation steps, moistener timers, carpenter stock, priority, owned rows and drift passed");
    }
    @SuppressWarnings("unchecked") private static void fermenter()throws Exception{
        Set<IFermenterRecipe> recipes=(Set<IFermenterRecipe>)field(type(PREFIX+"FermenterRecipeManager"),null,"recipes");List<IFermenterRecipe> old=new ArrayList<>(recipes);
        Map<ItemStack,FermenterFuel> fuels=FuelManager.fermenterFuel;Map<ItemStack,FermenterFuel> oldFuels=new HashMap<>(fuels);
        try{
            recipes.clear();fuels.clear();ItemStack input=new ItemStack(Items.apple,9);FluidStack water=new FluidStack(FluidRegistry.WATER,1000);
            IFermenterRecipe recipe=(IFermenterRecipe)type(PREFIX+"FermenterRecipe").getConstructor(ItemStack.class,int.class,float.class,Fluid.class,FluidStack.class).newInstance(input,5,0.5f,FluidRegistry.LAVA,water);recipes.add(recipe);
            ItemStack fuel=new ItemStack(Items.coal);fuels.put(fuel,new FermenterFuel(fuel,3,10));
            require(fuels.get(new ItemStack(Items.coal,9))!=null&&fuels.get(new ItemStack(Items.coal,1,1))==null,"Native fuel map matcher changed count/meta semantics");ItemStack taggedFuel=fuel.copy();taggedFuel.setTagInfo("fixture",new net.minecraft.nbt.NBTTagInt(1));require(fuels.get(taggedFuel)==null,"Native fuel map ignored exact NBT");
            require(invoke(type(PREFIX+"FermenterRecipeManager"),null,"findMatchingRecipe",new Class<?>[]{ItemStack.class,FluidStack.class},new ItemStack(Items.apple),new FluidStack(FluidRegistry.WATER,1))==recipe,"Fermenter incorrectly requires fluid display amount");
            FluidStack tagged=water.copy();tagged.tag=new net.minecraft.nbt.NBTTagCompound();tagged.tag.setInteger("fixture",1);
            require(invoke(type(PREFIX+"FermenterRecipeManager"),null,"findMatchingRecipe",new Class<?>[]{ItemStack.class,FluidStack.class},input,tagged)==null,"Fermenter fluid NBT matcher changed");
            Object tile=allocate(type("forestry.factory.tiles.TileFermenter"));FluidTank source=tank(FluidRegistry.WATER,100),product=tank(FluidRegistry.LAVA,0);
            set(tile,"resourceTank",source);set(tile,"productTank",product);set(tile,"currentRecipe",recipe);set(tile,"currentResourceModifier",1f);set(tile,"fermentationTime",5);set(tile,"fuelCurrentFerment",3);set(tile,"fuelBurnTime",10);
            require(!(Boolean)invoke(tile.getClass(),tile,"workCycle",new Class<?>[0])&&product.getFluidAmount()==2&&source.getFluidAmount()==97,"Fermenter failed first native rounded step");
            require((Boolean)invoke(tile.getClass(),tile,"workCycle",new Class<?>[0])&&product.getFluidAmount()==3&&source.getFluidAmount()==94&&field(tile,"currentRecipe")==null,"Fermenter tail drain/step rounding flattened");
            require(((Integer)field(tile,"fuelBurnTime"))==8,"Fermenter fuel persistence was lost");
            TemplateRecipeHandler handler=handler("Fermenter");RegistryRecipes adapter=adapter(handler);RecipeRow row=NativeContractFixture.forestryRow("forestry-fermenter-semantic",handler,input,fuel);prime(row,water);prime(row,new FluidStack(FluidRegistry.LAVA,1));
            require(adapter.capture(0,row),"Fermenter fixture rejected");require(row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull(),"Fermentation advertised guaranteed bulk yield");
            require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),"Fermenter consumed display item quantity");
            checkProgram(adapter,row,"fermenter");require(handler.arecipes.isEmpty(),"Semantic fermentation populated the native GUI cache");NativeContractFixture.writeOwned("forestry-fermenter-semantic",row,Arrays.asList(input,fuel),water,new FluidStack(FluidRegistry.LAVA,1));
            String before=row.record.toString();input.stackSize=10;require(before.equals(row.record.toString()),"Fermenter row retained mutable native stack");drift(adapter);
        }finally{recipes.clear();recipes.addAll(old);fuels.clear();fuels.putAll(oldFuels);}
    }
    @SuppressWarnings("unchecked") private static void moistener()throws Exception{
        Set<IMoistenerRecipe> recipes=(Set<IMoistenerRecipe>)field(type(PREFIX+"MoistenerRecipeManager"),null,"recipes");List<IMoistenerRecipe> old=new ArrayList<>(recipes);
        Map<ItemStack,MoistenerFuel> fuels=FuelManager.moistenerResource;Map<ItemStack,MoistenerFuel> oldFuels=new HashMap<>(fuels);
        try{
            recipes.clear();fuels.clear();ItemStack input=new ItemStack(Items.wheat,8),output=new ItemStack(Items.paper,2),fuel=new ItemStack(Items.coal);
            IMoistenerRecipe recipe=(IMoistenerRecipe)type(PREFIX+"MoistenerRecipe").getConstructor(ItemStack.class,ItemStack.class,int.class).newInstance(input,output,5);recipes.add(recipe);fuels.put(fuel,new MoistenerFuel(fuel,new ItemStack(Items.gunpowder),0,12));
            ItemStack taggedInput=new ItemStack(Items.wheat);taggedInput.setTagInfo("fixture",new net.minecraft.nbt.NBTTagInt(2));require(invoke(type(PREFIX+"MoistenerRecipeManager"),null,"findMatchingRecipe",new Class<?>[]{ItemStack.class},taggedInput)==recipe,"Native untagged Moistener template unexpectedly required empty offered NBT");input.setTagInfo("fixture",new net.minecraft.nbt.NBTTagInt(1));require(invoke(type(PREFIX+"MoistenerRecipeManager"),null,"findMatchingRecipe",new Class<?>[]{ItemStack.class},taggedInput)==null,"Native tagged Moistener template ignored NBT");input.setTagCompound(null);
            Object tile=allocate(type("forestry.factory.tiles.TileMoistener"));NativeWorld world=(NativeWorld)allocate(NativeWorld.class);world.light=0;set(tile,"worldObj",world);set(tile,"tickCount",1);set(tile,"errorHandler",type("forestry.core.errors.ErrorLogic").newInstance());
            IInventory inventory=(IInventory)type("forestry.factory.inventory.InventoryMoistener").getConstructor(tile.getClass()).newInstance(tile);inventory.setInventorySlotContents(11,input.copy());set(tile,"inventory",inventory);
            FluidTank water=tank(FluidRegistry.WATER,100);set(tile,"resourceTank",water);set(tile,"burnTime",12);
            invoke(tile.getClass(),tile,"checkRecipe",new Class<?>[0]);require(((Integer)field(tile,"productionTime"))==5,"Moistener native timer absent");
            invoke(tile.getClass(),tile,"updateServerSide",new Class<?>[0]);require(water.getFluidAmount()==99&&((Integer)field(tile,"productionTime"))==1&&inventory.getStackInSlot(11).stackSize==8,"Moistener dark speed/drain changed");
            invoke(tile.getClass(),tile,"updateServerSide",new Class<?>[0]);require(water.getFluidAmount()==98&&inventory.getStackInSlot(11).stackSize==7&&inventory.getStackInSlot(10).stackSize==2,"Moistener completion did not consume one input");
            world.light=12;invoke(tile.getClass(),tile,"updateServerSide",new Class<?>[0]);require(water.getFluidAmount()==98,"Moistener operated in excess light");
            world.light=0;set(tile,"burnTime",12);inventory.setInventorySlotContents(10,new ItemStack(Items.paper,64));invoke(tile.getClass(),tile,"updateServerSide",new Class<?>[0]);invoke(tile.getClass(),tile,"updateServerSide",new Class<?>[0]);require(field(tile,"pendingProduct")!=null&&inventory.getStackInSlot(11).stackSize==6&&water.getFluidAmount()==96,"Moistener blocked result was not retained after resource consumption");invoke(tile.getClass(),tile,"updateServerSide",new Class<?>[0]);require(water.getFluidAmount()==96&&field(tile,"pendingProduct")!=null,"Pending result advanced timers or consumed water");inventory.setInventorySlotContents(10,null);invoke(tile.getClass(),tile,"updateServerSide",new Class<?>[0]);require(field(tile,"pendingProduct")==null&&inventory.getStackInSlot(10).stackSize==2,"Pending result did not resume when output space returned");
            inventory.setInventorySlotContents(9,fuel.copy());require(!(Boolean)invoke(tile.getClass(),tile,"rotateWorkingSlot",new Class<?>[0])&&inventory.getStackInSlot(9)==null&&inventory.getStackInSlot(6).getItem()==Items.gunpowder,"Native expired Moistener fuel did not mature independently into reservoir");
            TemplateRecipeHandler handler=handler("Moistener");RegistryRecipes adapter=adapter(handler);RecipeRow row=NativeContractFixture.forestryRow("forestry-moistener-semantic",handler,input,output,fuel,new ItemStack(Items.gunpowder));prime(row,new FluidStack(FluidRegistry.WATER,1));require(adapter.capture(0,row),"Moistener fixture rejected");
            require(row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull(),"Moistener yielded without timer/fuel/space conditions");checkProgram(adapter,row,"moistener");require(handler.arecipes.isEmpty(),"Semantic moistening populated the native GUI cache");NativeContractFixture.writeOwned("forestry-moistener-semantic",row,Arrays.asList(input,output,fuel,new ItemStack(Items.gunpowder)),new FluidStack(FluidRegistry.WATER,1));
            fuels.put(new ItemStack(Items.stick),new MoistenerFuel(new ItemStack(Items.stick),new ItemStack(Items.paper),1,8));drift(adapter);
        }finally{recipes.clear();recipes.addAll(old);fuels.clear();fuels.putAll(oldFuels);}
    }
    @SuppressWarnings("unchecked") private static void carpenter()throws Exception{
        Set<ICarpenterRecipe> recipes=(Set<ICarpenterRecipe>)field(type(PREFIX+"CarpenterRecipeManager"),null,"recipes");List<ICarpenterRecipe> old=new ArrayList<>(recipes);
        try{
            recipes.clear();Object manager=type(PREFIX+"CarpenterRecipeManager").newInstance();ItemStack box=new ItemStack(Items.paper,7),input=new ItemStack(Items.iron_ingot,9),output=new ItemStack(Items.diamond,2);FluidStack fluid=new FluidStack(FluidRegistry.WATER,250);
            invoke(manager.getClass(),manager,"addRecipe",new Class<?>[]{int.class,FluidStack.class,ItemStack.class,ItemStack.class,Object[].class},3,fluid,box,output,new Object[]{"A",'A',input});
            ICarpenterRecipe first=recipes.iterator().next();invoke(manager.getClass(),manager,"addRecipe",new Class<?>[]{int.class,FluidStack.class,ItemStack.class,ItemStack.class,Object[].class},4,fluid,box,new ItemStack(Items.emerald),new Object[]{"A",'A',input});
            InventoryBasic grid=new InventoryBasic("ghost",true,9);grid.setInventorySlotContents(0,new ItemStack(Items.iron_ingot,2));Object nativeFirst=invoke(manager.getClass(),null,"findMatchingRecipe",new Class<?>[]{FluidStack.class,ItemStack.class,IInventory.class},fluid,new ItemStack(Items.paper),grid);
            require(nativeFirst==recipes.iterator().next(),"Carpenter native priority changed");
            require(invoke(manager.getClass(),null,"findMatchingRecipe",new Class<?>[]{FluidStack.class,ItemStack.class,IInventory.class},new FluidStack(FluidRegistry.WATER,249),box,grid)==null,"Carpenter fluid amount flattened");
            FluidStack taggedFluid=fluid.copy();taggedFluid.tag=new net.minecraft.nbt.NBTTagCompound();taggedFluid.tag.setInteger("fixture",1);require(invoke(manager.getClass(),null,"findMatchingRecipe",new Class<?>[]{FluidStack.class,ItemStack.class,IInventory.class},taggedFluid,box,grid)==null,"Carpenter fluid matcher ignored NBT");
            InventoryBasic stock=new InventoryBasic("stock",true,18);stock.setInventorySlotContents(0,new ItemStack(Items.iron_ingot,3));
            require((Boolean)invoke(type("forestry.core.utils.InventoryUtil"),null,"removeSets",new Class<?>[]{IInventory.class,int.class,ItemStack[].class,net.minecraft.entity.player.EntityPlayer.class,boolean.class,boolean.class,boolean.class,boolean.class},stock,1,new ItemStack[]{grid.getStackInSlot(0)},null,true,true,false,true)&&stock.getStackInSlot(0).stackSize==1,"Carpenter stock failed offered ghost count");
            Object tile=allocate(type("forestry.factory.tiles.TileCarpenter"));set(tile,"worldObj",allocate(NativeWorld.class));set(tile,"accessHandler",allocate(type("forestry.core.access.AccessHandler")));set(tile,"currentRecipe",first);
            InventoryBasic nativeSlots=new InventoryBasic("nativeStock",true,30);nativeSlots.setInventorySlotContents(9,new ItemStack(Items.paper,3));nativeSlots.setInventorySlotContents(12,new ItemStack(Items.iron_ingot,3));
            Object mainInventory=type("forestry.core.inventory.InventoryAdapter").getConstructor(IInventory.class).newInstance(nativeSlots);set(tile,"inventory",mainInventory);
            Object ghost=allocate(type("forestry.core.inventory.InventoryAdapterTile"));set(ghost,"inventory",grid);set(tile,"craftingInventory",ghost);FluidTank tank=tank(FluidRegistry.WATER,1000);set(tile,"resourceTank",tank);
            require((Boolean)invoke(tile.getClass(),tile,"workCycle",new Class<?>[0])&&tank.getFluidAmount()==750&&nativeSlots.getStackInSlot(9).stackSize==2&&nativeSlots.getStackInSlot(12).stackSize==1&&nativeSlots.getStackInSlot(10).stackSize==2,"Actual Carpenter work cycle did not consume one box and offered ghost quantity");
            nativeSlots.setInventorySlotContents(12,null);require(!(Boolean)invoke(tile.getClass(),tile,"workCycle",new Class<?>[0])&&tank.getFluidAmount()==500&&nativeSlots.getStackInSlot(9).stackSize==1,"Carpenter failed allocation did not preserve native prior liquid/box removal");
            InventoryBasic containers=new InventoryBasic("containers",true,18);containers.setInventorySlotContents(0,new ItemStack(Items.milk_bucket,3));
            require((Boolean)invoke(type("forestry.core.utils.InventoryUtil"),null,"removeSets",new Class<?>[]{IInventory.class,int.class,ItemStack[].class,net.minecraft.entity.player.EntityPlayer.class,boolean.class,boolean.class,boolean.class,boolean.class},containers,1,new ItemStack[]{new ItemStack(Items.milk_bucket,3)},null,true,true,false,true)&&containers.getStackInSlot(0).getItem()==Items.bucket&&containers.getStackInSlot(0).stackSize==1,"Carpenter container callback was scaled per unit instead of removed chunk");
            TemplateRecipeHandler handler=handler("Carpenter");RegistryRecipes adapter=adapter(handler);RecipeRow row=NativeContractFixture.forestryRow("forestry-carpenter-semantic",handler,box,input,output,new ItemStack(Items.emerald));prime(row,fluid);require(adapter.capture(0,row),"Carpenter fixture rejected");checkProgram(adapter,row,"carpenter");
            require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),"Carpenter consumes box display count");
            require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("allocated"),"Carpenter omitted ghost stock allocation");require(handler.arecipes.isEmpty(),"Semantic carpentry populated the native GUI cache");NativeContractFixture.writeOwned("forestry-carpenter-semantic",row,Arrays.asList(box,input,output,new ItemStack(Items.emerald)),fluid);
            require(box.stackSize==7&&input.stackSize==9,"Carpenter cached display mutated registry");((ItemStack)first.getCraftingGridRecipe().getIngredients()[0]).stackSize=12;drift(adapter);
        }finally{recipes.clear();recipes.addAll(old);}
    }
    private static void checkProgram(RegistryRecipes adapter,RecipeRow row,String machine){JsonObject process=row.record.getAsJsonObject("process");require(process!=null&&process.get("kind").getAsString().equals("forestryProcessing")&&process.get("machine").getAsString().equals(machine),"Native process contract absent");String id=adapter.program(row.facts);require(id!=null&&process.get("program").getAsString().equals(id),"Native ordered registry context absent");}
    private static void drift(RegistryRecipes adapter){try{adapter.finishVerification();throw new AssertionError("Native processing drift accepted");}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_changed")||expected.code.equals("environment_changed"),"Wrong drift code");}finally{adapter.close();}}
    private static TemplateRecipeHandler handler(String kind)throws Exception{return (TemplateRecipeHandler)type(PREFIX+"nei.NEIHandler"+kind).newInstance();}
    private static RegistryRecipes adapter(TemplateRecipeHandler handler)throws Exception{Class<?> cls;try{cls=Class.forName("com.github.dcysteine.nesql.exporter.capture.ForestryProcessingRecipes");}catch(ClassNotFoundException absent){throw new AssertionError("Native Forestry processing adapter is missing");}Constructor<?> ctor=cls.getDeclaredConstructor(TemplateRecipeHandler.class);ctor.setAccessible(true);return (RegistryRecipes)ctor.newInstance(handler);}
    private static FluidTank tank(Fluid fluid,int amount)throws Exception{FluidTank tank=(FluidTank)type("forestry.core.fluids.tanks.FilteredTank").getConstructor(int.class,Collection.class).newInstance(10000,Arrays.asList(fluid));if(amount>0)tank.setFluid(new FluidStack(fluid,amount));return tank;}
    @SuppressWarnings("unchecked") private static void prime(RecipeRow row,FluidStack fluid){((Set<String>)field(row.facts,"fluids")).add(com.github.dcysteine.nesql.exporter.source.Identity.fluid(fluid.getFluid().getName(),com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(fluid.tag)));}
    private static Object allocate(Class<?> type)throws Exception{Field member=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");member.setAccessible(true);return ((sun.misc.Unsafe)member.get(null)).allocateInstance(type);}
    private static void set(Object owner,String name,Object value)throws Exception{for(Class<?> cls=owner.getClass();cls!=null;cls=cls.getSuperclass())try{Field member=cls.getDeclaredField(name);member.setAccessible(true);member.set(owner,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    public static final class NativeWorld extends net.minecraft.client.multiplayer.WorldClient{int light;private NativeWorld(){super(null,null,0,null,null);}@Override public int getBlockLightValue(int x,int y,int z){return light;}@Override public void markBlockForUpdate(int x,int y,int z){}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
