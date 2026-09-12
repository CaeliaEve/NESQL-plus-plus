package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import thaumcraft.api.crafting.InfusionRecipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Collections;
import java.util.function.Function;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;

/** Output semantics and native examples. Recipe-owned stacks and NBT never enter a mutating API. */
final class Products {
    private static final String CARRY = "net.glease.tc4tweak.modules.infusionRecipe.InfusionRecipeGetOutput";
    final ItemStack output;
    private final JsonObject action;
    private final Map<String, JsonObject> samples;

    private Products(ItemStack output, JsonObject action, Map<String, JsonObject> samples) {
        this.output = output; this.action = action; this.samples = samples;
    }

    static Products infusion(InfusionRecipe recipe, List<ItemStack> centers, Facts facts) {
        Object supplied = recipe.getRecipeOutput();
        JsonObject action;
        ItemStack base = null;
        String name = null;
        NBTBase tag = null;
        if (supplied instanceof ItemStack) {
            base = concrete((ItemStack) supplied);
            action = carry(recipe, base, facts);
            if (action == null) return new Products(base, null, new HashMap<>());
        } else if (supplied instanceof Object[]) {
            Object[] change = (Object[]) supplied;
            if (change.length != 2 || !(change[0] instanceof String) || !(change[1] instanceof NBTBase)) throw fault("Invalid infusion tag replacement");
            name = (String) change[0]; tag = (NBTBase) ((NBTBase) change[1]).copy();
            action = object("kind", "patch", "set", object(name, TypedNbt.encode(tag)), "limits", new JsonObject());
        } else throw fault("Infusion output requires a dedicated result adapter");
        final NBTBase replacement = tag;
        final String key = name;
        final ItemStack template = base;
        return observe(centers, action, center -> {
            if (replacement != null) {
                NBTTagCompound set = new NBTTagCompound(); set.setTag(key, replacement.copy());
                return patch(center, set, Collections.emptyMap());
            }
            return concrete((ItemStack) invoke(type(CARRY), null, "getOutput",
                    new Class<?>[] {InfusionRecipe.class, ItemStack.class, ItemStack.class}, recipe, center.copy(), template.copy()));
        }, facts);
    }

    static Products observe(List<ItemStack> centers, JsonObject action, Function<ItemStack, ItemStack> transform, Facts facts) {
        Map<String, JsonObject> samples = new HashMap<>();
        ItemStack first = null;
        for (ItemStack center : centers) {
            Jobs.checkpoint();
            String input = facts.item(center);
            if (samples.containsKey(input)) continue;
            ItemStack result = concrete(transform.apply(center.copy()));
            if (first == null) first = result;
            samples.put(input, object("id", facts.item(result), "amount", Integer.toString(result.stackSize)));
        }
        if (first == null) throw fault("Changed output has no representative input");
        return new Products(first, action, samples);
    }

    static ItemStack patch(ItemStack input, NBTTagCompound set, Map<String, Integer> limits) {
        ItemStack output = input.copy();
        for (Object key : set.func_150296_c()) output.setTagInfo((String) key, set.getTag((String) key).copy());
        if (!limits.isEmpty() && !output.hasTagCompound()) output.setTagCompound(new NBTTagCompound());
        for (Map.Entry<String, Integer> limit : limits.entrySet()) {
            output.getTagCompound().setInteger(limit.getKey(), Math.min(output.getTagCompound().getInteger(limit.getKey()), limit.getValue()));
        }
        return output;
    }

    void attach(RecipeRow row) {
        if (action == null) return;
        JsonObject center = null;
        for (JsonElement value : row.inputs) {
            JsonObject input = value.getAsJsonObject();
            if (input.get("kind").getAsString().equals("item") && input.get("slot").getAsInt() == 0) center = input;
        }
        if (center == null) throw fault("Changed output has no center binding");
        JsonArray examples = new JsonArray();
        for (JsonElement value : center.getAsJsonArray("choices")) {
            JsonObject sample = samples.get(value.getAsJsonObject().get("id").getAsString());
            if (sample == null) throw fault("Native result sample is missing an input choice");
            examples.add(sample);
        }
        row.outputs.get(0).getAsJsonObject().add("change", object("input", 0, "action", action, "samples", examples));
    }

    private static JsonObject carry(InfusionRecipe recipe, ItemStack base, Facts facts) {
        Object overrides = field(type(CARRY), null, "overrides");
        if (!(overrides instanceof Map<?, ?>)) throw fault("Invalid pinned infusion behavior registry");
        if (((Map<?, ?>) overrides).get(recipe) != null) throw fault("Custom infusion NBT behavior requires an explicit adapter");
        Class<?> configType = type("net.glease.tc4tweak.ConfigurationHandler");
        Object config = field(configType, null, "INSTANCE");
        if (!(Boolean) invoke(configType, config, "isInfusionRecipeNBTCarryOver", new Class<?>[0])) return null;
        boolean tools = (Boolean) invoke(configType, config, "isInfusionRecipeNBTModifyArmorToolOnly", new Class<?>[0]);
        if (tools && !(base.getItem() instanceof ItemArmor) && base.getItem().getToolClasses(base.copy()).isEmpty()) return null;
        Object values = invoke(configType, config, "getInfusionRecipeNBTWhitelist", new Class<?>[0]);
        if (!(values instanceof List<?>) || ((List<?>) values).size() > 4096) throw fault("Invalid native inheritance key filter");
        TreeSet<String> keys = new TreeSet<>();
        for (Object key : (List<?>) values) { if (!(key instanceof String)) throw fault("Invalid inheritance key"); keys.add((String) key); }
        if (!keys.isEmpty() && !base.hasTagCompound()) throw fault("Native filtered inheritance into an untagged output cannot be exported as a successful transformation");
        JsonArray filter = new JsonArray(); for (String key : keys) filter.add(value(key));
        return object("kind", "merge", "base", object("id", facts.item(base), "amount", Integer.toString(base.stackSize)),
                "keys", keys.isEmpty() ? null : filter, "tools", tools);
    }

    private static ItemStack concrete(ItemStack item) {
        if (item == null || item.getItem() == null || item.stackSize < 1) throw fault("Infusion has no concrete output");
        return item.copy();
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
