package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** ExtraUtilities 1.2.12 QED: native ordered definitions, never a NEI GUI projection. */
final class QedRecipes implements RegistryRecipes {
    private static final String HANDLER = "com.rwtema.extrautils.nei.EnderConstructorHandler";
    private static final String REGISTRY = "com.rwtema.extrautils.tileentity.enderconstructor.EnderConstructorRecipesHandler";
    private List<?> registry;
    private final boolean nativeRegistry;
    private final List<Entry> all = new ArrayList<>(), visible = new ArrayList<>();
    private final NativeDefinitions definitions;
    private boolean closed;

    static boolean supports(IRecipeHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    static List<?> registrationSource(IRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unadapted QED registration");
        MagicApi.version("QedRecipes","ExtraUtilities");
        return registry();
    }
    private static List<?> registry() {
        Object table = field(type(REGISTRY), null, "recipes");
        if (table == null) throw new Jobs.Fault("handler_not_ready", "Native QED registry is unavailable");
        if (table.getClass() != ArrayList.class) throw fault("Unadapted native QED registry storage");
        return (List<?>)table;
    }
    QedRecipes(TemplateRecipeHandler handler) { this(handler, registrationSource(handler), true); }
    QedRecipes(TemplateRecipeHandler handler, List<?> registry) { this(handler, registry, false); }
    private QedRecipes(TemplateRecipeHandler handler, List<?> registry, boolean nativeRegistry) {
        if (registry == null) throw new Jobs.Fault("handler_not_ready", "QED registry is unavailable");
        if (!supports(handler) || registry.getClass() != ArrayList.class) throw fault("Unadapted QED handler or registry storage");
        if (registry.size() > 262144) throw new Jobs.Fault("recipe_limit", "QED registry exceeds budget");
        this.registry = registry; this.nativeRegistry = nativeRegistry;
        for (Object recipe : registry) {
            Jobs.checkpoint(); Entry entry = new Entry(recipe, all.size(), true); all.add(entry);
            // The native NEI loader lists shaped entries, while gameplay selects
            // from the entire shared shaped/shapeless order.
            if (entry.raw.shaped) visible.add(entry);
        }
        definitions = new NativeDefinitions("QED", all, this::current, entry -> definition((Entry)entry));
    }
    private List<?> current() {
        if (nativeRegistry && field(type(REGISTRY), null, "recipes") != registry || registry.size() != all.size()) throw changed();
        return all;
    }
    public int size() { verify(); return visible.size(); }
    public void verify() {
        if (closed) throw new Jobs.Fault("capture_closed", "QED reader is closed");
        definitions.verify();
    }
    public void finishVerification() { verify(); definitions.finishVerification(); }
    public void close() {
        if (closed) return;
        closed = true; definitions.close(); all.clear(); visible.clear(); registry = null;
    }
    private JsonObject definition(Entry entry) {
        if (registry.get(entry.index) != entry.source) throw changed();
        Entry actual;
        try { actual = new Entry(entry.source, entry.index, false); }
        catch (Jobs.Fault failure) { Jobs.Fault drift = changed(); drift.initCause(failure); throw drift; }
        if (entry.anchors.size() != actual.anchors.size()) throw changed();
        for (int index = 0; index < entry.anchors.size(); index++) if (entry.anchors.get(index) != actual.anchors.get(index)) throw changed();
        for (NativeItemVariants variants : entry.variants) variants.verify();
        return actual.data;
    }
    public boolean capture(int index, RecipeRow row) {
        verify(); Entry entry = visible.get(index);
        return definitions.checked(entry.index, () -> capture(entry, row));
    }
    private boolean capture(Entry entry, RecipeRow row) {
        if (entry.raw.empty) return row.exclude(object("native", HANDLER, "reason", "empty_native_required_input_predicate", "nativeIndex", entry.index));
        JsonArray earlier = array(); int count = 0;
        for (Entry prior : all) {
            if (prior == entry) break;
            definitions.record(prior.index);
            if (prior.raw.empty) continue;
            for (List<ItemStack> group : prior.samples) if (group != null) count += group.size();
            if (count > 1048576) throw new Jobs.Fault("recipe_limit", "QED first-match selector candidates exceed budget");
            earlier.add(prior.selector(row.facts));
        }
        row.record.add("process", object("kind", "qed", "enderFlux", "20000", "earlier", earlier));
        if (entry.raw.copyNbt) row.record.getAsJsonObject("process").addProperty("copyNbt", true);
        row.record.add("grid", entry.raw.grid());
        Map<String, ItemStack> specimens = new HashMap<>();
        int slot = 0;
        for (List<ItemStack> group : entry.samples) if (group != null) {
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (ItemStack sample : group) {
                choices.add(new RecipeRow.Ingredient(sample.copy(), 1, false, entry.rules.get(sample)));
                if (entry.raw.copyNbt) specimens.put(row.facts.item(sample), sample.copy());
            }
            // The tile decrements every occupied cell, without container callbacks.
            row.itemInput(slot++, choices, false);
        }
        if (entry.raw.copyNbt) NativeCraftingNbt.attachResult((ShapedRecipes)entry.source, row, specimens);
        else row.itemOutput(0, entry.raw.output.copy(), 10000);
        return true;
    }
    private static final class Entry {
        final Object source; final int index;
        final NativeCraftingRecipes.Raw raw;
        final List<Object> anchors = new ArrayList<>();
        final List<NativeItemVariants> variants = new ArrayList<>();
        final List<List<ItemStack>> samples = new ArrayList<>();
        final Map<ItemStack, JsonObject> rules = new IdentityHashMap<>();
        final JsonObject data;
        Entry(Object source, int index, boolean prepare) {
            this.source = source; this.index = index;
            if (source == null) throw fault("Null QED native recipe");
            Class<?> c = source.getClass();
            if (c != ShapedRecipes.class && c != ShapedOreRecipe.class && c != ShapelessRecipes.class && c != ShapelessOreRecipe.class)
                throw fault("Unadapted QED executable recipe: " + c.getName());
            Object input = c == ShapedRecipes.class ? ((ShapedRecipes)source).recipeItems
                    : c == ShapedOreRecipe.class ? ((ShapedOreRecipe)source).getInput()
                    : c == ShapelessRecipes.class ? ((ShapelessRecipes)source).recipeItems : ((ShapelessOreRecipe)source).getInput();
            if (input == null) throw new Jobs.Fault("handler_not_ready", "QED input storage is unavailable");
            if (input instanceof List<?>) list(input);
            else if (input.getClass() != ItemStack[].class && input.getClass() != Object[].class) throw fault("Unadapted QED input storage");
            anchors.add(input);
            Object[] cells = input instanceof List<?> ? ((List<?>)input).toArray() : (Object[])input;
            for (Object cell : cells) {
                anchors.add(cell);
                if (cell == null) continue;
                if (cell instanceof ArrayList<?>) {
                    List<?> choices=NativeOreStorage.arrayList(cell);anchors.add(choices);
                    for (Object choice : choices) stack(choice, anchors);
                } else stack(cell, anchors);
            }
            stack(((IRecipe)source).getRecipeOutput(), anchors);
            raw = new NativeCraftingRecipes.Raw((IRecipe)source);
            JsonArray facts = array(); int count = 0;
            for (List<ItemStack> group : raw.inputs) {
                if (group == null) { facts.add(value(null)); samples.add(null); continue; }
                JsonArray nativeChoices = array(); List<ItemStack> examples = new ArrayList<>();
                for (ItemStack template : group) {
                    Jobs.checkpoint(); nativeChoices.add(RecipeRow.stackEvidence(template));
                    if (!prepare || raw.empty) continue;
                    boolean wildcard = net.minecraft.init.Items.feather.getDamage(template) == 32767;
                    ItemStack[] concrete;
                    if (wildcard) {
                        NativeItemVariants binding = new NativeItemVariants(template); variants.add(binding); concrete = binding.copies();
                    } else concrete = new ItemStack[] {template.copy()};
                    count += concrete.length;
                    if (count > 65536) throw new Jobs.Fault("recipe_limit", "QED concrete candidates exceed budget");
                    for (ItemStack sample : concrete) {
                        ItemStack owned = sample.copy(); owned.stackSize = 1;
                        owned.setTagCompound(template.getTagCompound() == null ? null : (net.minecraft.nbt.NBTTagCompound)template.getTagCompound().copy());
                        examples.add(owned); rules.put(owned, rule(wildcard));
                    }
                }
                facts.add(nativeChoices); samples.add(examples);
            }
            data = object("index", index, "class", c.getName(), "width", raw.width, "height", raw.height,
                    "mirror", raw.mirror, "copyNbt", raw.copyNbt, "cells", facts, "output", RecipeRow.stackEvidence(raw.output));
        }
        JsonObject selector(Facts facts) {
            JsonArray inputs = array();
            for (List<ItemStack> group : samples) if (group != null) {
                JsonArray choices = array(); Set<String> seen = new HashSet<>();
                for (ItemStack sample : group) {
                    JsonObject choice = object("id", facts.item(sample), "rule", rules.get(sample));
                    if (seen.add(choice.toString())) choices.add(choice);
                }
                inputs.add(choices);
            }
            return object("grid", raw.shaped ? raw.grid() : null, "inputs", inputs);
        }
    }
    private static void stack(Object value, List<Object> anchors) {
        if (value == null || value.getClass() != ItemStack.class || ((ItemStack)value).getItem() == null) throw fault("Unadapted QED native stack");
        ItemStack stack = (ItemStack)value;
        if (net.minecraft.init.Items.feather.getDamage(stack) < 0)
            throw fault("QED literal negative metadata requires a dedicated predicate; native copy would clamp it");
        if (ItemCallbacks.method(stack.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
            throw fault("QED input/output requires an audited metadata getter");
        anchors.add(stack); anchors.add(stack.getItem()); anchors.add(stack.getTagCompound());
    }
    private static void list(Object source) {
        Class<?> c = source.getClass();
        if (c != ArrayList.class && c != Arrays.asList().getClass() && c != Collections.singletonList(null).getClass() && c != Collections.emptyList().getClass())
            throw fault("Unadapted QED input-list callback");
    }
    private static JsonObject rule(boolean wildcard) { return object("kind", "wildcard", "meta", wildcard, "nbt", true); }
    private static Jobs.Fault changed() { return new Jobs.Fault("recipe_changed", "QED native source, order, member or facts changed"); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
