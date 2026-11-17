package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.item.Item;
import sun.misc.Unsafe;

/** Actual target classes, without constructing a GUI or loading a game world. */
public final class CropsNHReferencesTest {
    public static void main(String[] args) throws Exception {
        if (!(CropsNHReferencesTest.class.getClassLoader() instanceof net.minecraft.launchwrapper.LaunchClassLoader)) {
            java.net.URL[] urls = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                    .map(java.io.File::new).map(java.io.File::toURI).map(uri -> {
                        try { return uri.toURL(); } catch (Exception error) { throw new IllegalStateException(error); }
                    }).toArray(java.net.URL[]::new);
            try (net.minecraft.launchwrapper.LaunchClassLoader loader = new net.minecraft.launchwrapper.LaunchClassLoader(urls)) {
                loader.loadClass(CropsNHReferencesTest.class.getName()).getMethod("main",String[].class)
                        .invoke(null,(Object) args);
            }
            return;
        }
        cpw.mods.fml.common.Loader.injectData("7","99","40","1614","1.7.10","9.05",new java.io.File("."),Collections.emptyList());
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        Unsafe memory = (Unsafe) access.get(null);
        String prefix = "com.gtnewhorizon.cropsnh.compatibility.NEI.";
        for (String suffix : new String[] {"NEICropsNHCropHandler", "NEICropsNHAlternateSeedHandler",
                "NEICropsNHCropstickBreedingHandler", "NEICropsNHMutationPoolHandler"}) {
            ICraftingHandler handler = (ICraftingHandler) memory.allocateInstance(Class.forName(prefix + suffix));
            Recipes.Adapter adapter = Recipes.adapter(handler);
            if (adapter == null || !adapter.name().equals("CROPS_NH_REFERENCE"))
                throw new AssertionError("CropsNH native registry must be reference data: " + suffix);
        }
        for (String suffix : new String[] {"CropsNHNEICropBreederHandler", "CropsNHNEICropGeneExtractorHandler",
                "CropsNHNEICropSynthesizerHandler", "CropsNHNEISeedGeneratorHandler"}) {
            ICraftingHandler handler = (ICraftingHandler) memory.allocateInstance(Class.forName(prefix + suffix));
            if (Recipes.adapter(handler) == Recipes.Adapter.GT)
                throw new AssertionError("CropsNH fake map was admitted as an ordinary GT recipe: " + suffix);
        }
        nativeCrop(memory);
        System.out.println("CropsNH: four reference routes, fake maps not executable; native crop facts read without GUI cache writes");
    }

    private static void nativeCrop(Unsafe memory) throws Exception {
        Object crop = memory.allocateInstance(Class.forName("com.gtnewhorizon.cropsnh.crops.food.vanilla.CropWheat"));
        put(crop,"id","cropsnh:wheat"); put(crop,"numericId",1);
        put(crop,"dropTable",new HashMap<>()); put(crop,"likedBiomes",new HashSet<>());
        put(crop,"alternateSeeds",new ArrayList<>()); put(crop,"duplicationCatalysts",new ArrayList<>());
        Object light = memory.allocateInstance(Class.forName(
                "com.gtnewhorizon.cropsnh.farming.requirements.growth.MinLightLevelGrowthRequirement"));
        put(light,"minLightLevel",7); put(crop,"growthRequirements",new ArrayList<>(Arrays.asList(light)));
        Item seed = new Item();
        Item.itemRegistry.addObject(30000,"cropsnh:genericSeed",seed);
        Object reader = memory.allocateInstance(CropsNHReferences.class);
        Object[] roots = new Object[11]; roots[1] = Collections.singletonMap("cropsnh:wheat",crop); roots[10] = seed;
        put(reader,"roots",roots); put(reader,"section","crops");
        Class<?> entryClass = Class.forName(CropsNHReferences.class.getName() + "$Entry");
        java.lang.reflect.Constructor<?> constructor = entryClass.getDeclaredConstructor(Object.class,Item.class,Integer.class);
        constructor.setAccessible(true);
        Object entry = constructor.newInstance(crop,null,null);
        Method read = CropsNHReferences.class.getDeclaredMethod("read",entryClass,boolean.class); read.setAccessible(true);
        Object before = read.invoke(reader,entry,false);
        JsonObject data = (JsonObject) MagicApi.field(before,"data");
        if (data.get("tier").getAsInt() != 1 || data.get("growthDuration").getAsInt() != 1000
                || data.getAsJsonObject("crop").get("numericId").getAsInt() != 1
                || data.getAsJsonArray("requirements").get(0).getAsJsonObject().get("level").getAsInt() != 7)
            throw new AssertionError("Target crop scalars or native light requirement were lost");
        if (MagicApi.field(crop,"cachedSoils") != null || MagicApi.field(crop,"cachedSubSoils") != null)
            throw new AssertionError("Reading crop facts populated a GUI cache");
        Method same = CropsNHReferences.class.getDeclaredMethod("same",before.getClass(),before.getClass()); same.setAccessible(true);
        same.invoke(null,before,read.invoke(reader,entry,false));
        put(light,"minLightLevel",8);
        try {
            same.invoke(null,before,read.invoke(reader,entry,false));
            throw new AssertionError("Touched native crop requirement drift was not detected");
        } catch (java.lang.reflect.InvocationTargetException expected) {
            if (!(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault)) throw expected;
        }
    }
    private static void put(Object target, String name, Object value) throws Exception {
        for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
            try { Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target,value); return; }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
}
