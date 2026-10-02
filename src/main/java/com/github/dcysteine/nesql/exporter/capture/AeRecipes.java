package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;
import java.lang.reflect.Constructor;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** AE2 rv3-beta-695: registry recipes own semantics, native caches own layout only. */
final class AeRecipes {
    private static final String HANDLER = "appeng.integration.modules.NEIHelpers.NEIAE";
    private static final String RECIPE = "appeng.recipes.game.";
    private final TemplateRecipeHandler handler;
    private final List<IRecipe> recipes;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(HANDLER + "ShapedRecipeHandler") || name.equals(HANDLER + "ShapelessRecipeHandler");
    }

    AeRecipes(TemplateRecipeHandler handler) {
        version("appliedenergistics2", "rv3-beta-695-GTNH");
        this.handler = handler;
        String kind = handler.getClass().getName().equals(HANDLER + "ShapedRecipeHandler") ? "Shaped" : "Shapeless";
        recipes = enumerate(type(RECIPE + kind + "Recipe"), CraftingManager.getInstance().getRecipeList());
    }

    static List<IRecipe> enumerate(Class<?> recipeType, List<?> registry) {
        List<IRecipe> result = new ArrayList<>();
        for (Object recipe : registry) {
            Jobs.checkpoint();
            if (recipeType.isInstance(recipe) && Boolean.TRUE.equals(invoke(recipeType, recipe, "isEnabled", new Class<?>[0]))) {
                result.add((IRecipe) recipe);
            }
        }
        return result;
    }

    int size() { return recipes.size(); }
    void capture(int index, RecipeRow row) { capture(recipes.get(index), handler, row); }

    @SuppressWarnings("unchecked")
    static void capture(IRecipe source, TemplateRecipeHandler handler, RecipeRow row) {
        boolean shaped = source.getClass().getName().equals(RECIPE + "ShapedRecipe");
        if (!shaped && !source.getClass().getName().equals(RECIPE + "ShapelessRecipe")) {
            throw fault("AE2 recipe overrides native matching/output semantics: " + source.getClass().getName());
        }
        if (!Boolean.TRUE.equals(invoke(source.getClass(), source, "isEnabled", new Class<?>[0]))) {
            throw new Jobs.Fault("slot_changed", "AE2 recipe was disabled after enumeration");
        }
        int width = shaped ? (Integer) invoke(source.getClass(), source, "getWidth", new Class<?>[0]) : 3;
        int height = shaped ? (Integer) invoke(source.getClass(), source, "getHeight", new Class<?>[0]) : 3;
        Object supplied = invoke(source.getClass(), source, "getInput", new Class<?>[0]);
        Object[] raw = shaped ? ((Object[]) supplied).clone() : ((List<?>) supplied).toArray();
        if (width < 1 || width > 3 || height < 1 || height > 3 || raw.length < 1 || raw.length > 9
                || shaped && raw.length != width * height) throw fault("Invalid AE2 crafting grid");
        ItemStack output = source.getRecipeOutput();
        if (output == null || output.getItem() == null || output.stackSize <= 0) throw fault("AE2 recipe has no fixed positive output");
        output = output.copy();
        List<List<RecipeRow.Ingredient>> inputs = new ArrayList<>();
        Object[] viewInputs = new Object[raw.length];
        JsonArray cells = new JsonArray();
        for (int cell = 0; cell < raw.length; cell++) {
            Object ingredient = raw[cell];
            if (ingredient == null) { cells.add(value(null)); continue; }
            cells.add(value(inputs.size()));
            ItemStack[] stacks = stacks(ingredient);
            inputs.add(ingredients(stacks));
            viewInputs[cell] = viewIngredient(stacks);
        }
        TemplateRecipeHandler.CachedRecipe cached;
        try {
            Class<?> cacheType = type(handler.getClass().getName() + "$Cached" + (shaped ? "Shaped" : "Shapeless") + "Recipe");
            Constructor<?> constructor = cacheType.getDeclaredConstructor(handler.getClass(), source.getClass());
            constructor.setAccessible(true);
            Object projection = source.getClass().getConstructor(ItemStack.class, Object[].class)
                    .newInstance(output.copy(), shaped ? new Object[] {new String[] {" "}} : viewInputs);
            if (shaped) {
                for (String key : new String[] {"width", "height", "mirrored"}) set(projection, key, field(source, key));
                set(projection, "input", viewInputs);
            }
            cached = (TemplateRecipeHandler.CachedRecipe) constructor.newInstance(handler, projection);
        } catch (ReflectiveOperationException error) {
            Jobs.Fault failure = fault("Cannot construct pinned AE2 recipe layout"); failure.initCause(error); throw failure;
        }
        List<PositionedStack> displays = (List<PositionedStack>) field(cached, "ingredients");
        if (displays.size() != inputs.size()) throw new Jobs.Fault("slot_missing", "AE2 native view omitted a source ingredient");
        Set<PositionedStack> used = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int cell = 0; cell < raw.length; cell++) {
            if (raw[cell] == null) continue;
            int x = 25 + (cell % width) * 18, y = 6 + (cell / width) * 18;
            PositionedStack display = null;
            for (PositionedStack candidate : displays) if (candidate.relx == x && candidate.rely == y) {
                if (display != null) throw new Jobs.Fault("slot_conflict", "AE2 native view repeats a cell");
                display = candidate;
            }
            if (display == null || !used.add(display)) throw new Jobs.Fault("slot_missing", "AE2 native view has no source cell " + cell);
            int slot = cells.get(cell).getAsInt();
            row.itemInput(display, slot, inputs.get(slot), true);
        }
        if (shaped) row.record.add("grid", object("width", width, "height", height, "cells", cells, "mirror", field(source, "mirrored")));
        else row.property("minecraft:shapeless", "Shapeless", true);
        row.itemOutput(cached.getResult(), 0, output, 10000);
        handler.arecipes.clear(); handler.arecipes.add(cached);
    }

    private static ItemStack[] stacks(Object ingredient) {
        Class<?> api = type("appeng.api.recipes.IIngredient");
        if (!api.isInstance(ingredient)) throw fault("No AE2 ingredient adapter for " + ingredient.getClass().getName());
        ItemStack[] raw = (ItemStack[]) invoke(api, ingredient, "getItemStackSet", new Class<?>[0]);
        if (raw == null || raw.length == 0 || raw.length > 65536) throw fault("AE2 ingredient has no finite alternatives");
        for (ItemStack source : raw) {
            if (source == null || source.getItem() == null) throw fault("AE2 ingredient contains an empty stack");
        }
        return Arrays.stream(raw).map(ItemStack::copy).toArray(ItemStack[]::new);
    }

    /** Native cache setMaxSize mutates its offered stacks. Never hand it registry objects. */
    private static Object viewIngredient(ItemStack[] stacks) {
        Class<?> api = type("appeng.api.recipes.IIngredient");
        return java.lang.reflect.Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[] {api}, (proxy, method, args) -> {
            if (method.getName().equals("getItemStackSet")) return Arrays.stream(stacks).map(ItemStack::copy).toArray(ItemStack[]::new);
            if (method.getName().equals("getItemStack")) return stacks[0].copy();
            if (method.getName().equals("isAir")) return false;
            throw fault("Unexpected AE2 view ingredient method: " + method.getName());
        });
    }

    private static void set(Object target, String name, Object value) throws ReflectiveOperationException {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }

    private static List<RecipeRow.Ingredient> ingredients(ItemStack[] raw) {
        List<RecipeRow.Ingredient> result = new ArrayList<>();
        for (ItemStack source : raw) {
            ItemStack copy = source.copy(); copy.stackSize = 1;
            boolean wildcard = Items.feather.getDamage(copy) == OreDictionary.WILDCARD_VALUE;
            ItemStack[] variants = wildcard ? new PositionedStack(copy, 0, 0, true).items : new ItemStack[] {copy};
            if (variants == null || variants.length == 0 || variants.length + result.size() > 65536) throw fault("AE2 ingredient expansion exceeds its budget");
            for (ItemStack variant : variants) result.add(new RecipeRow.Ingredient(variant.copy(), 1, false,
                    object("kind", "wildcard", "meta", wildcard, "nbt", true)));
        }
        return result;
    }

    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
