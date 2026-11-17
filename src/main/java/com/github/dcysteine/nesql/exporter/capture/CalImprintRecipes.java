package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** GTNH 2.9 enum-backed CAL installation; no numeric item-registry or RecipeMap scan. */
final class CalImprintRecipes implements RegistryRecipes {
    private static final String HANDLER="gregtech.nei.GTNEIImprintHandler",NATIVE="gregtech.common.recipes.CALImprintRecipe";
    private static final String ENUM="bartworks.API.enums.CircuitImprint",PARTS="bartworks.system.material.CircuitGeneration.CircuitPartsItem";
    private Source source;
    private ItemStack machine;
    private JsonArray ids;
    private String machineSignature;
    private final List<Entry> definitions=new ArrayList<>(),active=new ArrayList<>();
    private final Map<Integer,Object> lookup=new HashMap<>();
    private final Map<String,Object> legacy=new HashMap<>();
    private boolean closed;

    static boolean supports(IRecipeHandler h){return h.getClass().getName().equals(HANDLER);}
    static Source registrationSource(IRecipeHandler handler){
        if(!supports(handler))throw fault("Unknown CAL imprint handler");
        MagicApi.version("CalImprintRecipes","gregtech");MagicApi.version("CalImprintRecipes","gregtech_nh");
        MagicApi.version("GtItemListBinding","gregtech_nh");
        return source();
    }
    private static Source source(){
        Class<?> owner=type(ENUM);
        Object values=field(owner,null,"VALUES"),ids=field(owner,null,"IMPRINT_LOOKUPS_BY_IDS"),
            names=field(owner,null,"IMPRINT_LOOKUPS_BY_UNLOCALISED_NAMES"),
            machine=field(type("bartworks.common.loaders.ItemRegistry"),null,"cal"),
            entities=field(type("gregtech.api.GregTechAPI"),null,"METATILEENTITIES"),
            parts=field(type(PARTS),null,"INSTANCE");
        if(values==null||ids==null||names==null||machine==null||entities==null||parts==null)
            throw new Jobs.Fault("handler_not_ready","CAL enum definitions are unavailable");
        if(values.getClass().getComponentType()!=owner||((Object[])values).length>1000
                ||ids.getClass()!=HashMap.class||names.getClass()!=HashMap.class
                ||((Map<?,?>)ids).size()>1000||((Map<?,?>)names).size()>1000
                ||!(machine instanceof ItemStack)||parts.getClass()!=type(PARTS))
            throw fault("Unadapted CAL enum/lookup storage");
        ItemStack definition=valid((ItemStack)machine);int meta=definition.getItemDamage();
        if(!entities.getClass().isArray()||meta<0||meta>=java.lang.reflect.Array.getLength(entities))
            throw fault("Invalid CAL native machine index");
        Object entity=java.lang.reflect.Array.get(entities,meta);
        if(entity==null||entity.getClass()!=type("bartworks.common.tileentities.multis.MTECircuitAssemblyLine"))
            throw fault("Unadapted CAL machine entity");
        return new Source((Object[])values,(Map<?,?>)ids,(Map<?,?>)names,(ItemStack)machine,entities,entity,(Item)parts);
    }
    static final class Source{
        final Object[] values;final Map<?,?> ids,names;final ItemStack machine;final Object entities,machineEntity;final Item parts;
        Source(Object[] values,Map<?,?> ids,Map<?,?> names,ItemStack machine,Object entities,Object machineEntity,Item parts){
            this.values=values;this.ids=ids;this.names=names;this.machine=machine;this.entities=entities;this.machineEntity=machineEntity;this.parts=parts;
        }
        boolean same(Source other){return values==other.values&&ids==other.ids&&names==other.names&&machine==other.machine
            &&entities==other.entities&&machineEntity==other.machineEntity&&parts==other.parts;}
    }
    CalImprintRecipes(TemplateRecipeHandler handler){this(registrationSource(handler));}

