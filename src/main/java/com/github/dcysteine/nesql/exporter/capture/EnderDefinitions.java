package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.TypedNbt;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.collect.Table;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Audited EnderIO definition fields. Never execute an arbitrary native recipe callback. */
final class EnderDefinitions {
    private static final String ROOT = "crazypants.enderio.machine.";

    static JsonObject assembly(Object source) {
        exact(source, ROOT + "recipe.BasicManyToOneRecipe");
        Object recipe = field(source, "recipe");
        exact(recipe, ROOT + "recipe.Recipe");
        return recipe(recipe);
    }

    static JsonObject vat(Object source) {
        exact(source, ROOT + "vat.VatRecipe");
        JsonObject result = recipe(source);
        Object[] inputs = (Object[]) invoke(source.getClass(), source, "getInputs", new Class<?>[0]);
        result.addProperty("valid", (Boolean) invoke(source.getClass(), source, "isValid", new Class<?>[0]));
        result.addProperty("requiredItems", (Integer) field(source, "requiredItems"));
        result.add("inputTable", table(source, "inputFluidStacks", inputs));
        result.add("outputTable", table(source, "outputFluidStacks", inputs));
        return result;
    }

    private static JsonObject recipe(Object source) {
        Object[] inputs = (Object[]) invoke(source.getClass(), source, "getInputs", new Class<?>[0]);
        Object[] outputs = (Object[]) invoke(source.getClass(), source, "getOutputs", new Class<?>[0]);
        if (inputs == null || inputs.length > 4096 || outputs == null || outputs.length > 128)
            throw fault("Invalid EnderIO native definition shape");
        JsonArray ins = array(), outs = array();
        for (Object input : inputs) {
            Jobs.checkpoint();
            EnderInputs.check(input);
            JsonArray patterns = array();
            for (EnderInputs.Pattern pattern : EnderInputs.patterns(input))
                patterns.add(object("item", RecipeRow.stackEvidence(pattern.item), "anyMeta", pattern.anyMeta));
            ins.add(object("class", input.getClass().getName(),
                    "input", RecipeRow.stackEvidence((ItemStack) field(type(EnderInputs.INPUT), input, "input")),
                    "fluid", fluid((FluidStack) field(type(EnderInputs.INPUT), input, "fluid")),
                    "slot", field(type(EnderInputs.INPUT), input, "slot"),
                    "useMeta", field(type(EnderInputs.INPUT), input, "useMeta"),
                    "multiplier", finite(field(type(EnderInputs.INPUT), input, "multiplier")),
                    "oreId", input.getClass().getName().equals(EnderInputs.ORE) ? field(input, "oreId") : null,
                    "patterns", patterns));
        }
        for (Object output : outputs) {
            Jobs.checkpoint();
            exact(output, ROOT + "recipe.RecipeOutput");
            outs.add(object("item", RecipeRow.stackEvidence((ItemStack) field(output, "output")),
                    "fluid", fluid((FluidStack) field(output, "outputFluid")),
                    "chance", finite(field(output, "chance")), "experience", finite(field(output, "exp"))));
        }
        return object("inputs", ins, "outputs", outs,
                "energy", invoke(source.getClass(), source, "getEnergyRequired", new Class<?>[0]),
                "bonus", invoke(source.getClass(), source, "getBonusType", new Class<?>[0]).toString());
    }

    private static String finite(Object source) {
        float number = (Float) source;
        if (!Float.isFinite(number)) throw fault("Non-finite EnderIO native definition value");
        return Float.toString(number);
    }

    @SuppressWarnings("unchecked")
    private static JsonArray table(Object source, String name, Object[] inputs) {
        Table<Object, Object, FluidStack> table = (Table<Object, Object, FluidStack>) field(source, name);
        if (table == null || table.size() > 262144) throw fault("Missing or oversized EnderIO fluid table");
        IdentityHashMap<Object, Integer> positions = new IdentityHashMap<>();
        for (int index = 0; index < inputs.length; index++) positions.putIfAbsent(inputs[index], index);
        List<JsonObject> cells = new ArrayList<>();
        for (Table.Cell<Object, Object, FluidStack> cell : table.cellSet()) {
            Jobs.checkpoint();
            Integer left = positions.get(cell.getRowKey()), right = positions.get(cell.getColumnKey());
            if (left == null || right == null) throw fault("EnderIO fluid table refers to an unknown reagent");
            cells.add(object("left", left, "right", right, "fluid", fluid(cell.getValue())));
        }
        cells.sort(Comparator.comparingInt((JsonObject cell) -> cell.get("left").getAsInt())
                .thenComparingInt(cell -> cell.get("right").getAsInt()));
        JsonArray result = array();
        for (JsonObject cell : cells) result.add(cell);
        return result;
    }

    private static JsonObject fluid(FluidStack source) {
        if (source == null) return null;
        if (source.getFluid() == null) throw fault("Missing EnderIO definition fluid");
        return object("name", source.getFluid().getName(), "amount", source.amount, "nbt", TypedNbt.encode(source.tag));
    }

    private static void exact(Object source, String name) {
        if (source == null || !source.getClass().getName().equals(name)) throw fault("Unknown EnderIO definition: " + name);
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("recipe_unsupported", message); }
}
