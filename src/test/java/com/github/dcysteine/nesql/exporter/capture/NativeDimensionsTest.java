package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.TemplateRecipeHandler;
import java.util.Arrays;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

/** Native alloy rules survive the removal of NEI viewport capture. */
final class NativeDimensionsTest {
    static void run() throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("tconstruct.plugins.nei.RecipeHandlerAlloying").newInstance();
        FluidStack water = new FluidStack(FluidRegistry.WATER, 3), lava = new FluidStack(FluidRegistry.LAVA, 5);
        FluidStack result = new FluidStack(FluidRegistry.WATER, 7);
        Object alloy = Class.forName("tconstruct.library.crafting.AlloyMix")
                .getConstructor(FluidStack.class, java.util.List.class).newInstance(result, Arrays.asList(water, lava));
        Facts facts = new Facts("en_US");
        Recipes.Handler source = new Recipes.Handler(handler, 0);
        final RecipeRow[] emitted=new RecipeRow[1];
        RegistryRecipes registry=new RegistryRecipes(){
            public int size(){return 1;}
            public boolean capture(int index,RecipeRow row){TinkerRecipes.capture(handler,alloy,row);emitted[0]=row;return true;}
        };
        if (handler.getRecipeHeight(0) != 0) throw new AssertionError("Native fixture no longer has zero GUI height");
        try (Recipes.Cursor cursor = new Recipes.Cursor(source, facts, registry, null)) {
            if(cursor.capture(0).kind!=com.github.dcysteine.nesql.exporter.task.CaptureOutcome.Kind.CAPTURED)throw new AssertionError("Alloy did not commit a semantic recipe");
        }
        RecipeRow row=emitted[0];
        Facts.Batch batch = facts.drain();
        if (!batch.pictures.isEmpty() || row.record.has("view"))
            throw new AssertionError("Alloy emitted native GUI assets");
        if (row.inputs.size() != 2 || row.outputs.get(0).getAsJsonObject().get("amount").getAsInt() != 7
                || water.amount != 3 || lava.amount != 5 || result.amount != 7)
            throw new AssertionError("Removing dimensions changed native alloy quantities");
        System.out.println("Native alloy: zero GUI height does not gate semantic recipe output");
    }
}
