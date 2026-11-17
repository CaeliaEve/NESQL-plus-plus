package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Actual pinned builders, generators, host callbacks and share calculation, with fixture registries. */
final class GtMineralTest {
    static void run() throws Exception {
        Class<?> adapter=Class.forName("com.github.dcysteine.nesql.exporter.capture.GtMineralReferences");
        Method snapshot=adapter.getDeclaredMethod("snapshot",boolean.class,boolean.class,JsonObject.class,Map.class,Map.class,Map.class,Map.class,Map.class,Map.class,Map.class,List.class,boolean.class,Map.class);
        snapshot.setAccessible(true);
        set(cpw.mods.fml.common.Loader.instance(),"namedMods",new HashMap<>());
        Class<?> materials=type("gregtech.api.enums.Materials"),api=type("gregtech.api.GregTechAPI");
        Object iron=field(materials,null,"Iron"),gold=field(materials,null,"Gold");
        // The isolated bootstrap has no resource-language initialization; names are explicit fixture scaffolding.
        set(iron,"mLocalizedName","Fixture Iron");set(gold,"mLocalizedName","Fixture Gold");
        setStatic(api,"sBlockOres1",Blocks.stone);
        Object small=type("gregtech.common.SmallOreBuilder").newInstance();
        set(small,"smallOreName","ore.small.fixture");set(small,"minY",10);set(small,"maxY",10);set(small,"amount",0);set(small,"ore",iron);
        @SuppressWarnings("unchecked") Map<String,Boolean> rawDims=(Map<String,Boolean>)field(small,"dimsEnabled");rawDims.put("Overworld",true);rawDims.put("Nether",false);
        Object generator=type("gregtech.common.WorldgenGTOreSmallPieces").getConstructor(small.getClass()).newInstance(small);
        Object wrapper=type("gtneioreplugin.util.GT5OreSmallHelper$OreSmallWrapper").getConstructor(small.getClass()).newInstance(small);
        Map<String,Object> builders=new TreeMap<>(),wrappers=new TreeMap<>(),generators=new TreeMap<>();
        builders.put("ore.small.fixture",small);wrappers.put("ore.small.fixture",wrapper);generators.put("ore.small.fixture",generator);
        Map<Object,Object> buffered=new HashMap<>();buffered.put(wrapper,invoke(type("gtneioreplugin.util.DimensionHelper"),null,"getDims",new Class<?>[]{wrapper.getClass()},wrapper));
        Object dim=type("gtneioreplugin.util.GT5OreSmallHelper$SmallOreDimensionWrapper").newInstance();
        @SuppressWarnings("unchecked") List<Object> list=(List<Object>)field(dim,"internalDimOreList");list.add(wrapper);
        invoke(dim.getClass(),dim,"calculateWeights",new Class<?>[0]);
        Map<Short,List<ItemStack>> drops=new HashMap<>();drops.put((Short)field(wrapper,"oreMeta"),Arrays.asList(new ItemStack(Items.diamond,2),new ItemStack(Items.coal,1)));
        Map<String,Short> lookup=new HashMap<>();lookup.put(new ItemStack(Items.diamond).getUnlocalizedName(),(Short)field(wrapper,"oreMeta"));lookup.put(new ItemStack(Items.coal).getUnlocalizedName(),(Short)field(wrapper,"oreMeta"));
        JsonObject source=object("owner","gtneioreplugin","handler","gtneioreplugin.plugin.gregtech5.PluginGT5SmallOreStat","key","GTOrePluginOreSmall");
        Map<String,ItemStack> visuals=new TreeMap<>();
        JsonArray rows=(JsonArray)snapshot.invoke(null,true,true,source,builders,wrappers,generators,buffered,Collections.singletonMap("Ow",dim),drops,lookup,Arrays.asList(iron,gold),false,visuals);
        JsonObject entry=rows.get(1).getAsJsonObject().getAsJsonObject("data");
        require(entry.getAsJsonObject("configured").get("amount").getAsInt()==0,"Builder amount altered");
        require(entry.getAsJsonObject("generator").get("amount").getAsInt()==1&&entry.getAsJsonObject("generator").get("maxY").getAsInt()==11,"Actual constructor adjustments omitted");
        require(entry.getAsJsonArray("roles").get(0).getAsJsonObject().getAsJsonArray("hosts").size()==7,"Native host samples omitted");
        require(entry.getAsJsonArray("drops").size()==2&&entry.getAsJsonArray("drops").get(0).getAsJsonObject().get("amount").getAsInt()==2,"Drops conflated with host examples");
        require(entry.getAsJsonArray("dimensions").size()==2,"Disabled dimension disappeared");
        JsonObject share=rows.get(2).getAsJsonObject().getAsJsonObject("data");
        require(share.get("nativeShare").isJsonNull()&&share.get("nonFinite").getAsString().equals("nan"),"Native zero denominator forged as a probability");
        JsonArray smallRows=rows;
        Object vein=type("gregtech.common.OreMixBuilder").newInstance();
        set(vein,"oreMixName","ore.mix.fixture");set(vein,"minY",20);set(vein,"maxY",50);set(vein,"weight",7);set(vein,"density",3);set(vein,"size",12);set(vein,"primary",iron);set(vein,"secondary",gold);set(vein,"between",iron);set(vein,"sporadic",gold);set(vein,"localizedName","Fixture layers");
        @SuppressWarnings("unchecked") Map<String,Boolean> veinDims=(Map<String,Boolean>)field(vein,"dimsEnabled");veinDims.put("Overworld",true);veinDims.put("Nether",false);
        Object veinGen=type("gregtech.common.WorldgenGTOreLayer").getConstructor(vein.getClass()).newInstance(vein);
        Object veinWrapper=type("gtneioreplugin.util.GT5OreLayerHelper$OreLayerWrapper").getConstructor(vein.getClass()).newInstance(vein);
        Object veinDim=type("gtneioreplugin.util.GT5OreLayerHelper$NormalOreDimensionWrapper").newInstance();
        @SuppressWarnings("unchecked") List<Object> veins=(List<Object>)field(veinDim,"internalDimOreList");veins.add(veinWrapper);invoke(veinDim.getClass(),veinDim,"calculateWeights",new Class<?>[0]);
        buffered=new HashMap<>();buffered.put(veinWrapper,invoke(type("gtneioreplugin.util.DimensionHelper"),null,"getDims",new Class<?>[]{veinWrapper.getClass()},veinWrapper));
        source=object("owner","gtneioreplugin","handler","gtneioreplugin.plugin.gregtech5.PluginGT5VeinStat","key","GTOrePluginVein");
        rows=(JsonArray)snapshot.invoke(null,true,false,source,Collections.singletonMap("ore.mix.fixture",vein),Collections.singletonMap("ore.mix.fixture",veinWrapper),Collections.singletonMap("ore.mix.fixture",veinGen),buffered,Collections.singletonMap("Ow",veinDim),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),false,visuals);
        require(rows.get(1).getAsJsonObject().getAsJsonObject("data").getAsJsonArray("roles").size()==4,"Native vein layers lost");
        require(rows.get(2).getAsJsonObject().getAsJsonObject("data").get("nativeShare").getAsDouble()==1d,"Actual installed dimension callback differs");
        set(vein,"enabledByDefault",false);
        JsonArray disabled=(JsonArray)snapshot.invoke(null,true,false,source,Collections.singletonMap("ore.mix.fixture",vein),Collections.singletonMap("ore.mix.fixture",veinWrapper),Collections.emptyMap(),buffered,Collections.singletonMap("Ow",veinDim),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),false,new TreeMap<>());
        require(disabled.get(1).getAsJsonObject().getAsJsonObject("data").get("generator").isJsonNull(),"Disabled definition masquerades as active generation");
        set(vein,"enabledByDefault",true);
        JsonArray itemFacts=array();
        for(Map.Entry<String,ItemStack> item:visuals.entrySet())itemFacts.add(object("id",item.getKey(),"registry",net.minecraft.item.Item.itemRegistry.getNameForObject(item.getValue().getItem()),"meta",item.getValue().getItemDamage(),"tags",array(),"nbt",com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(item.getValue().getTagCompound())));
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/gt-minerals-contract.json"),CanonicalJson.bytes(object("small",smallRows,"veins",rows,"disabled",disabled,"items",itemFacts)));
        Map<Object,Object> badBuffer=new HashMap<>(buffered);badBuffer.put(veinWrapper,Collections.singletonMap("Ne",true));
        try{snapshot.invoke(null,true,false,source,Collections.singletonMap("ore.mix.fixture",vein),Collections.singletonMap("ore.mix.fixture",veinWrapper),Collections.singletonMap("ore.mix.fixture",veinGen),badBuffer,Collections.singletonMap("Ow",veinDim),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),false,new TreeMap<>());throw new AssertionError("Stale dimensions accepted");}
        catch(InvocationTargetException expected){if(!(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault))throw expected;}
        try{snapshot.invoke(null,false,false,source,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),false,new TreeMap<>());throw new AssertionError("Unready empty accepted");}
        catch(InvocationTargetException expected){require(((com.github.dcysteine.nesql.exporter.task.Jobs.Fault)expected.getCause()).code.equals("handler_not_ready"),"Wrong readiness error");}
        JsonArray empty=(JsonArray)snapshot.invoke(null,true,false,source,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),Collections.emptyList(),false,new TreeMap<>());
        require(empty.size()==1,"Ready empty rejected");
        // Route the actual handler classes without constructing their GUI-only translation/transfer rectangles.
        Object smallHandler=allocate(type("gtneioreplugin.plugin.gregtech5.PluginGT5SmallOreStat")),veinHandler=allocate(type("gtneioreplugin.plugin.gregtech5.PluginGT5VeinStat"));
        require(Recipes.adapter((codechicken.nei.recipe.ICraftingHandler)smallHandler).name().equals("GT_MINERALS_REFERENCE")&&Recipes.adapter((codechicken.nei.recipe.ICraftingHandler)veinHandler).name().equals("GT_MINERALS_REFERENCE"),"Handlers still use consumption template");
        System.out.println("GT minerals: native adjusted generators, 7 hosts, 4 layers, distinct drops, dimensions, native NaN, drift and readiness passed");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void set(Object target,String name,Object value)throws Exception{for(Class<?> owner=target.getClass();owner!=null;owner=owner.getSuperclass())try{Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException missing){}throw new NoSuchFieldException(name);}
    private static void setStatic(Class<?> owner,String name,Object value)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    private static Object allocate(Class<?> owner)throws Exception{Class<?> unsafe=Class.forName("sun.misc.Unsafe");Field f=unsafe.getDeclaredField("theUnsafe");f.setAccessible(true);return unsafe.getMethod("allocateInstance",Class.class).invoke(f.get(null),owner);}
}
