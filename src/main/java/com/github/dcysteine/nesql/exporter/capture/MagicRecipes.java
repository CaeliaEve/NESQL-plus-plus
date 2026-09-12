package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.common.items.wands.ItemWandCasting;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Pinned Thaumcraft registries supply semantics; copied native NEI recipes supply positions and backgrounds. */
final class MagicRecipes {
    private static final String NEI = "ru.timeconqueror.tcneiadditions.nei.";
    private static final String PLUGIN = "com.djgiannuzz.thaumcraftneiplugin.";
    private static final String EXT = "net.glease.tc4tweak.api.infusionrecipe.";

    private enum Family {
        SHAPED(NEI + "arcaneworkbench.ArcaneCraftingShapedHandler", ShapedArcaneRecipe.class, "ArcaneShapedCachedRecipe"),
        SHAPELESS(NEI + "arcaneworkbench.ArcaneCraftingShapelessHandler", ShapelessArcaneRecipe.class, "ArcaneShapelessCachedRecipe"),
        CRUCIBLE(NEI + "TCNACrucibleRecipeHandler", CrucibleRecipe.class, "CrucibleCachedRecipe"),
        INFUSION(NEI + "TCNAInfusionRecipeHandler", InfusionRecipe.class, "InfusionCachedRecipe");
        final String handler, cached;
        final Class<?> recipe;
        Family(String handler, Class<?> recipe, String cached) { this.handler = handler; this.recipe = recipe; this.cached = cached; }
    }

    private final TemplateRecipeHandler handler;
    private final Family family;
    private final List<Object> recipes = new ArrayList<>();
    private final Constructor<?> constructor;
    private final List<Object> displayedCosts;
    private final Wands wands;
    private final boolean creative;

    static boolean supports(ICraftingHandler handler) { return family(handler) != null; }
    private static Family family(ICraftingHandler handler) {
        for (Family family : Family.values()) if (handler.getClass().getName().equals(family.handler)) return family;
        return null;
    }

    @SuppressWarnings("unchecked")
    MagicRecipes(TemplateRecipeHandler handler) {
        this.handler = handler;
        family = family(handler);
        if (family == null) throw fault("Unrecognized Thaumcraft NEI handler");
        version("tcneiadditions", "1.5.4");
        version("tc4tweak", "1.5.39");
        try {
            Class<?> cached = Class.forName(family.handler + "$" + family.cached);
            constructor = cached.getDeclaredConstructor(handler.getClass(), family.recipe, boolean.class);
            constructor.setAccessible(true);
            displayedCosts = (List<Object>) field(handler, "aspectsAmount");
        } catch (ReflectiveOperationException error) { throw fault(error.toString()); }
        List<IArcaneRecipe> replacements = new ArrayList<>();
        for (Object recipe : ThaumcraftApi.getCraftingRecipes()) {
            if (family.recipe.isInstance(recipe)) recipes.add(recipe);
            else if (Wands.supports(recipe)) { if (family == Family.SHAPELESS) replacements.add((IArcaneRecipe) recipe); }
            else if (family == Family.SHAPED && recipe instanceof IArcaneRecipe && !(recipe instanceof ShapelessArcaneRecipe)) {
                throw new Jobs.Fault("recipe_unsupported", "Arcane recipe requires a specialized adapter: " + recipe.getClass().getName());
            }
        }
        wands = replacements.isEmpty() ? null : new Wands(replacements);
        creative = Wands.creative();
    }

    int size() { return recipes.size() + (wands == null ? 0 : wands.size()); }

