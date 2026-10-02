package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gregtech.api.enums.Materials;
import gregtech.api.enums.MaterialsUEVplus;
import gregtech.api.util.GTRecipe;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import tectech.TecTech;
import tectech.recipe.EyeOfHarmonyRecipe;
import tectech.recipe.EyeOfHarmonyRecipeStorage;
import tectech.util.ItemStackLong;
import tectech.util.FluidStackLong;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** GT 5.09.51.482: NEI is a projection of a shared, stateful machine process. */
final class Harmony {
    private Harmony() {}

    static List<RecipeRow> capture(GTRecipe display, List<GtRecipes.Placement> inputs,
                                  List<GtRecipes.Placement> outputs, RecipeRow row) {
        return capture(display, inputs, outputs, row, TecTech.eyeOfHarmonyRecipeStorage);
    }

    static List<RecipeRow> capture(GTRecipe display, List<GtRecipes.Placement> inputs,
                                  List<GtRecipes.Placement> outputs, RecipeRow row, EyeOfHarmonyRecipeStorage storage) {
        require(display.mSpecialItems != null && display.mSpecialItems.getClass() == EyeOfHarmonyRecipe.class,
                "Unknown Eye of Harmony recipe class");
        EyeOfHarmonyRecipe nativeRecipe = (EyeOfHarmonyRecipe)display.mSpecialItems;
        ItemStack trigger = nativeRecipe.getRecipeTriggerItem();
        require(trigger != null && trigger.getItem() != null && storage != null
                && storage.recipeLookUp(trigger) == nativeRecipe, "Unregistered Harmony process");
        require(display.mInputs.length == 1 && same(trigger, display.mInputs[0]) && inputs.size() == 1
                && !inputs.get(0).binding.fluid && !inputs.get(0).binding.special && inputs.get(0).binding.index == 0,
                "Harmony trigger projection changed");
        require(display.mFluidInputs.length == 3 && fluid(display.mFluidInputs[0], Materials.Hydrogen.getGas(0))
                && fluid(display.mFluidInputs[1], Materials.Helium.getGas(0))
                && fluid(display.mFluidInputs[2], MaterialsUEVplus.RawStarMatter.getFluid(0)), "Harmony fluid projection changed");
        for (FluidStack f : display.mFluidInputs) require(f.amount == 0, "Harmony fluid placeholders changed");
        List<ItemStackLong> items = nativeRecipe.getOutputItems(); List<FluidStackLong> fluids = nativeRecipe.getOutputFluids();
        require(display.mOutputs.length == items.size() && display.mFluidOutputs.length == fluids.size(), "Harmony output projection changed");
        for (int i=0; i<items.size(); i++) require(same(items.get(i).itemStack, display.mOutputs[i]), "Harmony item projection changed at " + i);
        for (int i=0; i<fluids.size(); i++) require(fluid(fluids.get(i).fluidStack, display.mFluidOutputs[i]), "Harmony fluid projection changed at " + i);
        List<RecipeRow> rows = rows(nativeRecipe, row);
        for (RecipeRow branch : rows) {
            GtRecipes.Placement input = inputs.get(0);
            branch.slot("input", "item", 0, input.display.relx, input.display.rely);
            for (GtRecipes.Placement output : outputs) {
                GtRecipes.Binding binding = output.binding;
                require(!binding.special && binding.index >= 0 && binding.index < (binding.fluid ? fluids.size() : items.size()), "Invalid Harmony display output");
                require(binding.fluid ? fluid(fluids.get(binding.index).fluidStack, (FluidStack)output.source)
                        : same(items.get(binding.index).itemStack, (ItemStack)output.source), "Harmony view changes source identity");
                branch.slot("output", binding.fluid ? "fluid" : "item", binding.index, output.display.relx, output.display.rely);
            }
        }
        return rows;
    }

