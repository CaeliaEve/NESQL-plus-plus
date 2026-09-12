package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/** Small JSON construction helpers; domain field names and units remain explicit at call sites. */
public final class Json {
    private Json() {}
    /** Explicit unsigned metadata; quantities still use decimal strings. */
    public static JsonPrimitive uint(long value) {
        if (value < 0 || value > 0xffffffffL) throw new IllegalArgumentException("Unsigned metadata exceeds 32 bits");
        return new JsonPrimitive(value);
    }
    public static JsonObject object(Object... fields) {
        if (fields.length % 2 != 0) throw new IllegalArgumentException("Expected field/value pairs");
        JsonObject result = new JsonObject();
        for (int index = 0; index < fields.length; index += 2) {
            String key = (String) fields[index];
            if (result.has(key)) throw new IllegalArgumentException("Duplicate JSON field: " + key);
            result.add(key, value(fields[index + 1]));
        }
        return result;
    }
    public static JsonArray array(Object... values) {
        JsonArray result = new JsonArray();
        for (Object value : values) result.add(value(value));
        return result;
    }
    public static JsonElement value(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonElement) return (JsonElement) value;
        if (value instanceof String) return new JsonPrimitive((String) value);
        if (value instanceof Boolean) return new JsonPrimitive((Boolean) value);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) return new JsonPrimitive((Number) value);
        if (value instanceof Long) throw new IllegalArgumentException("Exact quantities must use decimal strings");
        throw new IllegalArgumentException("Unsupported JSON field type: " + value.getClass());
    }
}
