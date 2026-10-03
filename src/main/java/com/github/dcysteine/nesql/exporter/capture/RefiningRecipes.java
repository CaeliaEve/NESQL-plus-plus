package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Proxy;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** BuildCraft refinery registry, distinct from Galacticraft's tank conversion. */
final class RefiningRecipes implements RegistryRecipes {
    private static final String HANDLER="buildcraft.compat.nei.RecipeHandlerRefinery";
    private static final String MANAGER="buildcraft.core.recipes.RefineryRecipeManager";
    private final TemplateRecipeHandler handler;
    private final List<Entry> recipes=new ArrayList<>();
    private final List<List<FluidStack>> filling=new ArrayList<>();
    private final int capacity;
    private final Map<Integer,JsonObject> exclusions=new HashMap<>();
    static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals(HANDLER);}
    RefiningRecipes(TemplateRecipeHandler handler){this(handler,registry(),(Integer)field(type("buildcraft.factory.TileRefinery"),null,"LIQUID_PER_SLOT"));}
    private static Object registry(){
        version("BuildCraft|Factory","7.1.44");version("BuildCraft|Compat","7.1.18");
        Object registry=field(type(MANAGER),null,"INSTANCE");
        if(registry!=field(type("buildcraft.api.recipes.BuildcraftRecipeRegistry"),null,"refinery"))throw fault("BuildCraft refinery registry differs from the machine manager");
        return registry;
    }
    RefiningRecipes(TemplateRecipeHandler handler,Object manager,int capacity){
        if(!supports(handler)||manager==null||!manager.getClass().getName().equals(MANAGER)||capacity<=0)throw fault("Unknown refinery manager or capacity");
        this.handler=handler;this.capacity=capacity;
        Collection<?> values=(Collection<?>)invoke(manager.getClass(),manager,"getRecipes",new Class<?>[0]);
        if(values.size()>4096)throw fault("Refinery registry budget exceeded");
        // HashMap iteration is the actual native selection order. Do not sort it by name.
        for(Object recipe:values){Jobs.checkpoint();recipes.add(new Entry(recipe));}
        for(String key:Arrays.asList("validFluids1","validFluids2")){
            List<?> allowed=(List<?>)field(manager,key);if(allowed.size()>65536)throw fault("Refinery fill predicate budget exceeded");
            List<FluidStack> list=new ArrayList<>();for(Object f:allowed)list.add(fluid(f,false));filling.add(list);
        }
    }
    public int size(){return recipes.size();}
    public boolean capture(int index,RecipeRow row){
        Entry entry=recipes.get(index);
        if(entry.energy<=0){
            exclusions.put(index,object("reason","native_energy_gate_rejects_nonpositive","nativeIndex",index,"energy",entry.energy,"machine","buildcraft.factory.TileRefinery"));return false;
        }
        JsonArray earlier=new JsonArray(),allowed=new JsonArray();int count=0;
        for(int i=0;i<index;i++){
            JsonArray requirements=new JsonArray();
            for(FluidStack fluid:recipes.get(i).inputs){
                if(++count>65536)throw fault("Refinery priority predicate budget exceeded");
                requirements.add(object("id",row.facts.fluid(fluid),"amount",Integer.toString(fluid.amount)));
            }
            earlier.add(requirements);
        }
        for(List<FluidStack> list:filling){
            Set<String> seen=new LinkedHashSet<>();for(FluidStack fluid:list)seen.add(row.facts.fluid(fluid));
            JsonArray tank=new JsonArray();for(String id:seen)tank.add(new com.google.gson.JsonPrimitive(id));allowed.add(tank);
        }
        row.record.add("process",object("kind","buildcraftRefinery","energy",entry.energy,"delay",Long.toString(entry.delay),"capacity",capacity,"earlier",earlier,"filling",allowed));
        for(int slot=0;slot<entry.inputs.size();slot++){
            row.fluidInput(null,slot,entry.inputs.get(slot).copy(),false);
            row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().add("consume",object("kind","allocated"));
            if(slot<2)row.slot("input","fluid",slot,slot==0?33:121,23);
        }
        row.fluidOutput(null,0,entry.output.copy());row.slot("output","fluid",0,77,23);
        Class<?> api=type("buildcraft.api.recipes.IFlexibleRecipeViewable");
        Object projection=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(p,m,a)->{
            switch(m.getName()){
                case "getInputs":{List<FluidStack> copy=new ArrayList<>();for(FluidStack f:entry.inputs)copy.add(f.copy());return copy;}
                case "getOutput":return entry.output.copy();
                case "getEnergyCost":return entry.energy;
                case "getCraftingTime":return entry.delay;
                default:throw fault("Unexpected refinery display method: "+m.getName());
            }
        });
        TemplateRecipeHandler.CachedRecipe cached=(TemplateRecipeHandler.CachedRecipe)TinkerRecipes.construct(type(HANDLER+"$CachedRefineryRecipe"),new Class<?>[]{handler.getClass(),api},handler,projection);
        handler.arecipes.clear();handler.arecipes.add(cached);return true;
    }
    public JsonObject exclusion(int index){JsonObject p=exclusions.get(index);return p==null?null:new com.google.gson.JsonParser().parse(p.toString()).getAsJsonObject();}
    private static final class Entry {
        final List<FluidStack> inputs=new ArrayList<>();final FluidStack output;final int energy;final long delay;
        Entry(Object recipe){
            if(recipe==null||!recipe.getClass().getName().equals("buildcraft.core.recipes.FlexibleRecipe"))throw fault("Unknown refinery recipe can alter native priority");
            if(!((List<?>)field(recipe,"inputItems")).isEmpty()||!((List<?>)field(recipe,"inputItemsWithAlternatives")).isEmpty())throw fault("Refinery recipe declares unmodeled item requirements");
            List<?> fluids=(List<?>)field(recipe,"inputFluids");
            if(fluids.isEmpty()||fluids.size()>4096)throw fault("Refinery requires a bounded nonempty fluid list");
            for(Object f:fluids)inputs.add(fluid(f,true));
            output=fluid(field(recipe,"output"),false);
            if(output.amount<=0)throw fault("Invalid refinery output amount");
            energy=(Integer)field(recipe,"energyCost");delay=(Long)field(recipe,"craftingTime");
        }
    }
    private static FluidStack fluid(Object value,boolean input){
        if(!(value instanceof FluidStack)||((FluidStack)value).getFluid()==null)throw fault("Invalid refinery fluid identity");
        FluidStack fluid=(FluidStack)value;
        if(input&&(fluid.amount<=0||fluid.tag!=null))throw fault("Unadapted nonpositive or tagged refinery requirement");
        if(!input&&fluid.tag!=null)throw fault("Tagged refinery state requires native NBT comparison");
        return fluid.copy();
    }
    static void draw(TemplateRecipeHandler handler,int index){
        handler.drawBackground(index);
        // Keep the original illustration as native visual evidence. Typed process data
        // separately records per-operation RF rather than adopting the NEI RF/t label.
        handler.drawExtras(index);
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
