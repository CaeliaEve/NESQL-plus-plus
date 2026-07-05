package com.github.dcysteine.nesql.exporter.plugin.nei.metadata;

import com.github.dcysteine.nesql.exporter.nativeui.NativeUiExportAbi;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared NEI template geometry rules used by raw-export capture and template catalog generation.
 */
public final class NeiUiTemplateLayoutSpecs {
    private NeiUiTemplateLayoutSpecs() {}

    public static List<UiTemplateSlot> defaultLayoutSlots(String layoutKind) {
        List<UiTemplateSlot> slots = new ArrayList<UiTemplateSlot>();
        if ("crafting-grid".equals(layoutKind)) {
            slots.add(slot("item-input", 0, 3, 3, 30, 12));
            slots.add(slot("item-output", 9, 1, 1, 124, 30));
        } else if ("furnace".equals(layoutKind)) {
            slots.add(slot("item-input", 0, 1, 1, 45, 24));
            slots.add(slot("item-output", 1, 1, 1, 115, 24));
            slots.add(slot("fuel", 2, 1, 1, 45, 46));
        } else if ("fluid-machine".equals(layoutKind)) {
            slots.add(slot("item-input", 0, 3, 2, 18, 16));
            slots.add(slot("fluid-input", 0, 3, 2, 72, 16));
            slots.add(slot("item-output", 6, 3, 2, 126, 16));
        } else if ("machine".equals(layoutKind)) {
            slots.add(slot("item-input", 0, 3, 3, 18, 12));
            slots.add(slot("fluid-input", 0, 1, 3, 76, 12));
            slots.add(slot("item-output", 9, 2, 2, 112, 21));
        } else {
            slots.add(slot("item-input", 0, 3, 2, 24, 18));
            slots.add(slot("item-output", 6, 2, 2, 116, 20));
        }
        return slots;
    }

    public static int boundedSurfaceWidth(String layoutKind, int requestedWidth) {
        return Math.max(Math.max(1, requestedWidth), minimumSurfaceWidth(layoutKind));
    }

    public static int boundedSurfaceHeight(String layoutKind, int requestedHeight) {
        return Math.max(Math.max(1, requestedHeight), minimumSurfaceHeight(layoutKind));
    }

    public static int minimumSurfaceWidth(String layoutKind) {
        int right = 1;
        for (UiTemplateSlot slot : defaultLayoutSlots(layoutKind)) {
            right = Math.max(right, slot.x + Math.max(0, slot.columns - 1) * slot.pitchX + slot.slotWidth);
        }
        return right;
    }

    public static int minimumSurfaceHeight(String layoutKind) {
        int bottom = 1;
        for (UiTemplateSlot slot : defaultLayoutSlots(layoutKind)) {
            bottom = Math.max(bottom, slot.y + Math.max(0, slot.rows - 1) * slot.pitchY + slot.slotHeight);
        }
        return bottom;
    }

    public static JsonArray defaultLayoutSlotsJson(String layoutKind) {
        JsonArray slots = new JsonArray();
        for (UiTemplateSlot slot : defaultLayoutSlots(layoutKind)) {
            slots.add(toJson(slot));
        }
        return slots;
    }

    public static JsonArray defaultProgressBarsJson(String canonicalMachineFamily, String layoutKind) {
        JsonArray bars = new JsonArray();
        if (!isGregTechMachineLayout(canonicalMachineFamily, layoutKind)) {
            return bars;
        }
        JsonObject bar = new JsonObject();
        bar.addProperty("kind", "progress-bar");
        bar.addProperty("role", "gt-progress");
        bar.addProperty("x", 78);
        bar.addProperty("y", 24);
        bar.addProperty("width", 20);
        bar.addProperty("height", 18);
        bar.addProperty("coordinateSpace", NativeUiExportAbi.COORDINATE_SPACE);
        bar.addProperty("anchor", NativeUiExportAbi.ANCHOR);
        bar.addProperty("orientation", "horizontal");
        bar.addProperty("source", "gtnh-basic-ui-properties-default");
        bars.add(bar);
        return bars;
    }

    private static boolean isGregTechMachineLayout(String canonicalMachineFamily, String layoutKind) {
        String family = canonicalMachineFamily == null ? "" : canonicalMachineFamily.trim().toLowerCase(java.util.Locale.ROOT);
        String kind = layoutKind == null ? "" : layoutKind.trim().toLowerCase(java.util.Locale.ROOT);
        return "gregtech-machine".equals(family)
                && ("machine".equals(kind) || "fluid-machine".equals(kind));
    }

    public static UiTemplateSlot slot(
            String role,
            int startIndex,
            int columns,
            int rows,
            int x,
            int y) {
        UiTemplateSlot slot = new UiTemplateSlot();
        slot.role = role;
        slot.startIndex = startIndex;
        slot.columns = columns;
        slot.rows = rows;
        slot.x = x;
        slot.y = y;
        slot.coordinateSpace = NativeUiExportAbi.COORDINATE_SPACE;
        slot.anchor = NativeUiExportAbi.ANCHOR;
        slot.slotWidth = NativeUiExportAbi.SLOT_SIZE;
        slot.slotHeight = NativeUiExportAbi.SLOT_SIZE;
        slot.pitchX = NativeUiExportAbi.SLOT_PITCH;
        slot.pitchY = NativeUiExportAbi.SLOT_PITCH;
        return slot;
    }

    public static JsonObject toJson(UiTemplateSlot slot) {
        JsonObject json = new JsonObject();
        if (slot == null) {
            return json;
        }
        json.addProperty("role", slot.role);
        json.addProperty("startIndex", slot.startIndex);
        json.addProperty("columns", slot.columns);
        json.addProperty("rows", slot.rows);
        json.addProperty("x", slot.x);
        json.addProperty("y", slot.y);
        json.addProperty("coordinateSpace", slot.coordinateSpace);
        json.addProperty("anchor", slot.anchor);
        json.addProperty("slotWidth", slot.slotWidth);
        json.addProperty("slotHeight", slot.slotHeight);
        json.addProperty("pitchX", slot.pitchX);
        json.addProperty("pitchY", slot.pitchY);
        return json;
    }

    public static final class UiTemplateSlot {
        public String role;
        public int startIndex;
        public int columns;
        public int rows;
        public int x;
        public int y;
        public String coordinateSpace;
        public String anchor;
        public int slotWidth;
        public int slotHeight;
        public int pitchX;
        public int pitchY;
    }
}
