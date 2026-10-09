package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;

/** Red test for the LogisticsPipes soldering-station adapter. */
public final class NativeLogisticsPipesTest {
    public static void run() throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName(
                "logisticspipes.nei.NEISolderingStationRecipeManager").newInstance();
        if (Recipes.adapter(handler) != Recipes.Adapter.LOGISTICS_SOLDERING)
            throw new AssertionError("LogisticsPipes soldering station has no native adapter");
        System.out.println("Native LogisticsPipes: soldering station adapter routed");
    }
}
