package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.source.Json.object;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** IC2 Advanced crafting uses its own recipe classes and absolute masks; it is not vanilla crafting. */
final class Ic2AdvancedRecipes implements RegistryRecipes {
    private static final String HANDLERS = "ic2.neiIntegration.core.recipehandler.";
    private final TemplateRecipeHandler handler;
    private final List<IRecipe> recipes = new ArrayList<>();
    private final boolean shapeless;
    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) {
        return (HANDLERS + "AdvRecipeHandler").equals(name) || (HANDLERS + "AdvShapelessRecipeHandler").equals(name);
    }
    Ic2AdvancedRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown IC2 advanced crafting handler");
        this.handler = handler;
        shapeless = handler.getClass().getName().endsWith("AdvShapelessRecipeHandler");
        for (Object value : CraftingManager.getInstance().getRecipeList()) {
            if (!(value instanceof IRecipe)) continue;
            String name = value.getClass().getName();
            if (shapeless ? "ic2.core.AdvShapelessRecipe".equals(name) : "ic2.core.AdvRecipe".equals(name)) recipes.add((IRecipe)value);
        }
        if (recipes.size() > 262144) throw fault("IC2 advanced registry exceeds its budget");
    }
    public int size() { return recipes.size(); }
    public boolean capture(int index, RecipeRow row) {
        IRecipe recipe = recipes.get(index);
        ItemStack output = recipe.getRecipeOutput();
        if (output == null || output.getItem() == null || output.stackSize <= 0) throw fault("IC2 advanced recipe has no output");
        if (Boolean.TRUE.equals(field(recipe, "hidden"))) return false;
        Object raw = field(recipe, "input");
        if (!(raw instanceof Object[])) throw fault("IC2 advanced recipe input field changed");
        Object[] inputs = (Object[])raw;
        int width = 3, height = 3;
        if (!shapeless) {
            Object w = field(recipe, "inputWidth"), h = field(recipe, "inputHeight");
            width = ((Number) w).intValue(); height = ((Number) h).intValue();
            if (width < 1 || width > 3 || height < 1 || height > 3 || inputs.length != width * height)
                throw fault("IC2 advanced shaped mask is outside the 3x3 contract");
        }
        int slot = 0; com.google.gson.JsonArray cells = new com.google.gson.JsonArray();
        for (int i = 0; i < (shapeless ? inputs.length : width * height); i++) {
            Object value = inputs[i];
            if (value == null || value instanceof Character || value instanceof Boolean) {
                cells.add(com.google.gson.JsonNull.INSTANCE);
                continue;
            }
            ItemStack[] candidates = candidates(value);
            if (candidates.length == 0) throw fault("IC2 advanced recipe has an empty input group");
            int nativeSlot = shapeless ? slot : i;
            int visualSlot = shapeless ? slot : i;
            PositionedStack display = new PositionedStack(candidates, shapedX(visualSlot), shapedY(visualSlot), false);
            int amount = amount(value);
            row.itemInput(display, nativeSlot, amount, false, true, object("kind", "wildcard", "meta", true, "nbt", true));
            cells.add(new com.google.gson.JsonPrimitive(slot++));
        }
        PositionedStack result = new PositionedStack(output.copy(), 119, 24);
        row.itemOutput(result, 0, output, 10000);
        if (!shapeless) row.record.add("grid", object("width", width, "height", height, "cells", cells,
                "mirror", field(recipe, "masksMirrored") != null));
        return true;
    }
    private static int amount(Object value) {
        String name = value.getClass().getName();
        if (name.equals("ic2.api.recipe.RecipeInputItemStack") || name.equals("ic2.api.recipe.RecipeInputOreDict")) {
            Object amount = invoke(value.getClass(), value, "getAmount", new Class<?>[0]);
            if (amount instanceof Number && ((Number) amount).intValue() > 0) return ((Number) amount).intValue();
        }
        return 1;
    }
    private int shapedX(int slot) { return shapeless ? 25 + (slot % 3) * 18 : 25 + (slot % 3) * 18; }
    private int shapedY(int slot) { return shapeless ? 6 + (slot / 3) * 18 : 6 + (slot / 3) * 18; }
    private static ItemStack[] candidates(Object value) {
        if (value instanceof ItemStack) return new ItemStack[] {((ItemStack)value).copy()};
        if (value instanceof Item) return new ItemStack[] {new ItemStack((Item)value, 1, OreDictionary.WILDCARD_VALUE)};
        if (value instanceof Block) return new ItemStack[] {new ItemStack((Block)value, 1, OreDictionary.WILDCARD_VALUE)};
        if (value instanceof String) {
            String name = (String) value;
            if (name.startsWith("liquid$")) {
                List<ItemStack> fluids = new ArrayList<>();
                String fluid = name.substring(7);
                for (FluidContainerRegistry.FluidContainerData data : FluidContainerRegistry.getRegisteredFluidContainerData())
                    if (data.fluid != null && data.fluid.getFluid() != null && fluid.equals(data.fluid.getFluid().getName()) && data.filledContainer != null) fluids.add(data.filledContainer.copy());
                return fluids.toArray(new ItemStack[fluids.size()]);
            }
            List<ItemStack> ores = OreDictionary.getOres(name);
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack item : ores) if (item != null && item.getItem() != null) copies.add(item.copy());
            return copies.toArray(new ItemStack[copies.size()]);
        }
        if (value.getClass().isArray()) {
            List<ItemStack> result = new ArrayList<>();
            for (int i=0; i<Array.getLength(value); i++) result.addAll(java.util.Arrays.asList(candidates(Array.get(value, i))));
            return result.toArray(new ItemStack[result.size()]);
        }
        if (value instanceof Iterable<?>) {
            List<ItemStack> result = new ArrayList<>();
            for (Object item : (Iterable<?>)value) result.addAll(java.util.Arrays.asList(candidates(item)));
            return result.toArray(new ItemStack[result.size()]);
        }
        String name = value.getClass().getName();
        if (name.equals("ic2.api.recipe.RecipeInputItemStack") || name.equals("ic2.api.recipe.RecipeInputOreDict")) {
            Object result = invoke(value.getClass(), value, "getInputs", new Class<?>[0]);
            if (!(result instanceof List<?>)) throw fault("IC2 advanced input predicate changed");
            List<ItemStack> copies = new ArrayList<>();
            for (Object item : (List<?>)result) if (item instanceof ItemStack) copies.add(((ItemStack)item).copy());
            return copies.toArray(new ItemStack[copies.size()]);
        }
        throw fault("Unadapted IC2 advanced input: " + name);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
