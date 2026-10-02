package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Railcraft 9.16.33 furnace registries, with native two-pass matching priority. */
final class RailRecipes implements RegistryRecipes {
    private static final String NEI = "tonius.neiintegration.mods.railcraft.RecipeHandler";
    private static final String CRAFTING = "mods.railcraft.common.util.crafting.";
    private final TemplateRecipeHandler handler;
    private final boolean coke;
    private final List<Entry> entries = new ArrayList<>();

    static boolean supports(ICraftingHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(NEI + "CokeOven") || name.equals(NEI + "BlastFurnace");
    }
    RailRecipes(TemplateRecipeHandler handler) { this(handler, registry(handler)); }
    private static List<?> registry(TemplateRecipeHandler handler) {
        version("Railcraft", "9.16.33"); version("neiintegration", "1.5.0");
        boolean coke = handler.getClass().getName().equals(NEI + "CokeOven");
        Object manager = field(type("mods.railcraft.api.crafting.RailcraftCraftingManager"), null, coke ? "cokeOven" : "blastFurnace");
        if (manager == null || !manager.getClass().getName().equals(CRAFTING + (coke ? "CokeOven" : "BlastFurnace") + "CraftingManager"))
            throw fault("Unadapted Railcraft furnace registry");
        return new ArrayList<>((List<?>) invoke(manager.getClass(), manager, "getRecipes", new Class<?>[0]));
    }
    // Also used by native conformance tests with an owned registry; no world is involved.
    RailRecipes(TemplateRecipeHandler handler, List<?> registry) {
        if (!supports(handler)) throw fault("Unadapted Railcraft furnace handler");
        this.handler = handler; coke = handler.getClass().getName().equals(NEI + "CokeOven");
        if (registry.size() > 262144) throw fault("Railcraft registry exceeds its budget");
        String expected = CRAFTING + (coke ? "CokeOvenCraftingManager$CokeOvenRecipe" : "BlastFurnaceCraftingManager$BlastFurnaceRecipe");
        List<Entry> deferred = new ArrayList<>();
        for (Object recipe : registry) {
            Jobs.checkpoint();
            if (recipe == null || !recipe.getClass().getName().equals(expected)) throw fault("Unadapted Railcraft furnace recipe implementation");
            Entry entry = new Entry(recipe, coke);
            // The manager's first pass selects matchDamage/non-wildcard templates,
            // even for items without subtypes. The second pass retains registry order.
            if (entry.damage && entry.input.getItemDamage() != 32767) entries.add(entry);
            else deferred.add(entry);
        }
        entries.addAll(deferred);
    }
    public int size() { return entries.size(); }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = entries.get(index);
        if (coke && entry.time > Integer.MAX_VALUE / 50 * 50)
            throw fault("Railcraft cook time crosses the native signed counter wraparound");
        if (coke && entry.nbt && entry.input.hasTagCompound() && entry.input.getTagCompound().hasKey("synthetic")) return false;
        List<Pattern> earlier = new ArrayList<>();
        for (int i = 0; i < index; i++) {
            Entry previous = entries.get(i);
            if (previous.input.getItem() == entry.input.getItem()) earlier.addAll(previous.patterns());
        }
        List<RecipeRow.Ingredient> choices = new ArrayList<>();
        for (Pattern base : entry.patterns()) {
            Jobs.checkpoint();
            boolean covered = false;
            for (Pattern prior : earlier) if (prior.covers(base)) { covered = true; break; }
            if (covered) continue;
            JsonArray exclude = new JsonArray(); Set<String> seen = new HashSet<>();
            for (Pattern prior : earlier) if (prior.overlaps(base)) {
                JsonObject rule = prior.rule(false);
                JsonObject filter = object("id", row.facts.item(prior.item), "rule", rule);
                if (seen.add(CanonicalJson.digest(filter))) exclude.add(filter);
                if (exclude.size() > 4096) throw fault("Railcraft input priority exceeds its exclusion budget");
            }
            JsonObject rule = base.rule(coke && !entry.nbt);
            if (exclude.size() > 0) rule = object("kind", "except", "base", rule, "exclude", exclude);
            choices.add(new RecipeRow.Ingredient(base.item, 1, false, rule));
        }
        if (choices.isEmpty()) return false;
        ItemStack[] displayed = new ItemStack[choices.size()];
        for (int i = 0; i < displayed.length; i++) displayed[i] = choices.get(i).item.copy();
        int x = coke ? 21 : 51, y = coke ? 32 : 6;
        PositionedStack input = new PositionedStack(displayed, x, y, false);
        row.itemInput(input, 0, choices, false);
        PositionedStack output = entry.output == null ? null : new PositionedStack(entry.output.copy(), coke ? 67 : 111, coke ? 32 : 24, false);
        if (output != null) row.itemOutput(output, 0, entry.output.copy(), 10000);
        if (entry.fluid != null) row.fluidOutput(null, 0, entry.fluid.copy());
        if (output == null && entry.fluid == null) throw fault("Railcraft furnace recipe has no result");
        // Native coke processing advances by 50 on each machine clock pulse.
        long duration = coke ? Math.max(1L, ((long) entry.time + 49) / 50) * 50 : Math.max(1, entry.time);
        row.record.addProperty("duration", Long.toString(duration));
        if (coke) row.property("railcraft:clockInterval", "Machine processing interval (ticks)", 50);
        else row.property("railcraft:fuel", "Fuel requirement", "Independent furnace fuel supply; native burn budget decreases by 5 per world tick, including idle time");
        row.property("railcraft:registeredTime", "Registered cook time (ticks)", entry.time);
        Object projection = coke ? TinkerRecipes.construct(type(CRAFTING + "CokeOvenCraftingManager$CokeOvenRecipe"),
                new Class<?>[] {ItemStack.class, boolean.class, boolean.class, ItemStack.class, FluidStack.class, int.class},
                entry.input.copy(), entry.damage, entry.nbt, entry.output == null ? null : entry.output.copy(), entry.fluid == null ? null : entry.fluid.copy(), entry.time)
            : TinkerRecipes.construct(type(CRAFTING + "BlastFurnaceCraftingManager$BlastFurnaceRecipe"),
                new Class<?>[] {ItemStack.class, boolean.class, boolean.class, int.class, ItemStack.class},
                entry.input.copy(), entry.damage, entry.nbt, entry.time, entry.output.copy());
        String family = coke ? "CokeOven" : "BlastFurnace";
        TemplateRecipeHandler.CachedRecipe cached = (TemplateRecipeHandler.CachedRecipe) TinkerRecipes.construct(type(NEI + family + "$Cached" + family + "Recipe"),
                new Class<?>[] {handler.getClass(), type("mods.railcraft.api.crafting.I" + family + "Recipe")}, handler, projection);
        handler.arecipes.clear(); handler.arecipes.add(cached);
        return true;
    }

    private static final class Entry {
        final ItemStack input, output; final FluidStack fluid; final boolean damage, nbt; final int time;
        Entry(Object recipe, boolean coke) {
            ItemStack raw = (ItemStack) field(recipe, "input");
            if (raw == null || raw.getItem() == null || raw.getItemDamage() < 0) throw fault("Invalid Railcraft input template");
            // InvTools calls the item's damage getter. A custom getter may turn
            // arbitrary metadata into a wildcard; it needs its own semantics.
            try {
                if (raw.getItem().getClass().getMethod("getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                    throw fault("Railcraft input uses a custom metadata predicate: " + raw.getItem().getClass().getName());
            } catch (NoSuchMethodException error) { throw fault("Missing native Item damage getter"); }
            input = raw.copy(); input.stackSize = 1;
            damage = (Boolean) field(recipe, "matchDamage"); nbt = (Boolean) field(recipe, "matchNBT");
            // This is an owned matching example. Ignored tags do not constrain
            // the recipe, and synthetic-tag templates need a usable coke example.
            if (!nbt) input.setTagCompound(null);
            time = (Integer) field(recipe, "cookTime");
            ItemStack result = (ItemStack) field(recipe, "output"); output = result == null ? null : result.copy();
            FluidStack liquid = coke ? (FluidStack) field(recipe, "fluidOutput") : null; fluid = liquid == null ? null : liquid.copy();
        }
        List<Pattern> patterns() {
            boolean ignoreMeta = !damage || !input.getHasSubtypes() || input.getItemDamage() == 32767;
            List<Pattern> patterns = new ArrayList<>(); patterns.add(new Pattern(input, ignoreMeta, !nbt));
            if (!ignoreMeta) {
                ItemStack wildcard = new ItemStack(input.getItem(), 1, 32767);
                if (input.hasTagCompound()) wildcard.setTagCompound((net.minecraft.nbt.NBTTagCompound) input.getTagCompound().copy());
                patterns.add(new Pattern(wildcard, false, !nbt));
            }
            return patterns;
        }
    }
    private static final class Pattern {
        final ItemStack item; final boolean meta, nbt;
        Pattern(ItemStack item, boolean meta, boolean nbt) { this.item = item; this.meta = meta; this.nbt = nbt; }
        boolean covers(Pattern other) { return (meta || !other.meta && item.getItemDamage() == other.item.getItemDamage())
                && (nbt || !other.nbt && ItemStack.areItemStackTagsEqual(item, other.item)); }
        boolean overlaps(Pattern other) { return (meta || other.meta || item.getItemDamage() == other.item.getItemDamage())
                && (nbt || other.nbt || ItemStack.areItemStackTagsEqual(item, other.item)); }
        JsonObject rule(boolean excludeSynthetic) {
            if (excludeSynthetic) return object("kind", "tags", "meta", meta, "keys", array(), "present", array(), "absent", array("synthetic"));
            return meta || nbt ? object("kind", "wildcard", "meta", meta, "nbt", nbt) : object("kind", "exact");
        }
    }
    static int[][] progressBars(TemplateRecipeHandler handler) {
        return handler.getClass().getName().equals(NEI + "CokeOven")
                ? new int[][] {{40, 32, 177, 61, 21, 16, 100, 0}, {21, 15, 176, 47, 14, 14, 100, 11}}
                : new int[][] {{51, 25, 176, 0, 14, 14, 48, 7}, {74, 23, 176, 14, 24, 16, 48, 0}};
    }
    static void draw(TemplateRecipeHandler handler) {
        handler.drawBackground(0);
        if (handler.getClass().getName().equals(NEI + "CokeOven"))
            invoke(handler.getClass(), handler, "drawFluidTanks", new Class<?>[] {int.class}, 0);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
