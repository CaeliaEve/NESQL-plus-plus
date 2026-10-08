package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned Blood Magic shaped recipes; integer ingredients mean minimum orb level, not a displayed orb. */
final class BloodOrbShapedRecipes implements RegistryRecipes {
    private static final String ROOT = "WayofTime.alchemicalWizardry.";
    private static final String HANDLER = ROOT + "client.nei.NEIBloodOrbShapedHandler";
    private static final String RECIPE = ROOT + "api.items.ShapedBloodOrbRecipe";
    private static final String BATTERY = ROOT + "common.items.EnergyBattery";
    private final TemplateRecipeHandler handler;
    private final List<IRecipe> recipes;
    private final List<Item> orbs;
    private final String signature;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    BloodOrbShapedRecipes(TemplateRecipeHandler handler) {
        version("AWWayofTime", "1.7.52");
        if (!supports(handler)) throw fault("Unadapted Blood Orb handler");
        this.handler = handler; recipes = registry(); orbs = orbs(); signature = signature(recipes, orbs);
    }
    public int size() { return recipes.size(); }
    public boolean capture(int index, RecipeRow row) { return capture(recipes.get(index), handler, orbs, row); }
    public void verify() {
        if (!signature.equals(signature(registry(), orbs()))) throw new Jobs.Fault("environment_changed", "Blood Orb crafting registry or orb levels changed");
    }
    private static List<IRecipe> registry() {
        List<IRecipe> result = new ArrayList<>(); Class<?> nativeType = type(RECIPE);
        for (Object recipe : CraftingManager.getInstance().getRecipeList()) if (nativeType.isInstance(recipe)) {
            Jobs.checkpoint();
            if (!recipe.getClass().getName().equals(RECIPE)) throw fault("Unadapted Blood Orb recipe override: " + recipe.getClass().getName());
            result.add((IRecipe)recipe);
            if (result.size() > 262144) throw fault("Blood Orb recipe registry exceeds its budget");
        }
        return result;
    }
    static List<Item> orbs() {
        List<Item> result = new ArrayList<>(); Class<?> api = type(ROOT + "api.items.interfaces.IBloodOrb");
        for (Object item : Item.itemRegistry) if (api.isInstance(item)) {
            Jobs.checkpoint(); orbLevel((Item)item); result.add((Item)item);
            if (result.size() > 4096) throw fault("Blood Orb registry exceeds its budget");
        }
        result.sort(Comparator.comparing(item -> Item.itemRegistry.getNameForObject(item)));
        return result;
    }
    static boolean capture(IRecipe source, TemplateRecipeHandler handler, List<Item> orbs, RecipeRow row) {
        if (!supports(handler) || source == null || !source.getClass().getName().equals(RECIPE)) throw fault("Unadapted Blood Orb shaped recipe");
        int width = (Integer)field(source, "width"), height = (Integer)field(source, "height");
        Object[] raw = (Object[])field(source, "input");
        if (width < 1 || width > 3 || height < 1 || height > 3 || raw == null || raw.length != width * height)
            throw fault("Invalid Blood Orb crafting grid");
        ItemStack output = (ItemStack)field(source, "output");
        if (output == null || output.getItem() == null || output.stackSize <= 0) throw fault("Blood Orb recipe has no positive fixed output");
        output = output.copy();
        JsonArray cells = array(); Object[] projection = new Object[raw.length];
        List<List<RecipeRow.Ingredient>> inputs = new ArrayList<>();
        Map<String, ItemStack> returns = new HashMap<>(); int candidates = 0;
        for (int cell = 0; cell < raw.length; cell++) {
            Object value = raw[cell];
            if (value == null) { cells.add(value(null)); continue; }
            List<ItemStack> choices = new ArrayList<>(); boolean tier = value instanceof Integer;
            if (tier) {
                for (Item orb : orbs) if (orbLevel(orb) >= (Integer)value) choices.add(new ItemStack(orb));
                row.property("bloodmagic:orbTier" + inputs.size(), "Minimum blood orb level for input " + inputs.size(), value);
            } else if (value instanceof ItemStack) choices.add((ItemStack)value);
            else if (oreGroup(value)) {
                for (Object member : (ArrayList<?>)value) {
                    if (!(member instanceof ItemStack)) throw fault("Blood Orb ore group contains a non-item");
                    choices.add((ItemStack)member);
                }
            } else throw fault("Unknown Blood Orb ingredient predicate: " + value.getClass().getName());
            if (choices.isEmpty()) return false;
            List<RecipeRow.Ingredient> ingredients = new ArrayList<>(); ArrayList<ItemStack> displayed = new ArrayList<>();
            for (ItemStack original : choices) {
                Jobs.checkpoint();
                if (++candidates > 65536 || original == null || original.getItem() == null) throw fault("Invalid or excessive Blood Orb candidates");
                Item item = original.getItem();
                if (!tier && ItemCallbacks.method(item, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                    throw fault("Unadapted Blood Orb companion metadata callback");
                int metadata = Items.feather.getDamage(original);
                if (metadata < 0) throw fault("Blood Orb ingredient has negative metadata");
                boolean wildcard = tier || metadata == 32767;
                ItemStack example = new ItemStack(item, 1, wildcard ? 0 : metadata);
                boolean keep = type(ROOT + "api.items.interfaces.IBloodOrb").isInstance(item);
                if (keep) orbLevel(item); // Verify unchanged offered-stack return implementation, even inside an ore group.
                else {
                    ItemStack returned = ItemCallbacks.container(example, wildcard);
                    if (returned != null) returns.put(row.facts.item(example), returned);
                }
                ingredients.add(new RecipeRow.Ingredient(example, 1, keep, object("kind", "wildcard", "meta", wildcard, "nbt", true)));
                displayed.add(example.copy());
            }
            cells.add(value(inputs.size())); inputs.add(ingredients); projection[cell] = displayed;
        }
        if (inputs.isEmpty()) throw fault("Blood Orb recipe has no ingredients");
        TemplateRecipeHandler.CachedRecipe cached = (TemplateRecipeHandler.CachedRecipe)TinkerRecipes.construct(
                type(HANDLER + "$CachedBloodOrbRecipe"), new Class<?>[]{handler.getClass(), int.class, int.class, Object[].class, ItemStack.class},
                handler, width, height, projection, output.copy());
        List<PositionedStack> positions = cached.getIngredients();
        if (positions.size() != inputs.size()) throw new Jobs.Fault("slot_changed", "Blood Orb native layout changed ingredient count");
        for (int cell = 0; cell < raw.length; cell++) if (raw[cell] != null) {
            int x = 25 + cell % width * 18, y = 6 + cell / width * 18;
            PositionedStack position = null;
            for (PositionedStack candidate : positions) if (candidate.relx == x && candidate.rely == y) {
                if (position != null) throw new Jobs.Fault("slot_changed", "Blood Orb native layout repeats a cell");
                position = candidate;
            }
            if (position == null) throw new Jobs.Fault("slot_changed", "Blood Orb native layout omitted a cell");
            int slot = cells.get(cell).getAsInt(); row.itemInput(position, slot, inputs.get(slot), false);
            for (JsonElement candidate : row.inputs.get(row.inputs.size() - 1).getAsJsonObject().getAsJsonArray("choices")) {
                JsonObject choice = candidate.getAsJsonObject(); ItemStack returned = returns.get(choice.get("id").getAsString());
                if (returned != null) choice.getAsJsonArray("returns").add(object("kind", "item", "id", row.facts.item(returned), "amount", Integer.toString(returned.stackSize)));
            }
        }
        row.record.add("grid", object("width", width, "height", height, "cells", cells, "mirror", field(source, "mirrored")));
        row.itemOutput(cached.getResult(), 0, output, 10000);
        handler.arecipes.clear(); handler.arecipes.add(cached); return true;
    }
    static int orbLevel(Item item) {
        if (item == null || !type(ROOT + "api.items.interfaces.IBloodOrb").isInstance(item)) throw fault("Blood Orb candidate is not an orb");
        try {
            if (!item.getClass().getMethod("getOrbLevel").getDeclaringClass().getName().equals(BATTERY)
                    || !ItemCallbacks.method(item, "hasContainerItem", "hasContainerItem", ItemStack.class).getDeclaringClass().getName().equals(BATTERY)
                    || !ItemCallbacks.method(item, "getContainerItem", "getContainerItem", ItemStack.class).getDeclaringClass().getName().equals(BATTERY))
                throw fault("Unadapted Blood Orb tier or container override: " + item.getClass().getName());
        } catch (NoSuchMethodException error) { throw fault("Missing Blood Orb tier getter"); }
        // Pinned EnergyBattery methods return this field and the exact offered stack.
        return (Integer)field(item, "orbLevel");
    }
    static boolean oreGroup(Object value) {
        if (value == null) return false;
        if (value.getClass() == ArrayList.class) return true;
        // Forge 1.7.10 getOres exposes this native read-only ArrayList wrapper.
        // Its reads/equals delegate to a backing ArrayList; no custom callbacks.
        if (!value.getClass().getName().equals("net.minecraftforge.oredict.OreDictionary$UnmodifiableArrayList")) return false;
        Object backing = field(value, "list");
        if (backing == null || backing.getClass() != ArrayList.class) throw fault("Unadapted Blood Orb ore-list backing implementation");
        return true;
    }
    private static String signature(List<IRecipe> recipes, List<Item> orbs) {
        JsonArray records = array();
        for (IRecipe recipe : recipes) {
            JsonArray inputs = array();
            for (Object input : (Object[])field(recipe, "input")) {
                if (input == null || input instanceof Integer) inputs.add(value(input));
                else if (input instanceof ItemStack) inputs.add(stack((ItemStack)input));
                else if (oreGroup(input)) { JsonArray choices = array(); for (Object choice : (ArrayList<?>)input) choices.add(stack((ItemStack)choice)); inputs.add(choices); }
                else throw fault("Unknown Blood Orb input for registry guard");
            }
            records.add(object("width", field(recipe,"width"), "height", field(recipe,"height"), "mirror", field(recipe,"mirrored"), "inputs", inputs, "output", stack((ItemStack)field(recipe,"output"))));
        }
        for (Item orb : orbs) records.add(object("orb", Item.itemRegistry.getNameForObject(orb), "tier", orbLevel(orb)));
        return CanonicalJson.digest(records);
    }
    private static JsonObject stack(ItemStack item) {
        if (item == null || item.getItem() == null) throw fault("Invalid Blood Orb registry stack");
        return object("item", Item.itemRegistry.getNameForObject(item.getItem()), "meta", Items.feather.getDamage(item), "amount", item.stackSize, "nbt", TypedNbt.encode(item.getTagCompound()));
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
