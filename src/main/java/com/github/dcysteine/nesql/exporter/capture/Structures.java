package com.github.dcysteine.nesql.exporter.capture;

import blockrenderer6343.client.world.DummyWorld;
import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.source.Probe;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.relauncher.ReflectionHelper;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Reads definitions and captures native construction in an owned preview world. */
final class Structures {
    static List<Machine> all() {
        version("structurelib", "1.4.23");
        version("blockrenderer6343", "1.3.17");
        List<Machine> result = new ArrayList<>();
        for (int id = 0; id < GregTechAPI.METATILEENTITIES.length; id++) {
            IMetaTileEntity machine = GregTechAPI.METATILEENTITIES[id];
            if (machine instanceof IConstructable) result.add(new Machine(id, machine));
        }
        return result;
    }

    static void version(String id, String version) {
        ModContainer mod = Loader.instance().getIndexedModList().get(id);
        if (mod == null || !version.equals(mod.getVersion())) {
            throw new Jobs.Fault("structure_version", "Structure capture requires " + id + " " + version);
        }
    }

    static final class Machine {
        final int id;
        final IMetaTileEntity machine;
        Machine(int id, IMetaTileEntity machine) { this.id = id; this.machine = machine; }
    }

    static final class Cursor implements AutoCloseable {
        private Object context;
        private final Facts facts;
        private final Machine source;
        private final Models models;
        private final List<Probe> probes;
        private Preview preview;
        private final boolean complete;
        private final JsonObject record;
        private final JsonArray pieces = new JsonArray();
        private final JsonArray variants = new JsonArray();
        private StructureDefinition<Object> definition;
        private Iterator<Map.Entry<String, String>> definitions;
        private Map<String, Set<Vec3Impl>> occupied;
        private final ItemStack trigger;
        private DummyWorld world;
        private final AutoPlaceEnvironment environment = AutoPlaceEnvironment.fromLegacy(null, null, message -> {});
        private Map.Entry<String, String> entry;
        private IStructureElement<Object>[] elements;
        private final Map<Character, Integer> symbols = new LinkedHashMap<>();
        private final Set<Vec3Impl> visited = new HashSet<>();
        private JsonArray rules, chunks, cells, anchors;
        private final int[] position = new int[3], size = new int[3];
        private Iterator<Vec3Impl> remaining;
        private int next, count, variant;
        private boolean finished;

