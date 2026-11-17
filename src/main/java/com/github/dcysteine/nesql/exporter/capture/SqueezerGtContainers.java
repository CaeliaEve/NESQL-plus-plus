package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraftforge.fluids.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Finite GT table reads and owned drain-target closure; never executes fluid/container callbacks. */
final class SqueezerGtContainers {
    private static final String BASE="gregtech.api.items.MetaBaseItem", PLUS="gtPlusPlus.xmod.gregtech.api.items.GTMetaItemBase";
    private static final String BW="bartworks.system.material.BWMetaGeneratedItems", GENERIC="gregtech.api.items.GTGenericItem", PLUS_GENERIC="gtPlusPlus.xmod.gregtech.api.items.GTGenericItem";
    private static final String UTILITY="gregtech.api.util.GTUtility", KEY="gregtech.api.objects.GTItemStack";
    private final Set<String> keys;
    private final SqueezerCallbackClasses classes=new SqueezerCallbackClasses();
    private final Map<Item,SortedMap<Short,FluidContainerRegistry.FluidContainerData>> table=new IdentityHashMap<>();
    private final JsonArray dependencies=array(),items=array();
    private final List<GtItemListBinding> bindings=new ArrayList<>();

    SqueezerGtContainers(Set<String> keys){
        this.keys=keys;
        if(!classes.pinned(SqueezerGtContainers.class.getClassLoader(),UTILITY,KEY))throw fault("Unpinned GT fluid table source");
        Object raw=field(type(UTILITY),null,"sFilledContainerToData");
        if(raw==null||raw.getClass()!=HashMap.class||((Map<?,?>)raw).size()>65536)throw fault("Unadapted GT fluid table");
        TreeMap<String,JsonObject> ordered=new TreeMap<>();int bytes=0;
        for(Map.Entry<?,?> entry:((Map<?,?>)raw).entrySet()){
            Jobs.checkpoint();Object nativeKey=entry.getKey(),nativeData=entry.getValue();
            if(nativeKey==null||nativeKey.getClass()!=type(KEY)||nativeData==null||nativeData.getClass()!=FluidContainerRegistry.FluidContainerData.class)throw fault("Unadapted GT fluid table entry");
            Item item=(Item)field(nativeKey,"mItem");short meta=((Number)field(nativeKey,"mMetaData")).shortValue();
            String registry=registered(item);FluidContainerRegistry.FluidContainerData data=(FluidContainerRegistry.FluidContainerData)nativeData;
            if(data.fluid==null)throw fault("GT fluid table has a null fluid");
            SortedMap<Short,FluidContainerRegistry.FluidContainerData> rows=table.get(item);
            if(rows==null){rows=new TreeMap<>();table.put(item,rows);}
            if(rows.put(meta,data)!=null)throw fault("GT fluid table has a duplicate native key");
            JsonObject evidence=object("registry",registry,"meta",(int)meta,"fluid",SqueezerRules.fluid(data.fluid),
                    "filled",evidence(data.filledContainer),"empty",evidence(data.emptyContainer));
            bytes+=CanonicalJson.bytes(evidence).length;if(bytes>16*1024*1024)throw fault("GT fluid proof exceeds 16 MiB");
            if(ordered.put(registry+"\0"+meta,evidence)!=null)throw fault("GT fluid table has a duplicate registry key");
        }
        JsonArray rows=array();for(JsonObject row:ordered.values())rows.add(row);
        dependencies.add(object("gtFluidTable",rows));
    }

