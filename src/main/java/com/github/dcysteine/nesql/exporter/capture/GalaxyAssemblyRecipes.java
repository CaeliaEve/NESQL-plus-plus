package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.IRecipeHandler;
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

/** GalaxySpace registered assembly with explicit repair-first and selection conditions. */
final class GalaxyAssemblyRecipes implements RegistryRecipes {
    private static final String HANDLER = "galaxyspace.core.nei.AssemblyMachineRecipeHandler";
    private static final String REGISTRY = "galaxyspace.core.recipe.AssemblyRecipes";
    private static final String CONFIG = "micdoodle8.mods.galacticraft.core.util.ConfigManagerCore";
    private List<?> registry;
    private final List<Entry> entries = new ArrayList<>();
    private final boolean hard;
    private final boolean nativeRegistry;
    private final NativeDefinitions definitions;
    private boolean closed;

    static boolean supports(IRecipeHandler handler) { return handler.getClass().getName().equals(HANDLER); }
    GalaxyAssemblyRecipes(TemplateRecipeHandler handler) {
        this(handler, registrationSource(handler), hardMode(), true);
    }
    static List<?> registrationSource(IRecipeHandler handler) {
        if (!supports(handler)) throw fault("Unadapted GalaxySpace assembly registration");
        MagicApi.version("GalaxyAssemblyRecipes","GalaxySpace"); MagicApi.version("GalaxyAssemblyRecipes","GalacticraftCore");
        Object source = field(type(REGISTRY), null, "RECIPES");
        if (source == null) throw new Jobs.Fault("handler_not_ready", "GalaxySpace assembly registry is unavailable");
        if (source.getClass() != ArrayList.class) throw fault("Unadapted assembly registry storage");
        return (List<?>)source;
    }
    static boolean hardMode() { return (Boolean)field(type(CONFIG), null, "hardMode"); }
    GalaxyAssemblyRecipes(TemplateRecipeHandler handler, List<?> registry, boolean hard) {
        this(handler, registry, hard, false);
    }
    private GalaxyAssemblyRecipes(TemplateRecipeHandler handler, List<?> registry, boolean hard, boolean nativeRegistry) {
        if (registry == null) throw new Jobs.Fault("handler_not_ready", "GalaxySpace assembly registry is unavailable");
        if (!supports(handler) || registry.getClass() != ArrayList.class) throw fault("Unadapted assembly handler/registry storage");
        if (registry.size() > 65536) throw new Jobs.Fault("recipe_limit", "GalaxySpace assembly registry exceeds budget");
        this.registry = registry; this.hard = hard; this.nativeRegistry = nativeRegistry;
        Work work = new Work();
        for (Object source : registry) { Jobs.checkpoint(); entries.add(new Entry(source, entries.size(), work, true)); }
        definitions = new NativeDefinitions("GalaxySpace assembly", entries, this::current, entry -> definition((Entry)entry));
    }
    private List<?> current() {
        if (registry.size() != entries.size() || nativeRegistry && (field(type(REGISTRY), null, "RECIPES") != registry || hardMode() != hard)) changed();
        return entries;
    }
    public int size() { verify(); return entries.size(); }
    public void verify() {
        if (closed) throw new Jobs.Fault("capture_closed", "GalaxySpace assembly reader is closed");
        definitions.verify();
    }
    public void finishVerification() { verify(); definitions.finishVerification(); }
    public void close() { if (!closed) { closed = true; definitions.close(); entries.clear(); registry = null; } }
    private JsonObject definition(Entry entry) {
        if (registry.get(entry.index) != entry.source) changed();
        Entry actual;
        try { actual = new Entry(entry.source, entry.index, new Work(), false); }
        catch (Jobs.Fault failure) { Jobs.Fault drift = drift(); drift.initCause(failure); throw drift; }
        if (entry.anchors.size() != actual.anchors.size()) changed();
        for (int index = 0; index < entry.anchors.size(); index++) if (entry.anchors.get(index) != actual.anchors.get(index)) changed();
        for (NativeItemVariants binding : entry.variants) binding.verify();
        return object("definition", actual.signature);
    }
    public boolean capture(int index, RecipeRow row) {
        verify();
        return definitions.checked(index, () -> captureRow(index, row));
    }
    private boolean captureRow(int index, RecipeRow row) {
        Entry entry = entries.get(index); Work work = new Work();
        if (entry.ignored || entry.impossible) return row.exclude(object("native", entry.source.getClass().getName(),
                "reason", entry.ignored ? "native_assembly_selector_ignores_this_recipe_kind" : "empty_native_required_input_predicate",
                "hardMode", hard, "signature", entry.signature));
        JsonArray earlier = new JsonArray();
        for (Entry prior : entries) {
            work.step(); if (prior == entry) break;
            definitions.record(prior.index);
            if (!prior.ignored && !prior.impossible) earlier.add(prior.selector(row.facts, work));
        }
        row.record.add("process", object("kind", "galaxyspace-assembly", "earlier", earlier, "repair", entry.repair(row.facts)));
        if (entry.shaped) row.record.add("grid", entry.grid());
        int slot = 0;
        for (int cell = 0; cell < entry.raw.length; cell++) {
            List<ItemStack> group = entry.raw[cell]; if (group == null) continue;
            List<RecipeRow.Ingredient> choices = new ArrayList<>();
            for (ItemStack item : group) {
                work.step(); boolean wildcard = item.getItemDamage() == 32767;
                for (ItemStack sample : entry.samples(item)) {
                    work.step(); choices.add(new RecipeRow.Ingredient(sample, 1, false, rule(wildcard)));
                    if (choices.size() > 65536) throw fault("Assembly display alternatives exceed their budget");
                }
            }
            row.itemInput(slot++, choices, false);
        }
        row.itemOutput(0, entry.output.copy(), 10000);
        row.property("galaxyspace:execution", "Native execution conditions", "Initially empty output slot, stable valid crafting-grid inputs and stable supply tier other than -1, sufficient energy, reset progress counter; registered assembly only when repair-first and earlier registered selectors do not intercept");
        row.property("galaxyspace:duration", "Native completion threshold", "Each running tick increments progress, then compares against Java int 400 / (1 + poweredByTierGC); a zero or negative threshold completes on the first tick; tier -1 causes division by zero");
        row.property("galaxyspace:maxExtract", "Configured maximum energy extraction (gJ/t)", hard ? 90 : 75);
        return true;
    }
    private static final class Work {
        int remaining = 1048576;
        void step() { if (--remaining < 0) throw fault("Assembly proof/candidate work exceeds its budget"); if ((remaining & 1023) == 0) Jobs.checkpoint(); }
    }
    private static final class Entry {
        final Object source; final int index; final boolean shaped, ignored, impossible; final int width, height;
        final List<ItemStack>[] raw; final ItemStack output; final String signature;
        final List<Object> anchors = new ArrayList<>();
        final List<NativeItemVariants> variants = new ArrayList<>();
        final List<NativeCraftingRepair> repairs = new ArrayList<>();
        final Map<ItemStack, ItemStack[]> examples = new IdentityHashMap<>();
        @SuppressWarnings("unchecked") Entry(Object source, int index, Work work, boolean prepare) {
            this.source = source; this.index = index; work.step();
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
            Object nativeInputs = shaped ? ((ShapedRecipes)source).recipeItems : ((ShapelessOreRecipe)source).getInput();
            if (nativeInputs == null) throw new Jobs.Fault("handler_not_ready", "Assembly input storage unavailable");
            if (!shaped && nativeInputs.getClass() != ArrayList.class) throw fault("Unadapted assembly input-list callback");
            if (shaped && nativeInputs.getClass() != ItemStack[].class) throw fault("Unadapted assembly input-array storage");
            anchors.add(nativeInputs);
            Object[] cells = shaped ? ((ItemStack[])nativeInputs).clone() : ((List<?>)nativeInputs).toArray();
            if (cells.length == 0 || cells.length > 9 || shaped && (width < 1 || width > 3 || height < 1 || height > 3 || width * height != cells.length))
                throw fault("Invalid assembly ingredient grid");
            raw = (List<ItemStack>[]) new List<?>[cells.length];
            JsonArray fingerprint = new JsonArray(); int count = 0, occupied = 0; boolean empty = false;
            for (int i = 0; i < cells.length; i++) {
                anchors.add(cells[i]);
                if (cells[i] == null) { if (!shaped) throw fault("Null shapeless assembly ingredient"); fingerprint.add(value(null)); continue; }
                occupied++;
                List<?> candidates = !shaped && cells[i].getClass() == ArrayList.class ? (List<?>) cells[i] : Collections.singletonList(cells[i]);
                raw[i] = new ArrayList<>(); JsonArray values = new JsonArray(); empty |= candidates.isEmpty();
                for (Object candidate : candidates) {
                    work.step();
                    if (++count > 65536 || candidate == null || candidate.getClass() != ItemStack.class || ((ItemStack) candidate).getItem() == null) throw fault("Invalid assembly candidate");
                    ItemStack item = (ItemStack) candidate;
                    if (Items.feather.getDamage(item) < 0) throw fault("Assembly negative metadata requires a dedicated native predicate");
                    if (ItemCallbacks.method(item.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                        throw fault("Assembly input overrides native metadata matching");
                    anchors.add(item); anchors.add(item.getItem()); anchors.add(item.getTagCompound());
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
                    NativeCraftingRepair state = new NativeCraftingRepair(item); repairs.add(state);
                    repair.add(object("item", Item.itemRegistry.getNameForObject(item), "state", state.state));
                }
            }
            ItemStack result = ((IRecipe) source).getRecipeOutput();
            if (result == null || result.getClass() != ItemStack.class || result.getItem() == null || result.stackSize <= 0) throw fault("Assembly lacks a positive fixed native result");
            if (Items.feather.getDamage(result) < 0 || ItemCallbacks.method(result.getItem(), "getDamage", "getDamage", ItemStack.class).getDeclaringClass() != Item.class)
                throw fault("Assembly result metadata requires a dedicated native rule");
            anchors.add(result); anchors.add(result.getItem()); anchors.add(result.getTagCompound());
            // findMatchingRecipe uses getRecipeOutput().copy(), never getCraftingResult(): the copy-NBT flag is inert here.
            output = result.copy();
            signature = CanonicalJson.digest(object("class", kind.getName(), "width", width, "height", height, "inputs", fingerprint,
                    "copyNbtFlag", shaped && (Boolean) field(source, "field_92101_f"), "repair", repair, "output", stackValue(output)));
            if (prepare && !impossible) for (List<ItemStack> group : raw) if (group != null) for (ItemStack template : group) {
                boolean wildcard = template.getItemDamage() == 32767; ItemStack[] concrete;
                if (wildcard) { NativeItemVariants binding = new NativeItemVariants(template); variants.add(binding); concrete = binding.copies(); }
                else concrete = new ItemStack[] {template.copy()};
                for (int n = 0; n < concrete.length; n++) {
                    work.step(); ItemStack sample = concrete[n].copy(); sample.stackSize = 1;
                    sample.setTagCompound(template.getTagCompound() == null ? null : (net.minecraft.nbt.NBTTagCompound)template.getTagCompound().copy());
                    concrete[n] = sample;
                }
                examples.put(template, concrete);
            }
        }
        ItemStack[] samples(ItemStack template) {
            ItemStack[] values = examples.get(template);
            if (values == null || values.length == 0) throw fault("Assembly concrete candidates are unavailable");
            return values;
        }
        JsonArray repair(Facts facts) {
            JsonArray result = new JsonArray();
            for (NativeCraftingRepair state : repairs) {
                ItemStack specimen = null;
                search: for (List<ItemStack> group : raw) if (group != null)
                    for (ItemStack template : group) if (template.getItem() == state.item) { specimen = samples(template)[0]; break search; }
                if (specimen == null) throw fault("Assembly repair item has no concrete specimen");
                result.add(object("item", facts.item(specimen), "enabled", state.enabled, "maximum", state.maximum));
            }
            return result;
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
    private static JsonObject stackValue(ItemStack item) { return object("item", Item.itemRegistry.getNameForObject(item.getItem()), "meta", Items.feather.getDamage(item), "amount", item.stackSize, "nbt", TypedNbt.encode(item.getTagCompound())); }
    private static Jobs.Fault drift() { return new Jobs.Fault("recipe_changed", "GalaxySpace assembly registry, repair eligibility or energy mode changed during capture"); }
    private static void changed() { throw drift(); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
