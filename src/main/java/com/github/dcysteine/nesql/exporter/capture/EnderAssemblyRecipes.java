package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemShears;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** EnderIO many-to-one machines share matching, consumption and output selection. */
final class EnderAssemblyRecipes implements RegistryRecipes {
    private static final String ROOT="crazypants.enderio.machine.", HANDLER="crazypants.enderio.nei.AlloySmelterRecipeHandler", SPLICE="crazypants.enderio.nei.SliceAndSpliceRecipeHandler";
    private final String nativeHandler;
    private final boolean splice;
    private final List<ItemStack> axes=new ArrayList<>(),shears=new ArrayList<>();
    private final List<Entry> entries=new ArrayList<>();
    private final Map<Item,Map<Integer,Float>> experience=new IdentityHashMap<>();
    private final NativeDefinitions definitions;
    private boolean closed;
    static boolean supports(ICraftingHandler h){return h.getClass().getName().equals(HANDLER)||h.getClass().getName().equals(SPLICE);}
    EnderAssemblyRecipes(TemplateRecipeHandler h){this(h,registry(h));}
    private EnderAssemblyRecipes(TemplateRecipeHandler h,Object manager){
        this(h,(List<?>)call(manager,"getRecipes"),h.getClass().getName().equals(HANDLER)&&(Boolean)field(call(manager,"getVanillaRecipe"),"enabled"),
                () -> current(h));
    }
    private static List<?> current(TemplateRecipeHandler handler){
        Object manager=registry(handler);
        if(handler.getClass().getName().equals(HANDLER)&&(Boolean)field(call(manager,"getVanillaRecipe"),"enabled"))
            throw new Jobs.Fault("recipe_changed","Native Alloy furnace mode was enabled during capture");
        return (List<?>)call(manager,"getRecipes");
    }
    private static Object registry(TemplateRecipeHandler h){
        MagicApi.version("EnderAssemblyRecipes","EnderIO");
        boolean splice=h.getClass().getName().equals(SPLICE);
        Object manager=invoke(type(ROOT+(splice?"slicensplice.SliceAndSpliceRecipeManager":"alloy.AlloyRecipeManager")),null,"getInstance",new Class<?>[0]);
        Object vanilla=splice?null:call(manager,"getVanillaRecipe");
        if(!splice&&!vanilla.getClass().getName().equals(ROOT+"alloy.VanillaSmeltingRecipe"))throw fault("Unknown Alloy furnace implementation");
        Object machine=field(type("crazypants.enderio.ModObject"),null,splice?"blockSliceAndSplice":"blockAlloySmelter");
        Object registry=field(type(ROOT+"MachineRecipeRegistry"),null,"instance");
        Map<?,?> registered=(Map<?,?>)((Map<?,?>)field(registry,"machineRecipes")).get(field(machine,"unlocalisedName"));
        if(registered==null)throw fault("EnderIO assembly runtime registry is absent");
        int wrappers=0;
        for(Object wrapper:registered.values()){
            if(vanilla!=null&&wrapper==vanilla)continue;
            if(wrapper==null||!wrapper.getClass().getName().equals(ROOT+"recipe.ManyToOneMachineRecipe")||field(wrapper,"recipeManager")!=manager)
                throw fault("Unknown EnderIO assembly runtime selector");
            wrappers++;
        }
        if(wrappers!=1)throw fault("EnderIO assembly selector is missing or repeated");
        return manager;
    }
    EnderAssemblyRecipes(TemplateRecipeHandler h,List<?> source,boolean furnaceEnabled){
        this(h,source,furnaceEnabled,() -> source);
    }
    private EnderAssemblyRecipes(TemplateRecipeHandler h,List<?> source,boolean furnaceEnabled,java.util.function.Supplier<List<?>> current){
        if(!supports(h)||source==null||source.size()>262144)throw fault("Unknown or oversized alloy registry");
        // The pinned GTNH configuration disables this independent, dynamic furnace path.
        if(furnaceEnabled)throw fault("Enabled Alloy furnace mode requires its separate native batch adapter");
        nativeHandler=h.getClass().getName();splice=nativeHandler.equals(SPLICE);
        definitions=new NativeDefinitions(nativeHandler,source,current,EnderDefinitions::assembly);
        for(Object record:source){Jobs.checkpoint();entries.add(new Entry(record,splice?5:2));}
        // Native getExperianceForOutput scans the first primary result, even for a secondary output.
        for(Entry entry:entries){Output primary=entry.outputs.get(0);
            experience.computeIfAbsent(primary.item.getItem(),ignored->new HashMap<>()).putIfAbsent(primary.item.getItemDamage(),primary.experience);}
        if(splice){
            // Admission is instanceof, not ore tags or the NEI representative iron tools.
            for(Object registered:Item.itemRegistry){
                Jobs.checkpoint();Item item=(Item)registered;
                if(item instanceof ItemAxe)axes.add(new ItemStack(item));
                if(item instanceof ItemShears)shears.add(new ItemStack(item));
            }
            Comparator<ItemStack> order=Comparator.comparing(s->Item.itemRegistry.getNameForObject(s.getItem()));
            axes.sort(order);shears.sort(order);
            if(axes.isEmpty()||shears.isEmpty()||axes.size()>65536||shears.size()>65536)throw fault("Invalid splice tool registry");
        }
    }
    public int size(){verify();return entries.size();}
    public void verify(){if(closed)throw new Jobs.Fault("capture_closed","Native assembly reader is closed");definitions.verify();}
    public void finishVerification(){verify();definitions.finishVerification();if(splice){verifyTools(axes,true);verifyTools(shears,false);}}
    public void close(){if(!closed){entries.clear();experience.clear();axes.clear();shears.clear();definitions.close();closed=true;}}
    public boolean capture(int index,RecipeRow row){
        verify();
        return definitions.checked(index,() -> captureEntry(index,row));
    }
    private boolean captureEntry(int index,RecipeRow row){
        Entry e=entries.get(index);JsonArray slots=array();
        // Each offered stack advances only one positive requirement, bounded by physical inputs.
        if(e.inputs.size()>(splice?6:3))return row.exclude(object("reason","requirements_exceed_native_physical_inputs",
            "handler",nativeHandler,"requirements",e.inputs.size(),"physicalInputs",splice?6:3));
        for(int i=0;i<e.inputs.size();i++){
            Input in=e.inputs.get(i);if(in.patterns.isEmpty())return row.exclude(object("reason","empty_native_required_input_predicate",
                "handler",nativeHandler,"requirement",i,"slot",in.slot));
            List<RecipeRow.Ingredient> choices=new ArrayList<>();
            for(EnderInputs.Pattern p:in.patterns)choices.add(new RecipeRow.Ingredient(p.item,in.amount,false,p.rule()));
            // Input.slot identifies the ordered requirement, not a physical machine inventory slot.
            row.itemInput(i,choices,false);
            for(com.google.gson.JsonElement c:row.inputs.get(i).getAsJsonObject().getAsJsonArray("choices"))
                c.getAsJsonObject().add("consume",object("kind","allocated"));
            slots.add(value(in.slot));
        }
        row.record.add("process",object("kind",splice?"splice":"alloy","energy",e.energy,"slots",slots));
        if(splice){tool(row,6,axes);tool(row,7,shears);}
        List<Float> outputExperience=new ArrayList<>();
        for(int i=0;i<e.outputs.size();i++){
            Output out=e.outputs.get(i);
            row.itemOutput(i,out.item.copy(),10000,
                object("kind","sharedRoll","nominal",Integer.toString(out.item.stackSize),"threshold",Float.toString(out.chance)));
            outputExperience.add(experience.getOrDefault(out.item.getItem(),Collections.emptyMap()).getOrDefault(out.item.getItemDamage(),0f));
        }
        row.property("enderio:experience","Experience lookup per output item",outputExperience);
        return true;
    }
    private static void tool(RecipeRow row,int slot,List<ItemStack> tools){
        List<RecipeRow.Ingredient> choices=new ArrayList<>();
        for(ItemStack tool:tools)choices.add(new RecipeRow.Ingredient(tool.copy(),1,false,object("kind","wildcard","meta",true,"nbt",true)));
        row.itemInput(slot,choices,false);
        for(com.google.gson.JsonElement c:row.inputs.get(row.inputs.size()-1).getAsJsonObject().getAsJsonArray("choices"))c.getAsJsonObject().add("consume",object("kind","wear"));
    }
    private static void verifyTools(List<ItemStack> captured,boolean axe){
        Set<Item> remaining=Collections.newSetFromMap(new IdentityHashMap<Item,Boolean>());
        for(ItemStack stack:captured)remaining.add(stack.getItem());
        for(Object registered:Item.itemRegistry){
            Jobs.checkpoint();Item item=(Item)registered;
            if(axe?item instanceof ItemAxe:item instanceof ItemShears)
                if(!remaining.remove(item))throw new Jobs.Fault("recipe_changed","Native splice tool registry gained or repeated an item");
        }
        if(!remaining.isEmpty())throw new Jobs.Fault("recipe_changed","Native splice tool registry lost an item");
    }
    private static final class Entry {
        final List<Input> inputs=new ArrayList<>();final List<Output> outputs=new ArrayList<>();final int energy;
        Entry(Object record,int maxSlot){
            if(record==null||!record.getClass().getName().equals(ROOT+"recipe.BasicManyToOneRecipe"))throw fault("Unknown Alloy recipe implementation");
            Object raw=field(record,"recipe");if(raw==null||!raw.getClass().getName().equals(ROOT+"recipe.Recipe"))throw fault("Unknown Alloy matcher");
            Object[] ins=(Object[])call(record,"getInputs"),outs=(Object[])call(record,"getOutputs");
            if(ins==null||ins.length==0||ins.length>4096||outs==null||outs.length==0||outs.length>128)throw fault("Invalid alloy recipe shape");
            for(Object in:ins)inputs.add(new Input(in,maxSlot));for(Object out:outs)outputs.add(new Output(out));
            energy=(Integer)call(record,"getEnergyRequired");
        }
    }
    private static final class Input {
        final int slot,amount;final List<EnderInputs.Pattern> patterns;
        Input(Object source,int maxSlot){
            EnderInputs.check(source);if((Boolean)call(source,"isFluid"))throw fault("Alloy machine has no input fluid tank");
            ItemStack item=(ItemStack)call(source,"getInput");slot=(Integer)call(source,"getSlotNumber");
            if(item==null||item.stackSize<=0||slot< -1||slot>maxSlot)throw fault("Unsupported native EnderIO assembly requirement");
            amount=item.stackSize;patterns=EnderInputs.patterns(source);
        }
    }
    private static final class Output {
        final ItemStack item;final float chance,experience;
        Output(Object source){
            if(source==null||!source.getClass().getName().equals(ROOT+"recipe.RecipeOutput")||(Boolean)call(source,"isFluid"))throw fault("Unsupported Alloy result");
            ItemStack original=(ItemStack)call(source,"getOutput");chance=(Float)call(source,"getChance");
            experience=(Float)call(source,"getExperiance");
            if(original==null||original.stackSize<=0||!Float.isFinite(chance)||chance<0||chance>1)throw fault("Invalid Alloy result amount or threshold");
            if(!Float.isFinite(experience))throw fault("Invalid native assembly experience");
            if(ItemCallbacks.method(original.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)
                throw fault("Assembly experience lookup requires a dedicated output metadata getter");
            item=original.copy();
        }
    }
    private static Object call(Object target,String name){return invoke(target.getClass(),target,name,new Class<?>[0]);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
