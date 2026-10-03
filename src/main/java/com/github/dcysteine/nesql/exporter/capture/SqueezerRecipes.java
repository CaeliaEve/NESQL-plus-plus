package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.Chance;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.recipes.ISqueezerRecipe;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native stock requirements, shared rule identity and conditional completion. */
final class SqueezerRecipes implements RegistryRecipes {
    private final TemplateRecipeHandler handler;
    private final SqueezerRules rules;
    private final SqueezerEntries entries;
    private final String program;
    private final List<String> callbacks=new ArrayList<>();
    private final Map<Integer,JsonArray> progress=new HashMap<>();
    static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals("forestry.factory.recipes.nei.NEIHandlerSqueezer");}
    SqueezerRecipes(TemplateRecipeHandler handler){this(handler,SqueezerRules.runtime());}
    SqueezerRecipes(TemplateRecipeHandler handler,SqueezerRules rules){
        if(!supports(handler))throw new Jobs.Fault("handler_unsupported","Not a native Forestry squeezer");
        this.handler=handler;this.rules=rules;entries=rules.entries();
        JsonObject context=rules.context();program=Identity.content("program",object("kind","forestrySqueezer","rules",context));
        for(JsonElement value:context.getAsJsonArray("dynamic")){
            JsonObject callback=value.getAsJsonObject();
            if(callback.get("kind").getAsString().equals("unsupported"))callbacks.add(callback.get("registry").getAsString());
        }
    }
    public int size(){return entries.size()+callbacks.size();}
    public String program(Facts facts){return rules.publish(facts);}
    public void verify(){rules.checkUnchanged();}
    public boolean capture(int index,RecipeRow row){
        if(index<0||index>=size())throw new IndexOutOfBoundsException("Squeezer entry "+index);
        if(index>=entries.size())throw new Jobs.Fault("recipe_unsupported","Squeezer dynamic container callback remains unadapted: "+callbacks.get(index-entries.size()));
        ISqueezerRecipe source=entries.recipe(index);
        TemplateRecipeHandler.CachedRecipe cached=entries.cached(handler,index);
        @SuppressWarnings("unchecked") List<PositionedStack> positions=(List<PositionedStack>)field(cached,"inputs");
        ItemStack[] inputs=source.getResources();
        int displayed=0;
        for(int slot=0;slot<inputs.length;slot++){
            ItemStack requirement=inputs[slot];if(requirement==null)continue;
            PositionedStack display=positions.get(displayed++);JsonArray choices=new JsonArray();Set<String> seen=new HashSet<>();
            for(ItemStack variant:display.items){
                if(variant.getItem()!=requirement.getItem()||Items.feather.getDamage(requirement)!=OreDictionary.WILDCARD_VALUE&&Items.feather.getDamage(variant)!=Items.feather.getDamage(requirement))throw new Jobs.Fault("slot_changed","Squeezer display changed native input "+slot);
                ItemStack fact=variant.copy();fact.setTagCompound(requirement.getTagCompound()==null?null:(net.minecraft.nbt.NBTTagCompound)requirement.getTagCompound().copy());
                String id=row.facts.item(fact);
                if(seen.add(id))choices.add(object("id",id,"amount",Integer.toString(requirement.stackSize),"rule",object("kind","forestry"),"consume",object("kind","allocated"),"returns",array()));
            }
            if(choices.size()==0)throw new Jobs.Fault("empty_ingredient","Squeezer display has no native input examples");
            row.inputs.add(object("slot",slot,"kind","item","choices",choices));
            row.slot("input","item",slot,display.relx,display.rely);
        }
        FluidStack fluid=source.getFluidOutput();
        if(fluid!=null)row.fluidOutput(null,0,fluid,object("kind","squeezer","nominal",Integer.toString(fluid.amount)));
        ItemStack remnant=source.getRemnants();
        if(remnant!=null)row.itemOutput((PositionedStack)field(cached,"remnants"),0,remnant,10000,object("kind","squeezer","nominal",Integer.toString(remnant.stackSize)));
        row.record.add("process",object("kind","forestrySqueezer","program",program,"selector",entries.selector(index),"time",source.getProcessingTime(),"chance",Chance.nextFloat(source.getRemnantsChance(),false)));
        row.property("forestry:workSteps","Base work steps",source.getProcessingTime());
        row.property("forestry:stepTicks","Game ticks per work step",5);
        row.property("forestry:energyRF","Energy parameter before difficulty (RF)",source.getProcessingTime()*200);
        handler.arecipes.clear();handler.arecipes.add(cached);
        return true;
    }
    JsonArray decorations(Facts facts){
        int time=(Integer)field(handler.arecipes.get(0),"processingTime"),ticks=time*5;
        JsonArray result=progress.get(ticks);
        if(result==null){result=Ui.neiProgress(facts,handler,program,new int[][]{{70,30,176,60,43,18,ticks,0}});progress.put(ticks,result);}
        return result;
    }
    static void draw(TemplateRecipeHandler handler,int index){
        handler.drawBackground(index);
        invoke(type("forestry.core.recipes.nei.RecipeHandlerBase"),handler,"drawFluidTanks",new Class<?>[]{int.class},index);
        invoke(type("forestry.core.recipes.nei.RecipeHandlerBase"),handler,"changeToGuiTexture",new Class<?>[0]);
    }
}
