package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeDiagramContents.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import codechicken.nei.ItemList;
import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.source.CanonicalJson;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.collect.Multimap;
import com.google.gson.*;
import java.util.*;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;

/** Finite GT native relationships, independent of the original diagram's drawing geometry. */
final class NativeGtDiagramReferences implements NativeReferences {
    static final String PREFIX = "neicustomdiagram.diagramgroup.gregtech.";
    private static final int MAX = 262144;
    private final ICraftingHandler handler;
    private final JsonObject source;
    private final Object matcher;
    private final Object info;
    private final NativeDiagramSelection selection;
    private final List<ItemStack> panel;
    private final Snapshot snapshot;
    private final String signature;
    private boolean closed;

    NativeGtDiagramReferences(ICraftingHandler handler, JsonObject source) {
        this.handler = handler;
        this.source = copy(source);
        String group = handler.getHandlerId();
        if (!source.get("handler").getAsString().equals(handler.getClass().getName())
                || !source.get("key").getAsString().equals(group)
                || !group.startsWith(PREFIX)
                || !Arrays.asList(
                                "circuits",
                                "lenses",
                                "materialparts",
                                "oreprocessing",
                                "materialtools")
                        .contains(group.substring(PREFIX.length())))
            throw fault("Unknown GT diagram source");
        matcher = field(handler, "matcher");
        info = field(handler, "info");
        if (matcher == null || info == null) throw unready();
        selection = new NativeDiagramSelection(info);
        boolean material = group.endsWith(".materialparts") || group.endsWith(".materialtools");
        if (material != isCustom()) throw fault("Native GT matcher family differs");
        panel = ItemList.items;
        if (!ItemList.loadFinished || panel == null) throw unready();
        snapshot = read();
        signature = CanonicalJson.digest(snapshot.records);
    }

    public int size() {
        ensureOpen();
        return snapshot.records.size();
    }

    public String domain() {
        ensureOpen();
        return snapshot.records.get(0).getAsJsonObject().get("domain").getAsString();
    }

    public JsonObject counts() {
        ensureOpen();
        return copy(
                snapshot.records
                        .get(0)
                        .getAsJsonObject()
                        .getAsJsonObject("data")
                        .getAsJsonObject("counts"));
    }

    public void verify() {
        ensureOpen();
        Jobs.checkpoint();
        selection.verify();
        if (field(handler, "matcher") != matcher
                || field(handler, "info") != info
                || (!ItemList.loadFinished
                        || ItemList.items != panel
                        || panel.size() != snapshot.scannedItems)) throw drift();
    }

    public void finishVerification() {
        verify();
        if (!signature.equals(CanonicalJson.digest(read().records))) throw drift();
    }

    public void capture(int index, Facts facts) {
        verify();
        JsonObject row = copy(snapshot.records.get(index).getAsJsonObject());
        publish(row.getAsJsonObject("data"), facts);
        facts.row("references", row);
    }

    public void close() {
        if (!closed) {
            snapshot.contents.items.clear();
            snapshot.contents.fluids.clear();
            closed = true;
        }
    }

    private void publish(JsonElement value, Facts facts) {
        NativeComponentFacts.visit(
                value,
                id -> {
                    if (snapshot.contents.items.containsKey(id)) {
                        if (!id.equals(facts.item(snapshot.contents.items.get(id).copy())))
                            throw drift();
                    } else if (snapshot.contents.fluids.containsKey(id)) {
                        if (!id.equals(facts.fluid(snapshot.contents.fluids.get(id).copy())))
                            throw drift();
                    } else throw drift();
                },
                facts::text);
    }

