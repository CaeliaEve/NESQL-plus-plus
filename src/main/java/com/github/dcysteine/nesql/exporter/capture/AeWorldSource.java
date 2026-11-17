package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.base.Optional;
import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual API/config/ore identities. No definition, stack-source or GUI selection callbacks. */
final class AeWorldSource {
    private static final String[] MATERIALS={"certusQuartzCrystalCharged","logicProcessorPress","calcProcessorPress","engProcessorPress",
        "fluixCrystal","qESingularity","purifiedCertusQuartzCrystal","purifiedNetherQuartzCrystal","purifiedFluixCrystal","singularity"};
    private static final String[] ORES={"dustEnder","dustEnderPearl"};
    final Object api,definitions,materials,items,config;
    final EnumSet<?> features,featureValues;
    final Map<?,?> oreNames;final List<?> oreTable;
    final List<?> catalog;
    private final int oreNameCount,oreTableCount,catalogCount;
    final LinkedHashMap<String,Definition> entries=new LinkedHashMap<>();
    final List<Ore> ores=new ArrayList<>();
    private final Map<Item,List<ItemStack>> examples=new IdentityHashMap<>();

    static AeWorldSource read(IRecipeHandler h){
        admit(h);return new AeWorldSource();
    }
    static Object[] registrationAnchors(IRecipeHandler h){
        admit(h);return new AeWorldSource(false).anchors();
    }
    private static void admit(IRecipeHandler h){
        if(!h.getClass().getName().equals("appeng.integration.modules.NEIHelpers.NEIWorldCraftingHandler"))throw fault("Unknown AE2 world handler");
        MagicApi.version("AeWorldSource","appliedenergistics2");
    }
    private AeWorldSource(){this(true);}
    private AeWorldSource(boolean snapshot){
        api=exact(field(type("appeng.api.AEApi"),null,"HELD_API"),"appeng.core.Api");
        definitions=exact(field(api,"definitions"),"appeng.core.ApiDefinitions");
        materials=field(definitions,"materials");items=field(definitions,"items");
        config=exact(field(type("appeng.core.AEConfig"),null,"instance"),"appeng.core.AEConfig");
        Object rawFeatures=field(config,"featureFlags");
        if(rawFeatures==null)throw unready("AE2 feature flags are unavailable");
        if(!rawFeatures.getClass().getName().equals("java.util.RegularEnumSet")&&!rawFeatures.getClass().getName().equals("java.util.JumboEnumSet"))throw fault("Unadapted native AE2 feature set");
        features=(EnumSet<?>)rawFeatures;featureValues=features.clone();
        for(Object flag:features)if(((Enum<?>)flag).getDeclaringClass()!=type("appeng.core.features.AEFeature"))throw fault("Unknown AE2 feature enum");
        for(int index=0;index<9;index++){
            String name=MATERIALS[index];if(feature(featureFor(name))){
                exact(materials,"appeng.core.api.definitions.ApiMaterials");entries.put(name,new Definition(field(materials,name)));
            }
        }
        if(present("fluixCrystal"))entries.putIfAbsent("certusQuartzCrystalCharged",new Definition(field(materials,"certusQuartzCrystalCharged")));
        if(present("qESingularity"))entries.put("singularity",new Definition(field(materials,"singularity")));
        if(present("purifiedCertusQuartzCrystal")||present("purifiedNetherQuartzCrystal")||present("purifiedFluixCrystal")){
            exact(items,"appeng.core.api.definitions.ApiItems");entries.put("crystalSeed",new Definition(field(items,"crystalSeed")));
        }
        if(!present("qESingularity")){oreNames=null;oreTable=null;catalog=null;oreNameCount=oreTableCount=catalogCount=0;return;}
        Object names=field(OreDictionary.class,null,"nameToId"),table=field(OreDictionary.class,null,"idToStack");
        if(names==null||table==null)throw unready("Native ore dictionary is unavailable");
        if(!NativeOreStorage.names(names)||table.getClass()!=ArrayList.class)throw fault("Unadapted native ore dictionary storage");
        oreNames=(Map<?,?>)names;oreTable=(List<?>)table;
        oreNameCount=oreNames.size();oreTableCount=oreTable.size();
        for(String name:ORES){
            Object id=oreNames.get(name);List<?> members=null;
            if(id!=null){if(!(id instanceof Integer)||(Integer)id<0||(Integer)id>=oreTable.size())throw fault("Invalid native ore ID");
                Object raw=oreTable.get((Integer)id);if(raw==null||raw.getClass()!=ArrayList.class)throw fault("Unadapted native ore membership list");members=(List<?>)raw;}
            ores.add(new Ore(name,id,members,snapshot));
        }
        // Registration binds a conservative catalogue root without traversing examples.
        // Readiness and wildcard contents belong to the world reader's snapshot.
        if(!snapshot){catalog=codechicken.nei.ItemList.items;catalogCount=0;return;}
        boolean wildcard=false;
        for(Ore ore:ores)for(ItemStack template:ore.templates)wildcard|=template.getItemDamage()==32767;
        if(wildcard){
            if(!codechicken.nei.ItemList.loadFinished||codechicken.nei.ItemList.items==null)throw unready("AE2 wildcard ore catalogue is unavailable");
            if(codechicken.nei.ItemList.items.size()>1000000)throw fault("AE2 wildcard ore catalogue exceeds budget");
            catalog=codechicken.nei.ItemList.items;
        }else catalog=null;
        catalogCount=catalog==null?0:catalog.size();
    }
    void verifyRoots(){
        Jobs.checkpoint();
        if(api!=field(type("appeng.api.AEApi"),null,"HELD_API")||definitions!=field(api,"definitions")
                ||materials!=field(definitions,"materials")||items!=field(definitions,"items")
                ||config!=field(type("appeng.core.AEConfig"),null,"instance")
                ||features!=field(config,"featureFlags")||!featureValues.equals(features))throw changed();
        if(oreNames!=null){
            if(oreNames!=field(OreDictionary.class,null,"nameToId")||oreTable!=field(OreDictionary.class,null,"idToStack")
                    ||oreNames.size()!=oreNameCount||oreTable.size()!=oreTableCount)throw changed();
            for(Ore ore:ores){
                Object id=oreNames.get(ore.name);
                if(!Objects.equals(ore.id,id)||id!=null&&oreTable.get((Integer)id)!=ore.members
                        ||ore.members!=null&&ore.members.size()!=ore.identities.size())throw changed();
            }
        }
        if(catalog!=null&&(!codechicken.nei.ItemList.loadFinished||catalog!=codechicken.nei.ItemList.items
                ||catalog.size()!=catalogCount))throw changed();
    }
    void verifyDefinition(String name){
        Definition initial=entries.get(name);
        if(initial==null||!initial.same(new Definition(field(name.equals("crystalSeed")?items:materials,name))))throw changed();
    }
    void verifyOres(){
        for(Ore ore:ores)if(!ore.same(new Ore(ore.name,ore.id,ore.members)))throw changed();
        for(Map.Entry<Item,List<ItemStack>> entry:examples.entrySet()){
            List<ItemStack> actual=CatalogExamples.metadata(new ItemStack(entry.getKey(),1,32767));
            List<ItemStack> expected=entry.getValue();
            if(actual.size()!=expected.size())throw changed();
            for(int index=0;index<actual.size();index++)if(actual.get(index).getItemDamage()!=expected.get(index).getItemDamage())throw changed();
        }
    }
    void finishVerification(){
        verifyRoots();
        for(String name:entries.keySet()){Jobs.checkpoint();verifyDefinition(name);}
        verifyOres();
    }
    List<ItemStack> metadata(ItemStack template){
        if(template.getItemDamage()!=32767)return Collections.singletonList(template.copy());
        List<ItemStack> saved=examples.get(template.getItem());
        if(saved==null){
            if(ItemCallbacks.method(template.getItem(),"setDamage","setDamage",ItemStack.class,int.class).getDeclaringClass()!=Item.class)
                throw fault("Unadapted AE2 wildcard metadata setter");
            saved=CatalogExamples.metadata(template);examples.put(template.getItem(),saved);
        }
        List<ItemStack> result=new ArrayList<>();
        for(ItemStack example:saved){
            ItemStack copy=template.copy();copy.setItemDamage(example.getItemDamage());result.add(copy);
        }
        return result;
    }
    void close(){entries.clear();ores.clear();examples.clear();}
    boolean same(AeWorldSource other){
        if(api!=other.api||definitions!=other.definitions||materials!=other.materials||items!=other.items||config!=other.config
                ||features!=other.features||!featureValues.equals(other.featureValues)||oreNames!=other.oreNames||oreTable!=other.oreTable
                ||catalog!=other.catalog||!entries.keySet().equals(other.entries.keySet())||ores.size()!=other.ores.size())return false;
        for(String name:entries.keySet())if(!entries.get(name).same(other.entries.get(name)))return false;
        for(int i=0;i<ores.size();i++)if(!ores.get(i).same(other.ores.get(i)))return false;return true;
    }
    Object[] anchors(){
        List<Object> anchors=new ArrayList<>();
        for(Object anchor:new Object[]{api,definitions,materials,items,config,features,oreNames,oreTable,catalog})if(anchor!=null)anchors.add(anchor);
        for(Definition entry:entries.values()){
            anchors.add(entry.definition);anchors.add(entry.optional);
            if(entry.source!=null)anchors.add(entry.source);if(entry.member!=null)anchors.add(entry.member);if(entry.item!=null)anchors.add(entry.item);
        }
        for(Ore ore:ores)if(ore.members!=null)anchors.add(ore.members);
        return anchors.toArray();
    }
    boolean feature(String name){for(Object flag:featureValues)if(((Enum<?>)flag).name().equals(name))return true;return false;}
    private boolean present(String name){Definition entry=entries.get(name);return entry!=null&&((Optional<?>)entry.optional).isPresent();}
    static String featureFor(String name){return name.equals("certusQuartzCrystalCharged")?"CertusQuartzWorldGen":name.endsWith("ProcessorPress")?"MeteoriteWorldGen":
        name.equals("fluixCrystal")?"InWorldFluix":name.equals("qESingularity")?"InWorldSingularity":"InWorldPurification";}
    ItemStack stack(String name){Definition entry=entries.get(name);if(entry==null)throw fault("Unknown native AE2 definition: "+name);return entry.stack();}

