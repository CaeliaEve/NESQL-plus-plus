package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.api.wands.StaffRod;
import thaumcraft.api.wands.WandCap;
import thaumcraft.api.wands.WandRod;
import thaumcraft.common.config.ConfigItems;
import thaumcraft.common.items.wands.ItemWandCasting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Salis replacements over the final component registries. One combination per
 * cursor step; only isolated stacks/inventories enter native result/cost methods. */
final class Wands {
    private static final String ROOT = "dev.rndmorris.salisarcana.";
    private static final String CORE = ROOT + "common.recipes.ReplaceWandCoreRecipe";
    private static final String CAPS = ROOT + "common.recipes.ReplaceWandCapsRecipe";
    private static final String BRACELET = "tb.common.item.ItemCastingBracelet";
    private static final String DISPOSABLE = "com.kentington.thaumichorizons.common.items.ItemWandCastingDisposable";
    private final List<WandRod> rods;
    private final List<WandCap> caps;
    private final List<ItemWandCasting> items = new ArrayList<>();
    private final Map<String, State> fixed = new TreeMap<>();
    private final List<State> fixedStates;
    private final List<Part> parts = new ArrayList<>();
    private final Map<String, List<ItemStack>> known = new TreeMap<>();
    private final List<Aspect> primal = new ArrayList<>(Aspect.getPrimalAspects());
    private final boolean preserve, single, enforce, materials, creative;
    private final int size;
    private final int variableStates;
    private int previous = -1;
    private List<MagicRecipes.Candidate> centers;

    static boolean supports(Object recipe) {
        return recipe != null && (recipe.getClass().getName().equals(CORE) || recipe.getClass().getName().equals(CAPS));
    }

    static boolean creative() {
        if (!cpw.mods.fml.common.Loader.isModLoaded("salisarcana")) return false;
        version("salisarcana", "1.1.33-GTNH");
        return enabled(field(type(ROOT + "config.SalisConfig"), null, "features"), "infiniteCreativeVis");
    }

    Wands(List<IArcaneRecipe> recipes) {
        version("salisarcana", "1.1.33-GTNH"); version("gtnhtcwands", "1.4.6");
        Object features = field(type(ROOT + "config.SalisConfig"), null, "features");
        preserve = enabled(features, "preserveWandVis"); single = enabled(features, "allowSingleWandReplacement");
        creative = enabled(features, "infiniteCreativeVis");
        enforce = enabled(features, "enforceWandCoreTypes");
        Object compat = field(field(type(ROOT + "config.SalisConfig"), null, "modCompat"), "gtnhWands");
        materials = enabled(compat, "coreSwapMaterials");
        // Clearing uses Salis' six-aspect list, while preserving uses TC's primal registry.
        // Require their agreement instead of silently losing a mod-added primal charge.
        AspectList cleared = (AspectList) invoke(type(ROOT + "lib.AspectHelper"), null, "primalList", new Class<?>[] {int.class}, 0);
        if (primal.isEmpty() || primal.size() != cleared.size() || !primal.containsAll(Arrays.asList(cleared.getAspects()))) {
            throw fault("Salis and Thaumcraft disagree on primal charge keys");
        }
        primal.sort(Comparator.comparing(Aspect::getTag));
        rods = new ArrayList<>(new TreeMap<>(WandRod.rods).values());
        caps = new ArrayList<>(new TreeMap<>(WandCap.caps).values());
        if (rods.isEmpty() || caps.isEmpty() || rods.size() > 256 || caps.size() > 256) throw fault("Invalid wand component registry size");
        for (Object item : Item.itemRegistry) if (item instanceof ItemWandCasting) {
            ItemWandCasting wand = (ItemWandCasting) item;
            if (item.getClass().getName().equals(BRACELET)) {
                version("thaumicbases", "1.8.13");
                String[] names = (String[]) field(item.getClass(), null, "names");
                if (names.length != 13) throw fault("Unexpected casting bracelet types");
                for (int meta = 0; meta < names.length; meta++) fixed(wand, meta);
            } else {
                if (item.getClass().getName().equals(DISPOSABLE)) version("ThaumicHorizons", "1.7.9");
                else if (item.getClass() != ItemWandCasting.class) throw fault("Wand subclass requires its own state adapter: " + item.getClass().getName());
                items.add(wand);
            }
        }
        items.sort(Comparator.comparing(item -> Item.itemRegistry.getNameForObject(item)));
        for (IArcaneRecipe recipe : recipes) parts.addAll(parts(recipe));
        if (parts.isEmpty()) throw fault("Registered replacements have no initialized component mappings");
        parts.sort(Comparator.comparing((Part part) -> part.core ? "rod" : "cap").thenComparing(part -> part.name));
        for (ItemStack stack : ItemList.items) remember(stack);
        for (Object recipe : ThaumcraftApi.getCraftingRecipes()) {
            if (recipe.getClass() == ShapedArcaneRecipe.class || recipe.getClass() == ShapelessArcaneRecipe.class) {
                remember(((IArcaneRecipe) recipe).getRecipeOutput());
            }
        }
        fixedStates = new ArrayList<>(fixed.values());
        long variable = (long) rods.size() * caps.size() * 2 * items.size();
        if (variable > 2000000) throw fault("Wand states exceed their budget");
        variableStates = (int) variable;
        long count = ((long) variableStates + fixedStates.size()) * parts.size();
        if (count > 2000000 || count == 0) throw fault("Invalid wand combination budget");
        size = (int) count;
    }

