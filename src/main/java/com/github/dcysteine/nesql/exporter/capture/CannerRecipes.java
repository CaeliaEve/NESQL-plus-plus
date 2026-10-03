package com.github.dcysteine.nesql.exporter.capture;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import java.lang.reflect.Field;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** IC2 2.2.828 bottle/enrichment registries; native display cells are never recipe substances. */
final class CannerRecipes implements RegistryRecipes {
    private static final String HANDLERS = "ic2.neiIntegration.core.recipehandler.", API = "ic2.api.recipe.";
    private final TemplateRecipeHandler handler;
    private final boolean fluid;
    private final List<Map.Entry<?, ?>> sources = new ArrayList<>();
    private final Entry[] entries;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(HANDLERS + "SolidCannerRecipeHandler") || name.equals(HANDLERS + "FluidCannerRecipeHandler");
    }
    private static boolean fluid(TemplateRecipeHandler handler) { return handler.getClass().getName().equals(HANDLERS + "FluidCannerRecipeHandler"); }
    private static Object manager(TemplateRecipeHandler handler) {
        version("IC2", "2.2.828-experimental");
        return field(type(API + "Recipes"), null, fluid(handler) ? "cannerEnrich" : "cannerBottle");
    }
    CannerRecipes(TemplateRecipeHandler handler) { this(handler, manager(handler)); }
    CannerRecipes(TemplateRecipeHandler handler, Object manager) {
        this.handler = handler; this.fluid = fluid(handler);
        if (!supports(handler) || manager == null || !manager.getClass().getName().equals("ic2.core.block.machine.Canner" + (fluid ? "Enrich" : "Bottle") + "RecipeManager")) {
            throw fault("Unadapted IC2 canner manager");
        }
        Map<?, ?> recipes = (Map<?, ?>) invoke(manager.getClass(), manager, "getRecipes", new Class<?>[0]);
        if (recipes.size() > 262144) throw fault("IC2 canner registry exceeds its budget");
        for (Map.Entry<?, ?> source : recipes.entrySet()) { Jobs.checkpoint(); sources.add(new AbstractMap.SimpleImmutableEntry<>(source)); }
        entries = new Entry[sources.size()];
    }
    public int size() { return sources.size(); }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = entry(index);
        // Native HashMap iteration picks the first matching predicate before checking counts.
        // addRecipe normally rejects collisions, but getRecipes is mutable and ore lists can grow.
        // Do not flatten a colliding selector into independent unconditional recipe choices.
        long[] budget = {1048576};
        for (int other = 0; other < size(); other++) {
            Jobs.checkpoint();
            if (other == index) continue;
            Entry sibling = entry(other);
            if (fluid ? entry.input.isFluidEqual(sibling.input) && overlaps(entry.fill, sibling.fill, budget)
                    : overlaps(entry.container, sibling.container, budget) && overlaps(entry.fill, sibling.fill, budget)) {
                throw fault("Overlapping native canner selectors at " + index + " and " + other);
            }
        }
        TemplateRecipeHandler.CachedRecipe cached = cached(entry);
        List<PositionedStack> positions = positions(cached);
        if (positions.size() != 2 || cached.getResult() == null) throw new Jobs.Fault("slot_changed", "Native canner layout changed");
        if (fluid) {
            row.fluidInput(positions.get(0), 0, entry.input, false);
            row.itemInput(positions.get(1), 0, entry.fill, false);
            row.fluidOutput(cached.getResult(), 0, entry.output);
            row.property("ic2:cannerMode", "Mode", "EnrichLiquid");
            row.property("ic2:cannerTanks", "Input/output tank capacity (mB each)", 8000);
            row.property("ic2:cannerPackaging", "Enriched fluid enters the output tank; installed containers may then be filled separately", true);
        } else {
            row.itemInput(positions.get(0), 0, entry.container, false);
            row.itemInput(positions.get(1), 1, entry.fill, false);
            for (int i = 0; i < entry.products.size(); i++) row.itemOutput(i == 0 ? cached.getResult() : null, i, entry.products.get(i), 10000);
        }
        row.record.addProperty("duration", "200");
        row.record.addProperty("energy", fluid ? "4" : "2");
        handler.arecipes.clear(); handler.arecipes.add(cached);
        return true;
    }
    @SuppressWarnings("unchecked")
    private static List<PositionedStack> positions(TemplateRecipeHandler.CachedRecipe cached) {
        return (List<PositionedStack>) field(cached, "ingredients");
    }
    private Entry entry(int index) {
        if (entries[index] != null) return entries[index];
        Map.Entry<?, ?> source = sources.get(index);
        Object key = source.getKey();
        if (key == null || !key.getClass().getName().equals(API + "ICanner" + (fluid ? "Enrich" : "Bottle") + "RecipeManager$Input")) throw fault("Unknown canner selector");
        Entry entry = new Entry();
        entry.fill = Ic2Recipes.ingredients(field(key, fluid ? "additive" : "fill"));
        if (fluid) {
            entry.input = fluid(field(key, "fluid")); entry.output = fluid(source.getValue());
        } else {
            entry.container = Ic2Recipes.ingredients(field(key, "container"));
            Object output = source.getValue();
            if (output == null || !output.getClass().getName().equals(API + "RecipeOutput")) throw fault("Unknown canner output");
            List<?> products = (List<?>) field(output, "items");
            if (products.isEmpty() || products.size() > 4096) throw fault("Invalid canner output count");
            entry.products = new ArrayList<>();
            for (Object value : products) {
                if (!(value instanceof ItemStack) || ((ItemStack)value).getItem() == null || ((ItemStack)value).stackSize <= 0) throw fault("Invalid canner item output");
                entry.products.add(((ItemStack)value).copy());
            }
        }
        return entries[index] = entry;
    }
    private static FluidStack fluid(Object value) {
        if (!(value instanceof FluidStack) || ((FluidStack)value).getFluid() == null || ((FluidStack)value).amount <= 0) throw fault("Invalid canner fluid");
        return ((FluidStack)value).copy();
    }
    private TemplateRecipeHandler.CachedRecipe cached(Entry entry) {
        Class<?> input = type(API + "IRecipeInput");
        try {
            if (fluid) return (TemplateRecipeHandler.CachedRecipe) type(HANDLERS + "FluidCannerRecipeHandler$CachedFluidCannerRecipe")
                    .getConstructor(handler.getClass(), input, FluidStack.class, FluidStack.class)
                    .newInstance(handler, Ic2Recipes.projection(entry.fill), entry.input.copy(), entry.output.copy());
            List<ItemStack> copies = new ArrayList<>(); for (ItemStack item : entry.products) copies.add(item.copy());
            Class<?> output = type(API + "RecipeOutput");
            Object result = output.getConstructor(NBTTagCompound.class, List.class).newInstance(null, copies);
            return (TemplateRecipeHandler.CachedRecipe) type(HANDLERS + "SolidCannerRecipeHandler$CachedSolidCannerRecipe")
                    .getConstructor(handler.getClass(), input, input, output)
                    .newInstance(handler, Ic2Recipes.projection(entry.container), Ic2Recipes.projection(entry.fill), result);
        } catch (ReflectiveOperationException error) { Jobs.Fault failure = fault("Cannot construct owned IC2 canner layout"); failure.initCause(error); throw failure; }
    }
    private static boolean overlaps(List<RecipeRow.Ingredient> left, List<RecipeRow.Ingredient> right, long[] budget) {
        for (RecipeRow.Ingredient a : left) for (RecipeRow.Ingredient b : right) {
            Jobs.checkpoint();
            if (--budget[0] < 0) throw fault("Canner selector overlap check exceeds its budget");
            if (a.item.getItem() == b.item.getItem() && (a.rule.get("meta").getAsBoolean() || b.rule.get("meta").getAsBoolean()
                    || a.item.getItemDamage() == b.item.getItemDamage())) return true;
        }
        return false;
    }
    static int[][] progressBars(TemplateRecipeHandler handler) {
        return fluid(handler) ? new int[][] {{68, 26, 233, 0, 25, 16, 20, 0}} : new int[][] {{83, 19, 176, 14, 25, 16, 20, 0}};
    }
    // The fluid handler draws its 85px native background from y=20, below its item slots.
    static int height(TemplateRecipeHandler handler) { return fluid(handler) ? 105 : 65; }
    static void scene(TemplateRecipeHandler handler, Runnable draw) {
        try {
            Field clock = handler.getClass().getDeclaredField("ticks"); clock.setAccessible(true);
            int previous = clock.getInt(handler);
            try { clock.setInt(handler, 20); draw.run(); } finally { clock.setInt(handler, previous); }
        } catch (ReflectiveOperationException error) { Jobs.Fault failure = fault("Cannot preserve IC2 canner UI clock"); failure.initCause(error); throw failure; }
    }
    static void draw(TemplateRecipeHandler handler, int index) {
        handler.drawBackground(index);
        if (!fluid(handler)) { handler.drawForeground(index); return; }
        // Native drawExtras also asks the live GuiRecipe for mouse coordinates. Capture only
        // its steady energy display; tanks stay native, progress is its separate texture track.
        GuiDraw.changeTexture(handler.getGuiTexture());
        GuiDraw.drawTexturedModalRect(3, 65, 176, 0, 14, 14);
    }
    private static final class Entry {
        List<RecipeRow.Ingredient> container, fill;
        List<ItemStack> products;
        FluidStack input, output;
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