    static final class Definition{
        final Object definition,optional,source,member;final Item item;final int metadata;final boolean sourceEnabled;
        final String registryName;
        Definition(Object definition){
            if(definition==null)throw unready("Native AE2 item definition is missing");this.definition=definition;
            Object source=null,member=null,rawItem=null;int metadata=0;boolean sourceEnabled=true;
            String owner=definition.getClass().getName();
            if(owner.equals("appeng.core.features.ItemDefinition")){
                optional=field(definition,"item");rawItem=present(optional);
            }else if(owner.equals("appeng.core.features.DamagedItemDefinition")){
                optional=field(definition,"source");source=present(optional);
                if(source!=null){
                    String kind=source.getClass().getName();
                    if(kind.equals("appeng.core.features.ItemStackSrc")){
                        rawItem=field(source,"item");metadata=(Integer)field(source,"damage");sourceEnabled=(Boolean)field(source,"enabled");
                    }else if(kind.equals("appeng.core.features.MaterialStackSrc")){
                        member=exact(field(source,"src"),"appeng.items.materials.MaterialType");
                        rawItem=field(member,"itemInstance");metadata=(Integer)field(member,"damageValue");
                    }else throw fault("Unadapted native AE2 stack source: "+kind);
                }
            }else throw fault("Unadapted native AE2 item definition: "+owner);
            if(rawItem!=null&&!(rawItem instanceof Item))throw fault("Invalid native AE2 definition item");
            item=(Item)rawItem;this.source=source;this.member=member;this.metadata=metadata;this.sourceEnabled=sourceEnabled;
            registryName=item==null?null:Item.itemRegistry.getNameForObject(item);
        }
        boolean same(Definition other){return definition==other.definition&&optional==other.optional&&source==other.source&&member==other.member
                &&item==other.item&&metadata==other.metadata&&sourceEnabled==other.sourceEnabled&&Objects.equals(registryName,other.registryName);}
        ItemStack stack(){
            if(!((Optional<?>)optional).isPresent())return null;
            if(item==null)throw unready("Native AE2 material item is uninitialized");
            if(metadata<0||metadata==32767)throw unready("Native AE2 material metadata is not initialized/concrete");
            return valid(new ItemStack(item,1,metadata));
        }
    }
    static final class Ore{
        final String name;final Object id;final List<?> members;
        final List<ItemStack> templates=new ArrayList<>();
        final List<Object> identities=new ArrayList<>();final String signature;
        Ore(String name,Object id,List<?> members){this(name,id,members,true);}
        Ore(String name,Object id,List<?> members,boolean snapshot){
            this.name=name;this.id=id;this.members=members;JsonArray values=array();
            if(members!=null){if(members.size()>65536)throw fault("Native AE2 ore membership exceeds budget");
                if(snapshot)for(Object raw:members){Jobs.checkpoint();if(raw==null||raw.getClass()!=ItemStack.class)throw fault("Invalid native AE2 ore member");
                    ItemStack stack=valid((ItemStack)raw,false);identities.add(raw);templates.add(stack);values.add(RecipeRow.stackEvidence(stack));}}
            signature=snapshot?CanonicalJson.digest(values):null;
        }
        boolean same(Ore other){
            if(!Objects.equals(id,other.id)||members!=other.members||!signature.equals(other.signature)||identities.size()!=other.identities.size())return false;
            for(int i=0;i<identities.size();i++)if(identities.get(i)!=other.identities.get(i))return false;return true;
        }
    }
    static ItemStack valid(ItemStack item){
        return valid(item,true);
    }
    static ItemStack valid(ItemStack item,boolean positive){
        if(item==null||item.getClass()!=ItemStack.class||item.getItem()==null||(positive&&item.stackSize<=0)||Item.itemRegistry.getNameForObject(item.getItem())==null)throw fault("Invalid registered native AE2 stack");
        if(ItemCallbacks.method(item.getItem(),"getDamage","getDamage",ItemStack.class).getDeclaringClass()!=Item.class)throw fault("Unadapted AE2 metadata getter");
        if(item.getItemDamage()<0)throw fault("Negative native AE2 metadata requires an adapter");
        return item.copy();
    }
    private static Object present(Object value){if(!(value instanceof Optional<?>))throw fault("Unknown native AE2 optional");return ((Optional<?>)value).orNull();}
    private static Object exact(Object value,String owner){if(value==null)throw unready(owner+" is unavailable");if(!value.getClass().getName().equals(owner))throw fault("Unadapted native AE2 source: "+value.getClass().getName());return value;}
    private static Jobs.Fault unready(String message){return new Jobs.Fault("handler_not_ready",message);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","AE2 world source identity, definition or dependency changed");}
}
