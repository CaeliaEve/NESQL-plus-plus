package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.Fluid;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Native in-world fluid acquisition. The NEI display item is never a semantic product. */
final class SpaceAcquisitionRecipes implements RegistryRecipes {
    private static final String ROOT="gtnhintergalactic.";
    private final boolean pump;
    private final List<Entry> entries;
    private final Map<?,?> table;
    private final List<Parent> parents=new ArrayList<>();
    private final NativeDefinitions definitions;
    private final int changes;
    private boolean closed;
    static boolean supports(IRecipeHandler handler){String c=handler.getClass().getName();return c.equals(ROOT+"nei.SpacePumpModuleRecipeHandler")||c.equals(ROOT+"nei.GasSiphonRecipeHandler");}
    static Map<?,?> registrationSource(IRecipeHandler handler){
        MagicApi.version("SpaceAcquisitionRecipes","gregtech");MagicApi.version("SpaceAcquisitionRecipes","gregtech_nh");
        if(!supports(handler))throw fault("Unknown space acquisition handler");
        return table(handler.getClass().getName().endsWith("SpacePumpModuleRecipeHandler"));
    }
    private static Map<?,?> table(boolean pump){
        Object source=field(type(ROOT+"recipe."+(pump?"SpacePumpingRecipes":"GasSiphonRecipes")),null,"RECIPES");
        if(source==null)throw new Jobs.Fault("handler_not_ready","Native space acquisition table is unavailable");
        if(source.getClass()!=HashMap.class||((Map<?,?>)source).size()>65536)throw fault("Unadapted native space acquisition table");return (Map<?,?>)source;
    }
    SpaceAcquisitionRecipes(TemplateRecipeHandler handler){
        table=registrationSource(handler);pump=handler.getClass().getName().endsWith("SpacePumpModuleRecipeHandler");
        changes=changes(table);entries=read();definitions=new NativeDefinitions("Space acquisition",entries,this::current,this::definition);verify();
    }
    public int size(){verify();return entries.size();}
    private List<?> current(){
        if(table(pump)!=table||changes(table)!=changes||table.size()!=(pump?entries.size():parents.size()))throw changed("root table or count changed");return entries;
    }
    public void verify(){
        if(closed)throw new Jobs.Fault("capture_closed","Space acquisition reader is closed");
        definitions.verify();for(Parent parent:parents)parent.verify(table);
    }
    public void finishVerification(){
        verify();IdentityHashMap<Object,Boolean> keys=new IdentityHashMap<>();
        for(Entry entry:entries)keys.put(entry.key,Boolean.TRUE);
        if(pump){for(Object key:table.keySet())if(!keys.containsKey(key))throw changed("native pump key instance changed");}
        else{
            IdentityHashMap<Object,Boolean> planets=new IdentityHashMap<>();
            for(Parent parent:parents){planets.put(parent.key,Boolean.TRUE);for(Object key:parent.table.keySet())if(!parent.keys.containsKey(key))throw changed("native siphon key instance changed");}
            for(Object key:table.keySet())if(!planets.containsKey(key))throw changed("native planet key instance changed");
        }
        definitions.finishVerification();
    }
    public void close(){if(closed)return;closed=true;definitions.close();entries.clear();parents.clear();}
    public boolean capture(int index,RecipeRow row){verify();return definitions.checked(index,()->captureRow(index,row));}
    private boolean captureRow(int index,RecipeRow row){
        Entry entry=entries.get(index);
        row.fluidOutput(0,entry.fluid.copy(),object("kind","spaceAcquisition","nominal",Integer.toString(entry.fluid.amount)));
        JsonObject process=object("kind","spaceAcquisition","operation",pump?"pump":"siphon",
            "planet",entry.planet,"selector",entry.selector,"fluid",row.facts.fluid(entry.fluid),"baseAmount",Integer.toString(entry.fluid.amount),
            "toolDebit","none","durationLaw",pump?"20TimesMaximumClampedBatch":"20Ticks",
            "environmentLaw",pump?"connectedElevatorAndValidModuleStructure":"orbitProviderAndValidSiphonStructure",
            "powerLaw",pump?"maximumModuleCapacityEnergyBufferGateAndSignedIntEUt":"orbitTierDepthSignedIntLogFourOverclock",
            "outputLaw",pump?"firstMatchingLockedFilledHatchUnlessMEOrSafeVoid":"baseFluidSpaceBeforeOverclock",
            "selectionLaw",pump?"moduleSettingsPlanetGas":"lastCircuitControllerThenFirstValidBus");
        if(pump){JsonArray modules=array();
            modules.add(object("tier",1,"voltageTier",9,"motorTier",2,"parallels",4,"slots",1));
            modules.add(object("tier",2,"voltageTier",10,"motorTier",3,"parallels",4,"slots",4));
            modules.add(object("tier",3,"voltageTier",13,"motorTier",4,"parallels",64,"slots",4));
            process.add("modules",modules);process.addProperty("energyPerParallel","1966080");process.addProperty("batchLimit",128);
        }else{process.add("modules",array());process.add("energyPerParallel",JsonNull.INSTANCE);process.add("batchLimit",JsonNull.INSTANCE);
            process.addProperty("pipeLaw","signedIntSumAtLeastDepthTimes64");process.addProperty("circuitItemRole","gregtech.api.enums.ItemList.Circuit_Integrated");
            process.addProperty("pipeItemRole","IC2:miningPipe");}
        row.record.add("process",process);
        return true;
    }
    private List<Entry> read(){
        List<Entry> result=new ArrayList<>();
        for(Map.Entry<?,?> entry:table.entrySet()){
            Jobs.checkpoint();
            if(pump){
                Object key=entry.getKey();int[] values=pair(key);result.add(new Entry(Integer.toString(values[0]),values[1],key,entry.getValue(),null));
            }else{
                if(!(entry.getKey() instanceof String)||((String)entry.getKey()).isEmpty()||entry.getValue()==null||entry.getValue().getClass()!=HashMap.class)throw fault("Invalid native siphon planet table");
                Map<?,?> depths=(Map<?,?>)entry.getValue();if(depths.size()>65536)throw fault("Native siphon depth count exceeds budget");
                Parent parent=new Parent(entry.getKey(),depths);parents.add(parent);
                for(Map.Entry<?,?> depth:depths.entrySet()){
                    Jobs.checkpoint();if(!(depth.getKey() instanceof Integer))throw fault("Invalid native siphon depth");
                    parent.keys.put(depth.getKey(),Boolean.TRUE);
                    result.add(new Entry((String)entry.getKey(),(Integer)depth.getKey(),depth.getKey(),depth.getValue(),parent));
                    if(result.size()>65536)throw fault("Space acquisition entry budget exceeded");
                }
            }
        }
        result.sort(Comparator.comparing((Entry e)->e.planet).thenComparingInt(e->e.selector));return result;
    }
    private JsonObject definition(Object raw){
        Entry entry=(Entry)raw;Map<?,?> owner=entry.parent==null?table:entry.parent.table;
        if(entry.parent!=null)entry.parent.verify(table);
        if(entry.parent==null){int[] selectors=pair(entry.key);if(!entry.planet.equals(Integer.toString(selectors[0]))||entry.selector!=selectors[1])throw changed("native pump selector changed");}
        if(owner.get(entry.key)!=entry.raw)throw changed("native fluid stack instance changed");
        FluidStack value=fluid(entry.raw);
        if(value.getFluid()!=entry.nativeFluid)throw changed("native fluid registry identity changed");
        return object("planet",entry.planet,"selector",entry.selector,"registry",value.getFluid().getName(),"amount",value.amount,"nbt",TypedNbt.encode(value.tag));
    }
    private static int[] pair(Object key){
        if(key==null||(key.getClass()!=org.apache.commons.lang3.tuple.ImmutablePair.class&&key.getClass()!=org.apache.commons.lang3.tuple.MutablePair.class))throw fault("Unadapted native pump key callback");
        Object left=field(key,"left"),right=field(key,"right");
        if(!(left instanceof Integer)||!(right instanceof Integer))throw fault("Invalid native pump selectors");return new int[]{(Integer)left,(Integer)right};
    }
    private static FluidStack fluid(Object raw){
        if(raw==null||raw.getClass()!=FluidStack.class)throw fault("Unadapted native acquisition fluid stack");
        FluidStack fluid=(FluidStack)raw;
        if(fluid.getFluid()==null||fluid.amount<=0)throw fault("Invalid native acquisition fluid");
        try{if(fluid.getFluid().getClass().getMethod("getName").getDeclaringClass()!=Fluid.class)throw fault("Unadapted native fluid name callback");}
        catch(NoSuchMethodException failure){throw fault("Missing native fluid name callback");}
        return fluid;
    }
    private static final class Parent{
        final Object key;final Map<?,?> table;final int count,changes;
        final IdentityHashMap<Object,Boolean> keys=new IdentityHashMap<>();
        Parent(Object key,Map<?,?> table){this.key=key;this.table=table;count=table.size();changes=changes(table);}
        void verify(Map<?,?> root){if(root.get(key)!=table||table.size()!=count||changes(table)!=changes)throw changed("native planet table/count changed");}
    }
    private static int changes(Map<?,?> table){return (Integer)field(HashMap.class,table,"modCount");}
    private static final class Entry{
        final String planet;final int selector;final Object key,raw;final Parent parent;final FluidStack fluid;final Fluid nativeFluid;
        Entry(String planet,int selector,Object key,Object raw,Parent parent){
            this.planet=planet;this.selector=selector;this.key=key;this.raw=raw;this.parent=parent;
            FluidStack value=fluid(raw);nativeFluid=value.getFluid();fluid=value.copy();
        }
    }
    private static Jobs.Fault changed(String message){return new Jobs.Fault("recipe_changed","Space acquisition "+message);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
