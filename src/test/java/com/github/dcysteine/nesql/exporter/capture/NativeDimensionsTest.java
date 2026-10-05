package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Scene emission against the real TConstruct cached layout; no client or GL required. */
final class NativeDimensionsTest {
    static void run() throws Exception {
        TemplateRecipeHandler handler = (TemplateRecipeHandler) Class.forName("tconstruct.plugins.nei.RecipeHandlerAlloying").newInstance();
        FluidStack water = new FluidStack(FluidRegistry.WATER, 3), lava = new FluidStack(FluidRegistry.LAVA, 5);
        FluidStack result = new FluidStack(FluidRegistry.WATER, 7);
        Object alloy = Class.forName("tconstruct.library.crafting.AlloyMix")
                .getConstructor(FluidStack.class, java.util.List.class).newInstance(result, Arrays.asList(water, lava));
        Facts facts = new Facts("en_US");
        Recipes.Handler source = new Recipes.Handler(handler, 0);
        RecipeRow row = new RecipeRow(facts, source.origin, source.id, 0);
        TinkerRecipes.capture(handler, alloy, row);
        require(handler.getRecipeHeight(0) == 0 && row.elements.size() == 0,
                "Native alloy no longer exposes the zero-height, fluid-only layout");
        Facts.Scene scene = emit(handler, row);
        require(scene.height == 65 && scene.width >= 160, "Native alloy background was lost or clipped");
        require(row.inputs.size() == 2 && row.outputs.get(0).getAsJsonObject().get("amount").getAsInt() == 7
                && water.amount == 3 && lava.amount == 5 && result.amount == 7, "Visual sizing changed alloy quantities");

        // NEIRecipeWidget uses a positive per-recipe height; otherwise HandlerInfo's height.
        for (int[] test : new int[][] {{0, 91, 91}, {-1, 77, 77}, {42, 91, 42}, {2048, 65, 2048}}) {
            SizedHandler sized = new SizedHandler(test[0]);
            HandlerInfo prior = GuiRecipeTab.handlerMap.put(sized.getHandlerId(),
                    new HandlerInfo.Builder(sized.getHandlerId(), "Sizing", "NEI").setHeight(test[1]).build());
            try {
                RecipeRow plain = row(sized);
                require(emit(sized, plain).height == test[2], "Scene differs from NEI's configured/per-recipe height");
                if (test[0] == 0) {
                    RecipeRow slots = row(sized); slots.slot("input", "fluid", 0, 5, 10);
                    require(emit(sized, slots).height == 91, "Small slots clipped the configured native background");
                    RecipeRow tall = row(sized); tall.slot("input", "fluid", 0, 5, 100);
                    require(emit(sized, tall).height == 116, "Native slot extents were clipped by fallback sizing");
                }
            } finally { restore(sized, prior); }
        }
        for (int[] test : new int[][] {{2049, 65}, {0, 2049}, {0, 0}}) {
            SizedHandler sized = new SizedHandler(test[0]);
            HandlerInfo prior = GuiRecipeTab.handlerMap.put(sized.getHandlerId(),
                    new HandlerInfo.Builder(sized.getHandlerId(), "Sizing", "NEI").setHeight(test[1]).build());
            try {
                try { emit(sized, row(sized)); throw new AssertionError("Invalid native dimensions accepted"); }
                catch (Jobs.Fault expected) { require(expected.code.equals("view_limit"), "Wrong dimension failure"); }
            } finally { restore(sized, prior); }
        }
        System.out.println("Native dimensions: TConstruct alloy scene, NEI height fallback, positive overrides, slot extents and limits passed");
    }

    private static RecipeRow row(TemplateRecipeHandler handler) {
        Recipes.Handler source = new Recipes.Handler(handler, 0);
        RecipeRow row = new RecipeRow(new Facts("en_US"), source.origin, source.id, 0);
        row.fluidInput(null, 0, new FluidStack(FluidRegistry.WATER, 3));
        row.fluidOutput(null, 0, new FluidStack(FluidRegistry.LAVA, 7));
        return row;
    }
    private static Facts.Scene emit(TemplateRecipeHandler handler, RecipeRow row) throws Exception {
        Recipes.Cursor cursor = new Recipes.Cursor(new Recipes.Handler(handler, 0), handler, row.facts,
                true, null, null, null, null, null);
        Method emit = Recipes.Cursor.class.getDeclaredMethod("emit", int.class, RecipeRow.class, Facts.class);
        emit.setAccessible(true);
        try { emit.invoke(cursor, 0, row, row.facts); }
        catch (InvocationTargetException error) {
            if (error.getCause() instanceof Error) throw (Error) error.getCause();
            throw (Exception) error.getCause();
        }
        Facts.Batch batch = row.facts.drain();
        require(batch.scenes.size() == 1, "Recipe was not emitted as one native scene");
        return batch.scenes.get(0);
    }
    private static void restore(TemplateRecipeHandler handler, HandlerInfo prior) {
        if (prior == null) GuiRecipeTab.handlerMap.remove(handler.getHandlerId());
        else GuiRecipeTab.handlerMap.put(handler.getHandlerId(), prior);
    }
    private static final class SizedHandler extends TemplateRecipeHandler {
        private final int height;
        SizedHandler(int height) { this.height = height; }
        @Override public int getRecipeHeight(int index) { return height; }
        @Override public String getRecipeName() { return "Sizing"; }
        @Override public String getGuiTexture() { return ""; }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