        Cursor(Machine source, Facts facts, Models models, List<Probe> probes, boolean complete) {
            this.facts = facts;
            this.source = source;
            this.models = models;
            this.probes = Probe.order(probes);
            this.complete = complete;
            trigger = Preview.trigger(this.probes.get(0));
            ItemStack controller = source.machine.getStackForm(1);
            JsonObject origin = object("owner", "gregtech", "handler", source.machine.getClass().getName(), "key", Integer.toString(source.id));
            JsonArray description = new JsonArray();
            record = object("id", Identity.origin("structure", origin), "source", origin, "name", facts.text(controller.getDisplayName()),
                    "controller", facts.item(controller), "description", description, "probe", this.probes.get(0).json(),
                    "pieces", pieces, "variants", variants, "problem", null);
            try {
                if (open()) describe(description);
                else record.addProperty("problem", facts.text("首组参数无法创建控制器预览，未读取片段定义。"));
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        @SuppressWarnings("unchecked")
        private void describe(JsonArray description) {
            context = preview.machine;
            IConstructable constructable = (IConstructable) context;
            String[] lines = constructable.getStructureDescription(trigger.copy());
            if (lines == null || lines.length > 1024) throw new Jobs.Fault("structure_description", "Invalid structure description");
            for (String line : lines) description.add(value(facts.text(line)));
            IStructureDefinition<?> supplied = constructable.getStructureDefinition();
            if (!(supplied instanceof StructureDefinition) || ((StructureDefinition<?>) supplied).getShapes().isEmpty()) {
                if (complete) throw new Jobs.Fault("structure_unsupported", "No enumerable structure definition: " + context.getClass().getName());
                record.addProperty("problem", facts.text("此控制器没有可枚举的结构定义。"));
            } else {
                definition = (StructureDefinition<Object>) supplied;
                if (definition.getShapes().size() > 256) throw new Jobs.Fault("structure_limit", "Too many structure pieces");
                definitions = new TreeMap<>(definition.getShapes()).entrySet().iterator();
                // Target 1.4.23 replaces '~' with navigation, but retains those positions in occupiedSpaces.
                occupied = ReflectionHelper.getPrivateValue(StructureDefinition.class, definition, "occupiedSpaces");
                world = preview.world;
            }
        }

        /** Navigation, metadata probes and occupied-space scans share one bounded client-thread cursor. */
        boolean capture() {
            if (finished) throw new IllegalStateException("Structure already captured");
            long deadline = System.nanoTime() + 2_000_000;
            for (int work = 0; work < 256; work++) {
                Jobs.checkpoint();
                if (work > 0 && System.nanoTime() >= deadline) return false;
                if (definition == null) return finish();
                if (entry == null) {
                    if (!definitions.hasNext()) return finish();
                    begin();
                }
                if (next < elements.length) {
                    IStructureElement<Object> element = elements[next];
                    char symbol = entry.getValue().charAt(next++);
                    if (element.isNavigating()) {
                        position[0] = (element.resetA() ? 0 : position[0]) + element.getStepA();
                        position[1] = (element.resetB() ? 0 : position[1]) + element.getStepB();
                        position[2] = (element.resetC() ? 0 : position[2]) + element.getStepC();
                        continue;
                    }
                    Vec3Impl at = new Vec3Impl(position[0], position[1], position[2]);
                    bounds(at);
                    if (!occupied.get(entry.getKey()).contains(at) || !visited.add(at)) {
                        throw new Jobs.Fault("structure_position", "Structure navigation disagrees with occupied positions");
                    }
                    Integer rule = symbols.get(symbol);
                    if (rule == null) {
                        if (symbols.size() >= 4096) throw new Jobs.Fault("structure_limit", "Too many structure rules");
                        rule = symbols.size(); symbols.put(symbol, rule);
                        rules.add(rule(symbol, element));
                    }
                    cells.add(object("at", coordinates(at), "index", rule));
                    if (++count > 1_048_576) throw new Jobs.Fault("structure_limit", "Structure exceeds its geometry budget");
                    if (cells.size() == 2048) flush();
                    position[0]++;
                } else {
                    if (remaining == null) { flush(); remaining = occupied.get(entry.getKey()).iterator(); }
                    if (remaining.hasNext()) {
                        Vec3Impl at = remaining.next();
                        if (!visited.contains(at)) {
                            if (anchors.size() >= 64) throw new Jobs.Fault("structure_limit", "Too many controller markers");
                            bounds(at); anchors.add(coordinates(at));
                        }
                    } else {
                        List<com.google.gson.JsonElement> ordered = new ArrayList<>();
                        for (com.google.gson.JsonElement anchor : anchors) ordered.add(anchor);
                        ordered.sort((left, right) -> {
                            for (int axis = 2; axis >= 0; axis--) {
                                int comparison = Integer.compare(left.getAsJsonArray().get(axis).getAsInt(), right.getAsJsonArray().get(axis).getAsInt());
                                if (comparison != 0) return comparison;
                            }
                            return 0;
                        });
                        JsonArray markers = new JsonArray(); ordered.forEach(markers::add);
                        pieces.add(object("name", entry.getKey(), "size", array(size[0], size[1], size[2]), "anchors", markers,
                                "rules", rules, "chunks", chunks, "cells", count));
                        entry = null;
                    }
                }
            }
            return false;
        }

        private void begin() {
            entry = definitions.next();
            elements = definition.getStructureFor(entry.getKey());
            if (elements.length != entry.getValue().length() || !occupied.containsKey(entry.getKey())) {
                throw new Jobs.Fault("structure_definition", "Structure symbols and instructions disagree");
            }
            symbols.clear(); visited.clear();
            rules = new JsonArray(); chunks = new JsonArray(); cells = new JsonArray(); anchors = new JsonArray();
            java.util.Arrays.fill(position, 0); java.util.Arrays.fill(size, 0);
            next = 0; count = 0; remaining = null;
        }

        private JsonObject rule(char symbol, IStructureElement<Object> element) {
            String kind = element.getClass() == StructureUtility.isAir().getClass() ? "air"
                    : element.getClass() == StructureUtility.notAir().getClass() ? "solid" : "element";
            JsonArray placements = null;
            if (kind.equals("element")) {
                IStructureElement.BlocksToPlace blocks = element.getBlocksToPlace(context, world, 0, 64, 0, trigger.copy(), environment);
                if (blocks != null && blocks.getStacks() != null) {
                    TreeSet<String> ids = new TreeSet<>();
                    int scanned = 0;
                    for (ItemStack stack : blocks.getStacks()) {
                        if (++scanned > 1024) throw new Jobs.Fault("structure_limit", "Too many advisory placement stacks");
                        ids.add(facts.item(stack));
                    }
                    placements = new JsonArray();
                    for (String id : ids) placements.add(value(id));
                }
            }
            return object("symbol", String.valueOf(symbol), "kind", kind, "implementation", element.getClass().getName(), "placements", placements);
        }

        private void bounds(Vec3Impl at) {
            int[] values = {at.get0(), at.get1(), at.get2()};
            for (int axis = 0; axis < 3; axis++) {
                if (values[axis] < 0 || values[axis] >= 65536) throw new Jobs.Fault("structure_position", "Structure coordinate outside supported bounds");
                size[axis] = Math.max(size[axis], values[axis] + 1);
            }
        }

        private void flush() {
            if (cells.size() == 0) return;
            JsonObject shape = object("cells", cells);
            String id = Identity.content("shape", shape);
            shape.addProperty("id", id); facts.row("shapes", shape); chunks.add(value(id)); cells = new JsonArray();
        }

        private boolean finish() {
            // Definition probes belong to the first parameter set; release that context before later builds.
            context = null; world = null; definition = null; definitions = null; occupied = null; elements = null;
            visited.clear();
            if (variant < probes.size()) {
                if (preview == null && !open()) return false;
                try { if (!preview.build()) return false; }
                catch (Jobs.Fault failure) {
                    if (complete || !failure.code.startsWith("preview_")) throw failure;
                    close();
                    outcome(null, facts.text(failure.getMessage()));
                    return false;
                }
                // Once records are emitted, an error must abort publication, never leave orphaned geometry.
                String build = preview.capture(facts, record.get("id").getAsString());
                if (build == null) return false;
                close();
                outcome(build, null);
                return false;
            }
            facts.row("structures", record); finished = true; return true;
        }

        private boolean open() {
            try { preview = new Preview(source, probes.get(variant), models, complete); return true; }
            catch (Jobs.Fault failure) {
                if (complete || !failure.code.startsWith("preview_")) throw failure;
                outcome(null, facts.text(failure.getMessage()));
                return false;
            }
        }

        private void outcome(String build, String problem) {
            variants.add(object("probe", probes.get(variant++).json(), "build", build, "problem", problem));
        }

        @Override public void close() {
            if (preview != null) {
                Preview owned = preview; preview = null;
                owned.close();
            }
        }
        private static JsonArray coordinates(Vec3Impl at) { return array(at.get0(), at.get1(), at.get2()); }
    }
}