    static List<RecipeRow> rows(EyeOfHarmonyRecipe recipe, RecipeRow template) {
        require(recipe.getClass() == EyeOfHarmonyRecipe.class, "Unknown Harmony recipe subclass");
        long hydrogen=recipe.getHydrogenRequirement(), helium=recipe.getHeliumRequirement(), ticks=recipe.getRecipeTimeInTicks();
        long start=recipe.getEUStartCost(), energy=recipe.getEUOutput(), tier=recipe.getRocketTier(), compression=recipe.getSpacetimeCasingTierRequired();
        double chance=recipe.getBaseRecipeSuccessChance();
        require(hydrogen>0 && helium>0 && ticks>0 && start>0 && energy>0 && tier>=0 && tier<=9
                && compression==Math.min(8,tier) && Double.isFinite(chance) && chance>=0 && chance<=1, "Invalid Harmony native parameters");
        List<ItemStackLong> items=recipe.getOutputItems(); List<FluidStackLong> fluids=recipe.getOutputFluids();
        require(!items.isEmpty() && fluids.size()>=2 && items.size()+fluids.size()+1<=4096, "Invalid Harmony output count");
        List<RecipeRow> result=new ArrayList<>();
        for (boolean parallel : new boolean[]{false,true}) {
            RecipeRow row=template.branch();
            JsonObject process=object("kind","harmony","mode",parallel ? "parallel":"single",
                    "hydrogen",Long.toString(hydrogen),"helium",Long.toString(helium),"ticks",Long.toString(ticks),
                    "startEu",Long.toString(start),"outputEu",Long.toString(energy),"chance",Double.toString(chance),
                    "rocketTier",(int)tier,"compressionTier",(int)compression);
            row.record.add("process",process);
            ItemStack trigger=recipe.getRecipeTriggerItem().copy(); trigger.stackSize=1;
            JsonArray choices = new JsonArray();
            net.minecraft.block.Block block = net.minecraft.block.Block.getBlockFromItem(trigger.getItem());
            require(block != net.minecraft.init.Blocks.air, "Harmony trigger is not a registered block item");
            List<ItemStack> triggers = new ArrayList<>();
            for (Object value : net.minecraft.item.Item.itemRegistry) {
                net.minecraft.item.Item item = (net.minecraft.item.Item)value;
                if (net.minecraft.block.Block.getBlockFromItem(item) == block) triggers.add(new ItemStack(item));
            }
            triggers.sort(Comparator.comparing(item -> net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem())));
            require(!triggers.isEmpty(), "Missing registered Harmony trigger");
            for (ItemStack alternative : triggers) choices.add(object("id",row.facts.item(alternative),"amount","1","consume",object("kind","keep"),
                    "returns",array(),"rule",object("kind","wildcard","meta",true,"nbt",true)));
            row.inputs.add(object("slot",0,"kind","item","choices",choices));
            if (parallel) buffer(row,2,MaterialsUEVplus.RawStarMatter.getFluid(1),Math.max(1,(long)Math.ceil((double)helium*1.24E-5*8.0)));
            else { buffer(row,0,Materials.Hydrogen.getGas(1),hydrogen); buffer(row,1,Materials.Helium.getGas(1),helium); }
            for (int i=0;i<items.size();i++) {
                ItemStackLong output=items.get(i);
                row.itemOutput(null,i,output.itemStack.copy(),10000,quantity("success",output.stackSize));
            }
            for (int i=0;i<fluids.size();i++) {
                FluidStackLong output=fluids.get(i);
                row.fluidOutput(null,i,output.fluidStack.copy(),quantity("success",output.amount));
            }
            row.fluidOutput(null,fluids.size(),MaterialsUEVplus.SpaceTime.getMolten(1),quantity("failure",14400L*(1L<<(tier+1))));
            row.property("tectech:mode","运行模式",parallel ? "星界阵列并行（安装 1–8637 个；8–1048576 并行）":"单次（无已安装星界阵列）");
            row.property("tectech:process","运行条件","同批产出共享成功次数；产量受稳定场、加速场、流体过量与历史保底状态影响。启动时耗尽所选内部流体存量；能量按整次结算。电路和已安装阵列不是每次消耗品。");
            result.add(row);
        }
        return result;
    }
    private static void buffer(RecipeRow row,int slot,FluidStack fluid,long minimum) {
        require(fluid!=null && fluid.getFluid()!=null,"Missing Harmony buffer fluid");
        row.inputs.add(object("slot",slot,"kind","fluid","choices",array(object("id",row.facts.fluid(fluid),
                "amount",Long.toString(minimum),"consume",object("kind","buffer"),"returns",array(),
                "rule",object("kind","wildcard","meta",false,"nbt",true)))));
    }
    private static JsonObject quantity(String outcome,long nominal) {
        require(nominal>0,"Nonpositive Harmony output base");
        return object("kind","harmony","outcome",outcome,"nominal",Long.toString(nominal));
    }
    private static boolean same(ItemStack a,ItemStack b) { return a!=null && b!=null && a.getItem()==b.getItem()
            && damage(a)==damage(b) && ItemStack.areItemStackTagsEqual(a,b); }
    private static int damage(ItemStack stack) { return net.minecraft.init.Items.feather.getDamage(stack); }
    private static boolean fluid(FluidStack a,FluidStack b) { return a!=null && b!=null && a.isFluidEqual(b); }
    private static void require(boolean ok,String message) { if(!ok)throw new Jobs.Fault("recipe_unsupported",message); }
}
