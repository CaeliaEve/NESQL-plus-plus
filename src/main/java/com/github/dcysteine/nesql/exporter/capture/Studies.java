package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategoryList;
import thaumcraft.api.research.ResearchItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

import static com.github.dcysteine.nesql.exporter.source.Json.object;

/** Global research identities resolved by the game's API, including its installed patches. */
final class Studies {
    private final Map<String, ResearchItem> studies = new TreeMap<>();
    private final Function<String, ResearchItem> lookup;
    private final JsonArray rows = new JsonArray();
    private final JsonArray problems = new JsonArray();
    private int entries, references, aliases, details, conflicts;
    private String failure;

    static Studies capture() {
        return new Studies(ResearchCategories.researchCategories, ResearchCategories::getResearch);
    }

    Studies(Map<String, ResearchCategoryList> categories, Function<String, ResearchItem> lookup) {
        this.lookup = lookup;
        Map<String, List<Entry>> groups = new TreeMap<>();
        for (Map.Entry<String, ResearchCategoryList> category : categories.entrySet()) {
            if (category.getValue() == null || category.getValue().research == null) {
                problem(object("category", label(category.getKey())), "Missing research category: " + label(category.getKey()));
                continue;
            }
            for (Map.Entry<String, ResearchItem> entry : category.getValue().research.entrySet()) {
                entries++;
                ResearchItem item = entry.getValue();
                if (item == null || item.key == null || item.key.isEmpty() || item.key.length() > 256) {
                    problem(object("category", label(category.getKey()), "entry", label(entry.getKey())),
                            "Invalid research at " + label(category.getKey()) + "/" + label(entry.getKey()));
                    continue;
                }
                groups.computeIfAbsent(item.key, key -> new ArrayList<>()).add(new Entry(category.getKey(), entry.getKey(), item));
            }
        }
        for (Map.Entry<String, List<Entry>> group : groups.entrySet()) {
            String key = group.getKey();
            List<Entry> registered = group.getValue();
            ResearchItem resolved = lookup.apply(key);
            boolean found = false, unusual = registered.size() > 1;
            String error = null;
            for (Entry entry : registered) {
                unusual |= !key.equals(entry.key) || !Objects.equals(entry.item.category, entry.category);
                if (entry.item == resolved) {
                    if (found) aliases++;
                    found = true;
                } else if (marker(entry.item)) {
                    references++;
                } else {
                    error = "Research '" + key + "' has a competing definition at " + entry.location()
                            + "; the game resolves " + (resolved == null ? "nothing" : label(resolved.category) + " (" + resolved.getClass().getName() + ")");
                }
            }
            if (!found || resolved == null || !key.equals(resolved.key)) {
                error = "Research '" + key + "' resolves outside its registrations: " + registered.get(0).location();
            }
            if (resolved != null && (resolved.category == null || resolved.category.isEmpty())) {
                error = "Research '" + key + "' has no declared category";
            }
            if (error == null) studies.put(key, resolved);
            if (unusual || error != null) {
                JsonArray locations = new JsonArray();
                for (int index = 0; index < Math.min(8, registered.size()); index++) locations.add(registered.get(index).describe(resolved));
                JsonObject row = object("key", key, "resolved", resolved == null ? null : object("category", label(resolved.category),
                                "type", label(resolved.getClass().getName()), "virtual", resolved.isVirtual()),
                        "entries", registered.size(), "registrations", locations, "omitted", registered.size() - locations.size(), "valid", error == null);
                if (error == null) detail(row);
                else problem(row, error);
            }
        }
        if (groups.isEmpty()) problem(new JsonObject(), "Research registry has not loaded");
    }

    List<ResearchItem> all() {
        require();
        return new ArrayList<>(studies.values());
    }

    ResearchItem get(String key) {
        require();
        key(key);
        ResearchItem expected = studies.get(key);
        if (lookup.apply(key) != expected) throw new Jobs.Fault("registry_changed", "Research lookup changed during export: " + key);
        return reference(key, expected);
    }

    static ResearchItem find(String key) {
        key(key);
        return reference(key, ResearchCategories.getResearch(key));
    }

    JsonObject describe() {
        JsonArray report = new JsonArray();
        problems.forEach(report::add);
        for (int index = 0; index < rows.size() && report.size() < 32; index++) report.add(rows.get(index));
        return object("valid", failure == null, "count", studies.size(), "entries", entries, "references", references,
                "aliases", aliases, "conflicts", conflicts, "rows", report, "omitted", details - report.size());
    }

    private void require() {
        if (failure != null) throw new Jobs.Fault("research_registry", failure + "; inspect_game.research.rows contains registration details");
    }

    private void problem(JsonObject row, String message) {
        if (failure == null) failure = message;
        conflicts++;
        row.addProperty("valid", false);
        row.add("error", object("code", "research_registry", "message", message));
        details++;
        if (problems.size() < 32) problems.add(row);
    }

    private void detail(JsonObject row) {
        details++;
        // Keep inspect_game within the bridge response budget, alongside mods and handlers.
        if (rows.size() < 32) rows.add(row);
    }

    private static void key(String key) {
        if (key == null || key.isEmpty() || key.length() > 256) throw new Jobs.Fault("research_key", "Invalid research prerequisite: " + label(key));
    }

    private static ResearchItem reference(String key, ResearchItem study) {
        if (study == null && !key.startsWith("@")) throw new Jobs.Fault("research_reference", "Research refers to an unregistered key: " + key);
        if (study != null && !key.equals(study.key)) throw new Jobs.Fault("research_registry", "Research lookup for '" + key + "' returned '" + label(study.key) + "'");
        return study;
    }

    private static boolean marker(ResearchItem item) {
        // Gadomancy registers plain, empty virtual items under existing TC keys.
        // Only those references may be folded into the API's chosen definition.
        // Virtual items with their own content, unlocks or custom behavior are retained/conflicted.
        return item.getClass() == ResearchItem.class && item.isVirtual() && item.getComplexity() == 0
                && item.displayColumn == 0 && item.displayRow == 0 && item.icon_item == null && item.icon_resource == null
                && item.tags != null && item.tags.size() == 0
                && empty(item.parents) && empty(item.parentsHidden) && empty(item.siblings) && empty(item.getPages())
                && empty(item.getItemTriggers()) && empty(item.getEntityTriggers()) && empty(item.getAspectTriggers())
                && !item.isAutoUnlock() && !item.isConcealed() && !item.isHidden() && !item.isLost()
                && !item.isRound() && !item.isSecondary() && !item.isSpecial() && !item.isStub();
    }

    private static boolean empty(Object[] values) { return values == null || values.length == 0; }
    private static String label(String value) { return value == null || value.length() <= 256 ? value : value.substring(0, 256) + "…"; }

    private static final class Entry {
        final String category, key;
        final ResearchItem item;

        Entry(String category, String key, ResearchItem item) { this.category = category; this.key = key; this.item = item; }
        String location() { return label(category) + "/" + label(key) + " (" + item.getClass().getName() + ")"; }
        JsonObject describe(ResearchItem resolved) {
            return object("category", label(category), "entry", label(key), "declaredCategory", label(item.category),
                    "type", label(item.getClass().getName()), "virtual", item.isVirtual(), "reference", marker(item), "selected", item == resolved);
        }
    }
}
