package com.github.dcysteine.nesql.exporter.capture;

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
    private final ForestryRecipeBindings bindings;
    private final NativeItemExamples examples=new NativeItemExamples();
    private boolean closed;
    private final SqueezerRules rules;
    private final SqueezerEntries entries;
    private final String program;
    private final List<String> callbacks=new ArrayList<>();
    static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals("forestry.factory.recipes.nei.NEIHandlerSqueezer");}
    SqueezerRecipes(TemplateRecipeHandler handler){this(handler,SqueezerRules.runtime());}
    SqueezerRecipes(TemplateRecipeHandler handler,SqueezerRules rules){
        if(!supports(handler))throw new Jobs.Fault("handler_unsupported","Not a native Forestry squeezer");
        bindings=new ForestryRecipeBindings(handler.getClass());this.rules=rules;entries=rules.entries();
        JsonObject context=rules.context();program=Identity.content("program",object("kind","forestrySqueezer","rules",context));
        for(JsonElement value:context.getAsJsonArray("dynamic")){
            JsonObject callback=value.getAsJsonObject();
            if(callback.get("kind").getAsString().equals("unsupported"))callbacks.add(callback.get("registry").getAsString());
        }
    }
    public int size(){alive();return entries.size()+callbacks.size();}
    public String program(Facts facts){alive();return rules.publish(facts);}
    public void verify(){alive();bindings.verifyRoots();}
    public void finishVerification(){verify();bindings.verify();rules.checkUnchanged();examples.verify();}
    public boolean capture(int index,RecipeRow row){
        if(index<0||index>=size())throw new IndexOutOfBoundsException("Squeezer entry "+index);
        if(index>=entries.size())throw new Jobs.Fault("recipe_unsupported","Squeezer dynamic container callback remains unadapted: "+callbacks.get(index-entries.size()));
        ISqueezerRecipe source=entries.recipe(index);
        ItemStack[] inputs=source.getResources();
        for(int slot=0;slot<inputs.length;slot++){
            ItemStack requirement=inputs[slot];if(requirement==null)continue;
            JsonArray choices=new JsonArray();Set<String> seen=new HashSet<>();
            for(ItemStack variant:examples.copies(requirement)){
                if(variant.getItem()!=requirement.getItem()||Items.feather.getDamage(requirement)!=OreDictionary.WILDCARD_VALUE&&Items.feather.getDamage(variant)!=Items.feather.getDamage(requirement))throw new Jobs.Fault("slot_changed","Squeezer display changed native input "+slot);
                ItemStack fact=variant.copy();fact.setTagCompound(requirement.getTagCompound()==null?null:(net.minecraft.nbt.NBTTagCompound)requirement.getTagCompound().copy());
                String id=row.facts.item(fact);
                if(seen.add(id))choices.add(object("id",id,"amount",Integer.toString(requirement.stackSize),"rule",object("kind","forestry"),"consume",object("kind","allocated"),"returns",array()));
            }
            if(choices.size()==0)throw new Jobs.Fault("empty_ingredient","Squeezer display has no native input examples");
            row.inputs.add(object("slot",slot,"kind","item","choices",choices));
        }
        FluidStack fluid=source.getFluidOutput();
        if(fluid!=null)row.fluidOutput(0,fluid,object("kind","squeezer","nominal",Integer.toString(fluid.amount)));
        ItemStack remnant=source.getRemnants();
        if(remnant!=null)row.itemOutput(0,remnant,10000,object("kind","squeezer","nominal",Integer.toString(remnant.stackSize)));
        row.record.add("process",object("kind","forestrySqueezer","program",program,"selector",entries.selector(index),"time",source.getProcessingTime(),"chance",Chance.nextFloat(source.getRemnantsChance(),false)));
        row.note("forestry:squeezer-cycle","forestry.factory.tiles.TileSqueezer#workCycle",row.facts.locale().equals("zh_CN")?"基础工作步数 "+source.getProcessingTime()+"，每步 5 个游戏 tick；难度前能量参数 "+(source.getProcessingTime()*200)+" RF。产物依原生库存分配、流体空间及残余物状态完成，非保证批次产量。":"Base work steps: "+source.getProcessingTime()+"; 5 game ticks per step; energy parameter before difficulty: "+(source.getProcessingTime()*200)+" RF. Native stock allocation, fluid space and remnant state govern completion; a preview is not a guaranteed batch yield.");
        return true;
    }
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Squeezer reader is closed");}
    public void close(){if(closed)return;closed=true;entries.close();examples.close();bindings.close();callbacks.clear();}
}
