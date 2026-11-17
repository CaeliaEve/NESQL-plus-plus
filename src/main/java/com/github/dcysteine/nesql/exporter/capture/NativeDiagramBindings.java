package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.source.Json.*;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.*;
import java.util.*;

/** Store each native normalized component once per direction, retaining every destination. */
final class NativeDiagramBindings {
    private static final int MAX = 262144;
    final List<JsonObject> rows = new ArrayList<>();
    private final Map<String,Integer> indices = new HashMap<>();
    private final List<Set<Integer>> destinations = new ArrayList<>();
    private int edges;
    long bytes;

    static String key(JsonObject binding) {
        return CanonicalJson.digest(object("query",binding.get("query"),"component",NativeDiagramContents.compact(binding.getAsJsonObject("component"))));
    }
    Integer index(JsonObject binding) { return indices.get(key(binding)); }
    int add(JsonObject binding, int diagram) {
        String key = key(binding); Integer index = indices.get(key);
        if (index == null) {
            if (rows.size() >= MAX) throw fault("Native query binding budget exceeded");
            JsonObject row = object("query",binding.get("query"),"component",NativeDiagramContents.compact(binding.getAsJsonObject("component")),"diagrams",array());
            index = rows.size(); rows.add(row); destinations.add(new HashSet<>());
            indices.put(key,index); bytes += CanonicalJson.bytes(row).length;
        }
        Set<Integer> members = destinations.get(index);
        if (!members.add(diagram)) throw fault("Duplicate native diagram query destination");
        if (++edges > MAX) throw fault("Native query destination budget exceeded");
        JsonArray targets = rows.get(index).getAsJsonArray("diagrams");
        bytes += Integer.toString(diagram).length() + (targets.size() == 0 ? 0 : 1);
        targets.add(value(diagram)); return index;
    }
    private static Jobs.Fault fault(String message) { return new Jobs.Fault("reference_unsupported",message); }
}
