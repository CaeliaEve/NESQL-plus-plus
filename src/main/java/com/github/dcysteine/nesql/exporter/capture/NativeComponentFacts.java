package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.Map;
import java.util.function.Consumer;

/** Walk explicit semantic components; arbitrary raw NBT is never a dependency language. */
final class NativeComponentFacts {
    static void visit(JsonElement value, Consumer<String> fact, Consumer<String> text) {
        new NativeComponentFacts(fact, text).read(value, 0);
    }

    private final Consumer<String> fact, text;
    private int nodes;

    private NativeComponentFacts(Consumer<String> fact, Consumer<String> text) {
        this.fact = fact;
        this.text = text;
    }

    private void read(JsonElement value, int depth) {
        Jobs.checkpoint();
        if (depth > 128 || ++nodes > 262144)
            throw new Jobs.Fault(
                    "reference_limit", "Native component traversal exceeds its budget");
        if (value.isJsonArray())
            for (JsonElement child : value.getAsJsonArray()) read(child, depth + 1);
        else if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            JsonElement kind = object.get("kind");
            if (kind != null && kind.isJsonPrimitive() && kind.getAsJsonPrimitive().isString()) {
                if ((kind.getAsString().equals("item") || kind.getAsString().equals("fluid") || kind.getAsString().equals("itemFact"))
                        && object.has("fact")
                        && !object.get("fact").isJsonNull())
                    fact.accept(string(object.get("fact")));
                if (kind.getAsString().equals("text")) text.accept(string(object.get("text")));
            }
            for (Map.Entry<String, JsonElement> child : object.entrySet())
                if (!child.getKey().equals("nbt") && !child.getKey().equals("raw"))
                    read(child.getValue(), depth + 1);
        }
    }

    private static String string(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new Jobs.Fault("reference_unsupported", "Malformed native fact/text field");
        return value.getAsString();
    }
}
