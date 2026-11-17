package com.github.dcysteine.nesql.exporter.capture;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.*;

/** Zero is a native incubator cost; the selected fluid object and exact tags are still required. */
final class NativeBinnieZeroTest {
    static void run() throws Exception {
        FluidStack template=new FluidStack(FluidRegistry.WATER,0);template.tag=new NBTTagCompound();template.tag.setString("culture","native");
        Class<?> type=MagicApi.type("binnie.genetics.machine.incubator.IncubatorRecipe");
        Object recipe=type.getConstructor(ItemStack.class,FluidStack.class,FluidStack.class,float.class)
            .newInstance(new ItemStack(Items.apple),template,null,0f);
        FluidStack stock=template.copy();stock.amount=17;
        if(!(Boolean)type.getMethod("isInputLiquid",FluidStack.class).invoke(recipe,stock)
            ||!(Boolean)type.getMethod("isInputLiquidSufficient",FluidStack.class).invoke(recipe,stock)
            ||(Boolean)type.getMethod("isInputLiquid",FluidStack.class).invoke(recipe,new Object[]{null}))throw new AssertionError("Native zero-cost incubator fluid predicate differs");
        stock.tag.setString("culture","different");
        if((Boolean)type.getMethod("isInputLiquid",FluidStack.class).invoke(recipe,stock))throw new AssertionError("Native zero-cost incubator ignored NBT");
        RecipeRow row=NativeCoreFixesTest.row();row.zeroFluidInput(0,template,"binnieIncubator");
        com.google.gson.JsonObject choice=row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
        if(!choice.get("amount").getAsString().equals("0")||!choice.getAsJsonObject("consume").get("kind").getAsString().equals("keep")||template.amount!=0)throw new AssertionError("Native zero-cost fluid was normalized or consumed");
        System.out.println("Native incubator zero fluid: identity/NBT/presence, sufficient amount and zero debit passed");
    }
}
