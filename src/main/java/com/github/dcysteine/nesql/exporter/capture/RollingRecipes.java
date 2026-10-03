package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Railcraft 9.16.33: one ordered registry shared by both native NEI categories. */
final class RollingRecipes implements RegistryRecipes {
    private static final String NEI = "tonius.neiintegration.mods.railcraft.RecipeHandlerRollingMachine";
    private final TemplateRecipeHandler handler;
    private final List<Entry> all = new ArrayList<>(), visible = new ArrayList<>();
    private final boolean powered;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(NEI + "Shaped") || name.equals(NEI + "Shapeless");
    }
    RollingRecipes(TemplateRecipeHandler handler) { this(handler, registry(), power()); }
    private static List<?> registry() {
        version("Railcraft", "9.16.33"); version("neiintegration", "1.5.0");
        Object manager = field(type("mods.railcraft.api.crafting.RailcraftCraftingManager"), null, "rollingMachine");
        if (manager == null || !manager.getClass().getName().equals("mods.railcraft.common.util.crafting.RollingMachineCraftingManager"))
            throw fault("Unadapted rolling registry");
        return new ArrayList<>((List<?>) invoke(manager.getClass(), manager, "getRecipeList", new Class<?>[0]));
    }
    private static boolean power() {
        return (Boolean) invoke(type("mods.railcraft.common.core.RailcraftConfig"), null, "machinesRequirePower", new Class<?>[0]);
    }
    RollingRecipes(TemplateRecipeHandler handler, List<?> registry, boolean powered) {
        if (!supports(handler) || registry.size() > 262144) throw fault("Invalid rolling handler or registry budget");
        this.handler = handler; this.powered = powered;
        boolean shaped = handler.getClass().getName().equals(NEI + "Shaped");
        for (Object recipe : registry) {
            Jobs.checkpoint();
            Entry entry = new Entry(recipe);
            all.add(entry);
            if (entry.shaped == shaped) visible.add(entry);
        }
    }
    public int size() { return visible.size(); }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = visible.get(index);
        if (entry.impossible) return false;
        if (entry.copyNbt) throw fault("Rolling recipe copies offered ingredient NBT; fixed-result capture is not valid");
        JsonArray earlier = new JsonArray();
        int priorChoices = 0;
        for (Entry prior : all) {
            if (prior == entry) break;
            Jobs.checkpoint();
            if (!prior.impossible) {
                for (List<ItemStack> choices : prior.raw) if (choices != null) priorChoices += choices.size();
                if (priorChoices > 1048576) throw fault("Rolling selector budget exceeded");
                earlier.add(prior.selector(row.facts));
            }
        }
        row.record.add("process", object("kind", "rolling", "powered", powered, "earlier", earlier));
        if (entry.shaped) row.record.add("grid", entry.grid());
        List<List<RecipeRow.Ingredient>> ingredients = new ArrayList<>();
        Object[] projection = new Object[entry.raw.length];
        for (int cell = 0; cell < entry.raw.length; cell++) {
            List<ItemStack> templates = entry.raw[cell];
            if (templates == null) continue;
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (ItemStack template : templates) {
                boolean wildcard = template.getItemDamage() == 32767;
                ItemStack[] expanded = wildcard ? new PositionedStack(template.copy(), 0, 0, true).items : new ItemStack[] {template.copy()};
                if (expanded.length == 0 || choices.size() + expanded.length > 65536) throw fault("Invalid rolling display expansion");
                for (ItemStack sample : expanded) {
                    if (sample == null || sample.getItem() != template.getItem()) throw new Jobs.Fault("slot_changed", "Rolling display substituted native input");
                    ItemStack owned = sample.copy(); owned.stackSize = 1;
                    choices.add(new RecipeRow.Ingredient(owned, 1, false, rule(wildcard)));
                }
            }
            ingredients.add(choices);
            projection[cell] = choices.stream().map(c -> c.item.copy()).toArray(ItemStack[]::new);
        }
        Class<?> cacheType = type(handler.getClass().getName() + (entry.shaped ? "$CachedRollingMachineShapedRecipe" : "$CachedRollingMachineShapelessRecipe"));
        TemplateRecipeHandler.CachedRecipe cache = (TemplateRecipeHandler.CachedRecipe) (entry.shaped
                ? TinkerRecipes.construct(cacheType, new Class<?>[] {handler.getClass(), int.class, int.class, Object[].class, ItemStack.class},
                        handler, entry.width, entry.height, projection, entry.output.copy())
                : TinkerRecipes.construct(cacheType, new Class<?>[] {handler.getClass(), Object[].class, ItemStack.class}, handler, projection, entry.output.copy()));
        @SuppressWarnings("unchecked") List<PositionedStack> positions = (List<PositionedStack>) field(cache, "inputs");
        if (positions.size() != ingredients.size()) throw new Jobs.Fault("slot_missing", "Rolling cache omitted an ingredient");
        int slot = 0;
        int[][] order = {{0,0},{1,0},{0,1},{1,1},{0,2},{1,2},{2,0},{2,1},{2,2}};
        for (int cell = 0; cell < entry.raw.length; cell++) {
            if (entry.raw[cell] == null) continue;
            int x = 25 + (entry.shaped ? cell % entry.width : order[cell][0]) * 18;
            int y = (entry.shaped ? 8 : 6) + (entry.shaped ? cell / entry.width : order[cell][1]) * 18;
            PositionedStack display = null;
            for (PositionedStack candidate : positions) if (candidate.relx == x && candidate.rely == y) {
                if (display != null) throw new Jobs.Fault("slot_conflict", "Rolling cache repeats a cell");
                display = candidate;
            }
            if (display == null) throw new Jobs.Fault("slot_missing", "Rolling cache lacks cell " + cell);
            // The machine decrements every occupied cell, never invoking container callbacks.
            row.itemInput(display, slot, ingredients.get(slot), false); slot++;
        }
        row.itemOutput(cache.getResult(), 0, entry.output.copy(), 10000);
        handler.arecipes.clear(); handler.arecipes.add(cache);
        return true;
    }
    static int[][] progressBars() { return new int[][] {{84,36,176,0,25,12,60,0}}; }
    private static JsonObject rule(boolean wildcard) { return object("kind", "wildcard", "meta", wildcard, "nbt", true); }

    private static final class Entry {
        final boolean shaped, mirror, copyNbt, impossible;
        final int width, height;
        final List<ItemStack>[] raw;
        final ItemStack output;
        @SuppressWarnings("unchecked") Entry(Object value) {
            if (value == null) throw fault("Null entry in rolling registry");
            Class<?> c = value.getClass();
            if (c != ShapedRecipes.class && c != ShapedOreRecipe.class && c != ShapelessRecipes.class && c != ShapelessOreRecipe.class)
                throw fault("Unadapted rolling selector: " + c.getName());
            shaped = c == ShapedRecipes.class || c == ShapedOreRecipe.class;
            boolean ore = c == ShapedOreRecipe.class || c == ShapelessOreRecipe.class;
            width = c == ShapedRecipes.class ? ((ShapedRecipes) value).recipeWidth : shaped ? (Integer) field(value, "width") : 0;
            height = c == ShapedRecipes.class ? ((ShapedRecipes) value).recipeHeight : shaped ? (Integer) field(value, "height") : 0;
            mirror = shaped && (!ore || (Boolean) field(value, "mirrored"));
            copyNbt = c == ShapedRecipes.class && (Boolean) ReflectionHelper.getPrivateValue(ShapedRecipes.class, (ShapedRecipes) value, "field_92101_f");
            Object input = c == ShapedOreRecipe.class ? ((ShapedOreRecipe) value).getInput()
                    : c == ShapelessOreRecipe.class ? ((ShapelessOreRecipe) value).getInput()
                    : c == ShapedRecipes.class ? ((ShapedRecipes) value).recipeItems : ((ShapelessRecipes) value).recipeItems;
            Object[] cells = input instanceof List<?> ? ((List<?>) input).toArray() : ((Object[]) input).clone();
            if (cells.length == 0 || cells.length > 9 || shaped && (width < 1 || width > 3 || height < 1 || height > 3 || cells.length != width * height))
                throw fault("Invalid rolling grid");
            raw = (List<ItemStack>[]) new List<?>[cells.length];
            int count = 0, occupied = 0; boolean empty = false;
            for (int i = 0; i < cells.length; i++) {
                if (cells[i] == null) { if (!shaped) throw fault("Null shapeless rolling ingredient"); continue; }
                occupied++;
                List<?> choices = cells[i] instanceof ArrayList<?> && ore ? (List<?>) cells[i] : Collections.singletonList(cells[i]);
                raw[i] = new ArrayList<>(); empty |= choices.isEmpty();
                for (Object choice : choices) {
                    if (++count > 65536 || !(choice instanceof ItemStack) || ((ItemStack) choice).getItem() == null) throw fault("Invalid rolling ingredient");
                    ItemStack stack = (ItemStack) choice;
                    try {
                        if (stack.getItem().getClass().getMethod("getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                            throw fault("Rolling input uses a custom metadata getter");
                    } catch (NoSuchMethodException ex) { throw fault("Missing native metadata getter"); }
                    ItemStack owned = stack.copy(); owned.stackSize = 1; raw[i].add(owned);
                }
            }
            if (occupied == 0) throw fault("Rolling recipe has no occupied input cells");
            impossible = empty;
            ItemStack result = ((IRecipe) value).getRecipeOutput();
            if (result == null || result.getItem() == null || result.stackSize <= 0) throw fault("Rolling recipe has no positive fixed result");
            output = result.copy();
        }
        JsonObject grid() {
            JsonArray cells = new JsonArray(); int slot = 0;
            for (List<ItemStack> group : raw) cells.add(value(group == null ? null : slot++));
            return object("width", width, "height", height, "cells", cells, "mirror", mirror);
        }
        JsonObject selector(Facts facts) {
            JsonArray inputs = new JsonArray();
            for (List<ItemStack> group : raw) if (group != null) {
                JsonArray choices = new JsonArray(); Set<String> seen = new HashSet<>();
                for (ItemStack stack : group) {
                    JsonObject choice = object("id", facts.item(stack), "rule", rule(stack.getItemDamage() == 32767));
                    if (seen.add(choice.toString())) choices.add(choice);
                }
                inputs.add(choices);
            }
            return object("grid", shaped ? grid() : null, "inputs", inputs);
        }
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
