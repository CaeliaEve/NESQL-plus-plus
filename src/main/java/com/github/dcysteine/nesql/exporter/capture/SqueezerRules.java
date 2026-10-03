package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import forestry.api.recipes.ISqueezerRecipe;
import forestry.api.recipes.RecipeManagers;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.*;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Owned native priority/stock rules. The recipe cursor must check drift before each batch. */
final class SqueezerRules {
    private static final String PREFIX="forestry.factory.recipes.";
    private final Collection<?> recipes;
    private final Map<?,?> containers;
    private final JsonObject context;
    private final String fingerprint;

    static SqueezerRules runtime(){
        version("Forestry","4.10.17");
        if(RecipeManagers.squeezerManager==null||!RecipeManagers.squeezerManager.getClass().getName().equals(PREFIX+"SqueezerRecipeManager"))throw fault("Unadapted squeezer manager");
        return new SqueezerRules(RecipeManagers.squeezerManager.recipes(),(Map<?,?>)field(type(PREFIX+"SqueezerRecipeManager"),null,"containerRecipes"));
    }
    SqueezerRules(Collection<?> recipes,Map<?,?> containers){
        this.recipes=recipes;this.containers=containers;
        context=snapshot();fingerprint=CanonicalJson.digest(context);
    }
    JsonObject context(){return new JsonParser().parse(context.toString()).getAsJsonObject();}
    SqueezerEntries entries(){checkUnchanged();SqueezerEntries result=new SqueezerEntries(recipes,containers,context);checkUnchanged();return result;}
    String publish(Facts facts){checkUnchanged();return facts.squeezerProgram(context);}
    void checkUnchanged(){
        if(!fingerprint.equals(CanonicalJson.digest(snapshot())))throw new Jobs.Fault("recipe_changed","Squeezer rules or fluid/ore registry changed during capture");
    }
    private JsonObject snapshot(){
        if(recipes.size()>262144||containers.size()>4096)throw fault("Squeezer registry exceeds its budget");
        JsonArray ordinary=new JsonArray(),rules=new JsonArray(),filled=new JsonArray(),dynamic=new JsonArray();
        for(Object raw:recipes){Jobs.checkpoint();ordinary.add(recipe(raw));}
        for(Map.Entry<?,?> entry:containers.entrySet()){
            Jobs.checkpoint();Object raw=entry.getValue();exact(raw,"SqueezerContainerRecipe");
            Object key=entry.getKey();JsonObject predicate;
            if(key instanceof ItemStack)predicate=object("kind","stack","stack",stack((ItemStack)key));
            else if(key instanceof Item)predicate=object("kind","item","registry",registered((Item)key));
            else if(key instanceof String){
                List<ItemStack> members=OreDictionary.getOres((String)key);
                if(members.size()>65536)throw fault("Squeezer ore key exceeds its budget");
                JsonArray values=new JsonArray();for(ItemStack member:members)values.add(stack(member));
                predicate=object("kind","ore","members",values);
            }else throw fault("Unadapted squeezer container key");
            rules.add(object("key",predicate,"empty",stack((ItemStack)field(raw,"emptyContainer")),"time",field(raw,"processingTime"),
                "remnant",stack((ItemStack)field(raw,"remnants")),"chance",bits((Float)field(raw,"remnantsChance"))));
        }
        FluidContainerRegistry.FluidContainerData[] data=FluidContainerRegistry.getRegisteredFluidContainerData();
        if(data.length>65536)throw fault("Fixed container registry exceeds its budget");
        TreeMap<String,JsonObject> fixed=new TreeMap<>();
        for(FluidContainerRegistry.FluidContainerData value:data){
            Jobs.checkpoint();
            if(value.filledContainer==null||value.filledContainer.getItem()==null)throw fault("Invalid fixed container item");
            // Forestry dispatches these to arbitrary Java callbacks before Forge's
            // fixed registry. They stay explicit, rather than becoming fixed recipes.
            if(value.filledContainer.getItem() instanceof IFluidContainerItem)continue;
            JsonObject item=stack(value.filledContainer);
            String key=item.get("registry").getAsString()+"\0"+item.get("meta").getAsString();
            JsonObject row=object("filled",item,"empty",stack(value.emptyContainer),"fluid",fluid(value.fluid));
            if(fixed.put(key,row)!=null)throw fault("Duplicate fixed container identity");
        }
        for(JsonObject row:fixed.values())filled.add(row);
        TreeMap<String,Item> callbacks=new TreeMap<>();
        for(Object raw:Item.itemRegistry)if(raw instanceof IFluidContainerItem)callbacks.put(registered((Item)raw),(Item)raw);
        SqueezerCallbacks policy=new SqueezerCallbacks(rules);
        for(Map.Entry<String,Item> callback:callbacks.entrySet())dynamic.add(policy.describe(callback.getKey(),callback.getValue()));
        JsonObject value=object("ordinary",ordinary,"containers",rules,"filled",filled,"dynamic",dynamic);
        if(CanonicalJson.bytes(value).length>16*1024*1024)throw fault("Squeezer rule snapshot exceeds 16 MiB");
        return value;
    }
    static JsonObject recipe(Object raw){
        exact(raw,"SqueezerRecipe");ISqueezerRecipe recipe=(ISqueezerRecipe)raw;
        ItemStack[] inputs=recipe.getResources();
        if(inputs==null||inputs.length>9)throw fault("Squeezer requirements exceed the native layout");
        JsonArray requirements=new JsonArray();for(ItemStack input:inputs)requirements.add(stack(input));
        return object("time",recipe.getProcessingTime(),"requirements",requirements,"fluid",fluid(recipe.getFluidOutput()),
            "remnant",stack(recipe.getRemnants()),"chance",bits(recipe.getRemnantsChance()));
    }
    static JsonObject stack(ItemStack item){
        if(item==null)return null;
        if(item.getClass()!=ItemStack.class||item.getItem()==null)throw fault("Unadapted squeezer stack implementation");
        TreeSet<String> names=new TreeSet<>();for(int id:OreDictionary.getOreIDs(item))names.add(OreDictionary.getOreName(id));
        if(names.size()>4096)throw fault("Squeezer item ore membership exceeds its budget");
        JsonArray ores=new JsonArray();for(String name:names)ores.add(new JsonPrimitive(name));
        return object("registry",registered(item.getItem()),"meta",Items.feather.getDamage(item),"amount",item.stackSize,
            "nbt",TypedNbt.encode(item.getTagCompound()),"ores",ores);
    }
    private static JsonObject fluid(FluidStack value){
        if(value==null)return null;
        if(value.getClass()!=FluidStack.class||value.getFluid()==null)throw fault("Invalid squeezer fluid");
        String name=FluidRegistry.getFluidName(value.getFluid());
        if(name==null||!name.equals(value.getFluid().getName()))throw fault("Unregistered squeezer fluid");
        return object("registry",name,"amount",value.amount,"nbt",TypedNbt.encode(value.tag));
    }
    private static String registered(Item item){
        String name=item==null?null:Item.itemRegistry.getNameForObject(item);
        if(name==null)throw new Jobs.Fault("unregistered_item","Squeezer rule uses an unregistered item");
        return name;
    }
    private static String bits(float value){return String.format(Locale.ROOT,"%08x",Float.floatToRawIntBits(value));}
    private static void exact(Object value,String kind){if(value==null||!value.getClass().getName().equals(PREFIX+kind))throw fault("Unadapted squeezer recipe: "+(value==null?"null":value.getClass().getName()));}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