    JsonObject describe(String registry,Item item){
        Class<?> type=item.getClass();ClassLoader loader=type.getClassLoader();String base=owner(type,"getFluid",ItemStack.class);
        if(!base.equals(BASE)&&!base.equals(PLUS))return null;
        if(!owner(type,"drain",ItemStack.class,int.class,boolean.class).equals(base)
                ||!owner(type,"getFluidContent",ItemStack.class).equals(base)
                ||!owner(type,"setFluidContent",ItemStack.class,FluidStack.class).equals(base)
                ||!classes.pinned(loader,base,UTILITY,KEY))return null;
        Stats stats=stats(item);if(stats==null)return null;
        SortedMap<Short,FluidContainerRegistry.FluidContainerData> rows=table.get(item);
        if(rows==null)rows=Collections.emptySortedMap();
        JsonObject facts=object("registry",registry,"stats",stats.facts);items.add(facts);
        if(rows.isEmpty()&&stats.positive.isEmpty())return object("registry",registry,"kind","noFluid");
        if(keys.contains(registry))return null;
        if(!rows.isEmpty()&&!owner(type,"setDamage",ItemStack.class,int.class).equals(Item.class.getName()))return null;
        String getter=containerOwner(item),presence=presenceOwner(item);
        facts.addProperty("containerOwner",getter);facts.addProperty("presenceOwner",presence);
        if(!presence.equals(GENERIC)&&!presence.equals(PLUS_GENERIC))return null;
        if(!classes.pinned(loader,presence))return null;
        if(getter.equals(BW)){
            if(!classes.pinned(loader,BW))return null;
            Object prefix=field(item,"orePrefixes");facts.addProperty("prefix",enumName(prefix));
            GtItemListBinding binding=GtItemListBinding.bartworks(item);
            ItemStack empty=bind(binding,facts,"bwContainer");
            if(empty!=null&&stats.positive.isEmpty()){
                JsonArray fixed=array();
                for(Map.Entry<Short,FluidContainerRegistry.FluidContainerData> row:rows.entrySet())fixed.add(object(
                        "meta",(int)row.getKey(),"fluid",SqueezerRules.fluid(row.getValue().fluid),
                        "emptyRegistry",registered(empty.getItem()),"emptyMeta",Math.max(0,Items.feather.getDamage(empty))));
                return object("registry",registry,"kind","gtFixed","rows",fixed);
            }
            Set<String> targets=new TreeSet<>();targets.add(registry);
            if(empty!=null)targets.add(registered(empty.getItem()));else if(!fallback(facts,targets))return null;
            if(!stats.positive.isEmpty()&&!usability(item,stats,facts))return null;
            return nonMatch(registry,targets,minimum(item,stats));
        }
        Set<String> targets=new TreeSet<>();targets.add(registry);
        if(!rows.isEmpty()&&!targets(item,getter,rows,facts,targets))return null;
        if(!stats.positive.isEmpty()&&!usability(item,stats,facts))return null;
        return nonMatch(registry,targets,minimum(item,stats));
    }

    JsonArray dependencies(){JsonArray result=array();for(JsonElement row:dependencies)result.add(row);result.add(object("gtItems",items));return result;}
    void verify(){for(GtItemListBinding binding:bindings)binding.verify();}

    private JsonObject nonMatch(String registry,Set<String> targets,int minimum){
        for(String target:targets)if(keys.contains(target))return null;
        JsonArray registries=array();for(String target:targets)registries.add(value(target));
        return object("registry",registry,"kind","noContainerRule","drainedRegistries",registries,"readEffect","preserve",
                "minMeta",minimum,"maxMeta",Integer.MAX_VALUE,"compoundTags",array());
    }

    private Stats stats(Item item){
        Class<?> type=item.getClass();String owner=owner(type,"getFluidContainerStats",ItemStack.class);
        if(!classes.pinned(type.getClassLoader(),owner))return null;
        Stats result=new Stats();Object raw;
        switch(owner){
            case "gregtech.api.items.MetaGeneratedTool":
            case "bartworks.system.material.CircuitGeneration.BWMetaItems$BW_GT_MetaGen_Item_Hook":
            case "ggfab.items.GGMetaItemDumbItems":return result;
            case "gregtech.api.items.MetaGeneratedItem":
                raw=field(item,"mFluidContainerStats");if(raw==null||raw.getClass()!=ConcurrentHashMap.class)return null;break;
            case "gtPlusPlus.xmod.gregtech.api.items.GTMetaItem":
                raw=field(item,"mFluidContainerStats");if(raw==null||raw.getClass()!=HashMap.class)return null;break;
            default:return null;
        }
        if(((Map<?,?>)raw).size()>65536)return null;
        TreeMap<Short,JsonObject> ordered=new TreeMap<>();int bytes=0;
        for(Map.Entry<?,?> entry:((Map<?,?>)raw).entrySet()){
            Jobs.checkpoint();if(!(entry.getKey() instanceof Short)||entry.getValue()==null||entry.getValue().getClass()!=Long[].class)return null;
            short meta=(Short)entry.getKey();Long[] values=(Long[])entry.getValue();if(values.length==0||values[0]==null||values.length>64)return null;
            JsonArray encoded=array();for(Long value:values)encoded.add(value==null?JsonNull.INSTANCE:value(value.toString()));
            JsonObject row=object("meta",(int)meta,"values",encoded);bytes+=CanonicalJson.bytes(row).length;if(bytes>4*1024*1024)return null;
            ordered.put(meta,row);if(values[0]>0)result.positive.add(meta);
        }
        for(JsonObject row:ordered.values())result.facts.add(row);return result;
    }

