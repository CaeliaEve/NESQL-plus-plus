package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned native IC2 work() and inventory classes; no world or render loop. */
final class NativeBlastTest {
    private static final String API="ic2.api.recipe.", MACHINE="ic2.core.block.machine.tileentity.TileEntityBlastFurnace";
    static void run() throws Exception {
        TemplateRecipeHandler handler=(TemplateRecipeHandler)Class.forName("ic2.neiIntegration.core.recipehandler.BlastFurnaceRecipeHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"IC2 blast furnace has no staged native adapter");
        Class<?> machineType=Class.forName(MACHINE), items=Class.forName("ic2.core.Ic2Items");
        items.getField("airCell").set(null,new ItemStack(Items.paper));items.getField("cell").set(null,new ItemStack(Items.bucket));
        machineType.getMethod("init").invoke(null);
        Object manager=Class.forName(API+"Recipes").getField("blastfurance").get(null);
        ItemStack input=new ItemStack(Items.iron_ingot,11), product=new ItemStack(Items.diamond,2), slag=new ItemStack(Items.coal,3);
        Object predicate=Class.forName(API+"RecipeInputItemStack").getConstructor(ItemStack.class,int.class).newInstance(input,4);
        manager.getClass().getMethod("addRecipe",Class.forName(API+"IRecipeInput"),NBTTagCompound.class,ItemStack[].class)
            .invoke(manager,predicate,null,new ItemStack[]{product,slag,new ItemStack(Items.gold_ingot,9)});
        Object machine=machineType.newInstance();Method work=machineType.getDeclaredMethod("work");work.setAccessible(true);
        put(machine,"inputSlot",new ItemStack(Items.iron_ingot,3));work.invoke(machine);
        require(progress(machine)==0,"Native blast count gate disappeared");
        put(machine,"inputSlot",input.copy());put(machine,"airSlot",new ItemStack(Items.paper,9));
        int steps=0;while(get(machine,"outputSlot")==null && steps++<6010)work.invoke(machine);
        require(steps==6001 && progress(machine)==0,"Fresh hot blast cycle must advance 6000 times then complete separately");
        require(get(machine,"inputSlot").stackSize==10 && get(machine,"airSlot").stackSize==3 && get(machine,"airOutputSlot").stackSize==6,
            "Native blast must consume one primary, six separate air cells and return six cells");
        require(get(machine,"outputSlot").stackSize==2 && get(machine,"slagOutputSlot").stackSize==3,"Native blast changed first two products");
        // Existing main output accepts diamond but cannot accept coal: slag check uses the wrong slot.
        work.invoke(machine);require(progress(machine)==0,"Native second-result admission unexpectedly uses the slag slot");
        put(machine,"outputSlot",null);put(machine,"slagOutputSlot",new ItemStack(Items.gold_ingot,64));set(machine,"progress",6000);
        work.invoke(machine);require(get(machine,"outputSlot").stackSize==2 && get(machine,"slagOutputSlot").getItem()==Items.gold_ingot,
            "Native completion must retain main output even when slag is lost");
        put(machine,"outputSlot",null);put(machine,"inputSlot",null);set(machine,"progress",1000);work.invoke(machine);
        require(progress(machine)==1000,"Removing input cleared native progress");
        put(machine,"inputSlot",input.copy());put(machine,"airSlot",null);work.invoke(machine);
        require(progress(machine)==1000,"Missing air did not stop at the checkpoint");
        put(machine,"airSlot",new ItemStack(Items.paper));put(machine,"airOutputSlot",new ItemStack(Items.bucket,64));work.invoke(machine);
        require(progress(machine)==1000,"Blocked empty-cell output did not stop air consumption");
        put(machine,"airOutputSlot",null);work.invoke(machine);require(progress(machine)==1001,"Resuming did not retain the air checkpoint");
        // Actual air slot wrapper normalizes empty NBT, rejects nonempty tags and ignores meta for plain items.
        Object airSlot=MagicApi.field(machine,"airSlot");Method accepts=airSlot.getClass().getMethod("accepts",ItemStack.class);
        ItemStack air=new ItemStack(Items.paper,1,7);air.setTagCompound(new NBTTagCompound());
        require((Boolean)accepts.invoke(airSlot,air),"Native air wrapper rejected empty tags/ignored metadata");
        air.getTagCompound().setInteger("owner",1);require(!(Boolean)accepts.invoke(airSlot,air),"Native air wrapper ignored nonempty tags");
        RegistryRecipes adapter=adapter(handler,manager,12345);
        RecipeRow row=row(input,product,slag,new ItemStack(Items.paper),new ItemStack(Items.bucket));adapter.capture(0,row);
        require(row.inputs.size()==2 && row.outputs.size()==2 && row.elements.size()==4,"Blast row must keep staged air and only the first two products");
        require(row.record.getAsJsonObject("process").get("heat").getAsInt()==12345 && row.record.get("duration").isJsonNull() && row.record.get("energy").isJsonNull(),"Blast heat gate became fixed duration/EU");
        require(choice(row,0).get("amount").getAsInt()==4 && choice(row,0).getAsJsonObject("consume").get("kind").getAsString().equals("staged"),"Blast registry gate was confused with final unit consumption");
        require(choice(row,1).get("amount").getAsInt()==1 && choice(row,1).getAsJsonArray("returns").size()==1
            && choice(row,1).getAsJsonObject("rule").get("kind").getAsString().equals("untagged"),"Blast air is not modeled per stage");
        require(row.outputs.get(1).getAsJsonObject().get("amount").isJsonNull() && row.outputs.get(1).getAsJsonObject().getAsJsonObject("quantity").get("nominal").getAsInt()==3,"Slag loss became an unconditional fixed output");
        handler.arecipes.get(0).getResult().items[0].stackSize=99;
        RecipeRow repeat=row(input,product,slag,new ItemStack(Items.paper),new ItemStack(Items.bucket));adapter.capture(0,repeat);
        require(repeat.outputs.equals(row.outputs) && input.stackSize==11 && product.stackSize==2,"Blast display mutated native registry");
        put(machine,"outputSlot",null);put(machine,"slagOutputSlot",new ItemStack(Items.coal,63));set(machine,"progress",6000);work.invoke(machine);
        require(get(machine,"slagOutputSlot").stackSize==64,"Native slag overflow did not retain the partial fitting amount");
        Object containerManager=manager.getClass().newInstance();
        Object containerInput=predicate.getClass().getConstructor(ItemStack.class,int.class).newInstance(new ItemStack(Items.milk_bucket),1);
        manager.getClass().getMethod("addRecipe",Class.forName(API+"IRecipeInput"),NBTTagCompound.class,ItemStack[].class)
            .invoke(containerManager,containerInput,null,new ItemStack[]{product});
        try{adapter(handler,containerManager,50000).capture(0,row(new ItemStack(Items.milk_bucket),product));throw new AssertionError("Container consumption was flattened into unit loss");}
        catch(Jobs.Fault expected){require(expected.code.equals("recipe_unsupported"),"Wrong container boundary error");}
        @SuppressWarnings("unchecked") List<Object> callbacks=(List<Object>)MagicApi.field(manager,"uncacheableRecipes");
        callbacks.add(new Object());
        try{adapter.capture(0,row(input,product,slag));throw new AssertionError("Late native callback was executed/accepted");}
        catch(Jobs.Fault expected){require(expected.code.equals("recipe_unsupported"),"Wrong selector drift error");}finally{callbacks.clear();}
        require(Ic2Recipes.supports(handler)==false,"Blast inherited ordinary IC2 machine semantics");
        System.out.println("Native IC2 blast: count gate, 6001 hot calls, six staged air/cell events, retained progress, wrong-slot gate/slag loss, owned projection passed");
    }
    private static RegistryRecipes adapter(TemplateRecipeHandler handler,Object manager,int heat)throws Exception {
        Constructor<?> c=Class.forName("com.github.dcysteine.nesql.exporter.capture.BlastRecipes").getDeclaredConstructor(TemplateRecipeHandler.class,Object.class,int.class,ItemStack.class,ItemStack.class);c.setAccessible(true);
        try{return (RegistryRecipes)c.newInstance(handler,manager,heat,new ItemStack(Items.paper),new ItemStack(Items.bucket));}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    private static void put(Object machine,String name,ItemStack item)throws Exception{Object slot=MagicApi.field(machine,name);slot.getClass().getMethod("put",ItemStack.class).invoke(slot,item);}
    private static ItemStack get(Object machine,String name)throws Exception{Object slot=MagicApi.field(machine,name);return (ItemStack)slot.getClass().getMethod("get").invoke(slot);}
    private static void set(Object machine,String key,int value)throws Exception{machine.getClass().getField(key).setInt(machine,value);}
    private static int progress(Object machine)throws Exception{return machine.getClass().getField("progress").getInt(machine);}
    private static JsonObject choice(RecipeRow row,int slot){return row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();}
    @SuppressWarnings("unchecked") private static RecipeRow row(ItemStack... stacks){Facts facts=new Facts("en_US");Set<String> known=(Set<String>)MagicApi.field(facts,"items");for(ItemStack stack:stacks)known.add(Identity.item(Item.itemRegistry.getNameForObject(stack.getItem()),stack.getItemDamage(),TypedNbt.encode(stack.getTagCompound())));return new RecipeRow(facts,object("owner","IC2","handler","native","key","blast"),"category_test",0);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
