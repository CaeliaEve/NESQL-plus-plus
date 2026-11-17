package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.*;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;

/** Installed upgrade table, native item classes and source-bound codec observations. */
final class MatterManipulatorSource implements AutoCloseable {
    static final String ROOT=MatterManipulatorCodec.ROOT,ITEM=ROOT+"common.items.manipulator.ItemMatterManipulator",
        META=ROOT+"common.items.MetaItem",UPGRADES=ROOT+"common.items.MMUpgrades",RECIPE=ROOT+"common.items.RecipeInstallUpgrade";
    final List<Object> upgrades=new ArrayList<>();
    final List<Item> manipulators=new ArrayList<>(),metaItems=new ArrayList<>();
    final List<Branch> branches=new ArrayList<>();
    private List<ItemStack> itemList;
    private List<?> crafting;
    private final List<ItemStack> borrowed=new ArrayList<>();
    private final Map<Item,List<State>> states=new IdentityHashMap<>();
    private final Map<Object,List<ItemStack>> componentSamples=new IdentityHashMap<>();
    private final SortedMap<Integer,Object> mappings=new TreeMap<>();
    private Object table,defaultUpgrade,registeredRecipe;
    private MatterManipulatorCodec codec;
    private NativeDefinitionGraph graph;
    private boolean closed,registered;
    private int itemCount;
    private int specimenCount;
    private long specimenBytes;

    static Object[] registrationAnchors(){
        MagicApi.version("MatterManipulatorSource","matter-manipulator");
        CraftingManager manager=CraftingManager.getInstance();
        if(manager==null||manager.getClass()!=CraftingManager.class)throw new Jobs.Fault("handler_not_ready","Matter crafting manager is unavailable");
        Object raw=manager.getRecipeList();
        if(raw==null||raw.getClass()!=ArrayList.class||((List<?>)raw).size()>1048576)
            throw fault("Unknown Matter crafting registration backing");
        Object recipe=field(type(RECIPE),null,"INSTANCE");
        if(recipe==null||recipe.getClass()!=type(RECIPE))throw new Jobs.Fault("handler_not_ready","Matter native recipe singleton is unavailable");
        boolean registered=containsIdentity((List<?>)raw,recipe);
        List<Object> anchors=new ArrayList<>(Arrays.asList(type(MatterManipulatorRecipes.HANDLER),manager,raw,recipe,Boolean.valueOf(registered)));
        if(registered){
            Object table=field(type(UPGRADES),null,"UPGRADES_BY_META");
            if(table==null||table.getClass()!=type("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap"))throw fault("Unknown Matter upgrade map backing");
            if(!ItemList.loadFinished||ItemList.items==null)throw new Jobs.Fault("handler_not_ready","Matter native content variants are not ready");
            if(ItemList.items.getClass()!=ArrayList.class||ItemList.items.size()>1048576)throw fault("Unknown Matter item-list backing or budget");
            anchors.add(table);anchors.add(ItemList.items);anchors.add(Item.itemRegistry);
        }
        return anchors.toArray();
    }

