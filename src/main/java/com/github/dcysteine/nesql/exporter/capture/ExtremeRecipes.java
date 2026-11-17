package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Pinned Avaritia definitions; owns copies, never the manager or a NEI cache. */
final class ExtremeRecipes implements RegistryRecipes {
    private static final String PREFIX = "fox.spiteful.avaritia.";
    private Source source;
    private final List<Object> nativeOrder;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> visible = new ArrayList<>();
    private final NativeDefinitions definitions;
    private boolean closed;

    static boolean supports(IRecipeHandler handler) {
        String name = handler.getClass().getName();
        return name.equals(PREFIX + "compat.nei.ExtremeShapedRecipeHandler")
                || name.equals(PREFIX + "compat.nei.ExtremeShapelessRecipeHandler");
    }
    static Source registrationSource(IRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unadapted extreme handler: " + handler.getClass().getName());
        MagicApi.version("ExtremeRecipes","Avaritia");
        return new Source();
    }
    static final class Source {
        final Object manager;
        final List<?> recipes;
        Source() {
            Class<?> type = type(PREFIX + "crafting.ExtremeCraftingManager");
            manager = field(type, null, "instance");
            Object table = manager == null ? null : field(manager, "recipes");
            if (manager == null || table == null) throw new Jobs.Fault("handler_not_ready", "Extreme crafting manager/list is unavailable");
            if (manager.getClass() != type || table.getClass() != ArrayList.class)
                throw fault("Unadapted extreme manager/list implementation");
            recipes = (List<?>)table;
            if (recipes.size() > 262144) throw new Jobs.Fault("recipe_limit", "Extreme registry exceeds its budget");
        }
    }
    ExtremeRecipes(TemplateRecipeHandler handler) {
        source = registrationSource(handler);
        nativeOrder = new ArrayList<>(source.recipes);
        boolean shaped = handler.getClass().getName().endsWith("ExtremeShapedRecipeHandler");
        for (int index = 0; index < nativeOrder.size(); index++) {
            Jobs.checkpoint();
            Object recipe = nativeOrder.get(index);
            // Both handlers use this ordered manager, even when their visible rows differ.
            Raw raw = new Raw(recipe, true);
            Entry entry = new Entry(recipe, index, raw);
            entries.add(entry);
            if (raw.shaped == shaped) visible.add(entry);
        }
        definitions = new NativeDefinitions("Extreme crafting", entries, this::current,
                entry -> ((Entry)entry).definition());
    }
    private List<?> current() {
        Source actual;
        try { actual = new Source(); }
        catch (Jobs.Fault failure) { Jobs.Fault drift = changed(); drift.initCause(failure); throw drift; }
        if (source.manager != actual.manager || source.recipes != actual.recipes || nativeOrder.size() != source.recipes.size()) throw changed();
        return entries;
    }
    public int size() { verify(); return visible.size(); }
    public void verify() {
        if (closed) throw new Jobs.Fault("capture_closed", "Extreme reader is closed");
        definitions.verify();
    }
    public void finishVerification() {
        verify();
        for (int index = 0; index < nativeOrder.size(); index++) {
            Jobs.checkpoint();
            if (source.recipes.get(index) != nativeOrder.get(index)) throw changed();
        }
        definitions.finishVerification();
    }
    public boolean capture(int index, RecipeRow row) {
        verify();
        Entry entry = visible.get(index);
        return definitions.checked(entry.nativeIndex, () -> {
            JsonArray earlier = array(); int candidates = 0;
            for (Entry prior : entries) {
                if (prior == entry) break;
                definitions.record(prior.nativeIndex);
                if (prior.raw.empty) continue;
                for (List<?> group : prior.raw.inputs) if (group != null) candidates += group.size();
                if (candidates > 1048576) throw new Jobs.Fault("recipe_limit", "Extreme selector candidates exceed budget");
                earlier.add(prior.raw.selector(row.facts));
            }
            row.record.add("process", object("kind", "extremeCrafting", "nativeIndex", entry.nativeIndex,
                    "earlier", earlier, "repair", entry.raw.repair(row.facts)));
            return emit(entry.raw, row);
        });
    }
    public void close() {
        if (closed) return;
        closed = true;
        definitions.close(); entries.clear(); visible.clear(); nativeOrder.clear(); source = null;
    }
    private final class Entry {
        final Object recipe;
        final int nativeIndex;
        final Raw raw;
        Entry(Object recipe, int nativeIndex, Raw raw) { this.recipe = recipe; this.nativeIndex = nativeIndex; this.raw = raw; }
        JsonObject definition() {
            if (source.recipes.get(nativeIndex) != recipe) throw changed();
            Raw actual;
            try { actual = new Raw(recipe, false); }
            catch (Jobs.Fault failure) { Jobs.Fault drift = changed(); drift.initCause(failure); throw drift; }
            sameAnchors(raw.anchors, actual.anchors);
            raw.verifyInputs();
            return object("nativeIndex", nativeIndex, "definition", actual.data);
        }
    }

