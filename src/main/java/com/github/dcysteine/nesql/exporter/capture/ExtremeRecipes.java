package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Avaritia 1.77 registry and conditional NBT semantics, with the native 9x9 layout. */
final class ExtremeRecipes implements RegistryRecipes {
    private static final String PREFIX = "fox.spiteful.avaritia.";
    private final TemplateRecipeHandler handler;
    private final List<IRecipe> recipes = new ArrayList<>();
    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(PREFIX + "compat.nei.ExtremeShapedRecipeHandler") || name.equals(PREFIX + "compat.nei.ExtremeShapelessRecipeHandler");
    }
    ExtremeRecipes(TemplateRecipeHandler handler) {
        version("Avaritia", "1.77");
        this.handler = handler;
        Class<?> manager = type(PREFIX + "crafting.ExtremeCraftingManager");
        Object singleton = invoke(manager, null, "getInstance", new Class<?>[0]);
        boolean shaped = handler.getClass().getName().endsWith("ExtremeShapedRecipeHandler");
        Class<?> first = type(PREFIX + "crafting.Extreme" + (shaped ? "Shaped" : "Shapeless") + "Recipe");
        Class<?> second = shaped ? type(PREFIX + "crafting.ExtremeShapedOreRecipe") : ShapelessOreRecipe.class;
        for (Object recipe : (List<?>) invoke(manager, singleton, "getRecipeList", new Class<?>[0])) {
            Jobs.checkpoint();
            // Enumerate subclasses too so an overridden recipe fails explicitly at capture, rather than disappearing.
            if (first.isInstance(recipe) || second.isInstance(recipe)) recipes.add((IRecipe) recipe);
        }
    }
    public int size() { return recipes.size(); }
    public boolean capture(int index, RecipeRow row) { return capture(recipes.get(index), handler, row); }

    @SuppressWarnings("unchecked")
    static boolean capture(IRecipe recipe, TemplateRecipeHandler handler, RecipeRow row) {
        String name = recipe.getClass().getName();
        boolean shaped = name.equals(PREFIX + "crafting.ExtremeShapedRecipe") || name.equals(PREFIX + "crafting.ExtremeShapedOreRecipe");
        boolean forge = recipe.getClass() == ShapelessOreRecipe.class;
        if (!shaped && !forge && !name.equals(PREFIX + "crafting.ExtremeShapelessRecipe")) throw fault("Unadapted extreme recipe override: " + name);
        boolean ore = name.endsWith("OreRecipe");
        int width = shaped ? (Integer) field(recipe, ore ? "width" : "recipeWidth") : 9;
        int height = shaped ? (Integer) field(recipe, ore ? "height" : "recipeHeight") : 9;
        Object supplied = forge ? ((ShapelessOreRecipe) recipe).getInput() : ore
                ? invoke(recipe.getClass(), recipe, "getInput", new Class<?>[0]) : field(recipe, "recipeItems");
        Object[] raw = supplied instanceof List<?> ? ((List<?>) supplied).toArray() : ((Object[]) supplied).clone();
        if (width < 1 || width > 9 || height < 1 || height > 9 || raw.length == 0 || raw.length > 81
                || shaped && raw.length != width * height) throw fault("Invalid extreme crafting grid");
        ItemStack output = recipe.getRecipeOutput();
        if (output == null || output.getItem() == null || output.stackSize <= 0) throw fault("Extreme recipe has no fixed positive output");
        output = output.copy();
        JsonArray cells = new JsonArray();
        List<List<RecipeRow.Ingredient>> inputs = new ArrayList<>();
        Object[] projection = new Object[raw.length];
        int candidateCount = 0;
        for (int cell = 0; cell < raw.length; cell++) {
            Jobs.checkpoint();
            Object value = raw[cell];
            if (value == null) {
                if (!shaped) throw fault("Null shapeless ingredient");
                cells.add(value(null)); continue;
            }
            boolean group = value instanceof ArrayList<?>;
            List<?> choices = group ? (List<?>) value : Collections.singletonList(value);
            if (group && !ore) throw fault("Unexpected list in an item-only extreme recipe");
            if (choices.isEmpty()) return false; // Native NEI excludes an unsatisfiable ore predicate.
            List<RecipeRow.Ingredient> ingredients = new ArrayList<>();
            for (Object choice : choices) {
                if (!(choice instanceof ItemStack) || ((ItemStack) choice).getItem() == null) throw fault("Invalid extreme ingredient");
                ItemStack stack = ((ItemStack) choice).copy(); stack.stackSize = 1;
                boolean wildcard = stack.getItemDamage() == OreDictionary.WILDCARD_VALUE;
                // Forge shapeless and shaped ore-list branches ignore NBT. Direct Avaritia stacks only require it when present.
                boolean ignoreNbt = forge || group || !stack.hasTagCompound();
                ItemStack[] expanded = wildcard ? new PositionedStack(stack, 0, 0, true).items : new ItemStack[] {stack};
                candidateCount += expanded.length;
                if (expanded.length == 0 || candidateCount > 65536) throw fault("Extreme ingredient expansion exceeds its budget");
                for (ItemStack variant : expanded) {
                    if (variant.getItem() != stack.getItem()) throw new Jobs.Fault("slot_changed", "Extreme wildcard expansion substituted an item");
                    // NEI permutations may contain different display NBT; the source predicate owns the required tags.
                    ItemStack semantic = variant.copy(); semantic.setTagCompound(stack.getTagCompound() == null ? null : (net.minecraft.nbt.NBTTagCompound) stack.getTagCompound().copy());
                    ingredients.add(new RecipeRow.Ingredient(semantic, 1, false, object("kind", "wildcard", "meta", wildcard, "nbt", ignoreNbt)));
                }
            }
            cells.add(value(inputs.size())); inputs.add(ingredients);
            projection[cell] = ingredients.stream().map(ingredient -> ingredient.item.copy()).toArray(ItemStack[]::new);
        }
        if (shaped) row.record.add("grid", object("width", width, "height", height, "cells", cells,
                "mirror", !ore || (Boolean) field(recipe, "mirrored")));
        else row.property("minecraft:shapeless", "Shapeless", true);
        TemplateRecipeHandler.CachedRecipe cached;
        try {
            Class<?> cache = type(handler.getClass().getName() + (shaped ? "$CachedExtremeRecipe" : "$CachedExtremeShapelessRecipe"));
            cached = (TemplateRecipeHandler.CachedRecipe) (shaped
                    ? cache.getConstructor(handler.getClass(), int.class, int.class, Object[].class, ItemStack.class).newInstance(handler, width, height, projection, output.copy())
                    : cache.getConstructor(handler.getClass(), Object[].class, ItemStack.class).newInstance(handler, projection, output.copy()));
        } catch (ReflectiveOperationException error) { Jobs.Fault failure = fault("Cannot construct native extreme layout"); failure.initCause(error); throw failure; }
        List<PositionedStack> positions = (List<PositionedStack>) field(cached, "ingredients");
        if (positions.size() != inputs.size()) throw new Jobs.Fault("slot_missing", "Extreme layout omitted ingredients");
        for (int cell = 0; cell < raw.length; cell++) {
            if (raw[cell] == null) continue;
            int x = 3 + cell % width * 18, y = 3 + cell / width * 18;
            PositionedStack display = null;
            for (PositionedStack position : positions) if (position.relx == x && position.rely == y) {
                if (display != null) throw new Jobs.Fault("slot_conflict", "Extreme layout repeats a grid cell");
                display = position;
            }
            if (display == null) throw new Jobs.Fault("slot_missing", "Extreme layout has no cell " + cell);
            int slot = cells.get(cell).getAsInt();
            row.itemInput(display, slot, inputs.get(slot), true);
        }
        row.itemOutput(cached.getResult(), 0, output, 10000);
        handler.arecipes.clear(); handler.arecipes.add(cached);
        return true;
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
