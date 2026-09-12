package com.github.dcysteine.nesql.exporter.source;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable parameters shared by job requests, capture records and environment identity. */
public final class Probe implements Comparable<Probe> {
    public final int count;
    public final Map<String, Integer> channels;

    public Probe(int count, Map<String, Integer> channels) {
        if (count < 1 || count > 64 || channels == null || channels.size() > 32) throw new IllegalArgumentException("Invalid structure probe");
        TreeMap<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : channels.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().matches("[a-z0-9_-]{1,64}") || entry.getValue() == null
                    || entry.getValue() < 1 || entry.getValue() > 65535) throw new IllegalArgumentException("Invalid structure channel");
            copy.put(entry.getKey(), entry.getValue());
        }
        this.count = count; this.channels = Collections.unmodifiableMap(copy);
    }

    public static Probe defaults() { return new Probe(1, Collections.emptyMap()); }

    public static List<Probe> order(List<Probe> probes) {
        if (probes == null || probes.isEmpty() || probes.size() > 16) throw new IllegalArgumentException("Choose between 1 and 16 structure probes");
        TreeSet<Probe> ordered = new TreeSet<>();
        for (Probe probe : probes) {
            if (probe == null || !ordered.add(probe)) throw new IllegalArgumentException("Structure probes must be unique");
        }
        return Collections.unmodifiableList(new ArrayList<>(ordered));
    }

    public static Probe parse(JsonElement element) {
        if (element == null || !element.isJsonObject()) throw new IllegalArgumentException("A structure probe must be an object");
        JsonObject value = element.getAsJsonObject();
        if (!value.entrySet().stream().allMatch(entry -> entry.getKey().equals("count") || entry.getKey().equals("channels"))) {
            throw new IllegalArgumentException("Unknown structure probe field");
        }
        Map<String, Integer> channels = new TreeMap<>();
        if (value.has("channels")) {
            if (!value.get("channels").isJsonObject()) throw new IllegalArgumentException("Probe channels must be an object");
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject("channels").entrySet()) channels.put(entry.getKey(), integer(entry.getValue()));
        }
        return new Probe(integer(value.get("count")), channels);
    }

    private static int integer(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("[1-9][0-9]{0,4}")) throw new IllegalArgumentException("Probe values must be positive integers");
        return Integer.parseInt(value.getAsString());
    }

    public JsonObject json() {
        JsonObject values = new JsonObject();
        channels.forEach(values::addProperty);
        return Json.object("count", count, "channels", values);
    }

    @Override public int compareTo(Probe other) {
        int order = Integer.compare(count, other.count);
        if (order != 0) return order;
        Iterator<Map.Entry<String, Integer>> left = channels.entrySet().iterator(), right = other.channels.entrySet().iterator();
        while (left.hasNext() && right.hasNext()) {
            Map.Entry<String, Integer> a = left.next(), b = right.next();
            order = a.getKey().compareTo(b.getKey());
            if (order == 0) order = Integer.compare(a.getValue(), b.getValue());
            if (order != 0) return order;
        }
        return Boolean.compare(left.hasNext(), right.hasNext());
    }
    @Override public boolean equals(Object other) { return other instanceof Probe && count == ((Probe) other).count && channels.equals(((Probe) other).channels); }
    @Override public int hashCode() { return 31 * count + channels.hashCode(); }
}
