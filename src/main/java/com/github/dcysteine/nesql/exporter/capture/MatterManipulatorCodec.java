package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.item.*;
import net.minecraft.nbt.*;

/** Pinned 0.0.51 codec on owned state. The native Gson's caches are never used. */
final class MatterManipulatorCodec implements AutoCloseable {
    static final String ROOT="com.recursive_pineapple.matter_manipulator.",STATE=ROOT+"common.items.manipulator.MMState";
    private static final String PERSIST=ROOT+"common.persist.",SPEC=ROOT+"common.building.BlockSpec",WEIGHTED=ROOT+"common.data.WeightedSpecList";
    private final List<Object> upgrades;
    private Gson owned;
    private Object nativeGson,architecture;
    private NativeDefinitionGraph graph;
    private final Map<String,String[]> shapes=new HashMap<>();
    private boolean closed;

    MatterManipulatorCodec(List<Object> upgrades){
        this.upgrades=upgrades;
        try{
            Field capability=type(STATE).getDeclaredField("upgradeProvidedCapabilities");
            if(Modifier.isTransient(capability.getModifiers()))throw fault("Matter codec is not the installed 0.0.51 state format");
            architecture=field(type(ROOT+"common.utils.Mods"),null,"ArchitectureCraft");
            if(!Boolean.TRUE.equals(field(architecture,"checkedMod")))throw new Jobs.Fault("handler_not_ready","Matter content variants must initialize their native optional-mod readiness first");
            nativeGson=field(type(STATE),null,"GSON");
            if(nativeGson==null||nativeGson.getClass()!=Gson.class)throw fault("Unadapted Matter Gson owner");
            owned=new Gson();List<?> defaults=factories(owned),actual=factories(nativeGson);
            Map<String,String> adapters=new HashMap<>();
            adapters.put("cpw.mods.fml.common.registry.GameRegistry$UniqueIdentifier",PERSIST+"UIDJsonAdapter");
            adapters.put(NBTTagCompound.class.getName(),PERSIST+"NBTJsonAdapter");
            adapters.put("net.minecraftforge.common.util.ForgeDirection",PERSIST+"StaticEnumJsonAdapter");
            adapters.put(WEIGHTED,PERSIST+"WeightedListJsonAdapter");
            List<Object> ordinary=new ArrayList<>();Set<String> found=new HashSet<>();
            for(Object factory:actual){
                String name=factory.getClass().getName();
                if(name.equals("com.google.gson.TreeTypeAdapter$SingleTypeFactory")){
                    Object raw=field(factory,"exactType");
                    if(raw==null||raw.getClass()!=TypeToken.class||field(factory,"hierarchyType")!=null||!Boolean.TRUE.equals(field(factory,"matchRawType")))throw fault("Unknown Matter Gson type binding");
                    String target=((TypeToken<?>)raw).getRawType().getName(),expected=adapters.get(target);
                    Object serializer=field(factory,"serializer"),deserializer=field(factory,"deserializer");
                    if(expected==null||!found.add(target)||serializer==null||serializer!=deserializer||serializer.getClass()!=type(expected))throw fault("Unknown Matter custom serialization callback");
                    shapes.put(name,new String[]{"exactType","matchRawType","hierarchyType","serializer","deserializer"});
                    shapes.put(expected,expected.endsWith("StaticEnumJsonAdapter")?new String[]{"clazz","values"}:new String[0]);
                    if(expected.endsWith("StaticEnumJsonAdapter")&&field(serializer,"clazz")!=type(target))throw fault("Matter direction codec changed its enum type");
                }else{ordinary.add(factory);shapes.put(name,new String[0]);}
            }
            if(found.size()!=4||ordinary.size()!=defaults.size())throw fault("Matter codec factories differ from the pinned definition");
            IdentityHashMap<Object,Object> definitions=new IdentityHashMap<>();
            for(int i=0;i<defaults.size();i++)admitDefault(ordinary.get(i),defaults.get(i),definitions,0);
            for(String flag:Arrays.asList("serializeNulls","htmlSafe","generateNonExecutableJson","prettyPrinting"))
                if(!Objects.equals(field(nativeGson,flag),field(owned,flag)))throw fault("Matter Gson settings differ from native defaults");
            // Default factories and primitive adapters belong to the owned Gson.
            // Only the four admitted stateless native adapters are borrowed.
            List<Object> bound=new ArrayList<>();int defaultIndex=0;
            for(Object factory:actual)bound.add(factory.getClass().getName().equals("com.google.gson.TreeTypeAdapter$SingleTypeFactory")?factory:defaults.get(defaultIndex++));
            Field factories=Gson.class.getDeclaredField("factories");factories.setAccessible(true);
            factories.set(owned,Collections.unmodifiableList(bound));
            shapes.put(Gson.class.getName(),new String[0]);shapes.put(TypeToken.class.getName(),new String[]{"rawType","type","hashCode"});
            shapes.put(Class.class.getName(),new String[0]);shapes.put(architecture.getClass().getName(),new String[0]);
            shapes.put("java.util.Collections$UnmodifiableRandomAccessList",new String[]{"list"});
            shapes.put("java.util.Collections$UnmodifiableList",new String[]{"list"});
            shapes.put("java.util.Collections$EmptyList",new String[0]);shapes.put("java.util.Collections$EmptyMap",new String[0]);
            shapes.put("net.minecraftforge.common.util.ForgeDirection",new String[0]);
            // The reflective factory must have the native default name/exclusion
            // policy and no custom object constructors.
            for(Object factory:ordinary)if(factory.getClass().getName().equals("com.google.gson.internal.bind.ReflectiveTypeAdapterFactory")){
                Object policy=field(factory,"fieldNamingPolicy"),excluder=field(factory,"excluder"),constructors=field(factory,"constructorConstructor");
                if(policy!=com.google.gson.FieldNamingPolicy.IDENTITY||!emptyJdk(field(constructors,"instanceCreators")))throw fault("Matter codec has an unadapted field/constructor policy");
                for(String field:new String[]{"serializationStrategies","deserializationStrategies"})if(!emptyJdk(field(excluder,field)))throw fault("Matter codec has custom exclusion callbacks");
                Object defaultExcluder=field(type("com.google.gson.internal.Excluder"),null,"DEFAULT");
                for(String field:new String[]{"version","modifiers","serializeInnerClasses","requireExpose"})if(!Objects.equals(field(excluder,field),field(defaultExcluder,field)))throw fault("Matter codec excludes different native fields");
                shapes.put(factory.getClass().getName(),new String[]{"fieldNamingPolicy","excluder","constructorConstructor"});
                shapes.put(policy.getClass().getName(),new String[0]);shapes.put(excluder.getClass().getName(),new String[]{"version","modifiers","serializeInnerClasses","requireExpose","serializationStrategies","deserializationStrategies"});
                shapes.put(constructors.getClass().getName(),new String[]{"instanceCreators"});
            }
            graph=new NativeDefinitionGraph(this::roots,shapes);
        }catch(ReflectiveOperationException error){close();throw fault("Missing pinned Matter codec member: "+error);}
        catch(RuntimeException|Error error){close();throw error;}
    }
    /** Compare only Gson 2.2.4's default factory definitions, never its caches. */
    private void admitDefault(Object actual,Object expected,IdentityHashMap<Object,Object> seen,int depth)throws ReflectiveOperationException{
        Jobs.checkpoint();if(depth>24||seen.size()>4096)throw fault("Matter Gson definition exceeds budget");
        if(actual==null||expected==null){if(actual!=expected)throw fault("Matter default codec member changed");return;}
        Class<?> owner=actual.getClass();
        if(emptyJdk(actual)&&emptyJdk(expected)&&((actual instanceof Map)==(expected instanceof Map))){shapes.put(owner.getName(),new String[0]);return;}
        if(owner!=expected.getClass())throw fault("Unknown Matter default serialization callback");
        if(owner==String.class||owner==Boolean.class||owner==Character.class||owner==Byte.class||owner==Short.class||owner==Integer.class||owner==Long.class||owner==Float.class||owner==Double.class){
            if(!actual.equals(expected))throw fault("Matter default codec setting changed");return;
        }
        if(owner==Class.class||actual instanceof Enum){if(actual!=expected)throw fault("Matter default codec type changed");shapes.put(owner.getName(),new String[0]);return;}
        if(owner==Gson.class){if(actual!=nativeGson||expected!=owned)throw fault("Unknown Matter primitive adapter context");return;}
        if(owner==ArrayList.class||owner==HashMap.class||owner.getName().equals("java.util.Collections$EmptyList")||owner.getName().equals("java.util.Collections$EmptyMap")){
            if(!emptyJdk(actual)||!emptyJdk(expected))throw fault("Matter codec has custom construction/exclusion callbacks");
            shapes.put(owner.getName(),new String[0]);return;
        }
        if(!owner.getName().startsWith("com.google.gson."))throw fault("Unknown Matter default codec dependency: "+owner.getName());
        if(seen.containsKey(actual)){if(seen.get(actual)!=expected)throw fault("Matter codec dependency alias changed");return;}seen.put(actual,expected);
        List<String> members=new ArrayList<>();
        for(Class<?> c=owner;c!=null&&c!=Object.class;c=c.getSuperclass())for(Field member:c.getDeclaredFields())if(!Modifier.isStatic(member.getModifiers())){
            member.setAccessible(true);members.add(member.getName());admitDefault(member.get(actual),member.get(expected),seen,depth+1);
        }
        shapes.put(owner.getName(),members.toArray(new String[0]));
    }
    private Object[] roots(){
        return new Object[]{field(type(STATE),null,"GSON"),factories(nativeGson),architecture,
            field(architecture,"checkedMod"),field(architecture,"modLoaded"),
            field(nativeGson,"serializeNulls"),field(nativeGson,"htmlSafe"),field(nativeGson,"generateNonExecutableJson"),field(nativeGson,"prettyPrinting")};
    }
    private static List<?> factories(Object gson){
        Object raw=field(gson,"factories");String name=raw==null?"":raw.getClass().getName();
        if(!Arrays.asList("java.util.ArrayList","java.util.Collections$UnmodifiableList","java.util.Collections$UnmodifiableRandomAccessList").contains(name)||((List<?>)raw).size()>128)throw fault("Unknown Matter Gson factory backing");
        return (List<?>)raw;
    }
    private static boolean emptyJdk(Object value){
        if(value==null)return false;Class<?> c=value.getClass();
        if(c==HashMap.class||c.getName().equals("java.util.Collections$EmptyMap"))return ((Map<?,?>)value).isEmpty();
        if(c==ArrayList.class||c.getName().equals("java.util.Collections$EmptyList"))return ((List<?>)value).isEmpty();return false;
    }
    NBTTagCompound normalize(ItemStack original){
        verify();NativeStackAudit.audit(original);ItemStack copy=original.copy();
        NBTTagCompound tag=copy.hasTagCompound()?copy.getTagCompound():new NBTTagCompound();
        JsonObject json=((JsonElement)invoke(type(ROOT+"common.utils.MMUtils"),null,"toJsonObject",new Class<?>[]{NBTBase.class},tag)).getAsJsonObject();
        // Installed 0.0.51 uses version 0 when absent; this is NOT the later
        // version-2/base64 implementation in the current upstream checkout.
        int version=json.has("jv")?json.get("jv").getAsInt():0;
        if(version==0){JsonElement config=json.get("config");if(config!=null&&config.isJsonObject())for(String key:new String[]{"corners","edges","faces","volumes","cables","replaceWhitelist","replaceWith"})config.getAsJsonObject().remove(key);version=1;}
        json.addProperty("jv",version);Object state=owned.fromJson(json,type(STATE));
        if(state==null)throw fault("Native Matter state decoder returned null");
        if(field(state,"config")==null)set(state,"config",TinkerRecipes.construct(type(ROOT+"common.items.manipulator.MMConfig"),new Class<?>[0]));
        Object raw=field(state,"installedUpgrades");if(raw==null||raw.getClass()!=BitSet.class)throw fault("Native Matter BitSet is null or has an unknown owner");
        BitSet bits=(BitSet)raw;if(bits.length()>65536)throw fault("Matter BitSet exceeds state budget");
        int capabilities=(Integer)field(state,"upgradeProvidedCapabilities");
        for(Object upgrade:upgrades)if(bits.get((Integer)field(upgrade,"bit")))capabilities|=(Integer)field(upgrade,"providesCaps");
        set(state,"upgradeProvidedCapabilities",capabilities);
        auditWeighted(state);
        NBTTagCompound normalized=(NBTTagCompound)invoke(type(ROOT+"common.utils.MMUtils"),null,"toNbt",new Class<?>[]{JsonElement.class},owned.toJsonTree(state));
        NativeStackAudit.nbt(normalized);return normalized;
    }
    static BitSet bits(NBTTagCompound state){
        BitSet result=new BitSet();if(state==null||!state.hasKey("installedUpgrades"))return result;
        NBTBase raw=state.getTag("installedUpgrades");List<Integer> values=new ArrayList<>();
        if(raw instanceof NBTTagByteArray)for(byte value:((NBTTagByteArray)raw).func_150292_c())values.add((int)value);
        else if(raw instanceof NBTTagIntArray)for(int value:((NBTTagIntArray)raw).func_150302_c())values.add(value);
        else if(raw instanceof NBTTagList){List<?> list=(List<?>)field(raw,"tagList");
            for(Object entry:list){NBTBase value=(NBTBase)entry;
                if(value instanceof NBTTagString)values.add(Integer.parseInt(((NBTTagString)value).func_150285_a_()));
                else if(value instanceof NBTTagFloat||value instanceof NBTTagDouble)values.add((int)((NBTBase.NBTPrimitive)value).func_150286_g());
                else if(value instanceof NBTBase.NBTPrimitive)values.add((int)((NBTBase.NBTPrimitive)value).func_150291_c());
                else throw fault("Native Matter BitSet expects a primitive array");
            }
        }else throw fault("Native Matter BitSet expects an array, not base64 text");
        if(values.size()>65536)throw fault("Matter BitSet exceeds state budget");
        for(int i=0;i<values.size();i++)if(values.get(i)!=0)result.set(i);return result;
    }
    static NBTTagCompound install(NBTTagCompound normalized,int bit){
        BitSet bits=bits(normalized);if(bits.get(bit))throw fault("Native Matter upgrade is already installed");bits.set(bit);
        NBTTagCompound result=(NBTTagCompound)normalized.copy();byte[] flags=new byte[bits.length()];for(int i=bits.nextSetBit(0);i>=0;i=bits.nextSetBit(i+1))flags[i]=1;
        result.setByteArray("installedUpgrades",flags);return result;
    }
    private void auditWeighted(Object state){
        Object config=field(state,"config");
        for(String name:new String[]{"corners","edges","faces","volumes","replaceWhitelist","replaceWith"}){
            Object weighted=field(config,name);if(weighted==null)continue;
            if(weighted.getClass()!=type(WEIGHTED))throw fault("Unknown Matter weighted state owner");Object raw=field(weighted,"specs");
            if(raw==null||raw.getClass()!=ArrayList.class||((List<?>)raw).size()>65536)throw fault("Unknown Matter weighted state backing");
            if(((List<?>)raw).size()!=1)continue;Object pair=((List<?>)raw).get(0);
            if(pair==null||pair.getClass()!=type("it.unimi.dsi.fastutil.objects.ObjectIntMutablePair"))throw fault("Unknown Matter weighted pair owner");
            Object spec=field(pair,"left");if(spec==null)continue;if(spec.getClass()!=type(SPEC))throw fault("Unknown Matter block specification owner");
            if(field(spec,"objectId")==null)continue;
            // isAir eagerly evaluates getBlockMeta, including its toStack path.
            // Guard callbacks BEFORE the native serializer can reach them.
            Block block=(Block)invoke(type(SPEC),spec,"getBlock",new Class<?>[0]);if(block==null)continue;
            if(field(spec,"intrinsicProperties")!=null||hasField(spec,"arch")&&field(spec,"arch")!=null)throw fault("Matter singleton air check needs an intrinsic/architecture state adapter");
            if(owner(block.getClass(),"createStackedBlock","func_149644_j",int.class)!=Block.class
                ||owner(block.getClass(),"getMaterial","func_149688_o")!=Block.class)throw fault("Matter singleton air check needs its block callback adapter");
            NativeStackAudit.identityOwner(block);admitItem(Item.getItemFromBlock(block));
            Object fallback=field(spec,"item");if(fallback==null||fallback.getClass()!=Optional.class)throw fault("Uninitialized Matter block item lookup");
            admitItem((Item)((Optional<?>)fallback).orElse(null));
        }
    }
    private static void admitItem(Item item){
        if(item!=null&&(ItemCallbacks.method(item,"getHasSubtypes","func_77614_k").getDeclaringClass()!=Item.class
            ||!Arrays.asList(Item.class,ItemBlock.class).contains(ItemCallbacks.method(item,"getMetadata","func_77647_b",int.class).getDeclaringClass())))
            throw fault("Matter singleton air check needs its item metadata adapter");
    }
    private static boolean hasField(Object target,String name){try{target.getClass().getDeclaredField(name);return true;}catch(NoSuchFieldException absent){return false;}}
    private static Class<?> owner(Class<?> type,String mcp,String srg,Class<?>...parameters){
        for(Class<?> current=type;current!=null;current=current.getSuperclass())for(String name:new String[]{mcp,srg})try{return current.getDeclaredMethod(name,parameters).getDeclaringClass();}catch(NoSuchMethodException absent){}
        throw fault("Missing Matter state callback: "+mcp);
    }
    private static void set(Object target,String name,Object value){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);}catch(ReflectiveOperationException error){throw fault("Missing owned Matter state field: "+name);}}
    void verify(){Jobs.checkpoint();if(closed)throw new Jobs.Fault("capture_closed","Matter state codec is closed");graph.verifyRoots();}
    void finishVerification(){verify();graph.verify();}
    public void close(){closed=true;if(graph!=null)graph.close();owned=null;nativeGson=null;architecture=null;shapes.clear();}
    private static Jobs.Fault fault(String text){return new Jobs.Fault("recipe_unsupported",text);}
}