    int size() { return size; }

    Result capture(int index, Facts facts) {
        Jobs.checkpoint();
        Part part = parts.get(index % parts.size());
        int group = index / parts.size();
        State state = state(group);
        ItemWandCasting wand = state.item;
        WandRod rod = state.rod; WandCap cap = state.cap; boolean sceptre = state.sceptre;
        if (part.value == (part.core ? rod : cap)) return null;
        if (part.core && enforce && state.staff != (part.value instanceof StaffRod)) return null;
        if (previous != group) { centers = centers(state); previous = group; }
        List<List<MagicRecipes.Candidate>> inputs = new ArrayList<>();
        inputs.add(centers); inputs.add(part.choices);
        ItemStack center = centers.get(0).item;
        if (part.core && materials) {
            if (!materials(inputs, center, (WandRod) part.value, sceptre)) return null;
        } else if (!part.core) {
            for (int slot = 1; slot < (sceptre ? 3 : 2); slot++) inputs.add(part.choices);
        }
        IInventory inventory = inventory(inputs, center);
        ItemStack nativeOutput = part.recipe.getCraftingResult(inventory);
        AspectList aspects = part.recipe.getAspects(inventory(inputs, center));
        // Six zeroes can be paid. An empty list needs Salis' creative waiver.
        if (nativeOutput == null || aspects == null || (aspects.size() == 0 && !creative)) return null;
        int capacity = wand.getMaxVis(nativeOutput);
        if (capacity < 0) throw fault("Wand capacity overflows its native integer");
        if (wand.getClass().getName().equals(DISPOSABLE) && capacity != 25000) throw fault("Disposable wand recipe observed outside its crafting context");

        ItemStack changes = new ItemStack(wand, 1, 0);
        if (part.core) wand.setRod(changes, (WandRod) part.value);
        else wand.setCap(changes, (WandCap) part.value);
        NBTTagCompound set = (NBTTagCompound) changes.getTagCompound().copy();
        Map<String, Integer> limits = new TreeMap<>();
        for (Aspect aspect : primal) {
            if (!preserve) set.setInteger(aspect.getTag(), 0);
            else if (part.core) limits.put(aspect.getTag(), capacity);
        }
        JsonObject limitValues = new JsonObject(); limits.forEach(limitValues::addProperty);
        JsonObject action = object("kind", "patch", "set", TypedNbt.encode(set).getAsJsonObject().get("value"), "limits", limitValues);
        List<ItemStack> examples = new ArrayList<>(); for (MagicRecipes.Candidate candidate : centers) examples.add(candidate.item);
        Products product = Products.observe(examples, action, input -> {
            AspectList cost = part.recipe.getAspects(inventory(inputs, input));
            if (!sameCost(aspects, cost)) throw fault("Wand cost depends on undeclared input data");
            ItemStack output = part.recipe.getCraftingResult(inventory(inputs, input));
            ItemStack expected = Products.patch(input, set, limits);
            if (output == null || !ItemStack.areItemStacksEqual(expected, output)) throw fault("Native wand result differs from its declared patch");
            return output;
        }, facts);
        TreeSet<String> research = new TreeSet<>();
        addResearch(research, part.recipe.getResearch());
        addResearch(research, part.core ? ((WandRod) part.value).getResearch() : ((WandCap) part.value).getResearch());
        JsonObject payment = null;
        if (single && !state.staff) {
            JsonObject charges = new JsonObject(); for (Aspect aspect : primal) charges.addProperty(Aspects.id(aspect), aspect.getTag());
            payment = object("input", 0, "charges", charges, "capacity", capacity, "preserve", preserve);
        }
        return new Result(inputs, aspects.copy(), research, product, payment, part.core ? "rod" : "cap", state.meta != null);
    }

