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
import net.minecraft.item.crafting.CraftingManager;
import java.util.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Blood Magic's greedy matcher, restricted to provably order-independent groups. */
final class BloodOrbShapelessRecipes implements RegistryRecipes {
    private static final String ROOT = "WayofTime.alchemicalWizardry.";
    private static final String HANDLER = ROOT + "client.nei.NEIBloodOrbShapelessHandler";
    private static final String RECIPE = ROOT + "api.items.ShapelessBloodOrbRecipe";
    private static final int[][] POSITIONS = {{25,6},{43,6},{25,24},{43,24},{25,42},{43,42},{61,6},{61,24},{61,42}};
    private final TemplateRecipeHandler handler;
    private final List<Entry> entries;
    private final String signature;

    static boolean supports(ICraftingHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    BloodOrbShapelessRecipes(TemplateRecipeHandler handler) {
        version("AWWayofTime", "1.7.52");
        if (!supports(handler)) throw fault("Unadapted Blood Orb shapeless handler");
        this.handler = handler; List<Item> orbs = BloodOrbShapedRecipes.orbs();
        entries = registry(orbs); signature = signature(entries, orbs);
    }
    public int size() { return entries.size(); }
    public void verify() {
        List<Item> orbs = BloodOrbShapedRecipes.orbs();
        if (!signature.equals(signature(registry(orbs), orbs))) throw new Jobs.Fault("environment_changed", "Blood Orb shapeless registry or orb levels changed");
    }
    public boolean capture(int index, RecipeRow row) {
        Entry entry = entries.get(index);
        if (entry.emptyInput >= 0) return false;
        ArrayList<Object> projection = new ArrayList<>();
        for (Group group : entry.groups) {
            ArrayList<ItemStack> choices = new ArrayList<>();
            for (Pattern pattern : group.patterns) choices.add(pattern.item.copy());
            projection.add(choices);
        }
        TemplateRecipeHandler.CachedRecipe cached = (TemplateRecipeHandler.CachedRecipe)TinkerRecipes.construct(
                type(HANDLER + "$CachedBloodOrbRecipe"), new Class<?>[]{handler.getClass(), ArrayList.class, ItemStack.class},
                handler, projection, entry.output.copy());
        List<PositionedStack> positions = cached.getIngredients();
        if (positions.size() != entry.groups.size()) throw new Jobs.Fault("slot_changed", "Blood Orb shapeless native view lost an input");
        for (int slot = 0; slot < entry.groups.size(); slot++) {
            Group group = entry.groups.get(slot); PositionedStack position = positions.get(slot);
            if (position.relx != POSITIONS[slot][0] || position.rely != POSITIONS[slot][1])
                throw new Jobs.Fault("slot_changed", "Blood Orb native shapeless stack order changed");
            List<RecipeRow.Ingredient> choices = new ArrayList<>(); Map<String, ItemStack> returns = new HashMap<>();
            for (Pattern pattern : group.patterns) {
                choices.add(new RecipeRow.Ingredient(pattern.item.copy(), 1, pattern.keep, pattern.rule()));
                if (pattern.returned != null) returns.put(row.facts.item(pattern.item), pattern.returned);
            }
            row.itemInput(position, slot, choices, false);
            for (com.google.gson.JsonElement item : row.inputs.get(row.inputs.size() - 1).getAsJsonObject().getAsJsonArray("choices")) {
                JsonObject choice = item.getAsJsonObject(); ItemStack returned = returns.get(choice.get("id").getAsString());
                if (returned != null) choice.getAsJsonArray("returns").add(object("kind", "item", "id", row.facts.item(returned), "amount", Integer.toString(returned.stackSize)));
            }
            if (group.tier != null) row.property("bloodmagic:orbTier" + slot, "Minimum blood orb level for input " + slot, group.tier);
        }
        row.itemOutput(cached.getResult(), 0, entry.output.copy(), 10000);
        handler.arecipes.clear(); handler.arecipes.add(cached); return true;
    }
    public JsonObject exclusion(int index) {
        Entry entry = entries.get(index);
        return entry.emptyInput < 0 ? null : object("reason", "native_input_domain_empty", "scope", "registered_entry", "nativeClass", RECIPE,
                "input", entry.emptyInput, "condition", "A native ore group or minimum-orb-level group has no registered matching items, so the required group cannot be removed by matches");
    }
    private static List<Entry> registry(List<Item> orbs) {
        Class<?> nativeType = type(RECIPE); List<Entry> entries = new ArrayList<>();
        for (Object recipe : CraftingManager.getInstance().getRecipeList()) if (nativeType.isInstance(recipe)) {
            Jobs.checkpoint();
            if (entries.size() == 262144) throw fault("Blood Orb shapeless registry exceeds its budget");
            entries.add(new Entry(recipe, orbs));
        }
        return entries;
    }
    private static final class Entry {
        final List<Group> groups = new ArrayList<>();
        final ItemStack output;
        final int emptyInput;
        final String signature;
        Entry(Object recipe, List<Item> orbs) {
            if (!recipe.getClass().getName().equals(RECIPE)) throw fault("Unadapted Blood Orb shapeless recipe override");
            Object source = field(recipe, "input");
            if (source == null || source.getClass() != ArrayList.class) throw fault("Unadapted Blood Orb shapeless ingredient list");
            List<?> raw = (List<?>)source;
            if (raw.isEmpty() || raw.size() > 9) throw fault("Invalid Blood Orb shapeless ingredient count");
            output = owned((ItemStack)field(recipe, "output"));
            if (output.stackSize <= 0) throw fault("Blood Orb shapeless recipe has no positive fixed output");
            Work work = new Work(); JsonArray inputs = array(); int empty = -1;
            for (Object input : raw) {
                Group group = new Group(input, orbs, work); groups.add(group); inputs.add(value(group.sourceSignature));
                if (group.patterns.isEmpty()) empty = groups.size() - 1;
            }
            emptyInput = empty;
            if (emptyInput < 0) for (int i = 0; i < groups.size(); i++) for (int j = i + 1; j < groups.size(); j++) {
                Group first = groups.get(i), later = groups.get(j);
                boolean equal = first.signature.equals(later.signature);
                // An integer requirement aborts on a mismatch rather than trying
                // later groups. Every later group must therefore be equivalent.
                if (first.tier != null && !equal) throw fault("Blood Orb integer requirement has a later non-equivalent group and an order-dependent early failure");
                // With equal/disjoint groups the greedy first removal is exactly
                // unordered matching, including repeated distinct required slots.
                if (!equal && first.overlaps(later, work)) throw fault("Blood Orb shapeless predicates overlap with native order-dependent greedy removal");
            }
            signature = CanonicalJson.digest(object("inputs", inputs, "output", stack(output)));
        }
    }
    private static final class Group {
        final Integer tier;
        final List<Pattern> patterns = new ArrayList<>();
        final String signature, sourceSignature;
        Group(Object source, List<Item> orbs, Work work) {
            if (source == null) throw fault("Blood Orb shapeless input is null");
            tier = source instanceof Integer ? (Integer)source : null;
            JsonArray raw = array();
            if (tier != null) {
                raw.add(value(tier));
                for (Item orb : orbs) { work.step(); if (BloodOrbShapedRecipes.orbLevel(orb) >= tier) patterns.add(new Pattern(new ItemStack(orb), true)); }
            } else if (source.getClass() == ItemStack.class) {
                ItemStack item = owned((ItemStack)source); raw.add(stack(item)); patterns.add(new Pattern(item, false));
            } else if (BloodOrbShapedRecipes.oreGroup(source)) {
                for (Object value : (ArrayList<?>)source) {
                    work.step();
                    if (value == null || value.getClass() != ItemStack.class) throw fault("Blood Orb shapeless ore group contains an unaudited stack");
                    ItemStack item = owned((ItemStack)value); raw.add(stack(item)); patterns.add(new Pattern(item, false));
                }
            } else throw fault("Unadapted Blood Orb shapeless input predicate: " + source.getClass().getName());
            sourceSignature = CanonicalJson.digest(object("kind", source.getClass().getName(), "values", raw));
            TreeMap<String, Pattern> unique = new TreeMap<>();
            for (Pattern pattern : patterns) { work.step(); unique.put(CanonicalJson.digest(pattern.record()), pattern); }
            patterns.clear(); patterns.addAll(unique.values());
            JsonArray normalized = array(); for (Pattern pattern : patterns) normalized.add(pattern.record());
            signature = CanonicalJson.digest(normalized);
        }
        boolean overlaps(Group other, Work work) {
            for (Pattern a : patterns) for (Pattern b : other.patterns) { work.step(); if (a.item.getItem() == b.item.getItem()
                    && (a.wildcard || b.wildcard || Items.feather.getDamage(a.item) == Items.feather.getDamage(b.item))) return true; }
            return false;
        }
    }
    private static final class Pattern {
        final ItemStack item, returned;
        final boolean wildcard, keep;
        Pattern(ItemStack source, boolean tier) {
            source = owned(source); Item raw = source.getItem();
            if (!tier && ItemCallbacks.method(raw, "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                throw fault("Unadapted Blood Orb shapeless metadata callback");
            int metadata = Items.feather.getDamage(source);
            if (metadata < 0) throw fault("Blood Orb shapeless input has negative metadata");
            wildcard = tier || metadata == 32767; item = new ItemStack(raw, 1, wildcard ? 0 : metadata);
            keep = type(ROOT + "api.items.interfaces.IBloodOrb").isInstance(raw);
            if (keep) { BloodOrbShapedRecipes.orbLevel(raw); returned = null; }
            else { ItemStack container = ItemCallbacks.container(item.copy(), wildcard); returned = container == null ? null : container.copy(); }
        }
        JsonObject rule() { return object("kind", "wildcard", "meta", wildcard, "nbt", true); }
        JsonObject record() { return object("item", stack(item), "rule", rule(), "keep", keep, "returned", returned == null ? null : stack(returned)); }
    }
    private static ItemStack owned(ItemStack item) {
        if (item == null || item.getClass() != ItemStack.class || item.getItem() == null || Item.itemRegistry.getNameForObject(item.getItem()) == null)
            throw fault("Invalid Blood Orb shapeless registry stack");
        return item.copy();
    }
    private static JsonObject stack(ItemStack item) {
        return object("item", Item.itemRegistry.getNameForObject(item.getItem()), "meta", Items.feather.getDamage(item), "amount", item.stackSize, "nbt", TypedNbt.encode(item.getTagCompound()));
    }
    private static String signature(List<Entry> entries, List<Item> orbs) {
        JsonArray values = array(); for (Entry entry : entries) values.add(value(entry.signature));
        for (Item orb : orbs) values.add(object("orb", Item.itemRegistry.getNameForObject(orb), "tier", BloodOrbShapedRecipes.orbLevel(orb)));
        return CanonicalJson.digest(values);
    }
    private static final class Work { int count; void step() { Jobs.checkpoint(); if (++count > 262144) throw fault("Blood Orb shapeless predicate work exceeds its budget"); } }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