    @SuppressWarnings("unchecked")
    boolean capture(int index, RecipeRow row) {
        boolean replacement = index >= recipes.size();
        Object source = replacement ? null : recipes.get(index);
        List<List<Candidate>> inputs = new ArrayList<>();
        List<Integer> positionsToSlots = new ArrayList<>();
        TreeSet<String> research = new TreeSet<>();
        AspectList aspects;
        ItemStack output;
        Object projection;
        Products product = null;
        JsonObject payment = null;
        String kind;
        Integer central = null, instability = null;
        if (replacement) {
            Wands.Result result = wands.capture(index - recipes.size(), row.facts);
            if (result == null) return false;
            inputs = result.inputs; aspects = result.aspects; research = result.research; product = result.product;
            output = product.output; projection = shapeless(inputs, output, aspects); payment = result.payment; kind = "arcane";
            row.property("salisarcana:replacement", "Replace wand part", result.part);
            if (result.fixed) row.property("salisarcana:effect", "Part effect", "Bracelet components are fixed by its item type; replacement only writes part tags.");
        } else if (family == Family.SHAPED) {
            exact(source, ShapedArcaneRecipe.class);
            ShapedArcaneRecipe recipe = (ShapedArcaneRecipe) source;
            aspects = recipe.getAspects(); output = result(recipe.getRecipeOutput()); addResearch(research, recipe.getResearch());
            if (recipe.width < 1 || recipe.height < 1 || recipe.width > 3 || recipe.height > 3
                    || recipe.input.length != recipe.width * recipe.height) throw fault("Invalid arcane grid");
            JsonArray cells = new JsonArray();
            for (Object ingredient : recipe.input) {
                if (ingredient == null) cells.add(value(null));
                else { cells.add(value(inputs.size())); inputs.add(ordinary(ingredient, true)); }
            }
            boolean mirror = (Boolean) field(recipe, "mirrored");
            row.record.add("grid", object("width", recipe.width, "height", recipe.height, "cells", cells, "mirror", mirror));
            // TCNA's native shaped handler traverses columns first; grid facts use rows first.
            for (int x = 0; x < recipe.width; x++) for (int y = 0; y < recipe.height; y++) {
                if (!cells.get(y * recipe.width + x).isJsonNull()) positionsToSlots.add(cells.get(y * recipe.width + x).getAsInt());
            }
            // Native cached constructors can shrink input stacks. Never pass a registered recipe to them.
            ShapedArcaneRecipe copy = new ShapedArcaneRecipe(recipe.getResearch(), output.copy(), costs(aspects), " ");
            copy.width = recipe.width; copy.height = recipe.height; copy.input = new Object[recipe.input.length]; copy.setMirrored(mirror);
            for (int cell = 0; cell < recipe.input.length; cell++) if (!cells.get(cell).isJsonNull()) {
                copy.input[cell] = stacks(inputs.get(cells.get(cell).getAsInt()));
            }
            projection = copy; kind = "arcane";
        } else if (family == Family.SHAPELESS) {
            exact(source, ShapelessArcaneRecipe.class);
            ShapelessArcaneRecipe recipe = (ShapelessArcaneRecipe) source;
            aspects = recipe.getAspects(); output = result(recipe.getRecipeOutput()); addResearch(research, recipe.getResearch());
            for (Object ingredient : recipe.getInput()) inputs.add(ordinary(ingredient, true));
            if (inputs.isEmpty() || inputs.size() > 9) throw fault("Invalid shapeless arcane ingredients");
            projection = shapeless(inputs, output, aspects); kind = "arcane";
        } else if (family == Family.CRUCIBLE) {
            exact(source, CrucibleRecipe.class);
            CrucibleRecipe recipe = (CrucibleRecipe) source;
            aspects = recipe.aspects; output = result(recipe.getRecipeOutput()); addResearch(research, recipe.key);
            inputs.add(ordinary(recipe.catalyst, false));
            projection = new CrucibleRecipe(recipe.key, output.copy(), new ArrayList<>(Arrays.asList(stacks(inputs.get(0)))), costs(aspects));
            kind = "crucible";
        } else {
            if (source.getClass() != InfusionRecipe.class && !source.getClass().getName().equals(EXT + "EnhancedInfusionRecipe")) {
                throw new Jobs.Fault("recipe_unsupported", "Infusion recipe overrides base semantics: " + source.getClass().getName());
            }
            InfusionRecipe recipe = (InfusionRecipe) source;
            aspects = recipe.getAspects(); addResearch(research, recipe.getResearch());
            Object api = invoke(type(EXT + "InfusionRecipeExt"), null, "get", new Class<?>[0]);
            Object enhanced = invoke(type(EXT + "InfusionRecipeExt"), api, "convert", new Class<?>[] {InfusionRecipe.class}, recipe);
            inputs.add(ingredient(invoke(type(EXT + "EnhancedInfusionRecipe"), enhanced, "getCentral", new Class<?>[0])));
            Object components = invoke(type(EXT + "EnhancedInfusionRecipe"), enhanced, "getComponentsExt", new Class<?>[0]);
            if (!(components instanceof List<?>) || ((List<?>) components).size() > 4095) throw fault("Invalid infusion component list");
            for (Object component : (List<?>) components) inputs.add(ingredient(component));
            product = Products.infusion(recipe, Arrays.asList(stacks(inputs.get(0))), row.facts);
            output = product.output;
            ItemStack[] componentsCopy = new ItemStack[inputs.size() - 1];
            for (int part = 1; part < inputs.size(); part++) componentsCopy[part - 1] = inputs.get(part).get(0).item.copy();
            instability = recipe.getInstability(); central = 0;
            projection = new InfusionRecipe(recipe.getResearch(), output.copy(), instability, costs(aspects), inputs.get(0).get(0).item.copy(), componentsCopy);
            kind = "infusion";
        }
        if (positionsToSlots.isEmpty()) for (int slot = 0; slot < inputs.size(); slot++) positionsToSlots.add(slot);
        if (inputs.stream().mapToInt(List::size).sum() > 65536) throw fault("Recipe alternatives exceed their budget");
        if (!replacement && kind.equals("arcane") && output.getItem() instanceof ItemWandCasting) {
            ItemWandCasting wand = (ItemWandCasting) output.getItem();
            if (wand.getRod(output) == null || wand.getCap(output) == null) throw fault("Wand recipe has no rod or cap");
            addResearch(research, wand.getRod(output).getResearch()); addResearch(research, wand.getCap(output).getResearch());
            if (wand.isSceptre(output)) addResearch(research, "SCEPTRE");
        }
        JsonArray links = new JsonArray();
        for (String key : research) links.add(Magic.study(key));
        JsonArray amounts = Aspects.amounts(costs(aspects));
        if (kind.equals("arcane") && amounts.size() == 0 && !creative) return false;
        row.record.add("magic", object("kind", kind, "aspects", amounts, "research", links, "central", central, "instability", instability,
                "payment", payment, "creative", kind.equals("arcane") && creative));
        TemplateRecipeHandler.CachedRecipe nativeRecipe;
        try { nativeRecipe = (TemplateRecipeHandler.CachedRecipe) constructor.newInstance(handler, projection, true); }
        catch (ReflectiveOperationException error) { throw fault(error.toString()); }
        handler.arecipes.clear(); handler.arecipes.add(nativeRecipe);
        displayedCosts.clear();
        if (kind.equals("arcane")) {
            displayedCosts.add(invoke(handler.getClass().getSuperclass(), null, "getAmounts", new Class<?>[] {family.recipe}, projection));
        } else displayedCosts.add(costs(aspects));
        List<PositionedStack> positions = (List<PositionedStack>) field(nativeRecipe, "ingredients");
        int slot = 0;
        for (PositionedStack position : positions) {
            if (position.item != null && position.item.getItem().getClass().getName().equals(PLUGIN + "items.ItemAspect")) {
                AspectList value = (AspectList) invoke(type(PLUGIN + "items.ItemAspect"), null, "getAspects", new Class<?>[] {ItemStack.class}, position.item);
                if (value == null || value.size() != 1) throw fault("Native cost icon has no unique aspect");
                String aspect = Aspects.id(value.getAspects()[0]);
                int cost = -1;
                for (int at = 0; at < amounts.size(); at++) if (amounts.get(at).getAsJsonObject().get("aspect").getAsString().equals(aspect)) cost = at;
                if (cost < 0) throw fault("Native view refers to an undeclared aspect cost");
                row.elements.add(object("kind", "cost", "index", cost, "x", position.relx, "y", position.rely, "width", 16, "height", 16, "z", 2));
            } else {
                if (slot >= inputs.size()) throw fault("Native view contains an unexpected input slot");
                int bound = positionsToSlots.get(slot++);
                List<Candidate> candidates = inputs.get(bound);
                ItemStack[] copies = stacks(candidates);
                row.itemInput(new PositionedStack(copies, position.relx, position.rely, false), bound, 1, false, kind.equals("arcane"),
                        (item, choice) -> candidates.get(choice).rule);
            }
        }
        if (slot != inputs.size()) throw fault("Native view omits registered inputs");
        PositionedStack result = nativeRecipe.getResult();
        if (result == null) throw fault("Native view has no result slot");
        row.itemOutput(result, 0, output, 10000);
        if (product != null) product.attach(row);
        return true;
    }

