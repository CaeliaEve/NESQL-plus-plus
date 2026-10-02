package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.*;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Real EnderIO recipe tables, matcher and task consumer on an isolated registry. No world/GL. */
final class NativeVatTest {
    private static final String ROOT="crazypants.enderio.machine.";
    private static Class<?> input, machineInput;
    static void run() throws Exception {
        Object loader=cpw.mods.fml.common.Loader.instance();Field named=loader.getClass().getDeclaredField("namedMods");named.setAccessible(true);Object prior=named.get(loader);
        try {named.set(loader,Collections.emptyMap());runOwned();}finally{named.set(loader,prior);}
    }
    private static void runOwned() throws Exception {
        input=type(ROOT+"recipe.RecipeInput");machineInput=type(ROOT+"MachineRecipeInput");
        Class<?> vat=type(ROOT+"vat.VatRecipe");
        require(vat.getProtectionDomain().getCodeSource().getLocation().toString().contains("/native-tests/enderio.jar!"),"Pinned EnderIO jar required");
        TemplateRecipeHandler handler=(TemplateRecipeHandler)type("crazypants.enderio.nei.VatRecipeHandler").newInstance();
        require(Recipes.adapter(handler)!=null,"Missing EnderIO vat adapter");
        type("com.enderio.core.client.render.RenderUtil").getMethod("renderGuiTank",FluidStack.class,int.class,int.class,double.class,double.class,double.class,double.class,double.class);
        type("crazypants.enderio.power.PowerDisplayUtil").getMethod("formatPower",int.class);
        Object a=item(Items.iron_ingot,3,0,1.25f), b=item(Items.gold_ingot,5,1,1.1f);
        Object recipe=recipe(a,b,fluid(1.3f));
        Object offered=offered(new ItemStack(Items.iron_ingot),new ItemStack(Items.gold_ingot),new FluidStack(FluidRegistry.WATER,1375));
        require(matches(recipe,offered),"Native Vat unexpectedly requires full item quantities");
        FluidStack expected=(FluidStack)invoke(vat,recipe,"getFluidOutput",new Class<?>[]{offered.getClass()},offered);
        require(expected.amount==Math.round((1.25f*1.1f)*1.3f*1000f),"Native float/table calculation changed");
        require(!matches(recipe,offered(new ItemStack(Items.iron_ingot),new ItemStack(Items.gold_ingot),new FluidStack(FluidRegistry.WATER,1374))),"Fluid minimum lost");
        Object manager=invoke(type(ROOT+"vat.VatRecipeManager"),null,"getInstance",new Class<?>[0]);
        @SuppressWarnings("unchecked") List<Object> registered=(List<Object>)invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0]);
        List<Object> previous=new ArrayList<>(registered);
        try {
            registered.clear();registered.add(recipe);
            Object nativeMachine=type(ROOT+"vat.VatMachineRecipe").newInstance();
            List<?> consumed=(List<?>)invoke(nativeMachine.getClass(),nativeMachine,"getQuantitiesConsumed",new Class<?>[]{offered.getClass()},offered);
            require(((ItemStack)field(consumed.get(1),"item")).stackSize==3&&((ItemStack)field(consumed.get(2),"item")).stackSize==5,"Native consumption limit changed");
            Class<?> adapter=type("com.github.dcysteine.nesql.exporter.capture.VatRecipes");
            Constructor<?> constructor=adapter.getDeclaredConstructor(TemplateRecipeHandler.class,List.class);constructor.setAccessible(true);
            RegistryRecipes cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(recipe));
            RecipeRow row=row(0);
            require(cursor.size()==1&&cursor.capture(0,row),"Expected one native table pair");
            require(row.outputs.get(0).getAsJsonObject().get("amount").getAsInt()==expected.amount,"Exporter used display instead of native table output");
            JsonObject choice=row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.getAsJsonObject("consume").get("kind").getAsString().equals("upto")&&choice.get("amount").getAsInt()==3,"Understock consumption not explicit");
            require(row.record.getAsJsonObject("process").getAsJsonArray("extra").size()==0,"Two-slot recipe gained an optional slot");
            // Last fluid input overwrites both precomputed tables; required item count is still ignored.
            Object overwritten=recipe(a,b,fluid(1.1f),fluid(2f));
            cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(overwritten));row=row(0);require(cursor.capture(0,row),"Overwritten fluid pair omitted");
            require(row.outputs.get(0).getAsJsonObject().get("amount").getAsInt()==2750,"Last native fluid mapping did not win");
            // Cross-slot consumption picks the first matching item record, regardless of declared slot.
            Object repeated=recipe(item(Items.iron_ingot,7,0,1f),item(Items.iron_ingot,2,1,1f),fluid(1f));
            cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(repeated));row=row(0);require(cursor.capture(0,row),"Repeated reagent failed");
            require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().get("amount").getAsInt()==7,"Consumption incorrectly used selected slot's count");
            // One required slot allows an unmatched extra stack, which the native consumer still consumes.
            Object single=recipe(a,fluid(1f));registered.clear();registered.add(single);
            Object extra=offered(new ItemStack(Items.iron_ingot),new ItemStack(Items.diamond,9),new FluidStack(FluidRegistry.WATER,1250));
            require(matches(single,extra),"Single-slot native match now rejects extra input");
            consumed=(List<?>)invoke(nativeMachine.getClass(),nativeMachine,"getQuantitiesConsumed",new Class<?>[]{extra.getClass()},extra);
            require(((ItemStack)field(consumed.get(2),"item")).stackSize==1,"Unmatched extra default consumption differs");
            cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(single));row=row(0);require(cursor.capture(0,row),"Single-slot recipe failed");
            require(row.record.getAsJsonObject("process").getAsJsonArray("extra").size()==1,"Optional consumption rules lost");
            // Rounding may produce no output; keep the operation but never create a false product edge.
            Object zero=recipe(item(Items.iron_ingot,1,0,1f),fluid(0.00000001f));
            cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(zero));row=row(0);require(cursor.capture(0,row),"Zero-yield operation omitted");
            require(row.outputs.size()==0&&!row.record.getAsJsonObject("process").get("zeroOutput").isJsonNull(),"Zero-yield output fabricated");
            require(((ItemStack)invoke(input,a,"getInput",new Class<?>[0])).stackSize==3,"Native input mutated");
            // Slot selection is first-match even when a later same-slot record has another multiplier.
            Object shadowed=recipe(a,item(Items.iron_ingot,9,0,2f),fluid(1f));
            cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(shadowed));
            require(cursor.size()==2&&cursor.capture(0,row(0))&&!cursor.capture(1,row(1)),"Shadowed same-slot pair became a usable recipe");
            // Zero-volume fluid identities can match. They are preserved as presence, never a positive debit.
            Object zeroIn=recipe(item(Items.iron_ingot,1,0,0.00000001f),fluid(1f));
            require(matches(zeroIn,offered(new ItemStack(Items.iron_ingot),null,new FluidStack(FluidRegistry.WATER,0))),"Native zero-volume identity no longer matches");
            cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(zeroIn));row=row(0);require(cursor.capture(0,row),"Zero-input table omitted");
            require(row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("consume").get("kind").getAsString().equals("keep"),"Zero input became positive consumption");
            FluidTank empty=new FluidTank(8000);
            require(empty.fill(new FluidStack(FluidRegistry.LAVA,0),true)==0&&empty.getFluid()!=null&&empty.getFluidAmount()==0,"Native empty-tank zero-fill identity changed");
            // Match and export preserve the native table's NBT, while capture owns copied cells.
            FluidStack tagged=new FluidStack(FluidRegistry.WATER,1000);tagged.tag=new net.minecraft.nbt.NBTTagCompound();tagged.tag.setInteger("batch",7);
            Object taggedRecipe=recipe(a,input.getConstructor(FluidStack.class,float.class).newInstance(tagged,1f));
            FluidStack offeredTag=tagged.copy();offeredTag.amount=1250;
            require(matches(taggedRecipe,offered(new ItemStack(Items.iron_ingot),null,offeredTag))&&!matches(taggedRecipe,offered(new ItemStack(Items.iron_ingot),null,new FluidStack(FluidRegistry.WATER,1250))),"Fluid tag predicate lost");
            cursor=(RegistryRecipes)constructor.newInstance(handler,Arrays.asList(taggedRecipe));row=row(0);
            @SuppressWarnings("unchecked") Set<String> known=(Set<String>)field(row.facts,"fluids");known.add(Identity.fluid("water",TypedNbt.encode(tagged.tag)));
            FluidStack cell=(FluidStack)invoke(vat,taggedRecipe,"getRequiredFluidInput",new Class<?>[]{offered.getClass()},offered(new ItemStack(Items.iron_ingot),null,offeredTag));
            cell.amount=777;cell.tag.setInteger("batch",8);
            require(cursor.capture(0,row),"Owned table capture failed");
            JsonObject capturedFluid=row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(capturedFluid.get("amount").getAsInt()==1250&&capturedFluid.get("id").getAsString().equals(Identity.fluid("water",TypedNbt.encode(tagged.tag))),"Captured table aliases native cells");
        } finally {registered.clear();registered.addAll(previous);}
        System.out.println("EnderIO Vat native tables: float rounding, overwrite, understock, cross-slot consumption, optional input and zero yield passed");
    }
    private static Object item(Item item,int amount,int slot,float multiplier)throws Exception{return input.getConstructor(ItemStack.class,boolean.class,float.class,int.class).newInstance(new ItemStack(item,amount),true,multiplier,slot);}
    private static Object fluid(float multiplier)throws Exception{return input.getConstructor(FluidStack.class,float.class).newInstance(new FluidStack(FluidRegistry.WATER,1000),multiplier);}
    private static Object recipe(Object... inputs)throws Exception{
        Object array=Array.newInstance(input,inputs.length);for(int i=0;i<inputs.length;i++)Array.set(array,i,inputs[i]);
        Class<?> out=type(ROOT+"recipe.RecipeOutput"),bonus=type(ROOT+"recipe.RecipeBonusType");
        Object result=out.getConstructor(FluidStack.class).newInstance(new FluidStack(FluidRegistry.LAVA,1000)), outputs=Array.newInstance(out,1);Array.set(outputs,0,result);
        Object base=type(ROOT+"recipe.Recipe").getConstructor(array.getClass(),outputs.getClass(),int.class,bonus).newInstance(array,outputs,1200,Enum.valueOf((Class)bonus,"NONE"));
        return type(ROOT+"vat.VatRecipe").getConstructor(type(ROOT+"recipe.IRecipe")).newInstance(base);
    }
    private static Object offered(ItemStack a,ItemStack b,FluidStack f)throws Exception{Object array=Array.newInstance(machineInput,3);Array.set(array,0,machineInput.getConstructor(int.class,ItemStack.class).newInstance(0,a));Array.set(array,1,machineInput.getConstructor(int.class,ItemStack.class).newInstance(1,b));Array.set(array,2,machineInput.getConstructor(int.class,FluidStack.class).newInstance(0,f));return array;}
    private static boolean matches(Object recipe,Object inputs){return (Boolean)invoke(recipe.getClass(),recipe,"isInputForRecipe",new Class<?>[]{inputs.getClass()},inputs);}
    @SuppressWarnings("unchecked") private static RecipeRow row(int index){Facts facts=new Facts("en_US");for(Item item:new Item[]{Items.iron_ingot,Items.gold_ingot,Items.diamond})((Set<String>)field(facts,"items")).add(Identity.item(Item.itemRegistry.getNameForObject(item),0,TypedNbt.encode(null)));for(Fluid f:new Fluid[]{FluidRegistry.WATER,FluidRegistry.LAVA})((Set<String>)field(facts,"fluids")).add(Identity.fluid(f.getName(),null));return new RecipeRow(facts,object("owner","fixture","handler","vat","key","vat"),"fixture",index);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
