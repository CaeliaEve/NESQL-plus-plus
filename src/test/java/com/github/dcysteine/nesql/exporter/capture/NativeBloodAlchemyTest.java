package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import com.google.gson.JsonObject;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.field;

/** Red test for the native Blood Magic alchemy route. */
public final class NativeBloodAlchemyTest {
    public static void run() throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(
                "WayofTime.alchemicalWizardry.client.nei.NEIAlchemyRecipeHandler").newInstance();
        if (Recipes.adapter(handler) != Recipes.Adapter.BLOOD_ALCHEMY)
            throw new AssertionError("Blood Magic alchemy has no native adapter");

        Item orb = NativeCoreFixesTest.item(
                "WayofTime.alchemicalWizardry.common.items.EnergyBattery", "alchemy_orb");
        Field orbLevel = Class.forName("WayofTime.alchemicalWizardry.common.items.EnergyBattery")
                .getDeclaredField("orbLevel");
        orbLevel.setAccessible(true); orbLevel.setInt(orb, 3);
        ItemStack wildcard = new ItemStack(Items.paper, 1, 32767);
        ItemStack exact = new ItemStack(Items.iron_ingot, 2, 0);
        ItemStack output = new ItemStack(Items.diamond, 4, 0);
        Class<?> recipeType = Class.forName("WayofTime.alchemicalWizardry.api.alchemy.AlchemyRecipe");
        Object nativeRecipe = recipeType.getConstructor(ItemStack.class, int.class, ItemStack[].class, int.class)
                .newInstance(output, 250, new ItemStack[] {wildcard, exact, null, null, null}, 2);
        @SuppressWarnings("unchecked") List<Object> registry = (List<Object>) field(
                Class.forName("WayofTime.alchemicalWizardry.api.alchemy.AlchemyRecipeRegistry"), null, "recipes");
        List<Object> before = new ArrayList<>(registry);
        registry.clear(); registry.add(nativeRecipe);
        try {
            Constructor<BloodAlchemyRecipes> constructor = BloodAlchemyRecipes.class
                    .getDeclaredConstructor(TemplateRecipeHandler.class);
            constructor.setAccessible(true);
            RegistryRecipes adapter = constructor.newInstance(handler);
            require(adapter.size() == 1, "Alchemy registry entry was not discovered");
            RecipeRow row = NativeCoreFixesTest.row(wildcard, exact, output, new ItemStack(orb));
            require(adapter.capture(0, row), "Alchemy recipe was excluded");
            require(row.inputs.size() == 3 && row.outputs.size() == 1, "Alchemy slots were not preserved");
            JsonObject wildcardRule = row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices")
                    .get(0).getAsJsonObject().getAsJsonObject("rule");
            JsonObject exactRule = row.inputs.get(1).getAsJsonObject().getAsJsonArray("choices")
                    .get(0).getAsJsonObject().getAsJsonObject("rule");
            require(wildcardRule.get("meta").getAsBoolean() && !exactRule.get("meta").getAsBoolean(),
                    "Alchemy wildcard and exact metadata rules collapsed");
            require(row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").size() == 1
                    && row.inputs.get(2).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("consume").get("kind").getAsString().equals("keep"),
                    "Reusable orb was not modeled as a kept candidate");
            require(row.properties.getAsJsonObject("bloodmagic:lpCost").getAsJsonObject("value")
                    .get("value").getAsInt() == 25000, "Alchemy LP cost changed");
            require(row.properties.getAsJsonObject("bloodmagic:orb").getAsJsonObject("value")
                    .get("value").getAsInt() == 2, "Alchemy orb tier changed");
            require(output.stackSize == 4 && wildcard.getItemDamage() == 32767,
                    "Alchemy capture mutated source stacks");
        } finally { registry.clear(); registry.addAll(before); }
        System.out.println("Native Blood Magic: alchemy route, five-slot projection, wildcard/exact metadata, reusable orb, LP cost and immutable source passed");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
