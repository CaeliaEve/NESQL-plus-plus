package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.PositionedStack;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.Chance;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.HashSet;
import java.util.Set;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Builds one recipe from adapter-owned game semantics and positions. */
final class RecipeRow {
    final Facts facts;
    final JsonObject record;
    final JsonArray inputs = new JsonArray(), outputs = new JsonArray(), elements = new JsonArray();
    final JsonObject properties = new JsonObject();

    RecipeRow(Facts facts, JsonObject origin, String category, int order) {
        this.facts = facts;
        record = object("source", origin, "category", category, "inputs", inputs, "outputs", outputs,
                "duration", null, "energy", null, "properties", properties, "grid", null, "magic", null, "view", null, "order", order);
    }

    void itemInput(PositionedStack display, int slot, long amount, boolean keep, boolean crafting, JsonObject rule) {
        itemInput(display, slot, amount, keep, crafting, (item, index) -> rule);
    }

    void itemInput(PositionedStack display, int slot, long amount, boolean keep, boolean crafting,
                   java.util.function.BiFunction<ItemStack, Integer, JsonObject> rule) {
        if (display.items == null || display.items.length == 0) throw new Jobs.Fault("empty_ingredient", "Recipe ingredient has no choices");
        JsonArray choices = new JsonArray();
        Set<String> seen = new HashSet<>();
        int index = 0;
        for (ItemStack item : display.items) {
            String id = facts.item(item);
            JsonObject matching = rule.apply(item, index++);
            if (!seen.add(id + CanonicalJson.digest(matching))) continue;
            JsonObject consumption = object("kind", keep ? "keep" : "consume");
            JsonArray returns = new JsonArray();
            if (crafting && item.getItem().hasContainerItem(item)) {
                ItemStack original = item.copy(); original.stackSize = 1;
                ItemStack returned = original.getItem().getContainerItem(original.copy());
                if (returned != null && returned.getItem() != null && returned.stackSize > 0) {
                    if (returned.getItem() == original.getItem() && ItemStack.areItemStackTagsEqual(original, returned)) {
                        int damage = Items.feather.getDamage(returned) - Items.feather.getDamage(original);
                        if (damage > 0 && original.isItemStackDamageable() && returned.stackSize == 1) consumption = object("kind", "damage", "points", damage);
                        else if (damage == 0 && returned.stackSize == 1) consumption = object("kind", "keep");
                        else returns.add(object("kind", "item", "id", facts.item(returned), "amount", Integer.toString(returned.stackSize)));
                    } else returns.add(object("kind", "item", "id", facts.item(returned), "amount", Integer.toString(returned.stackSize)));
                }
            }
            choices.add(object("id", id, "amount", positive(amount), "consume", consumption, "returns", returns, "rule", matching));
        }
        inputs.add(object("slot", slot, "kind", "item", "choices", choices));
        slot(display, "input", "item", slot);
    }

    void fluidInput(PositionedStack display, int slot, FluidStack fluid) {
        JsonArray choices = new JsonArray();
        choices.add(object("id", facts.fluid(fluid), "amount", positive(Math.max(1, fluid.amount)),
                "consume", object("kind", fluid.amount == 0 ? "keep" : "consume"), "returns", new JsonArray(), "rule", object("kind", "exact")));
        inputs.add(object("slot", slot, "kind", "fluid", "choices", choices));
        slot(display, "input", "fluid", slot);
    }

    void itemOutput(PositionedStack display, int slot, ItemStack item, int chance) {
        output(display, slot, "item", facts.item(item), item.stackSize, chance);
    }

    void fluidOutput(PositionedStack display, int slot, FluidStack fluid) {
        output(display, slot, "fluid", facts.fluid(fluid), fluid.amount, 10000);
    }

    private void output(PositionedStack display, int slot, String kind, String id, long amount, int chance) {
        if (chance < 0 || chance > 10000) throw new Jobs.Fault("invalid_chance", "GT recipe chance is outside 0..10000");
        outputs.add(object("slot", slot, "kind", kind, "id", id, "amount", positive(amount),
                "chance", Chance.of(chance, 10000), "role", "result", "change", null));
        slot(display, "output", kind, slot);
    }

    private void slot(PositionedStack display, String direction, String kind, int slot) {
        elements.add(object("kind", "slot", "direction", direction, "substance", kind, "slot", slot,
                "x", display.relx, "y", display.rely, "width", 16, "height", 16, "z", 1));
    }

    void property(String key, String label, Object value) {
        if (properties.has(key)) throw new Jobs.Fault("metadata_conflict", "Duplicate recipe property " + key);
        properties.add(key, object("name", facts.text(label), "value", Values.capture(value, facts)));
    }

    void finish() { record.addProperty("id", Identity.recipe(record)); }

    private static String positive(long amount) {
        if (amount <= 0) throw new Jobs.Fault("invalid_amount", "Recipe amount must be positive");
        return Long.toString(amount);
    }
}
