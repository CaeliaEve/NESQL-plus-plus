package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.recipes.ISqueezerRecipe;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Owned ordinary and fixed-container projections. No dynamic item callbacks are enumerated. */
final class SqueezerEntries {
    private static final String PREFIX="forestry.factory.recipes.";
    private final List<ISqueezerRecipe> recipes=new ArrayList<>();
    private final List<JsonObject> selectors=new ArrayList<>();

    SqueezerEntries(Collection<?> ordinary, Map<?,?> containers, JsonObject context) {
        int index=0;
        for(Object raw:ordinary){
            Jobs.checkpoint();
            if(!raw.getClass().getName().equals(PREFIX+"SqueezerRecipe"))throw fault("Unknown ordinary squeezer recipe");
            add((ISqueezerRecipe)raw,object("kind","ordinary","index",index++));
        }
        if(!containers.getClass().getName().equals("forestry.core.utils.datastructures.ItemStackMap"))throw fault("Unknown squeezer container lookup");
        List<? extends Map.Entry<?,?>> rules=new ArrayList<>(containers.entrySet());
        Map<String,ItemStack> fixed=new HashMap<>();
        for(FluidContainerRegistry.FluidContainerData data:FluidContainerRegistry.getRegisteredFluidContainerData()){
            if(data.filledContainer.getItem() instanceof IFluidContainerItem)continue;
            ItemStack stack=data.filledContainer;
            fixed.put(key(Item.itemRegistry.getNameForObject(stack.getItem()),Items.feather.getDamage(stack)),stack.copy());
        }
        JsonArray filled=context.getAsJsonArray("filled");
        for(int f=0;f<filled.size();f++){
            Jobs.checkpoint();
            JsonObject spec=filled.get(f).getAsJsonObject().getAsJsonObject("filled");
            ItemStack offered=fixed.get(key(spec.get("registry").getAsString(),spec.get("meta").getAsInt()));
            if(offered==null)throw new Jobs.Fault("recipe_changed","Fixed container disappeared during squeezer capture");
            // The guarded item is not IFluidContainerItem. These audited native
            // helpers therefore take Forge's fixed-table path, never item callbacks.
            ItemStack empty=(ItemStack)invoke(type("forestry.core.fluids.FluidHelper"),null,"getEmptyContainer",new Class<?>[]{ItemStack.class},offered.copy());
            if(empty==null)continue;
            for(int r=0;r<rules.size();r++){
                Map.Entry<?,?> entry=rules.get(r);
                if(!(Boolean)invoke(containers.getClass(),containers,"areEqual",new Class<?>[]{ItemStack.class,Object.class},empty,entry.getKey()))continue;
                Object nativeRule=entry.getValue();
                if(!nativeRule.getClass().getName().equals(PREFIX+"SqueezerContainerRecipe"))throw fault("Unknown fixed-container recipe");
                ISqueezerRecipe recipe=(ISqueezerRecipe)invoke(nativeRule.getClass(),nativeRule,"getSqueezerRecipe",new Class<?>[]{ItemStack.class},offered.copy());
                if(recipe!=null)add(recipe,object("kind","container","container",r,"filled",f));
                break;
            }
        }
    }

    int size(){return recipes.size();}
    ISqueezerRecipe recipe(int index){return copy(recipes.get(index));}
    JsonObject selector(int index){return new JsonParser().parse(selectors.get(index).toString()).getAsJsonObject();}

    TemplateRecipeHandler.CachedRecipe cached(TemplateRecipeHandler handler,int index){
        if(!handler.getClass().getName().equals(PREFIX+"nei.NEIHandlerSqueezer"))throw fault("Wrong squeezer handler");
        try{
            return (TemplateRecipeHandler.CachedRecipe)type(handler.getClass().getName()+"$CachedSqueezerRecipe")
                .getConstructor(handler.getClass(),ISqueezerRecipe.class,boolean.class).newInstance(handler,recipe(index),true);
        }catch(ReflectiveOperationException error){Jobs.Fault failure=fault("Cannot construct owned native squeezer layout");failure.initCause(error);throw failure;}
    }

    private void add(ISqueezerRecipe recipe,JsonObject selector){
        if(recipes.size()>=327680)throw fault("Squeezer cursor exceeds ordinary/fixed registry budget");
        recipes.add(copy(recipe));selectors.add(selector);
    }
    private static ISqueezerRecipe copy(ISqueezerRecipe source){
        ItemStack[] original=source.getResources(),inputs=original==null?null:new ItemStack[original.length];
        if(inputs!=null)for(int i=0;i<inputs.length;i++)inputs[i]=original[i]==null?null:original[i].copy();
        FluidStack output=source.getFluidOutput();ItemStack remnant=source.getRemnants();
        try{
            return (ISqueezerRecipe)type(PREFIX+"SqueezerRecipe").getConstructor(int.class,ItemStack[].class,FluidStack.class,ItemStack.class,float.class)
                .newInstance(source.getProcessingTime(),inputs,output==null?null:output.copy(),remnant==null?null:remnant.copy(),source.getRemnantsChance());
        }catch(ReflectiveOperationException error){Jobs.Fault failure=fault("Cannot copy native squeezer recipe");failure.initCause(error);throw failure;}
    }
    private static String key(String registry,int meta){return registry+"\0"+meta;}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
