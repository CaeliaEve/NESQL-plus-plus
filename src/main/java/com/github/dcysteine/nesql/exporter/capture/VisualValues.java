package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Exact, locale-independent values shared by native visual modules. */
final class VisualValues {
    private VisualValues() { }
    static String bits(float value) {
        if (!Float.isFinite(value)) throw new Jobs.Fault("animation_invalid", "Non-finite native visual value");
        String raw = Integer.toHexString(Float.floatToRawIntBits(value));
        return "00000000".substring(raw.length()) + raw;
    }
    static JsonArray floats(float... values) {
        JsonArray result = new JsonArray(); for (float value : values) result.add(value(bits(value))); return result;
    }
    static JsonObject record(JsonObject data) {
        JsonObject result = object("data", data); result.addProperty("id", Identity.content("visual", result)); return result;
    }
}
