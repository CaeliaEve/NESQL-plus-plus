package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Four audited, input-driven native queries. Read-only references, never recipes. */
final class NativeUsageReferences implements NativeReferences {
    static final String AE="appeng.integration.modules.NEIHelpers.NEICellViewHandler";
    static final String FLUID="com.glodblock.github.nei.NEICellViewHandler";
    static final String MANUAL="li.cil.oc.integration.nei.ManualUsageHandler";
    static final String CALLBACKS="li.cil.oc.integration.nei.CallbackDocHandler";
    interface Source {
        Object[] roots();
        /** Structural source identities for discovery; execution admission remains mandatory on open. */
        default Object[] registrationRoots(){return roots();}
        Data read(String locale);
    }
    private final Source source;
    private final String locale;
    private final Object[] roots;
    private final Data data;
    private final String signature;
    private boolean closed;

    static boolean supports(IRecipeHandler handler) {
        return handler!=null&&Arrays.asList(AE,FLUID,MANUAL,CALLBACKS).contains(handler.getClass().getName());
    }
    static Source source(String handler) {
        if(AE.equals(handler)||FLUID.equals(handler))return new NativeCellQueries(handler);
        if(MANUAL.equals(handler)||CALLBACKS.equals(handler))return new NativeOcQueries(handler);
        throw fault("Unknown native usage-only handler");
    }
    static Object[] registrationAnchors(IRecipeHandler handler) {return source(handler.getClass().getName()).registrationRoots();}
    NativeUsageReferences(IRecipeHandler handler,JsonObject origin,String locale) {
        if(!supports(handler)||!handler.getClass().getName().equals(origin.get("handler").getAsString())
                ||!handler.getHandlerId().equals(origin.get("key").getAsString()))throw fault("Native usage origin differs");
        this.locale=locale;source=source(handler.getClass().getName());roots=source.roots();data=source.read(locale);
        data.seal(origin,locale);signature=CanonicalJson.digest(data.records);
    }
    public int size(){alive();return data.records.size();}
    public String domain(){alive();return data.domain;}
    public JsonObject counts(){alive();return copy(data.records.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("counts"));}
    public void verify(){
        alive();Object[] current=source.roots();if(current.length!=roots.length)throw changed();
        for(int i=0;i<roots.length;i++)if(!NativeDefinitionGraph.sameRoot(roots[i],current[i]))throw changed();
    }
    public void finishVerification(){
        verify();Data current=source.read(locale);
        current.seal(data.records.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("source"),locale);
        if(!signature.equals(CanonicalJson.digest(current.records)))throw changed();
    }
    public void capture(int index,Facts facts){
        verify();if(!locale.equals(facts.locale()))throw changed();
        JsonObject record=copy(data.records.get(index).getAsJsonObject());retain(record.getAsJsonObject("data"),facts);facts.row("references",record);
    }
    private void retain(JsonElement value,Facts facts){
        if(value.isJsonObject())for(Map.Entry<String,JsonElement> entry:value.getAsJsonObject().entrySet())retain(entry.getValue(),facts);
        else if(value.isJsonArray())for(JsonElement member:value.getAsJsonArray())retain(member,facts);
        else if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()){
            String id=value.getAsString();
            if(data.items.containsKey(id)&&!facts.item(data.items.get(id).copy()).equals(id))throw changed();
            if(data.fluids.containsKey(id)&&!facts.fluid(data.fluids.get(id).copy()).equals(id))throw changed();
        }
    }
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Native usage query cursor is closed");}
    public void close(){closed=true;data.items.clear();data.fluids.clear();}
    static JsonObject copy(JsonObject value){return new JsonParser().parse(value.toString()).getAsJsonObject();}
    static Jobs.Fault fault(String message){return new Jobs.Fault("reference_unsupported",message);}
    static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Native usage source, input, content or locale changed");}

    static final class Data {
        final String backend,version;
        final JsonArray observations=array(),documents=array(),resources=array(),records=array();
        final Map<String,ItemStack> items=new LinkedHashMap<>();
        final Map<String,FluidStack> fluids=new LinkedHashMap<>();
        final Set<String> queries=new HashSet<>();
        int scanned,unmatched,duplicates;long bytes;String domain;
        Data(String backend,String version){this.backend=backend;this.version=version;}
        String item(ItemStack stack){
            String id=itemId(stack);items.putIfAbsent(id,stack.copy());return id;
        }
        private String itemId(ItemStack stack){
            if(stack==null||stack.getItem()==null||stack.stackSize<=0)throw fault("Invalid native usage item");
            if(stack.getClass()!=ItemStack.class)throw fault("Foreign usage stack callback");
            NativeStackAudit.nbt(stack.getTagCompound());String registry=Item.itemRegistry.getNameForObject(stack.getItem());
            if(registry==null||Item.itemRegistry.getObject(registry)!=stack.getItem())throw fault("Unregistered usage item");
            return Identity.item(registry,Items.feather.getDamage(stack),TypedNbt.encode(stack.getTagCompound()));
        }
        static ItemStack owned(ItemStack stack){
            if(stack==null||stack.getClass()!=ItemStack.class||stack.getItem()==null||stack.stackSize<=0)throw fault("Invalid native usage input");
            NativeStackAudit.nbt(stack.getTagCompound());return stack.copy();
        }
        String fluid(FluidStack stack){
            if(stack==null||stack.getFluid()==null)throw fault("Invalid native usage fluid");
            String id=Identity.fluid(stack.getFluid().getName(),TypedNbt.encode(stack.tag));fluids.putIfAbsent(id,stack.copy());return id;
        }
        boolean query(ItemStack input){if(queries.add(itemId(input)+":"+input.stackSize))return true;duplicates++;return false;}
        void observation(int nativeIndex,ItemStack input,JsonObject result){
            append(observations,object("kind","usageObservation","nativeIndex",nativeIndex,
                    "input",object("item",item(input),"amount",Integer.toString(input.stackSize)),"result",result));
        }
        void append(JsonArray target,JsonObject row){
            Jobs.checkpoint();long size=CanonicalJson.bytes(row).length;
            if(size>1024*1024||(bytes+=size)>64L*1024*1024||target.size()>=262144)throw fault("Native usage data exceeds its budget");target.add(row);
        }
        void seal(JsonObject origin,String locale){
            if(!Arrays.asList("zh_CN","en_US").contains(locale))throw fault("Unsupported native usage locale");
            if(scanned!=queries.size()+duplicates||queries.size()!=observations.size()+unmatched)throw fault("Native usage accounting differs");
            JsonArray all=array();all.add(object("kind","usageDefinition","source",copy(origin),"backend",backend,"nativeVersion",version,
                    "locale",locale,"scope","catalogObservations","ready",true,"consuming",false,
                    "counts",object("scanned",scanned,"queries",queries.size(),"matched",observations.size(),"unmatched",unmatched,
                        "duplicates",duplicates,"documents",documents.size(),"resources",resources.size())));
            for(JsonArray group:Arrays.asList(observations,documents,resources))for(JsonElement row:group)all.add(row);
            domain=Identity.content("reference",object("kind","usageQueries","records",all));
            for(int i=0;i<all.size();i++){
                JsonObject row=object("domain",domain,"order",i,"data",all.get(i));row.addProperty("id",Identity.content("refrow",row));records.add(row);
            }
        }
    }
}