    private Snapshot read() {
        Snapshot result = new Snapshot();
        String group = handler.getHandlerId();
        List<Object> diagrams = new ArrayList<>();
        IdentityHashMap<Object, Integer> indices = new IdentityHashMap<>();
        NativeDiagramBindings grouped = result.bindings;
        List<JsonObject> bindings = grouped.rows;
        Map<String, JsonArray> actions = new TreeMap<>();
        List<JsonObject> contents = new ArrayList<>();
        if (!isCustom()) {
            if (!matcher.getClass()
                    .getName()
                    .equals(API + "diagram.matcher.ComponentDiagramMatcher"))
                throw fault("Unknown native diagram matcher");
            Map<?, ?> maps = map(field(matcher, "matchData"));
            if (maps.size() != 2) throw fault("Incomplete native crafting/usage matcher");
            for (Map.Entry<?, ?> direction : maps.entrySet()) {
                String mode = ((Enum<?>) direction.getKey()).name().toLowerCase(Locale.ROOT);
                if (!mode.equals("crafting") && !mode.equals("usage")
                        || !(direction.getValue() instanceof Multimap))
                    throw fault("Unknown native matcher binding");
                Multimap<?, ?> values = (Multimap<?, ?>) direction.getValue();
                if (values.size() > MAX) throw fault("Native matcher binding budget exceeded");
                for (Map.Entry<?, ?> entry : values.entries()) {
                    Jobs.checkpoint();
                    int index = diagram(diagrams, indices, entry.getValue());
                    addBinding(
                            result,
                            bindings,
                            object(
                                    "query",
                                    mode,
                                    "component",
                                    result.contents.component(entry.getKey()),
                                    "diagram",
                                    index));
                }
            }
        } else {
            List<?> nativeDiagrams = list(field(matcher, "allDiagrams"));
            for (Object diagram : nativeDiagrams) diagram(diagrams, indices, diagram);
            Object function = field(matcher, "diagramFunction");
            String generator = "com.github.dcysteine.neicustomdiagram.generators.gregtech5.";
            String suffix = group.substring(PREFIX.length());
            String owner =
                    generator
                            + suffix
                            + (suffix.equals("materialparts")
                                    ? ".GregTechMaterialParts"
                                    : ".GregTechMaterialTools");
            List<Object> captured = captured(function);
            if (!function.getClass().getName().startsWith(owner + "$$Lambda$")
                    || captured.size() != 1
                    || !captured.get(0).getClass().getName().equals(owner))
                throw fault("Unknown native material matcher callback");
            Map<?, ?> nativeMaterials = map(field(captured.get(0), "materialsMap"));
            if (nativeMaterials.size() != nativeDiagrams.size())
                throw fault("Incomplete material diagram registry");
            for (Object diagram : nativeMaterials.values())
                if (!indices.containsKey(diagram))
                    throw fault("Material matcher points outside finite diagram closure");
        }
        if (handler.getClass().getName().equals(API + "diagram.CustomDiagramGroup")) {
            Map<?, ?> custom = map(field(handler, "customBehaviorMap"));
            if (!group.equals(PREFIX + "circuits")
                    || custom.size() != 1
                    || !custom.containsKey(group)) throw fault("Unknown GT custom diagram action");
            Object supplier = custom.get(group);
            List<Object> captured = captured(supplier);
            if (!supplier.getClass()
                            .getName()
                            .startsWith(
                                    "com.github.dcysteine.neicustomdiagram.generators.gregtech5.circuits.GregTechCircuits$$Lambda$")
                    || captured.size() != 1
                    || !(captured.get(0) instanceof Collection))
                throw fault("Unknown native circuit overview supplier");
            JsonArray destinations = array();
            for (Object overview : collection(captured.get(0)))
                destinations.add(value(diagram(diagrams, indices, overview)));
            actions.put(group, destinations);
        } else {
            JsonArray all = array();
            for (int i = 0; i < diagrams.size(); i++) all.add(value(i));
            actions.put(group, all);
        }
        for (Object diagram : diagrams) contents.add(content(result, diagram, group));
        NativeDiagramQueries queries =
                new NativeDiagramQueries(
                        matcher,
                        (Boolean) call(info, "ignoreNbt"),
                        result.contents,
                        indices,
                        grouped,
                        !isCustom(),
                        result.contentBytes + grouped.bytes);
        result.scannedItems = panel.size();
        result.scannedFluids = FluidRegistry.getRegisteredFluids().size();
        queries.scan(panel);
        JsonArray records = array();
        records.add(
                object(
                        "kind",
                        "neiDiagramDefinition",
                        "source",
                        source,
                        "group",
                        group,
                        "name",
                        call(info, "groupName"),
                        "description",
                        call(info, "description"),
                        "ignoreNbt",
                        call(info, "ignoreNbt"),
                        "matcher",
                        isCustom() ? "nativeMaterial" : "explicitComponent",
                        "queryScope",
                        "readyItemListRegistryFluidsAndDiagramComponents",
                        "selection",
                        selection.policy(),
                        "ready",
                        true,
                        "counts",
                        object(
                                "diagrams",
                                diagrams.size(),
                                "bindings",
                                bindings.size(),
                                "actions",
                                actions.size(),
                                "lookups",
                                queries.lookups.size(),
                                "scannedItems",
                                result.scannedItems,
                                "scannedFluids",
                                result.scannedFluids),
                        "consuming",
                        false));
        for (int i = 0; i < diagrams.size(); i++)
            append(
                    result,
                    records,
                    object(
                            "kind",
                            "neiDiagram",
                            "source",
                            source,
                            "group",
                            group,
                            "diagram",
                            i,
                            "nativeSlotInsertions",
                            NativeDiagramSelection.insertions(diagrams.get(i)),
                            "content",
                            contents.get(i),
                            "consuming",
                            false));
        for (int i = 0; i < bindings.size(); i++)
            append(
                    result,
                    records,
                    object(
                            "kind",
                            "neiDiagramBinding",
                            "source",
                            source,
                            "group",
                            group,
                            "binding",
                            i,
                            "query",
                            bindings.get(i).get("query"),
                            "component",
                            bindings.get(i).get("component"),
                            "diagrams",
                            bindings.get(i).get("diagrams"),
                            "consuming",
                            false));
        for (int i = 0; i < queries.lookups.size(); i++) {
            JsonObject lookup = queries.lookups.get(i);
            append(
                    result,
                    records,
                    object(
                            "kind",
                            "neiDiagramLookup",
                            "source",
                            source,
                            "group",
                            group,
                            "lookup",
                            i,
                            "original",
                            lookup.get("original"),
                            "damage",
                            lookup.get("damage"),
                            "bindings",
                            lookup.get("bindings"),
                            "consuming",
                            false));
        }
        int action = 0;
        for (Map.Entry<String, JsonArray> entry : actions.entrySet())
            append(
                    result,
                    records,
                    object(
                            "kind",
                            "neiDiagramAction",
                            "source",
                            source,
                            "group",
                            group,
                            "action",
                            action++,
                            "key",
                            entry.getKey(),
                            "diagrams",
                            entry.getValue(),
                            "consuming",
                            false));
        String domain =
                Identity.content("reference", object("kind", "neiDiagrams", "records", records));
        for (int i = 0; i < records.size(); i++) {
            JsonObject row = object("domain", domain, "order", i, "data", records.get(i));
            row.addProperty("id", Identity.content("refrow", row));
            result.records.add(row);
        }
        return result;
    }