    private boolean targets(Item item,String getter,SortedMap<Short,FluidContainerRegistry.FluidContainerData> rows,JsonObject facts,Set<String> targets){
        if(!classes.pinned(item.getClass().getClassLoader(),getter))return false;
        switch(getter){
            case GENERIC:case PLUS_GENERIC:break;
            case "gregtech.common.items.MetaGeneratedItem98":
                add(bind(new GtItemListBinding("Cell_Empty"),facts,"meta98Cell"),targets);break;
            case "gregtech.common.items.MetaGeneratedItem99":
                // Fluid table keys are shorts, but the container getter consumes the
                // full offered metadata. Include every native prefix, not just rows:
                // short aliases may select another prefix or the fallback branch.
                Class<?> meta99=type(getter);
                int count=((Number)field(meta99,null,"NUM_CRACKED_CELL_TYPES")).intValue();
                if(count<0||count>32)return false;
                for(int index=-1;index<count;index++){
                    ItemStack query=new ItemStack(item,1,index<0?0:10000+index*1000);
                    String role=GtItemListBinding.containerEntry(query,getter);
                    add(bind(new GtItemListBinding(role,query),facts,"meta99Prefix"+index),targets);
                }
                break;
            case "gregtech.common.items.MetaGeneratedItem01":
                add(bind(new GtItemListBinding("Spray_Empty"),facts,"spray"),targets);
                if(!prefixes(item,rows,facts,targets))return false;break;
            case "gregtech.common.items.MetaGeneratedItem02":
                add(bind(new GtItemListBinding("ThermosCan_Empty"),facts,"thermos"),targets);
                add(bind(new GtItemListBinding("Bottle_Empty"),facts,"bottle"),targets);break;
            case "gregtech.api.items.MetaGeneratedItemX32":
            case "gtPlusPlus.xmod.gregtech.api.items.GTMetaItemX32":
                if(!prefixes(item,rows,facts,targets))return false;break;
            case "gregtech.api.items.MetaGeneratedItemX01":
                Object prefix=field(item,"mPrefix");facts.addProperty("prefix",enumName(prefix));
                ItemStack result=prefix==null?null:prefixContainer(prefix);facts.add("prefixContainer",RecipeRow.stackEvidence(result));add(result,targets);break;
            default:return false;
        }
        // A metadata alias can make a generated item's native getter return null.
        // The remaining owned GTUtility branches return only one of these ItemList roles,
        // the source item (tool damage), or null. Their decision predicates need not run.
        return fallback(facts,targets);
    }

    private boolean prefixes(Item item,SortedMap<Short,FluidContainerRegistry.FluidContainerData> rows,JsonObject facts,Set<String> targets){
        Object raw=field(item,"mGeneratedPrefixList");if(raw==null||!raw.getClass().isArray())return false;
        int length=java.lang.reflect.Array.getLength(raw);if(length>32)return false;
        Object materials=field(type("gregtech.api.GregTechAPI"),null,"sGeneratedMaterials");
        if(materials==null||!materials.getClass().isArray())return false;
        int materialCount=java.lang.reflect.Array.getLength(materials);
        facts.addProperty("materialCount",materialCount);JsonArray prefixes=array();
        for(int i=0;i<length;i++){
            Object prefix=java.lang.reflect.Array.get(raw,i);ItemStack result=prefix==null?null:prefixContainer(prefix);
            prefixes.add(object("prefix",enumName(prefix),"container",RecipeRow.stackEvidence(result)));add(result,targets);
        }
        facts.add("prefixes",prefixes);
        for(short meta:rows.keySet())if(meta>=0&&meta<32000&&(meta/1000>=length||meta%1000>=materialCount))return false;
        return true;
    }

    private boolean fallback(JsonObject facts,Set<String> targets){
        if(!classes.pinned(SqueezerGtContainers.class.getClassLoader(),"gregtech.api.util.GTModHandler"))return false;
        add(bind(new GtItemListBinding("Cell_Empty"),facts,"fallbackCell"),targets);
        add(bind(new GtItemListBinding("Bottle_Empty"),facts,"fallbackBottle"),targets);return true;
    }

