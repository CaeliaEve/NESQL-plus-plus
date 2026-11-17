package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned GT world-generation definitions and NEI display projections, never consuming recipes. */
final class GtMineralReferences implements NativeReferences {
    static final String SMALL="gtneioreplugin.plugin.gregtech5.PluginGT5SmallOreStat";
    static final String VEIN="gtneioreplugin.plugin.gregtech5.PluginGT5VeinStat";
    private final boolean small;
    private final JsonObject source;
    private final JsonArray rows;
    private final String signature,domain;
    private final Map<String,ItemStack> specimens=new TreeMap<>();
    private final int wrappers,dimensions;
    static boolean supports(ICraftingHandler handler){return handler!=null&&(SMALL.equals(handler.getClass().getName())||VEIN.equals(handler.getClass().getName()));}
    GtMineralReferences(ICraftingHandler handler,JsonObject source){
        if(!supports(handler))throw fault("Unknown GT mineral handler");
        small=SMALL.equals(handler.getClass().getName());this.source=copy(source);
        rows=current(specimens);signature=CanonicalJson.digest(rows);domain=rows.get(0).getAsJsonObject().get("domain").getAsString();
        wrappers=registry("mapOre"+(small?"Small":"Layer")+"Wrapper").size();dimensions=registry(small?"dimToSmallOreWrapper":"dimToOreWrapper").size();
    }
    private Class<?> helper(){return type("gtneioreplugin.util.GT5Ore"+(small?"Small":"Layer")+"Helper");}
    private Map<?,?> registry(String name){return map(field(helper(),null,name));}
    private void readiness(){if(!Loader.instance().hasReachedState(LoaderState.AVAILABLE))throw new Jobs.Fault("handler_not_ready","GT mineral helper initialization is unfinished");}
    private JsonArray current(Map<String,ItemStack> visuals){
        readiness();
        Class<?> definitions=type("gregtech.api.enums."+(small?"SmallOres":"OreMixes"));
        Object values=invoke(definitions,null,"values",new Class<?>[0]);
        if(!(values instanceof Object[])||((Object[])values).length>65536)throw fault("Invalid GT mineral definitions");
        Map<String,Object> builders=new TreeMap<>();
        for(Object value:(Object[])values){Object builder=field(value,small?"smallOreBuilder":"oreMixBuilder");String name=text(field(builder,small?"smallOreName":"oreMixName"));if(builders.put(name,builder)!=null)throw fault("Duplicate GT mineral definition");}
        Object raw=small?field(type("gregtech.api.GregTechAPI"),null,"sWorldgenList"):field(type("gregtech.common.WorldgenGTOreLayer"),null,"sList");
        List<?> generators=list(raw);if(generators.size()>262144)throw fault("GT world-generation list exceeds budget");
        Map<String,Object> nativeGenerators=new TreeMap<>();String expected="gregtech.common.WorldgenGTOre"+(small?"SmallPieces":"Layer");
        for(Object generator:generators)if(type(expected).isInstance(generator)){
            if(!generator.getClass().getName().equals(expected))throw fault("Unadapted GT mineral generator subclass");
            String name=text(field(generator,"mWorldGenName"));if(small&&!name.startsWith("ore.small."))continue;
            if(nativeGenerators.put(name,generator)!=null)throw fault("Duplicate GT mineral generator");
        }
        List<?> dusts=small?Arrays.asList((Object[])invoke(helper(),null,"getDroppedDusts",new Class<?>[0])):Collections.emptyList();
        return snapshot(true,small,source,builders,registry("mapOre"+(small?"Small":"Layer")+"Wrapper"),nativeGenerators,registry("bufferedDims"),registry(small?"dimToSmallOreWrapper":"dimToOreWrapper"),
            small?registry("mapOreMetaToOreDrops"):Collections.emptyMap(),small?registry("mapOreDropUnlocalizedNameToOreMeta"):Collections.emptyMap(),dusts,small&&bool(field(helper(),null,"restrictBiomeSupport")),visuals);
    }
    public int size(){return rows.size();}
    public String domain(){return domain;}
    public JsonObject counts(){return copy(rows.get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("counts"));}
    public void verify(){readiness();if(wrappers!=registry("mapOre"+(small?"Small":"Layer")+"Wrapper").size()||dimensions!=registry(small?"dimToSmallOreWrapper":"dimToOreWrapper").size())throw new Jobs.Fault("environment_changed","GT mineral helper size changed");}
    public void finishVerification(){verify();if(!signature.equals(CanonicalJson.digest(current(new TreeMap<>()))))throw new Jobs.Fault("environment_changed","GT mineral configuration, generator, host, drop or dimension projection changed");}
    public void capture(int index,Facts facts){
        if(index<0||index>=rows.size())throw fault("Invalid mineral record index");JsonObject row=copy(rows.get(index).getAsJsonObject());
        if(row.getAsJsonObject("data").get("kind").getAsString().equals("gtMineralEntry")){
            JsonObject data=row.getAsJsonObject("data");
            for(JsonElement role:data.getAsJsonArray("roles"))for(JsonElement host:role.getAsJsonObject().getAsJsonArray("hosts"))emitItem(host.getAsJsonObject().get("item").getAsString(),facts);
            for(JsonElement drop:data.getAsJsonArray("drops"))emitItem(drop.getAsJsonObject().get("item").getAsString(),facts);
        }
        facts.row("references",row);
    }
    private void emitItem(String id,Facts facts){ItemStack stack=specimens.get(id);if(stack==null||!id.equals(facts.item(stack.copy())))throw fault("Native mineral item identity changed");}

