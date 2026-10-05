package com.github.dcysteine.nesql.exporter.capture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.IdentityHashMap;
import java.util.Map;

/** Detaches JSON after native drawing, preserving aliases used by late asset commits.
 * Accessed only by the serial client/exporter handoff, never the PNG worker.
 */
final class JsonSnapshot {
    private final IdentityHashMap<JsonElement, JsonElement> copies = new IdentityHashMap<>();
    private boolean frozen;
    long weight;

    JsonObject object(JsonObject value) { return resolve(value).getAsJsonObject(); }
    JsonArray array(JsonArray value) { return resolve(value).getAsJsonArray(); }
    private JsonElement resolve(JsonElement value) {
        if (!frozen) return value;
        JsonElement owned = copies.get(value);
        if (owned == null) throw new IllegalStateException("Visual commit refers to JSON outside its snapshot");
        return owned;
    }
    void freeze(Facts.Batch batch) {
        if (frozen) throw new IllegalStateException("Recipe JSON already detached");
        for (Facts.Record record : batch.records) copy(record.value);
        for (Facts.Icon icon : batch.icons) copy(icon.record);
        for (Facts.Picture picture : batch.pictures) copy(picture.record);
        for (Facts.Scene scene : batch.scenes) { copy(scene.recipe); copy(scene.elements); }
        // Reserve space for the content IDs populated by queued asset/view commits.
        weight += 512L * (batch.icons.size() + batch.pictures.size() + batch.scenes.size());
        frozen = true;
    }
    private JsonElement copy(JsonElement value) {
        JsonElement previous = copies.get(value);
        if (previous != null) return previous;
        weight += 128; // Conservative queue accounting for nodes and identity-map entries.
        if (value.isJsonObject()) {
            JsonObject target = new JsonObject(); copies.put(value, target);
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                weight += 64L + entry.getKey().length() * 2L; target.add(entry.getKey(), copy(entry.getValue()));
            }
            return target;
        }
        if (value.isJsonArray()) {
            JsonArray target = new JsonArray(); copies.put(value, target);
            for (JsonElement element : value.getAsJsonArray()) target.add(copy(element));
            return target;
        }
        // Gson primitives/null are immutable; containers and their membership are not.
        weight += value.toString().length() * 2L; copies.put(value, value); return value;
    }
}