    /** Small native-family test entry; production uses the guarded reader. */
    static boolean capture(IRecipe recipe, TemplateRecipeHandler handler, RecipeRow row) {
        Raw raw = new Raw(recipe, true);
        boolean result = emit(raw, row);
        Raw actual = new Raw(recipe, false);
        sameAnchors(raw.anchors, actual.anchors);
        if (!raw.data.equals(actual.data)) throw changed();
        raw.verifyInputs();
        return result;
    }
    private static boolean emit(Raw raw, RecipeRow row) {
        raw.verifyInputs();
        if (raw.empty) return row.exclude(object("native", raw.name, "reason", "empty_native_required_input_predicate"));
        if (raw.shaped) row.record.add("grid", raw.grid());
        else row.property("minecraft:shapeless", "Shapeless", true);
        int slot = 0;
        for (List<ExtremeIngredients.Choice> group : raw.inputs) {
            if (group == null) continue;
            List<RecipeRow.Ingredient> ingredients = new ArrayList<>();
            for (ExtremeIngredients.Choice choice : group) ingredients.add(choice.ingredient());
            row.nativeCraftingInput(slot++, ingredients);
        }
        row.itemOutput(0, raw.output.copy(), 10000);
        return true;
    }
    private static final class Raw {
        final String name;
        final boolean shaped, mirror, empty;
        final int width, height;
        final ItemStack output;
        final List<Object> anchors = new ArrayList<>();
        final List<List<ExtremeIngredients.Choice>> inputs = new ArrayList<>();
        final List<NativeCraftingRepair> repairs = new ArrayList<>();
        final JsonObject data;
        Raw(Object recipe, boolean prepare) {
            if (recipe == null) throw fault("Null extreme native definition");
            Class<?> owner = recipe.getClass(); name = owner.getName();
            shaped = name.equals(PREFIX + "crafting.ExtremeShapedRecipe") || name.equals(PREFIX + "crafting.ExtremeShapedOreRecipe");
            boolean forge = owner == ShapelessOreRecipe.class;
            if (!shaped && !forge && !name.equals(PREFIX + "crafting.ExtremeShapelessRecipe"))
                throw fault("Unadapted extreme recipe override: " + name);
            boolean ore = name.endsWith("OreRecipe");
            width = shaped ? (Integer)field(recipe, ore ? "width" : "recipeWidth") : 9;
            height = shaped ? (Integer)field(recipe, ore ? "height" : "recipeHeight") : 9;
            mirror = shaped && (!ore || (Boolean)field(recipe, "mirrored"));
            Object supplied = field(recipe, ore ? "input" : "recipeItems");
            if (supplied == null) throw new Jobs.Fault("handler_not_ready", "Extreme native input is unavailable");
            if (supplied instanceof List<?>) requireList(supplied);
            else if (supplied.getClass() != Object[].class && supplied.getClass() != ItemStack[].class)
                throw fault("Unadapted extreme input storage");
            anchors.add(supplied);
            Object[] cells = supplied instanceof List<?> ? ((List<?>)supplied).toArray() : ((Object[])supplied).clone();
            if (width < 1 || width > 9 || height < 1 || height > 9 || cells.length == 0 || cells.length > 81
                    || shaped && cells.length != width * height) throw fault("Invalid extreme crafting grid");
            JsonArray rawCells = array(); int occupied = 0, candidates = 0; boolean missing = false;
            List<List<ItemStack>> templates = new ArrayList<>();
            for (Object cell : cells) {
                Jobs.checkpoint(); anchors.add(cell);
                if (cell == null) {
                    if (!shaped) throw fault("Null shapeless extreme ingredient");
                    rawCells.add(value(null)); inputs.add(null); continue;
                }
                occupied++;
                boolean group = cell instanceof ArrayList<?>;
                if (group && !ore) throw fault("Unadapted extreme alternative list");
                List<?> choices = group ? NativeOreStorage.arrayList(cell) : Collections.singletonList(cell);
                if(group)anchors.add(choices);
                missing |= choices.isEmpty();
                JsonArray facts = array(); List<ExtremeIngredients.Choice> offered = new ArrayList<>();
                List<ItemStack> nativeGroup = new ArrayList<>(); templates.add(nativeGroup);
                for (Object choice : choices) {
                    ItemStack stack = stack(choice, anchors);
                    nativeGroup.add(stack);
                    facts.add(RecipeRow.stackEvidence(stack));
                    boolean ignoreNbt = forge || group || !stack.hasTagCompound();
                    List<ExtremeIngredients.Choice> concrete = prepare ? ExtremeIngredients.expand(stack, ignoreNbt) : Collections.emptyList();
                    candidates += prepare ? concrete.size() : 1;
                    if (candidates > 65536) throw new Jobs.Fault("recipe_limit", "Extreme ingredient expansion exceeds its budget");
                    offered.addAll(concrete);
                }
                rawCells.add(facts); inputs.add(offered);
            }
            if (occupied == 0) throw fault("Extreme recipe has no occupied inputs");
            empty = missing;
            ItemStack result = stack(field(recipe, ore ? "output" : "recipeOutput"), anchors);
            if (result.stackSize <= 0) throw fault("Extreme recipe has no fixed positive output");
            output = result.copy();
            JsonArray repairData = array();
            if (templates.size() == 2) {
                Set<Item> second = Collections.newSetFromMap(new IdentityHashMap<>());
                for (ItemStack stack : templates.get(1)) second.add(stack.getItem());
                Set<Item> seen = Collections.newSetFromMap(new IdentityHashMap<>());
                for (ItemStack stack : templates.get(0)) if (second.contains(stack.getItem()) && seen.add(stack.getItem())) {
                    NativeCraftingRepair repair = new NativeCraftingRepair(stack.getItem()); repairs.add(repair);
                    repairData.add(object("registry", Item.itemRegistry.getNameForObject(repair.item), "state", repair.state));
                }
            }
            data = object("class", name, "width", width, "height", height, "mirror", mirror, "cells", rawCells,
                    "output", RecipeRow.stackEvidence(result), "repair", repairData);
        }
        void verifyInputs() {
            Set<Object> metadata = Collections.newSetFromMap(new IdentityHashMap<>());
            for (List<ExtremeIngredients.Choice> group : inputs) if (group != null)
                for (ExtremeIngredients.Choice choice : group) { Jobs.checkpoint(); choice.verify(metadata); }
        }
        JsonObject grid() {
            JsonArray cells = array(); int slot = 0;
            for (List<?> group : inputs) cells.add(value(group == null ? null : slot++));
            return object("width", width, "height", height, "cells", cells, "mirror", mirror);
        }
        JsonObject selector(Facts facts) {
            JsonArray required = array();
            for (List<ExtremeIngredients.Choice> group : inputs) if (group != null) {
                JsonArray choices = array(); Set<String> seen = new HashSet<>();
                for (ExtremeIngredients.Choice offered : group) {
                    RecipeRow.Ingredient ingredient = offered.ingredient();
                    JsonObject choice = object("id", facts.item(ingredient.item), "rule", ingredient.rule);
                    if (seen.add(choice.toString())) choices.add(choice);
                }
                required.add(choices);
            }
            return object("grid", shaped ? grid() : null, "inputs", required);
        }
        JsonArray repair(Facts facts) {
            JsonArray result = array();
            for (NativeCraftingRepair repair : repairs) {
                ItemStack specimen = null;
                for (List<ExtremeIngredients.Choice> group : inputs) if (group != null)
                    for (ExtremeIngredients.Choice offered : group) if (offered.sample.getItem() == repair.item) { specimen = offered.sample; break; }
                if (specimen == null) throw fault("Extreme repair item has no concrete specimen");
                result.add(object("item", facts.item(specimen), "enabled", repair.enabled, "maximum", repair.maximum));
            }
            return result;
        }
    }
    static ItemStack stack(Object value, List<Object> anchors) {
        if (value == null || value.getClass() != ItemStack.class || ((ItemStack)value).getItem() == null)
            throw fault("Unadapted extreme native stack");
        ItemStack stack = (ItemStack)value;
        if (net.minecraft.init.Items.feather.getDamage(stack) < 0)
            throw fault("Extreme literal negative metadata requires a dedicated predicate; native copy would clamp it");
        NativeStackAudit.auditLogical(stack);
        anchors.add(stack); anchors.add(stack.getItem()); anchors.add(stack.getTagCompound());
        return stack;
    }
    private static void requireList(Object list) {
        Class<?> kind = list.getClass();
        if (kind != ArrayList.class && kind != Arrays.asList().getClass()
                && kind != Collections.singletonList(null).getClass() && kind != Collections.emptyList().getClass())
            throw fault("Unadapted extreme input list");
    }
    static void sameAnchors(List<Object> expected, List<Object> actual) {
        if (expected.size() != actual.size()) throw changed();
        for (int index = 0; index < expected.size(); index++) if (expected.get(index) != actual.get(index)) throw changed();
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
    private static Jobs.Fault changed() { return new Jobs.Fault("recipe_changed", "Extreme native source, order, member or definition changed"); }
}
