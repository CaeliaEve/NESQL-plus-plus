package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.JsonObject;
import forestry.api.recipes.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.InventoryBasic;
import net.minecraftforge.fluids.*;
import java.lang.reflect.Constructor;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Regressions for fixed-container filling and ghost-grid stock/plan semantics. */
final class NativeForestryRemainingTest {
    @SuppressWarnings("unchecked") static void run() throws Exception {
        Map<String,cpw.mods.fml.common.ModContainer> mods=(Map<String,cpw.mods.fml.common.ModContainer>)field(cpw.mods.fml.common.Loader.instance(),"namedMods");
        cpw.mods.fml.common.ModContainer prior=mods.get("Forestry");
        NativeCoreFixesTest.version("Forestry","4.10.17");
        try { if(System.getProperty("nesql.nativeFamily").endsWith("bottler")) bottler(); else fabricator(); }
        finally { if(prior==null) mods.remove("Forestry"); else mods.put("Forestry",prior); }
    }
    // Detects flattening the native amount check or ignoring fixed-container fluid NBT.
    private static void bottler() throws Exception {
        ItemStack empty=new ItemStack(Items.bucket,7), filled=new ItemStack(Items.water_bucket);
        FluidStack water=new FluidStack(FluidRegistry.WATER,1000);
        Object recipe=invoke(type("forestry.factory.recipes.BottlerRecipe"),null,"getRecipe",new Class<?>[]{FluidStack.class,ItemStack.class},water,empty);
        require(recipe!=null,"Native vanilla container fixture was not registered");
        Class<?> nativeType=recipe.getClass();
        require(!(Boolean)invoke(nativeType,recipe,"matches",new Class<?>[]{ItemStack.class,FluidStack.class},empty,new FluidStack(FluidRegistry.WATER,999)),"Native fixed container accepted insufficient fluid");
        FluidStack tagged=water.copy(); tagged.tag=new net.minecraft.nbt.NBTTagCompound(); tagged.tag.setInteger("fixture",1);
        require(!(Boolean)invoke(nativeType,recipe,"matches",new Class<?>[]{ItemStack.class,FluidStack.class},empty,tagged),"Native fixed matcher ignored fluid NBT");
        TemplateRecipeHandler handler=handler("Bottler"); RegistryRecipes adapter=adapter(handler);
        List<ItemStack> examples=new ArrayList<>();
        for(FluidContainerRegistry.FluidContainerData data:FluidContainerRegistry.getRegisteredFluidContainerData()){examples.add(data.emptyContainer);examples.add(data.filledContainer);}
        RecipeRow row=NativeContractFixture.forestryRow("forestry-bottler-semantic",handler,examples.toArray(new ItemStack[0]));
        for(FluidContainerRegistry.FluidContainerData data:FluidContainerRegistry.getRegisteredFluidContainerData())primeFluid(row,data.fluid);
        row=find(adapter,row,Items.water_bucket);
        require(row!=null,"Registered water bucket was omitted");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1"),"Bottler consumed the display stack instead of one container");
        require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsString().equals("1000"),"Bottler fluid amount was flattened");
        require(handler.arecipes.isEmpty(),"Semantic bottling populated the borrowed NEI GUI cache");
        require(empty.stackSize==7 && water.amount==1000,"Bottler capture mutated borrowed data");
        NativeContractFixture.writeOwned("forestry-bottler-semantic",row,examples,water);
        dynamic(adapter,handler);
        adapter.finishVerification();adapter.close();
        System.out.println("Native Forestry bottler: amount, fluid NBT, unit container and semantic output passed");
    }
    // Detects treating a 2000 mB callback container as one fixed consumed item:
    // native filling replaces the input at 1000 mB and only outputs it at capacity.
    @SuppressWarnings({"unchecked","rawtypes"}) private static void dynamic(RegistryRecipes adapter,TemplateRecipeHandler handler)throws Exception{
        Map names=(Map)field(net.minecraft.item.Item.itemRegistry,"registryObjects"); Map oldNames=new HashMap(names);
        Object numeric=field(net.minecraft.item.Item.itemRegistry,"underlyingIntegerMap");
        Map ids=null; List values=null;
        for(java.lang.reflect.Field member:numeric.getClass().getDeclaredFields()){member.setAccessible(true);Object current=member.get(numeric);if(current instanceof Map)ids=(Map)current;else if(current instanceof List)values=(List)current;}
        Map oldIds=new IdentityHashMap(ids); List oldValues=new ArrayList(values);
        try{
            ItemFluidContainer container=new ItemFluidContainer(0,2000);container.setMaxStackSize(64);
            net.minecraft.item.Item.itemRegistry.addObject(31999,"nesqltest:bottler_dynamic",container);
            ItemStack empty=new ItemStack(container);FluidStack water=new FluidStack(FluidRegistry.WATER,1000);
            Object recipe=invoke(type("forestry.factory.recipes.BottlerRecipe"),null,"getRecipe",new Class<?>[]{FluidStack.class,ItemStack.class},water,empty);
            require(((FluidStack)field(recipe,"input")).amount==1000,"Native bottler did not cap dynamic work amount");
            InventoryBasic inventory=new InventoryBasic("fixture",true,2);inventory.setInventorySlotContents(0,empty.copy());
            TestTank tank=new TestTank(3000);
            Class<?> helper=type("forestry.core.fluids.FluidHelper");
            invoke(helper,null,"fillContainers",new Class<?>[]{IFluidHandler.class,net.minecraft.inventory.IInventory.class,int.class,int.class,Fluid.class},tank,inventory,0,1,FluidRegistry.WATER);
            require(inventory.getStackInSlot(1)==null && container.getFluid(inventory.getStackInSlot(0)).amount==1000 && tank.tank.getFluidAmount()==2000,"Native partial fill consumed the container or drained capacity");
            ItemStack full=(ItemStack)field(recipe,"filled");RecipeRow row=NativeContractFixture.forestryRow("forestry-bottler-dynamic-semantic",handler,empty,full);primeFluid(row,water);
            java.lang.reflect.Method callback=adapter.getClass().getDeclaredMethod("callback",net.minecraft.item.Item.class);callback.setAccessible(true);
            JsonObject policy=(JsonObject)callback.invoke(null,container);
            Class<?> bottleType=type(adapter.getClass().getName()+"$Bottle");Constructor<?> ctor=bottleType.getDeclaredConstructor(Object.class,JsonObject.class,ItemStack.class,FluidStack.class);ctor.setAccessible(true);
            java.lang.reflect.Method capture=adapter.getClass().getDeclaredMethod("bottle",bottleType,RecipeRow.class);capture.setAccessible(true);capture.invoke(adapter,ctor.newInstance(recipe,policy,empty,water),row);
            require(row.record.getAsJsonObject("process").getAsJsonObject("callback").get("capacity").getAsInt()==2000 && row.outputs.get(0).getAsJsonObject().get("amount").isJsonNull(),"Dynamic bottle callback was flattened into fixed full output");
            NativeContractFixture.writeOwned("forestry-bottler-dynamic-semantic",row,Arrays.asList(empty,full),water);
            invoke(helper,null,"fillContainers",new Class<?>[]{IFluidHandler.class,net.minecraft.inventory.IInventory.class,int.class,int.class,Fluid.class},tank,inventory,0,1,FluidRegistry.WATER);
            require(inventory.getStackInSlot(0)==null && container.getFluid(inventory.getStackInSlot(1)).amount==2000,"Native full fill failed to move one completed container");
        }finally{names.clear();names.putAll(oldNames);ids.clear();ids.putAll(oldIds);values.clear();values.addAll(oldValues);}
    }
    private static final class TestTank implements IFluidHandler {
        final FluidTank tank;TestTank(int amount){tank=new FluidTank(new FluidStack(FluidRegistry.WATER,amount),10000);}
        public int fill(net.minecraftforge.common.util.ForgeDirection side,FluidStack resource,boolean apply){return tank.fill(resource,apply);}
        public FluidStack drain(net.minecraftforge.common.util.ForgeDirection side,FluidStack resource,boolean apply){return resource.isFluidEqual(tank.getFluid())?tank.drain(resource.amount,apply):null;}
        public FluidStack drain(net.minecraftforge.common.util.ForgeDirection side,int amount,boolean apply){return tank.drain(amount,apply);}
        public boolean canFill(net.minecraftforge.common.util.ForgeDirection side,Fluid fluid){return true;}
        public boolean canDrain(net.minecraftforge.common.util.ForgeDirection side,Fluid fluid){return true;}
        public FluidTankInfo[] getTankInfo(net.minecraftforge.common.util.ForgeDirection side){return new FluidTankInfo[]{tank.getInfo()};}
    }
    // Detects consuming a plan, consuming ghost display counts, and omitting the smelting registry.
    @SuppressWarnings("unchecked") private static void fabricator() throws Exception {
        Set<IFabricatorRecipe> recipes=(Set<IFabricatorRecipe>)field(type("forestry.factory.recipes.FabricatorRecipeManager"),null,"recipes");
        Set<IFabricatorSmeltingRecipe> smelting=(Set<IFabricatorSmeltingRecipe>)field(type("forestry.factory.recipes.FabricatorSmeltingRecipeManager"),null,"recipes");
        List<IFabricatorRecipe> old=new ArrayList<>(recipes); List<IFabricatorSmeltingRecipe> oldSmelting=new ArrayList<>(smelting);
        try (NativeRegistryScope scope=new NativeRegistryScope()) {
            scope.replace(type("forestry.api.recipes.RecipeManagers"),null,"fabricatorManager",type("forestry.factory.recipes.FabricatorRecipeManager").newInstance());
            recipes.clear(); smelting.clear();
            ItemStack plan=new ItemStack(Items.paper,4), input=new ItemStack(Items.iron_ingot,9), output=new ItemStack(Items.diamond,2);
            FluidStack fluid=new FluidStack(FluidRegistry.WATER,250);
            IFabricatorRecipe recipe=(IFabricatorRecipe)type("forestry.factory.recipes.FabricatorRecipe").getConstructor(ItemStack.class,FluidStack.class,ItemStack.class,Object[].class)
                    .newInstance(plan,fluid,output,new Object[]{"A",'A',input}); recipes.add(recipe);
            smelting.add((IFabricatorSmeltingRecipe)type("forestry.factory.recipes.FabricatorSmeltingRecipe").getConstructor(ItemStack.class,FluidStack.class,int.class)
                    .newInstance(new ItemStack(Items.glass_bottle,5),new FluidStack(FluidRegistry.WATER,100),1000));
            InventoryBasic grid=new InventoryBasic("fixture",true,9); grid.setInventorySlotContents(0,input.copy());
            require(invoke(type("forestry.factory.recipes.FabricatorRecipeManager"),null,"findMatchingRecipe",new Class<?>[]{ItemStack.class,net.minecraft.inventory.IInventory.class},new ItemStack(Items.paper),grid)==recipe,"Native plan matching required the display quantity");
            require(invoke(type("forestry.factory.recipes.FabricatorRecipeManager"),null,"findMatchingRecipe",new Class<?>[]{ItemStack.class,net.minecraft.inventory.IInventory.class},new ItemStack(Items.book),grid)==null,"Native plan predicate accepted wrong item");
            InventoryBasic stock=new InventoryBasic("stock",true,18);stock.setInventorySlotContents(0,new ItemStack(Items.iron_ingot,3));
            boolean removed=(Boolean)invoke(type("forestry.core.utils.InventoryUtil"),null,"removeSets",new Class<?>[]{net.minecraft.inventory.IInventory.class,int.class,ItemStack[].class,net.minecraft.entity.player.EntityPlayer.class,boolean.class,boolean.class,boolean.class,boolean.class},stock,1,new ItemStack[]{new ItemStack(Items.iron_ingot,2)},null,true,true,false,true);
            require(removed && stock.getStackInSlot(0).stackSize==1,"Native Fabricator did not allocate the offered ghost quantity");
            TemplateRecipeHandler handler=handler("Fabricator"); RegistryRecipes adapter=adapter(handler);
            require(adapter.size()==2,"Fabricator omitted native smelting transitions");
            RecipeRow row=NativeContractFixture.forestryRow("forestry-fabricator-semantic",handler,plan,input,output,new ItemStack(Items.glass_bottle)); primeFluid(row,fluid);
            require(adapter.capture(0,row),"Fabrication fixture rejected");
            JsonObject process=row.record.getAsJsonObject("process");
            require(process!=null && process.get("kind").getAsString().equals("forestryFabricator"),"Ghost-grid allocation has no native process contract");
            String program=adapter.program(row.facts);
            require(process.get("operation").getAsString().equals("craft") && process.get("program").getAsString().equals(program),"Fabricator reservoir dependency is missing");
            Facts.Batch batch=(Facts.Batch)field(row.facts,"batch"); int chunks=0;
            for(Facts.Record record:batch.records) if(record.kind.equals("programs")){
                require(record.value.get("program").getAsString().equals(program),"Program chunk belongs to another snapshot"); chunks++;
            }
            require(chunks==2,"Both ordered native registries were not published");
            adapter.program(row.facts);
            int after=0; for(Facts.Record record:batch.records) if(record.kind.equals("programs")) after++;
            require(after==2,"Fabricator shared context was published twice");
            NativeContractFixture.writeOwned("forestry-fabricator-semantic",row,Arrays.asList(plan,input,output,new ItemStack(Items.glass_bottle)),fluid);
            require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("keep"),"Plain native plan was consumed");
            require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("allocated"),"Fabricator used display quantities instead of native stock allocation");
            require(handler.arecipes.isEmpty(),"Semantic fabrication populated the borrowed NEI GUI cache");
            require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("2"),"Fabricator changed the native result quantity");
            adapter.capture(1,NativeCoreFixesTest.row(new ItemStack(Items.glass_bottle)));
            require(plan.stackSize==4 && input.stackSize==9 && fluid.amount==250,"Fabricator capture mutated native recipe");
            ((ItemStack)recipe.getIngredients()[0]).stackSize=10;
            try { adapter.finishVerification(); throw new AssertionError("Fabricator registry drift was accepted"); }
            catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("recipe_changed"),"Wrong drift failure");}
            adapter.close();
            System.out.println("Native Forestry fabricator: plan predicate/reuse, ghost stock process, smelting, semantic output and drift passed");
        } finally { recipes.clear(); recipes.addAll(old); smelting.clear(); smelting.addAll(oldSmelting); }
    }
    private static TemplateRecipeHandler handler(String kind)throws Exception{
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type("forestry.factory.recipes.nei.NEIHandler"+kind).newInstance();
        require(Recipes.adapter(handler)!=null,"Native Forestry factory adapter is not routed in production");return handler;
    }
    private static RegistryRecipes adapter(TemplateRecipeHandler handler)throws Exception{
        Class<?> type;
        try {type=Class.forName("com.github.dcysteine.nesql.exporter.capture.ForestryRemainingRecipes");}
        catch(ClassNotFoundException absent){throw new AssertionError("Native Forestry bottler/fabricator adapter is missing");}
        Constructor<?> constructor=type.getDeclaredConstructor(TemplateRecipeHandler.class); constructor.setAccessible(true);return (RegistryRecipes)constructor.newInstance(handler);
    }
    @SuppressWarnings("unchecked") private static void primeFluid(RecipeRow row,FluidStack fluid){
        ((Set<String>)field(row.facts,"fluids")).add(com.github.dcysteine.nesql.exporter.source.Identity.fluid(fluid.getFluid().getName(),com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(fluid.tag)));
    }
    private static RecipeRow find(RegistryRecipes adapter,RecipeRow row,net.minecraft.item.Item result){
        String expected=com.github.dcysteine.nesql.exporter.source.Identity.item(net.minecraft.item.Item.itemRegistry.getNameForObject(result),0,com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(null));
        for(int i=0;i<adapter.size();i++){RecipeRow candidate=row.branch();if(adapter.capture(i,candidate)&&candidate.outputs.size()>0&&candidate.outputs.get(0).getAsJsonObject().get("id").getAsString().equals(expected))return candidate;}return null;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
