package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

/** Registry and origin ids are language-independent; content ids address exact snapshot facts. */
public final class Identity {
    private Identity() {}

    public static String item(String registry, int meta, JsonElement nbt) {
        JsonObject key = key("item", registry, nbt);
        key.addProperty("meta", meta);
        return "item_" + CanonicalJson.digest(key);
    }

    public static String fluid(String registry, JsonElement nbt) {
        if (registry == null || registry.isEmpty()) {
            throw new IllegalArgumentException("A Forge fluid registry key is required: " + registry);
        }
        JsonObject key = new JsonObject();
        key.addProperty("kind", "fluid");
        key.addProperty("registry", registry);
        key.add("nbt", nbt);
        return "fluid_" + CanonicalJson.digest(key);
    }

    public static String content(String kind, JsonObject record) {
        JsonObject key = new JsonObject();
        record.entrySet().stream().filter(entry -> !entry.getKey().equals("id"))
                .forEach(entry -> key.add(entry.getKey(), entry.getValue()));
        return kind + "_" + CanonicalJson.digest(key);
    }

    public static String origin(String kind, JsonObject origin) {
        return kind + "_" + CanonicalJson.digest(origin);
    }

    public static String recipe(JsonObject record) {
        JsonObject key = new JsonObject();
        for (String field : new String[] {"source", "category", "inputs", "duration", "energy", "grid"}) {
            key.add(field, record.get(field));
        }
        JsonArray outputs = new JsonArray();
        for (JsonElement value : record.getAsJsonArray("outputs")) {
            JsonObject output = value.getAsJsonObject();
            if (!output.has("change") || output.get("change").isJsonNull()) { outputs.add(output); continue; }
            JsonObject changed = new JsonObject(), operation = new JsonObject();
            output.entrySet().stream().filter(entry -> !entry.getKey().equals("id") && !entry.getKey().equals("amount"))
                    .forEach(entry -> changed.add(entry.getKey(), entry.getValue()));
            output.getAsJsonObject("change").entrySet().stream().filter(entry -> !entry.getKey().equals("samples"))
                    .forEach(entry -> operation.add(entry.getKey(), entry.getValue()));
            changed.add("change", operation); outputs.add(changed);
        }
        key.add("outputs", outputs);
        JsonObject properties = new JsonObject();
        record.getAsJsonObject("properties").entrySet().forEach(entry ->
                properties.add(entry.getKey(), entry.getValue().getAsJsonObject().get("value")));
        key.add("properties", properties);
        JsonElement magic = record.get("magic");
        if (magic != null && magic.isJsonObject()) {
            JsonObject cost = new JsonObject();
            magic.getAsJsonObject().entrySet().forEach(entry -> cost.add(entry.getKey(), entry.getValue()));
            JsonArray research = new JsonArray();
            for (JsonElement entry : cost.getAsJsonArray("research")) {
                JsonObject link = new JsonObject();
                entry.getAsJsonObject().entrySet().stream().filter(field -> !field.getKey().equals("completed"))
                        .forEach(field -> link.add(field.getKey(), field.getValue()));
                research.add(link);
            }
            cost.add("research", research);
            magic = cost;
        }
        key.add("magic", magic);
        return "recipe_" + CanonicalJson.digest(key);
    }

    private static JsonObject key(String kind, String registry, JsonElement nbt) {
        // Forge 1.7 registry keys are opaque text, not ResourceLocation paths.
        // Keep spaces, case, Unicode and additional separators exactly as registered.
        int separator = registry == null ? -1 : registry.indexOf(':');
        if (separator <= 0 || separator == registry.length() - 1) {
            throw new IllegalArgumentException("A namespaced Forge registry key is required: " + registry);
        }
        JsonObject key = new JsonObject();
        key.addProperty("kind", kind);
        key.addProperty("registry", registry);
        key.add("nbt", nbt);
        return key;
    }
}
