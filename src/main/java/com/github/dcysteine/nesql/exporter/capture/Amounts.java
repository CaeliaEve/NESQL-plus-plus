package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Explicit rules for the pinned sparging loop; never sample random outputs during capture. */
final class Amounts {
    private Amounts() {}

    static Map<Integer, JsonObject> sparge(int input, int outputs, int limit, int gas) {
        if (outputs < 2 || outputs > 130 || gas < 1 || outputs > 2 && limit < 1) {
            throw new Jobs.Fault("quantity_rule", "Invalid sparging budget: gas=" + gas + "; outputs=" + outputs + "; limit=" + limit);
        }
        Map<Integer, JsonObject> result = new LinkedHashMap<>();
        JsonArray preceding = new JsonArray();
        long minimum = gas;
        for (int slot = 2; slot < outputs; slot++) {
            if (minimum <= 1) throw new Jobs.Fault("quantity_rule", "Sparging can exhaust its gas before output slot=" + slot);
            JsonArray after = new JsonArray(); preceding.forEach(after::add);
            result.put(slot, object("kind", "draw", "input", input, "after", after, "limit", Integer.toString(limit)));
            preceding.add(value(slot));
            minimum = Math.max(1, minimum - limit);
        }
        result.put(1, object("kind", "remainder", "input", input, "after", preceding));
        return result;
    }

    static JsonObject branch(String group, String branch, String condition, String threshold, long nominal) {
        if (nominal < 1) throw new Jobs.Fault("quantity_rule", "Invalid branch nominal amount: " + nominal);
        JsonObject result = object("kind", "branch", "group", group, "branch", branch, "nominal", Long.toString(nominal));
        if (condition != null) result.addProperty("condition", condition);
        if (threshold != null) result.addProperty("threshold", threshold);
        return result;
    }

    static JsonObject potential(String stat, String condition, String sample, long nominal) {
        if (nominal < 1) throw new Jobs.Fault("quantity_rule", "Invalid potential nominal amount: " + nominal);
        JsonObject result = object("kind", "potential", "stat", stat, "nominal", Long.toString(nominal));
        if (condition != null) result.addProperty("condition", condition);
        if (sample != null) result.addProperty("sample", sample);
        return result;
    }
}
