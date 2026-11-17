package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned Soul Binder registry. Displayed mobs are examples of native predicates, not their domain. */
final class SoulRecipes implements RegistryRecipes {
    private static final String ROOT="crazypants.enderio.", SOUL=ROOT+"machine.soul.", HANDLER=ROOT+"nei.SoulBinderRecipeHandler";
    private static final Set<String> FIXED=new HashSet<>(Arrays.asList("BasicSoulBinderRecipe","SoulBinderReanimationRecipe","SoulBinderSentientRecipe",
        "SoulBinderEnderCystalRecipe","SoulBinderAttractorCystalRecipe","SoulBinderPrecientCystalRecipe"));
    private Item vessel;
    private int capacity;
    private boolean drains,closed;
    private final List<Entry> entries=new ArrayList<>();
    private List<String> examples=new ArrayList<>();
    private NativeDefinitionGraph definitions;
    private Object[] sourceAnchors;
    private Map<?,?> entityRoot;
    private final Map<Object,Object> entityBindings=new IdentityHashMap<>();
    static boolean supportsClass(String name){return name.equals(HANDLER);}
    static boolean supports(ICraftingHandler h){return supportsClass(h.getClass().getName());}
    SoulRecipes(TemplateRecipeHandler h){
      if(!supports(h))throw fault("Unknown Soul Binder handler");
      try{
        sourceAnchors=registrationAnchors();Snapshot s=snapshot();
        if(s.recipes==null||s.recipes.size()>4096||s.vessel==null||s.vessel.getClass()!=type(ROOT+"item.ItemSoulVessel")
            ||s.capacity<0||s.capacity>Integer.MAX_VALUE/20)throw fault("Invalid Soul Binder registry or unrepresentable XP/liquid capacity");
        vessel=s.vessel;capacity=s.capacity;drains=s.drains;examples=names(s.examples,false);
        entityRoot=net.minecraft.entity.EntityList.classToStringMapping;
        if(entityRoot==null||entityRoot.getClass()!=HashMap.class||entityRoot.size()>65536)throw fault("Unadapted native entity names backing");
        entityBindings.putAll(entityRoot);
        for(Object record:s.recipes){Jobs.checkpoint();entries.add(new Entry(record,s.blacklist));}
        Map<String,String[]> shapes=new HashMap<>();
        String[] base={"energyRequired","uid","xpLevelsRequired","xpRequired","supportedEntities"};
        for(String name:FIXED){String[] fields=base;if(name.equals("BasicSoulBinderRecipe")){fields=Arrays.copyOf(base,base.length+2);fields[base.length]="inputStack";fields[base.length+1]="outputStack";}shapes.put(SOUL+name,fields);}
        shapes.put(SOUL+"SoulBinderSpawnerRecipe",new String[]{"xpLevelsRequired","xpRequired"});
        definitions=new NativeDefinitionGraph(SoulRecipes::definitionRoots,shapes);
      }catch(RuntimeException|Error failure){close();throw failure;}
    }
    private static Snapshot snapshot(){
        MagicApi.version("SoulRecipes","EnderIO");Snapshot s=new Snapshot();Class<?> mod=type(ROOT+"EnderIO");
        s.vessel=(Item)field(mod,null,"itemSoulVessel");
        Map<?,?> records=(Map<?,?>)registrationAnchors()[2];
        s.recipes=new ArrayList<>(records.values());s.blacklist=Collections.emptyList();s.examples=Collections.emptyList();
        Object spawner=s.recipes.stream().filter(r->r!=null&&r.getClass().getName().equals(SOUL+"SoulBinderSpawnerRecipe")).findFirst().orElse(null);
        if(spawner!=null){
            Object block=field(mod,null,"blockPoweredSpawner");
            if(block==null||!block.getClass().getName().equals(ROOT+"machine.spawner.BlockPoweredSpawner"))throw fault("Unknown spawner blacklist provider");
            Object config=field(type(ROOT+"machine.spawner.PoweredSpawnerConfig"),null,"instance");
            s.blacklist=strings(field(config,"blackList"));
            // EntityUtil's pinned predicate, without invoking or instantiating registered entities.
            boolean captures=(Boolean)field(type(ROOT+"config.Config"),null,"soulVesselCapturesBosses");
            List<String> examples=new ArrayList<>();Map<?,?> entities=net.minecraft.entity.EntityList.classToStringMapping;
            if(entities==null||entities.getClass()!=HashMap.class||entities.size()>65536)throw fault("Unadapted native entity names backing");
            for(Map.Entry<?,?> entry:entities.entrySet()){
                if(!(entry.getKey() instanceof Class)||!(entry.getValue() instanceof String))throw fault("Invalid native entity name binding");
                Class<?> entity=(Class<?>)entry.getKey();if(net.minecraft.entity.EntityLiving.class.isAssignableFrom(entity)
                    &&(captures||!net.minecraft.entity.boss.IBossDisplayData.class.isAssignableFrom(entity)))examples.add((String)entry.getValue());
            }s.examples=examples;
        }
        int max=(Integer)field(type(ROOT+"config.Config"),null,"soulBinderMaxXpLevel");
        s.capacity=(Integer)invoke(type(ROOT+"xp.XpUtil"),null,"getExperienceForLevel",new Class<?>[]{int.class},max);
        s.drains=field(mod,null,"fluidXpJuice")!=null;
        return s;
    }
    static Object[] registrationAnchors(){
        Object registry=field(type(ROOT+"machine.MachineRecipeRegistry"),null,"instance");
        if(registry==null||registry.getClass()!=type(ROOT+"machine.MachineRecipeRegistry"))throw new Jobs.Fault("handler_not_ready","Soul recipe manager unavailable");
        Object table=field(registry,"machineRecipes");if(table==null||table.getClass()!=HashMap.class)throw fault("Unadapted Soul machine registry storage");
        Object machine=field(type(ROOT+"ModObject"),null,"blockSoulBinder");
        Object records=((Map<?,?>)table).get(field(machine,"unlocalisedName"));
        if(records==null||records.getClass()!=LinkedHashMap.class||((Map<?,?>)records).size()>4096)throw new Jobs.Fault("handler_not_ready","Soul Binder registration table unavailable");
        return new Object[]{registry,table,records,field(type(ROOT+"EnderIO"),null,"itemSoulVessel")};
    }
    private static Object[] definitionRoots(){
        Object[] roots=registrationAnchors();List<Object> values=new ArrayList<>();values.add(roots[2]);
        Class<?> mod=type(ROOT+"EnderIO"),config=type(ROOT+"config.Config");
        for(String name:new String[]{"itemSoulVessel","itemBrokenSpawner","itemFrankenSkull","itemMaterial","blockPoweredSpawner","fluidXpJuice"})values.add(field(mod,null,name));
        values.add(net.minecraft.init.Items.emerald);
        for(String name:new String[]{"soulBinderMaxXpLevel","soulBinderBrokenSpawnerRF","soulVesselCapturesBosses"})values.add(field(config,null,name));
        Object spawner=field(type(ROOT+"machine.spawner.PoweredSpawnerConfig"),null,"instance");
        if(spawner==null||spawner.getClass()!=type(ROOT+"machine.spawner.PoweredSpawnerConfig"))throw fault("Unadapted Soul spawner config owner");
        values.add(field(spawner,"blackList"));return values.toArray();
    }
    public void verify(){alive();Object[] now=registrationAnchors();for(int i=0;i<now.length;i++)if(now[i]!=sourceAnchors[i])throw changed();definitions.verifyRoots();if(net.minecraft.entity.EntityList.classToStringMapping!=entityRoot||entityRoot.size()!=entityBindings.size())throw changed();}
    public void finishVerification(){verify();definitions.verify();for(Map.Entry<Object,Object> entry:entityBindings.entrySet())if(entityRoot.get(entry.getKey())!=entry.getValue())throw changed();}
    public int size(){alive();return entries.size();}
    public boolean capture(int index,RecipeRow row){
        alive();
        Entry e=entries.get(index);
        List<String> selected=e.spawner?examples:e.names;
        List<ItemStack> displays=new ArrayList<>();
        for(String name:selected){if(e.spawner&&(name==null||e.names.contains(name)))continue;displays.add(vial(name));}
        if(displays.isEmpty()){
            if(!e.spawner)return row.exclude(object("native",HANDLER,"reason","empty_native_soul_allow_set",
                "allowedNames",e.names.size(),"spawner",false));
            throw fault("Soul spawner has no registered display example; predicate cannot be represented as an empty input");
        }
        JsonObject filter=filter(row,e);List<RecipeRow.Ingredient> choices=new ArrayList<>();
        for(ItemStack display:displays)choices.add(new RecipeRow.Ingredient(display,1,false,object("kind","soul","filter",filter)));
        row.itemInput(0,choices,false);
        row.itemInput(1,Collections.singletonList(new RecipeRow.Ingredient(e.input.copy(),1,false,e.materialRule())),false);
        JsonArray earlier=array();
        for(int i=0;i<index;i++){
            Entry prior=entries.get(i);
            earlier.add(object("soul",filter(row,prior),"material",object("id",row.facts.item(prior.input),"rule",prior.materialRule())));
        }
        row.record.add("process",object("kind","soul","energy",e.energy,"levels",e.levels,"experience",e.experience,"capacity",capacity,"drains",drains,"spawner",e.spawner,"earlier",earlier));
        ItemStack empty=new ItemStack(vessel);
        row.itemOutput(0,empty,10000,e.spawner?null:object("kind","soul","nominal","1"));
        if(e.spawner){
            JsonArray samples=array();ItemStack first=null;
            for(ItemStack display:displays){
                ItemStack out=new ItemStack(e.output.getItem());out.setTagCompound(new NBTTagCompound());out.getTagCompound().setString("mobType",display.getTagCompound().getString("id"));
                if(first==null)first=out;samples.add(object("id",row.facts.item(out),"amount","1"));
            }
            row.itemOutput(1,first,10000);
            row.outputs.get(1).getAsJsonObject().add("change",object("input",0,"action",object("kind","soul","base",row.facts.item(e.output)),"samples",samples));
        }else row.itemOutput(1,e.output.copy(),10000,object("kind","soul","nominal",Integer.toString(e.output.stackSize)));
        return true;
    }
    private JsonObject filter(RecipeRow row,Entry e){JsonArray names=array();for(String name:e.names)names.add(value(name));return object("vessel",row.facts.item(new ItemStack(vessel)),"names",names,"exclude",e.spawner);}
    private ItemStack vial(String name){ItemStack stack=new ItemStack(vessel);if(name!=null){stack.setTagCompound(new NBTTagCompound());stack.getTagCompound().setString("id",name);}return stack;}
    private static final class Entry {
        final ItemStack input,output;final int energy,levels,experience;final boolean spawner;final List<String> names;
        Entry(Object raw,List<String> blacklist){
            if(raw==null)throw fault("Null Soul Binder record");String name=raw.getClass().getName();spawner=name.equals(SOUL+"SoulBinderSpawnerRecipe");
            if(!spawner&&(!name.startsWith(SOUL)||!FIXED.contains(name.substring(SOUL.length()))))throw fault("Unknown Soul Binder recipe implementation: "+name);
            if(raw.getClass()!=type(name))throw fault("Soul recipe class identity changed");
            for(String method:new String[]{"getInputStack","getOutputStack","getEnergyRequired","getExperienceLevelsRequired","getExperienceRequired","getSupportedSouls"})admit(raw,method);
            input=copy(call(raw,"getInputStack"));input.stackSize=1;input.setTagCompound(null);
            output=copy(call(raw,"getOutputStack"));if(output.stackSize<=0)throw fault("Nonpositive Soul Binder output");
            // Native isItemEqual uses the item's damage getter; custom getters need their own predicate.
            new EnderInputs.Pattern(input,spawner);
            energy=(Integer)call(raw,"getEnergyRequired");levels=(Integer)call(raw,"getExperienceLevelsRequired");experience=(Integer)call(raw,"getExperienceRequired");
            if(spawner){List<String> denied=new ArrayList<>(blacklist);denied.add(null);names=names(denied,true);input.setItemDamage(0);output.setItemDamage(0);output.setTagCompound(null);output.stackSize=1;}
            else names=names(strings(call(raw,"getSupportedSouls")),true);
        }
        JsonObject materialRule(){return object("kind","wildcard","meta",spawner,"nbt",true);}
    }
    private static ItemStack copy(Object value){if(!(value instanceof ItemStack)||((ItemStack)value).getItem()==null)throw fault("Invalid Soul Binder item");return ((ItemStack)value).copy();}
    private static List<String> strings(Object raw){if(!(raw instanceof Collection))throw fault("Invalid Soul name collection");List<String> result=new ArrayList<>();for(Object name:(Collection<?>)raw){if(name!=null&&!(name instanceof String))throw fault("Invalid Soul name");result.add((String)name);}return result;}
    private static List<String> names(List<String> raw,boolean nullable){
        if(raw==null||raw.size()>65536)throw fault("Oversized Soul name collection");TreeSet<String> ordered=new TreeSet<>(Comparator.nullsFirst(Comparator.naturalOrder()));
        for(String name:raw){if(name==null&&!nullable||name!=null&&name.length()>65535)throw fault("Invalid Soul name");ordered.add(name);}return new ArrayList<>(ordered);
    }
    private static Object call(Object target,String method){return invoke(target.getClass(),target,method,new Class<?>[0]);}
    private static void admit(Object target,String method){try{Class<?> owner=target.getClass().getMethod(method).getDeclaringClass();String name=owner.getName();
        if(owner!=type(name)||!name.equals(SOUL+"AbstractSoulBinderRecipe")&&!name.equals(SOUL+"SoulBinderSpawnerRecipe")&&(!name.startsWith(SOUL)||!FIXED.contains(name.substring(SOUL.length()))))throw fault("Unadapted Soul callback owner: "+name+"#"+method);
      }catch(NoSuchMethodException error){throw fault("Missing Soul callback: "+method);}}
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Soul Binder reader is closed");}
    public void close(){closed=true;if(definitions!=null)definitions.close();entries.clear();examples.clear();entityBindings.clear();entityRoot=null;vessel=null;sourceAnchors=null;}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Soul Binder native registration or entity bindings changed");}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static final class Snapshot {List<?> recipes;Item vessel;List<String> blacklist,examples;int capacity;boolean drains;}
}
