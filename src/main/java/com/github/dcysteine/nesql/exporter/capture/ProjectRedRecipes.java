package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** ProjectRed's absolute-grid crafting builders. Their NEI handlers are not ordinary shaped/shapeless handlers. */
final class ProjectRedRecipes implements RegistryRecipes {
    private static final String ROOT = "mrtjp.projectred.core.libmc.recipe.";
    private static final String SHAPED_HANDLER = ROOT + "PRShapedRecipeHandler";
    private static final String SHAPELESS_HANDLER = ROOT + "PRShapelessRecipeHandler";
    private static final String SHAPED_RECIPE = ROOT + "ShapedBuilderRecipe";
    private static final String SHAPELESS_RECIPE = ROOT + "ShapelessBuilderRecipe";
    private final TemplateRecipeHandler handler;
    private final List<IRecipe> sources = new ArrayList<>();
    private final List<TemplateRecipeHandler.CachedRecipe> caches = new ArrayList<>();
    private final boolean shaped;

    static boolean supports(ICraftingHandler value) { return supportsClass(value.getClass().getName()); }
    static boolean supportsClass(String name) { return SHAPED_HANDLER.equals(name) || SHAPELESS_HANDLER.equals(name); }

    ProjectRedRecipes(TemplateRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unknown ProjectRed handler: " + handler.getClass().getName());
        this.handler = handler;
        shaped = SHAPED_HANDLER.equals(handler.getClass().getName());
        String recipeType = shaped ? SHAPED_RECIPE : SHAPELESS_RECIPE;
        for (Object value : CraftingManager.getInstance().getRecipeList()) {
            Jobs.checkpoint();
            if (!(value instanceof IRecipe) || value.getClass().getName().equals(recipeType) == false) continue;
            IRecipe recipe = (IRecipe) value;
            sources.add(recipe);
            caches.add(cache(recipe));
        }
        if (sources.size() != caches.size() || sources.isEmpty() || sources.size() > 262144)
            throw fault("ProjectRed registry has an invalid recipe count: " + sources.size());
    }

    public int size() { return sources.size(); }

    public boolean capture(int index, RecipeRow row) {
        if (handler.arecipes.size() != 0) handler.arecipes.clear();
        IRecipe source = sources.get(index);
        TemplateRecipeHandler.CachedRecipe cached = caches.get(index);
        List<PositionedStack> inputs = cached.getIngredients();
        if (inputs == null || inputs.isEmpty() || inputs.size() > 9) throw fault("ProjectRed recipe has invalid inputs");
        int slot = 0;
        for (PositionedStack input : inputs) {
            if (input == null || input.item == null || input.items == null || input.items.length == 0)
                throw fault("ProjectRed recipe has an empty input");
            int nativeSlot = shaped ? absoluteSlot(input) : slot;
            boolean wildcard = false;
            for (ItemStack candidate : input.items) wildcard |= candidate.getItemDamage() == Short.MAX_VALUE;
            row.itemInput(input, nativeSlot, 1, false, true, object("kind", "wildcard", "meta", wildcard, "nbt", true));
            slot++;
        }
        PositionedStack output = cached.getResult();
        if (output == null || output.item == null || output.items == null || output.items.length == 0)
            throw fault("ProjectRed recipe has no output");
        row.itemOutput(output, 0, output.item, 10000);
        if (shaped) {
            Object builder = field(source, "builder");
            int size = ((Number) field(builder, "size")).intValue();
            if (size < 1 || size > 3) throw fault("ProjectRed shaped grid exceeds the vanilla 3x3 view");
            int width = 0, height = 0;
            for (PositionedStack input : inputs) {
                int nativeSlot = absoluteSlot(input);
                width = Math.max(width, nativeSlot % 3 + 1);
                height = Math.max(height, nativeSlot / 3 + 1);
            }
            if (width > size || height > size) throw fault("ProjectRed native builder size disagrees with its NEI layout");
            int[] cellSlots = new int[size * size];
            java.util.Arrays.fill(cellSlots, -1);
            for (PositionedStack input : inputs) {
                int nativeSlot = absoluteSlot(input);
                int local = (nativeSlot / 3) * size + (nativeSlot % 3);
                if (local >= cellSlots.length || cellSlots[local] >= 0) throw fault("ProjectRed shaped slot is outside its native grid");
                cellSlots[local] = slotForInput(inputs, input);
            }
            com.google.gson.JsonArray cells = new com.google.gson.JsonArray();
            for (int cell : cellSlots) cells.add(cell < 0 ? com.google.gson.JsonNull.INSTANCE : new com.google.gson.JsonPrimitive(cell));
            row.record.add("grid", object("width", size, "height", size, "cells", cells, "mirror", false));
        }
        handler.arecipes.add(cached);
        return true;
    }

    private TemplateRecipeHandler.CachedRecipe cache(IRecipe source) {
        try {
            String suffix = shaped ? "$CachedShapedRecipe" : "$CachedShapelessRecipe";
            Class<?> type = Class.forName(handler.getClass().getName() + suffix);
            Constructor<?> constructor = null;
            for (Constructor<?> candidate : type.getDeclaredConstructors()) {
                Class<?>[] parameters = candidate.getParameterTypes();
                if (parameters.length == 2 && parameters[0].isAssignableFrom(handler.getClass())
                        && parameters[1].isAssignableFrom(source.getClass())) { constructor = candidate; break; }
            }
            if (constructor == null) throw new NoSuchMethodException(type.getName());
            constructor.setAccessible(true);
            TemplateRecipeHandler.CachedRecipe result = (TemplateRecipeHandler.CachedRecipe) constructor.newInstance(handler, source);
            try { invoke(result.getClass(), result, "computeVisuals", new Class<?>[0]); }
            catch (Jobs.Fault ignored) { /* shapeless cache has no computeVisuals hook */ }
            return result;
        } catch (ReflectiveOperationException error) {
            Jobs.Fault failure = fault("ProjectRed native cache constructor changed");
            failure.initCause(error);
            throw failure;
        }
    }

    private static int absoluteSlot(PositionedStack stack) {
        int x = stack.relx - 25, y = stack.rely - 6;
        if (x < 0 || y < 0 || x % 18 != 0 || y % 18 != 0 || x > 36 || y > 36)
            throw fault("ProjectRed shaped layout is not a 3x3 absolute grid");
        return (y / 18) * 3 + (x / 18);
    }

    private static int slotForInput(List<PositionedStack> inputs, PositionedStack target) {
        for (int i = 0; i < inputs.size(); i++) if (inputs.get(i) == target) return i;
        throw fault("ProjectRed input identity changed while building grid");
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
