package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;

/** Red test for the LogisticsPipes soldering-station adapter. */
public final class NativeLogisticsPipesTest {
    public static void run() throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(
                "logisticspipes.nei.NEISolderingStationRecipeManager").newInstance();
        if (Recipes.adapter(handler) != Recipes.Adapter.LOGISTICS_SOLDERING)
            throw new AssertionError("LogisticsPipes soldering station has no native adapter");

        Class<?> registryType = Class.forName("logisticspipes.recipes.SolderingStationRecipes");
        Field recipesField = registryType.getDeclaredField("recipes"); recipesField.setAccessible(true);
        @SuppressWarnings("unchecked") List<Object> registry = (List<Object>) recipesField.get(null);
        List<Object> before = new ArrayList<>(registry);
        Class<?> recordType = Class.forName("logisticspipes.recipes.SolderingStationRecipes$SolderingStationRecipe");
        Class<?> callbackType = Class.forName("logisticspipes.interfaces.ICraftingResultHandler");
        Constructor<?> record = recordType.getConstructor(ItemStack[].class, ItemStack.class, callbackType);
        ItemStack[] source = new ItemStack[9]; source[0] = new ItemStack(Items.iron_ingot, 2);
        ItemStack result = new ItemStack(Items.diamond, 3);
        Object staticEntry = record.newInstance(source, result, null);
        Object callback = Proxy.newProxyInstance(callbackType.getClassLoader(), new Class<?>[] {callbackType},
                (proxy, method, args) -> { throw new AssertionError("Dynamic LP callback must never execute during capture"); });
        Object dynamicEntry = record.newInstance(source, result, callback);
        registry.clear(); registry.add(staticEntry); registry.add(dynamicEntry);
        try {
            Constructor<LogisticsPipesRecipes> adapterCtor = LogisticsPipesRecipes.class
                    .getDeclaredConstructor(TemplateRecipeHandler.class);
            adapterCtor.setAccessible(true);
            RegistryRecipes adapter = adapterCtor.newInstance(handler);
            require(adapter.size() == 2, "Dynamic callback entry must not block static registry entries");
            RecipeRow row = NativeCoreFixesTest.row(source[0], result, new ItemStack(Items.iron_ingot));
            require(adapter.capture(0, row), "Static soldering entry was not captured");
            require(row.inputs.size() == 2 && row.outputs.size() == 1, "Soldering layout or auxiliary iron input was lost");
            JsonObject choice = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject();
            require(choice.get("amount").getAsString().equals("1"), "Soldering input quantity changed");
            require(!adapter.capture(1, NativeCoreFixesTest.row(source[0], result)),
                    "Dynamic callback entry was silently exported");
            JsonObject proof = adapter.exclusion(1);
            require(proof != null && proof.get("reason").getAsString().equals("dynamic_result_callback")
                            && proof.get("index").getAsInt() == 1,
                    "Dynamic callback exclusion lacked a bounded per-entry proof");
            require(source[0].stackSize == 2 && result.stackSize == 3, "Soldering capture mutated native source stacks");
        } finally { registry.clear(); registry.addAll(before); }
        System.out.println("Native LogisticsPipes: static capture, per-entry dynamic callback rejection, layout and immutability passed");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
