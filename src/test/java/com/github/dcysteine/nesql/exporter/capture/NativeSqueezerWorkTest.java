package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import forestry.api.recipes.ISqueezerRecipe;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeSqueezerTest.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Uses an unticked native tile for hasWork; native stock/tank helpers for completion. */
final class NativeSqueezerWorkTest {
    private static final String TILE="forestry.factory.tiles.TileSqueezer";
    private static final JsonArray work=new JsonArray(),power=new JsonArray();
    @SuppressWarnings("unchecked")
    static void run()throws Exception{
        Class<?> api=type("forestry.api.core.ForestryAPI");Object previous=field(api,null,"activeMode");
        Object mode=allocate(type("forestry.core.config.GameMode"));
        set(mode,"floatSettings",new HashMap<String,Float>());
        api.getField("activeMode").set(null,mode);
        try{
            ((Map<String,Float>)field(mode,"floatSettings")).put("energy.demand.modifier",1f);
            stillSpace();
            NativeStillWorkTest.run();
            ISqueezerRecipe ordinary=recipe(10,100,new ItemStack(Items.paper,2),0.5f);
            check("ordinary-miss",ordinary,null,null,new ItemStack(Items.iron_ingot,3),0);
            check("ordinary-hit",recipe(10,100,new ItemStack(Items.paper,2),1f),null,null,new ItemStack(Items.iron_ingot,3),0);
            check("tank-short",ordinary,new FluidStack(FluidRegistry.WATER,9950),null,new ItemStack(Items.iron_ingot,3),0);
            check("tank-exact-fit",ordinary,new FluidStack(FluidRegistry.WATER,9900),null,new ItemStack(Items.iron_ingot,3),0);
            check("tank-other-fluid",ordinary,new FluidStack(FluidRegistry.LAVA,10),null,new ItemStack(Items.iron_ingot,3),0);
            FluidStack tagged=new FluidStack(FluidRegistry.WATER,10);tagged.tag=new NBTTagCompound();
            check("tank-null-vs-empty-tags",ordinary,tagged,null,new ItemStack(Items.iron_ingot,3),0);
            check("zero-fluid-output",recipe(10,0,null,0),new FluidStack(FluidRegistry.LAVA,10000),null,new ItemStack(Items.iron_ingot,3),0);
            check("negative-fluid-output",recipe(10,-1,null,0),null,null,new ItemStack(Items.iron_ingot,3),0);
            check("blocked-remnant-even-zero-chance",recipe(10,100,new ItemStack(Items.paper,2),0),null,new ItemStack(Items.coal,64),new ItemStack(Items.iron_ingot,3),0);
            check("no-remnant-needs-no-slot",recipe(10,100,null,0),null,new ItemStack(Items.coal,64),new ItemStack(Items.iron_ingot,3),0);
            check("remnant-one-slot-short",ordinary,null,new ItemStack(Items.paper,63),new ItemStack(Items.iron_ingot,3),0);
            check("remnant-exact-fit",recipe(10,100,new ItemStack(Items.paper,2),1),null,new ItemStack(Items.paper,62),new ItemStack(Items.iron_ingot,3),0);
            check("oversized-empty-remnant",recipe(10,100,new ItemStack(Items.paper,100),1),null,null,new ItemStack(Items.iron_ingot,3),0);
            check("nonstackable-remnant",recipe(10,100,new ItemStack(Items.water_bucket),1),null,new ItemStack(Items.water_bucket),new ItemStack(Items.iron_ingot,3),0);
            ItemStack emptyTag=new ItemStack(Items.paper,2);emptyTag.setTagCompound(new NBTTagCompound());
            check("remnant-null-vs-empty-tags",ordinary,null,emptyTag,new ItemStack(Items.iron_ingot,3),0);
            check("no-stock-clears-current",ordinary,null,null,null,0);
            check("nan-chance-never-hits",recipe(10,100,new ItemStack(Items.paper,2),Float.NaN),null,null,new ItemStack(Items.iron_ingot,3),0);
            check("greater-than-one-chance",recipe(10,100,new ItemStack(Items.paper,2),2),null,null,new ItemStack(Items.iron_ingot,3),0);
            ISqueezerRecipe nullFluid=(ISqueezerRecipe)type("forestry.factory.recipes.SqueezerRecipe").getConstructor(int.class,ItemStack[].class,FluidStack.class,ItemStack.class,float.class)
                .newInstance(10,new ItemStack[]{new ItemStack(Items.iron_ingot)},null,null,0f);
            check("null-output-with-no-stock",nullFluid,null,null,null,0);
            int[] times={10,7,0,-1,10737419,Integer.MAX_VALUE};
            for(int time:times)energy("base-"+time,time,1f,1f,1f,1000);
            energy("fractional-difficulty",7,1.3333334f,1f,1f,1000);
            energy("upgraded",7,1.25f,1.5f,1.7f,1000);
            energy("zero-speed",7,1f,0f,1f,1000);
            energy("negative-speed",7,1f,-2f,1f,1000);
            energy("half-tie",1,1f,2f,1f,1000);
            energy("zero-difficulty",7,0f,1f,1f,1000);
            energy("zero-power",7,1f,1f,0f,1000);
            energy("insufficient-energy",7,1f,1f,1f,199);
            energy("exact-energy",7,1f,1f,1f,200);
            energy("negative-infinite-capacity-order",10,Float.NEGATIVE_INFINITY,10f,1f,Integer.MIN_VALUE);
        }finally{api.getField("activeMode").set(null,previous);}
        Files.write(Paths.get("build/native-tests/forestry-work-observations.json"),CanonicalJson.bytes(object("native","Forestry 4.10.17 / CoFH 3.1.4","work",work,"power",power)));
        System.out.println("Native Forestry work: "+work.size()+" preflight/completion and "+power.size()+" energy/upgrade observations recorded");
    }
    private static void stillSpace()throws Exception{
        Object tile=allocate(type("forestry.factory.tiles.TileStill"));set(tile,"errorHandler",type("forestry.core.errors.ErrorLogic").newInstance());
        Class<?> filtered=type("forestry.core.fluids.tanks.FilteredTank");
        FluidTank input=(FluidTank)filtered.getConstructor(int.class,Collection.class).newInstance(10000,Arrays.asList(FluidRegistry.WATER));
        FluidTank output=(FluidTank)filtered.getConstructor(int.class,Collection.class).newInstance(10000,Arrays.asList(FluidRegistry.LAVA));
        input.setFluid(new FluidStack(FluidRegistry.WATER,100));output.setFluid(new FluidStack(FluidRegistry.LAVA,9995));
        Object recipe=type("forestry.factory.recipes.StillRecipe").getConstructor(int.class,FluidStack.class,FluidStack.class)
            .newInstance(3,new FluidStack(FluidRegistry.WATER,10),new FluidStack(FluidRegistry.LAVA,3));
        set(tile,"resourceTank",input);set(tile,"productTank",output);set(tile,"currentRecipe",recipe);
        boolean ready=(Boolean)invoke(tile.getClass(),tile,"hasWork",new Class<?>[0]);
        int reserved=((FluidStack)field(tile,"bufferedLiquid")).amount;
        boolean completed=(Boolean)invoke(tile.getClass(),tile,"workCycle",new Class<?>[0]);
        if(!ready||!completed||input.getFluidAmount()!=70||output.getFluidAmount()!=10000||reserved!=30||field(tile,"bufferedLiquid")!=null)
            throw new AssertionError("Native still partial-space behavior changed");
        Files.write(Paths.get("build/native-tests/forestry-still-space-observation.json"),CanonicalJson.bytes(object("native","Forestry 4.10.17","cycles",3,
            "unitInput",10,"unitOutput",3,"outputBefore",9995,"hasWork",ready,"reservedInput",reserved,"inputAfter",input.getFluidAmount(),
            "workCycle",completed,"outputAfter",output.getFluidAmount(),"nominalOutput",9,"actualOutput",5,"bufferCleared",true)));
    }
    private static void check(String name,ISqueezerRecipe recipe,FluidStack initialTank,ItemStack initialRemnant,ItemStack input,long seed)throws Exception{
        Object tile=allocate(type(TILE));set(tile,"errorHandler",type("forestry.core.errors.ErrorLogic").newInstance());
        Object inventory=type("forestry.factory.inventory.InventorySqueezer").getConstructor(type(TILE)).newInstance(tile);
        IInventory inv=(IInventory)inventory;inv.setInventorySlotContents(0,copy(input));inv.setInventorySlotContents(9,copy(initialRemnant));
        Object tank=type("forestry.core.fluids.tanks.StandardTank").getConstructor(int.class).newInstance(10000);
        ((FluidTank)tank).setFluid(initialTank==null?null:initialTank.copy());
        set(tile,"inventory",inventory);set(tile,"productTank",tank);set(tile,"currentRecipe",recipe);
        boolean nativeHasWork=(Boolean)invoke(type(TILE),tile,"hasWork",new Class<?>[0]);
        boolean completed=false;Float roll=null;
        if(nativeHasWork){
            completed=(Boolean)invoke(inventory.getClass(),inventory,"removeResources",new Class<?>[]{ItemStack[].class,net.minecraft.entity.player.EntityPlayer.class},recipe.getResources(),null);
            if(completed){
                ((FluidTank)tank).fill(recipe.getFluidOutput(),true);
                if(recipe.getRemnants()!=null){
                    roll=new Random(seed).nextFloat();
                    if(roll<recipe.getRemnantsChance())invoke(inventory.getClass(),inventory,"addRemnant",new Class<?>[]{ItemStack.class,boolean.class},recipe.getRemnants().copy(),true);
                }
            }
        }
        ItemStack[] after=new ItemStack[9];for(int i=0;i<9;i++)after[i]=inv.getStackInSlot(i);
        work.add(object("name",name,"recipe",recipeJson(recipe),"stock",stacks(new ItemStack[]{input}),"tank",fluidJson(initialTank),
            "remnant",stack(initialRemnant),"slotLimit",initialRemnant==null?64:initialRemnant.getMaxStackSize(),"stackable",initialRemnant!=null&&initialRemnant.isStackable(),
            "draw",bits(new Random(seed).nextFloat()),"hasWork",nativeHasWork,"completed",completed,"rolled",roll!=null,
            "remaining",stacks(after),"tankAfter",fluidJson(((FluidTank)tank).getFluid()),"remnantAfter",stack(inv.getStackInSlot(9))));
    }
    @SuppressWarnings("unchecked")
    private static void energy(String name,int time,float difficulty,float speed,float multiplier,int stored)throws Exception{
        Object mode=field(type("forestry.api.core.ForestryAPI"),null,"activeMode");((Map<String,Float>)field(mode,"floatSettings")).put("energy.demand.modifier",difficulty);
        Object tile=allocate(type(TILE));set(tile,"speedMultiplier",speed);set(tile,"powerMultiplier",multiplier);
        set(tile,"worldObj",allocate(type("net.minecraft.client.multiplayer.WorldClient")));
        invoke(tile.getClass(),tile,"setTicksPerWorkCycle",new Class<?>[]{int.class},time);
        invoke(tile.getClass(),tile,"setEnergyPerWorkCycle",new Class<?>[]{int.class},time*200);
        int ticks=(Integer)invoke(tile.getClass(),tile,"getTicksPerWorkCycle",new Class<?>[0]);
        int energy=(Integer)invoke(tile.getClass(),tile,"getEnergyPerWorkCycle",new Class<?>[0]);
        Object manager=type("forestry.energy.EnergyManager").getConstructor(int.class,int.class).newInstance(1100,5000);
        invoke(manager.getClass(),manager,"fromGuiInt",new Class<?>[]{int.class},stored);
        int before=(Integer)invoke(manager.getClass(),manager,"getTotalEnergyStored",new Class<?>[0]);
        boolean consumed=(Boolean)invoke(manager.getClass(),manager,"consumeEnergyToDoWork",new Class<?>[]{int.class,int.class},ticks,energy);
        int after=(Integer)invoke(manager.getClass(),manager,"getTotalEnergyStored",new Class<?>[0]);
        power.add(object("name",name,"time",time,"difficulty",bits(difficulty),"speed",bits(speed),"power",bits(multiplier),"stored",before,
            "ticks",ticks,"energy",energy,"capacity",invoke(manager.getClass(),manager,"getMaxEnergyStored",new Class<?>[0]),"consumed",consumed,"after",after));
    }
    private static ISqueezerRecipe recipe(int time,int amount,ItemStack remnant,float chance)throws Exception{return (ISqueezerRecipe)type("forestry.factory.recipes.SqueezerRecipe").getConstructor(int.class,ItemStack[].class,FluidStack.class,ItemStack.class,float.class).newInstance(time,new ItemStack[]{new ItemStack(Items.iron_ingot,1)},new FluidStack(FluidRegistry.WATER,amount),remnant,chance);}
    static Object allocate(Class<?> type)throws Exception{Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);return ((sun.misc.Unsafe)field.get(null)).allocateInstance(type);}
    static void set(Object owner,String name,Object value)throws Exception{for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass()){try{Field field=c.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);return;}catch(NoSuchFieldException ignored){}}throw new NoSuchFieldException(name);}
    private static ItemStack copy(ItemStack stack){return stack==null?null:stack.copy();}
    private static JsonElement stack(ItemStack value){return stacks(new ItemStack[]{value}).get(0);}
    private static String bits(float value){return String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits(value));}
}
