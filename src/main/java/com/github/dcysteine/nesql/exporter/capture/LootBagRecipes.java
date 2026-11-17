package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Frozen opening rules. No bag opening, shuffle or player limit storage access. */
final class LootBagRecipes implements RegistryRecipes {
    private static final String ROOT="eu.usrv.enhancedlootbags.";
    private static final String HANDLER=ROOT+"integration.nei.LootBagRecipeHandler";
    private final Object registry;
    private final Item bag;
    private final Source source;
    private final List<Group> groups;
    private final SortedMap<String,Group> buffered;
    private final boolean allowFortune;
    private final String signature;
    private boolean closed;
    static boolean supports(ICraftingHandler handler){return handler.getClass().getName().equals(HANDLER);}
    LootBagRecipes(TemplateRecipeHandler handler){
        source=registrationSource(handler);registry=source.registry;bag=source.bag;
        groups=readGroups();buffered=readBuffered();allowFortune=fortune();signature=digest();
    }
    static Source registrationSource(IRecipeHandler handler){
        MagicApi.version("LootBagRecipes","enhancedlootbags");MagicApi.version("LootBagRecipes","YAMCore");
        if(!handler.getClass().getName().equals(HANDLER))throw fault("Unknown loot bag handler");return new Source(handler.getClass());
    }
    public int size(){verify();return groups.size();}
    public void verify(){
        if(closed)throw new Jobs.Fault("capture_closed","Loot bag reader is closed");
        Source current;
        try{current=new Source(source.handler);}catch(Jobs.Fault failure){Jobs.Fault changed=changed();changed.initCause(failure);throw changed;}
        if(!source.same(current))throw changed();
        for(Group group:groups)group.verifyItems();for(Group group:buffered.values())group.verifyItems();
        if(!signature.equals(digest()))throw changed();
    }
    public void close(){if(closed)return;closed=true;groups.clear();buffered.clear();source.anchors.clear();}
    public boolean capture(int index,RecipeRow row){
        verify();boolean captured;
        try{captured=captureRow(index,row);}catch(RuntimeException|Error failure){try{verify();}catch(RuntimeException|Error drift){if(failure!=drift)failure.addSuppressed(drift);}throw failure;}
        verify();return captured;
    }
    private boolean captureRow(int index,RecipeRow row){
        Group selected=groups.get(index);ItemStack input=new ItemStack(bag,1,selected.id);
        row.itemInput(0,Collections.singletonList(new RecipeRow.Ingredient(input,1,false,object("kind","wildcard","meta",false,"nbt",true))),false);
        row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().add("consume",object("kind","allocated"));
        JsonArray inventory=array(),cache=array();for(Group group:groups)inventory.add(group.json(row.facts));
        for(Map.Entry<String,Group> e:buffered.entrySet())cache.add(object("key",e.getKey(),"group",e.getValue().json(row.facts)));
        LinkedHashMap<String,ItemStack> candidates=new LinkedHashMap<>();add(candidates,selected,row.facts);
        if(selected.combine){for(Group g:groups)if(g.id==selected.trash){add(candidates,g,row.facts);break;}
            for(Map.Entry<String,Group> e:buffered.entrySet())if(e.getKey().startsWith(selected.id+"-"))add(candidates,e.getValue(),row.facts);}
        if(candidates.size()>65536)throw fault("Loot output inventory exceeds budget");
        int slot=0;for(ItemStack item:candidates.values())row.itemOutput(slot++,item,10000,object("kind","lootBag"));
        row.record.add("process",object("kind","lootBagOpening","nativeItem",ROOT+"core.items.ItemLootBag",
            "bag",row.facts.item(input),"group",selected.id,"allowFortune",allowFortune,"groups",inventory,"buffered",cache,
            "retryLimit",10,"quantityLaw","maxExclusivePlusOneWhenGreaterThanMin","budgetLaw","splitStackCount",
            "retryState","retainedSelectedAndPending","limitLaw","playerGroupIdentifierBeforeResolution",
            "debitLaw","afterCompleteOpening","emptyDraw","abortKeepBagAndPriorSpawnedDrops","shuffleLaw","nativeRandomPermutationPerOpening"));
        return true;
    }
    private static void add(Map<String,ItemStack> items,Group group,Facts facts){for(Drop d:group.drops)if(d.item!=null)items.putIfAbsent(facts.item(d.item),d.item.copy());}
    private List<Group> readGroups(){Object table=field(registry,"_mLootGroups");if(table==null)throw fault("Native server loot unavailable");
        List<Group> result=new ArrayList<>();for(Object group:list(field(table,"mLootGroups")))result.add(new Group(group));return result;}
    private SortedMap<String,Group> readBuffered(){Object source=field(registry,"_mBufferedLootGroups");
        if(!(source instanceof Map)||((Map<?,?>)source).size()>65536)throw fault("Invalid buffered loot groups");
        SortedMap<String,Group> result=new TreeMap<>();for(Map.Entry<?,?> e:((Map<?,?>)source).entrySet()){
            if(!(e.getKey() instanceof String))throw fault("Invalid loot cache key");result.put((String)e.getKey(),new Group(e.getValue()));}return result;}
    private boolean fortune(){return (Boolean)field(field(type(ROOT+"EnhancedLootBags"),null,"ELBCfg"),"AllowFortuneBags");}
    private String digest(){JsonArray g=array(),b=array();for(Group group:readGroups())g.add(group.json(null));
        for(Map.Entry<String,Group> e:readBuffered().entrySet())b.add(object("key",e.getKey(),"group",e.getValue().json(null)));
        return CanonicalJson.digest(object("groups",g,"buffered",b,"allowFortune",fortune()));}
    private static List<?> list(Object value){if(value==null)return Collections.emptyList();if(!(value instanceof List)||((List<?>)value).size()>65536)throw fault("Loot inventory exceeds budget");return (List<?>)value;}
    private static int integer(Object target,String name){return ((Number)field(target,name)).intValue();}
    private static String text(Object target,String name){return (String)field(target,name);}
    private static Jobs.Fault fault(String message){return new Jobs.Fault("recipe_unsupported",message);}
    private static Jobs.Fault changed(){return new Jobs.Fault("recipe_changed","Loot configuration, native sources or resolved items changed");}
    static final class Source {
        final Class<?> handler;final Object registry;final Item bag;final List<Object> anchors=new ArrayList<>();
        Source(Class<?> handler){
            this.handler=handler;anchors.add(handler);
            registry=required(field(type(ROOT+"EnhancedLootBags"),null,"LootGroupHandler"),ROOT+"core.LootGroupsHandler");anchors.add(registry);
            Object item=required(field(type(ROOT+"core.LootGroupsHandler"),null,"mLootBagItem"),ROOT+"core.items.ItemLootBag");bag=(Item)item;anchors.add(bag);
            if(field(bag,"_mLGHandler")!=registry||Item.itemRegistry.getNameForObject(bag)==null)throw fault("Loot bag registry binding differs");
            Object cfg=required(field(type(ROOT+"EnhancedLootBags"),null,"ELBCfg"),ROOT+"config.ELBConfig");anchors.add(cfg);
            Object groups=required(field(registry,"_mLootGroups"),ROOT+"core.serializer.LootGroups");anchors.add(groups);
            List<?> table=sequence(field(groups,"mLootGroups"));anchors.add(table);for(Object group:table)group(group);
            Object raw=required(field(registry,"_mBufferedLootGroups"),HashMap.class.getName());anchors.add(raw);Map<?,?> cache=(Map<?,?>)raw;
            if(cache.size()>65536)throw fault("Loot buffered groups exceed budget");
            List<String> keys=new ArrayList<>();for(Object key:cache.keySet()){if(!(key instanceof String))throw fault("Unknown loot cache key");keys.add((String)key);}Collections.sort(keys);
            for(String key:keys){anchors.add(key);group(cache.get(key));}
        }
        private void group(Object group){
            required(group,ROOT+"core.serializer.LootGroups$LootGroup");anchors.add(group);
            List<?> drops=sequence(field(group,"mDrops"));anchors.add(drops);
            for(Object drop:drops){required(drop,ROOT+"core.serializer.LootGroups$LootGroup$Drop");anchors.add(drop);if(anchors.size()>262144)throw fault("Loot source bindings exceed budget");}
        }
        boolean same(Source other){if(anchors.size()!=other.anchors.size())return false;for(int i=0;i<anchors.size();i++)if(anchors.get(i)!=other.anchors.get(i))return false;return true;}
        private static Object required(Object value,String className){if(value==null)throw new Jobs.Fault("handler_not_ready","Native loot source unavailable: "+className);if(!value.getClass().getName().equals(className))throw fault("Unadapted loot source: "+value.getClass().getName());return value;}
        private static List<?> sequence(Object value){required(value,ArrayList.class.getName());if(((List<?>)value).size()>65536)throw fault("Loot list exceeds budget");return (List<?>)value;}
    }
    private static final class Group {
        final int id,min,max,trash,cachedWeight,rarity;final String name;final boolean combine;final List<Drop> drops=new ArrayList<>();
        Group(Object source){if(source==null||!source.getClass().getName().equals(ROOT+"core.serializer.LootGroups$LootGroup"))throw fault("Unknown native loot group");
            id=integer(source,"mGroupID");min=integer(source,"mMinItems");max=integer(source,"mMaxItems");trash=integer(source,"mTrashGroup");
            cachedWeight=integer(source,"mMaxWeight");rarity=integer(source,"mRarity");name=text(source,"mGroupName");combine=(Boolean)field(source,"mCombineWithTrash");
            for(Object d:list(field(source,"mDrops")))drops.add(new Drop(d));}
        JsonObject json(Facts facts){JsonArray entries=array();for(Drop d:drops)entries.add(d.json(facts));
            return object("id",id,"name",name,"minItems",min,"maxItems",max,"trash",trash,"combine",combine,"cachedWeight",cachedWeight,"rarity",rarity,"drops",entries);}
        void verifyItems(){for(Drop drop:drops)if(drop.item!=null&&Item.itemRegistry.getObject(drop.registry)!=drop.item.getItem())throw changed();}
    }
    private static final class Drop {
        final String descriptor,identifier,nbt,group,registry;final int amount,weight,limit,stackLimit;final boolean random;final ItemStack item;
        Drop(Object source){if(source==null||!source.getClass().getName().equals(ROOT+"core.serializer.LootGroups$LootGroup$Drop"))throw fault("Unknown native loot drop");
            descriptor=text(source,"mItemName");identifier=text(source,"mDropID");nbt=text(source,"mTag");String g=text(source,"mItemGroup");group=g==null?"":g;
            amount=integer(source,"mAmount");weight=integer(source,"mChance");limit=integer(source,"mLimitedDropCount");random=(Boolean)field(source,"mIsRandomAmount");
            // The pinned resolver allocates owned stacks/NBT, without group, RNG or storage writes.
            // Parse without its eager verification so unknown item callbacks are audited first.
            Object parsed=invoke(type("eu.usrv.yamcore.auxiliary.ItemDescriptor"),null,"fromString",new Class<?>[]{String.class,boolean.class},descriptor,false);
            Item target=parsed==null?null:(Item)invoke(parsed.getClass(),parsed,"getItem",new Class<?>[0]);
            if(target!=null)LootBagItems.audit(target);
            ItemStack resolved=(ItemStack)invoke(source.getClass(),source,"getItemStack",new Class<?>[]{int.class},1);item=resolved==null?null:resolved.copy();
            registry=item==null?null:Item.itemRegistry.getNameForObject(item.getItem());stackLimit=item==null?0:item.getMaxStackSize();
            if(item!=null&&(item.getItem()!=target||registry==null||stackLimit<1))throw fault("Invalid native loot item");}
        JsonObject json(Facts facts){return object("identifier",identifier,"descriptor",descriptor,"nbt",nbt,"itemGroup",group,"amount",amount,"weight",weight,"limit",limit,"random",random,
            "resolved",item==null?null:facts==null?Identity.item(Item.itemRegistry.getNameForObject(item.getItem()),net.minecraft.init.Items.feather.getDamage(item),TypedNbt.encode(item.getTagCompound())):facts.item(item),
            "resolvedNbt",item==null?null:TypedNbt.encode(item.getTagCompound()),"stackLimit",item==null?null:stackLimit);}
    }
}
