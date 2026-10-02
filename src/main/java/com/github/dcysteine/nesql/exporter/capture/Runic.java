package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.ItemList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import thaumcraft.api.IRunicArmor;
import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.crafting.InfusionRecipe;
import net.minecraftforge.oredict.OreDictionary;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import static com.github.dcysteine.nesql.exporter.source.Json.*;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.InfusionRunicAugmentRecipe;
import thaumcraft.common.lib.events.EventHandlerRunic;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import java.util.*;

/** One symbolic recipe per registered IRunicArmor, not an enumeration of possible NBT. */
final class Runic {
    final InfusionRunicAugmentRecipe recipe;
    final ItemStack center;
    private Runic(InfusionRunicAugmentRecipe recipe, ItemStack center) { this.recipe = recipe; this.center = center; }

    static List<Runic> expand(InfusionRunicAugmentRecipe recipe) {
        if (recipe.getClass() != InfusionRunicAugmentRecipe.class)
            throw new Jobs.Fault("recipe_unsupported", "Runic recipe overrides native semantics");
        Map<Item, ItemStack> examples = new IdentityHashMap<>();
        for (ItemStack stack : ItemList.items) if (stack != null && stack.getItem() instanceof IRunicArmor)
            examples.putIfAbsent(stack.getItem(), stack);
        List<Item> items = new ArrayList<>();
        for (Object item : Item.itemRegistry) if (item instanceof IRunicArmor) items.add((Item) item);
        items.sort(Comparator.comparing(Item.itemRegistry::getNameForObject));
        List<Runic> result = new ArrayList<>();
        for (Item item : items) {
            Jobs.checkpoint();
            ItemStack center = examples.containsKey(item) ? examples.get(item).copy() : new ItemStack(item);
            center.stackSize = 1;
            result.add(new Runic(recipe, center));
        }
        return result;
    }

    static Sample sample(InfusionRunicAugmentRecipe recipe, ItemStack input) {
        if (input == null || !(input.getItem() instanceof IRunicArmor))
            throw new Jobs.Fault("runic_input", "Runic augmentation requires IRunicArmor");
        ItemStack copy = input.copy();
        // Separate copies prevent a mod's charge provider from mutating another observation.
        int charge = EventHandlerRunic.getFinalCharge(copy.copy());
        return new Sample(charge, recipe.getInstability(copy.copy()), recipe.getAspects(copy.copy()),
                (ItemStack) recipe.getRecipeOutput(copy.copy()));
    }

    static List<MagicRecipes.Candidate> component(ItemStack template, Facts facts) {
        JsonArray ores = new JsonArray();
        List<ItemStack> pool = new ArrayList<>(); pool.add(template.copy());
        String[] names = OreDictionary.getOreNames(); Arrays.sort(names);
        for (String name : names) {
            Jobs.checkpoint();
            List<ItemStack> values = OreDictionary.getOres(name);
            if (!ThaumcraftApiHelper.containsMatch(false, new ItemStack[]{template.copy()}, values.toArray(new ItemStack[0]))) continue;
            ores.add(value(name)); pool.addAll(values);
        }
        // Expand wildcard ore samples with observed concrete metadata, without inventing subtypes.
        Set<Item> types = Collections.newSetFromMap(new IdentityHashMap<Item, Boolean>());
        for (ItemStack item : pool) if (item != null) types.add(item.getItem());
        for (ItemStack item : ItemList.items) if (item != null && types.contains(item.getItem())) pool.add(item);
        JsonObject rule = object("kind", "infusion", "template", facts.item(template), "ores", ores);
        Map<String, MagicRecipes.Candidate> choices = new TreeMap<>();
        for (ItemStack item : pool) {
            Jobs.checkpoint();
            if (item == null || item.getItem() == null || item.getItemDamage() == OreDictionary.WILDCARD_VALUE) continue;
            ItemStack copy = item.copy(); copy.stackSize = 1;
            if (InfusionRecipe.areItemStacksEqual(copy, template.copy(), true))
                choices.putIfAbsent(facts.item(copy), new MagicRecipes.Candidate(copy, rule));
        }
        if (choices.isEmpty()) throw new Jobs.Fault("runic_input", "No native runic component samples");
        return new ArrayList<>(choices.values());
    }

    static void finish(RecipeRow row, List<List<MagicRecipes.Candidate>> inputs, Sample sample) {
        for (int slot = 1; slot <= 2; slot++) {
            JsonArray choices = row.inputs.get(slot).getAsJsonObject().getAsJsonArray("choices");
            for (int at = 0; at < choices.size(); at++) {
                JsonObject choice = choices.get(at).getAsJsonObject();
                if (slot == 2) {
                    choice.addProperty("amount", Long.toString(sample.pedestals));
                    choice.add("consume", object("kind", "pedestals"));
                }
                // TileInfusionMatrix replaces each used pedestal with the native container.
                ItemStack input = inputs.get(slot).get(at).item.copy();
                ItemStack returned = input.getItem().getContainerItem(input);
                if (returned != null) {
                    if (returned.getItem() == null || returned.stackSize <= 0)
                        throw new Jobs.Fault("runic_input", "Invalid native pedestal container");
                    choice.getAsJsonArray("returns").add(object("kind", "item", "id", row.facts.item(returned.copy()),
                            "amount", Integer.toString(returned.stackSize)));
                }
            }
        }
    }

    static final class Sample {
        final int charge, instability;
        final long pedestals;
        final AspectList aspects;
        final ItemStack output;
        Sample(int charge, int instability, AspectList aspects, ItemStack output) {
            this.charge = charge; this.instability = instability; this.aspects = aspects; this.output = output;
            pedestals = 1L + Math.max(0, charge);
        }
    }
}