    static JsonArray snapshot(boolean ready,boolean small,JsonObject source,Map<?,?> builders,Map<?,?> wrappers,Map<?,?> generators,Map<?,?> buffered,Map<?,?> dimensions,Map<?,?> drops,Map<?,?> lookup,List<?> dusts,boolean restrictBiome,Map<String,ItemStack> visuals){
        if(!ready)throw new Jobs.Fault("handler_not_ready","GT mineral helper initialization is unfinished");
        if(builders.size()>65536||wrappers.size()>65536||generators.size()>65536||dimensions.size()>4096||lookup.size()>262144||dusts.size()>64)throw fault("GT mineral registry budget exceeded");
        JsonArray dustMaterials=array(),dropLookup=array();for(Object dust:dusts)dustMaterials.add(material(dust));
        for(Map.Entry<String,Object> value:sorted(lookup).entrySet())dropLookup.add(object("name",value.getKey(),"meta",number(value.getValue())));
        JsonObject definition=object("kind","gtMineralsDefinition","source",source,"family",small?"smallOre":"vein","ready",true,"consuming",false,
            "hostPolicy","nativeDimensionSevenHosts","shareSemantics",small?"relativeAmount":"relativeWeight","dustMaterials",dustMaterials,"dropLookup",dropLookup,"restrictBiomeSupport",small?restrictBiome:null);
        JsonArray data=array(definition);Map<Object,String> keys=new IdentityHashMap<>();Map<String,JsonObject> entries=new TreeMap<>();Map<String,Object> ordered=sorted(wrappers);
        Set<String> expectedNames=new TreeSet<>();if(small)expectedNames.addAll(sorted(generators).keySet());else expectedNames.addAll(sorted(builders).keySet());
        if(!ordered.keySet().equals(expectedNames)||buffered.size()!=ordered.size())throw new Jobs.Fault("environment_changed","GT mineral native definition/generator and helper coverage differ");
        for(Map.Entry<String,Object> value:ordered.entrySet()){
            Jobs.checkpoint();String key=value.getKey();Object wrapper=value.getValue(),builder=builders.get(key),generator=generators.get(key);
            if(builder==null||wrapper==null||!wrapper.getClass().getName().equals("gtneioreplugin.util.GT5Ore"+(small?"Small":"Layer")+"Helper$Ore"+(small?"Small":"Layer")+"Wrapper")||!key.equals(text(field(wrapper,small?"oreGenName":"veinName"))))throw fault("Invalid pinned GT mineral wrapper");
            if(generator==null&&bool(field(builder,"enabledByDefault")))throw new Jobs.Fault("handler_not_ready","Enabled GT mineral generator is absent: "+key);
            JsonObject configured=settings(builder,small,false),runtime=generator==null?null:generator(generator,small,key);
            JsonArray rawDimensions=array();Map<?,?> rawDims=map(field(wrapper,"allowedDimWithOrigNames"));if(!rawDims.equals(map(field(builder,"dimsEnabled")))||rawDims.size()>4096)throw fault("GT mineral definition dimension drift");
            for(Map.Entry<String,Object> dim:sorted(rawDims).entrySet())rawDimensions.add(object("name",dim.getKey(),"enabled",bool(dim.getValue())));
            Map<?,?> projected=map(invoke(type("gtneioreplugin.util.DimensionHelper"),null,"getDims",new Class<?>[]{wrapper.getClass()},wrapper));
            if(!projected.equals(buffered.get(wrapper)))throw new Jobs.Fault("environment_changed","GT mineral cached dimension projection differs");
            JsonArray labels=array();for(Map.Entry<String,Object> dim:sorted(projected).entrySet()){if(!bool(dim.getValue()))throw fault("Disabled projected GT mineral dimension");labels.add(new JsonPrimitive(dim.getKey()));}
            String height=text(field(wrapper,"worldGenHeightRange"));if(!height.equals(number(field(builder,"minY"))+"-"+number(field(builder,"maxY"))))throw fault("GT mineral displayed height differs");
            JsonArray roles=array(),dropItems=array();Object[] materials=small?new Object[]{invoke(wrapper.getClass(),wrapper,"getOreMaterial",new Class<?>[0])}:new Object[]{field(wrapper,"mPrimaryVeinMaterial"),field(wrapper,"mSecondaryMaterial"),field(wrapper,"mBetweenMaterial"),field(wrapper,"mSporadicMaterial")};
            String[] names=small?new String[]{"small"}:new String[]{"primary","secondary","between","sporadic"};String[] fields=small?new String[]{"ore"}:new String[]{"primary","secondary","between","sporadic"};
            for(int role=0;role<materials.length;role++){
                Object nativeMaterial=materials[role];if(nativeMaterial!=field(builder,fields[role]))throw fault("GT mineral layer material differs");JsonObject fact=material(nativeMaterial);
                int meta=small?number(field(wrapper,"oreMeta")):((short[])field(wrapper,"Meta"))[role];if(meta!=(short)number(field(nativeMaterial,"mMetaItemSubID")))throw fault("GT mineral layer metadata differs");
                if(runtime!=null&&runtime.getAsJsonArray("metas").get(role).getAsInt()!=meta)throw fault("GT mineral generator metadata differs");
                List<?> hostStacks=list(small?invoke(wrapper.getClass(),wrapper,"getMaterialDrops",new Class<?>[]{int.class},7):invoke(wrapper.getClass(),wrapper,"getVeinLayerOre",new Class<?>[]{int.class,int.class},7,role));
                if(hostStacks.size()!=7)throw fault("GT mineral native host count differs");JsonArray hosts=array();
                for(int host=0;host<hostStacks.size();host++){ItemStack stack=stack(hostStacks.get(host));if(stack.stackSize!=1||stack.getItemDamage()!=(small?16000:0)+meta+1000*host)throw fault("GT mineral host metadata differs");hosts.add(object("host",host,"item",item(stack,visuals)));}
                roles.add(object("role",names[role],"material",fact,"meta",meta,"hosts",hosts));
            }
            int nativeWeight=number(field(wrapper,small?"amountPerChunk":"randomWeight"));if(nativeWeight!=(short)configured.get(small?"amount":"weight").getAsInt())throw fault("GT mineral display weight differs");
            if(!small&&(number(field(wrapper,"density"))!=(short)configured.get("density").getAsInt()||number(field(wrapper,"size"))!=(short)configured.get("size").getAsInt()))throw fault("GT vein display density or size differs");
            if(small){Object rawDrop=drops.get((short)number(field(wrapper,"oreMeta")));if(rawDrop==null)throw new Jobs.Fault("handler_not_ready","Small-ore drop table is absent: "+key);List<?> nativeDrops=list(rawDrop);if(nativeDrops.size()>65536)throw fault("Small-ore drop list exceeds budget");for(Object raw:nativeDrops){ItemStack stack=stack(raw);dropItems.add(object("item",item(stack,visuals),"amount",stack.stackSize));}}
            JsonObject entry=object("kind","gtMineralEntry","key",key,"ordinal",entries.size(),"displayName",small?null:text(field(wrapper,"localizedName")),"configured",configured,"enabledByDefault",bool(field(builder,"enabledByDefault")),"generator",runtime,"dimensions",rawDimensions,"displayDimensions",labels,"displayHeight",height,"nativeWeight",nativeWeight,"roles",roles,"drops",dropItems);
            keys.put(wrapper,key);entries.put(key,entry);data.add(entry);
        }
        Map<String,Integer> expected=new TreeMap<>(),actual=new TreeMap<>();for(JsonObject entry:entries.values())for(JsonElement dim:entry.getAsJsonArray("displayDimensions"))expected.merge(dim.getAsString()+"\0"+entry.get("key").getAsString(),1,Integer::sum);
        int shares=0;for(Map.Entry<String,Object> dim:sorted(dimensions).entrySet()){
            Object projection=dim.getValue();List<?> nativeList=list(field(projection,"internalDimOreList"));Map<?,?> ratios=map(field(projection,"oreVeinToProbabilityInDimension"));
            if(nativeList.size()>65536||ratios.size()!=nativeList.size()||nativeList.isEmpty())throw fault("Invalid GT mineral dimension share list");
            int total=0;for(Object wrapper:nativeList){String key=keys.get(wrapper);if(key==null)throw fault("Unknown GT mineral dimension entry");total=Math.addExact(total,entries.get(key).get("nativeWeight").getAsInt());}
            for(int ordinal=0;ordinal<nativeList.size();ordinal++){
                Object wrapper=nativeList.get(ordinal);String key=keys.get(wrapper);int weight=entries.get(key).get("nativeWeight").getAsInt();Object rawRatio=ratios.get(wrapper);if(!(rawRatio instanceof Double)||Double.doubleToLongBits((Double)rawRatio)!=Double.doubleToLongBits((double)weight/total))throw new Jobs.Fault("environment_changed","GT mineral native dimension share differs");
                double ratio=(Double)rawRatio;String nonFinite=Double.isNaN(ratio)?"nan":Double.isInfinite(ratio)?ratio>0?"positiveInfinity":"negativeInfinity":null;
                data.add(object("kind","gtMineralDimensionShare","dimension",dim.getKey(),"ordinal",ordinal,"key",key,"numerator",weight,"denominator",total,"nativeShare",nonFinite==null?Double.toString(ratio):null,"nonFinite",nonFinite));
                actual.merge(dim.getKey()+"\0"+key,1,Integer::sum);if(++shares>262144)throw fault("GT mineral share budget exceeded");
            }
        }
        if(!actual.equals(expected))throw new Jobs.Fault("environment_changed","GT mineral dimension memberships differ");
        definition.add("counts",object("entries",entries.size(),"dimensionShares",shares));String domain=Identity.content("reference",object("kind","gtMinerals","records",data));JsonArray rows=array();
        for(int index=0;index<data.size();index++){JsonObject row=object("domain",domain,"order",index,"data",data.get(index));row.addProperty("id",Identity.content("refrow",row));if(CanonicalJson.bytes(row).length>1048576)throw fault("GT mineral record exceeds budget");rows.add(row);}return rows;
    }
    private static JsonObject settings(Object raw,boolean small,boolean runtime){return object("minY",number(field(raw,runtime?"mMinY":"minY")),"maxY",number(field(raw,runtime?"mMaxY":"maxY")),"amount",small?number(field(raw,runtime?"mAmount":"amount")):null,"weight",small?null:number(field(raw,runtime?"mWeight":"weight")),"density",small?null:number(field(raw,runtime?"mDensity":"density")),"size",small?null:number(field(raw,runtime?"mSize":"size")));}
    private static JsonObject generator(Object raw,boolean small,String key){
        if(!key.equals(text(field(raw,"mWorldGenName")))||!bool(field(raw,"mEnabled")))throw fault("GT mineral generator key/readiness differs");JsonObject result=settings(raw,small,true);JsonArray metas=array();
        for(String name:small?new String[]{"mMeta"}:new String[]{"mPrimaryMeta","mSecondaryMeta","mBetweenMeta","mSporadicMeta"})metas.add(new JsonPrimitive(number(field(raw,name))));
        JsonArray providers=array(),blacklist=array();Object allowed=field(raw,"mAllowedProviders"),denied=field(raw,"blackListedProviders");
        if(!(allowed instanceof Class[])||((Class<?>[])allowed).length>256||denied!=null&&(!(denied instanceof String[])||((String[])denied).length>256))throw fault("Invalid native GT provider rules");
        for(Class<?> provider:(Class<?>[])allowed)providers.add(new JsonPrimitive(provider.getName()));if(denied!=null)for(String provider:(String[])denied)blacklist.add(new JsonPrimitive(text(provider)));
        result.add("metas",metas);result.addProperty("biome",text(field(raw,small?"mBiome":"mRestrictBiome")));result.add("providers",providers);result.add("blacklistedProviders",blacklist);
        result.add("flags",object("overworld",bool(field(raw,"mOverworld")),"nether",bool(field(raw,"mNether")),"end",bool(field(raw,"mEnd")),"twilightForest",bool(field(raw,"twilightForest")),"endAsteroid",small?null:bool(field(raw,"mEndAsteroid"))));return result;
    }
    private static JsonObject material(Object raw){if(raw==null||!raw.getClass().getName().equals("gregtech.api.enums.Materials"))throw fault("Unadapted GT mineral material");return object("key",text(field(raw,"mName")),"meta",number(field(raw,"mMetaItemSubID")),"name",text(field(raw,"mLocalizedName")));}
    private static String item(ItemStack stack,Map<String,ItemStack> visuals){Object raw=Item.itemRegistry.getNameForObject(stack.getItem());String registry=text(raw);String id=Identity.item(registry,stack.getItemDamage(),TypedNbt.encode(stack.getTagCompound()));visuals.put(id,stack.copy());return id;}
    private static ItemStack stack(Object raw){if(!(raw instanceof ItemStack)||((ItemStack)raw).getItem()==null||((ItemStack)raw).stackSize<=0)throw fault("Invalid native mineral item");return (ItemStack)raw;}
    private static JsonObject copy(JsonObject row){return new JsonParser().parse(row.toString()).getAsJsonObject();}
    private static Map<String,Object> sorted(Map<?,?> raw){Map<String,Object> sorted=new TreeMap<>();for(Map.Entry<?,?> e:raw.entrySet())sorted.put(text(e.getKey()),e.getValue());return sorted;}
    private static Map<?,?> map(Object raw){if(!(raw instanceof Map))throw fault("Missing native GT mineral map");return (Map<?,?>)raw;}
    private static List<?> list(Object raw){if(!(raw instanceof List))throw fault("Missing native GT mineral list");return (List<?>)raw;}
    private static boolean bool(Object raw){if(!(raw instanceof Boolean))throw fault("Invalid native GT mineral flag");return (Boolean)raw;}
    private static int number(Object raw){if(!(raw instanceof Integer)&&!(raw instanceof Short))throw fault("Invalid native GT mineral integer");return ((Number)raw).intValue();}
    private static String text(Object raw){if(!(raw instanceof String)||((String)raw).isEmpty()||((String)raw).length()>1024)throw fault("Invalid native GT mineral text");return (String)raw;}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("reference_unsupported",message);}
    @Override public void close(){specimens.clear();}
}
