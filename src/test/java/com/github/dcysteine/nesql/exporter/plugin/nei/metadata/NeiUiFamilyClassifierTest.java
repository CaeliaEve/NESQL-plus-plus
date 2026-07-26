package com.github.dcysteine.nesql.exporter.plugin.nei.metadata;

public final class NeiUiFamilyClassifierTest {
    private NeiUiFamilyClassifierTest() {}

    public static void main(String[] args) {
        requireFamily(
                "botania",
                "vazkii.botania.client.integration.nei.recipe.RecipeHandlerManaPool",
                "Mana Pool",
                "Botania");
        requireFamily(
                "native-nei",
                "pneumaticCraft.common.thirdparty.nei.NEIAssemblyControllerRecipeManager",
                "Assembly Controller",
                "PneumaticCraft");
        requireFamily(
                "native-nei",
                "cofh.thermalexpansion.plugins.nei.handlers.RecipeHandlerCrucible",
                "Crucible",
                "ThermalExpansion");
        requireFamily(
                "native-nei",
                "WayofTime.alchemicalWizardry.client.nei.NEIAltarRecipeHandler",
                "Blood Altar",
                "AWWayofTime");
        requireClassification(
                "native-nei",
                "native-nei",
                "WayofTime.alchemicalWizardry.client.nei.NEIAltarRecipeHandler",
                "Blood Altar",
                "AWWayofTime");
        requireClassification(
                "native-nei",
                "native-nei",
                "example.nei.InfusionRecipeHandler",
                "Infusion Recipes",
                "ExampleMod");
        requireClassification(
                "native-nei",
                "native-nei",
                "example.nei.ManaRecipeManager",
                "Mana Recipes",
                "ExampleMod");
        requireClassification(
                "thaumcraft",
                "native-nei",
                "thaumcraft.client.nei.InfusionRecipeHandler",
                "Arcane Infusion",
                "Thaumcraft");
        requireFamily(
                "fluid-machine",
                "mekanism.client.nei.ChemicalOxidizerRecipeHandler",
                "Chemical Oxidizer",
                "Mekanism");
        requireClassification(
                "fluid-machine",
                "fluid-machine",
                "mekanism.client.nei.ChemicalOxidizerRecipeHandler",
                "Chemical Oxidizer",
                "Mekanism");
        requireFamily(
                "fluid-machine",
                "ic2.neiIntegration.core.recipehandler.FluidCannerRecipeHandler",
                "Fluid Canner",
                "IC2");
    }

    private static void requireFamily(String expected, String handlerClass, String itemName, String modId) {
        String actual = NeiUiFamilyClassifier.classifyHandlerFamily(handlerClass, itemName, modId);
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but got " + actual + " for " + handlerClass);
        }
    }

    private static void requireClassification(
            String expectedFamily,
            String expectedLayout,
            String handlerClass,
            String itemName,
            String modId) {
        String actualFamily = NeiUiFamilyClassifier.classifyHandlerFamily(handlerClass, itemName, modId);
        if (!expectedFamily.equals(actualFamily)) {
            throw new AssertionError(
                    "expected family " + expectedFamily + " but got " + actualFamily + " for " + handlerClass);
        }
        String actualLayout = NeiUiFamilyClassifier.inferLayoutKind(handlerClass, itemName, actualFamily);
        if (!expectedLayout.equals(actualLayout)) {
            throw new AssertionError(
                    "expected layout " + expectedLayout + " but got " + actualLayout + " for " + handlerClass);
        }
    }
}
