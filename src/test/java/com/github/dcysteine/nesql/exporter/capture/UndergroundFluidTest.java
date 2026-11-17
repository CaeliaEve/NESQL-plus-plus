package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.*;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned GT configuration objects and selection callback; registry contents are a local fixture. */
final class UndergroundFluidTest {
    static void run() throws Exception {
        Class<?> adapter=Class.forName("com.github.dcysteine.nesql.exporter.capture.UndergroundFluidReferences");
        Method snapshot=adapter.getDeclaredMethod("snapshot",boolean.class,JsonObject.class,Map.class,Map.class,Map.class,Map.class);
        snapshot.setAccessible(true);
        Class<?> dimensionClass=Class.forName("gregtech.api.objects.GTUODimension");
        Class<?> wrapperClass=Class.forName("gtneioreplugin.util.GT5UndergroundFluidHelper$UndergroundFluidWrapper");
        ConfigCategory config=new ConfigCategory("fixture");
        config.put("Dimension",new Property("Dimension","0",Property.Type.STRING));
        for(int i=0;i<6;i++) {
            ConfigCategory fluid=new ConfigCategory("entry"+i,config);
            fluid.put("Registry",new Property("Registry","fixture_oil_"+i,Property.Type.STRING));
            fluid.put("Chance",new Property("Chance","1",Property.Type.INTEGER));
            fluid.put("MinAmount",new Property("MinAmount","100",Property.Type.INTEGER));
            fluid.put("MaxAmount",new Property("MaxAmount","200",Property.Type.INTEGER));
            fluid.put("DecreasePerOperationAmount",new Property("DecreasePerOperationAmount","7",Property.Type.INTEGER));
            if(i!=5) FluidRegistry.registerFluid(new Fluid("fixture_oil_"+i));
        }
        Object dimension=dimensionClass.getConstructor(ConfigCategory.class).newInstance(config);
        Map<String,Object> dimensions=new LinkedHashMap<>();dimensions.put("0",dimension);
        Map<String,List<Object>> helper=new TreeMap<>();
        for(int i=0;i<5;i++) helper.put("fixture_oil_"+i,new ArrayList<>(Arrays.asList(wrapperClass.getConstructor(String.class,int.class,int.class,int.class).newInstance("Ow",1666,200,100))));
        JsonObject source=object("owner","gtneioreplugin","handler","gtneioreplugin.plugin.gregtech5.PluginGT5UndergroundFluid","key","GTOrePluginUndergroundFluid");
        Map<String,Object> visuals=new HashMap<>();
        JsonArray rows=(JsonArray)snapshot.invoke(null,true,source,dimensions,helper,Collections.singletonMap("0","Ow"),visuals);
        if(rows.size()!=13 || visuals.size()!=5) throw new AssertionError("Lost raw configs, display projection or unresolved fluid");
        JsonObject nativeDimension=rows.get(1).getAsJsonObject().getAsJsonObject("data");
        if(nativeDimension.get("nullTickets").getAsInt()!=3) throw new AssertionError("Native null outcome hidden");
        Method select=dimensionClass.getMethod("getRandomFluid",Random.class);
        Map<String,Integer> actual=new HashMap<>();int[] nul={0};
        for(int ticket=0;ticket<1000;ticket++) {
            final int chosen=ticket;
            Object selected=select.invoke(dimension,new Random(0){@Override public int nextInt(int bound){if(bound!=1000)throw new AssertionError("Selection bound drift");return chosen;}});
            if(selected==null)nul[0]++;else actual.merge((String)selected.getClass().getField("Registry").get(selected),1,Integer::sum);
        }
        for(int i=2;i<8;i++) {
            JsonObject entry=rows.get(i).getAsJsonObject().getAsJsonObject("data");
            if(entry.get("selectedTickets").getAsInt()!=actual.getOrDefault(entry.get("registry").getAsString(),0))throw new AssertionError("Selection probability differs from installed callback");
            if(entry.get("registry").getAsString().equals("fixture_oil_5") && !entry.get("fluid").isJsonNull())throw new AssertionError("Unregistered config presented as resolved");
        }
        if(nul[0]!=3)throw new AssertionError("Fixture native remainder differs");
        JsonArray fluids=array();
        for(Object raw:visuals.values()){
            net.minecraftforge.fluids.FluidStack stack=(net.minecraftforge.fluids.FluidStack)raw;Fluid fluid=stack.getFluid();
            fluids.add(object("id",com.github.dcysteine.nesql.exporter.source.Identity.fluid(fluid.getName(),null),"registry",fluid.getName(),"nbt",null,"temperature",fluid.getTemperature(stack),"density",fluid.getDensity(stack),"viscosity",fluid.getViscosity(stack),"gaseous",fluid.isGaseous(stack),"luminosity",fluid.getLuminosity(stack)));
        }
        java.nio.file.Files.write(java.nio.file.Paths.get("build/native-tests/underground-fluids-contract.json"),CanonicalJson.bytes(object("records",rows,"fluids",fluids)));
        helper.get("fixture_oil_0").clear();
        try{snapshot.invoke(null,true,source,dimensions,helper,Collections.singletonMap("0","Ow"),new HashMap<>());throw new AssertionError("Stale native projection accepted");}
        catch(InvocationTargetException expected){if(!(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault))throw expected;}
        try{snapshot.invoke(null,false,source,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),new HashMap<>());throw new AssertionError("Unready empty catalogue accepted");}
        catch(InvocationTargetException expected){if(!((com.github.dcysteine.nesql.exporter.task.Jobs.Fault)expected.getCause()).code.equals("handler_not_ready"))throw expected;}
        JsonArray empty=(JsonArray)snapshot.invoke(null,true,source,Collections.emptyMap(),Collections.emptyMap(),Collections.emptyMap(),new HashMap<>());
        if(empty.size()!=1)throw new AssertionError("Ready empty configuration rejected");
        ConfigCategory unknown=new ConfigCategory("unknown");unknown.put("Dimension",new Property("Dimension","unmapped-world",Property.Type.STRING));
        Object unknownDimension=dimensionClass.getConstructor(ConfigCategory.class).newInstance(unknown);
        JsonArray unknownRows=(JsonArray)snapshot.invoke(null,true,source,Collections.singletonMap("unmapped-world",unknownDimension),Collections.emptyMap(),Collections.emptyMap(),new HashMap<>());
        JsonObject unknownData=unknownRows.get(1).getAsJsonObject().getAsJsonObject("data");
        if(!unknownData.get("displayDimension").isJsonNull()||unknownData.get("nullTickets").getAsInt()!=1000)throw new AssertionError("Unknown empty dimension hidden or fabricated");
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        codechicken.nei.recipe.ICraftingHandler handler=(codechicken.nei.recipe.ICraftingHandler)((sun.misc.Unsafe)unsafeField.get(null)).allocateInstance(Class.forName(source.get("handler").getAsString()));
        if(Recipes.adapter(handler)!=Recipes.Adapter.UNDERGROUND_FLUIDS_REFERENCE)throw new AssertionError("World generation still consuming a dimension display item");
        System.out.println("Underground fluids: installed configuration and exact 1000-ticket selection, native null remainder, display projection, unresolved fluid, readiness and empty pool passed");
    }
}