    private static ShapelessArcaneRecipe shapeless(List<List<Candidate>> inputs, ItemStack output, AspectList aspects) {
        ShapelessArcaneRecipe copy = new ShapelessArcaneRecipe("", output.copy(), costs(aspects));
        for (List<Candidate> candidates : inputs) copy.getInput().add(new ArrayList<>(Arrays.asList(stacks(candidates))));
        return copy;
    }

    static JsonObject tags(ItemStack item, boolean meta) {
        TreeSet<String> keys = new TreeSet<>();
        if (item.hasTagCompound()) for (Object key : item.getTagCompound().func_150296_c()) keys.add((String) key);
        JsonArray names = new JsonArray(); for (String key : keys) names.add(value(key));
        return object("kind", "tags", "meta", meta, "keys", names, "present", new JsonArray(), "absent", new JsonArray());
    }

    private static List<Candidate> ordinary(Object ingredient, boolean tags) {
        List<Candidate> result = new ArrayList<>();
        List<?> values = ingredient instanceof ItemStack ? java.util.Collections.singletonList(ingredient)
                : ingredient instanceof ItemStack[] ? Arrays.asList((ItemStack[]) ingredient)
                : ingredient instanceof List<?> ? (List<?>) ingredient : null;
        if (values == null || values.isEmpty() || values.size() > 65536) throw fault("Unsupported or empty magic ingredient");
        for (Object value : values) {
            if (!(value instanceof ItemStack) || ((ItemStack) value).getItem() == null) throw fault("Magic ingredient contains an invalid stack");
            ItemStack item = ((ItemStack) value).copy(); item.stackSize = 1;
            boolean wildcard = Items.feather.getDamage(item) == OreDictionary.WILDCARD_VALUE;
            result.add(new Candidate(item, tags ? tags(item, wildcard)
                    : object("kind", "wildcard", "meta", wildcard, "nbt", true)));
        }
        return result;
    }

