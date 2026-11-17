package com.github.dcysteine.nesql.exporter.capture;

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
import java.util.List;
import java.util.Set;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Builds one recipe from adapter-owned game semantics. */
final class RecipeRow {
    final Facts facts;
    final JsonObject record;
    final JsonArray inputs = new JsonArray(), outputs = new JsonArray();
    final JsonObject properties = new JsonObject();
    JsonObject exclusion;
    private final JsonArray notes = new JsonArray();
    private final Set<String> noteKeys = new HashSet<>();

    RecipeRow(Facts facts, JsonObject origin, String category, int order) {
        this.facts = facts;
        record = object("source", origin, "category", category, "inputs", inputs, "outputs", outputs,
                "duration", null, "energy", null, "properties", properties, "grid", null, "magic", null, "order", order);
    }

    /** A rejected native predicate still has an immutable, inspectable disposition. */
    boolean exclude(JsonObject proof) {
        exclusion = com.github.dcysteine.nesql.exporter.task.CaptureOutcome.excluded(proof).json().getAsJsonObject("proof");
        return false;
    }

    static JsonObject stackEvidence(ItemStack item) {
        if (item == null) return null;
        return object("registry", item.getItem() == null ? null : net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()),
                "meta", Items.feather.getDamage(item), "amount", Integer.toString(item.stackSize),
                "nbt", com.github.dcysteine.nesql.exporter.source.TypedNbt.encode(item.getTagCompound()));
    }

    void itemInput(int slot, List<Ingredient> ingredients, boolean crafting) {
        itemInput(slot,ingredients,crafting,false);
    }
    void nativeCraftingInput(int slot,List<Ingredient> ingredients) {
        itemInput(slot,ingredients,true,true);
    }
    private void itemInput(int slot,List<Ingredient> ingredients,boolean crafting,boolean nativeCrafting) {
        if (ingredients.isEmpty() || ingredients.size() > 65536) throw new Jobs.Fault("empty_ingredient", "Invalid recipe ingredient choice count");
        JsonArray choices = new JsonArray();
        Set<String> seen = new HashSet<>();
        for (Ingredient ingredient : ingredients) {
            Jobs.checkpoint();
            ItemStack item = ingredient.item;
            String id = facts.item(item);
            JsonObject consumption = object("kind", ingredient.keep ? "keep" : "consume");
            JsonArray returns = new JsonArray();
            if (crafting && !ingredient.keep) {
                ItemStack original = item.copy(); original.stackSize = 1;ItemStack returned=null;
                if(nativeCrafting && GtToolConsumption.supports(original)) {
                    JsonObject tool=new GtToolConsumption(original).consumption();
                    if(tool!=null)consumption=tool;
                } else if(nativeCrafting) {
                    JsonObject dynamic=NativeCraftingContainers.consumption(original);
                    if(dynamic!=null)consumption=dynamic;
                    else returned=ItemCallbacks.container(original,false);
                }
                else if(original.getItem().hasContainerItem(original.copy()))returned=original.getItem().getContainerItem(original.copy());
                if (returned != null && returned.getItem() != null && returned.stackSize > 0) {
                    if (returned.getItem() == original.getItem() && ItemStack.areItemStackTagsEqual(original, returned)) {
                        int damage = Items.feather.getDamage(returned) - Items.feather.getDamage(original);
                        if (damage > 0 && original.isItemStackDamageable() && returned.stackSize == 1) consumption = object("kind", "damage", "points", damage);
                        else if (damage == 0 && returned.stackSize == 1) consumption = object("kind", "keep");
                        else returns.add(object("kind", "item", "id", facts.item(returned), "amount", Integer.toString(returned.stackSize)));
                    } else returns.add(object("kind", "item", "id", facts.item(returned), "amount", Integer.toString(returned.stackSize)));
                }
            }
            JsonObject choice = object("id", id, "amount", positive(ingredient.amount), "consume", consumption,
                    "returns", returns, "rule", ingredient.rule);
            // The same item and predicate may be offered for different quantities
            // or consumption. Only identical complete alternatives are duplicates.
            if (seen.add(CanonicalJson.digest(choice))) choices.add(choice);
        }
        inputs.add(object("slot", slot, "kind", "item", "choices", choices));
    }

    void fluidInput(int slot, FluidStack fluid) {
        fluidInput(slot, fluid, false);
    }

    void fluidInput(int slot, FluidStack fluid, boolean keep) {
        fluidInput(slot, fluid, keep, object("kind", "exact"));
    }

    void fluidInput(int slot, FluidStack fluid, boolean keep, JsonObject rule) {
        if (fluid.amount < 0) throw new Jobs.Fault("invalid_amount", "Negative fluid input slot=" + slot
                + "; registry=" + fluid.getFluid().getName() + "; amount=" + fluid.amount);
        JsonArray choices = new JsonArray();
        String amount = positive(fluid.amount); // Zero semantics require an explicit native family.
        choices.add(object("id", facts.fluid(fluid), "amount", amount,
                "consume", object("kind", keep ? "keep" : "consume"), "returns", new JsonArray(), "rule", rule));
        inputs.add(object("slot", slot, "kind", "fluid", "choices", choices));
    }

    void zeroFluidInput(int slot, FluidStack fluid, String policy) {
        if (fluid == null || fluid.getFluid() == null || fluid.amount != 0
                || !("gregtechCost".equals(policy) || "gregtechPresence".equals(policy) || "vatMatch".equals(policy) || "binnieIncubator".equals(policy)))
            throw new Jobs.Fault("invalid_amount", "Zero fluid input requires a verified native zero-cost policy");
        inputs.add(object("slot", slot, "kind", "fluid", "choices", array(object(
                "id", facts.fluid(fluid), "amount", "0", "consume", object("kind", "keep"),
                "returns", array(), "rule", object("kind", "nativeZeroFluid", "policy", policy)))));
    }

    void itemOutput(int slot, ItemStack item, int chance) {
        itemOutput(slot, item, chance, null, null);
    }

    void itemOutput(int slot, ItemStack item, int chance, JsonObject quantity) {
        itemOutput(slot, item, chance, null, quantity);
    }

    void itemOutput(int slot, ItemStack item, int chance, String amount, JsonObject quantity) {
        if (quantity != null) {
            output(slot, "item", facts.item(item), null, chance, quantity);
        } else {
            String exactAmount = amount != null ? amount : Integer.toString(item.stackSize);
            if (exactAmount.startsWith("-") || exactAmount.equals("0")) {
                throw new Jobs.Fault("invalid_amount", "Item output slot=" + slot + "; registry="
                        + net.minecraft.item.Item.itemRegistry.getNameForObject(item.getItem()) + "; meta=" + Items.feather.getDamage(item)
                        + "; amount=" + exactAmount + ": a fixed recipe output must be positive");
            }
            output(slot, "item", facts.item(item), exactAmount, chance, null);
        }
    }

    void fluidOutput(int slot, FluidStack fluid) {
        fluidOutput(slot, fluid, null, null);
    }

    void fluidOutput(int slot, FluidStack fluid, JsonObject quantity) {
        fluidOutput(slot, fluid, null, quantity);
    }

    void fluidOutput(int slot, FluidStack fluid, String amount, JsonObject quantity) {
        if (quantity != null) {
            output(slot, "fluid", facts.fluid(fluid), null, 10000, quantity);
        } else {
            String exactAmount = amount != null ? amount : Integer.toString(fluid.amount);
            if (exactAmount.startsWith("-") || exactAmount.equals("0")) {
                throw new Jobs.Fault("invalid_amount", "Fluid output slot=" + slot + "; registry="
                        + fluid.getFluid().getName() + "; amount=" + exactAmount + ": a fixed recipe output must be positive");
            }
            output(slot, "fluid", facts.fluid(fluid), exactAmount, 10000, null);
        }
    }

    private void output(int slot, String kind, String id, String amount, int chance, JsonObject quantity) {
        if (chance < 0 || chance > 10000) throw new Jobs.Fault("invalid_chance", "GT recipe chance is outside 0..10000");
        outputs.add(object("slot", slot, "kind", kind, "id", id, "amount", amount, "quantity", quantity,
                "chance", Chance.of(chance, 10000), "role", "result", "change", null));
    }

    void property(String key, String label, Object value) {
        if (properties.has(key)) throw new Jobs.Fault("metadata_conflict", "Duplicate recipe property " + key);
        properties.add(key, object("name", facts.text(label), "value", Values.capture(value, facts)));
    }

    /** Native explanation only; machine rules belong in typed properties/process. */
    void note(String key, String source, String text) {
        if (key == null || key.length() > 256 || !key.matches("[A-Za-z0-9_.-]+:[A-Za-z0-9_./$-]+")
                || source == null || source.isEmpty() || source.length() > 512
                || source.chars().anyMatch(Character::isISOControl) || text == null || text.isEmpty())
            throw new Jobs.Fault("metadata_invalid", "Native note needs a stable key, reader source and text");
        if (notes.size() == 256 || !noteKeys.add(key))
            throw new Jobs.Fault("metadata_conflict", "Too many or duplicate native notes: " + key);
        notes.add(object("key", key, "source", source, "text", facts.text(text)));
        record.add("notes", notes);
    }

    void finish() { record.addProperty("id", Identity.recipe(record)); }

    RecipeRow branch() {
        RecipeRow row = new RecipeRow(facts, record.getAsJsonObject("source"), record.get("category").getAsString(), record.get("order").getAsInt());
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : properties.entrySet()) row.properties.add(entry.getKey(), entry.getValue());
        for (com.google.gson.JsonElement note : notes) {
            row.notes.add(new com.google.gson.JsonParser().parse(note.toString()));
            row.noteKeys.add(note.getAsJsonObject().get("key").getAsString());
        }
        if (row.notes.size() != 0) row.record.add("notes", row.notes);
        return row;
    }

    static final class Ingredient {
        final ItemStack item, display;
        final long amount;
        final boolean keep;
        final JsonObject rule;
        Ingredient(ItemStack item, long amount, boolean keep, JsonObject rule) {
            this(item, item, amount, keep, rule);
        }
        Ingredient(ItemStack item, ItemStack display, long amount, boolean keep, JsonObject rule) {
            this.item = item; this.display = display; this.amount = amount; this.keep = keep; this.rule = rule;
        }
    }

    private static String positive(long amount) {
        if (amount <= 0) throw new Jobs.Fault("invalid_amount", "Recipe amount must be positive");
        return Long.toString(amount);
    }
}
