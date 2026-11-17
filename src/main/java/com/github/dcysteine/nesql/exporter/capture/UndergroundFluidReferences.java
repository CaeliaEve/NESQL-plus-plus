package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import net.minecraftforge.fluids.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** World-generation configuration and its NEI projection, never consuming recipes or GUI pixels. */
final class UndergroundFluidReferences implements NativeReferences {
    static final String HANDLER="gtneioreplugin.plugin.gregtech5.PluginGT5UndergroundFluid";
    private static final String HELPER="gtneioreplugin.util.GT5UndergroundFluidHelper";
    private final JsonObject source;
    private final JsonArray rows;
    private final Map<String,FluidStack> specimens=new TreeMap<>();
    private final String domain,signature;
    static boolean supports(ICraftingHandler handler){return handler!=null&&HANDLER.equals(handler.getClass().getName());}
    UndergroundFluidReferences(ICraftingHandler handler,JsonObject source){
        if(!supports(handler))throw fault("Unknown GT underground-fluid handler");
        this.source=copy(source);rows=current(specimens);domain=rows.get(0).getAsJsonObject().get("domain").getAsString();signature=CanonicalJson.digest(rows);
    }
    private JsonArray current(Map<String,FluidStack> visuals){
        boolean ready=Loader.instance().hasReachedState(LoaderState.AVAILABLE);
        if(!ready)throw new Jobs.Fault("handler_not_ready","GT ore plugin load-complete initialization is not finished");
        Object proxy=field(type("gregtech.GTMod"),null,"proxy");
        if(proxy==null)throw new Jobs.Fault("handler_not_ready","GT underground-fluid configuration is unavailable");
        Object oil=field(proxy,"mUndergroundOil");
        if(oil==null)throw new Jobs.Fault("handler_not_ready","GT underground-fluid configuration is unavailable");
        Map<?,?> dimensions=map(invoke(oil.getClass(),oil,"getDimensionList",new Class<?>[0]));
        Map<?,?> helper=map(invoke(type(HELPER),null,"getAllEntries",new Class<?>[0]));
        Class<?> names=type("gtneioreplugin.util.DimensionHelper");
        Object trimmed=field(names,null,"DimNameTrimmed"),displayed=field(names,null,"DimNameDisplayed");
        if(!(trimmed instanceof String[])||!(displayed instanceof String[])||((String[])trimmed).length!=((String[])displayed).length)throw fault("Invalid native dimension names");
        Map<String,String> mapped=new TreeMap<>();
        for(Object key:dimensions.keySet()){
            String raw=text(key),label=null;
            try {if(Integer.parseInt(raw)==0)label="Ow";} catch(NumberFormatException nonNumeric){
                switch(raw){case "aCentauriBb":label="CB";break;case "BarnardaC":label="BC";break;case "BarnardaE":label="BE";break;case "BarnardaF":label="BF";break;case "TCetiE":label="TE";break;default:}
                if(label==null)for(int i=0;i<((String[])trimmed).length;i++)if(((String[])trimmed)[i].equalsIgnoreCase(raw)){label=((String[])displayed)[i];break;}
            }
            if(label!=null)mapped.put(raw,label);
        }
        return snapshot(true,source,dimensions,helper,mapped,visuals);
    }
    public int size(){return rows.size();}
    public String domain(){return domain;}
    public JsonObject counts(){return copy(rows.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("counts"));}
    public void verify(){if(!signature.equals(CanonicalJson.digest(current(new TreeMap<>()))))throw new Jobs.Fault("environment_changed","GT underground configuration, selection order, projection or registry changed");}
    public void capture(int index,Facts facts){
        if(index<0||index>=rows.size())throw fault("Invalid underground-fluid record index");
        JsonObject row=copy(rows.get(index).getAsJsonObject()),data=row.getAsJsonObject("data");
        if(data.get("kind").getAsString().equals("undergroundFluidEntry")&&!data.get("fluid").isJsonNull()){
            String id=data.get("fluid").getAsString();FluidStack stack=specimens.get(id);
            if(stack==null||!id.equals(facts.fluid(stack.copy())))throw fault("Native underground fluid identity changed");
        }
        facts.row("references",row);
    }
    static JsonArray snapshot(boolean ready,JsonObject source,Map<?,?> dimensions,Map<?,?> helper,Map<String,String> mapped,Map<String,FluidStack> visuals){
        if(!ready)throw new Jobs.Fault("handler_not_ready","GT ore plugin load-complete initialization is not finished");
        if(dimensions.size()>4096||helper.size()>65536)throw fault("GT underground-fluid registry budget exceeded");
        JsonObject definition=object("kind","undergroundFluidsDefinition","source",source,"ready",true,"consuming",false,
            "selection","nativeThousandTicketsInclusive","amountGeneration","nativeFifthPowerVeinData");
        JsonArray data=array(definition);int entries=0,displays=0;Map<String,Integer> expected=new TreeMap<>();
        TreeMap<String,Object> sorted=new TreeMap<>();for(Map.Entry<?,?> entry:dimensions.entrySet())sorted.put(text(entry.getKey()),entry.getValue());
        for(Map.Entry<String,Object> dimension:sorted.entrySet()){
            Jobs.checkpoint();String raw=dimension.getKey();Object nativeDim=dimension.getValue();
            if(nativeDim==null||!nativeDim.getClass().getName().equals("gregtech.api.objects.GTUODimension")||!raw.equals(text(field(nativeDim,"Dimension"))))throw fault("Invalid native underground dimension identity");
            Map<?,?> fluids=map(invoke(nativeDim.getClass(),nativeDim,"getFluids",new Class<?>[0]));
            if(fluids.size()>65536||entries+fluids.size()>262144)throw fault("GT underground-fluid entry budget exceeded");
            List<JsonObject> leaves=new ArrayList<>();long sum=0;
            for(Map.Entry<?,?> fluid:fluids.entrySet()){
                Object config=fluid.getValue();String registry=text(fluid.getKey());
                if(config==null||!config.getClass().getName().equals("gregtech.api.objects.GTUOFluid")||!registry.equals(text(field(config,"Registry"))))throw fault("Invalid native underground fluid key");
                int weight=integer(field(config,"Chance")),min=integer(field(config,"MinAmount")),max=integer(field(config,"MaxAmount")),decrease=integer(field(config,"DecreasePerOperationAmount"));
                if(weight<0||weight>Integer.MAX_VALUE/10000||min<0||max<=0||min>max||decrease<0)throw fault("Invalid or overflowing native underground-fluid configuration");
                sum+=weight;if(sum>Integer.MAX_VALUE)throw fault("Native underground-fluid weight sum overflows");
                Fluid registered=FluidRegistry.getFluid(registry);String id=registered==null?null:Identity.fluid(registry,null);
                if(registered!=null){if(!registry.equals(registered.getName()))throw fault("Native fluid alias identity differs");visuals.put(id,new FluidStack(registered,1));}
                leaves.add(object("kind","undergroundFluidEntry","dimension",raw,"ordinal",leaves.size(),"registry",registry,"fluid",id,
                    "weight",weight,"minAmount",min,"maxAmount",max,"decreasePerOperation",decrease,"selectedTickets",0));
            }
            int total=(int)sum;
            if(integer(field(nativeDim,"maxChance"))!=total||!leaves.isEmpty()&&total<=0)throw fault("Native underground-fluid accumulated weight differs");
            int[] tickets=new int[leaves.size()];int cumulative=0,assigned=0;
            // Exact interval form of installed nextInt(1000), <= and integer subtraction. O(entries).
            for(int i=0;i<leaves.size();i++){
                cumulative+=leaves.get(i).get("weight").getAsInt()*1000/total;
                int upper=Math.min(1000,cumulative+1);tickets[i]=upper-assigned;assigned=upper;
            }
            int nullTickets=1000-assigned;
            String label=mapped.get(raw);
            if(label!=null)label=text(label);
            data.add(object("kind","undergroundFluidDimension","dimension",raw,"displayDimension",label,"entries",leaves.size(),"totalWeight",total,"nullTickets",nullTickets));
            for(int i=0;i<leaves.size();i++){
                JsonObject leaf=leaves.get(i);leaf.addProperty("selectedTickets",tickets[i]);data.add(leaf);entries++;
                if(label!=null&&!leaf.get("fluid").isJsonNull())expected.merge(displayKey(label,leaf.get("registry").getAsString(),leaf.get("weight").getAsInt()*10000/total,leaf.get("minAmount").getAsInt(),leaf.get("maxAmount").getAsInt()),1,Integer::sum);
            }
        }
        TreeMap<String,Object> projection=new TreeMap<>();for(Map.Entry<?,?> entry:helper.entrySet())projection.put(text(entry.getKey()),entry.getValue());
        Map<String,Integer> actual=new TreeMap<>();
        for(Map.Entry<String,Object> group:projection.entrySet()){
            if(!(group.getValue() instanceof List)||((List<?>)group.getValue()).isEmpty())throw fault("Invalid native underground-fluid projection");
            if(FluidRegistry.getFluid(group.getKey())==null)throw fault("Native underground-fluid projection has an unregistered fluid");
            int ordinal=0;
            for(Object wrapper:(List<?>)group.getValue()){
                if(wrapper==null||!wrapper.getClass().getName().equals(HELPER+"$UndergroundFluidWrapper"))throw fault("Unadapted underground-fluid display wrapper");
                String label=text(field(wrapper,"dimension"));int chance=integer(field(wrapper,"chance")),min=integer(field(wrapper,"minAmount")),max=integer(field(wrapper,"maxAmount"));
                actual.merge(displayKey(label,group.getKey(),chance,min,max),1,Integer::sum);
                data.add(object("kind","undergroundFluidDisplay","registry",group.getKey(),"ordinal",ordinal++,"dimension",label,"nativeChance",chance,"minAmount",min,"maxAmount",max));
                if(++displays>262144)throw fault("Native underground-fluid display budget exceeded");
            }
        }
        if(!actual.equals(expected))throw new Jobs.Fault("environment_changed","Underground-fluid helper projection does not match the native configuration");
        definition.add("counts",object("dimensions",sorted.size(),"entries",entries,"displays",displays));
        String domain=Identity.content("reference",object("kind","gtUndergroundFluids","records",data));JsonArray rows=array();
        for(int index=0;index<data.size();index++){JsonObject row=object("domain",domain,"order",index,"data",data.get(index));row.addProperty("id",Identity.content("refrow",row));if(CanonicalJson.bytes(row).length>1048576)throw fault("Underground-fluid record exceeds budget");rows.add(row);}
        return rows;
    }
    private static String displayKey(String dimension,String registry,int chance,int min,int max){return CanonicalJson.digest(object("dimension",dimension,"registry",registry,"chance",chance,"min",min,"max",max));}
    private static Map<?,?> map(Object raw){if(!(raw instanceof Map))throw fault("Missing native underground-fluid registry");return (Map<?,?>)raw;}
    private static int integer(Object raw){if(!(raw instanceof Integer))throw fault("Invalid native underground-fluid integer");return (Integer)raw;}
    private static String text(Object raw){if(!(raw instanceof String)||((String)raw).isEmpty()||((String)raw).length()>256)throw fault("Invalid native underground-fluid identifier");return (String)raw;}
    private static JsonObject copy(JsonObject row){return new JsonParser().parse(row.toString()).getAsJsonObject();}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("reference_unsupported",message);}
    @Override public void close(){specimens.clear();}
}
