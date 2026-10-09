package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Method;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Gendustry genetic machines. Native NEI caches are the sole recipe source. */
final class GendustryGeneticsRecipes implements RegistryRecipes {
    private static final String ROOT = "net.bdew.gendustry.nei.";
    private final TemplateRecipeHandler handler;
    private final String kind;
    private final String recipeClass;
    private final int size;
    static boolean supports(ICraftingHandler h) {
        if (h == null) return false;
        return supportsClass(h.getClass().getName());
    }
    static boolean supportsClass(String n) {
        if (n == null) return false;
        return n.equals(ROOT+"ImprinterHandler") || n.equals(ROOT+"SamplerHandler") || n.equals(ROOT+"TransposerHandler")
            || n.equals(ROOT+"MutatronHandler") || n.equals(ROOT+"ReplicatorHandler") || n.equals(ROOT+"ExtractorHandler");
    }
    GendustryGeneticsRecipes(TemplateRecipeHandler h) {
        if (!supports(h)) throw fault("Unadapted Gendustry genetics handler");
        handler=h; kind=h.getClass().getSimpleName(); recipeClass=ROOT + kind + "$" + kind.substring(0, kind.length() - "Handler".length()) + "Recipe"; h.arecipes.clear();
        invoke(h, "addAllRecipes");
        size=h.arecipes.size(); if(size<=0||size>65536) throw fault("Invalid Gendustry genetics registry size");
    }
    public int size(){return size;}
    public boolean capture(int index, RecipeRow row){
        if(handler.arecipes.size()!=size) throw new Jobs.Fault("environment_changed","Gendustry genetics cache changed");
        Object recipe=handler.arecipes.get(index);
        if (!recipeClass.equals(recipe.getClass().getName()))
            throw fault("Gendustry genetics recipe implementation changed: " + recipe.getClass().getName());
        Object raw=call(recipe,"getIngredients");
        if(!(raw instanceof List) || ((List<?>)raw).isEmpty() || ((List<?>)raw).size()>16) throw fault("Invalid Gendustry ingredient list");
        int slot=0;
        for(Object value:(List<?>)raw){
            if(!(value instanceof PositionedStack)) throw fault("Gendustry ingredient is not a PositionedStack");
            PositionedStack p=(PositionedStack)value; if(p.items==null||p.items.length==0||p.items[0]==null) throw new Jobs.Fault("slot_missing","Gendustry ingredient is empty");
            int amount=p.items[0].stackSize;
            if (amount <= 0) throw new Jobs.Fault("invalid_amount", "Gendustry genetics input has non-positive native amount");
            for (ItemStack choice : p.items) {
                if (choice == null || choice.stackSize != amount)
                    throw new Jobs.Fault("slot_changed", "Gendustry genetics alternatives do not share a native quantity");
            }
            final int inputSlot=slot++;
            row.itemInput(p,inputSlot,amount,false,false,(item,choice)->com.github.dcysteine.nesql.exporter.source.Json.object("kind","exact","meta",false,"nbt",true));
            applyLabwareConsumption(kind, p, row, inputSlot);
        }
        PositionedStack out=(PositionedStack)call(recipe,"getResult");
        if(out!=null&&out.items!=null&&out.items.length>0&&out.items[0]!=null) row.itemOutput(out,0,out.items[0],10000);
        else if(kind.equals("ExtractorHandler")){
            ItemStack input=(ItemStack)call(recipe,"in"); int amount=(Integer)call(recipe,"out"); Fluid dna=FluidRegistry.getFluid("dna");
            if(input==null||amount<=0||dna==null) throw fault("Invalid Gendustry extractor output");
            row.fluidOutput(null,0,new FluidStack(dna,amount));
        } else throw new Jobs.Fault("output_missing","Gendustry recipe has no native result");
        addMachineProperties(row);
        return true;
    }
    private static void applyLabwareConsumption(String kind, PositionedStack display, RecipeRow row, int slot) {
        if (kind.equals("ReplicatorHandler")) return; // Replicator's native recipe has no labware slot.
        boolean labware = (display.relx == 98 && (display.rely == 28 || display.rely == 17)) ||
                (kind.equals("ExtractorHandler") && display.relx == 94 && display.rely == 19);
        if (!labware) return;
        int chance = labwareChance(kind);
        if (chance != 0 && chance != 100)
            throw fault("Gendustry labware consumption probability cannot be represented by the recipe contract: " + chance + "%");
        com.google.gson.JsonArray choices = row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices");
        for (com.google.gson.JsonElement value : choices)
            value.getAsJsonObject().add("consume", com.github.dcysteine.nesql.exporter.source.Json.object("kind", chance == 0 ? "keep" : "consume"));
        row.property("gendustry:labwareConsumption", "Native labware consumption probability (%)", chance);
    }
    private static int labwareChance(String kind) {
        String machine=kind.substring(0,kind.length()-"Handler".length());
        String cls="net.bdew.gendustry.machines."+machine.toLowerCase()+".Machine"+machine;
        Object value=MagicApi.invoke(type(cls),null,"labwareConsumeChance",new Class<?>[0]);
        if (!(value instanceof Number)) throw fault("Invalid Gendustry labware consumption chance: "+machine);
        int chance=((Number)value).intValue();
        if (chance<0 || chance>100) throw fault("Gendustry labware consumption chance outside 0..100: "+chance);
        return chance;
    }
    private void addMachineProperties(RecipeRow row){
        String machine=kind.substring(0,kind.length()-"Handler".length());
        String cls="net.bdew.gendustry.machines."+machine.toLowerCase()+".Machine"+machine;
        try { Class<?> t=type(cls); row.property("gendustry:mjPerItem","Energy per item (MJ)",MagicApi.invoke(t,null,"mjPerItem",new Class<?>[0]));
            if(machine.equals("Mutatron")) row.property("gendustry:mutagenPerItem","Mutagen per item (mB)",MagicApi.invoke(t,null,"mutagenPerItem",new Class<?>[0]));
            if(machine.equals("Replicator")){row.property("gendustry:dnaPerItem","DNA per item (mB)",MagicApi.invoke(t,null,"dnaPerItem",new Class<?>[0])); row.property("gendustry:proteinPerItem","Protein per item (mB)",MagicApi.invoke(t,null,"proteinPerItem",new Class<?>[0]));}
            if(machine.equals("Mutatron")) row.property("gendustry:tankCapacity","Mutagen tank capacity (mB)",MagicApi.invoke(t,null,"tankSize",new Class<?>[0]));
            if(machine.equals("Replicator")){ row.property("gendustry:dnaTankCapacity","DNA tank capacity (mB)",MagicApi.invoke(t,null,"dnaTankSize",new Class<?>[0])); row.property("gendustry:proteinTankCapacity","Protein tank capacity (mB)",MagicApi.invoke(t,null,"proteinTankSize",new Class<?>[0])); }
            if(machine.equals("Extractor")) row.property("gendustry:tankCapacity","DNA tank capacity (mB)",MagicApi.invoke(t,null,"tankSize",new Class<?>[0]));
        } catch(RuntimeException e){ throw fault("Gendustry machine configuration unavailable: "+machine); }
    }
    private static Object call(Object o,String n){return MagicApi.invoke(o.getClass(),o,n,new Class<?>[0]);}
    private static void invoke(Object o,String n){MagicApi.invoke(o.getClass(),o,n,new Class<?>[0]);}
    private static Jobs.Fault fault(String m){return new Jobs.Fault("recipe_unsupported",m);}
}
