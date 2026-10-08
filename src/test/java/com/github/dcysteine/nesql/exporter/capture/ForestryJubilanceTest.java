package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.JsonObject;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.apiculture.IAlleleBeeSpeciesCustom;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IJubilanceProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Tests the provider boundary without a housing, world or live jubilance evaluation. */
public final class ForestryJubilanceTest {
    public static void run() throws Exception {
        Class<?> helper;
        try { helper = Class.forName("com.github.dcysteine.nesql.exporter.capture.ForestryJubilance"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Bee specialties lose their native jubilance description", missing); }
        Method capture = helper.getDeclaredMethod("capture", IAlleleBeeSpecies.class, Facts.class);
        capture.setAccessible(true);
        Facts facts = new Facts("zh_CN");
        String description = "§a适宜气候\n§7No creatures nearby";
        String id = (String) capture.invoke(null, species(provider(description), true), facts);
        Facts.Batch batch = facts.drain();
        require(batch.records.size() == 1 && batch.records.get(0).kind.equals("strings"), "Description did not produce one localized string");
        JsonObject text = batch.records.get(0).value;
        require(text.get("id").getAsString().equals(id) && text.get("text").getAsString().equals(description)
                && text.get("locale").getAsString().equals("zh_CN"), "Native description formatting or locale changed");
        require(capture.invoke(null, species(null, true), facts) == null, "Null provider became invented conditions");
        require(capture.invoke(null, species(provider(null), true), facts) == null, "Missing provider description became invented conditions");
        require(capture.invoke(null, species(null, false), facts) == null, "Non-custom species acquired a fabricated provider");
        String empty = (String) capture.invoke(null, species(provider(""), true), facts);
        require(empty != null, "An explicit empty native description was conflated with absence");
        IJubilanceProvider failure = new IJubilanceProvider() {
            public boolean isJubilant(IAlleleBeeSpecies bee, IBeeGenome genome, IBeeHousing housing) { throw new AssertionError("Runtime eligibility must not be evaluated"); }
            public String getDescription() { throw new IllegalStateException("native provider failed"); }
        };
        try { capture.invoke(null, species(failure, true), facts); throw new AssertionError("Provider failure was silently erased"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof IllegalStateException, "Provider error changed type"); }
    }

    private static IJubilanceProvider provider(String text) {
        return new IJubilanceProvider() {
            public boolean isJubilant(IAlleleBeeSpecies bee, IBeeGenome genome, IBeeHousing housing) { throw new AssertionError("Runtime eligibility must not be evaluated"); }
            public String getDescription() { return text; }
        };
    }

    private static IAlleleBeeSpecies species(IJubilanceProvider provider, boolean custom) {
        Class<?> type = custom ? IAlleleBeeSpeciesCustom.class : IAlleleBeeSpecies.class;
        return (IAlleleBeeSpecies) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getName().equals("getJubilanceProvider")) return provider;
            throw new AssertionError("Unexpected species evaluation: " + method.getName());
        });
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