    private boolean usability(Item item,Stats stats,JsonObject facts){
        String owner=owner(item.getClass(),"isItemStackUsable",ItemStack.class);facts.addProperty("usabilityOwner",owner);
        if(!classes.pinned(item.getClass().getClassLoader(),owner))return false;
        if(owner.equals(PLUS_GENERIC)||owner.equals(GENERIC))return true;
        if(!owner.equals(BASE)&&!owner.equals("gregtech.common.items.MetaGeneratedItem02"))return false;
        if(owner.equals("gregtech.common.items.MetaGeneratedItem02"))for(short meta:stats.positive)if(meta>=25000&&meta<27000)return false;
        if(!owner(item.getClass(),"getDamage",ItemStack.class).equals(Item.class.getName()))return false;
        Object raw=field(item,"mItemBehaviors");if(raw==null||raw.getClass()!=ConcurrentHashMap.class)return false;
        Map<?,?> behaviors=(Map<?,?>)raw;JsonArray observed=array();String behavior="gregtech.common.items.behaviors.BehaviourNone";
        for(short meta:stats.positive){
            Object value=behaviors.get(meta);JsonArray owners=array();
            if(value!=null){
                if(value.getClass()!=ArrayList.class||((List<?>)value).size()>4096)return false;
                for(Object callback:(List<?>)value){
                    if(callback==null||!owner(callback.getClass(),"isItemStackUsable",Item.class,ItemStack.class).equals(behavior)
                            ||!owner(callback.getClass(),"isItemStackUsable",type(BASE),ItemStack.class).equals(behavior))return false;
                    owners.add(value(callback.getClass().getName()));
                }
            }
            observed.add(object("meta",(int)meta,"behaviors",owners));
        }
        if(!classes.pinned(item.getClass().getClassLoader(),behavior))return false;
        facts.add("fluidUsabilityBehaviors",observed);return true;
    }

    private int minimum(Item item,Stats stats){return !stats.positive.isEmpty()&&owner(item.getClass(),"isItemStackUsable",ItemStack.class).equals("gregtech.common.items.MetaGeneratedItem02")?0:Integer.MIN_VALUE;}
    private ItemStack bind(GtItemListBinding binding,JsonObject facts,String role){
        if(binding==null){facts.add(role,JsonNull.INSTANCE);return null;}
        bindings.add(binding);ItemStack result=binding.result();facts.add(role,RecipeRow.stackEvidence(result));return result;
    }
    private static ItemStack prefixContainer(Object prefix){enumName(prefix);Object raw=field(prefix,"mContainerItem");if(raw!=null&&raw.getClass()!=ItemStack.class)throw fault("Unadapted GT prefix container");return (ItemStack)raw;}
    private static String enumName(Object value){if(value==null)return null;if(!(value instanceof Enum<?>)||!((Enum<?>)value).getDeclaringClass().getName().equals("gregtech.api.enums.OrePrefixes"))throw fault("Unadapted GT container prefix");return ((Enum<?>)value).name();}
    private static void add(ItemStack stack,Set<String> targets){if(stack!=null&&stack.getItem()!=null&&stack.stackSize>=0)targets.add(registered(stack.getItem()));}
    private static String registered(Item item){String name=item==null?null:Item.itemRegistry.getNameForObject(item);if(name==null)throw fault("Unregistered GT container item");return name;}
    private static JsonObject evidence(ItemStack stack){if(stack!=null&&stack.getClass()!=ItemStack.class)throw fault("Unadapted GT fluid proof stack");return RecipeRow.stackEvidence(stack);}
    private static String containerOwner(Item item){return ItemCallbacks.method(item,"getContainerItem","getContainerItem",ItemStack.class).getDeclaringClass().getName();}
    private static String presenceOwner(Item item){return ItemCallbacks.method(item,"hasContainerItem","hasContainerItem",ItemStack.class).getDeclaringClass().getName();}
    private static String owner(Class<?> type,String method,Class<?>... args){try{return type.getMethod(method,args).getDeclaringClass().getName();}catch(NoSuchMethodException failure){return "";}}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static final class Stats {final SortedSet<Short> positive=new TreeSet<>();final JsonArray facts=array();}
}
