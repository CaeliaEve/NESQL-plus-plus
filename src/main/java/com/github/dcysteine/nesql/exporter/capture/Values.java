package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gregtech.api.util.recipe.SolarFactoryRecipeData;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Shared metadata types for recipe fields and allele values; no generic Object.toString(). */
final class Values {
    private Values() {}

    static JsonObject capture(Object value, Facts facts) { return capture(value, facts, 0); }

    private static JsonObject capture(Object value, Facts facts, int depth) {
        if (depth > 16) throw new Jobs.Fault("metadata_limit", "Metadata exceeds 16 levels");
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof BigInteger) {
            if (value instanceof BigInteger && ((BigInteger) value).bitLength() > 63) throw new Jobs.Fault("metadata_number", "Metadata integer exceeds signed 64-bit range");
            return object("kind", "integer", "value", value.toString());
        }
        if (value instanceof Float || value instanceof Double || value instanceof BigDecimal) {
            if (value instanceof Float && !Float.isFinite((Float) value) || value instanceof Double && !Double.isFinite((Double) value)) {
                throw new Jobs.Fault("metadata_number", "Metadata contains a non-finite number");
            }
            BigDecimal decimal = new BigDecimal(value.toString()).stripTrailingZeros();
            if (decimal.precision() > 256 || Math.abs((long) decimal.scale()) > 256) throw new Jobs.Fault("metadata_limit", "Metadata decimal exceeds its digit budget");
            String encoded = decimal.toPlainString();
            if (encoded.length() > 256) throw new Jobs.Fault("metadata_limit", "Metadata decimal exceeds 256 characters");
            return object("kind", "decimal", "value", encoded);
        }
        if (value instanceof Boolean) return object("kind", "flag", "value", value);
        if (value instanceof String) return object("kind", "text", "text", facts.text((String) value));
        if (value instanceof ItemStack) {
            ItemStack stack = (ItemStack) value;
            return stack("item", facts.item(stack), stack.stackSize, "count", depth);
        }
        if (value instanceof FluidStack) {
            FluidStack stack = (FluidStack) value;
            return stack("fluid", facts.fluid(stack), stack.amount, "mb", depth);
        }
        if (value instanceof Enum<?>) {
            Enum<?> symbol = (Enum<?>) value;
            return object("kind", "symbol", "namespace", symbol.getDeclaringClass().getName(), "value", symbol.name());
        }
        if (value instanceof SolarFactoryRecipeData) {
            SolarFactoryRecipeData solar = (SolarFactoryRecipeData) value;
            return object("kind", "map", "values", object(
                    "minimumWaferTier", capture(solar.minimumWaferTier, facts, depth + 1),
                    "minimumWaferCount", capture(solar.minimumWaferCount, facts, depth + 1),
                    "tierRequired", capture(solar.tierRequired, facts, depth + 1)));
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            if (length > 4096) throw new Jobs.Fault("metadata_limit", "Metadata list exceeds 4096 entries");
            JsonArray values = new JsonArray();
            for (int index = 0; index < length; index++) values.add(capture(Array.get(value, index), facts, depth + 1));
            return object("kind", "list", "values", values);
        }
        if (value instanceof Map<?, ?>) {
            Map<?, ?> entries = (Map<?, ?>) value;
            if (entries.size() > 4096) throw new Jobs.Fault("metadata_limit", "Metadata map exceeds 4096 entries");
            JsonObject values = new JsonObject();
            for (Map.Entry<?, ?> entry : entries.entrySet()) {
                if (!(entry.getKey() instanceof String)) throw new Jobs.Fault("metadata_type", "Metadata map requires string keys");
                values.add((String) entry.getKey(), capture(entry.getValue(), facts, depth + 1));
            }
            return object("kind", "map", "values", values);
        }
        if (value instanceof Iterable<?>) {
            List<JsonObject> elements = new ArrayList<>();
            for (Object element : (Iterable<?>) value) {
                if (elements.size() == 4096) throw new Jobs.Fault("metadata_limit", "Metadata list exceeds 4096 entries");
                elements.add(capture(element, facts, depth + 1));
            }
            if (value instanceof Set<?>) {
                Map<JsonObject, String> order = new java.util.IdentityHashMap<>();
                for (JsonObject element : elements) order.put(element, new String(CanonicalJson.bytes(element), java.nio.charset.StandardCharsets.UTF_8));
                elements.sort(java.util.Comparator.comparing(order::get, CanonicalJson.KEY_ORDER));
            }
            JsonArray values = new JsonArray();
            elements.forEach(values::add);
            return object("kind", "list", "values", values);
        }
        throw new Jobs.Fault("metadata_type", "No metadata adapter for " + (value == null ? "null" : value.getClass().getName()));
    }

    private static JsonObject stack(String kind, String id, int amount, String unit, int depth) {
        if (depth == 16) throw new Jobs.Fault("metadata_limit", "Stack metadata exceeds 16 levels");
        return object("kind", "map", "values", object(
                "stack", object("kind", "reference", "target", object("kind", kind, "id", id)),
                "amount", object("kind", "quantity", "amount", Integer.toString(amount), "unit", unit)));
    }
}