    private State state(int index) {
        if (index >= variableStates) return fixedStates.get(index - variableStates);
        int rest = index;
        boolean sceptre = (rest & 1) != 0; rest /= 2;
        WandCap cap = caps.get(rest % caps.size()); rest /= caps.size();
        WandRod rod = rods.get(rest % rods.size()); rest /= rods.size();
        return new State(items.get(rest), rod, cap, rod instanceof StaffRod, sceptre, null);
    }

    private List<MagicRecipes.Candidate> centers(State state) {
        ItemWandCasting wand = state.item; WandRod rod = state.rod; WandCap cap = state.cap; boolean sceptre = state.sceptre;
        Map<String, ItemStack> values = new LinkedHashMap<>();
        for (ItemStack sample : known.getOrDefault(state.key(), Collections.emptyList())) put(values, sample);
        ItemStack base = new ItemStack(wand, 1, state.meta == null ? 0 : state.meta);
        if (state.meta == null) {
            wand.setRod(base, rod); wand.setCap(base, cap);
            if (sceptre) base.getTagCompound().setBoolean("sceptre", true);
        }
        put(values, base);
        // Untagged wood/iron defaults are a different presence predicate from explicit tags.
        for (int omit = 1; state.meta == null && omit < 4; omit++) {
            if (((omit & 1) != 0 && rod != ConfigItems.WAND_ROD_WOOD) || ((omit & 2) != 0 && cap != ConfigItems.WAND_CAP_IRON)) continue;
            ItemStack implicit = base.copy();
            if ((omit & 1) != 0) implicit.getTagCompound().removeTag("rod");
            if ((omit & 2) != 0) implicit.getTagCompound().removeTag("cap");
            if (implicit.getTagCompound().hasNoTags()) implicit.setTagCompound(null);
            put(values, implicit);
        }
        if (values.size() > 128) throw fault("Too many representative states for one wand configuration");
        List<MagicRecipes.Candidate> result = new ArrayList<>();
        for (ItemStack value : values.values()) {
            if (state.meta != null) { result.add(candidate(value, false)); continue; }
            JsonArray keys = new JsonArray(), present = new JsonArray(), absent = new JsonArray();
            for (String name : Arrays.asList("cap", "rod")) {
                if (value.hasTagCompound() && value.getTagCompound().hasKey(name)) keys.add(value(name));
                else absent.add(value(name));
            }
            (sceptre ? present : absent).add(value("sceptre"));
            result.add(new MagicRecipes.Candidate(value, object("kind", "tags", "meta", true, "keys", keys, "present", present, "absent", absent)));
        }
        return result;
    }

