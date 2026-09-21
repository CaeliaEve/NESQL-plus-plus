package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Native slot order disambiguates overlaps; raw alternative inputs can have no base stack. */
final class SlotsTest {
    private SlotsTest() {}

    static void run() {
        ItemStack first = new ItemStack(Items.paper, 3), second = new ItemStack(Items.diamond, 9);
        FluidStack water = new FluidStack(FluidRegistry.WATER, 125);
        List<GtRecipes.Binding> slots = Arrays.asList(
                new GtRecipes.Binding(0, false, false, false, true, 10, 20),
                new GtRecipes.Binding(0, false, false, false, false, 10, 20),
                new GtRecipes.Binding(1, false, false, false, false, 30, 20),
                new GtRecipes.Binding(2, false, false, true, false, 10, 20),
                new GtRecipes.Binding(0, true, false, false, false, 10, 20));
        // The middle slot is empty; three populated output slots overlap exactly.
        List<PositionedStack> displays = Arrays.asList(display(), display(), display());
        java.util.function.Function<GtRecipes.Binding, Object> source = binding -> binding.input ? first
                : binding.fluid ? water : binding.index == 0 ? first : binding.index == 2 ? second : null;
        List<GtRecipes.Placement> projected = GtRecipes.project(slots, source, displays, false);
        require(projected.size() == 3 && projected.get(0).source == first && projected.get(1).source == second
                && projected.get(1).binding.overflow && projected.get(2).source == water, "Overlapping native slots lost their identities or source values");
        require(GtRecipes.project(slots, source, Collections.singletonList(display()), true).get(0).source == first,
                "Input/output directions collided");
        Facts facts = new Facts("en_US");
        Set<String> known = ReflectionHelper.getPrivateValue(Facts.class, facts, "items");
        known.add(Identity.item("minecraft:paper", 0, null)); known.add(Identity.item("minecraft:diamond", 0, null));
        RecipeRow row = new RecipeRow(facts, object("owner", "fixture", "handler", "slots", "key", "overlap"), "fixture", 0);
        for (GtRecipes.Placement placed : projected) {
            if (placed.binding.fluid) row.fluidOutput(placed.display, placed.binding.index, (FluidStack) placed.source);
            else row.itemOutput(placed.display, placed.binding.index, (ItemStack) placed.source, 10000);
        }
        require(row.outputs.get(0).getAsJsonObject().get("amount").getAsString().equals("3")
                && row.outputs.get(1).getAsJsonObject().get("amount").getAsString().equals("9")
                && row.outputs.get(2).getAsJsonObject().get("amount").getAsString().equals("125")
                && row.outputs.get(1).getAsJsonObject().get("slot").getAsInt() == 2
                && row.elements.size() == 3, "Projection replaced source quantities with display quantities");
        reject("slot_missing", () -> GtRecipes.project(slots, source, displays.subList(0, 2), false));
        reject("slot_missing", () -> GtRecipes.project(slots, source, Arrays.asList(display(), display(), display(), display()), false));
        List<PositionedStack> moved = Arrays.asList(display(), new PositionedStack(first, 11, 20, false), display());
        reject("slot_changed", () -> GtRecipes.project(slots, source, moved, false));

        ItemStack[] choices = {new ItemStack(Items.iron_axe), new ItemStack(Items.diamond_axe)};
        Object[] inputs = GtRecipes.inputs(new ItemStack[0], new ItemStack[][] {choices, null, {new ItemStack(Items.stick, 0)}});
        require(inputs.length == 3 && inputs[1] == null,
                "Alternative-only inputs were indexed through the empty base array");
        ItemStack[] copies = (ItemStack[]) inputs[0];
        require(copies.length == 2 && copies[0] != choices[0] && copies[1].getItem() == Items.diamond_axe,
                "Alternative projection lost copies or choices");
        require(GtRecipes.ingredient(inputs[2]).stackSize == 0, "Catalyst input lost its zero quantity");
        copies[0].stackSize = 8;
        require(choices[0].stackSize == 1, "Projection mutated the registered recipe");
        reject("recipe_unsupported", () -> GtRecipes.ingredient(copies));
        Object[] fallback = GtRecipes.inputs(new ItemStack[] {first, second}, new ItemStack[][] {new ItemStack[0]});
        require(fallback.length == 2 && ((ItemStack) fallback[0]).stackSize == 3 && fallback[1] != second,
                "An empty or absent alternative hid the base source");
        System.out.println("GT slots: ordered overlaps, empty slots, input/output separation, exact output quantities and alternative-only inputs passed");
    }

    private static PositionedStack display() { return new PositionedStack(new ItemStack(Items.paper), 10, 20, false); }
    private static void reject(String code, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + code); }
        catch (Jobs.Fault expected) { require(expected.code.equals(code), "Wrong projection failure: " + expected.code); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
