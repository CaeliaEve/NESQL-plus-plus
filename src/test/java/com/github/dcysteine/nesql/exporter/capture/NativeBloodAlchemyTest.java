package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;

/** Red test for the native Blood Magic alchemy route. */
public final class NativeBloodAlchemyTest {
    public static void run() throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(
                "WayofTime.alchemicalWizardry.client.nei.NEIAlchemyRecipeHandler").newInstance();
        if (Recipes.adapter(handler) != Recipes.Adapter.BLOOD_ALCHEMY)
            throw new AssertionError("Blood Magic alchemy has no native adapter");
        System.out.println("Native Blood Magic: alchemy adapter routed");
    }
}