    private boolean materials(List<List<MagicRecipes.Candidate>> inputs, ItemStack center, WandRod target, boolean sceptre) {
        Class<?> form = type(ROOT + "lib.WandType");
        Object shape = invoke(form, null, "getWandType", new Class<?>[] {ItemStack.class}, center.copy());
        Object wrapper = invoke(type(ROOT + "common.compat.GTNHTCWandsCompat"), null, "getWandWrapper", new Class<?>[] {WandRod.class, form}, target, shape);
        if (wrapper == null) return false;
        Object details = invoke(wrapper.getClass(), wrapper, "getDetails", new Class<?>[0]);
        String ore = (String) invoke(details.getClass(), details, "getScrew", new Class<?>[0]);
        ItemStack conductor = (ItemStack) invoke(details.getClass(), details, "getConductor", new Class<?>[0]);
        if (conductor == null || !OreDictionary.doesOreNameExist(ore)) return false;
        if (conductor.getItem() instanceof ItemWandCasting || mappedRod(conductor) != null) return false;
        List<MagicRecipes.Candidate> screws = new ArrayList<>();
        for (ItemStack screw : OreDictionary.getOres(ore)) {
            if (screw == null || screw.getItem() == null || screw.getItem() instanceof ItemWandCasting) continue;
            boolean wildcard = Items.feather.getDamage(screw) == OreDictionary.WILDCARD_VALUE;
            if (wildcard && (screw.getItem() == conductor.getItem() || mappedRodItem(screw.getItem()))) {
                throw fault("Wildcard screw ingredient overlaps a conductor or wand rod: " + ore);
            }
            if (mappedRod(screw) != null || screw.isItemEqual(conductor)) continue;
            // Enumerated item/metadata alternatives preserve OreDictionary semantics
            // without accidentally accepting an excluded conductor/rod through the ore name.
            screws.add(candidate(screw, wildcard));
        }
        if (screws.isEmpty()) return false;
        for (int slot = 0; slot < (sceptre ? 2 : 4); slot++) inputs.add(screws);
        List<MagicRecipes.Candidate> conductors = Collections.singletonList(candidate(conductor, false));
        inputs.add(conductors); inputs.add(conductors);
        return true;
    }

    private List<Part> parts(IArcaneRecipe recipe) {
        boolean core = recipe.getClass().getName().equals(CORE);
        Map<?, ?> mapping = (Map<?, ?>) field(type(ROOT + "lib.WandHelper"), null, core ? "WAND_RODS" : "WAND_CAPS");
        List<Object> diagnostic = Arrays.asList(field(type(ROOT + "lib.WandHelper"), null, "CAP_UNKNOWN"),
                field(type(ROOT + "lib.WandHelper"), null, "ROD_UNKNOWN"), field(type(ROOT + "lib.WandHelper"), null, "STAFF_UNKNOWN"));
        Map<String, Part> parts = new TreeMap<>();
        for (Map.Entry<?, ?> item : mapping.entrySet()) {
            if (!(item.getKey() instanceof Item) || item.getKey() instanceof ItemWandCasting) throw fault("Wand component mapping overlaps a wand item");
            for (Map.Entry<?, ?> variant : ((Map<?, ?>) item.getValue()).entrySet()) {
                Object component = variant.getValue();
                // Salis maps these diagnostic sentinels to bedrock. They have no
                // obtainable component research and are not replacement materials.
                // Keep registered sentinel states as possible OLD components.
                if (diagnostic.contains(component)) continue;
                String name = core ? ((WandRod) component).getTag() : ((WandCap) component).getTag();
                Part part = parts.computeIfAbsent(name, ignored -> new Part(recipe, core, component, name));
                if (part.value != component) throw fault("Distinct mapped wand components share a tag: " + name);
                part.choices.add(candidate(new ItemStack((Item) item.getKey(), 1, (Integer) variant.getKey()), false));
            }
        }
        for (Part part : parts.values()) part.choices.sort(Comparator.comparing(candidate -> identity(candidate.item)));
        return new ArrayList<>(parts.values());
    }