    MatterManipulatorSource(){
        try{
            Object[] anchors=registrationAnchors();crafting=(List<?>)anchors[2];registeredRecipe=anchors[3];registered=(Boolean)anchors[4];
            if(!registered)return;
            if(!ItemList.loadFinished||ItemList.items==null)throw new Jobs.Fault("handler_not_ready","Matter upgrade specimens require finished native content variants");
            itemList=ItemList.items;if(itemList.getClass()!=ArrayList.class||itemList.size()>1048576)throw fault("Unknown Matter item-list backing or budget");itemCount=itemList.size();
            upgrades.addAll(Arrays.asList(type(UPGRADES).getEnumConstants()));
            if(upgrades.isEmpty()||upgrades.size()>16)throw fault("Matter upgrade enum exceeds its bitset witness budget");
            for(Object upgrade:upgrades){int bit=(Integer)field(upgrade,"bit");if(bit<0||bit>=65536)throw fault("Matter upgrade bit exceeds budget");}
            manipulators.addAll(census(ITEM));metaItems.addAll(census(META));
            for(ItemStack stack:itemList)if(stack!=null&&stack.getItem()!=null&&(type(ITEM).isInstance(stack.getItem())||type(META).isInstance(stack.getItem()))){NativeStackAudit.audit(stack);borrowed.add(stack);}
            table=field(type(UPGRADES),null,"UPGRADES_BY_META");readMappings(mappings);defaultUpgrade=field(table,"defRetValue");admitUpgrade(defaultUpgrade);
            Map<String,String[]> shapes=new HashMap<>();
            shapes.put(table.getClass().getName(),new String[]{"key","value","n","size","containsNullKey","defRetValue"});
            shapes.put(UPGRADES,new String[0]);shapes.put(ITEM+"$ManipulatorTier",new String[0]);shapes.put(RECIPE,new String[0]);
            shapes.put("java.util.Collections$UnmodifiableSet",new String[]{"c"});
            shapes.put("it.unimi.dsi.fastutil.objects.ObjectOpenHashSet",new String[]{"key","n","size","containsNull"});
            graph=new NativeDefinitionGraph(this::roots,shapes);
            codec=new MatterManipulatorCodec(upgrades);
            for(Item item:manipulators){Object tier=field(type(ITEM),item,"tier");
                if(tier==null||tier.getClass()!=type(ITEM+"$ManipulatorTier"))throw fault("Unknown Matter tier object");
                List<Object> allowed=allowed(tier);allowed.sort(Comparator.comparingInt(v->((Enum<?>)v).ordinal()));
                for(Object upgrade:allowed){branches.add(new Branch(item,tier,upgrade,allowed));if(branches.size()>65536)throw fault("Matter native branch budget exceeded");}
            }
        }catch(RuntimeException|Error error){close();throw error;}
    }
    private static List<Item> census(String owner){
        List<Item> result=new ArrayList<>();for(Object raw:Item.itemRegistry)if(type(owner).isInstance(raw)){
            NativeStackAudit.identityOwner(raw);result.add((Item)raw);if(result.size()>65536)throw fault("Matter registered item census exceeds budget");}
        result.sort(Comparator.comparing(Item.itemRegistry::getNameForObject));return result;
    }
    private static boolean containsIdentity(List<?> list,Object target){for(Object entry:list)if(entry==target)return true;return false;}
    private void admitUpgrade(Object raw){if(raw!=null&&(raw.getClass()!=type(UPGRADES)||!containsIdentity(upgrades,raw)))throw fault("Unknown Matter upgrade table value");}
    private void readMappings(Map<Integer,Object> result){
        if(table==null||table.getClass()!=type("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap"))throw fault("Unknown Matter upgrade map backing");
        int n=(Integer)field(table,"n"),size=(Integer)field(table,"size");
        int[] keys=(int[])field(table,"key");Object[] values=(Object[])field(table,"value");
        if(n<1||n>1048576||keys.length!=n+1||values.length!=n+1||size<0||size>65536)throw fault("Matter upgrade map exceeds budget or has invalid storage");
        if(Boolean.TRUE.equals(field(table,"containsNullKey"))){admitUpgrade(values[n]);result.put(0,values[n]);}
        for(int at=n;at-->0;)if(keys[at]!=0){admitUpgrade(values[at]);if(result.containsKey(keys[at]))throw fault("Duplicate native Matter metadata mapping");result.put(keys[at],values[at]);}
        if(result.size()!=size)throw fault("Matter metadata mapping count changed while reading");
    }
    private List<Object> allowed(Object tier){
        Object wrapper=field(tier,"allowedUpgrades");
        if(wrapper==null||!wrapper.getClass().getName().equals("java.util.Collections$UnmodifiableSet"))throw fault("Unknown Matter allowed-upgrade set wrapper");
        Object backing=field(wrapper,"c");if(backing==null||backing.getClass()!=type("it.unimi.dsi.fastutil.objects.ObjectOpenHashSet"))throw fault("Unknown Matter allowed-upgrade set backing");
        Object[] keys=(Object[])field(backing,"key");int n=(Integer)field(backing,"n"),size=(Integer)field(backing,"size");
        if(n<1||n>65536||keys.length!=n+1||size<0||size>65536)throw fault("Matter allowed set exceeds budget");
        List<Object> result=new ArrayList<>();for(Object key:keys)if(key!=null){admitUpgrade(key);result.add(key);}
        int actual=result.size()+(Boolean.TRUE.equals(field(backing,"containsNull"))?1:0);
        if(actual!=size)throw fault("Matter allowed set changed while reading");return result;
    }
    private Object[] roots(){
        Object[] registration=registrationAnchors();
        List<Object> roots=new ArrayList<>();roots.add(field(type(UPGRADES),null,"UPGRADES_BY_META"));roots.add(field(type(RECIPE),null,"INSTANCE"));
        roots.add(registration[4]);roots.add(borrowed);
        for(Object upgrade:upgrades){roots.add(upgrade);roots.add(field(upgrade,"id"));roots.add(field(upgrade,"bit"));roots.add(field(upgrade,"providesCaps"));}
        for(Item item:census(ITEM)){roots.add(item);roots.add(Item.itemRegistry.getNameForObject(item));Object tier=field(type(ITEM),item,"tier");roots.add(tier);roots.add(field(tier,"allowedUpgrades"));}
        for(Item item:census(META)){roots.add(item);roots.add(Item.itemRegistry.getNameForObject(item));}
        if(roots.size()>4096)throw fault("Matter source binding exceeds root budget");return roots.toArray();
    }
    JsonObject handlerExclusion(){alive();return registered?null:object("reason","native_recipe_not_registered","native",RECIPE,"source","CraftingManager.getRecipeList identity census");}
    Object lookup(int metadata){return mappings.containsKey(metadata)?mappings.get(metadata):defaultUpgrade;}
    List<ItemStack> components(Object target){
        alive();List<ItemStack> cached=componentSamples.get(target);if(cached!=null)return cached;
        Map<String,ItemStack> result=new TreeMap<>();
        for(ItemStack sample:borrowed)if(type(META).isInstance(sample.getItem())&&lookup(net.minecraft.init.Items.feather.getDamage(sample))==target)put(result,sample);
        for(Item item:metaItems)for(Map.Entry<Integer,Object> mapping:mappings.entrySet())if(mapping.getValue()==target)put(result,new ItemStack(item,1,mapping.getKey()));
        if(defaultUpgrade==target&&result.isEmpty()){
            int metadata=0;while(mappings.containsKey(metadata)){if(++metadata>65536)throw fault("Matter default metadata witness exceeds budget");}
            for(Item item:metaItems)put(result,new ItemStack(item,1,metadata));
        }
        List<ItemStack> samples=new ArrayList<>(result.values());componentSamples.put(target,samples);return samples;
    }
    List<State> states(Item item){
        alive();List<State> cached=states.get(item);if(cached!=null)return cached;
        Map<String,ItemStack> samples=new TreeMap<>();Map<String,String> origins=new HashMap<>();
        for(ItemStack sample:borrowed)if(sample.getItem()==item){put(samples,sample);origins.put(identity(sample),"nativeItemList");}
        ItemStack base=new ItemStack(item,1,0);put(samples,base);origins.putIfAbsent(identity(base),"registeredItemClass");
        for(int mask=0;mask<(1<<upgrades.size());mask++){
            BitSet bits=new BitSet();for(int bit=0;bit<upgrades.size();bit++)if((mask&(1<<bit))!=0)bits.set((Integer)field(upgrades.get(bit),"bit"));
            byte[] flags=new byte[bits.length()];for(int bit=bits.nextSetBit(0);bit>=0;bit=bits.nextSetBit(bit+1))flags[bit]=1;
            ItemStack witness=new ItemStack(item,1,0);NBTTagCompound tag=new NBTTagCompound();tag.setByte("jv",(byte)1);tag.setByteArray("installedUpgrades",flags);witness.setTagCompound(tag);
            put(samples,witness);origins.putIfAbsent(identity(witness),"declaredBitsetWitness");
        }
        List<State> values=new ArrayList<>();
        for(Map.Entry<String,ItemStack> entry:samples.entrySet()){
            NBTTagCompound normalized=codec.normalize(entry.getValue());
            reserve(CanonicalJson.bytes(TypedNbt.encode(normalized)).length,false);
            values.add(new State(entry.getValue(),normalized,origins.get(entry.getKey())));
        }
        states.put(item,values);return values;
    }
    JsonObject describe(Branch branch,List<State> samples,Facts facts){
        JsonArray allowed=array(),all=array(),meta=array(),mapping=array(),observations=array();
        for(Object upgrade:upgrades)all.add(upgrade(upgrade));
        for(Object upgrade:branch.allowed)allowed.add(upgrade(upgrade));for(Item item:metaItems)meta.add(value(Item.itemRegistry.getNameForObject(item)));
        for(Map.Entry<Integer,Object> entry:mappings.entrySet())mapping.add(object("metadata",entry.getKey(),"upgrade",entry.getValue()==null?null:upgrade(entry.getValue())));
        for(State state:samples)observations.add(object("id",facts.item(state.input),"normalized",TypedNbt.encode(state.normalized),"origin",state.origin));
        return object("kind","matterManipulatorUpgrade","nativeRecipe",RECIPE,"version","0.0.51-GTNH","codec","mm0051GsonV1",
            "tier",((Enum<?>)branch.tier).ordinal(),"manipulator",Item.itemRegistry.getNameForObject(branch.item),"upgrade",upgrade(branch.upgrade),
            "upgrades",all,"allowed",allowed,"metaItems",meta,"mappings",mapping,"defaultUpgrade",defaultUpgrade==null?null:upgrade(defaultUpgrade),"states",observations);
    }
    private static JsonObject upgrade(Object value){return object("name",((Enum<?>)value).name(),"ordinal",((Enum<?>)value).ordinal(),"id",field(value,"id"),"bit",field(value,"bit"),"providesCaps",field(value,"providesCaps"));}
    private void put(Map<String,ItemStack> target,ItemStack original){
        Jobs.checkpoint();NativeStackAudit.audit(original);String id=identity(original);if(target.containsKey(id))return;
        reserve(CanonicalJson.bytes(TypedNbt.encode(original.getTagCompound())).length,true);
        ItemStack sample=original.copy();sample.stackSize=1;target.put(id,sample);
    }
    private void reserve(long bytes,boolean specimen){
        specimenBytes+=bytes;if(specimen)specimenCount++;
        if(specimenCount>65536||specimenBytes>32L*1024*1024)throw fault("Matter cumulative specimen/normalization budget exceeded");
    }
    private static String identity(ItemStack sample){return com.github.dcysteine.nesql.exporter.source.Identity.item(Item.itemRegistry.getNameForObject(sample.getItem()),net.minecraft.init.Items.feather.getDamage(sample),TypedNbt.encode(sample.getTagCompound()));}
    void verify(){
        alive();Object[] anchors=registrationAnchors();if(anchors[2]!=crafting||anchors[3]!=registeredRecipe||(Boolean)anchors[4]!=registered)throw changed();
        if(!registered)return;
        if(!ItemList.loadFinished||ItemList.items!=itemList||itemList.size()!=itemCount)throw changed();graph.verifyRoots();codec.verify();
    }
    void finishVerification(){
        verify();if(!registered)return;List<ItemStack> current=new ArrayList<>();for(ItemStack sample:itemList)if(sample!=null&&sample.getItem()!=null&&(type(ITEM).isInstance(sample.getItem())||type(META).isInstance(sample.getItem())))current.add(sample);
        if(current.size()!=borrowed.size())throw changed();for(int i=0;i<current.size();i++)if(current.get(i)!=borrowed.get(i))throw changed();
        graph.verify();codec.finishVerification();
        for(List<State> rows:states.values())for(State row:rows)if(!row.signature.equals(CanonicalJson.digest(TypedNbt.encode(codec.normalize(row.input)))))throw changed();
    }
    private void alive(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Matter source reader is closed");}
    public void close(){closed=true;if(graph!=null)graph.close();if(codec!=null)codec.close();upgrades.clear();manipulators.clear();metaItems.clear();branches.clear();borrowed.clear();states.clear();componentSamples.clear();mappings.clear();itemList=null;crafting=null;table=null;defaultUpgrade=null;registeredRecipe=null;}
    static final class Branch{final Item item;final Object tier,upgrade;final List<Object> allowed;Branch(Item item,Object tier,Object upgrade,List<Object> allowed){this.item=item;this.tier=tier;this.upgrade=upgrade;this.allowed=new ArrayList<>(allowed);}}
    static final class State{final ItemStack input;final NBTTagCompound normalized;final String origin,signature;State(ItemStack input,NBTTagCompound normalized,String origin){this.input=input.copy();this.normalized=(NBTTagCompound)normalized.copy();this.origin=origin;signature=CanonicalJson.digest(TypedNbt.encode(normalized));}}
    private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
    private static Jobs.Fault changed(){return new Jobs.Fault("environment_changed","Matter native registration, mapping, item census or serialized state changed");}
}
