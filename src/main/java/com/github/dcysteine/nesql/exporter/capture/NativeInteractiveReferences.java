package com.github.dcysteine.nesql.exporter.capture;

import codechicken.nei.recipe.ICraftingHandler;
import com.github.dcysteine.nesql.exporter.task.Jobs;
import com.google.gson.JsonObject;
import java.util.*;

/** Native diagram routing. Dimensions-only descriptors are not semantic adaptation. */
final class NativeInteractiveReferences implements NativeReferences {
    private static final String GROUP =
            "com.github.dcysteine.neicustomdiagram.api.diagram.DiagramGroup";
    private static final String CUSTOM =
            "com.github.dcysteine.neicustomdiagram.api.diagram.CustomDiagramGroup";
    private static final Set<String> GROUPS =
            new HashSet<>(
                    Arrays.asList(
                            "neicustomdiagram.diagramgroup.enderstorage.tankoverview",
                            "neicustomdiagram.diagramgroup.enderstorage.chestoverview",
                            NativeGtDiagramReferences.PREFIX + "circuits",
                            NativeGtDiagramReferences.PREFIX + "lenses",
                            NativeGtDiagramReferences.PREFIX + "materialparts",
                            NativeGtDiagramReferences.PREFIX + "oreprocessing",
                            NativeGtDiagramReferences.PREFIX + "materialtools"));
    private final NativeReferences reader;

    static boolean supports(ICraftingHandler value) {
        return value != null
                && (GROUP.equals(value.getClass().getName())
                        || CUSTOM.equals(value.getClass().getName()))
                && GROUPS.contains(value.getHandlerId());
    }

    NativeInteractiveReferences(ICraftingHandler handler, JsonObject source) {
        if (!supports(handler))
            throw new Jobs.Fault("reference_unsupported", "Unknown native diagram group");
        if (handler.getHandlerId().startsWith(NativeGtDiagramReferences.PREFIX))
            reader = new NativeGtDiagramReferences(handler, source);
        else reader = new NativeEnderStorageReferences(handler, source);
    }

    public int size() {
        return reader.size();
    }

    public String domain() {
        return reader.domain();
    }

    public JsonObject counts() {
        return reader.counts();
    }

    public void verify() {
        reader.verify();
    }

    public void finishVerification() {
        reader.finishVerification();
    }

    public void capture(int index, Facts facts) {
        reader.capture(index, facts);
    }

    public void close() {
        reader.close();
    }
}
