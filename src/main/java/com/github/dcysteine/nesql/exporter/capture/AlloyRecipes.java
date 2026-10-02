package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Registered alloy operations use two distinct native allocation traversals. */
final class AlloyRecipes implements RegistryRecipes {
    private static final String ROOT="crazypants.enderio.machine.", HANDLER="crazypants.enderio.nei.AlloySmelterRecipeHandler";
    private final TemplateRecipeHandler handler;
    private final List<Entry> entries=new ArrayList<>();
    static boolean supports(ICraftingHandler h){return h.getClass().getName().equals(HANDLER);}
    AlloyRecipes(TemplateRecipeHandler h){this(h,registry());}
    private AlloyRecipes(TemplateRecipeHandler h,Object manager){this(h,(List<?>)call(manager,"getRecipes"),(Boolean)field(call(manager,"getVanillaRecipe"),"enabled"));}
    private static Object registry(){
        version("EnderIO","2.9.28");
        Object manager=invoke(type(ROOT+"alloy.AlloyRecipeManager"),null,"getInstance",new Class<?>[0]);
        Object vanilla=call(manager,"getVanillaRecipe");
        if(!vanilla.getClass().getName().equals(ROOT+"alloy.VanillaSmeltingRecipe"))throw fault("Unknown Alloy furnace implementation");
        Object machine=field(type("crazypants.enderio.ModObject"),null,"blockAlloySmelter");
        Object registry=field(type(ROOT+"MachineRecipeRegistry"),null,"instance");
        Map<?,?> registered=(Map<?,?>)((Map<?,?>)field(registry,"machineRecipes")).get(field(machine,"unlocalisedName"));
        if(registered==null)throw fault("Alloy runtime machine registry is absent");
        int wrappers=0;
        for(Object wrapper:registered.values()){
            if(wrapper==vanilla)continue;
            if(!wrapper.getClass().getName().equals(ROOT+"recipe.ManyToOneMachineRecipe")||field(wrapper,"recipeManager")!=manager)
                throw fault("Unknown Alloy runtime selector");
            wrappers++;
        }
        if(wrappers!=1)throw fault("Alloy registry selector is missing or repeated");
        return manager;
    }
    AlloyRecipes(TemplateRecipeHandler h,List<?> source,boolean furnaceEnabled){
        if(!supports(h)||source==null||source.size()>262144)throw fault("Unknown or oversized alloy registry");
        // The pinned GTNH configuration disables this independent, dynamic furnace path.
        if(furnaceEnabled)throw fault("Enabled Alloy furnace mode requires its separate native batch adapter");
        handler=h;
        for(Object record:source){Jobs.checkpoint();entries.add(new Entry(record));}
    }
    public int size(){return entries.size();}
    public boolean capture(int index,RecipeRow row){
        Entry e=entries.get(index);JsonArray slots=array();
        // Each offered stack advances only one positive requirement; the machine has three inputs.
        if(e.inputs.size()>3)return false;
        int[][] positions={{50,13},{75,3},{99,13}};
        for(int i=0;i<e.inputs.size();i++){
            Input in=e.inputs.get(i);if(in.patterns.isEmpty())return false;
            List<RecipeRow.Ingredient> choices=new ArrayList<>();List<ItemStack> display=new ArrayList<>();
            for(EnderInputs.Pattern p:in.patterns){choices.add(new RecipeRow.Ingredient(p.item,in.amount,false,p.rule()));display.add(p.item.copy());}
            // Input.slot identifies the ordered requirement, not a physical machine inventory slot.
            row.itemInput(i<3?new PositionedStack(display,positions[i][0],positions[i][1],false):null,i,choices,false);
            for(com.google.gson.JsonElement c:row.inputs.get(i).getAsJsonObject().getAsJsonArray("choices"))
                c.getAsJsonObject().add("consume",object("kind","allocated"));
            slots.add(value(in.slot));
        }
        row.record.add("process",object("kind","alloy","energy",e.energy,"slots",slots));
        for(int i=0;i<e.outputs.size();i++){
            Output out=e.outputs.get(i);
            row.itemOutput(i==0?new PositionedStack(out.item.copy(),75,42,false):null,i,out.item.copy(),10000,
                object("kind","sharedRoll","nominal",Integer.toString(out.item.stackSize),"threshold",Float.toString(out.chance)));
        }
        handler.arecipes.clear();handler.arecipes.add(handler.new CachedRecipe(){@Override public PositionedStack getResult(){return null;}});
        return true;
    }
    static int[][] progressBars(){return new int[][]{{51,31,166,0,22,13,48,3},{99,31,166,0,22,13,48,3}};}
    void draw(int index){
        handler.drawBackground(0);
        Class<?> power=type("crazypants.enderio.power.PowerDisplayUtil");
        String energy=invoke(power,null,"formatPower",new Class<?>[]{int.class},entries.get(index).energy)+" "+invoke(power,null,"abrevation",new Class<?>[0]);
        codechicken.lib.gui.GuiDraw.drawString(energy,100,52,0x808080,false);
    }
    private static final class Entry {
        final List<Input> inputs=new ArrayList<>();final List<Output> outputs=new ArrayList<>();final int energy;
        Entry(Object record){
            if(record==null||!record.getClass().getName().equals(ROOT+"recipe.BasicManyToOneRecipe"))throw fault("Unknown Alloy recipe implementation");
            Object raw=field(record,"recipe");if(raw==null||!raw.getClass().getName().equals(ROOT+"recipe.Recipe"))throw fault("Unknown Alloy matcher");
            Object[] ins=(Object[])call(record,"getInputs"),outs=(Object[])call(record,"getOutputs");
            if(ins==null||ins.length==0||ins.length>4096||outs==null||outs.length==0||outs.length>128)throw fault("Invalid alloy recipe shape");
            for(Object in:ins)inputs.add(new Input(in));for(Object out:outs)outputs.add(new Output(out));
            energy=(Integer)call(record,"getEnergyRequired");
        }
    }
    private static final class Input {
        final int slot,amount;final List<EnderInputs.Pattern> patterns;
        Input(Object source){
            EnderInputs.check(source);if((Boolean)call(source,"isFluid"))throw fault("Alloy machine has no input fluid tank");
            ItemStack item=(ItemStack)call(source,"getInput");slot=(Integer)call(source,"getSlotNumber");
            if(item==null||item.stackSize<=0||slot< -1||slot>2)throw fault("Unsupported native Alloy requirement");
            amount=item.stackSize;patterns=EnderInputs.patterns(source);
        }
    }
    private static final class Output {
        final ItemStack item;final float chance;
        Output(Object source){
            if(source==null||!source.getClass().getName().equals(ROOT+"recipe.RecipeOutput")||(Boolean)call(source,"isFluid"))throw fault("Unsupported Alloy result");
            ItemStack original=(ItemStack)call(source,"getOutput");chance=(Float)call(source,"getChance");
            if(original==null||original.stackSize<=0||!Float.isFinite(chance)||chance<0||chance>1)throw fault("Invalid Alloy result amount or threshold");
            item=original.copy();
        }
    }
    private static Object call(Object target,String name){return invoke(target.getClass(),target,name,new Class<?>[0]);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
