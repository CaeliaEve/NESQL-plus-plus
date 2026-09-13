package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import thaumcraft.common.lib.utils.InventoryUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import static com.github.dcysteine.nesql.exporter.source.Json.value;
import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Declared research patterns and their observed matches are distinct facts. */
final class Clues {
    private final Map<Item, List<ItemStack>> items = new IdentityHashMap<>();

    Clues(Iterable<ItemStack> catalog) {
        for (ItemStack stack : catalog) {
            Jobs.checkpoint();
            if (stack == null || stack.getItem() == null) throw new Jobs.Fault("research_trigger", "The clue catalog contains an empty item");
            if (concrete(stack)) items.computeIfAbsent(stack.getItem(), key -> new ArrayList<>()).add(stack);
        }
    }

    Cursor open(ItemStack[] triggers) { return new Cursor(triggers); }

    final class Cursor {
        private final ItemStack[] triggers;
        private final Set<String> matches = new TreeSet<>();
        private final JsonArray records = new JsonArray();
        private List<ItemStack> candidates;
        private JsonObject record;
        private int trigger, next, total;

        Cursor(ItemStack[] triggers) {
            if (triggers != null && triggers.length > 4096) throw new Jobs.Fault("research_trigger", "Research has too many item triggers");
            this.triggers = triggers == null ? new ItemStack[0] : triggers.clone();
        }

        /** Bound native matching and fact capture to sixteen matches or two milliseconds per tick. */
        boolean capture(Function<ItemStack, String> capture) {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(2);
            int captured = 0, checked = 0;
            while (trigger < triggers.length) {
                Jobs.checkpoint();
                if (checked > 0 && (captured >= 16 || System.nanoTime() >= deadline)) return false;
                ItemStack pattern = triggers[trigger];
                try {
                    if (candidates == null) {
                        if (pattern == null || pattern.getItem() == null) throw new Jobs.Fault("research_trigger", "Empty research item trigger");
                        String registry = Item.itemRegistry.getNameForObject(pattern.getItem());
                        if (registry == null || Item.itemRegistry.getObject(registry) != pattern.getItem()) {
                            throw new Jobs.Fault("research_trigger", "Research trigger uses an unregistered item");
                        }
                        int meta = Items.feather.getDamage(pattern);
                        com.google.gson.JsonElement nbt = TypedNbt.encode(pattern.getTagCompound());
                        Identity.item(registry, meta, nbt); // Validate identity text without creating a concrete item fact.
                        int ore = OreDictionary.getOreID(pattern.copy());
                        record = object("registry", registry, "meta", meta, "nbt", nbt,
                                "ore", ore == -1 ? null : OreDictionary.getOreName(ore), "matches", new JsonArray());
                        candidates = candidates(pattern, ore);
                    }
                    while (next < candidates.size()) {
                        Jobs.checkpoint();
                        if (checked > 0 && (captured >= 16 || System.nanoTime() >= deadline)) return false;
                        ItemStack candidate = candidates.get(next++);
                        checked++;
                        // This is the same call made by ResearchManager.createClue. Copies keep
                        // item hooks away from research-owned, ore-dictionary and NEI stacks.
                        if (!InventoryUtils.areItemStacksEqual(pattern.copy(), candidate.copy(), true, true, false)) continue;
                        String id = capture.apply(candidate.copy());
                        if (matches.add(id) && ++total > 4096) throw new Jobs.Fault("research_trigger", "Resolved research clues exceed their budget");
                        captured++;
                    }
                    for (String id : matches) record.getAsJsonArray("matches").add(value(id));
                    records.add(record);
                    trigger++;
                    candidates = null; next = 0; matches.clear();
                    checked++; // Empty patterns also consume a scheduling step.
                } catch (java.util.concurrent.CancellationException error) { throw error; }
                catch (RuntimeException error) {
                    Jobs.Fault fault = new Jobs.Fault(error instanceof Jobs.Fault ? ((Jobs.Fault) error).code : "research_trigger",
                            "Item trigger " + trigger + " (" + describe(pattern) + "): " + error);
                    fault.initCause(error);
                    throw fault;
                }
            }
            return true;
        }

        JsonArray records() {
            if (trigger != triggers.length) throw new IllegalStateException("Research clues are still being resolved");
            return records;
        }
    }

    private List<ItemStack> candidates(ItemStack trigger, int ore) {
        List<ItemStack> candidates = new ArrayList<>();
        Set<Item> types = new LinkedHashSet<>();
        types.add(trigger.getItem());
        // Native matching uses the FIRST ore id of the trigger, then falls back
        // to item/meta/NBT comparison. Taking every ore id would widen the rule.
        if (ore != -1) for (ItemStack entry : OreDictionary.getOres(ore)) {
            Jobs.checkpoint();
            if (entry == null || entry.getItem() == null) throw new Jobs.Fault("research_trigger", "The trigger's ore entry is empty");
            types.add(entry.getItem());
        }
        for (Item type : types) {
            Jobs.checkpoint();
            List<ItemStack> known = items.getOrDefault(type, Collections.emptyList());
            if (candidates.size() + (long) known.size() > 262144) throw new Jobs.Fault("research_trigger", "Research clue candidates exceed their budget");
            candidates.addAll(known);
        }
        return candidates;
    }

    private static boolean concrete(ItemStack stack) { return Items.feather.getDamage(stack) != OreDictionary.WILDCARD_VALUE; }

    private static String describe(ItemStack stack) {
        return stack == null || stack.getItem() == null ? "empty" : Item.itemRegistry.getNameForObject(stack.getItem())
                + "; meta=" + Items.feather.getDamage(stack);
    }
}
