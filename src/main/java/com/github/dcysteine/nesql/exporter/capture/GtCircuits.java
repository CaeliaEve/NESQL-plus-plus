package com.github.dcysteine.nesql.exporter.capture;

import com.github.dcysteine.nesql.exporter.source.Identity;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import gregtech.api.enums.GTValues;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.dcysteine.nesql.exporter.source.Json.*;

/** Reads the target diagram provider's definitions instead of maintaining another circuit list. */
final class GtCircuits {
    private static final String PACKAGE = "com.github.dcysteine.neicustomdiagram.";
    private static final String HANDLER = PACKAGE + "generators.gregtech5.circuits.CircuitLineHandler";
    private final List<Family> families = new ArrayList<>();
    private final Method stack;

    GtCircuits() throws Exception {
        ModContainer mod = Loader.instance().getIndexedModList().get("neicustomdiagram");
        if (mod == null || !"1.7.5".equals(mod.getVersion())) {
            throw new Jobs.Fault("domain_unsupported", "Circuit definitions require the target NEICustomDiagram 1.7.5");
        }
        Class<?> type = Class.forName(HANDLER);
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object definitions = constructor.newInstance();
        // This target method creates definition lists and indexes on its own instance only.
        method(type, "initialize").invoke(definitions);
        Class<?> line = Class.forName(PACKAGE + "generators.gregtech5.circuits.CircuitLine");
        Method boards = method(line, "boards"), circuits = method(line, "circuits"), start = method(line, "startTier");
        for (String group : new String[] {"circuitLines", "individualCircuits"}) {
            for (Object family : list(method(type, group).invoke(definitions))) {
                families.add(new Family(list(boards.invoke(family)), list(circuits.invoke(family)), (Integer) start.invoke(family)));
            }
        }
        for (Object parts : list(method(type, "circuitParts").invoke(definitions))) {
            families.add(new Family(Collections.emptyList(), list(parts), null));
        }
        java.lang.reflect.Field additional = type.getDeclaredField("additionalDiagramItems");
        additional.setAccessible(true);
        List<?> items = list(additional.get(definitions));
        if (!items.isEmpty()) families.add(new Family(Collections.emptyList(), items, null));
        if (families.isEmpty() || families.size() > 256) throw new Jobs.Fault("circuit_limit", "Invalid circuit definition count");
        stack = Class.forName(PACKAGE + "api.diagram.component.ItemComponent").getMethod("stack", int.class);
    }

    int size() { return families.size(); }

    void capture(int index, Facts facts) throws Exception {
        Jobs.checkpoint();
        Family family = families.get(index);
        if (family.steps.isEmpty() || family.steps.size() > 256 || family.boards.size() > 256) throw new Jobs.Fault("circuit_limit", "Invalid circuit family");
        JsonArray boards = new JsonArray(), steps = new JsonArray();
        for (Object board : family.boards) boards.add(value(facts.item(item(board))));
        for (int step = 0; step < family.steps.size(); step++) {
            JsonObject tier = null;
            if (family.start != null) {
                int level = family.start + step;
                if (level < 0 || level >= GTValues.V.length || level >= GTValues.VN.length) throw new Jobs.Fault("circuit_tier", "Unknown voltage tier");
                tier = object("level", level, "name", facts.text(GTValues.VN[level]), "voltage", Long.toString(GTValues.V[level]));
            }
            steps.add(object("item", facts.item(item(family.steps.get(step))), "tier", tier));
        }
        String kind = family.start == null ? "parts" : "line";
        JsonObject source = object("owner", "neicustomdiagram", "handler", HANDLER,
                "key", kind + ":" + steps.get(0).getAsJsonObject().get("item").getAsString());
        facts.row("circuits", object("id", Identity.origin("circuit", source), "source", source,
                "name", facts.text(item(family.steps.get(0)).getDisplayName()), "kind", kind,
                "boards", boards, "steps", steps, "order", index));
    }

    private ItemStack item(Object component) throws Exception {
        Object item = stack.invoke(component, 1);
        if (!(item instanceof ItemStack)) throw new Jobs.Fault("circuit_item", "Circuit definition does not contain an item");
        return ((ItemStack) item).copy();
    }

    private static List<?> list(Object value) {
        if (!(value instanceof List<?>)) throw new Jobs.Fault("circuit_contract", "Circuit definitions do not match the target API");
        return (List<?>) value;
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method;
    }

    private static final class Family {
        final List<?> boards, steps;
        final Integer start;
        Family(List<?> boards, List<?> steps, Integer start) { this.boards = boards; this.steps = steps; this.start = start; }
    }
}
