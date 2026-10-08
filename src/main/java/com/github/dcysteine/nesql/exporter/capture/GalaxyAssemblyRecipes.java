package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** GalaxySpace ordinary assembly, after proving the native repair-first branch cannot intercept it. */
final class GalaxyAssemblyRecipes implements RegistryRecipes {
    private static final String HANDLER = "galaxyspace.core.nei.AssemblyMachineRecipeHandler";
    private static final String REGISTRY = "galaxyspace.core.recipe.AssemblyRecipes";
    private static final String CONFIG = "micdoodle8.mods.galacticraft.core.util.ConfigManagerCore";
    private final TemplateRecipeHandler handler;
    private final List<?> registry;
    private final List<Entry> entries = new ArrayList<>();
    private final boolean hard;
    private boolean nativeRegistry;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    GalaxyAssemblyRecipes(TemplateRecipeHandler handler) {
        this(handler, registry(), (Boolean) field(type(CONFIG), null, "hardMode")); nativeRegistry = true;
    }
    private static List<?> registry() {
        version("GalaxySpace", "1.1.121-GTNH"); version("GalacticraftCore", "3.3.13-GTNH");
        return (List<?>) invoke(type(REGISTRY), null, "getRecipeList", new Class<?>[0]);
    }
    GalaxyAssemblyRecipes(TemplateRecipeHandler handler, List<?> registry, boolean hard) {
        if (!supports(handler) || registry == null || registry.size() > 65536) throw fault("Invalid GalaxySpace assembly registry");
        this.handler = handler; this.registry = registry; this.hard = hard;
        Work work = new Work();
        for (Object source : registry) { Jobs.checkpoint(); entries.add(new Entry(source, work)); }
    }
    public int size() { return entries.size(); }
    public void verify() {
        if (registry.size() != entries.size() || nativeRegistry && (invoke(type(REGISTRY), null, "getRecipeList", new Class<?>[0]) != registry
                || (Boolean) field(type(CONFIG), null, "hardMode") != hard)) changed();
        Work work = new Work();
        for (int i = 0; i < entries.size(); i++) {
            Jobs.checkpoint(); Entry entry = entries.get(i);
            if (registry.get(i) != entry.source || !entry.signature.equals(new Entry(entry.source, work).signature)) changed();
        }
    }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = entries.get(index); Work work = new Work();
        if (!entry.signature.equals(new Entry(entry.source, work).signature)) changed();
        if (entry.ignored || entry.impossible) return false;
        JsonArray earlier = new JsonArray();
        for (Entry prior : entries) {
            work.step(); if (prior == entry) break;
            if (!prior.ignored && !prior.impossible) earlier.add(prior.selector(row.facts, work));
        }
        row.record.add("process", object("kind", "galaxyspace-assembly", "earlier", earlier));
        if (entry.shaped) row.record.add("grid", entry.grid());
        List<PositionedStack> display = new ArrayList<>(); int slot = 0;
        for (int cell = 0; cell < entry.raw.length; cell++) {
            List<ItemStack> group = entry.raw[cell]; if (group == null) continue;
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (ItemStack item : group) {
                work.step(); boolean wildcard = item.getItemDamage() == 32767;
                for (ItemStack sample : samples(item)) {
                    work.step(); choices.add(new RecipeRow.Ingredient(sample, 1, false, rule(wildcard)));
                    if (choices.size() > 65536) throw fault("Assembly display alternatives exceed their budget");
                }
            }
            // NEIGalaxySpaceConfig indexes by three even when the semantic shaped recipe is narrower.
            PositionedStack position = new PositionedStack(choices.stream().map(choice -> choice.item.copy()).toArray(ItemStack[]::new), 21 + cell % 3 * 18, 26 + cell / 3 * 18, false);
            row.itemInput(position, slot++, choices, false); display.add(position);
        }
        PositionedStack result = new PositionedStack(entry.output.copy(), 140, 44, false);
        row.itemOutput(result, 0, entry.output.copy(), 10000);
        row.property("galaxyspace:execution", "Native execution conditions", "Initially empty output slot, stable valid crafting-grid inputs and stable supply tier other than -1, sufficient energy, reset progress counter; ordinary assembly after proving repair cannot intercept");
        row.property("galaxyspace:duration", "Native completion threshold", "Each running tick increments progress, then compares against Java int 400 / (1 + poweredByTierGC); a zero or negative threshold completes on the first tick; tier -1 causes division by zero");
        row.property("galaxyspace:maxExtract", "Configured maximum energy extraction (gJ/t)", hard ? 90 : 75);
        TemplateRecipeHandler.CachedRecipe cache;
        try {
            java.lang.reflect.Constructor<?> constructor = type(HANDLER + "$AssemblyMachineRecipe").getDeclaredConstructor(handler.getClass(), List.class, PositionedStack.class);
            constructor.setAccessible(true); cache = (TemplateRecipeHandler.CachedRecipe) constructor.newInstance(handler, display, result);
        } catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause(); if (cause instanceof Error) throw (Error) cause; if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw fault("Native assembly view constructor failed: " + cause);
        } catch (ReflectiveOperationException error) { throw fault("Native assembly view constructor changed: " + error); }
        handler.arecipes.clear(); handler.arecipes.add(cache);
        return true;
    }
    static String texture() { return "galaxyspace:textures/gui/assembly_machine.png"; }
    static void scene(TemplateRecipeHandler handler, Runnable draw) {
        try {
            java.lang.reflect.Field clock = handler.getClass().getDeclaredField("ticksPassed"); clock.setAccessible(true);
            int previous = clock.getInt(null);
            try { clock.setInt(null, 0); draw.run(); }
            finally { clock.setInt(null, previous); }
        } catch (ReflectiveOperationException error) { throw new Jobs.Fault("native_cleanup", "Could not preserve GalaxySpace assembly animation clock: " + error); }
    }
    static void draw(TemplateRecipeHandler handler, int index) {
        scene(handler, () -> { handler.drawBackground(index); handler.drawForeground(index); });
    }
    static JsonArray frames(boolean flame) {
        JsonArray frames = new JsonArray();
        for (int tick = 0; tick < 70; tick++) {
            JsonArray areas = new JsonArray();
            if (flame && tick > 26) areas.add(array("0", "0", "1", "1"));
            if (!flame && tick > 0) areas.add(array("0", "0", Float.toString(Math.min(tick, 53) / 53f), "1"));
            JsonObject previous = frames.size() == 0 ? null : frames.get(frames.size() - 1).getAsJsonObject();
            if (previous != null && previous.getAsJsonArray("areas").equals(areas)) previous.addProperty("ticks", previous.get("ticks").getAsInt() + 1);
            else frames.add(object("ticks", 1, "areas", areas));
        }
        return frames;
    }
    static JsonArray decorations(Facts facts, String location) {
        JsonArray result = new JsonArray(); int[][] regions = {{93,39,176,0,17,13},{79,46,176,13,53,17}};
        for (int index = 0; index < regions.length; index++) {
            int[] region = regions[index];
            JsonObject element = object("kind", "clip", "asset", null, "x", region[0], "y", region[1], "width", region[4], "height", region[5],
                    "z", index, "track", facts.track(frames(index == 0)));
            facts.picture(new Facts.Picture(element, "asset", location + "/progress/assembly/" + index, region[4], region[5], () -> {
                codechicken.lib.gui.GuiDraw.changeTexture(texture()); org.lwjgl.opengl.GL11.glColor4f(1, 1, 1, 1);
                codechicken.lib.gui.GuiDraw.drawTexturedModalRect(0, 0, region[2], region[3], region[4], region[5]);
            }));
            result.add(element);
        }
        return result;
    }
    private static final class Work {
        int remaining = 1048576;
        void step() { if (--remaining < 0) throw fault("Assembly proof/candidate work exceeds its budget"); if ((remaining & 1023) == 0) Jobs.checkpoint(); }
    }
    private static final class Entry {
        final Object source; final boolean shaped, ignored, impossible; final int width, height;
        final List<ItemStack>[] raw; final ItemStack output; final String signature;
        @SuppressWarnings("unchecked") Entry(Object source, Work work) {
            this.source = source; work.step();
            if (!(source instanceof IRecipe)) throw fault("Invalid assembly recipe object");
            Class<?> kind = source.getClass();
            shaped = source instanceof ShapedRecipes;
            ignored = !shaped && !(source instanceof ShapelessOreRecipe);
            if (ignored) {
                width = height = 0; raw = (List<ItemStack>[]) new List<?>[0]; output = null; impossible = true;
                signature = CanonicalJson.digest(object("ignoredNativeType", kind.getName())); return;
            }
            if (kind != ShapedRecipes.class && kind != ShapelessOreRecipe.class) throw fault("Unadapted executable assembly recipe override: " + kind.getName());
            width = shaped ? ((ShapedRecipes) source).recipeWidth : 0; height = shaped ? ((ShapedRecipes) source).recipeHeight : 0;
            Object[] cells = shaped ? ((ShapedRecipes) source).recipeItems.clone() : ((ShapelessOreRecipe) source).getInput().toArray();
            if (cells.length == 0 || cells.length > 9 || shaped && (width < 1 || width > 3 || height < 1 || height > 3 || width * height != cells.length))
                throw fault("Invalid assembly ingredient grid");
            raw = (List<ItemStack>[]) new List<?>[cells.length];
            JsonArray fingerprint = new JsonArray(); int count = 0, occupied = 0; boolean empty = false;
            for (int i = 0; i < cells.length; i++) {
                if (cells[i] == null) { if (!shaped) throw fault("Null shapeless assembly ingredient"); fingerprint.add(value(null)); continue; }
                occupied++;
                List<?> candidates = !shaped && cells[i].getClass() == ArrayList.class ? (List<?>) cells[i] : Collections.singletonList(cells[i]);
                raw[i] = new ArrayList<>(); JsonArray values = new JsonArray(); empty |= candidates.isEmpty();
                for (Object candidate : candidates) {
                    work.step();
                    if (++count > 65536 || !(candidate instanceof ItemStack) || ((ItemStack) candidate).getItem() == null) throw fault("Invalid assembly candidate");
                    ItemStack item = (ItemStack) candidate;
                    if (ItemCallbacks.method(item.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                        throw fault("Assembly input overrides native metadata matching");
                    values.add(stackValue(item)); raw[i].add(item.copy());
                }
                fingerprint.add(values);
            }
            if (occupied == 0) throw fault("Empty-input assembly needs a dedicated free-production contract");
            impossible = empty;
            JsonArray repair = new JsonArray();
            if (occupied == 2 && !impossible) {
                List<List<ItemStack>> groups = new ArrayList<>(); for (List<ItemStack> group : raw) if (group != null) groups.add(group);
                Set<Item> checked = Collections.newSetFromMap(new IdentityHashMap<Item, Boolean>());
                for (ItemStack a : groups.get(0)) for (ItemStack b : groups.get(1)) {
                    work.step(); Item item = a.getItem(); if (item != b.getItem() || !checked.add(item)) continue;
                    if (ItemCallbacks.method(item, "isRepairable", "isRepairable").getDeclaringClass() != Item.class
                            || ItemCallbacks.method(item, "isDamageable", "func_77645_m").getDeclaringClass() != Item.class)
                        throw fault("Assembly repair-first eligibility has an unadapted item callback");
                    boolean eligible = item.isRepairable(); repair.add(object("item", Item.itemRegistry.getNameForObject(item), "eligible", eligible));
                    if (eligible) throw fault("Assembly two-item inputs overlap native repair-first output transformation");
                }
            }
            ItemStack result = ((IRecipe) source).getRecipeOutput();
            if (result == null || result.getItem() == null || result.stackSize <= 0) throw fault("Assembly lacks a positive fixed result");
            // findMatchingRecipe uses getRecipeOutput().copy(), never getCraftingResult(): the copy-NBT flag is inert here.
            output = result.copy();
            signature = CanonicalJson.digest(object("class", kind.getName(), "width", width, "height", height, "inputs", fingerprint,
                    "copyNbtFlag", shaped && (Boolean) field(source, "field_92101_f"), "repair", repair, "output", stackValue(output)));
        }
        JsonObject grid() {
            JsonArray cells = new JsonArray(); int slot = 0; for (List<ItemStack> group : raw) cells.add(value(group == null ? null : slot++));
            return object("width", width, "height", height, "cells", cells, "mirror", true);
        }
        JsonObject selector(Facts facts, Work work) {
            JsonArray inputs = new JsonArray();
            for (List<ItemStack> group : raw) if (group != null) {
                JsonArray choices = new JsonArray(); Set<String> seen = new HashSet<>();
                for (ItemStack item : group) {
                    work.step(); JsonObject choice = object("id", facts.item(samples(item)[0]), "rule", rule(item.getItemDamage() == 32767));
                    if (seen.add(CanonicalJson.digest(choice))) choices.add(choice);
                }
                inputs.add(choices);
            }
            return object("grid", shaped ? grid() : null, "inputs", inputs);
        }
    }
    private static JsonObject rule(boolean wildcard) { return object("kind", "wildcard", "meta", wildcard, "nbt", true); }
    private static ItemStack[] samples(ItemStack source) {
        ItemStack owned = source.copy(); owned.stackSize = 1; owned.setTagCompound(null);
        ItemStack[] samples = owned.getItemDamage() == 32767 ? new PositionedStack(owned, 0, 0, true).items : new ItemStack[]{owned};
        if (samples == null || samples.length == 0 || samples.length > 65536) throw fault("Assembly wildcard lacks bounded native examples");
        ItemStack[] result = new ItemStack[samples.length];
        for (int i = 0; i < samples.length; i++) {
            ItemStack sample = samples[i];
            if (sample == null || sample.getItem() != owned.getItem() || sample.getItemDamage() == 32767) throw fault("Assembly wildcard lacks concrete native examples");
            result[i] = sample.copy(); result[i].stackSize = 1; result[i].setTagCompound(null);
        }
        return result;
    }
    private static JsonObject stackValue(ItemStack item) { return object("item", Item.itemRegistry.getNameForObject(item.getItem()), "meta", Items.feather.getDamage(item), "amount", item.stackSize, "nbt", TypedNbt.encode(item.getTagCompound())); }
    private static void changed() { throw new Jobs.Fault("recipe_changed", "GalaxySpace assembly registry, repair eligibility or energy mode changed during capture"); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
