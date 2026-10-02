package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
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
    private final TemplateRecipeHandler handler;
    private final Item vessel;
    private final int capacity;
    private final boolean drains;
    private final List<Entry> entries=new ArrayList<>();
    private final List<String> examples;
    static boolean supports(ICraftingHandler h){return h.getClass().getName().equals(HANDLER);}
    SoulRecipes(TemplateRecipeHandler h){this(h,snapshot());}
    private SoulRecipes(TemplateRecipeHandler h,Snapshot s){this(h,s.recipes,s.vessel,s.blacklist,s.capacity,s.drains,s.examples);}
    SoulRecipes(TemplateRecipeHandler h,List<?> records,Item vessel,List<String> blacklist,int capacity,boolean drains,List<String> examples){
        if(!supports(h)||records==null||records.size()>4096||vessel==null||!vessel.getClass().getName().equals(ROOT+"item.ItemSoulVessel")
            ||capacity<0||capacity>Integer.MAX_VALUE/20)throw fault("Invalid Soul Binder registry or XP capacity");
        handler=h;this.vessel=vessel;this.capacity=capacity;this.drains=drains;this.examples=names(examples,false);
        for(Object record:records){Jobs.checkpoint();entries.add(new Entry(record,blacklist));}
    }
    private static Snapshot snapshot(){
        version("EnderIO","2.9.28");Snapshot s=new Snapshot();Class<?> mod=type(ROOT+"EnderIO");
        s.vessel=(Item)field(mod,null,"itemSoulVessel");
        Object machine=field(type(ROOT+"ModObject"),null,"blockSoulBinder");
        Object registry=field(type(ROOT+"machine.MachineRecipeRegistry"),null,"instance");
        Map<?,?> records=(Map<?,?>)((Map<?,?>)field(registry,"machineRecipes")).get(field(machine,"unlocalisedName"));
        if(records==null||records.getClass()!=LinkedHashMap.class)throw fault("Missing or unordered Soul Binder registry");
        s.recipes=new ArrayList<>(records.values());s.blacklist=Collections.emptyList();s.examples=Collections.emptyList();
        Object spawner=s.recipes.stream().filter(r->r!=null&&r.getClass().getName().equals(SOUL+"SoulBinderSpawnerRecipe")).findFirst().orElse(null);
        if(spawner!=null){
            Object block=field(mod,null,"blockPoweredSpawner");
            if(block==null||!block.getClass().getName().equals(ROOT+"machine.spawner.BlockPoweredSpawner"))throw fault("Unknown spawner blacklist provider");
            Object config=field(type(ROOT+"machine.spawner.PoweredSpawnerConfig"),null,"instance");
            s.blacklist=strings(field(config,"blackList"));
            // Native NEI examples: EnderCore reads entity classes without instantiating them.
            s.examples=strings(call(spawner,"getSupportedSouls"));
        }
        int max=(Integer)field(type(ROOT+"config.Config"),null,"soulBinderMaxXpLevel");
        s.capacity=Math.min(Integer.MAX_VALUE/20,(Integer)invoke(type(ROOT+"xp.XpUtil"),null,"getExperienceForLevel",new Class<?>[]{int.class},max));
        s.drains=field(mod,null,"fluidXpJuice")!=null;
        return s;
    }
    public int size(){return entries.size();}
    public boolean capture(int index,RecipeRow row){
        Entry e=entries.get(index);
        List<String> selected=e.spawner?examples:e.names;
        List<ItemStack> displays=new ArrayList<>();
        for(String name:selected){if(e.spawner&&(name==null||e.names.contains(name)))continue;displays.add(vial(name));}
        if(displays.isEmpty()){
            if(!e.spawner)return false; // An empty finite allow set cannot match any input.
            throw fault("Soul spawner has no registered display example; predicate cannot be represented as an empty input");
        }
        JsonObject filter=filter(row,e);List<RecipeRow.Ingredient> choices=new ArrayList<>();
        for(ItemStack display:displays)choices.add(new RecipeRow.Ingredient(display,1,false,object("kind","soul","filter",filter)));
        row.itemInput(new PositionedStack(displays,27,23,false),0,choices,false);
        row.itemInput(new PositionedStack(e.input.copy(),48,23,false),1,1,false,false,e.materialRule());
        JsonArray earlier=array();
        for(int i=0;i<index;i++){
            Entry prior=entries.get(i);
            earlier.add(object("soul",filter(row,prior),"material",object("id",row.facts.item(prior.input),"rule",prior.materialRule())));
        }
        row.record.add("process",object("kind","soul","energy",e.energy,"levels",e.levels,"experience",e.experience,"capacity",capacity,"drains",drains,"spawner",e.spawner,"earlier",earlier));
        ItemStack empty=new ItemStack(vessel);
        row.itemOutput(new PositionedStack(empty.copy(),101,23,false),0,empty,10000,e.spawner?null:object("kind","soul","nominal","1"));
        if(e.spawner){
            JsonArray samples=array();ItemStack first=null;
            for(ItemStack display:displays){
                ItemStack out=new ItemStack(e.output.getItem());out.setTagCompound(new NBTTagCompound());out.getTagCompound().setString("mobType",display.getTagCompound().getString("id"));
                if(first==null)first=out;samples.add(object("id",row.facts.item(out),"amount","1"));
            }
            row.itemOutput(new PositionedStack(first.copy(),123,23,false),1,first,10000);
            row.outputs.get(1).getAsJsonObject().add("change",object("input",0,"action",object("kind","soul","base",row.facts.item(e.output)),"samples",samples));
        }else row.itemOutput(new PositionedStack(e.output.copy(),123,23,false),1,e.output.copy(),10000,object("kind","soul","nominal",Integer.toString(e.output.stackSize)));
        handler.arecipes.clear();handler.arecipes.add(handler.new CachedRecipe(){@Override public PositionedStack getResult(){return null;}});
        return true;
    }
    private JsonObject filter(RecipeRow row,Entry e){JsonArray names=array();for(String name:e.names)names.add(value(name));return object("vessel",row.facts.item(new ItemStack(vessel)),"names",names,"exclude",e.spawner);}
    private ItemStack vial(String name){ItemStack stack=new ItemStack(vessel);if(name!=null){stack.setTagCompound(new NBTTagCompound());stack.getTagCompound().setString("id",name);}return stack;}
    int[][] progressBars(){return new int[][]{{70,23,177,14,23,17,160,0}};}
    void draw(int index){
        handler.drawBackground(0);Entry e=entries.get(index);Class<?> power=type(ROOT+"power.PowerDisplayUtil");
        String text=invoke(power,null,"formatPower",new Class<?>[]{int.class},e.energy)+" "+invoke(power,null,"abrevation",new Class<?>[0]);
        codechicken.lib.gui.GuiDraw.drawStringC(text,83,45,0x808080,false);
        if(e.levels>0)codechicken.lib.gui.GuiDraw.drawStringC(net.minecraft.client.resources.I18n.format("container.repair.cost",e.levels),83,55,8453920,false);
    }
    private static final class Entry {
        final ItemStack input,output;final int energy,levels,experience;final boolean spawner;final List<String> names;
        Entry(Object raw,List<String> blacklist){
            if(raw==null)throw fault("Null Soul Binder record");String name=raw.getClass().getName();spawner=name.equals(SOUL+"SoulBinderSpawnerRecipe");
            if(!spawner&&(!name.startsWith(SOUL)||!FIXED.contains(name.substring(SOUL.length()))))throw fault("Unknown Soul Binder recipe implementation: "+name);
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
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static final class Snapshot {List<?> recipes;Item vessel;List<String> blacklist,examples;int capacity;boolean drains;}
}
