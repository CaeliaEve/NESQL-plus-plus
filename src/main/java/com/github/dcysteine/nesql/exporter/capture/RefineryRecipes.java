package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Galacticraft 3.3.13 tank conversion, independent of its NEI canister illustration. */
final class RefineryRecipes implements RegistryRecipes {
    private static final String GC = "micdoodle8.mods.galacticraft.core.";
    private final TemplateRecipeHandler handler;
    private final List<Fluid> inputs = new ArrayList<>();
    private final Fluid output;
    private final boolean hard;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(GC + "nei.RefineryRecipeHandler"); }
    RefineryRecipes(TemplateRecipeHandler handler) {
        version("GalacticraftCore", "3.3.13-GTNH");
        if (!supports(handler)) throw fault("Unadapted refinery handler");
        this.handler = handler;
        Class<?> core = type(GC + "GalacticraftCore"), config = type(GC + "util.ConfigManagerCore");
        Fluid oil = (Fluid) field(core, null, "fluidOil");
        output = FluidRegistry.getFluid((Boolean) field(config, null, "useOldFuelFluidID") ? "fuelgc" : "fuel");
        if (oil == null || !oil.getName().startsWith("oil") || FluidRegistry.getFluid(oil.getName()) != oil
                || output == null || field(core, null, "fluidFuel") != output)
            throw fault("Refinery tank fluids disagree with the active native configuration");
        hard = (Boolean) field(config, null, "hardMode");
        // fill() accepts precisely this case-sensitive prefix, then normalizes foreign oil to fluidOil.
        for (Map.Entry<String, Fluid> entry : new TreeMap<>(FluidRegistry.getRegisteredFluids()).entrySet()) {
            Jobs.checkpoint();
            if (entry.getKey().startsWith("oil")) inputs.add(entry.getValue());
        }
    }
    public int size() { return inputs.size(); }
    public boolean capture(int index, RecipeRow row) {
        // Reuse native positions, but the semantic slots contain fluids, not disposable canisters.
        row.fluidInput(null, 0, new FluidStack(inputs.get(index), 1), false,
                object("kind", "wildcard", "meta", false, "nbt", true));
        row.fluidOutput(null, 0, new FluidStack(output, 1));
        row.slot("input", "fluid", 0, 2, 3);
        row.slot("output", "fluid", 0, 148, 3);
        row.record.addProperty("duration", "2");
        row.property("galacticraft:startupDelay", "Initial startup delay (ticks)", 1);
        row.property("galacticraft:energyPerTick", "Energy per tick (gJ/t)", hard ? 90 : 60);
        handler.arecipes.clear(); handler.arecipes.add(handler.new CachedRecipe() {
            @Override public PositionedStack getResult() { return null; }
        });
        return true;
    }

    static void draw(TemplateRecipeHandler handler) {
        codechicken.lib.gui.GuiDraw.changeTexture(handler.getGuiTexture());
        org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
        // The original method interleaves all three animations with the base; capture only this base quad.
        codechicken.lib.gui.GuiDraw.drawTexturedModalRect(-2, 0, 3, 4, 168, 64);
    }
    static JsonArray progress(int layer) {
        if (layer < 0 || layer > 2) throw fault("Invalid refinery animation layer");
        JsonArray frames = new JsonArray();
        // Native onUpdate advances ticksPassed by two, so its 144-unit clock lasts 72 game ticks.
        for (int step = 0; step < 72; step++) {
            int phase = step * 2;
            boolean left = phase > 40 && phase < 104;
            JsonArray areas = new JsonArray();
            if (layer == 0 && left || layer == 1 && !left && phase < 124) areas.add(array("0", "0", "1", "1"));
            if (layer == 2 && phase > 0) areas.add(array("0", "0", Float.toString(phase / 144f), "1"));
            JsonObject last = frames.size() == 0 ? null : frames.get(frames.size() - 1).getAsJsonObject();
            if (last != null && last.get("areas").equals(areas)) last.addProperty("ticks", last.get("ticks").getAsInt() + 1);
            else frames.add(object("ticks", 1, "areas", areas));
        }
        return frames;
    }
    static JsonArray decorations(Facts facts, TemplateRecipeHandler handler, String location) {
        JsonArray result = new JsonArray();
        int[][] regions = {{2, 42, 176, 6, 16, 20}, {148, 42, 192, 6, 16, 20}, {21, 21, 0, 186, 144, 20}};
        for (int layer = 0; layer < regions.length; layer++) {
            int[] area = regions[layer];
            JsonObject element = object("kind", "clip", "asset", null, "x", area[0], "y", area[1], "width", area[4], "height", area[5],
                    "z", 0, "track", facts.track(progress(layer)));
            facts.picture(new Facts.Picture(element, "asset", location + "/progress/" + layer, area[4], area[5], () -> {
                codechicken.lib.gui.GuiDraw.changeTexture(handler.getGuiTexture()); org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
                codechicken.lib.gui.GuiDraw.drawTexturedModalRect(0, 0, area[2], area[3], area[4], area[5]);
            }));
            result.add(element);
        }
        return result;
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
