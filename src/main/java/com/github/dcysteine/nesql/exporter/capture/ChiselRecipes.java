package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Chisel pages are one input group with alternative carving targets. */
final class ChiselRecipes implements RegistryRecipes {
    private static final String HANDLER = "team.chisel.compat.nei.RecipeHandlerChisel";
    private final TemplateRecipeHandler handler;
    private final List<Page> pages;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return HANDLER.equals(name); }

    ChiselRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown Chisel handler");
        this.handler = handler;
        handler.loadCraftingRecipes("chisel2.chisel");
        int count = handler.numRecipes();
        if (count <= 0 || count > 65536) throw fault("Chisel registry has an invalid size: " + count);
        pages = new ArrayList<>();
        for (int index = 0; index < count; index++) pages.add(page(handler, index));
    }
    ChiselRecipes(TemplateRecipeHandler handler, List<Page> pages) {
        if (!supports(handler) || pages == null || pages.isEmpty() || pages.size() > 65536) throw fault("Invalid Chisel fixture");
        this.handler = handler;
        this.pages = new ArrayList<>(pages);
    }
    public int size() { return pages.size(); }

    private static Page page(TemplateRecipeHandler handler, int index) {
        List<PositionedStack> inputs = handler.getIngredientStacks(index);
        List<PositionedStack> outputs = handler.getOtherStacks(index);
        if (inputs == null || inputs.size() != 1 || inputs.get(0) == null || inputs.get(0).item == null)
            throw fault("Chisel page must have one input");
        if (outputs == null || outputs.isEmpty() || outputs.size() > 45) throw fault("Chisel page has invalid outputs");
        return new Page(inputs.get(0), outputs);
    }

    public boolean capture(int index, RecipeRow row) {
        return !captureRows(index, row).isEmpty();
    }

    public List<RecipeRow> captureRows(int index, RecipeRow row) {
        Page page = pages.get(index);
        PositionedStack input = page.input;
        if (input.items == null || input.items.length == 0) throw fault("Chisel input has no candidates");
        boolean wildcard = input.items.length != 1;
        for (ItemStack candidate : input.items) if (candidate == null || candidate.getItemDamage() == Short.MAX_VALUE) wildcard = true;
        List<RecipeRow> rows = new ArrayList<>();
        row.property("chisel:toolRequired", "Requires a compatible chisel tool", true);
        row.property("chisel:toolWear", "Tool wear per successful carving", 1);
        row.property("chisel:targetTag", "Selected target is stored on the chisel", "chiselTarget");
        for (PositionedStack output : page.outputs) {
            if (output == null || output.item == null || output.items == null || output.items.length == 0) throw fault("Chisel output is empty");
            if (output.items.length != 1) throw fault("Chisel output has an unadapted alternative list");
            RecipeRow branch = rows.isEmpty() ? row : row.branch();
            branch.itemInput(input, 0, Math.max(1, input.item.stackSize), false, false,
                    object("kind", wildcard ? "wildcard" : "exact", "meta", wildcard, "nbt", true));
            branch.itemOutput(output, 0, output.item, 10000);
            rows.add(branch);
        }
        return rows;
    }
    static final class Page {
        final PositionedStack input;
        final List<PositionedStack> outputs;
        Page(PositionedStack input, List<PositionedStack> outputs) {
            this.input = input;
            this.outputs = Collections.unmodifiableList(new ArrayList<>(outputs));
        }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