    private static List<Candidate> ingredient(Object ingredient) {
        List<Candidate> result = new ArrayList<>();
        ingredient(ingredient, result, 0);
        if (result.isEmpty() || result.size() > 65536) throw fault("Infusion ingredient has no valid alternatives or exceeds its budget");
        return result;
    }

    private static void ingredient(Object ingredient, List<Candidate> result, int depth) {
        Jobs.checkpoint();
        if (ingredient == null || depth > 16 || result.size() > 65536) throw fault("Infusion ingredient exceeds its budget");
        String name = ingredient.getClass().getName();
        if (name.equals(EXT + "RecipeIngredientDefer")) {
            ingredient(invoke(ingredient.getClass(), ingredient, "get", new Class<?>[0]), result, depth + 1); return;
        }
        if (name.equals(EXT + "RecipeIngredientOr")) {
            Object[] parts = (Object[]) field(ingredient, "or");
            if (parts.length > 4096) throw fault("Infusion ingredient has too many branches");
            for (Object part : parts) ingredient(part, result, depth + 1);
            return;
        }
        if (!Arrays.asList(EXT + "RecipeIngredient$1", EXT + "RecipeIngredient$2", EXT + "RecipeIngredient$3", EXT + "RecipeIngredient$4").contains(name)) {
            throw new Jobs.Fault("recipe_unsupported", "No predicate adapter for infusion ingredient " + name);
        }
        Object values = invoke(type(EXT + "RecipeIngredient"), ingredient, "getRepresentativeStacks", new Class<?>[0]);
        List<Candidate> candidates = ordinary(values, false);
        if (name.equals(EXT + "RecipeIngredient$1") || name.equals(EXT + "RecipeIngredient$2")) {
            String ore = (String) field(ingredient, "val$name");
            boolean exclusive = name.endsWith("$2");
            for (Candidate candidate : candidates) {
                if (exclusive && OreDictionary.getOreIDs(candidate.item).length != 1) continue;
                result.add(new Candidate(candidate.item, object("kind", "ore", "name", ore, "exclusive", exclusive)));
            }
        } else if (name.equals(EXT + "RecipeIngredient$3") || name.equals(EXT + "RecipeIngredient$4")) {
            boolean nbt = (Boolean) field(ingredient, "val$checkNBTTags");
            for (Candidate candidate : candidates) {
                boolean wildcard = Items.feather.getDamage(candidate.item) == OreDictionary.WILDCARD_VALUE;
                result.add(new Candidate(candidate.item, nbt && !wildcard ? object("kind", "exact")
                        : object("kind", "wildcard", "meta", wildcard, "nbt", !nbt)));
            }
        }
    }

    private static ItemStack[] stacks(List<Candidate> values) { return values.stream().map(value -> value.item.copy()).toArray(ItemStack[]::new); }
    private static ItemStack result(ItemStack item) {
        if (item == null || item.getItem() == null || item.stackSize < 1) throw fault("Magic recipe has no concrete output");
        return item.copy();
    }
    private static AspectList costs(AspectList aspects) { if (aspects == null) throw fault("Magic recipe has no cost list"); return aspects.copy(); }
    private static void addResearch(TreeSet<String> keys, String key) { if (key != null && !key.isEmpty()) keys.add(key); }
    private static void exact(Object value, Class<?> type) {
        if (value.getClass() != type) throw new Jobs.Fault("recipe_unsupported", "Recipe overrides base semantics: " + value.getClass().getName());
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("magic_recipe", message); }
    static final class Candidate {
        final ItemStack item;
        final JsonObject rule;
        Candidate(ItemStack item, JsonObject rule) { this.item = item; this.rule = rule; }
    }
}