    private CalImprintRecipes(Source source){
        this.source=source;
        try{
            machine=valid(source.machine);machine.stackSize=1;
            if(!(Boolean)call("isCAL",machine.copy())||machine.hasTagCompound()&&machine.getTagCompound().hasKey("Type")
                    ||call("getCircuitImprintFromCAL",machine.copy())!=null)
                throw fault("CAL display definition is imprinted or needs legacy lookup");
            machineSignature=CanonicalJson.digest(raw(source.machine));
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
            for(int at=0;at<source.values.length;at++){
                Jobs.checkpoint();Entry entry=new Entry(at,source.values[at]);
                if(!seen.add(entry.value))throw fault("Duplicate CAL enum definition");
                definitions.add(entry);if(entry.loaded)active.add(entry);
            }
            for(Map.Entry<?,?> entry:source.ids.entrySet()){
                if(!(entry.getKey() instanceof Integer)||!seen.contains(entry.getValue()))throw fault("Invalid CAL enum lookup");
                lookup.put((Integer)entry.getKey(),entry.getValue());
            }
            for(Map.Entry<?,?> entry:source.names.entrySet()){
                if(!(entry.getKey() instanceof String)||((String)entry.getKey()).length()>65535||!seen.contains(entry.getValue()))
                    throw fault("Invalid legacy CAL name lookup");
                legacy.put((String)entry.getKey(),entry.getValue());
            }
            if(lookup.size()!=active.size())throw fault("CAL loaded enum and ID lookup disagree");
            ids=array();for(int id:new TreeSet<>(lookup.keySet()))ids.add(value(id));
            finishVerification();
        }catch(RuntimeException|Error failure){close();throw failure;}
    }
    public int size(){verify();return active.size();}
    public void verify(){
        if(closed)throw new Jobs.Fault("capture_closed","CAL imprint reader is closed");
        Source current;
        try{current=source();}catch(RuntimeException failure){Jobs.Fault drift=changed();drift.initCause(failure);throw drift;}
        if(!source.same(current)||source.values.length!=definitions.size()||source.ids.size()!=lookup.size()
                ||source.names.size()!=legacy.size()||source.machine.getItem()!=machine.getItem()
                ||!machineSignature.equals(CanonicalJson.digest(raw(source.machine))))throw changed();
    }
    public void finishVerification(){
        verify();
        for(Entry entry:definitions)entry.verify();
        for(Map.Entry<Integer,Object> entry:lookup.entrySet())if(source.ids.get(entry.getKey())!=entry.getValue())throw changed();
        for(Map.Entry<String,Object> entry:legacy.entrySet())if(source.names.get(entry.getKey())!=entry.getValue())throw changed();
    }
    public boolean capture(int index,RecipeRow row){
        verify();Entry entry=active.get(index);entry.verify();
        try{
            captureRow(entry,row);
        }catch(RuntimeException|Error failure){
            try{verify();entry.verify();}catch(RuntimeException|Error drift){if(drift!=failure)failure.addSuppressed(drift);}throw failure;
        }
        verify();entry.verify();return true;
    }
    private void captureRow(Entry entry,RecipeRow row){
        ItemStack imprint=entry.imprint.copy(),output=valid((ItemStack)invoke(type(NATIVE),null,"installImprint",
            new Class<?>[]{ItemStack.class,type(ENUM)},machine.copy(),entry.value));
        for(int slot=0;slot<2;slot++)row.itemInput(slot,Collections.singletonList(new RecipeRow.Ingredient(
            (slot==0?machine:imprint).copy(),1,false,object("kind","calInput","role",slot==0?"machine":"imprint","imprintIds",ids))),false);
        row.itemOutput(0,output,10000);
        row.outputs.get(0).getAsJsonObject().add("change",object("input",0,"action",object("kind","calImprint","imprint",1),
            "samples",array(object("id",row.facts.item(output),"amount","1")),"bindings",array(array(0,0))));
        row.record.add("process",object("kind","calImprint","nativeRecipe",NATIVE,"machine",0,"imprint",1,
            "occupiedLimit",2,"tag","id_imprint","imprintIds",ids));
    }
    public void close(){
        if(closed)return;closed=true;definitions.clear();active.clear();lookup.clear();legacy.clear();
        source=null;machine=null;ids=null;machineSignature=null;
    }
    private final class Entry{
        final int index,id;final Object value,mod,holder;final boolean loaded;
        final GtItemListBinding binding;final ItemStack imprint;
        Entry(int index,Object value){
            this.index=index;this.value=value;
            if(!(value instanceof Enum<?>)||((Enum<?>)value).getDeclaringClass()!=type(ENUM))throw fault("Unadapted CAL enum entry");
            id=((Number)field(value,"id")).intValue();mod=field(value,"sourceMod");holder=field(value,"imprint");
            if(id<0||id>=1000||!(mod instanceof Enum<?>)||((Enum<?>)mod).getDeclaringClass()!=type("gregtech.api.enums.Mods"))
                throw fault("Invalid CAL enum ID or mod");
            if(!(Boolean)field(mod,"checked"))throw new Jobs.Fault("handler_not_ready","CAL source mod state is unchecked");
            loaded=(Boolean)field(mod,"modLoaded");
            binding=loaded?GtItemListBinding.calImprint(holder):null;
            imprint=loaded?valid(binding.result()):null;
            if(loaded&&(imprint.getItem()!=source.parts||imprint.getItemDamage()!=id
                    ||invoke(type(ENUM),null,"findCircuitImprintByImprintStack",new Class<?>[]{ItemStack.class},imprint.copy())!=value))
                throw fault("CAL ItemList imprint differs from its native enum lookup");
        }
        void verify(){
            Jobs.checkpoint();
            if(source.values[index]!=value||((Number)field(value,"id")).intValue()!=id||field(value,"sourceMod")!=mod
                    ||field(value,"imprint")!=holder||!(Boolean)field(mod,"checked")||(Boolean)field(mod,"modLoaded")!=loaded
                    ||(loaded?source.ids.get(id)!=value:source.ids.containsKey(id)))throw changed();
            if(binding!=null)binding.verify();
        }
    }
    private static Object call(String method,ItemStack stack){return invoke(type(NATIVE),null,method,new Class<?>[]{ItemStack.class},stack);}
    private static JsonObject raw(ItemStack s){ItemStack item=valid(s);return object("registry",Item.itemRegistry.getNameForObject(item.getItem()),"meta",item.getItemDamage(),"nbt",TypedNbt.encode(item.getTagCompound()),"amount",Integer.toString(item.stackSize));}
    private static ItemStack valid(ItemStack s){
        if(s==null||s.stackSize<=0||s.getItem()==null||Item.itemRegistry.getNameForObject(s.getItem())==null)throw fault("Invalid positive registered CAL stack");
        NativeStackAudit.audit(s);return s.copy();
    }
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Native CAL enum, lookup, ItemList or machine definition changed");}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
}
