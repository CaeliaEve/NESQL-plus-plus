package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Galacticraft's five fixed inputs, including AmunRa's registrations in the same native machine. */
final class CircuitRecipes implements RegistryRecipes {
    private static final String GC = "micdoodle8.mods.galacticraft.";
    private static final String AR = "de.katzenpapst.amunra.";
    private static final String HANDLER = GC + "core.nei.CircuitFabricatorRecipeHandler";
    private final TemplateRecipeHandler handler;
    private final List<Map.Entry<ItemStack[], ItemStack>> recipes;
    private final boolean quick, hard;

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(HANDLER) || name.equals(AR + "nei.recipehandler.ARCircuitFab");
    }

    CircuitRecipes(TemplateRecipeHandler handler) {
        version("GalacticraftCore", "3.3.13-GTNH");
        if (!supports(handler)) throw fault("Unadapted circuit fabricator handler");
        this.handler = handler;
        List<?> sources = null;
        if (handler.getClass().getName().startsWith(AR)) {
            version("GalacticraftAmunRa", "0.8.2");
            sources = (List<?>) invoke(type(AR + "crafting.RecipeHelper"), null, "getCircuitFabricatorRecipes", new Class<?>[0]);
        }
        recipes = snapshot((Map<?, ?>) field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "recipes"), sources);
        quick = (Boolean) field(type(GC + "core.util.ConfigManagerCore"), null, "quickMode");
        hard = (Boolean) field(type(GC + "core.util.ConfigManagerCore"), null, "hardMode");
    }

    static List<Map.Entry<ItemStack[], ItemStack>> snapshot(Map<?, ?> registry, List<?> sources) {
        if (registry.size() > 262144 || sources != null && sources.size() > 262144) throw fault("Circuit registry exceeds its budget");
        if (sources != null) for (Object source : sources) {
            if (source == null || !source.getClass().getName().equals(AR + "crafting.CircuitFabricatorRecipe"))
                throw fault("Unadapted AmunRa circuit registration");
        }
        Set<List<String>> seen = new HashSet<>();
        IdentityHashMap<net.minecraft.item.Item, Integer> items = new IdentityHashMap<>();
        List<Map.Entry<ItemStack[], ItemStack>> result = new ArrayList<>();
        for (Map.Entry<?, ?> entry : registry.entrySet()) {
            Jobs.checkpoint();
            if (!(entry.getKey() instanceof ItemStack[]) || ((ItemStack[]) entry.getKey()).length != 5)
                throw fault("Circuit registry requires five fixed slots");
            ItemStack[] original = (ItemStack[]) entry.getKey();
            List<String> key = new ArrayList<>();
            ItemStack[] input = new ItemStack[5];
            for (int slot = 0; slot < 5; slot++) {
                ItemStack stack = original[slot];
                if (stack != null && stack.getItem() == null) throw fault("Circuit registry has an invalid item");
                // The native matcher ignores count and NBT; 32767 is an exact metadata value here.
                key.add(stack == null ? null : items.computeIfAbsent(stack.getItem(), item -> items.size()) + "/" + stack.getItemDamage());
                if (stack != null) { input[slot] = stack.copy(); input[slot].stackSize = 1; input[slot].setTagCompound(null); }
            }
            // Preserve native HashMap encounter precedence BEFORE selecting the AmunRa category.
            if (!seen.add(key)) continue;
            if (sources != null && !linked(sources, original, entry.getValue())) continue;
            if (!(entry.getValue() instanceof ItemStack)) throw fault("Circuit registry has no fixed result");
            result.add(new AbstractMap.SimpleImmutableEntry<>(input, ((ItemStack) entry.getValue()).copy()));
        }
        return result;
    }

    private static boolean linked(List<?> sources, ItemStack[] input, Object output) {
        for (Object source : sources) {
            Jobs.checkpoint();
            // RecipeHelper passes these very objects to addRecipe; no NEI list or candidate cross-product is executed.
            if (field(source, "output") != output) continue;
            String[] names = {"crystal", "silicon1", "silicon2", "redstone", "optional"};
            boolean matches = true;
            for (int slot = 0; slot < 5; slot++) {
                ItemStack[] options = (ItemStack[]) field(source, names[slot]);
                boolean found = (options == null || options.length == 0) && input[slot] == null;
                if (options != null) for (ItemStack option : options) if (option == input[slot]) { found = true; break; }
                if (!found) { matches = false; break; }
            }
            if (matches) return true;
        }
        return false;
    }

    public int size() { return recipes.size(); }
    public boolean capture(int index, RecipeRow row) {
        return capture(handler, recipes.get(index), quick, hard, row);
    }

    static boolean capture(TemplateRecipeHandler handler, Map.Entry<ItemStack[], ItemStack> recipe, boolean quick, boolean hard, RecipeRow row) {
        ItemStack[] input = recipe.getKey();
        List<?> admitted = (List<?>) field(type(GC + "api.recipe.CircuitFabricatorRecipes"), null, "slotValidItems");
        if (input.length != 5 || admitted.size() != 5) throw fault("Circuit inventory registration changed");
        int[][] positions = {{10, 22}, {69, 51}, {69, 69}, {117, 51}, {140, 25}};
        ArrayList<PositionedStack> display = new ArrayList<>();
        List<Integer> empty = new ArrayList<>();
        for (int slot = 0; slot < 5; slot++) {
            if (input[slot] == null) { empty.add(slot + 1); continue; }
            boolean valid = false;
            for (Object option : (List<?>) admitted.get(slot)) {
                if (option instanceof ItemStack && input[slot].isItemEqual((ItemStack) option)) { valid = true; break; }
            }
            if (!valid) return false; // Neither manual SlotSpecific nor sided inventory accepts this input.
            ItemStack stack = input[slot].copy(); stack.stackSize = 1; stack.setTagCompound(null);
            PositionedStack position = new PositionedStack(stack.copy(), positions[slot][0], positions[slot][1], false);
            row.itemInput(position, slot + 1, Collections.singletonList(new RecipeRow.Ingredient(stack, 1, false,
                    object("kind", "wildcard", "meta", false, "nbt", true))), false);
            display.add(position);
        }
        if (display.isEmpty()) throw fault("Circuit recipe has no input");
        ItemStack output = recipe.getValue().copy();
        if (quick && output.getItem() == field(type(GC + "core.items.GCItems"), null, "basicItem")) {
            if (output.getItemDamage() == 13) output.stackSize = 5;
            else if (output.getItemDamage() == 14) output.stackSize = 2;
        }
        PositionedStack result = new PositionedStack(output.copy(), 147, 91, false);
        row.itemOutput(result, 0, output, 10000);
        row.record.addProperty("duration", "300");
        row.property("galacticraft:energyPerTick", "Energy per tick (gJ/t)", hard ? 40 : 20);
        if (!empty.isEmpty()) row.property("galacticraft:emptySlots", "Required empty machine slots", empty);
        row.property("galacticraft:layout", "Input slot numbering", "Native circuit fabricator inventory indices; fixed positions");
        // compressItems decrements each occupied slot by one; it does not return containers.
        handler.arecipes.clear();
        handler.arecipes.add(handler.new CachedRecipe() {
            @Override public List<PositionedStack> getIngredients() { return display; }
            @Override public PositionedStack getResult() { return result; }
        });
        return true;
    }

    static void scene(TemplateRecipeHandler handler, Runnable draw) {
        try {
            java.lang.reflect.Field clock = type(HANDLER).getDeclaredField("ticksPassed"); clock.setAccessible(true);
            int previous = clock.getInt(handler);
            try { clock.setInt(handler, 0); draw.run(); }
            finally { clock.setInt(handler, previous); }
        } catch (ReflectiveOperationException error) { throw fault("Circuit UI clock is unavailable: " + error); }
    }

    static JsonArray progress(int phase) {
        if (phase < 0 || phase > 2) throw fault("Invalid circuit animation phase");
        JsonArray frames = new JsonArray();
        for (int tick = 0; tick < 70; tick++) {
            int width = Math.min(tick, 51);
            JsonArray areas = new JsonArray();
            if (width > 0 && width / 3 % 3 == phase) areas.add(array("0", "0", Float.toString(width / 51f), "1"));
            JsonObject last = frames.size() == 0 ? null : frames.get(frames.size() - 1).getAsJsonObject();
            if (last != null && last.get("areas").equals(areas)) last.addProperty("ticks", last.get("ticks").getAsInt() + 1);
            else frames.add(object("ticks", 1, "areas", areas));
        }
        return frames;
    }

    static JsonArray decorations(Facts facts, TemplateRecipeHandler handler, String location) {
        JsonArray result = new JsonArray();
        for (int phase = 0; phase < 3; phase++) {
            final int y = 17 + phase * 10;
            JsonObject element = object("kind", "clip", "asset", null, "x", 83, "y", 25, "width", 51, "height", 10,
                    "z", 0, "track", facts.track(progress(phase)));
            facts.picture(new Facts.Picture(element, "asset", location + "/progress/" + phase, 51, 10, () -> {
                codechicken.lib.gui.GuiDraw.changeTexture(handler.getGuiTexture()); org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
                codechicken.lib.gui.GuiDraw.drawTexturedModalRect(0, 0, 176, y, 51, 10);
            }));
            result.add(element);
        }
        return result;
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
