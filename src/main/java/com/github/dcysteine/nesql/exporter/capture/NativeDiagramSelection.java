package com.github.dcysteine.nesql.exporter.capture;

import static com.github.dcysteine.nesql.exporter.capture.MagicApi.*;
import static com.github.dcysteine.nesql.exporter.capture.NativeDiagramContents.*;
import static com.github.dcysteine.nesql.exporter.source.Json.*;

import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.common.collect.Multiset;
import com.google.gson.JsonObject;
import java.util.*;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/** Read-only native selection declarations. Never executes predicates or keyboard/GUI callbacks. */
final class NativeDiagramSelection {
    private static final String ROOT = "com.github.dcysteine.neicustomdiagram.";
    private static final Set<String> VISIBILITIES =
            new HashSet<>(
                    Arrays.asList(
                            "ALWAYS_SHOWN",
                            "ALWAYS_HIDDEN",
                            "SHOW_ON_SHIFT",
                            "HIDE_ON_SHIFT",
                            "SHOW_ON_CTRL",
                            "HIDE_ON_CTRL",
                            "DISABLED"));
    private final Object info, option, predicate;
    private final Configuration config;
    private final JsonObject empty;
    private final JsonObject policy;

    NativeDiagramSelection(Object info) {
        this.info = info;
        predicate = call(info, "emptyDiagramPredicate");
        Class<?> infoType = type(API + "diagram.DiagramGroupInfo");
        Object defaultBuilder =
                invoke(
                        infoType,
                        null,
                        "builder",
                        new Class<?>[] {
                            String.class,
                            String.class,
                            type(API + "diagram.component.ItemComponent"),
                            int.class
                        },
                        call(info, "groupName"),
                        call(info, "groupId"),
                        call(info, "icon"),
                        1);
        Object defaultPredicate = call(call(defaultBuilder, "build"), "emptyDiagramPredicate");
        Object thresholdPredicate =
                invoke(
                        type(ROOT + "util.DiagramUtil"),
                        null,
                        "buildEmptyDiagramPredicate",
                        new Class<?>[] {int.class},
                        0);
        List<Object> captures = captured(predicate);
        if (predicate.getClass() == defaultPredicate.getClass() && captures.isEmpty())
            empty = object("kind", "never");
        else if (predicate.getClass() == thresholdPredicate.getClass()
                && captures.size() == 1
                && captures.get(0) instanceof Integer) {
            int minimum = (Integer) captures.get(0);
            if (minimum < 0 || minimum > 262144)
                throw fault("Native diagram predicate threshold exceeds budget");
            empty = object("kind", "slotInsertionsBelow", "minimum", minimum);
        } else
            throw fault(
                    "Unknown native empty-diagram predicate: " + predicate.getClass().getName());
        option = field(type(ROOT + "main.config.ConfigOptions"), null, "SHOW_EMPTY_DIAGRAMS");
        Object nativeConfig = field(type(ROOT + "main.config.Config"), null, "CONFIG");
        if (!(nativeConfig instanceof Configuration)) throw unready();
        config = (Configuration) nativeConfig;
        policy = read();
    }

    JsonObject policy() {
        verify();
        return new com.google.gson.JsonParser().parse(policy.toString()).getAsJsonObject();
    }

    void verify() {
        Jobs.checkpoint();
        if (call(info, "emptyDiagramPredicate") != predicate
                || field(type(ROOT + "main.config.ConfigOptions"), null, "SHOW_EMPTY_DIAGRAMS")
                        != option
                || field(type(ROOT + "main.config.Config"), null, "CONFIG") != config
                || !policy.equals(read()))
            throw new Jobs.Fault(
                    "environment_changed", "Native diagram selection configuration changed");
    }

    private JsonObject read() {
        Object property = field(option, "property");
        if (!(property instanceof Property)) throw unready();
        String nativeDefault = ((Enum<?>) call(info, "defaultVisibility")).name();
        if (!VISIBILITIES.contains(nativeDefault))
            throw fault("Unknown native diagram default visibility");
        String group = (String) call(info, "groupId");
        // ConfigOptions.getDiagramGroupVisibility would create entries and migrate old booleans.
        // Inspect existing properties and represent its audited result without mutating
        // configuration.
        Property configured =
                config.hasCategory("diagram_groups")
                        ? config.getCategory("diagram_groups").get(group)
                        : null;
        String raw = configured == null ? null : configured.getString();
        if (raw != null && raw.length() > 65536)
            throw fault("Native diagram visibility text exceeds budget");
        String effective = raw == null ? nativeDefault : raw;
        if (effective.equals("true")) effective = "ALWAYS_SHOWN";
        else if (effective.equals("false")) effective = "DISABLED";
        else if (!VISIBILITIES.contains(effective)) effective = "ALWAYS_SHOWN";
        return object(
                "showEmptyDiagrams",
                ((Property) property).getBoolean(),
                "empty",
                empty,
                "defaultVisibility",
                nativeDefault,
                "configuredVisibility",
                raw,
                "visibility",
                effective);
    }

    static int insertions(Object diagram) {
        Object value = call(diagram, "slotInsertions");
        if (!(value instanceof Multiset)) throw fault("Missing native diagram insertion multiset");
        Multiset<?> insertions = (Multiset<?>) value;
        if (insertions.size() > 262144) throw fault("Native diagram insertion budget exceeded");
        return insertions.size();
    }

    private static Jobs.Fault unready() {
        return new Jobs.Fault(
                "handler_not_ready", "Native diagram selection configuration is not initialized");
    }
}
