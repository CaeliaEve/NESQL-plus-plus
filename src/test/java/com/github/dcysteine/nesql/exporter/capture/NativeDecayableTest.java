package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import java.lang.reflect.*;
import java.util.*;

/** Native decay chest batches, persistent tags, stack limit and owned exporter snapshots. */
public final class NativeDecayableTest {
    private static final String DUST="gtPlusPlus.core.item.materials.DustDecayable";
    private static final String RECIPE="gtPlusPlus.core.handler.Recipes.DecayableRecipe";
    public static void main(String[] args)throws Exception{NativeGendustryFluidsTest.main(new String[]{"decayable"});}
    static void run()throws Exception{
        java.nio.file.Path temporary=java.nio.file.Files.createTempDirectory("decayable-native-game-");
        java.io.File oldLaunch=net.minecraft.launchwrapper.Launch.minecraftHome;
        java.io.File oldFml=(java.io.File)MagicApi.field(cpw.mods.fml.relauncher.FMLInjectionData.class,null,"minecraftHome");
        net.minecraft.launchwrapper.Launch.minecraftHome=temporary.toFile();
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLInjectionData.class,null,temporary.toFile(),"minecraftHome");
        try{test();}finally{
            net.minecraft.launchwrapper.Launch.minecraftHome=oldLaunch;
            cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLInjectionData.class,null,oldFml,"minecraftHome");
            try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(temporary)){
                for(java.nio.file.Path path:(Iterable<java.nio.file.Path>)paths.sorted(Comparator.reverseOrder())::iterator)java.nio.file.Files.delete(path);
            }
        }
    }
    private static void test()throws Exception{
        NativeCoreFixesTest.version("gregtech_nh","5.09.51.482");
        Item dust=NativeCoreFixesTest.item(DUST,"decay_fixture");dust.setUnlocalizedName("decay_fixture");
        ItemStack template=new ItemStack(Items.diamond,7,2);template.setTagInfo("typed",new net.minecraft.nbt.NBTTagShort((short)9));
        set(dust,"turnsIntoItem",template);set(dust,"maxTicks",18);
        World world=(World)allocate(Class.forName("net.minecraft.world.WorldServer"));
        Object chest=Class.forName("gtPlusPlus.core.tileentities.general.TileEntityDecayablesChest").newInstance();
        IInventory inventory=(IInventory)chest.getClass().getMethod("getInventory").invoke(chest);
        Method batch=chest.getClass().getMethod("tryUpdateDecayable",Class.forName(DUST),ItemStack.class,World.class);
        ItemStack normal=new ItemStack(dust,16);inventory.setInventorySlotContents(0,normal);
        require(normal.stackSize==1&&inventory.getInventoryStackLimit()==1,"Native decay chest must clamp ordinary insertion to one");
        for(int maximum:new int[]{1,18,19,20,38,39}){
            set(dust,"maxTicks",maximum);template.stackSize=7;
            load(inventory,new ItemStack(dust,16,5));
            int batches=0;
            while(inventory.getStackInSlot(0).getItem()==dust){require(++batches<=4,"Native fresh decay did not finish");batch.invoke(chest,dust,inventory.getStackInSlot(0),world);}
            require(batches==(maximum+21)/20,"Native fresh batch count disagrees at "+maximum);
            require(inventory.getStackInSlot(0).stackSize==1&&template.stackSize==1
                    &&inventory.getStackInSlot(0).getTagCompound().getShort("typed")==9,"Native loaded stack/result-template behavior changed");
        }
        set(dust,"maxTicks",Integer.MAX_VALUE);template.stackSize=7;
        for(long tick:new long[]{Integer.MAX_VALUE-1L,Long.MIN_VALUE,Long.MAX_VALUE}){
            ItemStack active=tagged(dust,tick,true);load(inventory,active);
            batch.invoke(chest,dust,inventory.getStackInSlot(0),world);
            require(inventory.getStackInSlot(0).getItem()==Items.diamond,"Native long-overflow/threshold branch was lost for "+tick);
        }
        ItemStack partial=new ItemStack(dust);partial.setTagInfo("unrelated",new net.minecraft.nbt.NBTTagInt(3));load(inventory,partial);
        batch.invoke(chest,dust,inventory.getStackInSlot(0),world);
        require(inventory.getStackInSlot(0).getItem()==Items.diamond,"Existing NBT without TickableItem must become inactive");
        ItemStack wrongType=new ItemStack(dust);wrongType.setTagInfo("TickableItem",new net.minecraft.nbt.NBTTagString("not a compound"));load(inventory,wrongType);
        try{batch.invoke(chest,dust,inventory.getStackInSlot(0),world);throw new AssertionError("Wrong-type TickableItem unexpectedly became readable");}
        catch(InvocationTargetException expected){require(expected.getCause() instanceof net.minecraft.util.ReportedException,"Wrong malformed-NBT exception");}
        require(inventory.getStackInSlot(0).getItem()==dust,"Malformed state unexpectedly produced a result");
        set(dust,"maxTicks",100);load(inventory,tagged(dust,0,true));
        inventory.getStackInSlot(0).getTagCompound().getCompoundTag("TickableItem").setLong("maxTick",1);
        batch.invoke(chest,dust,inventory.getStackInSlot(0),world);
        require(inventory.getStackInSlot(0).getItem()==dust&&inventory.getStackInSlot(0).getTagCompound().getCompoundTag("TickableItem").getLong("Tick")==20,"Native item maxTicks was replaced by the informational maxTick tag");
        ItemStack playerStack=tagged(dust,3,true);dust.onUpdate(playerStack,world,null,0,false);
        require(playerStack.getTagCompound().getCompoundTag("TickableItem").getLong("Tick")==3,"Dust unexpectedly advanced through ordinary onUpdate");
        load(inventory,tagged(dust,0,false));set(chest,"tickCount",Integer.MAX_VALUE-2);
        ((net.minecraft.tileentity.TileEntity)chest).setWorldObj(world);
        for(int i=0;i<10;i++)((net.minecraft.tileentity.TileEntity)chest).updateEntity();
        require(inventory.getStackInSlot(0).getItem()==dust,"Native scheduler ran before signed-int wrap modulo boundary");
        ((net.minecraft.tileentity.TileEntity)chest).updateEntity();
        require(inventory.getStackInSlot(0).getItem()==Items.diamond,"Native scheduler failed at signed-int wrap modulo boundary");
        System.out.println("Native decay behavior verified: stack clamp/load, exact fresh batches, saved/malformed NBT, long/int overflow, shared result count mutation and no ordinary onUpdate progress");
        Class<?> adapter;
        try{adapter=Class.forName("com.github.dcysteine.nesql.exporter.capture.DecayableRecipes");}
        catch(ClassNotFoundException missing){throw new AssertionError("Missing native decay chest adapter",missing);}
        set(dust,"maxTicks",Integer.MAX_VALUE);template.stackSize=7;
        Object source=recipe(Integer.MAX_VALUE,new ItemStack(dust),template);
        List registry=(List)MagicApi.field(Class.forName(RECIPE),null,"mRecipes");registry.add(source);
        try{
            TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("gtPlusPlus.nei.DecayableRecipeHandler").newInstance();
            Constructor<?> constructor=adapter.getDeclaredConstructor(TemplateRecipeHandler.class);constructor.setAccessible(true);
            RegistryRecipes cursor=(RegistryRecipes)constructor.newInstance(handler);
            require(cursor.size()==1,"Native decay registry entry lost");cursor.verify();
            RecipeRow row=NativeCoreFixesTest.row(new ItemStack(dust),template);
            require(cursor.capture(0,row),"Native decay entry excluded");
            JsonObject choice=row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.getAsJsonObject("rule").get("meta").getAsBoolean()&&!choice.getAsJsonObject("rule").get("nbt").getAsBoolean(),"Saved/malformed NBT states leaked into fresh-input decay rule");
            require(choice.getAsJsonObject("consume").get("kind").getAsString().equals("stack")&&choice.getAsJsonArray("returns").size()==0,"Whole native slot replacement became unit/container crafting");
            require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("1")&&row.record.get("duration").isJsonNull(),"Native decay became displayed count or fixed NEI duration");
            require(row.properties.getAsJsonObject("decay:freshBatches").getAsJsonObject("value").get("value").getAsString().equals("107374183"),"Fresh batch count overflowed signed int");
            require(template.stackSize==7&&handler.arecipes.get(0).getResult().items[0].stackSize==7,"Capture mutated shared result or changed original native view");
            require(handler.arecipes.get(0).getIngredients().get(0).relx==93&&handler.arecipes.get(0).getResult().relx==142,"Native decay slot layout changed");
            row.finish();java.nio.file.Path file=java.nio.file.Files.createTempFile("decayable-facts-",".json");
            try{java.nio.file.Files.write(file,CanonicalJson.bytes(row.record));require(new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(file),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("outputs").size()==1,"Durable decay facts lost result");}
            finally{java.nio.file.Files.delete(file);}
            template.stackSize=1;
            try{cursor.verify();throw new AssertionError("Native shared result-template drift accepted");}catch(com.github.dcysteine.nesql.exporter.task.Jobs.Fault expected){require(expected.code.equals("environment_changed"),"Wrong decay drift failure");}
            registry.clear();
            registry.add(recipe(0,null,null));
            RegistryRecipes invalid=(RegistryRecipes)constructor.newInstance(handler);
            require(invalid.size()==1&&!invalid.capture(0,NativeCoreFixesTest.row())&&invalid.exclusion(0)!=null,"Invalid native registry entry lacks bounded exclusion evidence");
            registry.clear();registry.add(recipe(1,new ItemStack(dust),new ItemStack(Items.paper)));
            try{constructor.newInstance(handler);throw new AssertionError("Registry output different from native item result accepted");}catch(InvocationTargetException expected){require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Wrong mismatched result failure");}
            registry.clear();set(dust,"maxTicks",0);registry.add(recipe(1,new ItemStack(dust),template));
            try{constructor.newInstance(handler);throw new AssertionError("Nonpositive native maxTicks accepted");}catch(InvocationTargetException expected){require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Wrong invalid threshold failure");}
            registry.clear();
            Item unknown=NativeCoreFixesTest.item(UnknownDust.class.getName(),"decay_override");set(unknown,"maxTicks",1);set(unknown,"turnsIntoItem",template);
            registry.add(recipe(1,new ItemStack(unknown),template));
            try{constructor.newInstance(handler);throw new AssertionError("Unknown native decay override accepted");}catch(InvocationTargetException expected){require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault,"Unknown decay callback was invoked");}
        }finally{registry.clear();}
        System.out.println("Native decay chest: insertion limit, loaded whole-stack replacement, shared-template side effect, NBT/long/int overflow scheduling, exact batches, native view isolation, durable JSON, registry drift and override guard passed");
    }
    private static ItemStack tagged(Item item,long tick,boolean active){ItemStack stack=new ItemStack(item,16);NBTTagCompound state=new NBTTagCompound();state.setLong("Tick",tick);state.setBoolean("isActive",active);stack.setTagInfo("TickableItem",state);return stack;}
    private static void load(IInventory inventory,ItemStack stack)throws Exception{NBTTagCompound root=new NBTTagCompound(),entry=new NBTTagCompound();stack.writeToNBT(entry);entry.setInteger("Slot",0);NBTTagList list=new NBTTagList();list.appendTag(entry);root.setTag("Items",list);inventory.getClass().getMethod("readFromNBT",NBTTagCompound.class).invoke(inventory,root);require(inventory.getStackInSlot(0).stackSize==stack.stackSize,"Native NBT loader unexpectedly clamped a stored stack");}
    private static Object recipe(int time,ItemStack input,ItemStack output)throws Exception{Object recipe=allocate(Class.forName(RECIPE));set(recipe,"mTime",time);set(recipe,"mInput",input);set(recipe,"mOutput",output);return recipe;}
    private static Object allocate(Class<?> type)throws Exception{Field access=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");access.setAccessible(true);return ((sun.misc.Unsafe)access.get(null)).allocateInstance(type);}
    private static void set(Object target,String name,Object value)throws Exception{for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    public static final class UnknownDust extends gtPlusPlus.core.item.materials.DustDecayable{
        public UnknownDust(){super("unused",0,1,new String[0],new ItemStack(Items.paper),0,null);}
        @Override public ItemStack getDecayResult(){throw new AssertionError("Unknown decay result callback executed");}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
