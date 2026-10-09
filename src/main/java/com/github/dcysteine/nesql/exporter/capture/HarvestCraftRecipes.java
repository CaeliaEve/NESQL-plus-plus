package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** HarvestCraft garden and trap drops projected from the native NEI cache. */
final class HarvestCraftRecipes implements RegistryRecipes {
    private static final String GARDEN="com.pam.harvestcraft.GardenRecipeHandler";
    private static final String FISH="com.pam.harvestcraft.FishTrapRecipeHandler";
    private static final String ANIMAL="com.pam.harvestcraft.AnimalTrapRecipeHandler";
    private final TemplateRecipeHandler handler;
    private final int size;
    static boolean supports(ICraftingHandler value){ return value!=null && supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name){ return GARDEN.equals(name)||FISH.equals(name)||ANIMAL.equals(name); }
    HarvestCraftRecipes(TemplateRecipeHandler value){
        if(!supports(value)) throw fault("Unadapted HarvestCraft handler");
        handler=value; handler.arecipes.clear();
        String overlay=FISH.equals(value.getClass().getName())?"harvestcraftFishTrapDrops":ANIMAL.equals(value.getClass().getName())?"harvestcraftAnimalTrapDrops":"harvestcraftGardenDrops";
        value.loadCraftingRecipes(overlay);
        size=value.arecipes.size(); if(size<=0||size>262144) throw fault("Invalid HarvestCraft recipe registry size");
    }
    public int size(){return size;}
    public boolean capture(int index, RecipeRow row){
        if(handler.arecipes.size()!=size) throw new Jobs.Fault("environment_changed","HarvestCraft NEI cache changed");
        TemplateRecipeHandler.CachedRecipe cached=handler.arecipes.get(index);
        PositionedStack input=handler.getIngredientStacks(index).isEmpty()?null:handler.getIngredientStacks(index).get(0);
        if(input==null||input.items==null||input.items.length==0||input.items[0]==null) throw new Jobs.Fault("slot_missing","HarvestCraft recipe has no input");
        row.itemInput(input,0,Math.max(1,input.items[0].stackSize),false,false,object("kind","exact","meta",false,"nbt",true));
        PositionedStack result=handler.getResultStack(index);
        if(result!=null&&result.items!=null&&result.items.length>0&&result.items[0]!=null) row.itemOutput(result,0,result.items[0],10000);
        List<PositionedStack> others=handler.getOtherStacks(index);
        if(others!=null){
            int slot=result!=null?1:0;
            int outputIndex=0;
            for(PositionedStack out:others){
                if(out==null||out.items==null||out.items.length==0||out.items[0]==null)
                    throw new Jobs.Fault("slot_missing","HarvestCraft recipe has an empty output at index="+outputIndex);
                int chance=isTrap()?trapChance(cached,outputIndex):10000;
                row.itemOutput(out,slot++,out.items[0],chance);
                outputIndex++;
            }
        }
        if(row.outputs.size()==0) throw new Jobs.Fault("output_missing","HarvestCraft recipe has no result");
        if(GARDEN.equals(handler.getClass().getName())){
            Object key=invoke(cached.getClass(),cached,"getBiomeTransKey",new Class<?>[0]);
            if(!(key instanceof String)||((String)key).isEmpty())
                throw new Jobs.Fault("recipe_unsupported","HarvestCraft garden recipe has no biome translation key");
            row.property("harvestcraft:biome","Native biome drop key",key);
        }
        return true;
    }
    private boolean isTrap(){return FISH.equals(handler.getClass().getName())||ANIMAL.equals(handler.getClass().getName());}
    private static int trapChance(Object cached,int index){
        try {
            Object raw=field(cached,"chances");
            if(!(raw instanceof List)||index<0||index>=((List<?>)raw).size())
                throw fault("HarvestCraft trap chance list does not match output list");
            Object value=((List<?>)raw).get(index);
            if(!(value instanceof Number)) throw fault("Invalid HarvestCraft trap chance");
            double percent=((Number)value).doubleValue();
            if(!Double.isFinite(percent)||percent<0||percent>100) throw fault("Invalid HarvestCraft trap chance percent");
            return (int)Math.round(percent*100d);
        } catch(RuntimeException e){ if(e instanceof Jobs.Fault) throw e; throw fault("HarvestCraft trap chance unavailable"); }
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
