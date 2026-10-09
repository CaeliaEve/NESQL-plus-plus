package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import java.lang.reflect.Field;

/** Native Blood Magic Alchemic Calcinator projection test. */
public final class NativeBloodCalcinatorTest {
    public static void run() throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(
                "WayofTime.alchemicalWizardry.client.nei.NEICalcinatorHandler").newInstance();
        require(Recipes.adapter(handler) == Recipes.Adapter.BLOOD_CALCINATOR, "Blood Magic calcinator has no adapter");
        Class<?> reagentType = Class.forName("WayofTime.alchemicalWizardry.api.alchemy.energy.Reagent");
        Object reagent = reagentType.getConstructor(String.class).newInstance("nesql_test_reagent");
        reagentType.getMethod("setColour", int.class, int.class, int.class, int.class).invoke(reagent, 17, 34, 51, 255);
        Class<?> stackType = Class.forName("WayofTime.alchemicalWizardry.api.alchemy.energy.ReagentStack");
        Object reagentStack = stackType.getConstructor(reagentType, int.class).newInstance(reagent, 250);
        Class<?> registryType = Class.forName("WayofTime.alchemicalWizardry.api.alchemy.energy.ReagentRegistry");
        registryType.getMethod("registerItemAndReagent", net.minecraft.item.ItemStack.class, stackType)
                .invoke(null, new net.minecraft.item.ItemStack(net.minecraft.init.Items.paper), reagentStack);
        net.minecraft.item.Item orb = NativeCoreFixesTest.item(
                "WayofTime.alchemicalWizardry.common.items.EnergyBattery", "calcinator_orb");
        Field orbLevel = Class.forName("WayofTime.alchemicalWizardry.common.items.EnergyBattery")
                .getDeclaredField("orbLevel"); orbLevel.setAccessible(true); orbLevel.setInt(orb, 1);
        BloodCalcinatorRecipes adapter = new BloodCalcinatorRecipes(handler);
        require(adapter.size() > 0, "Blood Magic calcinator registry is empty");
        RecipeRow row = NativeCoreFixesTest.row(new net.minecraft.item.ItemStack(orb), new net.minecraft.item.ItemStack(net.minecraft.init.Items.paper));
        require(adapter.capture(0, row), "Blood Magic calcinator first recipe was excluded");
        require(row.inputs.size() == 1 && row.outputs.size() == 1, "Calcinator lost orb input or reagent output");
        require(row.inputs.get(0).getAsJsonObject().getAsJsonArray("choices").size() > 0, "Calcinator orb candidates disappeared");
        require(row.properties.has("bloodmagic:reagentAmount") && row.properties.has("bloodmagic:orb"), "Calcinator native properties missing");
        System.out.println("Native Blood Magic: calcinator orb candidates, reagent output and reusable catalyst semantics passed");
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