    private void remember(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemWandCasting)) return;
        ItemWandCasting wand = (ItemWandCasting) stack.getItem();
        WandRod rod = wand.getRod(stack); WandCap cap = wand.getCap(stack);
        boolean bracelet = wand.getClass().getName().equals(BRACELET);
        if (!bracelet && (!rods.contains(rod) || !caps.contains(cap))) return;
        State state = bracelet ? fixed(wand, Items.feather.getDamage(stack)) : new State(wand, rod, cap, wand.isStaff(stack), wand.isSceptre(stack), null);
        List<ItemStack> samples = known.computeIfAbsent(state.key(), ignored -> new ArrayList<>());
        if (samples.size() >= 1024) throw fault("Wand examples exceed their budget");
        ItemStack copy = stack.copy(); copy.stackSize = 1; samples.add(copy);
    }
    private State fixed(ItemWandCasting wand, int meta) {
        if (meta < 0) throw fault("Invalid casting bracelet metadata");
        ItemStack sample = new ItemStack(wand, 1, meta);
        State state = new State(wand, wand.getRod(sample), wand.getCap(sample), wand.isStaff(sample), wand.isSceptre(sample), meta);
        if (fixed.size() >= 4096 && !fixed.containsKey(state.key())) throw fault("Casting bracelet states exceed their budget");
        fixed.putIfAbsent(state.key(), state); return fixed.get(state.key());
    }
    private static String identity(ItemStack item) {
        return Identity.item(Item.itemRegistry.getNameForObject(item), Items.feather.getDamage(item), TypedNbt.encode(item.getTagCompound()));
    }
    private static void put(Map<String, ItemStack> values, ItemStack item) { values.putIfAbsent(identity(item), item.copy()); }
    private static MagicRecipes.Candidate candidate(ItemStack item, boolean meta) {
        ItemStack copy = item.copy(); copy.stackSize = 1;
        return new MagicRecipes.Candidate(copy, object("kind", "wildcard", "meta", meta, "nbt", true));
    }
    private static IInventory inventory(List<List<MagicRecipes.Candidate>> inputs, ItemStack center) {
        InventoryBasic inventory = new InventoryBasic("NEI wand replacement", false, 11);
        inventory.setInventorySlotContents(0, center.copy());
        for (int slot = 1; slot < inputs.size(); slot++) inventory.setInventorySlotContents(slot, inputs.get(slot).get(0).item.copy());
        return inventory;
    }
    private static boolean sameCost(AspectList left, AspectList right) {
        if (right == null || left.size() != right.size()) return false;
        for (Aspect aspect : left.getAspects()) if (left.getAmount(aspect) != right.getAmount(aspect)) return false;
        return true;
    }
    private static boolean enabled(Object group, String name) {
        Object setting = field(group, name); return (Boolean) invoke(setting.getClass(), setting, "isEnabled", new Class<?>[0]);
    }
    private static Object mappedRod(ItemStack item) {
        return invoke(type(ROOT + "lib.WandHelper"), null, "getWandRodFromItem", new Class<?>[] {ItemStack.class}, item.copy());
    }
    private static boolean mappedRodItem(Item item) {
        return ((Map<?, ?>) field(type(ROOT + "lib.WandHelper"), null, "WAND_RODS")).containsKey(item);
    }
    private static void addResearch(TreeSet<String> keys, String key) { if (key != null && !key.isEmpty()) keys.add(key); }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }

    private static final class Part {
        final IArcaneRecipe recipe;
        final boolean core;
        final Object value;
        final String name;
        final List<MagicRecipes.Candidate> choices = new ArrayList<>();
        Part(IArcaneRecipe recipe, boolean core, Object value, String name) { this.recipe = recipe; this.core = core; this.value = value; this.name = name; }
    }
    private static final class State {
        final ItemWandCasting item;
        final WandRod rod;
        final WandCap cap;
        final boolean staff, sceptre;
        final Integer meta;
        State(ItemWandCasting item, WandRod rod, WandCap cap, boolean staff, boolean sceptre, Integer meta) {
            if (rod == null || cap == null) throw fault("Wand state has no effective components");
            this.item = item; this.rod = rod; this.cap = cap; this.staff = staff; this.sceptre = sceptre; this.meta = meta;
        }
        String key() { return Item.itemRegistry.getNameForObject(item) + "\n" + rod.getTag() + "\n" + cap.getTag() + "\n" + sceptre + "\n" + meta; }
    }
    static final class Result {
        final List<List<MagicRecipes.Candidate>> inputs;
        final AspectList aspects;
        final TreeSet<String> research;
        final Products product;
        final JsonObject payment;
        final String part;
        final boolean fixed;
        Result(List<List<MagicRecipes.Candidate>> inputs, AspectList aspects, TreeSet<String> research, Products product, JsonObject payment, String part, boolean fixed) {
            this.inputs = inputs; this.aspects = aspects; this.research = research; this.product = product; this.payment = payment; this.part = part; this.fixed = fixed;
        }
    }
}