    private static int diagram(
            List<Object> diagrams, IdentityHashMap<Object, Integer> indices, Object value) {
        if (indices.containsKey(value)) return indices.get(value);
        if (diagrams.size() >= MAX) throw fault("Native diagram budget exceeded");
        int index = diagrams.size();
        indices.put(value, index);
        diagrams.add(value);
        return index;
    }

    private static JsonObject content(Snapshot snapshot, Object diagram, String group) {
        JsonObject content = snapshot.contents.read(diagram);
        Set<String> keys = new TreeSet<>();
        for (JsonElement cell : content.getAsJsonArray("cells"))
            if (cell.getAsJsonObject().getAsJsonArray("alternatives").size() > 0)
                keys.add(cell.getAsJsonObject().get("group").getAsString());
        JsonArray relations = array();
        // These associations come from the audited factory insertion/branch code, not arrow pixels.
        if (group.endsWith(".circuits")) {
            for (String key : keys)
                if (key.startsWith("recipe-item-inputs-") || key.equals("recipe-fluid-inputs"))
                    relation(relations, keys, key, "recipe-output", "circuitRecipe");
        } else if (group.endsWith(".lenses")) {
            for (String key : keys)
                if (key.startsWith("recipe-input-"))
                    relation(
                            relations,
                            keys,
                            key,
                            "recipe-output-" + key.substring("recipe-input-".length()),
                            "lensRecipe");
        } else if (group.endsWith(".oreprocessing")) {
            String[][] steps = {
                {"raw-ore", "raw-ore-macerate"},
                {"true-raw-ore", "true-raw-ore-macerate"},
                {"raw-ore-macerate", "crushed-ore-macerate"},
                {"raw-ore-macerate", "crushed-ore-wash"},
                {"raw-ore-macerate", "crushed-ore-bath-mercury"},
                {"raw-ore-macerate", "crushed-ore-bath-sodium-persulfate"},
                {"crushed-ore-wash", "purified-ore-macerate"},
                {"crushed-ore-wash", "purified-ore-sift"},
                {"crushed-ore-macerate", "impure-dust-centrifuge"},
                {"purified-ore-macerate", "purified-dust-centrifuge"},
                {"purified-ore-macerate", "purified-dust-electromagnetic-separate"},
                {"raw-ore-macerate", "ore-thermal-centrifuge"},
                {"crushed-ore-wash", "ore-thermal-centrifuge"},
                {"raw-ore-macerate", "ore-thermal-centrifuge-crushed"},
                {"crushed-ore-wash", "ore-thermal-centrifuge-purified"},
                {"ore-thermal-centrifuge", "ore-thermal-centrifuge-macerate"},
                {"ore-thermal-centrifuge-crushed", "ore-thermal-centrifuge-macerate-extra"},
                {"ore-thermal-centrifuge-purified", "ore-thermal-centrifuge-macerate-extra"}
            };
            for (String[] step : steps) relation(relations, keys, step[0], step[1], "oreStage");
        }
        content.add("relations", relations);
        int bytes = CanonicalJson.bytes(content).length;
        if (bytes > 1024 * 1024
                || (snapshot.contentBytes += bytes) + snapshot.bindings.bytes > 32L * 1024 * 1024)
            throw fault("Native diagram content byte budget exceeded before transport allocation");
        return content;
    }

