package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.*;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Tests real pinned registry matching against serialized adapter facts, without a client/world. */
public final class NativeGendustryFluidsTest {
    public static void main(String[] args) throws Exception {
        if (!(NativeGendustryFluidsTest.class.getClassLoader() instanceof net.minecraft.launchwrapper.LaunchClassLoader)) {
            java.net.URL[] urls = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                    .map(java.io.File::new).map(java.io.File::toURI).map(uri -> {
                        try { return uri.toURL(); } catch (Exception error) { throw new IllegalStateException(error); }
                    }).toArray(java.net.URL[]::new);
            ClassLoader previous = Thread.currentThread().getContextClassLoader();
            try (net.minecraft.launchwrapper.LaunchClassLoader loader = new net.minecraft.launchwrapper.LaunchClassLoader(urls)) {
                Thread.currentThread().setContextClassLoader(loader);
                try { loader.loadClass(NativeGendustryFluidsTest.class.getName()).getMethod("main", String[].class).invoke(null, (Object) args); }
                catch (InvocationTargetException error) {
                    if (error.getCause() instanceof Error) throw (Error) error.getCause();
                    throw (Exception) error.getCause();
                }
            } finally { Thread.currentThread().setContextClassLoader(previous); }
            return;
        }
        cpw.mods.fml.common.Loader.injectData("7", "99", "40", "1614", "1.7.10", "9.05", new java.io.File("."), Collections.emptyList());
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.common.Loader.class, cpw.mods.fml.common.Loader.instance(), new HashMap<String, cpw.mods.fml.common.ModContainer>(), "namedMods");
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(cpw.mods.fml.relauncher.FMLRelaunchLog.class, null, cpw.mods.fml.relauncher.Side.CLIENT, "side");
        net.minecraft.init.Bootstrap.func_151354_b();
        if (args.length == 1 && args[0].equals("blood-orb")) NativeBloodOrbTest.run();
        else if (args.length == 1 && args[0].equals("creativecore")) NativeCreativeCoreTest.run();
        else if (args.length == 1 && args[0].equals("decayable")) NativeDecayableTest.run();
        else if (args.length == 1 && args[0].equals("blood-orb-shapeless")) NativeBloodOrbShapelessTest.run();
        else run();
    }

    @SuppressWarnings("unchecked")
    static void run() throws Exception {
        Class<?> nativeType = Class.forName("net.bdew.gendustry.fluids.FluidSourceRegistry");
        Object registry = nativeType.newInstance();
        Method register = nativeType.getMethod("register", Item.class, int.class, int.class);
        Method lookup = nativeType.getMethod("getValue", ItemStack.class);
        register.invoke(registry, Items.paper, 32767, 80);
        register.invoke(registry, Items.paper, 2, 125);
        register.invoke(registry, Items.paper, 3, 0);
        register.invoke(registry, Items.paper, 4, -7);
        ItemStack tagged = new ItemStack(Items.paper, 9, 2); tagged.setTagInfo("ignored", new net.minecraft.nbt.NBTTagInt(91));
        require((Integer) lookup.invoke(registry, tagged) == 125, "Native exact metadata must override wildcard and ignore NBT/count");
        require((Integer) lookup.invoke(registry, new ItemStack(Items.paper, 1, 3)) == 0, "Native zero blocker must suppress wildcard");
        require((Integer) lookup.invoke(registry, new ItemStack(Items.paper, 1, 4)) == -7, "Native negative blocker must suppress wildcard");
        Class<?> adapter;
        try { adapter = Class.forName("com.github.dcysteine.nesql.exporter.capture.GendustryFluidRecipes"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("Missing native Gendustry fluid adapter", missing); }
        Constructor<?> constructor = adapter.getDeclaredConstructor(Object.class, FluidStack.class, float.class, float.class, float.class, int.class);
        constructor.setAccessible(true);
        Object recipes = constructor.newInstance(registry, new FluidStack(FluidRegistry.WATER, 1), 12.5f, 0.1f, 5f, 1000);
        Method capture = adapter.getDeclaredMethod("captureFacts", int.class, RecipeRow.class); capture.setAccessible(true);
        Method size = adapter.getDeclaredMethod("size"); size.setAccessible(true);
        require((Integer) size.invoke(recipes) == 4, "Native zero/negative source keys disappeared from the registry ledger");
        com.google.gson.JsonArray rows = array();
        for (int i = 0; i < 4; i++) {
            RecipeRow row = NativeCoreFixesTest.row(new ItemStack(Items.paper,1,0), new ItemStack(Items.paper,1,2), new ItemStack(Items.paper,1,3), new ItemStack(Items.paper,1,4));
            ((Set<String>) MagicApi.field(row.facts, "fluids")).add(Identity.fluid("water", null));
            boolean captured = (Boolean) capture.invoke(recipes, i, row);
            if (!captured) { require(i == 1 || i == 2, "Wrong nonpositive source omitted"); continue; }
            JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.get("amount").getAsString().equals("1") && choice.getAsJsonArray("returns").size() == 0
                    && choice.getAsJsonObject("consume").get("kind").getAsString().equals("consume"), "Machine must consume one input without crafting container returns");
            if (i == 3) {
                JsonObject rule = choice.getAsJsonObject("rule");
                require(rule.get("kind").getAsString().equals("except") && rule.getAsJsonArray("exclude").size() == 3,
                        "Wildcard recipe regained exact-key blockers");
                require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("80"), "Wildcard yield changed");
            } else {
                require(!choice.getAsJsonObject("rule").get("meta").getAsBoolean() && choice.getAsJsonObject("rule").get("nbt").getAsBoolean(), "Exact source input predicate changed");
                require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("125"), "Exact native yield changed");
            }
            require(row.record.get("duration").isJsonNull() && row.properties.has("gendustry:mjPerItem")
                    && row.properties.has("gendustry:activationEnergy"), "Variable power progress became a fictional fixed duration");
            row.finish(); rows.add(row.record);
        }
        require(rows.size() == 2 && (Integer)lookup.invoke(registry, tagged) == 125 && tagged.stackSize == 9, "Capture mutated native source state");
        java.nio.file.Path file = java.nio.file.Files.createTempFile("gendustry-fluid-facts-", ".json");
        try {
            java.nio.file.Files.write(file, CanonicalJson.bytes(rows));
            require(new com.google.gson.JsonParser().parse(new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray().size() == 2, "Serialized recipe records were lost");
        } finally { java.nio.file.Files.delete(file); }
        register.invoke(registry, Items.paper, 2, 126);
        Method verify = adapter.getDeclaredMethod("verify"); verify.setAccessible(true);
        try { verify.invoke(recipes); throw new AssertionError("Native registry mutation was accepted"); }
        catch (InvocationTargetException expected) { require(expected.getCause() instanceof com.github.dcysteine.nesql.exporter.task.Jobs.Fault, "Wrong registry drift failure"); }
        System.out.println("Native Gendustry fluids: exact/wildcard precedence, nonpositive blockers, unit consumption, NBT/count independence, amounts, variable energy, durable JSON and registry drift passed");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
