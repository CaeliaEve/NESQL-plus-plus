package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** BuildCraft refinery registry, distinct from Galacticraft's tank conversion. */
final class RefiningRecipes implements RegistryRecipes {
    private static final String HANDLER="buildcraft.compat.nei.RecipeHandlerRefinery";
    private static final String MANAGER="buildcraft.core.recipes.RefineryRecipeManager";
    private final Object manager;
    private final Map<?,?> registry;
    private final Supplier<Object> currentManager;
    private final IntSupplier currentCapacity;
    private final List<List<?>> nativeFilling=new ArrayList<>();
    private final List<List<Object>> nativeFillEntries=new ArrayList<>();
    private final List<JsonArray> fillDefinitions=new ArrayList<>();
    private final NativeDefinitions definitions;
    private final List<Entry> recipes=new ArrayList<>();
    private final List<List<FluidStack>> filling=new ArrayList<>();
    private final int capacity;
    private final Map<Integer,JsonObject> exclusions=new HashMap<>();
    private boolean closed;
    static boolean supports(IRecipeHandler handler){return handler.getClass().getName().equals(HANDLER);}
    RefiningRecipes(TemplateRecipeHandler handler){this(handler,registry(),capacity(),RefiningRecipes::currentRegistry,RefiningRecipes::capacity);}
    static Object registrationSource(IRecipeHandler handler){if(!supports(handler))throw fault("Unknown BuildCraft refinery handler");return registry();}
    private static int capacity(){return (Integer)field(type("buildcraft.factory.TileRefinery"),null,"LIQUID_PER_SLOT");}
    private static Object currentRegistry(){
        Object current=field(type("buildcraft.api.recipes.BuildcraftRecipeRegistry"),null,"refinery");
        if(current!=field(type(MANAGER),null,"INSTANCE"))throw changed("machine/API registry binding changed");
        return current;
    }
    private static Object registry(){
        MagicApi.version("RefiningRecipes","BuildCraft|Factory");MagicApi.version("RefiningRecipes","BuildCraft|Compat");
        Object registry=field(type(MANAGER),null,"INSTANCE");
        if(registry==null)throw new Jobs.Fault("handler_not_ready","BuildCraft refinery manager is unavailable");
        if(registry!=field(type("buildcraft.api.recipes.BuildcraftRecipeRegistry"),null,"refinery"))throw fault("BuildCraft refinery registry differs from the machine manager");
        return registry;
    }
    RefiningRecipes(TemplateRecipeHandler handler,Object manager,int capacity){
        this(handler,manager,capacity,()->manager,()->capacity);
    }
    private RefiningRecipes(TemplateRecipeHandler handler,Object manager,int capacity,Supplier<Object> currentManager,IntSupplier currentCapacity){
        if(manager==null)throw new Jobs.Fault("handler_not_ready","BuildCraft refinery manager is unavailable");
        if(!supports(handler)||!manager.getClass().getName().equals(MANAGER)||capacity<=0)throw fault("Unknown refinery manager or capacity");
        this.manager=manager;this.capacity=capacity;this.currentManager=currentManager;this.currentCapacity=currentCapacity;
        Object table=field(manager,"recipes");
        if(table==null)throw new Jobs.Fault("handler_not_ready","BuildCraft refinery table is unavailable");
        if(table.getClass()!=HashMap.class)throw fault("Unknown refinery storage map");
        registry=(Map<?,?>)table;
        if(registry.size()>4096)throw new Jobs.Fault("recipe_limit","Refinery registry budget exceeded");
        // HashMap iteration is the actual native selection order. Do not sort it by name.
        for(Map.Entry<?,?> entry:registry.entrySet()){
            Jobs.checkpoint();if(!(entry.getKey() instanceof String))throw fault("Unknown refinery registry key");
            recipes.add(new Entry((String)entry.getKey(),entry.getValue()));
        }
        for(String key:Arrays.asList("validFluids1","validFluids2")){
            Object allowed=field(manager,key);
            if(allowed==null)throw new Jobs.Fault("handler_not_ready","Refinery fill predicate is unavailable");
            if(allowed.getClass()!=ArrayList.class)throw fault("Unknown refinery fill predicate list");
            List<?> source=(List<?>)allowed;if(source.size()>65536)throw new Jobs.Fault("recipe_limit","Refinery fill predicate budget exceeded");
            nativeFilling.add(source);nativeFillEntries.add(new ArrayList<>(source));fillDefinitions.add(fillDefinition(source));
            List<FluidStack> list=new ArrayList<>();for(Object f:source){Jobs.checkpoint();list.add(fluid(f,false));}filling.add(list);
        }
        definitions=new NativeDefinitions("BuildCraft refinery",recipes,this::currentVisible,raw->definition((Entry)raw));
    }
    public int size(){verify();return recipes.size();}
    public void verify(){if(closed)throw new Jobs.Fault("capture_closed","BuildCraft refinery reader is closed");definitions.verify();}
    private List<?> currentVisible(){
        if(currentManager.get()!=manager||field(manager,"recipes")!=registry||registry.size()!=recipes.size()||currentCapacity.getAsInt()!=capacity)
            throw changed("manager, native map, capacity or count changed");
        for(int index=0;index<2;index++)if(field(manager,index==0?"validFluids1":"validFluids2")!=nativeFilling.get(index)
                ||nativeFilling.get(index).size()!=filling.get(index).size())throw changed("fill predicate list or count changed");
        return recipes;
    }
    public void finishVerification(){
        verify();int index=0;
        for(Map.Entry<?,?> raw:registry.entrySet()){
            Jobs.checkpoint();Entry original=recipes.get(index++);
            if(!original.key.equals(raw.getKey())||original.nativeRecipe!=raw.getValue())throw changed("native selection order or recipe instance changed");
        }
        for(int tank=0;tank<2;tank++){
            List<?> current=nativeFilling.get(tank);List<Object> originals=nativeFillEntries.get(tank);
            for(int at=0;at<current.size();at++){Jobs.checkpoint();if(current.get(at)!=originals.get(at))throw changed("fill template identity/order changed");}
            if(!fillDefinitions.get(tank).equals(fillDefinition(current)))throw changed("fill template facts changed");
        }
        definitions.finishVerification();
    }
    public void close(){if(closed)return;closed=true;recipes.clear();filling.clear();nativeFilling.clear();nativeFillEntries.clear();fillDefinitions.clear();exclusions.clear();definitions.close();}
    public boolean capture(int index,RecipeRow row){
        verify();return definitions.checked(index,()->captureRow(index,row));
    }
    private boolean captureRow(int index,RecipeRow row){
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
            row.fluidInput(slot,entry.inputs.get(slot).copy(),false);
            row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().add("consume",object("kind","allocated"));
        }
        row.fluidOutput(0,entry.output.copy());
        return true;
    }
    public JsonObject exclusion(int index){JsonObject p=exclusions.get(index);return p==null?null:new com.google.gson.JsonParser().parse(p.toString()).getAsJsonObject();}
    private static final class Entry {
        final String key;final Object nativeRecipe;
        final List<FluidStack> inputs=new ArrayList<>();final FluidStack output;final int energy;final long delay;
        Entry(String key,Object recipe){
            this.key=key;nativeRecipe=recipe;
            if(recipe==null||!recipe.getClass().getName().equals("buildcraft.core.recipes.FlexibleRecipe"))throw fault("Unknown refinery recipe can alter native priority");
            if(!nativeList(recipe,"inputItems").isEmpty()||!nativeList(recipe,"inputItemsWithAlternatives").isEmpty())throw fault("Refinery recipe declares unmodeled item requirements");
            List<?> fluids=nativeList(recipe,"inputFluids");
            if(fluids.isEmpty()||fluids.size()>4096)throw fault("Refinery requires a bounded nonempty fluid list");
            for(Object f:fluids)inputs.add(fluid(f,true));
            output=fluid(field(recipe,"output"),false);
            if(output.amount<=0)throw fault("Invalid refinery output amount");
            energy=(Integer)field(recipe,"energyCost");delay=(Long)field(recipe,"craftingTime");
        }
    }
    private JsonObject definition(Entry entry){
        if(registry.get(entry.key)!=entry.nativeRecipe)throw changed("native recipe identity changed");
        Entry current=new Entry(entry.key,entry.nativeRecipe);JsonArray inputs=array();
        for(FluidStack input:current.inputs)inputs.add(fluidFacts(input));
        return object("key",entry.key,"id",field(entry.nativeRecipe,"id"),"inputs",inputs,"output",fluidFacts(current.output),
            "energy",current.energy,"delay",Long.toString(current.delay));
    }
    private static List<?> nativeList(Object owner,String key){
        Object list=field(owner,key);if(list==null||list.getClass()!=ArrayList.class)throw fault("Unknown refinery native list: "+key);
        return (List<?>)list;
    }
    private static JsonArray fillDefinition(List<?> source){JsonArray result=array();for(Object value:source){Jobs.checkpoint();result.add(fluidFacts(fluid(value,false)));}return result;}
    private static JsonObject fluidFacts(FluidStack fluid){return object("fluid",fluid.getFluid().getName(),"amount",Integer.toString(fluid.amount),
        "nbt",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(fluid.tag));}
    private static FluidStack fluid(Object value,boolean input){
        if(value==null||value.getClass()!=FluidStack.class||((FluidStack)value).getFluid()==null)throw fault("Invalid refinery fluid identity");
        FluidStack fluid=(FluidStack)value;
        if(input&&(fluid.amount<=0||fluid.tag!=null))throw fault("Unadapted nonpositive or tagged refinery requirement");
        if(!input&&fluid.tag!=null)throw fault("Tagged refinery state requires native NBT comparison");
        return fluid.copy();
    }
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(String message){return new Jobs.Fault("recipe_changed","BuildCraft refinery "+message);}
}