    private static void addBinding(
            Snapshot snapshot, List<JsonObject> bindings, JsonObject binding) {
        snapshot.bindings.add(binding, binding.get("diagram").getAsInt());
        if (snapshot.bindings.bytes + snapshot.contentBytes > 32L * 1024 * 1024)
            throw fault("Native diagram binding byte budget exceeded before transport allocation");
    }

    private static void relation(
            JsonArray relations, Set<String> keys, String from, String to, String kind) {
        if (keys.contains(from) && keys.contains(to))
            relations.add(object("kind", kind, "from", from, "to", to));
    }

    private static void append(Snapshot snapshot, JsonArray records, JsonObject data) {
        int bytes = CanonicalJson.bytes(data).length;
        if (records.size() >= MAX
                || bytes > 1024 * 1024
                || (snapshot.bytes += bytes) > 32L * 1024 * 1024)
            throw fault("Native diagram transport budget exceeded");
        records.add(data);
    }

    private boolean isCustom() {
        return matcher.getClass().getName().equals(API + "diagram.matcher.CustomDiagramMatcher");
    }

    private void ensureOpen() {
        if (closed) throw new Jobs.Fault("capture_closed", "GT diagram reader is closed");
    }

    private static JsonObject copy(JsonObject value) {
        return new JsonParser().parse(value.toString()).getAsJsonObject();
    }

    private static Jobs.Fault unready() {
        return new Jobs.Fault("handler_not_ready", "Native GT diagram/item registry is not ready");
    }

    private static Jobs.Fault drift() {
        return new Jobs.Fault(
                "environment_changed",
                "Native GT diagram content, matcher, quantity or language changed");
    }

    static final class Snapshot {
        final JsonArray records = array();
        final NativeDiagramContents contents = new NativeDiagramContents();
        int scannedItems, scannedFluids;
        final NativeDiagramBindings bindings = new NativeDiagramBindings();
        long bytes, contentBytes;
    }
}
